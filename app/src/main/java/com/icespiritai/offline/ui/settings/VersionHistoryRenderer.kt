package com.icespiritai.offline.ui.settings

/**
 * Parses `app/src/main/assets/user-changelog.md` as a pure function so the
 * markdown grammar can be exercised in JVM unit tests without an Android
 * `Context` / `AssetManager`.
 *
 * Format (mirrors `ice_chat_minimal`):
 *   - `#` top-level title (1 occurrence, ignored)
 *   - `## vX.Y.Z · YYYY-MM-DD` version header
 *   - 1 prose summary line (plain text, ends with `。`/`!`/`?`) — captured
 *     as [HistoryEntry.summary]. Optional; empty when omitted (legacy
 *     sections that go straight to bullets stay valid).
 *   - `- ...` bullet lines attached to the current version
 *   - blank lines: paragraph separator (ignored, does not close the version)
 *   - any other line (prose): ignored (e.g. legacy verbose paragraphs that
 *     predate the brief-summary format)
 *
 * The first prose line following `## v` — and preceding the first `###`
 * sub-header or `-` bullet — is captured as the summary. This matches
 * `validate-changelog-format.js` Check 4 ("摘要行必须以「。」结尾"). The
 * in-app banner (`UpdateSection`) does NOT use this parser — it reads the
 * verbatim first section via `LatestJsonGenerator.extractLatestChangelog`
 * in buildSrc, which already preserves prose; the two parsers are pinned
 * separately by their own tests.
 *
 * Returns entries in file order; the asset is maintained newest-first so the
 * caller can render directly without resorting. String-sorting version names
 * would put "v0.9" after "v0.10".
 */
object VersionHistoryRenderer {

    /** One version section: header + optional summary + body bullets. */
    data class HistoryEntry(
        /** e.g. `"v0.1.1"`. The `v` prefix is preserved verbatim. */
        val version: String,
        /** e.g. `"2026-08-18"`. Empty string when the header has no separator. */
        val date: String,
        /**
         * Brief plain-text summary (e.g. `"修 APP 设置页打开更新日志闪退..."`).
         * Empty string when the section has no prose summary line — in that
         * case the bullets alone describe the version (legacy format).
         */
        val summary: String,
        /** Bullet text with the leading `- ` stripped; empty list if no bullets. */
        val bullets: List<String>,
    )

    /**
     * Separators allowed between the version and the date in a `## vX.Y.Z` header,
     * tried in priority order. The `" - "` form (with surrounding spaces) prevents
     * splitting on the hyphen in an ISO date like `2026-08-18`.
     */
    private val HEADER_SEPARATORS = listOf("·", "|", "—", " - ")

    /**
     * Punctuation allowed at the end of a prose summary line. Mirrors
     * `validate-changelog-format.js` Check 4 (中文句号 / ASCII 叹号 / 问号).
     * Any line ending with one of these, sitting between the `## v` header
     * and the first `###` sub-header / `-` bullet, is captured as summary.
     */
    private val SUMMARY_TERMINATORS = listOf("。", "!", "?")

    fun parse(markdown: String): List<HistoryEntry> {
        if (markdown.isBlank()) return emptyList()

        val entries = mutableListOf<HistoryEntry>()
        var version: String? = null
        var date = ""
        var summary = ""
        var bullets = mutableListOf<String>()

        fun flush() {
            val v = version ?: return
            entries.add(HistoryEntry(v, date, summary, bullets.toList()))
        }

        for (line in markdown.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("## ") -> {
                    flush()
                    val (parsedVersion, parsedDate) = parseHeader(trimmed.removePrefix("## ").trim())
                    version = parsedVersion
                    date = parsedDate
                    summary = ""
                    bullets = mutableListOf()
                }

                version != null && summary.isEmpty() && trimmed.isNotEmpty()
                    && !trimmed.startsWith("- ") && !trimmed.startsWith("### ")
                    && isSummaryLine(trimmed) -> {
                    summary = trimmed
                }

                trimmed.startsWith("- ") && version != null -> {
                    bullets.add(trimmed.removePrefix("- ").trim())
                }
            }
        }
        flush()
        return entries
    }

    private fun parseHeader(header: String): Pair<String, String> {
        for (separator in HEADER_SEPARATORS) {
            val index = header.indexOf(separator)
            if (index > 0) {
                return header.substring(0, index).trim() to
                    header.substring(index + separator.length).trim()
            }
        }
        return header.trim() to ""
    }

    private fun isSummaryLine(trimmed: String): Boolean {
        val last = trimmed.last()
        return SUMMARY_TERMINATORS.any { last == it[0] }
    }
}
