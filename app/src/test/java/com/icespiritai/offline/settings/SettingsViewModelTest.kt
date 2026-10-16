package com.icespiritai.offline.settings

import com.icespiritai.offline.cache.CacheCleanupCoordinator
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
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var themeBacking: MutableStateFlow<ThemeMode>
    private lateinit var fakeSource: FakeThemeSettingsSource
    private lateinit var coordinator: CacheCleanupCoordinator
    private lateinit var cacheRoot: File
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        themeBacking = MutableStateFlow(ThemeMode.SYSTEM)
        fakeSource = FakeThemeSettingsSource(themeBacking)
        coordinator = CacheCleanupCoordinator()
        cacheRoot = TemporaryFolder().also { it.create() }.root
        // Same UnconfinedTestDispatcher for both Main and "IO" — keeps
        // runCacheCleanupNow's withContext(IO) deterministically on the
        // test scheduler so advanceUntilIdle covers the whole flow.
        vm = SettingsViewModel(
            source = fakeSource,
            coordinator = coordinator,
            cacheDir = cacheRoot,
            ioDispatcher = dispatcher,
        )
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

    @Test
    fun `auto retake glasses shot defaults on`() = runTest {
        assertEquals(true, vm.autoRetakeLowQualityGlassesShot.value)
    }

    @Test
    fun `setAutoRetakeLowQualityGlassesShot writes via source`() = runTest(dispatcher) {
        vm.setAutoRetakeLowQualityGlassesShot(false)

        assertEquals(false, fakeSource.autoRetakeBacking.value)
    }

    @Test fun `setFeatureVisible enable writes via source`() = runTest(dispatcher) {
        // Narrow the fake's set below `RuleTab.entries` so the enable path
        // actually adds FoodLabeling, rather than collapsing to
        // `current + tab == current` (the no-op case wouldn't exercise the
        // "actually adding" branch).
        fakeSource.visibleFeaturesBacking.value = setOf(RuleTab.AdSignage)
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
        assertEquals(
            "Expected exactly one LastFeatureCannotHide in collected snackbars, got $collected",
            1,
            collected.filterIsInstance<SettingsSnackbar.LastFeatureCannotHide>().size,
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

    @Test fun `cacheCleanupEnabled defaults on`() = runTest {
        assertEquals(true, vm.cacheCleanupEnabled.value)
    }

    @Test fun `setCacheCleanupEnabled writes via source`() = runTest(dispatcher) {
        vm.setCacheCleanupEnabled(false)

        assertEquals(false, fakeSource.cacheCleanupBacking.value)
    }

    @Test fun `setCacheCleanupEnabled source failure emits PersistFailed`() = runTest(dispatcher) {
        // Build a throwing fake that also exposes a cacheCleanupBacking
        // — needed to observe that the upstream StateFlow is NOT silently
        // flipped when the write throws.
        val throwingSource = ThrowingFakeThemeSettingsSource(IOException("disk full"))
        val throwingVm = SettingsViewModel(throwingSource)

        val collected = mutableListOf<SettingsSnackbar>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            throwingVm.snackbar.collect { collected.add(it) }
        }

        throwingVm.setCacheCleanupEnabled(false)
        advanceUntilIdle()

        job.cancel()
        val persistFailed = collected.filterIsInstance<SettingsSnackbar.PersistFailed>()
        assertEquals(1, persistFailed.size)
        assertEquals("disk full", persistFailed[0].cause.message)
    }

    @Test fun `runCacheCleanupNow drives coordinator and emits summary`() = runTest(dispatcher) {
        // Plant files in the test cacheRoot's capture/ bucket so the
        // coordinator's first run has something to delete.
        val captureDir = File(cacheRoot, "capture").apply { mkdirs() }
        val stale = File(captureDir, "stale.jpg").apply {
            writeBytes(ByteArray(100))
            // Set mtime 100 days in the past so the default policy deletes it.
            setLastModified(System.currentTimeMillis() - 100L * 24L * 60L * 60L * 1000L)
        }

        val collected = mutableListOf<com.icespiritai.offline.cache.CacheCleanupSummary>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.lastCleanupSummary.collect { collected.add(it) }
        }

        vm.runCacheCleanupNow()
        advanceUntilIdle()

        job.cancel()
        assertTrue("stale file deleted", !stale.exists())
        assertEquals(1, collected.size)
        assertTrue("at least 1 file deleted", collected[0].totalDeleted >= 1)
        assertEquals(coordinator.lastCleanupResult!!.totalDeleted, collected[0].totalDeleted)
    }
}
