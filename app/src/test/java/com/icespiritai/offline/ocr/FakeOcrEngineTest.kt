package com.icespiritai.offline.ocr

import android.net.StubUri
import com.icespiritai.offline.domain.OcrEngineUnavailable
import com.icespiritai.offline.domain.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeOcrEngineTest {
    @Test
    fun `recognize returns canned text and confidence`() = runTest {
        val engine = FakeOcrEngine(cannedText = "根治糖尿病", cannedConfidence = 0.95f)
        val result: OcrResult = engine.recognize(StubUri())
        assertEquals("根治糖尿病", result.fullText)
        assertEquals(0.95f, result.avgConfidence)
        assertEquals(1, result.lineBoxes.size)
        assertEquals("根治糖尿病", result.lineBoxes[0].text)
        assertEquals(0.95f, result.lineBoxes[0].confidence, 0.0001f)
    }

    @Test
    fun `recognize throws OcrEngineUnavailable when canned text empty`() = runTest {
        val engine = FakeOcrEngine()
        try {
            engine.recognize(StubUri())
            fail("expected OcrEngineUnavailable")
        } catch (e: OcrEngineUnavailable) {
            assertTrue(true) // expected
        }
    }

    @Test
    fun `recognize returns text when delay configured`() = runTest {
        val engine = FakeOcrEngine(cannedText = "test", simulatedDelayMs = 10L)
        val result = engine.recognize(StubUri())
        assertEquals("test", result.fullText)
    }

    @Test
    fun `release is a no-op and does not throw`() = runTest {
        val engine = FakeOcrEngine(cannedText = "x")
        engine.release()
        // no assertion needed — just confirms it doesn't throw
    }
}