package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * Phase 0 feasibility smoke test for the PaddleOCR official SDK.
 *
 * Verifies the whole integration path end to end on a real device:
 * AAR on the classpath -> ONNX Runtime + OpenCV native libs load ->
 * models resolve from `app/src/main/assets/models/` -> inference returns text.
 *
 * This is temporary scaffolding. Phase 1 Task 7 wraps this same API behind
 * the `OcrEngine` interface (`PaddleOcrEngine`), at which point this test is
 * replaced by tests against that interface.
 *
 * Uses `runBlocking` rather than `runTest`: the SDK's `create`/`recognize` do
 * real blocking IO and inference on `Dispatchers.IO`, so there is no virtual
 * time to fast-forward. This also keeps `kotlinx-coroutines-test` out of the
 * dependency set.
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrSmokeTest {

    @Test
    fun sdk_loadsModelsAndRecognizes_chineseTestImage() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device (com.quickbirdstudios:opencv:4.5.3)",
            OpenCVLoader.initDebug(),
        )

        val context = ApplicationProvider.getApplicationContext<Context>()

        val ocr = PaddleOCR.create(
            context = context,
            config = PaddleOCRConfig(),
            engineConfig = EngineConfig(numThreads = 4),
            detModelAssetPath = "models/det/inference.onnx",
            recModelAssetPath = "models/rec/inference.onnx",
            recConfigAssetPath = "models/rec/inference.yml",
        )

        try {
            // test.png lives in androidTest assets and is merged into the test APK.
            val bitmap = context.assets.open("test.png").use(BitmapFactory::decodeStream)
            assertNotNull("test.png failed to decode from androidTest assets", bitmap)

            val result = ocr.recognize(bitmap!!)

            val text = result.results.joinToString(" | ") { it.text }
            println(
                "[PaddleOcrSmokeTest] lineCount=${result.lineCount} " +
                    "coldLoadMs=${result.coldLoadTimeMs} " +
                    "detMs=${result.detectionTimeMs} " +
                    "recMs=${result.recognitionTimeMs} " +
                    "totalMs=${result.totalTimeMs}",
            )
            println("[PaddleOcrSmokeTest] text=$text")

            assertTrue(
                "Expected at least 1 recognized line, got none. " +
                    "Models loaded but detection returned nothing.",
                result.results.isNotEmpty(),
            )
            assertTrue(
                "Recognized text should contain a term from the fixture " +
                    "(国家级 / 最佳品牌 / 全国销量第一), but was: $text",
                listOf("国家级", "最佳品牌", "全国销量第一", "优质产品")
                    .any { it in text },
            )
            assertTrue(
                "coldLoadTimeMs should be > 0 on a cold engine",
                result.coldLoadTimeMs > 0,
            )
        } finally {
            ocr.release()
        }
    }
}
