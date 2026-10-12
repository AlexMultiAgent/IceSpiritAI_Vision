package com.icespiritai.offline.auth

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LicensePayloadTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
        assertEquals("""{"v":1,"u":"x9y8z7","n":"demo","e":0,"d":"advertising"}""", encoded)
    }

    @Test
    fun roundtrips_with_department_field() {
        val original = LicensePayload(v = 1, u = "a1b2c3", n = "acme", e = 20270630, d = "food")
        val encoded = json.encodeToString(LicensePayload.serializer(), original)
        val decoded = json.decodeFromString(LicensePayload.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("food", decoded.d)
    }

    @Test
    fun defaults_department_to_advertising_when_missing_in_input() {
        // 旧签发器(没有 d 字段)生成的码 — Kotlin 端必须有合理 fallback。
        // 注:LicenseCodec 的 Json 配置 ignoreUnknownKeys=true,所以这里解析没有问题;
        // 真实生产路径走的是 LicenseCodec.parse,会触发 d.length 校验 — 但若 d 字段不存在,
        // 严格模式 (ignoreUnknownKeys=false) 直接拒掉,所以这个回退路径只是给 unit test 用。
        val legacy = """{"v":1,"u":"a1b2c3","n":"acme","e":20270630}"""
        val decoded = json.decodeFromString(LicensePayload.serializer(), legacy)
        assertEquals("advertising", decoded.d)
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
    fun payload_does_not_validate_v_field() {
        val p = LicensePayload(v = 99, u = "x", n = "y", e = 20270630)
        // Payload 自身只承数据形状,不做范围校验;Codec 阶段才校验 v
        assertEquals(99, p.v)
    }
}