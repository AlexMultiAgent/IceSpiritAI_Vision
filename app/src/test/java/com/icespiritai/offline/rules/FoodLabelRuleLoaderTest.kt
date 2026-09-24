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

    @Test
    fun load_realAssets_foodLabelRulesDoNotCiteAdLaw() {
        // v0.5.6 P0-RULE-1 (2026-09-25) — invariant pin: T1-scoped rules
        // in food_label_rules.json must NOT cite 《广告法》 in their
        // regulation field. Per memory `feedback-foodlabel-kb-scope`
        // (2026-09-10), the food_label domain is strictly scoped to
        // GB 7718 / 食品标识监督管理办法 / 食品安全法 / 食品安全法实施条例 /
        // sector-specific standards (GB 13432 / GB 28050 / GB 14880
        // etc.). Ad-law citations belong in ad_signage_rules.json, not
        // here — mixing them violates scope and risks mis-anchoring OCR
        // hits against the wrong statute.
        //
        // Companion ad_signage cleanup landed in commit b7f4f30; the
        // food_label side was missed. This test pins the T1 subset
        // (`food_nozero_add` + `food_art8_minor_unapproved`) clean.
        //
        // Out of scope for T1 (will be addressed in T2 / later):
        //   • food_gb13432_infant_breastmilk_substitute — regulation cites
        //     广告法 §20 (breast-milk-substitute cross-domain citation).
        //
        // We read the source JSON directly rather than going through
        // FoodLabelRuleLoader(ctx).load() — the shell profile stages an
        // empty 24-byte skeleton ({"version":1,"rules":[]}), so the loader
        // path sees zero rules regardless. This guard pins the
        // source-of-truth file shipped in `ice_ocr_rules` profile. Mirrors
        // AssetRuleLoaderTest.load_parsesActualBundledFoodLabelAssetShape.
        val src = java.io.File(
            "src/main/assets/rules/food_label_rules.json"
        ).readText(Charsets.UTF_8)
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val rules = json.decodeFromString(FoodLabelRuleSet.serializer(), src).rules
        val t1ScopedIds = setOf("food_nozero_add", "food_art8_minor_unapproved")
        val adLawHits = rules.filter { rule ->
            rule.id in t1ScopedIds && rule.regulation.contains("广告法")
        }
        assertEquals(
            "T1-scoped food_label rules 不得跨域引《广告法》" +
                "(memory feedback-foodlabel-kb-scope),违规: " +
                adLawHits.joinToString { "${it.id}(${it.regulation})" },
            0,
            adLawHits.size,
        )
    }
}