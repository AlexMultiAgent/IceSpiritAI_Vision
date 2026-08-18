package com.icespiritai.offline.analysis

import android.net.Uri
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.OcrEngineUnavailable
import com.icespiritai.offline.domain.OcrFailed
import com.icespiritai.offline.domain.RuleLoadFailed
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.ocr.OcrEngine
import com.icespiritai.offline.rules.RuleMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Orchestrates the OCR -> rule-scan pipeline and reports progress as a cold
 * [Flow] of [AnalysisState], which the ViewModel maps straight onto the UI.
 *
 * Success path emits 5 states:
 *   Loading(OcrRunning) -> OcrDone -> Loading(RuleScanning) -> RuleScanned -> Complete
 *
 * Failures terminate the flow with [AnalysisState.Error] instead of throwing, so a
 * collector never has to wrap `analyze` in a try/catch.
 *
 * The rule matcher is supplied per [analyze] call rather than at construction
 * time so a multi-tab caller ([IceSpiritVisionViewModel] routes the same OCR
 * output through AdSignage and FoodLabeling rule sets) drives a single
 * repository instance with whichever matcher is current.
 */
class ImageAnalyzerRepository(
    private val ocrEngine: OcrEngine,
) {
    fun analyze(uri: Uri, matcher: RuleMatcher): Flow<AnalysisState> = flow {
        emit(AnalysisState.Loading(AnalysisState.Loading.Stage.OcrRunning))

        // OcrEngine implementations own their dispatcher (see PaddleOcrEngine).
        val ocrResult = try {
            ocrEngine.recognize(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OcrEngineUnavailable) {
            emit(
                AnalysisState.Error(
                    message = e.message ?: e.javaClass.simpleName,
                    errorCode = ErrorCode.OCR_UNAVAILABLE,
                    retryable = true,
                    cause = e,
                )
            )
            return@flow
        } catch (e: OcrFailed) {
            emit(
                AnalysisState.Error(
                    message = e.message ?: e.javaClass.simpleName,
                    errorCode = ErrorCode.OCR_FAILED,
                    retryable = true,
                    cause = e,
                )
            )
            return@flow
        } catch (e: Exception) {
            emit(
                AnalysisState.Error(
                    message = e.message ?: e.javaClass.simpleName,
                    errorCode = ErrorCode.UNKNOWN,
                    retryable = true,
                    cause = e,
                )
            )
            return@flow
        }

        emit(
            AnalysisState.OcrDone(
                text = ocrResult.fullText,
                confidence = ocrResult.avgConfidence,
                lineBoxes = ocrResult.lineBoxes
            )
        )

        emit(AnalysisState.Loading(AnalysisState.Loading.Stage.RuleScanning))

        val hits = try {
            withContext(Dispatchers.IO) { matcher.scan(ocrResult.fullText) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuleLoadFailed) {
            emit(
                AnalysisState.Error(
                    message = e.message ?: e.javaClass.simpleName,
                    errorCode = ErrorCode.RULES_FAILED,
                    retryable = false,
                    cause = e,
                )
            )
            return@flow
        } catch (e: Exception) {
            emit(
                AnalysisState.Error(
                    message = e.message ?: e.javaClass.simpleName,
                    errorCode = ErrorCode.UNKNOWN,
                    retryable = true,
                    cause = e,
                )
            )
            return@flow
        }

        emit(AnalysisState.RuleScanned(hits))

        emit(
            AnalysisState.Complete(
                ViolationReport(
                    imageUri = uri,
                    ocrText = ocrResult.fullText,
                    hits = hits,
                    timestampMs = System.currentTimeMillis(),
                    avgConfidence = ocrResult.avgConfidence,
                )
            )
        )
    }
}
