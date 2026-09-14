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

    @Test fun `empty hits returns fallback then disclaimer`() {
        val report = ViolationReport(StubUri(), "", emptyList(), 0)
        val segs = SegmentedScript.build(report, BuildOptions.Default)
        assertEquals(listOf("未筛查出违规事项", "AI识别仅供参考,合规判断以现场检查为准"), segs.map { it.text })
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
        // v0.3.2: by default the TTS bucket does NOT include the regulation
        // citation (用户反馈 truncated 20 字 + "等" 念出来割裂;屏 UI 仍
        // 显示依据 + 法条原文,跟 TTS 是独立路径)。Opt-in via
        // `BuildOptions.Default.copy(includeLawCitation = true)` is tested
        // separately in `law citation opt-in includes regulation` below.
        assertTrue(
            "violation bucket should NOT contain 依据 by default: ${segs[1].text}",
            !segs[1].text.contains("依据"),
        )
        assertTrue("warning bucket label", segs[2].text.startsWith("警告:"))
        assertTrue("info bucket label", segs[3].text.startsWith("信息:"))
        assertTrue("disclaimer is last", segs[4].text.contains("AI识别仅供参考"))
        assertEquals("prefix hitIndex = -1", -1, segs[0].hitIndex)
        assertEquals("violation bucket hitIndex = 0 (first truncated hit)", 0, segs[1].hitIndex)
        assertEquals("warning bucket hitIndex = 1", 1, segs[2].hitIndex)
        assertEquals("info bucket hitIndex = 2", 2, segs[3].hitIndex)
        assertEquals("disclaimer hitIndex = -1", -1, segs[4].hitIndex)
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

    /**
     * v0.3.2: explicit opt-in for the law-citation suffix. The default
     * (`BuildOptions.Default.includeLawCitation = false`) omits the
     * citation because truncated 20-char + "等" reads poorly in audio.
     * Callers that still want the citation (e.g. a future settings
     * toggle) can pass `includeLawCitation = true` explicitly.
     */
    @Test fun `law citation opt-in includes regulation section name when lawText present`() {
        val hits = listOf(hit("无麸质", Severity.Violation, "GB 7718-2025 §5.1", "致敏原强制标示"))
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default.copy(includeLawCitation = true),
        )
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

    @Test fun `trailing disclaimer always last segment`() {
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

    @Test fun `default omits regulation section in TTS bucket`() {
        // v0.3.2: BuildOptions.Default.includeLawCitation = false — the
        // truncated 20-char + "等" citation reads poorly in audio and
        // 误导. The bucket should contain only the matched text.
        val hits = listOf(hit("100% 中国第一", Severity.Violation))
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default,
        )
        assertTrue("violation bucket should NOT contain 依据 by default: ${segs[1].text}", !segs[1].text.contains("依据"))
    }

    @Test fun `trailingDisclaimer false drops last disclaimer segment`() {
        val hits = listOf(hit("100% 中国第一", Severity.Violation))
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default.copy(trailingDisclaimer = false),
        )
        assertEquals(2, segs.size)  // prefix + bucket only, no disclaimer
    }

    @Test fun `domainPrefix prepends to count summary when present`() {
        val hits = listOf(hit("100% 中国第一", Severity.Violation))
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default.copy(domainPrefix = "广告招牌"),
        )
        assertTrue("prefix should start with domain: ${segs[0].text}", segs[0].text.startsWith("广告招牌。"))
    }

    @Test fun `topN equal hits size emits no truncation suffix`() {
        val hits = (1..5).map { hit("无麸质 $it", Severity.Violation) }
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default.copy(topN = 5),
        )
        // prefix + bucket + disclaimer (no 其余 X 项 suffix)
        assertEquals(3, segs.size)
        assertTrue("no 其余 suffix expected", segs.none { it.text.startsWith("其余") })
    }

    @Test fun `blank regulation omits citation even with opt-in`() {
        // v0.3.2 boundary: even when includeLawCitation is on, a hit
        // with empty `regulation` field must NOT emit a stray `,依据`.
        // Guards against future re-enablement of the citation feature
        // regressing on the blank-regulation case.
        val hits = listOf(RuleHit(
            ruleId = "r_no_reg", matchedText = "100% 中国第一", category = "absolute",
            regulation = "", severity = Severity.Violation, domain = "ad",
            lawText = "致敏原强制标示",
        ))
        val segs = SegmentedScript.build(
            ViolationReport(StubUri(), "", hits, 0),
            BuildOptions.Default.copy(includeLawCitation = true),
        )
        assertTrue("bucket should NOT contain 依据 when regulation blank: ${segs[1].text}", !segs[1].text.contains("依据"))
    }

    @Test fun `Positive severity counted in summary and emitted as bucket`() {
        val hits = listOf(
            hit("100% 中国第一", Severity.Violation),
            RuleHit(
                ruleId = "r_pos", matchedText = "SC认证齐全", category = "compliant",
                regulation = "GB 7718-2025 §4", severity = Severity.Positive, domain = "food",
            ),
        )
        val segs = SegmentedScript.build(ViolationReport(StubUri(), "", hits, 0), BuildOptions.Default)
        assertTrue("prefix should mention 1 条合规: ${segs[0].text}", segs[0].text.contains("1 条合规"))
        assertTrue("expected 合规 bucket segment", segs.any { it.text.startsWith("合规:") })
    }
}
