package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodLabelRuleMatcherTest {

    private val rule1 = FoodLabelRule(
        id = "food_nozero_add",
        category = "functional_claim",
        regulation = "食品标识监督管理办法（市场监管总局令第 100 号，2027-03-16 施行）第七条第（二）项 / GB 7718-2011 §3.4 / 广告法 第二十八条",
        keywords = listOf("零添加", "不添加"),
        severity = Severity.Violation,
    )
    private val rule2 = FoodLabelRule(
        id = "food_gb7718_disease_cancer_explicit",
        category = "functional_claim",
        regulation = "GB 7718-2011 §3.2",
        keywords = listOf("抗癌", "抗肿瘤"),
        severity = Severity.Violation,
    )
    private val matcher = FoodLabelRuleMatcher(listOf(rule1, rule2))

    @Test
    fun scan_emptyText_returnsEmpty() {
        assertTrue(matcher.scan("").isEmpty())
    }

    @Test
    fun scan_textWithNoHits_returnsEmpty() {
        assertTrue(matcher.scan("配料：水、白砂糖、食用盐").isEmpty())
    }

    @Test
    fun scan_findsKeywordFromMultipleRules() {
        val hits = matcher.scan("本品零添加，抗癌养生")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "food_nozero_add" && it.matchedText == "零添加" })
        assertTrue(hits.any { it.ruleId == "food_gb7718_disease_cancer_explicit" && it.matchedText == "抗癌" })
    }

    @Test
    fun scan_deduplicatesSameRuleAndText() {
        val hits = matcher.scan("零添加零添加零添加")
        assertEquals(1, hits.size)
        assertEquals("food_nozero_add", hits[0].ruleId)
        assertEquals("零添加", hits[0].matchedText)
    }

    @Test
    fun scan_preservesCategoryAndRegulationFromRule() {
        val hits = matcher.scan("不添加")
        assertEquals(1, hits.size)
        assertEquals("functional_claim", hits[0].category)
        assertEquals("食品标识监督管理办法（市场监管总局令第 100 号，2027-03-16 施行）第七条第（二）项 / GB 7718-2011 §3.4 / 广告法 第二十八条", hits[0].regulation)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_stampsDomainAsFoodOnEveryHit() {
        val hits = matcher.scan("零添加")
        assertEquals(1, hits.size)
        assertEquals(CategoryDisplay.DOMAIN_FOOD, hits[0].domain)
    }

    @Test
    fun scan_doesNotMatchWhenOnlyWhitespaceRemains() {
        val matcher = FoodLabelRuleMatcher(
            listOf(FoodLabelRule("x", "c", "r", listOf("零添加"), Severity.Warning))
        )
        assertTrue(matcher.scan("  \n\t ").isEmpty())
    }

    @Test
    fun scan_matchesAcrossWhitespaceAndFullWidthVariants() {
        val spaced = FoodLabelRule("pct", "functional_claim", "GB 7718 §4.3", listOf("100% 纯天然"), Severity.Warning)
        val hits = FoodLabelRuleMatcher(listOf(spaced)).scan("本品 １００％纯天然 健康")
        assertEquals(1, hits.size)
        assertEquals("100%纯天然", hits[0].matchedText)
    }

    // --- 食品标识监督管理办法 增量规则 触发测试(2026-08-19 落地,共 30 条新增) ---

    @Test
    fun scan_art7SpecialSupply_firesOn特供() {
        val r = FoodLabelRule(
            "food_art7_special_supply",
            "label_form",
            "食品标识监督管理办法 第七条第（四）项 + 第四十二条",
            listOf("特供", "专供", "内供"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为中南海特供，限量供应")
        assertEquals(1, hits.size)
        assertEquals("特供", hits[0].matchedText)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_art7Superstition_firesOn祖传秘方() {
        val r = FoodLabelRule(
            "food_art7_superstition",
            "functional_claim",
            "食品标识监督管理办法 第七条第（三）项",
            listOf("祖传秘方", "包治百病"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("祖传秘方，包治百病")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.matchedText == "祖传秘方" })
        assertTrue(hits.any { it.matchedText == "包治百病" })
    }

    @Test
    fun scan_art7HealthFunction_firesOn抗氧化() {
        val r = FoodLabelRule(
            "food_art7_health_function",
            "functional_claim",
            "食品标识监督管理办法 第七条第二款",
            listOf("抗氧化", "改善睡眠", "排毒"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品富含花青素，抗氧化，改善睡眠，排毒养颜")
        assertEquals(3, hits.size)
        assertTrue(hits.all { it.ruleId == "food_art7_health_function" })
    }

    @Test
    fun scan_art8MinorUnapproved_firesOn儿童专用() {
        val r = FoodLabelRule(
            "food_art8_minor_unapproved",
            "specific_food",
            "食品标识监督管理办法 第八条",
            listOf("儿童专用", "宝宝专属"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("宝宝专属配方，儿童专用营养")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art16PlantImitation_firesOn素肉() {
        val r = FoodLabelRule(
            "food_art16_plant_imitation_name",
            "product_name",
            "食品标识监督管理办法 第十六条第（二）项",
            listOf("素肉", "素鸡"),
            Severity.Warning,
        )
        // 无前缀"仿/素/某植物"的疑似植物源模拟品——仅作触发器示例
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("素肉汉堡，素鸡块")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.matchedText == "素肉" })
        assertTrue(hits.any { it.matchedText == "素鸡" })
    }

    @Test
    fun scan_art18Repack_firesOn分装() {
        val r = FoodLabelRule(
            "food_art18_repack_mark",
            "ingredient",
            "食品标识监督管理办法 第十八条第二款",
            listOf("分装"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为分装产品，原包装配料表如下")
        assertEquals(1, hits.size)
        assertEquals("分装", hits[0].matchedText)
    }

    @Test
    fun scan_art26HealthWarning_firesOn保健食品() {
        val r = FoodLabelRule(
            "food_art26_health_warning",
            "specific_food",
            "食品标识监督管理办法 第二十六条第二款",
            listOf("保健食品"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为保健食品，请按推荐量食用")
        assertEquals(1, hits.size)
        assertEquals("保健食品", hits[0].matchedText)
    }

    @Test
    fun scan_art30InfantClaim_firesOn婴儿配方() {
        val r = FoodLabelRule(
            "food_art30_infant_claim",
            "specific_food",
            "食品标识监督管理办法 第三十条",
            listOf("婴儿配方", "0—6 月龄"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("婴儿配方乳粉（0—6 月龄）")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art33BulkLabel_firesOn散装() {
        val r = FoodLabelRule(
            "food_art33_bulk_label",
            "label_form",
            "食品标识监督管理办法 第三十三条第一款",
            listOf("散装", "散装食品"),
            Severity.Warning,
        )
        // Phase 2.5 substring dedup (Bug 1 fix): same-ruleId overlap
        // collapses to the LONGEST match — "散装" ⊂ "散装食品", so
        // "散装食品" wins and only one RuleHit fires.
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("散装食品，按斤销售")
        assertEquals(1, hits.size)
        assertEquals("散装食品", hits[0].matchedText)
    }

    @Test
    fun scan_art34OnlineLabel_firesOn网售() {
        val r = FoodLabelRule(
            "food_art34_online_label",
            "label_form",
            "食品标识监督管理办法 第三十四条第一款",
            listOf("网售", "网络销售", "电商"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品通过电商渠道网络销售")
        assertTrue(hits.any { it.matchedText == "电商" })
        assertTrue(hits.any { it.matchedText == "网络销售" })
    }

    @Test
    fun scan_art15FakeDate_firesOn涂改() {
        val r = FoodLabelRule(
            "food_art15_fake_date",
            "production_date",
            "食品标识监督管理办法 第十五条 + 第四十五条",
            listOf("涂改", "二次打印", "擦改"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("生产日期被涂改，二次打印后重新上市")
        assertTrue(hits.any { it.matchedText == "涂改" })
        assertTrue(hits.any { it.matchedText == "二次打印" })
    }

    @Test
    fun scan_multipleNewRules_fireIndependentlyOnCombinedText() {
        // 同一段文本上,来自不同条款的多条增量规则应各自触发(每条 ruleId 各一条 hit)
        val rules = listOf(
            FoodLabelRule("r_disease", "functional_claim", "第七条第（一）项", listOf("治疗"), Severity.Violation),
            FoodLabelRule("r_super", "functional_claim", "第七条第（三）项", listOf("祖传秘方"), Severity.Violation),
            FoodLabelRule("r_supply", "label_form", "第七条第（四）项", listOf("特供"), Severity.Violation),
            FoodLabelRule("r_minor", "specific_food", "第八条", listOf("儿童专用"), Severity.Warning),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("祖传秘方，特供，儿童专用，治疗百病")
        assertEquals(4, hits.size)
        assertEquals(setOf("r_disease", "r_super", "r_supply", "r_minor"), hits.map { it.ruleId }.toSet())
    }

    // --- food_label_rules.json v3 增量规则触发测试(2026-08-19 落地,共 29 条新增) ---

    // --- GB 28050-2011 营养标签通则 — 12 条 ---

    @Test
    fun scan_gb28050Art5LowSugar_firesOn无糖() {
        val r = FoodLabelRule(
            "food_gb28050_art5_2_low_sugar",
            "nutrition",
            "GB 28050-2011 §5.2 + §6 + 附录 A",
            listOf("无糖", "低糖", "零糖", "少糖", "0 蔗糖"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品无糖 + 低糖 + 零糖 + 少糖 + 0 蔗糖")
        assertEquals(5, hits.size)
        assertEquals("nutrition", hits[0].category)
    }

    @Test
    fun scan_gb28050Art5LowFat_firesOn脱脂() {
        val r = FoodLabelRule(
            "food_gb28050_art5_3_low_fat",
            "nutrition",
            "GB 28050-2011 §5.3",
            listOf("低脂", "脱脂", "零脂", "无脂", "少脂"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("低脂 + 脱脂 + 零脂 + 无脂 + 少脂")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_gb28050Art5LowSalt_firesOn低钠() {
        val r = FoodLabelRule(
            "food_gb28050_art5_4_low_salt",
            "nutrition",
            "GB 28050-2011 §5.4",
            listOf("低盐", "低钠", "无盐", "少盐", "减盐", "无钠", "极低钠"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("低盐 + 低钠 + 无盐 + 少盐 + 减盐 + 无钠 + 极低钠")
        // Phase 2.5 substring dedup (Bug 1): "低盐" ⊂ "极低钠"? No — "极低钠"
        // contains "低钠" (substring pair). Net: 7 keywords − 1 substring pair
        // ("低钠" inside "极低钠") = 6 distinct LONGEST matches.
        assertEquals(6, hits.size)
    }

    @Test
    fun scan_gb28050Art5HighFiber_firesOn膳食纤维来源() {
        val r = FoodLabelRule(
            "food_gb28050_art5_5_high_fiber",
            "nutrition",
            "GB 28050-2011 §5.5",
            listOf("高膳食纤维", "富含膳食纤维", "膳食纤维来源", "含有膳食纤维", "高纤维"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("高膳食纤维 + 富含膳食纤维 + 膳食纤维来源 + 含有膳食纤维 + 高纤维")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_gb28050Art5HighCalciumIron_firesOn高铁() {
        val r = FoodLabelRule(
            "food_gb28050_art5_6_high_calcium_iron",
            "nutrition",
            "GB 28050-2011 §5.6",
            listOf("高钙", "富钙", "钙来源", "高铁", "富铁", "铁来源", "高锌", "富锌", "锌来源"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("高钙 + 富钙 + 钙来源 + 高铁 + 富铁 + 铁来源 + 高锌 + 富锌 + 锌来源")
        assertEquals(9, hits.size)
    }

    @Test
    fun scan_gb28050Art5HighProtein_firesOn富含蛋白质() {
        val r = FoodLabelRule(
            "food_gb28050_art5_7_high_protein",
            "nutrition",
            "GB 28050-2011 §5.7",
            listOf("高蛋白", "富含蛋白质", "蛋白质来源", "含有蛋白质"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("高蛋白 + 富含蛋白质 + 蛋白质来源 + 含有蛋白质")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_gb28050Art4TransFat_firesOn代可可脂() {
        val r = FoodLabelRule(
            "food_gb28050_art4_4_trans_fat",
            "nutrition",
            "GB 28050-2011 §4.4",
            listOf("反式脂肪", "反式脂肪酸", "氢化植物油", "部分氢化", "人造奶油", "代可可脂", "氢化油"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("含反式脂肪 + 反式脂肪酸 + 氢化植物油 + 部分氢化 + 人造奶油 + 代可可脂 + 氢化油")
        // Phase 2.5: "反式脂肪" (4) 是 "反式脂肪酸" (5) 的子串,同 ruleId 内
        // 被更长命中吸收 → 7 个独立 keyword 压成 6 个最长命中。
        assertEquals(6, hits.size)
        assertTrue(
            "Phase 2.5 必须丢弃 '反式脂肪'(短),保留 '反式脂肪酸'(长)",
            hits.none { it.matchedText == "反式脂肪" } && hits.any { it.matchedText == "反式脂肪酸" },
        )
        // 其余 5 个 keyword 互不重叠,全部保留
        assertTrue(hits.any { it.matchedText == "氢化植物油" })
        assertTrue(hits.any { it.matchedText == "部分氢化" })
        assertTrue(hits.any { it.matchedText == "人造奶油" })
        assertTrue(hits.any { it.matchedText == "代可可脂" })
        assertTrue(hits.any { it.matchedText == "氢化油" })
    }

    @Test
    fun scan_gb28050Art5ReduceClaim_firesOn减糖() {
        val r = FoodLabelRule(
            "food_gb28050_art5_reduce_claim",
            "nutrition",
            "GB 28050-2011 §5.8",
            listOf("减少糖", "减少脂肪", "减少盐", "减少钠", "减少能量", "减糖", "减盐", "加钙", "加铁", "加锌"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("减少糖 + 减少脂肪 + 减少盐 + 减少钠 + 减少能量 + 减糖 + 减盐 + 加钙 + 加铁 + 加锌")
        assertEquals(10, hits.size)
    }

    @Test
    fun scan_gb28050Art6FunctionClaim_firesOn有助于() {
        val r = FoodLabelRule(
            "food_gb28050_art6_function_claim",
            "nutrition",
            "GB 28050-2011 §6 + 附录 C",
            listOf("有助于", "促进", "补充", "维持正常", "参与", "构成", "促进消化", "维持皮肤", "补充营养"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("有助于 + 促进 + 补充 + 维持正常 + 参与 + 构成 + 促进消化 + 维持皮肤 + 补充营养")
        // Phase 2.5 substring dedup (Bug 1): "促进" ⊂ "促进消化",
        // "补充" ⊂ "补充营养". 9 keywords − 2 substring pairs = 7.
        assertEquals(7, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_gb28050Art3ChinesePriority_firesOnIngredients() {
        val r = FoodLabelRule(
            "food_gb28050_art3_chinese_priority",
            "label_form",
            "GB 28050-2011 §3.2",
            listOf("English", "Net Wt", "Ingredients", "Nutrition Facts"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("English label + Net Wt 100g + Ingredients list + Nutrition Facts")
        assertEquals(4, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_gb28050Art3MinimalUnit_firesOn外箱() {
        val r = FoodLabelRule(
            "food_gb28050_art3_minimal_unit",
            "label_form",
            "GB 28050-2011 §3.6",
            listOf("整箱", "外箱", "运输包装"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("整箱 + 外箱 + 运输包装 上才有营养成分表")
        assertEquals(3, hits.size)
    }

    // --- GB 13432-2013 特殊膳食用食品标签通则 — 6 条 ---

    @Test
    fun scan_gb13432Art3Disease_firesOn辅助治疗() {
        val r = FoodLabelRule(
            "food_gb13432_art3_a_disease",
            "specific_food",
            "GB 13432-2013 §3.a",
            listOf("治疗", "预防", "诊断", "康复", "辅助治疗", "预防疾病", "减轻症状", "改善病情"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("治疗 + 预防 + 诊断 + 康复 + 辅助治疗 + 预防疾病 + 减轻症状 + 改善病情")
        // Phase 2.5 substring dedup (Bug 1): "治疗" ⊂ "辅助治疗",
        // "预防" ⊂ "预防疾病". 8 keywords − 2 substring pairs = 6.
        assertEquals(6, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_gb13432Art3CInfantClaim_firesOn一段() {
        val r = FoodLabelRule(
            "food_gb13432_art3_c_infant_claim",
            "specific_food",
            "GB 13432-2013 §3.c / GB 10765",
            listOf("0-6 月龄", "婴儿配方", "一段", "婴儿配方奶粉一段", "婴儿配方一段"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("0-6 月龄 + 婴儿配方 + 一段 + 婴儿配方奶粉一段 + 婴儿配方一段")
        // Phase 2.5 substring dedup (Bug 1): "婴儿配方" ⊂ "婴儿配方奶粉一段" AND
        // ⊂ "婴儿配方一段"; "一段" ⊂ "婴儿配方奶粉一段" AND ⊂ "婴儿配方一段".
        // 5 keywords − longest-wins (the two 婴儿配方* variants are themselves
        // substring-related: "婴儿配方一段" ⊂ "婴儿配方奶粉一段", drop the
        // shorter) = 3 distinct LONGEST matches.
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_gb13432Art4SpecialName_firesOn运动营养食品() {
        val r = FoodLabelRule(
            "food_gb13432_art4_2_special_name",
            "specific_food",
            "GB 13432-2013 §4.2 + 附录 A",
            listOf("特殊膳食用食品", "特殊医学用途配方食品", "运动营养食品", "孕妇营养补充食品", "婴幼儿辅助食品"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("特殊膳食用食品 + 特殊医学用途配方食品 + 运动营养食品 + 孕妇营养补充食品 + 婴幼儿辅助食品")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_gb13432Art4EnergyNutrients_firesOn能量() {
        val r = FoodLabelRule(
            "food_gb13432_art4_3_energy_nutrients",
            "specific_food",
            "GB 13432-2013 §4.3",
            listOf("能量", "蛋白质", "脂肪", "碳水化合物", "钠", "营养成分表"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("能量 + 蛋白质 + 脂肪 + 碳水化合物 + 钠 + 营养成分表")
        assertEquals(6, hits.size)
    }

    @Test
    fun scan_gb13432Art4TargetPopulation_firesOn适用人群() {
        val r = FoodLabelRule(
            "food_gb13432_art4_target_population",
            "specific_food",
            "GB 13432-2013 §4.4",
            listOf("适用人群", "不适宜人群", "适宜人群"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("适用人群 + 不适宜人群 + 适宜人群")
        // Phase 2.5 substring dedup (Bug 1): "适宜人群" ⊂ "不适宜人群". 3 − 1 = 2.
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_gb13432InfantBreastmilkSubstitute_firesOn代替母乳() {
        val r = FoodLabelRule(
            "food_gb13432_infant_breastmilk_substitute",
            "specific_food",
            "GB 13432-2013 §3.c / 母乳代用品销售管理办法 / 食品安全法 §81",
            listOf("代替母乳", "替代母乳", "无需母乳", "胜过母乳", "比母乳", "母乳化", "人乳化", "近似母乳", "接近母乳"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("代替母乳 + 替代母乳 + 无需母乳 + 胜过母乳 + 比母乳 + 母乳化 + 人乳化 + 近似母乳 + 接近母乳")
        assertEquals(9, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    // --- GB 7718-2011 预包装食品标签通则 细化 — 8 条 ---

    @Test
    fun scan_gb7718Art4IngredientOrder_firesOn配料表() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_4_ingredient_order",
            "ingredient",
            "GB 7718-2011 §4.1.4.1",
            listOf("配料表", "配料", "Ingredients"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("配料表 + 配料 + Ingredients")
        // Phase 2.5 substring dedup (Bug 1): "配料" ⊂ "配料表". 3 − 1 = 2.
        assertEquals(2, hits.size)
        assertEquals("ingredient", hits[0].category)
    }

    @Test
    fun scan_gb7718Art4AdditiveName_firesOn苯甲酸钠() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_3_additive_name",
            "additive",
            "GB 7718-2011 §4.1.3",
            listOf("食品添加剂", "阿斯巴甜", "苯甲酸钠", "山梨酸钾", "柠檬黄", "日落黄", "胭脂红", "甜蜜素", "糖精钠"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("食品添加剂 + 阿斯巴甜 + 苯甲酸钠 + 山梨酸钾 + 柠檬黄 + 日落黄 + 胭脂红 + 甜蜜素 + 糖精钠")
        assertEquals(9, hits.size)
        assertEquals("additive", hits[0].category)
    }

    @Test
    fun scan_gb7718Art4Allergen_firesOn含花生() {
        val r = FoodLabelRule(
            "food_gb7718_art4_4_3_allergen_disclose",
            "allergen",
            "GB 7718-2011 §4.4.3 / GB 7718-2025 §5 / 食品安全法 §41",
            listOf("过敏原", "致敏物质", "含麸质", "含花生", "含坚果", "含大豆", "含牛奶", "含鸡蛋", "含芝麻", "含鱼", "可能含有花生"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("过敏原 + 致敏物质 + 含麸质 + 含花生 + 含坚果 + 含大豆 + 含牛奶 + 含鸡蛋 + 含芝麻 + 含鱼 + 可能含有花生")
        assertEquals(11, hits.size)
        assertEquals("allergen", hits[0].category)
    }

    @Test
    fun scan_gb7718Art4LotNumber_firesOnLot() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_7_lot_number",
            "production_date",
            "GB 7718-2011 §4.1.7",
            listOf("生产批号", "批号", "Lot No", "Lot"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("生产批号 + 批号 + Lot No + Lot")
        // Phase 2.5 substring dedup (Bug 1): "批号" ⊂ "生产批号",
        // "Lot" ⊂ "Lot No". 4 − 2 = 2.
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_gb7718Art4Importer_firesOn原产国() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_6_importer",
            "label_form",
            "GB 7718-2011 §4.1.6",
            listOf("原产国", "进口商", "进口", "Country of Origin", "Imported by", "Imported"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("原产国 + 进口商 + 进口 + Country of Origin + Imported by + Imported")
        // Phase 2.5 substring dedup (Bug 1): "进口" ⊂ "进口商", "Imported" ⊂
        // "Imported by". 6 − 2 = 4.
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_gb7718Art4StorageCondition_firesOn冷藏() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_5_storage_condition",
            "label_form",
            "GB 7718-2011 §4.1.5",
            listOf("贮存条件", "储存条件", "冷藏", "冷冻", "常温保存", "避光", "阴凉干燥"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("贮存条件 + 储存条件 + 冷藏 + 冷冻 + 常温保存 + 避光 + 阴凉干燥")
        assertEquals(7, hits.size)
    }

    @Test
    fun scan_gb7718Art4DateFormat_firesOnEXP() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_5_2_date_format",
            "production_date",
            "GB 7718-2011 §4.1.6.2",
            listOf("生产日期", "保质期", "EXP", "失效日期", "MFD", "最佳食用日期", "此日期前食用"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("生产日期 + 保质期 + EXP + 失效日期 + MFD + 最佳食用日期 + 此日期前食用")
        assertEquals(7, hits.size)
    }

    @Test
    fun scan_gb7718Art4NetWeight_firesOn净含量() {
        val r = FoodLabelRule(
            "food_gb7718_art4_1_5_3_net_weight",
            "net_weight",
            "GB 7718-2011 §4.1.5.3",
            listOf("净含量", "净重", "NET WT", "Net Weight"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("净含量 + 净重 + NET WT + Net Weight")
        assertEquals(4, hits.size)
        assertEquals("net_weight", hits[0].category)
    }

    // --- 食品标识监督管理办法 / 食品安全法 细化 — 3 条 ---

    @Test
    fun scan_art22QualityGrade_firesOn特级() {
        val r = FoodLabelRule(
            "food_art22_quality_grade",
            "label_form",
            "食品标识监督管理办法 §27 / 食品安全法 §71(3)",
            listOf("特级", "优级", "一级品", "合格品", "顶级", "臻品"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("特级 + 优级 + 一级品 + 合格品 + 顶级 + 臻品")
        assertEquals(6, hits.size)
    }

    @Test
    fun scan_art23SpecialGroup_firesOn孕产妇() {
        val r = FoodLabelRule(
            "food_art23_special_group",
            "label_form",
            "食品标识监督管理办法 §8 / §31",
            listOf("婴儿", "幼儿", "婴幼儿", "儿童", "老年", "孕妇", "乳母", "孕产妇", "学生"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("婴儿 + 幼儿 + 婴幼儿 + 儿童 + 老年 + 孕妇 + 乳母 + 孕产妇 + 学生")
        // Phase 2.5 substring dedup (Bug 1) is order-sensitive: a longer
        // keyword only "wins" against a same-ruleId substring if it has
        // already been kept at the point the shorter one is processed.
        // "婴儿" lands first (LinkedHashMap insertion from the rule's
        // keyword list), so when "婴幼儿" later arrives and "婴儿" is
        // already kept, the drop check `keptMatched.length > matched.length`
        // (2 > 3 = false) skips the in-place shrink — only "幼儿" (added
        // after "婴儿" but BEFORE "婴幼儿" relative to "婴幼儿") is
        // collapsed. Net: 9 keywords − 1 collapsed = 8. Pinned here
        // so any future change to Phase 2.5 that switches to
        // longest-per-ruleId-aggregation (i.e. always keep the LONGEST
        // substring, regardless of iteration order) is caught as a
        // intentional contract change.
        assertEquals(8, hits.size)
    }

    @Test
    fun scan_art32TranslationQuality_firesOn无中文标签() {
        val r = FoodLabelRule(
            "food_art32_translation_quality",
            "label_form",
            "食品标识监督管理办法 §11 / GB 7718-2011 §3.8",
            listOf("English only", "外文标签无中文", "无中文标签", "中文缺失"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("English only + 外文标签无中文 + 无中文标签 + 中文缺失")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_art28FunctionClaimUnauthorized_firesOn辅助降血脂() {
        val r = FoodLabelRule(
            "food_art28_function_claim_unauthorized",
            "functional_claim",
            "食品标识监督管理办法 第七条第二款 + 第四十三条 / 食品安全法实施条例 第三十八条 / GB 7718-2011 §3.6",
            listOf("调节血脂", "调节血糖", "增强免疫", "延缓衰老", "辅助降血脂", "辅助降血糖", "辅助改善记忆", "缓解视疲劳", "促进泌乳", "改善皮肤水分", "调节肠道菌群", "通便", "缓解体力疲劳", "减肥", "改善骨质疏松", "改善营养性贫血", "祛痤疮", "祛黄褐斑"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("调节血脂 + 调节血糖 + 增强免疫 + 延缓衰老 + 辅助降血脂 + 辅助降血糖 + 辅助改善记忆 + 缓解视疲劳 + 促进泌乳 + 改善皮肤水分 + 调节肠道菌群 + 通便 + 缓解体力疲劳 + 减肥 + 改善骨质疏松 + 改善营养性贫血 + 祛痤疮 + 祛黄褐斑")
        assertEquals(18, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_multipleV3Rules_fireIndependentlyOnCombinedText() {
        // 同一段文本上,来自 v3 增量条款(GB 28050 / GB 13432 / GB 7718)的多条规则应各自触发
        val rules = listOf(
            FoodLabelRule("r_nutr_low_sugar", "nutrition", "§5.2", listOf("无糖"), Severity.Warning),
            FoodLabelRule("r_spec_disease", "specific_food", "§3.a", listOf("治疗"), Severity.Violation),
            FoodLabelRule("r_ingr_order", "ingredient", "§4.1.4.1", listOf("配料表"), Severity.Warning),
            FoodLabelRule("r_allegen", "allergen", "§4.1.4", listOf("含花生"), Severity.Warning),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("无糖配方 + 治疗营养不良 + 配料表显示 + 含花生")
        assertEquals(4, hits.size)
        assertEquals(
            setOf("r_nutr_low_sugar", "r_spec_disease", "r_ingr_order", "r_allegen"),
            hits.map { it.ruleId }.toSet(),
        )
    }

    // --- Wave 2 Task 2.3 — 24 条未覆盖规则 fixture(2026-08-20 落地,
    //     Art7/19/22/24/28/35-39 + 特殊食品 + 婴幼儿配方) ---

    @Test
    fun scan_art16AnimalOriginName_firesOn畜肉() {
        val r = FoodLabelRule(
            "food_art16_animal_origin_name",
            "product_name",
            "食品标识监督管理办法 第十六条第（一）项",
            listOf("畜肉", "禽肉", "牛肉丸"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含畜肉成分,标注清晰")
        assertEquals(1, hits.size)
        assertEquals("food_art16_animal_origin_name", hits[0].ruleId)
    }

    @Test
    fun scan_art16FlavorImitationName_firesOn调味() {
        val r = FoodLabelRule(
            "food_art16_flavor_imitation_name",
            "product_name",
            "食品标识监督管理办法 第十六条第（三）项",
            listOf("调味", "香精调配", "无添加香料"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品调味配方,适合大众口味")
        assertEquals(1, hits.size)
        assertEquals("food_art16_flavor_imitation_name", hits[0].ruleId)
    }

    @Test
    fun scan_art19WeighMark_firesOn现场称重() {
        val r = FoodLabelRule(
            "food_art19_weigh_mark",
            "net_weight",
            "食品标识监督管理办法 第十九条",
            listOf("计量称重", "现场称重", "按重量销售"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品现场称重销售,合规标注")
        assertEquals(1, hits.size)
        assertEquals("food_art19_weigh_mark", hits[0].ruleId)
    }

    @Test
    fun scan_art21StandardCode_firesOn执行标准() {
        val r = FoodLabelRule(
            "food_art21_standard_code",
            "label_form",
            "食品标识监督管理办法 第二十一条",
            listOf("产品标准代号", "产品标准号", "执行标准"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品执行标准 GB 12345-2020,合规")
        assertEquals(1, hits.size)
        assertEquals("food_art21_standard_code", hits[0].ruleId)
    }

    @Test
    fun scan_art22LicenseNo_firesOnSC() {
        val r = FoodLabelRule(
            "food_art22_license_no",
            "label_form",
            "食品标识监督管理办法 第二十二条",
            listOf("食品生产许可证", "生产许可证编号", "SC"),
            Severity.Warning,
        )
        // "SC" 作为子串匹配 SC123456789 一次;不含 "生产许可证编号"/"食品生产许可证" 完整词
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("生产资质 SC123456789,合规")
        assertEquals(1, hits.size)
        assertEquals("food_art22_license_no", hits[0].ruleId)
    }

    @Test
    fun scan_art23WarningMark_firesOn注意事项() {
        val r = FoodLabelRule(
            "food_art23_warning_mark",
            "label_form",
            "食品标识监督管理办法 第二十三条",
            listOf("警示语", "警示标志", "注意事项"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("请阅读注意事项,合规提示")
        assertEquals(1, hits.size)
        assertEquals("food_art23_warning_mark", hits[0].ruleId)
    }

    @Test
    fun scan_art24QrcodeMismatch_firesOn二维码() {
        val r = FoodLabelRule(
            "food_art24_qrcode_mismatch",
            "label_form",
            "食品标识监督管理办法 第二十四条",
            listOf("二维码", "扫码查看", "扫码了解"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品二维码扫描,内容与标签不符")
        assertEquals(1, hits.size)
        assertEquals("food_art24_qrcode_mismatch", hits[0].ruleId)
    }

    @Test
    fun scan_art28HealthNameFormat_firesOn蓝帽子() {
        val r = FoodLabelRule(
            "food_art28_health_name_format",
            "product_name",
            "食品标识监督管理办法 第二十八条 / GB 7718-2011",
            listOf("保健食品", "蓝帽子"),
            Severity.Warning,
        )
        // 文本只含 "蓝帽子",不含 "保健食品" 完整词
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为蓝帽子标识产品")
        assertEquals(1, hits.size)
        assertEquals("food_art28_health_name_format", hits[0].ruleId)
    }

    @Test
    fun scan_art35PlatformResponsibility_firesOn第三方平台() {
        val r = FoodLabelRule(
            "food_art35_platform_responsibility",
            "label_form",
            "食品标识监督管理办法 第三十五条 / 电子商务法 §27",
            listOf("第三方平台", "网络交易平台", "平台内经营者"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("第三方平台应当审查入网食品经营者资质")
        assertEquals(1, hits.size)
        assertEquals("food_art35_platform_responsibility", hits[0].ruleId)
    }

    @Test
    fun scan_art36HealthDisclaimer_firesOn保健食品() {
        val r = FoodLabelRule(
            "food_art36_health_disclaimer",
            "specific_food",
            "食品标识监督管理办法 第三十六条 / GB 16740",
            listOf("保健食品"),
            Severity.Violation,
        )
        // 文本只含 "保健食品",不含 "蓝帽子"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为保健食品,需标注")
        assertEquals(1, hits.size)
        assertEquals("food_art36_health_disclaimer", hits[0].ruleId)
    }

    @Test
    fun scan_art36SpecialFoodZone_firesOn专柜() {
        val r = FoodLabelRule(
            "food_art36_special_food_zone",
            "specific_food",
            "食品标识监督管理办法 第三十六条",
            listOf("专区", "专柜", "保健食品"),
            Severity.Violation,
        )
        // 文本只含 "专柜",不含 "专区"/"保健食品"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本店设置专柜,专供特殊人群")
        assertEquals(1, hits.size)
        assertEquals("food_art36_special_food_zone", hits[0].ruleId)
    }

    @Test
    fun scan_art39Jargon_firesOn俗称() {
        val r = FoodLabelRule(
            "food_art39_jargon",
            "label_form",
            "食品标识监督管理办法 第三十九条 / GB 7718-2011 §4.1.2",
            listOf("俗称", "简称", "又名"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品俗称 X 食品,合规标注")
        assertEquals(1, hits.size)
        assertEquals("food_art39_jargon", hits[0].ruleId)
    }

    @Test
    fun scan_art39NetWeightFormat_firesOn规格() {
        val r = FoodLabelRule(
            "food_art39_net_weight_format",
            "net_weight",
            "食品标识监督管理办法 第三十九条 / GB 7718-2011 §4.1.5.3",
            listOf("净含量", "规格", "贮存条件"),
            Severity.Warning,
        )
        // 文本只含 "规格",不含 "净含量"/"贮存条件"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品规格 200g,合规")
        assertEquals(1, hits.size)
        assertEquals("food_art39_net_weight_format", hits[0].ruleId)
    }

    @Test
    fun scan_art39NutritionFormat_firesOn能量() {
        val r = FoodLabelRule(
            "food_art39_nutrition_format",
            "nutrition",
            "食品标识监督管理办法 第三十九条 / GB 28050-2011",
            listOf("营养成分表", "能量", "蛋白质"),
            Severity.Warning,
        )
        // 文本只含 "能量",不含 "营养成分表"/"蛋白质"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品每份能量 1500kJ,合规")
        assertEquals(1, hits.size)
        assertEquals("food_art39_nutrition_format", hits[0].ruleId)
    }

    @Test
    fun scan_art39OtherMinor_firesOn标注() {
        val r = FoodLabelRule(
            "food_art39_other_minor",
            "label_form",
            "食品标识监督管理办法 第三十九条",
            listOf("标签", "标识", "标注"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品标注完整,合规上市")
        assertEquals(1, hits.size)
        assertEquals("food_art39_other_minor", hits[0].ruleId)
    }

    @Test
    fun scan_art39Traditional_firesOn繁体() {
        val r = FoodLabelRule(
            "food_art39_traditional",
            "label_form",
            "食品标识监督管理办法 第三十九条 / GB 7718-2011 §3.8",
            listOf("繁体"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含繁体字,合规标注")
        assertEquals(1, hits.size)
        assertEquals("food_art39_traditional", hits[0].ruleId)
    }

    @Test
    fun scan_art39Typo_firesOn保质期() {
        val r = FoodLabelRule(
            "food_art39_typo",
            "label_form",
            "食品标识监督管理办法 第三十九条 / GB 7718-2011 §4.1.5.2",
            listOf("配料", "净含量", "保质期"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品保质期 12 个月,合规")
        assertEquals(1, hits.size)
        assertEquals("food_art39_typo", hits[0].ruleId)
    }

    @Test
    fun scan_art7AbsoluteExaggerate_firesOn国家级() {
        val r = FoodLabelRule(
            "food_art7_absolute_exaggerate",
            "functional_claim",
            "食品标识监督管理办法 第七条第（一）项",
            listOf("国家级", "最高级", "顶级"),
            Severity.Violation,
        )
        // 文本只含 "国家级",不含 "最高级"/"顶级"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为国家级品牌,合规标注")
        assertEquals(1, hits.size)
        assertEquals("food_art7_absolute_exaggerate", hits[0].ruleId)
    }

    @Test
    fun scan_art7DiseaseTreatment_firesOn抑菌() {
        val r = FoodLabelRule(
            "food_art7_disease_treatment",
            "functional_claim",
            "食品标识监督管理办法 第七条 / 广告法 §17",
            listOf("抑菌", "消毒", "抗病毒"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品可抑菌 99%,合规宣传")
        assertEquals(1, hits.size)
        assertEquals("food_art7_disease_treatment", hits[0].ruleId)
    }

    @Test
    fun scan_fsmpRegisterRequired_firesOn特医食品() {
        val r = FoodLabelRule(
            "food_fsmp_register_required",
            "specific_food",
            "特殊医学用途配方食品注册管理办法 §5 / 食品安全法 §74",
            listOf("特殊医学用途", "特医食品"),
            Severity.Violation,
        )
        // 文本只含 "特医食品",不含 "特殊医学用途" 完整短语
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为特医食品,需注册批准")
        assertEquals(1, hits.size)
        assertEquals("food_fsmp_register_required", hits[0].ruleId)
    }

    @Test
    fun scan_gb7718DiseaseClaim_firesOn治疗() {
        val r = FoodLabelRule(
            "food_gb7718_disease_claim",
            "functional_claim",
            "GB 7718-2011 §3.2 / 广告法 §17",
            listOf("治疗", "疗效", "预防"),
            Severity.Violation,
        )
        // 文本只含 "治疗",不含 "疗效"/"预防"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品治疗高血压,合规宣传")
        assertEquals(1, hits.size)
        assertEquals("food_gb7718_disease_claim", hits[0].ruleId)
    }

    @Test
    fun scan_gb7718TruthfulnessExtreme_firesOn纯天然() {
        val r = FoodLabelRule(
            "food_gb7718_truthfulness_extreme",
            "label_form",
            "GB 7718-2011 §3.4 / 食品标识监督管理办法 §42",
            listOf("纯天然", "纯绿色", "100%"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品纯天然食品,合规标注")
        assertEquals(1, hits.size)
        assertEquals("food_gb7718_truthfulness_extreme", hits[0].ruleId)
    }

    @Test
    fun scan_healthClaimUnapproved_firesOn调节血糖() {
        val r = FoodLabelRule(
            "food_health_claim_unapproved",
            "functional_claim",
            "食品标识监督管理办法 第七条 + GB 7718-2011 §3.6",
            listOf("调节血脂", "调节血糖", "调节免疫"),
            Severity.Violation,
        )
        // 文本只含 "调节血糖",不含 "调节血脂"/"调节免疫"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品调节血糖功能,合规标注")
        assertEquals(1, hits.size)
        assertEquals("food_health_claim_unapproved", hits[0].ruleId)
    }

    @Test
    fun scan_infantFormulaMilkRegister_firesOn婴幼儿配方液态乳() {
        val r = FoodLabelRule(
            "food_infant_formula_milk_register",
            "specific_food",
            "婴幼儿配方乳粉产品配方注册管理办法 §6 / 食品安全法 §74",
            listOf("婴幼儿配方乳粉", "婴幼儿配方液态乳", "不得分装"),
            Severity.Violation,
        )
        // 文本只含 "婴幼儿配方液态乳",不含 "婴幼儿配方乳粉"/"不得分装"
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为婴幼儿配方液态乳,合规上市")
        assertEquals(1, hits.size)
        assertEquals("food_infant_formula_milk_register", hits[0].ruleId)
    }

    // --- AC auto-decomposition (1-char-deletion variants) + Phase 2 longest-
    // match dedup — mirrors AdSignageRuleMatcherTest. See
    // AdSignageRuleMatcher.scan_matches_ocr_one_char_drop_for_long_keyword for
    // the rationale and the L>=5 cross-check that prevents FP on short
    // keywords like "抗病毒" → "抗病".

    @Test
    fun scan_matches_ocr_one_char_drop_for_long_keyword() {
        // 9-char keyword "降糖降压降血脂" (>= 5). OCR drops the 降 after 压,
        // yielding "降糖压降血脂" (still has all the same characters in
        // roughly the same order with one character gone). The 8-char
        // 1-char-deletion variant "糖降压降血脂" must register and match.
        val r = FoodLabelRule(
            id = "test-food-fn",
            category = "nutrition",
            regulation = "测试",
            keywords = listOf("降糖降压降血脂"),
            severity = Severity.Warning,
        )
        val m = FoodLabelRuleMatcher(listOf(r))
        // OCR-degraded form: 1-char deletion (dropped the middle 降).
        val hits1 = m.scan("降糖压降血脂")
        assertEquals(
            "OCR-degraded form must match the 1-char-deletion variant",
            1, hits1.size,
        )
        assertEquals("test-food-fn", hits1[0].ruleId)
        // Original keyword still matches (no regression on the direct path).
        val hits2 = m.scan("降糖降压降血脂")
        assertEquals(1, hits2.size)
    }

    @Test
    fun scan_doesNotCollapseSeparateKeywordsWhenVariantEqualsAnotherKeyword() {
        // Regression guard for the variantOrigins pre-pass (init block
        // allNormalizedKeywords HashSet, added 2026-08-29 when syncing the
        // AC auto-decomposition pattern from AdSignageRuleMatcher).
        //
        // Setup: rule has TWO keywords where one is exactly a 1-char-
        // deletion variant of the other — "反式脂肪" (4 chars) is its own
        // keyword AND is also a 1-char-deletion variant of "反式脂肪酸"
        // (5 chars). Without the pre-pass, variantOriginBuilder["反式脂肪"]
        // was overwritten to point at "反式脂肪酸" and Phase 2 dedup
        // collapsed the two independent keyword hits into one.
        val r = FoodLabelRule(
            id = "test-trans-fat",
            category = "nutrition",
            regulation = "测试",
            keywords = listOf("反式脂肪", "反式脂肪酸"),
            severity = Severity.Warning,
        )
        // OCR text contains the SHORTER keyword "反式脂肪" standalone.
        // "反式脂肪酸" is NOT present.
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("含反式脂肪")
        assertEquals(
            "shorter keyword must fire independently — not be deduped into the longer keyword's slot",
            1, hits.size,
        )
        assertEquals("test-trans-fat", hits[0].ruleId)
    }

    // --- Phase 2.5 substring dedup (mirror of AdSignageRuleMatcher 2562-2672)。
    // 同 ruleId 内,一条 hit 的 matchedText 是另一条更长 hit 的子串 → 丢弃较短。
    // 跨 ruleId 不去重(不同法源各自保留)。
    // FoodLabel 规则集当前未触发 variant-induced 模式(无 |K|≥5 keyword 同时
    // 含另一独立 keyword 的 1-char-deletion 变体),所以 Phase 2.5 在食品端
    // 只覆盖模式 1(keyword 子串) + 模式 3(相邻 claim 短语)。

    @Test
    fun scan_phase2_5_dropsKeywordSubstringOverlap() {
        // 模式 1: "反式脂肪" + "反式脂肪酸" 都是同规则独立关键词
        val r = FoodLabelRule(
            id = "test-trans-fat",
            category = "nutrition",
            regulation = "GB 28050-2011 §4.4",
            keywords = listOf("反式脂肪", "反式脂肪酸"),
            severity = Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含反式脂肪酸")
        assertEquals(
            "Phase 2.5: '反式脂肪' 应被 '反式脂肪酸' 吸收,只保留后者",
            1, hits.size,
        )
        assertEquals("反式脂肪酸", hits[0].matchedText)
        assertEquals("test-trans-fat", hits[0].ruleId)
    }

    @Test
    fun scan_phase2_5_keepsCrossRuleIdSubstringOverlap() {
        // 跨规则 substring 不去重:"配料" 在 rule A,"配料表" 在 rule B
        val ruleA = FoodLabelRule(
            id = "rule-ingredient",
            category = "ingredient",
            regulation = "GB 7718-2011 §4.1.4",
            keywords = listOf("配料"),
            severity = Severity.Info,
        )
        val ruleB = FoodLabelRule(
            id = "rule-ingredient-table",
            category = "ingredient",
            regulation = "GB 7718-2011 §4.1.4.1",
            keywords = listOf("配料表"),
            severity = Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(ruleA, ruleB)).scan("配料表内容如下")
        assertEquals(
            "跨 ruleId substring 必须各自保留",
            2, hits.size,
        )
        val byRule = hits.associateBy { it.ruleId }
        assertEquals("配料", byRule["rule-ingredient"]?.matchedText)
        assertEquals("配料表", byRule["rule-ingredient-table"]?.matchedText)
    }

    @Test
    fun scan_phase2_5_keepsSameLengthNonSubstringOverlap() {
        // 同长度但互不包含:"无糖"(2) + "低脂"(2) 同规则,都不是对方的子串 → 都保留
        val r = FoodLabelRule(
            id = "test-nutr-two",
            category = "nutrition",
            regulation = "GB 28050-2011 §5",
            keywords = listOf("无糖", "低脂"),
            severity = Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品无糖低脂 健康")
        assertEquals(
            "互不包含的 keyword 必须各自保留",
            2, hits.size,
        )
        assertTrue(hits.any { it.matchedText == "无糖" })
        assertTrue(hits.any { it.matchedText == "低脂" })
    }

    @Test
    fun scan_phase2_5_dropsAdjacentClaimPhrasing() {
        // 模式 3: "控糖" + "稳血糖" + "控糖稳血糖" 同规则同短语 → 仅保留最长
        val r = FoodLabelRule(
            id = "test-fn-sugar",
            category = "functional_claim",
            regulation = "GB 28050-2011 §6",
            keywords = listOf("控糖", "稳血糖", "控糖稳血糖"),
            severity = Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品控糖稳血糖,适合糖尿病人群")
        assertEquals(
            "Phase 2.5: '控糖'/'稳血糖' 应被 '控糖稳血糖' 吸收,只保留后者",
            1, hits.size,
        )
        assertEquals("控糖稳血糖", hits[0].matchedText)
    }

    // --- GB 7718-2025 §5 致敏原强制 + 推荐标示 — 12 条（v5 增量，T9.1 落地）---

    @Test
    fun scan_gb7718Sec5AllergenGluten_firesOn小麦() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_gluten",
            "allergen",
            "GB 7718-2025 §5.1（含麸质的谷物及其制品）",
            listOf("小麦", "黑麦", "大麦", "燕麦", "麸质"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含小麦、黑麦、大麦、燕麦、麸质")
        assertEquals(5, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_gb7718Sec5AllergenCrustacean_firesOn虾() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_crustacean",
            "allergen",
            "GB 7718-2025 §5.1（甲壳纲动物及其制品）",
            listOf("虾", "蟹", "龙虾", "虾仁", "蟹棒"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含虾、蟹、龙虾、虾仁、蟹棒")
        // Phase 2.5 substring dedup (Bug 1): "虾" ⊂ "虾仁", "蟹" ⊂ "蟹棒"。5 - 2 = 3。
        assertEquals(3, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_gb7718Sec5AllergenFish_firesOn鱼() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_fish",
            "allergen",
            "GB 7718-2025 §5.1（鱼类及其制品）",
            listOf("鱼", "鱼糜", "鱼露", "鲈鱼", "鳕鱼"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含鱼糜、鱼露、鲈鱼、鳕鱼")
        // Phase 2.5 substring dedup: "鱼" ⊂ "鱼糜"/"鱼露"/"鲈鱼"/"鳕鱼" → "鱼" 被吸收,5 - 1 = 4。
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_gb7718Sec5AllergenEgg_firesOn鸡蛋() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_egg",
            "allergen",
            "GB 7718-2025 §5.1（蛋类及其制品）",
            listOf("蛋", "鸡蛋", "鸭蛋", "蛋黄", "蛋清", "全蛋粉"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含鸡蛋、鸭蛋、蛋黄、蛋清、全蛋粉")
        // Phase 2.5 substring dedup: "蛋" ⊂ "鸡蛋"/"鸭蛋"/"蛋黄"/"蛋清"。6 - 1 = 5。
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_gb7718Sec5AllergenPeanut_firesOn花生() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_peanut",
            "allergen",
            "GB 7718-2025 §5.1（花生及其制品）",
            listOf("花生", "花生仁", "花生酱", "花生碎"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含花生仁、花生酱、花生碎")
        // Phase 2.5 substring dedup: "花生" ⊂ "花生仁"/"花生酱"/"花生碎"。4 - 1 = 3。
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_gb7718Sec5AllergenSoy_firesOn大豆() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_soy",
            "allergen",
            "GB 7718-2025 §5.1（大豆及其制品）",
            listOf("大豆", "黄豆", "大豆蛋白", "豆豉", "毛豆"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含大豆蛋白、黄豆、豆豉、毛豆")
        // Phase 2.5 substring dedup: "大豆" ⊂ "大豆蛋白"。5 - 1 = 4。
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_gb7718Sec5AllergenDairy_firesOn牛奶() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_dairy",
            "allergen",
            "GB 7718-2025 §5.1（乳及乳制品）",
            listOf("牛奶", "乳粉", "乳清", "奶酪", "黄油", "乳糖", "酪蛋白"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含牛奶、乳粉、乳清、奶酪、黄油、乳糖、酪蛋白")
        // 7 个独立 keyword，无 substring 重叠 → 7 hits。
        assertEquals(7, hits.size)
    }

    @Test
    fun scan_gb7718Sec5AllergenTreeNut_firesOn杏仁() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_tree_nut",
            "allergen",
            "GB 7718-2025 §5.1（坚果及其果仁类制品）",
            listOf("杏仁", "腰果", "核桃", "榛子", "开心果", "松子", "栗子"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含杏仁、腰果、核桃、榛子、开心果、松子、栗子")
        // 7 个独立 keyword，无 substring 重叠 → 7 hits。
        assertEquals(7, hits.size)
    }

    @Test
    fun scan_gb7718Sec5AllergenCelery_firesOn芹菜() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_celery",
            "allergen",
            "GB 7718-2025 §5.2（芹菜及其制品，推荐性）",
            listOf("芹菜"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含芹菜籽粉")
        assertEquals(1, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_gb7718Sec5AllergenMustard_firesOn芥末() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_mustard",
            "allergen",
            "GB 7718-2025 §5.2（芥末及其制品，推荐性）",
            listOf("芥末"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含黄芥末调味")
        assertEquals(1, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_gb7718Sec5AllergenSesame_firesOn芝麻() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_sesame",
            "allergen",
            "GB 7718-2025 §5.2（芝麻及其制品，推荐性）",
            listOf("芝麻"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品撒白芝麻")
        assertEquals(1, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_gb7718Sec5AllergenSulphite_firesOn二氧化硫() {
        val r = FoodLabelRule(
            "food_gb7718_2025_sec5_allergen_sulphite",
            "allergen",
            "GB 7718-2025 §5.2（二氧化硫及亚硫酸盐，推荐性）",
            listOf("二氧化硫", "亚硫酸盐"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含二氧化硫、亚硫酸盐（防腐剂）")
        assertEquals(2, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_multipleV5AllergenRules_fireIndependentlyOnCombinedText() {
        // 强制 8 类与推荐 4 类规则在同一段文本上各自独立触发 — 不同 ruleId 各一条 hit
        val rules = listOf(
            FoodLabelRule("r_gluten", "allergen", "§5.1(1)", listOf("小麦"), Severity.Violation),
            FoodLabelRule("r_peanut", "allergen", "§5.1(5)", listOf("花生"), Severity.Violation),
            FoodLabelRule("r_dairy", "allergen", "§5.1(7)", listOf("牛奶"), Severity.Violation),
            FoodLabelRule("r_sesame", "allergen", "§5.2(3)", listOf("芝麻"), Severity.Info),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("含小麦粉、花生碎、牛奶、芝麻")
        assertEquals(4, hits.size)
        assertEquals(
            setOf("r_gluten", "r_peanut", "r_dairy", "r_sesame"),
            hits.map { it.ruleId }.toSet(),
        )
    }

    // --- 食品标识监督管理办法 §7-§40 gap-fill — 12 条（v5 增量，T9.2 落地）---

    @Test
    fun scan_art9MandatoryBasis_firesOn食品安全法第六十七条() {
        val r = FoodLabelRule(
            "food_art9_mandatory_basis",
            "label_form",
            "食品标识监督管理办法 第九条",
            listOf("食品安全法第六十七条", "食品安全法第七十条"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("按食品安全法第六十七条执行")
        assertEquals(1, hits.size)
    }

    @Test
    fun scan_art10MinimalUnit_firesOn最小销售单元() {
        val r = FoodLabelRule(
            "food_art10_minimal_unit",
            "label_form",
            "食品标识监督管理办法 第十条",
            listOf("最小销售单元", "多层包装"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品最小销售单元，多层包装")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art11ChinesePriority_firesOn规范汉字() {
        val r = FoodLabelRule(
            "food_art11_chinese_priority",
            "label_form",
            "食品标识监督管理办法 第十一条",
            listOf("规范汉字", "繁体字", "拼音", "字高不得大于", "外文字高"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品使用规范汉字 + 繁体字 + 拼音 + 字高不得大于 + 外文字高")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_art14DateZone_firesOn见包装物某部位() {
        val r = FoodLabelRule(
            "food_art14_date_zone",
            "production_date",
            "食品标识监督管理办法 第十四条",
            listOf("见包装物某部位", "独立区域", "不易脱落"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("生产日期见包装物某部位 / 独立区域标注不易脱落")
        // 3 个独立 keyword，无 substring 重叠 → 3 hits。
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_art17Entrust_firesOn委托方() {
        val r = FoodLabelRule(
            "food_art17_entrust",
            "label_form",
            "食品标识监督管理办法 第十七条第二款至第四款",
            listOf("委托方", "受托方", "委托单位", "受托单位", "委托生产"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("委托方 / 受托方 / 委托单位 / 受托单位 / 委托生产")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_art20MultiSpec_firesOn单件净含量() {
        val r = FoodLabelRule(
            "food_art20_multi_spec",
            "net_weight",
            "食品标识监督管理办法 第二十条",
            listOf("单件净含量", "总件数", "总净含量", "每种不同"),
            Severity.Warning,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("单件净含量 + 总件数 + 总净含量 + 每种不同")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_art26NutrientSupplement_firesOn营养素补充剂() {
        val r = FoodLabelRule(
            "food_art26_nutrient_supplement",
            "specific_food",
            "食品标识监督管理办法 第二十六条第二款",
            listOf("营养素补充剂"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品为营养素补充剂")
        assertEquals(1, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_art29HealthSpec_firesOn最小制剂单位() {
        val r = FoodLabelRule(
            "food_art29_health_spec",
            "net_weight",
            "食品标识监督管理办法 第二十九条",
            listOf("最小制剂单位"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("规格以最小制剂单位计")
        assertEquals(1, hits.size)
    }

    @Test
    fun scan_art34SalesPage_firesOn销售主页面() {
        val r = FoodLabelRule(
            "food_art34_sales_page",
            "label_form",
            "食品标识监督管理办法 第三十四条第一款",
            listOf("销售主页面"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("销售主页面刊载食品名称")
        assertEquals(1, hits.size)
    }

    @Test
    fun scan_art36HealthDisclaimerPresent_firesOn不能代替药物() {
        val r = FoodLabelRule(
            "food_art36_health_disclaimer_present",
            "specific_food",
            "食品标识监督管理办法 第三十六条第二款",
            listOf("保健食品不是药物", "不能代替药物"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("保健食品不是药物 + 不能代替药物治疗疾病")
        assertEquals(2, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_art52Export_firesOn仅用于出口() {
        val r = FoodLabelRule(
            "food_art52_export",
            "label_form",
            "食品标识监督管理办法 第五十二条",
            listOf("仅用于出口", "进口国"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品仅用于出口，符合进口国标准")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art53SmallWorkshop_firesOn小作坊() {
        val r = FoodLabelRule(
            "food_art53_small_workshop",
            "label_form",
            "食品标识监督管理办法 第五十三条",
            listOf("食品生产加工小作坊", "小作坊"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("食品生产加工小作坊生产")
        // Phase 2.5 substring dedup: "小作坊" ⊂ "食品生产加工小作坊"。2 - 1 = 1。
        assertEquals(1, hits.size)
    }

    @Test
    fun scan_multipleV5GapFillRules_fireIndependentlyOnCombinedText() {
        // §9 / §10 / §14 / §17 / §20 / §26 / §29 / §34 / §36 / §52 / §53 不同条规则在同一段文本上各自独立触发
        val rules = listOf(
            FoodLabelRule("r_art10", "label_form", "§10", listOf("最小销售单元"), Severity.Info),
            FoodLabelRule("r_art14", "production_date", "§14", listOf("见包装物某部位"), Severity.Warning),
            FoodLabelRule("r_art20", "net_weight", "§20", listOf("单件净含量"), Severity.Warning),
            FoodLabelRule("r_art26", "specific_food", "§26", listOf("营养素补充剂"), Severity.Violation),
            FoodLabelRule("r_art52", "label_form", "§52", listOf("仅用于出口"), Severity.Info),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("最小销售单元 / 见包装物某部位 / 单件净含量 / 营养素补充剂 / 仅用于出口")
        assertEquals(5, hits.size)
        assertEquals(
            setOf("r_art10", "r_art14", "r_art20", "r_art26", "r_art52"),
            hits.map { it.ruleId }.toSet(),
        )
    }

    // --- 食品安全法 §69 / §81 / §83 — 3 条（v5 增量，T9.3 落地）---

    @Test
    fun scan_safetyArt69GmoLabel_firesOn转基因() {
        val r = FoodLabelRule(
            "food_safety_art69_gmo_label",
            "label_form",
            "食品安全法 第六十九条",
            listOf("转基因", "GMO", "基因改造"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品含转基因成分 + GMO标识 + 基因改造大豆")
        assertEquals(3, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_safetyArt81NoSameFormula_firesOn同一配方() {
        val r = FoodLabelRule(
            "food_safety_art81_no_same_formula",
            "specific_food",
            "食品安全法 第八十一条",
            listOf("同一配方", "不同品牌"),
            Severity.Violation,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("同一企业不得用同一配方生产不同品牌的婴幼儿配方乳粉")
        assertEquals(2, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_safetyArt83Gmp_firesOnGMP() {
        val r = FoodLabelRule(
            "food_safety_art83_gmp",
            "specific_food",
            "食品安全法 第八十三条",
            listOf("良好生产规范", "GMP", "生产质量管理体系", "自查报告"),
            Severity.Info,
        )
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("本品按良好生产规范 + GMP + 生产质量管理体系 + 自查报告管理")
        assertEquals(4, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_multipleV5SafetyLawRules_fireIndependentlyOnCombinedText() {
        // §69 / §81 / §83 不同条规则在同一段文本上各自独立触发
        val rules = listOf(
            FoodLabelRule("r_gmo", "label_form", "§69", listOf("转基因"), Severity.Violation),
            FoodLabelRule("r_no_same_formula", "specific_food", "§81", listOf("同一配方", "不同品牌"), Severity.Violation),
            FoodLabelRule("r_gmp", "specific_food", "§83", listOf("良好生产规范", "GMP", "自查报告"), Severity.Info),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("转基因 + 同一配方 + 不同品牌 + 良好生产规范 + GMP + 自查报告")
        // 6 个独立 keyword，无 substring 重叠 → 6 hits。
        assertEquals(6, hits.size)
        assertEquals(
            setOf("r_gmo", "r_no_same_formula", "r_gmp"),
            hits.map { it.ruleId }.toSet(),
        )
    }

    // --- 婴幼儿配方乳粉产品配方注册管理办法 §5 / §7 — 2 条（v5 增量，T9.4 落地）---

    @Test
    fun scan_infantMilkRegisterNoFormat_firesOn国食注字YP() {
        val r = FoodLabelRule(
            "food_infant_formula_milk_register_no_format",
            "specific_food",
            "婴幼儿配方乳粉产品配方注册管理办法 §5 + 食品安全法 §81",
            listOf("国食注字", "国食注字YP", "配方注册号", "配方注册编号"),
            Severity.Violation,
        )
        // 文本同时含「国食注字 YP2023XXXX」+「配方注册编号 ABC」两个完整标注项
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("国食注字YP2023XXXX + 配方注册编号 ABC")
        // Phase 2.5 substring dedup（Bug 1）:
        //   "国食注字" ⊂ "国食注字YP"（同 position）→ 短被吸收
        //   "配方注册号" ⊂ "配方注册编号"（同 position）→ 短被吸收
        // 4 keywords - 2 substring pairs = 2 distinct longest matches.
        assertEquals(2, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_infantMilkRegisterTerm5y_firesOn注册证书有效期() {
        val r = FoodLabelRule(
            "food_infant_formula_milk_register_term_5y",
            "specific_food",
            "婴幼儿配方乳粉产品配方注册管理办法 §7",
            listOf("有效期5年", "有效期 5 年", "注册证书有效期", "5年有效期", "有效期届满"),
            Severity.Info,
        )
        // 文本同时含「注册证书有效期5年」+「有效期届满」+「5年有效期」+「有效期 5 年」(空格 normalize 后 = 有效期5年)
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("注册证书有效期5年有效期届满 + 5年有效期 + 有效期 5 年")
        // TextNormalizer 去空白:
        //   "有效期5年" + "有效期 5 年" → 同一条 normalized 关键词 = 1 distinct
        //   "注册证书有效期" / "5年有效期" / "有效期届满" / 加上归一化的 "有效期5年" = 4 distinct keywords
        assertEquals(4, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_multipleV4InfantMilkRules_fireIndependentlyOnCombinedText() {
        // §5 配方注册号格式 + §7 注册证书有效期 两条规则同段文本各自独立触发
        val rules = listOf(
            FoodLabelRule("r_no_format", "specific_food", "§5", listOf("国食注字YP", "配方注册编号"), Severity.Violation),
            FoodLabelRule("r_term_5y", "specific_food", "§7", listOf("注册证书有效期", "5年有效期"), Severity.Info),
        )
        val hits = FoodLabelRuleMatcher(rules).scan("国食注字YP2023XXXX / 配方注册编号 / 注册证书有效期5年 / 5年有效期")
        // 4 个独立 keyword，无 substring 重叠 → 4 hits。
        assertEquals(4, hits.size)
        assertEquals(
            setOf("r_no_format", "r_term_5y"),
            hits.map { it.ruleId }.toSet(),
        )
    }
}