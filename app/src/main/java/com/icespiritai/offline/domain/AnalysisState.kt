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
        /**
         * Display-oriented dimensions of the FULL bitmap the OCR engine
         * actually saw (post-EXIF rotation, in pixels). Used by
         * [com.icespiritai.offline.ui.home.ImagePreview] as the reference
         * space for HighlightOverlay rects — not `painter.intrinsicSize`,
         * which reflects Coil's layout-size downsampled bitmap and would
         * put boxes in a coordinate space that does NOT match OCR's.
         *
         * Defaulted to 0 so legacy callers / unit tests that don't need
         * overlay rects still compile; `computeFitTransform` treats
         * (0, 0) as "fall back to painter.intrinsicSize".
         */
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
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
    val avgConfidence: Float,
    /**
     * Display-oriented dimensions of the FULL bitmap OCR actually saw
     * (post-EXIF rotation). See [AnalysisState.OcrDone.imageWidth] for
     * why this is propagated through the pipeline.
     */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
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
    val severity: Severity,
    /**
     * Domain marker — `"ad"` for 广告招牌 or `"food"` for 食品标识. Drives the
     * [CategoryDisplay] lookup and the `domain` field on the ZIP manifest's
     * `report.json`. Defaulted to `"ad"` so legacy hits and tests compile
     * without ceremony.
     */
    val domain: String = "ad",
    /**
     * Full text of the provision cited by [regulation]. Empty for legacy /
     * programmatically constructed hits; the UI hides the expand control then.
     */
    val lawText: String = "",
)

/**
 * Hit severity, used to drive the [StatusBannerKind] banner choice and the
 * per-bucket counts surfaced in the result panel. Ordering matters here
 * even though we don't rely on enum.ordinal: the matcher rules in
 * `StatusBannerFor` are written as `when (severity)` and exhaustive, so
 * adding a new bucket means adding a new entry AND a new `when` branch
 * (compiler-enforced).
 *
 * Order picked so Positive sits at the END — keeping it as the trailing
 * entry guards against any future caller that falls back to enum.ordinal
 * for ranking and accidentally surfaces "compliant" as the worst banner.
 */
enum class Severity { Violation, Warning, Info, Positive }

/**
 * Explicit rank used to pick the **worst** severity across a list of hits.
 *
 * Why not enum.ordinal: the enum ordering `[Violation, Warning, Info, Positive]`
 * is a presentation detail (Positive is intentionally trailing so any
 * accidental fallback to ordinal ranking would still pick Violation over it,
 * but `maxOfOrNull { it.severity }` would otherwise surface Positive as
 * "worst" once a Positive-emit rule ships). Pinning the policy in code keeps
 * the worst-hit selection independent of enum reorderings.
 *
 * Higher number = worse. Positive is 0 — Positive hits must NEVER escalate a
 * "worst" pick; they're surfaced through a separate KPI bucket if/when one
 * ships, not by hijacking the banner / row tint.
 */
fun severityRank(severity: Severity): Int = when (severity) {
    Severity.Violation -> 3
    Severity.Warning -> 2
    Severity.Info -> 1
    Severity.Positive -> 0
}

data class ViolationReport(
    val imageUri: Uri,
    val ocrText: String,
    val hits: List<RuleHit>,
    val timestampMs: Long,
    /**
     * Average per-line OCR confidence from the run that produced [ocrText].
     * `0f` means no text was detected (or the engine does not expose scores).
     * Used to surface the "low confidence, treat as reference only" hint
     * instead of silently trusting a noisy recognition result.
     */
    val avgConfidence: Float = 0f,
    /**
     * Per-line OCR text + bounding boxes from the run that produced [ocrText].
     * Drives the Viewer's per-line text list and the overlay hit boxes.
     * Defaults to `emptyList()` so legacy / programmatic constructions still
     * compile (the Viewer shows an empty text list and skips the overlay).
     */
    val lineBoxes: List<TextLine> = emptyList(),
    /**
     * Display-oriented dimensions of the analyzed image, propagated from
     * the [OcrResult] that produced [lineBoxes]. Drives the overlay's
     * coordinate-space transform on the HomeScreen when the user has
     * progressed past OcrDone into the Complete state (where the OcrDone
     * `AnalysisState` is no longer the active state and we have to read
     * the dims from the report). Defaulted to 0 so legacy constructions
     * continue to compile.
     */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {
    /** True when OCR found at least one non-blank character. */
    val hasText: Boolean get() = ocrText.isNotBlank()

    /** Low-confidence OCR should be surfaced as a hint, not hidden. */
    val lowConfidence: Boolean get() = hasText && avgConfidence < 0.5f
}
