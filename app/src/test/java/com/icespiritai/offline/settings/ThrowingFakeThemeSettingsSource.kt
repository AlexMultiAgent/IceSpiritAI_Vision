package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test double for [ThemeSettingsSource] whose write setters
 * ([setVisibleFeatures] and [setCacheCleanupEnabled]) throw the
 * configured [Throwable] (typically an [java.io.IOException] from a
 * simulated DataStore write failure).
 *
 * Used by `SettingsViewModelTest` to pin the
 * [SettingsSnackbar.PersistFailed] emission path: the production
 * `SettingsViewModel` wraps the upstream write in
 * `runCatching { ... }.onFailure { _snackbar.tryEmit(PersistFailed(it)) }`,
 * so a throwing fake lets us assert that contract without needing
 * Robolectric + a real corrupted DataStore on disk.
 *
 * `themeMode` / `setThemeMode` behave like [FakeThemeSettingsSource]
 * (backed by an internal [MutableStateFlow]) — irrelevant to the
 * failure tests, but kept working so the ViewModel's other state
 * continues to construct cleanly. Other setters
 * ([setGlassesCaptureEnabled], [setAutoRetakeLowQualityGlassesShot])
 * are no-ops so they don't disturb tests that don't care about their
 * failure path.
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

    override val enableGlassesCapture: Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setGlassesCaptureEnabled(enabled: Boolean) {
        // No-op: this fake's failure-injection surface is
        // `setVisibleFeatures` and `setCacheCleanupEnabled` only.
    }

    override val autoRetakeLowQualityGlassesShot: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setAutoRetakeLowQualityGlassesShot(enabled: Boolean) {
        // Same rationale as [setGlassesCaptureEnabled].
    }

    override val cacheCleanupEnabled: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setCacheCleanupEnabled(enabled: Boolean) {
        throw throwOnSetVisible
    }
}