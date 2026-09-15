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
         * Broadcast-name prefixes that identify a peripheral as our
         * smart-glasses (vs unrelated devices like smart watches /
         * fitness bands). Spec hardware advertises as `Glass-D15`
         * (per `docs/glass/AI识图传图提速_App连接参数配合.md`); the
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
         * Look up a bonded smart-glasses device via the OS Bluetooth
         * stack, returning `null` if no BondedDevice name matches any
         * accepted smart-glasses prefix (see [NAME_PREFIXES]).
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
         * the first device whose name matches our prefix list. The
         * caller should persist the result via
         * [GlassesDeviceStore.saveLastPaired] so subsequent launches
         * bypass this fallback.
         *
         * `null` is returned in three cases:
         *   - BluetoothAdapter unavailable (emulator / device without BT)
         *   - Bluetooth radio off
         *   - No BondedDevice name matches any accepted prefix
         */
        fun findBondedDevice(context: Context): GlassesDevice? {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            if (!adapter.isEnabled) return null
            return adapter.bondedDevices
                ?.firstOrNull { btDevice -> nameMatches(btDevice.name) }
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
