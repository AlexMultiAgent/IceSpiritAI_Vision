package com.icespiritai.offline.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDisplayTest {

    @Test
    fun adDomain_knownKeys_mapToChineseNames() {
        val expected = mapOf(
            "absolute" to "绝对化用语",
            "education" to "教育培训",
            "medical" to "医疗医药",
            "finance" to "金融投资",
            "realestate" to "房地产",
            "restricted" to "烟酒类",
            "minor" to "未成年人",
            "outdoor" to "户外广告",
            "signage" to "广告文案",
        )
        for ((key, label) in expected) {
            assertEquals(label, CategoryDisplay.displayName(CategoryDisplay.DOMAIN_AD, key))
        }
    }

    @Test
    fun adDomain_unknownKey_passesThroughUnchanged() {
        assertEquals(
            "future-category",
            CategoryDisplay.displayName(CategoryDisplay.DOMAIN_AD, "future-category"),
        )
    }

    @Test
    fun foodDomain_knownKeys_mapToChineseNames() {
        val expected = mapOf(
            "label_form" to "标签形式",
            "product_name" to "产品名称",
            "ingredient" to "配料表",
            "allergen" to "过敏原",
            "production_date" to "生产日期与保质期",
            "net_weight" to "净含量",
            "nutrition" to "营养成分表",
            "additive" to "食品添加剂",
            "functional_claim" to "功能声称",
            "specific_food" to "特殊食品",
        )
        for ((key, label) in expected) {
            assertEquals(label, CategoryDisplay.displayName(CategoryDisplay.DOMAIN_FOOD, key))
        }
    }

    @Test
    fun foodDomain_unknownKey_passesThroughUnchanged() {
        assertEquals(
            "future-food-category",
            CategoryDisplay.displayName(CategoryDisplay.DOMAIN_FOOD, "future-food-category"),
        )
    }

    @Test
    fun unknownDomain_passesThroughCategoryUnchanged() {
        assertEquals(
            "absolute",
            CategoryDisplay.displayName("future-domain", "absolute"),
        )
    }

    @Test
    fun adAndFoodCategoriesAreIndependent() {
        // The same raw key should resolve differently when stamped with a
        // different domain — proving the (domain, category) tuple is the
        // actual key and there's no leakage between the two sub-tables.
        assertEquals(
            "绝对化用语",
            CategoryDisplay.displayName(CategoryDisplay.DOMAIN_AD, "absolute"),
        )
        assertEquals(
            "absolute",
            CategoryDisplay.displayName(CategoryDisplay.DOMAIN_FOOD, "absolute"),
        )
    }
}