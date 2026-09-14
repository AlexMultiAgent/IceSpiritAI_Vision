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
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
     * the firmware spec in `docs/glass/AI识图传图提速_App连接参数配合.md` §6.
     *
     * If the actual firmware uses different characteristic handles (e.g.
     * FFF3 instead of FFF1 for write), adjust the constants here — the
     * rest of the pipeline (Repository / Protocol / Stream) treats them
     * as opaque handles.
     */
    companion object {
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

    private val _lastConnInterval = MutableStateFlow(32)   // 32 × 1.25ms = 40ms (Android default)
    /**
     * Most recent BLE connection interval in **1.25 ms units**. 32 = 40 ms
     * (Android BALANCED default); 12 = 15 ms (target under HIGH priority
     * after the firmware accepts the parameter update).
     *
     * **Not currently auto-updated** — the Android SDK stub the AGP 9.3
     * toolchain binds against does not expose `onConnectionUpdated` as an
     * overridable method on `BluetoothGattCallback` (the compileSdk 37
     * stub has a different signature). The state is preserved as a hook
     * for a future re-bind when the stub stabilizes; for now callers
     * should treat `32` as "no observation yet" and rely on the
     * HIGH-priority behavior empirically (observe transfer time).
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

    private val _fa12Notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    /**
     * FA12 (photo data) notify payloads — each emission is one JPEG
     * chunk in the wire format documented in
     * [GlassesPhotoProtocol.parsePhotoChunk]. 256-buffer capacity
     * tolerates a brief consumer stall during state transitions.
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
     * Per `docs/glass/AI识图传图提速_App连接参数配合.md` §3: call once at
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
     * Negotiate the ATT MTU. Suspends until the OS completes the
     * exchange. Throws if not currently connected.
     */
    suspend fun requestMtu(target: Int): Int {
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

    /** Enable FA12 notify (CCCd write). Suspends until OS confirms. */
    suspend fun enableFa12Notify(): Boolean = enableNotify(FA10_SERVICE_UUID, FA12_CHAR_UUID) {
        fa12Desc = it
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
    suspend fun writeFa11(payload: ByteArray): Boolean =
        writeCharacteristic(FA11_CHAR_UUID, payload)

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
        _lastConnInterval.value = 32
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
        val g = gatt ?: error("not connected")
        // Look up by UUID since the cache may have been invalidated by
        // a service-change indication. Cheap linear scan.
        val ch: BluetoothGattCharacteristic = g.services
            ?.mapNotNull { it.getCharacteristic(charUuid) }
            ?.firstOrNull()
            ?: error("characteristic $charUuid not found")
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ch.value = payload
        pendingCharWrite = CompletableDeferred()
        g.writeCharacteristic(ch)
        return pendingCharWrite!!.await()
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
                FFF2_CHAR_UUID -> _fff0Notify.tryEmit(data)
                FA12_CHAR_UUID -> _fa12Notify.tryEmit(data)
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
            pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
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
