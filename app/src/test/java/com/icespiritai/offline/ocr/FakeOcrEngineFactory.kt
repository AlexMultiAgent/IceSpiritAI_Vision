package com.icespiritai.offline.ocr

import android.content.Context

/**
 * Test-only OcrEngineFactory. Pairs with [FakeOcrEngine] in `src/test/`
 * so unit tests can drive the OCR pipeline without depending on the
 * shell-profile sourceSet.
 *
 * Discovery via ServiceLoader works because this FQN is registered in
 * `src/test/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`.
 * The shell-profile runtime factory lives separately in
 * [com.icespiritai.offline.ocr.ShellStubOcrEngineFactory].
 */
class FakeOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context): OcrEngine =
        FakeOcrEngine(
            cannedText = "本店专治糖尿病,100% 有效",
            cannedConfidence = 0.9f,
        )
}
