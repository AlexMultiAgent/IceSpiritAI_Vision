package com.icespiritai.offline.ui.nav

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icespiritai.offline.ui.home.HomeScreen
import com.icespiritai.offline.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

/**
 * Root NavHost, wrapped in a [Surface] that fills the viewport with
 * `colorScheme.background`. This is required because [enableEdgeToEdge]
 * makes the host Activity's window background transparent — without an
 * explicit Compose background, every Composable that doesn't paint its own
 * background (e.g. plain `Column { }` roots) would show the underlying
 * Activity window background, which follows the system night mode and
 * diverges from the Compose theme when `ThemeMode` is overridden.
 */
@Composable
fun IceSpiritNavHost(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
