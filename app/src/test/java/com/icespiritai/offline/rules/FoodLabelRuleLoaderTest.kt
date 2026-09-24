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
    fun load_realAssets_t1ScopedRulesDoNotCiteAdLaw() {
        // v0.5.6 P0-RULE-1 (2026-09-25) — invariant pin: T1-scoped rules
        // in food_label_rules.json must NOT cite 《广告法》 in their
        // regulation field. Per memory `feedback-foodlabel-kb-scope`
        // (2026-09-10), the food_label domain is strictly scoped to
        // GB 7718 / 食品标识监督管理办法 / 食品安全法 / sector-specific
        // standards (GB 13432 / GB 28050 / GB 14880 etc.). Ad-law
        // citations belong in ad_signage_rules.json, not here — mixing
        // them violates scope and risks mis-anchoring OCR hits against
        // the wrong statute.
        //
        // NOTE: 《食品安全法实施条例》 is ALSO forbidden in food_label
        // rules per memory `feedback-foodlabel-kb-scope`; that broader
        // invariant is pinned by
        // `load_realAssets_knownFoodLabelAdLawCrossCites` below. This
        // test is intentionally scoped to the 2 T1-fixed IDs so the T1
        // commit message (`fix(rules): food_label 跨域引《广告法》清理(2 条)`)
        // matches the assertion.
        //
        // Companion ad_signage cleanup landed in commit `749d4e1`
        // (v0.1.64) — `feat(v0.1.64): ad_signage 跨域引用清理(30 条) +
        // 广告业务新规则(14 条)`. The food_label side was missed at
        // that time and was finally cleaned in this T1 commit. (Note:
        // `b7f4f30` was a different, narrower fix — it cleaned ONE
        // specific rule's abolished-regulation citation, not the 30-rule
        // cross-domain cleanup.)
        //
        // Out of scope for T1 (addressed in T2 / later, see
        // `load_realAssets_knownFoodLabelAdLawCrossCites`):
        //   • food_gb13432_infant_breastmilk_substitute — cites 广告法 §20.
        //   • food_health_claim_unapproved — cites 食品安全法实施条例 §68.
        //   • food_art7_health_function — cites 食品安全法实施条例 §68.
        //   • food_art28_function_claim_unauthorized — cites
        //     食品安全法实施条例 §38.
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

    @Test
    fun load_realAssets_knownFoodLabelAdLawCrossCites() {
        // v0.5.6 P0-RULE-1 follow-up (2026-09-25) — domain-wide regression
        // pin + T2 punch-list. Asserts that NO food_label rule cites either
        // 《广告法》 or 《食品安全法实施条例》 in its regulation field.
        //
        // Status: RED today (4 cross-cite violations remain).
        // After T2 cleans all 4, this test turns GREEN and stays as the
        // domain-wide regression pin (do NOT delete after T2).
        //
        // Per memory `feedback-foodlabel-kb-scope` (2026-09-10), both
        // statutes are cross-domain for the food_label scope: 《广告法》
        // belongs in ad_signage_rules.json, and 《食品安全法实施条例》 is
        // enforcement procedural detail rather than labeling substance,
        // so it sits outside the GB 7718 / 食品标识监督管理办法 / 食品安全法
        // / sector-specific-standards core.
        //
        // T2 PUNCH-LIST (cross-cite IDs to clean to make this test GREEN):
        //   1. food_gb13432_infant_breastmilk_substitute — cites 广告法 §20
        //      (also cites abolished 母乳代用品销售管理办法 — T2 step 2.3
        //      in `docs/superpowers/plans/2026-09-25-v0.5.6-p0-fixes.md`
        //      removes the entire 食品安全法 / 广告法 / 母乳代用品 cluster,
        //      leaving only `GB 13432-2013 §3.c + 食品安全法 第八十一条`.)
        //   2. food_health_claim_unapproved — cites 食品安全法实施条例 §68
        //      (处罚依据 in `regulation` 字段,属 enforcement procedural).
        //   3. food_art7_health_function — cites 食品安全法实施条例 §68
        //      (同 2,处罚依据 形态).
        //   4. food_art28_function_claim_unauthorized — cites
        //      食品安全法实施条例 §38 (处罚依据 形态).
        //
        // Why this is a separate test from
        // `load_realAssets_t1ScopedRulesDoNotCiteAdLaw`: the T1 test is
        // scoped to exactly the 2 IDs that T1 fixed (so the assertion
        // text matches the commit message); this test is the BROADER
        // invariant pin that survives past T1 and catches future drift.
        //
        // We read the source JSON directly for the same reason as the
        // T1 test — shell profile stages an empty skeleton, so the loader
        // path is invisible to Robolectric. This guards the
        // source-of-truth file shipped in `ice_ocr_rules` profile.
        val src = java.io.File(
            "src/main/assets/rules/food_label_rules.json"
        ).readText(Charsets.UTF_8)
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val rules = json.decodeFromString(FoodLabelRuleSet.serializer(), src).rules
        val crossCiteRules = rules.filter { rule ->
            rule.regulation.contains("广告法") ||
                rule.regulation.contains("食品安全法实施条例")
        }
        assertEquals(
            "food_label rules 不得跨域引《广告法》/《食品安全法实施条例》" +
                "(memory feedback-foodlabel-kb-scope). " +
                "T2 PUNCH-LIST — ${crossCiteRules.size} cross-cite violation(s) remain: " +
                crossCiteRules.sortedBy { it.id }.joinToString {
                    "${it.id}(regulation='${it.regulation}')"
                },
            0,
            crossCiteRules.size,
        )
    }
}