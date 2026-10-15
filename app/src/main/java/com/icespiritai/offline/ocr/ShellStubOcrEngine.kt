package com.icespiritai.offline.ocr

import android.graphics.Rect
import android.net.Uri
import com.icespiritai.offline.domain.OcrResult
import com.icespiritai.offline.domain.TextLine

/**
 * Minimal OCR engine for the `shell` modelProfile APK — no ONNX model,
 * no PaddleOCR native libs, just a canned single-line result so the UI
 * can be exercised end-to-end on a CI build.
 *
 * Distinct from [com.icespiritai.offline.ocr.FakeOcrEngine] (which lives
 * in `src/test/` and is only used by unit tests). The shell profile is
 * a deployment-time concept; [FakeOcrEngine] is a test-time concept —
 * keeping them separate prevents `src/shell/` from leaking into the
 * `testDebugUnitTestKotlin` classpath under `ice_ocr_rules` profile.
 */
class ShellStubOcrEngine(
    private val cannedText: String = "本店专治糖尿病,100% 有效",
    private val cannedConfidence: Float = 0.9f,
) : OcrEngine {
    override suspend fun recognize(uri: Uri): OcrResult =
        OcrResult(
            fullText = cannedText,
            lineBoxes = listOf(TextLine(cannedText, Rect(), cannedConfidence)),
            avgConfidence = cannedConfidence,
        )

    override suspend fun release() = Unit
}
