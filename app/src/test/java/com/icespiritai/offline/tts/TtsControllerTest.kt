package com.icespiritai.offline.tts

import android.net.StubUri
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * State machine coverage for [TtsController] (Task 3 / spec §5.2):
 *   - initial state = Idle when setting enabled
 *   - setEnabled(false) → Disabled
 *   - speak + onDone → Idle
 *   - stop during Speaking → Idle
 *   - speak when Disabled is no-op
 *   - engine init failure → InitFailed
 *   - toggle from Idle starts speaking
 *   - toggle from Speaking stops
 *   - (Bug 3 pivot v0.1.60) setEnginePackage to LOCAL routes speak to sherpa engine
 *   - (Bug 3 pivot v0.1.60) downloadEngine() triggers model install + refresh
 *
 * Pure JVM: standard test dispatcher + FakeTtsSettingRepository backed by a
 * MutableStateFlow (no Robolectric / no DataStore).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TtsControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private lateinit var fakeEngine: FakeTtsEngine
    private lateinit var fakeSettings: FakeTtsSettingRepository
    private lateinit var controller: TtsController

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEngine = FakeTtsEngine()
        fakeSettings = FakeTtsSettingRepository()
        controller = TtsController(
            systemEngine = fakeEngine,
            sherpaEngine = null,
            modelInstaller = null,
            settings = fakeSettings,
            scope = testScope,
        )
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `initial state is Idle when setting enabled`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        assertEquals(TtsState.Idle, controller.state.first())
    }

    @Test fun `setting enabled false transitions to Disabled`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.setEnabled(false)
        advanceUntilIdle()
        assertEquals(TtsState.Disabled, controller.state.first())
    }

    @Test fun `speak transitions Idle to Speaking then back to Idle on done`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        val report = reportWith("100% 中国第一")
        controller.speak(report)
        assertEquals(TtsState.Speaking, controller.state.first())
        fakeEngine.completeLastUtterance()
        assertEquals(TtsState.Idle, controller.state.first())
    }

    @Test fun `stop during Speaking returns to Idle`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.speak(reportWith("x"))
        controller.stop()
        assertEquals(TtsState.Idle, controller.state.first())
        assertEquals(1, fakeEngine.stopCallCount)
    }

    @Test fun `speak when Disabled is no-op`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = false))
        advanceUntilIdle()
        controller.speak(reportWith("x"))
        assertEquals(0, fakeEngine.speakCallCount)
        assertEquals(TtsState.Disabled, controller.state.first())
    }

    @Test fun `engine init failure transitions to InitFailed`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        fakeEngine.failInit()
        controller.refreshEngineStatus()
        assertTrue(controller.state.first() is TtsState.InitFailed)
    }

    @Test fun `toggle from Idle starts speaking`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        // toggle() is parameterless in production; the controller reads
        // its report from `latestReport` (set via setLatestReport or speak()).
        controller.setLatestReport(reportWith("hello"))
        controller.toggle()
        assertEquals(TtsState.Speaking, controller.state.first())
    }

    @Test fun `toggle from Speaking stops`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.setLatestReport(reportWith("hello"))
        controller.toggle()
        controller.toggle()
        assertEquals(TtsState.Idle, controller.state.first())
        assertEquals(1, fakeEngine.stopCallCount)
    }

    // --- Bug 3 pivot: multi-engine routing + installer wiring ---

    @Test fun `setEnginePackage to LOCAL_PACKAGE routes speak to sherpa engine`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        val sherpaEngine = FakeTtsEngine()
        val ctrl = TtsController(
            systemEngine = fakeEngine,
            sherpaEngine = sherpaEngine,
            modelInstaller = null,
            settings = fakeSettings,
            scope = testScope,
        )
        // Simulate user picking the local engine in the picker.
        ctrl.setEnginePackage(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE)
        advanceUntilIdle()
        ctrl.speak(reportWith("你好"))
        assertEquals(TtsState.Speaking, ctrl.state.first())
        assertEquals(1, sherpaEngine.speakCallCount)
        assertEquals(0, fakeEngine.speakCallCount)  // system engine NOT called
        sherpaEngine.completeLastUtterance()
    }

    @Test fun `setEnginePackage to null routes speak back to system engine`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        val sherpaEngine = FakeTtsEngine()
        val ctrl = TtsController(
            systemEngine = fakeEngine,
            sherpaEngine = sherpaEngine,
            modelInstaller = null,
            settings = fakeSettings,
            scope = testScope,
        )
        ctrl.setEnginePackage(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE)
        advanceUntilIdle()
        // Toggle back to system default.
        ctrl.setEnginePackage(null)
        advanceUntilIdle()
        ctrl.speak(reportWith("hi"))
        assertEquals(1, fakeEngine.speakCallCount)
        assertEquals(0, sherpaEngine.speakCallCount)
    }

    @Test fun `downloadEngine calls modelInstaller and refreshes engines list`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        val installer = FakeTtsModelInstaller()
        val sherpaEngine = FakeTtsEngine(
            enginesAfterInstall = listOf(
                EngineInfo(
                    packageName = com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE,
                    label = com.icespiritai.offline.tts.sherpa.SherpaTtsEngine.LOCAL_LABEL,
                    supportsChinese = true,
                ),
            ),
        )
        val ctrl = TtsController(
            systemEngine = fakeEngine,
            sherpaEngine = sherpaEngine,
            modelInstaller = installer,
            settings = fakeSettings,
            scope = testScope,
        )
        ctrl.downloadEngine()
        advanceUntilIdle()
        assertEquals(1, installer.downloadModelCallCount)
        // After Done, engines list should include the local engine.
        assertTrue(ctrl.engines.value.any { it.packageName == com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE })
    }

    @Test fun `speak with no sherpaEngine falls back to system engine`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        // controller from setUp() has sherpaEngine = null
        ctrl_pickLocalPackage()
        controller.speak(reportWith("hi"))
        assertEquals(1, fakeEngine.speakCallCount)
    }

    private suspend fun ctrl_pickLocalPackage() {
        controller.setEnginePackage(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE)
    }

    // --- helpers ---

    private fun reportWith(text: String): ViolationReport = ViolationReport(
        imageUri = StubUri(),
        ocrText = text,
        hits = listOf(
            RuleHit(
                ruleId = "r",
                matchedText = text,
                category = "absolute",
                regulation = "广告法 §9",
                severity = Severity.Violation,
            ),
        ),
        timestampMs = 0,
    )
}

class FakeTtsEngine(
    private val enginesAfterInstall: List<EngineInfo> = emptyList(),
) : TtsEngine {
    var speakCallCount = 0
    var stopCallCount = 0
    var initCallCount = 0
    var lastSpokenText: String? = null
    private var pendingOnDone: ((String) -> Unit)? = null
    private var failInitNext = false

    override fun init(onDone: (Boolean) -> Unit) {
        initCallCount++
        if (failInitNext) {
            failInitNext = false
            onDone(false)
        } else {
            onDone(true)
        }
    }

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        speakCallCount++
        lastSpokenText = text
        pendingOnDone = onDone
    }

    fun completeLastUtterance() {
        val cb = pendingOnDone ?: error("no pending utterance")
        pendingOnDone = null
        cb("last")
    }

    override fun stop() {
        stopCallCount++
    }

    override fun isSpeaking(): Boolean = pendingOnDone != null

    override fun supportedChineseEngines(): List<EngineInfo> = enginesAfterInstall

    override fun setEngine(pkg: String?) {}

    override fun release() {}

    fun failInit() {
        failInitNext = true
    }
}

/**
 * Lightweight stand-in for [TtsModelInstaller] in controller tests.
 * Implements [TtsInstallerLike] (the controller's surface) directly —
 * the installer's real download machinery isn't exercised here.
 */
class FakeTtsModelInstaller : TtsInstallerLike {
    private val state_ = kotlinx.coroutines.flow.MutableStateFlow<InstallState>(InstallState.Idle)
    override val state: kotlinx.coroutines.flow.StateFlow<InstallState> = state_
    var downloadModelCallCount = 0

    override fun downloadModel() {
        downloadModelCallCount++
        state_.value = InstallState.Done
    }
}

class FakeTtsSettingRepository : TtsSettingRepositoryLike {
    private val flow = MutableStateFlow(TtsSetting())
    override val setting: Flow<TtsSetting> = flow
    override suspend fun setEnabled(b: Boolean) {
        flow.value = flow.value.copy(enabled = b)
    }

    override suspend fun setEnginePackage(pkg: String?) {
        flow.value = flow.value.copy(enginePackage = pkg)
    }

    suspend fun emit(s: TtsSetting) {
        flow.value = s
    }
}
