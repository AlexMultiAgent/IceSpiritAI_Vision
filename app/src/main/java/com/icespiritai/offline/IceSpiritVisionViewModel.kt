package com.icespiritai.offline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.analysis.ImageAnalyzerRepository
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.AnalysisState.Idle
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.ocr.OcrEngine
import com.icespiritai.offline.ocr.PaddleOcrEngine
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
 * Profile gating:
 *   - `modelProfile = "ice_ocr_rules"` → [PaddleOcrEngine] (real PaddleOCR SDK)
 *   - anything else (default `shell`) → [FakeOcrEngine] returning canned text
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

    private val ocrEngine: OcrEngine = if (BuildConfig.MODEL_PROFILE == "ice_ocr_rules") {
        PaddleOcrEngine(application)
    } else {
        FakeOcrEngine(cannedText = "本店专治糖尿病,100% 有效", cannedConfidence = 0.9f)
    }

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