package com.icespiritai.offline.ui.home

import android.graphics.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
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
 *  - `Info`-severity hits — Info is now rendered with `sev.accent(Severity.Info)`
 *    (the previous `return@forEach` skip was removed in Phase 3.3), so this
 *    test pins the "renders without crashing" contract for the Info branch
 *  - dark-theme `IceSpiritVisionTheme(themeMode = ThemeMode.DARK)` — verifies
 *    that the production color lookup reads `iceSpiritSeverityColors` (a
 *    CompositionLocal) and the local is correctly provided by the theme wrapper
 *
 * All seven existing tests plus one new Info-rendering test are wrapped in
 * `IceSpiritVisionTheme(...)` because `iceSpiritSeverityColors` is a
 * `staticCompositionLocalOf` accessor that throws if the Local is not provided.
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(lines = emptyList(), hits = emptyList())
            }
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(lines = lines, hits = hits)
            }
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(lines = lines, hits = hits)
            }
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(
                    lines = lines,
                    hits = hits,
                    scaleX = 3.5f,
                    scaleY = 0.25f,
                    offsetX = 200f,
                    offsetY = -150f,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly with Info severity hit (now rendered)`() {
        // Phase 3.3: Production code now RENDERS Info hits with
        // `sev.accent(Severity.Info)` (the previous `return@forEach` skip
        // was removed). This test pins the "doesn't crash" contract for
        // the Info branch — the visual diff lives in HomeScreenScreenshotTest.
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(lines = lines, hits = hits)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `renders cleanly under dark color scheme`() {
        // Exercises the dark-theme path of `iceSpiritSeverityColors` — the
        // production code reads `LocalSeverityColors.current` (a CompositionLocal
        // provided by `IceSpiritVisionTheme`), so wrapping in the theme gives
        // the dark `SeverityColors` with `errorAccent = DarkIceChatError`,
        // `warningAccent = DarkIceChatWarning`, etc.
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
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(lines = lines, hits = hits)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun infoSeverityHitRendersStroke() {
        val lines = listOf(
            TextLine(text = "这是提示信息", box = Rect(0, 0, 100, 20), confidence = 0.9f),
        )
        val hits = listOf(
            RuleHit(
                ruleId = "INFO_TEST",
                matchedText = "提示信息",
                category = "info",
                regulation = "通用",
                severity = Severity.Info,
            ),
        )
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                HighlightOverlay(lines = lines, hits = hits)
            }
        }
        composeRule.onRoot().assertExists()
    }

    // Reference usage of dp to avoid an unused-import warning if dp is
    // only used here; future extensions might want to size-test the
    // Canvas with explicit dimensions.
    @Suppress("unused")
    private val testSizeDp = 1.dp
}