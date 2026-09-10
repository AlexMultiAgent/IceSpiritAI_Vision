package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var themeBacking: MutableStateFlow<ThemeMode>
    private lateinit var fakeSource: FakeThemeSettingsSource
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        themeBacking = MutableStateFlow(ThemeMode.SYSTEM)
        fakeSource = FakeThemeSettingsSource(themeBacking)
        vm = SettingsViewModel(fakeSource)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `themeMode reflects repository flow`() = runTest {
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)

        themeBacking.value = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode calls repository`() = runTest {
        vm.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, themeBacking.value)
    }

    @Test fun `setFeatureVisible enable writes via source`() = runTest(dispatcher) {
        vm.setFeatureVisible(RuleTab.FoodLabeling, true)

        assertEquals(
            setOf(RuleTab.AdSignage, RuleTab.FoodLabeling),
            fakeSource.visibleFeaturesBacking.value,
        )
    }

    @Test fun `setFeatureVisible disable non-last writes via source`() = runTest(dispatcher) {
        vm.setFeatureVisible(RuleTab.FoodLabeling, false)

        assertEquals(
            setOf(RuleTab.AdSignage),
            fakeSource.visibleFeaturesBacking.value,
        )
    }

    /**
     * Enforcement contract: when the user tries to hide the LAST visible tab
     * (would leave the tab bar empty), the ViewModel must NOT call the
     * upstream write — it surfaces [SettingsSnackbar.LastFeatureCannotHide]
     * instead so the UI can show a "至少保留一个" toast and the persisted
     * state stays at the last-allowed value.
     */
    @Test fun `setFeatureVisible disable last emits LastFeatureCannotHide`() = runTest(dispatcher) {
        // Subscribe BEFORE the action so tryEmit lands directly on the
        // active collector (replay=0 + extraBufferCapacity=4 keeps items
        // briefly buffered, but reading from an active collector is the
        // deterministic pattern).
        val collected = mutableListOf<SettingsSnackbar>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.snackbar.collect { collected.add(it) }
        }

        // Initial: {AdSignage, FoodLabeling}. Hide FoodLabeling first →
        // {AdSignage}.
        vm.setFeatureVisible(RuleTab.FoodLabeling, false)
        advanceUntilIdle()
        // Now hide AdSignage — would leave the set empty → rejected.
        vm.setFeatureVisible(RuleTab.AdSignage, false)
        advanceUntilIdle()

        job.cancel()
        assertTrue(
            "Expected LastFeatureCannotHide in collected snackbars, got $collected",
            collected.any { it is SettingsSnackbar.LastFeatureCannotHide },
        )
        // Persisted state must NOT have moved — the rejected write must
        // not flip the upstream to an empty set.
        assertEquals(setOf(RuleTab.AdSignage), fakeSource.visibleFeaturesBacking.value)
    }

    /**
     * Persistence failure contract: when the upstream write throws (e.g.
     * DataStore IOException from a corrupted preferences file), the
     * ViewModel surfaces [SettingsSnackbar.PersistFailed] with the cause
     * so the UI can offer retry — and crucially, the in-memory StateFlow
     * has not been silently advanced to the would-be new value, because
     * the write never landed.
     */
    @Test fun `setFeatureVisible source failure emits PersistFailed`() = runTest(dispatcher) {
        val throwingSource = ThrowingFakeThemeSettingsSource(IOException("disk full"))
        val throwingVm = SettingsViewModel(throwingSource)

        val collected = mutableListOf<SettingsSnackbar>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            throwingVm.snackbar.collect { collected.add(it) }
        }

        throwingVm.setFeatureVisible(RuleTab.FoodLabeling, false)
        advanceUntilIdle()

        job.cancel()
        val persistFailed = collected.filterIsInstance<SettingsSnackbar.PersistFailed>()
        assertEquals(1, persistFailed.size)
        assertEquals("disk full", persistFailed[0].cause.message)
    }
}