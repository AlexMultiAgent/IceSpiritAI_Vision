package com.icespiritai.offline.ocr

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Phase 2 / Task 4 — verifies that [PaddleOcrEngine] correctly handles EXIF
 * rotation via [BitmapLoader]. Loads `test.png` and `test_rotated.jpg` (PNG
 * with no EXIF + JPEG with EXIF orientation=6, both containing the same
 * Chinese ad text) and asserts OCR recognizes both with overlapping text.
 *
 * Skips if OpenCV native libs fail to load (portable across devices).
 *
 * Run:
 *   ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrExifTest
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrExifTest {

    @Test
    fun rotated_jpeg_with_exif_orientation_6_recognizes_same_text_as_test_png() = runBlocking {
        assumeTrue("OpenCV must load on test device", OpenCVLoader.initDebug())

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        val pngUri = stageFixture(appCtx, testCtx, "test.png", "exif_test.png")
        val jpgUri = stageFixture(appCtx, testCtx, "test_rotated.jpg", "exif_test_rotated.jpg")

        val engine = PaddleOcrEngine(appCtx)
        val pngText = engine.recognize(pngUri).fullText
        val jpgText = engine.recognize(jpgUri).fullText
        engine.release()

        assertTrue("PNG OCR returned empty", pngText.isNotBlank())
        assertTrue("JPG OCR returned empty", jpgText.isNotBlank())
        // Loose overlap assertion: OCR is noisy, so exact equality is brittle.
        val overlap = pngText.toSet().intersect(jpgText.toSet()).size
        assertTrue(
            "Expected at least 3 overlapping chars between PNG and rotated JPG OCR; got $overlap",
            overlap >= 3,
        )
    }

    private fun stageFixture(
        appCtx: Context,
        testCtx: Context,
        srcName: String,
        dstName: String,
    ): Uri {
        val cacheFile = File(appCtx.cacheDir, dstName)
        testCtx.assets.open(srcName).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(cacheFile)
    }
}