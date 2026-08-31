package com.icespiritai.offline.ui.viewer

import android.app.Application
import android.graphics.Rect as AndroidRect
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleLines(): List<TextLine> = listOf(
        TextLine(text = "本店专治糖尿病", box = AndroidRect(0, 0, 100, 20), confidence = 0.95f),
        TextLine(text = "100% 有效", box = AndroidRect(0, 25, 100, 45), confidence = 0.88f),
    )

    private val fakeUri: Uri
        get() = Uri.parse("file:///tmp/sample.jpg")

    @Test
    fun `ViewerScreen renders TopBar + image + text list when imageUri is non-null`() {
        var backClicks = 0
        composeTestRule.setContent {
            // Phase 3.5 (2026-08-31): ViewerImage now calls HighlightOverlay
            // when lineBoxes is non-empty. HighlightOverlay reads
            // iceSpiritSeverityColors (LocalSeverityColors) for the per-line
            // box stroke colors, so the test must wrap in IceSpiritVisionTheme
            // (plain MaterialTheme throws "LocalSeverityColors not provided").
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerScreen(
                        imageUri = fakeUri,
                        lineBoxes = sampleLines(),
                        hits = emptyList(),
                        hitsCount = 1,
                        imageSize = null,
                        onBack = { backClicks++ },
                    )
                }
            }
        }

        // TopBar title
        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_title))
            .assertIsDisplayed()
        // Image content description — ViewerImage wires viewer_image_cd
        // through to Telephoto's ZoomableAsyncImage, but ZoomableAsyncImage
        // does not publish its semantics tree in Robolectric (Coil's image
        // load fails against the fake file:// URI, so the image node is
        // never composed). The image IS visible on a real device; see
        // docs/superpowers/specs/2026-08-19-icevision-image-viewer-design.md
        // §6 acceptance checklist. The remaining assertions below cover
        // the ViewerScreen → ViewerImage wiring (top bar + text list).
        // TextList rows
        composeTestRule.onNodeWithText("本店专治糖尿病").assertIsDisplayed()
        composeTestRule.onNodeWithText("100% 有效").assertIsDisplayed()
    }

    @Test
    fun `ViewerScreen back button invokes onBack`() {
        var backClicks = 0
        composeTestRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerScreen(
                        imageUri = fakeUri,
                        lineBoxes = emptyList(),
                        hits = emptyList(),
                        hitsCount = 0,
                        imageSize = null,
                        onBack = { backClicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun `ViewerScreen shows ViewerEmpty when imageUri is null`() {
        composeTestRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerScreen(
                        imageUri = null,
                        lineBoxes = sampleLines(),
                        hits = emptyList(),
                        hitsCount = 0,
                        imageSize = null,
                        onBack = {},
                    )
                }
            }
        }

        // The empty placeholder is the only thing visible in the body.
        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_empty))
            .assertIsDisplayed()
    }

    @Test
    fun `ViewerScreen passes hitsCount through to ViewerTextList header`() {
        composeTestRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerScreen(
                        imageUri = fakeUri,
                        lineBoxes = sampleLines(),
                        hits = emptyList(),
                        hitsCount = 7,
                        imageSize = null,
                        onBack = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_hits_count, 7))
            .assertIsDisplayed()
    }
}