package com.icespiritai.offline.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 长按坐标反算：预览框里的触点 → 图片内的相对坐标。
 *
 * 这是「放大识别这块 region」的入口数学。图片按等比缩放居中绘制
 * （见 [computeFitTransform]），所以触点落在图片外的信箱区时必须返回
 * `null` —— 否则用户在留白处一按，就会被当成"选了图片边缘那一块"，
 * 裁出来的区域完全是错的。
 */
class ImagePreviewFractionTest {

    @Test
    fun returnsRelativeCoordinatesInsideTheImage() {
        val transform = FitTransform(scaleX = 1f, scaleY = 1f, offsetX = 0f, offsetY = 0f)

        assertEquals(0.5f to 0.5f, viewOffsetToImageFraction(Offset(320f, 240f), transform, IntSize(640, 480)))
        assertEquals(0f to 0f, viewOffsetToImageFraction(Offset(0f, 0f), transform, IntSize(640, 480)))
        assertEquals(1f to 1f, viewOffsetToImageFraction(Offset(640f, 480f), transform, IntSize(640, 480)))
    }

    @Test
    fun accountsForTheLetterboxOffsetAndScale() {
        // 图片 640×480 画在 1080×2400 的框里：scale = min(1.687, 5) = 1.6875，
        // 垂直方向留黑边 (2400 - 480×1.6875)/2 = 795。
        val transform = computeFitTransform(
            painter = null,
            boxSize = IntSize(1080, 2400),
            imageSize = IntSize(640, 480),
        )

        val centre = viewOffsetToImageFraction(
            offset = Offset(1080f / 2f, 795f + 480f * 1.6875f / 2f),
            transform = transform,
            imageSize = IntSize(640, 480),
        )
        assertEquals(0.5f, centre!!.first, 0.01f)
        assertEquals(0.5f, centre.second, 0.01f)
    }

    @Test
    fun rejectsTapsInTheLetterboxAndOutsideTheImage() {
        val transform = computeFitTransform(
            painter = null,
            boxSize = IntSize(1080, 2400),
            imageSize = IntSize(640, 480),
        )

        // 上黑边里（图片从 y=795 才开始）。
        assertNull(
            viewOffsetToImageFraction(Offset(540f, 100f), transform, IntSize(640, 480)),
        )
        // 图片右边界之外。
        assertNull(
            viewOffsetToImageFraction(Offset(2000f, 1200f), transform, IntSize(640, 480)),
        )
    }

    @Test
    fun rejectsUnknownImageSize() {
        val transform = FitTransform(1f, 1f, 0f, 0f)
        assertNull(viewOffsetToImageFraction(Offset(10f, 10f), transform, null))
        assertNull(viewOffsetToImageFraction(Offset(10f, 10f), transform, IntSize(0, 0)))
    }
}
