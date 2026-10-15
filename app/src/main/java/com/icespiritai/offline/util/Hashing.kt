package com.icespiritai.offline.util

import java.security.MessageDigest

/**
 * Common digest helpers used across the app. Existed as the same 4-line
 * `sha256Short` private function copy-pasted into
 * [com.icespiritai.offline.updater.UpdateRepository] and
 * [com.icespiritai.offline.settings.SettingsViewModel]; lifted here so
 * the encoding (lowercase hex, first 16 chars) is defined in one place
 * and any future change (e.g. switch to a 32-char prefix, or
 * uppercase) is a one-line edit.
 */
object Hashing {

    /**
     * First 16 lowercase-hex chars of SHA-256(s). Used as a stable
     * short id for download / cancel PendingIntent matching where
     * Android requires a deterministic `requestCode` so that
     * FLAG_UPDATE_CURRENT collapses two consecutive intents on the
     * same download.
     */
    fun sha256Short(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }
}
