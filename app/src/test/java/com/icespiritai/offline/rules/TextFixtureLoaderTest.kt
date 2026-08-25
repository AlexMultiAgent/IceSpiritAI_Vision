package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TextFixtureLoaderTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `parse reads all 8 fields from a minimal frontmatter`() {
        val md = tmp.newFile("text_medical_ykzp_01.md")
        md.writeText(
            """---
来源: https://www.samr.gov.cn/x/2024/01/01
场景: 处罚通报
违规点: 药店宣传根治糖尿病
法律依据: 广告法 §16
原始违法广告语: |
  本品根治糖尿病,无效退款。
  拨打 138-0000-0000。
预期命中规则:
  - id: ad_signage_art16_med_abs
    severity: Violation
处罚结果: 罚款 10 万元
备注: 测试 fixture
---
# 标题

正文
""".trimIndent()
        )
        val f = TextFixtureLoader.parse(md)
        assertEquals("text_medical_ykzp_01", f.slug)
        assertEquals("medical", f.category)
        assertEquals("https://www.samr.gov.cn/x/2024/01/01", f.source)
        assertEquals("处罚通报", f.scene)
        assertEquals("药店宣传根治糖尿病", f.violationPoint)
        assertEquals("广告法 §16", f.legalBasis)
        assertEquals("本品根治糖尿病,无效退款。\n拨打 138-0000-0000。", f.originalAdText)
        assertEquals(1, f.expected.size)
        assertEquals("ad_signage_art16_med_abs", f.expected[0].id)
        assertEquals(Severity.Violation, f.expected[0].severity)
        assertEquals("罚款 10 万元", f.penalty)
        assertEquals("测试 fixture", f.remark)
    }

    @Test
    fun `parse reads multiple expected rules`() {
        val md = tmp.newFile("text_medical_ykzp_02.md")
        md.writeText(
            """---
来源: https://example.gov.cn/y
场景: 监管公示
违规点: 多规则触发
法律依据: 广告法 §16
原始违法广告语: |
  根治糖尿病 + 治愈率 95%
预期命中规则:
  - id: ad_signage_art16_med_abs
    severity: Violation
  - id: ad_signage_art16_med_health
    severity: Violation
处罚结果: 责令停止
---
""".trimIndent()
        )
        val f = TextFixtureLoader.parse(md)
        assertEquals(2, f.expected.size)
        assertTrue(f.expected.any { it.id == "ad_signage_art16_med_abs" })
        assertTrue(f.expected.any { it.id == "ad_signage_art16_med_health" })
    }

    @Test
    fun `parse category derived from slug second segment`() {
        val md = tmp.newFile("text_absolute_best_03.md")
        md.writeText(
            """---
来源: https://example.gov.cn/z
场景: 处罚通报
违规点: 绝对化用语
法律依据: 广告法 §9
原始违法广告语: |
  最佳品牌
预期命中规则:
  - id: ad_signage_art9_abs_top
    severity: Warning
处罚结果: 罚款 5 万元
---
""".trimIndent()
        )
        val f = TextFixtureLoader.parse(md)
        assertEquals("absolute", f.category)
    }

    @Test
    fun `parse throws when slug does not match text_category pattern`() {
        val md = tmp.newFile("medical_ykzp_01.md")  // missing text_ prefix
        md.writeText("---\n来源: x\n---\n")
        try {
            TextFixtureLoader.parse(md)
            assert(false) { "expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("text_<category>"))
        }
    }

    @Test
    fun `parse returns null remark when field omitted`() {
        val md = tmp.newFile("text_medical_ykzp_03.md")
        md.writeText(
            """---
来源: https://example.gov.cn/a
场景: 处罚通报
违规点: 药店
法律依据: 广告法 §16
原始违法广告语: |
  根治糖尿病
预期命中规则:
  - id: ad_signage_art16_med_abs
    severity: Violation
处罚结果: 罚款
---
""".trimIndent()
        )
        val f = TextFixtureLoader.parse(md)
        assertEquals(null, f.remark)
    }
}
