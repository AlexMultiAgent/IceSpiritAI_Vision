package com.icespiritai.offline.ui.settings

/**
 * Parses `app/src/main/assets/user-changelog.md` as a pure function so the
 * markdown grammar can be exercised in JVM unit tests without an Android
 * `Context` / `AssetManager`.
 *
 * Format (mirrors `ice_chat_minimal`):
 *   - `#` top-level title (1 occurrence, ignored)
 *   - `## vX.Y.Z · YYYY-MM-DD` version header
 *   - `- ...` bullet lines attached to the current version
 *   - blank lines: paragraph separator (ignored, does not close the version)
 *   - any other line (prose): ignored
 *
 * Returns entries in file order; the asset is maintained newest-first so the
 * caller can render directly without resorting. String-sorting version names
 * would put "v0.9" after "v0.10".
 */
object VersionHistoryRenderer {

    /** One version section: header + body bullets. */
    data class HistoryEntry(
        /** e.g. `"v0.1.1"`. The `v` prefix is preserved verbatim. */
        val version: String,
        /** e.g. `"2026-08-18"`. Empty string when the header has no separator. */
        val date: String,
        /** Bullet text with the leading `- ` stripped; empty list if no bullets. */
        val bullets: List<String>,
    )

    /**
     * Separators allowed between the version and the date in a `## vX.Y.Z` header,
     * tried in priority order. The `" - "` form (with surrounding spaces) prevents
     * splitting on the hyphen in an ISO date like `2026-08-18`.
     */
    private val HEADER_SEPARATORS = listOf("·", "|", "—", " - ")

    fun parse(markdown: String): List<HistoryEntry> {
        if (markdown.isBlank()) return emptyList()

        val entries = mutableListOf<HistoryEntry>()
        var version: String? = null
        var date = ""
        var bullets = mutableListOf<String>()

        fun flush() {
            val v = version ?: return
            entries.add(HistoryEntry(v, date, bullets.toList()))
        }

        for (line in markdown.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("## ") -> {
                    flush()
                    val (parsedVersion, parsedDate) = parseHeader(trimmed.removePrefix("## ").trim())
                    version = parsedVersion
                    date = parsedDate
                    bullets = mutableListOf()
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
}
