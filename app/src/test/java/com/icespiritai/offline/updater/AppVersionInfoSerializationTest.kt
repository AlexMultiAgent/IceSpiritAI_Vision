package com.icespiritai.offline.updater

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class AppVersionInfoSerializationTest {

    private val parser = Json { ignoreUnknownKeys = true }

    @Test
    fun roundTrip_preservesAllFields() {
        val info = AppVersionInfo(
            versionCode = 2,
            versionName = "0.2.0",
            apkUrl = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision.apk",
            apkSize = 18392192L,
            apkSha256 = "a".repeat(64),
            changelog = "## v0.2.0\n- 修复X\n- 新增Y",
            apkCumulativeDownloads = 42L,
        )
        val text = parser.encodeToString(AppVersionInfo.serializer(), info)
        val decoded = parser.decodeFromString(AppVersionInfo.serializer(), text)
        assertEquals(info, decoded)
    }

    @Test
    fun ignoreUnknownKeys_doesNotThrowOnStrayField() {
        val text = """
            {
              "versionCode": 2,
              "versionName": "0.2.0",
              "apkUrl": "http://x/y.apk",
              "apkSize": 1,
              "apkSha256": "${"b".repeat(64)}",
              "futureFieldWeDoNotKnowYet": {"nested": [1, 2, 3]}
            }
        """.trimIndent()
        val info = parser.decodeFromString(AppVersionInfo.serializer(), text)
        assertEquals(2, info.versionCode)
        assertEquals("", info.changelog) // default
        assertEquals(0L, info.apkCumulativeDownloads) // default
    }

    @Test
    fun requiredFieldsMissing_throwsMissingFieldException() {
        val text = """{"versionCode": 1}"""
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            parser.decodeFromString(AppVersionInfo.serializer(), text)
        }
    }

    @Test
    fun signerCertSha256_defaultEmptySerializesAsAbsent_backwardCompat() {
        val info = AppVersionInfo(
            versionCode = 1,
            versionName = "0.0.0",
            apkUrl = "x",
            apkSize = 0L,
            apkSha256 = "y",
        )
        val text = parser.encodeToString(AppVersionInfo.serializer(), info)
        // encodeDefaults = false 默认行为:默认值字段不写入 wire,保证旧 vision-latest.json 兼容
        assertFalse(
            "signerCertSha256 默认值不应序列化到 wire (encodeDefaults = false): $text",
            text.contains("signerCertSha256"),
        )
        // 反向验证:解码后默认值仍然为 ""
        val decoded = parser.decodeFromString(AppVersionInfo.serializer(), text)
        assertEquals("", decoded.signerCertSha256)
    }

    @Test
    fun signerCertSha256_nonEmptyIsSerialized() {
        val info = AppVersionInfo(
            versionCode = 1,
            versionName = "0.0.0",
            apkUrl = "x",
            apkSize = 0L,
            apkSha256 = "y",
            signerCertSha256 = "abc123",
        )
        val text = parser.encodeToString(AppVersionInfo.serializer(), info)
        assert(text.contains("\"signerCertSha256\":\"abc123\"")) {
            "应序列化 signerCertSha256 字段: $text"
        }
    }
}