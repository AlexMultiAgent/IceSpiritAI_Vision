package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `themeMode reflects repository flow`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val source = FakeThemeSettingsSource(backing)
        val vm = SettingsViewModel(source)

        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)

        backing.value = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode calls repository`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val source = FakeThemeSettingsSource(backing)
        val vm = SettingsViewModel(source)

        vm.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, backing.value)
    }
}