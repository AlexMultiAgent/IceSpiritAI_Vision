package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.map

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed [ThemeSettingsSource]. Production wiring goes through
 * the `SettingsViewModel.factory(SettingsRepository(applicationContext))`
 * path; tests substitute a fake.
 */
class SettingsRepository(private val context: Context) : ThemeSettingsSource {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val disclaimerKey = longPreferencesKey("disclaimer_accepted_at")

    override val themeMode: kotlinx.coroutines.flow.Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            ThemeMode.fromName(prefs[themeModeKey])
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    /** 用户首次启动点 "我了解" 后写时间戳,null = 还没接受过。 */
    val disclaimerAcceptedAt: kotlinx.coroutines.flow.Flow<Long?> =
        context.dataStore.data.map { it[disclaimerKey] }

    suspend fun acceptDisclaimer() {
        context.dataStore.edit { prefs ->
            prefs[disclaimerKey] = System.currentTimeMillis()
        }
    }
}
