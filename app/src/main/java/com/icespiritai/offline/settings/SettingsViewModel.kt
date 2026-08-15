package com.icespiritai.offline.settings

import androidx.appcompat.app.AppCompatDelegate
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
        // Matches ThemeMode.fromName(null) so a brand-new install's first
        // composition doesn't briefly flip through a different value before
        // DataStore's first read lands. Factory default is SYSTEM (follow
        // the OS); the user can pin to DARK/LIGHT from settings.
        initialValue = ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        // Persist to DataStore first, then push the new night mode to AppCompat.
        // Order matters: if we flipped night mode before the write landed, an
        // Activity recreate could read the previous value from DataStore and
        // snap the theme back. Both calls run on the main dispatcher because
        // viewModelScope defaults to Dispatchers.Main.immediate.
        viewModelScope.launch {
            source.setThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
        }
    }

    companion object {
        fun factory(repository: SettingsRepository) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository) as T
            }
        }
    }
}