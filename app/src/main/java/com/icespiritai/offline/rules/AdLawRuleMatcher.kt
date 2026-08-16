package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextNormalizer
import java.util.TreeMap

class AdLawRuleMatcher(rules: List<AdLawRule>) : RuleMatcher {

    private val trie = AhoCorasickDoubleArrayTrie<List<String>>()

    init {
        // Each normalized keyword -> ALL rule ids that declare it. A keyword
        // shared by two rules (e.g. "第一" in both the education and the
        // general absolute-claim rules) must attribute the hit to every
        // applicable rule instead of silently shadowing all but the last one.
        // TreeMap gives deterministic (sorted) iteration order; the library
        // accepts any Map<String, V>.
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

    private val ruleById: Map<String, AdLawRule> = rules.associateBy { it.id }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        val hits = mutableListOf<RuleHit>()
        val hit = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            val matched = normalized.substring(begin, end)
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                // Deduplicate: same ruleId + matchedText counts once
                if (hits.none { it.ruleId == rule.id && it.matchedText == matched }) {
                    hits.add(
                        RuleHit(
                            ruleId = rule.id,
                            matchedText = matched,
                            category = rule.category,
                            regulation = rule.regulation,
                            severity = rule.severity
                        )
                    )
                }
            }
        }
        trie.parseText(normalized, hit)
        return hits
    }
}
