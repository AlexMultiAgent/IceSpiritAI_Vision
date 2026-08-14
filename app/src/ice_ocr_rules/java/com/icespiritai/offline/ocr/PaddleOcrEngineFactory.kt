package com.icespiritai.offline.ocr

import android.content.Context

class PaddleOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context): OcrEngine = PaddleOcrEngine(context)
}
