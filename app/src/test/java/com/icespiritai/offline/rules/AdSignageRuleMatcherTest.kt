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
}