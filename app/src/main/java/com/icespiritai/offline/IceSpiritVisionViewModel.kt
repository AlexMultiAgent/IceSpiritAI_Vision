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

    private var currentJob: Job? = null

    /**
     * Switch the active tab. Returns `true` when the call actually changed
     * the selected tab — the caller typically pairs that with a
     * [reset]/image-clear to drop the previous domain's stale report.
     */
    fun setTab(tab: RuleTab): Boolean {
        val changed = _currentTab.value != tab
        _currentTab.value = tab
        return changed
    }

    fun startAnalysis(uri: Uri) {
        currentJob?.cancel()
        val matcher = matcherFor(_currentTab.value)
        currentJob = viewModelScope.launch {
            repository.analyze(uri, matcher).collect { _state.value = it }
        }
    }

    fun reset() {
        currentJob?.cancel()
        _state.value = Idle
    }

    override fun onCleared() {
        currentJob?.cancel()
        // Intentionally no ocrEngine.release() — see KDoc above.
        super.onCleared()
    }
}
