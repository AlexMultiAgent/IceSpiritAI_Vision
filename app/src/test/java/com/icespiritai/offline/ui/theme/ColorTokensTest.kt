package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTokensTest {

    @Test fun lightIceChatBg() = assertEquals(Color(0xFFF4F8FB), LightIceChatBg)
    @Test fun lightIceChatPanel() = assertEquals(Color(0xFFFFFFFF), LightIceChatPanel)
    @Test fun lightIceChatPanelSoft() = assertEquals(Color(0xFFEAF2F7), LightIceChatPanelSoft)
    @Test fun lightIceChatPanelStrong() = assertEquals(Color(0xFFD6E2EC), LightIceChatPanelStrong)
    @Test fun lightIceChatOnBg() = assertEquals(Color(0xFF0B1E26), LightIceChatOnBg)
    @Test fun lightIceChatOnBgMuted() = assertEquals(Color(0xFF5A6E78), LightIceChatOnBgMuted)
    @Test fun lightIceChatOnBgSubtle() = assertEquals(Color(0xFF9DA9B0), LightIceChatOnBgSubtle)
    @Test fun lightIceChatOnBgDisabled() = assertEquals(Color(0xFF9DA9B0), LightIceChatOnBgDisabled)
    @Test fun lightIceChatOnBgPlaceholder() = assertEquals(Color(0xFFA8B4BB), LightIceChatOnBgPlaceholder)
    @Test fun lightIceChatAccent() = assertEquals(Color(0xFF1F3A52), LightIceChatAccent)
    @Test fun lightIceChatAccentSecondary() = assertEquals(Color(0xFF5A7090), LightIceChatAccentSecondary)
    @Test fun lightIceChatOnAccent() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnAccent)
    @Test fun lightIceChatDivider() = assertEquals(Color(0x141F3A52), LightIceChatDivider)
    @Test fun lightIceChatError() = assertEquals(Color(0xFFD32F2F), LightIceChatError)
    @Test fun lightIceChatOnError() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnError)

    @Test fun darkIceChatBg() = assertEquals(Color(0xFF08131B), DarkIceChatBg)
    @Test fun darkIceChatPanel() = assertEquals(Color(0xFF11212C), DarkIceChatPanel)
    @Test fun darkIceChatPanelSoft() = assertEquals(Color(0xFF1A2D3A), DarkIceChatPanelSoft)
    @Test fun darkIceChatPanelStrong() = assertEquals(Color(0xFF243748), DarkIceChatPanelStrong)
    @Test fun darkIceChatOnBg() = assertEquals(Color(0xFFE0F0F8), DarkIceChatOnBg)
    @Test fun darkIceChatOnBgMuted() = assertEquals(Color(0xFF7A95A3), DarkIceChatOnBgMuted)
    @Test fun darkIceChatOnBgSubtle() = assertEquals(Color(0xFF4A5C66), DarkIceChatOnBgSubtle)
    @Test fun darkIceChatOnBgDisabled() = assertEquals(Color(0xFF4A5C66), DarkIceChatOnBgDisabled)
    @Test fun darkIceChatOnBgPlaceholder() = assertEquals(Color(0xFF6A7C86), DarkIceChatOnBgPlaceholder)
    @Test fun darkIceChatAccent() = assertEquals(Color(0xFFA8C0D0), DarkIceChatAccent)
    @Test fun darkIceChatAccentSecondary() = assertEquals(Color(0xFF7DA4BD), DarkIceChatAccentSecondary)
    @Test fun darkIceChatOnAccent() = assertEquals(Color(0xFF08131B), DarkIceChatOnAccent)
    @Test fun darkIceChatDivider() = assertEquals(Color(0x264FC0E8), DarkIceChatDivider)
    @Test fun darkIceChatError() = assertEquals(Color(0xFFFF6B6B), DarkIceChatError)
    @Test fun darkIceChatOnError() = assertEquals(Color(0xFF08131B), DarkIceChatOnError)
}