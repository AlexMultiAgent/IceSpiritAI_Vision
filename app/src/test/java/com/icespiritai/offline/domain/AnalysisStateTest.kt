package com.icespiritai.offline.domain

import android.graphics.Rect
import android.net.StubUri
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStateTest {

    @Test
    fun loading_carriesStage() {
        val state = AnalysisState.Loading(AnalysisState.Loading.Stage.OcrRunning)
        assertEquals(AnalysisState.Loading.Stage.OcrRunning, state.stage)
    }

    @Test
    fun ocrDone_marksLowConfidenceBelow50Percent() {
        val low = AnalysisState.OcrDone("hi", 0.3f, emptyList())
        assertTrue(low.lowConfidence)

        val high = AnalysisState.OcrDone("hi", 0.9f, emptyList())
        assertFalse(high.lowConfidence)
    }

    @Test
    fun error_carriesCauseAndRetryable() {
        val cause = IllegalStateException("boom")
        val state = AnalysisState.Error(
            message = "OCR failed",
            errorCode = ErrorCode.OCR_FAILED,
            retryable = true,
            cause = cause,
        )
        assertEquals("OCR failed", state.message)
        assertEquals(ErrorCode.OCR_FAILED, state.errorCode)
        assertTrue(state.retryable)
        assertNotNull(state.cause)
    }

    @Test
    fun defaultRetryable_matchesErrorCode() {
        assertTrue(AnalysisState.Error("a", ErrorCode.OCR_UNAVAILABLE).retryable)
        assertTrue(AnalysisState.Error("a", ErrorCode.OCR_FAILED).retryable)
        assertTrue(AnalysisState.Error("a", ErrorCode.UNKNOWN).retryable)
        assertFalse(AnalysisState.Error("a", ErrorCode.RULES_FAILED).retryable)
    }

    @Test
    fun allErrorCodes_areDistinct() {
        val codes = ErrorCode.values()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun violationReport_carriesAllFields() {
        // android.net.Uri is a framework stub under unitTests.isReturnDefaultValues=true:
        // both Uri.parse() and Uri.EMPTY are null. Use a minimal Uri subclass from
        // package android.net (see StubUri.kt) — its constructor is package-private.
        val uri: Uri = StubUri()
        val hit = RuleHit("r1", "最佳", "extreme-claim", "广告法 §9", Severity.Violation)
        val report = ViolationReport(uri, "最佳品牌", listOf(hit), 1700000000000L)
        assertEquals(uri, report.imageUri)
        assertEquals("最佳品牌", report.ocrText)
        assertEquals(1, report.hits.size)
        assertEquals(Severity.Violation, report.hits[0].severity)
    }

    @Test
    fun violationReport_derivesHasTextAndLowConfidence() {
        val uri: Uri = StubUri()
        val noText = ViolationReport(uri, "  ", emptyList(), 1L, avgConfidence = 0f)
        assertFalse(noText.hasText)
        assertFalse(noText.lowConfidence)

        val lowConfidence = ViolationReport(uri, "最佳品牌", emptyList(), 1L, avgConfidence = 0.4f)
        assertTrue(lowConfidence.hasText)
        assertTrue(lowConfidence.lowConfidence)

        val confident = ViolationReport(uri, "最佳品牌", emptyList(), 1L, avgConfidence = 0.9f)
        assertTrue(confident.hasText)
        assertFalse(confident.lowConfidence)
    }

    @Test
    fun violationReport_avgConfidenceDefaultsToZeroForBackwardCompat() {
        val report = ViolationReport(StubUri(), "text", emptyList(), 1L)
        assertEquals(0f, report.avgConfidence, 0.0001f)
    }

    @Test
    fun violationReport_lineBoxesDefaultsToEmptyForBackwardCompat() {
        val report = ViolationReport(StubUri(), "text", emptyList(), 1L)
        assertEquals(emptyList<TextLine>(), report.lineBoxes)
    }

    @Test
    fun violationReport_carriesLineBoxes() {
        val boxes = listOf(TextLine("a", Rect(0, 0, 10, 10), 0.9f))
        val report = ViolationReport(StubUri(), "a", emptyList(), 1L, lineBoxes = boxes)
        assertEquals(boxes, report.lineBoxes)
    }
}
