package com.icespiritai.buildhelpers

import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Pure helpers for the vision-latest.json manifest. Mirrors the shape of
 * translate's same-name class (D:/GitHub/IceSpiritAI_Translate/app/src/main/
 * java/com/icespiritai/offline/updater/LatestJsonGenerator.kt). Lives in
 * buildSrc/ so the Gradle task in app/build.gradle.kts (Task 15) can call
 * these helpers — buildSrc/ is auto-loaded onto every build script's
 * classpath, whereas app/src/main/java is not.
 */
object LatestJsonGenerator {

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            DigestInputStream(fis, md).use { dis ->
                val buf = ByteArray(64 * 1024)
                while (dis.read(buf) >= 0) { /* drain */ }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extract the FIRST `## vX.Y.Z` section from a user-changelog.md blob,
     * verbatim. The returned string starts with the `## vX.Y.Z` header line
     * and ends just before the next `## v...` header (or EOF).
     *
     * Format matches what `AppVersionInfo.changelog` is displayed as in
     * UpdateSection.kt — plain text, bullets kept with `- ` prefix so the
     * banner reads naturally. Empty / no-section input returns "".
     *
     * MUST STAY IN SYNC with `VersionHistoryRenderer.parse` in
     * app/src/main/java/.../ui/settings/. Build scripts cannot import from
     * app/src/main/java/ (see CLAUDE.md Gotchas); the two parsers are pinned
     * together by the unit test in buildSrc/src/test.
     */
    fun extractLatestChangelog(markdown: String): String {
        if (markdown.isBlank()) return ""
        val lines = markdown.lines()
        val startIdx = lines.indexOfFirst { it.trim().startsWith("## v") }
        if (startIdx < 0) return ""
        val endIdx = (startIdx + 1 until lines.size)
            .firstOrNull { lines[it].trim().startsWith("## v") }
            ?: lines.size
        // Drop blank separator lines so the JSON value is compact and
        // visually clean when rendered as plain text in the in-app update
        // banner. VersionHistoryRenderer in app/src/main/java ignores blank
        // lines too, so the two parsers stay aligned.
        return lines.subList(startIdx, endIdx)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun buildLatestJson(
        versionCode: Int,
        versionName: String,
        apkUrl: String,
        apkSize: Long,
        apkSha256: String,
        changelog: String,
        apkCumulativeDownloads: Long = 0,
    ): String {
        val sb = StringBuilder(256)
        sb.append('{')
        sb.append("\"versionCode\":").append(versionCode).append(',')
        sb.append("\"versionName\":").append(jsonString(versionName)).append(',')
        sb.append("\"apkUrl\":").append(jsonString(apkUrl)).append(',')
        sb.append("\"apkSize\":").append(apkSize).append(',')
        sb.append("\"apkSha256\":").append(jsonString(apkSha256)).append(',')
        sb.append("\"changelog\":").append(jsonString(changelog)).append(',')
        sb.append("\"apkCumulativeDownloads\":").append(apkCumulativeDownloads)
        sb.append('}')
        return sb.toString()
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
