package com.icespiritai.offline.rules

import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.domain.RuleLoadFailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Loader-level tests for FoodLabelRuleLoader. Symmetric to
 * AdSignageRuleLoaderTest; covers the bundled `rules/food_label_rules.json`
 * asset through `context.assets.open(...)`. Malformed-JSON coverage lives
 * in `AssetRuleLoaderTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoodLabelRuleLoaderTest {

    @Test
    fun load_realAssets_doesNotThrow() {
        // Profile-dependent bundled content (shell ships empty skeleton,
        // ice_ocr_rules ships 66 rules / v4). Loader must complete without
        // throwing either way.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = FoodLabelRuleLoader(ctx).load()
        assertNotNull("loader must return a non-null List", rules)
        assertTrue(
            "every loaded food rule must bundle a non-blank provision",
            rules.all { it.lawText.isNotBlank() },
        )
        assertTrue(
            "every loaded rule id must be unique",
            rules.map { it.id }.toSet().size == rules.size,
        )
    }

    @Test
    fun load_explicitAssetPath_doesNotThrow() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = FoodLabelRuleLoader(ctx, path = "rules/food_label_rules.json").load()
        assertNotNull(rules)
    }

    @Test
    fun load_missingPath_throwsRuleLoadFailed() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            FoodLabelRuleLoader(ctx, path = "rules/__missing_food_label__.json").load()
            fail("expected RuleLoadFailed for missing asset path")
        } catch (e: RuleLoadFailed) {
            assertTrue(
                "RuleLoadFailed message should reference the missing path",
                e.message?.contains("__missing_food_label__") == true,
            )
            assertNotNull("RuleLoadFailed must retain its cause", e.cause)
        }
    }

    @Test
    fun load_emptyPath_throwsRuleLoadFailed() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            FoodLabelRuleLoader(ctx, path = "").load()
            fail("expected RuleLoadFailed for empty asset path")
        } catch (e: RuleLoadFailed) {
            assertEquals(RuleLoadFailed::class.java, e::class.java)
        }
    }
}