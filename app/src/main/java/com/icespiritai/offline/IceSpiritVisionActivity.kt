package com.icespiritai.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.ui.nav.IceSpiritNavHost
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    }
}
