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
        val hits = FoodLabelRuleMatcher(listOf(r)).scan("散装食品，按斤销售")
        assertTrue(hits.any { it.matchedText == "散装食品" })
        assertTrue(hits.any { it.matchedText == "散装" })
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
        assertEquals(7, hits.size)
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
        assertEquals(7, hits.size)
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
        assertEquals(9, hits.size)
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
        assertEquals(8, hits.size)
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
        assertEquals(5, hits.size)
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
        assertEquals(3, hits.size)
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
        assertEquals(3, hits.size)
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
        assertEquals(4, hits.size)
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
        assertEquals(6, hits.size)
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
        assertEquals(9, hits.size)
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
}