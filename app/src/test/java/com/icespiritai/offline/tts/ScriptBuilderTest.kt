package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.net.StubUri

class ScriptBuilderTest {

    private fun hit(text: String, sev: Severity) = RuleHit(
        ruleId = "r_$text", matchedText = text, category = "absolute",
        regulation = "广告法 §9", severity = sev,
    )

    @Test fun `empty hits still speaks the clean verdict`() {
        // v0.3.3 said 0 hits -> completely silent. The user revised that on
        // 2026-09-17: 「如果全为0,需要播一下"未发现违规用语"+兜底」 — the
        // glasses path has no screen, so silence was unreadable.
        val report = ViolationReport(StubUri(), "", emptyList(), 0)
        val segs = ScriptBuilder.buildSegments(report)
        assertEquals(1, segs.size)
        assertTrue(segs[0].text.contains(NO_VIOLATION_SPOKEN_TEXT))
    }

    @Test fun `single Violation hit emits prefix + bucket + disclaimer`() {
        val report = ViolationReport(StubUri(), "", listOf(hit("100% 中国第一", Severity.Violation)), 0)
        val segs = ScriptBuilder.buildSegments(report)
        assertEquals(3, segs.size)
        // segs[0] is count prefix
        assertTrue(segs[0].text.startsWith("共 1 条违规"))
        // segs[1] is violation bucket
        assertEquals(Severity.Violation, segs[1].severity)
        assertTrue(segs[1].text.contains("100% 中国第一"))
        // segs[2] is disclaimer
        assertTrue(segs[2].text.contains("AI识别仅供参考"))
    }

    @Test fun `multiple hits sort by severityRank descending into distinct buckets`() {
        val hits = listOf(
            hit("信息类提示", Severity.Info),
            hit("100% 中国第一", Severity.Violation),
            hit("国家级 特供", Severity.Warning),
        )
        val report = ViolationReport(StubUri(), "", hits, 0)
        val segs = ScriptBuilder.buildSegments(report)
        // 5 segments: prefix + 3 buckets + disclaimer
        assertEquals(5, segs.size)
        // Bucket order: Violation → Warning → Info (severityRank DESC)
        assertEquals(Severity.Violation, segs[1].severity)
        assertEquals(Severity.Warning, segs[2].severity)
        assertEquals(Severity.Info, segs[3].severity)
        assertTrue(segs[1].text.contains("100% 中国第一"))
        assertTrue(segs[2].text.contains("国家级 特供"))
        assertTrue(segs[3].text.contains("信息类提示"))
    }

    @Test fun `trims whitespace in matchedText within bucket`() {
        val report = ViolationReport(
            StubUri(), "", listOf(hit("  100%  ", Severity.Violation)), 0,
        )
        val segs = ScriptBuilder.buildSegments(report)
        val bucket = segs.first { !it.isMeta && it.severity == Severity.Violation }
        assertTrue("bucket should contain trimmed text: ${bucket.text}", bucket.text.contains("100%"))
        assertFalse("bucket should NOT contain extra spaces: ${bucket.text}", bucket.text.contains("  100%  "))
    }

    @Test fun `handles long text without truncation`() {
        val long = "违".repeat(500)
        val report = ViolationReport(
            StubUri(), "", listOf(hit(long, Severity.Violation)), 0,
        )
        val segs = ScriptBuilder.buildSegments(report)
        val bucket = segs.first { !it.isMeta && it.severity == Severity.Violation }
        assertTrue("bucket should contain full 500-char text", bucket.text.contains(long))
    }

    @Test fun `chinese-english mixed hits land in severity rank order within buckets`() {
        val hits = listOf(
            hit("Best in Class", Severity.Warning),
            hit("100% 排名第一", Severity.Violation),
        )
        val report = ViolationReport(StubUri(), "", hits, 0)
        val segs = ScriptBuilder.buildSegments(report)
        // 4 segments: prefix + Violation bucket + Warning bucket + disclaimer
        assertEquals(4, segs.size)
        val violationBucket = segs.first { !it.isMeta && it.severity == Severity.Violation }
        val warningBucket = segs.first { !it.isMeta && it.severity == Severity.Warning }
        assertTrue(violationBucket.text.contains("100% 排名第一"))
        assertTrue(warningBucket.text.contains("Best in Class"))
    }

    @Test fun `deprecated build still returns concatenated text for backward compat`() {
        val report = ViolationReport(StubUri(), "", listOf(hit("100% 中国第一", Severity.Violation)), 0)
        @Suppress("DEPRECATION")
        val text = ScriptBuilder.build(report)
        // Deprecated shim joins all segment texts — must include both bucket text AND disclaimer
        assertTrue("deprecated build should include hit text: $text", text.contains("100% 中国第一"))
        assertTrue("deprecated build should include disclaimer: $text", text.contains("AI识别仅供参考"))
    }
}
