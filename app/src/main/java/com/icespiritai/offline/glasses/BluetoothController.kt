package com.icespiritai.offline.glasses

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single-device BLE GATT orchestrator for the smart glasses.
 *
 * Owns one [BluetoothGatt] connection at a time, exposes connection
 * lifecycle as a [StateFlow] (consumed by [GlassesPhotoCaptureRepository]
 * for state-machine transitions and by [GlassesCaptureOverlay] for the
 * spinner UI), and routes notification payloads through two typed
 * [SharedFlow]s ([fff0Notifications] / [fa12Notifications]) — one per
 * characteristic the firmware uses.
 *
 * **Threading.** The `BluetoothGattCallback` runs on the Android main
 * thread (Binder thread). All [StateFlow] mutations here are thread-safe
 * by construction, and [SharedFlow.tryEmit] is non-blocking. Suspending
 * GATT operations ([requestMtu] / [discoverServices] / [writeFff0] etc.)
 * resolve their backing [CompletableDeferred] from the callback, so
 * callers don't need to manage their own waits.
 *
 * **Lifetime.** [release] **must** be called when the owning Activity /
 * ViewModel tears down — otherwise the [BluetoothGatt] reference leaks
 * and the system-level slot stays held. Calling [release] multiple times
 * is a no-op.
 *
 * **Process / scope note.** The engine this orchestrates (PP-OCRv6_small)
 * holds process-wide native resources. Per the same precedent as
 * `IceSpiritVisionViewModel.onCleared` ([IceSpiritVisionViewModel.kt:353]),
 * this class never explicitly closes anything except the GATT handle —
 * the engine's lifecycle is owned by [AppGraph].
 */
class BluetoothController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceStore: GlassesDeviceStore,
) {

    // ────────────────────────────────────────────────────────────────────
    // BLE service / characteristic UUIDs
    // ────────────────────────────────────────────────────────────────────

    /**
     * UUIDs for the two GATT services the firmware exposes. These match
     * the firmware spec in `docs/glasses/AI识图传图提速_App连接参数配合.md` §6.
     *
     * If the actual firmware uses different characteristic handles (e.g.
     * FFF3 instead of FFF1 for write), adjust the constants here — the
     * rest of the pipeline (Repository / Protocol / Stream) treats them
     * as opaque handles.
     */
    companion object {
        private const val TAG = "BluetoothController"
        private val FFF0_SERVICE_UUID: UUID = uuid16(0xFFF0)
        private val FFF1_CHAR_UUID: UUID = uuid16(0xFFF1)   // write — App → Glass (0x33 Request)
        private val FFF2_CHAR_UUID: UUID = uuid16(0xFFF2)   // notify — Glass → App (0x33 Response + 0x51 Status)
        private val FA10_SERVICE_UUID: UUID = uuid16(0xFA10)
        private val FA11_CHAR_UUID: UUID = uuid16(0xFA11)   // write — App → Glass (op2/op3/op4)
        private val FA12_CHAR_UUID: UUID = uuid16(0xFA12)   // notify — Glass → App (JPEG chunks)
        private val CCCD_UUID: UUID = uuid16(0x2902)        // standard CCCD

        /** Build a Bluetooth UUID from a 16-bit short form. */
        private fun uuid16(short: Int): UUID =
            UUID.fromString("0000%04X-0000-1000-8000-00805F9B34FB".format(short))

        /**
         * MTU floor required by the protocol. The firmware will refuse
         * to start a transfer below this. See plan §D5 + CLAUDE.md
         * §"Instrumented test" PHOTO_BLE_MIN_MTU.
         */
        const val REQUIRED_MIN_MTU: Int = 247
        const val DESIRED_MTU: Int = 517

        /**
         * Max FA12 CCCD retry attempts on a non-success write. Each
         * retry waits [FA12_CCCD_RETRY_DELAY_MS]. Mirrors OEM
         * reference (`fa12CccdRetryCount < 2` in zhang).
         */
        private const val FA12_CCCD_MAX_RETRIES: Int = 2

        /**
         * Backoff between FA12 CCCD retries (ms). 200 ms is the OEM
         * value — long enough for the GATT server to drain its
         * descriptor-write queue, short enough to keep the connect
         * path under ~600 ms total (FFF2 + 80 ms + 2 × 200 ms).
         */
        private const val FA12_CCCD_RETRY_DELAY_MS: Long = 200L

        /**
         * Emission buffer for the FA12 notify flow. Sized for a whole
         * photo, not for "a brief consumer stall": at the documented
         * ~240 B/block (spec §2.1) a 30 KB JPEG is ~128 blocks, and the
         * V2.4.5 firmware streams them back-to-back in under a second
         * once the link is at HIGH priority. 512 slots keeps
         * [kotlinx.coroutines.flow.MutableSharedFlow.tryEmit] from
         * returning `false` for the entire session even if the
         * downstream coroutine is descheduled for the whole transfer.
         */
        private const val FA12_EMIT_BUFFER: Int = 512

        /**
         * Sentinel for [lastConnInterval] before any `onConnectionUpdated`
         * has arrived. The OEM app defaults its copy to 0 and bails out of
         * the first-chunk re-boost on `interval < 1` rather than guessing —
         * an unobserved interval is not evidence that the link is slow.
         */
        private const val INTERVAL_NOT_OBSERVED = 0
    }

    // ────────────────────────────────────────────────────────────────────
    // Connection-state data classes
    // ────────────────────────────────────────────────────────────────────

    sealed class ConnectionState {
        object Idle : ConnectionState()
        data class Connecting(val device: GlassesDevice) : ConnectionState()
        data class Connected(val device: GlassesDevice, val mtu: Int) : ConnectionState()
        object Disconnected : ConnectionState()
        data class Failed(val reason: String) : ConnectionState()
    }

    // ────────────────────────────────────────────────────────────────────
    // Public reactive surface
    // ────────────────────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastConnInterval = MutableStateFlow(INTERVAL_NOT_OBSERVED)
    /**
     * Most recent BLE connection interval in **1.25 ms units**: 32 = 40 ms
     * (the BALANCED default the glasses link sits at), 6–12 = 7.5–15 ms
     * (what HIGH is supposed to buy, spec §4.1). [INTERVAL_NOT_OBSERVED]
     * means no `onConnectionUpdated` has arrived yet, which is *not* the
     * same thing as "slow" — see [shouldReboostHighOnFirstFa12].
     *
     * The value is fed by [BluetoothGattCallback.onConnectionUpdated],
     * which is **not in the compileSdk stub** and so cannot be marked
     * `override` here. Declaring the matching signature on a
     * [BluetoothGattCallback] subclass is the usual way to receive a
     * hidden framework callback: whether the platform actually dispatches
     * it on a given ROM can only be settled on device. The `FA12 first
     * block, interval=` log below is the discriminator — if it never
     * leaves [INTERVAL_NOT_OBSERVED], the hook is dead on that device and
     * the interval-driven re-boost stays inert by design.
     */
    val lastConnInterval: StateFlow<Int> = _lastConnInterval.asStateFlow()

    private val _mtu = MutableStateFlow(23)                // BLE default before negotiation
    /** Current ATT MTU after [requestMtu] resolves. */
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    private val _fff0Notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    /**
     * FFF0 (management channel) notify payloads — 0x33 Response and 0x51
     * Status notifications from the glasses. Feed these to
     * [GlassesPhotoProtocol.parseStatusNotify] (or a 0x33-response
     * handler) inside [GlassesPhotoCaptureRepository].
     */
    val fff0Notifications: SharedFlow<ByteArray> = _fff0Notify.asSharedFlow()

    private val _fa12Notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = FA12_EMIT_BUFFER)
    /**
     * FA12 (photo data) notify payloads — each emission is one JPEG
     * chunk in the wire format documented in
     * [GlassesPhotoProtocol.parsePhotoChunk].
     *
     * **Consumers must hold a single subscription for as long as they
     * want to keep receiving.** This flow is `replay = 0`: a payload
     * emitted while nobody is subscribed is **discarded immediately, and
     * [android.bluetooth.BluetoothGattCallback]-side
     * [kotlinx.coroutines.flow.MutableSharedFlow.tryEmit] still returns
     * `true`** — so the loss is invisible in the logs. The buffer above
     * only protects a *subscribed but momentarily slow* consumer.
     *
     * In particular `flow.first()` is not a safe way to read this: it
     * subscribes, takes one value and unsubscribes, so every chunk that
     * lands in the gap between two calls is gone. That is what broke
     * the FA10 transfer before 2026-09-15 (see
     * [GlassesPhotoCaptureRepository.runCapturePipeline], which taps
     * this flow once per capture session into an unbounded channel).
     */
    val fa12Notifications: SharedFlow<ByteArray> = _fa12Notify.asSharedFlow()

    // ────────────────────────────────────────────────────────────────────
    // Internal GATT state
    // ────────────────────────────────────────────────────────────────────

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var gatt: BluetoothGatt? = null

    /** Pending one-shot GATT operations, completed by the matching callback. */
    private var pendingMtu: CompletableDeferred<Int>? = null
    private var pendingServices: CompletableDeferred<Boolean>? = null
    private var pendingCharWrite: CompletableDeferred<Boolean>? = null
    private var pendingDescriptorWrite: CompletableDeferred<Boolean>? = null

    /**
     * FA12 CCCD write retry counter. Some ROMs (Huawei nova 6 /
     * HarmonyOS in particular) return `0` (GATT_SUCCESS-ish but
     * with the descriptor not actually subscribed) on the first CCCD
     * write when it follows immediately after a FFF2 CCCD write —
     * the GATT server hasn't finished processing the prior descriptor
     * yet. The OEM reference app retries up to 2 times with a 200 ms
     * backoff; we mirror that behaviour. Reset to 0 on each fresh
     * `enableFa12Notify` call and on a successful write.
     */
    private var fa12CccdRetryCount: Int = 0

    /**
     * Main-thread Handler for posting delayed CCCD write retries. The
     * GATT callback fires on a Binder thread; retry scheduling needs
     * a looper-backed Handler so the delayed Runnable still runs even
     * if the original caller's coroutine was cancelled.
     */
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    /**
     * Serializes GATT writes. Concurrent writeCharacteristic() calls
     * would clobber each other's `pendingCharWrite` deferred (smoke 12
     * 2026-09-15: 8 op2 fanout writes → only the first completed,
     * the rest hung). See writeCharacteristic() for the long version.
     */
    private val writeMutex = Mutex()

    /**
     * Cached characteristic + descriptor handles after
     * [discoverServices] succeeds. Avoids re-scanning the GATT table
     * on every write.
     */
    private var fff1Char: BluetoothGattCharacteristic? = null
    private var fa11Char: BluetoothGattCharacteristic? = null
    private var fff2Desc: BluetoothGattDescriptor? = null
    private var fa12Desc: BluetoothGattDescriptor? = null

    /** Address currently connecting / connected; cleared on disconnect / release. */
    private var currentDevice: GlassesDevice? = null

    // ────────────────────────────────────────────────────────────────────
    // Public API — connect / disconnect / priority
    // ────────────────────────────────────────────────────────────────────

    /**
     * Open a GATT connection to [device]. Suspends until the connection
     * either succeeds, fails, or is cancelled by the caller. Subsequent
     * calls while already connected to **the same** device are no-ops;
     * calls targeting a **different** device first close the existing
     * GATT handle to avoid leaking the radio slot.
     *
     * **Multi-glasses note.** This controller holds a single GATT
     * connection at a time (v1 architecture — see plan §D1). The
     * "close-then-reconnect" guard here prevents the obvious leak where
     * `gatt = btDevice.connectGatt(...)` overwrites a previous handle
     * without releasing it. For concurrent multi-glasses support, swap
     * to a per-device `BluetoothController` instance — out of v1 scope.
     *
     * Side effects on success:
     *   - records the device address in [GlassesDeviceStore]
     *   - does **not** auto-discover services or enable notifies — caller
     *     must explicitly call [discoverServices] / [enableFff0Notify] /
     *     [enableFa12Notify] after this resolves.
     */
    suspend fun connect(device: GlassesDevice) {
        when (val s = _connectionState.value) {
            is ConnectionState.Connecting -> if (s.device.address == device.address) return
            is ConnectionState.Connected -> if (s.device.address == device.address) return
            else -> Unit
        }
        val a = adapter ?: run {
            _connectionState.value = ConnectionState.Failed("BluetoothAdapter unavailable")
            return
        }
        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.Failed("BLUETOOTH_CONNECT permission not granted")
            return
        }

        // Tear down any existing handle before opening a new one. Without
        // this, switching devices leaks the previous GATT handle and the
        // radio slot stays reserved until process death.
        gatt?.close()
        gatt = null
        cancelPendingDeferreds()

        _stateResetBeforeReconnect()

        _connectionState.value = ConnectionState.Connecting(device)
        currentDevice = device
        val btDevice: BluetoothDevice = a.getRemoteDevice(device.address)
        // Force LE transport. The deprecated 3-arg connectGatt overload
        // defaults to BR/EDR (Classic BT), which causes PAGE_TIMEOUT
        // failures on LE-only peripherals like Glass-D15 / Glasses-A88 —
        // real-device smoke test on 2026-09-14 (nova 6) caught this:
        // 8-second PAGE_TIMEOUT → GATT_CONN_L2C_FAILURE → BLE pipeline
        // never reached. The 4-arg overload with TRANSPORT_LE is the
        // correct call for a BLE-only smart-glasses peripheral.
        gatt = btDevice.connectGatt(
            context,
            /* autoConnect = */ false,
            callback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    /**
     * Request a clean disconnect. Idempotent. After this returns the GATT
     * is still open until [BluetoothGattCallback.onConnectionStateChange]
     * fires with `STATE_DISCONNECTED`; [release] is what fully tears down.
     */
    fun disconnect() {
        gatt?.disconnect()
    }

    /**
     * Hint the Android BLE stack to prefer a faster connection interval
     * (HIGH = 7.5–15 ms) or revert to BALANCED (~40 ms).
     *
     * Per `docs/glasses/AI识图传图提速_App连接参数配合.md` §3: call once at
     * session start, once on end; first FA12 chunk received under still-
     * 40 ms interval → call again. The push-based [lastConnInterval]
     * StateFlow is what the caller polls to detect the "still slow"
     * case.
     *
     * Returns `false` if the OS rejected the request (typically because
     * the radio is currently negotiating another parameter update).
     * The caller should retry once after ~200 ms; further failures are
     * benign — they fall back to the BALANCED speed (3.6 s transfer).
     */
    fun requestPriority(priority: Int): Boolean {
        return gatt?.requestConnectionPriority(priority) ?: false
    }

    /**
     * One-shot HIGH re-push for the case spec §3.3.3 describes and the OEM
     * app implements in `maybeRetryAiPhotoHighOnFirstChunk`: the session
     * asked for HIGH at entry, Android granted something slower, and the
     * first photo block is proof the transfer is about to run at 40 ms.
     *
     * Call once per session, on the first FA12 block
     * ([GlassesFa12Collector] guarantees the single call). Returns whether
     * a request was actually made, so the caller can log which world it is
     * in: `false` means the interval was never observed (hidden
     * `onConnectionUpdated` not firing on this ROM) or was already fast —
     * in both cases there is nothing to correct, and pushing anyway would
     * just burn the ROM's rate limit.
     */
    fun boostPriorityIfFirstFa12StillSlow(): Boolean {
        val interval = _lastConnInterval.value
        if (!shouldReboostHighOnFirstFa12(interval)) {
            Log.d(TAG, "FA12 first block, interval=$interval — no HIGH re-push")
            return false
        }
        Log.w(TAG, "FA12 first block, interval=$interval still slow — re-pushing HIGH")
        return requestPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    /**
     * Negotiate the ATT MTU. Suspends until the OS completes the
     * exchange. Throws if not currently connected.
     *
     * Pre-MTU delay of 200 ms mirrors the OEM reference app
     * (zhang BluetoothController.onConnectionStateChange). Some ROMs
     * (notably the Huawei nova 6 / HarmonyOS stack under Glass-D15
     * V2.4.5) drop the MTU exchange if it's fired within the first
     * ~100 ms of STATE_CONNECTED — the link layer is still settling
     * the encryption / parameter update. Waiting 200 ms is empirically
     * the smallest window that consistently grants the negotiated MTU
     * without changing the negotiated value.
     */
    suspend fun requestMtu(target: Int): Int {
        delay(200L)
        val g = gatt ?: error("not connected")
        pendingMtu = CompletableDeferred()
        g.requestMtu(target)
        return pendingMtu!!.await()
    }

    /**
     * Trigger GATT service discovery. Suspends until the callback
     * resolves. The characteristic / descriptor handles are cached on
     * this controller so subsequent writes don't re-walk the table.
     */
    suspend fun discoverServices(): Boolean {
        val g = gatt ?: error("not connected")
        pendingServices = CompletableDeferred()
        g.discoverServices()
        val ok = pendingServices!!.await()
        if (ok) cacheCharacteristicHandles(g)
        return ok
    }

    /** Enable FFF2 notify (CCCd write). Suspends until OS confirms. */
    suspend fun enableFff0Notify(): Boolean = enableNotify(FFF0_SERVICE_UUID, FFF2_CHAR_UUID) {
        fff2Desc = it
    }

    /**
     * Enable FA12 notify (CCCd write). Suspends until OS confirms.
     *
     * Pre-write delay of 80 ms mirrors the OEM reference app (zhang
     * BluetoothController.onDescriptorWrite). Writing the FA12 CCCD
     * back-to-back after the FFF2 CCCD races the GATT server's
     * descriptor-write queue on some ROMs — the second write returns
     * success-but-not-subscribed. The OEM's fix is to schedule the
     * FA12 write 80 ms after the FFF2 callback resolves; we approximate
     * that with a flat `delay(80)` since `enableFa12Notify` is always
     * called immediately after `enableFff0Notify` in the
     * `ensureConnected` pipeline.
     *
     * Also resets [fa12CccdRetryCount] so a fresh attempt starts at 0.
     * The retry itself is handled inside
     * `BluetoothGattCallback.onDescriptorWrite`.
     */
    suspend fun enableFa12Notify(): Boolean {
        fa12CccdRetryCount = 0
        delay(80L)
        return enableNotify(FA10_SERVICE_UUID, FA12_CHAR_UUID) {
            fa12Desc = it
        }
    }

    /**
     * Write a 0x33 capture-request frame to FFF1. Suspends until the OS
     * confirms the write completed.
     */
    suspend fun writeFff0(payload: ByteArray): Boolean =
        writeCharacteristic(FFF1_CHAR_UUID, payload)

    /**
     * Write an FA11 control opcode (resend / CRC / cancel) to FA11.
     * Suspends until the OS confirms.
     */
    suspend fun writeFa11(payload: ByteArray): Boolean {
        Log.d(TAG, "FA11 write size=${payload.size} raw=" + payload.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        val ok = writeCharacteristic(FA11_CHAR_UUID, payload)
        Log.d(TAG, "FA11 write ok=$ok")
        return ok
    }

    /**
     * Close the GATT handle and reset to Idle. Idempotent. After this,
     * any in-flight pending * coroutine resolves with an exception; the
     * caller is expected to cancel and re-create the controller rather
     * than reuse it.
     */
    fun release() {
        gatt?.close()
        gatt = null
        currentDevice = null
        fff1Char = null
        fa11Char = null
        fff2Desc = null
        fa12Desc = null
        fa12CccdRetryCount = 0
        _connectionState.value = ConnectionState.Idle
        cancelPendingDeferreds()
    }

    /** Cancel any straggler pending coroutines so callers waiting on
     *  them get a CancellationException rather than hanging.
     */
    private fun cancelPendingDeferreds() {
        pendingMtu?.cancel()
        pendingServices?.cancel()
        pendingCharWrite?.cancel()
        pendingDescriptorWrite?.cancel()
        pendingMtu = null
        pendingServices = null
        pendingCharWrite = null
        pendingDescriptorWrite = null
    }

    /**
     * Reset per-connection cached state before opening a new GATT
     * handle. Service discovery results, characteristic handles, and
     * pending async operations all become invalid when the GATT
     * instance changes.
     */
    private fun _stateResetBeforeReconnect() {
        fff1Char = null
        fa11Char = null
        fff2Desc = null
        fa12Desc = null
        fa12CccdRetryCount = 0
        _lastConnInterval.value = INTERVAL_NOT_OBSERVED
        _mtu.value = 23
    }

    // ────────────────────────────────────────────────────────────────────
    // Internals
    // ────────────────────────────────────────────────────────────────────

    private fun cacheCharacteristicHandles(gatt: BluetoothGatt) {
        fff1Char = gatt.getService(FFF0_SERVICE_UUID)?.getCharacteristic(FFF1_CHAR_UUID)
        fff2Desc = gatt.getService(FFF0_SERVICE_UUID)
            ?.getCharacteristic(FFF2_CHAR_UUID)
            ?.getDescriptor(CCCD_UUID)
        fa11Char = gatt.getService(FA10_SERVICE_UUID)?.getCharacteristic(FA11_CHAR_UUID)
        fa12Desc = gatt.getService(FA10_SERVICE_UUID)
            ?.getCharacteristic(FA12_CHAR_UUID)
            ?.getDescriptor(CCCD_UUID)
    }

    private suspend fun enableNotify(
        serviceUuid: UUID,
        charUuid: UUID,
        cacheDesc: (BluetoothGattDescriptor) -> Unit,
    ): Boolean {
        val g = gatt ?: error("not connected")
        val service: BluetoothGattService = g.getService(serviceUuid)
            ?: error("service $serviceUuid not found")
        val ch = service.getCharacteristic(charUuid)
            ?: error("characteristic $charUuid not found")
        g.setCharacteristicNotification(ch, true)
        val desc = ch.getDescriptor(CCCD_UUID)
            ?: error("CCCD not found on $charUuid")
        cacheDesc(desc)
        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        pendingDescriptorWrite = CompletableDeferred()
        g.writeDescriptor(desc)
        return pendingDescriptorWrite!!.await()
    }

    private suspend fun writeCharacteristic(
        charUuid: UUID,
        payload: ByteArray,
    ): Boolean {
        // Serialize writes — `pendingCharWrite` is a single var, so
        // concurrent writeCharacteristic() calls would clobber each
        // other's deferreds: call A sets deferred_A, call B sets
        // deferred_B; the OS GATT callback for write A fires while
        // pendingCharWrite == deferred_B and completes deferred_B
        // (the wrong one), leaving deferred_A unresolved forever.
        // The Repository's resend fanout (smoke 12, 2026-09-15)
        // triggered this — 8 op2 writes fired in <1 ms, only the
        // first round's first write completed; the rest hung. A
        // Mutex around the whole write body makes calls 2..N block
        // until call 1's await() returns, then run safely. Cheap
        // (one local Mutex, no per-call allocation).
        writeMutex.lock()
        try {
            val g = gatt ?: error("not connected")
            // Look up by UUID since the cache may have been invalidated
            // by a service-change indication. Cheap linear scan.
            val ch: BluetoothGattCharacteristic = g.services
                ?.mapNotNull { it.getCharacteristic(charUuid) }
                ?.firstOrNull()
                ?: error("characteristic $charUuid not found")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ch.value = payload
            pendingCharWrite = CompletableDeferred()
            g.writeCharacteristic(ch)
            // Bound the wait so a stuck callback can't pin the
            // collector forever. 2 s is generous for a single
            // GATT write (typical <50 ms); on timeout we return
            // false so the fanout can try the next gap.
            val ok = withTimeoutOrNull(2_000L) {
                pendingCharWrite!!.await()
            } ?: false
            pendingCharWrite = null
            return ok
        } finally {
            writeMutex.unlock()
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-31 doesn't require an explicit permission for GATT — the
            // legacy BLUETOOTH permission grants it.
            true
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val dev = currentDevice
                    if (dev != null) deviceStore.saveLastPaired(dev.address)
                    _connectionState.value = dev?.let { ConnectionState.Connected(it, _mtu.value) }
                        ?: ConnectionState.Failed("connected but no device")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS &&
                newState != BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Failed("status=$status newState=$newState")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status services=${gatt.services.size}")
            gatt.services.forEach { svc ->
                Log.d(TAG, "  service ${svc.uuid} chars=${svc.characteristics.size}")
            }
            pendingServices?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _mtu.value = mtu
                (currentDevice?.let { dev ->
                    (_connectionState.value as? ConnectionState.Connected)?.let {
                        _connectionState.value = ConnectionState.Connected(dev, mtu)
                    }
                })
            }
            pendingMtu?.complete(mtu)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val data = characteristic.value ?: return
            when (characteristic.uuid) {
                FFF2_CHAR_UUID -> {
                    val ok = _fff0Notify.tryEmit(data)
                    Log.d(
                        TAG,
                        "FFF2 notify size=${data.size} tryEmit=$ok raw=" +
                            data.joinToString("") { "%02x".format(it.toInt() and 0xFF) },
                    )
                }
                FA12_CHAR_UUID -> {
                    // Sample the subscriber count *before* tryEmit: with
                    // replay=0 a zero-subscriber emission is discarded
                    // even though tryEmit reports success, and that was
                    // the invisible half of the pre-2026-09-15 transfer
                    // failures.
                    val subscribers = _fa12Notify.subscriptionCount.value
                    val ok = _fa12Notify.tryEmit(data)
                    when {
                        !ok -> Log.w(
                            TAG,
                            "FA12 notify LOST size=${data.size} — emit buffer full " +
                                "(subscribers=$subscribers)",
                        )
                        subscribers == 0 -> Log.w(
                            TAG,
                            "FA12 notify LOST size=${data.size} — no subscriber " +
                                "(replay=0 discards it; tryEmit=true is misleading)",
                        )
                        data.size < 8 || data.size % 4 != 0 ->
                            Log.d(TAG, "FA12 notify size=${data.size} (unusual size)")
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingCharWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            when (descriptor.characteristic?.uuid) {
                FA12_CHAR_UUID -> handleFa12CccdWrite(gatt, descriptor, status)
                else -> pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        /**
         * Handle a CCCD write completion on FA12 with up to 2 retries
         * (200 ms backoff) when the OS reports non-success. Mirrors
         * the OEM reference app's `fa12CccdRetryCount` logic in zhang
         * BluetoothController.onDescriptorWrite. Only the FA12 path is
         * retried — FFF2 CCCD failures are surfaced immediately because
         * the management channel is far less failure-prone and we
         * don't want a buggy FA12 to mask a real FFF2 issue.
         *
         * The retry is scheduled on the main-thread Handler so it
         * survives even if the awaiting coroutine is cancelled (the
         * pending deferred is left pending until the retry resolves or
         * gives up).
         */
        private fun handleFa12CccdWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                fa12CccdRetryCount = 0
                pendingDescriptorWrite?.complete(true)
                return
            }
            if (fa12CccdRetryCount < FA12_CCCD_MAX_RETRIES) {
                fa12CccdRetryCount++
                Log.w(
                    TAG,
                    "FA12 CCCD write status=$status, retry #$fa12CccdRetryCount in ${FA12_CCCD_RETRY_DELAY_MS}ms",
                )
                mainHandler.postDelayed({
                    // Bail if the controller was released or the GATT
                    // handle was swapped in the meantime — firing a
                    // write against a stale handle is a guaranteed
                    // SecurityException / GATT_FAILURE storm.
                    if (this@BluetoothController.gatt !== gatt) {
                        Log.d(TAG, "FA12 CCCD retry aborted: gatt handle changed")
                        pendingDescriptorWrite?.complete(false)
                        return@postDelayed
                    }
                    try {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    } catch (e: Throwable) {
                        Log.w(TAG, "FA12 CCCD retry threw", e)
                        pendingDescriptorWrite?.complete(false)
                    }
                }, FA12_CCCD_RETRY_DELAY_MS)
            } else {
                Log.e(TAG, "FA12 CCCD write failed after $FA12_CCCD_MAX_RETRIES retries")
                pendingDescriptorWrite?.complete(false)
            }
        }

        fun onConnectionUpdated(
            gatt: BluetoothGatt,
            interval: Int,
            latency: Int,
            timeout: Int,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _lastConnInterval.value = interval
            }
        }
    }
}

/**
 * Connection interval (1.25 ms units) at or above which the OEM glasses
 * app re-pushes HIGH once per capture session, on the assumption the link
 * is still at the 40 ms BALANCED default.
 *
 * `PHOTO_BLE_FAST_INTERVAL_MAX + 1` in the OEM binary
 * (`com.deepvision_tek.glass_front` 3.1.00): `maybeRetryAiPhotoHighOnFirstChunk`
 * returns early unless `lastBleConnInterval >= 17`, and
 * `boostAiPhotoConnectionPriority` treats `<= 16` as already fast.
 */
private const val FA12_STILL_SLOW_INTERVAL_MIN = 17

/**
 * Should the first FA12 block of a session trigger one more HIGH request?
 *
 * File-scope and pure so the rule is unit-testable without a
 * [android.bluetooth.BluetoothGatt]. An unobserved interval (0) answers
 * `false` — the OEM refuses to guess, and so do we: without a real reading
 * this would be a blind re-push of what `capture()` already asked for.
 */
internal fun shouldReboostHighOnFirstFa12(interval: Int): Boolean =
    interval >= FA12_STILL_SLOW_INTERVAL_MIN
