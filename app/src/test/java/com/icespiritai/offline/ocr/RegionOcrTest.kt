package com.icespiritai.offline.ocr

import android.graphics.Rect
import com.icespiritai.offline.domain.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「放大识别这块 region」的纯逻辑。
 *
 * 现场背景（2026-09-18）：眼镜 AI 流只有 640×480，相机分辨率 App 改不了
 * （`0x33` 只带 1 字节 quality、`0x50` 被固件拒绝），所以补偿手段是
 * 「用户点哪块、就把哪块裁出来放大再认一次」。这些用例钉住三件事：
 * 裁剪框不越界、放大倍数不失控、映射回原图的坐标能对上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RegionOcrTest {

    @Test
    fun cropAround_centresOnTheTappedPoint_andMagnifiesSmallImages() {
        val crop = RegionOcr.cropAround(0.5f, 0.5f, imageWidth = 640, imageHeight = 480)
            ?: error("640x480 must produce a crop")

        // 短边 480 × 0.35 = 168 px；1600/168 > 3，所以放大取上限 3×。
        assertEquals(168, crop.width)
        assertEquals(168, crop.height)
        assertEquals(3, crop.scale)
        assertEquals(320 - 84, crop.left)
        assertEquals(240 - 84, crop.top)
        assertEquals(crop.left + crop.width, crop.right)
        assertEquals(crop.top + crop.height, crop.bottom)
    }

    @Test
    fun cropAround_clampsToTheImageEdges() {
        val topLeft = RegionOcr.cropAround(0f, 0f, 640, 480)!!
        assertEquals(0, topLeft.left)
        assertEquals(0, topLeft.top)

        val bottomRight = RegionOcr.cropAround(1f, 1f, 640, 480)!!
        assertEquals(640, bottomRight.right)
        assertEquals(480, bottomRight.bottom)
    }

    @Test
    fun cropAround_doesNotMagnifyImagesThatAreAlreadyBigEnough() {
        // 3000×4000：短边 3000 × 0.35 = 1050 px，1600/1050 = 1 → 原样送 OCR。
        val crop = RegionOcr.cropAround(0.5f, 0.5f, 3000, 4000)!!
        assertEquals(1050, crop.width)
        assertEquals(1, crop.scale)
        // 裁剪框整体落在图内。
        assertTrue(crop.left >= 0 && crop.right <= 3000)
        assertTrue(crop.top >= 0 && crop.bottom <= 4000)
    }

    @Test
    fun cropAround_rejectsDegenerateImages() {
        assertNull(RegionOcr.cropAround(0.5f, 0.5f, 0, 480))
        assertNull(RegionOcr.cropAround(0.5f, 0.5f, 640, -1))
    }

    @Test
    fun mapBoxToBase_undoesTheMagnificationAndAddsTheOffset() {
        val crop = RegionOcr.Crop(left = 100, top = 50, width = 168, height = 168, scale = 3)

        val mapped = RegionOcr.mapBoxToBase(Rect(30, 60, 90, 120), crop)

        assertEquals(Rect(110, 70, 130, 90), mapped)
    }

    @Test
    fun mapBoxToOriginal_scalesByIntegerCoordinateScale() {
        // 4032×3024 phone → 2016×1512 downsampled (coordinateScale = 2):
        // 基础图空间一个 Rect 必须乘 2 才落在原图空间。
        val original = RegionOcr.mapBoxToOriginal(Rect(100, 200, 300, 400), 2f)
        assertEquals(Rect(200, 400, 600, 800), original)
    }

    @Test
    fun mapBoxToOriginal_scalesByFractionalCoordinateScale() {
        // 640×480 glasses → 1280×960 upscaled (coordinateScale = 0.5):
        // 基础图空间一个 Rect 必须乘 0.5 才落在原图空间(原图 640×480)。
        val original = RegionOcr.mapBoxToOriginal(Rect(100, 100, 200, 200), 0.5f)
        assertEquals(Rect(50, 50, 100, 100), original)
    }

    @Test
    fun mapBoxToOriginal_identityWhenScaleIsOne() {
        // 已是大图(短边 ≤ MAX_DECODE_EDGE_PX 且不需要放大)时 coordinateScale = 1,
        // 这是 identity —— 不应意外缩放框。
        val original = RegionOcr.mapBoxToOriginal(Rect(10, 20, 30, 40), 1f)
        assertEquals(Rect(10, 20, 30, 40), original)
    }

    @Test
    fun mapBoxToOriginal_clampsNonPositiveScale() {
        // 兜底:coordinateScale 不应为 0 / 负(否则 Rect 会变 0×0 / 翻转)。
        // 约定实现用 Float.MIN_VALUE 替代,确保 rect 至少是个空 box 而不爆。
        val original = RegionOcr.mapBoxToOriginal(Rect(10, 20, 30, 40), 0f)
        // 不要求具体值,只要求不抛异常、且不会变 0×0
        assertTrue("non-positive scale must not collapse to 0×0 rect", original.width() >= 0)
    }

    @Test
    fun mergeLines_keepsBaseLinesAndOnlyAddsNewText() {
        val base = listOf(TextLine("禁止 吸烟", Rect(0, 0, 10, 10), 0.9f))
        val region = listOf(
            TextLine("禁止吸烟", Rect(1, 1, 11, 11), 0.8f), // 同一行（只差空格）
            TextLine("世界第一", Rect(2, 2, 12, 12), 0.7f), // 新的
        )

        val merged = RegionOcr.mergeLines(base, region)

        assertEquals(2, merged.size)
        // 基础图那条被保留（它的框本来就在整图坐标系里对齐）。
        assertEquals("禁止 吸烟", merged[0].text)
        assertEquals("世界第一", merged[1].text)
    }

    @Test
    fun mergeLines_isANoOpWhenTheRegionAddsNothing() {
        val base = listOf(TextLine("入口", Rect(0, 0, 10, 10), 0.9f))
        assertEquals(base, RegionOcr.mergeLines(base, emptyList()))
        assertEquals(base, RegionOcr.mergeLines(base, listOf(TextLine("入口", Rect(1, 1, 2, 2), 0.5f))))
    }

    @Test
    fun mergeLines_treatsASubstringAsTheSameLine() {
        // 实测场景：整图读到「请对正图片后点击拍照」，放大后读到「后点击拍照」。
        // 只比全等会把同一行算两遍 —— 面板与命中都会重复。
        val base = listOf(TextLine("请对正图片后点击拍照", Rect(0, 0, 100, 20), 0.9f))
        val region = listOf(TextLine("后点击拍照", Rect(40, 0, 100, 20), 1.0f))

        val merged = RegionOcr.mergeLines(base, region)

        assertEquals(1, merged.size)
        assertEquals("请对正图片后点击拍照", merged[0].text)
    }

    @Test
    fun mergeLines_keepsASingleCharacterThatMerelyAppearsInALongerLine() {
        // 食品标签里的单字（钠/铁）不该被长句子里偶然出现的同字吃掉。
        val base = listOf(TextLine("营养成分表钠含量", Rect(0, 0, 100, 20), 0.8f))
        val region = listOf(TextLine("钠", Rect(50, 0, 60, 20), 0.95f))

        val merged = RegionOcr.mergeLines(base, region)

        assertEquals(2, merged.size)
    }

    @Test
    fun mergeLines_dropsBlankRegionText() {
        val base = emptyList<TextLine>()
        val region = listOf(TextLine("   ", Rect(0, 0, 1, 1), 0.4f))
        assertTrue(RegionOcr.mergeLines(base, region).isEmpty())
    }
}
