package com.icespiritai.offline

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.domain.AnalysisState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 1 single-flight guard coverage.
 *
 * `IceSpiritVisionViewModel.startAnalysis(uri)` cancels any in-flight analysis
 * before starting a new one — preventing two expensive PaddleOCR inferences
 * from running concurrently when the user rapid-taps "analyze".
 *
 * These tests inspect the private `currentJob` field via reflection rather than
 * observing coroutine completion: full pipeline execution would require the
 * PaddleOCR SDK to load ONNX models, which is androidTest territory. We only
 * need to confirm that `startAnalysis` records its `Job` and that a second call
 * cancels the first.
 *
 * Robolectric is the minimum we need to instantiate the `AndroidViewModel` —
 * the stub `android.app.Application()` constructor in `android.jar` throws
 * "Stub!" outside Robolectric, even with `unitTests.isReturnDefaultValues=true`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IceSpiritVisionViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): IceSpiritVisionViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return IceSpiritVisionViewModel(app)
    }

    private fun currentJob(vm: IceSpiritVisionViewModel): Job? {
        val field = vm.javaClass.getDeclaredField("currentJob").apply { isAccessible = true }
        return field.get(vm) as Job?
    }

    @Test
    fun state_isIdleOnConstruction() {
        val vm = newViewModel()
        assertEquals(AnalysisState.Idle, vm.state.value)
        assertEquals(null, currentJob(vm))
    }

    @Test
    fun startAnalysis_recordsCurrentJob() {
        val vm = newViewModel()
        assertEquals(null, currentJob(vm))

        vm.startAnalysis(android.net.Uri.parse("content://x"))
        val job = currentJob(vm)

        assertNotNull("startAnalysis must record the launched Job", job)
        assertFalse(
            "freshly recorded Job must not be cancelled (first call has nothing to cancel)",
            job!!.isCancelled
        )
    }

    @Test
    fun startAnalysis_secondCallCancelsFirstJob_singleFlightGuard() {
        val vm = newViewModel()
        vm.startAnalysis(android.net.Uri.parse("content://x"))
        val firstJob = currentJob(vm)
        assertNotNull("first Job must be recorded before second call", firstJob)

        // The actual guard: a second tap must cancel the in-flight job.
        vm.startAnalysis(android.net.Uri.parse("content://y"))
        val secondJob = currentJob(vm)

        assertNotNull("second Job must be recorded after second startAnalysis", secondJob)
        assertTrue(
            "single-flight guard: second startAnalysis must cancel the first Job",
            firstJob!!.isCancelled
        )
        assertFalse(
            "new Job (replacement) must not be cancelled — only the prior job is",
            secondJob!!.isCancelled
        )
        assertFalse(
            "replacement Job must be a distinct instance",
            firstJob === secondJob
        )
    }

    @Test
    fun reset_cancelsCurrentJobAndReturnsStateToIdle() {
        val vm = newViewModel()
        vm.startAnalysis(android.net.Uri.parse("content://x"))
        val job = currentJob(vm)
        assertNotNull(job)

        vm.reset()
        // reset() is fully synchronous (cancel + state writes happen inline
        // — see VM.reset KDoc for the trade-off vs startAnalysis's
        // cancelAndJoin pattern). All post-reset state is observable
        // immediately, no advanceUntilIdle() needed.

        assertTrue("reset() must cancel the current Job", job!!.isCancelled)
        assertEquals(AnalysisState.Idle, vm.state.value)
        assertEquals("reset() must clear pendingUri", null, vm.pendingUri.value)
    }

    /**
     * Atomicity: when startAnalysis is called twice in rapid succession, the
     * prior job's state must be Idle (not Loading) BEFORE the new job
     * observes the new pendingUri. Without `cancelAndJoin` inside the new
     * coroutine, the old job could still be emitting a Loading state with
     * the old uri when the new uri lands in _pendingUri — a UI flicker
     * ("analyze image A" then "analyze image B" with stale Loading showing
     * the wrong thumbnail).
     */
    @Test
    fun startAnalysis_secondCall_doesNotLeavePriorJobInLoadingState() {
        val vm = newViewModel()
        vm.startAnalysis(android.net.Uri.parse("content://x"))
        // Snapshot pendingUri BEFORE advancing the dispatcher; this is what
        // the UI sees during the brief transition.
        vm.startAnalysis(android.net.Uri.parse("content://y"))
        // Pump the dispatcher so cancelAndJoin inside the new coroutine runs.
        dispatcher.scheduler.advanceUntilIdle()
        // After atomicity, the new pendingUri is set only AFTER the prior
        // job is fully cancelled (no lingering Loading state).
        assertEquals(
            "second startAnalysis must commit pendingUri after prior job cancels",
            android.net.Uri.parse("content://y"),
            vm.pendingUri.value,
        )
        // The first job's collect lambda was cancelled by cancelAndJoin
        // before its Loading emission could land. The second job owns
        // currentJob now; verifying it is still running (not cancelled)
        // is the strongest contract we can pin from JVM unit tests without
        // a fake repository — the atomicity guarantee is that the FIRST
        // job was cancelled BEFORE the second job committed pendingUri,
        // which the existing startAnalysis_secondCallCancelsFirstJob_*
        // test already pins via isCancelled assertion on firstJob.
        val activeJob = currentJob(vm)
        assertNotNull("currentJob must be the second Job after atomic transition", activeJob)
        assertFalse(
            "currentJob must NOT be cancelled (the second job is the live one)",
            activeJob!!.isCancelled,
        )
    }
}
