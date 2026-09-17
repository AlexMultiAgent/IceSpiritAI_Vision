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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * **Design note: one receive tap, sequential consumption.** The stages
 * above run in a single suspending chain — but the two notification
 * streams are read through a [ReceiveTap] each, opened before stage 1 and
 * closed when the session ends. The tap is what makes that safe: it holds
 * one subscription for the whole session and buffers, so no notification
 * is lost in the gap between two stages. Reading the raw `SharedFlow`s
 * per stage instead (with `first()`) is what broke the transfer; see
 * [runCapturePipeline].
 *
 * **Connection priority.** HIGH at [capture] entry, BALANCED again when the
 * session ends — the same pair the official glasses app uses
 * (`prewarmAiPhotoBlePriority` / `restoreBlePriorityAfterAiPhoto`) and what
 * spec §3.2 asks for, including on the failure path.
 *
 * **Abandonment.** Every App-side abort of an in-flight transfer writes
 * FA11 `0x04` — from [collectFa12Chunks] (stall / timeout / no signal) or
 * from [cancel] (user closed the overlay). Without it the glasses keep
 * pushing FA12 into a session nobody is collecting any more.
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
        /**
         * [Repairing] and [ReceivingChunks] are the same transport state
         * (FA12 blocks coming in) split by whether the glasses dropped part
         * of the burst and the App is now asking for the gaps one batch at a
         * time. The distinction is user-facing only: without it the overlay
         * reports 「拍照中…」 at a crawl for as long as the repair takes
         * (2026-09-16 field runs: 11-22 s on a 48 KB photo).
         */
        enum class Stage { SendingCommand, WaitingForStart, ReceivingChunks, Repairing, Verifying }
    }

    private val _state = MutableStateFlow<GlassesCaptureState>(GlassesCaptureState.Idle)
    val state: StateFlow<GlassesCaptureState> = _state.asStateFlow()

    private companion object {
        const val TAG = "GlassesCapture"

        /**
         * Upper bound accepted for a `0x51 START` `file_size`, and the size
         * allocated for it. A glasses JPEG is ~20-30 KB (spec §7), so this
         * only ever rejects a corrupt trailer — but it has to be rejected
         * before `GlassesPhotoStream` allocates, not after.
         */
        const val MAX_AI_PHOTO_BYTES = 2 * 1024 * 1024

        /**
         * Sequence byte for the firmware-version read. The glasses echo it
         * but nothing demuxes on it (only one request is ever in flight),
         * so a fixed value keeps the frame byte-identical to the vendor
         * reference's first read.
         */
        const val FIRMWARE_READ_SEQ: Byte = 0x01

        /** Re-arm the button tap at least this often (also re-checks the link). */
        const val SHUTTER_TAP_REARM_MS = 60_000L

        /** How long to wait before retrying a failed connect while watching. */
        const val RECONNECT_RETRY_MS = 5_000L

        /** OEM backoff before the single 0x33 retry after a busy rejection. */
        const val BUSY_RETRY_DELAY_MS = 400L

        /**
         * How many times a busy rejection is retried (400 ms, 800 ms, 1600 ms).
         * The OEM does one; the shutter-button flow needs more, because the
         * glasses are busy with the shot the wearer just took.
         */
        const val MAX_BUSY_RETRIES = 3

        /**
         * Attempts per shutter press. The first 0x33 of a button session can
         * land while the glasses are still finishing that same shot, and the
         * firmware answers `0x51 FAILED` rather than the retryable `err=1`.
         */
        const val MAX_BUTTON_CAPTURE_ATTEMPTS = 3

        /** Pause between button-capture attempts (the busy window is ~1-2 s). */
        const val BUTTON_CAPTURE_RETRY_MS = 1_500L
    }

    // ────────────────────────────────────────────────────────────────────
    // Tunables — see `docs/glasses/AI识图传图提速_App连接参数配合.md` §9
    // ────────────────────────────────────────────────────────────────────

    private val captureTimeoutMs = 90_000L
    private val chunkStallMs = 3_500L

    /**
     * Per-cycle wait once repair has started, i.e. the window a batch of
     * FA11 op2 requests has to produce blocks.
     *
     * The OEM's `withTimeoutOrNull(2500)` (spec §2.3 Step 6 "单轮等待
     * ~2.5 s") assumes one request restores the rest of the file. V2.4.5
     * answers with 1-3 blocks instead, and does it in ~20-80 ms (2026-09-16
     * field logs: op2 at 19:40:40.186 → block applied at .263), so 2.5 s per
     * request capped repair at ~0.3 block/s and could never close the tens
     * to hundreds of gaps a lossy burst leaves behind. 250 ms is ~3x the
     * observed reply latency while keeping cycles tight; the abort decision
     * is made on progress ([FA12_REPAIR_NO_PROGRESS_MS]), not on this wait.
     *
     * Kept short on purpose: replies to a batch keep arriving after this
     * window (V2.4.5 queues them behind its ATT responses), so a long wait
     * would idle the link. Anything that lands late is still applied by the
     * next cycle's drain, and a request whose block arrives late is simply
     * re-asked next cycle — the stream reports the repeat as a duplicate.
     */
    private val resendWaitMs = 120L

    /**
     * FA11 op2 requests issued per repair cycle, spaced
     * [resendStrideBytes] apart — see [FA12_REPAIR_BATCH] / [FA12_REPAIR_STRIDE_BYTES].
     */
    private val resendBatchSize = FA12_REPAIR_BATCH
    private val resendStrideBytes = FA12_REPAIR_STRIDE_BYTES

    /**
     * How long a session with blocks may stop filling gaps before the App
     * gives up on it — see [FA12_REPAIR_NO_PROGRESS_MS].
     */
    private val repairNoProgressMs = FA12_REPAIR_NO_PROGRESS_MS

    /**
     * Repair cycles allowed per session — a guard against spamming op2 into
     * a firmware that keeps answering too slowly to matter, not the primary
     * budget: [repairNoProgressMs] ends a stalled session in ~10 s, and the
     * OEM's "≤24" (spec §2.3 Step 6) counted single requests, so the same
     * number of *cycles* buys `resendBatchSize`× the repair.
     */
    private val maxRepairCycles = 64

    private val captureSeq = AtomicInteger(0)
    private var activeCaptureJob: Job? = null
    private var currentTempFile: File? = null

    /**
     * Guards [watchShutterButton] so only one watcher loop is ever active —
     * see the comment in that function.
     */
    private val shutterWatchMutex = Mutex()

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /**
     * Connect + negotiate MTU + discover services + enable notifies.
     * Suspends until [GlassesCaptureState.Ready] or a terminal
     * [GlassesCaptureState.Failed].
     *
     * **Safe to call repeatedly, but "Ready" is re-verified first.** The
     * repository state is a record of what *did* happen, not proof the link
     * is still up: when the glasses reboot (they do, at the end of every
     * firmware upgrade) or the GATT drops, `_state` stays [Ready] while the
     * handle is gone. The old early-return then made every later read/write
     * talk into a dead handle — the firmware-version row reported
     * 「固件版本读取超时」 and only an App restart helped (user report
     * 2026-09-17). Now the state has to agree with
     * [BluetoothController.connectionState] or we rebuild the link.
     */
    suspend fun ensureConnected(device: GlassesDevice) {
        if (_state.value is GlassesCaptureState.Capturing) return
        if (isGlassesLinkUsable(_state.value, bluetoothController.connectionState.value, device.address)) {
            return
        }
        if (_state.value is GlassesCaptureState.Ready) {
            // Stale "Ready": the link is gone even though we last saw it up.
            // Close the dead handle so BluetoothController.connect() does not
            // early-return on its own (equally stale) Connected state.
            bluetoothController.resetLink()
            _state.value = GlassesCaptureState.Idle
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
        // NOTE (2026-09-17): subscribing to FA00/EA01 here — the third
        // notify channel the OEM app_config lists — destabilised the link on
        // V2.5.8: right after the CCCD write, every FA11 write came back
        // `onCharacteristicWrite Status=133` and the transfer could not be
        // repaired. It is therefore NOT part of the capture pipeline;
        // `BluetoothController.enableSecondaryNotifies()` stays available for
        // an explicit diagnostic session (and the callback logs any frame
        // that arrives on those channels).

        _state.value = GlassesCaptureState.Ready(captureDevice)
    }

    /**
     * Read the glasses' firmware version (FFF0 `0x10` device-info read,
     * sub-command `0x20`) and return it, or `null` if the link could not be
     * established or the glasses stayed silent for [timeoutMs].
     *
     * Read-only and safe at any time: it reuses [ensureConnected] (so it
     * cannot run while a capture owns the session — [capture] refuses to
     * start unless the state is [GlassesCaptureState.Ready]), sends one
     * 7-byte request, and accepts the answer either as the `0x10` Response
     * or from a `0x11` device-status notify, because V2.4.5 mirrors the
     * firmware TLV there too (vendor reference
     * `handleDeviceInfoPayload` / `handleDeviceStatusNotify`).
     *
     * This is the piece the firmware-upgrade UI needs before it can say
     * anything useful (which version is installed, and after a flash whether
     * the glasses actually moved), and it is the only part of the OTA story
     * that can be exercised without a firmware image.
     */
    suspend fun readFirmwareVersion(device: GlassesDevice, timeoutMs: Long = 5_000L): String? {
        readFirmwareVersionOnce(device, timeoutMs)?.let { return it }
        // Second chance. The glasses reboot at the end of a firmware upgrade
        // and the GATT can drop between reads, so a single silent attempt is
        // exactly the case where the user would otherwise have to restart the
        // App to get an answer.
        Log.i(TAG, "firmware version: no answer — forcing a fresh link and retrying once")
        bluetoothController.resetLink()
        if (_state.value is GlassesCaptureState.Ready) {
            _state.value = GlassesCaptureState.Idle
        }
        return readFirmwareVersionOnce(device, timeoutMs)
    }

    /** One attempt: ensure the link, ask `0x10|0x20`, wait [timeoutMs]. */
    private suspend fun readFirmwareVersionOnce(device: GlassesDevice, timeoutMs: Long): String? {
        ensureConnected(device)
        if (_state.value !is GlassesCaptureState.Ready) return null

        // Same single-subscription discipline as the capture pipeline: the
        // notify flow is replay=0, so the tap has to be live *before* the
        // request goes out or the answer lands in the void.
        val status = StatusFrames(scope.tapSharedFlow(bluetoothController.fff0Notifications))
        status.drainBuffered()
        try {
            val sent = bluetoothController.writeFff0(
                GlassesPhotoProtocol.buildFirmwareVersionRequestFrame(seq = FIRMWARE_READ_SEQ),
            )
            if (!sent) {
                Log.w(TAG, "firmware version request rejected by the stack")
                return null
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    Log.w(TAG, "firmware version: no answer within ${timeoutMs}ms")
                    return null
                }
                val frame = status.receiveWithin(remaining) ?: return null
                GlassesPhotoProtocol.parseFirmwareVersion(frame)?.let {
                    Log.d(TAG, "firmware version: $it")
                    return it
                }
            }
        } finally {
            status.stop()
        }
    }

    /**
     * Run one full capture cycle. Returns the FileProvider URI of the
     * JPEG on success, or `null` on failure (state transitions to
     * [GlassesCaptureState.Failed] with a human-readable reason).
     */
    suspend fun capture(): Uri? {
        if (_state.value is GlassesCaptureState.Capturing) return null
        val state = _state.value
        val ready = state as? GlassesCaptureState.Ready
        if (ready != null && !isGlassesLinkUsable(
                state,
                bluetoothController.connectionState.value,
                ready.device.address,
            )
        ) {
            // Same stale-Ready trap as ensureConnected(): saying "未连接眼镜"
            // and letting the Retry button re-run the pipeline (which
            // reconnects) beats sending 0x33 into a dead handle.
            _state.value = GlassesCaptureState.Idle
            return fail("蓝牙连接已断开", retryable = true).let { null }
        }
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
        val liveReady = ready
            ?: return fail("未连接眼镜", retryable = true).let { null }

        // HIGH for the duration of the session, exactly like the official
        // app's `prewarmAiPhotoBlePriority` / `boostAiPhotoConnectionPriority`
        // (both call requestGattConnectionPriority(1, …), i.e.
        // CONNECTION_PRIORITY_HIGH). The 2026-09-15 field log confirms it
        // works on Glass-D15 V2.4.5 + nova 6: the glass pushed all 83 blocks
        // of a 19 907 B JPEG in 0.78 s.
        //
        // The earlier "use BALANCED because HIGH costs 35 % of the blocks"
        // note that stood here was wrong — that loss was the FA12
        // subscription bug, not the radio (see runCapturePipeline).
        bluetoothController.requestPriority(android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH)

        val captureDevice = liveReady.device
        val startedMs = System.currentTimeMillis()

        activeCaptureJob = scope.launch(Dispatchers.IO) {
            try {
                runCapturePipeline(captureDevice, startedMs)
            } catch (e: CancellationException) {
                cleanupTempFile()
                // Cancellation was external — typically the user closed
                // the overlay mid-capture. Force state to a terminal so
                // the next capture() doesn't observe a stale Capturing
                // (smoke 2026-09-15 §P1 #3: capture() first line checks
                // _state.value is Capturing → return null → 90 s timeout
                // before the user can retry).
                if (_state.value is GlassesCaptureState.Capturing) {
                    _state.value = GlassesCaptureState.Failed("已取消", retryable = false)
                }
                throw e
            } catch (e: Throwable) {
                cleanupTempFile()
                fail(e.message ?: "未知错误", retryable = true)
            } finally {
                // Restore BALANCED when the session ends — the official app's
                // `restoreBlePriorityAfterAiPhoto` does exactly this
                // (requestGattConnectionPriority(0, …), guarded so it runs
                // once), and spec §3.2 / §2.4 require it on the failure path
                // as well. Holding HIGH past the transfer invites some ROMs
                // to rate-limit the next session's parameter update; the
                // next capture raises HIGH again at entry, so nothing is lost.
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
    /**
     * Turn the glasses' **hardware shutter button** into a capture, emitting
     * the image URI for each press.
     *
     * **Why a re-capture, not "the" photo.** The firmware does report the
     * press — a `0x11` device-status notify carrying the `mediaPhotoResult`
     * TLV (real frame captured on the device 2026-09-17:
     * `55aa15 11 03 0300 17 01 00` = type 0x17, len 1, value 0 = success) —
     * but it does **not** push that photo over BLE. The official app fetches
     * normal/button photos over FTP (`captureOnly` → `downloadPhotoFromFtp`)
     * or SPP/GFSP, i.e. a Wi-Fi/AP subsystem this App does not have.
     *
     * Until the firmware offers a BLE push for button shots (requested from
     * the vendor), the pragmatic answer is to fire the AI-photo path
     * (`0x33`) the moment the event lands: it returns the same scene ~1-2 s
     * later over the channel we already have, and the usual analysis +
     * spoken verdict follow.
     */
    fun watchShutterButton(device: GlassesDevice): Flow<Uri> = flow {
        // Only one watcher may be live at a time. The Home screen can be
        // composed more than once (recomposition, duplicate nav entries), and
        // with two watchers every press produces two 0x33 writes — the glasses
        // then reject both as busy (`err=1`) and nothing gets captured
        // (observed on the device 2026-09-17 08:35: two identical `Stage 1`
        // lines, both ending in "rejected again").
        shutterWatchMutex.withLock {
            while (currentCoroutineContext().isActive) {
            ensureConnected(device)
            if (_state.value !is GlassesCaptureState.Ready) {
                delay(RECONNECT_RETRY_MS)
                continue
            }
            // Long-lived tap: the notify flow is replay=0, so a frame that
            // arrives with no subscriber is gone, and the button can be
            // pressed at any time.
            val status = StatusFrames(scope.tapSharedFlow(bluetoothController.fff0Notifications))
            status.drainBuffered()
            try {
                while (currentCoroutineContext().isActive) {
                    val frame = status.receiveWithin(SHUTTER_TAP_REARM_MS) ?: break
                    if (!GlassesPhotoProtocol.reportsShutterPhoto(frame)) continue
                    Log.i(TAG, "glasses shutter button: taking an AI photo over BLE")
                    // The glasses often answer the first 0x33 of a button
                    // session with `0x51 FAILED` (or `err=1`) because they are
                    // still finishing the shot the wearer just took — seen on
                    // the device 2026-09-17 08:40:49 (press → FAILED 0.9 s
                    // later) while the press 7 s later succeeded. Give them a
                    // moment and try again before giving up on the press.
                    for (attempt in 1..MAX_BUTTON_CAPTURE_ATTEMPTS) {
                        ensureConnected(device)
                        if (_state.value !is GlassesCaptureState.Ready) {
                            delay(BUTTON_CAPTURE_RETRY_MS)
                            continue
                        }
                        val uri = capture()
                        if (uri != null) {
                            emit(uri)
                            break
                        }
                        if (attempt < MAX_BUTTON_CAPTURE_ATTEMPTS) {
                            Log.w(
                                TAG,
                                "button capture attempt $attempt failed — retrying in ${BUTTON_CAPTURE_RETRY_MS}ms",
                            )
                            delay(BUTTON_CAPTURE_RETRY_MS)
                        }
                    }
                    // capture() ran its own taps; re-arm for the next press.
                    break
                }
            } finally {
                status.stop()
            }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun cancel() {
        // Launch the cancel packet on the repository scope rather than the
        // capture job, which is being cancelled right now: without FA11
        // op4 the glasses keep pushing FA12 into a session nobody is
        // collecting (official `cancelAiPhotoBleTransfer`, spec §2.4).
        scope.launch(Dispatchers.IO) {
            try {
                bluetoothController.writeFa11(GlassesPhotoProtocol.buildFa11Cancel())
            } catch (_: Throwable) {
                // The link is usually what broke.
            }
        }
        activeCaptureJob?.cancel()
        cleanupTempFile()
    }

    /** Clear terminal state so the next [capture] can run. */
    fun reset() {
        cleanupTempFile()
        val current = _state.value
        when (current) {
            is GlassesCaptureState.Failed, is GlassesCaptureState.Success -> {
                _state.value =
                    if (bluetoothController.connectionState.value is BluetoothController.ConnectionState.Connected) {
                        val dev = (bluetoothController.connectionState.value
                            as BluetoothController.ConnectionState.Connected).device
                        GlassesCaptureState.Ready(GlassesCaptureDevice.from(dev))
                    } else {
                        GlassesCaptureState.Idle
                    }
            }
            // Defensive: if some path leaves _state stuck at Capturing
            // (e.g. a missed cancellation), drop to Idle so the next
            // capture() doesn't return null at its first-line guard
            // (smoke 2026-09-15 §P1 #3). cancel() handles the in-flight
            // job; reset() here just clears the flag.
            is GlassesCaptureState.Capturing -> {
                _state.value = GlassesCaptureState.Idle
            }
            else -> Unit
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
     * **This wrapper owns the receive taps; [runStages] does the work.**
     *
     * Both notify flows in [BluetoothController] are `SharedFlow`s with
     * `replay = 0`, which means an emission that finds no subscriber is
     * thrown away — and, the trap, `tryEmit` still reports that discard
     * as a success. [runStages] used to read FA12 with
     * `fa12Notifications.first()` once per loop iteration, so the
     * pipeline held a subscription only while it was suspended inside
     * that one call:
     *
     *   - everything the glasses pushed from `0x51 START` until the
     *     collector actually subscribed was lost (V2.4.5 leaves ~1.8 s
     *     between its warm-up START and the START that carries
     *     `file_size`) → the head of the JPEG was never filled, hence
     *     `filled=0/19907` in the 2026-09-15 15:59 log;
     *   - everything that landed while the loop was busy parsing,
     *     assembling or publishing [state] was lost too — at "chunk #20"
     *     the app had 20 blocks while the firmware was already at offset
     *     11 280 (block #47).
     *
     * Un-filled head bytes mean `GlassesPhotoStream.isComplete` can
     * never become true, so FA11 `0x03` is never written and the
     * firmware times the session out with `0x51 FAILED` ~8 s later.
     *
     * The tap below holds **one** subscription for the whole session and
     * republishes into an unbounded channel, making delivery as lossless
     * as the OEM reference, which reassembles synchronously in
     * `onCharacteristicChanged` and parks pre-START blocks in
     * `aiPhotoEarlyChunks` (`docs/glasses/zhang/.../BluetoothController.kt`
     * :1124-1160). The inbox is our `aiPhotoEarlyChunks`.
     *
     * @return `true` if the capture completed successfully — the
     *   caller reads [state] for the result URI.
     */
    private suspend fun runCapturePipeline(
        device: GlassesCaptureDevice,
        startedMs: Long,
    ): Boolean {
        val fa12Tap = scope.tapSharedFlow(bluetoothController.fa12Notifications)
        // Status frames parked by an earlier stage (the 0x33 Response
        // lands while we are still waiting for 0x51 START) so the next
        // stage still sees them.
        val status = StatusFrames(scope.tapSharedFlow(bluetoothController.fff0Notifications))
        // Blocks from an aborted previous session must not be allowed to
        // land in this one's stream — offsets are meaningless across
        // captures.
        fa12Tap.drainBuffered()
        status.drainBuffered()
        try {
            return runStages(fa12Tap, status, startedMs)
        } finally {
            fa12Tap.stop()
            status.stop()
        }
    }

    /**
     * [runCapturePipeline]'s body — stages 1-4. See the wrapper for the
     * receive-tap contract these stages rely on: both inboxes are already
     * subscribed before `0x33` goes out, so no notification can be missed
     * while a stage is being entered.
     */
    private suspend fun runStages(
        fa12Tap: ReceiveTap<ByteArray>,
        status: StatusFrames,
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
        //
        // Frames that are neither START nor FAILED (the `0x33` Response,
        // anything unparseable) are parked rather than discarded: the
        // stage-3 collector still has to see a `SUCCESS` that lands in
        // this window, and the whole point of the tap is that nothing
        // gets dropped between stages.
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.WaitingForStart,
                bytesReceived = 0, totalBytes = null,
                elapsedMs = System.currentTimeMillis() - startedMs,
            ),
        )
        var totalSize: Int? = null
        // The glasses decline a capture while they are busy — most obviously
        // right after the wearer pressed the shutter button (the button
        // watcher fires 0x33 in that exact moment, and V2.5.8 answers
        // `55aa003302010001`, err=1). The OEM retries once after 400 ms;
        // without this the first button press after a shot always failed.
        var busyRetries = 0
        val startDeadline = System.currentTimeMillis() + captureTimeoutMs
        while (totalSize == null) {
            val remaining = startDeadline - System.currentTimeMillis()
            val frame = status.receiveWithin(remaining.coerceAtLeast(1L))
            if (frame == null) {
                Log.w(TAG, "0x51 START not received within ${captureTimeoutMs}ms")
                cleanupTempFile()
                fail("等待 START 超时", retryable = true)
                return false
            }
            val hex = frame.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            val ackError = GlassesPhotoProtocol.captureAckError(frame)
            if (ackError != null && ackError != 0) {
                if (busyRetries < MAX_BUSY_RETRIES) {
                    // Growing backoff: the glasses are busy with the very photo
                    // the wearer just took, and that window is longer than the
                    // OEM's single 400 ms retry (V2.5.8 answered err=1 twice in
                    // a row on the device).
                    val delayMs = BUSY_RETRY_DELAY_MS shl busyRetries
                    busyRetries++
                    Log.w(TAG, "0x33 rejected (err=$ackError) — retry #$busyRetries after ${delayMs}ms")
                    delay(delayMs)
                    val resent = bluetoothController.writeFff0(
                        GlassesPhotoProtocol.buildCaptureRequestFrame(seq = 0x00),
                    )
                    Log.d(TAG, "0x33 retry writeFff0 returned: $resent")
                    if (!resent) {
                        cleanupTempFile()
                        fail("0x33 重试发送失败", retryable = true)
                        return false
                    }
                    continue
                }
                Log.w(TAG, "0x33 rejected $busyRetries time(s) (err=$ackError) — giving up")
                cleanupTempFile()
                fail("眼镜拒绝拍照 (err=$ackError)", retryable = true)
                return false
            }
            when (val notify = GlassesPhotoProtocol.parseStatusNotify(frame)) {
                is GlassesPhotoProtocol.StatusNotify.Start -> {
                    Log.d(TAG, "0x51 START received: ${frame.size} bytes; raw=$hex")
                    val announced = notify.fileSize
                    totalSize = announced
                    if (announced != null && announced !in 1..MAX_AI_PHOTO_BYTES) {
                        // Boundary validation, and it is the firmware's own
                        // number: GlassesPhotoStream allocates exactly
                        // file_size bytes, so a corrupt u32 here would try to
                        // reserve up to 4 GB. The official app applies the
                        // same 2 MiB ceiling in beginAiPhotoReceive.
                        Log.w(TAG, "0x51 START declared fileSize=$announced — out of range")
                        cleanupTempFile()
                        fail("眼镜上报的文件大小异常 ($announced)", retryable = true)
                        return false
                    }
                    // If the firmware omitted fileSize in this START, loop
                    // and wait for the next one (V2.4.5 sends a second
                    // START ~1.8 s later that does carry it). Don't waste
                    // the warm-up START — it's still firmware confirmation
                    // that the capture request was accepted. Meanwhile FA12
                    // keeps accumulating in fa12Tap.
                    if (totalSize == null) {
                        Log.d(TAG, "0x51 START without fileSize — waiting for the next one")
                    }
                }
                is GlassesPhotoProtocol.StatusNotify.Failed -> {
                    Log.w(TAG, "0x51 FAILED while waiting for START: raw=$hex")
                    cleanupTempFile()
                    fail("眼镜拒绝拍照 (code=${notify.code})", retryable = true)
                    return false
                }
                else -> {
                    Log.d(TAG, "frame while waiting for START, parked: raw=$hex")
                    status.park(frame)
                }
            }
            if (totalSize == null && System.currentTimeMillis() >= startDeadline) {
                Log.w(TAG, "0x51 START-with-fileSize not received within ${captureTimeoutMs}ms " +
                    "(last frames seen were parked/without size)")
                cleanupTempFile()
                fail("等待 START 超时", retryable = true)
                return false
            }
        }

        // ── Stage 3: collect FA12 chunks ─────────────────────────────
        // `totalSize` is non-null here: the wait loop above only exits
        // with a START that carried `file_size`, or returns false. The
        // old `GlassesPhotoStream(1)` placeholder is gone — with chunks
        // now buffered *before* START (see runCapturePipeline), a
        // placeholder stream would have tripped the resize path and
        // thrown away the whole back-buffered prefix.
        var stream = GlassesPhotoStream(totalSize)
        _state.value = GlassesCaptureState.Capturing(
            CaptureProgress(
                stage = CaptureProgress.Stage.ReceivingChunks,
                bytesReceived = 0, totalBytes = totalSize,
                elapsedMs = System.currentTimeMillis() - startedMs,
            ),
        )

        // Reassembly + stall repair lives in collectFa12Chunks so it can
        // be driven against a simulated firmware without a Context
        // (GlassesFa12CollectorTest).
        when (val outcome = collectFa12Chunks(
            stream = stream,
            fa12 = fa12Tap,
            status = status,
            // Gap repair is the one FA11 opcode whose throughput the user
            // actually feels (a lossy burst leaves tens to hundreds of
            // blocks behind, and each request buys 1-3 of them). Send those
            // as ATT write commands when the firmware says FA11 accepts
            // them, so the repair is not capped at one round trip per block.
            // CRC (0x03) and cancel (0x04) stay write-with-response: they
            // are single, terminal, and worth a delivery guarantee.
            writeFa11 = { payload ->
                bluetoothController.writeFa11(
                    payload,
                    noResponse = payload.isNotEmpty() &&
                        payload[0] == GlassesPhotoProtocol.FA11_OP_RESEND,
                )
            },
            onProgress = { filled, total, repairing ->
                _state.value = GlassesCaptureState.Capturing(
                    CaptureProgress(
                        stage = if (repairing) {
                            CaptureProgress.Stage.Repairing
                        } else {
                            CaptureProgress.Stage.ReceivingChunks
                        },
                        bytesReceived = filled,
                        totalBytes = total,
                        elapsedMs = System.currentTimeMillis() - startedMs,
                    ),
                )
            },
            // The session asked for HIGH at entry; if Android granted a slow
            // interval anyway, the first block is the moment to ask again
            // (spec §3.3.3 / OEM maybeRetryAiPhotoHighOnFirstChunk).
            onFirstBlock = bluetoothController::boostPriorityIfFirstFa12StillSlow,
            chunkStallMs = chunkStallMs,
            resendWaitMs = resendWaitMs,
            maxRepairCycles = maxRepairCycles,
            timeoutMs = captureTimeoutMs,
            resendBatchSize = resendBatchSize,
            resendStrideBytes = resendStrideBytes,
            repairNoProgressMs = repairNoProgressMs,
        )) {
            is Fa12Collection.Complete -> stream = outcome.stream
            is Fa12Collection.CompletedWithGaps -> stream = outcome.stream
            is Fa12Collection.Failed -> {
                // fail() drops the temp file and publishes the reason.
                fail(outcome.reason, retryable = true)
                return false
            }
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

/**
 * Is the repository's [repositoryState] backed by a **live** link to
 * [address]?
 *
 * [GlassesPhotoCaptureRepository.GlassesCaptureState.Ready] alone only means
 * "we reached Ready at some point". The glasses reboot at the end of a
 * firmware upgrade and the GATT can drop at any moment, and neither event
 * demotes the repository state — so every caller that wants to *use* the
 * link has to check the controller's live connection state too (user report
 * 2026-09-17: after the OTA reboot the firmware-version read timed out until
 * the App was restarted).
 *
 * Pure and file-scope so the rule is unit-testable without Android.
 */
internal fun isGlassesLinkUsable(
    repositoryState: GlassesPhotoCaptureRepository.GlassesCaptureState,
    connectionState: BluetoothController.ConnectionState,
    address: String,
): Boolean {
    if (repositoryState !is GlassesPhotoCaptureRepository.GlassesCaptureState.Ready) return false
    if (connectionState !is BluetoothController.ConnectionState.Connected) return false
    return connectionState.device.address.equals(address, ignoreCase = true)
}
