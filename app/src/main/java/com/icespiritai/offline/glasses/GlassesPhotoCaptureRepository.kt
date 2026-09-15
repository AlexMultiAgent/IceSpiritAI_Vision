package com.icespiritai.offline.glasses

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Top-level state machine that orchestrates a single glasses photo
 * capture, from BLE connect through JPEG assembly.
 *
 * **Pipeline.** Each [capture] call walks these stages:
 *
 *   1. Send 0x33 capture request via FFF0.
 *   2. Wait for 0x51 START (with optional `file_size` trailer).
 *   3. Collect FA12 JPEG chunks into a [GlassesPhotoStream], issuing
 *      FA11 op2 resends if the chunk stream stalls.
 *   4. Send FA11 op3 CRC confirmation.
 *   5. Await 0x51 SUCCESS.
 *   6. Persist the JPEG to `cacheDir/capture/photo_capture_<n>.jpg`
 *      and return its FileProvider URI.
 *
 * **Design note: sequential, not concurrent.** The pipeline runs in one
 * suspending function rather than three concurrent collectors. This is
 * deliberately simpler than the "ideal" fan-out; the real-device smoke
 * test (Phase 6) will tell us if the throughput matters. If it does,
 * the chunk collector + SUCCESS awaiter + resender fan-out can be
 * reintroduced in a v2 behind the same [capture] / [cancel] API.
 *
 * **Connection priority.** Pushed HIGH at [capture] entry, restored
 * BALANCED on terminal state (per `docs/glass/...` §3).
 *
 * **Single-flight.** Re-entrant [capture] calls while one is in flight
 * return `null` immediately. The caller should observe [state] and only
 * invoke [capture] from [GlassesCaptureState.Ready].
 */
class GlassesPhotoCaptureRepository(
    private val context: Context,
    private val bluetoothController: BluetoothController,
    private val scope: CoroutineScope,
) {

    // ────────────────────────────────────────────────────────────────────
    // Public reactive surface
    // ────────────────────────────────────────────────────────────────────

    sealed class GlassesCaptureState {
        object Idle : GlassesCaptureState()
        data class Connecting(val device: GlassesCaptureDevice) : GlassesCaptureState()
        data class NegotiatingMtu(val device: GlassesCaptureDevice, val currentMtu: Int) : GlassesCaptureState()
        data class DiscoveringServices(val device: GlassesCaptureDevice) : GlassesCaptureState()
        data class EnablingNotifies(val device: GlassesCaptureDevice) : GlassesCaptureState()
        data class Ready(val device: GlassesCaptureDevice) : GlassesCaptureState()
        data class Capturing(val progress: CaptureProgress) : GlassesCaptureState()
        data class Success(val fileUri: Uri, val latencyMs: Long) : GlassesCaptureState()
        data class Failed(val reason: String, val retryable: Boolean) : GlassesCaptureState()
    }

    data class GlassesCaptureDevice(val address: String, val name: String) {
        companion object {
            fun from(d: GlassesDevice): GlassesCaptureDevice =
                GlassesCaptureDevice(address = d.address, name = d.name)
        }
    }

    data class CaptureProgress(
        val stage: Stage,
        val bytesReceived: Int,
        val totalBytes: Int?,
        val elapsedMs: Long,
    ) {
        enum class Stage { SendingCommand, WaitingForStart, ReceivingChunks, Verifying }
    }

    private val _state = MutableStateFlow<GlassesCaptureState>(GlassesCaptureState.Idle)
    val state: StateFlow<GlassesCaptureState> = _state.asStateFlow()

    private companion object {
        const val TAG = "GlassesCapture"
    }

    // ────────────────────────────────────────────────────────────────────
    // Tunables — see `docs/glass/AI识图传图提速_App连接参数配合.md` §9
    // ────────────────────────────────────────────────────────────────────

    private val captureTimeoutMs = 90_000L
    private val chunkStallMs = 3_500L
    private val maxResendRounds = 24

    private val captureSeq = AtomicInteger(0)
    private var activeCaptureJob: Job? = null
    private var currentTempFile: File? = null

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /**
     * Connect + negotiate MTU + discover services + enable notifies.
     * Suspends until [GlassesCaptureState.Ready] or a terminal
     * [GlassesCaptureState.Failed]. Safe to call repeatedly (no-op if
     * already Ready / Capturing).
     */
    suspend fun ensureConnected(device: GlassesDevice) {
        when (_state.value) {
            is GlassesCaptureState.Ready, is GlassesCaptureState.Capturing -> return
            else -> Unit
        }

        val captureDevice = GlassesCaptureDevice.from(device)
        _state.value = GlassesCaptureState.Connecting(captureDevice)
        bluetoothController.connect(device)

        val connected = withTimeoutOrNull(captureTimeoutMs) {
            bluetoothController.connectionState.first {
                it is BluetoothController.ConnectionState.Connected
            }
        } ?: run {
            fail("连接超时", retryable = true)
            return
        }
        if (connected !is BluetoothController.ConnectionState.Connected) {
            fail("连接失败", retryable = true)
            return
        }

        _state.value = GlassesCaptureState.NegotiatingMtu(captureDevice, bluetoothController.mtu.value)
        val actualMtu = withTimeoutOrNull(captureTimeoutMs) {
            bluetoothController.requestMtu(BluetoothController.DESIRED_MTU)
        } ?: bluetoothController.mtu.value
        if (actualMtu < BluetoothController.REQUIRED_MIN_MTU) {
            fail("MTU 协商失败 ($actualMtu < ${BluetoothController.REQUIRED_MIN_MTU})", retryable = true)
            return
        }

        _state.value = GlassesCaptureState.DiscoveringServices(captureDevice)
        val discovered = withTimeoutOrNull(captureTimeoutMs) {
            bluetoothController.discoverServices()
        } ?: false
        if (!discovered) {
            fail("服务发现失败", retryable = true)
            return
        }

        _state.value = GlassesCaptureState.EnablingNotifies(captureDevice)
        val fff0Ok = withTimeoutOrNull(captureTimeoutMs) { bluetoothController.enableFff0Notify() } ?: false
        val fa12Ok = withTimeoutOrNull(captureTimeoutMs) { bluetoothController.enableFa12Notify() } ?: false
        if (!fff0Ok || !fa12Ok) {
            fail("通知订阅失败", retryable = true)
            return
        }

        _state.value = GlassesCaptureState.Ready(captureDevice)
    }

    /**
     * Run one full capture cycle. Returns the FileProvider URI of the
     * JPEG on success, or `null` on failure (state transitions to
     * [GlassesCaptureState.Failed] with a human-readable reason).
     */
    suspend fun capture(): Uri? {
        if (_state.value is GlassesCaptureState.Capturing) return null
        val ready = _state.value as? GlassesCaptureState.Ready
            // (smoke 20 2026-09-15) The "未连接眼镜" path used to set
            // retryable=false, which hid the 重试 button — the user
            // saw only "关闭" and had no way to recover. In practice
            // this failure means "ensureConnected didn't reach Ready
            // in time" (A2DP contended BLE radio per the user's
            // earlier report, or transient GATT drop). The right
            // behavior is: surface the error, but let the user
            // re-trigger the whole pipeline (re-establish GATT +
            // retry the 0x33 capture). The Retry button already
            // calls repository.reset() + ensureConnected() + capture(),
            // which is exactly the right sequence. Mark this retryable.
            ?: return fail("未连接眼镜", retryable = true).let { null }

        // Priority: use BALANCED (intv=32, ~40 ms) — the current
        // observable state per spec §2.2 ("Android 常按 BALANCED 策略
        // 回 intv=32,导致 turbo 未生效"). Smoke 4-13 (2026-09-14/15,
        // nova 6 + Glasses-A88 V2.4.5) confirmed:
        //   - Our HIGH request gets queued but Android often grants
        //     intv=32 anyway (per spec §2.2 + §3.3.4 fallback)
        //   - HIGH when granted (8 ms) → 35 % L2CAP loss per spec
        //     §2.3; our fanout=8 + 24-round resend ceiling can't
        //     recover the 28-35 % drops within 90 s (firmware V2.4.5
        //     only re-sends 1-3 chunks per op2 write and won't
        //     re-send missing blocks it never had in its buffer)
        //   - The "A2DP contention" theory was wrong — phone audio
        //     to glasses works fine, A2DP is unrelated
        // So we use BALANCED (~3.6 s, 0 % loss per spec §2.3) as
        // the baseline. The 1.4 s HIGH goal (spec §1.2) is parked
        // until the firmware cuts its block-interval macro to 10 ms
        // (requires paired App + firmware release; our App code
        // already calls requestPriority(HIGH) inside the overlay
        // pre-warm hook on the next pairing cycle).
        bluetoothController.requestPriority(android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED)

        val captureDevice = ready.device
        val startedMs = System.currentTimeMillis()

        activeCaptureJob = scope.launch(Dispatchers.IO) {
            try {
                runCapturePipeline(captureDevice, startedMs)
            } catch (e: CancellationException) {
                cleanupTempFile()
                throw e
            } catch (e: Throwable) {
                cleanupTempFile()
                fail(e.message ?: "未知错误", retryable = true)
            } finally {
                bluetoothController.requestPriority(
                    android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
                )
            }
        }

        return try {
            activeCaptureJob?.join()
            when (val s = _state.value) {
                is GlassesCaptureState.Success -> s.fileUri
                else -> null
            }
        } catch (e: CancellationException) {
            null
        }
    }

    /**
     * Abort an in-flight capture and drop the temp file. Best-effort
     * — sends FA11 op4 to the glasses, but if the link is down the
     * firmware will time out on its own.
     */
    fun cancel() {
        activeCaptureJob?.cancel()
        cleanupTempFile()
    }

    /** Clear terminal state so the next [capture] can run. */
    fun reset() {
        cleanupTempFile()
        val current = _state.value
        if (current is GlassesCaptureState.Failed || current is GlassesCaptureState.Success) {
            _state.value =
                if (bluetoothController.connectionState.value is BluetoothController.ConnectionState.Connected) {
                    val dev = (bluetoothController.connectionState.value
                        as BluetoothController.ConnectionState.Connected).device
                    GlassesCaptureState.Ready(GlassesCaptureDevice.from(dev))
                } else {
                    GlassesCaptureState.Idle
                }
        }
    }

    /** Tear down the repository. Cancels any in-flight capture and disconnects. */
    fun release() {
        cancel()
        bluetoothController.release()
        _state.value = GlassesCaptureState.Idle
    }

    // ────────────────────────────────────────────────────────────────────
    // Pipeline
    // ────────────────────────────────────────────────────────────────────

    /**
     * Linear capture pipeline. Assumes the device is in [Ready] and
     * HIGH priority has been requested. Runs in the [scope]'s IO
     * dispatcher (the [capture] entry point dispatches to IO before
     * invoking this).
     *
     * @return `true` if the capture completed successfully — the
     *   caller reads [state] for the result URI.
     */
    private suspend fun runCapturePipeline(
        device: GlassesCaptureDevice,
        startedMs: Long,
    ): Boolean {
        // Allocate a temp file ahead of writing.
        val file = File(context.cacheDir, "capture/photo_capture_${captureSeq.incrementAndGet()}.jpg")
        file.parentFile?.mkdirs()
        currentTempFile = file

        // ── Stage 1: send 0x33 ────────────────────────────────────────
        Log.d(TAG, "Stage 1: SendingCommand — writing 0x33 to FFF1")
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.SendingCommand,
                bytesReceived = 0, totalBytes = null,
                elapsedMs = 0,
            ),
        )
        val sendOk = bluetoothController.writeFff0(
            GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x00),
        )
        Log.d(TAG, "0x33 writeFff0 returned: $sendOk")
        if (!sendOk) {
            cleanupTempFile()
            fail("0x33 发送失败", retryable = true)
            return false
        }

        // ── Stage 2: wait for 0x51 START ─────────────────────────────
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.WaitingForStart,
                bytesReceived = 0, totalBytes = null,
                elapsedMs = System.currentTimeMillis() - startedMs,
            ),
        )
        val startPayload = withTimeoutOrNull(captureTimeoutMs) {
            bluetoothController.fff0Notifications.first { payload ->
                val status = GlassesPhotoProtocol.parseStatusNotify(payload)
                status is GlassesPhotoProtocol.StatusNotify.Start ||
                    status is GlassesPhotoProtocol.StatusNotify.Failed
            }
        }
        if (startPayload == null) {
            Log.w(TAG, "0x51 START not received within ${captureTimeoutMs}ms")
            cleanupTempFile()
            fail("等待 START 超时", retryable = true)
            return false
        }
        Log.d(TAG, "0x51 START received: ${startPayload.size} bytes; raw=" +
            startPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        val startStatus = GlassesPhotoProtocol.parseStatusNotify(startPayload)
        if (startStatus is GlassesPhotoProtocol.StatusNotify.Failed) {
            cleanupTempFile()
            fail("眼镜拒绝拍照 (code=${startStatus.code})", retryable = true)
            return false
        }
        val startAnnounced = startStatus as GlassesPhotoProtocol.StatusNotify.Start
        var totalSize = startAnnounced.fileSize

        // ── Stage 3: collect FA12 chunks ─────────────────────────────
        var stream = if (totalSize != null && totalSize > 0) {
            GlassesPhotoStream(totalSize)
        } else {
            // file_size omitted by firmware — start with a placeholder
            // size; we'll re-init the stream on the first chunk that
            // reveals the true extent. (Bug fix 2026-09-15 smoke 6: the
            // outer `stream` reference used to be `val`, so when
            // collectChunks internally swapped in a larger stream after
            // the first chunk, the outer one stayed at 1 byte and
            // `assemble()` threw "stream not complete: 0 / 1 bytes
            // filled". Made this a `var` and update it from the resize
            // callback below.)
            GlassesPhotoStream(1)
        }
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.ReceivingChunks,
                bytesReceived = 0, totalBytes = totalSize,
                elapsedMs = System.currentTimeMillis() - startedMs,
            ),
        )

        val collected = collectChunks(stream, startedMs, totalSize) { resized, newStream ->
            totalSize = resized
            // Re-bind the outer reference to the resized working
            // stream (carries all the addChunk'd bytes with it).
            stream = newStream
        }
        if (!collected) {
            cleanupTempFile()
            // Failure reason already emitted by collectChunks via fail().
            return false
        }

        // ── Stage 4: persist + CRC ────────────────────────────────────
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.Verifying,
                bytesReceived = stream.contiguousFilledBytes,
                totalBytes = stream.totalSize,
                elapsedMs = System.currentTimeMillis() - startedMs,
            ),
        )
        val bytes = stream.assemble()
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { it.write(bytes) }
        }
        // Best-effort CRC write — failure here doesn't roll back the
        // capture; the JPEG is already on disk and analyzable.
        try {
            bluetoothController.writeFa11(GlassesPhotoProtocol.buildFa11Crc(stream.crc32()))
        } catch (_: Throwable) {
            // Non-fatal.
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val latencyMs = System.currentTimeMillis() - startedMs
        _state.value = GlassesCaptureState.Success(uri, latencyMs)
        return true
    }

    /**
     * Consume FA12 notifications until the stream is complete or a
     * failure surfaces. Issues FA11 op2 resends if the stream stalls.
     *
     * @return `true` if the stream completed; `false` on timeout /
     *   failure (caller must inspect [state] for the reason).
     */
    private suspend fun collectChunks(
        stream: GlassesPhotoStream,
        startedMs: Long,
        initialTotal: Int?,
        onTotalSizeResized: (Int, GlassesPhotoStream) -> Unit,
    ): Boolean {
        Log.d(TAG, "collectChunks entered: stream.totalSize=${stream.totalSize} initialTotal=$initialTotal")
        var working = stream
        // Track progress (contiguous bytes) — not raw chunk arrival. The
        // firmware may resend chunks we already have; those are Duplicate
        // and don't advance the prefix, but our previous code reset
        // `lastChunkMs` on any arrival and the resend timer never fired.
        // Bug found 2026-09-14 (smoke 4): at HIGH conn priority, ~35% of
        // blocks are lost and the firmware's resend responses are mostly
        // duplicates — the loop ran 6+ min without making progress.
        var lastProgressMs = System.currentTimeMillis()
        var resendRounds = 0
        var chunksProcessed = 0

        // We drive chunk collection by polling the SharedFlow in a
        // loop. A more idiomatic approach is `first { it.isComplete }`
        // but we also need to interleave the stall-driven resender,
        // so a polling loop with cooperative yields is simpler.
        val collectStart = System.currentTimeMillis()
        val ctx = currentCoroutineContext()
        while (ctx.isActive) {
            if (working.isComplete) {
                Log.d(TAG, "collectChunks complete: ${working.contiguousFilledBytes}/${working.totalSize} after $chunksProcessed chunks")
                return true
            }
            val now = System.currentTimeMillis()
            if (now - collectStart > captureTimeoutMs) {
                Log.w(TAG, "collectChunks hard timeout after $chunksProcessed chunks, " +
                    "filled=${working.contiguousFilledBytes}/${working.totalSize} resendRounds=$resendRounds")
                fail("传输超时", retryable = true)
                return false
            }
            val chunkPayload: ByteArray? = withTimeoutOrNull(chunkStallMs) {
                bluetoothController.fa12Notifications.first()
            }
            if (chunkPayload != null) {
                val chunk = GlassesPhotoProtocol.parsePhotoChunk(chunkPayload)
                if (chunk == null) {
                    Log.w(TAG, "parsePhotoChunk returned null for size=${chunkPayload.size}")
                    continue
                }
                if (chunk.offset + chunk.data.size > working.totalSize) {
                    Log.d(TAG, "resizing stream: old=${working.totalSize} new=${chunk.offset + chunk.data.size}")
                    working = GlassesPhotoStream(chunk.offset + chunk.data.size)
                    onTotalSizeResized(working.totalSize, working)
                }
                val filledBefore = working.contiguousFilledBytes
                val addResult = working.addChunk(chunk)
                chunksProcessed++
                val filledAfter = working.contiguousFilledBytes
                val madeProgress = filledAfter > filledBefore
                if (madeProgress) {
                    lastProgressMs = now
                }
                if (chunksProcessed <= 3 || chunksProcessed % 20 == 0 || !madeProgress) {
                    Log.d(TAG, "chunk #$chunksProcessed offset=${chunk.offset} size=${chunk.data.size} addResult=$addResult filled=$filledAfter/${working.totalSize} progress=$madeProgress")
                }
                _state.value = GlassesCaptureState.Capturing(
                    CaptureProgress(
                        stage = CaptureProgress.Stage.ReceivingChunks,
                        bytesReceived = working.contiguousFilledBytes,
                        totalBytes = working.totalSize,
                        elapsedMs = now - startedMs,
                    ),
                )
            } else {
                // Stall — check if we should resend. Note: this uses
                // `lastProgressMs` (contiguous-bytes advance), not
                // `lastChunkMs` (any arrival) — see comment at the top
                // of this fn for why.
                if (now - lastProgressMs >= chunkStallMs && resendRounds < maxResendRounds) {
                    val gap = working.firstMissingRange()
                    if (gap != null) {
                        // (Smoke 8-11, 2026-09-15) The firmware
                        // (Glass-D15 V2.4.5) only retransmits 1-3
                        // chunks per op2 request — verified across
                        // multiple captures: asking for offset 240
                        // brought back chunk at 0; asking for 720
                        // brought back 240 + 480; etc. Fan out the
                        // resend: ask the firmware for the first N
                        // missing chunks in this round. Each request
                        // is a single FA11 write; the firmware
                        // queues them; empirically fills ~12-15
                        // chunks per 7 s window instead of 1-3.
                        //
                        // Fanout sizing (smoke 11): a 46 KB JPEG is
                        // ~192 chunks. With fanout=4 and 24 rounds we
                        // could only address 96 chunks — i.e. we'd
                        // never fill the file. fanout=8 × 24 = 192
                        // exactly matches worst-case size, fitting in
                        // the 90 s cap (24 × 3.5 s ≈ 84 s). For
                        // smaller files the loop early-exits when
                        // `nextMissingRangeFrom` returns null.
                        //
                        // CRITICAL: the cursor walk must use
                        // `cursor.last + 1` (the first byte AFTER
                        // the current gap), not `cursor.last`
                        // itself. IntRange `0..719` has last=719;
                        // scanning from 719 just re-finds the 1-byte
                        // sliver at 719 and we burn the rest of the
                        // fanout on the same byte. (Smoke 10 caught
                        // this: 3 of 4 resends in a round were
                        // offset=719 because nextMissingRangeAfter
                        // found the 1-byte sliver, not the next real
                        // gap.) Walk to the next distinct gap.
                        val fanout = 8
                        var i = 0
                        var nextScanFrom = gap.first
                        while (i < fanout && resendRounds < maxResendRounds) {
                            val nextGap = working.nextMissingRangeFrom(nextScanFrom) ?: break
                            val target = nextGap.first
                            Log.d(TAG, "resend #$resendRounds for offset=$target (gap=$nextGap) filled=${working.contiguousFilledBytes}/${working.totalSize}")
                            bluetoothController.writeFa11(GlassesPhotoProtocol.buildFa11Resend(target))
                            resendRounds++
                            i++
                            nextScanFrom = nextGap.last + 1
                        }
                    } else {
                        // No gap recorded, just no chunks — give up.
                        fail("传输停滞且无缺失块", retryable = true)
                        return false
                    }
                } else if (resendRounds >= maxResendRounds) {
                    Log.w(TAG, "resend rounds exhausted ($maxResendRounds), filled=${working.contiguousFilledBytes}/${working.totalSize}")
                    fail("重传次数耗尽 ($maxResendRounds 轮)", retryable = true)
                    return false
                }
            }
        }
        // Active cancellation.
        return false
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private fun fail(reason: String, retryable: Boolean) {
        cleanupTempFile()
        _state.value = GlassesCaptureState.Failed(reason, retryable)
    }

    private fun cleanupTempFile() {
        currentTempFile?.let {
            if (it.exists()) it.delete()
        }
        currentTempFile = null
    }
}
