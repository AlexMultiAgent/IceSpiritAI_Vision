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
     * Below this longest edge the decoded bitmap is enlarged before OCR.
     *
     * The smart glasses hand us **640x480** JPEGs (`ocr input: 640x480` on
     * device, 2026-09-17) while the phone camera gives 2736x3648. PaddleOCR's
     * detector runs with `det_limit_type = "max"`, i.e. it only ever *shrinks*
     * — so at 640 px a 5 mm text line is a couple of pixels tall and is never
     * detected at all. Measured on 12 real case images downscaled to 640 px:
     * character recall 0.908 without upscaling vs 1.000 with it
     * (`docs/knowledge/ocr-resolution-tradeoff.md`).
     */
    private const val UPSCALE_BELOW_MAX_EDGE_PX = 960

    /** Longest edge small photos are enlarged to before OCR. */
    private const val UPSCALE_TO_MAX_EDGE_PX = 1280

    /**
     * A decoded bitmap plus [coordinateScale]: multiply a coordinate in this
     * bitmap's space by it to get the coordinate in the **original** image
     * (the space the preview and the highlight overlay use).
     *
     * It is a `Float` because the bitmap may be larger than the original:
     * downsampling gives a factor > 1, upscaling small photos gives < 1.
     */
    data class DownsampledBitmap(val bitmap: Bitmap, val coordinateScale: Float)

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
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        // Enlarge genuinely small photos (the glasses' 640x480) so the OCR
        // detector sees more than a couple of pixels per text line. Large
        // photos (the album) are untouched — the factor stays 1.
        val longest = maxOf(decoded.width, decoded.height)
        val factor = if (longest in 1 until UPSCALE_BELOW_MAX_EDGE_PX) {
            UPSCALE_TO_MAX_EDGE_PX.toFloat() / longest
        } else {
            1f
        }
        val bitmap = if (factor > 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * factor).toInt().coerceAtLeast(1),
                (decoded.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        return DownsampledBitmap(bitmap = bitmap, coordinateScale = sample / factor)
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
