package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin IceSpiritAI_Vision's `IceChat*` color palette to the IceSpiritAI_Chat
 * `ice_chat_*` family (per spec §3.1). Single-source-of-truth for hex values
 * — any drift between this test and Chat's `values/colors.xml` should be
 * reviewed and reconciled before merging.
 *
 * Drift detector: if a hex literal here no longer matches the corresponding
 * Chat token (`ice_chat_bg` / `ice_error` / `ice_warning` / `ice_positive` /
 * etc.), DO NOT edit the test to match — instead reconcile Vision's palette
 * back to Chat or open a spec change.
 */
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
    @Test fun lightIceChatWarning() = assertEquals(Color(0xFFB04030), LightIceChatWarning)
    @Test fun lightIceChatOnWarning() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnWarning)
    @Test fun lightIceChatPositive() = assertEquals(Color(0xFF2C8A6B), LightIceChatPositive)
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
    @Test fun darkIceChatWarning() = assertEquals(Color(0xFFE08570), DarkIceChatWarning)
    @Test fun darkIceChatOnWarning() = assertEquals(Color(0xFF08131B), DarkIceChatOnWarning)
    @Test fun darkIceChatPositive() = assertEquals(Color(0xFF5FC2A0), DarkIceChatPositive)
    @Test fun darkIceChatError() = assertEquals(Color(0xFFFF6B6B), DarkIceChatError)
    @Test fun darkIceChatOnError() = assertEquals(Color(0xFF08131B), DarkIceChatOnError)

    // Severity Container / OnContainer / Info tokens (Phase 3.1 Task 1)
    @Test fun darkIceChatErrorContainerIsPinned() = assertEquals(Color(0xFF7F1D1D), DarkIceChatErrorContainer)
    @Test fun darkIceChatOnErrorContainerIsPinned() = assertEquals(Color(0xFFFECACA), DarkIceChatOnErrorContainer)
    @Test fun lightIceChatErrorContainerIsPinned() = assertEquals(Color(0xFFFEE2E2), LightIceChatErrorContainer)
    @Test fun lightIceChatOnErrorContainerIsPinned() = assertEquals(Color(0xFF7F1D1D), LightIceChatOnErrorContainer)

    @Test fun darkIceChatWarningContainerIsPinned() = assertEquals(Color(0xFF78350F), DarkIceChatWarningContainer)
    @Test fun darkIceChatOnWarningContainerIsPinned() = assertEquals(Color(0xFFFDE68A), DarkIceChatOnWarningContainer)
    @Test fun lightIceChatWarningContainerIsPinned() = assertEquals(Color(0xFFFEF3C7), LightIceChatWarningContainer)
    @Test fun lightIceChatOnWarningContainerIsPinned() = assertEquals(Color(0xFF78350F), LightIceChatOnWarningContainer)

    @Test fun darkIceChatPositiveContainerIsPinned() = assertEquals(Color(0xFF14532D), DarkIceChatPositiveContainer)
    @Test fun darkIceChatOnPositiveContainerIsPinned() = assertEquals(Color(0xFFBBF7D0), DarkIceChatOnPositiveContainer)
    @Test fun lightIceChatPositiveContainerIsPinned() = assertEquals(Color(0xFFDCFCE7), LightIceChatPositiveContainer)
    @Test fun lightIceChatOnPositiveContainerIsPinned() = assertEquals(Color(0xFF14532D), LightIceChatOnPositiveContainer)

    // Positive onAccent (added Phase 3.1 Task 4 — needed by SeverityColors.Positive mapping)
    @Test fun darkIceChatOnPositiveIsPinned() = assertEquals(Color(0xFF08131B), DarkIceChatOnPositive)
    @Test fun lightIceChatOnPositiveIsPinned() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnPositive)

    @Test fun darkIceChatInfoIsPinned() = assertEquals(Color(0xFF60A5FA), DarkIceChatInfo)
    @Test fun darkIceChatOnInfoIsPinned() = assertEquals(Color(0xFF08131B), DarkIceChatOnInfo)
    @Test fun darkIceChatInfoContainerIsPinned() = assertEquals(Color(0xFF1E3A8A), DarkIceChatInfoContainer)
    @Test fun darkIceChatOnInfoContainerIsPinned() = assertEquals(Color(0xFFBFDBFE), DarkIceChatOnInfoContainer)
    @Test fun lightIceChatInfoIsPinned() = assertEquals(Color(0xFF2563EB), LightIceChatInfo)
    @Test fun lightIceChatOnInfoIsPinned() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnInfo)
    @Test fun lightIceChatInfoContainerIsPinned() = assertEquals(Color(0xFFDBEAFE), LightIceChatInfoContainer)
    @Test fun lightIceChatOnInfoContainerIsPinned() = assertEquals(Color(0xFF1E3A8A), LightIceChatOnInfoContainer)
}
