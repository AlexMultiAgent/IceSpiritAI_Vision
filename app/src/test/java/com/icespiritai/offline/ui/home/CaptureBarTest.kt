package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [CaptureBar] — the Material 3 [androidx.compose.material3.BottomAppBar]
 * hosting the pick / export / capture [androidx.compose.material3.ExtendedFloatingActionButton]
 * affordances.
 *
 * v0.1.41 (2026-08-31) layout — user feedback after v0.1.40:
 *  - 3 buttons when `hasHits = true`: pick (left), export (center), capture (right).
 *  - 2 buttons when `hasHits = false`: export button hidden entirely, pick + capture
 *    take half-width each.
 *  - Export FAB shows the visible "导出" label + accessibility desc "导出取证包".
 *
 * Earlier (v0.1.31 → v0.1.40) layout, kept intact:
 *  - Pick FAB: text-then-icon. Empty icon slot + Row in text slot gives us
 *    "选图 → PhotoLibrary" without inheriting the icon→text padding the FAB
 *    would normally insert.
 *  - Capture FAB: Extended FAB (icon-then-text "拍照"), stretched to fill
 *    its half of the bar in the 2-button case.
 *
 * Pins:
 *  - both base affordances (拍照 + 从相册选图) render with their documented
 *    accessibility labels so TalkBack can find them without the localized text
 *  - each exposes its own contentDescription
 *  - pick FAB now shows a visible "选图" text label next to the PhotoLibrary icon
 *    (per user spec 2026-08-26)
 *  - export FAB appears when `hasHits = true` and disappears when `hasHits = false`
 *  - export FAB is reachable as "导出取证包" (the full verb in the a11y desc)
 *  - click handlers route to the right callback (拍照 → onCapture, 选图 → onPick,
 *    导出 → onExport)
 *  - `enabled = false` disables the capture FAB only; the pick FAB stays
 *    clickable (escape hatch during Loading — see CaptureBar KDoc)
 *
 * Note on finder strategy: each FAB's outer Surface carries our
 * contentDescription, which causes Compose UI test's merged tree to suppress
 * the inner Icon / Text nodes (TalkBack-style: announce the description, not
 * the underlying icon). The capture Extended FAB has the same property on its
 * `Text("拍照")` child. Text-only assertions therefore use
 * `useUnmergedTree = true`; click / a11y assertions target the merged Surface
 * via `onNodeWithContentDescription`.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaptureBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders capture label and pick accessibility description`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = {}, onPick = {}, onExport = {}, hasHits = false)
            }
        }
        // CaptureButton (ExtendedFloatingActionButton) carries its
        // contentDescription on the outer modifier — merged tree suppresses
        // the inner Text node, so use `useUnmergedTree` to find the raw Text.
        composeRule.onNodeWithText("拍照", useUnmergedTree = true).assertExists()
        // Pick FAB now shows visible "选图" text alongside the PhotoLibrary
        // icon (per user spec 2026-08-26). Assert the text exists and the
        // accessibility description is also exposed for TalkBack.
        composeRule.onNodeWithText("选图", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
    }

    @Test
    fun `exposes distinct contentDescriptions for each base button`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = {}, onPick = {}, onExport = {}, hasHits = false)
            }
        }
        composeRule.onNodeWithContentDescription("拍照").assertExists()
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
    }

    @Test
    fun `clicking capture routes to onCapture and not onPick or onExport`() {
        var captured = 0
        var picked = 0
        var exported = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                    onExport = { exported++ },
                    hasHits = true,
                )
            }
        }
        composeRule.onNodeWithContentDescription("拍照").performClick()
        assertEquals(1, captured)
        assertEquals(0, picked)
        assertEquals(0, exported)
    }

    @Test
    fun `clicking pick routes to onPick and not onCapture or onExport`() {
        var captured = 0
        var picked = 0
        var exported = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                    onExport = { exported++ },
                    hasHits = true,
                )
            }
        }
        composeRule.onNodeWithContentDescription("从相册选图").performClick()
        assertEquals(0, captured)
        assertEquals(1, picked)
        assertEquals(0, exported)
    }

    @Test
    fun `clicking export routes to onExport and not onCapture or onPick`() {
        var captured = 0
        var picked = 0
        var exported = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                    onExport = { exported++ },
                    hasHits = true,
                )
            }
        }
        composeRule.onNodeWithContentDescription("导出取证包").performClick()
        assertEquals(0, captured)
        assertEquals(0, picked)
        assertEquals(1, exported)
    }

    @Test
    fun `disabled=false disables capture fab but pick fab stays clickable`() {
        // The pick FAB intentionally stays clickable when `enabled = false`
        // — pick-from-gallery is an escape hatch during Loading. The capture
        // FAB's `enabled = false` is routed through CaptureButton's
        // `if (enabled) onClick else ({})` + disabled semantics (covered in
        // CaptureButtonTest).
        var captured = 0
        var picked = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                    onExport = {},
                    hasHits = true,
                    enabled = false,
                )
            }
        }
        // Capture FAB disabled: click attempt no-ops.
        composeRule.onNodeWithContentDescription("拍照").performClick()
        assertEquals(0, captured)
        // Pick FAB always enabled: click fires.
        composeRule.onNodeWithContentDescription("从相册选图").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun `captureBarExposesCaptureAndPickFabs`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(
                    onCapture = {},
                    onPick = {},
                    onExport = {},
                    hasHits = false,
                    enabled = true,
                )
            }
        }
        composeRule.onNodeWithText("拍照", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
    }

    @Test
    fun `export button is shown when hasHits=true`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = {}, onPick = {}, onExport = {}, hasHits = true)
            }
        }
        // Visible "导出" label (shortened from "导出取证包" per v0.1.41 user
        // feedback) and the accessibility description "导出取证包" both
        // present.
        composeRule.onNodeWithText("导出", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("导出取证包").assertExists()
    }

    @Test
    fun `export button is hidden when hasHits=false`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = {}, onPick = {}, onExport = {}, hasHits = false)
            }
        }
        // No export affordance — both the visible label and the a11y
        // description should be absent (no empty slot, no disabled state).
        composeRule.onNodeWithText("导出", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("导出取证包").assertDoesNotExist()
    }
}
