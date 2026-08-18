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
        assertTrue(result[0].bullets.isEmpty())
    }

    @Test
    fun header_without_date_separator() {
        val md = "## v1.0\n\n- x\n"
        val result = VersionHistoryRenderer.parse(md)
        assertEquals("v1.0", result[0].version)
        assertEquals("", result[0].date)
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
    fun non_bullet_non_header_lines_are_ignored() {
        val md = """
            # 顶层标题

            一些描述性段落文字,不算 bullet

            ## v1.0 · 2026-08-01

            - 唯一一条 bullet
        """.trimIndent()

        val result = VersionHistoryRenderer.parse(md)
        assertEquals(1, result.size)
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
        assertEquals(listOf("real bullet"), result[0].bullets)
    }
}
