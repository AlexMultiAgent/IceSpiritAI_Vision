package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.RuleHit

interface RuleMatcher {
    fun scan(text: String): List<RuleHit>
}
