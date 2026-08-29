package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextNormalizer
import java.util.TreeMap

/**
 * Parallel to [AdSignageRuleMatcher] but operating on the 食品标识 domain.
 * Same HankCS Aho-Corasick build + scan pattern; only the rule type and the
 * `domain` tag stamped on each emitted [RuleHit] differ. Two concrete
 * classes kept side-by-side per project decision (each domain can evolve
 * independently; no premature abstraction across them).
 *
 * Auto-decomposition (length≥[MIN_KEYWORD_FOR_VARIANTS] keywords registered
 * with all 1-char-deletion variants) and 3-phase scan (raw → dedup-by-
 * original + longest-match → emit) are mirrored from
 * [AdSignageRuleMatcher] so PP-OCRv6_small's tendency to drop/merge 1
 * Chinese char on dense text is tolerated uniformly across both domains.
 * See [AdSignageRuleMatcher] KDoc for the full rationale and the L≥5
 * threshold cross-check.
 *
 * Absence-rule support ([AdSignageRuleMatcher.sourceMarkers]) is **not**
 * mirrored here: the [FoodLabelRule] data class does not declare a
 * `sourceMarkers` field and the bundled `food_label_rules.json` carries no
 * such entries (grep 0 命中 at 2026-08-29). If a future rule needs absence
 * semantics, add `val sourceMarkers: List<String> = emptyList()` to
 * [FoodLabelRule] and replicate the Phase 3 branch from AdSignageRuleMatcher.
 */
class FoodLabelRuleMatcher(rules: List<FoodLabelRule>) : RuleMatcher {

    private val ruleById: Map<String, FoodLabelRule> = rules.associateBy { it.id }
    private val keywordTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val hasKeywordTrie: Boolean
    /**
     * Variant → original-keyword lookup for OCR-degradation tolerance.
     * Mirror of [AdSignageRuleMatcher.variantOrigins]; see that field's
     * KDoc for the dedup rationale.
     */
    private val variantOrigins: Map<String, String>

    init {
        // Pre-pass: collect every normalized keyword across all rules. We need
        // this set BEFORE registering variants so a 1-char-deletion variant
        // that ALSO happens to be a separately-declared keyword (e.g. "反式脂
        // 肪" is both its own 4-char rule keyword AND a 1-char-deletion variant
        // of "反式脂肪酸" 5-char rule keyword) is NOT marked as a variant — that
        // string is already a legitimate independent keyword and must dedup
        // under its own (ruleId, "反式脂肪") key, not the variant's
        // (ruleId, "反式脂肪酸") key. Without this pre-pass, Phase 2 dedup
        // collapses both keyword hits into one and the rule's expected
        // hit-count assertion fails.
        val allNormalizedKeywords = HashSet<String>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) allNormalizedKeywords += key
            }
        }
        val keywordToRuleIds = TreeMap<String, List<String>>()
        val variantOriginBuilder = HashMap<String, String>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) {
                    // Auto-decompose long keywords (>= MIN_KEYWORD_FOR_VARIANTS)
                    // into all 1-char-deletion variants — mirrors the AdSignage
                    // matcher behavior so both domains tolerate OCR 1-char
                    // degradation uniformly. See AdSignageRuleMatcher for the
                    // L≥5 threshold cross-check (3-char "抗病毒" → "抗病"
                    // would FP on plant-disease descriptors; L>=5 keeps the
                    // high-density variant set while leaving short, ambiguous
                    // keywords exact-match only).
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

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        // Phase 1: collect raw (ruleId, matchedText) pairs from the AC pass.
        // No dedup inside the handler — see AdSignageRuleMatcher.scan for the
        // rationale (variant over-match + AC emit-order unpredictability).
        val rawPairs = mutableListOf<Pair<String, String>>()

        val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            val matched = normalized.substring(begin, end)
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                rawPairs.add(rule.id to matched)
            }
        }

        // Same parseText guard as AdSignageRuleMatcher — calling parseText on
        // an unbuilt HankCS trie throws Cannot load from int array because
        // "this.base" is null. Shell profile ships an empty rules JSON that
        // leaves this trie unbuilt.
        if (hasKeywordTrie) {
            keywordTrie.parseText(normalized, keywordHandler)
        }

        // Phase 2: dedup at (ruleId, originalKeyword) granularity, keeping
        // the LONGEST matchedText per key. Mirrors AdSignageRuleMatcher
        // semantics so 100%有效 beats 100%有, "血压血糖血脂降下去" beats
        // "压血糖血脂降下去", etc.
        val longestByKey = LinkedHashMap<Pair<String, String>, String>()
        for ((ruleId, matched) in rawPairs) {
            val originalKeyword = variantOrigins[matched] ?: matched
            val key = ruleId to originalKeyword
            val existing = longestByKey[key]
            if (existing == null || matched.length > existing.length) {
                longestByKey[key] = matched
            }
        }

        // Phase 3: emit (no absence-rule dedup — FoodLabelRule has no
        // sourceMarkers field, see class KDoc).
        val hits = mutableListOf<RuleHit>()
        for ((key, matched) in longestByKey) {
            val (ruleId, _) = key
            val rule = ruleById[ruleId] ?: continue
            hits.add(
                RuleHit(
                    ruleId = rule.id,
                    matchedText = matched,
                    category = rule.category,
                    regulation = rule.regulation,
                    lawText = rule.lawText,
                    severity = rule.severity,
                    domain = CategoryDisplay.DOMAIN_FOOD,
                )
            )
        }
        return hits
    }

    companion object {
        /**
         * Minimum keyword length to auto-generate 1-char-deletion variants
         * for OCR-degradation tolerance. Mirrors the AdSignage matcher
         * constant; see [AdSignageRuleMatcher]'s KDoc for the L≥5 cross-
         * check rationale (L=3 would let "抗病毒" generate "抗病" and FP on
         * plant-disease descriptors).
         */
        private const val MIN_KEYWORD_FOR_VARIANTS = 5
    }
}
