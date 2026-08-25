package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkScheme = darkColorScheme(
    primary = DarkIceChatAccent,
    onPrimary = DarkIceChatOnAccent,
    secondary = DarkIceChatAccentSecondary,
    background = DarkIceChatBg,
    onBackground = DarkIceChatOnBg,
    surface = DarkIceChatPanel,
    onSurface = DarkIceChatOnBg,
    surfaceVariant = DarkIceChatPanelSoft,
    onSurfaceVariant = DarkIceChatOnBgMuted,
    surfaceContainerHigh = DarkIceChatPanelStrong,
    outline = DarkIceChatDivider,
    error = DarkIceChatError,
    onError = DarkIceChatOnError,
)

private val LightScheme = lightColorScheme(
    primary = LightIceChatAccent,
    onPrimary = LightIceChatOnAccent,
    secondary = LightIceChatAccentSecondary,
    background = LightIceChatBg,
    onBackground = LightIceChatOnBg,
    surface = LightIceChatPanel,
    onSurface = LightIceChatOnBg,
    surfaceVariant = LightIceChatPanelSoft,
    onSurfaceVariant = LightIceChatOnBgMuted,
    surfaceContainerHigh = LightIceChatPanelStrong,
    outline = LightIceChatDivider,
    error = LightIceChatError,
    onError = LightIceChatOnError,
)

/**
 * Resolves the user's [ThemeMode] preference into a concrete dark/light
 * boolean for [MaterialTheme]. Must be `@Composable` because the SYSTEM
 * branch reads `isSystemInDarkTheme()` from the active composition.
 */
@Composable
fun ThemeMode.toDarkTheme(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun IceSpiritVisionTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.toDarkTheme()
    val severityColors = SeverityColors(isDark = darkTheme)
    CompositionLocalProvider(LocalSeverityColors provides severityColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = IceSpiritShapes,
            typography = IceSpiritTypography,
            content = content,
        )
    }
}
