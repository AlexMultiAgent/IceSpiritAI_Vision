package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import java.io.File

data class TextFixture(
    val slug: String,
    val category: String,
    val source: String,
    val scene: String,
    val violationPoint: String,
    val legalBasis: String,
    val originalAdText: String,
    val expected: List<ExpectedRule>,
    val penalty: String,
    val remark: String? = null,
)

data class ExpectedRule(
    val id: String,
    val severity: Severity,
)

object TextFixtureLoader {

    fun loadAll(dir: File): List<TextFixture> =
        dir.listFiles { f -> f.name.startsWith("text_") && f.name.endsWith(".md") }
            ?.map { parse(it) }
            ?.sortedBy { it.slug }
            ?: emptyList()

    fun parse(file: File): TextFixture {
        val content = file.readText(Charsets.UTF_8)
        val parts = content.split("---", limit = 3)
        require(parts.size >= 3) { "${file.name}: missing --- markers" }
        val fm = parts[1].trim()
        val lines = fm.lines()
        val slug = file.nameWithoutExtension
        // text_<category>_<scene>_<NN> → 4 segments: text, category, scene, NN
        val slugSegs = slug.split("_")
        check(slugSegs.size >= 4 && slugSegs[0] == "text") {
            "${file.name}: slug 不符合 text_<category>_<scene>_<NN> 模式"
        }
        // 合桶 category 名(如 internet_ad 是 2 段)需要拼接 slugSegs[1..2]。
        // 其他单段 category 直接取 slugSegs[1]。
        val category = if (slugSegs.size >= 5 &&
            slugSegs[1] == "internet" && slugSegs[2] == "ad"
        ) {
            "internet_ad"
        } else {
            slugSegs[1]
        }

        return TextFixture(
            slug = slug,
            category = category,
            source = extractString(lines, "来源"),
            scene = extractString(lines, "场景"),
            violationPoint = extractString(lines, "违规点"),
            legalBasis = extractString(lines, "法律依据"),
            originalAdText = extractMultiline(lines, "原始违法广告语"),
            expected = extractRuleList(lines, "预期命中规则"),
            penalty = extractString(lines, "处罚结果"),
            remark = extractStringOrNull(lines, "备注"),
        )
    }

    private fun extractString(lines: List<String>, key: String): String {
        val idx = lines.indexOfFirst { it.startsWith("$key:") }
        require(idx >= 0) { "missing key: $key" }
        val value = lines[idx].substringAfter(":").trim()
        require(value.isNotEmpty()) { "empty value for key: $key" }
        return value
    }

    private fun extractStringOrNull(lines: List<String>, key: String): String? {
        val idx = lines.indexOfFirst { it.startsWith("$key:") }
        if (idx < 0) return null
        val value = lines[idx].substringAfter(":").trim()
        return value.ifEmpty { null }
    }

    private fun extractMultiline(lines: List<String>, key: String): String {
        val idx = lines.indexOfFirst { it.startsWith("$key:") }
        require(idx >= 0) { "missing key: $key" }
        require(lines[idx].substringAfter(":").trim() == "|") {
            "multiline key $key must use | block scalar"
        }
        val block = mutableListOf<String>()
        var i = idx + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++; continue
            }
            if (line.startsWith(" ") || line.startsWith("\t")) {
                block.add(line.trim())
                i++
            } else {
                break
            }
        }
        return block.joinToString("\n")
    }

    private fun extractRuleList(lines: List<String>, key: String): List<ExpectedRule> {
        val idx = lines.indexOfFirst { it.startsWith("$key:") }
        require(idx >= 0) { "missing key: $key" }
        require(lines[idx].substringAfter(":").trim().isEmpty()) {
            "list key $key must have empty value (children on following indented lines)"
        }
        val items = mutableListOf<ExpectedRule>()
        var i = idx + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            if (!line.startsWith("  -")) break
            // "  - id: foo" → id = foo
            val firstLine = line.removePrefix("  -").trim()
            require(firstLine.startsWith("id:")) {
                "list item must start with id: — got '$firstLine'"
            }
            val id = firstLine.substringAfter(":").trim()
            i++
            // optional "    severity: Violation" on continuation lines
            var severity = Severity.Warning  // default
            while (i < lines.size && lines[i].startsWith("    ") && !lines[i].startsWith("  -")) {
                val cont = lines[i].trim()
                if (cont.startsWith("severity:")) {
                    severity = Severity.valueOf(cont.substringAfter(":").trim())
                }
                i++
            }
            items.add(ExpectedRule(id = id, severity = severity))
        }
        require(items.isNotEmpty()) { "list key $key is empty" }
        return items
    }
}
