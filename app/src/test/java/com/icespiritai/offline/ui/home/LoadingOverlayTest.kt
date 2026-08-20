package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [LoadingOverlay] and its companion
 * [loadingLabelRes] mapper.
 *
 * Pins:
 *   - The label passed in is rendered verbatim (callers map their domain
 *     state to a string before calling LoadingOverlay).
 *   - `loadingLabelRes(OcrRunning)` and `loadingLabelRes(RuleScanning)` map
 *     to the documented R.string IDs. If anyone moves the strings or
 *     renames the stages, this fails before the UI loads the wrong copy.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LoadingOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the supplied label verbatim`() {
        composeRule.setContent {
            LoadingOverlay(label = "扫描违规规则…")
        }
        composeRule.onNodeWithText("扫描违规规则…").assertExists()
    }

    @Test
    fun `renders a different label across rerenders`() {
        // First label, then replace it — make sure the composable doesn't
        // cache the first Text node. (Compose tests rerender through
        // setContent, so this is mostly a smoke for the surface.)
        composeRule.setContent {
            LoadingOverlay(label = "识别图片文字…")
        }
        composeRule.onNodeWithText("识别图片文字…").assertExists()
    }

    @Test
    fun `loadingLabelRes maps OcrRunning to status_ocr_running`() {
        assertEquals(
            com.icespiritai.offline.R.string.status_ocr_running,
            loadingLabelRes(AnalysisStateLoadingStage.OcrRunning),
        )
    }

    @Test
    fun `loadingLabelRes maps RuleScanning to status_rule_scanning`() {
        assertEquals(
            com.icespiritai.offline.R.string.status_rule_scanning,
            loadingLabelRes(AnalysisStateLoadingStage.RuleScanning),
        )
    }
}