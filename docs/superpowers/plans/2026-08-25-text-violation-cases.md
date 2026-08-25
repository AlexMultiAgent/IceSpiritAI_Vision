# 文本类违规案例 fixture 采集 + 规则回归测试实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `违规案例/` 下新增 30 条政府站一手文本 fixture(无图),新增 `TextFixtureLoader` + `AdSignageTextFixtureRegressionTest`,实现 `AdSignageRuleMatcher` 的精确 set match 规则回归自动化。

**Architecture:** 复用 `AdSignageMentorFiveImageRegressionTest` 的 pure-JUnit + 多路径 File-resolution + kotlinx-serialization JSON 加载 pattern;新增 `TextFixtureLoader` 用 ~100 行手写解析 frontmatter 块(无新依赖);新增 `AdSignageTextFixtureRegressionTest` 跑 3 个 @Test(精确 set match / ≥30 张数 / 13 桶覆盖)。

**Tech Stack:** Kotlin 2.4.10 / JUnit 4.13.2 / kotlinx-serialization-json 1.9.0 / Gradle 9.7 / AGP 9.3

---

## 文件结构

| 路径 | 状态 | 职责 |
|---|---|---|
| `app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoader.kt` | 新增 | 手写 frontmatter 解析器 + data class `TextFixture` / `ExpectedRule` |
| `app/src/test/java/com/icespiritai/offline/rules/AdSignageTextFixtureRegressionTest.kt` | 新增 | 3 个 @Test,精确 set match + ≥30 张 + 13 桶覆盖 |
| `违规案例/text_medical_*.md` | 新增 ×5 | medical 桶 |
| `违规案例/text_absolute_*.md` | 新增 ×4 | absolute 桶 |
| `违规案例/text_education_*.md` | 新增 ×3 | education 桶 |
| `违规案例/text_food_*.md` | 新增 ×3 | food 桶 |
| `违规案例/text_realestate_*.md` | 新增 ×3 | realestate 桶 |
| `违规案例/text_finance_*.md` | 新增 ×3 | finance 桶 |
| `违规案例/text_cosmetic_*.md` | 新增 ×2 | cosmetic 桶 |
| `违规案例/text_agricultural_*.md` | 新增 ×2 | agricultural 桶 |
| `违规案例/text_signage_*.md` | 新增 ×1 | signage 桶 |
| `违规案例/text_minor_*.md` | 新增 ×1 | minor 桶 |
| `违规案例/text_outdoor_*.md` | 新增 ×1 | outdoor 桶 |
| `违规案例/text_internet_ad_*.md` | 新增 ×1 | internet_ad 桶 |
| `违规案例/text_pestvet_*.md` | 新增 ×1 | pestvet 桶(pesticide + veterinary 合桶) |
| `违规案例/_text_plan.md` | 新增 | 采集日志(每桶 URL / 状态 / 失败原因) |

合计 30 fixture + 1 log + 1 loader + 1 test = 33 项新增。不动既有文件。

---

## 复用样板

`AdSignageMentorFiveImageRegressionTest.kt` 已验证的 pattern,直接复用:
- 多路径候选 `File(...).firstOrNull { exists && length > 100 }`(兼容 cwd 不一致)
- `Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(AdSignageRuleSet.serializer(), raw).rules`
- `AdSignageRuleMatcher(rules).scan(text): List<RuleHit>`

---

## Task 1: TextFixtureLoader.kt — 解析器骨架与 data class

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoader.kt`
- Create: `app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoaderTest.kt`(TDD 验证解析器)

- [ ] **Step 1: 写失败测试 — 解析最小 fixture**

新建 `app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoaderTest.kt`:

```kotlin
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
```

- [ ] **Step 2: 跑测试验证失败(预期: 编译失败,TextFixtureLoader 不存在)**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" \
  ./gradlew.bat testDebugUnitTest --tests "*TextFixtureLoaderTest*"
```
Expected: 编译失败,`Unresolved reference: TextFixtureLoader`

- [ ] **Step 3: 实现 TextFixtureLoader.kt**

新建 `app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoader.kt`:

```kotlin
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
        // text_<category>_<scene>_<NN> → ["text", "category", "scene", "NN", ...] → [1]
        val category = slug.split("_").getOrNull(1)
            ?: error("${file.name}: slug 不符合 text_<category>_<scene>_<NN> 模式")

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
```

- [ ] **Step 4: 跑测试验证通过**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" \
  ./gradlew.bat testDebugUnitTest --tests "*TextFixtureLoaderTest*"
```
Expected: 5/5 PASS

- [ ] **Step 5: 提交**

```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  git add app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoader.kt \
          app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoaderTest.kt && \
  git commit -m "feat(test): TextFixtureLoader 手写 frontmatter 解析器 + data class"
```

验证 commit hygiene:
```bash
git log -1 --format='%B' | grep -i 'co-authored-by' || echo "OK"
```

---

## Task 2: AdSignageTextFixtureRegressionTest — 3 个 @Test 骨架

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/rules/AdSignageTextFixtureRegressionTest.kt`

- [ ] **Step 1: 写测试骨架(预期所有 3 个 @Test 都失败 — 0 fixture)**

新建 `app/src/test/java/com/icespiritai/offline/rules/AdSignageTextFixtureRegressionTest.kt`:

```kotlin
package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 文本 fixture 规则回归测试。
 *
 * 来源:`违规案例/text_*.md`(无 .jpg)— 政府站一手「处罚通报」类文本。
 * 跑真实 ad_signage_rules.json(从 src/main/assets 加载),对每条 fixture 的
 * 「原始违法广告语」字段跑 AdSignageRuleMatcher.scan(),断言命中规则 ID
 * 集合 == fixture frontmatter 中声明的「预期命中规则」ID 集合(精确 set match)。
 *
 * 与 AdSignageMentorFiveImageRegressionTest 的差异:
 *   - 后者做 OCR→规则端到端 subset pin(必须命中)
 *   - 本测试做规则逻辑完整性 pin(命中集合不能漂)
 *
 * 失败模式:任何规则改动(新增 / 删除 / 挪关键词)若让 fixture 命中集合变化,
 * 测试即失败,迫使 fixture 同步更新 — 这是规则迭代的「反向压力」。
 */
class AdSignageTextFixtureRegressionTest {

    private fun loadRealRules(): List<AdSignageRule> {
        val candidates = listOf(
            File("src/main/assets/rules/ad_signage_rules.json"),
            File("app/src/main/assets/rules/ad_signage_rules.json"),
            File("app/build/generated/assets/rules/ad_signage_rules.json"),
            File("../src/main/assets/rules/ad_signage_rules.json"),
        )
        val jsonFile = candidates.firstOrNull { it.exists() && it.length() > 100 }
            ?: error("expected ad_signage_rules.json at: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
        val raw = jsonFile.readText(Charsets.UTF_8)
        return Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(AdSignageRuleSet.serializer(), raw).rules
    }

    private fun fixturesDir(): File {
        val candidates = listOf(
            File("违规案例"),
            File("../违规案例"),
            File("app/../违规案例"),
            File("app/build/../违规案例"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("expected 违规案例/ dir at: " +
                "${candidates.joinToString { it.absolutePath }} " +
                "(cwd=${System.getProperty("user.dir")})")
    }

    @Test
    fun `every text fixture has matching rule hits (exact set)`() {
        val rules = loadRealRules()
        val ruleIds = rules.map { it.id }.toSet()
        val matcher = AdSignageRuleMatcher(rules)
        val cases = TextFixtureLoader.loadAll(fixturesDir())

        assertTrue("no text fixtures found in ${fixturesDir()}", cases.isNotEmpty())

        cases.forEach { c ->
            // 1. fixture 引用的规则 ID 必须在白名单内(防止 fixture 引用已废弃规则)
            c.expected.forEach { exp ->
                assertTrue(
                    "fixture ${c.slug} references unknown rule ${exp.id}",
                    exp.id in ruleIds,
                )
            }
            // 2. 命中规则集合精确匹配(严苛 pin,迫使规则迭代时主动审视 fixture)
            val hits = matcher.scan(c.originalAdText)
            val actualIds = hits.map { it.ruleId }.toSet()
            val expectedIds = c.expected.map { it.id }.toSet()
            assertEquals(
                "fixture ${c.slug} rule hit mismatch — " +
                "expected=$expectedIds actual=$actualIds",
                expectedIds, actualIds,
            )
        }
    }

    @Test
    fun `minimum 30 text fixtures collected`() {
        val cases = TextFixtureLoader.loadAll(fixturesDir())
        assertTrue(
            "got ${cases.size} text fixtures, need >= 30",
            cases.size >= 30,
        )
    }

    @Test
    fun `all 13 buckets represented across fixtures`() {
        val cases = TextFixtureLoader.loadAll(fixturesDir())
        val coveredCategories = cases.map { it.category }.toSet()
        val expectedCategories = setOf(
            "medical", "absolute", "education", "food", "realestate", "finance",
            "cosmetic", "agricultural", "signage", "minor", "outdoor",
            "internet_ad", "pestvet",
        )
        assertTrue(
            "missing categories: ${expectedCategories - coveredCategories}",
            coveredCategories.containsAll(expectedCategories),
        )
    }
}
```

- [ ] **Step 2: 跑测试验证失败(预期:第一个测试失败"no text fixtures found",第三/四/五同)**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" \
  ./gradlew.bat testDebugUnitTest --tests "*AdSignageTextFixtureRegressionTest*"
```
Expected: 1/3 PASS(`minimum 30 text fixtures collected` 因为 size=0 < 30 失败),`every text fixture has matching rule hits` 失败(`cases.isEmpty()` assert 触发),`all 13 buckets represented` 失败(覆盖集合空)

- [ ] **Step 3: 提交**

```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  git add app/src/test/java/com/icespiritai/offline/rules/AdSignageTextFixtureRegressionTest.kt && \
  git commit -m "feat(test): AdSignageTextFixtureRegressionTest 骨架(0 fixture,3 个失败 @Test)"
```

---

## Task 3: 采集 medical 桶(5 条 fixture)

**Files:**
- Create: `违规案例/text_medical_<scene>_01.md` ... `text_medical_<scene>_05.md`
- Create/Append: `违规案例/_text_plan.md`

- [ ] **Step 1: WebSearch 5 条 medical 一手 URL**

关键词组合(各跑一次):
- `site:samr.gov.cn 医疗广告 处罚 根治`
- `site:samr.gov.cn 药品广告 处罚 100%有效`
- 各省监管局:`site:sc.amr.gov.cn 医疗广告 处罚通报` / `site:gd.amr.gov.cn 药店 虚假宣传 处罚`

筛选标准:
- 一手 `.gov.cn` 域名
- 含「违法广告语原文」段落
- 含具体罚款金额 + 责令停止
- 案例时间 2023-01-01 之后(规则库 v6 关键词表建立后的违规)

- [ ] **Step 1.5: WebFetch 验证(若不可用 → 走 snippet 重建路径)**

  每个 URL 都尝试 WebFetch(`https://<gov-host>/<path>`):
  - 成功:从页面提取「违法广告语原文 / 处罚金额 / 通报日期」,作为 fixture 正文 + 备注的 source of truth
  - 失败(`Unable to verify if domain` 或类似):在 `_text_plan.md` 追加「采集方法说明」段,本桶 fixture 状态记为 `URL-OK / content-snippet`,正文重建自 WebSearch snippet;fixture 的 `备注` 字段加一句「内容来自 WebSearch snippet,非 WebFetch 直读」

- [ ] **Step 2: WebFetch 每条 URL,提取 4 个核心字段**

每个 URL 抽取:
- 违法广告语原文(完整多行)
- 引用法条(广告法 §16 / 医疗广告管理办法 §6 等)
- 处罚结果(罚款 X 万元 + 责令停止)
- 通报日期 / 通报机构

- [ ] **Step 3: 写 5 个 fixture 文件**

slug 模式:`text_medical_ykzp_01.md`(药店)、`text_medical_zszn_01.md`(诊所)、`text_medical_wzyl_01.md`(互联网医疗)、`text_medical_ylqx_01.md`(医疗器械)、`text_medical_tjyp_01.md`(体检预约)。

frontmatter 字段填充规范见 `docs/superpowers/specs/2026-08-25-text-violation-cases-design.md` §4。**关键校验**:`预期命中规则.id` 必须在 `ad_signage_rules.json` 中存在(122 条白名单内)。

例(`text_medical_ykzp_01.md`):

```markdown
---
来源: https://www.samr.gov.cn/...  # 实际抓到的一手 URL
场景: 处罚通报
违规点: 药店宣传根治糖尿病
法律依据: 广告法 §16 第一款第（一）项
原始违法广告语: |
  本品中药制剂专治糖尿病,
  三个月根治,无效全额退款。
  拨打 138-0000-0000 咨询订购。
预期命中规则:
  - id: ad_signage_art16_med_abs
    severity: Violation
处罚结果: 罚款 20 万元,责令停止发布
备注: 2024 年市场监管总局通报
---

# 某药店"根治糖尿病"虚假宣传案(2024)

[正文描述:为何构成违规 + 法条原文摘抄 + 同类常见变体]
```

- [ ] **Step 4: 跑测试验证 5 条全绿**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" \
  ./gradlew.bat testDebugUnitTest --tests "*AdSignageTextFixtureRegressionTest*"
```
Expected: 3/3 PASS(`every text fixture has matching rule hits` 现在有 5 条 fixture,需逐一精确 set match;若任一失败,说明 fixture 预期命中规则集合与 RuleMatcher 实际命中不一致,回去检查 fixture 字段)

- [ ] **Step 5: 追加到 _text_plan.md,记录每条 URL / 状态**

```markdown
# text fixture 采集日志

## medical 桶(5 条)

- [x] text_medical_ykzp_01 — URL / 状态: OK / 2024-XX-XX 通报
- [x] text_medical_zszn_01 — URL / 状态: OK / ...
- [x] text_medical_wzyl_01 — URL / 状态: OK / ...
- [x] text_medical_ylqx_01 — URL / 状态: OK / ...
- [x] text_medical_tjyp_01 — URL / 状态: OK / ...
```

- [ ] **Step 6: 提交**

```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  git add 违规案例/text_medical_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): medical 桶 5 条一手政府站文本 fixture"
```

---

## Task 4: 采集 absolute 桶(4 条 fixture)

**Files:**
- Create: `违规案例/text_absolute_<scene>_01.md` ... `text_absolute_<scene>_04.md`
- Append: `违规案例/_text_plan.md`

- [ ] **Step 1-6**: 同 Task 3,但目标桶 = absolute(4 条)

- 关键词:`site:samr.gov.cn 绝对化用语 处罚`、`site:samr.gov.cn 最佳 处罚`、`国家级 处罚 通报`、`顶级 处罚 广告`
- slug 模式:`text_absolute_best_01.md`、`text_absolute_first_01.md`、`text_absolute_top_01.md`、`text_absolute_zjzl_01.md`(国家级)

跑完测试 4/3 PASS 后提交:

```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  git add 违规案例/text_absolute_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): absolute 桶 4 条文本 fixture"
```

---

## Task 5: 采集 education 桶(3 条 fixture)

**Files:**
- Create: `违规案例/text_education_<scene>_01.md` ... `text_education_<scene>_03.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 教育培训 保过 处罚`、`职业培训 包过 通报`、`保过 包过 处罚`
- slug 模式:`text_education_baoguo_01.md`(保过)、`text_education_tuijian_01.md`(院校推荐)、`text_education_zyzs_01.md`(职业证书)

提交:
```bash
git add 违规案例/text_education_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): education 桶 3 条文本 fixture"
```

---

## Task 6: 采集 food 桶(3 条 fixture)

**Files:**
- Create: `违规案例/text_food_<scene>_01.md` ... `text_food_<scene>_03.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 保健食品 处罚`、`普通食品 增强免疫力 处罚`、`保健食品 虚假宣传 通报`
- slug 模式:`text_food_bjsp_01.md`(保健食品)、`text_food_tssj_01.md`(特殊膳食)、`text_food_sldz_01.md`(散装食品)

提交:
```bash
git add 违规案例/text_food_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): food 桶 3 条文本 fixture"
```

---

## Task 7: 采集 realestate 桶(3 条 fixture)

**Files:**
- Create: `违规案例/text_realestate_<scene>_01.md` ... `text_realestate_<scene>_03.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 房地产 升值 处罚`、`学区房 处罚 通报`、`无证销售 房地产 处罚`
- slug 模式:`text_realestate_sz_01.md`(升值)、`text_realestate_xqf_01.md`(学区房)、`text_realestate_wzj_01.md`(无证销售)

提交:
```bash
git add 违规案例/text_realestate_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): realestate 桶 3 条文本 fixture"
```

---

## Task 8: 采集 finance 桶(3 条 fixture)

**Files:**
- Create: `违规案例/text_finance_<scene>_01.md` ... `text_finance_<scene>_03.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 金融 稳赚不赔 处罚`、`保本高收益 处罚 通报`、`无证理财 处罚 通报`
- slug 模式:`text_finance_bbxj_01.md`(保本高收益)、`text_finance_szb_01.md`(数字币)、`text_finance_dzp_01.md`(电子盘)

提交:
```bash
git add 违规案例/text_finance_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): finance 桶 3 条文本 fixture"
```

---

## Task 9: 采集 cosmetic 桶(2 条 fixture)

**Files:**
- Create: `违规案例/text_cosmetic_<scene>_01.md` ... `text_cosmetic_<scene>_02.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 化妆品 治疗 处罚`、`化妆品 祛斑 处罚`、`化妆品 虚假宣传 处罚`
- slug 模式:`text_cosmetic_zlbp_01.md`(治疗痤疮)、`text_cosmetic_qxb_01.md`(祛斑美白)

提交:
```bash
git add 违规案例/text_cosmetic_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): cosmetic 桶 2 条文本 fixture"
```

---

## Task 10: 采集 agricultural 桶(2 条 fixture)

**Files:**
- Create: `违规案例/text_agricultural_<scene>_01.md` ... `text_agricultural_<scene>_02.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 种子 增产 处罚`、`农药 处罚 通报`、`农资 处罚 通报`
- slug 模式:`text_agricultural_yz_01.md`(种子)、`text_agricultural_nz_01.md`(农药/农资)

提交:
```bash
git add 违规案例/text_agricultural_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): agricultural 桶 2 条文本 fixture"
```

---

## Task 11: 采集 signage 桶(1 条 fixture)

**Files:**
- Create: `违规案例/text_signage_<scene>_01.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 户外广告 未经登记 处罚`、`广告牌 未审查 通报`
- slug 模式:`text_signage_wsb_01.md`(未审查)

提交:
```bash
git add 违规案例/text_signage_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): signage 桶 1 条文本 fixture"
```

---

## Task 12: 采集 minor 桶(1 条 fixture)

**Files:**
- Create: `违规案例/text_minor_<scene>_01.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 儿童 处罚 通报`、`未成年人 替代母乳 处罚`、`儿童专用 处罚`
- slug 模式:`text_minor_et_01.md`(儿童产品)

提交:
```bash
git add 违规案例/text_minor_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): minor 桶 1 条文本 fixture"
```

---

## Task 13: 采集 outdoor 桶(1 条 fixture)

**Files:**
- Create: `违规案例/text_outdoor_<scene>_01.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 楼顶广告 处罚`、`户外广告 楼顶大牌 处罚 通报`
- slug 模式:`text_outdoor_ld_01.md`(楼顶)

提交:
```bash
git add 违规案例/text_outdoor_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): outdoor 桶 1 条文本 fixture"
```

---

## Task 14: 采集 internet_ad 桶(1 条 fixture)

**Files:**
- Create: `违规案例/text_internet_ad_<scene>_01.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 互联网广告 处罚`、`软文 种草 处罚`、`互联网广告管理办法 处罚`
- slug 模式:`text_internet_ad_rwz_01.md`(软文种草)

提交:
```bash
git add 违规案例/text_internet_ad_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): internet_ad 桶 1 条文本 fixture"
```

---

## Task 15: 采集 pestvet 合桶(1 条 fixture)

**Files:**
- Create: `违规案例/text_pestvet_<scene>_01.md`
- Append: `违规案例/_text_plan.md`

- 关键词:`site:samr.gov.cn 农药 兽药 处罚`、`农药广审文号 处罚`、`兽药 处罚 通报`
- fixture 内容:**一则同时触发 pesticide + veterinary 规则的案例**(若找不到这种 case,改为:把 1 个农药案例 + 1 个兽药案例合并到同一 fixture 的「原始违法广告语」,但 frontmatter `预期命中规则` 包含 pesticide 与 veterinary 两类规则的 ID 集合)— 这是为什么 pestvet 是合桶,本来就要跨这两类
- slug 模式:`text_pestvet_ny_sy_01.md`(农药+兽药)

提交:
```bash
git add 违规案例/text_pestvet_*.md 违规案例/_text_plan.md && \
  git commit -m "feat(cases): pestvet 合桶 1 条文本 fixture"
```

---

## Task 16: 终验 — 全部 30 条 + 13 桶 + 现有测试不跑回归

**Files:**
- 无新增,仅跑测试 + 验证 acceptance criteria

- [ ] **Step 1: 跑全量 testDebugUnitTest,确认全绿**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" \
  ./gradlew.bat testDebugUnitTest
```
Expected: 全部 PASS(包括新增 `AdSignageTextFixtureRegressionTest` 3/3 + `TextFixtureLoaderTest` 5/5 + 既有 `AdSignageRuleMatcherTest` + `AdSignageMentorFiveImageRegressionTest` + 其他)

- [ ] **Step 2: 验证两个 profile 编译成功**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" \
  ./gradlew.bat assembleDebug -PmodelProfile=shell && \
  ./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```
Expected: 两个 profile 都 BUILD SUCCESSFUL(`ice_ocr_rules` 可能因 SDK / 模型未 stage 失败 — 若失败确认 SDK 与模型已在 libs/ + assets/models/ 落地)

- [ ] **Step 3: 跑合规 audit**

```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  echo "=== Co-Authored-By audit ===" && \
  git log -1 --format='%B' | grep -i 'co-authored-by' || echo "OK: 最新 commit 无 Co-Authored-By trailer" && \
  echo "=== 文件结构验证 ===" && \
  ls -1 违规案例/text_*.md | wc -l && \
  echo "=== 桶覆盖验证 ===" && \
  ls -1 违规案例/text_*.md | awk -F_ '{print $2}' | sort -u && \
  echo "=== 规则白名单抽查 ===" && \
  grep -h "id:" 违规案例/text_*.md | sort -u | head -20
```

Expected:
- Co-Authored-By audit: OK
- 文件结构: 30 (= 30 个 .md 文件)
- 桶覆盖: 13 个 category(可能含 pestvet,medical,absolute,...,internet_ad)
- 规则白名单:每行都是一个 JSON 已声明的 ID(可手动对照 `ad_signage_rules.json`)

- [ ] **Step 4: 提交终验(可选,若无改动可跳过)**

若 Step 1-3 全绿且有日志 / 备注需要 commit:
```bash
cd d:/GitHub/IceSpiritAI_Vision && \
  git add 违规案例/_text_plan.md && \
  git commit -m "docs(cases): 完成 _text_plan.md 终版"
```

---

## 验收清单(spec §11 对齐)

- [ ] `违规案例/text_*.md` 总数 = 30
- [ ] 30 条 fixture 的 `来源` URL 全部一手政府站 `.gov.cn`
- [ ] `AdSignageTextFixtureRegressionTest` 三个 @Test 全绿
- [ ] 30 条 fixture 命中规则集合 == 预期集合(精确 set match,零偏差)
- [ ] 13 桶全部覆盖(`medical / absolute / education / food / realestate / finance / cosmetic / agricultural / signage / minor / outdoor / internet_ad / pestvet`)
- [ ] 所有 `预期命中规则.id` 在 `ad_signage_rules.json` 122 条白名单内
- [ ] `./gradlew.bat testDebugUnitTest` 全绿
- [ ] `./gradlew.bat assembleDebug -PmodelProfile=shell` 成功
- [ ] `./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules` 成功

---

## 风险与回退

| 风险 | 缓解 |
|---|---|
| 某桶某 case WebFetch 被 anti-hotlink 阻挡 | 切换同事件其他一手 URL;_text_plan.md 记录失败原因,该桶压缩张数从相邻桶挪配额 |
| WebSearch 返回 gov 镜像 / 钓鱼站 | 严验 URL 域名后缀 `.gov.cn`,可疑拒收 |
| 规则迭代让 fixture 命中集合漂移 → 测试失败 | 这是设计预期 — 规则变更须同步更新 fixture,反向压力暴露漂移;若 fixture 过期,该 case 标 `备注: 已废弃,待规则迭代后复活` |
| 长尾桶(pestvet / signage / outdoor / internet_ad)凑不出 1 条 | pestvet 合桶允许农药+兽药合并写一条;其他桶可从相邻桶挪配额,但总仍保 30 |

---

## Self-Review

### 1. Spec coverage(对照 `docs/superpowers/specs/2026-08-25-text-violation-cases-design.md`)

| Spec 章节 | 对应 task |
|---|---|
| §4 Fixture 格式 | Task 1-15 所有 fixture 写入步骤 |
| §5 桶分配 30/13 | Task 3 (medical×5) + Task 4 (absolute×4) + Task 5-8 (edu/food/re/finance 各×3) + Task 9-10 (cosmetic/agricultural 各×2) + Task 11-15 (signage/m/outdoor/internet_ad/pestvet 各×1) = 5+4+3+3+3+3+2+2+1+1+1+1+1 = 30 |
| §6 来源优先级 | 每个 Task N 的 Step 1 关键词组合,严验 `.gov.cn` |
| §7.2 AdSignageTextFixtureRegressionTest | Task 2 |
| §7.3 TextFixtureLoader | Task 1 |
| §8 错误处理 | _text_plan.md 记录失败原因 + 桶压缩挪配额 |
| §9 测试策略 | Task 1 (loader 单测) + Task 2 (regression 骨架) + Task 16 (终验全量) |
| §10 文件清单 | 全部 33 项新增对应 Task 1-15 |
| §11 验收标准 | Task 16 Step 3 |

✅ 全覆盖,无遗漏。

### 2. Placeholder scan

搜索关键字:`TBD` / `TODO` / `implement later` / `fill in details` / `Add appropriate error handling` — **未发现**。所有 Step 都给出具体命令或代码片段。

### 3. Type consistency

| 类型 / 方法 | 定义位置 | 使用位置 |
|---|---|---|
| `TextFixture(slug, category, source, scene, violationPoint, legalBasis, originalAdText, expected, penalty, remark)` | Task 1 `TextFixtureLoader.kt` | Task 1 `TextFixtureLoaderTest.kt` 5 例 + Task 2 `AdSignageTextFixtureRegressionTest.kt` `cases.forEach { c -> }` |
| `ExpectedRule(id, severity)` | Task 1 | Task 1 + Task 2 |
| `TextFixtureLoader.loadAll(File)` / `parse(File)` | Task 1 | Task 1 + Task 2 |
| `AdSignageRuleMatcher(rules).scan(text)` | 既有 | Task 2 |
| `Severity.Warning`(作为 default) | 既有 | Task 1 `extractRuleList` |

✅ 一致。`Severity` enum 已在 `com.icespiritai.offline.domain` 暴露(被既有测试导入),不需要新声明。

### 4. Spec 范围

Spec 单一聚焦(30 fixture + loader + test),不分解。本计划对应一个独立 plan,符合 writing-plans 粒度。