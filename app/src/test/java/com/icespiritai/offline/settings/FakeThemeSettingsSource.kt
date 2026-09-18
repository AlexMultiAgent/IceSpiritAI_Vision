package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test double for [ThemeSettingsSource] backed by a [MutableStateFlow].
 *
 * Shared by `SettingsViewModelTest` and `UpdateSectionTest`. Lives at the
 * top-level package so tests in both `settings/` and `ui/settings/` can
 * use it without copy-pasting the wrapper.
 *
 * `visibleFeatures` defaults to [RuleTab.entries] (all tabs visible) so
 * callers that don't care about per-tab visibility get the production
 * "show everything" baseline; tests that need a narrower set can swap
 * [visibleFeaturesBacking] for a tighter MutableStateFlow before passing
 * the fake to the ViewModel.
 *
 * `visibleFeaturesBacking` is `internal` (not `private`) so tests in
 * the same module can assert the persisted value after `setFeatureVisible`
 * without needing to subscribe to the upstream Flow (which is how
 * `SettingsViewModel` verifies the write actually landed).
 */
internal class FakeThemeSettingsSource(
    private val backing: MutableStateFlow<ThemeMode>,
) : ThemeSettingsSource {
    internal val visibleFeaturesBacking = MutableStateFlow<Set<RuleTab>>(RuleTab.entries.toSet())
    internal val enableGlassesCaptureBacking = MutableStateFlow(false)

    override val themeMode: StateFlow<ThemeMode> = backing
    override suspend fun setThemeMode(mode: ThemeMode) {
        backing.value = mode
    }

    override val visibleFeatures: Flow<Set<RuleTab>> = visibleFeaturesBacking
    override suspend fun setVisibleFeatures(value: Set<RuleTab>) {
        visibleFeaturesBacking.value = value
    }

    override val enableGlassesCapture: Flow<Boolean> = enableGlassesCaptureBacking
    override suspend fun setGlassesCaptureEnabled(enabled: Boolean) {
        enableGlassesCaptureBacking.value = enabled
    }

    /** 「拍糊自动重拍」默认开（与生产默认值一致）。 */
    internal val autoRetakeBacking = MutableStateFlow(true)

    override val autoRetakeLowQualityGlassesShot: Flow<Boolean> = autoRetakeBacking
    override suspend fun setAutoRetakeLowQualityGlassesShot(enabled: Boolean) {
        autoRetakeBacking.value = enabled
    }
}
