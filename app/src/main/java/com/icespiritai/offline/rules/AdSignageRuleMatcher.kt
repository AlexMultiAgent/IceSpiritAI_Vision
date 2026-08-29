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
    private val hasKeywordTrie: Boolean
    private val hasSourceMarkerTrie: Boolean
    private val hasAnyAbsenceRule: Boolean
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
        // Two passes' worth of normalized keyword -> ALL rule ids that declare it.
        // A keyword shared by two rules (e.g. "第一" in both the education and the
        // general absolute-claim rules) must attribute the hit to every applicable
        // rule instead of silently shadowing all but the last one. TreeMap gives
        // deterministic (sorted) iteration order; the library accepts any
        // Map<String, V>.
        val keywordToRuleIds = TreeMap<String, List<String>>()
        val sourceMarkerToRuleIds = TreeMap<String, List<String>>()
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
                            if (variant.isNotEmpty() && variant != key) {
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
        }
        hasKeywordTrie = keywordToRuleIds.isNotEmpty()
        hasSourceMarkerTrie = sourceMarkerToRuleIds.isNotEmpty()
        if (hasKeywordTrie) keywordTrie.build(keywordToRuleIds)
        if (hasSourceMarkerTrie) sourceMarkerTrie.build(sourceMarkerToRuleIds)
        // Absence composite logic only runs when at least one rule declared
        // sourceMarkers. 117 existing rules all default to sourceMarkers =
        // emptyList(), so the legacy single-pass code path stays byte-equivalent.
        hasAnyAbsenceRule = rules.any { it.sourceMarkers.isNotEmpty() }
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

        // `parseText` on a HankCS trie that was never `build()`-ed throws
        // `Cannot load from int array because "this.base" is null` (the
        // internal base array is uninitialized). The shell profile ships an
        // empty rules JSON (`{"version":1,"rules":[]}` — see
        // prepare-ocr-rules.gradle.kts), which leaves both tries unbuilt.
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

        // Phase 3: absence rule dedup — when a rule has sourceMarkers, at most
        // one hit per ruleId (the rule as a whole either fires or doesn't; the
        // matchedText is whichever claim keyword was longest). Non-absence
        // rules are kept one-per-(ruleId, originalKeyword) from Phase 2.
        // Insertion order preserved so the deterministic HashMap iteration of
        // [ruleById] does not shuffle the user-visible hit list.
        val seenAbsenceRules = mutableSetOf<String>()
        val hits = mutableListOf<RuleHit>()
        for ((key, matched) in longestByKey) {
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
        return hits.filter { hit ->
            val rule = ruleById[hit.ruleId] ?: return@filter true
            if (rule.sourceMarkers.isEmpty()) true
            else rule.id !in sourceMarkerHitRules
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
