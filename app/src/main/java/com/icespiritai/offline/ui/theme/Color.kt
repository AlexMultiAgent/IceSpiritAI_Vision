package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color

// IceChat family palette — aligned 1:1 with IceSpiritAI_Chat's
// `ice_chat_*` tokens (per spec §3.1). Light values mirror Chat's
// `app/src/main/res/values/colors.xml`; dark values mirror Chat's
// `app/src/main/res/values-night/colors.xml`. Single source of truth
// for visual hex values; pin test in `ColorTokensTest`.

// Dark scheme (Night — slate navy, A 调 enforcement tone)
val DarkIceChatBg = Color(0xFF08131B)
val DarkIceChatPanel = Color(0xFF11212C)
val DarkIceChatPanelSoft = Color(0xFF1A2D3A)
val DarkIceChatPanelStrong = Color(0xFF243748)
val DarkIceChatOnBg = Color(0xFFE0F0F8)
val DarkIceChatOnBgMuted = Color(0xFF7A95A3)
val DarkIceChatOnBgSubtle = Color(0xFF4A5C66)
val DarkIceChatOnBgDisabled = Color(0xFF4A5C66)
val DarkIceChatOnBgPlaceholder = Color(0xFF6A7C86)
val DarkIceChatAccent = Color(0xFFA8C0D0)
val DarkIceChatAccentSecondary = Color(0xFF7DA4BD)
val DarkIceChatOnAccent = Color(0xFF08131B)
val DarkIceChatDivider = Color(0x264FC0E8)
// Semantic severity tokens (per spec §3.1 取舍说明)— 不进 Material3
// colorScheme,直接 val 暴露给 SeverityBadge / HighlightOverlay / StatusBanner
val DarkIceChatWarning = Color(0xFFE08570)
val DarkIceChatOnWarning = Color(0xFF08131B)
val DarkIceChatPositive = Color(0xFF5FC2A0)
val DarkIceChatError = Color(0xFFFF6B6B)
val DarkIceChatOnError = Color(0xFF08131B)

// Light scheme (Day — soft white, archive / export)
val LightIceChatBg = Color(0xFFF4F8FB)
val LightIceChatPanel = Color(0xFFFFFFFF)
val LightIceChatPanelSoft = Color(0xFFEAF2F7)
val LightIceChatPanelStrong = Color(0xFFD6E2EC)
val LightIceChatOnBg = Color(0xFF0B1E26)
val LightIceChatOnBgMuted = Color(0xFF5A6E78)
val LightIceChatOnBgSubtle = Color(0xFF9DA9B0)
val LightIceChatOnBgDisabled = Color(0xFF9DA9B0)
val LightIceChatOnBgPlaceholder = Color(0xFFA8B4BB)
val LightIceChatAccent = Color(0xFF1F3A52)
val LightIceChatAccentSecondary = Color(0xFF5A7090)
val LightIceChatOnAccent = Color(0xFFFFFFFF)
val LightIceChatDivider = Color(0x141F3A52)
// Semantic severity tokens (per spec §3.1 取舍说明)
val LightIceChatWarning = Color(0xFFB04030)
val LightIceChatOnWarning = Color(0xFFFFFFFF)
val LightIceChatPositive = Color(0xFF2C8A6B)
val LightIceChatError = Color(0xFFD32F2F)
val LightIceChatOnError = Color(0xFFFFFFFF)
