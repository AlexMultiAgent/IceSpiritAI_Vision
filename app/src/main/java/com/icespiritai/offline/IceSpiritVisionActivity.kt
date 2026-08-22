package com.icespiritai.offline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.ui.nav.IceSpiritNavHost
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import com.icespiritai.offline.updater.UpdateDownloadActions
import com.icespiritai.offline.updater.UpdateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class IceSpiritVisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SettingsRepository(applicationContext)
        // Apply the persisted night mode asynchronously instead of blocking the
        // main thread on the first DataStore read.
        lifecycleScope.launch {
            AppCompatDelegate.setDefaultNightMode(settings.themeMode.first().toNightMode())
        }

        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                // First-frame placeholder before the first DataStore read
                // returns. Matches the factory default (SYSTEM) so a
                // freshly installed user does not see a one-frame flash
                // before the first read lands.
                initialValue = ThemeMode.SYSTEM,
            )
            IceSpiritVisionTheme(themeMode = themeMode) {
                IceSpiritNavHost()
            }
        }

        // In-app update: silent startup check. checkForUpdatesAsync owns a
        // process-wide default scope, so the check survives Activity
        // recreation (a fresh Activity would otherwise re-fire it). Its
        // state mutations land in `UpdateRepository.state`, which is observed
        // by `SettingsViewModel.updateState`. Fire-and-forget.
        UpdateRepository.checkForUpdatesAsync(
            jsonUrl = BuildConfig.UPDATE_JSON_URL,
            currentVersionCode = BuildConfig.VERSION_CODE,
        )

        // Notification PendingIntents for [立即安装] / [稍后] launch the
        // Activity with ACTION_INSTALL / ACTION_LATER. Handle the action
        // here so the tap is consumed before the user sees the home
        // screen. Clearing `intent.action` prevents onResume / rotation
        // from re-triggering the install.
        handleUpdateActionIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the Activity's current intent so subsequent reads via
        // getIntent() see the new payload (matches the spec'd single-
        // launch semantics for ACTION_INSTALL).
        setIntent(intent)
        handleUpdateActionIntent(intent)
    }

    private fun handleUpdateActionIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            UpdateDownloadActions.ACTION_INSTALL -> {
                val downloadId = intent.getStringExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID)
                    ?: return
                val file = File(cacheDir, "update/$downloadId.apk")
                if (file.exists()) UpdateRepository.requestInstall(this, file)
                // Prevent onResume / rotation from re-firing install.
                intent.action = null
            }
            UpdateDownloadActions.ACTION_LATER -> {
                // [稍后] is a no-op beyond consuming the action — the
                // notification already represents the user-deferred
                // choice. Clearing the action stops rotation re-triggers.
                intent.action = null
            }
        }
    }
}
