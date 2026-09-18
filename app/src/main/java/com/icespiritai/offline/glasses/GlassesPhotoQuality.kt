package com.icespiritai.offline.glasses

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 「这张眼镜照片值不值得送去 OCR」——用两个便宜指标判断，低于阈值就重拍。
 *
 * 为什么值得做：眼镜的 AI 流只有 640×480，拍糊/拍暗是常态，而**一次拍糊的往返
 * 是纯浪费**（按键 → 出图 ≈1.3 s、传输 0.1–0.7 s、OCR ≈0.3 s），用户还只得到
 * 「未发现违规用语」。实测（2026-09-18，同一副眼镜、同一个包装、同一天）：
 *
 * | 样本 | 清晰度 | 亮度 | OCR |
 * |---|---|---|---|
 * | 镜头被挡住/对空 | **20.3** | 172.6 | 0 行 |
 * | 对着手机屏 30–40 cm | 1686–2048 | 178–185 | 5–26 行（conf 0.70–0.87） |
 * | 对包装凑近 | 2327–6588 | 144–177 | 8–20 行 |
 *
 * 也就是：清晰度低于 ~1000 时检测器基本出不了框；亮度低于 ~120 时基本是暗光长
 * 曝光拖影（同样是 0 行）。阈值取在两者之间，宁可偶尔多拍一张，也不要交一张
 * 读不出字的图。
 *
 * 指标在 [SAMPLE_LONG_EDGE_PX] 的等比缩略图上算，因此与原始分辨率无关，
 * 640×480 的眼镜图和 4000×3000 的相册图可以直接比。
 */
object GlassesPhotoQuality {

    /** 清晰度（Laplacian 方差）下限。低于它 → 重拍。 */
    const val MIN_SHARPNESS = 1000.0

    /** 平均亮度下限（0–255）。低于它 → 大概率是暗光拖影，重拍。 */
    const val MIN_LUMINANCE = 110.0

    /** 平均亮度上限（0–255）。过曝同样丢字。 */
    const val MAX_LUMINANCE = 245.0

    /**
     * 指标的计算分辨率（长边）。固定住是为了让阈值跨设备/跨分辨率可比——
     * Laplacian 方差随缩放变化，不归一化的话 640×480 与 2048×1536 的两张图
     * 根本没法用同一个阈值判断。
     */
    const val SAMPLE_LONG_EDGE_PX = 160

    data class Reading(val sharpness: Double, val luminance: Double) {
        /** 这张图够清楚、够亮，值得送去 OCR。 */
        val usable: Boolean
            get() = sharpness >= MIN_SHARPNESS &&
                luminance in MIN_LUMINANCE..MAX_LUMINANCE

        override fun toString(): String =
            "sharpness=%.1f luminance=%.1f".format(sharpness, luminance)
    }

    /**
     * 从 JPEG 字节算指标；解码失败返回 `null`（调用方当作"无法判断"，
     * 不重拍——宁可交一张未知质量的图，也不要因为读不出自己的文件就卡住流程）。
     */
    fun measureJpeg(bytes: ByteArray): Reading? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= SAMPLE_LONG_EDGE_PX) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return try {
            measureScaled(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** [measureJpeg] 的核心：把图缩到 [SAMPLE_LONG_EDGE_PX] 后算指标。 */
    internal fun measureScaled(bitmap: Bitmap): Reading {
        val longest = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = SAMPLE_LONG_EDGE_PX.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(3)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(3)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()

        var luminanceSum = 0L
        var laplacianSum = 0.0
        var laplacianSqSum = 0.0
        var samples = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val centre = luma(pixels[y * width + x])
                val lap = (
                    luma(pixels[y * width + x - 1]) +
                        luma(pixels[y * width + x + 1]) +
                        luma(pixels[(y - 1) * width + x]) +
                        luma(pixels[(y + 1) * width + x]) -
                        4 * centre
                    ).toDouble()
                luminanceSum += centre
                laplacianSum += lap
                laplacianSqSum += lap * lap
                samples++
            }
        }
        if (samples == 0) return Reading(sharpness = 0.0, luminance = 0.0)
        val meanLap = laplacianSum / samples
        return Reading(
            sharpness = (laplacianSqSum / samples) - meanLap * meanLap,
            luminance = luminanceSum.toDouble() / samples,
        )
    }

    /** Rec.601 luma of a packed ARGB pixel. */
    private fun luma(p: Int): Int =
        ((p shr 16 and 0xFF) * 30 + (p shr 8 and 0xFF) * 59 + (p and 0xFF) * 11) / 100
}
