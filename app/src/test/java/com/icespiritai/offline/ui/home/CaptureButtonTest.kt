package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.assertIsEnabled
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
 * Compose UI test for [CaptureButton] — the primary "拍照" CTA on the
 * home screen.
 *
 * Pins:
 *   - "拍照" label always rendered (covers string resource binding)
 *   - contentDescription matches R.string.capture_button_desc (used by
 *     Espresso / TalkBack to find the button without the localized label)
 *   - Click invokes the onClick handler
 *   - `enabled = false` makes the underlying FAB non-clickable
 *
 * Note on finder strategy: ExtendedFloatingActionButton's outer Surface
 * carries our `contentDescription`, which causes Compose UI test's merged
 * tree to suppress the inner Text node (TalkBack-style: announce the
 * description, not the underlying text). Text-only assertions therefore
 * use `useUnmergedTree = true`; click / enabled assertions target the
 * merged Surface via `onNodeWithContentDescription`.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34; same workaround as HomeScreenTest / ViewerScreenTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaptureButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the take-photo label`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureButton(onClick = {})
            }
        }
        composeRule.onNodeWithText("拍照", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `exposes capture-button accessibility description`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureButton(onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("拍照").assertExists()
    }

    @Test
    fun `click invokes onClick handler`() {
        var clicks = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureButton(onClick = { clicks++ })
            }
        }
        composeRule.onNodeWithContentDescription("拍照").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `disabled button does not fire onClick`() {
        var clicks = 0
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureButton(onClick = { clicks++ }, enabled = false)
            }
        }
        // The wrapper emits the `disabled` semantic when !enabled and
        // no-ops the onClick; a click attempt on a disabled FAB silently
        // no-ops — verified via the counter (the visual disabled state is
        // a Material3 implementation detail, so we don't pin
        // assertIsNotEnabled here).
        composeRule.onNodeWithContentDescription("拍照").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `enabled default state is enabled`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                CaptureButton(onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("拍照").assertIsEnabled()
    }
}
