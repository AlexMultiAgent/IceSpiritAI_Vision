package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.TextNormalizer
import java.util.TreeMap

/**
 * Shared Aho-Corasick rule matcher for OCR-driven ad-compliance rule
 * domains (广告招牌 [AdSignageRuleMatcher] + 食品标识
 * [FoodLabelRuleMatcher]). Centralizes the 3-phase keyword scan pipeline
 * that used to be duplicated across both concrete matchers:
 *
 * 1. **Phase 1 — raw AC collect**: run the AC trie over the normalized
 *    text and collect `(ruleId, matchedText)` pairs without dedup. AC
 *    emit order is unpredictable and a single OCR occurrence can fan
 *    out to many overlapping hits (1-char-deletion variants + the
 *    original keyword).
 * 2. **Phase 2 — longest-by-key dedup**: collapse variants back to
 *    their original keyword (via [variantOrigins]) and keep the
 *    LONGEST matchedText per `(ruleId, originalKeyword)` pair. This
 *    handles OCR 1-char degradation uniformly — `100%有效` beats
 *    `100%有`, `保本高收益` beats `保本收益`, etc.
 * 3. **Phase 2.5 — same-ruleId substring dedup**: when two distinct
 *    `(ruleId, originalKeyword)` keys happen to have a substring
 *    relationship (e.g. `增强免疫` (4) + `增强免疫力` (5) in the same
 *    rule's keyword list), keep only the longest match. Cross-ruleId
 *    substring relationships are NOT collapsed — different rules
 *    point at different regulations even when their terms overlap.
 *
 * Subclass composition contract — `scan(text)` lives in the subclass
 * because AdSignage inserts extra AC passes (source-marker
 * suppression + anchor-presence / anchor-absence gates) between the
 * keyword pass and emit, while FoodLabel has no extra passes. The
 * template method [runKeywordScan] performs Phase 1/2/2.5 against the
 * already-normalized text and returns the post-dedup map. Subclasses
 * call it, layer any per-domain passes + emit logic, and return the
 * final `List<RuleHit>`. See [AdSignageRuleMatcher.scan] for the
 * gated reference implementation and [FoodLabelRuleMatcher.scan]
 * for the no-gate passthrough.
 *
 * Bug history:
 *  - 2026-08-29 (FoodLabelRuleMatcher sync): extracted this 3-phase
 *    shape from AdSignage's original implementation so the two domains
 *    tolerate OCR 1-char degradation uniformly. Pre-sync FoodLabel
 *    rules missed variants and under-detected in the audit71 e2e.
 *  - 2026-09-10 (Opt-1 refactor): consolidated the two near-identical
 *    matcher classes into this shared base + thin per-domain
 *    subclasses (~410 LOC of duplication removed; see [Rule] KDoc).
 */
abstract class AhoCorasickMatcher<R : Rule>(
    rules: List<R>,
) : RuleMatcher {

    protected val ruleById: Map<String, R> = rules.associateBy { it.id }

    private val keywordTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val hasKeywordTrie: Boolean

    /**
     * Variant → original-keyword lookup for OCR-degradation tolerance. For
     * any keyword K with |K| ≥ [MIN_KEYWORD_FOR_VARIANTS], every
     * 1-char-deletion variant V of K is registered to the AC trie and also
     * recorded here so Phase 2 dedup can collapse at the
     * (ruleId, originalKeyword) level — otherwise a keyword whose variants
     * are all substrings of itself (e.g. "保本高收益" 5 chars → variants
     * "本高收益" / "保高收益" / "保本收益" / "保本高益" / "保本高收", every
     * one of which is a substring of the original) emits 6 hits per scan
     * instead of 1.
     *
     * Not present in this map for original keywords themselves (use
     * `getOrDefault(matchedText, matchedText)` to recover the canonical
     * key in Phase 2).
     */
    private val variantOrigins: Map<String, String>

    init {
        // Pre-pass: collect every normalized keyword across all rules. We need
        // this set BEFORE registering variants so a 1-char-deletion variant
        // that ALSO happens to be a separately-declared keyword (e.g. a 4-char
        // keyword registered as both its own rule keyword AND as a variant of
        // a 5-char keyword that contains it) is NOT marked as a variant — that
        // string is already a legitimate independent keyword and must dedup
        // under its own (ruleId, keyword) key, not the variant's longer-origin
        // key. Without this pre-pass, Phase 2 dedup collapses both keyword
        // hits into one and the rule's expected hit-count assertion fails.
        val allNormalizedKeywords = HashSet<String>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) allNormalizedKeywords += key
            }
        }
        // Two passes' worth of normalized keyword -> ALL rule ids that
        // declare it. A keyword shared by two rules (e.g. "第一" in both
        // the education and the general absolute-claim rules) must
        // attribute the hit to every applicable rule instead of silently
        // shadowing all but the last one. TreeMap gives deterministic
        // (sorted) iteration order; the library accepts any Map<String, V>.
        val keywordToRuleIds = TreeMap<String, List<String>>()
        val variantOriginBuilder = HashMap<String, String>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) {
                    // Auto-decompose long keywords (>= MIN_KEYWORD_FOR_VARIANTS)
                    // into all 1-char-deletion variants. PP-OCRv6_small tends
                    // to drop/merge 1 Chinese char on dense text — e.g. the
                    // food_function_claim keyword "血压血糖血脂降下去" (9 chars)
                    // shows up in #48 nova 6 OCR as "高压血糖血脂降下去" with
                    // the leading 血 dropped. Without the variant, exact-
                    // substring match fails and the slot goes MISS. With the
                    // variant registered, the same OCR text matches the 8-char
                    // 1-char-deletion variant and the rule fires.
                    //
                    // Threshold L>=5 chosen via 2026-08-29 66-fixture cross-
                    // check: lowering to L=4 would let 3-char keywords like
                    // "抗病毒" generate a "抗病" variant, which would false-
                    // positive on #13/#26 豌豆种子/无筋豆种子 "抗病高产"
                    // plant-disease descriptors. L>=5 keeps the high-density
                    // variant set while leaving short, ambiguous keywords
                    // exact-match only.
                    if (key.length >= MIN_KEYWORD_FOR_VARIANTS) {
                        for (i in key.indices) {
                            val variant = key.substring(0, i) + key.substring(i + 1)
                            if (variant.isNotEmpty() && variant != key &&
                                variant !in allNormalizedKeywords
                            ) {
                                keywordToRuleIds[variant] =
                                    (keywordToRuleIds[variant] ?: emptyList()) + rule.id
                                variantOriginBuilder[variant] = key
                            }
                        }
                    }
                    keywordToRuleIds[key] = (keywordToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
        }
        hasKeywordTrie = keywordToRuleIds.isNotEmpty()
        if (hasKeywordTrie) keywordTrie.build(keywordToRuleIds)
        variantOrigins = variantOriginBuilder
    }

    /**
     * Phase 1 + Phase 2 + Phase 2.5 over the already-normalized text.
     * Subclasses call this inside their `scan(text)` and feed the
     * returned map into their per-domain emit + gate logic. The base
     * class does NOT also call this from its own scan() — AdSignage
     * needs to interleave additional AC passes (sourceMarker /
     * categoryAnchors / categoryAnchorsAbsent) BEFORE Phase 2/2.5,
     * while FoodLabel has no extra passes. Letting the subclass own
     * scan() preserves the byte-equivalent ordering of the
     * sourceMarker pass vs the keyword pass (the old code interleaved
     * them in one big scan() block; this keeps that interleaving
     * identical).
     *
     * The `normalized` input must already be `TextNormalizer.forMatching(text)`
     * of the caller's raw text — callers handle the empty / non-Chinese
     * short-circuit themselves so they can compose their extra passes
     * against the same normalized string without re-normalizing.
     *
     * `parseText` on a HankCS trie that was never `build()`-ed throws
     * `Cannot load from int array because "this.base" is null` (the
     * internal base array is uninitialized). The shell profile ships an
     * empty rules JSON (`{"version":1,"rules":[]}` — see
     * prepare-ocr-rules.gradle.kts), which leaves the trie unbuilt.
     * Calling parseText unconditionally on every scan blew up the
     * analyze flow with an NPE, surfaced to the user as
     * `ErrorCode.UNKNOWN` → "未知错误,请重试". Gate parseText on the
     * built-state flag captured at init.
     */
    protected fun runKeywordScan(
        normalized: String,
    ): LinkedHashMap<Pair<String, String>, String> {
        // Phase 1: collect raw (ruleId, matchedText) pairs from the AC pass.
        // We don't dedup inside the handler — AC emit order is unpredictable
        // and the 1-char-deletion variants of a |K|≥5 keyword can produce many
        // overlapping hits in one OCR occurrence. Post-processing picks the
        // best per (ruleId, originalKeyword).
        val rawPairs = mutableListOf<Pair<String, String>>()
        val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            val matched = normalized.substring(begin, end)
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                rawPairs.add(rule.id to matched)
            }
        }
        if (hasKeywordTrie) {
            keywordTrie.parseText(normalized, keywordHandler)
        }

        // Phase 2: dedup at (ruleId, originalKeyword) granularity, keeping
        // the LONGEST matchedText per key. Variants of a |K|≥5 keyword all
        // map back to K via [variantOrigins]; original keywords themselves
        // are not in the map (getOrDefault returns input). Picking longest
        // prefers the original keyword (5+ chars) over its 1-char-deletion
        // variants (length-1) when both are substrings of the same OCR
        // occurrence — yielding `matchedText == "国家发改委"` not `"国家发改"`
        // / `"家发改委"` and `matchedText == "100%有效"` not `"100%有"`.
        val longestByKey = LinkedHashMap<Pair<String, String>, String>()
        for ((ruleId, matched) in rawPairs) {
            val originalKeyword = variantOrigins[matched] ?: matched
            val key = ruleId to originalKeyword
            val existing = longestByKey[key]
            if (existing == null || matched.length > existing.length) {
                longestByKey[key] = matched
            }
        }

        // Phase 2.5: same-ruleId substring dedup, keeping the LONGEST
        // matchedText. Phase 2 collapses 1-char-deletion VARIANTS of a
        // single original keyword back to that original, but does NOT
        // touch cases where two distinct (ruleId, originalKeyword) keys
        // happen to have a substring relationship. Three real-world
        // overlap modes are caught here:
        //   1. Keyword substring overlap — both keywords independently
        //      registered in the same rule's `keywords` list, AC emits
        //      both from one OCR occurrence. Example: rule has
        //      "增强免疫" (4) + "增强免疫力" (5); OCR "增强免疫力" matches
        //      both.
        //   2. Variant-induced false positives — `呵护心血管` (5) auto-
        //      decomposes into the variant "护心血管", which substring-
        //      matches a SEPARATE rule keyword "保护心血管" in the OCR
        //      text. Phase 2 collapses the variant back to "呵护心血管"
        //      but the two end up as distinct (ruleId, originalKeyword)
        //      keys and both survive.
        //   3. Adjacent claim phrasing — `控糖` (2) + `稳血糖` (3) both
        //      fire inside the phrase `控糖稳血糖` (5). Keeping the
        //      longest `控糖稳血糖` still surfaces both banned claims.
        // Cross-ruleId substring relationships are NOT collapsed — different
        // rules point at different regulations even when their terms overlap.
        //
        // The loop walks entries in insertion order but checks BOTH
        // directions: case A (current `matched` is shorter than a kept
        // entry and is its substring → drop `matched`), case B (current
        // `matched` is longer than a kept entry and contains it → drop
        // the kept entry, keep `matched`). LinkedHashMap iteration order
        // is the raw-pair insertion order, which is NOT length-sorted —
        // so a shorter entry can land before its longer counterpart and
        // we need case B to fix it up when the longer arrives.
        val substringDeduped = LinkedHashMap<Pair<String, String>, String>()
        for ((key, matched) in longestByKey) {
            val (ruleId, _) = key
            val isSubstringOfKept = substringDeduped.entries.any { (keptKey, keptMatched) ->
                keptKey.first == ruleId &&
                    keptMatched.length > matched.length &&
                    matched in keptMatched
            }
            if (isSubstringOfKept) continue
            val containedKeptKeys = substringDeduped.entries
                .filter { (keptKey, keptMatched) ->
                    keptKey.first == ruleId &&
                        matched.length > keptMatched.length &&
                        keptMatched in matched
                }
                .map { it.key }
            containedKeptKeys.forEach { substringDeduped.remove(it) }
            substringDeduped[key] = matched
        }
        return substringDeduped
    }

    companion object {
        /**
         * Minimum keyword length to auto-generate 1-char-deletion variants
         * for OCR-degradation tolerance. PP-OCRv6_small tends to drop or
         * merge 1 Chinese character on dense text — the 1-char-deletion
         * variant of a long keyword matches the OCR-degraded form exactly.
         *
         * Threshold L>=5 chosen via 2026-08-29 66-fixture cross-check:
         *   - L>=3 would let "抗病毒" (3 chars) generate a "抗病" variant,
         *     which false-positives on #13/#26 豌豆/无筋豆种子 "抗病高产"
         *     plant-disease descriptors.
         *   - L>=4 is borderline safe; L=4 keywords like "彻底治愈" produce
         *     3-char variants that are too specific to random text to FP,
         *     but L>=5 leaves more safety margin for future rule additions.
         *
         * Source markers are NOT decomposed — they are official citation
         * terms (e.g. "据某" / "数据来源") that should match exactly to
         * indicate the data citation is present.
         */
        const val MIN_KEYWORD_FOR_VARIANTS = 5
    }
}