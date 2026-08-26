package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `home idle shows capture and pick buttons`() {
        var captured = 0
        var picked = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                HomeScreenBare(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                )
            }
        }
        // ExtendedFloatingActionButton / OutlinedButton carry their
        // contentDescription on the outer modifier; Compose UI test's
        // merged tree suppresses the inner Text node (TalkBack-style).
        // See CaptureButtonTest / CaptureBarTest header notes.
        composeRule.onNodeWithText("拍照", useUnmergedTree = true).assertExists()
        // Pick FAB shows visible "选图" label alongside PhotoLibrary icon
        // (per user spec 2026-08-26). Assert the text label and the a11y
        // description both exist.
        composeRule.onNodeWithText("选图", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("从相册选图").assertExists()
        composeRule.onNodeWithContentDescription("拍照").performClick()
        composeRule.onNodeWithContentDescription("从相册选图").performClick()
        assert(captured == 1)
        assert(picked == 1)
    }

    @Test
    fun `home idle shows image hint`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                HomeScreenBare(onCapture = {}, onPick = {})
            }
        }
        composeRule.onNodeWithText("请对正图片后点击拍照").assertExists()
    }

    /**
     * v0.1.11 wiring smoke: HomeScreen accepts `onOpenViewer` and passes it
     * down to `ImagePreview.onDoubleTap`. In the Idle state the OCR pipeline
     * has not run, so `lineBoxes` is empty and ImagePreview must NOT install
     * its gesture detector — double-tapping the preview is therefore a no-op
     * even though the callback is wired. This catches a regression where
     * someone breaks the gating (e.g. by passing `lineBoxes` unconditionally)
     * while still validating that the new `onOpenViewer` parameter exists on
     * the `HomeScreen` signature (compile-time check).
     *
     * The positive case — double-tap with non-empty lineBoxes fires the
     * callback — is covered by [ImagePreviewDoubleTapTest] for the underlying
     * composable. Driving HomeScreen itself to `AnalysisState.Complete` from a
     * unit test would require a fake OCR engine + fake rule matchers injected
     * through `IceSpiritVisionViewModel`, which would force a refactor of the
     * ViewModel scope (out of scope for the v0.1.11 image-viewer work).
     */
    @Test
    fun `home idle double-tap on image preview does NOT invoke onOpenViewer`() {
        var openViewerClicks = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                HomeScreen(
                    onOpenSettings = {},
                    onOpenViewer = { openViewerClicks++ },
                )
            }
        }
        composeRule.onNodeWithTag("image_preview").assertExists()
        composeRule.onNodeWithTag("image_preview")
            .performTouchInput { doubleClick(center) }
        assertEquals(0, openViewerClicks)
    }
}
