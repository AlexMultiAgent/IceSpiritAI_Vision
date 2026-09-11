package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import android.net.StubUri

class SegmentedScriptTest {

    private fun hit(
        text: String,
        sev: Severity,
        reg: String = "GB 7718-2025 §5.1",
        lawText: String = "过敏原强制标示",
        domain: String = "ad",
    ) = RuleHit(
        ruleId = "r_$text", matchedText = text, category = "absolute",
        regulation = reg, severity = sev, domain = domain, lawText = lawText,
    )

    @Test fun `empty hits returns fallback text`() {
        val report = ViolationReport(StubUri(), "", emptyList(), 0)
        val segs = SegmentedScript.build(report, BuildOptions.Default)
        assertEquals(listOf("未筛查出违规事项,AI识别仅供参考"), segs.map { it.text })
    }

    @Test fun `severity grouping emits prefix + buckets + disclaimer`() {
        val hits = listOf(
            hit("100% 中国第一", Severity.Violation),
            hit("国家级 特供", Severity.Warning),
            hit("维生素A", Severity.Info),
        )
        val report = ViolationReport(StubUri(), "", hits, 0)
        val segs = SegmentedScript.build(report, BuildOptions.Default)
        // 5 segments: count prefix + 违规 bucket + 警告 bucket + 信息 bucket + disclaimer
        assertEquals(5, segs.size)
        assertTrue("prefix should start with 共 X 条违规: ${segs[0].text}", segs[0].text.startsWith("共 1 条违规"))
        assertTrue("violation bucket label", segs[1].text.startsWith("违规:"))
        assertTrue("violation bucket content", segs[1].text.contains("100% 中国第一"))
        assertTrue("violation bucket citation", segs[1].text.contains("GB 7718-2025 §5.1"))
        assertTrue("warning bucket label", segs[2].text.startsWith("警告:"))
        assertTrue("info bucket label", segs[3].text.startsWith("信息:"))
        assertTrue("disclaimer is last", segs[4].text.contains("AI识别仅供参考"))
    }

    @Test fun `count summary includes zero buckets`() {
        val hits = listOf(hit("100% 中国第一", Severity.Violation))
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default,
        )
        assertTrue(segs[0].text.contains("1 条违规"))
        assertTrue(segs[0].text.contains("0 条警告"))
        assertTrue(segs[0].text.contains("0 条信息"))
    }

    @Test fun `law citation per hit includes regulation section name when lawText present`() {
        val hits = listOf(hit("无麸质", Severity.Violation, "GB 7718-2025 §5.1", "致敏原强制标示"))
        val segs = SegmentedScript.build(ViolationReport(StubUri(), "", hits, 0), BuildOptions.Default)
        assertTrue("expected law section name in citation: ${segs[1].text}", segs[1].text.contains("GB 7718-2025 §5.1 致敏原强制标示"))
    }

    @Test fun `topN truncation marks suffix with omitted count`() {
        val hits = (1..12).map { hit("无麸质 $it", Severity.Violation) }
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default.copy(topN = 5),
        )
        // 4 segments: prefix + violation bucket (5 hits) + omitted suffix + disclaimer
        assertEquals(4, segs.size)
        assertTrue("expected 其余 7 项 in suffix: ${segs[2].text}", segs[2].text.contains("其余 7 项"))
    }

    @Test fun `trailing AI disclaimer always present in positive case`() {
        val hits = listOf(hit("100%", Severity.Violation))
        val segs = SegmentedScript.build(ViolationReport(StubUri(), "", hits, 0), BuildOptions.Default)
        assertTrue(segs.last().text.contains("AI识别仅供参考"))
    }

    @Test fun `Error state hits a dedicated segment`() {
        val error = com.icespiritai.offline.domain.AnalysisState.Error(
            message = "OCR 引擎未初始化",
            errorCode = ErrorCode.OCR_UNAVAILABLE,
        )
        val segs = SegmentedScript.buildError(error)
        assertEquals(1, segs.size)
        assertTrue(segs[0].text.contains("OCR 引擎未初始化"))
        assertTrue(segs[0].text.contains("AI识别仅供参考"))
    }
}
