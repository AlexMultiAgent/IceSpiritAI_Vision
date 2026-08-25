package com.icespiritai.offline.ui.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTokensTest {
    @Test fun displaySmallIsPinned() {
        assertEquals(40.sp, IceSpiritTypography.displaySmall.fontSize)
    }
    @Test fun headlineMediumIsPinned() {
        assertEquals(30.sp, IceSpiritTypography.headlineMedium.fontSize)
    }
    @Test fun headlineSmallIsPinned() {
        assertEquals(26.sp, IceSpiritTypography.headlineSmall.fontSize)
    }
}