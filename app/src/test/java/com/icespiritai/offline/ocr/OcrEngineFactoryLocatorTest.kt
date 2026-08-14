package com.icespiritai.offline.ocr

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
}
