package com.icespiritai.offline.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val source: ThemeSettingsSource) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = source.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { source.setThemeMode(mode) }
    }
}