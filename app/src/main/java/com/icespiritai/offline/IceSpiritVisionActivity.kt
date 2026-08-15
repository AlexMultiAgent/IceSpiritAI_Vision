package com.icespiritai.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.ui.nav.IceSpiritNavHost
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class IceSpiritVisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SettingsRepository(applicationContext)
        runBlocking {
            AppCompatDelegate.setDefaultNightMode(settings.themeMode.first().toNightMode())
        }

        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                initialValue = com.icespiritai.offline.ui.theme.ThemeMode.SYSTEM,
            )
            IceSpiritVisionTheme(themeMode = themeMode) {
                IceSpiritNavHost()
            }
        }
    }
}
