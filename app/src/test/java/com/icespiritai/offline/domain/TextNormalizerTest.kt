package com.icespiritai.offline.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun forMatching_removesWhitespaceAndUnifiesFullWidthCharacters() {
        assertEquals("100%有效", TextNormalizer.forMatching(" １００％　有 效\n"))
    }

    @Test
    fun forMatching_isCaseInsensitiveForLatin() {
        assertEquals("usfda", TextNormalizer.forMatching("US FDA"))
    }

    @Test
    fun forMatching_keepsChineseUnchanged() {
        assertEquals("根治率99%", TextNormalizer.forMatching("根治率 99%"))
    }
}
