package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 文本 fixture 规则回归测试。
 *
 * 来源:`违规案例/text_*.md`(无 .jpg)— 政府站一手「处罚通报」类文本。
 * 跑真实 ad_signage_rules.json(从 src/main/assets 加载),对每条 fixture 的
 * 「原始违法广告语」字段跑 AdSignageRuleMatcher.scan(),断言命中规则 ID
 * 集合 == fixture frontmatter 中声明的「预期命中规则」ID 集合(精确 set match)。
 *
 * 与 AdSignageMentorFiveImageRegressionTest 的差异:
 *   - 后者做 OCR→规则端到端 subset pin(必须命中)
 *   - 本测试做规则逻辑完整性 pin(命中集合不能漂)
 *
 * 失败模式:任何规则改动(新增 / 删除 / 挪关键词)若让 fixture 命中集合变化,
 * 测试即失败,迫使 fixture 同步更新 — 这是规则迭代的「反向压力」。
 */
class AdSignageTextFixtureRegressionTest {

    private fun loadRealRules(): List<AdSignageRule> {
        val candidates = listOf(
            File("src/main/assets/rules/ad_signage_rules.json"),
            File("app/src/main/assets/rules/ad_signage_rules.json"),
            File("app/build/generated/assets/rules/ad_signage_rules.json"),
            File("../src/main/assets/rules/ad_signage_rules.json"),
        )
        val jsonFile = candidates.firstOrNull { it.exists() && it.length() > 100 }
            ?: error("expected ad_signage_rules.json at: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
        val raw = jsonFile.readText(Charsets.UTF_8)
        return Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(AdSignageRuleSet.serializer(), raw).rules
    }

    private fun fixturesDir(): File {
        val candidates = listOf(
            File("违规案例"),
            File("../违规案例"),
            File("app/../违规案例"),
            File("app/build/../违规案例"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("expected 违规案例/ dir at: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
    }

    @Test
    fun `every text fixture has matching rule hits (exact set)`() {
        val rules = loadRealRules()
        val ruleIds = rules.map { it.id }.toSet()
        val matcher = AdSignageRuleMatcher(rules)
        val cases = TextFixtureLoader.loadAll(fixturesDir())

        assertTrue("no text fixtures found in ${fixturesDir()}", cases.isNotEmpty())

        cases.forEach { c ->
            // 1. fixture 引用的规则 ID 必须在白名单内(防止 fixture 引用已废弃规则)
            c.expected.forEach { exp ->
                assertTrue(
                    "fixture ${c.slug} references unknown rule ${exp.id}",
                    exp.id in ruleIds,
                )
            }
            // 2. 命中规则集合精确匹配(严苛 pin,迫使规则迭代时主动审视 fixture)
            val hits = matcher.scan(c.originalAdText)
            val actualIds = hits.map { it.ruleId }.toSet()
            val expectedIds = c.expected.map { it.id }.toSet()
            assertEquals(
                "fixture ${c.slug} rule hit mismatch — " +
                "expected=$expectedIds actual=$actualIds",
                expectedIds, actualIds,
            )
        }
    }

    @Test
    fun `minimum 30 text fixtures collected`() {
        val cases = TextFixtureLoader.loadAll(fixturesDir())
        assertTrue(
            "got ${cases.size} text fixtures, need >= 30",
            cases.size >= 30,
        )
    }

    @Test
    fun `all 13 buckets represented across fixtures`() {
        val cases = TextFixtureLoader.loadAll(fixturesDir())
        val coveredCategories = cases.map { it.category }.toSet()
        val expectedCategories = setOf(
            "medical", "absolute", "education", "food", "realestate", "finance",
            "cosmetic", "agricultural", "signage", "minor", "outdoor",
            "internet_ad", "pestvet",
        )
        assertTrue(
            "missing categories: ${expectedCategories - coveredCategories}",
            coveredCategories.containsAll(expectedCategories),
        )
    }
}
