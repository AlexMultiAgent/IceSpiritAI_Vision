package com.icespiritai.offline.tts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal 注入 [TtsController]。缺失时报清晰错误,避免 UI 误用。
 * 提供方在 [com.icespiritai.offline.IceSpiritVisionActivity.onCreate]。
 */
val LocalTtsController = staticCompositionLocalOf<TtsController> {
    error("TtsController not provided. Wrap your content in IceSpiritOfflineTheme.")
}
