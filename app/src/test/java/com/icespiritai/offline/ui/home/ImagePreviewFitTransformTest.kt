package com.icespiritai.offline.ui.home

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression pin for v0.1.30 "红框位置标错了！" round 2 (real-device A/B
 * confirmation that v0.1.29's EXIF double-rotation fix did not actually
 * land boxes on text).
 *
 * Root cause (this fix): Coil's `AsyncImage` downsamples the source
 * bitmap to fit the layout constraints (e.g. 800×1000 px on a typical
 * phone preview). `painter.intrinsicSize` reflects the DOWNSAMPLED
 * bitmap dims, NOT the FULL bitmap the OCR engine processed. If
 * `computeFitTransform` uses the painter's intrinsicSize as the
 * reference space for OCR boxes (which were emitted in the FULL
 * bitmap's display-oriented pixel space, e.g. 3024×4032), the scale
 * collapses to 1.0 and the boxes are drawn at raw 3024×4032 coords
 * onto an 800×1000 canvas — drifting past the right/bottom edges and
 * into the OCR text panel.
 *
 * This fix routes the FULL bitmap's display-oriented dims through
 * `OcrResult.imageWidth/imageHeight → AnalysisState.OcrDone/ViolationReport
 * → ImagePreview.imageSize → computeFitTransform(imageSize)`, and
 * `computeFitTransform` PREFERS that explicit `imageSize` over
 * `painter.intrinsicSize`.
 *
 * Robolectric runner + sdk=33 so `IntSize` is the Robolectric-mapped
 * Compose unit type. Pure-function tests — no Compose rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImagePreviewFitTransformTest {

    /**
     * Helper: a Painter whose intrinsicSize is whatever the test wants
     * (simulates Coil's downsampled bitmap dims, which are typically
     * much smaller than the FULL bitmap the OCR engine saw). onDraw
     * is a no-op — the tests don't render anything.
     */
    private fun painterSized(w: Float, h: Float): Painter = object : Painter() {
        override val intrinsicSize: Size = Size(w, h)
        override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
            // no-op for tests
        }
    }

    @Test
    fun `prefers explicit imageSize over painter intrinsicSize`() {
        // The corn-advertisement repro: 3024×4032 full bitmap, 800×1000
        // layout box. Coil would return a painter with intrinsicSize
        // ~800×1000 (downsampled). imageSize carries the FULL dims.
        val t = computeFitTransform(
            painter = painterSized(800f, 1000f),
            boxSize = IntSize(800, 1000),
            imageSize = IntSize(3024, 4032),
        )
        // Reference is 3024×4032, NOT 800×1000.
        val expectedScale = minOf(800f / 3024f, 1000f / 4032f)
        assertEquals(expectedScale, t.scaleX, 0.0001f)
        assertEquals(expectedScale, t.scaleY, 0.0001f)
        // Letterbox on width: image is taller-aspect than the box
        // (3024/4032 ≈ 0.75 vs 800/1000 = 0.8), so the box is the
        // bottleneck and height is fully consumed — offsetY = 0.
        assertEquals(0f, t.offsetY, 0.0001f)
        // offsetX > 0 because the image's width-side leaves margin.
        assertTrue(
            "offsetX should be positive when image is taller-aspect than box, got ${t.offsetX}",
            t.offsetX > 0f,
        )
        // The smoking gun: a box at (1500, 2000, 1700, 2100) in OCR
        // space should land WELL INSIDE the 800×1000 canvas. Under the
        // buggy painter-based transform this same box would draw at
        // x ≈ 1500 (way past 800) — i.e. off-screen.
        val x = t.offsetX + 1500f * t.scaleX
        val y = t.offsetY + 2000f * t.scaleY
        assertTrue(
            "rect x=$x must be inside the 800-wide canvas, was off-screen",
            x in 0f..800f && y in 0f..1000f,
        )
    }

    @Test
    fun `falls back to painter intrinsicSize when imageSize is null`() {
        // Shell profile / FakeOcrEngine path: no real image dims, only
        // whatever Coil handed back. Painter's intrinsicSize wins.
        val t = computeFitTransform(
            painter = painterSized(1200f, 1600f),
            boxSize = IntSize(600, 800),
            imageSize = null,
        )
        val expectedScale = minOf(600f / 1200f, 800f / 1600f)
        assertEquals(expectedScale, t.scaleX, 0.0001f)
        assertEquals(expectedScale, t.scaleY, 0.0001f)
    }

    @Test
    fun `falls back to painter intrinsicSize when imageSize is zero`() {
        // 0×0 is the sentinel that [OcrResult.imageWidth = 0, imageHeight = 0]
        // default produces (legacy callers, tests that don't care). It must
        // NOT crash and must NOT be treated as "use 0×0 reference".
        val t = computeFitTransform(
            painter = painterSized(400f, 400f),
            boxSize = IntSize(200, 200),
            imageSize = IntSize(0, 0),
        )
        val expectedScale = minOf(200f / 400f, 200f / 400f)
        assertEquals(expectedScale, t.scaleX, 0.0001f)
        assertEquals(0f, t.offsetX, 0.0001f)
        assertEquals(0f, t.offsetY, 0.0001f)
    }

    @Test
    fun `returns identity when box and image are the same size`() {
        // Edge case: layout box happens to match image dims 1:1.
        val t = computeFitTransform(
            painter = painterSized(1000f, 1000f),
            boxSize = IntSize(1000, 1000),
            imageSize = IntSize(1000, 1000),
        )
        assertEquals(1f, t.scaleX, 0.0001f)
        assertEquals(1f, t.scaleY, 0.0001f)
        assertEquals(0f, t.offsetX, 0.0001f)
        assertEquals(0f, t.offsetY, 0.0001f)
    }

    @Test
    fun `returns identity when nothing is usable`() {
        // Both painter and imageSize are missing/zero — used at Idle /
        // Loading state where pendingUri hasn't reached OcrDone yet and
        // Coil hasn't loaded anything. Boxes aren't drawn at that point
        // (showLineBoxes gate), but the function must still be safe.
        val t = computeFitTransform(
            painter = null,
            boxSize = IntSize(800, 1000),
            imageSize = null,
        )
        assertEquals(1f, t.scaleX, 0.0001f)
        assertEquals(0f, t.offsetX, 0.0001f)
        assertEquals(0f, t.offsetY, 0.0001f)
    }

    @Test
    fun `imageSize drives letterbox centering`() {
        // Square image in a wide box: height is the bottleneck, so the
        // width-side gets equal left/right letterbox margins.
        val t = computeFitTransform(
            painter = painterSized(200f, 200f),
            boxSize = IntSize(800, 200),
            imageSize = IntSize(200, 200),
        )
        assertEquals(1f, t.scaleX, 0.0001f)
        assertEquals(0f, t.offsetY, 0.0001f)
        // offsetX = (800 - 200) / 2 = 300
        assertEquals(300f, t.offsetX, 0.0001f)
    }
}