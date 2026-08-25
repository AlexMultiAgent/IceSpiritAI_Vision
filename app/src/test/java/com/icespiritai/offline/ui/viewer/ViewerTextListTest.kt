package com.icespiritai.offline.ui.viewer

import android.app.Application
import android.graphics.Rect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.TextLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerTextListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleLines(): List<TextLine> = listOf(
        TextLine(text = "本店专治糖尿病", box = Rect(0, 0, 100, 20), confidence = 0.95f),
        TextLine(text = "100% 有效", box = Rect(0, 25, 100, 45), confidence = 0.88f),
        TextLine(text = "三行", box = Rect(0, 50, 100, 70), confidence = 0.80f),
    )

    @Test
    fun `ViewerTextList renders one text per TextLine and header counts`() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerTextList(
                        lineBoxes = sampleLines(),
                        hitsCount = 2,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("本店专治糖尿病").assertIsDisplayed()
        composeTestRule.onNodeWithText("100% 有效").assertIsDisplayed()
        composeTestRule.onNodeWithText("三行").assertIsDisplayed()

        // Header: viewer_lines_count("共 %1$d 行文字") + viewer_hits_count("命中 %1$d 处")
        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_lines_count, 3))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_hits_count, 2))
            .assertIsDisplayed()
    }

    @Test
    fun `ViewerTextList renders empty placeholder when lineBoxes is empty`() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerTextList(
                        lineBoxes = emptyList(),
                        hitsCount = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_empty))
            .assertIsDisplayed()
    }

    @Test
    fun `ViewerTextList renders one row per TextLine`() {
        val lines = sampleLines()
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerTextList(
                        lineBoxes = lines,
                        hitsCount = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Each TextLine.text should produce exactly one rendered Text node.
        // Use onAllNodesWithText to count — when the same text is unique per
        // line, this gives 1 each.
        for (line in lines) {
            composeTestRule.onAllNodesWithText(line.text).assertCountEquals(1)
        }
    }

    /**
     * Regression pin for the v0.1.26 Viewer crash: a [LazyColumn] keyed by
     * `it.text` blows up with `IllegalArgumentException: Key X was already
     * used` when two TextLines share identical text — a common case in OCR
     * output (e.g. "门店" detected in multiple bounding boxes on the same
     * sign). The previous `key = { it.text }` formulation crashed the
     * Viewer the moment the user scrolled; the fix is index-based keys.
     * If anyone reverts to a content-based key, this test fails at
     * setContent() with the underlying IllegalArgumentException.
     */
    @Test
    fun `ViewerTextList does not crash when TextLines share identical text`() {
        val lines = listOf(
            TextLine(text = "门店", box = Rect(0, 0, 100, 20), confidence = 0.95f),
            TextLine(text = "门店", box = Rect(0, 25, 100, 45), confidence = 0.88f),
            TextLine(text = "门店", box = Rect(0, 50, 100, 70), confidence = 0.80f),
        )
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerTextList(
                        lineBoxes = lines,
                        hitsCount = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // setContent would throw the IllegalArgumentException above if the
        // LazyColumn key wasn't unique. Surviving setContent + rendering the
        // header at all is the assertion.
        composeTestRule.onNodeWithText(ctx.getString(R.string.viewer_lines_count, 3))
            .assertIsDisplayed()
    }
}