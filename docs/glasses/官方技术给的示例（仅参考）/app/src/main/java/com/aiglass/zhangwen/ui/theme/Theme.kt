package com.aiglass.zhangwen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GovColorScheme = lightColorScheme(
    primary = GovBlue,
    onPrimary = Color.White,
    primaryContainer = GovBlueLight,
    onPrimaryContainer = Color.White,
    secondary = GovBlueDark,
    onSecondary = Color.White,
    tertiary = GovRed,
    onTertiary = Color.White,
    background = GovBg,
    onBackground = GovTextPrimary,
    surface = GovSurface,
    onSurface = GovTextPrimary,
    onSurfaceVariant = GovTextSecondary,
    outline = GovDivider,
    error = GovRed,
    onError = Color.White
)

@Composable
fun ZhangwenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 固定浅色正式风格，不跟随系统暗色
    MaterialTheme(
        colorScheme = GovColorScheme,
        typography = Typography,
        content = content
    )
}
