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
    fun `package contains image, report json, manifest`() {
        val rawImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)  // PNG magic
        val imageProvider = StubImageProvider(rawImage)
        val report = ViolationReport(
            imageUri = Uri.parse("file:///tmp/test.jpg"),
            ocrText = "本店专治糖尿病",
            hits = listOf(
                RuleHit(
                    ruleId = "AD_LAW_007",
                    matchedText = "100% 有效",
                    category = "绝对化用语",
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

        assertTrue("image.jpg missing", "image.jpg" in entries)
        assertTrue("report.json missing", "report.json" in entries)
        assertTrue("manifest.txt missing", "manifest.txt" in entries)
        assertEquals(rawImage.size, entries.getValue("image.jpg").size)
        assertTrue(
            "report.json lacks matchedText",
            String(entries.getValue("report.json")).contains("100% 有效"),
        )
        assertTrue(
            "report.json lacks lawText",
            String(entries.getValue("report.json")).contains("第九条 广告不得有下列情形"),
        )
        assertTrue(
            "report.json lacks Chinese category label",
            String(entries.getValue("report.json")).contains("\"categoryLabel\": \"绝对化用语\""),
        )
        assertTrue(
            "manifest.txt lacks version",
            String(entries.getValue("manifest.txt")).contains("IceSpiritAI_Vision"),
        )
    }

    private class StubImageProvider(private val bytes: ByteArray) : ImageBytesProvider {
        override fun open(uri: Uri): ByteArray = bytes
    }
}
