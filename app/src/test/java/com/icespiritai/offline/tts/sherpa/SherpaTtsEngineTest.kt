package com.icespiritai.offline.tts.sherpa

import android.content.Context
import com.icespiritai.offline.tts.EngineInfo
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
 *  - supportedChineseEngines() returns empty when ONNX model not installed
 *  - supportedChineseEngines() returns the local engine label when installed
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
        // Model missing initially → supportedChineseEngines empty
        assertTrue(engine.supportedChineseEngines().isEmpty())

        // Populate the ONNX model files (only existence matters for isModelInstalled)
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }

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

    @Test fun `supportedChineseEngines empty when model not installed`() {
        assertTrue(engine.supportedChineseEngines().isEmpty())
    }

    @Test fun `supportedChineseEngines returns local engine when model installed`() {
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }

        val list = engine.supportedChineseEngines()
        assertEquals(1, list.size)
        val info: EngineInfo = list[0]
        assertEquals(com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE, info.packageName)
        assertEquals(SherpaTtsEngine.LOCAL_LABEL, info.label)
        assertTrue(info.supportsChinese)
    }

    @Test fun `speak plays the samples returned by Synthesizer generate`() = runTest(testDispatcher) {
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }

        val expected = floatArrayOf(-0.5f, 0f, 0.5f, 1f, -1f, 0.25f, -0.25f)
        fakeSynth.nextSamples = expected
        fakeSynth.nextSampleRate = 44100

        engine.speak("测试", "u2") {}
        advanceUntilIdle()

        assertEquals(7, fakePlayer.lastSamples.size)
        assertEquals(44100, fakePlayer.lastSampleRate)
    }

    @Test fun `stop cancels playback`() = runTest(testDispatcher) {
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }

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
        // Model not installed yet — local engine should NOT appear.
        assertFalse(engine.supportedChineseEngines().any { it.packageName == com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE })
    }

    @Test fun `repeated speak reuses cached Synthesizer`() = runTest(testDispatcher) {
        File(engine.modelDir, "model-steps-3.onnx").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        File(engine.modelDir, "vocos-22khz-univ.onnx").apply { writeBytes(ByteArray(10)) }
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
