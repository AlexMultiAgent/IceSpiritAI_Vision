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
            "ad_signage_art12_fake_patent",
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
            listOf("销量第一", "首选", "金奖", "唯一"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("销量第一 + 首选 + 金奖 + 全国第一")
        // "全国第一" 已从 pesticide 规则挪到 ad_signage_art28b_fake_data(更通用类目),
        // 本规则只覆盖销量第一 / 首选 / 金奖 / 唯一,3 条
        assertEquals(3, hits.size)
        assertTrue(hits.none { it.matchedText == "全国第一" })
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
            listOf("销量第一", "首选", "金奖", "唯一"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("销量第一 + 首选 + 金奖 + 全国第一")
        // "全国第一" 已从 veterinary 规则挪到 ad_signage_art28b_fake_data(更通用类目),
        // 本规则只覆盖销量第一 / 首选 / 金奖 / 唯一,3 条
        assertEquals(3, hits.size)
        assertTrue(hits.none { it.matchedText == "全国第一" })
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

    // --- ad_signage_rules.json v4 增量规则触发测试(2026-08-19 落地,共 31 条新增) ---

    // --- 化妆品监督管理条例(国务院令第727号)— 12 条 ---

    @Test
    fun scan_cosmeticArt23MedicalClaim_firesOn治疗皮炎() {
        val r = AdSignageRule(
            "cosmetic_art23_medical_claim",
            "cosmetic",
            "化妆品监督管理条例 §23 + §25(2)",
            listOf("治疗", "治愈", "疗效", "祛斑", "消炎", "抑菌"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品治疗皮炎、治愈 + 疗效显著 + 祛斑 + 消炎 + 抑菌")
        assertEquals(6, hits.size)
        assertEquals("cosmetic", hits[0].category)
    }

    @Test
    fun scan_cosmeticArt23Misleading_firesOn零添加() {
        val r = AdSignageRule(
            "cosmetic_art23_misleading_claim",
            "cosmetic",
            "化妆品监督管理条例 §22 + §25(1)",
            listOf("零添加", "100% 安全", "纯天然", "立竿见影", "零刺激"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("零添加 + 100% 安全 + 纯天然 + 立竿见影 + 零刺激")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_cosmeticArt23MedicalExplicit_firesOn彻底治愈() {
        val r = AdSignageRule(
            "cosmetic_art23_medical_explicit",
            "cosmetic",
            "化妆品监督管理条例 §23 + §25(2) / 广告法 §16",
            listOf("彻底治愈", "一针见效", "当天见效", "包治", "特效"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("彻底治愈 + 一针见效 + 当天见效 + 包治 + 特效")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_cosmeticArt20ClaimBasis_firesOn专利配方() {
        val r = AdSignageRule(
            "cosmetic_art20_claim_basis",
            "cosmetic",
            "化妆品监督管理条例 §20 + §21",
            listOf("研究表明", "专利配方", "临床验证", "博士研发"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("研究表明 + 专利配方 + 临床验证 + 博士研发")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_cosmeticArt17SpecialClass_firesOn染发剂() {
        val r = AdSignageRule(
            "cosmetic_art17_special_class",
            "cosmetic",
            "化妆品监督管理条例 §17 + §18",
            listOf("染发剂", "烫发剂", "祛斑", "防晒", "防脱"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("染发剂 + 烫发剂 + 祛斑 + 防晒 + 防脱")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_cosmeticArt23SpecialRegno_firesOn特殊化妆品() {
        val r = AdSignageRule(
            "cosmetic_art23_special_regno",
            "cosmetic",
            "化妆品监督管理条例 §23(一)",
            listOf("特殊化妆品", "国妆特字缺失", "注册证编号缺失"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("特殊化妆品 + 国妆特字缺失 + 注册证编号缺失")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_cosmeticArt23GeneralFileno_firesOn普通化妆品() {
        val r = AdSignageRule(
            "cosmetic_art23_general_fileno",
            "cosmetic",
            "化妆品监督管理条例 §23(一) + §18",
            listOf("普通化妆品", "国妆网备字缺失", "备案号缺失"),
            Severity.Info,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("普通化妆品 + 国妆网备字缺失 + 备案号缺失")
        assertEquals(3, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_cosmeticArt23Ingredients_firesOn成分表缺失() {
        val r = AdSignageRule(
            "cosmetic_art23_ingredients",
            "cosmetic",
            "化妆品监督管理条例 §23(五) + 标签管理办法 §11",
            listOf("成分表缺失", "未标全成分", "Ingredients 缺失"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("成分表缺失 + 未标全成分 + Ingredients 缺失")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_cosmeticArt23LicenseNo_firesOn生产许可证缺失() {
        val r = AdSignageRule(
            "cosmetic_art23_license_no",
            "cosmetic",
            "化妆品监督管理条例 §23(三)",
            listOf("生产许可证缺失", "XK16-108 缺失"),
            Severity.Info,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("生产许可证缺失 + XK16-108 缺失")
        assertEquals(2, hits.size)
        assertEquals(Severity.Info, hits[0].severity)
    }

    @Test
    fun scan_cosmeticArt23SafetyWarning_firesOn使用期限缺失() {
        val r = AdSignageRule(
            "cosmetic_art23_safety_warning",
            "cosmetic",
            "化妆品监督管理条例 §23(七) + 标签管理办法 §15",
            listOf("使用期限缺失", "使用方法缺失", "安全警示缺失"),
            Severity.Info,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("使用期限缺失 + 使用方法缺失 + 安全警示缺失")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_cosmeticArt9AbsExtended_firesOn顶级() {
        val r = AdSignageRule(
            "cosmetic_art9_abs_extended",
            "cosmetic",
            "广告法 §9(三) + 化妆品监督管理条例 §22",
            listOf("顶级", "首选", "唯一", "独家", "最强"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("顶级 + 首选 + 唯一 + 独家 + 最强")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_cosmeticArt8AwardClaim_firesOn金奖() {
        val r = AdSignageRule(
            "cosmetic_art8_award_claim",
            "cosmetic",
            "广告法 §8 + 化妆品监督管理条例 §25",
            listOf("金奖", "第一品牌", "中国名牌", "驰名商标"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("金奖 + 第一品牌 + 中国名牌 + 驰名商标")
        assertEquals(4, hits.size)
    }

    // --- 银发〔2019〕316号 金融营销宣传规制 — 10 条 ---

    @Test
    fun scan_finance316Art3FraudGuarantee_firesOn稳赚不赔() {
        val r = AdSignageRule(
            "finance_316_art3_2_fraud_guarantee",
            "finance",
            "银发〔2019〕316号 §3(二) / 广告法 §25(一)",
            listOf("稳赚不赔", "保本高收益", "无风险", "100% 盈利", "保收益"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("稳赚不赔 + 保本高收益 + 无风险 + 100% 盈利 + 保收益")
        assertEquals(5, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_finance316Art3Scope_firesOn无证理财() {
        val r = AdSignageRule(
            "finance_316_art3_1_scope",
            "finance",
            "银发〔2019〕316号 §3(一)",
            listOf("无证经营", "超范围宣传", "未备案金融产品", "非法集资"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("无证经营 + 超范围宣传 + 未备案金融产品 + 非法集资")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_finance316Art3RegulatorUse_firesOn央行推荐() {
        val r = AdSignageRule(
            "finance_316_art3_2_regulator_use",
            "finance",
            "银发〔2019〕316号 §3(二)",
            listOf("央行推荐", "银保监认证", "监管批准", "央行备案", "官方背书"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("央行推荐 + 银保监认证 + 监管批准 + 央行备案 + 官方背书")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_finance316Art3ConsumerRight_firesOn免审核() {
        val r = AdSignageRule(
            "finance_316_art3_2_consumer_right",
            "finance",
            "银发〔2019〕316号 §3(二)(五)",
            listOf("免审核", "免风险揭示", "零门槛", "无需风险评估", "全民可投"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("免审核 + 免风险揭示 + 零门槛 + 无需风险评估 + 全民可投")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_finance316Art3FairCompetition_firesOn某某跑路() {
        val r = AdSignageRule(
            "finance_316_art3_3_fair_competition",
            "finance",
            "银发〔2019〕316号 §3(三) / 反不正当竞争法 §11",
            listOf("其他平台都是骗子", "某某破产", "某某跑路", "某平台倒闭"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("其他平台都是骗子 + 某某破产 + 某某跑路 + 某平台倒闭")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_finance316Art3GovernmentUse_firesOn国家担保() {
        val r = AdSignageRule(
            "finance_316_art3_4_government_use",
            "finance",
            "银发〔2019〕316号 §3(四)",
            listOf("国家担保", "政府兜底", "央行背书", "国务院批准"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("国家担保 + 政府兜底 + 央行背书 + 国务院批准")
        assertEquals(4, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_finance316Art3Internet_firesOn直播带单() {
        val r = AdSignageRule(
            "finance_316_art3_6_internet",
            "finance",
            "银发〔2019〕316号 §3(六)",
            listOf("加微信", "扫码进群", "直播带单", "快手直播带单", "群内带单"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("加微信 + 扫码进群 + 直播带单 + 快手直播带单 + 群内带单")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_finance316Art3UnlicensedSend_firesOn短信群发() {
        val r = AdSignageRule(
            "finance_316_art3_7_unlicensed_send",
            "finance",
            "银发〔2019〕316号 §3(七)",
            listOf("短信群发", "电话营销", "AI 外呼", "智能外呼", "短信营销"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("短信群发 + 电话营销 + AI 外呼 + 智能外呼 + 短信营销")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_financeArt25EndorsementReinforced_firesOn首席经济学家() {
        val r = AdSignageRule(
            "finance_art25_endorsement_reinforced",
            "finance",
            "广告法 §25(二)",
            listOf("首席经济学家推荐", "首席分析师", "经济学家推荐", "基金经理推荐"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("首席经济学家推荐 + 首席分析师 + 经济学家推荐 + 基金经理推荐")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_financeArt9AbsInvestment_firesOn最佳平台() {
        val r = AdSignageRule(
            "finance_art9_abs_investment",
            "finance",
            "广告法 §9(三) + §25",
            listOf("最佳平台", "最安全", "最稳", "第一平台", "顶级理财"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("最佳平台 + 最安全 + 最稳 + 第一平台 + 顶级理财")
        assertEquals(5, hits.size)
    }

    // --- 互联网广告管理办法(SAMR令第72号)— 9 条 ---

    @Test
    fun scan_internetArt6Identifiable_firesOn种草() {
        val r = AdSignageRule(
            "internet_art6_identifiable",
            "internet_ad",
            "互联网广告管理办法 §6 / 广告法 §29",
            listOf("亲测", "种草", "达人推荐", "排行榜", "测评"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("亲测 + 种草 + 达人推荐 + 排行榜 + 测评")
        assertEquals(5, hits.size)
        assertEquals("internet_ad", hits[0].category)
    }

    @Test
    fun scan_internetArt6Softarticle_firesOn软文() {
        val r = AdSignageRule(
            "internet_art6_softarticle",
            "internet_ad",
            "互联网广告管理办法 §6(2)",
            listOf("软文", "种草文", "测评报告", "植入式广告", "原生广告"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("软文 + 种草文 + 测评报告 + 植入式广告 + 原生广告")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_internetArt21PaidSearch_firesOn百度推广() {
        val r = AdSignageRule(
            "internet_art21_paid_search",
            "internet_ad",
            "互联网广告管理办法 §21 / §6(2)",
            listOf("百度推广", "搜狗推广", "P4P", "付费搜索", "竞价排名"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("百度推广 + 搜狗推广 + P4P + 付费搜索 + 竞价排名")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_internetArt15PopupClose_firesOn开屏广告() {
        val r = AdSignageRule(
            "internet_art15_popup_close",
            "internet_ad",
            "互联网广告管理办法 §15 / 广告法 §44",
            listOf("打开 App 弹出", "开屏广告", "弹窗广告", "强制停留"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("打开 App 弹出 + 开屏广告 + 弹窗广告 + 强制停留")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_internetArt9HealthSoftarticle_firesOn健康讲座() {
        val r = AdSignageRule(
            "internet_art9_health_softarticle",
            "internet_ad",
            "互联网广告管理办法 §9(2) / §7 / 广告法 §17",
            listOf("健康讲座", "养生秘笈", "老中医", "专家解读", "养生堂"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("健康讲座 + 养生秘笈 + 老中医 + 专家解读 + 养生堂")
        assertEquals(5, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_internetArt8Tobacco_firesOn电子烟() {
        val r = AdSignageRule(
            "internet_art8_tobacco",
            "internet_ad",
            "互联网广告管理办法 §8(1) / 广告法 §22",
            listOf("电子烟", "戒烟灵", "新型烟草", "雾化烟", "烟弹"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("电子烟 + 戒烟灵 + 新型烟草 + 雾化烟 + 烟弹")
        assertEquals(5, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_internetArt8RxDrug_firesOn处方药() {
        val r = AdSignageRule(
            "internet_art8_rx_drug",
            "internet_ad",
            "互联网广告管理办法 §8(1) / 广告法 §15",
            listOf("处方药", "Rx", "凭处方", "医师处方"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("处方药 + Rx + 凭处方 + 医师处方")
        assertEquals(4, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_internetArt7PreReview_firesOn药品互联网销售() {
        val r = AdSignageRule(
            "internet_art7_pre_review",
            "internet_ad",
            "互联网广告管理办法 §7 / §10",
            listOf("药品互联网销售", "医疗器械互联网", "保健食品网售", "广告审查批准文号缺失"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("药品互联网销售 + 医疗器械互联网 + 保健食品网售 + 广告审查批准文号缺失")
        assertEquals(4, hits.size)
    }

    @Test
    fun scan_internetArt22AlgorithmDisclose_firesOn千人千面() {
        val r = AdSignageRule(
            "internet_art22_algorithm_disclose",
            "internet_ad",
            "互联网广告管理办法 §22 / 互联网信息服务算法推荐管理规定 §16",
            listOf("算法推荐", "智能推荐", "千人千面", "AI 推荐", "个性化推送"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("算法推荐 + 智能推荐 + 千人千面 + AI 推荐 + 个性化推送")
        assertEquals(5, hits.size)
    }

    @Test
    fun scan_multipleV4Rules_fireIndependentlyOnCombinedText() {
        // 同一段文本上,来自 v4 增量条款(化妆 + 金融 + 互联网广告)的多条规则应各自触发
        val rules = listOf(
            AdSignageRule("r_cosmetic_med", "cosmetic", "§23(2)", listOf("治疗"), Severity.Violation),
            AdSignageRule("r_finance_guar", "finance", "316号 §3(二)", listOf("稳赚不赔"), Severity.Violation),
            AdSignageRule("r_internet_tobacco", "internet_ad", "§8(1)", listOf("电子烟"), Severity.Violation),
            AdSignageRule("r_internet_popup", "internet_ad", "§15", listOf("弹窗广告"), Severity.Warning),
        )
        val hits = AdSignageRuleMatcher(rules).scan("本品治疗皮炎 + 理财稳赚不赔 + 网售电子烟 + 弹窗广告")
        assertEquals(4, hits.size)
        assertEquals(
            setOf("r_cosmetic_med", "r_finance_guar", "r_internet_tobacco", "r_internet_popup"),
            hits.map { it.ruleId }.toSet(),
        )
    }

    // --- ad_signage_rules.json v5 增量规则触发测试(2026-08-20 落地,普通食品医疗宣传 + 极限词泛化) ---

    // --- 极限词扩展("首个" / "首家" / "首选" / "领导品牌" 挪进通用 §9(三) 规则) ---

    @Test
    fun scan_art9AbsTop_extended_firesOn首个() {
        // "首个" / "首家" / "首选" / "领导品牌" / "领军品牌" / "首屈一指"
        // 现在落入通用 absolute 规则(ad_signage_art9_abs_top),不再仅 cosmetic 适用
        val r = AdSignageRule(
            "ad_signage_art9_abs_top",
            "absolute",
            "广告法 §9(三)",
            listOf("最佳", "最好", "第一", "顶级", "唯一", "首个", "首家", "首选", "领导品牌", "领军品牌", "首屈一指"),
            Severity.Warning,
        )
        // 6 keywords 各在文本中出现 1 次(避免"领军企业"被误判为命中"领军品牌")
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "本店首个公益装 + 首家旗舰店 + 首选品牌 + 行业领导品牌 + 领军品牌 + 首屈一指的设计"
        )
        assertEquals(6, hits.size)
    }

    @Test
    fun scan_art28bFakeData_extended_firesOn全国第一() {
        // "全国第一" / "全国门店数量第一" / "行业第一" / "全网销量第一" / "市场占有率领先"
        // 已被挪进 ad_signage_art28b_fake_data(更通用类目),
        // 不再被错误归类到 pesticide / veterinary 专属规则
        val r = AdSignageRule(
            "ad_signage_art28b_fake_data",
            "absolute",
            "广告法 §28(二) + §55",
            listOf("销量第一", "全网第一", "市场占有率第一", "全国销量冠军", "消费者满意度第一",
                   "全国第一", "全国销量第一", "全国门店数量第一", "全国连锁数量第一", "行业第一",
                   "全网销量第一", "市场占有率领先", "销量遥遥领先", "全国第一品牌"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "大闸蟹十年累计销量全国第一 + 全国门店数量第一 + 行业第一 + 全网销量第一 + 市场占有率领先"
        )
        // 调试输出
        println("DEBUG hit count=${hits.size} matchedTexts=${hits.map { it.matchedText }}")
        assertTrue(hits.any { it.matchedText == "全国第一" })
        assertTrue(hits.any { it.matchedText == "全国门店数量第一" })
        assertTrue(hits.any { it.matchedText == "行业第一" })
        assertTrue(hits.any { it.matchedText == "全网销量第一" })
        assertTrue(hits.any { it.matchedText == "市场占有率领先" })
        // 5 个核心关键词命中(加上可能在累计销量全国第一中匹配到的次级关键词)
        assertTrue("至少 5 个命中", hits.size >= 5)
    }

    // --- 普通食品宣传保健功能(v5 新规则 signage_food_function_claim)— ---

    @Test
    fun scan_signageFoodFunctionClaim_firesOn增强免疫() {
        val r = AdSignageRule(
            "ad_signage_signage_food_function_claim",
            "signage",
            "广告法 §17 + §58 + GB 7718-2011",
            listOf("增强免疫力", "提高免疫力", "调节免疫", "调节血糖", "控糖", "降血糖", "稳血糖",
                   "调节血脂", "降血脂", "降胆固醇", "调节血压", "降血压", "保护心血管",
                   "清血管", "软化血管", "抗氧化", "抗衰老", "抗疲劳", "保护视力",
                   "缓解视疲劳", "改善视力", "护眼", "护双眼", "改善睡眠", "改善记忆",
                   "调节内分泌", "排毒", "清理肠道", "通便", "润肠通便", "防癌", "抗癌"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "本品增强免疫力 + 提高免疫力 + 调节血糖 + 控糖稳血糖 + 降血压 + 保护心血管 + 抗氧化 + 抗衰老 + 护双眼 + 改善睡眠 + 排毒 + 防癌"
        )
        // 13 keywords matched: 增强免疫力 / 提高免疫力 / 调节血糖 / 控糖 / 稳血糖 / 降血压 /
        // 保护心血管 / 抗氧化 / 抗衰老 / 护双眼 / 改善睡眠 / 排毒 / 防癌
        assertEquals(13, hits.size)
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_signageFoodFunctionClaim_firesOn护眼() {
        // 单关键词命中验证 - "护眼" 这种保健食品功能词
        val r = AdSignageRule(
            "ad_signage_signage_food_function_claim",
            "signage",
            "广告法 §17 + §58 + GB 7718-2011",
            listOf("护眼", "护双眼", "抗氧化"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("叶黄素软糖,护眼护双眼,超强抗氧化")
        assertEquals(3, hits.size)
    }

    // --- 普通食品针对特定疾病(v5 新规则 signage_food_disease_target)— ---

    @Test
    fun scan_signageFoodDiseaseTarget_firesOn糖尿病() {
        val r = AdSignageRule(
            "ad_signage_signage_food_disease_target",
            "signage",
            "广告法 §17 + §58 + 食品标识监督管理办法",
            listOf("糖尿病", "高血压", "高血脂", "冠心病", "脑血栓", "动脉硬化", "癌症",
                   "肿瘤", "中风", "老年痴呆", "阿尔茨海默", "心脑血管",
                   "糖尿病患者", "高血压患者", "冠心病患者", "癌症患者", "肿瘤患者"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "本品是糖尿病患者 + 高血压患者 + 冠心病患者的安心选择 + 远离癌症 + 预防中风 + 心脑血管疾病人群适用"
        )
        assertTrue(hits.size >= 6)
        assertTrue(hits.any { it.matchedText == "糖尿病患者" })
        assertTrue(hits.any { it.matchedText == "糖尿病" })
        assertTrue(hits.any { it.matchedText == "心脑血管" })
        assertEquals(Severity.Violation, hits[0].severity)
    }

    @Test
    fun scan_signageFoodDiseaseTarget_firesOn肿瘤() {
        // 简化的关键词命中
        val r = AdSignageRule(
            "ad_signage_signage_food_disease_target",
            "signage",
            "广告法 §17 + §58 + 食品标识监督管理办法",
            listOf("肿瘤", "癌症", "中风"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本保健品预防肿瘤 + 远离癌症 + 减少中风风险")
        assertEquals(3, hits.size)
    }

    @Test
    fun scan_v5FoodRules_deduplicateEachOther() {
        // 同一文本上的两个新规则应各自独立触发(每条 ruleId 各一条 hit),
        // 不应在两个规则间 dedupe(ruleId 不同)
        val foodFunction = AdSignageRule(
            "ad_signage_signage_food_function_claim", "signage", "§17",
            listOf("增强免疫力"), Severity.Violation,
        )
        val foodDisease = AdSignageRule(
            "ad_signage_signage_food_disease_target", "signage", "§17",
            listOf("糖尿病"), Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(foodFunction, foodDisease))
            .scan("增强免疫力 + 糖尿病患者的安心选择")
        assertEquals(2, hits.size)
        assertEquals(setOf("ad_signage_signage_food_function_claim", "ad_signage_signage_food_disease_target"),
                     hits.map { it.ruleId }.toSet())
    }

    @Test
    fun scan_v5RulesDoNotShadowAdSignageSignageDiseasePrevention() {
        // 验证 v5 新规则与现有直接疾病用语规则协同触发(不是替代)
        // 文本同时含"治疗"(直接) + "增强免疫力"(间接保健),两条规则应同时命中
        val direct = AdSignageRule(
            "ad_signage_signage_disease_prevention", "signage", "广告法 §17",
            listOf("治疗", "治愈"), Severity.Warning,
        )
        val indirect = AdSignageRule(
            "ad_signage_signage_food_function_claim", "signage", "广告法 §17 + GB 7718",
            listOf("增强免疫力", "保护心血管"), Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(direct, indirect))
            .scan("本品治疗皮炎 + 增强免疫力 + 保护心血管")
        assertEquals(3, hits.size)
        assertEquals(setOf("ad_signage_signage_disease_prevention", "ad_signage_signage_food_function_claim"),
                     hits.map { it.ruleId }.toSet())
        assertTrue(hits.any { it.severity == Severity.Violation })
        assertTrue(hits.any { it.severity == Severity.Warning })
    }

    @Test
    fun scan_art9AbsTop_extended_doesNotIntroduceCosmeticFalsePositiveForGenericUse() {
        // 验证 "首个" 在 ad_signage_art9_abs_top 触发时 category=absolute,
        // 而 cosmetic_art9_abs_extended 触发时 category=cosmetic —
        // 即同一文本上,两条规则应各自归到自己的 category(没有泛化丢类目)
        val absRule = AdSignageRule(
            "ad_signage_art9_abs_top", "absolute", "广告法 §9(三)",
            listOf("首个"), Severity.Warning,
        )
        val cosRule = AdSignageRule(
            "cosmetic_art9_abs_extended", "cosmetic", "化妆品条例 §22",
            listOf("首个"), Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(absRule, cosRule)).scan("本品首个配方升级")
        assertEquals(2, hits.size)
        assertEquals(setOf("absolute", "cosmetic"), hits.map { it.category }.toSet())
    }

    // --- Wave 2 Task 2.3 — 24 条未覆盖规则 fixture(2026-08-20 落地,
    //     广告法 + 部门规章 + 户外 + 兽药 + 教育 + 金融 + 房地产 + signage v5 通用化) ---

    @Test
    fun scan_art10Minor_firesOn儿童专用() {
        val r = AdSignageRule(
            "ad_signage_art10_minor",
            "minor",
            "广告法 §10(2) + §57",
            listOf("儿童专用", "宝宝必备"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店儿童专用配方,宝宝放心")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art10_minor", hits[0].ruleId)
    }

    @Test
    fun scan_art16MedAbs_firesOn根治() {
        val r = AdSignageRule(
            "ad_signage_art16_med_abs",
            "medical",
            "广告法 §16(1) / 医疗广告管理办法 §6",
            listOf("根治", "100% 有效", "彻底治愈"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品可根治慢性病")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art16_med_abs", hits[0].ruleId)
    }

    @Test
    fun scan_art16MedHealth_firesOn疗效() {
        val r = AdSignageRule(
            "ad_signage_art16_med_health",
            "medical",
            "广告法 §16(2) / 医疗广告管理办法 §6",
            listOf("疗效", "治愈率", "根治率"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品疗效显著")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art16_med_health", hits[0].ruleId)
    }

    @Test
    fun scan_art22TobAlc_firesOn戒烟() {
        val r = AdSignageRule(
            "ad_signage_art22_tob_alc",
            "restricted",
            "广告法 §22 + §42",
            listOf("戒烟", "解酒"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("戒烟神器,告别尼古丁")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art22_tob_alc", hits[0].ruleId)
    }

    @Test
    fun scan_art23AlcoholRelief_firesOn提神酒() {
        val r = AdSignageRule(
            "ad_signage_art23_alcohol_relief",
            "restricted",
            "广告法 §23(三) / 酒类广告管理办法 §5",
            listOf("解酒", "解乏", "提神酒"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店提神酒,夜间开长途")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art23_alcohol_relief", hits[0].ruleId)
    }

    @Test
    fun scan_art24EduGuar_firesOn保过() {
        val r = AdSignageRule(
            "ad_signage_art24_edu_guar",
            "education",
            "广告法 §24(1) + §58",
            listOf("保过", "包过", "不过退款"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("考证保过,签协议保障")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art24_edu_guar", hits[0].ruleId)
    }

    @Test
    fun scan_art25FinPrm_firesOn稳赚不赔() {
        val r = AdSignageRule(
            "ad_signage_art25_fin_prm",
            "finance",
            "广告法 §25(一) + §58",
            listOf("稳赚不赔", "无风险", "保本高收益"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店理财产品稳赚不赔,签约保障")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art25_fin_prm", hits[0].ruleId)
    }

    @Test
    fun scan_art26RePrm_firesOn升值回报() {
        val r = AdSignageRule(
            "ad_signage_art26_re_prm",
            "realestate",
            "广告法 §26(一) + §58",
            listOf("升值回报", "投资回报", "学区房包入学"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本楼盘升值回报率高,五年翻倍")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art26_re_prm", hits[0].ruleId)
    }

    @Test
    fun scan_art9AbsPct_firesOn100Pct() {
        val r = AdSignageRule(
            "ad_signage_art9_abs_pct",
            "absolute",
            "广告法 §9(三) — 百分比极限词",
            listOf("100%", "百分百", "百分之百"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("100% 满意的产品,口碑保证")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art9_abs_pct", hits[0].ruleId)
    }

    @Test
    fun scan_art9EduAbs_firesOn最强师资() {
        val r = AdSignageRule(
            "ad_signage_art9_edu_abs",
            "education",
            "广告法 §9(三) + §24",
            listOf("最好", "最强师资", "第一"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本校最强师资,签约培养")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_art9_edu_abs", hits[0].ruleId)
    }

    @Test
    fun scan_eduArt24Recommendation_firesOn研究院推荐() {
        val r = AdSignageRule(
            "ad_signage_edu_art24_recommendation",
            "education",
            "广告法 §24(3) + §58",
            listOf("研究院推荐", "学会推荐", "协会推荐"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本课程由研究院推荐,权威保障")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_edu_art24_recommendation", hits[0].ruleId)
    }

    @Test
    fun scan_finArt25Unlawful_firesOn本金保障() {
        val r = AdSignageRule(
            "ad_signage_fin_art25_unlawful",
            "finance",
            "广告法 §25(一) + §58 / 银发〔2019〕316号 §3(二)",
            listOf("零风险", "无风险收益", "本金保障"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本理财本金保障,签约保收益")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_fin_art25_unlawful", hits[0].ruleId)
    }

    @Test
    fun scan_medArt7Compare_firesOn最强医院() {
        val r = AdSignageRule(
            "ad_signage_med_art7_compare",
            "medical",
            "医疗广告管理办法 §7 / 广告法 §16(三)",
            listOf("最好医院", "最强医院", "第一医院"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本地区最强医院,联合门诊")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_med_art7_compare", hits[0].ruleId)
    }

    @Test
    fun scan_medArt7Technicality_firesOn治疗技术() {
        val r = AdSignageRule(
            "ad_signage_med_art7_technicality",
            "medical",
            "医疗广告管理办法 §7(5) / 广告法 §16",
            listOf("处方药", "疗法", "治疗技术"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("独家治疗技术,签约见效")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_med_art7_technicality", hits[0].ruleId)
    }

    @Test
    fun scan_medicalArt6Producer_firesOn医用() {
        val r = AdSignageRule(
            "ad_signage_medical_art6_producer",
            "medical",
            "医疗器械广告审查发布标准 §6 + §9",
            listOf("医疗器械", "医用", "医用设备"),
            Severity.Violation,
        )
        // 文本只含 "医用",不含 "医用设备"/"医疗器械" 子串(避免 AC 同时命中多条关键词)
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店售卖医用产品,合规上市")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_medical_art6_producer", hits[0].ruleId)
    }

    @Test
    fun scan_medicalArt6Registerno_firesOn注册证号() {
        val r = AdSignageRule(
            "ad_signage_medical_art6_registerno",
            "medical",
            "医疗器械广告审查发布标准 §6(三)",
            listOf("国械注准", "国械注许", "注册证号"),
            Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店产品注册证号缺失,违规发布")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_medical_art6_registerno", hits[0].ruleId)
    }

    @Test
    fun scan_outdoorArt10Misleading_firesOn国家免检() {
        val r = AdSignageRule(
            "ad_signage_outdoor_art10_misleading",
            "outdoor",
            "广告法 §28 + 户外广告登记管理规定 §10",
            listOf("权威推荐", "专家推荐", "国家免检"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店品牌国家免检产品,放心购买")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_outdoor_art10_misleading", hits[0].ruleId)
    }

    @Test
    fun scan_outdoorCityArt32Heritage_firesOn5A景区() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_heritage",
            "outdoor",
            "广告法 §32(二) / 城市市容和环境卫生管理条例 §11",
            listOf("景区内", "自然保护区", "5A 景区"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本广告位于 5A 景区核心地带,违规设置")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_outdoor_city_art32_heritage", hits[0].ruleId)
    }

    @Test
    fun scan_outdoorCityArt32Municipal_firesOn配电箱() {
        val r = AdSignageRule(
            "ad_signage_outdoor_city_art32_municipal",
            "outdoor",
            "广告法 §32(一) / 城市市容和环境卫生管理条例 §11",
            listOf("消防栓", "配电箱", "燃气调压站"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本广告覆盖配电箱外壳,违规张贴")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_outdoor_city_art32_municipal", hits[0].ruleId)
    }

    @Test
    fun scan_reArt26PriceViolation_firesOn最低价() {
        val r = AdSignageRule(
            "ad_signage_re_art26_price_violation",
            "realestate",
            "广告法 §26(五) + §58 / 房地产广告发布规定 §4",
            listOf("最低价", "一口价", "封顶价"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本楼盘全市最低价,签约保价")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_re_art26_price_violation", hits[0].ruleId)
    }

    @Test
    fun scan_signageOtcLabel_firesOnOTC() {
        val r = AdSignageRule(
            "ad_signage_signage_otc_label",
            "signage",
            "广告法 §15 + §57 / OTC 标识管理办法",
            listOf("OTC", "非处方药", "甲类非处方"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店 OTC 标识齐全,合规销售")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_signage_otc_label", hits[0].ruleId)
    }

    @Test
    fun scan_signageArt30SelfPublish_firesOn个人发布() {
        val r = AdSignageRule(
            "ad_signage_signage_art30_self_publish",
            "signage",
            "广告法 §30 + §59",
            listOf("无证经营", "未取得广告发布资质", "个人发布"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店为个人发布,未经工商登记")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_signage_art30_self_publish", hits[0].ruleId)
    }

    @Test
    fun scan_veterinaryArt4Endorsement_firesOn学会推荐() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art4_endorsement",
            "veterinary",
            "兽药广告审查发布规定 §4(五) / 广告法 §16(四)",
            listOf("专家推荐", "研究院推荐", "学会推荐"),
            Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品由学会推荐,权威机构背书")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_veterinary_art4_endorsement", hits[0].ruleId)
    }

    @Test
    fun scan_veterinaryArt5Deprecate_firesOn比X差() {
        val r = AdSignageRule(
            "ad_signage_veterinary_art5_deprecate",
            "veterinary",
            "兽药广告审查发布规定 §5 + 广告法 §28",
            listOf("不如", "比 X 差", "完胜同类"),
            Severity.Warning,
        )
        // 文本只含 "比 X 差"(空格被 normalizer 折叠),不含 "完胜同类"/"不如"
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本品对比同类产品比 X 差远了")
        assertEquals(1, hits.size)
        assertEquals("ad_signage_veterinary_art5_deprecate", hits[0].ruleId)
    }

    // --- 《广告法》第十一条第二款 absence 复合匹配(TDD, 2026-08-21 v0.1.15) ---
    //
    // 当 rule.sourceMarkers 非空时,matcher 跑两遍 AC:
    //   1. claim keywords → 收集 RuleHit
    //   2. source markers → 收集命中的 ruleIds
    // 最终过滤:对一个 absence rule,只要它的 source marker 在文本里出现,就
    // 抑制该 rule 的所有 claim hits(数据有出处 = 合法)。所有 117 条既有规则
    // 的 sourceMarkers = emptyList(),走的是旧路径,行为字节级不变。
    // `scan_emptySourceMarkers_legacyPathUnchanged` 在改动期间 pin 这条路径。

    @Test
    fun scan_absenceRule_firesWhenClaimPresentSourceAbsent() {
        val r = AdSignageRule(
            id = "ad_signage_art11_data_citation",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            keywords = listOf("万", "累计"),
            severity = Severity.Warning,
            sourceMarkers = listOf("据", "报告", "来源"),
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("全国技师超9万人｜累计服务超1000万次")
        assertEquals(1, hits.size)
        assertEquals(Severity.Warning, hits.first().severity)
        // matchedText 应记录 claim 关键词,不是 source
        assertTrue(hits.first().matchedText in listOf("万", "累计"))
    }

    @Test
    fun scan_absenceRule_doesNotFireWhenSourceMarkerPresent() {
        val r = AdSignageRule(
            id = "ad_signage_art11_data_citation",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            keywords = listOf("万"),
            severity = Severity.Warning,
            sourceMarkers = listOf("据", "报告"),
        )
        // claim 在,source "据 艾瑞" 也在 → absence 不成立
        val hits = AdSignageRuleMatcher(listOf(r)).scan("据艾瑞 2024 年报告,本品牌累计服务超1000万次")
        assertEquals(0, hits.size)
    }

    @Test
    fun scan_emptySourceMarkers_legacyPathUnchanged() {
        val r = AdSignageRule(
            id = "existing_rule",
            category = "signage",
            regulation = "x",
            keywords = listOf("最佳"),
            severity = Severity.Warning,
        )
        // sourceMarkers 默认 emptyList() → 旧规则行为不变
        val hits = AdSignageRuleMatcher(listOf(r)).scan("本店是当地最佳餐厅")
        assertEquals(1, hits.size)
    }

    @Test
    fun scan_absenceRule_absentSourceMarkerNullReturnsEmpty() {
        val r = AdSignageRule(
            id = "ad_signage_art11_data_citation",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            keywords = listOf("万"),
            severity = Severity.Warning,
            sourceMarkers = listOf("据"),
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("今天天气真好")
        assertEquals(0, hits.size)
    }

    @Test
    fun scan_absenceRule_sharedKeywordBetweenClaimAndSource_doesNotFire() {
        val r = AdSignageRule(
            id = "ad_signage_art11_data_citation",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            keywords = listOf("调查"),
            severity = Severity.Warning,
            sourceMarkers = listOf("调查"),  // 同词既是 claim 也是 source
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("调查显示 90% 用户首选")
        assertEquals(0, hits.size)
    }

    // --- 真实文案 fixture:东郊文案 (2026-08-21 v0.1.15 上线) ---
    //
    // 规则配置取自 app/src/main/assets/rules/ad_signage_rules.json 的
    // "ad_signage_art11_data_citation" 条目 (keywords / sourceMarkers 切片与
    // JSON 完全对齐)。fixture 文本是实地拍摄的东郊文案:
    //   "全国技师超9万人｜累计服务超1000万次"
    // claim 关键词 "万" / "累计" 命中(absence rule 触发),
    // 文本中无 source marker("据" / "报告" / "来源" / 年份字符串 等) →
    // 最终过滤后保留 1 条 Warning hit。

    @Test
    fun scan_dongjiao_realWorldFixture_triggersArt11() {
        val r = AdSignageRule(
            id = "ad_signage_art11_data_citation",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            // JSON keywords 切片(去掉全角 %/％ 等易与 punctuation 撞车的项,保留核心 token)
            keywords = listOf(
                "万人", "万次", "万家", "万份", "万件", "万店", "万瓶",
                "亿人", "亿次", "亿份", "亿元", "亿件",
                "百分之",
                "倍",
                "全国超", "全国第一", "全国领先",
                "累计", "累计用户", "累计服务", "累计销售",
                "同比增长", "环比增长", "增长率",
                "销量第一", "排名第一", "份额第一",
                "调查", "调查显示", "研究报告", "报告显示",
                "研究表明", "专家表示", "专家指出",
                "数据表明", "事实证明",
            ),
            // JSON sourceMarkers 切片(去掉"截至"等会与正文日期写法撞车的项,
            // 保留 "据" / "报告" / "来源" 等核心出处指示词 + 一组年份字符串样本)
            sourceMarkers = listOf(
                "出处", "来源", "数据来源", "据", "据某", "据该",
                "报告", "调查报告", "白皮书", "统计报告",
                "调查", "研究", "研究表明", "调查显示",
                "引用", "引自", "引证",
                "截止", "截止到", "统计于",
                "2024", "2024年", "2024年1月", "2024年6月", "2024年12月",
                "2025", "2025年", "2025年1月", "2025年6月",
            ),
            severity = Severity.Warning,
        )
        val text = "全国技师超9万人｜累计服务超1000万次"
        val hits = AdSignageRuleMatcher(listOf(r)).scan(text)
        val art11 = hits.filter { it.ruleId == "ad_signage_art11_data_citation" }
        assertEquals(1, art11.size)
        assertEquals(Severity.Warning, art11.first().severity)
        assertEquals("signage", art11.first().category)
        // matchedText 应是 claim 关键词,而不是 source marker;且非空
        val matched = art11.first().matchedText
        assertTrue("matchedText must not be empty, actual='$matched'", matched.isNotEmpty())
        assertTrue(
            "matchedText must be a claim keyword (not a source marker), actual='$matched'",
            matched in r.keywords,
        )
    }

    @Test
    fun scan_dongjiao_realWorldFixtureWithSourceMarker_doesNotFire() {
        // 反向 fixture:同一文案但加上出处,absence 不成立,无 hit
        val r = AdSignageRule(
            id = "ad_signage_art11_data_citation",
            category = "signage",
            regulation = "《广告法》第十一条第二款",
            keywords = listOf("万", "累计"),
            sourceMarkers = listOf("据", "2024年", "来源"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "据 2024 年艾瑞报告,本品牌全国技师超9万人,累计服务超1000万次"
        )
        assertEquals(0, hits.size)
    }

    // --- 《广告法》第二十七条 农作物种子 / 种养殖广告 产量 / 效益保证 ---
    @Test fun scan_art27SeedYieldGuarantee_firesOnBiZengchan() {
        val r = AdSignageRule(
            id = "ad_signage_art27_seed_yield_guarantee",
            category = "agricultural",
            regulation = "《广告法》第二十七条 + 第五十八条",
            keywords = listOf(
                "必增产", "保证增产", "确保增产", "承诺增产",
                "产量保证", "产量承诺", "高产保证", "保证丰产", "保证稳产",
                "效益保证", "效益承诺", "增产达", "亩产保证",
                "科学上无法验证"
            ),
            severity = Severity.Violation
        )
        // 真实 5_2011 玉米种子广告原文:右侧 billboard "必增产" + "服务电话:400-658-9878"
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "墅蓝·多行 玉米种子\n必增产\n增产必选\n服务电话:400-658-9878"
        )
        // 命中: 必增产 → 1 hit(absence 规则不适用 §27,所以走 legacy 路径,keyword 命中即报)
        // 注:"增产必选" 不在 keyword 列表中,不应命中
        assertEquals("必增产 应触发 §27", 1, hits.size)
        assertEquals("ad_signage_art27_seed_yield_guarantee", hits[0].ruleId)
        assertEquals(Severity.Violation, hits[0].severity)
        assertEquals("必增产", hits[0].matchedText)
    }

    @Test fun scan_art27SeedYieldGuarantee_firesOnGuaranteeIncrease() {
        val r = AdSignageRule(
            id = "ad_signage_art27_seed_yield_guarantee",
            category = "agricultural",
            regulation = "《广告法》第二十七条 + 第五十八条",
            keywords = listOf("必增产", "保证增产", "确保增产", "承诺增产"),
            severity = Severity.Violation
        )
        // "保证增产" / "确保增产" / "承诺增产" 都是 §27(二) 功效保证的不同写法
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "本品种保证增产,确保增产,承诺增产"
        )
        // 经 TextNormalizer 归一后:"保证增产" / "确保增产" / "承诺增产" 是 3 个 distinct keywords
        // (归一后仍是 3 个 distinct,因为它们字面不同)
        assertTrue("保证增产系列应命中 ≥3", hits.size >= 3)
        assertTrue(hits.all { it.ruleId == "ad_signage_art27_seed_yield_guarantee" })
        assertTrue(hits.all { it.severity == Severity.Violation })
    }

    @Test fun scan_art27SeedYieldGuarantee_doesNotFireOnNormalAgricultureCopy() {
        val r = AdSignageRule(
            id = "ad_signage_art27_seed_yield_guarantee",
            category = "agricultural",
            regulation = "《广告法》第二十七条 + 第五十八条",
            keywords = listOf("必增产", "保证增产", "确保增产", "承诺增产"),
            severity = Severity.Violation
        )
        // 正常种子广告:仅说"有效增加玉米单行数",没有保证/断言/承诺
        val hits = AdSignageRuleMatcher(listOf(r)).scan(
            "有效增加玉米单行数\n品种:青芒农业\n建桩待 1.5 亿种植 10 亿\n股票代码 Z:1526999"
        )
        assertEquals("正常种子广告文案不应触发 §27", 0, hits.size)
    }

    // --- Auto-decomposition:length>=5 keyword 自动 1-char-deletion 变体(2026-08-29 v0.1.32 落地) ---
    //
    // PP-OCRv6_small 在密集中文行上倾向于 drop/merge 1 字符(ver 2026-08-29 nova 6 实测:
    // 关键字 "血压血糖血脂降下去" 9 字 → OCR 退化为 "高压血糖血脂降下去" 8 字,丢首字 血)。
    // init 块对 length>=MIN_KEYWORD_FOR_VARIANTS (5) 的 keyword 自动注册所有
    // 1-char-deletion 变体,以 1 次 build-time 展开替代 fuzzy-match runtime cost。
    // 阈值 L>=5 通过 66-fixture cross-check 选定:见 AdSignageRuleMatcher.companion
    // 对象 KDoc。

    @Test fun scan_matches_ocr_one_char_drop_for_long_keyword() {
        // 真实 case #48 (66-fixture / nova 6 OCR):
        //   - keyword: "血压血糖血脂降下去" (9 字, length>=5 → 触发 1-char-deletion 变体)
        //   - OCR 退化形式: "高血压患者 本品 高压血糖血脂降下去 安全无忧"
        //     首字 血 被 OCR 吃,前面又插了个 高。AC trie 注册的 variant
        //     "压血糖血脂降下去" (drop 首字 血, 8 字) 是 substring,exact-substring 命中。
        // 注意:matchedText 是 AC 命中的字面子串 (= variant 长度),不包含
        // variant 前面那个被 OCR 多插进来的 高。
        val rule = AdSignageRule(
            id = "test-food-fn",
            category = "signage",
            regulation = "广告法 §17 + §58",
            keywords = listOf("血压血糖血脂降下去"),
            severity = Severity.Violation,
        )
        val m = AdSignageRuleMatcher(listOf(rule))

        // (a) OCR 退化形式 — 由 1-char-deletion variant 命中
        val hitsDegraded = m.scan("高血压患者 本品 高压血糖血脂降下去 安全无忧")
        assertEquals(
            "OCR 退化形式 '压血糖血脂降下去' 应通过 1-char-deletion variant 命中 keyword",
            1, hitsDegraded.size,
        )
        assertEquals("test-food-fn", hitsDegraded[0].ruleId)
        // matchedText 是 AC 命中的字面子串 — 8 字 variant,不包含 OCR 多插的 高
        assertEquals("压血糖血脂降下去", hitsDegraded[0].matchedText)

        // (b) 原 keyword 仍能命中 — 没有回归
        val hitsOriginal = m.scan("本品宣传 血压血糖血脂降下去 三高人群适用")
        assertEquals("原 keyword 9 字形式仍应命中", 1, hitsOriginal.size)
        assertEquals("test-food-fn", hitsOriginal[0].ruleId)
        assertEquals("血压血糖血脂降下去", hitsOriginal[0].matchedText)
    }

    @Test fun scan_no_fp_on_short_keyword_deletion() {
        // 回归保护:#13 #26 66-fixture OCR 含 "抗病高产"(植物抗病性描述,GT=art27_seed_yield_guarantee)。
        // 若 "抗病毒" (3 字, length<5) 被分解为变体 "抗病",则 "抗病高产" 会 FP 命中
        // disease_prevention 规则。threshold L>=5 必须保证 length<5 keyword **不分解**。
        val rule = AdSignageRule(
            id = "test-disease-prev",
            category = "signage",
            regulation = "广告法 §17 + §58",
            // 真实 ad_signage_signage_food_function_claim keywords 中的短 keyword
            keywords = listOf("抗病毒", "控糖", "稳血糖"),
            severity = Severity.Violation,
        )
        val m = AdSignageRuleMatcher(listOf(rule))

        // (a) length<5 keyword 不应被分解 — "抗病高产" 不应命中 "抗病毒" 的任何变体
        val hitsFP = m.scan("豌豆种子 抗病高产 适合北方栽培")
        assertTrue(
            "length<5 keyword '抗病毒' 必须不分解;否则 '抗病高产' 会 FP 命中 disease_prevention",
            hitsFP.isEmpty(),
        )

        // (b) 原 keyword "抗病毒" 自身仍正常命中 — exact substring match
        val hitsReal = m.scan("本品抗病毒消炎,有效预防流感")
        assertEquals("原 keyword '抗病毒' 应精确命中", 1, hitsReal.size)
        assertEquals("test-disease-prev", hitsReal[0].ruleId)
    }

    @Test fun scan_art28bFakeData_firesOnBuErZhiXuan() {
        // 真实 case #61 (66-fixture / audit_gaps.md 标注):
        //   - ad_signage_art9_abs_top 已含 "不二之选" (L119 of ad_signage_rules.json)
        //   - ad_signage_art28b_fake_data **之前没有** "不二之选" → #61 GT 第 3 条规则
        //     永远命中不了,FULL 升不上去。
        // 修复:在 art28b_fake_data keywords 末尾加 "不二之选"。同一 OCR 出现 "不二之选"
        // 时双命中(art9_abs_top + art28b_fake_data),合规审查上正确(同一事实同时违反
        // 绝对化条款 §9 + 虚假数据条款 §28b)。
        val art9 = AdSignageRule(
            id = "ad_signage_art9_abs_top",
            category = "absolute",
            regulation = "广告法 §9(三)",
            keywords = listOf("不二之选", "不二选择"),
            severity = Severity.Warning,
        )
        val art28b = AdSignageRule(
            id = "ad_signage_art28b_fake_data",
            category = "absolute",
            regulation = "广告法 §28(二)~(五) + §55",
            // 模拟扩 keyword 后的 art28b_fake_data 完整 keywords 子集(测试只关心"不二之选")
            keywords = listOf("销量第一", "全国第一", "不二之选"),
            severity = Severity.Warning,
        )
        val m = AdSignageRuleMatcher(listOf(art9, art28b))

        // (a) 纯 "不二之选" — 应双命中 art9_abs_top + art28b_fake_data
        val hits = m.scan("不二之选")
        assertEquals(
            "'不二之选' 必须同时命中 art9_abs_top + art28b_fake_data(#61 GT 第 3 条规则覆盖)",
            2, hits.size,
        )
        val ruleIds = hits.map { it.ruleId }.toSet()
        assertTrue(
            "必须含 art9_abs_top",
            "ad_signage_art9_abs_top" in ruleIds,
        )
        assertTrue(
            "必须含 art28b_fake_data(扩 keyword 后)",
            "ad_signage_art28b_fake_data" in ruleIds,
        )

        // (b) #61 fixture 风格的复合文本 — 三规则都应命中(art9_abs_top + art28b_fake_data + art11)
        //   注:此 test 不构造 art11,只验证两个 absolute 规则双命中
        val hitsCompound = m.scan("哈尔滨排名第一 不二之选 公考培训")
        assertEquals(
            "复合文本 '不二之选' 部分应双命中",
            2, hitsCompound.size,
        )
        assertEquals("不二之选", hitsCompound[0].matchedText)
        assertEquals("不二之选", hitsCompound[1].matchedText)
    }

    @Test fun scan_foodFunctionClaim_firesOnFengjiaoAndFengWangjiang() {
        // 真实 case #19 (66-fixture / audit_gaps.md 描述):
        //   - 配料表含「蜂王浆冻干粉 + 蜂胶 + 蜂蜜」,宣传语含「改善营养 补充脑力」
        //   - 旧 keywords 没有 "蜂胶" / "蜂王浆" / "灵芝孢子" → 即使 OCR 识出也命中不了
        // 修复:food_function_claim keywords 加 蜂胶 / 蜂王浆 / 灵芝孢子。覆盖 #19 + 类似
        // 蜂产品/保健食品 fixture。
        val fn = AdSignageRule(
            id = "ad_signage_signage_food_function_claim",
            category = "signage",
            regulation = "广告法 §17 + §18 + §58",
            // 模拟扩 keyword 后的 food_function_claim 完整 keywords 子集
            keywords = listOf("蜂胶", "蜂王浆", "灵芝孢子", "提高人体免疫力", "改善营养"),
            severity = Severity.Violation,
        )
        val m = AdSignageRuleMatcher(listOf(fn))

        // (a) "蜂胶" 单独出现 — 配料表场景
        val hitsFengjiao = m.scan("本品含蜂胶成分,适用人群广")
        assertEquals("'蜂胶' 必须命中 food_function_claim(#19 配料表关键词)", 1, hitsFengjiao.size)
        assertEquals("蜂胶", hitsFengjiao[0].matchedText)

        // (b) "蜂王浆冻干粉" — #19 audit 描述实际 OCR 文本形式
        val hitsFwj = m.scan("配料:蜂王浆冻干粉、蜂胶、蜂蜜")
        val ruleIds = hitsFwj.map { it.ruleId }.toSet()
        assertTrue(
            "复合配料表文本应至少命中 food_function_claim",
            "ad_signage_signage_food_function_claim" in ruleIds,
        )
        // 应至少命中 蜂胶 + 蜂王浆 两次(去重后 2 个 unique keyword)
        assertEquals(
            "复合文本应命中蜂胶 + 蜂王浆 两条 keyword(去重 2)",
            2, hitsFwj.size,
        )

        // (c) "灵芝孢子" — 保健品场景
        val hitsLz = m.scan("破壁灵芝孢子粉 增强免疫")
        assertEquals("'灵芝孢子' 必须命中 food_function_claim", 1, hitsLz.size)
        assertEquals("灵芝孢子", hitsLz[0].matchedText)
    }

    @Test fun scan_diseasePrevention_firesOnJiangXueTangAndJiangSanGao() {
        // 真实 case #48 (66-fixture / audit_gaps.md GT 标注):
        //   - GT 第 2 条规则 = ad_signage_signage_disease_prevention
        //   - audit 期望此规则命中「降三高/降血糖/降血脂/降血压」类具体疾病治疗承诺
        //   - 旧 keywords 没有这 4 个具体承诺 → matcher 命中不到 disease_prevention
        // 修复:disease_prevention keywords 加 降血糖 / 降三高 / 降血压 / 降血脂。
        val dp = AdSignageRule(
            id = "ad_signage_signage_disease_prevention",
            category = "signage",
            regulation = "广告法 §17 + §58",
            // 模拟扩 keyword 后的 disease_prevention 完整 keywords 子集
            keywords = listOf("降血糖", "降三高", "降血压", "降血脂", "治疗", "治愈"),
            severity = Severity.Warning,
        )
        val m = AdSignageRuleMatcher(listOf(dp))

        // (a) 单独 keyword 命中
        assertEquals(1, m.scan("本品可降血糖 适用三高人群").size)
        assertEquals(1, m.scan("降三高保健品 调节血脂血压").size)
        assertEquals(1, m.scan("高血压人群可降血压").size)
        assertEquals(1, m.scan("降血脂 软化血管").size)

        // (b) #48 fixture 风格的复合文本
        //   OCR 文本可能为「...血压血糖血脂降下去...」(audit 描述) 或含「降三高」等
        val hitsCompound = m.scan("本品降三高 适合血压血糖血脂降下去人群")
        assertEquals(
            "复合文本应命中 disease_prevention(降三高 + 降血糖 + 降血压 + 降血脂 多 keyword)",
            hitsCompound.size >= 1, true,
        )
        val ruleIds = hitsCompound.map { it.ruleId }.toSet()
        assertEquals(
            "至少包含 disease_prevention",
            setOf("ad_signage_signage_disease_prevention"),
            ruleIds,
        )
    }
}