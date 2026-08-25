package com.icespiritai.offline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.icespiritai.offline.domain.Severity

/**
 * Maps [Severity] to a 4-token Material You color set:
 * accent (left bar / border) / onAccent (text on accent) / container (12% bg) / onContainer (text on bg).
 * Created in Phase 3.1 Task 4 — components now read these instead of hand-picking dark/light pairs.
 */
@Immutable
data class SeverityColors(
    val isDark: Boolean,
    val errorAccent: Color,
    val errorOnAccent: Color,
    val errorContainer: Color,
    val errorOnContainer: Color,
    val warningAccent: Color,
    val warningOnAccent: Color,
    val warningContainer: Color,
    val warningOnContainer: Color,
    val positiveAccent: Color,
    val positiveOnAccent: Color,
    val positiveContainer: Color,
    val positiveOnContainer: Color,
    val infoAccent: Color,
    val infoOnAccent: Color,
    val infoContainer: Color,
    val infoOnContainer: Color,
) {
    fun accent(s: Severity): Color = when (s) {
        Severity.Violation -> errorAccent
        Severity.Warning -> warningAccent
        Severity.Positive -> positiveAccent
        Severity.Info -> infoAccent
    }
    fun onAccent(s: Severity): Color = when (s) {
        Severity.Violation -> errorOnAccent
        Severity.Warning -> warningOnAccent
        Severity.Positive -> positiveOnAccent
        Severity.Info -> infoOnAccent
    }
    fun container(s: Severity): Color = when (s) {
        Severity.Violation -> errorContainer
        Severity.Warning -> warningContainer
        Severity.Positive -> positiveContainer
        Severity.Info -> infoContainer
    }
    fun onContainer(s: Severity): Color = when (s) {
        Severity.Violation -> errorOnContainer
        Severity.Warning -> warningOnContainer
        Severity.Positive -> positiveOnContainer
        Severity.Info -> infoOnContainer
    }
}

fun SeverityColors(isDark: Boolean): SeverityColors = if (isDark) {
    SeverityColors(
        isDark = true,
        errorAccent = DarkIceChatError,
        errorOnAccent = DarkIceChatOnError,
        errorContainer = DarkIceChatErrorContainer,
        errorOnContainer = DarkIceChatOnErrorContainer,
        warningAccent = DarkIceChatWarning,
        warningOnAccent = DarkIceChatOnWarning,
        warningContainer = DarkIceChatWarningContainer,
        warningOnContainer = DarkIceChatOnWarningContainer,
        positiveAccent = DarkIceChatPositive,
        positiveOnAccent = DarkIceChatOnPositive,
        positiveContainer = DarkIceChatPositiveContainer,
        positiveOnContainer = DarkIceChatOnPositiveContainer,
        infoAccent = DarkIceChatInfo,
        infoOnAccent = DarkIceChatOnInfo,
        infoContainer = DarkIceChatInfoContainer,
        infoOnContainer = DarkIceChatOnInfoContainer,
    )
} else {
    SeverityColors(
        isDark = false,
        errorAccent = LightIceChatError,
        errorOnAccent = LightIceChatOnError,
        errorContainer = LightIceChatErrorContainer,
        errorOnContainer = LightIceChatOnErrorContainer,
        warningAccent = LightIceChatWarning,
        warningOnAccent = LightIceChatOnWarning,
        warningContainer = LightIceChatWarningContainer,
        warningOnContainer = LightIceChatOnWarningContainer,
        positiveAccent = LightIceChatPositive,
        positiveOnAccent = LightIceChatOnPositive,
        positiveContainer = LightIceChatPositiveContainer,
        positiveOnContainer = LightIceChatOnPositiveContainer,
        infoAccent = LightIceChatInfo,
        infoOnAccent = LightIceChatOnInfo,
        infoContainer = LightIceChatInfoContainer,
        infoOnContainer = LightIceChatOnInfoContainer,
    )
}

val LocalSeverityColors = staticCompositionLocalOf<SeverityColors> {
    error("LocalSeverityColors not provided. Wrap your screen in IceSpiritVisionTheme {}.")
}

/** Composable accessor for the active [SeverityColors]. Resolves dark/light from [ThemeMode]. */
val iceSpiritSeverityColors: SeverityColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSeverityColors.current