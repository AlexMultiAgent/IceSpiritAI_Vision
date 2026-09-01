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
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun hit(ruleId: String, matched: String, sev: Severity) = RuleHit(
        ruleId = ruleId,
        matchedText = matched,
        category = "cat",
        regulation = "reg",
        severity = sev,
    )

    @Test
    fun `ViewerTextList renders one text per TextLine and header counts`() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerTextList(
                        lineBoxes = sampleLines(),
                        hits = emptyList(),
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
                        hits = emptyList(),
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
                        hits = emptyList(),
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
                        hits = emptyList(),
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

    // ---- v0.1.41 hit-tint + substring highlight coverage ----

    @Test
    fun `worstSeverityForLine picks violation over warning over info`() {
        val line = TextLine("本店专治糖尿病", Rect(0, 0, 0, 0), 0.9f)
        // Policy: a violation + a warning in the same line must surface
        // the violation, not the warning — the row tint must reflect the
        // bucket the user has to act on, not the bucket with the most
        // matches.
        val hits = listOf(
            hit("r-warn", "本店", Severity.Warning),
            hit("r-vio", "糖尿病", Severity.Violation),
        )
        assertEquals(Severity.Violation, worstSeverityForLine(line, hits))
    }

    @Test
    fun `worstSeverityForLine ignores positive hits even when only positive matches`() {
        // A Positive-only report must not escalate the row tint. The
        // function returns null so the row falls back to the plain
        // surface color.
        val line = TextLine("通过审批", Rect(0, 0, 0, 0), 0.9f)
        val hits = listOf(hit("r-pos", "通过", Severity.Positive))
        assertNull(worstSeverityForLine(line, hits))
    }

    @Test
    fun `worstSeverityForLine returns null when no hit matches the line`() {
        val line = TextLine("本店专治糖尿病", Rect(0, 0, 0, 0), 0.9f)
        val hits = listOf(hit("r-vio", "全国连锁", Severity.Violation))
        assertNull(worstSeverityForLine(line, hits))
    }

    @Test
    fun `highlightMatchedSubstrings maps whitespace-tolerant containment back to original offsets`() {
        // "100% 有效" contains "100%有效" only after whitespace-strip
        // normalization — the highlight function must still find the
        // original substring and map it back to the un-normalized range.
        val line = TextLine("100% 有效", Rect(0, 0, 0, 0), 0.9f)
        val hits = listOf(hit("r-100", "100%有效", Severity.Violation))
        val matches = highlightMatchedSubstrings(line, hits)
        assertEquals(1, matches.size)
        val (range, sev) = matches.first()
        // Range must cover the "100% 有效" substring in the ORIGINAL
        // text (indices 0..6), including the space.
        assertEquals(0, range.first)
        assertEquals(6, range.last)
        assertEquals(Severity.Violation, sev)
    }

    @Test
    fun `highlightMatchedSubstrings returns empty when no hit matches`() {
        val line = TextLine("本店专治糖尿病", Rect(0, 0, 0, 0), 0.9f)
        val hits = listOf(hit("r-vio", "全国连锁", Severity.Violation))
        assertTrue(highlightMatchedSubstrings(line, hits).isEmpty())
    }

    @Test
    fun `highlightMatchedSubstrings finds all non-overlapping occurrences`() {
        // "门店门店" contains "门店" twice — the function should emit
        // two ranges, not one.
        val line = TextLine("门店门店", Rect(0, 0, 0, 0), 0.9f)
        val hits = listOf(hit("r-store", "门店", Severity.Warning))
        val matches = highlightMatchedSubstrings(line, hits)
        assertEquals(2, matches.size)
        assertEquals(0..1, matches[0].first)
        assertEquals(2..3, matches[1].first)
    }

    // ---- mapNormRangeToOriginal (v0.1.41 helper) ----
    //
    // The viewer highlights matched substrings inside OCR lines. The
    // rule engine emits match ranges in the *normalized* string (whitespace
    // stripped); to paint them on the original line we have to walk the
    // original text and skip whitespace, advancing the normalized index in
    // lockstep. These tests pin the offset math so that a one-character
    // shift in the rule match can't silently paint on the wrong character.

    @Test
    fun `mapNormRangeToOriginal maps simple no-whitespace string 1to1`() {
        // No whitespace → normalized index == original index. The full
        // string maps to its full original range.
        val original = "本店专治糖尿病"
        // normalized = "本店专治糖尿病", idx=0 len=7
        val mapped = mapNormRangeToOriginal(original, normStart = 0, normLength = 7)
        // 7 chars occupy original indices 0..6 inclusive.
        assertEquals(0..6, mapped)
    }

    @Test
    fun `mapNormRangeToOriginal skips leading and internal whitespace`() {
        // OCR often produces strings like "本店 专治 糖尿病" (mixed CJK + spaces).
        // Normalized = "本店专治糖尿病" (7 chars). The normalized range [2, 4)
        // — covering "专治" — should map to original indices that skip the
        // intervening space.
        val original = "本店 专治 糖尿病"
        val mapped = mapNormRangeToOriginal(original, normStart = 2, normLength = 2)
        // First 2 norm chars are "本店" → original indices 0..1
        // normIdx 2 = '专' → original index 3 (skipping space at idx 2)
        // normIdx 3 = '治' → original index 4
        // range = original[3..4] inclusive (IntRange displays as start..endInclusive)
        assertEquals(3..4, mapped)
    }

    @Test
    fun `mapNormRangeToOriginal handles trailing whitespace in original`() {
        // OCR occasionally emits trailing space; that doesn't affect the
        // mapping (the function walks until origIdx == original.length).
        val original = "本店专治糖尿病   "
        val mapped = mapNormRangeToOriginal(original, normStart = 4, normLength = 3)
        // norm chars 4..7 = "尿病"; original indices 4..6 inclusive
        // (the trailing spaces are skipped — they're past the last
        // normalized char so don't matter for the range, only the walk).
        assertEquals(4..6, mapped)
    }

    @Test
    fun `mapNormRangeToOriginal returns null when norm range runs off the end`() {
        // normStart=8 with a 7-char normalized string never lands — the
        // function should return null (caller treats as "no highlight").
        val original = "本店专治糖尿病"
        val mapped = mapNormRangeToOriginal(original, normStart = 8, normLength = 1)
        assertNull("out-of-bounds norm range must return null", mapped)
    }

    @Test
    fun `mapNormRangeToOriginal returns null for zero or negative inputs`() {
        // Defensive: normLength=0 means "no highlight" (caller skipped it);
        // negative inputs are programmer error and must not silently paint
        // a wrong range.
        val original = "本店专治糖尿病"
        assertNull("normLength=0 must return null", mapNormRangeToOriginal(original, 0, 0))
        assertNull("normStart<0 must return null", mapNormRangeToOriginal(original, -1, 2))
        assertNull("normLength<0 must return null", mapNormRangeToOriginal(original, 0, -1))
    }

    @Test
    fun `ViewerTextList composes with hits when wrapped in IceSpiritVisionTheme`() {
        // Smoke test: passing a non-empty hit list with a real severity
        // must not crash composition (the row reads
        // iceSpiritSeverityColors to paint the tint). Without
        // IceSpiritVisionTheme, the CompositionLocal throws.
        val lines = listOf(
            TextLine("本店专治糖尿病", Rect(0, 0, 100, 20), 0.95f),
            TextLine("门店", Rect(0, 25, 100, 45), 0.88f),
        )
        composeTestRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViewerTextList(
                        lineBoxes = lines,
                        hits = listOf(
                            hit("r-vio", "糖尿病", Severity.Violation),
                            hit("r-warn", "门店", Severity.Warning),
                        ),
                        hitsCount = 2,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // Surviving setContent + both lines rendering is the assertion —
        // the throws-once path would have aborted setContent.
        composeTestRule.onNodeWithText("本店专治糖尿病").assertIsDisplayed()
        composeTestRule.onNodeWithText("门店").assertIsDisplayed()
    }
}
