package com.icespiritai.offline.tts

/**
 * TTS 控制器状态机。优先级(spec §5.2):
 *   setting.enabled=false > InitFailed > state==Complete > Idle > Speaking
 *
 * - [Idle]     — 引擎 OK,setting 开,可朗读但当前没在说
 * - [Speaking] — 正在朗读
 * - [Disabled] — setting.enabled=false,所有 speak 静默 no-op
 * - [InitFailed] — 引擎 init 失败 / 中文 locale 不可用,不降级不朗读,引导用户去 Settings
 */
sealed interface TtsState {
    object Idle : TtsState
    object Speaking : TtsState
    object Disabled : TtsState
    data class InitFailed(val reason: String) : TtsState
}
