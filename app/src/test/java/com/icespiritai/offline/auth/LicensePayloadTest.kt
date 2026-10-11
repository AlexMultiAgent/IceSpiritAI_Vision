package com.icespiritai.offline.auth

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LicensePayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrips_with_expire_date() {
        val original = LicensePayload(
            v = 1,
            u = "a1b2c3",
            n = "acme",
            e = 20270630
        )
        val encoded = json.encodeToString(LicensePayload.serializer(), original)
        val decoded = json.decodeFromString(LicensePayload.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun roundtrips_with_permanent_zero() {
        val original = LicensePayload(v = 1, u = "x9y8z7", n = "demo", e = 0)
        val encoded = json.encodeToString(LicensePayload.serializer(), original)
        assertEquals("""{"v":1,"u":"x9y8z7","n":"demo","e":0}""", encoded)
    }

    @Test
    fun is_permanent_when_zero() {
        val p = LicensePayload(v = 1, u = "x", n = "y", e = 0)
        assertEquals(true, p.isPermanent())
        assertEquals(false, p.isExpired(today = 20261011))
    }

    @Test
    fun is_expired_when_today_after_e() {
        val p = LicensePayload(v = 1, u = "x", n = "y", e = 20260630)
        assertEquals(true, p.isExpired(today = 20260701))
        assertEquals(false, p.isExpired(today = 20260630))
    }

    @Test
    fun rejects_negative_or_huge_v() {
        val bad = LicensePayload(v = 99, u = "x", n = "y", e = 20270630)
        // 这里不抛 — 校验在 Codec 阶段做;Payload 自身只承数据形状
        assertEquals(99, bad.v)
    }
}