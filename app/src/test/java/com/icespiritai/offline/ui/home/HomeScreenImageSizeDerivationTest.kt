package com.icespiritai.offline.ui.home

import android.net.StubUri
import androidx.compose.ui.unit.IntSize
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression pin for v0.1.30 "红框位置标错了！" (round 2): the
 * `imageSize` derivation in [HomeScreen] picks the right source dims for
 * [com.icespiritai.offline.ui.home.ImagePreview.computeFitTransform].
 *
 * Why a pure helper test rather than driving Compose: HomeScreen's
 * imageSize wiring lives behind [IceSpiritVisionViewModel] state, and
 * forcing a fake VM through Compose is far more brittle than pinning the
 * precedence rules directly. The downstream consumer
 * [computeFitTransform] has its own precedence test
 * ([ImagePreviewFitTransformTest]); together they pin the contract that
 * OCR-engine-emitted bitmap dims flow through state to the overlay
 * without being silently dropped or substituted with the layout-size
 * downsampled painter.intrinsicSize.
 *
 * Robolectric is not required — this is a pure JVM unit test.
 */
class HomeScreenImageSizeDerivationTest {

    private fun ocrDone(w: Int = 0, h: Int = 0): AnalysisState.OcrDone =
        AnalysisState.OcrDone(
            text = "sample text",
            confidence = 0.9f,
            lineBoxes = emptyList(),
            imageWidth = w,
            imageHeight = h,
        )

    private fun report(w: Int = 0, h: Int = 0): ViolationReport =
        ViolationReport(
            imageUri = StubUri(),
            ocrText = "sample",
            hits = listOf(
                RuleHit(
                    ruleId = "x",
                    matchedText = "sample",
                    category = "c",
                    regulation = "r",
                    severity = Severity.Info,
                ),
            ),
            timestampMs = 0L,
            avgConfidence = 0.9f,
            lineBoxes = emptyList(),
            imageWidth = w,
            imageHeight = h,
        )

    @Test
    fun `OcrDone dims win when both OcrDone and Complete report are populated`() {
        val s = imageSizeForState(
            ocrDone(w = 3024, h = 4032),
            report(w = 9999, h = 9999),
        )
        assertEquals(IntSize(3024, 4032), s)
    }

    @Test
    fun `falls back to Complete report dims when OcrDone is missing or zero`() {
        // Mid-Complete state: OcrDone is no longer the active state, so its
        // dims are null in practice. But even if some legacy caller passes a
        // zero-dim OcrDone, the report must still win when it has real dims.
        assertEquals(
            IntSize(1512, 2016),
            imageSizeForState(ocrDone(w = 0, h = 0), report(w = 1512, h = 2016)),
        )
        assertEquals(
            IntSize(3024, 4032),
            imageSizeForState(ocrResult = null, completeReport = report(w = 3024, h = 4032)),
        )
    }

    @Test
    fun `returns null when both are missing or zero (Idle Loading legacy)`() {
        assertNull(imageSizeForState(ocrResult = null, completeReport = null))
        assertNull(imageSizeForState(ocrDone(w = 0, h = 0), report(w = 0, h = 0)))
        // Mixed: OcrDone has dims, report doesn't — OcrDone still wins.
        assertEquals(
            IntSize(3024, 4032),
            imageSizeForState(ocrDone(w = 3024, h = 4032), report(w = 0, h = 0)),
        )
        // Mixed: OcrDone has zero dims, report has real dims — report wins.
        assertEquals(
            IntSize(1512, 2016),
            imageSizeForState(ocrDone(w = 0, h = 4032), report(w = 1512, h = 2016)),
        )
    }

    @Test
    fun `negative or partial-zero dims are treated as unusable`() {
        // Defensive: a future refactor could accidentally populate only one
        // of the two dims. The guard `imageWidth > 0 && imageHeight > 0`
        // ensures we don't derive a partial IntSize that would propagate a
        // half-formed transform to computeFitTransform.
        assertNull(imageSizeForState(ocrDone(w = 3024, h = 0), completeReport = null))
        assertNull(imageSizeForState(ocrDone(w = 0, h = 4032), completeReport = null))
        assertNull(imageSizeForState(ocrDone(w = -1, h = 4032), completeReport = null))
    }
}
