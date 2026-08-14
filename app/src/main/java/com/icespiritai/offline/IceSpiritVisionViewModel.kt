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
import com.icespiritai.offline.rules.AdLawRuleMatcher
import com.icespiritai.offline.rules.AssetRuleLoader
import com.icespiritai.offline.rules.RuleMatcher
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
 * [ruleMatcherProvider] is a lambda (not an eager `RuleMatcher`) so that an
 * asset-load failure surfaces as [AnalysisState.Error] on first `analyze()`
 * instead of throwing out of this ViewModel's constructor — where no UI state
 * exists to display the failure. The provider is invoked at most once via the
 * repository's internal `lazy`.
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

    private val ruleMatcherProvider: () -> RuleMatcher = {
        AdLawRuleMatcher(AssetRuleLoader(getApplication()).load())
    }

    private val repository = ImageAnalyzerRepository(ocrEngine, ruleMatcherProvider)

    private val _state = MutableStateFlow<AnalysisState>(Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    private var currentJob: Job? = null

    fun startAnalysis(uri: Uri) {
        // Single-flight guard: a second tap cancels any in-flight analysis
        // before starting a new one.
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            repository.analyze(uri).collect { _state.value = it }
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