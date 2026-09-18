package com.icespiritai.offline.glasses

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 画质门控的判据（阈值来源见 [GlassesPhotoQuality] 的 KDoc：2026-09-18 实测
 * 糊的那批清晰度 20–113、可用的 1686–6588、亮度 ~116–125 是暗光拖影）。
 *
 * 用合成图而不是真机照片：这里要钉的是**指标本身的行为**（清晰图判定可用、
 * 平灰图判定为糊、暗图判定为暗），真机分布由 `docs/smoke` 里的实测记录背书。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassesPhotoQualityTest {

    private fun bitmap(width: Int, height: Int, painter: (Int, Int) -> Int): Bitmap {
        val pixels = IntArray(width * height) { index -> painter(index % width, index / width) }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 4px 棋盘格：Laplacian 响应很强，代表"对上了、有细节"的图。 */
    private fun checkerboard(size: Int = 160): Bitmap = bitmap(size, size) { x, y ->
        val dark = 0xFF202020.toInt()
        val light = 0xFFE0E0E0.toInt()
        if (((x / 4) + (y / 4)) % 2 == 0) light else dark
    }

    @Test
    fun sharpBrightShotIsUsable() {
        val reading = GlassesPhotoQuality.measureScaled(checkerboard())

        assertTrue("checkerboard must read sharp: $reading", reading.sharpness > GlassesPhotoQuality.MIN_SHARPNESS)
        assertTrue("checkerboard must read bright enough: $reading", reading.usable)
    }

    @Test
    fun flatGreyShotIsBlurry() {
        val reading = GlassesPhotoQuality.measureScaled(
            bitmap(160, 160) { _, _ -> 0xFFB0B0B0.toInt() },
        )

        // 纯色图没有梯度：方差 ≈ 0，正是"镜头被挡住/对空"那种样本（实测 20.3）。
        assertTrue("flat image must read as blurry: $reading", reading.sharpness < GlassesPhotoQuality.MIN_SHARPNESS)
        assertTrue(!reading.usable)
    }

    @Test
    fun darkShotIsRejectedEvenWhenItHasDetail() {
        // 暗光长曝光拖影的典型样本（实测亮度 116–125 → 0 行）：即使还有对比度，
        // 亮度低于下限也不能直接送去 OCR。
        val dark = bitmap(160, 160) { x, y ->
            if (((x / 8) + (y / 8)) % 2 == 0) 0xFF101010.toInt() else 0xFF303030.toInt()
        }
        val reading = GlassesPhotoQuality.measureScaled(dark)

        assertTrue("dark sample luminance=${reading.luminance}", reading.luminance < GlassesPhotoQuality.MIN_LUMINANCE)
        assertTrue(!reading.usable)
    }

    @Test
    fun blowsOutOverexposedShots() {
        val white = bitmap(160, 160) { _, _ -> 0xFFFFFFFF.toInt() }
        val reading = GlassesPhotoQuality.measureScaled(white)

        assertTrue(reading.luminance > GlassesPhotoQuality.MAX_LUMINANCE)
        assertTrue(!reading.usable)
    }

    @Test
    fun measureJpeg_decodesAndJudgesTheEncodedBytes() {
        val bytes = ByteArrayOutputStream().use { out ->
            checkerboard().compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }

        val reading = GlassesPhotoQuality.measureJpeg(bytes)

        assertNotNull(reading)
        assertTrue("encoded checkerboard should survive JPEG: $reading", reading!!.usable)
    }

    @Test
    fun measureJpeg_neverCallsGarbageUsable() {
        // 真机上 BitmapFactory 对垃圾字节返回 null → measureJpeg 也返回 null；
        // Robolectric 的 shadow 比较宽容，会把垃圾解成一张全黑小图，于是得到
        // Reading(0.0, 0.0)。两种结果对门控都是安全的（都不 usable），
        // 这里钉的正是这条不变量：**读不出内容的图永远不会被送去 OCR**。
        assertTrue(!isUsable(GlassesPhotoQuality.measureJpeg(byteArrayOf(1, 2, 3, 4, 5))))
        assertTrue(!isUsable(GlassesPhotoQuality.measureJpeg(ByteArray(0))))
    }

    private fun isUsable(reading: GlassesPhotoQuality.Reading?): Boolean = reading?.usable == true
}
