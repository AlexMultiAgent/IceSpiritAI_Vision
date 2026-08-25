package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for the skeleton variant of [LoadingOverlay] — three
 * rounded-rect "hit card" placeholders with a pulsing alpha on the inner
 * text bars, plus a running-phase label beneath.
 *
 * The literal Chinese strings in `onNodeWithText(...)` are the actual
 * values from `app/src/main/res/values/strings.xml`:
 *   - `status_ocr_running`    → "识别图片文字…"
 *   - `status_rule_scanning`  → "扫描违规规则…"
 * The plan's draft literals ("OCR 识别中…" / "规则扫描中…") were wrong
 * and were corrected here.
 *
 * Wrapped in `IceSpiritVisionTheme(themeMode = ThemeMode.DARK)` because
 * `LoadingOverlay` reads `MaterialTheme.colorScheme.surfaceContainerHigh`
 * and `surfaceVariant`; without the theme wrapper Robolectric throws
 * `IllegalStateException: CompositionLocal ... not present`. Same wrap
 * pattern as [HighlightOverlayTest] and [ImagePreviewDoubleTapTest].
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LoadingOverlaySkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingOverlayRendersPhaseText_OcrRunning() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                LoadingOverlay(phase = AnalysisState.Loading.Stage.OcrRunning)
            }
        }
        composeRule.onNodeWithText("识别图片文字…").assertExists()
    }

    @Test
    fun loadingOverlayRendersPhaseText_RuleScanning() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                LoadingOverlay(phase = AnalysisState.Loading.Stage.RuleScanning)
            }
        }
        composeRule.onNodeWithText("扫描违规规则…").assertExists()
    }
}
