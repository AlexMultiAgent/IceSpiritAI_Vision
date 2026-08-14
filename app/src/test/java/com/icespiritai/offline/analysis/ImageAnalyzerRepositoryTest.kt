package com.icespiritai.offline.analysis

import android.net.StubUri
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.RuleLoadFailed
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.rules.FakeRuleMatcher
import com.icespiritai.offline.rules.RuleMatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAnalyzerRepositoryTest {

    private val cannedText = "本店专治糖尿病，100% 有效"
    private val cannedHits = listOf(
        RuleHit(
            ruleId = "medical-001",
            matchedText = "专治",
            category = "medical-claim",
            regulation = "广告法 §16",
            severity = Severity.Violation
        )
    )

    private val ocr = FakeOcrEngine(cannedText = cannedText, cannedConfidence = 0.9f)
    private val matcher = FakeRuleMatcher(mapOf(cannedText to cannedHits))

    private fun repo(
        ocrEngine: com.icespiritai.offline.ocr.OcrEngine = ocr,
        ruleMatcherProvider: () -> RuleMatcher = { matcher }
    ) = ImageAnalyzerRepository(ocrEngine, ruleMatcherProvider)

    @Test
    fun `analyze emits Loading-Ocr, OcrDone, Loading-Rule, RuleScanned, Complete in order`() =
        runTest {
            val uri = StubUri()
            val states = repo().analyze(uri).toList()

            assertEquals(5, states.size)

            val first = states[0]
            assertTrue("first should be Loading", first is AnalysisState.Loading)
            assertEquals(
                AnalysisState.Loading.Stage.OcrRunning,
                (first as AnalysisState.Loading).stage
            )

            val ocrDone = states[1]
            assertTrue("second should be OcrDone", ocrDone is AnalysisState.OcrDone)
            ocrDone as AnalysisState.OcrDone
            assertEquals(cannedText, ocrDone.text)
            assertEquals(0.9f, ocrDone.confidence, 0.0001f)
            assertEquals(1, ocrDone.lineBoxes.size)
            assertFalse("0.9 confidence is not low", ocrDone.lowConfidence)

            val ruleLoading = states[2]
            assertTrue("third should be Loading", ruleLoading is AnalysisState.Loading)
            assertEquals(
                AnalysisState.Loading.Stage.RuleScanning,
                (ruleLoading as AnalysisState.Loading).stage
            )

            val scanned = states[3]
            assertTrue("fourth should be RuleScanned", scanned is AnalysisState.RuleScanned)
            assertEquals(cannedHits, (scanned as AnalysisState.RuleScanned).hits)

            val complete = states[4]
            assertTrue("fifth should be Complete", complete is AnalysisState.Complete)
            val report = (complete as AnalysisState.Complete).report
            assertSame("report carries the analyzed uri", uri, report.imageUri)
            assertEquals(cannedText, report.ocrText)
            assertEquals(cannedHits, report.hits)
            assertTrue("timestamp should be populated", report.timestampMs > 0L)
        }

    @Test
    fun `analyze emits Error when OCR throws`() = runTest {
        // FakeOcrEngine throws OcrEngineUnavailable when cannedText is empty.
        val states = repo(ocrEngine = FakeOcrEngine(cannedText = "")).analyze(StubUri()).toList()

        assertEquals(2, states.size)
        assertTrue(states[0] is AnalysisState.Loading)

        val err = states[1]
        assertTrue("second should be Error", err is AnalysisState.Error)
        err as AnalysisState.Error
        assertTrue("OCR failure is retryable", err.retryable)
        assertNotNull("cause is preserved", err.cause)
    }

    @Test
    fun `analyze emits Error when rule loading fails`() = runTest {
        val states = repo(
            ruleMatcherProvider = { throw RuleLoadFailed("assets/rules missing") }
        ).analyze(StubUri()).toList()

        // OCR still succeeded, so the failure surfaces after OcrDone.
        assertEquals(4, states.size)
        assertTrue(states[1] is AnalysisState.OcrDone)
        assertEquals(
            AnalysisState.Loading.Stage.RuleScanning,
            (states[2] as AnalysisState.Loading).stage
        )

        val err = states[3]
        assertTrue("fourth should be Error", err is AnalysisState.Error)
        err as AnalysisState.Error
        assertFalse("a missing/corrupt rule asset is not retryable", err.retryable)
        assertNotNull("cause is preserved", err.cause)
    }

    @Test
    fun `analyze with no rule hits still completes with empty hits`() = runTest {
        val states = repo(ruleMatcherProvider = { FakeRuleMatcher() }).analyze(StubUri()).toList()

        assertEquals(5, states.size)
        assertTrue((states[3] as AnalysisState.RuleScanned).hits.isEmpty())
        val complete = states[4] as AnalysisState.Complete
        assertTrue(complete.report.hits.isEmpty())
        assertEquals(cannedText, complete.report.ocrText)
    }

    @Test
    fun `rule matcher is resolved once and reused across analyze calls`() = runTest {
        var resolveCount = 0
        val repository = repo(ruleMatcherProvider = {
            resolveCount++
            matcher
        })

        repository.analyze(StubUri()).toList()
        repository.analyze(StubUri()).toList()

        assertEquals("provider should be invoked lazily exactly once", 1, resolveCount)
    }

    @Test
    fun `analyze does not resolve rule matcher until collected`() = runTest {
        var resolveCount = 0
        val flow = repo(ruleMatcherProvider = {
            resolveCount++
            matcher
        }).analyze(StubUri())

        assertEquals("cold flow must not do work before collection", 0, resolveCount)
        flow.toList()
        assertEquals(1, resolveCount)
    }
}
