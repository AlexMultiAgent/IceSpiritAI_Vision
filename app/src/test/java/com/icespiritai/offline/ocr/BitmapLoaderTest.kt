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

    // --- sampleSize() boundary tests (regression for v0.1.12) ---
    //
    // Floor-based sampleSize MUST NOT halve the result on a single pixel
    // overshoot of the 2^k * maxEdge boundary. These boundaries are where
    // the previous ceiling-based version had a cliff:
    //   2048 -> 2048 px (sample=1)
    //   2049 -> 1024 px (sample=2, 50% drop)  ← THE BUG
    //   4096 -> 2048 px (sample=2)
    //   4097 -> 1024 px (sample=4, 50% drop)  ← THE BUG
    // After the floor fix, all four cases above should pick sample=1 or
    // sample=2 (no 50% drop).

    @Test
    fun sampleSize_atExactMaxEdge_returnsOne() {
        // maxEdge=2048, longest=2048 → already at the target, no downsample.
        assertEquals(1, BitmapLoader.sampleSize(width = 2048, height = 1500, maxEdge = 2048))
    }

    @Test
    fun sampleSize_onePixelOverMaxEdge_returnsOneNotTwo() {
        // maxEdge=2048, longest=2049 → ceiling-based dropped to sample=2
        // (1024 px). Floor-based keeps sample=1 (2049 px), avoiding the
        // 50% information loss for a 1-px overshoot.
        assertEquals(1, BitmapLoader.sampleSize(width = 2049, height = 1500, maxEdge = 2048))
    }

    @Test
    fun sampleSize_atDoubleMaxEdge_returnsTwo() {
        // maxEdge=2048, longest=4096 → exactly 2x, sample=2 (2048 px).
        assertEquals(2, BitmapLoader.sampleSize(width = 4096, height = 3000, maxEdge = 2048))
    }

    @Test
    fun sampleSize_onePixelOverDoubleMaxEdge_returnsTwoNotFour() {
        // maxEdge=2048, longest=4097 → ceiling-based dropped to sample=4
        // (1024 px). Floor-based keeps sample=2 (~2048 px).
        assertEquals(2, BitmapLoader.sampleSize(width = 4097, height = 3000, maxEdge = 2048))
    }

    @Test
    fun sampleSize_wellAboveMaxEdge_keepsDoubling() {
        // 8192 px with maxEdge=2048 → sample=4 (2048 px), no cliff.
        assertEquals(4, BitmapLoader.sampleSize(width = 8192, height = 6000, maxEdge = 2048))
        // 16384 px → sample=8.
        assertEquals(8, BitmapLoader.sampleSize(width = 16384, height = 12000, maxEdge = 2048))
    }

    @Test
    fun sampleSize_belowMaxEdge_returnsOne() {
        // Anything below maxEdge stays at sample=1 regardless of dimensions.
        assertEquals(1, BitmapLoader.sampleSize(width = 1024, height = 1024, maxEdge = 2048))
        assertEquals(1, BitmapLoader.sampleSize(width = 2047, height = 100, maxEdge = 2048))
    }

    @Test
    fun sampleSize_usesLongestEdge() {
        // width is the longer side; height short — should pick based on width.
        assertEquals(2, BitmapLoader.sampleSize(width = 4096, height = 100, maxEdge = 2048))
        // height is the longer side; width short — should pick based on height.
        assertEquals(2, BitmapLoader.sampleSize(width = 100, height = 4096, maxEdge = 2048))
    }
}