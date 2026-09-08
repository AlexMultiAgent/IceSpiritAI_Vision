package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.icespiritai.offline.tts.LocalTtsController
import com.icespiritai.offline.tts.TtsController

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
    // Defaulted to null so existing UI tests that only need LocalSeverityColors /
    // MaterialTheme can keep calling `IceSpiritVisionTheme(themeMode = ...)`.
    // The Activity always passes a non-null controller in production, which is
    // where LocalTtsController.current is consumed by the TTS UI (Task 9+).
    ttsController: TtsController? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.toDarkTheme()
    val severityColors = SeverityColors(isDark = darkTheme)
    // Build the provider list dynamically — attach the TTS provider only when
    // an Activity-supplied controller exists, so tests / previews that omit
    // it stay free of the engine-side init() side effects TtsController.init
    // kicks off.
    val providers = buildList<androidx.compose.runtime.ProvidedValue<*>> {
        add(LocalSeverityColors provides severityColors)
        if (ttsController != null) add(LocalTtsController provides ttsController)
    }
    CompositionLocalProvider(*providers.toTypedArray()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = IceSpiritShapes,
            typography = IceSpiritTypography,
            content = content,
        )
    }
}
