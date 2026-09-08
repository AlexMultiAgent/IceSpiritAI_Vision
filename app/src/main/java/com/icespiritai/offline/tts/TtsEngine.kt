package com.icespiritai.offline.tts

/**
 * Installation status for an [EngineInfo] entry — only meaningful for
 * the local sherpa-onnx engine. System engines are always
 * [EngineStatus.Installed].
 *
 * Bug 4 fix (v0.1.61): the picker used to hide the local engine until
 * its ONNX model was downloaded, so users only ever saw the empty-state
 * "未找到中文 TTS 引擎" + the 150 MB download button — they didn't know
 * an on-device option existed. The local engine now always appears in
 * the list (the picker renders it with a status badge that shows
 * download state), so the path is "see option → tap → install → use".
 */
enum class EngineStatus {
    /** Ready to use. */
    Installed,

    /** Engine is bundled but ONNX model files are not yet on disk. */
    NeedsDownload,

    /** Download in flight. */
    Downloading,

    /** Download completed but the install failed. */
    DownloadFailed,
}

/** 设备上某个 TTS 引擎的元信息,用于 picker 列表显示。 */
data class EngineInfo(
    val packageName: String,
    val label: String,
    val supportsChinese: Boolean,
    val status: EngineStatus = EngineStatus.Installed,
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
