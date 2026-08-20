package com.icespiritai.offline.ui.home

import android.graphics.Rect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [HighlightOverlay] — the canvas that draws
 * severity-colored rounded rectangles around each [TextLine] whose
 * normalized text contains a hit's matched text.
 *
 * Canvas content is opaque to Compose UI tests (we can't read pixels or
 * snapshot DrawScope commands without visual screenshot diffing). So
 * instead of asserting drawn coordinates directly, this test exercises
 * the **input contract** the production code must accept without
 * crashing:
 *
 *  - empty `lines` + empty `hits` (the idle state)
 *  - a line whose normalized text contains a hit's keyword
 *  - a line whose box is off-screen (negative left, large width)
 *  - extreme scaleX / scaleY / offsetX / offsetY (zoom / pan)
 *  - `Info`-severity hits, which the overlay deliberately skips
 *    (`return@forEach` in HighlightOverlay.kt)
 *  - the dark-theme color scheme path (covers the `isDark` branch)
 *
 * If any of these regress — e.g. someone removes the `return@forEach`
 * for `Severity.Info` and the overlay starts drawing the default color
 * for it — `setContent` won't fail, but the visual diff in the
 * HomeScreenScreenshotTest will. Here we at least lock the
 * "must-not-crash" contract.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HighlightOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders cleanly when both lines and hits are empty`() {
        composeRule.setContent {
            HighlightOverlay(lines = emptyList(), hits = emptyList())
        }
        // No assertions on render — Compose test API can't read Canvas
        // pixels. The contract is "doesn't crash".
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly when lines are present but no hits match`() {
        val lines = listOf(
            TextLine("本店专治糖尿病", Rect(10, 10, 200, 50), 0.9f),
            TextLine("无效文本", Rect(10, 60, 200, 100), 0.8f),
        )
        composeRule.setContent {
            HighlightOverlay(
                lines = lines,
                hits = listOf(
                    RuleHit(
                        ruleId = "AD_LAW_999",
                        matchedText = "100% 有效",
                        category = "绝对化用语",
                        regulation = "《广告法》第 9 条",
                        severity = Severity.Violation,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly with matching line and hit`() {
        val lines = listOf(
            TextLine("100% 有效", Rect(0, 0, 200, 50), 0.9f),
        )
        val hits = listOf(
            RuleHit(
                ruleId = "AD_LAW_007",
                matchedText = "100% 有效",
                category = "绝对化用语",
                regulation = "《广告法》第 9 条",
                severity = Severity.Violation,
            ),
        )
        composeRule.setContent {
            HighlightOverlay(lines = lines, hits = hits)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly with off-screen line coordinates`() {
        // Negative left, very large right — the canvas' DrawScope clips
        // automatically; we just need to verify HighlightOverlay doesn't
        // throw when the math overshoots.
        val lines = listOf(
            TextLine("100% 有效", Rect(-1000, -1000, 99999, 99999), 0.9f),
        )
        val hits = listOf(
            RuleHit(
                ruleId = "AD_LAW_007",
                matchedText = "100% 有效",
                category = "绝对化用语",
                regulation = "《广告法》第 9 条",
                severity = Severity.Violation,
            ),
        )
        composeRule.setContent {
            HighlightOverlay(lines = lines, hits = hits)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly with extreme scale and offset`() {
        val lines = listOf(
            TextLine("100% 有效", Rect(10, 10, 100, 50), 0.9f),
        )
        val hits = listOf(
            RuleHit(
                ruleId = "AD_LAW_007",
                matchedText = "100% 有效",
                category = "绝对化用语",
                regulation = "《广告法》第 9 条",
                severity = Severity.Violation,
            ),
        )
        composeRule.setContent {
            HighlightOverlay(
                lines = lines,
                hits = hits,
                scaleX = 3.5f,
                scaleY = 0.25f,
                offsetX = 200f,
                offsetY = -150f,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly with Info severity hit (skipped branch)`() {
        // Production code deliberately skips drawing for Severity.Info
        // (`return@forEach` at HighlightOverlay.kt:46). We don't want a
        // future change to silently start drawing the default color for
        // Info hits; this test pins the input-shape contract for that
        // branch.
        val lines = listOf(
            TextLine("信息性提示", Rect(0, 0, 200, 50), 0.9f),
        )
        val hits = listOf(
            RuleHit(
                ruleId = "INFO_001",
                matchedText = "信息性提示",
                category = "普通提示",
                regulation = "无",
                severity = Severity.Info,
            ),
        )
        composeRule.setContent {
            HighlightOverlay(lines = lines, hits = hits)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly under dark color scheme`() {
        // Exercises the `isDark` branch in HighlightOverlay — important
        // because the production color lookup reads MaterialTheme and
        // Robolectric's default theme may not match the dark scheme path.
        val lines = listOf(
            TextLine("100% 有效", Rect(0, 0, 200, 50), 0.9f),
        )
        val hits = listOf(
            RuleHit(
                ruleId = "AD_LAW_007",
                matchedText = "100% 有效",
                category = "绝对化用语",
                regulation = "《广告法》第 9 条",
                severity = Severity.Warning,
            ),
        )
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HighlightOverlay(lines = lines, hits = hits)
            }
        }
        composeRule.waitForIdle()
    }

    // Reference usage of dp to avoid an unused-import warning if dp is
    // only used here; future extensions might want to size-test the
    // Canvas with explicit dimensions.
    @Suppress("unused")
    private val testSizeDp = 1.dp
}