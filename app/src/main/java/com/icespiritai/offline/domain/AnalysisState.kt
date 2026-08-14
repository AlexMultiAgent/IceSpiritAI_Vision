package com.icespiritai.offline.domain

import android.graphics.Rect
import android.net.Uri

enum class ErrorCode {
    /** OCR model missing / OpenCV native lib failed to load / model load exception. retryable=true. */
    OCR_UNAVAILABLE,
    /** OCR inference / image decode failed. retryable=true. */
    OCR_FAILED,
    /** Rules JSON load / parse failed (packaging defect). retryable=false. */
    RULES_FAILED,
    /** Catch-all. retryable=true. */
    UNKNOWN,
}

sealed class AnalysisState {
    object Idle : AnalysisState()
    data class Loading(val stage: Stage) : AnalysisState() {
        enum class Stage { OcrRunning, RuleScanning }
    }
    data class OcrDone(
        val text: String,
        val confidence: Float,
        val lineBoxes: List<TextLine>,
        val lowConfidence: Boolean = confidence < 0.5f
    ) : AnalysisState()
    data class RuleScanned(val hits: List<RuleHit>) : AnalysisState()
    data class Complete(val report: ViolationReport) : AnalysisState()
    data class Error(
        val message: String,
        val errorCode: ErrorCode,
        val retryable: Boolean = defaultRetryable(errorCode),
        val cause: Throwable? = null,
    ) : AnalysisState() {
        companion object {
            private fun defaultRetryable(code: ErrorCode): Boolean = when (code) {
                ErrorCode.OCR_UNAVAILABLE, ErrorCode.OCR_FAILED, ErrorCode.UNKNOWN -> true
                ErrorCode.RULES_FAILED -> false
            }
        }
    }
}

data class OcrResult(
    val fullText: String,
    val lineBoxes: List<TextLine>,
    val avgConfidence: Float
)

data class TextLine(
    val text: String,
    val box: Rect,
    val confidence: Float
)

data class RuleHit(
    val ruleId: String,
    val matchedText: String,
    val category: String,
    val regulation: String,
    val severity: Severity
)

enum class Severity { Info, Warning, Violation }

data class ViolationReport(
    val imageUri: Uri,
    val ocrText: String,
    val hits: List<RuleHit>,
    val timestampMs: Long
)
