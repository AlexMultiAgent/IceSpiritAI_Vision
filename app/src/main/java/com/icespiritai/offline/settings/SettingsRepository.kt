package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed [ThemeSettingsSource]. Production wiring goes through
 * the `SettingsViewModel.factory(SettingsRepository(applicationContext))`
 * path; tests substitute a fake.
 *
 * Bug 6 fix (2026-09-10): every `dataStore.data` consumer must wrap the
 * upstream with `.catch { emit(emptyPreferences()) }` — without it a
 * corrupt preferences file (e.g. interrupted write, schema drift after
 * R8 repackaging) makes `dataStore.data` throw `IOException` on every
 * read, which propagates to all downstream collectors and crashes the
 * app on next cold start. Empty-preferences recovery keeps the app
 * usable (user sees defaults) and a follow-up `setThemeMode` /
 * `acceptDisclaimer` call overwrites the bad file atomically.
 */
class SettingsRepository(private val context: Context) : ThemeSettingsSource {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val disclaimerKey = longPreferencesKey("disclaimer_accepted_at")

    override val themeMode: kotlinx.coroutines.flow.Flow<ThemeMode> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                ThemeMode.fromName(prefs[themeModeKey])
            }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    /** 用户首次启动点 "我了解" 后写时间戳,null = 还没接受过。 */
    val disclaimerAcceptedAt: kotlinx.coroutines.flow.Flow<Long?> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { it[disclaimerKey] }

    suspend fun acceptDisclaimer() {
        context.dataStore.edit { prefs ->
            prefs[disclaimerKey] = System.currentTimeMillis()
        }
    }
}
