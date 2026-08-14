package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.Serializable

@Serializable
data class AdLawRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity
)