package com.icespiritai.offline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun standardDurationIs300ms() {
        assertEquals(300, IceMotion.Default.standardDuration.inWholeMilliseconds.toInt())
    }
    @Test fun emphasizedDurationIs500ms() {
        assertEquals(500, IceMotion.Default.emphasizedDuration.inWholeMilliseconds.toInt())
    }
    @Test fun defaultMotionUsesFastOutSlowInEasingForStandard() {
        assertEquals(androidx.compose.animation.core.FastOutSlowInEasing, IceMotion.Default.standardEasing)
    }
    @Test fun emphasizedEasingIsExpressiveCurve() {
        val expected = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        assertEquals(expected, IceMotion.Default.emphasizedEasing)
    }
}