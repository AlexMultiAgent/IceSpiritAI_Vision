package com.icespiritai.offline.export

import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.AdCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidencePackageBuilder {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        report: ViolationReport,
        imageProvider: ImageBytesProvider,
        out: OutputStream,
        appVersion: String = "0.1.0",
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("image.jpg"))
            zip.write(imageProvider.open(report.imageUri))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("report.json"))
            val payload = buildJsonObject {
                put("timestampMs", JsonPrimitive(report.timestampMs))
                put("ocrText", JsonPrimitive(report.ocrText))
                put("hits", buildJsonArray {
                    report.hits.forEach { hit ->
                        add(buildJsonObject {
                            put("ruleId", JsonPrimitive(hit.ruleId))
                            put("matchedText", JsonPrimitive(hit.matchedText))
                            put("category", JsonPrimitive(hit.category))
                            put("categoryLabel", JsonPrimitive(AdCategory.displayName(hit.category)))
                            put("regulation", JsonPrimitive(hit.regulation))
                            put("lawText", JsonPrimitive(hit.lawText))
                            put("severity", JsonPrimitive(hit.severity.name))
                        })
                    }
                })
            }
            zip.write(json.encodeToString(JsonElement.serializer(), payload).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write(
                """
                IceSpiritAI_Vision evidence package
                Generated: ${report.timestampMs}
                AppVersion: $appVersion
                HitCount: ${report.hits.size}
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
    }

    fun toFile(
        report: ViolationReport,
        imageProvider: ImageBytesProvider,
        appVersion: String = "0.1.0",
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        build(report, imageProvider, buf, appVersion)
        return buf.toByteArray()
    }
}
