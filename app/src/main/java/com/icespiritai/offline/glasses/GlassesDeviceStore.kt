package com.icespiritai.offline.glasses

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for the last successfully-paired glasses
 * address.
 *
 * **Why SharedPreferences and not DataStore?** The data is a single
 * ~17-byte string (one BLE MAC), updated at most once per cold-pair
 * event (months apart). SharedPreferences's synchronous read on the
 * main thread is acceptable here because [loadLastPaired] is called
 * from the AppGraph init path, **not** from any Composable body.
 * DataStore's suspend-based API would force every call site to be
 * coroutine-aware; the value proposition isn't worth the ceremony
 * for a single key.
 *
 * **Why no encryption?** The MAC address is already exposed in every
 * BLE advertisement the peripheral transmits. Encrypting it in our
 * private storage would only delay an attacker who already has BLE
 * radio access — and they wouldn't need our storage to learn the MAC.
 *
 * **Pairing model.** Pairing itself happens via the **system** Bluetooth
 * settings (the user adds the glasses once). This store only records
 * which address we connected to last, so the app can auto-reconnect on
 * the next launch instead of forcing a fresh scan. The user can clear
 * the entry by forgetting the system pairing + uninstalling the app, or
 * by calling [clear].
 */
class GlassesDeviceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Record the address of the glasses we most recently connected to.
     * Called from [BluetoothController] after a successful
     * `onConnectionStateChange` transition to STATE_CONNECTED.
     */
    fun saveLastPaired(address: String) {
        prefs.edit().putString(KEY_LAST_PAIRED_ADDRESS, address).apply()
    }

    /**
     * Last paired address, or `null` if the app has never connected.
     * Sync I/O — only call from non-UI threads or AppGraph init.
     */
    fun loadLastPaired(): String? = prefs.getString(KEY_LAST_PAIRED_ADDRESS, null)

    /**
     * Forget the last-paired entry. Does **not** unpair the system —
     * the user still needs to clear the system pairing to fully sever
     * the bond. Used by SettingsScreen → "重置眼镜配对" CTA.
     */
    fun clear() {
        prefs.edit().remove(KEY_LAST_PAIRED_ADDRESS).apply()
    }

    /**
     * Last firmware version this App actually read from the glasses.
     *
     * Written by [GlassesPhotoCaptureRepository.readFirmwareVersion], so the
     * settings row can show a version immediately after a cold start — and,
     * more importantly, so a **finished firmware upgrade stays visible**: the
     * upgrade watch runs in a process-scoped object, so if the App is
     * restarted (or was closed) while the glasses flashed, the in-memory
     * result is gone and the user saw nothing (report 2026-09-17 22:08:
     * 「固件升级成功后没有提示」).
     */
    fun saveFirmwareVersion(version: String) {
        prefs.edit().putString(KEY_FIRMWARE_VERSION, version).apply()
    }

    /** Last read firmware version, or `null` if we never got one. */
    fun loadFirmwareVersion(): String? = prefs.getString(KEY_FIRMWARE_VERSION, null)

    companion object {
        private const val PREFS_NAME = "glasses_device_store"
        private const val KEY_LAST_PAIRED_ADDRESS = "last_paired_address"
        private const val KEY_FIRMWARE_VERSION = "firmware_version"
    }
}
