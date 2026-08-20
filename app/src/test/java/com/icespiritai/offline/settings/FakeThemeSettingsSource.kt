package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test double for [ThemeSettingsSource] backed by a [MutableStateFlow].
 *
 * Shared by `SettingsViewModelTest` and `UpdateSectionTest`. Lives at the
 * top-level package so tests in both `settings/` and `ui/settings/` can
 * use it without copy-pasting the wrapper.
 */
internal class FakeThemeSettingsSource(
    private val backing: MutableStateFlow<ThemeMode>,
) : ThemeSettingsSource {
    override val themeMode: StateFlow<ThemeMode> = backing
    override suspend fun setThemeMode(mode: ThemeMode) {
        backing.value = mode
    }
}