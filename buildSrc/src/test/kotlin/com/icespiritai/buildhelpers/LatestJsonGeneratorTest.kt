package com.icespiritai.buildhelpers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestJsonGeneratorTest {

    private val parser = Json { ignoreUnknownKeys = true }

    @Test
    fun buildLatestJson_roundTripsThroughAppVersionInfo() {
        val json = LatestJsonGenerator.buildLatestJson(
            versionCode = 7,
            versionName = "0.7.0",
            apkUrl = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision.apk",
            apkSize = 20_000_000L,
            apkSha256 = "d".repeat(64),
            changelog = "## v0.7.0\n- 修复A\n- 新增B",
            apkCumulativeDownloads = 100L,
        )
        val info = parser.decodeFromString(TestAppVersionInfo.serializer(), json)
        assertEquals(7, info.versionCode)
        assertEquals("0.7.0", info.versionName)
        assertTrue(info.apkUrl.endsWith("/icespiritai-vision.apk"))
        assertEquals(20_000_000L, info.apkSize)
        assertEquals("d".repeat(64), info.apkSha256)
        assertEquals(100L, info.apkCumulativeDownloads)
        assertTrue(info.changelog.contains("修复A"))
    }

    @Test
    fun buildLatestJson_cumulativeDownloadsDefaultsToZero() {
        val json = LatestJsonGenerator.buildLatestJson(
            versionCode = 1, versionName = "0.1.0",
            apkUrl = "http://x/y.apk", apkSize = 1L,
            apkSha256 = "e".repeat(64), changelog = "",
        )
        val info = parser.decodeFromString(TestAppVersionInfo.serializer(), json)
        assertEquals(0L, info.apkCumulativeDownloads)
    }

    @Test
    fun sha256Hex_isStableAndLowerCase64() {
        val tmp = java.io.File.createTempFile("icespirit-hash", ".bin")
        tmp.writeBytes(ByteArray(1024) { it.toByte() })
        try {
            val hex = LatestJsonGenerator.sha256Hex(tmp)
            assertEquals(64, hex.length)
            assertEquals(hex, hex.lowercase())
            // Recompute externally and compare
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(tmp.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(digest, hex)
        } finally {
            tmp.delete()
        }
    }
}

/**
 * Local mirror of `com.icespiritai.offline.updater.AppVersionInfo`. Exists
 * because `buildSrc/` is a SEPARATE module from `app/` and cannot depend
 * on `app/src/main/java/` (buildSrc compiles BEFORE the main project).
 * Keep the 7-field shape in sync with the production type — these two
 * classes are pinned together by the round-trip test above.
 */
@Serializable
private data class TestAppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String,
    val changelog: String = "",
    val apkCumulativeDownloads: Long = 0,
)
