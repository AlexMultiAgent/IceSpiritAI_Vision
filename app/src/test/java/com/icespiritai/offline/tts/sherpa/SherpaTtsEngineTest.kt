package com.icespiritai.offline.tts.sherpa

import android.content.Context
import com.icespiritai.offline.tts.EngineInfo
import com.icespiritai.offline.tts.EngineStatus
import com.k2fsa.sherpa.onnx.GeneratedAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [SherpaTtsEngine] (Bug 3 pivot v0.1.60).
 *
 * Covers:
 *  - speak() invokes generate via the SynthesizerProvider and onDone after playback
 *  - supportedChineseEngines() returns NeedsDownload status when ONNX model not installed (Bug 4 fix v0.1.61)
 *  - supportedChineseEngines() returns Installed status when model on disk
 *  - speak() is a no-op (no generate, no playback) when model missing
 *  - init() is a no-op and reports ok immediately
 *  - stop() resets the active player reference
 *  - repeated speak reuses cached Synthesizer
 *
 * Robolectric provides the [Context] (which is `final` in modern
 * Android SDKs and cannot be subclassed directly). The engine uses a
 * [FakeSynthesizerProvider] (no JNI) and the `playSamples` test seam
 * routes playback into a [FakePcmAudioPlayer].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SherpaTtsEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(SupervisorJob() + testDispatcher)

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var tempDir: File
    private lateinit var fakeSynth: FakeSynthesizer
    private lateinit var fakePlayer: FakePcmAudioPlayer
    private lateinit var engine: TestableSherpaTtsEngine

    @Before fun setUp() {
        tempDir = createTempDirectory(prefix = "sherpa-engine-test").toFile().apply { deleteOnExit() }
        fakeSynth = FakeSynthesizer()
        fakePlayer = FakePcmAudioPlayer()
        engine = TestableSherpaTtsEngine(
            context = context,
            modelDir = File(tempDir, "model"),
            provider = FakeSynthesizerProvider(fakeSynth),
            player = fakePlayer,
            scope = testScope,
        )
    }

    @After fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test fun `speak initializes Synthesizer lazily and plays via PcmAudioPlayer`() = runTest(testDispatcher) {
        // Model missing initially → supportedChineseEngines reports NeedsDownload (Bug 4 fix v0.1.61: local
        // engine is ALWAYS in the picker list so the user can see it and trigger the download).
        val preInstall = engine.supportedChineseEngines()
        assertEquals(1, preInstall.size)
        assertEquals(EngineStatus.NeedsDownload, preInstall[0].status)

        // v0.1.65 hardening: plant ALL 16 required files (2 ONNX + 5
        // text/rule + 9 espeak) — partial install routes speak() into
        // the early-return + onDone branch (Bug 7c root cause).
        plantFullModel()

        fakeSynth.nextSamples = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        fakeSynth.nextSampleRate = 22050

        var doneCalled = false
        engine.speak("你好", "u1") { doneCalled = true }
        advanceUntilIdle()

        assertEquals(1, fakeSynth.generateCallCount)
        assertEquals("你好", fakeSynth.lastText)
        assertEquals(1, fakePlayer.playCallCount)
        assertEquals(22050, fakePlayer.lastSampleRate)
        assertEquals(4, fakePlayer.lastSamples.size)
        assertTrue("onDone not invoked", doneCalled)
    }

    @Test fun `speak invokes onDone even when model not installed`() = runTest(testDispatcher) {
        // Don't create the ONNX files — model is "missing".
        var doneCalled = false
        engine.speak("hi", "u1") { doneCalled = true }
        advanceUntilIdle()

        assertEquals(0, fakeSynth.generateCallCount)
        assertEquals(0, fakePlayer.playCallCount)
        assertTrue("onDone must still fire even on early-return", doneCalled)
    }

    @Test fun `speak gracefully skips when espeak-ng-data missing`() = runTest(testDispatcher) {
        // v0.1.65 hardening: pre-fix this planted only 2 ONNX files
        // and called .speak() — the engine would have walked into the
        // JNI null deref at OfflineTts_generateImpl+268. Now ANY
        // missing espeak file must short-circuit speak() to the
        // early-return + onDone path without touching the synthesizer.
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }
        // text/rule resources present, but espeak-ng-data is empty.
        for (name in SherpaTtsEngine.REQUIRED_MODEL_FILES.drop(2)) {
            File(engine.modelDir, name).writeBytes(ByteArray(10))
        }
        File(engine.modelDir, com.icespiritai.offline.tts.TtsModelInstaller.ESPEAK_DATA_DIR).mkdirs()

        fakeSynth.nextSamples = floatArrayOf(0.1f, 0.2f, 0.3f)
        fakeSynth.nextSampleRate = 22050

        var doneCalled = false
        engine.speak("测试", "u1") { doneCalled = true }
        advanceUntilIdle()

        assertEquals("synthesizer must not be created when espeak files missing",
            0, fakeSynth.createdCount)
        assertEquals("generate must not be called", 0, fakeSynth.generateCallCount)
        assertEquals("playback must not happen", 0, fakePlayer.playCallCount)
        assertTrue("onDone must still fire on early-return", doneCalled)
    }

    @Test fun `supportedChineseEngines returns NeedsDownload status when model not installed`() {
        // Bug 4 fix v0.1.61: local engine is always listed so the user
        // sees the row + status chip and can tap to download. The engine
        // itself only knows Installed vs NeedsDownload; transient states
        // (Downloading / DownloadFailed) are layered by TtsController.
        val list = engine.supportedChineseEngines()
        assertEquals(1, list.size)
        val info: EngineInfo = list[0]
        assertEquals(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE, info.packageName)
        assertEquals(SherpaTtsEngine.LOCAL_LABEL, info.label)
        assertTrue(info.supportsChinese)
        assertEquals(EngineStatus.NeedsDownload, info.status)
    }

    @Test fun `supportedChineseEngines returns Installed status when model installed`() {
        plantFullModel()

        val list = engine.supportedChineseEngines()
        assertEquals(1, list.size)
        val info: EngineInfo = list[0]
        assertEquals(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE, info.packageName)
        assertEquals(SherpaTtsEngine.LOCAL_LABEL, info.label)
        assertTrue(info.supportsChinese)
        assertEquals(EngineStatus.Installed, info.status)
    }

    @Test fun `supportedChineseEngines returns NeedsDownload when espeak-ng-data missing`() {
        // v0.1.65 hardening: picker's Installed badge must reflect the
        // SAME integrity check speak() uses, otherwise the user sees
        // Installed but the engine silently no-ops every playback.
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }
        for (name in SherpaTtsEngine.REQUIRED_MODEL_FILES.drop(2)) {
            File(engine.modelDir, name).writeBytes(ByteArray(10))
        }
        // Only plant 1 of 9 espeak files — should still report NeedsDownload.
        val espeakDir = File(engine.modelDir, com.icespiritai.offline.tts.TtsModelInstaller.ESPEAK_DATA_DIR).apply { mkdirs() }
        File(espeakDir, "phontab").writeBytes(ByteArray(10))

        val info = engine.supportedChineseEngines().single()
        assertEquals(EngineStatus.NeedsDownload, info.status)
    }

    @Test fun `speak plays the samples returned by Synthesizer generate`() = runTest(testDispatcher) {
        plantFullModel()

        val expected = floatArrayOf(-0.5f, 0f, 0.5f, 1f, -1f, 0.25f, -0.25f)
        fakeSynth.nextSamples = expected
        fakeSynth.nextSampleRate = 44100

        engine.speak("测试", "u2") {}
        advanceUntilIdle()

        assertEquals(7, fakePlayer.lastSamples.size)
        assertEquals(44100, fakePlayer.lastSampleRate)
    }

    @Test fun `stop cancels playback`() = runTest(testDispatcher) {
        plantFullModel()

        fakeSynth.nextSamples = floatArrayOf(0.1f, 0.2f, 0.3f)
        fakeSynth.nextSampleRate = 22050

        engine.speak("x", "u1") {}
        advanceUntilIdle()
        // The engine's activePlayer is a real PcmAudioPlayer (we
        // override playSamples to redirect samples, but the player
        // ref itself is the real one). Calling stop() should not
        // throw and should leave the engine in a state where
        // isSpeaking() reports false after the next speak cycle.
        engine.stop()
        assertFalse(engine.isSpeaking())
    }

    @Test fun `engine init is no-op and reports ok immediately`() = runTest(testDispatcher) {
        var initOk = false
        engine.init { ok -> initOk = ok }
        assertTrue(initOk)
        // Bug 4 fix v0.1.61: local engine is always listed (with NeedsDownload when model missing).
        assertTrue(engine.supportedChineseEngines().any { it.packageName == com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE })
    }

    @Test fun `repeated speak reuses cached Synthesizer`() = runTest(testDispatcher) {
        plantFullModel()
        fakeSynth.nextSamples = floatArrayOf(0.1f, 0.2f, 0.3f)
        fakeSynth.nextSampleRate = 22050

        engine.speak("first", "u1") {}
        advanceUntilIdle()
        engine.speak("second", "u2") {}
        advanceUntilIdle()

        // Synthesizer is built lazily on first speak; second speak reuses.
        assertEquals(1, fakeSynth.createdCount)
        assertEquals(2, fakeSynth.generateCallCount)
    }

    /**
     * Plant ALL 16 required files (2 ONNX + 5 text/rule + 9 espeak) so
     * isModelInstalled() returns true. v0.1.65 hardening: pre-fix only
     * the 2 ONNX files were planted, which silently passed partial
     * installs into the native null deref path.
     * parentFile?.mkdirs() is required for nested espeak paths like
     * `lang/sit/cmn` whose parent dirs don't exist yet.
     */
    private fun plantFullModel() {
        engine.modelDir.mkdirs()
        for (name in SherpaTtsEngine.REQUIRED_MODEL_FILES) {
            File(engine.modelDir, name).writeBytes(ByteArray(10))
        }
        val espeakDir = File(engine.modelDir, com.icespiritai.offline.tts.TtsModelInstaller.ESPEAK_DATA_DIR).apply { mkdirs() }
        for (name in com.icespiritai.offline.tts.TtsModelInstaller.BUNDLED_ESPEAK_FILES) {
            val f = File(espeakDir, name)
            f.parentFile?.mkdirs()
            f.writeBytes(ByteArray(10))
        }
    }

    // ----- Test doubles -----

    /**
     * Subclass that routes playback into the fake player.
     */
    private class TestableSherpaTtsEngine(
        context: Context,
        val modelDir: File,
        provider: SherpaTtsEngine.SynthesizerProvider,
        private val player: FakePcmAudioPlayer,
        scope: kotlinx.coroutines.CoroutineScope,
    ) : SherpaTtsEngine(context, modelDir, provider, scope) {
        override suspend fun playSamples(
            player: PcmAudioPlayer,
            samples: FloatArray,
            sampleRate: Int,
        ) {
            this.player.play(samples, sampleRate)
        }
    }

    private class FakeSynthesizerProvider(
        private val synth: FakeSynthesizer,
    ) : SherpaTtsEngine.SynthesizerProvider {
        override fun create(modelDir: File): SherpaTtsEngine.Synthesizer {
            synth.createdCount++
            return synth
        }
    }

    private class FakeSynthesizer : SherpaTtsEngine.Synthesizer {
        var createdCount = 0
        var generateCallCount = 0
        var lastText: String? = null
        var nextSamples: FloatArray = FloatArray(0)
        var nextSampleRate: Int = 22050

        override fun generate(text: String, sid: Int, speed: Float): GeneratedAudio {
            generateCallCount++
            lastText = text
            return GeneratedAudio(samples = nextSamples, sampleRate = nextSampleRate)
        }
    }

    /**
     * Stub for PcmAudioPlayer — records call counts so the routing
     * contract is testable without an AudioTrack.
     */
    private class FakePcmAudioPlayer {
        var playCallCount = 0
        var stopCallCount = 0
        var lastSamples: FloatArray = FloatArray(0)
        var lastSampleRate: Int = 0

        suspend fun play(samples: FloatArray, sampleRate: Int) {
            playCallCount++
            lastSamples = samples
            lastSampleRate = sampleRate
        }

        fun stop() { stopCallCount++ }
    }
}
