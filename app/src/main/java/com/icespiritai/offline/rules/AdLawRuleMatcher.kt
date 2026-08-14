package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.RuleHit
import java.util.TreeMap

class AdLawRuleMatcher(rules: List<AdLawRule>) : RuleMatcher {

    private val trie = AhoCorasickDoubleArrayTrie<String>()

    init {
        // Each unique keyword -> ruleId. Duplicate keywords across rules: last rule wins.
        // TreeMap gives deterministic (sorted) iteration order; the library accepts any Map<String, V>.
        val keywordToRuleId = TreeMap<String, String>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                keywordToRuleId[kw] = rule.id
            }
        }
        trie.build(keywordToRuleId)
    }

    private val ruleById: Map<String, AdLawRule> = rules.associateBy { it.id }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val hits = mutableListOf<RuleHit>()
        val hit = AhoCorasickDoubleArrayTrie.IHit<String> { begin, end, ruleId ->
            val matched = text.substring(begin, end)
            val rule = ruleById[ruleId] ?: return@IHit
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
        trie.parseText(text, hit)
        return hits
    }
}
