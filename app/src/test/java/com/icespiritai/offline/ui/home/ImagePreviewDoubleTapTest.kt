package com.icespiritai.offline.ui.home

import android.graphics.Rect as AndroidRect
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins down the double-tap behavior of [ImagePreview]:
 *
 *  1. When `onDoubleTap` is provided AND `lineBoxes` is non-empty, two
 *     consecutive taps (within the platform double-tap window) invoke the
 *     callback exactly once.
 *
 *  2. A single tap never invokes `onDoubleTap`. `detectTapGestures` absorbs
 *     the tap silently — we never wire `onTap`, and even if a caller did,
 *     the contract here is "double-tap opens the Viewer, single-tap is
 *     reserved for future use".
 *
 *  3. When `lineBoxes` is empty (e.g. user took a photo of a wall) we don't
 *     even install the gesture detector — there's nothing in the OCR result
 *     worth a Viewer screen, so double-tapping must be a no-op. This is
 *     defense-in-depth: even if the user is mid-screenshot-animations or
 *     Race-conditions their finger, opening an empty Viewer is wrong.
 *
 * Real-world trigger: `HomeScreen` reads `completeReport?.hits` /
 * `lineBoxes` and only passes a non-null `onDoubleTap` (the Viewer router)
 * when at least one line came back from OCR.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImagePreviewDoubleTapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLines = listOf(
        TextLine(text = "本店专治糖尿病", box = AndroidRect(0, 0, 100, 20), confidence = 0.95f),
    )
    private val sampleHits = listOf(
        RuleHit(
            ruleId = "medical-001",
            matchedText = "专治",
            category = "medical-claim",
            regulation = "广告法 §16",
            severity = Severity.Violation,
        ),
    )

    @Test
    fun `double-tap on the preview invokes onDoubleTap callback`() {
        var dblClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImagePreview(
                        imageUri = Uri.parse("file:///tmp/sample.jpg"),
                        lineBoxes = sampleLines,
                        hits = sampleHits,
                        onDoubleTap = { dblClicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("image_preview")
            .performTouchInput { doubleClick(center) }
        assertEquals(1, dblClicks)
    }

    @Test
    fun `single tap does NOT invoke onDoubleTap`() {
        var dblClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImagePreview(
                        imageUri = Uri.parse("file:///tmp/sample.jpg"),
                        lineBoxes = sampleLines,
                        hits = sampleHits,
                        onDoubleTap = { dblClicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("image_preview")
            .performTouchInput { click(center) }
        assertEquals(0, dblClicks)
    }

    @Test
    fun `no callback when lineBoxes is empty even if double-tapped`() {
        var dblClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImagePreview(
                        imageUri = Uri.parse("file:///tmp/sample.jpg"),
                        lineBoxes = emptyList(),
                        hits = emptyList(),
                        onDoubleTap = { dblClicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("image_preview")
            .performTouchInput { doubleClick(center) }
        assertEquals(0, dblClicks)
    }

    /**
     * Regression test for the v0.1.x double-tap bug. Before the fix at
     * `HomeScreen.kt:145`, the `lineBoxes` derivation only consulted
     * `AnalysisState.OcrDone`. Once state advanced to `AnalysisState.Complete`,
     * `ocrResult` was null and `lineBoxes` collapsed to `emptyList()`, which
     * gated out the `pointerInput` block inside `ImagePreview`. The callback
     * was wired (`onOpenViewer = nav.navigate(Routes.VIEWER)`) but never
     * invoked, so double-tap on the Home preview silently did nothing.
     *
     * The fix makes HomeScreen pull `lineBoxes` from
     * `completeReport?.lineBoxes` as a second fallback. This test simulates
     * that post-fix derivation by passing a non-empty `lineBoxes` straight to
     * `ImagePreview` — the same value the fixed HomeScreen would now forward.
     */
    @Test
    fun `double-tap with non-empty lineBoxes from Complete-state derivation invokes callback`() {
        var dblClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImagePreview(
                        imageUri = Uri.parse("file:///tmp/sample.jpg"),
                        // After the fix, HomeScreen will pass this exact list when
                        // state = Complete and report.lineBoxes is non-empty.
                        lineBoxes = sampleLines,
                        hits = sampleHits,
                        onDoubleTap = { dblClicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("image_preview")
            .performTouchInput { doubleClick(center) }
        assertEquals(1, dblClicks)
    }
}
