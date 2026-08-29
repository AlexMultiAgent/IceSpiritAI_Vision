package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Performance regression guard for [AdSignageRuleMatcher] / [FoodLabelRuleMatcher]
 * — both the AC trie construction cost and the per-scan cost scale with the
 * number of registered keywords, and the L≥5 auto-decomposition pass added on
 * 2026-08-29 expands the keyword set with ~5–9 variants per long keyword
 * (~1,800 extra entries on the ad_signage ruleset alone). This test class
 * pins the construction + scan time budgets so future rule-set growth or AC
 * integration changes surface as a CI failure rather than a per-image
 * latency regression discovered in production.
 *
 * These are **soft** budgets: the assertions catch algorithmic regressions
 * (e.g. an O(n²) dedup loop, or a `LinkedHashMap` lookup replaced with a
 * linear scan) but do NOT enforce absolute wall-clock values. The CI machine
 * can be 3× slower than the author laptop — the budgets are set at ~10×
 * the measured ground truth on a developer MacBook Pro so a 5× slowdown
 * still trips, but a 2× noise spike does not.
 *
 * On the 2026-08-29 ground truth (developer machine, JDK 17.0.18, ad_signage
 * v5 121 rules / ~1,250 keywords / ~1,800 variants):
 *   - matcher construction (AC trie build): ~30–80 ms
 *   - scan of a ~300-char typical ad text:     ~0.5–2 ms
 *
 * Budgets set at 800 ms / 30 ms respectively. Soft by design — see comment
 * above. If these budgets start failing on an unrelated infrastructure
 * change (e.g. JDK upgrade slowing TreeMap operations), loosen them, do not
 * change the matcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MatcherPerformanceRegressionTest {

    /**
     * Typical ad signage OCR text — synthesized from the 66-fixture sample to
     * match the average length / density distribution observed in PP-OCRv6_small
     * nova 6 runs (~200–400 chars, ~5–20 keywords visible per rule category).
     * Stays under 1000 chars to keep `scan` cost dominated by AC traversal, not
     * by per-char normalization.
     */
    private val typicalAdText = """
        本店隆重推出纯手工养生秘制膏方,采用三十六味名贵中药材熬制,经多年临床验证,
        专治高血压患者、糖尿病人的、心脑血管病人、冠心病患者,调理三高,
        100%有效!据权威数据,30%人群服药后明显改善,根除疼痛,彻底治愈,无副作用。
        错过再等一年!全国第一连锁门店,销量遥遥领先,行业领导者。
        本公司拥有最先进技术,获得国家发改委认证,中国500强企业,全国第一品牌。
        报名即可领取免费礼品,活动仅限今日,详情致电 400-888-8888 转 1。
    """.trimIndent().replace("\n", "").replace(" ", "")

    @Test
    fun adSignageMatcher_construction_underBudget() {
        val rules = loadAdSignageRulesFromSource()
        // Skeleton profile ships 0 rules; perf assertion only meaningful when
        // there are real rules to build against. Skip on empty ruleset so the
        // test doesn't false-fail on the shell profile.
        assumeTrue(
            "real ruleset asset missing on this checkout — perf assertion skipped",
            rules.isNotEmpty(),
        )

        // Warm up the JVM (first matcher construction triggers class loading,
        // JIT warmup, etc. — not representative of steady-state cost).
        AdSignageRuleMatcher(rules).scan(typicalAdText)

        // Measure 3 consecutive constructions; take the median to dampen GC
        // / OS scheduling spikes.
        val times = LongArray(3)
        for (i in times.indices) {
            val t0 = System.nanoTime()
            AdSignageRuleMatcher(rules)
            times[i] = System.nanoTime() - t0
        }
        val medianMs = times.sortedArray()[1] / 1_000_000.0

        assertTrue(
            "AdSignageRuleMatcher construction took ${medianMs} ms (budget 800 ms) " +
                "with ${rules.size} rules — possible O(n²) regression or " +
                "variant explosion",
            medianMs < 800.0,
        )
    }

    @Test
    fun adSignageMatcher_scan_underBudget() {
        val rules = loadAdSignageRulesFromSource()
        assumeTrue(
            "real ruleset asset missing on this checkout — perf assertion skipped",
            rules.isNotEmpty(),
        )

        val matcher = AdSignageRuleMatcher(rules)
        // Warm up + populate internal state.
        matcher.scan(typicalAdText)

        // Measure 200 scans, take the median. 200 iterations is enough to
        // amortize one-off JIT effects but small enough to finish in <2 s.
        val n = 200
        val times = LongArray(n)
        for (i in 0 until n) {
            val t0 = System.nanoTime()
            matcher.scan(typicalAdText)
            times[i] = System.nanoTime() - t0
        }
        times.sort()
        val medianMs = times[n / 2] / 1_000_000.0

        assertTrue(
            "AdSignageRuleMatcher.scan took ${medianMs} ms (budget 30 ms) on " +
                "a ${typicalAdText.length}-char text — possible Phase-2 dedup " +
                "or AC emit-order regression",
            medianMs < 30.0,
        )
    }

    @Test
    fun foodLabelMatcher_construction_underBudget() {
        val rules = loadFoodLabelRulesFromSource()
        assumeTrue(
            "real ruleset asset missing on this checkout — perf assertion skipped",
            rules.isNotEmpty(),
        )

        // Warm up.
        FoodLabelRuleMatcher(rules).scan("含零添加糖,减少脂肪")

        val times = LongArray(3)
        for (i in times.indices) {
            val t0 = System.nanoTime()
            FoodLabelRuleMatcher(rules)
            times[i] = System.nanoTime() - t0
        }
        val medianMs = times.sortedArray()[1] / 1_000_000.0

        assertTrue(
            "FoodLabelRuleMatcher construction took ${medianMs} ms (budget 800 ms) " +
                "with ${rules.size} rules",
            medianMs < 800.0,
        )
    }

    @Test
    fun scan_zeroLengthKeywords_handledInReasonableTime() {
        // Regression guard for an edge case where every keyword normalizes
        // to the empty string (e.g. TextNormalizer strip-all). The trie
        // should still build successfully, and scan should return empty.
        val r = AdSignageRule(
            id = "perf-empty", category = "test", regulation = "测试",
            keywords = listOf("", " ", "  "),
            severity = Severity.Info,
        )
        val m = AdSignageRuleMatcher(listOf(r))
        // Should not throw / hang.
        assertTrue(m.scan("").isEmpty())
        assertTrue(m.scan("anything").isEmpty())
    }

    /**
     * Read `src/main/assets/rules/ad_signage_rules.json` directly from disk
     * (bypassing AssetManager). Needed because:
     *
     *   - The shell profile ships an EMPTY `ad_signage_rules.json`
     *     (`{"version":1,"rules":[]}` — see prepare-ocr-rules.gradle.kts).
     *     A perf assertion that reads via `AdSignageRuleLoader(ctx).load()`
     *     under the shell profile sees zero rules and would always skip.
     *
     *   - The ice_ocr_rules profile has the full ruleset, but its unit-test
     *     classpath excludes the `shell` sourceSet (where `FakeOcrEngine`
     *     lives) — compiling the rest of the rules test suite fails.
     *
     * Reading the raw source-of-truth file keeps this test runnable in the
     * default `shell` profile without cross-profile gymnastics. The JSON
     * decoder matches the loader's behavior (`ignoreUnknownKeys`, lenient).
     */
    private fun loadAdSignageRulesFromSource(): List<AdSignageRule> {
        val file = File("src/main/assets/rules/ad_signage_rules.json")
        if (!file.exists()) return emptyList()
        return json.decodeFromString<AdSignageRuleSet>(
            file.readText(Charsets.UTF_8),
        ).rules
    }

    private fun loadFoodLabelRulesFromSource(): List<FoodLabelRule> {
        val file = File("src/main/assets/rules/food_label_rules.json")
        if (!file.exists()) return emptyList()
        return json.decodeFromString<FoodLabelRuleSet>(
            file.readText(Charsets.UTF_8),
        ).rules
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}