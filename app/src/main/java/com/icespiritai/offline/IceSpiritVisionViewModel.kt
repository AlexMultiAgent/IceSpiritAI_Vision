package com.icespiritai.offline

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
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
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.settings.ThemeSettingsSource
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
 * [visibleFeatures] mirrors the persisted `visible_features: Set<RuleTab>`
 * from [ThemeSettingsSource] (DataStore-backed via [SettingsRepository] in
 * production). The user toggles each tab's visibility in Settings —
 * [matcherFor] / [setTab] consult this set so a disabled tab's matcher is
 * never loaded and a disabled tab click is a no-op. Initial value is
 * [RuleTab.entries] (all tabs visible) so a fresh install's first
 * composition matches the production "show everything" baseline even
 * before DataStore's first read lands.
 *
 * [onCleared] deliberately does **not** release [ocrEngine]: the underlying
 * PaddleOCR instance holds process-wide native resources (ONNX sessions,
 * native Mat arenas) whose teardown belongs to a process-scoped owner, not a
 * per-ViewModel lifecycle. Re-creating the engine on every ViewModel
 * instantiation would be wasteful; subsequent ViewModels would otherwise
 * re-init. Eager teardown is out of scope for Phase 1.
 */
class IceSpiritVisionViewModel(
    application: Application,
    settingsSource: ThemeSettingsSource,
) : AndroidViewModel(application) {

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

    /**
     * Spec §3.3 race 校验: 用户在设置层隐藏某个 tab 后,VM 必须把该 tab
     * 过滤掉 — `matcherFor` 拒绝返回 matcher,`setTab` 拒绝切换。Production
     * binding 由 [Companion.factory] 提供 [SettingsRepository];测试用
     * `FakeThemeSettingsSource` 注入任意 Flow 值。
     *
     * `internal` 是为了让测试访问(同 module)又不污染 production 公开 API
     * — 真实 UI 消费走 [setTab] / [matcherFor] 这两个公开/可见 entry,不再
     * 直接读 `settingsSource`。
     */
    val visibleFeatures: StateFlow<Set<RuleTab>> = settingsSource.visibleFeatures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RuleTab.entries.toSet(),
    )

    /**
     * Resolve the [RuleMatcher] for [tab], or `null` if [tab] is not in
     * [visibleFeatures].
     *
     * **Visible to tests** (`internal`, not `private`) so the disabled-tab
     * contract can be pinned from the JVM unit test class
     * [com.icespiritai.offline.IceSpiritVisionViewModelTabTest] without
     * reflection. Production callers (currently [startAnalysis]) check the
     * null result and skip the analyze pipeline if the requested tab is
     * disabled.
     *
     * **Resolution side-effect — read this before calling from Compose.**
     * `adMatcher` / `foodMatcher` are declared `by lazy { ... }` of static
     * type [RuleMatcher]; reading them from this function synchronously
     * invokes the generated getter and **force-resolves the lazy** (asset
     * read + JSON parse + Aho-Corasick keyword automaton build, ~tens of ms
     * on a cold device). The function cannot return the unresolved delegate
     * — its return type is [RuleMatcher], not `Lazy<RuleMatcher>`. The lazy
     * is process-cached: the *first* call per tab in the VM's lifetime pays
     * this cost; every subsequent call is O(1). A disabled tab is short-
     * circuited before resolution, so the asset is never read.
     *
     * **Do NOT call this from a Composable body without wrapping in
     * `remember` / `derivedStateOf`** — a recomposition would force-resolve
     * the lazy on every frame. Production call sites consume [startAnalysis]
     * (which is event-driven, not recomposition-driven), so this hazard
     * doesn't currently bite. If a future caller needs visibility, use
     * [isTabEnabled] for the cheap path and cache the matcher reference
     * via `remember { vm.matcherFor(tab) }` if the resolve is intentional.
     */
    internal fun matcherFor(tab: RuleTab): RuleMatcher? {
        val enabledTabs = visibleFeatures.value
        if (tab !in enabledTabs) {
            Log.w(
                TAG,
                "matcherFor($tab) requested but tab is disabled (visible=$enabledTabs)",
            )
            return null
        }
        return when (tab) {
            RuleTab.AdSignage -> adMatcher
            RuleTab.FoodLabeling -> foodMatcher
        }
    }

    /**
     * Cheap visibility probe for [tab]. Returns `true` iff [tab] is in the
     * current [visibleFeatures] set.
     *
     * Exists alongside [matcherFor] so UI callers (e.g. a future
     * `RuleTabBar` parameterization in Task 4 that needs to grey-out or
     * hide a tab) can check visibility **without** paying the matcher
     * lazy-resolve cost. A visibility check is a pure `Set.contains` over
     * a StateFlow snapshot — O(1), no asset I/O, no Aho-Corasick build.
     *
     * Use [isTabEnabled] when the question is "should this tab render at
     * all". Use [matcherFor] only when an actual matcher reference is
     * needed (the only such caller today is [startAnalysis]).
     */
    fun isTabEnabled(tab: RuleTab): Boolean = tab in visibleFeatures.value

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
     * Switch the active tab. Returns `true` iff the call actually changed
     * the selected tab. **`false` is overloaded** — it does not mean
     * "nothing happened"; inspect the case below:
     *
     *  1. **Disabled tab — rejected, no mutation.** [tab] is not in
     *     [visibleFeatures]: returns `false`, leaves [_currentTab] /
     *     [state] / [pendingUri] untouched. Guards the Spec §3.3 race
     *     (settings hide racing a stale TabBar render that still shows
     *     the disabled tab).
     *  2. **Same tab, state is Loading — no-op, no mutation.** Returns
     *     `false`, state stays Loading. Prevents a mis-tap from
     *     interrupting an in-flight OCR / rule scan.
     *  3. **Same tab, state is not Loading — `reset()` ran, but the tab
     *     itself did not change.** Returns `false` while
     *     synchronously clearing [state] to [Idle] and [pendingUri] to
     *     `null`. The user-facing intent here is the CLAUDE.md §Tab →
     *     初始页 affordance: tapping the already-selected tab on a
     *     Complete report goes back to the Idle initial page so a new
     *     image can be picked. The return value stays `false` because
     *     `_currentTab` did not change — callers that key off the
     *     "tab actually changed" signal are unaffected by this branch.
     *
     * **Caller note:** the only current caller (`HomeScreen.onSelectTab`)
     * ignores the return value, so the `false`-on-reset overload does
     * not affect production behaviour. If a future caller wants to
     * distinguish case 1 (reject, no mutation) from case 3 (reset
     * happened), it must inspect [state] / [pendingUri] afterwards
     * rather than rely on the Boolean alone.
     */
    fun setTab(tab: RuleTab): Boolean {
        if (tab !in visibleFeatures.value) {
            Log.w(
                TAG,
                "setTab($tab) ignored: tab is disabled (visible=${visibleFeatures.value})",
            )
            return false
        }
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
     * 1. Resolve the matcher for the current tab via [matcherFor]; if it
     *    returns `null` (the tab was just disabled in settings), bail out
     *    without touching [currentJob] — disabling the tab should not cancel
     *    a healthy in-flight analysis that was started while the tab was
     *    still enabled.
     * 2. Capture the currently-running job into [prior] and replace
     *    [currentJob] with the new launch synchronously (the UI can see the
     *    new Job the instant `startAnalysis` returns).
     * 3. Inside the new coroutine, `cancelAndJoin` on [prior] so the old job
     *    fully unwinds before we touch any state — preventing a brief window
     *    where the old job is still emitting a `Loading` state with the old
     *    `_pendingUri`, while the UI already sees a different
     *    `_pendingUri` from a rapid double-tap.
     * 4. Set `_pendingUri`, clear `_state` to [Idle], then start collecting.
     *
     * Callers do not need to wrap this in a coroutine; [viewModelScope] is
     * the parent scope.
     */
    fun startAnalysis(uri: Uri) {
        val matcher = matcherFor(_currentTab.value)
        if (matcher == null) {
            Log.w(
                TAG,
                "startAnalysis: currentTab=${_currentTab.value} is disabled (visible=${visibleFeatures.value}); skip analyze",
            )
            return
        }
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

    companion object {
        /**
         * Logcat tag for disabled-tab guards in [matcherFor] / [setTab] /
         * [startAnalysis]. Keeps the spec §3.3 race rejections grep-able.
         */
        private const val TAG = "IceSpiritVisionVM"

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

        /**
         * [androidx.lifecycle.ViewModelProvider.Factory] that wires a real
         * DataStore-backed [SettingsRepository] into the VM. Mirrors
         * [com.icespiritai.offline.settings.SettingsViewModel.factory] in
         * shape (single `repository` argument) — deviating from the
         * `AndroidViewModel`'s default factory is required because this VM
         * needs both an [Application] (for asset loading /
         * `OcrEngineFactoryLocator`) **and** a settings source (for
         * [visibleFeatures]) and the default factory only knows the no-arg
         * `AndroidViewModel(application)` constructor.
         *
         * The [Application] is recovered from [CreationExtras] via
         * [APPLICATION_KEY] — `viewModel()` populates this automatically
         * from the enclosing `LocalViewModelStoreOwner`'s
         * `ViewModelStoreOwner.androidApplicationContext` (the Activity,
         * when called from inside a `composable` block), so callers do
         * not need to pass it themselves and there is no
         * `as android.app.Application` cast at the call sites.
         *
         * Used by [com.icespiritai.offline.ui.nav.IceSpiritNavHost] and
         * the default branch of [com.icespiritai.offline.ui.home.HomeScreen].
         * Tests use a `FakeThemeSettingsSource` directly via the public
         * constructor.
         */
        fun factory(repository: SettingsRepository): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras — IceSpiritVisionViewModel.factory " +
                            "must be invoked through viewModel(), which populates it from the enclosing " +
                            "LocalViewModelStoreOwner's android context."
                    }
                    IceSpiritVisionViewModel(app, repository)
                }
            }
    }
}
