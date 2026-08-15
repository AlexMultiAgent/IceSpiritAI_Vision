package com.icespiritai.offline.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `SYSTEM maps to FOLLOW_SYSTEM`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.toNightMode())
    }

    @Test
    fun `DARK maps to YES`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.toNightMode())
    }

    @Test
    fun `LIGHT maps to NO`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.toNightMode())
    }

    @Test
    fun `fromName falls back to SYSTEM on unknown`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("nonsense"))
    }

    @Test
    fun `fromName parses valid names`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromName("LIGHT"))
    }
}
