package com.icespiritai.offline.ocr

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OcrEngineFactoryLocatorTest {

    @Test
    fun locator_discoversFakeOcrEngineFactory_whenPresentOnClasspath() {
        val engine = OcrEngineFactoryLocator.create(ApplicationProvider.getApplicationContext())
        assertTrue(
            "Expected FakeOcrEngine when FakeOcrEngineFactory is on classpath, got ${engine::class.simpleName}",
            engine is FakeOcrEngine,
        )
    }

    @Test
    fun fakeOcrEngineFactory_producesFakeOcrEngine_withCannedTextForDiabetesAd() = runBlocking {
        val engine = FakeOcrEngineFactory().create(ApplicationProvider.getApplicationContext())
        val result = engine.recognize(android.net.Uri.parse("content://x"))
        assertTrue(result.fullText.contains("糖尿病"))
    }

    /**
     * Bug 8 regression: the marker set per profile must cover reasonable
     * class renames — pinning it down here so a future refactor that
     * removes a marker (or narrows the ice_ocr_rules profile to a single
     * substring) is caught at CI rather than at first launch on a real
     * device.
     */
    @Test
    fun markersFor_shell_acceptsFake() {
        // shell profile's only source today is FakeOcrEngineFactory; no
        // expansion needed (no ONNX or Paddle here).
        assertEquals(listOf("Fake"), OcrEngineFactoryLocator.markersFor("shell"))
    }

    /**
     * Bug 8 regression: ice_ocr_rules profile's marker set must survive
     * typical renames of [com.icespiritai.offline.ocr.PaddleOcrEngineFactory].
     * "Paddle" covers the current name; "Pp" covers the short-form
     * rename (`PpOcrEngineFactory`); "Onnx" covers an ONNX-first naming
     * convention (`OnnxPaddleOcrEngineFactory`).
     */
    @Test
    fun markersFor_ice_ocr_rules_coversPaddlePpOnnxRenames() {
        val markers = OcrEngineFactoryLocator.markersFor("ice_ocr_rules")
        assertTrue(
            "ice_ocr_rules must accept Paddle-prefixed factories, got $markers",
            markers.any { it.contains("Paddle") },
        )
        assertTrue(
            "ice_ocr_rules must accept Pp-prefixed factories, got $markers",
            markers.any { it.contains("Pp") },
        )
        assertTrue(
            "ice_ocr_rules must accept Onnx-prefixed factories, got $markers",
            markers.any { it.contains("Onnx") },
        )
    }

    @Test
    fun markersFor_ice_vision_fallsBackToFake() {
        // ice_vision isn't implemented yet; until then the Fake factory
        // doubles as a placeholder so shell builds can resolve without
        // an ice_vision sourceSet.
        val markers = OcrEngineFactoryLocator.markersFor("ice_vision")
        assertTrue(markers.contains("Fake"))
        assertTrue(markers.contains("Vision"))
    }

    @Test
    fun markersFor_unknownProfile_fallsBackToRawName() {
        // The else-branch is the safety net for typos in -PmodelProfile=…
        // — we accept any factory whose FQN literally contains the
        // profile name. Don't widen this branch without checking the
        // error message in OcrEngineFactoryLocator.create stays helpful.
        assertEquals(listOf("wibble"), OcrEngineFactoryLocator.markersFor("wibble"))
    }
}
