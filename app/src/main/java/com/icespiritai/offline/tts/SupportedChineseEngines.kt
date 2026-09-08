package com.icespiritai.offline.tts

/**
 * 把 [android.speech.tts.TextToSpeech.getEngines] 的结果按 supportsChinese 过滤。
 * 过滤后的列表喂给 picker UI 让用户选引擎。
 *
 * 独立成纯函数是为了单测可覆盖(AndroidTtsEngine 本身的 init / speak 走真机 androidTest)。
 */
object SupportedChineseEngines {
    fun filter(engines: List<EngineInfo>): List<EngineInfo> =
        engines.filter { it.supportsChinese }
}