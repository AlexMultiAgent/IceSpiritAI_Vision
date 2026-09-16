package com.aiglass.zhangwen.config

import android.content.Context

object BoundDeviceStore {
    private const val PREFS = "bound_device"
    private const val KEY_NAME = "name"
    private const val KEY_MAC = "mac"

    fun save(context: Context, name: String, mac: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name)
            .putString(KEY_MAC, mac)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun getMac(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAC, null)
            ?.takeIf { it.isNotBlank() }

    fun getName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)
            ?.takeIf { it.isNotBlank() }
}
