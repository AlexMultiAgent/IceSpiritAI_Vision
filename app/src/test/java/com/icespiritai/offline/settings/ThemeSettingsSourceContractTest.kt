package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract test for [ThemeSettingsSource] implementations.
 *
 * Any new source (DataStore-backed, in-memory, fake) must satisfy the
 * post-conditions below:
 *  - initial emission equals the source's persisted value (or a
 *    documented default — here SYSTEM)
 *  - setThemeMode writes propagate to subsequent collectors
 *
 * Runs against [FakeThemeSettingsSource] (which is the in-memory test
 * double used by SettingsViewModelTest + UpdateSectionTest). The
 * DataStore-backed [SettingsRepository] is exercised separately by the
 * integration smoke at startup; that path is not Robolectric-friendly
 * for the contract level.
 */
class ThemeSettingsSourceContractTest {

    @Test
    fun `initial value flows to first collector`() = runTest {
        val source: ThemeSettingsSource = FakeThemeSettingsSource(
            MutableStateFlow(ThemeMode.SYSTEM),
        )
        assertEquals(ThemeMode.SYSTEM, source.themeMode.first())
    }

    @Test
    fun `setThemeMode write is observed by next collector`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val source: ThemeSettingsSource = FakeThemeSettingsSource(backing)
        source.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, source.themeMode.first())
    }

    @Test
    fun `themeMode is exposed as a Flow type`() {
        // Pin the type signature: callers like SettingsViewModel rely on
        // .stateIn() being available, which requires Flow (not StateFlow).
        val source: ThemeSettingsSource = FakeThemeSettingsSource(
            MutableStateFlow(ThemeMode.SYSTEM),
        )
        val flow: Flow<ThemeMode> = source.themeMode
        // Compile-time check — assertNonNull is just to give the local a
        // use so the cast doesn't get tree-shaken in some configurations.
        assertEquals(true, flow != null)
    }
}
