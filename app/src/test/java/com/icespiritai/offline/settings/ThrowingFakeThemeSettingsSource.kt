package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test double for [ThemeSettingsSource] whose [setVisibleFeatures] throws
 * the configured [Throwable] (typically an [java.io.IOException] from a
 * simulated DataStore write failure). Used by `SettingsViewModelTest` to
 * pin the `SettingsSnackbar.PersistFailed` emission path: the production
 * `SettingsViewModel.setFeatureVisible` wraps the upstream write in
 * `runCatching { ... }.onFailure { _snackbar.tryEmit(PersistFailed(it)) }`,
 * so a throwing fake lets us assert that contract without needing
 * Robolectric + a real corrupted DataStore on disk.
 *
 * `themeMode` / `setThemeMode` behave like [FakeThemeSettingsSource]
 * (backed by an internal [MutableStateFlow]) — irrelevant to the
 * `setFeatureVisible` test, but kept working so the ViewModel's other
 * state continues to construct cleanly.
 */
internal class ThrowingFakeThemeSettingsSource(
    private val throwOnSetVisible: Throwable,
    themeModeInitial: ThemeMode = ThemeMode.SYSTEM,
) : ThemeSettingsSource {

    private val themeBacking = MutableStateFlow(themeModeInitial)

    override val themeMode: StateFlow<ThemeMode> = themeBacking
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeBacking.value = mode
    }

    override val visibleFeatures: Flow<Set<RuleTab>> = MutableStateFlow(RuleTab.entries.toSet())
    override suspend fun setVisibleFeatures(value: Set<RuleTab>) {
        throw throwOnSetVisible
    }
}