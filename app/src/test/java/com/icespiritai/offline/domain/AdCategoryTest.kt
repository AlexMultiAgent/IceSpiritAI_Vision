package com.icespiritai.offline.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AdCategoryTest {

    @Test
    fun knownKeys_mapToChineseNames() {
        val expected = mapOf(
            "absolute" to "绝对化用语",
            "education" to "教育培训",
            "medical" to "医疗医药",
            "finance" to "金融投资",
            "realestate" to "房地产",
            "restricted" to "烟酒类",
            "minor" to "未成年人",
        )
        for ((key, label) in expected) {
            assertEquals(label, AdCategory.displayName(key))
        }
    }

    @Test
    fun unknownKey_passesThroughUnchanged() {
        assertEquals("future-category", AdCategory.displayName("future-category"))
    }
}
