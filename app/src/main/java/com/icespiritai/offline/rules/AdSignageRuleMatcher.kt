package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextNormalizer
import java.util.TreeMap

class AdSignageRuleMatcher(rules: List<AdSignageRule>) : RuleMatcher {

    private val ruleById: Map<String, AdSignageRule> = rules.associateBy { it.id }
    private val keywordTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val sourceMarkerTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val anchorTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val hasKeywordTrie: Boolean
    private val hasSourceMarkerTrie: Boolean
    private val hasAnchorTrie: Boolean
    private val hasAnyAbsenceRule: Boolean
    private val rulesRequiringAnchor: Set<String>
    /**
     * Variant → original-keyword lookup for OCR-degradation tolerance. For any
     * keyword K with |K| ≥ [MIN_KEYWORD_FOR_VARIANTS], every 1-char-deletion
     * variant V of K is registered to the AC trie and also recorded here so
     * [scan] can dedup at the (ruleId, originalKeyword) level — otherwise a
     * keyword whose variants are all substrings of itself (e.g. "保本高收益"
     * 5 chars → variants "本高收益" / "保高收益" / "保本收益" / "保本高益" /
     * "保本高收", every one of which is a substring of the original) emits
     * 6 hits per scan instead of 1.
     *
     * Not present in this map for original keywords themselves (use
     * `getOrDefault(matchedText, matchedText)` to recover the canonical key).
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
        // Bug surfaced 2026-08-29 while syncing AC behavior into
        // FoodLabelRuleMatcher — its test suite caught it via
        // scan_gb28050Art4TransFat which has both 4-char "反式脂肪" and 5-char
        // "反式脂肪酸" as separate keywords.
        val allNormalizedKeywords = HashSet<String>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) allNormalizedKeywords += key
            }
        }
        // Two passes' worth of normalized keyword -> ALL rule ids that declare it.
        // A keyword shared by two rules (e.g. "第一" in both the education and the
        // general absolute-claim rules) must attribute the hit to every applicable
        // rule instead of silently shadowing all but the last one. TreeMap gives
        // deterministic (sorted) iteration order; the library accepts any
        // Map<String, V>.
        val keywordToRuleIds = TreeMap<String, List<String>>()
        val sourceMarkerToRuleIds = TreeMap<String, List<String>>()
        val anchorToRuleIds = TreeMap<String, List<String>>()
        val variantOriginBuilder = HashMap<String, String>()
        val rulesRequiringAnchorBuilder = mutableSetOf<String>()
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
                    // Threshold L>=5 is chosen via 2026-08-29 66-fixture cross-
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
                                // Record which original keyword this variant came
                                // from so [scan] can dedup (ruleId, originalKeyword)
                                // instead of (ruleId, matchedText). Without this,
                                // a single OCR occurrence of "保本高收益" emits 6
                                // hits (1 original + 5 variants) for the same rule.
                                variantOriginBuilder[variant] = key
                            }
                        }
                    }
                    keywordToRuleIds[key] = (keywordToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
            for (sm in rule.sourceMarkers) {
                val key = TextNormalizer.forMatching(sm)
                if (key.isNotEmpty()) {
                    sourceMarkerToRuleIds[key] = (sourceMarkerToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
            // Category anchors: exact substring match (no 1-char variant
            // decomposition). Anchors are short official terms ("农药" /
            // "兽药" / "医院" / "化妆品" / "儿童") — variant generation would
            // expand them into adjacent substrings that lose semantic
            // specificity (e.g. "农药" → "农" / "药", neither of which is
            // domain-specific). Inverse polarity to sourceMarkers.
            for (anchor in rule.categoryAnchors) {
                val key = TextNormalizer.forMatching(anchor)
                if (key.isNotEmpty()) {
                    anchorToRuleIds[key] = (anchorToRuleIds[key] ?: emptyList()) + rule.id
                    rulesRequiringAnchorBuilder += rule.id
                }
            }
        }
        hasKeywordTrie = keywordToRuleIds.isNotEmpty()
        hasSourceMarkerTrie = sourceMarkerToRuleIds.isNotEmpty()
        hasAnchorTrie = anchorToRuleIds.isNotEmpty()
        if (hasKeywordTrie) keywordTrie.build(keywordToRuleIds)
        if (hasSourceMarkerTrie) sourceMarkerTrie.build(sourceMarkerToRuleIds)
        if (hasAnchorTrie) anchorTrie.build(anchorToRuleIds)
        // Absence composite logic only runs when at least one rule declared
        // sourceMarkers. 117 existing rules all default to sourceMarkers =
        // emptyList(), so the legacy single-pass code path stays byte-equivalent.
        hasAnyAbsenceRule = rules.any { it.sourceMarkers.isNotEmpty() }
        rulesRequiringAnchor = rulesRequiringAnchorBuilder
        variantOrigins = variantOriginBuilder
    }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        // Phase 1: collect raw (ruleId, matchedText) pairs from the AC pass.
        // We don't dedup inside the handler — AC emit order is unpredictable
        // and the 1-char-deletion variants of a |K|≥5 keyword can produce many
        // overlapping hits in one OCR occurrence. Post-processing picks the
        // best per (ruleId, originalKeyword).
        val rawPairs = mutableListOf<Pair<String, String>>()
        val sourceMarkerHitRules = mutableSetOf<String>()
        val anchorHitRules = mutableSetOf<String>()

        val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            val matched = normalized.substring(begin, end)
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                rawPairs.add(rule.id to matched)
            }
        }

        // Source marker pass: only collect which rule ids had any source marker
        // hit. We don't materialize RuleHit for source matches — claim hits are
        // the user-visible output; source markers are just a gate.
        val sourceMarkerHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) sourceMarkerHitRules += ruleId
        }

        // Category-anchor pass: collect rule ids whose text contained at least
        // one anchor substring. Used by the gate at Phase 3 to drop hits for
        // anchor-gated rules whose text did NOT contain any anchor. Inverse
        // polarity to sourceMarkers (which suppress on presence; anchors
        // require presence).
        val anchorHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) anchorHitRules += ruleId
        }

        // `parseText` on a HankCS trie that was never `build()`-ed throws
        // `Cannot load from int array because "this.base" is null` (the
        // internal base array is uninitialized). The shell profile ships an
        // empty rules JSON (`{"version":1,"rules":[]}` — see
        // prepare-ocr-rules.gradle.kts), which leaves all three tries unbuilt.
        // Calling parseText unconditionally on every scan blew up the
        // analyze flow with an NPE, surfaced to the user as
        // `ErrorCode.UNKNOWN` → "未知错误,请重试". Gate parseText on the
        // built-state flags captured at init.
        if (hasKeywordTrie) {
            keywordTrie.parseText(normalized, keywordHandler)
        }
        if (hasAnyAbsenceRule && hasSourceMarkerTrie) {
            sourceMarkerTrie.parseText(normalized, sourceMarkerHandler)
        }
        if (hasAnchorTrie) {
            anchorTrie.parseText(normalized, anchorHandler)
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
        // matchedText. Phase 2 collapses 1-char-deletion VARIANTS of a single
        // original keyword back to that original, but does NOT touch cases
        // where two distinct (ruleId, originalKeyword) keys happen to have a
        // substring relationship. Three real-world overlap modes are caught
        // here:
        //   1. Keyword substring overlap — both keywords independently
        //      registered in the same rule's `keywords` list, AC emits both
        //      from one OCR occurrence. Example: rule has "增强免疫" (4) +
        //      "增强免疫力" (5); OCR "增强免疫力" matches both.
        //   2. Variant-induced false positives — `呵护心血管` (5) auto-
        //      decomposes into the variant "护心血管" (line ~99), which
        //      substring-matches a SEPARATE rule keyword "保护心血管" in the
        //      OCR text. Phase 2 collapses the variant back to "呵护心血管"
        //      but the two end up as distinct (ruleId, originalKeyword) keys
        //      and both survive.
        //   3. Adjacent claim phrasing — `控糖` (2) + `稳血糖` (3) both fire
        //      inside the phrase `控糖稳血糖` (5). Keeping the longest
        //      `控糖稳血糖` still surfaces both banned claims (the matched
        //      text makes it clear that 控糖 and 稳血糖 are both implicated);
        //      dropping the shorter forms removes the visual redundancy
        //      without losing semantic information.
        // Cross-ruleId substring relationships are NOT collapsed — different
        // rules point at different regulations even when their terms overlap
        // (e.g. "心血管" in a blood-pressure rule + "保护心血管" in a
        // food-function rule).
        //
        // The loop must walk entries in insertion order but check BOTH
        // directions: case A (current `matched` is shorter than a kept entry
        // and is its substring → drop `matched`), case B (current `matched`
        // is longer than a kept entry and contains it → drop the kept entry,
        // keep `matched`). LinkedHashMap iteration order is the raw-pair
        // insertion order, which is NOT length-sorted — so a shorter entry
        // can land before its longer counterpart and we need case B to fix
        // it up when the longer arrives.
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

        // Phase 3: absence rule dedup — when a rule has sourceMarkers, at most
        // one hit per ruleId (the rule as a whole either fires or doesn't; the
        // matchedText is whichever claim keyword was longest). Non-absence
        // rules are kept one-per-(ruleId, originalKeyword) from Phase 2.
        // Insertion order preserved so the deterministic HashMap iteration of
        // [ruleById] does not shuffle the user-visible hit list.
        val seenAbsenceRules = mutableSetOf<String>()
        val hits = mutableListOf<RuleHit>()
        for ((key, matched) in substringDeduped) {
            val (ruleId, _) = key
            val rule = ruleById[ruleId] ?: continue
            if (rule.sourceMarkers.isNotEmpty()) {
                if (!seenAbsenceRules.add(rule.id)) continue
            }
            hits.add(
                RuleHit(
                    ruleId = rule.id,
                    matchedText = matched,
                    category = rule.category,
                    regulation = rule.regulation,
                    lawText = rule.lawText,
                    severity = rule.severity,
                    domain = CategoryDisplay.DOMAIN_AD,
                )
            )
        }

        // For absence rules, suppress claim hits whenever the rule's source marker
        // was matched anywhere in the text (one source marker present = claim
        // considered cited, regardless of which claim keyword matched). Non-
        // absence rules (sourceMarkers empty) pass through unchanged.
        //
        // For category-anchor-gated rules (categoryAnchors non-empty), keep
        // the hit only when at least one anchor substring was found anywhere
        // in the scanned text. Inverse polarity to source-marker suppression:
        // source marker presence suppresses; anchor presence is required.
        return hits.filter { hit ->
            val rule = ruleById[hit.ruleId] ?: return@filter true
            when {
                rule.sourceMarkers.isNotEmpty() -> rule.id !in sourceMarkerHitRules
                rule.categoryAnchors.isNotEmpty() -> rule.id in anchorHitRules
                else -> true
            }
        }
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
         *     plant-disease descriptors (GT: art27_seed_yield_guarantee,
         *     NOT disease_prevention).
         *   - L>=4 is borderline safe; L=4 keywords like "彻底治愈" produce
         *     3-char variants that are too specific to random text to FP,
         *     but L>=5 leaves more safety margin for future rule additions.
         *
         * Source markers are NOT decomposed — they are official citation
         * terms (e.g. "据某" / "数据来源") that should match exactly to
         * indicate the data citation is present.
         */
        private const val MIN_KEYWORD_FOR_VARIANTS = 5
    }
}
