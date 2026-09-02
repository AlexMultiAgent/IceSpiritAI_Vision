package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v11 rule extension tests (2026-09-02,15 new rules across signage/restricted/medical).
 * Each test pins one new rule by exact ruleId + verbatim keywords list against
 * a representative OCR-style scan text. AC trie dedup + Phase 2.5 substring
 * dedup are applied per AdSignageRuleMatcher contract; expected hit count
 * is the number of DISTINCT normalized keywords in the scan text.
 */
class V11GeneratedTests {

    @Test fun scan_v11_signage_major_event_endorsement_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_major_event_endorsement",
            category = "signage",
            regulation = "《体育法》第五十二条 + 《广告法》第九条第(二)项 + 第五十七条",
            keywords = listOf("亚冬会官方指定", "全运会赞助商", "冬奥会赞助商", "冰雪同梦"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("亚冬会官方指定供应商,全运会赞助商,冰雪同梦,冬奥会赞助商")
        assertEquals(
            "v11 rule ad_signage_signage_major_event_endorsement must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_major_event_endorsement" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_restricted_tobacco_sports_sponsorship_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_restricted_tobacco_sports_sponsorship",
            category = "restricted",
            regulation = "《烟草广告管理暂行办法》第八条 + 《广告法》第二十二条 + 第四十二条",
            keywords = listOf("龙烟", "为东北足球加油", "伴绿茵", "龙腾四海"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("龙烟为东北足球加油,伴绿茵,龙腾四海,鸣谢烟草")
        assertEquals(
            "v11 rule ad_signage_restricted_tobacco_sports_sponsorship must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_restricted_tobacco_sports_sponsorship" })
        assertEquals("category must propagate", "restricted", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_signage_active_military_image_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_active_military_image",
            category = "signage",
            regulation = "《广告法》第九条第(七)项 + 《关于禁止利用现役军人形象进行商业广告宣传的通知》",
            keywords = listOf("现役军人", "本人饮品全免", "一身戎装", "致敬最可爱的人"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("现役军人本人饮品全免,一身戎装,致敬最可爱的人,兵哥哥")
        assertEquals(
            "v11 rule ad_signage_signage_active_military_image must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_active_military_image" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_signage_national_political_symbol_misuse_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_national_political_symbol_misuse",
            category = "signage",
            regulation = "《广告法》第九条第(一)项 + 第(七)项 + 《特殊标志管理条例》第十六条",
            keywords = listOf("天安门", "盛世华诞", "建国75周年", "华表"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("天安门形象,盛世华诞,建国75周年,国庆立牌,华表")
        assertEquals(
            "v11 rule ad_signage_signage_national_political_symbol_misuse must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_national_political_symbol_misuse" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_signage_peoples_republic_misuse_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_peoples_republic_misuse",
            category = "signage",
            regulation = "《广告法》第九条第(七)项 + 第五十七条",
            keywords = listOf("人民咖啡馆", "人民照相馆", "共和国", "中央商业字样"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("人民咖啡馆,人民照相馆,共和国,中央商业字样")
        assertEquals(
            "v11 rule ad_signage_signage_peoples_republic_misuse must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_peoples_republic_misuse" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_restricted_wildlife_product_ad_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_restricted_wildlife_product_ad",
            category = "restricted",
            regulation = "《中华人民共和国野生动物保护法》第二十七条 + 第三十一条",
            keywords = listOf("熊胆", "龙江特产", "麝香", "虎骨"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("熊胆,龙江特产,麝香,虎骨,犀牛角")
        assertEquals(
            "v11 rule ad_signage_restricted_wildlife_product_ad must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_restricted_wildlife_product_ad" })
        assertEquals("category must propagate", "restricted", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_signage_recruitment_income_commitment_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_recruitment_income_commitment",
            category = "signage",
            regulation = "《广告法》第二十五条第(一)项 + 《就业促进法》第三条 + 第五十八条",
            keywords = listOf("月入过万", "一天轻松300元", "高薪招聘", "日赚几百"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("月入过万,一天轻松300元,高薪招聘,日赚几百,保底月薪")
        assertEquals(
            "v11 rule ad_signage_signage_recruitment_income_commitment must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_recruitment_income_commitment" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_medical_insurance_commitment_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_medical_insurance_commitment",
            category = "medical",
            regulation = "《广告法》第十六条第五项 + 《医疗广告管理办法》第三条",
            keywords = listOf("有保险更放心", "平安承保", "质量险承保", "百万医疗险"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("有保险更放心,平安承保,百万医疗险,质量险承保")
        assertEquals(
            "v11 rule ad_signage_medical_insurance_commitment must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_medical_insurance_commitment" })
        assertEquals("category must propagate", "medical", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_signage_diplomatic_event_endorsement_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_diplomatic_event_endorsement",
            category = "signage",
            regulation = "《广告法》第九条第(二)项 + 第(三)项 + 第五十七条",
            keywords = listOf("东盟10国贵宾礼", "上合组织", "一带一路指定", "G20 官方"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("东盟10国贵宾礼,上合组织,一带一路指定,G20 官方,金砖国家")
        assertEquals(
            "v11 rule ad_signage_signage_diplomatic_event_endorsement must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_diplomatic_event_endorsement" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_signage_cctv_misuse_absolute_rank_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_cctv_misuse_absolute_rank",
            category = "signage",
            regulation = "《广告法》第九条第(三)项 + 第二十八条第二款第(二)项 + 第五十五条 + 第五十七条",
            keywords = listOf("CCTV优评", "央视推荐", "中国十大名小吃", "必吃榜"),
            severity = Severity.Violation,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("CCTV优评,央视推荐,中国十大名小吃,大众点评上榜,必吃榜")
        assertEquals(
            "v11 rule ad_signage_signage_cctv_misuse_absolute_rank must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_cctv_misuse_absolute_rank" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Violation, hits.first().severity)
    }

    @Test fun scan_v11_restricted_alcohol_emotional_release_inducement_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_restricted_alcohol_emotional_release_inducement",
            category = "restricted",
            regulation = "《广告法》第二十三条第(一)项 + 第五十七条",
            keywords = listOf("哈!我下班啦", "牛马", "开满释放", "无节制饮酒"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("哈!我下班啦,牛马,开满释放,痛快喝,无节制饮酒")
        assertEquals(
            "v11 rule ad_signage_restricted_alcohol_emotional_release_inducement must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_restricted_alcohol_emotional_release_inducement" })
        assertEquals("category must propagate", "restricted", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_medical_otc_display_outdoor_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_medical_otc_display_outdoor",
            category = "medical",
            regulation = "《广告法》第十五条 + 《药品、医疗器械、保健食品、特殊医学用途配方食品广告审查管理暂行办法》第五条 + 第十一条第(二)项",
            keywords = listOf("脚气水", "杀菌止痒", "皮肤病药品", "OTC户外陈列"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("脚气水,杀菌止痒,皮肤病药品,OTC户外陈列,药房招牌病种")
        assertEquals(
            "v11 rule ad_signage_medical_otc_display_outdoor must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_medical_otc_display_outdoor" })
        assertEquals("category must propagate", "medical", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_medical_aesthetic_treatment_language_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_medical_aesthetic_treatment_language",
            category = "medical",
            regulation = "《广告法》第十六条第(一)项 + 《医疗广告管理办法》第七条第(一)项 + 第五十八条",
            keywords = listOf("精准抗衰", "高定美学", "精准焕颜", "细胞级抗衰"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("精准抗衰,高定美学,精准焕颜,细胞级抗衰,医美超市")
        assertEquals(
            "v11 rule ad_signage_medical_aesthetic_treatment_language must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_medical_aesthetic_treatment_language" })
        assertEquals("category must propagate", "medical", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_medical_national_level_claim_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_medical_national_level_claim",
            category = "medical",
            regulation = "《广告法》第九条第(三)项 + 《医疗广告管理办法》第六条 + 第五十七条",
            keywords = listOf("国家三级", "国家级专科", "中央部委直属", "认准三级"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("国家三级,国家级专科,中央部委直属,认准三级,国家级科室")
        assertEquals(
            "v11 rule ad_signage_medical_national_level_claim must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_medical_national_level_claim" })
        assertEquals("category must propagate", "medical", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }

    @Test fun scan_v11_signage_international_award_claim_firesOnKeywords() {
        val r = AdSignageRule(
            id = "ad_signage_signage_international_award_claim",
            category = "signage",
            regulation = "《广告法》第二十八条第二款第(二)项、第(三)项 + 第五十五条",
            keywords = listOf("布鲁塞尔金奖", "蒙特奖金奖", "特等金奖", "巴黎食品博览会"),
            severity = Severity.Warning,
        )
        val hits = AdSignageRuleMatcher(listOf(r)).scan("布鲁塞尔金奖,蒙特奖金奖,特等金奖,亚洲品牌500强,巴黎食品博览会")
        assertEquals(
            "v11 rule ad_signage_signage_international_award_claim must fire on 4 distinct keyword(s) in scan text",
            4, hits.size,
        )
        assertTrue("every hit must stamp the rule id", hits.all { it.ruleId == "ad_signage_signage_international_award_claim" })
        assertEquals("category must propagate", "signage", hits.first().category)
        assertEquals("severity must propagate", Severity.Warning, hits.first().severity)
    }
}
