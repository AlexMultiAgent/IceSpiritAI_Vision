package com.icespiritai.offline.tts.sherpa

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * TTS playback for [SherpaTtsEngine]. Port of translate's
 * `com.icespiritai.offline.audioengine.PcmAudioPlayer` simplified for
 * vision's TTS-only call sites — no AudioFocusRequest, no `DeviceFacts`
 * seam (vision doesn't have an ASR pipeline that needs focus
 * coordination), no peak normalization (Matcha+Vocos output is already
 * normalized). Drop-in functional equivalent for `play(samples, rate)`.
 *
 * Thread model:
 * - `play(samples, sampleRate)` is `suspend` and MUST be called from a
 *   coroutine. It blocks until playback completes (or is cancelled).
 * - `stop()` is non-suspending; safe to call from the main thread.
 *
 * MODE_STATIC for ≤50000 samples (~2.3 s @ 22050 Hz), MODE_STREAM
 * otherwise. Mirrors translate's perf heuristic (P1-T4 in translate's
 * audio engine history).
 */
class PcmAudioPlayer(private val context: Context) {

    @Volatile
    private var activeTrack: AudioTrack? = null

    /**
     * Play [samples] at [sampleRate] (Hz) on the device speaker. Blocks
     * until playback finishes or [stop] interrupts.
     */
    suspend fun play(samples: FloatArray, sampleRate: Int) {
        val shortSamples = ShortArray(samples.size) { (samples[it] * 32767f).toInt().toShort() }
        play(shortSamples, sampleRate)
    }

    suspend fun play(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "AudioTrack does not support $sampleRate Hz mono PCM16 output." }

        val useStatic = samples.size <= STATIC_MODE_MAX_SAMPLES
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(kotlin.math.max(minBuffer, samples.size * 2))
            .setTransferMode(if (useStatic) AudioTrack.MODE_STATIC else AudioTrack.MODE_STREAM)
            .build()

        try {
            // Honor / Huawei AudioFlinger transient: AudioTrack.Builder().build()
            // can return before state reaches STATE_INITIALIZED. Poll briefly.
            var polledMs = 0L
            while (newTrack.state != AudioTrack.STATE_INITIALIZED && polledMs < INIT_POLL_TIMEOUT_MS) {
                kotlinx.coroutines.delay(INIT_POLL_INTERVAL_MS)
                polledMs += INIT_POLL_INTERVAL_MS
            }
            check(newTrack.state == AudioTrack.STATE_INITIALIZED) {
                "AudioTrack failed to initialize within ${INIT_POLL_TIMEOUT_MS}ms"
            }

            activeTrack = newTrack
            newTrack.setVolume(AudioTrack.getMaxVolume())
            newTrack.play()

            if (useStatic) {
                val written = newTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                check(written == samples.size) { "AudioTrack write failed: $written of ${samples.size}" }
                waitForCompletion(newTrack, samples.size, sampleRate)
            } else {
                var offset = 0
                while (offset < samples.size) {
                    val written = newTrack.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                    check(written > 0) { "AudioTrack write failed: $written" }
                    offset += written
                }
                waitForCompletion(newTrack, samples.size, sampleRate)
            }
            Log.i(TAG, "PCM playback completed: samples=${samples.size}, sampleRate=$sampleRate")
        } finally {
            runCatching {
                if (newTrack.playState == AudioTrack.PLAYSTATE_PLAYING) newTrack.stop()
            }
            runCatching { newTrack.release() }
            if (activeTrack === newTrack) activeTrack = null
        }
    }

    /** Cancel in-flight playback (no-op if nothing playing). */
    fun stop() {
        val current = activeTrack ?: return
        runCatching {
            current.pause()
            current.flush()
            current.release()
        }
        if (activeTrack === current) activeTrack = null
    }

    private suspend fun waitForCompletion(track: AudioTrack, totalSamples: Int, sampleRate: Int) {
        val timeoutAt = System.currentTimeMillis() + ((totalSamples * 1_500L) / sampleRate).coerceAtLeast(1_500L)
        while (activeTrack === track && System.currentTimeMillis() < timeoutAt) {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
            if (track.playbackHeadPosition >= totalSamples) return
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val TAG = "VisionPcmPlayer"
        private const val POLL_INTERVAL_MS = 20L
        // At 22050 Hz mono PCM16, 50000 samples = ~2.27 s = ~100 KB RAM.
        // Most TTS outputs (≤3 chunks, ≤240 chars ZH) fit below this bound.
        private const val STATIC_MODE_MAX_SAMPLES = 50_000
        private const val INIT_POLL_INTERVAL_MS = 50L
        private const val INIT_POLL_TIMEOUT_MS = 150L
    }
}
