package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.domain.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    /**
     * Regression pin for v0.1.30 "红框位置标错了！" round 3 — `imageWidth /
     * imageHeight` MUST be the FULL display-oriented bitmap dims (= bitmap
     * width × sampleSize), NOT the downsampled bitmap dims. v0.1.30 added
     * the ImageSize plumbing through `OcrResult → AnalysisState.OcrDone /
     * ViolationReport → ImagePreview.imageSize → computeFitTransform`, and
     * pinned the consumer contract in `ImagePreviewFitTransformTest`, but
     * the producer side in `PaddleOcrEngine.recognize` set
     * `imageWidth = bitmap.width` (the downsampled bitmap's dims). Boxes
     * were already multiplied by `loaded.sampleSize` in
     * `OCRBox.toBoundingRect` to live in the full-res space, so the two
     * sides drifted apart — `computeFitTransform` got a refW/refH smaller
     * than the actual box coord space, and `HighlightOverlay` rects landed
     * in the canvas's letterbox instead of on text (verified on a 4032×3024
     * bus photo on 2026-08-26: red box ended up in the upper-right empty
     * area, OCR text and box coords nowhere near each other).
     *
     * This pin needs a fixture that triggers `sampleSize > 1` to actually
     * exercise the bug — `test.png` is small enough that
     * `BitmapLoader.sampleSize` returns 1, so the OLD buggy code would
     * already pass with it. `dongjiao_daojia.jpg` (the 2026-08-26 smoke
     * test fixture) is a real phone photo with longest edge well above
     * 2048 px, so its sampleSize is 2 and the buggy
     * `imageWidth = bitmap.width` produces half the right answer.
     */
    @Test
    fun recognize_imageSize_isFullResDisplayDims_notDownsampled() = runBlocking {
        assumeTrue(
            "OpenCV native libs must load on the test device",
            OpenCVLoader.initDebug(),
        )

        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        val cacheFile = File(appCtx.cacheDir, "image_dims_pin.jpg").apply {
            testCtx.assets.open("fixtures/dongjiao_daojia.jpg").use { input ->
                outputStream().use { input.copyTo(it) }
            }
        }
        val uri = Uri.fromFile(cacheFile)

        // Compute expected full-res display dims without allocating a
        // 4032×3024 ARGB_8888 bitmap (which is ~50 MB and slow on a
        // connected test device). `inJustDecodeBounds=true` reads only the
        // JPEG header — pre-EXIF raw dims — and we apply the EXIF rotation
        // manually to get the display dims that BitmapFactory's full decode
        // (and therefore PaddleOCR's input bitmap) actually use on API 24+.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cacheFile.absolutePath, boundsOpts)
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        val deg = BitmapLoader.exifRotationDegrees(cacheFile.readBytes())
        val (expectedW, expectedH) = if (deg == 90 || deg == 270) rawH to rawW else rawW to rawH

        // Sanity guard: this pin only catches the bug if the fixture
        // triggers sampleSize > 1. If dongjiao_daojia.jpg ever shrinks below
        // 2048 px longest edge (e.g., a re-export with reduced resolution),
        // sampleSize collapses to 1 and the OLD buggy code would also pass.
        // Fail loudly so we know to swap fixtures.
        assertTrue(
            "fixture must trigger sampleSize > 1 (longest edge > 2048) " +
                "for this pin to actually reproduce the bug; " +
                "got longest=${maxOf(expectedW, expectedH)} — pick a bigger fixture",
            maxOf(expectedW, expectedH) > 2048,
        )

        val engine = PaddleOcrEngine(appCtx)
        try {
            val result = engine.recognize(uri)
            // The imageSize contract: equals the FULL display-oriented
            // bitmap dims, so it matches the coord space the boxes were
            // emitted in (boxes were scaled by sampleSize in
            // OCRBox.toBoundingRect). The OLD buggy code returned the
            // DOWNSAMPLED bitmap dims (expectedW / sampleSize), which
            // would put every line.box.right past imageWidth and push
            // HighlightOverlay rects off-text.
            assertTrue(
                "imageWidth/Height must be > 0 (else ImagePreview falls back to " +
                    "painter.intrinsicSize and we lose the v0.1.30 fix); " +
                    "got imageWidth=${result.imageWidth} imageHeight=${result.imageHeight}",
                result.imageWidth > 0 && result.imageHeight > 0,
            )
            assertEquals(
                "imageWidth must equal FULL display-oriented bitmap dims, " +
                    "NOT downsampled bitmap dims (= expectedW / sampleSize)",
                expectedW, result.imageWidth,
            )
            assertEquals(
                "imageHeight must equal FULL display-oriented bitmap dims, " +
                    "NOT downsampled bitmap dims (= expectedH / sampleSize)",
                expectedH, result.imageHeight,
            )
            // Cross-check: every box's right/bottom must fit within
            // imageWidth/Height. Under the OLD buggy code, boxes live in
            // (expectedW × expectedH) space but imageSize reports
            // (expectedW / sampleSize × expectedH / sampleSize), so this
            // loop catches the violation on every line.
            result.lineBoxes.forEach { line ->
                assertTrue(
                    "line.box.right=${line.box.right} must be <= imageWidth=${result.imageWidth} " +
                        "(box was emitted in full-res space; imageSize must match)",
                    line.box.right <= result.imageWidth,
                )
                assertTrue(
                    "line.box.bottom=${line.box.bottom} must be <= imageHeight=${result.imageHeight} " +
                        "(box was emitted in full-res space; imageSize must match)",
                    line.box.bottom <= result.imageHeight,
                )
            }
        } finally {
            engine.release()
        }
    }
}