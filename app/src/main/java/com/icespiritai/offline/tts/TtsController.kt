package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Surface of [TtsModelInstaller] that the controller actually uses —
 * extracted to an interface so unit tests can swap a fake without
 * subclassing the installer's real HTTP-fetch machinery.
 */
interface TtsInstallerLike {
    val state: StateFlow<InstallState>
    fun downloadModel()
}

/**
 * Synthetic package name of the local sherpa-onnx engine — used by
 * [TtsSetting.enginePackage] to route speak() to the local engine
 * instead of the system one. Lives here (not in `tts.sherpa`) so the
 * controller doesn't need to import the sherpa subpackage.
 */
const val LOCAL_TTS_PACKAGE = "com.icespiritai.vision.sherpa-onnx"

/**
 * ViewModel 之外的 TTS 控制器,AppGraph 持 process-singleton 引用。
 *
 * 状态机(spec §5.2):
 *   setting.enabled=false → Disabled
 *   engine.init 失败 / 中文 locale 不可用 → InitFailed
 *   其余按当前 speak / stop / onDone 切换 Idle ↔ Speaking
 *
 * 永不静默失败、永远不切到英文朗读(feedback-ad-law-no-gray-area 同源)。
 *
 * Bug 3 pivot (v0.1.60): supports a SECOND engine ([SherpaTtsEngine])
 * alongside the system [AndroidTtsEngine]. The user picks via
 * [setEnginePackage]; routing happens at speak() time. When the
 * local ONNX model is not installed, [sherpaEngine] is null (or its
 * `supportedChineseEngines()` returns empty) and the picker falls back
 * to the system engine list. After [downloadEngine] completes, the
 * engine list refreshes and `LOCAL_PACKAGE` appears as a new option.
 */
class TtsController(
    private val systemEngine: TtsEngine,
    private val sherpaEngine: TtsEngine?,
    private val modelInstaller: TtsInstallerLike?,
    private val settings: TtsSettingRepositoryLike,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _engines = MutableStateFlow<List<EngineInfo>>(emptyList())
    val engines: StateFlow<List<EngineInfo>> = _engines.asStateFlow()

    val setting get() = settings.setting

    private var latestReport: ViolationReport? = null

    /**
     * Latest [TtsSetting] snapshot. Updated by the [init] block's
     * collector so [currentEngine] can read `enginePackage`
     * synchronously without suspending. Without this cache, every
     * `speak()` call would need to `runBlocking` the settings flow —
     * which trips Robolectric's test scheduler and stalls the
     * MainActivity click handler.
     */
    private var latestSetting: TtsSetting = TtsSetting()

    init {
        scope.launch {
            settings.setting.collect { s ->
                latestSetting = s
                if (!s.enabled) _state.value = TtsState.Disabled
                else if (_state.value is TtsState.Disabled) _state.value = TtsState.Idle
            }
        }
        systemEngine.init { ok ->
            _state.value = if (ok) TtsState.Idle else TtsState.InitFailed("引擎初始化失败")
            if (ok) _engines.value = mergedEngines()
        }
    }

    /**
     * Resolve which engine [speak] should dispatch to. null = follow
     * system default; [LOCAL_TTS_PACKAGE] = local engine.
     */
    private fun currentEngine(): TtsEngine {
        val selected = latestSetting.enginePackage
        return if (selected == LOCAL_TTS_PACKAGE && sherpaEngine != null) {
            sherpaEngine
        } else {
            systemEngine
        }
    }

    fun speak(report: ViolationReport) {
        val current = _state.value
        if (current is TtsState.Disabled || current is TtsState.InitFailed) return
        latestReport = report
        val text = ScriptBuilder.build(report)
        currentEngine().speak(text, utteranceId = "report") { _state.value = TtsState.Idle }
        _state.value = TtsState.Speaking
    }

    fun setLatestReport(report: ViolationReport?) {
        latestReport = report
    }

    fun stop() {
        currentEngine().stop()
        _state.value = TtsState.Idle
    }

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
        val list = mergedEngines()
        _engines.value = list
        val chinese = list.any { it.supportsChinese }
        _state.value = if (chinese) TtsState.Idle else TtsState.InitFailed("无可用中文引擎")
    }

    /**
     * Trigger the local-engine model download. UI calls this from the
     * picker empty-state's [TtsEnginePickerScreen.onDownloadEngine]
     * CTA. After the install completes (success or fail), the engines
     * StateFlow refreshes so the picker re-renders.
     */
    fun downloadEngine() {
        val installer = modelInstaller ?: return
        installer.downloadModel()
        // Observe state changes; refresh engine list on terminal states.
        scope.launch {
            installer.state.collect { st ->
                if (st is InstallState.Done || st is InstallState.Failed) {
                    _engines.value = mergedEngines()
                    if (st is InstallState.Done) {
                        // Once installed, switching the user-selected
                        // package to local is implicit — they
                        // initiated the download. Persist so next
                        // cold-start already points at the local
                        // engine.
                        settings.setEnginePackage(LOCAL_TTS_PACKAGE)
                    }
                }
            }
        }
    }

    /**
     * Merge the system engine list + the local engine's synthetic list.
     * Empty if neither is available; the picker renders the empty-state
     * CTA in that case.
     */
    private fun mergedEngines(): List<EngineInfo> {
        val system = systemEngine.supportedChineseEngines()
        val local = sherpaEngine?.supportedChineseEngines() ?: emptyList()
        return (system + local)
    }

    fun currentEngineLabel(currentPackage: String?): String {
        if (currentPackage == null) return "跟随系统默认"
        return mergedEngines().firstOrNull { it.packageName == currentPackage }?.label
            ?: "跟随系统默认"
    }

    fun release() {
        systemEngine.release()
        sherpaEngine?.release()
    }
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
