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

    /**
     * The Job of the currently-running [speak] coroutine, when there is
     * one. Held so a subsequent [speak] can cancel it (Opt-7 interrupt
     * semantics). Cleared by the in-flight coroutine's `finally` on
     * normal completion; cleared inline by the next speak() on
     * interrupt. Volatile because speak() can be called from any thread
     * the controller's appScope dispatches to.
     */
    @Volatile
    private var activeSpeakJob: kotlinx.coroutines.Job? = null

    /**
     * Captures the in-flight speak()'s onDone callback so the interrupt
     * path can fire it synchronously when a second speak() arrives
     * mid-playback. Mirrors Android's QUEUE_FLUSH semantics from the
     * controller's perspective — the state machine sees a fast
     * Speaking → Idle → Speaking transition, not a stuck Speaking until
     * the first coroutine's playSamples() drains.
     */
    @Volatile
    private var pendingInterruptOnDone: ((String) -> Unit)? = null

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
        // Opt-7 (v0.1.68): unify interrupt semantics with AndroidTtsEngine.
        //
        // Pre-fix, lifecycleMutex.withLock serialized speak() calls — the
        // second speak() queued behind the first and played after it
        // finished. Vision's TTS is one-shot report narration; if the user
        // re-runs analysis (or taps the play FAB twice) while a previous
        // reading is mid-playback, they expect the old reading to cut off
        // and the new one to start. Android's TextToSpeech engine does
        // this natively (QUEUE_FLUSH). We mirror it by:
        //   1. Stopping the active player if any. The in-flight coroutine's
        //      `finally { if (activePlayer === player) activePlayer = null }`
        //      would still null it out, but stop() runs synchronously so
        //      the new coroutine's `activePlayer = player` assignment in
        //      the lock body sees the cleared state.
        //   2. Firing the in-flight utterance's onDone synchronously here
        //      so the controller's state machine (Speaking → Idle) can
        //      transition immediately, rather than waiting for the
        //      in-flight coroutine to drain through withLock.
        //   3. Cancelling the in-flight coroutine's Job if we still hold
        //      a reference — the coroutine is on internalScope so we can
        //      cancel without affecting the controller's appScope.
        val inFlight = activeSpeakJob
        if (inFlight != null && !inFlight.isCompleted) {
            // Fire the in-flight's onDone synchronously BEFORE we overwrite
            // pendingInterruptOnDone below — this is the callback captured
            // by the previous speak()'s launch block. The closure variable
            // `onDone` is captured per-call, so the previous launch's
            // `finally { onDone(utteranceId) }` will also fire when the
            // cancelled coroutine exits — but invoking the captured
            // callback here lets the controller's state machine react
            // immediately rather than waiting for cancellation to
            // propagate through internalScope's dispatcher.
            pendingInterruptOnDone?.invoke("")
            pendingInterruptOnDone = null
            activePlayer?.stop()
            inFlight.cancel()
            activeSpeakJob = null
        }
        // Use `coroutineContext[Job]` inside the finally rather than
        // capturing the outer `val job` — Kotlin forbids forward
        // references to locals, and `val job = launch { ... }` declares
        // `job` AFTER the lambda starts executing on the same thread
        // (UnconfinedTestDispatcher + EagerThreadStart). coroutineContext
        // returns this coroutine's own Job regardless of when the
        // outer assignment happens, so the identity check is still
        // sound.
        val job = internalScope.launch {
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "speak() failed: ${e.message}", e)
                // Drop cached synthesizer on error — next speak() rebuilds.
                runCatching { cachedSynth?.release() }
                cachedSynth = null
            } finally {
                // Only clear activeSpeakJob if it still points at OUR job —
                // a concurrent speak() that already overwrote it with its
                // own Job means we're the cancelled predecessor and the
                // field no longer belongs to us.
                val self = coroutineContext[kotlinx.coroutines.Job]
                if (self != null && activeSpeakJob === self) {
                    activeSpeakJob = null
                }
                onDone(utteranceId)
            }
        }
        // Capture THIS speak()'s onDone so the NEXT speak()'s interrupt
        // path can fire it synchronously. We assign AFTER the interrupt
        // branch so a self-cancel path (inFlight pointing at a stale Job)
        // never overwrites the new callback with the old one's.
        pendingInterruptOnDone = onDone
        activeSpeakJob = job
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
