package com.icespiritai.offline.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionHistoryRendererTest {

    @Test
    fun blank_input_returns_empty_list() {
        assertEquals(emptyList<VersionHistoryRenderer.HistoryEntry>(), VersionHistoryRenderer.parse(""))
        assertEquals(emptyList<VersionHistoryRenderer.HistoryEntry>(), VersionHistoryRenderer.parse("   \n\n  "))
    }

    @Test
    fun single_section_with_bullets() {
        val md = """
            # 用户更新日志

            ## v1.0.0 · 2026-08-01

            - 第一条
            - 第二条
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("v1.0.0", result[0].version)
        assertEquals("2026-08-01", result[0].date)
        assertEquals("", result[0].summary)
        assertEquals(listOf("第一条", "第二条"), result[0].bullets)
    }

    @Test
    fun multiple_sections_in_file_order() {
        val md = """
            # 用户更新日志

            ## v1.1.0 · 2026-08-10

            - 新功能

            ## v1.0.0 · 2026-08-01

            - 首发
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(2, result.size)
        assertEquals("v1.1.0", result[0].version)
        assertEquals(listOf("新功能"), result[0].bullets)
        assertEquals("v1.0.0", result[1].version)
        assertEquals(listOf("首发"), result[1].bullets)
    }

    @Test
    fun section_with_no_bullets_yields_empty_list() {
        val md = "## v1.0.0 · 2026-08-01\n"
        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("", result[0].summary)
        assertTrue(result[0].bullets.isEmpty())
    }

    @Test
    fun header_without_date_separator() {
        val md = "## v1.0\n\n- x\n"
        val result = VersionHistoryRenderer.parse(md)
        assertEquals("v1.0", result[0].version)
        assertEquals("", result[0].date)
        assertEquals("", result[0].summary)
        assertEquals(listOf("x"), result[0].bullets)
    }

    @Test
    fun header_with_no_spaces_around_hyphen_does_not_split() {
        // `v1.0-2026-08-18` has no ` - ` substring (the hyphens have no
        // surrounding spaces), so it must NOT be treated as
        // version="v1.0" date="2026-08-18". The whole header stays as the
        // version, date empty — guarding against the parser accidentally
        // splitting on the bare hyphen of an ISO date.
        val md = "## v1.0-2026-08-18\n\n- fix\n"
        val result = VersionHistoryRenderer.parse(md)
        assertEquals("v1.0-2026-08-18", result[0].version)
        assertEquals("", result[0].date)
    }

    @Test
    fun header_with_space_hyphen_space_does_split() {
        // `v1.0 - 2026-08-18` (spaces around the hyphen) IS the canonical
        // separator form and must split as version=v1.0, date=2026-08-18.
        val md = "## v1.0 - 2026-08-18\n\n- fix\n"
        val result = VersionHistoryRenderer.parse(md)
        assertEquals("v1.0", result[0].version)
        assertEquals("2026-08-18", result[0].date)
        assertEquals("", result[0].summary)
        assertEquals(listOf("fix"), result[0].bullets)
    }

    @Test
    fun accepts_pipe_and_em_dash_separators() {
        val md1 = "## v1.0 | 2026-08-18\n\n- a\n"
        assertEquals("v1.0", VersionHistoryRenderer.parse(md1)[0].version)
        assertEquals("2026-08-18", VersionHistoryRenderer.parse(md1)[0].date)

        val md2 = "## v1.0 — 2026-08-18\n\n- a\n"
        assertEquals("v1.0", VersionHistoryRenderer.parse(md2)[0].version)
        assertEquals("2026-08-18", VersionHistoryRenderer.parse(md2)[0].date)
    }

    @Test
    fun prose_without_summary_terminator_is_ignored() {
        // Regression pin for the legacy "non-bullet non-header lines are ignored"
        // behavior: a prose line that does NOT end with `。`/`!`/`?` is still
        // dropped. This guards against the parser accidentally treating any
        // prose line as a summary (the summary format requires the
        // validate-changelog-format.js Check 4 terminator).
        val md = """
            # 顶层标题

            一些描述性段落文字,没有以句号结尾

            ## v1.0 · 2026-08-01

            - 唯一一条 bullet
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("", result[0].summary)
        assertEquals(listOf("唯一一条 bullet"), result[0].bullets)
    }

    @Test
    fun bullets_before_any_header_are_dropped() {
        val md = """
            - orphan bullet
            ## v1.0 · 2026-08-01
            - real bullet
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("", result[0].summary)
        assertEquals(listOf("real bullet"), result[0].bullets)
    }

    // --- prose summary capture (v0.5.13+ brief format) ---------------------

    @Test
    fun summary_line_terminated_with_chinese_period_is_captured() {
        // Mirrors the current shipping format: header → 1-2 line prose summary
        // → optionally bullets. The summary ends with `。` (中文句号) per
        // validate-changelog-format.js Check 4.
        val md = """
            ## v1.0 · 2026-08-01

            修一个闪退 bug。

            - 修复具体某处崩溃
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("修一个闪退 bug。", result[0].summary)
        assertEquals(listOf("修复具体某处崩溃"), result[0].bullets)
    }

    @Test
    fun summary_terminated_with_ascii_bang_or_question_mark() {
        // ASCII `!` / `?` are also accepted terminators (validate hook allows
        // both). Realistic case: a question-form summary.
        val md1 = """
            ## v1.0 · 2026-08-01

            重大更新!

        """.trimIndent()
        assertEquals("重大更新!", VersionHistoryRenderer.parse(md1)[0].summary)

        val md2 = """
            ## v1.0 · 2026-08-01

            这次修了什么?

        """.trimIndent()
        assertEquals("这次修了什么?", VersionHistoryRenderer.parse(md2)[0].summary)
    }

    @Test
    fun summary_section_with_no_bullets_still_captures_summary() {
        // The brief style (v0.5.3-v0.5.14) has ONLY a prose summary, no
        // bullets — the previous parser rendered these as empty cards. The
        // fix: capture summary regardless of whether bullets follow.
        val md = """
            ## v1.0 · 2026-08-01

            一段只有摘要没有 bullet 的 entry。
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("一段只有摘要没有 bullet 的 entry。", result[0].summary)
        assertTrue(result[0].bullets.isEmpty())
    }

    @Test
    fun only_first_prose_line_is_summary_subsequent_paragraphs_dropped() {
        // Per `validate-changelog-format.js`, each version has exactly ONE
        // summary line. Multiple paragraphs under one `## v` are not the
        // supported format; only the first prose-terminating line is
        // captured as summary.
        val md = """
            ## v1.0 · 2026-08-01

            第一段摘要。

            第二段说明文字,会被忽略。

            - bullet
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("第一段摘要。", result[0].summary)
        assertEquals(listOf("bullet"), result[0].bullets)
    }

    @Test
    fun summary_with_legacy_sub_section_after_it() {
        // Full format: header → summary → `### 新增` → bullets. The summary
        // is captured, then the `### ` line acts as a sub-section header
        // (not currently modeled in the data class — bullets go straight
        // under the version entry). This pins current behavior so a future
        // refactor doesn't accidentally move bullets under a sub-section.
        val md = """
            ## v1.0 · 2026-08-01

            这次加了一个新功能,顺便修一个 bug。

            ### 新增

            - 新增 A

            ### 修复

            - 修复 B
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
        assertEquals("这次加了一个新功能,顺便修一个 bug。", result[0].summary)
        assertEquals(listOf("新增 A", "修复 B"), result[0].bullets)
    }
}
