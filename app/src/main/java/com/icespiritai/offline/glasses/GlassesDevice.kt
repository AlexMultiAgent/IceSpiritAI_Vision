package com.icespiritai.offline.glasses

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * A nearby Bluetooth LE device that **might** be the glasses.
 *
 * Discovery produces many unrelated peripherals (smart watches, fitness
 * bands, headphones). The caller filters by [namePrefix] in
 * [GlassesScan] — typically `"Glass-D15"` per the firmware spec, but
 * kept as a constant on this class so adding future models is a one-line
 * change.
 *
 * [address] is the BLE MAC string (`AA:BB:CC:DD:EE:FF`). It is the
 * stable identity used by both [BluetoothAdapter.getRemoteDevice] and
 * [GlassesDeviceStore] for re-connect across app restarts.
 *
 * [lastSeenMs] is `System.currentTimeMillis()` at the moment this device
 * was last observed in a scan result. The store uses it as a "device is
 * reachable" hint — older than ~30 s usually means the user has moved
 * out of range.
 */
data class GlassesDevice(
    val address: String,
    val name: String,
    val lastSeenMs: Long,
) {
    companion object {
        /**
         * Broadcast-name prefixes that identify a peripheral as our
         * smart-glasses (vs unrelated devices like smart watches /
         * fitness bands). Spec hardware advertises as `Glass-D15`
         * (per `docs/glasses/AI识图传图提速_App连接参数配合.md`); the
         * firmware actually shipped on the device under test advertises
         * as `Glasses-A88` (first observed 2026-09-14, commit `552a8a7`).
         *
         * Both prefixes are accepted by [nameMatches] so the app does
         * not silently filter out hardware the user paired in system
         * Settings. New OEM / firmware revisions can be added here as a
         * one-line change.
         */
        val NAME_PREFIXES: List<String> = listOf("Glass-D15", "Glasses-A")

        /**
         * Spec-contract prefix. Kept as the first entry of
         * [NAME_PREFIXES] and as the canonical reference for callers
         * that want to display the spec name; new scan / filter code
         * should use [nameMatches] instead of this single prefix.
         */
        const val NAME_PREFIX: String = "Glass-D15"

        /**
         * Return `true` if [name] starts with any accepted
         * smart-glasses broadcast prefix. [name] is the Bluetooth
         * device name string from a scan record or bonded-device list.
         * `null` / blank → `false`.
         */
        fun nameMatches(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            return NAME_PREFIXES.any { name.startsWith(it) }
        }

        /**
         * Above this age (ms), a previously-seen device is treated as
         * "out of range" by [GlassesDeviceStore]. Conservative 30 s —
         * shorter than the typical BLE radio range refresh interval.
         */
        const val STALE_THRESHOLD_MS: Long = 30_000L

        /**
         * Read the OS bonded-device list as a [BondedSnapshot] — the raw
         * material for [resolveGlassesTarget].
         *
         * **Why the OS list is read at all** (smoke test 2026-09-14,
         * commit `552a8a7`): `GlassesDeviceStore.loadLastPaired()` only
         * records an address after a successful connect, so on a **first
         * launch** (or after `adb shell pm clear`) it is empty even though
         * the user has already paired the glasses in system Settings. The
         * OS list — which survives `pm clear` and app uninstalls — is what
         * breaks that chicken-and-egg.
         *
         * **This function is the v0.4.3 crash site's replacement.** The
         * previous implementation called `adapter.bondedDevices` and
         * `device.name` directly. Both require `BLUETOOTH_CONNECT` on
         * API 31+, and the permission was never requested at runtime, so
         * the call threw `SecurityException` on the main thread inside the
         * 「眼镜」 button's `onClick` → process death. On the Android 10
         * smoke device the legacy `BLUETOOTH` permission covered it, which
         * is why every pre-v0.4.3 smoke run passed.
         *
         * Three independent guards now stand between the UI and that
         * exception, in order of cheapness:
         *
         *  1. an explicit `checkSelfPermission` before touching the
         *     adapter — reports `connectPermissionGranted = false` instead
         *     of relying on the platform to throw;
         *  2. a `try/catch (SecurityException)` around the whole read,
         *     because ROMs differ in *which* call they enforce: some
         *     throw on `isEnabled`, some on `bondedDevices`, some only on
         *     the per-device `name` / `address` getters;
         *  3. per-device `try/catch` inside [toGlassesDevice] so one
         *     unreadable entry does not discard the whole list.
         *
         * Never throws. Callers get a snapshot they can hand to the pure
         * resolver, which is what makes the "no glasses paired" path
         * testable without a Bluetooth stack.
         */
        fun bondedSnapshot(context: Context): BondedSnapshot {
            val adapter = try {
                BluetoothAdapter.getDefaultAdapter()
            } catch (e: Throwable) {
                // Defensive: no known ROM throws here, but this is the first
                // line of the flow that used to kill the process, so it does
                // not get to be the exception.
                null
            } ?: return BondedSnapshot.NO_ADAPTER

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return BondedSnapshot.PERMISSION_MISSING
            }

            return try {
                if (!adapter.isEnabled) {
                    BondedSnapshot.BLUETOOTH_OFF
                } else {
                    BondedSnapshot(
                        bondedGlasses = adapter.bondedDevices
                            .orEmpty()
                            .mapNotNull(::toGlassesDevice),
                    )
                }
            } catch (e: SecurityException) {
                // The explicit check above should have caught this; keep the
                // fallback so a permission revoked mid-flight (or an OEM
                // enforcing a permission we do not know about) degrades to
                // the permission prompt instead of a crash.
                BondedSnapshot.PERMISSION_MISSING
            }
        }

        /**
         * Map one OS device to [GlassesDevice], or null when it is not a
         * smart-glasses or the platform refuses to hand over its
         * name/address.
         */
        private fun toGlassesDevice(btDevice: BluetoothDevice): GlassesDevice? = try {
            val deviceName = btDevice.name
            val deviceAddress = btDevice.address
            if (deviceName == null || deviceAddress == null || !nameMatches(deviceName)) {
                null
            } else {
                GlassesDevice(
                    address = deviceAddress,
                    name = deviceName,
                    lastSeenMs = System.currentTimeMillis(),
                )
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
