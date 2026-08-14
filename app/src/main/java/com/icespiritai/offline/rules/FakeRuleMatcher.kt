package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.RuleHit

class FakeRuleMatcher(
    private val hitsByQuery: Map<String, List<RuleHit>> = emptyMap()
) : RuleMatcher {
    override fun scan(text: String): List<RuleHit> = hitsByQuery[text] ?: emptyList()
}
