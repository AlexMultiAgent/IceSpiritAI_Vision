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
    /**
     * Fallback engine-APK installer. Optional so unit tests (and any
     * future headless caller) can construct the controller without an
     * Android [android.content.Context]. When null, [downloadEngine] is a
     * no-op.
     *
     * Bug 3 fix (v0.1.60): [TtsEngineInstaller] was never instantiated
     * anywhere under `app/src/main/`, so the picker's 「下载引擎」 button
     * had nothing to call even after Bug 2 made the empty state reachable.
     */
    private val installer: TtsEngineInstaller? = null,
) {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    /**
     * List of chinese-capable TTS engines currently discoverable on the
     * device. Populated after [engine.init] completes (so the probe has
     * access to the package manager) and re-populated by
     * [refreshEngineStatus] (which fires when the user toggles the TTS
     * feature back on from [TtsState.InitFailed] — engine installs may
     * have happened in the meantime).
     *
     * Bug 2 fix (v0.1.61): previously the picker UI had no way to receive
     * the engine list — [TtsEnginePickerScreen] always rendered the
     * EmptyTtsState branch because the NavHost threaded a hard-coded
     * `engines = emptyList()` to it. Surfacing the list as a [StateFlow]
     * lets the Activity collect it once and pass the snapshot down.
     */
    private val _engines = MutableStateFlow<List<EngineInfo>>(emptyList())
    val engines: StateFlow<List<EngineInfo>> = _engines.asStateFlow()

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
            // Probe after init so getEngines() reflects the package manager
            // state visible to the running engine.
            if (ok) _engines.value = engine.supportedChineseEngines()
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

    /**
     * Fallback engine download progress, surfaced so the picker can render
     * 「下载冰灵 TTS 引擎… N%」 instead of a dead button. Constant
     * [InstallState.Idle] when no [installer] was injected.
     */
    val installState: StateFlow<InstallState> =
        installer?.state ?: MutableStateFlow<InstallState>(InstallState.Idle).asStateFlow()

    /**
     * Kick off engine APK download + system install. Routed from the
     * picker's empty-state 「下载引擎」 button (NavHost `onDownloadEngine`).
     * No-op when no [installer] was injected.
     *
     * [TtsEngineInstaller.install] is mutex-guarded, so a double tap while a
     * download is in flight returns the current state rather than starting a
     * second stream; the button is additionally disabled while
     * [installState] is [InstallState.Downloading].
     */
    fun downloadEngine() {
        val installer = installer ?: return
        scope.launch {
            // Failures land in `installer.state` as InstallState.Failed
            // (rendered by the picker). Catch defensively so an unexpected
            // throw can't cancel `scope` — it is the Activity-lifetime
            // SupervisorJob scope shared with the settings collector.
            runCatching { installer.install() }
        }
    }

    suspend fun setEnabled(b: Boolean) = settings.setEnabled(b)
    suspend fun setEnginePackage(pkg: String?) = settings.setEnginePackage(pkg)

    fun refreshEngineStatus() {
        val list = engine.supportedChineseEngines()
        _engines.value = list
        val chinese = list.any { it.supportsChinese }
        _state.value = if (chinese) TtsState.Idle else TtsState.InitFailed("无可用中文引擎")
    }

    /**
     * Display label for the engine the user is currently routed to, used
     * by the Settings "引擎" row (Bug 1 fix / v0.1.60). Resolution order:
     *  - [currentPackage] == null  → "跟随系统默认" (no engine pinned)
     *  - lookup in [engine.supportedChineseEngines]  → matching [EngineInfo.label]
     *  - fallback "跟随系统默认" if init hasn't run yet (engine list empty)
     *    or the pinned package was uninstalled
     *
     * Bug 2 (v0.1.61) will close the reactive loop: the Settings row's
     * `currentEngineLabel` does NOT currently re-collect when the user
     * picks a different engine in the picker — see CLAUDE.md out-of-scope
     * follow-up notes. This method is a *snapshot* by design.
     */
    fun currentEngineLabel(currentPackage: String?): String {
        if (currentPackage == null) return "跟随系统默认"
        return engine.supportedChineseEngines()
            .firstOrNull { it.packageName == currentPackage }
            ?.label
            ?: "跟随系统默认"
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
