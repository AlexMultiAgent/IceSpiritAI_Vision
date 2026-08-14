package com.icespiritai.offline.ocr

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapLoaderTest {

    private fun readFixtureBytes(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("Missing test fixture: $name on the unit-test classpath (src/test/resources/).")
        return stream.use { it.readBytes() }
    }

    @Test
    fun exifRotationDegrees_returnsZero_forPngWithNoExifTag() {
        val bytes = readFixtureBytes("test.png")
        val degrees = BitmapLoader.exifRotationDegrees(bytes)
        // test.png is a PNG with no EXIF orientation tag, so rotation is 0.
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
    fun applyExifRotation_preservesDimensions_at180Degrees() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = BitmapLoader.applyExifRotation(bmp, 180)
        assertEquals(100, rotated.width)
        assertEquals(200, rotated.height)
        // rotated != bmp (different instance)
        assertNotSame(bmp, rotated)
    }

    @Test
    fun downsampledBitmap_returnsBitmapWhoseLongestEdgeDoesNotExceedMaxEdge() {
        val bytes = readFixtureBytes("test.png")
        val bitmap = BitmapLoader.downsampledBitmap(bytes, maxEdgePx = 4096)
        assertNotNull(bitmap)
        val longest = maxOf(bitmap!!.width, bitmap.height)
        assertTrue("Longest edge $longest should be <= 4096", longest <= 4096)
    }
}