package com.icespiritai.offline.ocr

import android.net.Uri
import com.icespiritai.offline.domain.OcrResult

interface OcrEngine {
    suspend fun recognize(uri: Uri): OcrResult
}