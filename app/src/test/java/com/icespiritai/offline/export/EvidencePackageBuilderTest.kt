package com.icespiritai.offline.export

import android.net.Uri
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

// Robolectric is needed because `Uri.parse(...)` on `android.jar` under
// `unitTests.isReturnDefaultValues=true` returns null and trips Kotlin's
// platform-type null check on the non-nullable `ViolationReport.imageUri`.
// (Same pattern as `BitmapLoaderTest` in `app/src/test/.../ocr/`.)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EvidencePackageBuilderTest {

    @Test
    fun `package contains image and human-readable report txt`() {
        val rawImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)  // PNG magic
        val imageProvider = StubImageProvider(rawImage)
        val report = ViolationReport(
            imageUri = Uri.parse("file:///tmp/test.jpg"),
            ocrText = "本店专治糖尿病",
            hits = listOf(
                RuleHit(
                    ruleId = "AD_LAW_007",
                    matchedText = "100% 有效",
                    category = "absolute",
                    regulation = "《广告法》第 9 条",
                    severity = Severity.Violation,
                    lawText = "第九条 广告不得有下列情形：（三）使用“国家级”、“最高级”、“最佳”等用语。",
                ),
            ),
            timestampMs = 1_700_000_000_000L,
        )

        val out = ByteArrayOutputStream()
        EvidencePackageBuilder.build(
            report = report,
            imageProvider = imageProvider,
            out = out,
            appVersion = "0.0.0-test",
        )

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }

        // Phase 3.5 (2026-08-31): report.json + manifest.txt collapsed into a
        // single human-readable report.txt. Phone stock apps (Files / WPS /
        // 记事本) open it without a JSON viewer. The JSON file is gone.
        assertTrue("image.jpg missing", "image.jpg" in entries)
        assertTrue("report.txt missing", "report.txt" in entries)
        assertTrue("manifest.txt should be removed", "manifest.txt" !in entries)
        assertTrue("report.json should be removed", "report.json" !in entries)
        assertEquals(rawImage.size, entries.getValue("image.jpg").size)

        val reportTxt = String(entries.getValue("report.txt"))
        assertTrue("report.txt lacks matchedText", reportTxt.contains("100% 有效"))
        assertTrue("report.txt lacks lawText", reportTxt.contains("第九条 广告不得有下列情形"))
        assertTrue("report.txt lacks Chinese category label", reportTxt.contains("绝对化用语"))
        assertTrue("report.txt lacks header", reportTxt.contains("冰灵锐目"))
        assertTrue("report.txt lacks rule id", reportTxt.contains("AD_LAW_007"))
        assertTrue("report.txt lacks regulation", reportTxt.contains("《广告法》第 9 条"))
        assertTrue("report.txt lacks ocr text", reportTxt.contains("本店专治糖尿病"))
    }

    @Test
    fun `report txt renders empty hits gracefully`() {
        val imageProvider = StubImageProvider(byteArrayOf(1, 2, 3))
        val report = ViolationReport(
            imageUri = Uri.parse("file:///tmp/empty.jpg"),
            ocrText = "干净文本无命中",
            hits = emptyList(),
            timestampMs = 1_700_000_000_000L,
        )

        val txt = EvidencePackageBuilder.renderReport(report, appVersion = "0.0.0-test")

        // No hits → render an "(无命中)" placeholder rather than dropping
        // the section entirely. Confirms user sees the report DID run but
        // found nothing, vs. ambiguous empty body.
        assertTrue("empty report should not crash", txt.contains("冰灵锐目"))
        assertTrue("empty report must show placeholder", txt.contains("(无命中)"))
        assertTrue("empty report must include OCR text", txt.contains("干净文本无命中"))
    }

    private class StubImageProvider(private val bytes: ByteArray) : ImageBytesProvider {
        override fun open(uri: Uri): ByteArray = bytes
    }
}