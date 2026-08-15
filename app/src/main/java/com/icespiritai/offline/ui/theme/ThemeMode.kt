package com.icespiritai.offline.ui.theme

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT;

    fun toNightMode(): Int = when (this) {
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    }

    companion object {
        /**
         * Factory default is [SYSTEM] (follow the OS). The user can then
         * pin to [DARK] or [LIGHT] from the settings picker. Previously
         * we defaulted to [DARK] under the assumption that field-use is
         * the primary scenario, but per user feedback the app should
         * follow the host OS by default and let the user opt in to a
         * specific mode rather than the other way round.
         */
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}