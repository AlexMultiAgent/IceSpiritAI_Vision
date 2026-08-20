package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

object BitmapLoader {

    private const val DEFAULT_MAX_EDGE_PX = 2048

    /**
     * A decoded bitmap plus the power-of-two [inSampleSize] that was applied
     * during downsampling. OCR box coordinates are produced in the
     * downsampled space, so callers need [sampleSize] to map them back onto
     * the original image that the preview shows.
     */
    data class DownsampledBitmap(val bitmap: Bitmap, val sampleSize: Int)

    fun bytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    fun downsampledBitmapWithScale(
        bytes: ByteArray,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
    ): DownsampledBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        DownsampledBitmap(bitmap = bitmap, sampleSize = sample)
    } catch (e: Exception) {
        null
    }

    fun exifRotationDegrees(bytes: ByteArray): Int = try {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    fun applyExifRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        val longest = maxOf(width, height)
        // Round DOWN to the nearest power of 2. The previous ceiling-based
        // version had a cliff at exact 2^k * maxEdge boundaries: 2048 → 2048 px
        // (sample=1), 2049 → 1024 px (sample=2) — a 50% drop for a single
        // pixel of overshoot. Floor keeps the result close to but never far
        // below maxEdge (e.g., 2049 → 2049 px, 4097 → 2048 px), at the cost
        // of being up to ~maxEdge px above the target. BitmapFactory doesn't
        // auto-resize to the exact target, but a slightly-over bitmap is
        // strictly more informative for OCR than the cliff's halved one.
        // sampleSize stays an accurate downsample factor for box-coord
        // mapping back to the original image (the caller multiplies by it).
        var sample = 1
        while (longest.toLong() / (sample.toLong() * 2) >= maxEdge) sample *= 2
        return sample
    }
}
