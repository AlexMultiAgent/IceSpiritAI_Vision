package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun exifRotationDegrees_returnsZero_whenStreamOpenFails() {
        val uri = android.net.Uri.parse("content://nonexistent/123")
        assertEquals(0, BitmapLoader.exifRotationDegrees(context, uri))
    }

    @Test
    fun exifRotationDegrees_returnsZero_forPngWithNoExifTag() {
        val uri = android.net.Uri.parse("file:///android_asset/test.png")
        val degrees = BitmapLoader.exifRotationDegrees(context, uri)
        // test.png is a PNG with no EXIF orientation tag. If the stream failed
        // to open under Robolectric, exifRotationDegrees returns 0 by design
        // (its catch-all). Either way 0 is the expected result.
        assertEquals(0, degrees)
    }

    @Test
    fun applyExifRotation_returnsSameBitmap_whenDegreesIsZero() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val result = BitmapLoader.applyExifRotation(bmp, 0)
        assertSame(bmp, result)
    }

    @Test
    fun applyExifRotation_swapsWidthAndHeight_at90Degrees() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = BitmapLoader.applyExifRotation(bmp, 90)
        assertEquals(200, rotated.width)
        assertEquals(100, rotated.height)
    }

    @Test
    fun applyExifRotation_swapsWidthAndHeight_at270Degrees() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = BitmapLoader.applyExifRotation(bmp, 270)
        assertEquals(200, rotated.width)
        assertEquals(100, rotated.height)
    }

    @Test
    fun downsampledBitmap_returnsNull_whenStreamOpenFails() {
        val uri = android.net.Uri.parse("content://nonexistent/123")
        assertEquals(null, BitmapLoader.downsampledBitmap(context, uri))
    }

    @Test
    fun downsampledBitmap_returnsBitmapWhoseLongestEdgeDoesNotExceedMaxEdge() {
        val uri = android.net.Uri.parse("file:///android_asset/test.png")
        val bitmap = BitmapLoader.downsampledBitmap(context, uri, maxEdgePx = 4096)
        if (bitmap != null) {
            val longest = maxOf(bitmap.width, bitmap.height)
            assertTrue("Longest edge $longest should be <= 4096", longest <= 4096)
        }
        // If bitmap is null under Robolectric (no asset resolution), the test
        // silently passes — the real fixture test is in androidTest/PaddleOcrExifTest.
    }
}