package com.icespiritai.offline.export

import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.ViolationReport
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidencePackageBuilder {

    fun build(
        report: ViolationReport,
        imageProvider: ImageBytesProvider,
        out: OutputStream,
        appVersion: String,
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("image.jpg"))
            zip.write(imageProvider.open(report.imageUri))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write(renderReport(report, appVersion).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun toFile(
        report: ViolationReport,
        imageProvider: ImageBytesProvider,
        appVersion: String,
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        build(report, imageProvider, buf, appVersion)
        return buf.toByteArray()
    }

    /**
     * Human-readable report rendering. Phase 3.5 (2026-08-31) replaced the
     * machine-oriented `report.json` (pretty-printed JSON the user couldn't
     * open on a phone without a JSON viewer) with `report.txt`. The
     * structure is intentionally plain so it can be opened in any text app
     * preinstalled on Android (Files / WPS / 记事本 etc.) and skim-read by
     * a regulator or business owner.
     *
     * Sections, in order:
     *   - 头部 metadata (timestamp / app version / hit count)
     *   - OCR 全文(规则引擎扫的就是这份文本)
     *   - 命中详情(每个 hit 一段,含 ruleId / matchedText / 类别 / 严重度 / 法规 / 法条原文)
     */
    @JvmStatic
    fun renderReport(report: ViolationReport, appVersion: String): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            .format(Date(report.timestampMs))
        val sb = StringBuilder()
        sb.appendLine("冰灵锐目 取证报告")
        sb.appendLine("================================================")
        sb.appendLine("生成时间: $ts")
        sb.appendLine("App 版本: $appVersion")
        sb.appendLine("命中数量: ${report.hits.size}")
        sb.appendLine()

        sb.appendLine("OCR 文本")
        sb.appendLine("================================================")
        sb.appendLine(report.ocrText)
        sb.appendLine()

        sb.appendLine("命中详情")
        sb.appendLine("================================================")
        if (report.hits.isEmpty()) {
            sb.appendLine("(无命中)")
        } else {
            report.hits.forEachIndexed { idx, hit ->
                sb.appendLine("[${idx + 1}] ${hit.matchedText}")
                sb.appendLine("    规则 ID:   ${hit.ruleId}")
                sb.appendLine(
                    "    类别:     ${CategoryDisplay.displayName(hit.domain, hit.category)} " +
                        "(${hit.domain}/${hit.category})",
                )
                sb.appendLine("    严重度:   ${hit.severity.name}")
                sb.appendLine("    法规依据: ${hit.regulation}")
                sb.appendLine("    法条原文: ${hit.lawText}")
                sb.appendLine()
            }
        }
        return sb.toString()
    }
}