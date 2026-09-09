package com.icespiritai.offline.ui.home

import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression pins for [worstSeverityForOverlay], the worst-severity picker
 * the HomeScreen [HighlightOverlay] uses to color each line box.
 *
 * v0.1.66 audit fix: pre-fix this was inlined as
 * `normalizedHits.filter { ... }.maxOfOrNull { it.second }` inside the
 * Canvas DrawScope. `maxOfOrNull` on a Severity enum calls
 * `Comparable<Severity>` which orders by ordinal, so a single Positive
 * hit (ordinal=3) would mask a co-located Violation (ordinal=2) — the
 * exact opposite of what the user expects. Severity.Positive is also
 * explicitly dropped from the candidate pool now (mirrors
 * `worstSeverityForLine` in ViewerTextList.kt), so Positive-only hits
 * can't escalate a line tint.
 *
 * `HighlightOverlay`'s Canvas pixels are unreadable from Compose UI
 * tests, so the picker is extracted to an `internal` helper and pinned
 * here as a pure-function contract.
 */
class HighlightOverlaySeverityRankingTest {

    @Test
    fun `violation beats warning beats info on the same line`() {
        val line = "本店专治糖尿病 全国连锁"
        val hits = listOf(
            "糖尿病" to Severity.Violation,
            "全国连锁" to Severity.Warning,
            "本店" to Severity.Info,
        )
        assertEquals(Severity.Violation, worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `warning beats info when no violation matches`() {
        val line = "全国连锁"
        val hits = listOf(
            "全国连锁" to Severity.Warning,
            "门店" to Severity.Info,
        )
        assertEquals(Severity.Warning, worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `info is returned when only info hits match`() {
        val line = "门店介绍"
        val hits = listOf(
            "门店" to Severity.Info,
        )
        assertEquals(Severity.Info, worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `positive does not escalate when a violation also matches the line`() {
        // The audit's smoking gun: a single Positive hit on the same line
        // must NOT replace a co-located Violation. Pre-fix `maxOfOrNull`
        // by ordinal returned Positive (3) > Violation (2); the line box
        // would have rendered green. With severityRank + Positive filter,
        // the line now correctly renders as Violation.
        val line = "通过审批 糖尿病"
        val hits = listOf(
            "通过审批" to Severity.Positive,
            "糖尿病" to Severity.Violation,
        )
        assertEquals(Severity.Violation, worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `positive does not escalate when a warning also matches the line`() {
        val line = "通过审批 全国连锁"
        val hits = listOf(
            "通过审批" to Severity.Positive,
            "全国连锁" to Severity.Warning,
        )
        assertEquals(Severity.Warning, worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `returns null when only positive hits match`() {
        // A Positive-only line must NOT draw a colored stroke at all —
        // the caller treats null as "no draw". Pre-fix the ordinal-based
        // path would have drawn a Positive box here too.
        val line = "通过审批"
        val hits = listOf(
            "通过审批" to Severity.Positive,
        )
        assertNull(worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `returns null when no hit's normalized text appears in the line`() {
        // Mirrors ViewerTextList.worstSeverityForLine — the helper must
        // fall through to "no draw" if no containment match exists.
        val line = "本店专治糖尿病"
        val hits = listOf(
            "全国连锁" to Severity.Violation,
        )
        assertNull(worstSeverityForOverlay(line, hits))
    }

    @Test
    fun `returns null when hits list is empty`() {
        val line = "本店专治糖尿病"
        assertNull(worstSeverityForOverlay(line, emptyList()))
    }

    @Test
    fun `returns null when normalized line is empty`() {
        // Caller may pass an empty line (e.g. an OCR line with only
        // whitespace). The picker must not throw or surface a false
        // severity for an empty line.
        val hits = listOf(
            "门店" to Severity.Violation,
        )
        assertNull(worstSeverityForOverlay("", hits))
    }

    @Test
    fun `caller must normalize — helper does not re-normalize the input`() {
        // The HomeScreen overlay normalizes both line text and hit
        // matchedText outside the loop (see HighlightOverlay.kt line 43),
        // so the helper only runs `contains` on the already-normalized
        // strings. This test pins that contract: passing the un-normalized
        // "100% 有效" with the normalized "100%有效" does NOT match — the
        // space in the line prevents containment. Callers that forget
        // to normalize will silently render nothing for that line.
        val unNormalizedLine = "100% 有效"
        val normalizedHit = "100%有效"
        val hits = listOf(normalizedHit to Severity.Violation)
        // Un-normalized line + normalized hit → no match (caller forgot
        // to normalize).
        assertNull(worstSeverityForOverlay(unNormalizedLine, hits))
        // But pre-normalized line + normalized hit → match.
        val normalizedLine = "100%有效"
        assertEquals(Severity.Violation, worstSeverityForOverlay(normalizedLine, hits))
    }
}