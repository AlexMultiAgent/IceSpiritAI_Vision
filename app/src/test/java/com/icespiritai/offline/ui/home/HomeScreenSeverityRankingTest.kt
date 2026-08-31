package com.icespiritai.offline.ui.home

import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.severityRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the [severityRank] contract and the worst-hit filter used by
 * `StatusBannerFor` in [HomeScreen]. Background: prior to v0.1.36, the
 * banner code did `report.hits.maxOfOrNull { it.severity }` (enum.ordinal
 * ranking). [Severity]'s enum was `[Info, Warning, Violation, Positive]`,
 * so Positive's higher ordinal silently demoted a real Violation to
 * `Success` once any Positive hit landed in the same report.
 *
 * The fix has two parts that MUST move together:
 *   1. [Severity] reordered to `[Violation, Warning, Info, Positive]`
 *      (defense-in-depth — Positive now sits at the END, so an accidental
 *      fallback to ordinal ranking would still pick Violation).
 *   2. `StatusBannerFor` switched to an explicit rank via [severityRank]
 *      + a `.filter { it.severity != Severity.Positive }` pass that
 *      guarantees Positive can never escalate the banner.
 *
 * Robolectric is not required — pure JVM unit test.
 */
class HomeScreenSeverityRankingTest {

    @Test
    fun severityRank_violation_is_highest() {
        assertEquals(3, severityRank(Severity.Violation))
    }

    @Test
    fun severityRank_warning_beats_info() {
        assertEquals(true, severityRank(Severity.Warning) > severityRank(Severity.Info))
    }

    @Test
    fun severityRank_positive_is_intentionally_lowest() {
        // Positive must not be able to outrank any other bucket via rank.
        // It gets surfaced through a separate KPI bucket if/when Phase 3.3
        // ships — never through the banner.
        assertEquals(0, severityRank(Severity.Positive))
    }

    @Test
    fun worst_hit_picks_violation_even_when_positive_is_present() {
        // Regression pin: a Positive hit alongside a Violation MUST NOT
        // promote the banner to Success (the pre-v0.1.36 latent bug).
        val hits = listOf(
            hit("r-positive", Severity.Positive),
            hit("r-violation", Severity.Violation),
        )
        val worst = hits
            .filter { it.severity != Severity.Positive }
            .maxByOrNull { severityRank(it.severity) }
        assertNotNull("worst hit must exist when a Violation is present", worst)
        assertEquals(Severity.Violation, worst!!.severity)
    }

    @Test
    fun worst_hit_falls_back_to_warning_when_only_warning_present() {
        val hits = listOf(hit("r-w", Severity.Warning))
        val worst = hits
            .filter { it.severity != Severity.Positive }
            .maxByOrNull { severityRank(it.severity) }
        assertNotNull(worst)
        assertEquals(Severity.Warning, worst!!.severity)
    }

    @Test
    fun worst_hit_returns_null_when_only_positive_present() {
        // A Positive-only report: filter removes it, maxByOrNull returns
        // null, banner falls through to Success. Phase 3.3 may surface
        // Positive via a separate KPI bucket; for now this is the right
        // default.
        val hits = listOf(hit("r-p", Severity.Positive))
        val worst = hits
            .filter { it.severity != Severity.Positive }
            .maxByOrNull { severityRank(it.severity) }
        assertNull(worst)
    }

    @Test
    fun worst_hit_picks_violation_when_mixed_with_info_and_warning() {
        val hits = listOf(
            hit("r-info", Severity.Info),
            hit("r-warn", Severity.Warning),
            hit("r-vio", Severity.Violation),
        )
        val worst = hits
            .filter { it.severity != Severity.Positive }
            .maxByOrNull { severityRank(it.severity) }
        assertEquals("r-vio", worst!!.ruleId)
    }

    private fun hit(id: String, sev: Severity) = RuleHit(
        ruleId = id,
        matchedText = "x",
        category = "c",
        regulation = "r",
        severity = sev,
    )
}