package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [CaptureButton] — the wide "拍照" CTA on the home
 * screen.
 *
 * Pins:
 *   - "拍照" label always rendered (covers string resource binding)
 *   - contentDescription matches R.string.capture_button_desc (used by
 *     Espresso / TalkBack to find the button without the localized label)
 *   - Click invokes the onClick handler
 *   - `enabled = false` makes the underlying Button non-clickable
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
            CaptureButton(onClick = {})
        }
        composeRule.onNodeWithText("拍照").assertExists()
    }

    @Test
    fun `exposes capture-button accessibility description`() {
        composeRule.setContent {
            CaptureButton(onClick = {})
        }
        composeRule.onNodeWithContentDescription("拍照").assertExists()
    }

    @Test
    fun `click invokes onClick handler`() {
        var clicks = 0
        composeRule.setContent {
            CaptureButton(onClick = { clicks++ })
        }
        composeRule.onNodeWithText("拍照").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `disabled button does not fire onClick`() {
        var clicks = 0
        composeRule.setContent {
            CaptureButton(onClick = { clicks++ }, enabled = false)
        }
        // The Button composable marks itself as disabled; we assert the
        // semantic state and that a click attempt does not invoke the
        // handler. (Compose UI test `performClick` on a disabled Button
        // silently no-ops — verified via the counter.)
        composeRule.onNodeWithText("拍照").assertIsNotEnabled()
        composeRule.onNodeWithText("拍照").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `enabled default state is enabled`() {
        composeRule.setContent {
            CaptureButton(onClick = {})
        }
        composeRule.onNodeWithText("拍照").assertIsEnabled()
    }
}