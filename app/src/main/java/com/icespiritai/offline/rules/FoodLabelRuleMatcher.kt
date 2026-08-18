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
 */
class FoodLabelRuleMatcher(rules: List<FoodLabelRule>) : RuleMatcher {

    private val trie = AhoCorasickDoubleArrayTrie<List<String>>()

    init {
        val keywordToRuleIds = TreeMap<String, List<String>>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) {
                    keywordToRuleIds[key] = (keywordToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
        }
        trie.build(keywordToRuleIds)
    }

    private val ruleById: Map<String, FoodLabelRule> = rules.associateBy { it.id }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        val hits = mutableListOf<RuleHit>()
        val hit = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            val matched = normalized.substring(begin, end)
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                if (hits.none { it.ruleId == rule.id && it.matchedText == matched }) {
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
            }
        }
        trie.parseText(normalized, hit)
        return hits
    }
}
