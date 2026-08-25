package com.icespiritai.offline.ui.theme

import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityColorsTest {
    @Test fun darkViolationAccentIsDarkError() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatError, s.accent(Severity.Violation))
    }
    @Test fun lightViolationAccentIsLightError() {
        val s = SeverityColors(isDark = false)
        assertEquals(LightIceChatError, s.accent(Severity.Violation))
    }
    @Test fun darkViolationContainerIsPinned() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatErrorContainer, s.container(Severity.Violation))
    }
    @Test fun darkWarningAccentIsDarkWarning() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatWarning, s.accent(Severity.Warning))
    }
    @Test fun darkPositiveContainerIsPPositive() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatPositiveContainer, s.container(Severity.Positive))
    }
    @Test fun darkInfoAccentIsPInfo() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatInfo, s.accent(Severity.Info))
    }
    @Test fun lightInfoOnContainerIsPInfo() {
        val s = SeverityColors(isDark = false)
        assertEquals(LightIceChatOnInfoContainer, s.onContainer(Severity.Info))
    }
}