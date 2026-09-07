package com.icespiritai.offline.tts

/** 设备上某个 TTS 引擎的元信息,用于 picker 列表显示。 */
data class EngineInfo(
    val packageName: String,
    val label: String,
    val supportsChinese: Boolean,
)

/**
 * TTS 引擎抽象。生产实现包 [android.speech.tts.TextToSpeech],测试用 fake。
 *
 * - [init] 在 Activity onCreate 触发,onDone(true)=Success / onDone(false)=InitFailed
 * - [speak] 异步,通过 onDone 回调通知朗读结束(utteranceId 用于 stop / 多段管理)
 * - [setEngine] pkg=null 表示跟随系统首选,非 null 表示强制用某 package
 */
interface TtsEngine {
    fun init(onDone: (Boolean) -> Unit)
    fun speak(text: String, utteranceId: String, onDone: (String) -> Unit)
    fun stop()
    fun isSpeaking(): Boolean
    fun supportedChineseEngines(): List<EngineInfo>
    fun setEngine(pkg: String?)
    fun release()
}
