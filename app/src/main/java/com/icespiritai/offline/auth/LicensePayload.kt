package com.icespiritai.offline.auth

import kotlinx.serialization.Serializable

@Serializable
data class LicensePayload(
    val v: Int,
    val u: String,
    val n: String,
    val e: Long
) {
    fun isPermanent(): Boolean = e == 0L

    fun isExpired(today: Long): Boolean {
        if (isPermanent()) return false
        return today > e
    }
}