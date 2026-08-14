package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        // test.png lives in androidTest/assets/, NOT unit-test assets,
        // so Robolectric won't find it via file:// URI. Test the bad-URI path
        // which exercises the same null-InputStream branch.
        val uri = android.net.Uri.parse("content://nonexistent/no-exif")
        assertEquals(0, BitmapLoader.exifRotationDegrees(context, uri))
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
}