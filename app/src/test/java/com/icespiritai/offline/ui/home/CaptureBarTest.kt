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
 * hosting the pick [androidx.compose.material3.FloatingActionButton] on the left
 * and the [CaptureButton] Extended FAB on the right.
 *
 * Pins:
 *  - both affordances render with their documented accessibility labels
 *    ("拍照" + "从相册选图")
 *  - each exposes its own contentDescription so TalkBack can find them without
 *    the localized text
 *  - click handlers route to the right callback (拍照 → onCapture,
 *    从相册选图 → onPick)
 *  - `enabled = false` disables the capture FAB only; the pick FAB stays
 *    clickable (escape hatch during Loading — see CaptureBar KDoc)
 *
 * Note on finder strategy: the pick FAB's outer Surface carries our
 * contentDescription, which causes Compose UI test's merged tree to suppress
 * the inner Icon node (TalkBack-style: announce the description, not the
 * underlying icon). The capture Extended FAB has the same property on its
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
                CaptureBar(onCapture = {}, onPick = {})
            }
        }
        // CaptureButton (ExtendedFloatingActionButton) carries its
        // contentDescription on the outer modifier — merged tree suppresses
        // the inner Text node, so use `useUnmergedTree` to find the raw Text.
        composeRule.onNodeWithText("拍照", useUnmergedTree = true).assertExists()
        // The new pick FAB has no visible "选图" text label — only a
        // PhotoLibrary icon + the accessibility description set on the
        // outer Surface. Assert the a11y description instead.
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
    }

    @Test
    fun `exposes distinct contentDescriptions for each button`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = {}, onPick = {})
            }
        }
        composeRule.onNodeWithContentDescription("拍照").assertExists()
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
    }

    @Test
    fun `clicking capture routes to onCapture and not onPick`() {
        var captured = 0
        var picked = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = { captured++ }, onPick = { picked++ })
            }
        }
        composeRule.onNodeWithContentDescription("拍照").performClick()
        assertEquals(1, captured)
        assertEquals(0, picked)
    }

    @Test
    fun `clicking pick routes to onPick and not onCapture`() {
        var captured = 0
        var picked = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureBar(onCapture = { captured++ }, onPick = { picked++ })
            }
        }
        composeRule.onNodeWithContentDescription("从相册选图").performClick()
        assertEquals(0, captured)
        assertEquals(1, picked)
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
                CaptureBar(onCapture = {}, onPick = {}, enabled = true)
            }
        }
        composeRule.onNodeWithText("拍照", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
    }
}
