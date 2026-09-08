package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel 之外的 TTS 控制器,AppGraph 持 process-singleton 引用。
 *
 * 状态机(spec §5.2):
 *   setting.enabled=false → Disabled
 *   engine.init 失败 / 中文 locale 不可用 → InitFailed
 *   其余按当前 speak / stop / onDone 切换 Idle ↔ Speaking
 *
 * 永不静默失败、永远不切到英文朗读(feedback-ad-law-no-gray-area 同源)。
 */
class TtsController(
    private val engine: TtsEngine,
    private val settings: TtsSettingRepositoryLike,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    val setting get() = settings.setting

    // Latest pushed ViolationReport, read by [toggle] so the top-bar click
    // handler doesn't need to thread it through. Populated by callers that
    // own the OCR→rules pipeline (Task 16 follow-up). null until the first
    // Complete analysis lands.
    private var latestReport: ViolationReport? = null

    init {
        scope.launch {
            settings.setting.collect { s ->
                if (!s.enabled) _state.value = TtsState.Disabled
                else if (_state.value is TtsState.Disabled) _state.value = TtsState.Idle
            }
        }
        engine.init { ok ->
            _state.value = if (ok) TtsState.Idle else TtsState.InitFailed("引擎初始化失败")
        }
    }

    fun speak(report: ViolationReport) {
        val current = _state.value
        if (current is TtsState.Disabled || current is TtsState.InitFailed) return
        latestReport = report
        val text = ScriptBuilder.build(report)
        engine.speak(text, utteranceId = "report") { _state.value = TtsState.Idle }
        _state.value = TtsState.Speaking
    }

    /**
     * Push the latest analyzed [ViolationReport] so [toggle] can speak it
     * without the click handler threading it through. Called from the
     * OCR→rules Complete pipeline (Task 16 follow-up). Clearing with
     * `null` is allowed and lets [toggle] Idle-branch no-op instead of
     * replaying a stale report after a reset.
     */
    fun setLatestReport(report: ViolationReport?) {
        latestReport = report
    }

    fun stop() {
        engine.stop()
        _state.value = TtsState.Idle
    }

    /**
     * Speak/stop toggle on the top-bar 朗读 button. Parameterless so the
     * click handler doesn't need the [ViolationReport] — this method reads
     * it from [latestReport] (set by [speak] or [setLatestReport]).
     *
     * State-machine contract:
     * - [TtsState.Idle]      → speak [latestReport]. No-op when null
     *                          (no analysis has run yet — Task 16 wires the
     *                          push).
     * - [TtsState.Speaking]  → stop.
     * - [TtsState.Disabled]  → user gesture re-enables the feature
     *                          (persist `enabled=true` to DataStore); the
     *                          existing setting collector transitions state
     *                          to Idle once DataStore commits. Engine is
     *                          left untouched because `init` already
     *                          succeeded.
     * - [TtsState.InitFailed] → re-probe supported engines via
     *                          [refreshEngineStatus]. If a chinese-capable
     *                          engine exists now, mark Idle, else stay
     *                          InitFailed. No full engine re-init — that
     *                          would require shutdown + new TextToSpeech
     *                          instance and risks leaking the previous one.
     */
    fun toggle() {
        when (_state.value) {
            is TtsState.Speaking -> stop()
            is TtsState.Disabled -> scope.launch { settings.setEnabled(true) }
            is TtsState.InitFailed -> refreshEngineStatus()
            is TtsState.Idle -> {
                val report = latestReport ?: return
                speak(report)
            }
        }
    }

    suspend fun setEnabled(b: Boolean) = settings.setEnabled(b)
    suspend fun setEnginePackage(pkg: String?) = settings.setEnginePackage(pkg)

    fun refreshEngineStatus() {
        val chinese = engine.supportedChineseEngines().any { it.supportsChinese }
        _state.value = if (chinese) TtsState.Idle else TtsState.InitFailed("无可用中文引擎")
    }

    fun release() = engine.release()
}

/**
 * 给 controller 一份抽象(便于单测 fake,不必启动 DataStore)。
 */
interface TtsSettingRepositoryLike {
    val setting: kotlinx.coroutines.flow.Flow<TtsSetting>
    suspend fun setEnabled(b: Boolean)
    suspend fun setEnginePackage(pkg: String?)
}

class TtsSettingRepositoryAdapter(private val real: TtsSettingRepository) : TtsSettingRepositoryLike {
    override val setting = real.setting
    override suspend fun setEnabled(b: Boolean) = real.setEnabled(b)
    override suspend fun setEnginePackage(pkg: String?) = real.setEnginePackage(pkg)
}
