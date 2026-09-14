package com.icespiritai.offline.glasses

import android.bluetooth.BluetoothAdapter
import android.content.Context

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
         * considered the glasses (vs unrelated peripherals).
         *
         * Originally hardcoded to `"Glass-D15"` (the firmware spec reference
         * in `docs/glass/AI识图传图提速_App连接参数配合.md`). Real-device
         * smoke test on 2026-09-14 (commit `552a8a7`) discovered the
         * user's actual glasses advertise as `"Glasses-A88"` — a different
         * OEM / firmware revision than the spec assumed. Loosened to
         * `"Glasses-A"` (matches both `Glass-D15` AND `Glasses-A88`) so
         * the smoke test pipeline can be exercised against the available
         * hardware without renaming the device in system Bluetooth
         * settings. Production deployments should pin this back to the
         * exact OEM prefix once the model line is confirmed.
         */
        const val NAME_PREFIX: String = "Glasses-A"

        /**
         * Above this age (ms), a previously-seen device is treated as
         * "out of range" by [GlassesDeviceStore]. Conservative 30 s —
         * shorter than the typical BLE radio range refresh interval.
         */
        const val STALE_THRESHOLD_MS: Long = 30_000L

        /**
         * Look up a bonded smart-glasses device via the OS Bluetooth
         * stack, returning `null` if no BondedDevice name starts with
         * [NAME_PREFIX].
         *
         * **Why this exists** (smoke test 2026-09-14, commit `552a8a7`):
         * `GlassesDeviceStore.loadLastPaired()` is the per-app
         * SharedPreferences that records the address of the most recent
         * successful connect. It's only written from
         * [BluetoothController]'s `STATE_CONNECTED` callback — so on a
         * **first launch** (or after `adb shell pm clear`), the store is
         * empty even though the user has already paired the glasses in
         * system Settings. The original HomeScreen guard
         * (`if (loadLastPaired() == null) Toast + return`) created a
         * chicken-and-egg: no entry → can't connect → no entry.
         *
         * This helper breaks the deadlock by reading the OS-level
         * `BluetoothAdapter.bondedDevices` list (which the system keeps
         * across `pm clear` and across app uninstalls) and returning
         * the first device whose name matches our prefix. The caller
         * should persist the result via [GlassesDeviceStore.saveLastPaired]
         * so subsequent launches bypass this fallback.
         *
         * `null` is returned in three cases:
         *   - BluetoothAdapter unavailable (emulator / device without BT)
         *   - Bluetooth radio off
         *   - No BondedDevice name starts with [NAME_PREFIX]
         */
        fun findBondedDevice(context: Context): GlassesDevice? {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            if (!adapter.isEnabled) return null
            return adapter.bondedDevices
                ?.firstOrNull { btDevice ->
                    (btDevice.name ?: "").startsWith(NAME_PREFIX)
                }
                ?.let { btDevice ->
                    GlassesDevice(
                        address = btDevice.address,
                        name = btDevice.name ?: NAME_PREFIX,
                        lastSeenMs = System.currentTimeMillis(),
                    )
                }
        }
    }
}
