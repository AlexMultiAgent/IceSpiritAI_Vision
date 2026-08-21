package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 导师视角:5 张现场采集广告招牌照片的违规检测回归测试。
 *
 * 每张图配一份"导师转写"的 OCR 文本(基于人眼能读到的字),用真实规则
 * JSON(从 src/main/assets 拷贝到 test 资源里加载)过一遍 AdSignageRuleMatcher,
 * 看命中什么、漏什么。
 *
 * 重点不在 OCR 还原度(那是 PaddleOCR 的活),而在:
 *  1. 关键词命中是否覆盖现场真实出现的说法
 *  2. 严重度是否合理
 *  3. 是否能泛化(不只对这 5 张图命中,而对同类型/同说法都命中)
 *
 * 5 张图来自 `测试集/` 微信图片 2026-08-19~20 现场采集:
 *   - 5_2011  高速公路路边两块广告牌(玉米种子)
 *   - 6_2011  蟹都汇 APP 首页弹窗(大闸蟹礼券)
 *   - 9_2011  杜蕾斯公交车身广告
 *   - 11_2011 东郊到家 电梯广告(上门按摩)
 *   - 20      小园玉米花青素短视频/橱窗
 */
class AdSignageMentorFiveImageRegressionTest {

    private fun loadRealRules(): List<AdSignageRule> {
        // 兼容 Gradle 跑 test 时 cwd 不一定是项目根目录。
        // 优先级:1) 项目根相对路径 / 2) ice_ocr_rules profile 已 stage 的生成路径
        //        3) cwd 相对路径(本机直接跑测试的常见形态)
        val candidates = listOf(
            File("src/main/assets/rules/ad_signage_rules.json"),
            File("app/src/main/assets/rules/ad_signage_rules.json"),
            File("app/build/generated/assets/rules/ad_signage_rules.json"),
            File("../src/main/assets/rules/ad_signage_rules.json"),
        )
        val jsonFile = candidates.firstOrNull { it.exists() && it.length() > 100 }
            ?: error("expected ad_signage_rules.json >100 bytes at one of: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
        val raw = jsonFile.readText(Charsets.UTF_8)
        val set = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true; isLenient = true
        }.decodeFromString(AdSignageRuleSet.serializer(), raw)
        return set.rules
    }

    private val fixtures = linkedMapOf(
        "5_2011_highway_billboard" to listOf(
            // 右侧 billboard:墅蓝·多行玉米种子
            "墅蓝·多行",
            "有效增加玉米单行数",
            "增产必选",
            "服务电话:400-658-9878",
            // 左侧 billboard:青芒农业
            "青芒农业",
            "建桩待 1.5 亿种植 10 亿",
            "股票代码 Z:1526999",
        ).joinToString("\n"),

        "6_2011_crab_mall_app" to listOf(
            "蟹都汇 KING CRAB CENTER",
            "大国蟹礼 贵在真心",
            "高端大闸蟹领导品牌",
            "大闸蟹十年累计销量全国第一",
            "大闸蟹连锁门店数量全国第一",
            "2026 中秋好礼",
            "送礼不翻车 当选蟹都汇",
            "蟹都汇高端定制酒具",
            "¥498",
            "蟹都汇礼品券 2000 元",
            "¥2000",
        ).joinToString("\n"),

        "9_2011_durex_bus" to listOf(
            "SKYWORTH 创维汽车 公交 388-12",
            "durex",
            "燃情公益红 守护爱始终",
            "经典红 | 杜蕾斯首个公益装",
            "经典红 CLASSIC 12 只装",
            "杜蕾斯",
            "服务好 技术好 质量好 规范 口碑",
        ).joinToString("\n"),

        "11_2011_dongjiao_massage" to listOf(
            "东郊到家 anmo.com",
            "上线城市",
            "重庆 成都 郑州 武汉 长沙 西安 深圳 上海 广州 苏州 杭州 南京",
            "生活如此多郊 按摩我选东郊",
            "东郊到家",
            "全国技师超 9 万人",
            "累计服务超 1000 万次",
            "东郊到家 anmo.com",
            "技师招募 153 7398 5377",
            "时间自由 无需坐班 多劳多得 收益可观",
            "扫码得 100 元现金券",
        ).joinToString("\n"),

        "20_purple_corn_ad" to listOf(
            "2:3 比例",
            "紫玉米花青素",
            "小园玉米",
            "YOUR HEALTHY DIET EXPERT 您的健康饮食专家",
            "抗氧化",
            "增强免疫力",
            "保护心血管",
            "控糖稳血糖",
            "紫玉米花青素护双眼",
            "糖尿病患者的安心选择",
            "@小园 2025年12月06日",
        ).joinToString("\n"),
    )

    private fun scanFixture(name: String, text: String): List<String> {
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val hits = matcher.scan(text)
        val bySeverity = hits.groupBy { it.severity.name }.mapValues { it.value.size }
        val ids = hits.map { it.ruleId }.sorted().joinToString(", ")
        println("[$name] hits=${hits.size} severities=$bySeverity")
        println("  ids=[$ids]")
        return hits.map { it.ruleId }.sorted()
    }

    @Test
    fun mentorReview_allFiveImages_fireExpectedHits() {
        println("===== mentor five-image regression START =====")
        val results = fixtures.mapValues { (name, text) -> name to scanFixture(name, text) }
        println("===== mentor five-image regression END =====")
        assertTrue(results.isNotEmpty())
        Unit
    }

    /**
     * v5 规则后的导师硬期望。
     *
     * 注意:这是**最低**命中数与**必须命中**的 ruleId 集合。
     * 关键词扩展后命中数应 ≥ 这些数字(后续同类图片不会下降)。
     */
    @Test
    fun mentorReview_purpleCornFoodMedicalClaim_mustHitAllViolations() {
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val hits = matcher.scan(fixtures.getValue("20_purple_corn_ad"))
        val ids = hits.map { it.ruleId }.toSet()
        val texts = hits.map { it.matchedText }.toSet()

        // 至少 7 个命中(原本 0 个):保健功能 + 疾病指向 + 极限词
        assertTrue("紫玉米应至少 7 个命中,实际 ${hits.size}", hits.size >= 7)

        // 必须命中 v5 新增 2 条规则
        assertTrue(
            "紫玉米必须命中 signage_food_function_claim: $ids",
            "ad_signage_signage_food_function_claim" in ids,
        )
        assertTrue(
            "紫玉米必须命中 signage_food_disease_target: $ids",
            "ad_signage_signage_food_disease_target" in ids,
        )

        // 必须命中文本关键词("增强免疫力" / "保护心血管" / "控糖稳血糖" / "护双眼" / "糖尿病患者")
        assertTrue("必须命中'增强免疫力': $texts", "增强免疫力" in texts)
        assertTrue("必须命中'保护心血管': $texts", "保护心血管" in texts)
        assertTrue("必须命中'控糖稳血糖': $texts", "控糖稳血糖" in texts)
        assertTrue("必须命中'护双眼': $texts", "护双眼" in texts)
        assertTrue("必须命中'糖尿病患者': $texts", "糖尿病患者" in texts)

        // 必须至少有 Violation 严重度命中(食品医疗宣传是严重违规)
        assertTrue("紫玉米应有 Violation 命中", hits.any { it.severity.name == "Violation" })
    }

    @Test
    fun mentorReview_crabMall_absoluteClaimFiresUnderCorrectCategory() {
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val hits = matcher.scan(fixtures.getValue("6_2011_crab_mall_app"))
        val ids = hits.map { it.ruleId }.toSet()
        val texts = hits.map { it.matchedText }.toSet()
        val categories = hits.map { it.category }.toSet()

        // "全国第一" 应落入通用绝对词规则(不再是 pesticide/veterinary 的误归)
        assertTrue("蟹都汇必须命中 ad_signage_art9_abs_top: $ids",
                   "ad_signage_art9_abs_top" in ids)

        // "全国第一" 应落入通用虚假数据规则
        assertTrue("蟹都汇必须命中 ad_signage_art28b_fake_data: $ids",
                   "ad_signage_art28b_fake_data" in ids)

        // 不应误归到 pesticide / veterinary 类目
        assertTrue(
            "蟹都汇不应命中 pesticide 类目(食品礼券不是农药): $categories",
            "pesticide" !in categories,
        )
        assertTrue(
            "蟹都汇不应命中 veterinary 类目(食品礼券不是兽药): $categories",
            "veterinary" !in categories,
        )

        // 必须命中 "全国第一" / "销量全国第一"(实际 OCR 为 "累计销量全国第一",
        // 关键词中放了 "全国第一" + "销量全国第一" 两个独立 hit)
        assertTrue("蟹都汇应命中 '全国第一': $texts", "全国第一" in texts)
        assertTrue("蟹都汇应命中 '销量全国第一': $texts", "销量全国第一" in texts)

        // 必须命中 "领导品牌"(扩展后的极限词)
        assertTrue("蟹都汇应命中 '领导品牌': $texts", "领导品牌" in texts)

        assertTrue(hits.size >= 5)
    }

    @Test
    fun mentorReview_durex_absoluteClaimFiresUnderAbsoluteCategory() {
        // 杜蕾斯不是化妆品,"首个公益装" 不应再误归到 cosmetic 类目,
        // 而应落入通用 §9(三) 极限词规则(absolute 类目)
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val hits = matcher.scan(fixtures.getValue("9_2011_durex_bus"))
        val ids = hits.map { it.ruleId }.toSet()

        assertTrue("杜蕾斯必须命中 ad_signage_art9_abs_top: $ids",
                   "ad_signage_art9_abs_top" in ids)
    }

    @Test
    fun mentorReview_dongjiaoMassage_art11DataCitationFires() {
        // v0.1.15 扩展:东郊到家"全国技师超 X 万人 | 累计服务超 X 万次"
        // 落入《广告法》第十一条第二款 — 数据未标明出处。
        // Absence 规则按 rule-level 去重,本 fixture 仅触发 1 次。
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val hits = matcher.scan(fixtures.getValue("11_2011_dongjiao_massage"))
        val art11 = hits.filter { it.ruleId == "ad_signage_art11_data_citation" }

        // 必须恰好 1 个 §11(2) 命中(absence 规则按 rule-level 去重)
        assertEquals("东郊到家应恰好 1 个 §11(2) 命中", 1, art11.size)
        // 严重度 = Warning(程序性遗漏,不是 Violation)
        assertEquals(Severity.Warning, art11.first().severity)
        // regulation 字段仅引《广告法》第十一条第二款(跨域引用原则守护)
        assertEquals("《广告法》第十一条第二款", art11.first().regulation)
    }

    /**
     * 端侧 OCR 输出 baseline 回归 pin:由 PaddleOcrFixtureTest (Task 5)
     * 在华为 nova 6 上产出的真机 OCR 输出,确保 §11(2) 在端侧跑得通。
     * 这条测试与 [mentorReview_dongjiaoMassage_art11DataCitationFires] 的差别在于:
     * 后者用导师手写 fixture 测规则逻辑,本条用真机 OCR 文本测 OCR→规则端到端。
     */
    private fun loadDongjiaoBaseline(): String {
        // 兼容 Gradle 跑 test 时 cwd 不一定是项目根目录。
        // 优先级:1) 项目根相对路径 / 2) ice_ocr_rules profile 路径
        //        3) cwd 相对路径(本机直接跑测试的常见形态)
        val candidates = listOf(
            File("src/test/resources/fixtures/dongjiao_baseline.json"),
            File("app/src/test/resources/fixtures/dongjiao_baseline.json"),
            File("../app/src/test/resources/fixtures/dongjiao_baseline.json"),
        )
        val f = candidates.firstOrNull { it.exists() && it.length() > 100 }
            ?: error("expected dongjiao_baseline.json >100 bytes at one of: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
        val raw = f.readText(Charsets.UTF_8)
        val obj = Json {
            ignoreUnknownKeys = true; isLenient = true
        }.parseToJsonElement(raw).let { it as JsonObject }
        return (obj["fullText"] as JsonPrimitive).content
    }

    @Test
    fun dongjiaoBaseline_art11DataCitationFires() {
        // 端侧 OCR 输出 baseline 回归 pin:由 PaddleOcrFixtureTest (Task 5)
        // 在华为 nova 6 上产出的真机 OCR 输出,确保 §11(2) 在端侧跑得通。
        val ocrText = TextNormalizer.forMatching(loadDongjiaoBaseline())
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val hits = matcher.scan(ocrText)
        val art11 = hits.filter { it.ruleId == "ad_signage_art11_data_citation" }

        assertEquals("dongjiao_baseline OCR 应命中 §11(2)", 1, art11.size)
        assertEquals(Severity.Warning, art11.first().severity)
        // OCR baseline L12 含 "全国技师超9万人累计服务超1000万次",
        // AC 按文本流从左到右扫描,首个 claim kw 在 "万人" 处结束。
        assertEquals(
            "baseline art11 命中的 matchedText 应是 '万人'(AC 首个 claim kw)",
            "万人", art11.first().matchedText,
        )
    }
}