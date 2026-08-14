package com.icespiritai.offline.ocr

import android.content.Context
import java.util.ServiceLoader

object OcrEngineFactoryLocator {
    fun create(context: Context): OcrEngine =
        ServiceLoader.load(OcrEngineFactory::class.java)
            .firstOrNull()
            ?.create(context)
            ?: error(
                "No OcrEngineFactory on classpath. Check " +
                    "src/{shell,ice_ocr_rules}/resources/META-INF/services/."
            )
}
