package com.icespiritai.offline.tts.sherpa

import android.content.Context
import android.util.Log
import com.icespiritai.offline.tts.EngineInfo
import com.icespiritai.offline.tts.EngineStatus
import com.icespiritai.offline.tts.LOCAL_TTS_PACKAGE
import com.icespiritai.offline.tts.TtsEngine
import com.icespiritai.offline.tts.TtsModelInstaller
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Local Chinese TTS engine backed by sherpa-onnx (Matcha acoustic +
 * Vocos vocoder). 1:1 port of translate's
 * `com.icespiritai.offline.ttsengine.SherpaTtsEngine` adapted for
 * vision's [TtsEngine] callback interface (vision uses
 * `speak(text, utteranceId, onDone)`, translate uses `synthesize()` →
 * `SynthesisResult`).
 *
 * Asset layout — model installed under `filesDir/offline-models/zh/`
 * (hybrid: ONNX downloaded, text/rule resources bundled — Bug 7b fix v0.1.63):
 * - `model-steps-3.onnx` (acoustic, ~75 MB) — downloaded from Gitea
 *   `giteaadmin/Model` release `sherpa-onnx-matcha-zh-baker`
 * - `vocos-22khz-univ.onnx` (vocoder, ~54 MB) — same source
 * - text/rule resources (tokens.txt / lexicon.txt / phone.fst /
 *   date.fst / number.fst, ~1.6 MB) — bundled in the APK at
 *   `assets/models/tts/zh/` and copied to the modelDir by
 *   [com.icespiritai.offline.tts.TtsModelInstaller.copyBundledAssets]
 *   at first install. All 5 must be present alongside the ONNX files
 *   or `OfflineTts` config Validate fails with
 *   `Rule fst '<path>' does not exist` and `generate()` segfaults.
 *
 * Lifecycle:
 * - `init()` is a no-op (no async native init). OfflineTts is built
 *   lazily on first `speak()` so cold-start cost (~5 s on nova 6) does
 *   not block the Activity.
 * - `speak()` runs OfflineTts.generate() + PcmAudioPlayer.play() on an
 *   internal IO scope, then invokes the `onDone(utteranceId)` callback.
 * - `stop()` cancels the AudioTrack (in-flight generation still
 *   completes since OfflineTts is synchronous, but the playback is
 *   skipped and `onDone` fires after release).
 * - `release()` closes the cached OfflineTts.
 *
 * Test seam: [OfflineTts] is `final` in sherpa-onnx v1.13.5 (verified
 * via javap on the runtime jar), so direct subclassing is impossible.
 * The [synthesizerProvider] field lets tests substitute a fake
 * synthesizer that returns canned [GeneratedAudio] without touching
 * JNI. Production wiring supplies [DefaultSynthesizerProvider].
 */
open class SherpaTtsEngine(
    private val context: Context,
    private val modelDir: File,
    private val synthesizerProvider: SynthesizerProvider = DefaultSynthesizerProvider,
    internalScope: CoroutineScope? = null,
) : TtsEngine {

    private val lifecycleMutex = Mutex()
    @Volatile
    private var cachedSynth: Synthesizer? = null
    @Volatile
    private var closed = false
    @Volatile
    private var activePlayer: PcmAudioPlayer? = null

    // Internal IO scope — separate from the controller's appScope so a
    // controller cancel does not interrupt a mid-sentence playback.
    // Tests inject a TestScope (or `runTest`'s scope) so the launched
    // coroutine runs on the test dispatcher and `advanceUntilIdle`
    // actually drives it to completion.
    private val internalScope: CoroutineScope =
        internalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(onDone: (Boolean) -> Unit) {
        // No native init; OfflineTts is lazy on first speak.
        onDone(true)
    }

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        if (closed) {
            onDone(utteranceId)
            return
        }
        if (!isModelInstalled()) {
            Log.w(TAG, "speak(): ONNX model not installed at $modelDir; skipping")
            onDone(utteranceId)
            return
        }
        internalScope.launch {
            try {
                lifecycleMutex.withLock {
                    check(!closed) { "SherpaTtsEngine has been closed" }
                    val synth = cachedSynth ?: synthesizerProvider.create(modelDir).also {
                        cachedSynth = it
                    }
                    val generated = synth.generate(text, sid = 0, speed = 1.0f)
                    check(generated.samples.isNotEmpty()) {
                        "sherpa-onnx returned empty samples for text length=${text.length}"
                    }
                    val player = createPcmAudioPlayer()
                    activePlayer = player
                    try {
                        playSamples(player, generated.samples, generated.sampleRate)
                    } finally {
                        if (activePlayer === player) activePlayer = null
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "speak() failed: ${e.message}", e)
                // Drop cached synthesizer on error — next speak() rebuilds.
                runCatching { cachedSynth?.release() }
                cachedSynth = null
            } finally {
                onDone(utteranceId)
            }
        }
    }

    override fun stop() {
        // Cancel in-flight playback. Generation itself can't be cancelled
        // (OfflineTts.generate is synchronous); if it finishes after stop,
        // the player.play() will see activePlayer == null and skip.
        activePlayer?.stop()
    }

    override fun isSpeaking(): Boolean = activePlayer != null

    /**
     * Bug 4 fix (v0.1.61): the local engine is ALWAYS present in the
     * picker list so the user can see and select it; the [EngineStatus]
     * field tells the UI whether the ONNX model is on disk. The
     * `Downloading` / `DownloadFailed` statuses are layered on top by
     * [TtsController.mergedEngines] via the installer's state flow —
     * the engine itself only knows Installed vs NeedsDownload.
     */
    override fun supportedChineseEngines(): List<EngineInfo> = listOf(
        EngineInfo(
            packageName = LOCAL_TTS_PACKAGE,
            label = LOCAL_LABEL,
            supportsChinese = true,
            status = if (isModelInstalled()) EngineStatus.Installed
            else EngineStatus.NeedsDownload,
        ),
    )

    override fun setEngine(pkg: String?) {
        // Synthetic engine — no real switching (always routes to this
        // instance if pkg == LOCAL_PACKAGE). The controller handles
        // routing based on `selectedPackage`; this method exists only to
        // satisfy the TtsEngine interface.
    }

    override fun release() {
        closed = true
        activePlayer?.stop()
        internalScope.launch {
            lifecycleMutex.withLock {
                val toRelease = cachedSynth
                cachedSynth = null
                if (toRelease != null) {
                    runCatching { toRelease.release() }
                }
            }
        }
    }

    /**
     * True iff ALL files OfflineTts needs to generate without segfaulting
     * are present on disk: 2 ONNX + 5 text/rule resources + 9
     * espeak-ng-data files. See [com.icespiritai.offline.tts.TtsModelInstaller.isModelInstalled]
     * for the rationale — this is the same set, kept in lockstep so the
     * picker's Installed / NeedsDownload badge matches what speak()
     * would actually attempt at runtime.
     *
     * v0.1.65 hardening: pre-fix this only checked the 2 ONNX files,
     * which silently passed through partial installs where espeak-ng-data
     * was missing → `OfflineTts_generateImpl+268` null deref on first
     * playback (Bug 7c root cause). Now any missing file routes speak()
     * into the early-return + onDone branch instead of JNI.
     */
    private fun isModelInstalled(): Boolean {
        for (name in REQUIRED_MODEL_FILES) {
            if (!File(modelDir, name).isFile) return false
        }
        val espeakDir = File(modelDir, TtsModelInstaller.ESPEAK_DATA_DIR)
        for (name in TtsModelInstaller.BUNDLED_ESPEAK_FILES) {
            if (!File(espeakDir, name).isFile) return false
        }
        return true
    }

    /**
     * Production wires a real [PcmAudioPlayer] backed by AudioTrack;
     * tests override this method to inject a fake player. Marked
     * `protected open` so test subclasses can swap; callers outside
     * the class hierarchy must use [PcmAudioPlayer] directly.
     */
    protected open fun createPcmAudioPlayer(): PcmAudioPlayer = PcmAudioPlayer(context)

    /**
     * Bridge between the engine and the (possibly fake) [PcmAudioPlayer].
     * Tests override this so playback can be exercised without an
     * AudioTrack. Production routes to [PcmAudioPlayer.play].
     */
    protected open suspend fun playSamples(
        player: PcmAudioPlayer,
        samples: FloatArray,
        sampleRate: Int,
    ) {
        player.play(samples, sampleRate)
    }

    /**
     * Synthesizer abstraction — wraps a real [OfflineTts] so tests
     * can substitute a fake. Defined here so the production code is
     * the only file that imports `com.k2fsa.sherpa.onnx.*`.
     */
    interface Synthesizer {
        fun generate(text: String, sid: Int, speed: Float): GeneratedAudio
        fun release() {}
    }

    /**
     * Provider abstraction — produces a fresh [Synthesizer] for the
     * given model directory. Default implementation builds a real
     * [OfflineTts] (the only place JNI touches); tests substitute
     * with a fake provider that returns canned audio.
     */
    interface SynthesizerProvider {
        fun create(modelDir: File): Synthesizer
    }

    /**
     * Production provider — builds a real [OfflineTts] from the model
     * directory and wraps it as a [Synthesizer].
     */
    object DefaultSynthesizerProvider : SynthesizerProvider {
        override fun create(modelDir: File): Synthesizer {
            val offlineTts = OfflineTts(
                assetManager = null,
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        matcha = OfflineTtsMatchaModelConfig(
                            acousticModel = File(modelDir, ACOUSTIC_MODEL_FILE).absolutePath,
                            vocoder = File(modelDir, VOCODER_FILE).absolutePath,
                            lexicon = File(modelDir, "lexicon.txt").absolutePath,
                            tokens = File(modelDir, "tokens.txt").absolutePath,
                            // Bug 7c (v0.1.63): espeak-ng-data MUST exist at dataDir or
                            // sherpa-onnx Validate emits a warning then generate() segfaults
                            // on the null espeak lookup. We bundle the 2.2 MB Chinese-only
                            // subset (phontab + phonindex + phondata + intonations +
                            // cmn_dict + lang/sit/*) in the APK at
                            // `assets/models/tts/zh/espeak-ng-data/` and copyBundledAssets()
                            // walks it into modelDir at install time.
                            dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                    ),
                    ruleFsts = listOf(
                        File(modelDir, "phone.fst").absolutePath,
                        File(modelDir, "date.fst").absolutePath,
                        File(modelDir, "number.fst").absolutePath,
                    ).joinToString(","),
                    maxNumSentences = 1,
                    silenceScale = 0.18f,
                ),
            )
            return object : Synthesizer {
                override fun generate(text: String, sid: Int, speed: Float): GeneratedAudio =
                    offlineTts.generate(text, sid, speed)
                override fun release() = offlineTts.release()
            }
        }
    }

    companion object {
        /** Synthetic package name for the picker. */
        const val LOCAL_LABEL = "冰灵 TTS 引擎(本地)"

        private const val TAG = "SherpaTtsEngine"
        private const val ACOUSTIC_MODEL_FILE = "model-steps-3.onnx"
        private const val VOCODER_FILE = "vocos-22khz-univ.onnx"

        /**
         * Files sherpa-onnx Matcha-zh-baker OfflineTts needs at [modelDir]
         * (without the `espeak-ng-data/` subtree, which is checked
         * separately via [TtsModelInstaller.BUNDLED_ESPEAK_FILES]). 2 ONNX
         * + 5 text/rule resources (lexicon / tokens / 3× .fst). Missing any
         * one of these triggers `Rule fst '<path>' does not exist` then
         * segfault at OfflineTts_generateImpl+268 (Bug 7b, hardened v0.1.65).
         */
        val REQUIRED_MODEL_FILES: List<String> = listOf(
            ACOUSTIC_MODEL_FILE,
            VOCODER_FILE,
            "lexicon.txt",
            "tokens.txt",
            "phone.fst",
            "date.fst",
            "number.fst",
        )
    }
}
