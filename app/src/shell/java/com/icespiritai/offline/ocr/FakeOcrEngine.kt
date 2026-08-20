package com.icespiritai.offline.ocr

import android.graphics.Rect
import android.net.Uri
import com.icespiritai.offline.domain.OcrEngineUnavailable
import com.icespiritai.offline.domain.OcrResult
import com.icespiritai.offline.domain.TextLine
import kotlinx.coroutines.delay

class FakeOcrEngine(
    private val cannedText: String = "",
    private val cannedConfidence: Float = 1.0f,
    private val simulatedDelayMs: Long = 0L
) : OcrEngine {
    override suspend fun recognize(uri: Uri): OcrResult {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
        if (cannedText.isEmpty()) {
            throw OcrEngineUnavailable("FakeOcrEngine has no canned text")
        }
        return OcrResult(
            fullText = cannedText,
            lineBoxes = listOf(TextLine(cannedText, Rect(), cannedConfidence)),
            avgConfidence = cannedConfidence
        )
    }

    override suspend fun release() = Unit
}