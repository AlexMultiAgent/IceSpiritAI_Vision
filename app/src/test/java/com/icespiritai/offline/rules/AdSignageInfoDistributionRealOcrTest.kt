package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextNormalizer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真实 OCR 输出的 Severity 分布审计,重点回答一个用户问题:
 *
 * > Info 这一桶会不会日常都是 0?
 *
 * 走的是 audit66_ocr/ 仓库里的真实 PP-OCRv6_small 输出(manifest.json
 * 第 3-6 行:`model_name: PP-OCRv6_small_det+PP-OCRv6_small_rec`,
 * `runtime_note: ONNX Runtime CPU (matches Android ice_ocr_rules profile)`)
 * —— 等同于用户拿冰灵锐目真机扫广告招牌后,App 内部 OCR 引擎真正吐出来的
 * 文本(含真实字符漏识 / 乱码 / 英文 OCR 退化等),不是导师转写的干净 OCR。
 *
 * 5 张现场广告的 mentor 五图回归(AdSignageMentorFiveImageRegressionTest)
 * 是干净的转写 fixture,适合测规则逻辑;本测试走的是真实 OCR baseline,
 * 适合测「App 真跑起来后命中结构是否合理」。
 *
 * Mentor 五图 → audit66 真值 OCR baseline 映射:
 *
 * | Mentor 图                | 真实 OCR baseline                              | 说明                          |
 * |--------------------------|-------------------------------------------------|-------------------------------|
 * | 5_2011 玉米种子高速广告   | #12 东北景椒辣妹子种子-早熟高产抗病-种子广告    | 同类替代(无玉米种子原图)    |
 * | 6_2011 蟹都汇 APP 首页    | #65 蟹都汇总部-全国销量第一领导品牌-绝对化       | 同图不同 OCR 切图             |
 * | 9_2011 杜蕾斯公交车身     | #64 杜蕾斯公交车身-首个公益装-化妆品绝对化       | 同图不同 OCR 切图             |
 * | 11_2011 东郊到家按摩 APP  | #62 东郊到家按摩APP-9万人1000万次-数据引用       | 同图不同 OCR 切图             |
 * | 20 紫玉米花青素短视频    | #66 小园玉粱紫玉米花青素-增强免疫糖尿病安心-食品 | 同图不同 OCR 切图             |
 *
 * 每张图输出一行 `[name] hits=N severities={V:x,I:y,W:z} infoRules=[...]`,
 * 后续 review 直观对照。
 */
class AdSignageInfoDistributionRealOcrTest {

    companion object {
        // Hoisted out of [loadRealRules] so each call doesn't re-construct a
        // Json parser. Silences the `Redundant creation of Json format`
        // warning the Kotlin compiler emits on per-call instantiation.
        private val RULES_JSON = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * Mentor fixture → audit66 real-OCR file map. Paths are relative to
     * `app/src/test/resources/fixtures/audit66_ocr/`. The fixture filenames
     * contain CJK characters; `File(constructor path)` on the JVM test
     * runner reads them via UTF-8 (Gradle 9.7 default).
     */
    private data class Mapping(
        val mentorName: String,
        val filename: String,
        val note: String,
    )

    private val mappings = listOf(
        Mapping(
            mentorName = "5_2011_highway_billboard(种子类)",
            filename = "12_东北景椒辣妹子种子-早熟高产抗病-种子广告.txt",
            note = "audit66 无玉米种子原图;用同类的辣椒种子广告替代(都属种子广告 + 增产暗示)",
        ),
        Mapping(
            mentorName = "6_2011_crab_mall(蟹都汇大闸蟹)",
            filename = "65_蟹都汇总部-全国销量第一领导品牌-绝对化.txt",
            note = "audit66 #65 与 mentor 6_2011 同图不同 OCR 切图,均有「全国第一 / 销量第一 / 领导品牌」",
        ),
        Mapping(
            mentorName = "9_2011_durex_bus(杜蕾斯公交车身)",
            filename = "64_杜蕾斯公交车身-首个公益装-化妆品绝对化.txt",
            note = "audit66 #64 与 mentor 9_2011 同图不同 OCR 切图,均有「首个 / 公益装」",
        ),
        Mapping(
            mentorName = "11_2011_dongjiao_massage(东郊到家)",
            filename = "62_东郊到家按摩APP-9万人1000万次-数据引用.txt",
            note = "audit66 #62 与 mentor 11_2011 同图不同 OCR 切图,均有「9万人 / 1000万次」",
        ),
        Mapping(
            mentorName = "20_purple_corn_ad(紫玉米花青素)",
            filename = "66_小园玉粱紫玉米花青素-增强免疫糖尿病安心-食品.txt",
            note = "audit66 #66 与 mentor 20 同图不同 OCR 切图,均有「增强免疫 / 糖尿病」",
        ),
    )

    private fun loadRealRules(): List<AdSignageRule> {
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
        return RULES_JSON.decodeFromString(AdSignageRuleSet.serializer(), raw).rules
    }

    private fun loadAuditOcr(filename: String): String {
        val candidates = listOf(
            File("src/test/resources/fixtures/audit66_ocr/$filename"),
            File("app/src/test/resources/fixtures/audit66_ocr/$filename"),
            File("../app/src/test/resources/fixtures/audit66_ocr/$filename"),
        )
        val f = candidates.firstOrNull { it.exists() && it.length() > 0 }
            ?: error("expected audit66 fixture >0 bytes at one of: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
        return f.readText(Charsets.UTF_8)
    }

    /**
     * Audit pass: print bySeverity + Info-rule breakdown for each of the
     * 5 mentor categories. Reports — does NOT assert. Read this output to
     * see whether Info ever fires on real OCR, and if so which ruleIds.
     */
    @Test
    fun `real OCR — bySeverity distribution across 5 mentor categories`() {
        println("===== real-OCR severity distribution (audit66 baselines) START =====")
        val rules = loadRealRules()
        val matcher = AdSignageRuleMatcher(rules)
        for (m in mappings) {
            val raw = loadAuditOcr(m.filename)
            val text = TextNormalizer.forMatching(raw)
            val hits = matcher.scan(text)
            val bySev = hits.groupBy { it.severity.name }.mapValues { it.value.size }
            val infoRuleIds = hits.filter { it.severity == Severity.Info }
                .map { it.ruleId }.distinct().sorted()
            val infoTexts = hits.filter { it.severity == Severity.Info }
                .map { it.matchedText }.distinct().sorted()
            println("--- ${m.mentorName} ---")
            println("  note:       ${m.note}")
            println("  ocr chars:  ${raw.length} (normalized ${text.length})")
            println("  hits:       ${hits.size}")
            println("  bySeverity: V=${bySev["Violation"] ?: 0} " +
                "W=${bySev["Warning"] ?: 0} I=${bySev["Info"] ?: 0}")
            println("  infoRules:  $infoRuleIds")
            println("  infoTexts:  $infoTexts")
        }
        println("===== real-OCR severity distribution (audit66 baselines) END =====")
    }

    /**
     * Contract pin A(2026-08-31 实证):5 张导师图类别在真实 OCR 下 Info 全部 = 0。
     *
     * 这不是 bug,而是 Info 规则触发表面的**自然分布**:
     *   - 5 张全是非医疗 / 非化妆品 / 非烟酒 / 非母婴类广告
     *     (种子 / 大闸蟹 / 避孕套 / 按摩 / 紫玉米花青素)
     *   - Info 规则仅在广告内文**自报家门**时才亮:
     *     - `med_art11_qualifications` / `med_art6_registerno` / `med_art6_producer`
     *       → 「三甲专家 / 国械注准 / 医疗器械」
     *     - `cosmetic_art23_*` → 「XK16-108 缺失 / 限期使用日期缺失」
     *     - `art10_minor` → 「未成年人 / 儿童 / 宝宝」
     *     - `art22_tob_alc` → 「白酒 / 啤酒」
     *     - `signage_art30_self_publish` → 「无证经营」
     *   - 日常广告(食品 / 零售 / 服务 / 种子) → Info 常态 0 是设计预期,
     *     KPI 第三格是「罕见但关键」的桶,而不是「常用」桶
     *
     * 如果未来有人误删了所有 Info 规则导致整条链断掉,「Contract pin B」
     * 旁证(医疗 / 烟酒广告上 Info 必须亮)会先于本测试 fail,提醒 reviewer
     * Info 规则被挖空,这里再由「all five = 0」的现象顺势浮上来确认问题。
     */
    @Test
    fun `real OCR — Info is intentionally 0 on all 5 mentor categories by design`() {
        val rules = loadRealRules()
        val matcher = AdSignageRuleMatcher(rules)
        for (m in mappings) {
            val text = TextNormalizer.forMatching(loadAuditOcr(m.filename))
            val infoCount = matcher.scan(text).count { it.severity == Severity.Info }
            assertTrue(
                "real OCR on '${m.mentorName}' should yield Info=0 by design " +
                    "(this image is not in medical/cosmetic/tobacco/alcohol/minor " +
                    "category), got Info=$infoCount",
                infoCount == 0,
            )
        }
    }

    /**
     * 旁证合约(Contract pin B):Info 桶在「该亮的广告」上必须真的亮起来。
     *
     * 数据来源(2026-08-31 audit66 扫描实证,见
     * AdSignageInfoEligibleSearchTest 一次性探索脚本输出):
     * 66 张 audit66 真值 OCR 全部跑一遍 matcher,**仅 2 张能亮 Info**:
     *   - #05 五常龙江医院-首选院长亲诊-医疗绝对化 → `med_art11_qualifications`
     *     OCR 文本含「三甲专家」类资质词。
     *   - #19 蜂胶胶囊整图-提高免疫力消炎止痛-保健食品 → `art10_minor`
     *     OCR 文本含「儿童」未成年相关词。
     * 其它 64 张(98%)Info = 0,即使它们在「语义上应该亮」(白酒 / 紧急避孕药 /
     * 化妆品 / 烟酒 / 其它医疗器械广告)—— 因为 PP-OCRv6_small 在密集小字或
     * 资质编号上漏识严重,关键词「国械注准 / XK16-108 缺失 / 限期使用日期缺失
     * / 白酒」在真值 OCR 里基本不出。
     *
     * 钉这 2 张 → 守住「Info 桶在有信号的图上仍然会亮」这一底线,跟 Contract
     * pin A(5 张日常广告 Info = 0 by design)成对:防「Info 桶永灭」与
     * 「Info 桶永亮」两个方向同时收紧。
     */
    private val infoEligibleMappings = listOf(
        Mapping(
            mentorName = "05_五常龙江医院(医疗资质)",
            filename = "05_五常龙江医院-首选院长亲诊-医疗绝对化.txt",
            note = "expected ruleId: ad_signage_med_art11_qualifications (1 Info hit confirmed)",
        ),
        Mapping(
            mentorName = "19_蜂胶胶囊整图(未成年相关)",
            filename = "19_蜂胶胶囊整图-提高免疫力消炎止痛-保健食品.txt",
            note = "expected ruleId: ad_signage_art10_minor (1 Info hit confirmed)",
        ),
    )

    @Test
    fun `real OCR — Info fires on at least one Info-eligible image from audit66`() {
        val rules = loadRealRules()
        val matcher = AdSignageRuleMatcher(rules)
        var anyInfo = false
        var bestInfoImage = ""
        var bestInfoCount = 0
        for (m in infoEligibleMappings) {
            val text = TextNormalizer.forMatching(loadAuditOcr(m.filename))
            val hits = matcher.scan(text).filter { it.severity == Severity.Info }
            if (hits.size > bestInfoCount) {
                bestInfoCount = hits.size
                bestInfoImage = m.mentorName
            }
            if (hits.isNotEmpty()) anyInfo = true
            println("[Info-eligible] ${m.mentorName}: ${hits.size} Info hits " +
                "(${hits.map { it.ruleId }.distinct()})")
        }
        println("Best Info image: $bestInfoImage (Info=$bestInfoCount)")
        assertTrue(
            "At least one Info-eligible image must fire Info on real OCR " +
                "(best so far: $bestInfoImage with Info=$bestInfoCount); " +
                "if all 0, the Info bucket has been drained — keywords drifted " +
                "or rules were removed",
            anyInfo,
        )
    }
}