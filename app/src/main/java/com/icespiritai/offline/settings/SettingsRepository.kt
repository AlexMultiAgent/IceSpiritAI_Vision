package com.icespiritai.offline.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
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
    private val visibleFeaturesKey = stringSetPreferencesKey("visible_features")
    private val enableGlassesCaptureKey = booleanPreferencesKey("enable_glasses_capture")
    private val autoRetakeGlassesKey = booleanPreferencesKey("auto_retake_low_quality_glasses")

    override val themeMode: Flow<ThemeMode> =
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
    val disclaimerAcceptedAt: Flow<Long?> =
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

    /**
     * Persisted set of currently-visible [RuleTab] values. Falls back to
     * [RuleTab.DEFAULT_VISIBLE_FEATURES] (ad signage only) when:
     *  - the key is missing on a fresh install
     *  - every persisted name fails to map to a current [RuleTab] enum
     *    (e.g. a stale name from a previous version where the enum was
     *    renamed; deserialization yields an empty set, which we treat as
     *    the product default instead of leaving the user with a blank tab
     *    bar).
     *
     * Bug 6 IOException-only catch is preserved — non-IO failures still
     * propagate so a real programming bug doesn't get swallowed.
     */
    override val visibleFeatures: Flow<Set<RuleTab>> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                val defaults = RuleTab.DEFAULT_VISIBLE_FEATURES
                val raw = prefs[visibleFeaturesKey]
                    ?: return@map defaults
                raw.mapNotNullTo(mutableSetOf()) { name ->
                    RuleTab.entries.firstOrNull { it.name == name }
                }.ifEmpty {
                    Log.w(TAG, "visible_features 反序列化空集, fallback 默认功能可见性")
                    defaults
                }
            }

    override suspend fun setVisibleFeatures(value: Set<RuleTab>) {
        context.dataStore.edit { prefs ->
            prefs[visibleFeaturesKey] = value.map(RuleTab::name).toSet()
        }
    }

    /**
     * Smart-glasses capture enable flag. Defaults to `false` — the BLE
     * pipeline is opt-in because the app works fine without glasses
     * hardware (gallery + phone camera cover the core capture paths).
     *
     * The DataStore key is missing on a fresh install; [DataStore.data]
     * falls back to the empty-preferences recovery path (Bug 6 fix),
     * and the `.map` below yields `false` for that case — correct
     * "off-by-default" behaviour without an explicit `?: false` fallback.
     */
    override val enableGlassesCapture: Flow<Boolean> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { it[enableGlassesCaptureKey] ?: false }

    override suspend fun setGlassesCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[enableGlassesCaptureKey] = enabled
        }
    }

    /**
     * 默认 **`true`**：缺 key（全新安装）时按开启处理 —— 这是用户 2026-09-18
     * 明确要求的默认值，与 [enableGlassesCapture] 的 off-by-default 相反。
     */
    override val autoRetakeLowQualityGlassesShot: Flow<Boolean> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { it[autoRetakeGlassesKey] ?: true }

    override suspend fun setAutoRetakeLowQualityGlassesShot(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoRetakeGlassesKey] = enabled
        }
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
