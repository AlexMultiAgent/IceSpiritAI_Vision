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
 * Loader-level tests for AdSignageRuleLoader — the seam between the bundled
 * `rules/ad_signage_rules.json` asset and the AdSignageRuleMatcher used by
 * the ViewModel. Pairs with `AssetRuleLoaderTest`, which tests the JSON
 * schema independently; this test exercises the full IO path through
 * `context.assets.open(...)`.
 *
 * Malformed-JSON coverage is intentionally left to `AssetRuleLoaderTest` —
 * Robolectric's AssetManager cannot inject a synthetic bad-JSON file
 * without wrapping the loader seam, and that refactor is out of scope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdSignageRuleLoaderTest {

    @Test
    fun load_realAssets_doesNotThrow() {
        // The bundled asset is profile-dependent: `shell` ships empty rules
        // (skeleton), `ice_ocr_rules` ships the full v5 dataset. Either way,
        // the loader must complete without throwing and return a List.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = AdSignageRuleLoader(ctx).load()
        assertNotNull("loader must return a non-null List", rules)
        // Whichever profile, returned rules must satisfy the per-rule
        // invariant: if any rule exists, it must have a non-blank provision
        // and a unique id. Skeleton profile yields [] which vacuously holds.
        assertTrue(
            "every loaded rule must bundle a non-blank provision",
            rules.all { it.lawText.isNotBlank() },
        )
        assertTrue(
            "every loaded rule id must be unique",
            rules.map { it.id }.toSet().size == rules.size,
        )
    }

    @Test
    fun load_explicitAssetPath_doesNotThrow() {
        // Same shape-of-return guarantee when the constructor receives an
        // explicit path — covers the variant where a future caller passes
        // a non-default path.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = AdSignageRuleLoader(ctx, path = "rules/ad_signage_rules.json").load()
        assertNotNull(rules)
    }

    @Test
    fun load_missingPath_throwsRuleLoadFailed() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            AdSignageRuleLoader(ctx, path = "rules/__definitely_does_not_exist__.json").load()
            fail("expected RuleLoadFailed for missing asset path")
        } catch (e: RuleLoadFailed) {
            assertTrue(
                "RuleLoadFailed message should reference the missing path",
                e.message?.contains("__definitely_does_not_exist__") == true,
            )
            assertNotNull("RuleLoadFailed must retain its cause", e.cause)
        }
    }

    @Test
    fun load_realAssets_adSignageRulesCiteOnlyAdvertisingLaws() {
        // v8 (2026-08-25) — invariant pin: every rule in ad_signage_rules.json
        // must cite ONLY advertising-related statutes in its regulation field.
        // Historical cross-cites that surfaced on the in-app HitCard:
        //   • 食品标识管理规定 / 食品标识监督管理办法  →  food labeling, not ad law
        //   • 婴幼儿配方乳粉产品配方注册管理办法     →  product registration, not ad law
        //   • GB 7718-2011                          →  food labeling standard, not ad law
        // The rule of thumb: regulation must start with 《广告法》 OR contain
        // "广告审查" / "广告发布" / a 部门规章 whose title explicitly includes
        // "广告" (e.g. 广告发布规定, 广告审查管理暂行办法). Anything else is a
        // cross-domain cite and must be rejected.
        //
        // Profile-aware: shell ships slim/empty rules → silently passes.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = AdSignageRuleLoader(ctx).load()
        val bannedTokens = listOf(
            "食品标识",        // 食品标识管理规定 / 食品标识监督管理办法
            "食品标识监督管理办法",
            "GB 7718",          // 预包装食品标签通则
            "配方注册",        // 婴幼儿配方乳粉产品配方注册管理办法 etc.
            "婴幼儿配方",
            "特殊医学用途",   // 特殊医学用途配方食品管理办法
            "保健食品",        // 保健食品命名扫不到,但是"保健食品条例"串到了食品监管
            "蓝帽子",
            "国食健字",
        )
        val advertisingSignals = listOf(
            "《广告法》", "广告审查", "广告发布", "广告登记",
        )
        rules.forEach { rule ->
            val reg = rule.regulation
            val hitsBan = bannedTokens.filter { it in reg }
            val signalsPresent = advertisingSignals.filter { it in reg }
            assertTrue(
                "rule ${rule.id} regulation must not cite non-advertising statutes " +
                    "(hit banned tokens: $hitsBan); current regulation='$reg'",
                hitsBan.isEmpty(),
            )
            assertTrue(
                "rule ${rule.id} regulation must carry an advertising-law signal " +
                    "(none of $advertisingSignals found); current regulation='$reg'",
                signalsPresent.isNotEmpty(),
            )
        }
    }

    @Test
    fun load_realAssets_functionClaimContainsAntiOxidationKeyword() {
        // v7 (2026-08-25) — added 抗氧化 to signage_food_function_claim after a real-world
        // corn-product ad on Huawei nova 6 surfaced this keyword as a missed hit:
        // OCR returned 抗氧化 as its own TextLine, but the v6 keyword list did
        // not include it, so no RuleHit fired and the HighlightOverlay drew no
        // red box. Pin prevents accidental future keyword-list regressions.
        //
        // Profile-aware: shell ships slim/empty rules → silently passes.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = AdSignageRuleLoader(ctx).load()
        val fn = rules.firstOrNull { it.id == "ad_signage_signage_food_function_claim" }
        if (fn != null) {
            assertTrue(
                "function_claim must include 抗氧化 (v7 keyword expansion 2026-08-25)",
                fn.keywords.contains("抗氧化"),
            )
        }
    }

    @Test
    fun load_emptyPath_throwsRuleLoadFailed() {
        // Empty string is not a valid AssetManager key — AssetManager throws
        // and the loader wraps it in RuleLoadFailed.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            AdSignageRuleLoader(ctx, path = "").load()
            fail("expected RuleLoadFailed for empty asset path")
        } catch (e: RuleLoadFailed) {
            // Just assert the failure mode — message text is implementation-defined.
            assertEquals(RuleLoadFailed::class.java, e::class.java)
        }
    }
}