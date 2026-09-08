package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Test
import android.net.StubUri

class ScriptBuilderTest {

    private fun hit(text: String, sev: Severity) = RuleHit(
        ruleId = "r_$text", matchedText = text, category = "absolute",
        regulation = "广告法 §9", severity = sev,
    )

    @Test fun `empty hits returns fallback text`() {
        val report = ViolationReport(
            imageUri = StubUri(), ocrText = "", hits = emptyList(), timestampMs = 0,
        )
        assertEquals("未筛查出违规事项,AI识别仅供参考", ScriptBuilder.build(report))
    }

    @Test fun `single hit wraps text in 命中违规 prefix`() {
        val report = ViolationReport(
            imageUri = StubUri(), ocrText = "", hits = listOf(hit("100% 中国第一", Severity.Violation)),
            timestampMs = 0,
        )
        assertEquals("命中违规:100% 中国第一。", ScriptBuilder.build(report))
    }

    @Test fun `multiple hits sort by severityRank descending`() {
        val hits = listOf(
            hit("信息类提示", Severity.Info),
            hit("100% 中国第一", Severity.Violation),
            hit("国家级 特供", Severity.Warning),
        )
        val report = ViolationReport(StubUri(), "", hits, 0)
        assertEquals(
            "命中违规:100% 中国第一。国家级 特供。信息类提示。",
            ScriptBuilder.build(report),
        )
    }

    @Test fun `trims whitespace in matchedText`() {
        val report = ViolationReport(
            StubUri(), "", listOf(hit("  100%  ", Severity.Violation)), 0,
        )
        assertEquals("命中违规:100%。", ScriptBuilder.build(report))
    }

    @Test fun `handles long text without truncation at 500 chars`() {
        val long = "违".repeat(500)
        val report = ViolationReport(
            StubUri(), "", listOf(hit(long, Severity.Violation)), 0,
        )
        // 不截断,信任 TTS 引擎自身限制(spec §6.2 — 极长文本由设备引擎处理)
        assertEquals("命中违规:$long。", ScriptBuilder.build(report))
    }

    @Test fun `chinese-english mixed ordering preserved after rank sort`() {
        val hits = listOf(
            hit("Best in Class", Severity.Warning),
            hit("100% 排名第一", Severity.Violation),
        )
        val report = ViolationReport(StubUri(), "", hits, 0)
        assertEquals(
            "命中违规:100% 排名第一。Best in Class。",
            ScriptBuilder.build(report),
        )
    }
}