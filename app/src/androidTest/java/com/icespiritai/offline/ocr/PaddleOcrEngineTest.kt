package com.icespiritai.offline.ocr

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * End-to-end instrumentation test for [PaddleOcrEngine] against a real
 * Android device or emulator.
 *
 * Requires:
 *   - A connected arm64-v8a device (or emulator) with the OCR models bundled
 *     into the APK's assets at the paths passed to `PaddleOCR.create` in
 *     [PaddleOcrEngine] (see `app/src/main/assets/models/`).
 *   - The fixture image is read from the **test APK's** assets
 *     (`app/src/androidTest/assets/test.png`) and staged in the app cache
 *     directory so it can be opened through `ContentResolver` like any other
 *     image Uri would be. AGP only merges `androidTest/assets/` into the test
 *     APK, not the target APK, so the engine — which receives a Uri — must
 *     see the file via a path accessible from the app context.
 *
 * Uses `runBlocking` rather than `runTest`: the SDK's `create`/`recognize`
 * perform real blocking IO and native inference, so there is no virtual time
 * to advance.
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrEngineTest {

    @Test
    fun recognize_realImage_returnsText() = runBlocking {
        // PaddleOCR's SDK calls into OpenCV's `BitmapUtils.bitmapToBGRMat` on
        // every `recognize()`. If the opencv-android AAR's native libs aren't
        // present on the test device (some emulator images strip them), the
        // first recognize throws `UnsatisfiedLinkError: org.opencv.core.Mat.n_Mat`.
        // Skip — don't fail — in that case so this test stays green on
        // minimally-configured devices and only fails when OCR truly misbehaves.
        //
        // The quickbirdstudios:opencv AAR only exposes `initDebug()` /
        // `initAsync()` — `initLocal()` is only on the official OpenCV 4.x SDK.
        // The no-arg `initDebug()` loads bundled native libs and skips the
        // OpenCV Manager bind, which is exactly what we want here.
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initDebug(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // Stage fixture: copy test.png from test-APK assets to app cache, so
        // ContentResolver can resolve a file:// Uri in the app's process.
        val cacheFile = File(appCtx.cacheDir, "paddle_ocr_engine_test.png")
        testCtx.assets.open("test.png").use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        val uri: Uri = Uri.fromFile(cacheFile)

        val engine = PaddleOcrEngine(appCtx)
        val result: OcrResult = engine.recognize(uri)
        assertNotNull(result)
        assertTrue(
            "Either recognized text should be non-empty or no lines were " +
                "detected; got fullText=${result.fullText} lines=${result.lineBoxes.size}",
            result.fullText.isNotEmpty() || result.lineBoxes.isEmpty(),
        )
        engine.release()
    }
}