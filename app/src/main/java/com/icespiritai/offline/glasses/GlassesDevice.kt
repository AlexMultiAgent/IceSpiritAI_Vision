package com.icespiritai.offline.glasses

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
         * Substring the device's advertised name must contain to be
         * considered the glasses (vs unrelated peripherals). The firmware
         * exposes itself as e.g. `"Glass-D15 V2.4.5"` — a stable prefix
         * across firmware versions.
         */
        const val NAME_PREFIX: String = "Glass-D15"

        /**
         * Above this age (ms), a previously-seen device is treated as
         * "out of range" by [GlassesDeviceStore]. Conservative 30 s —
         * shorter than the typical BLE radio range refresh interval.
         */
        const val STALE_THRESHOLD_MS: Long = 30_000L
    }
}
