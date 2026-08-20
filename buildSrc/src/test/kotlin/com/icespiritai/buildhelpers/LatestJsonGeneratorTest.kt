package com.icespiritai.buildhelpers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun buildLatestJson_emitsSignerCertSha256WhenProvided() {
        val json = LatestJsonGenerator.buildLatestJson(
            versionCode = 14,
            versionName = "0.1.14",
            apkUrl = "https://gitea.icespiritai.com/giteaadmin/vision-app/releases/download/latest/icespiritai-vision.apk",
            apkSize = 18_000_000L,
            apkSha256 = "a".repeat(64),
            changelog = "## v0.1.14\n- cert-pin 接入",
            apkCumulativeDownloads = 0L,
            signerCertSha256 = "c".repeat(64),
        )
        val info = parser.decodeFromString(TestAppVersionInfo.serializer(), json)
        assertEquals("c".repeat(64), info.signerCertSha256)
        assertTrue("json should contain signerCertSha256 field", json.contains("\"signerCertSha256\":\"${"c".repeat(64)}\""))
    }

    @Test
    fun buildLatestJson_omitsSignerCertSha256WhenEmpty() {
        val json = LatestJsonGenerator.buildLatestJson(
            versionCode = 1, versionName = "0.1.0",
            apkUrl = "http://x/y.apk", apkSize = 1L,
            apkSha256 = "f".repeat(64), changelog = "",
        )
        assertFalse(
            "signerCertSha256 default empty should not appear in wire: $json",
            json.contains("signerCertSha256"),
        )
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

    @Test
    fun extractLatestChangelog_returnsFirstSectionVerbatim() {
        val md = """
            # 用户更新日志

            ## v0.1.1 · 2026-08-18

            - 设置新增 changelog
            - 设置项重排

            ## v0.1.0 · 2026-08-14

            - Phase 1 上线
        """.trimIndent()

        val result = LatestJsonGenerator.extractLatestChangelog(md)
        assertEquals(
            "## v0.1.1 · 2026-08-18\n- 设置新增 changelog\n- 设置项重排",
            result,
        )
    }

    @Test
    fun extractLatestChangelog_blankInputReturnsEmpty() {
        assertEquals("", LatestJsonGenerator.extractLatestChangelog(""))
        assertEquals("", LatestJsonGenerator.extractLatestChangelog("   \n\n  "))
    }

    @Test
    fun extractLatestChangelog_noHeaderReturnsEmpty() {
        assertEquals("", LatestJsonGenerator.extractLatestChangelog("# 标题\n- 一些 bullet\n"))
    }

    @Test
    fun extractLatestChangelog_sectionWithNoBullets() {
        val md = "## v0.1.0 · 2026-08-14\n\n## v0.0.0 · 2026-08-01\n\n- old\n"
        assertEquals("## v0.1.0 · 2026-08-14", LatestJsonGenerator.extractLatestChangelog(md))
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
    val signerCertSha256: String = "",
)
