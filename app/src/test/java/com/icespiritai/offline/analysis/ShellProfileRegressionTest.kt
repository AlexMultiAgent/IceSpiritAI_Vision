package com.icespiritai.offline.analysis

import android.net.StubUri
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.rules.AdSignageRuleMatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test: shell profile ships with empty rules JSON
 * ({"version":1,"rules":[]} — see prepare-ocr-rules.gradle.kts). The user
 * reported "未知错误,请重试" after uploading a real photo on v0.1.26; we
 * need to verify that the shell profile path (FakeOcrEngine + empty
 * AdSignageRuleMatcher) actually completes successfully instead of
 * emitting an Error state.
 */
class ShellProfileRegressionTest {
    @Test
    fun shellProfile_completeFlow_doesNotEmitError() = runTest {
        // Same construction as what IceSpiritVisionViewModel uses on first
        // analysis: FakeOcrEngine factory default + empty rule list.
        val ocr = FakeOcrEngine(cannedText = "本店专治糖尿病,100% 有效", cannedConfidence = 0.9f)
        val matcher = AdSignageRuleMatcher(emptyList())
        val repo = ImageAnalyzerRepository(ocr)

        val states = repo.analyze(StubUri(), matcher).toList()

        // Dump every state to surface UNKNOWN errors.
        val errorStates = states.filterIsInstance<AnalysisState.Error>()
        val stateNames = states.map { it::class.simpleName }
        val errorDetails = errorStates.map { e -> e.errorCode.name + ":" + e.message }
        assertTrue(
            "shell profile (fake OCR + empty rules) must NOT emit Error; " +
                "got states=$stateNames errors=$errorDetails",
            errorStates.isEmpty(),
        )
        // Last state should be Complete with canned text and empty hits.
        val complete = states.last() as AnalysisState.Complete
        assertEquals("本店专治糖尿病,100% 有效", complete.report.ocrText)
        assertTrue("empty rules means no hits", complete.report.hits.isEmpty())
    }
}
