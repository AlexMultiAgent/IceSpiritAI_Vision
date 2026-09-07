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
 * 8 cases covering TtsController state machine (Task 3 / spec §5.2):
 *   - initial state = Idle when setting enabled
 *   - setEnabled(false) → Disabled
 *   - speak + onDone → Idle
 *   - stop during Speaking → Idle
 *   - speak when Disabled is no-op
 *   - engine init failure → InitFailed
 *   - toggle from Idle starts speaking
 *   - toggle from Speaking stops
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
            engine = fakeEngine,
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
        controller.toggle(reportWith("hello"))
        assertEquals(TtsState.Speaking, controller.state.first())
    }

    @Test fun `toggle from Speaking stops`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.toggle(reportWith("hello"))
        controller.toggle(reportWith("hello"))
        assertEquals(TtsState.Idle, controller.state.first())
        assertEquals(1, fakeEngine.stopCallCount)
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

class FakeTtsEngine : TtsEngine {
    var speakCallCount = 0
    var stopCallCount = 0
    private var pendingOnDone: ((String) -> Unit)? = null
    private var failInitNext = false

    override fun init(onDone: (Boolean) -> Unit) {
        if (failInitNext) {
            failInitNext = false
            onDone(false)
        } else {
            onDone(true)
        }
    }

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        speakCallCount++
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

    override fun supportedChineseEngines(): List<EngineInfo> = emptyList()

    override fun setEngine(pkg: String?) {}

    override fun release() {}

    fun failInit() {
        failInitNext = true
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
