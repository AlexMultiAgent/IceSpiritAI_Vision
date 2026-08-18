package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSignageRuleMatcherTest {

    private val rule1 = AdSignageRule(
        id = "extreme-001",
        category = "absolute",
        regulation = "广告法 §9",
        keywords = listOf("最佳", "第一"),
        severity = Severity.Violation
    )
    private val rule2 = AdSignageRule(
        id = "guarantee-002",
        category = "absolute",
        regulation = "广告法 §28",
        keywords = listOf("保证"),
        severity = Severity.Warning
    )
    private val matcher = AdSignageRuleMatcher(listOf(rule1, rule2))

    @Test
    fun scan_emptyText_returnsEmpty() {
        assertTrue(matcher.scan("").isEmpty())
    }

    @Test
    fun scan_textWithNoHits_returnsEmpty() {
        assertTrue(matcher.scan("这是一个普通的产品说明").isEmpty())
    }

    @Test
    fun scan_findsKeywordFromMultipleRules() {
        val hits = matcher.scan("我们是最佳品牌，保证质量")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "extreme-001" && it.matchedText == "最佳" })
        assertTrue(hits.any { it.ruleId == "guarantee-002" && it.matchedText == "保证" })
    }

    @Test
    fun scan_deduplicatesSameRuleAndText() {
        val hits = matcher.scan("最佳最佳最佳")
        assertEquals(1, hits.size)
        assertEquals("extreme-001", hits[0].ruleId)
        assertEquals("最佳", hits[0].matchedText)
    }

    @Test
    fun scan_preservesCategoryAndRegulationFromRule() {
        val hits = matcher.scan("最佳")
        assertEquals(1, hits.size)
        assertEquals("absolute", hits[0].category)
        assertEquals("广告法 §9", hits[0].regulation)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_matchesAcrossWhitespaceAndFullWidthVariants() {
        val spaced = AdSignageRule("pct", "absolute", "广告法 §9", listOf("100% 有效"), Severity.Warning)
        val normalizedMatcher = AdSignageRuleMatcher(listOf(spaced))

        val hits = normalizedMatcher.scan("本品 １００％有效,请放心")
        assertEquals(1, hits.size)
        assertEquals("100%有效", hits[0].matchedText)
    }

    @Test
    fun scan_matchesPhraseSplitAcrossLineBreak() {
        val guarantee = AdSignageRule("edu", "education", "广告法 §24", listOf("不过退款"), Severity.Violation)
        val hits = AdSignageRuleMatcher(listOf(guarantee)).scan("考不过\n退款,全额退")
        assertEquals(1, hits.size)
        assertEquals("不过退款", hits[0].matchedText)
    }

    @Test
    fun scan_attributesSharedKeywordToEveryMatchingRule() {
        val education = AdSignageRule(
            id = "education-absolute",
            category = "education",
            regulation = "广告法 §24",
            keywords = listOf("第一"),
            severity = Severity.Warning,
        )
        val general = AdSignageRule(
            id = "absolute-top",
            category = "absolute",
            regulation = "广告法 §9",
            keywords = listOf("第一"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(education, general)).scan("全国销量第一")

        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "education-absolute" })
        assertTrue(hits.any { it.ruleId == "absolute-top" })
    }

    @Test
    fun scan_doesNotMatchWhenOnlyWhitespaceRemains() {
        val matcher = AdSignageRuleMatcher(
            listOf(AdSignageRule("x", "c", "r", listOf("最好"), Severity.Warning))
        )
        assertTrue(matcher.scan("  \n\t ").isEmpty())
    }

    @Test
    fun scan_stampsDomainAsAdOnEveryHit() {
        val hits = matcher.scan("最佳")
        assertEquals(1, hits.size)
        assertEquals(CategoryDisplay.DOMAIN_AD, hits[0].domain)
    }

    // --- ad_signage_rules.json v2 增量规则触发测试(2026-08-19 落地,共 32 条新增) ---

    @Test
    fun scan_art9Emblem_firesOn国旗() {
        val r = AdSignageRule(
            "ad_signage_art9_abs_emblem",
            "absolute",
            "广告法 §9(1) + §57",
            listOf("国旗", "国徽", "军旗"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店使用国旗做装饰,彰显品牌力量")
        assertEquals(1, hits.size)
        assertEquals("国旗", hits[0].matchedText)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_art9Authority_firesOn国务院指定() {
        val r = AdSignageRule(
            "ad_signage_art9_abs_authority",
            "absolute",
            "广告法 §9(2)",
            listOf("国务院指定", "中央推荐", "国家发改委"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("国家发改委推荐产品")
        assertTrue(hits.any { it.matchedText == "国家发改委" })
        assertEquals(1, hits.size)
    }

    @Test
    fun scan_art9Superstition_firesOn算命() {
        val r = AdSignageRule(
            "ad_signage_art9_abs_superstition",
            "absolute",
            "广告法 §9(7)(8)",
            listOf("算命", "占卜", "赌博"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本馆提供算命占卜服务,在线赌博游戏")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_art11FakePatent_firesOn国家专利() {
        val r = AdSignageRule(
            "ad_signage_art11_fake_patent",
            "absolute",
            "广告法 §11 + §59",
            listOf("国家专利", "专利号 ZL"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("国家专利独家认证,专利号 ZL201630000000")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art28bFakeData_firesOn销量第一() {
        val r = AdSignageRule(
            "ad_signage_art28b_fake_data",
            "absolute",
            "广告法 §28(二) + §55",
            listOf("销量第一", "全网第一", "市场占有率第一"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店全网第一,市场占有率第一,销量第一")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medArt6Indications_firesOn祖传秘方() {
        val r = AdSignageRule(
            "ad_signage_med_art6_indications",
            "medical",
            "医疗广告管理办法 §6 + §17",
            listOf("祖传秘方", "专治", "主治"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("祖传秘方,专治百病,主治疑难杂症")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medArt7Army_firesOn解放军医院() {
        val r = AdSignageRule(
            "ad_signage_med_art7_army",
            "medical",
            "医疗广告管理办法 §7(8)",
            listOf("解放军医院", "武警医院", "部队医院"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("武警医院皮肤科,解放军医院联合门诊")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_medArt11Qualifications_firesOn三甲专家() {
        val r = AdSignageRule(
            "ad_signage_med_art11_qualifications",
            "medical",
            "医疗广告管理办法 §11",
            listOf("三甲专家", "主任医师", "博士生导师"),
            Severity.Info,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("三甲专家亲诊,主任医师 + 博士生导师联合治疗")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medArt13Newsform_firesOn健康讲座() {
        val r = AdSignageRule(
            "ad_signage_med_art13_newsform",
            "medical",
            "医疗广告管理办法 §13",
            listOf("本台讯", "专题报道", "健康讲座"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本台讯:健康讲座专题报道")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_eduArt24TestAuthority_firesOn考试命题人() {
        val r = AdSignageRule(
            "ad_signage_edu_art24_test_authority",
            "education",
            "广告法 §24(2) + §58",
            listOf("考试命题人", "阅卷老师"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("考试命题人亲临授课,阅卷老师点评")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_finArt25Endorsement_firesOn专家荐股() {
        val r = AdSignageRule(
            "ad_signage_fin_art25_endorsement",
            "finance",
            "广告法 §25(2) + §58",
            listOf("专家荐股", "大师操盘", "经济学家推荐"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("专家荐股,大师操盘,经济学家推荐组合")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_reArt26TimeDistance_firesOn分钟到() {
        val r = AdSignageRule(
            "ad_signage_re_art26_time_distance",
            "realestate",
            "广告法 §26(二) + §58",
            listOf("分钟到", "车程", "步行 X 分钟可达"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("距市中心 5 分钟到,开车 15 分钟车程")
        assertTrue(hits.any { it.matchedText == "分钟到" })
        assertTrue(hits.any { it.matchedText == "车程" })
    }

    @Test
    fun scan_reArt26PlannedFacility_firesOn规划地铁() {
        val r = AdSignageRule(
            "ad_signage_re_art26_planned_facility",
            "realestate",
            "广告法 §26(四) + §58",
            listOf("地铁直达", "规划学校", "未来 X 号线"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("规划学校落地,未来 X 号线地铁直达")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_reArt4Sqmeter_firesOn赠送面积() {
        val r = AdSignageRule(
            "ad_signage_re_art4_sqmeter",
            "realestate",
            "房地产广告发布规定 §4 + §21",
            listOf("赠送面积", "超大户型", "使用面积"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("赠送面积 30 平,使用面积标注")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_reArt8Superstition_firesOn风水宝地() {
        val r = AdSignageRule(
            "ad_signage_re_art8_superstition",
            "realestate",
            "房地产广告发布规定 §8 + §21",
            listOf("风水宝地", "龙脉", "聚财", "旺宅"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("风水宝地,龙脉之上,聚财旺宅")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_reArt7LicenseNo_firesOn无证销售() {
        val r = AdSignageRule(
            "ad_signage_re_art7_license_no",
            "realestate",
            "房地产广告发布规定 §7 + §21",
            listOf("内部认购", "认筹", "无证销售", "小产权"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("内部认购,认筹优先,小产权项目")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_art22TobaccoInternet_firesOn电子烟() {
        val r = AdSignageRule(
            "ad_signage_art22_tobacco_internet",
            "restricted",
            "广告法 §22 + §42",
            listOf("电子烟", "烟弹", "网售烟草"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店网售烟草,电子烟 + 烟弹促销")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_art23AlcoholDrive_firesOn饮酒驾驶() {
        val r = AdSignageRule(
            "ad_signage_art23_alcohol_drive",
            "restricted",
            "广告法 §23(三) + §43",
            listOf("饮酒驾驶", "喝酒开车", "酒驾无罪"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("喝酒开车,饮酒驾驶,无酒驾之虞")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_art20Breastmilk_firesOn母乳化() {
        val r = AdSignageRule(
            "ad_signage_art20_breastmilk",
            "minor",
            "广告法 §20 + §57",
            listOf("替代母乳", "母乳化", "等同母乳"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本配方母乳化,等同母乳营养")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_outdoorArt14CertNo_firesOn户外广告登记证() {
        val r = AdSignageRule(
            "ad_signage_outdoor_art14_cert_no",
            "outdoor",
            "户外广告登记管理规定 §14 + §15",
            listOf("户外广告登记证", "证号缺失"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("户外广告登记证右下角缺漏,证号缺失")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_outdoorArt4Unaudited_firesOn未经登记() {
        val r = AdSignageRule(
            "ad_signage_outdoor_art4_unaudited",
            "outdoor",
            "户外广告登记管理规定 §4 + §21",
            listOf("未经登记", "无登记证"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本楼顶大牌未经登记,无登记证违规发布")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_signageMedicineFlag_firesOn蓝帽子() {
        val r = AdSignageRule(
            "ad_signage_signage_medicine_flag",
            "signage",
            "药械保健特医食品广告审查暂行办法 §7 + §26",
            listOf("蓝帽子", "保健食品", "国食健字"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品蓝帽子保健食品,国食健字 G20XXXX")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_signageInfantMilk_firesOn进口奶源() {
        val r = AdSignageRule(
            "ad_signage_signage_infant_milk",
            "signage",
            "婴幼儿配方乳粉产品配方注册管理办法 §/ 广告法 §20",
            listOf("进口奶源", "生态牧场", "母乳化"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本配方采用进口奶源 + 生态牧场")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_signageDiseasePrevention_firesOn疾病治疗() {
        val r = AdSignageRule(
            "ad_signage_signage_disease_prevention",
            "signage",
            "广告法 §17 + §58",
            listOf("治疗", "治愈", "疗效", "止痛"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本牙膏治疗口腔溃疡,治愈 + 疗效双保障 + 立即止痛")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_multipleNewAdRules_fireIndependentlyOnCombinedText() {
        // 同一段文本上,来自不同 ad_signage 增量条款的多条规则应各自触发(每条 ruleId 各一条 hit)
        val rules = listOf(
            AdSignageRule("r_emblem", "absolute", "§9(1)", listOf("国旗"), Severity.Violation),
            AdSignageRule("r_super", "absolute", "§9(8)", listOf("赌博"), Severity.Violation),
            AdSignageRule("r_breast", "minor", "§20", listOf("替代母乳"), Severity.Violation),
            AdSignageRule("r_tobacco", "restricted", "§22", listOf("电子烟"), Severity.Violation),
        )
        val hits = AdSignageRuleMatcher(rules).scan("广告内含国旗 + 赌博链接 + 替代母乳 + 电子烟")
        assertEquals(4, hits.size)
        assertEquals(setOf("r_emblem", "r_super", "r_breast", "r_tobacco"), hits.map { it.ruleId }.toSet())
    }

    // --- ad_signage_rules.json v3 增量规则触发测试(2026-08-19 落地,共 43 条新增) ---

    // --- 医疗器械广告审查发布标准 (令第40号) — 8 条 ---

    @Test
    fun scan_medicalArt4SelfuseLabel_firesOn血压计() {
        val r = AdSignageRule(
            "ad_signage_medical_art4_selfuse_label",
            "medical",
            "医疗器械广告审查发布标准 §4 + §9",
            listOf("血压计", "血糖仪", "助听器"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("家用血压计 + 血糖仪 + 助听器一键购")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medicalArt5Contraindication_firesOn禁忌() {
        val r = AdSignageRule(
            "ad_signage_medical_art5_contraindication",
            "medical",
            "医疗器械广告审查发布标准 §5",
            listOf("禁忌", "注意事项", "禁用人群"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("禁忌内容详见说明书,注意事项 / 禁用人群")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medicalArt6Adapproval_firesOn医械广审() {
        val r = AdSignageRule(
            "ad_signage_medical_art6_adapproval",
            "medical",
            "医疗器械广告审查发布标准 §6(四)",
            listOf("医械广审", "医疗器械广审文号"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("医械广审(文)字第 2025001 号,医疗器械广审文号")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_medicalArt7Assertion_firesOn100安全() {
        val r = AdSignageRule(
            "ad_signage_medical_art7_assertion",
            "medical",
            "医疗器械广告审查发布标准 §7(一) / 广告法 §16",
            listOf("100% 安全", "绝对安全", "零副作用"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本产品 100% 安全,绝对安全 + 零副作用")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medicalArt7CureRate_firesOn有效率() {
        val r = AdSignageRule(
            "ad_signage_medical_art7_cure_rate",
            "medical",
            "医疗器械广告审查发布标准 §7(二) / 广告法 §16(二)",
            listOf("治愈率", "有效率", "显效率"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("治愈率 90%、有效率 95%、显效率 80%")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medicalArt7Compare_firesOn完胜() {
        val r = AdSignageRule(
            "ad_signage_medical_art7_compare",
            "medical",
            "医疗器械广告审查发布标准 §7(三) / 广告法 §16(三)",
            listOf("比 X 强", "完胜", "最好医疗器械"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品牌完胜其他品牌,最好医疗器械,比 X 强 10 倍")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medicalArt7Endorsement_firesOn主任医师() {
        val r = AdSignageRule(
            "ad_signage_medical_art7_endorsement",
            "medical",
            "医疗器械广告审查发布标准 §7(四)",
            listOf("主任医师推荐", "患者证言", "康复案例"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("主任医师推荐 + 患者证言 + 真实康复案例")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_medicalArt8Commitment_firesOn无效退款() {
        val r = AdSignageRule(
            "ad_signage_medical_art8_commitment",
            "medical",
            "医疗器械广告审查发布标准 §8",
            listOf("无效退款", "保险公司保险"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("无效退款 + 保险公司保险")
        assertEquals(2, hits.size)
    }

    // --- 农药广告审查发布规定 (令第81号) — 10 条 ---

    @Test
    fun scan_pesticideArt2Unregistered_firesOn农药登记证() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art2_unregistered",
            "pesticide",
            "农药广告审查发布规定 §2 + §13",
            listOf("农药登记证", "PD"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("农药登记证 PD20160001,本剂可放心使用")
        assertEquals(2, hits.size)
    }

    @Test
    fun scan_pesticideArt3Overrange_firesOn万能杀虫() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art3_overrange",
            "pesticide",
            "农药广告审查发布规定 §3",
            listOf("全杀", "万能杀虫", "对 X 病虫草均有效"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("全杀型杀虫剂,万能杀虫,对 X 病虫草均有效")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_pesticideArt4Assertion_firesOn保证有效() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art4_assertion",
            "pesticide",
            "农药广告审查发布规定 §4(一)",
            listOf("100% 安全", "绝对安全", "保证有效", "高效低毒"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("保证有效 + 100% 安全 + 绝对安全 + 高效低毒")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_pesticideArt4Endorsement_firesOn研究院推荐() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art4_endorsement",
            "pesticide",
            "农药广告审查发布规定 §4(二)",
            listOf("研究院推荐", "教授推荐", "用户证言"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("研究院推荐 + 教授推荐 + 用户证言")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_pesticideArt4CureRate_firesOn杀灭率() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art4_cure_rate",
            "pesticide",
            "农药广告审查发布规定 §4(三)",
            listOf("有效率 90%", "防治效果 95%", "杀灭率 99%"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("有效率 90%、防治效果 95%、杀灭率 99%")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_pesticideArt4SafetyViolation_firesOn拌料口服() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art4_safety_violation",
            "pesticide",
            "农药广告审查发布规定 §4(四)",
            listOf("拌料口服", "随意加大剂量", "食用安全"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("拌料口服 + 随意加大剂量,食用安全")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_pesticideArt5Deprecate_firesOn不如() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art5_deprecate",
            "pesticide",
            "农药广告审查发布规定 §5",
            listOf("不如", "比 X 差", "完胜同类"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本剂完胜同类,其他品牌不如本品,比 X 差远了")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_pesticideArt6Endorsement_firesOn销量第一() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art6_endorsement",
            "pesticide",
            "农药广告审查发布规定 §6 / 广告法 §9(三)",
            listOf("销量第一", "首选", "金奖", "全国第一"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("销量第一 + 首选 + 金奖 + 全国第一")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_pesticideArt10Commitment_firesOn保险公司保险() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art10_commitment",
            "pesticide",
            "农药广告审查发布规定 §10 + §13",
            listOf("无效退款", "保险公司保险", "保证 100% 有效"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("无效退款 + 保险公司保险 + 保证 100% 有效")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_pesticideArt11ApprovalNo_firesOn农药广审文号() {
        val r = AdSignageRule(
            "ad_signage_pesticide_art11_approval_no",
            "pesticide",
            "农药广告审查发布规定 §11",
            listOf("农药广审文号", "农药广告批准文号"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("农药广审文号 / 农药广告批准文号 应同时发布")
        assertEquals(2, hits.size)
    }

    // --- 兽药广告审查发布规定 (令第82号) — 10 条 ---

    @Test
    fun scan_veterinaryArt3Prohibited_firesOn未取得兽药产品批准文号() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art3_prohibited",
            "veterinary",
            "兽药广告审查发布规定 §3",
            listOf("兽用麻醉", "未取得兽药产品批准文号", "未取得进口兽药注册证书"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("兽用麻醉药品 + 未取得兽药产品批准文号 / 未取得进口兽药注册证书")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_veterinaryArt4Assertion_firesOn绝对安全() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art4_assertion",
            "veterinary",
            "兽药广告审查发布规定 §4(一)",
            listOf("100% 有效", "保证有效", "绝对安全", "零副作用"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("100% 有效 + 保证有效 + 绝对安全 + 零副作用")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_veterinaryArt4CureRate_firesOn治愈率() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art4_cure_rate",
            "veterinary",
            "兽药广告审查发布规定 §4(三)",
            listOf("有效率", "治愈率", "防治效果"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("有效率 95% + 治愈率 90% + 防治效果 99%")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_veterinaryArt4SafetyViolation_firesOn随意加大剂量() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art4_safety_violation",
            "veterinary",
            "兽药广告审查发布规定 §4(四)",
            listOf("拌料口服", "随意加大剂量", "人畜同用"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("拌料口服 + 随意加大剂量 + 可人畜同用")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_veterinaryArt6Absolute_firesOn包治百病() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art6_absolute",
            "veterinary",
            "兽药广告审查发布规定 §6 / 广告法 §9(三)",
            listOf("最高技术", "最进步制法", "包治百病", "兽药仙丹"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("最高技术 + 最进步制法 + 包治百病 + 兽药仙丹")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_veterinaryArt7Endorsement_firesOn全国第一() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art7_endorsement",
            "veterinary",
            "兽药广告审查发布规定 §7 / 广告法 §9(三)",
            listOf("销量第一", "首选", "金奖", "全国第一"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("销量第一 + 首选 + 金奖 + 全国第一")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_veterinaryArt8Commitment_firesOn无效退款() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art8_commitment",
            "veterinary",
            "兽药广告审查发布规定 §8",
            listOf("无效退款", "保险公司保险", "100% 有效"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("无效退款 + 保险公司保险 + 100% 有效")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_veterinaryArt10ApprovalNo_firesOn兽药广审文号() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art10_approval_no",
            "veterinary",
            "兽药广告审查发布规定 §10",
            listOf("兽药广审文号", "兽药广告批准文号"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("兽药广审文号 / 兽药广告批准文号 应同时发布")
        assertEquals(2, hits.size)
    }

    // --- 城市市容和环境卫生管理条例 + 广告法 §32 户外广告细化 — 8 条 ---

    @Test
    fun scan_outdoorCityArt32Government_firesOn政府大楼() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_government",
            "outdoor",
            "广告法 §32(二) / 城市市容和环境卫生管理条例 §11",
            listOf("政府大楼", "机关大院内", "军事管理区"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("政府大楼 + 机关大院内 + 军事管理区 设置户外广告")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_outdoorCityArt32SchoolHospital_firesOn学校门口() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_school_hospital",
            "outdoor",
            "广告法 §32(二) / 城市市容和环境卫生管理条例 §11",
            listOf("学校门口", "校园内", "幼儿园外墙", "医院门口"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("学校门口 + 校园内 + 幼儿园外墙 + 医院门口 设置户外广告")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_outdoorCityArt32Traffic_firesOn交通信号灯() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_traffic",
            "outdoor",
            "广告法 §32(一) / 城市市容和环境卫生管理条例 §11",
            listOf("交通信号灯", "指路牌", "护栏"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("交通信号灯 + 指路牌 + 护栏 严禁设置户外广告")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_outdoorCityArt32Roof_firesOn楼顶广告() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_roof",
            "outdoor",
            "广告法 §32(三) + 各地户外广告设置管理办法",
            listOf("楼顶广告", "楼顶大牌", "屋顶招牌", "天面广告"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("楼顶广告 + 楼顶大牌 + 屋顶招牌 + 天面广告 全面禁止")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_outdoorCityArt32CulturalRelic_firesOn文物保护单位() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_cultural_relic",
            "outdoor",
            "广告法 §32(二) / 城市市容和环境卫生管理条例 §11",
            listOf("文物保护单位", "历史建筑", "古建筑", "不可移动文物"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("文物保护单位 + 历史建筑 + 古建筑 + 不可移动文物 周边禁设广告")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_outdoorCityArt32Airport_firesOn净空保护区() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_airport",
            "outdoor",
            "广告法 §32 + 各地户外广告设置管理办法",
            listOf("净空保护区", "机场附近", "气球广告", "飞艇广告"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("净空保护区 + 机场附近 + 气球广告 + 飞艇广告 全面禁止")
        assertEquals(4, hits.size)
    }

    // --- 广告法 §29 / §30 / §44 / §46 补漏 — 4 条 ---

    @Test
    fun scan_signageArt29InternetIdentifiable_firesOn软文() {
        val r = AdSignageRule(
            "ad_signage_signage_art29_internet_identifiable",
            "signage",
            "广告法 §29(1) + §59",
            listOf("软文", "科普", "知识讲座", "专家访谈", "消费者教育"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("软文 + 科普 + 知识讲座 + 专家访谈 + 消费者教育")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_signageArt29OneclickClose_firesOn弹窗广告() {
        val r = AdSignageRule(
            "ad_signage_signage_art29_oneclick_close",
            "signage",
            "广告法 §29(3) + §59",
            listOf("点击关闭", "一键关闭", "弹窗广告", "信息流广告"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("点击关闭 + 一键关闭 + 弹窗广告 + 信息流广告")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_signageArt46PreReview_firesOn未审查() {
        val r = AdSignageRule(
            "ad_signage_signage_art46_pre_review",
            "signage",
            "广告法 §46 + §58",
            listOf("未审查", "未取得审查", "未经审查", "未审批", "未通过审查"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("药品广告未审查 / 未取得审查 / 未经审查 / 未审批 / 未通过审查")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_signageArt44InternetProvider_firesOn公众号广告() {
        val r = AdSignageRule(
            "ad_signage_signage_art44_internet_provider",
            "signage",
            "广告法 §44 + §59",
            listOf("自媒体广告", "公众号广告", "小程序广告"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("自媒体广告 + 公众号广告 + 小程序广告 平台应审查")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_multipleV3Rules_fireIndependentlyOnCombinedText() {
        // 同一段文本上,来自 v3 增量条款的多条规则应各自触发(每条 ruleId 各一条 hit)
        val rules = listOf(
            AdSignageRule("r_med_selfuse", "medical", "§4", listOf("血压计"), Severity.Warning),
            AdSignageRule("r_pest_assert", "pesticide", "§4(一)", listOf("保证有效"), Severity.Violation),
            AdSignageRule("r_vet_absolute", "veterinary", "§6", listOf("包治百病"), Severity.Violation),
            AdSignageRule("r_outdoor_roof", "outdoor", "§32(三)", listOf("楼顶广告"), Severity.Warning),
            AdSignageRule("r_signage_internet", "signage", "§29(3)", listOf("弹窗广告"), Severity.Warning),
        )
        val hits = AdSignageRuleMatcher(rules).scan("血压计 + 保证有效 + 包治百病 + 楼顶广告 + 弹窗广告")
        assertEquals(5, hits.size)
        assertEquals(
            setOf("r_med_selfuse", "r_pest_assert", "r_vet_absolute", "r_outdoor_roof", "r_signage_internet"),
            hits.map { it.ruleId }.toSet(),
        )
    }
}