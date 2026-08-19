package com.icespiritai.offline.analysis

import android.net.StubUri
import android.net.Uri
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.OcrFailed
import com.icespiritai.offline.domain.OcrResult
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.RuleLoadFailed
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.ocr.OcrEngine
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
        ocrEngine: OcrEngine = ocr,
    ) = ImageAnalyzerRepository(ocrEngine)

    @Test
    fun `analyze emits Loading-Ocr, OcrDone, Loading-Rule, RuleScanned, Complete in order`() =
        runTest {
            val uri = StubUri()
            val states = repo().analyze(uri, matcher).toList()

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
            assertEquals(0.9f, report.avgConfidence, 0.0001f)
            assertTrue("non-empty OCR text must be flagged as hasText", report.hasText)
            assertTrue("timestamp should be populated", report.timestampMs > 0L)
        }

    @Test
    fun `analyze emits Error when OCR throws`() = runTest {
        // FakeOcrEngine throws OcrEngineUnavailable when cannedText is empty.
        val states = repo(ocrEngine = FakeOcrEngine(cannedText = ""))
            .analyze(StubUri(), matcher).toList()

        assertEquals(2, states.size)
        assertTrue(states[0] is AnalysisState.Loading)

        val err = states[1]
        assertTrue("second should be Error", err is AnalysisState.Error)
        err as AnalysisState.Error
        assertEquals(ErrorCode.OCR_UNAVAILABLE, err.errorCode)
        assertTrue("OCR failure is retryable", err.retryable)
        assertNotNull("cause is preserved", err.cause)
    }

    @Test
    fun `analyze emits Error with OCR_FAILED when OcrFailed is thrown`() = runTest {
        val throwingOcr = object : OcrEngine {
            override suspend fun recognize(uri: Uri) =
                throw OcrFailed("decode failed")
            override suspend fun release() = Unit
        }
        val states = repo(ocrEngine = throwingOcr).analyze(StubUri(), matcher).toList()

        assertEquals(2, states.size)
        val err = states[1] as AnalysisState.Error
        assertEquals(ErrorCode.OCR_FAILED, err.errorCode)
        assertTrue("OCR runtime failure is retryable", err.retryable)
        assertNotNull(err.cause)
    }

    @Test
    fun `analyze emits Error with UNKNOWN when generic Exception is thrown`() = runTest {
        val throwingOcr = object : OcrEngine {
            override suspend fun recognize(uri: Uri) =
                throw RuntimeException("unexpected")
            override suspend fun release() = Unit
        }
        val states = repo(ocrEngine = throwingOcr).analyze(StubUri(), matcher).toList()

        assertEquals(2, states.size)
        val err = states[1] as AnalysisState.Error
        assertEquals(ErrorCode.UNKNOWN, err.errorCode)
        assertTrue("unexpected failure is retryable", err.retryable)
        assertNotNull(err.cause)
    }

    @Test
    fun `analyze emits Error when rule loading fails`() = runTest {
        val failingMatcher = object : RuleMatcher {
            override fun scan(text: String): List<RuleHit> =
                throw RuleLoadFailed("assets/rules missing")
        }
        val states = repo().analyze(StubUri(), failingMatcher).toList()

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
        assertEquals(ErrorCode.RULES_FAILED, err.errorCode)
        assertFalse("a missing/corrupt rule asset is not retryable", err.retryable)
        assertNotNull("cause is preserved", err.cause)
    }

    @Test
    fun `analyze with no rule hits still completes with empty hits`() = runTest {
        val states = repo().analyze(StubUri(), FakeRuleMatcher()).toList()

        assertEquals(5, states.size)
        assertTrue((states[3] as AnalysisState.RuleScanned).hits.isEmpty())
        val complete = states[4] as AnalysisState.Complete
        assertTrue(complete.report.hits.isEmpty())
        assertEquals(cannedText, complete.report.ocrText)
    }

    @Test
    fun `analyze with empty OCR text completes with hasText false`() = runTest {
        val emptyOcr = object : OcrEngine {
            override suspend fun recognize(uri: Uri) = OcrResult(
                fullText = "",
                lineBoxes = emptyList(),
                avgConfidence = 0f,
            )

            override suspend fun release() = Unit
        }
        val states = repo(ocrEngine = emptyOcr)
            .analyze(StubUri(), FakeRuleMatcher()).toList()

        val complete = states[4] as AnalysisState.Complete
        assertFalse("empty OCR text must not claim hasText", complete.report.hasText)
        assertEquals(0f, complete.report.avgConfidence, 0.0001f)
        assertTrue("no text means no hits", complete.report.hits.isEmpty())
    }

    @Test
    fun `analyze routes the supplied matcher and ignores the previous one`() = runTest {
        val adMatcher = FakeRuleMatcher(
            mapOf(cannedText to listOf(cannedHits[0].copy(ruleId = "AD_HIT")))
        )
        val foodMatcher = FakeRuleMatcher(
            mapOf(cannedText to listOf(cannedHits[0].copy(ruleId = "FOOD_HIT")))
        )
        val repository = repo()

        val adStates = repository.analyze(StubUri(), adMatcher).toList()
        val foodStates = repository.analyze(StubUri(), foodMatcher).toList()

        val adReport = (adStates.last() as AnalysisState.Complete).report
        val foodReport = (foodStates.last() as AnalysisState.Complete).report
        assertEquals("AD_HIT", adReport.hits[0].ruleId)
        assertEquals("FOOD_HIT", foodReport.hits[0].ruleId)
    }

    @Test
    fun `complete state's ViolationReport carries the same lineBoxes as OcrDone`() = runTest {
        val uri = StubUri()
        val states = repo().analyze(uri, matcher).toList()
        val ocrDone = states[1] as AnalysisState.OcrDone
        val complete = states[4] as AnalysisState.Complete
        assertEquals(ocrDone.lineBoxes, complete.report.lineBoxes)
    }
}