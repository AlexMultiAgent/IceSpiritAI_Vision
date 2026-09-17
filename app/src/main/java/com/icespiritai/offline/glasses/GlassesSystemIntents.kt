package com.icespiritai.offline.glasses

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * System-settings deep links for the glasses setup flows.
 *
 * All three call sites (`HomeScreen` notice dialog, `GlassesCaptureOverlay`
 * preflight prompt, `SettingsScreen` card) need the same two destinations,
 * and all three need the same failure modes swallowed:
 *
 *  - `ActivityNotFoundException` — stripped ROMs without an
 *    `ACTION_BLUETOOTH_SETTINGS` handler (the old `SettingsScreen` code
 *    caught this one);
 *  - `SecurityException` — some OEMs gate the app-details page; not our
 *    permission to fix, but it must not crash a settings tap either.
 *
 * Returns whether an Activity was actually launched, so callers can fall
 * back to a message instead of silently doing nothing.
 */
object GlassesSystemIntents {

    private const val TAG = "GlassesSystemIntents"

    /**
     * System Bluetooth settings — the only place pairing happens in v1
     * (the app never calls `createBond()`).
     */
    fun openBluetoothSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), fallbackToGeneral = true)

    /**
     * 「蓝牙共享网络」 lives under the tethering page; that action exists from
     * API 29 and older ROMs fall back to the general wireless settings.
     *
     * Needed because the firmware upgrade cannot proceed without it: the
     * glasses fetch the `.rbl` through the phone's PAN link, so the upgrade
     * dialog offers this button the moment it notices tethering is off.
     */
    fun openTetheringSettings(context: Context): Boolean {
        // The tethering page has no public `Settings.ACTION_*` constant; this
        // is the platform's own action string (AOSP `Settings`,
        // `TetherSettings`). `start()` falls back to the general settings
        // screen when a ROM does not declare it.
        @Suppress("DEPRECATION")
        val intent = Intent("android.settings.TETHERING_SETTINGS")
        return start(context, intent, fallbackToGeneral = true)
    }

    /**
     * This app's detail page, where the user can re-grant a permanently
     * denied `BLUETOOTH_CONNECT`.
     */
    fun openAppSettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        // No fallback here: the caller is already targeting app settings,
        // so a fallback would either loop or land somewhere useless.
        return start(context, intent, fallbackToGeneral = false)
    }

    /**
     * @param fallbackToGeneral when true, a missing handler falls back to
     *   the top-level system Settings app.
     */
    private fun start(context: Context, intent: Intent, fallbackToGeneral: Boolean): Boolean {
        // A non-Activity context (Application, Service) cannot start an
        // Activity without NEW_TASK; adding it from an Activity would spawn
        // the settings UI in a second task.
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no handler for ${intent.action}", e)
            if (fallbackToGeneral) startGeneralSettings(context) else false
        } catch (e: SecurityException) {
            Log.w(TAG, "blocked by the OS: ${intent.action}", e)
            false
        } catch (e: RuntimeException) {
            // Anything else the platform/ROM decides to throw while starting
            // a settings Activity. A tap on 「去设置」 is never worth a crash.
            Log.w(TAG, "failed to start ${intent.action}", e)
            false
        }
    }

    /** Last resort when the vendor ROM has no Bluetooth-settings entry. */
    private fun startGeneralSettings(context: Context): Boolean = try {
        val intent = Intent(Settings.ACTION_SETTINGS)
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Throwable) {
        Log.w(TAG, "no settings Activity at all", e)
        false
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
