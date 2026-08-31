package com.icespiritai.offline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.analysis.ImageAnalyzerRepository
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.AnalysisState.Idle
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

    private val ocrEngine: OcrEngine = OcrEngineFactoryLocator.create(application)
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
            repository.analyze(uri, matcher).collect { _state.value = it }
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
