package com.icespiritai.offline.glasses

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime-permission gate for the smart-glasses BLE pipeline.
 *
 * **Why this exists** (v0.4.3, 2026-09-16 crash report): `BLUETOOTH_CONNECT`
 * was declared in `AndroidManifest.xml` but never *requested* at runtime.
 * On Android 12+ (API 31+) — the app targets 37 — every Bluetooth API that
 * touches device identity is enforced against that runtime permission, so
 * the un-asked permission is denied by default and the first call throws
 * `SecurityException`. The user-visible symptom was a hard crash when
 * tapping the home-screen 「眼镜」 button with no glasses paired (that path
 * is the only one that reads the OS bonded-device list).
 *
 * **Scope decision: `BLUETOOTH_CONNECT` only (v1).** `BLUETOOTH_SCAN` is
 * deliberately *not* requested here:
 *
 *  - v1 pairs in **system** Bluetooth settings and connects by MAC —
 *    `bondedDevices` + `getRemoteDevice` + `connectGatt` need CONNECT
 *    only. `GlassesScan` exists but is not wired to any UI.
 *  - Both permissions live in the `NEARBY_DEVICES` group, whose system
 *    dialog text is group-level ("发现并连接到附近的设备"). Asking for
 *    SCAN as well therefore buys no UX difference — only a wider
 *    permission surface to justify in privacy/compliance review.
 *  - The manifest declaration for SCAN stays in place, so wiring in-app
 *    pairing in v2 needs no manifest change; the scan-time request belongs
 *    next to the scan button, where the user's intent is self-evident.
 *
 * **Revocation.** `BLUETOOTH_CONNECT` is a `dangerous` permission: the user
 * can toggle it off in system Settings at any time, and doing so kills the
 * process. Never cache the result — call [missing] / [allGranted] at the
 * start of every flow, and re-check on resume (see `SettingsScreen`).
 */
object GlassesPermissions {

    /**
     * Permissions this build needs for the v1 glasses flow.
     *
     * Empty on API ≤ 30: the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN`
     * declarations (both capped at `maxSdkVersion=30` in the manifest)
     * already cover GATT there, and there is no runtime permission to ask
     * for.
     */
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    /** Subset of [required] that is not currently granted. */
    fun missing(context: Context): List<String> =
        required().filterNot { isGranted(context, it) }

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Should the app explain itself before asking again?
     *
     * `shouldShowRequestPermissionRationale` answers `true` after a plain
     * "deny" and `false` both *before the first ask* and after a permanent
     * denial ("don't ask again", or the second denial on Android 11+). The
     * two `false` worlds are indistinguishable here, which is why the
     * caller only reads `false` as "probably permanent" **after** a
     * request has already been refused — see `HomeScreen`'s launcher
     * callback. When no Activity is reachable (previews, unusual hosts) we
     * answer `true`, the conservative non-permanent branch.
     */
    fun shouldShowRationale(context: Context, permission: String): Boolean {
        val activity = context.findActivity() ?: return true
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
