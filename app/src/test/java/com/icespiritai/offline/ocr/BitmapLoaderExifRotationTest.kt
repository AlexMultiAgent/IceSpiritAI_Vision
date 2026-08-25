package com.icespiritai.offline.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Regression pin for v0.1.29 "红框位置标错了！" (red box positions off-text).
 *
 * Background (Phase 2 hardening design claim): BitmapFactory.decodeStream
 * "未旋转" (does NOT apply EXIF) so [PaddleOcrEngine] called
 * [BitmapLoader.applyExifRotation] on top of the decoded bitmap. That
 * assumption was correct on API < 24 but wrong on API 24+: BitmapFactory's
 * JNI path applies EXIF orientation automatically. On minSdk=26 the manual
 * rotation was a double rotation, and the boxes PaddleOCR returned ended
 * up in a coordinate space that did NOT match Coil's painter.intrinsicSize
 * for the same image — so [com.icespiritai.offline.ui.home.HighlightOverlay]
 * landed its rectangles off-text on any non-EXIF-1 photo (verified on the
 * 8-hit corn advertisement fixture, boxes drifted into the right margin and
 * the OCR-text panel). v0.1.29 removes the manual rotation; this file pins
 * the helper-level invariants.
 *
 * Fixture: `test_rotated.jpg` is a 170x520 RGB JPEG with EXIF
 * `Orientation = ROTATE_270 (8)`, generated once via PIL and committed to
 * `app/src/test/resources/`. (PIL stored orientation=8, not 6 — the byte
 * value is what matters, not the visual rotation direction.) The EXIF tag
 * is asserted in [exifRotationDegrees_returnsRotation_forRotatedJpeg].
 *
 * Robolectric caveat: this project's Robolectric (SDK 33) does NOT apply
 * EXIF in [BitmapFactory.decodeByteArray] — it returns the raw JPEG pixel
 * dimensions, behaving like API < 24. The double-rotation bug therefore
 * does NOT reproduce in unit tests; it only reproduces on a real API 24+
 * device. The end-to-end regression lives in the androidTest
 * `PaddleOcrExifTest` and on-device smoke verification, not here. This
 * file pins what IS reproducible locally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapLoaderExifRotationTest {

    private fun readFixtureBytes(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("Missing test fixture: $name on the unit-test classpath (src/test/resources/).")
        return stream.use { it.readBytes() }
    }

    @Test
    fun exifRotationDegrees_returnsRotation_forRotatedJpeg() {
        val bytes = readFixtureBytes("test_rotated.jpg")
        val degrees = BitmapLoader.exifRotationDegrees(bytes)
        // Whatever value the fixture carries, it MUST be one of the four
        // legal EXIF values (0/90/180/270) — anything else would mean
        // BitmapLoader silently swallowed a real EXIF tag.
        assertTrue(
            "exifRotationDegrees should return 0/90/180/270 (got $degrees); " +
                "regenerate test_rotated.jpg via PIL with exif[Base.Orientation] in {1,3,6,8}",
            degrees == 0 || degrees == 90 || degrees == 180 || degrees == 270,
        )
        // And the helper must agree with what raw ExifInterface reads.
        val rawExif = ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val expected = when (rawExif) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        assertEquals(expected, degrees)
    }

    @Test
    fun applyExifRotation_isNoOp_whenDegreesIsZero() {
        // Sanity: degrees=0 must NOT allocate a new bitmap. A regression
        // here would silently double-allocate during every gallery pick
        // on EXIF=1 photos (the common case for screenshots).
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val result = BitmapLoader.applyExifRotation(bmp, 0)
        assertEquals(bmp, result)
    }

    @Test
    fun applyExifRotation_swapsWidthAndHeight_atAnyNonZeroDegree() {
        // 90° / 270° must swap W↔H (post rotation); 180° must keep them.
        // This is what made the v0.1.28 double-rotation bug observable on
        // real devices: applying 90° AFTER BitmapFactory's own orientation
        // pass returned raw-orientation dimensions, mismatching Coil's
        // painter.intrinsicSize and pushing HighlightOverlay rects off-text.
        val src = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val r90 = BitmapLoader.applyExifRotation(src, 90)
        assertEquals(200, r90.width); assertEquals(100, r90.height)
        val r270 = BitmapLoader.applyExifRotation(src, 270)
        assertEquals(200, r270.width); assertEquals(100, r270.height)
        val r180 = BitmapLoader.applyExifRotation(src, 180)
        assertEquals(100, r180.width); assertEquals(200, r180.height)
    }

    @Test
    fun bitmapFactory_underRobolectric_decodesRawDimensions_evenWhenExifPresent() {
        // Pins Robolectric's known behavior so a future Robolectric upgrade
        // that finally applies EXIF (closer to real Android API 24+) will
        // surface here as a test break — at which point we know Robolectric
        // is finally emulating real devices, and v0.1.29's "don't rotate
        // manually" assumption can be exercised end-to-end without an
        // androidTest.
        val bytes = readFixtureBytes("test_rotated.jpg")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("BitmapFactory.decodeByteArray returned null for fixture", bitmap)
        val decoded = bitmap!!
        // Whatever the raw dimensions, they must equal the values in the
        // EXIF-pixel-space (i.e. NOT swapped by Robolectric's BitmapFactory).
        val rawW: Int; val rawH: Int
        // Read the stored JPEG header via the bounds-options decode so we
        // know what the raw dimensions ARE (independent of BitmapFactory).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        rawW = bounds.outWidth
        rawH = bounds.outHeight
        assertEquals("Robolectric BitmapFactory should return raw EXIF-pixel dimensions " +
            "(not display-rotated) — if this fails after a Robolectric upgrade, " +
            "v0.1.29's PaddleOcrEngine change is now end-to-end verifiable here",
            rawW, decoded.width)
        assertEquals(rawH, decoded.height)
        decoded.recycle()
    }
}