package com.icespiritai.offline.ocr

import android.content.Context

class FakeOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context): OcrEngine =
        FakeOcrEngine(
            cannedText = "本店专治糖尿病,100% 有效",
            cannedConfidence = 0.9f,
        )
}
