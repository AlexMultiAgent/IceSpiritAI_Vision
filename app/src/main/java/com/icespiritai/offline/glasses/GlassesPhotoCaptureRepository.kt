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
        // Priority: HIGH at capture entry — matches zhang reference app's
        // `prewarmAiPhotoBlePriority` (HIGH + never restore). Smoke 21
        // 2026-09-15: commit 8ca05b7 "back to BALANCED" was a wrong
        // call — it slowed the firmware to 40ms block cadence and
        // raised the loss rate. Going back to HIGH with the fanout-8
        // resend ceiling (24 rounds × 8 = 192, plenty of headroom for
        // the spec §2.3 35% loss case).
        bluetoothController.requestPriority(android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH)

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
                // zhang reference: HIGH throughout the capture session
                // — does NOT restore BALANCED until the BLE link itself
                // is torn down. Keeping HIGH lets a follow-up capture
                // session start fast. The GATT close path in
                // BluetoothController.requestPriority resets it.
                bluetoothController.requestPriority(
                    android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH,
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

        // ── Stage 2: wait for 0x51 START with fileSize ──────────────
        // Glass-D15 / Glasses-A88 V2.4.5 (smoke 21, 2026-09-15) emits
        // **two** START notifications: an initial 8-byte
        // `55 aa 01 51 03 01 00 01` (STATUS_START without the 4-byte
        // file_size trailer — firmware warming up), followed ~1.8 s
        // later by a real `55 aa 02 51 03 05 00 01 <fileSize u32 LE>`
        // that carries the true extent. The old code accepted the
        // initial 8-byte START as the final answer, kicked off
        // `collectChunks` with `GlassesPhotoStream(1)`, and the very
        // first FA12 chunk (240 B) tripped the resize-and-Complete
        // path so we assembled a 240-byte stub JPEG that OCR couldn't
        // read.
        //
        // Wait until a START with a non-null `fileSize` arrives before
        // opening the receive stream. Failed short-circuits the wait.
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.WaitingForStart,
                bytesReceived = 0, totalBytes = null,
                elapsedMs = System.currentTimeMillis() - startedMs,
            ),
        )
        var totalSize: Int? = null
        val startDeadline = System.currentTimeMillis() + captureTimeoutMs
        while (totalSize == null && System.currentTimeMillis() < startDeadline) {
            val startPayload = withTimeoutOrNull(
                (startDeadline - System.currentTimeMillis()).coerceAtLeast(1L),
            ) {
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
            totalSize = startAnnounced.fileSize
            // If the firmware omitted fileSize in this START, loop and
            // wait for the next one (V2.4.5 sends a second START
            // ~1.8 s later that does carry it). Don't waste the warm-up
            // START — it's still firmware confirmation that the
            // capture request was accepted.
            if (totalSize == null) {
                Log.d(TAG, "0x51 START without fileSize — waiting for the next one")
            }
        }

        // ── Stage 3: collect FA12 chunks ─────────────────────────────
        var stream = if (totalSize != null && totalSize > 0) {
            GlassesPhotoStream(totalSize)
        } else {
            // file_size was still null when the wait loop exited —
            // shouldn't happen on V2.4.5 (firmware always emits the
            // second START with file_size before captureTimeoutMs),
            // but kept as a defensive fallback. Start with a
            // placeholder size; we'll re-init the stream on the first
            // chunk that reveals the true extent. (Bug fix 2026-09-15
            // smoke 6: the outer `stream` reference used to be `val`,
            // so when collectChunks internally swapped in a larger
            // stream after the first chunk, the outer one stayed at
            // 1 byte and `assemble()` threw "stream not complete: 0 /
            // 1 bytes filled". Made this a `var` and update it from
            // the resize callback below.)
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
     * failure surfaces. Mirrors zhang reference app (passive receive —
     * no FA11 op2 resend fan-out; the Glass-D15 / Glasses-A88 V2.4.5
     * firmware's resend channel on this hardware is broken and
     * flooding it with op2s only burns the FA11 GATT write slot
     * without provoking retransmits — smoke 21 2026-09-15).
     *
     * Completion triggers:
     *   1. 0x51 SUCCESS from the firmware → force-complete with whatever
     *      bytes we have, fill any unfilled prefix bytes with 0xFF so
     *      OCR doesn't trip on a degenerate JPEG.
     *   2. contiguous prefix reaches `totalSize` (zhang's
     *      `nextClearBit(0) >= size` semantics).
     *   3. Capture timeout (90 s) → fail.
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
        var chunksProcessed = 0
        var forceCompleted = false
        var statusFlag: StatusFlag = StatusFlag.NONE

        // Side-channel observer: a child coroutine that watches the
        // 0x51 flow and flips `statusFlag` when SUCCESS/FAILED arrives.
        // Uses an independent SupervisorJob so a `cancel()` from inside
        // the observer doesn't propagate to `collectChunks` itself.
        //
        // Two completion signals are honored:
        //   * 0x33 SUCCESS (55 aa ?? 33 02 01 00 00): firmware ack'd the
        //     capture command. Once it's been sent, the firmware stops
        //     streaming new blocks; from there we just race the watchdog.
        //   * 0x51 SUCCESS / FAILED: classic photo-result channel —
        //     success means the firmware considers the transfer done
        //     (either full or just gave up), failure means give up.
        val statusObserverJob: Job = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            try {
                bluetoothController.fff0Notifications.collect { payload ->
                    when {
                        // 0x33 SUCCESS — capture command ack'd.
                        GlassesPhotoProtocol.isCaptureAckSuccess(payload) -> {
                            Log.d(TAG, "0x33 capture ack (err=0) — firmware accepted the photo command")
                            statusFlag = StatusFlag.SUCCESS
                        }
                        // 0x51 SUCCESS / FAILED — pass through parser.
                        GlassesPhotoProtocol.parseStatusNotify(payload) is
                            GlassesPhotoProtocol.StatusNotify.Success -> {
                            Log.d(TAG, "0x51 SUCCESS — firmware reports transfer complete")
                            statusFlag = StatusFlag.SUCCESS
                        }
                        GlassesPhotoProtocol.parseStatusNotify(payload) is
                            GlassesPhotoProtocol.StatusNotify.Failed -> {
                            Log.d(TAG, "0x51 FAILED — firmware reports transfer failure")
                            statusFlag = StatusFlag.FAILED
                        }
                        // 0x51 START / LegacyFtpReady — out of scope for
                        // the chunk-collection loop; runCapturePipeline
                        // already handled them up-stream.
                    }
                }
            } catch (_: CancellationException) {
                // normal exit when collectChunks returns and cancels us
            }
        }

        // Race FA12 chunks against 0x51 SUCCESS: whichever fires first
        // terminates the loop. Polling loop with cooperative yields is
        // simpler than two-channel `select { ... }` and lets us log
        // per-event.
        val collectStart = System.currentTimeMillis()
        val ctx = currentCoroutineContext()
        while (ctx.isActive && !forceCompleted) {
            if (working.isComplete) {
                Log.d(TAG, "collectChunks complete (contiguous full): ${working.contiguousFilledBytes}/${working.totalSize} after $chunksProcessed chunks")
                return true
            }
            val now = System.currentTimeMillis()
            if (now - collectStart > captureTimeoutMs) {
                Log.w(TAG, "collectChunks hard timeout after $chunksProcessed chunks, " +
                    "filled=${working.contiguousFilledBytes}/${working.totalSize}")
                fail("传输超时", retryable = true)
                return false
            }
            // Race: wait for the next event — either an FA12 chunk or
            // a 0x51 status notification — whichever arrives first
            // inside `chunkStallMs`. We use `withTimeoutOrNull` over
            // two `first()` calls in sequence (FA12 has priority since
            // the bulk of events are chunks). When the FA12 wait
            // times out, peek the 0x51 flow non-blockingly via a
            // dedicated observer launched once at the top of this
            // loop — see `statusObserver` below.
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
                working.addChunk(chunk)
                chunksProcessed++
                if (chunksProcessed <= 3 || chunksProcessed % 20 == 0) {
                    Log.d(TAG, "chunk #$chunksProcessed offset=${chunk.offset} size=${chunk.data.size} " +
                        "filled=${working.contiguousFilledBytes}/${working.totalSize} highest=${working.highestWrittenOffset}")
                }
                _state.value = GlassesCaptureState.Capturing(
                    CaptureProgress(
                        stage = CaptureProgress.Stage.ReceivingChunks,
                        bytesReceived = working.contiguousFilledBytes,
                        totalBytes = working.totalSize,
                        elapsedMs = now - startedMs,
                    ),
                )
            } else if (statusFlag == StatusFlag.SUCCESS) {
                Log.d(TAG, "0x51 SUCCESS — force-complete with " +
                    "${working.contiguousFilledBytes}/${working.totalSize} bytes filled (${chunksProcessed} chunks)")
                // Pad unfilled prefix with 0xFF so OCR sees a
                // well-formed JPEG header. Without padding the
                // assembled buffer is a mosaic of received
                // chunks with literal zero bytes where the
                // firmware dropped a 240-byte block; some OCR
                // frontends reject that as a malformed JPEG.
                working.fillGapsWith(0xFF.toByte())
                forceCompleted = true
            } else if (statusFlag == StatusFlag.FAILED) {
                Log.w(TAG, "0x51 FAILED during transfer")
                fail("眼镜报告传图失败", retryable = true)
                return false
            }
            // Otherwise: stall, neither chunk nor status — next
            // iteration will retry until captureTimeoutMs.
        }
        statusObserverJob.cancel()
        return forceCompleted
    }

    private enum class StatusFlag { NONE, SUCCESS, FAILED }

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
