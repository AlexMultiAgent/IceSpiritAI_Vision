package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCodecTest {

    @Test
    fun parses_well_formed_string() {
        // 真实合法 ICS1:{"v":1,"u":"a1b2c3","n":"acme","e":20270630,"d":"advertising"} base64url + dummy signature
        val code = "ICS1.eyJ2IjoxLCJ1IjoiYTFiMmMzIiwibiI6ImFjbWUiLCJlIjoyMDI3MDYzMCwiZCI6ImFkdmVydGlzaW5nIn0.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val result = LicenseCodec.parse(code)
        assertNotNull(result)
        assertEquals("ICS1", LicenseCodec.PREFIX)
        assertEquals("a1b2c3", result!!.payload.u)
        assertEquals("acme", result.payload.n)
        assertEquals(20270630L, result.payload.e)
        assertEquals("advertising", result.payload.d)
    }

    @Test
    fun rejects_missing_prefix() {
        val r = LicenseCodec.parse("XICS1.aGVsbG8.c2ln")
        assertNull(r)
    }

    @Test
    fun rejects_wrong_segment_count() {
        assertNull(LicenseCodec.parse("ICS1.aGVsbG8"))
        assertNull(LicenseCodec.parse("ICS1.aGVsbG8.c2ln.extra"))
    }

    @Test
    fun rejects_invalid_base64url() {
        assertNull(LicenseCodec.parse("ICS1.!!!.c2ln"))
    }

    @Test
    fun rejects_payload_with_invalid_field_values() {
        // u 长度 ≠ 6 → 拒绝
        val code = "ICS1.eyJ2IjoxLCJ1IjoiYWJjIiwibiI6ImFjbWUiLCJlIjoyMDI3MDYzMCwiZCI6ImFkdmVydGlzaW5nIn0.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        assertNull(LicenseCodec.parse(code))
    }

    @Test
    fun encodes_payload_to_bytes() {
        val p = LicensePayload(1, "a1b2c3", "acme", 20270630)
        val bytes = LicenseCodec.encodePayload(p)
        assertTrue(bytes.isNotEmpty())
        // base64URL 字符集:字母数字 + - _
        val s = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue(s.matches(Regex("^[A-Za-z0-9_-]+$")))
    }
}
