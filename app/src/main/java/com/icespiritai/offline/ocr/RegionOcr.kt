package com.icespiritai.offline.ocr

import android.graphics.Rect
import com.icespiritai.offline.domain.TextLine
import kotlin.math.roundToInt

/**
 * 「放大识别这块 region」——App 侧对眼镜 640×480 相机的补偿手段。
 *
 * 相机的分辨率我们改不了（`0x33` 只有 1 字节 JPEG quality，`0x50` 被固件拒绝，
 * 见 `docs/glasses/固件需求-按键照片直传与0x33耗时-2026-09-17.md` 需求 C），
 * 但 OCR 真正在意的是**文字占多少像素**：把用户点的那块裁出来放大
 * [MAX_SCALE] 倍，字形像素数就翻几倍，等效于"局部提高分辨率"。
 *
 * 必须**由用户指定区域**：我们离线量过"自动裁中心"，结果反而更差
 * （同一张包装照片，整图 17 行 / 172 字 → 中心裁 60% 剩 11 行 / 73 字，
 * 裁 40% 只剩 1 行 / 8 字），因为其它区域的文字被裁掉了。官方 App 的
 * `recognizeImageRegion`（用户框选 → pad 6% → Q92 重裁重识）也是这个思路。
 *
 * 这个 object 只有**纯几何 + 合并**逻辑，便于单测；真正的解码/裁剪/推理在
 * [com.icespiritai.offline.analysis.ImageAnalyzerRepository.recognizeRegion]。
 */
internal object RegionOcr {

    /**
     * 裁剪框边长占图片**短边**的比例。0.35 → 640×480 上约 168×168 px，
     * 大致是"一屏看一眼就能覆盖到的局部"；太小会切字，太大会稀释放大倍数。
     */
    const val DEFAULT_SPAN_FRACTION = 0.35f

    /** 放大倍数上限（超过这个倍率只是把已有的模糊插值得更模糊）。 */
    const val MAX_SCALE = 3

    /** 放大后长边上限：OCR 耗时随面积增长，1600 与 App 主路径的上限一致。 */
    const val MAX_OUTPUT_EDGE_PX = 1600

    /** 临时裁剪图的 JPEG 质量（对齐官方 `recognizeImageRegion` 的 Q≈92）。 */
    const val CROP_JPEG_QUALITY = 92

    /** 合并后的行数上限，防止反复框选把结果撑爆。 */
    const val MAX_MERGED_LINES = 200

    /**
     * 基础图里的一块矩形，以及送给 OCR 前要放大的倍数。
     * [left]/[top]/[width]/[height] 都是**基础图**坐标系（像素）。
     */
    data class Crop(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val scale: Int,
    ) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /**
     * 以 ([fractionX], [fractionY])（0..1 的图片相对坐标）为中心取方框。
     *
     * @return `null` 当图片尺寸非法（0 或负数）——调用方此时应放弃这次局部识别，
     *   而不是拿一个空框去 OCR。
     */
    fun cropAround(
        fractionX: Float,
        fractionY: Float,
        imageWidth: Int,
        imageHeight: Int,
        spanFraction: Float = DEFAULT_SPAN_FRACTION,
        maxScale: Int = MAX_SCALE,
        maxOutputEdgePx: Int = MAX_OUTPUT_EDGE_PX,
    ): Crop? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val fx = fractionX.coerceIn(0f, 1f)
        val fy = fractionY.coerceIn(0f, 1f)

        val shortest = minOf(imageWidth, imageHeight)
        // 下限 32 px：再小的一块即使放大也只剩插值噪声。
        val span = (shortest * spanFraction.coerceIn(0.05f, 1f)).roundToInt()
            .coerceIn(minOf(32, shortest), shortest)

        val left = (fx * imageWidth - span / 2f).roundToInt().coerceIn(0, imageWidth - span)
        val top = (fy * imageHeight - span / 2f).roundToInt().coerceIn(0, imageHeight - span)

        // 放大倍数与实际图一起受限：大图原样送（scale=1），小图才放大。
        val scale = (maxOutputEdgePx / span.coerceAtLeast(1)).coerceIn(1, maxScale)
        return Crop(left = left, top = top, width = span, height = span, scale = scale)
    }

    /**
     * 把 OCR 在**放大后的裁剪图**里给出的框，映射回基础图坐标。
     *
     * OCR 引擎返回的坐标始终在"它收到的那张图"的坐标系里（引擎内部的
     * `coordinateScale` 已经把自身缩放假回去了），所以这里只需
     * `÷ scale + 偏移`。
     */
    fun mapBoxToBase(box: Rect, crop: Crop): Rect {
        val scale = crop.scale.coerceAtLeast(1)
        val left = crop.left + (box.left / scale.toFloat()).roundToInt()
        val top = crop.top + (box.top / scale.toFloat()).roundToInt()
        val right = crop.left + (box.right / scale.toFloat()).roundToInt()
        val bottom = crop.top + (box.bottom / scale.toFloat()).roundToInt()
        return Rect(left, top, right, bottom)
    }

    /**
     * 把局部识别的行并入整图结果。
     *
     * 去重按**归一化文本**（去空格）比对，并且把"包含关系"也算重复：
     * 局部放大后把一行读得更完整（或只读出一行的一部分）是很常见的
     * ——实测里整图读到「请对正图片后点击拍照」，放大后读到「后点击拍照」，
     * 只比对全等就会把同一行算两遍，命中也跟着翻倍。
     *
     * 保留整图那条：它的框本来就在整图坐标系里对齐，而局部那条的坐标是
     * 映射回来的。
     *
     * 包含判断只在两边都 ≥ [MIN_CONTAINMENT_CHARS] 个字时生效，避免
     * 单个字（例如食品标签里的「钠」）被长句子偶然"包含"掉。
     */
    fun mergeLines(base: List<TextLine>, region: List<TextLine>): List<TextLine> {
        if (region.isEmpty()) return base
        val seen = base.mapTo(HashSet()) { it.text.normalizedForDedupe() }
        val merged = ArrayList<TextLine>(base.size + region.size)
        merged += base
        for (line in region) {
            val key = line.text.normalizedForDedupe()
            if (key.isEmpty() || seen.contains(key)) continue
            if (isCoveredByExisting(key, seen)) continue
            seen.add(key)
            merged += line
            if (merged.size >= MAX_MERGED_LINES) break
        }
        return merged
    }

    /** [candidate] 与已有任一行互为子串时视为同一行（见 [mergeLines]）。 */
    private fun isCoveredByExisting(candidate: String, existing: Set<String>): Boolean {
        if (candidate.length < MIN_CONTAINMENT_CHARS) return false
        return existing.any { line ->
            line.length >= MIN_CONTAINMENT_CHARS &&
                (line.contains(candidate) || candidate.contains(line))
        }
    }

    internal fun String.normalizedForDedupe(): String =
        filter { !it.isWhitespace() }.trim()

    /** 参与"包含即重复"判断的最短长度。 */
    private const val MIN_CONTAINMENT_CHARS = 2
}
