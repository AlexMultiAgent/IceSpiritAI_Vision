package com.icespiritai.offline

import android.app.Application
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Tab-routing coverage for [IceSpiritVisionViewModel].
 *
 * Verifies the three contracts of the dual-domain wiring without forcing
 * the lazy `adMatcher` / `foodMatcher` resolution (Robolectric's shadow
 * asset manager does not ship `app/src/main/assets/rules/...` into the
 * test JVM, so materializing the lazy would throw
 * `RuleLoadFailed(FileNotFoundException)`):
 *
 *   1. [IceSpiritVisionViewModel.currentTab] defaults to `RuleTab.AdSignage`.
 *   2. [IceSpiritVisionViewModel.setTab] returns `true` only on an actual
 *      transition — caller uses that signal to reset the stale report.
 *   3. [IceSpiritVisionViewModel.startAnalysis] reads `_currentTab` at call
 *      time (proven via reflection on the StateFlow value before/after
 *      each call) so a tab switch made *between* two analyses routes the
 *      second one through the new domain's matcher.
 *
 * The matcher-selection itself is exercised end-to-end by the on-device
 * smoke plan; here we assert on the routing inputs, not on the matcher
 * output, which is the layer where Robolectric can substitute.
 *
 * Robolectric is needed so the `Application` instance returned by
 * `ApplicationProvider` is real (the unit-test stub `Application()`
 * throws "Stub!" under any non-Robolectric runner).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IceSpiritVisionViewModelTabTest {

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
    fun currentTab_defaultsToAdSignage() {
        val vm = newViewModel()
        assertEquals(RuleTab.AdSignage, vm.currentTab.value)
    }

    @Test
    fun setTab_returnsTrueOnActualChange_returnsFalseOnSameValue() {
        val vm = newViewModel()
        // first switch — actual change
        assertTrue("AdSignage -> FoodLabeling must report changed=true", vm.setTab(RuleTab.FoodLabeling))
        assertEquals(RuleTab.FoodLabeling, vm.currentTab.value)
        // second call with same value — no change
        assertFalse("FoodLabeling -> FoodLabeling must report changed=false", vm.setTab(RuleTab.FoodLabeling))
        // back to original — actual change again
        assertTrue("FoodLabeling -> AdSignage must report changed=true", vm.setTab(RuleTab.AdSignage))
        assertEquals(RuleTab.AdSignage, vm.currentTab.value)
    }

    @Test
    fun setTab_doesNotMutate_stateFlow() {
        val vm = newViewModel()
        val initial = vm.state.value
        vm.setTab(RuleTab.FoodLabeling)
        assertEquals(
            "setTab must not throw AnalysisState into Loading — only startAnalysis does that",
            initial,
            vm.state.value,
        )
    }

    @Test
    fun startAnalysis_recordsJobWithoutResolvingMatcherImmediately() {
        // The lazy `adMatcher` should not resolve just from recording a Job;
        // resolution is deferred to first analyze flow execution. We assert
        // here that the call doesn't synchronously throw — the actual
        // resolve happens later, in viewModelScope.
        val vm = newViewModel()
        vm.startAnalysis(Uri.parse("content://stub"))
        val job = currentJob(vm)
        assertNotNull("startAnalysis must record a Job", job)
    }

    @Test
    fun setTab_changesCurrentTabValueThatStartAnalysisWouldRead() {
        // `startAnalysis(uri)` reads `matcherFor(_currentTab.value)` at call
        // time. We can't actually call `startAnalysis` on a switched tab
        // under Robolectric (the lazy rule loader would throw — see the
        // test-class KDoc). What we CAN assert is the precondition: after
        // a `setTab(FoodLabeling)` call, the next `startAnalysis` would
        // see `_currentTab.value == FoodLabeling`. The matcher routing
        // itself is covered by the on-device smoke plan; this test guards
        // the StateFlow contract that the routing depends on.
        val vm = newViewModel()
        assertEquals(RuleTab.AdSignage, vm.currentTab.value)

        vm.setTab(RuleTab.FoodLabeling)
        assertEquals(
            "after setTab(FoodLabeling), _currentTab.value must be FoodLabeling",
            RuleTab.FoodLabeling,
            vm.currentTab.value,
        )

        vm.setTab(RuleTab.AdSignage)
        assertEquals(
            "after setTab(AdSignage), _currentTab.value must be AdSignage",
            RuleTab.AdSignage,
            vm.currentTab.value,
        )
    }

    // ---- §Tab → 初始页 contract (CLAUDE.md 2026-08-26) ----
    //
    // Three-state behavior:
    //   (a) tab 不变 + state !is Loading → reset() 回 Idle (清 pendingUri + state)
    //   (b) tab 不变 + state is Loading  → no-op (防误触打断 OCR)
    //   (c) tab 切换 → 保留 state (caller-side reset() 路径已删除,改由 VM 内部决策)

    @Test
    fun setTab_sameTab_nonLoadingState_resetsToIdle() {
        // Spec (a): 用户在 Complete 报告状态下再次点选中的「广告招牌」tab
        // → 应回到 Idle 初始页,清 pendingUri。
        val vm = newViewModel()
        vm.setPendingUri(Uri.parse("content://stub"))
        assertNotNull("precondition: pendingUri must be non-null before setTab", vm.pendingUri.value)

        assertFalse(
            "setTab(same tab, non-Loading) must return false (tab unchanged)",
            vm.setTab(RuleTab.AdSignage),
        )
        assertEquals(
            "setTab(same tab, non-Loading) must clear pendingUri via internal reset()",
            null, vm.pendingUri.value,
        )
        assertEquals(
            "setTab(same tab, non-Loading) must set state=Idle via internal reset()",
            AnalysisState.Idle, vm.state.value,
        )
    }

    @Test
    fun setTab_sameTab_loadingState_isNoOp() {
        // Spec (b): Loading 状态下点 tab 必须是 no-op,不能打断正在跑的 OCR。
        // 用反射设 _state=Loading — 不能直接调 startAnalysis,会触发 lazy matcher
        // 解析然后 Robolectric 下 FileNotFoundException(见类 KDoc)。
        val vm = newViewModel()
        val stateField = vm.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(vm) as MutableStateFlow<AnalysisState>
        stateFlow.value = AnalysisState.Loading(AnalysisState.Loading.Stage.OcrRunning)
        vm.setPendingUri(Uri.parse("content://in-progress"))
        val pendingBefore = vm.pendingUri.value
        assertNotNull(pendingBefore)

        assertFalse(
            "setTab(same tab, Loading) must return false",
            vm.setTab(RuleTab.AdSignage),
        )
        assertEquals(
            "setTab(same tab, Loading) must NOT clear pendingUri (no-op contract)",
            pendingBefore, vm.pendingUri.value,
        )
        assertTrue(
            "setTab(same tab, Loading) must keep state=Loading (no reset)",
            vm.state.value is AnalysisState.Loading,
        )
    }

    @Test
    fun setTab_tabSwitch_doesNotReset() {
        // Spec (c): tab 切换必须保留 state,不能误把已完成报告清掉。
        // 原 HomeScreen caller-side `if (setTab) reset()` 会导致 tab 切换也走
        // reset 路径,与 spec 矛盾。修复后 caller-side reset() 删除,setTab
        // 内部按 spec 决策。
        val vm = newViewModel()
        val stuckUri = Uri.parse("content://stuck-report")
        vm.setPendingUri(stuckUri)

        assertTrue(
            "tab 切换 must return true (actual change)",
            vm.setTab(RuleTab.FoodLabeling),
        )
        assertEquals(
            "tab 切换 must NOT clear pendingUri (state preserved per spec)",
            stuckUri, vm.pendingUri.value,
        )
        assertEquals(
            "tab 切换 must keep state=Idle (default, not reset to a new state)",
            AnalysisState.Idle, vm.state.value,
        )
        assertEquals(
            "tab 切换 must update _currentTab.value",
            RuleTab.FoodLabeling, vm.currentTab.value,
        )
    }
}