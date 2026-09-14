package com.icespiritai.offline.glasses

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * BLE scan wrapper that surfaces **only** devices whose advertised name
 * starts with [GlassesDevice.NAME_PREFIX].
 *
 * The Android BLE stack's `BluetoothLeScanner.startScan()` callback is
 * fire-and-forget — results arrive on the main thread one `ScanResult`
 * at a time over a 5–10 s window. This class wraps that callback as a
 * cold [Flow] of unique [GlassesDevice] entries, deduplicating by MAC
 * address and refreshing [GlassesDevice.lastSeenMs] on each sighting.
 *
 * **Permissions.** The caller must already have `BLUETOOTH_SCAN` (API
 * 31+) or `BLUETOOTH` + `ACCESS_FINE_LOCATION` (API ≤30) granted before
 * invoking [scan]. This class surfaces the missing-permission case via
 * [ScanException.MissingPermission] so the caller can prompt the user
 * instead of silently collecting nothing.
 *
 * **Why a cold `callbackFlow`?** The underlying `BluetoothLeScanner`
 * holds a system-wide scan slot that must be explicitly stopped via
 * [ScanCallback] teardown. Cold flow + `awaitClose` guarantees the
 * scanner stops when the collector cancels — no leaks across
 * configuration changes, no zombie scans if the user navigates away
 * mid-discovery.
 */
class GlassesScan(private val context: Context) {

    /**
     * Cold flow of discovered glasses. Each emission is the current
     * set of unique devices seen since the scan started, ordered by
     * [GlassesDevice.lastSeenMs] descending (most-recent first).
     *
     * The flow completes when the collector cancels.
     */
    fun scan(): Flow<List<GlassesDevice>> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw ScanException.BluetoothUnavailable
        if (!adapter.isEnabled) {
            throw ScanException.BluetoothDisabled
        }
        if (!hasScanPermission()) {
            throw ScanException.MissingPermission
        }

        val seen = LinkedHashMap<String, GlassesDevice>()
        val scanner = adapter.bluetoothLeScanner
            ?: throw ScanException.BluetoothUnavailable

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName
                    ?: device.name
                    ?: return
                if (!name.startsWith(GlassesDevice.NAME_PREFIX)) return
                seen[device.address] = GlassesDevice(
                    address = device.address,
                    name = name,
                    lastSeenMs = System.currentTimeMillis(),
                )
                // Emit a fresh snapshot. LinkedHashMap preserves insertion
                // order so repeated sightings don't shuffle the list; the
                // lastSeenMs sort in the collector handles ordering.
                trySend(seen.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanException.ScanFailed(errorCode))
            }
        }

        scanner.startScan(null, settings, callback)
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: IllegalStateException) {
                // Scanner already torn down by the system; safe to ignore.
            }
        }
    }

    private fun hasScanPermission(): Boolean {
        // API 31+ split the legacy BLUETOOTH permission into SCAN/CONNECT.
        // Pre-31, BLUETOOTH alone covers scanning and we additionally
        // require ACCESS_FINE_LOCATION because the OS only exposes scan
        // results to apps that can derive location.
        val scanGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return scanGranted
        }
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return scanGranted && locationGranted
    }

    /**
     * Discriminator for scan-time failures so the UI can branch on the
     * reason (toast vs. permission prompt vs. settings redirect).
     */
    sealed class ScanException(message: String) : RuntimeException(message) {
        /** Device has no Bluetooth radio at all (rare, but possible on some emulators). */
        data object BluetoothUnavailable : ScanException("Bluetooth adapter unavailable")

        /** Radio is off — user needs to enable Bluetooth in system settings. */
        data object BluetoothDisabled : ScanException("Bluetooth is disabled")

        /** Caller didn't request / wasn't granted the scan permission. */
        data object MissingPermission : ScanException("BLUETOOTH_SCAN permission not granted")

        /** Underlying scan failed; [errorCode] is the Android `ScanCallback` code. */
        data class ScanFailed(val errorCode: Int) : ScanException("Scan failed with error code $errorCode")
    }
}
