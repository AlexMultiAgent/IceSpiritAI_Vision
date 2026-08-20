package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the persistent theme-mode setting. Implemented by
 * [SettingsRepository] for production (DataStore-backed) and by test
 * doubles under `src/test/`.
 *
 * `themeMode` is exposed as a [Flow] rather than a [kotlinx.coroutines.flow.StateFlow]
 * so the contract stays minimal: callers that need a StateFlow (e.g.
 * `SettingsViewModel` for Compose `collectAsStateWithLifecycle`) project it
 * via `stateIn(scope, started, initialValue)`. The interface intentionally
 * doesn't pin to StateFlow to avoid forcing every implementation to
 * eagerly materialize one.
 */
interface ThemeSettingsSource {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
