package com.icespiritai.offline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.analysis.ImageAnalyzerRepository
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.AnalysisState.Idle
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.ocr.OcrEngine
import com.icespiritai.offline.ocr.OcrEngineFactoryLocator
import com.icespiritai.offline.rules.AdSignageRuleLoader
import com.icespiritai.offline.rules.AdSignageRuleMatcher
import com.icespiritai.offline.rules.FoodLabelRuleLoader
import com.icespiritai.offline.rules.FoodLabelRuleMatcher
import com.icespiritai.offline.rules.RuleMatcher
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 1 UI driver: maps a cold [ImageAnalyzerRepository] flow onto a
 * [StateFlow] of [AnalysisState] for the Compose layer.
 *
 * Profile gating is resolved at build time by the active `modelProfile`
 * sourceSet (`shell/` or `ice_ocr_rules/`), which contributes one
 * `OcrEngineFactory` via `META-INF/services/`. [OcrEngineFactoryLocator]
 * picks the first such factory on the classpath — there is no compile-time
 * `if (BuildConfig.MODEL_PROFILE == ...)` branch here, and the `main`
 * sourceSet has no direct knowledge of either implementation. This keeps
 * the `shell` APK slim: the PaddleOCR SDK, ONNX Runtime, and OpenCV never
 * reach the classpath when the profile is `shell`.
 *
 * Two parallel rulesets are wired in (`ad_signage_rules.json` and
 * `food_label_rules.json`). Each matcher's first construction is wrapped in
 * `lazy { }`, so an asset-load failure (a missing or malformed bundled
 * JSON, e.g. from a packaging defect) only surfaces when the user first
 * analyzes on that tab — i.e. as `AnalysisState.Error(RULES_FAILED)` with
 * a UI message — instead of throwing out of this ViewModel's constructor
 * where no UI state exists to display it.
 *
 * [onCleared] deliberately does **not** release [ocrEngine]: the underlying
 * PaddleOCR instance holds process-wide native resources (ONNX sessions,
 * native Mat arenas) whose teardown belongs to a process-scoped owner, not a
 * per-ViewModel lifecycle. Re-creating the engine on every ViewModel
 * instantiation would be wasteful; subsequent ViewModels would otherwise
 * re-init. Eager teardown is out of scope for Phase 1.
 */
class IceSpiritVisionViewModel(application: Application) : AndroidViewModel(application) {

    // Lazy to match the rule-loader pattern below: a missing
    // `OcrEngineFactory` on the classpath (e.g. a packaging defect that
    // omitted `buildProfileServicesJar`'s output from the APK) should
    // surface as `AnalysisState.Error(OCR_UNAVAILABLE)` when the user
    // actually tries to analyze, not as an IllegalStateException out of
    // this ViewModel's constructor where no UI state exists to display it.
    // [OcrEngineFactoryLocator.create] throws IllegalStateException via
    // `error(...)`; that exception will propagate out of this lazy on
    // first access inside `startAnalysis`, where `repository.analyze`'s
    // catch-all block converts it into the domain Error.
    private val ocrEngine: OcrEngine by lazy { OcrEngineFactoryLocator.create(application) }
    private val app = application

    private val adMatcher: RuleMatcher by lazy {
        AdSignageRuleMatcher(AdSignageRuleLoader(app).load())
    }
    private val foodMatcher: RuleMatcher by lazy {
        FoodLabelRuleMatcher(FoodLabelRuleLoader(app).load())
    }

    private fun matcherFor(tab: RuleTab): RuleMatcher = when (tab) {
        RuleTab.AdSignage -> adMatcher
        RuleTab.FoodLabeling -> foodMatcher
    }

    private val repository = ImageAnalyzerRepository(ocrEngine)

    private val _currentTab = MutableStateFlow(RuleTab.AdSignage)
    val currentTab: StateFlow<RuleTab> = _currentTab.asStateFlow()

    private val _state = MutableStateFlow<AnalysisState>(Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    /**
     * The image URI currently staged for analysis (or shown in the viewer).
     *
     * `HomeScreen` is the only writer: it sets this when a capture / pick
     * succeeds. The Viewer route reads it via `collectAsState()` so the
     * full-screen viewer opens against the same image the user just
     * double-tapped. Cleared by [reset] alongside the analysis state so
     * "back to a clean slate" wipes both the preview and the in-flight
     * analysis in one shot.
     *
     * Kept on the ViewModel (not in `HomeScreen`'s local `remember`) so
     * the Viewer composable, which is a sibling destination in the
     * `NavHost`, can read it without `savedStateHandle` plumbing.
     */
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    private var currentJob: Job? = null

    private companion object {
        /**
         * P0-C002: outer analyze-pipeline watchdog. Caps the worst-case
         * hang of the full `BitmapLoader.decode + OCR + RuleMatcher.scan`
         * pipeline at 30 s before surfacing a recoverable Error state.
         *
         * Rationale for 30 s vs the audit's 10 s recommendation: the
         * full pipeline routinely exceeds 10 s on slower devices without
         * any system freeze (cold OCR first-touch, large rule sets, big
         * images). 30 s gives the legitimate slow path 6-12× headroom
         * against the documented 2.6 s warm / 5 s cold OCR SLA
         * (`docs/smoke/2026-08-20-icevision-v0.1.12-real-device.md`) while
         * still bounding user-perceived stall. The inner
         * [com.icespiritai.offline.ocr.PaddleOcrEngine] mutex timeout
         * (also 30 s) covers the OCR-specific deadlock independently;
         * this outer watchdog catches hangs in `BitmapLoader` or
         * `RuleMatcher.scan` that the inner timeout can't see.
         *
         * Together they cap worst-case hang at 30 s on either layer.
         */
        const val ANALYZE_WATCHDOG_TIMEOUT_MS = 30_000L
    }

    /**
     * Switch the active tab. Returns `true` when the call actually changed
     * the selected tab.
     *
     * Tab-routing contract (CLAUDE.md §Tab → 初始页, 2026-08-26):
     *   - tab 切换 → 老路径:切换 matcher,保留 state (no reset)
     *   - tab 不变 + `state is Loading` → no-op(防误触打断正在跑的 OCR / 规则扫描)
     *   - tab 不变 + `state !is Loading` → 「回到初始」调 reset()(清 pendingUri +
     *     state 走回 Idle)
     *
     * The third clause covers the user scenario where after a `Complete`
     * report is shown, the user taps the already-selected 「广告招牌」tab
     * to start fresh on a new image — there's no separate "back to home"
     * button, and the tab is the cleanest "clear" affordance.
     */
    fun setTab(tab: RuleTab): Boolean {
        val isTabSwitch = _currentTab.value != tab
        if (isTabSwitch) {
            _currentTab.value = tab
            return true
        }
        // Same tab: Loading 时 no-op,否则 reset 回 Idle。
        if (_state.value is AnalysisState.Loading) return false
        reset()
        return false
    }

    /**
     * Start analysis for [uri]. Atomic w.r.t. any in-flight job:
     *
     * 1. Capture the currently-running job into [prior] and replace
     *    [currentJob] with the new launch synchronously (the UI can see the
     *    new Job the instant `startAnalysis` returns).
     * 2. Inside the new coroutine, `cancelAndJoin` on [prior] so the old job
     *    fully unwinds before we touch any state — preventing a brief window
     *    where the old job is still emitting a `Loading` state with the old
     *    `_pendingUri`, while the UI already sees a different
     *    `_pendingUri` from a rapid double-tap.
     * 3. Set `_pendingUri`, clear `_state` to [Idle], then start collecting.
     *
     * Callers do not need to wrap this in a coroutine; [viewModelScope] is
     * the parent scope.
     */
    fun startAnalysis(uri: Uri) {
        val matcher = matcherFor(_currentTab.value)
        val prior = currentJob
        prior?.cancel()
        currentJob = viewModelScope.launch {
            prior?.cancelAndJoin()
            _pendingUri.value = uri
            _state.value = Idle
            // P0-C002: outer watchdog (see companion KDoc). withTimeoutOrNull
            // cancels its body when the timer expires — the cancellation
            // propagates through `repository.analyze`'s cold flow (the
            // `flow { ... }` builder rethrows CancellationException from
            // any in-flight suspend point, so OCR / rule scan stop cleanly)
            // and returns null. We then surface a recoverable Error so the
            // user can tap "Retry" instead of staring at a Loading spinner.
            withTimeoutOrNull(ANALYZE_WATCHDOG_TIMEOUT_MS) {
                repository.analyze(uri, matcher).collect { _state.value = it }
            } ?: run {
                // Cause is null on purpose: TimeoutCancellationException is
                // an internal coroutine primitive we don't want to leak
                // into the user's Error panel. The message carries the
                // context — including the "可能是后台被系统冻结" hint that
                // matches the v0.1.41 Toast in UpdateSection.kt for
                // background-killer correlations.
                _state.value = AnalysisState.Error(
                    message = "分析超时(>${ANALYZE_WATCHDOG_TIMEOUT_MS / 1000}s):可能是后台被系统冻结。请重试",
                    errorCode = ErrorCode.OCR_UNAVAILABLE,
                    retryable = true,
                )
            }
        }
    }

    /**
     * Set the pending URI without triggering a fresh analysis. Useful when
     * a higher layer (e.g. an exported share intent) wants to stage a URI
     * that the viewer should be able to open before any OCR pass runs.
     */
    fun setPendingUri(uri: Uri?) {
        _pendingUri.value = uri
    }

    /**
     * Reset the ViewModel to a clean slate: cancel any in-flight analysis
     * and clear [state] + [pendingUri]. Fully synchronous — both [Job.cancel]
     * and the state writes happen on the calling thread so the UI sees Idle
     * the instant `reset()` returns.
     *
     * **Why synchronous (not `cancelAndJoin`-inside-launch like
     * [startAnalysis]):** the analyze pipeline suspends on
     * `withContext(Dispatchers.Default) { matcher.scan(...) }` inside
     * `ImageAnalyzerRepository.analyze`. Under JVM unit tests,
     * `StandardTestDispatcher.advanceUntilIdle()` does NOT wait for
     * external dispatchers to complete, so an async `cancelAndJoin`
     * races with the test assertions. Synchronous cancel + state writes
     * give unit tests an immediate, deterministic post-reset state.
     *
     * **Atomicity trade-off:** [startAnalysis] defers its `_pendingUri`
     * write until after `cancelAndJoin` because a stale thumbnail showing
     * the wrong image is the worst-case UI artifact. [reset] does NOT
     * defer — the worst-case artifact of a stale Loading emission
     * landing between the Idle write and the prior job's next yield
     * point is a brief UI flicker, and in practice [reset] is only
     * invoked when state is not Loading (see [setTab] + the
     * `ErrorPanel.onReset` path), so the analyze pipeline is not in
     * flight when this runs.
     */
    fun reset() {
        currentJob?.cancel()
        _state.value = Idle
        _pendingUri.value = null
    }

    override fun onCleared() {
        currentJob?.cancel()
        // Intentionally no ocrEngine.release() — see KDoc above.
        super.onCleared()
    }
}
