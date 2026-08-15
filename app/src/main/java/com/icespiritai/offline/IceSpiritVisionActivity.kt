package com.icespiritai.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.ui.nav.IceSpiritNavHost
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
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
            IceSpiritVisionTheme {
                IceSpiritNavHost()
            }
        }
    }
}
