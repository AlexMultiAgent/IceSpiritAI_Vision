package com.icespiritai.offline.updater

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
            apkUrl = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision-update.apk",
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
}