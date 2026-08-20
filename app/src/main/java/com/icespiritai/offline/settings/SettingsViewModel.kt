package com.icespiritai.offline.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.updater.AppVersionInfo
import com.icespiritai.offline.updater.UpdateCheckResult
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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

    /**
     * Update flow read-through; ViewModel does not own the StateFlow
     * (singleton lives in [UpdateRepository]). Anything observing
     * `updateState` is observing the same process-global [UpdateRepository.state].
     */
    val updateState: StateFlow<UpdateState> = UpdateRepository.state

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

    /** Manual "Check for updates" tap (also invoked from [retry]). */
    fun refresh() {
        UpdateRepository.checkForUpdatesAsync(
            BuildConfig.UPDATE_JSON_URL,
            BuildConfig.VERSION_CODE,
            scope = viewModelScope,
        )
    }

    /**
     * Coroutine entry into the download path. [context] is the Activity
     * (caller-supplied via [androidx.compose.ui.platform.LocalContext]); the
     * `applicationContext` is what reaches [UpdateRepository.downloadApk] so
     * the cacheDir outlives any rotation-driven Activity recreation.
     */
    fun download(info: AppVersionInfo, context: Context) {
        UpdateRepository.downloadApk(info, context.applicationContext, scope = viewModelScope)
    }

    /**
     * Hand the APK file off to the system installer. If the user hasn't yet
     * granted "Install unknown apps" to this package, [ActivityNotFoundException]
     * is the documented signal — fall back to the system settings page so they
     * can flip the toggle and re-tap "Install".
     */
    fun install(file: File, context: Context) {
        try {
            UpdateRepository.requestInstall(context, file)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
    }

    /**
     * Smart retry: every [UpdateState.Failed] branch currently routes to
     * [refresh] (the download path has its own retry via [download], invoked
     * when a new [UpdateAvailable] lands). Keeping the explicit dispatch
     * documents which failures map to which recovery action.
     */
    fun retry() {
        when (val current = updateState.value) {
            is UpdateState.Failed -> when (current.result) {
                is UpdateCheckResult.Failed.DownloadInterrupted -> {
                    // Server fetch failed mid-download — re-check, no cached info to reuse.
                    refresh()
                }
                else -> refresh()
            }
            else -> refresh()
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
