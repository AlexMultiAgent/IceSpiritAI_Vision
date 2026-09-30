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
    fun buildLatestJson_pretty_isParseableAndHumanReadable() {
        val pretty = LatestJsonGenerator.buildLatestJson(
            versionCode = 1, versionName = "0.1.0",
            apkUrl = "http://x/y.apk", apkSize = 1L,
            apkSha256 = "g".repeat(64), changelog = "",
            pretty = true,
        )
        // Pretty format must still round-trip through the same parser.
        val info = parser.decodeFromString(TestAppVersionInfo.serializer(), pretty)
        assertEquals(1, info.versionCode)
        assertEquals("0.1.0", info.versionName)
        assertTrue("pretty output should contain newlines", pretty.contains("\n"))
    }

    @Test
    fun buildLatestJson_pretty_escapesAndRoundTripsPathologicalChangelog() {
        // Regression pin for bbc3e65 + future prettyPrint refactors:
        // pretty mode must (a) escape ASCII quotes / backslashes / control chars
        // inside string values, (b) preserve embedded newlines as \n (NOT raw),
        // (c) round-trip through the parser without loss.
        //
        // Test input mixes: ASCII quotes, backslash, embedded newline,
        // control character (\u0001 = SOH, must become \u0001 in JSON).
        val pathologicalChangelog = """## v0.5.6
- 修复 §"发布流水线踩坑" 引用
- 含 backslash: \\path\\to\\file
- 含 newline:
  第二行
- 中文 + 引号 "嵌套\"""".trimIndent()
            .let { it + "\u0001" }  // 末尾加 SOH control character

        val pretty = LatestJsonGenerator.buildLatestJson(
            versionCode = 1, versionName = "0.5.6",
            apkUrl = "http://x/y.apk", apkSize = 1L,
            apkSha256 = "h".repeat(64),
            changelog = pathologicalChangelog,
            pretty = true,
        )

        // (a) ASCII quotes inside string must be escaped as \"
        assertTrue(
            "pretty 输出必须含 \\\"(escaped form,got: ${pretty.take(400)}…)",
            pretty.contains("\\\""),
        )
        // (b) embedded newline 在 JSON wire form 必须是 \\n (2 chars),不是 raw \n (1 char)
        //     raw \n 会让 prettyPrint 把 string 提前关闭,后续字符被误解析为下一个 token
        //     简化:数 pretty 中 \\n 出现次数,至少 1 次(changelog 里 embedded newline)
        val escapedNewlineCount = "\\n".toRegex().findAll(pretty).count()
        assertTrue(
            "pretty 输出必须含 \\\\n(escaped newline),actual count=$escapedNewlineCount,got: ${pretty.take(400)}…",
            escapedNewlineCount >= 1,
        )
        // (c) control char (< 0x20) 必须 escape 成 \\uXXXX
        assertTrue(
            "pretty 输出必须含 \\\\u0001(control char escape),got: ${pretty.take(400)}…",
            pretty.contains("\\u0001"),
        )
        // (d) parser 不抛 —— 说明整个 wire JSON 合法
        val info = parser.decodeFromString(TestAppVersionInfo.serializer(), pretty)
        // (e) round-trip —— parser 反解回原值,无信息丢失
        assertEquals(pathologicalChangelog, info.changelog)
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

    @Test
    fun buildLatestJson_asciiQuotesInChangelogAreEscapedAndRoundTrip() {
        // Regression pin for bbc3e65 (2026-09-25): user-changelog.md 里的 ASCII 双引号
        // (如 §"发布流水线踩坑") 之前未 escape,导致 vision-latest.json line 7 column 2804
        // JSON parse 失败。修复:jsonString() 现在 escape " 为 \"。本测试确保下次有人改回
        // 手动 StringBuilder 拼接(绕开 jsonString)会立刻 fail。
        val changelogWithAsciiQuotes = """## v0.5.6
- 修复 §"发布流水线踩坑" 引用
- §"Critical ordering" 同步""".trimIndent()
        val json = LatestJsonGenerator.buildLatestJson(
            versionCode = 1, versionName = "0.5.6",
            apkUrl = "http://x/y.apk", apkSize = 1L,
            apkSha256 = "a".repeat(64),
            changelog = changelogWithAsciiQuotes,
        )
        // 1. JSON parse 不抛 —— 说明 escape 正确,wire JSON 合法
        val info = parser.decodeFromString(TestAppVersionInfo.serializer(), json)
        // 2. round-trip 后 changelog 跟原始一致 —— escape + unescape 无损
        assertEquals(changelogWithAsciiQuotes, info.changelog)
        // 3. raw JSON 必须包含 \"(escaped 形式),而不是 raw ASCII "
        assertTrue(
            "raw JSON 必须含 \\\"(escaped form),got: ${json.take(300)}…",
            json.contains("\\\""),
        )
        // 4. 反例:wire changelog 字段里不应有 raw ASCII "(只能出现 \")
        //    用 regex 区分:"(?<!\\)" 匹配不前导 \ 的 " —— 这是 raw,wire 上必须为 0
        val changelogStart = json.indexOf("\"changelog\":\"") + "\"changelog\":\"".length
        val changelogEnd = json.indexOf("\",\"apkCumulativeDownloads\"")
        val wireChangelog = json.substring(changelogStart, changelogEnd)
        val unescapedQuotes = Regex("(?<!\\\\)\"").findAll(wireChangelog).count()
        assertEquals(
            "wire changelog 字段里 raw \" 必须 escape 成 \\\"(got: ${wireChangelog.take(200)}…)",
            0,
            unescapedQuotes,
        )
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
