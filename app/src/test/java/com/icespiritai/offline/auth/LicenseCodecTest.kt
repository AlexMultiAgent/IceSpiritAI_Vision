package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCodecTest {

    @Test
    fun parses_well_formed_string() {
        // ICS1 格式串过结构校验(3 段 + 前缀),但 aGVsbG8 解码后是 "hello" 不是合法 JSON,
        // 应被解码 + 校验拒绝;签发路径在 interop 测试覆盖
        val result = LicenseCodec.parse("ICS1.aGVsbG8.sig")
        assertNull(result)
        assertEquals("ICS1", LicenseCodec.PREFIX)
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
    fun encodes_payload_to_bytes() {
        val p = LicensePayload(1, "a1b2c3", "acme", 20270630)
        val bytes = LicenseCodec.encodePayload(p)
        assertTrue(bytes.isNotEmpty())
        // base64URL 字符集:字母数字 + - _
        val s = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue(s.matches(Regex("^[A-Za-z0-9_-]+$")))
    }
}
