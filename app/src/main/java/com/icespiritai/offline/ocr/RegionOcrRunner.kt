package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.icespiritai.offline.domain.TextLine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 放大识别用户点选的区域：解码 → 裁剪 → 放大 → 再跑一次 OCR。
 *
 * 与 [RegionOcr]（纯几何）分开，是因为这里需要 [Context] 读 URI、写临时裁剪图；
 * 而 [com.icespiritai.offline.analysis.ImageAnalyzerRepository] 是无 Android
 * 依赖的纯 JVM 类（单测直接构造），不该为了这个功能被迫引入 Context。
 *
 * 临时裁剪图写到 `cacheDir/region/`，用完即删；同时用 FileProvider 暴露给
 * OCR 引擎（引擎接口收的是 URI）。
 */
class RegionOcrRunner(
    private val context: Context,
    private val ocrEngine: OcrEngine,
) {

    /** 局部识别的结果：坐标已经映射回**基础图**坐标系。 */
    data class RegionResult(
        val lines: List<TextLine>,
        val fullText: String,
        val avgConfidence: Float,
        /** 放大倍数（0 = 没做，例如图片尺寸非法）。 */
        val scale: Int,
    )

    /**
     * @return `null` 表示这张图没法学（读不到字节 / 解码失败 / 尺寸非法）；
     *   识别成功但一个字都没有时返回空 [RegionResult]（调用方据此提示"这块没字"）。
     */
    suspend fun recognize(
        baseUri: Uri,
        fractionX: Float,
        fractionY: Float,
    ): RegionResult? = withContext(Dispatchers.IO) {
        val bytes = BitmapLoader.bytes(context, baseUri) ?: run {
            Log.w(TAG, "region OCR: cannot read $baseUri")
            return@withContext null
        }
        // 和主路径同一个解码上限（2048）：区域坐标因此与分辨率无关，
        // 用户在图上的相对位置在任何尺寸的图里都成立。
        val loaded = BitmapLoader.downsampledBitmapWithScale(bytes, MAX_DECODE_EDGE_PX) ?: run {
            Log.w(TAG, "region OCR: decode failed for $baseUri")
            return@withContext null
        }
        val base = loaded.bitmap
        val crop = RegionOcr.cropAround(fractionX, fractionY, base.width, base.height) ?: return@withContext null

        val cropped = try {
            Bitmap.createBitmap(base, crop.left, crop.top, crop.width, crop.height)
        } catch (e: Exception) {
            Log.w(TAG, "region OCR: crop failed (${crop.left},${crop.top} ${crop.width}x${crop.height})", e)
            return@withContext null
        }
        val scaled = if (crop.scale > 1) {
            Bitmap.createScaledBitmap(
                cropped,
                cropped.width * crop.scale,
                cropped.height * crop.scale,
                true,
            ).also { if (it !== cropped) cropped.recycle() }
        } else {
            cropped
        }

        val dir = File(context.cacheDir, "region").apply { mkdirs() }
        val file = File(dir, "region_${System.currentTimeMillis()}.jpg")
        try {
            withContext(Dispatchers.IO) {
                file.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, RegionOcr.CROP_JPEG_QUALITY, out)
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val result = ocrEngine.recognize(uri)
            val lines = result.lineBoxes.map { line ->
                line.copy(box = RegionOcr.mapBoxToBase(line.box, crop))
            }
            Log.i(
                TAG,
                "region OCR: crop=${crop.width}x${crop.height} scale=${crop.scale} → " +
                    "${lines.size} lines, conf=%.2f".format(result.avgConfidence),
            )
            RegionResult(
                lines = lines,
                fullText = lines.joinToString("\n") { it.text },
                avgConfidence = result.avgConfidence,
                scale = crop.scale,
            )
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
            if (base !== scaled && !base.isRecycled) base.recycle()
            runCatching { file.delete() }
        }
    }

    private companion object {
        const val TAG = "RegionOcr"

        /** 与主 OCR 路径一致的全图解码上限（见 BitmapLoader.DEFAULT_MAX_EDGE_PX）。 */
        const val MAX_DECODE_EDGE_PX = 2048
    }
}
