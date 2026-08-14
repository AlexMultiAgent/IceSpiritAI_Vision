package com.icespiritai.offline.ocr

import android.content.Context

/**
 * Service-loader-discovered factory for [OcrEngine]. Each modelProfile sourceSet
 * (shell, ice_ocr_rules, ...) provides exactly one implementation via
 * `META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`. The first
 * service found by [OcrEngineFactoryLocator] wins.
 */
interface OcrEngineFactory {
    fun create(context: Context): OcrEngine
}
