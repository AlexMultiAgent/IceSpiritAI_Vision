package com.icespiritai.offline.tts.sherpa

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [PcmAudioPlayer] (Bug 3 pivot v0.1.60).
 *
 * Robolectric provides a stub [android.media.AudioManager] so we can
 * exercise the AudioTrack construction path without a real device —
 * but Robolectric's AudioTrack shadow is too thin to actually play
 * audio (the underlying state machine returns `WRITE_BLOCKING` = 0
 * bytes for every write, so the STATIC/STREAM write checks fire).
 *
 * These tests therefore pin the public-API contract only:
 *  - player can be constructed from a Context
 *  - empty-samples play() is a no-op (early-return path)
 *  - stop() is idempotent
 *
 * Real-device audio verification lives in
 * `connectedDebugAndroidTest` on Huawei nova 6 — see
 * `docs/smoke/2026-09-02-audit71-v11-rules-e2e.md` for the A/B
 * play-back pattern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PcmAudioPlayerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun `player constructs from context`() {
        val player = PcmAudioPlayer(context)
        assertNotNull(player)
    }

    @Test fun `stop is idempotent without an active track`() {
        val player = PcmAudioPlayer(context)
        player.stop()
        player.stop()  // second call must not throw
    }

    @Test fun `empty samples array is a no-op`() = runTest {
        val player = PcmAudioPlayer(context)
        // Early-return path: skips AudioTrack init entirely.
        player.play(FloatArray(0), 22050)
    }
}
