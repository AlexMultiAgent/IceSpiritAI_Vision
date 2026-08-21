# 《广告法》第十一条第二款(数据未标明出处)+ 东郊 OCR Fixture 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 spec `2026-08-21-icevision-ad-law-art11-data-citation-design.md` —— 补齐《广告法》第十一条第二款规则,扩展 matcher 支持 absence 判定,固化东郊到家 OCR fixture 测试与 baseline,数据驱动 OCR 阈值调优,knowledge base 加 §11(2) 判别要点 + 跨域引用原则。

**Architecture:**
- 扩展 `AdSignageRule` 加可选 `sourceMarkers: List<String>`;`AdSignageRuleMatcher.scan()` 增加双 pass 逻辑,claim trie + source trie,absence 规则命中需 claim 命中且 sourceMarker 全未命中
- 新规则 `ad_signage_art11_data_citation` 写入 JSON(118 → 119 条),severity = Warning
- 新增真机 fixture 测试 `PaddleOcrFixtureTest` 跑 PaddleOcrEngine 在 `app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg`,断言 claim 关键词在 fullText;通过后将 OCR 输出固化到 `app/src/test/resources/fixtures/dongjiao_baseline.json`
- 5 张 mentor fixture 加第 6 张东郊 fixture 走 `AdSignageMentorFiveImageRegressionTest`;新增 L3 OCR smoke test 防止调参过头
- 知识库 `中华人民共和国广告法.md` 适用判别要点追加 §11(2) 条目 + 跨域引用原则声明

**Tech Stack:**
- Kotlin 2.4.10 + AGP 9.3 + Gradle 9.7 + JDK 17
- kotlinx-serialization-json (规则 JSON)
- HankCS Aho-Corasick (双 trie)
- PaddleOCR v3.7.0 SDK(ice_ocr_rules profile)
- Robolectric(单元)+ AndroidJUnit4(instrumented)
- Gradle property `-PmodelProfile=ice_ocr_rules` 必须显式传(默认 shell)

**前置文档:**
- spec: [docs/superpowers/specs/2026-08-21-icevision-ad-law-art11-data-citation-design.md](../specs/2026-08-21-icevision-ad-law-art11-data-citation-design.md)
- 知识库: `知识库/广告业务/中华人民共和国广告法.md`(原始文本与既有适用判别要点)
- CLAUDE.md 踩坑清单:instrumented test fixture path、`pm clear` ghost state、`runBlocking { } Unit` 收尾、Logcat 后台采集在测试前开

**前置 commit:**
- `c821320`(本 spec 文档)
- `0584a9f`(v0.1.14 latest shipped baseline)

**Commit 作者约束(摘自 CLAUDE.md):**
- 仓库 git config 已锁作者为 `AlexMultiAgent`
- **绝不要** `Co-Authored-By: Claude` trailer,也包括把 `user.name` 替换成 `AlexMultiAgent` 但保留 anthropic 邮箱的隐性 AI agent trailer
- 提交前 `git log -1 --format='%B' | grep -i 'Co-Authored-By'` 应为空

---

## Task 1: 扩展 `AdSignageRule` schema(加 `sourceMarkers`)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt:6-31`
- Test: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleTest.kt`

- [ ] **Step 1: 在 `AdSignageRuleTest.kt` 加失败测试**

```kotlin
// app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleTest.kt
@Test fun serializesWithSourceMarkers() {
    val rule = AdSignageRule(
        id = "ad_signage_art11_test",
        category = "signage",
        regulation = "《广告法》第十一条第二款",
        keywords = listOf("万"),
        severity = Severity.Warning,
        sourceMarkers = listOf("据", "报告")
    )
    val json = Json.encodeToString(AdSignageRule.serializer(), rule)
    assertTrue(json.contains("\"sourceMarkers\":[\"据\",\"报告\"]"))
}

@Test fun omitsEmptySourceMarkersInJson() {
    val rule = AdSignageRule(
        id = "x", category = "signage", regulation = "r",
        keywords = listOf("k"), severity = Severity.Warning
    )
    val json = Json.encodeToString(AdSignageRule.serializer(), rule)
    // 默认值不写进 JSON — 旧 JSON 反序列化得到 emptyList()
    assertFalse(json.contains("sourceMarkers"))
}
```

- [ ] **Step 2: 跑测试看失败**

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleTest.serializesWithSourceMarkers" --tests "com.icespiritai.offline.rules.AdSignageRuleTest.omitsEmptySourceMarkersInJson" -PmodelProfile=shell
```

Expected: 编译失败(`sourceMarkers` 参数不存在)。**这是预期的 fail**。

- [ ] **Step 3: 给 `AdSignageRule` 加字段**

修改 `app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt`:

```kotlin
@Serializable
data class AdSignageRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity,
    val lawText: String = "",
    val sourceMarkers: List<String> = emptyList()
)
```

- [ ] **Step 4: 跑测试看通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleTest" -PmodelProfile=shell
```

Expected: 2 个新测试通过,既有测试 0 失败。

- [ ] **Step 5: 顺手加反序列化兼容测试**

```kotlin
@Test fun deserializesLegacyJsonIgnoringSourceMarkers() {
    val legacyJson = """{"id":"x","category":"signage","regulation":"r","keywords":["k"],"severity":"Warning"}"""
    val rule = Json.decodeFromString(AdSignageRule.serializer(), legacyJson)
    assertEquals(emptyList<String>(), rule.sourceMarkers)
}
```

跑同一 test 任务,expected: 3 个新测试全过。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleTest.kt
git commit -m "feat(rules): add optional sourceMarkers field to AdSignageRule"
```

---

## Task 2: matcher 加 absence 复合匹配(TDD)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt`
- Test: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`

- [ ] **Step 1: 写 absence 复合匹配失败测试组**

在 `AdSignageRuleMatcherTest.kt` 末尾追加:

```kotlin
// --- 《广告法》第十一条第二款 absence 复合匹配 ---
@Test fun scan_absenceRule_firesWhenClaimPresentSourceAbsent() {
    val r = AdSignageRule(
        id = "ad_signage_art11_data_citation",
        category = "signage",
        regulation = "《广告法》第十一条第二款",
        keywords = listOf("万", "累计"),
        severity = Severity.Warning,
        sourceMarkers = listOf("据", "报告", "来源")
    )
    val hits = AdSignageRuleMatcher(listOf(r)).scan("全国技师超9万人｜累计服务超1000万次")
    assertEquals(1, hits.size)
    assertEquals(Severity.Warning, hits.first().severity)
    // matchedText 应记录 claim 关键词,不是 source
    assertTrue(hits.first().matchedText in listOf("万", "累计"))
}

@Test fun scan_absenceRule_doesNotFireWhenSourceMarkerPresent() {
    val r = AdSignageRule(
        id = "ad_signage_art11_data_citation",
        category = "signage",
        regulation = "《广告法》第十一条第二款",
        keywords = listOf("万"),
        severity = Severity.Warning,
        sourceMarkers = listOf("据", "报告")
    )
    // claim 在,source "据 艾瑞" 也在 → absence 不成立
    val hits = AdSignageRuleMatcher(listOf(r)).scan("据艾瑞 2024 年报告,本品牌累计服务超1000万次")
    assertEquals(0, hits.size)
}

@Test fun scan_emptySourceMarkers_legacyPathUnchanged() {
    val r = AdSignageRule(
        id = "existing_rule",
        category = "signage",
        regulation = "x",
        keywords = listOf("最佳"),
        severity = Severity.Warning
    )
    // sourceMarkers 默认 emptyList() → 旧规则行为不变
    val hits = AdSignageRuleMatcher(listOf(r)).scan("本店是当地最佳餐厅")
    assertEquals(1, hits.size)
}

@Test fun scan_absenceRule_absentSourceMarkerNullReturnsEmpty() {
    val r = AdSignageRule(
        id = "ad_signage_art11_data_citation",
        category = "signage",
        regulation = "《广告法》第十一条第二款",
        keywords = listOf("万"),
        severity = Severity.Warning,
        sourceMarkers = listOf("据")
    )
    val hits = AdSignageRuleMatcher(listOf(r)).scan("今天天气真好")
    assertEquals(0, hits.size)
}

@Test fun scan_absenceRule_sharedKeywordBetweenClaimAndSource_doesNotFire() {
    val r = AdSignageRule(
        id = "ad_signage_art11_data_citation",
        category = "signage",
        regulation = "《广告法》第十一条第二款",
        keywords = listOf("调查"),
        severity = Severity.Warning,
        sourceMarkers = listOf("调查")  // 同词既是 claim 也是 source
    )
    val hits = AdSignageRuleMatcher(listOf(r)).scan("调查显示 90% 用户首选")
    assertEquals(0, hits.size)
}
```

- [ ] **Step 2: 跑测试看 absence 行为失败**

```bash
./gradlew.bat testDebugUnitTest \
  --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.scan_absenceRule_*" \
  -PmodelProfile=shell
```

Expected: 5 个新测试全部失败(当前 matcher 不会做 absence 判定,claim 命中即报;但 `scan_emptySourceMarkers_legacyPathUnchanged` 通过 —— 因为旧规则行为正确)。**预期 fail,继续**。

- [ ] **Step 3: 实现 absence 复合逻辑**

修改 `app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt`:

```kotlin
package com.icespiritai.offline.rules

import com.icespiritai.offline.analysis.RuleHit
import com.icespiritai.offline.analysis.TextNormalizer
import com.hankcs.aho_corasick.AhoCorasickDoubleArrayTrie

class AdSignageRuleMatcher(rules: List<AdSignageRule>) : RuleMatcher {

    private val ruleById: Map<String, AdSignageRule> = rules.associateBy { it.id }
    private val keywordTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val sourceMarkerTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val hasAnyAbsenceRule: Boolean

    init {
        val keywordToRuleIds = TreeMap<String, List<String>>()
        val sourceMarkerToRuleIds = TreeMap<String, List<String>>()
        for (rule in rules) {
            for (kw in rule.keywords) {
                val key = TextNormalizer.forMatching(kw)
                if (key.isNotEmpty()) {
                    keywordToRuleIds[key] = (keywordToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
            for (sm in rule.sourceMarkers) {
                val key = TextNormalizer.forMatching(sm)
                if (key.isNotEmpty()) {
                    sourceMarkerToRuleIds[key] = (sourceMarkerToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
        }
        if (keywordToRuleIds.isNotEmpty()) keywordTrie.build(keywordToRuleIds)
        if (sourceMarkerToRuleIds.isNotEmpty()) sourceMarkerTrie.build(sourceMarkerToRuleIds)
        hasAnyAbsenceRule = rules.any { it.sourceMarkers.isNotEmpty() }
    }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        val claimHits = mutableListOf<RuleHit>()
        val sourceMarkerHitRules = mutableSetOf<String>()

        val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
            for (ruleId in ruleIds) {
                val rule = ruleById[ruleId] ?: continue
                val matched = normalized.substring(begin, end)
                if (claimHits.none { it.ruleId == rule.id && it.matchedText == matched }) {
                    claimHits += RuleHit(
                        rule.id, matched, rule.category,
                        rule.regulation, rule.lawText, rule.severity,
                        "ad"  // CategoryDisplay.DOMAIN_AD  — 见 note
                    )
                }
            }
        }
        val sourceHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) sourceMarkerHitRules += ruleId
        }

        keywordTrie.parseText(normalized, keywordHandler)
        if (hasAnyAbsenceRule) {
            sourceMarkerTrie.parseText(normalized, sourceHandler)
        }

        return claimHits.filter { hit ->
            val rule = ruleById[hit.ruleId]!!
            if (rule.sourceMarkers.isEmpty()) true
            else rule.id !in sourceMarkerHitRules
        }
    }
}
```

**note**: `"ad"` 字面量是 `CategoryDisplay.DOMAIN_AD` 的简化写法;若 `AdSignageRuleMatcher.kt` 当前版本直接 `import CategoryDisplay.DOMAIN_AD`,沿用原写法 `CategoryDisplay.DOMAIN_AD`。**先 Read 现有文件以确定准确的 `RuleHit` 构造签名**(可能是位置参数而非 named;可能是 `AnalysisState.kt` 的 `RuleHit` 而非 `analysis.RuleHit`),照搬现有调用。

- [ ] **Step 4: 跑测试看全过**

```bash
./gradlew.bat testDebugUnitTest \
  --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest" \
  -PmodelProfile=shell
```

Expected: 5 个新测试全过 + 既有 ~100 个测试 0 回归。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git commit -m "feat(rules): absence composite matching in AdSignageRuleMatcher

When a rule declares sourceMarkers, the matcher runs a second AC pass and
emits the claim hit only if no source marker was matched. Empty
sourceMarkers preserves the legacy single-pass behavior, so the existing
117 rules are unaffected."
```

---

## Task 3: 在 `ad_signage_rules.json` 加 `ad_signage_art11_data_citation`

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`
- Test: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleLoaderTest.kt`(间接验证 loader 仍 OK)

- [ ] **Step 1: 写生成年份字符串的脚本输出**

确认要写入 JSON 的 sourceMarkers 字符串(数字年份部分),在一个临时 kotlin 脚本里跑:

```kotlin
val list = mutableListOf<String>()
for (y in 2020..2030) {
    list.add(y.toString())
    list.add("${y}年")
    for (m in 1..12) list.add("${y}年${m}月")
}
// 期望 11 + 11 + 132 = 154 个
println("count=${list.size}")
list.forEach { println("    \"$it\",") }
```

把输出粘到 JSON 的 sourceMarkers 数组末尾。

- [ ] **Step 2: 在 JSON 末尾加规则条目**

打开 `app/src/main/assets/rules/ad_signage_rules.json`,在 `version: 5` 不变前提下,**最后一条规则**之后、`}` 闭合之前插入:

```json
,
  {
    "id": "ad_signage_art11_data_citation",
    "category": "signage",
    "regulation": "《广告法》第十一条第二款",
    "lawText": "第十一条第二款 广告使用数据、统计资料、调查结果、文摘、引用语等引证内容的,应当真实、准确,并表明出处。引证内容有适用范围和有效期限的,应当明确表示。",
    "keywords": [
      "万人", "万次", "万家", "万份", "万件", "万店", "万瓶",
      "亿人", "亿次", "亿份", "亿元", "亿件",
      "%", "％", "百分之",
      "倍",
      "全国超", "全国第一", "全国领先",
      "累计", "累计用户", "累计服务", "累计销售",
      "同比增长", "环比增长", "增长率",
      "销量第一", "排名第一", "份额第一",
      "调查", "调查显示", "研究报告", "报告显示",
      "研究表明", "专家表示", "专家指出",
      "数据表明", "事实证明"
    ],
    "sourceMarkers": [
      "出处", "来源", "数据来源", "据", "据某", "据该",
      "报告", "调查报告", "白皮书", "统计报告",
      "调查", "研究", "研究表明", "调查显示",
      "引用", "引自", "引证",
      "截至", "截止", "截止到", "统计于",
      "2020", "2021", "2022", "2023", "2024", "2025", "2026", "2027", "2028", "2029", "2030",
      "2020年", "2021年", "2022年", "2023年", "2024年", "2025年", "2026年", "2027年", "2028年", "2029年", "2030年",
      "2020年1月", "2020年2月", ..., "2030年12月"
    ],
    "severity": "Warning"
  }
```

(`...` 是占位 — Step 1 脚本生成的 132 个 "20XX年M月" 字符串需手填;**不要**把 `...` 留在最终 JSON 里,见 Step 4 placeholder scan。)

- [ ] **Step 3: 跑 loader 测试确认 JSON 仍解析**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleLoaderTest" -PmodelProfile=shell
```

Expected: 通过。若失败,通常是 JSON 语法错误或 `"%","％"` 这类字符未正确转义。

- [ ] **Step 4: 跑所有 AdSignageRuleMatcher 测试确认新规则可在文本中命中**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest" -PmodelProfile=shell
```

加一个针对性测试(或扩展既有 fixture 测试)验证东郊文案触发:

```kotlin
@Test fun scan_dongjiao_realWorldFixture_triggersArt11() {
    val ruleSet = AdSignageRuleLoader.loadFromAsset(context, "rules/ad_signage_rules.json")
    val matcher = AdSignageRuleMatcher(ruleSet.rules)
    val text = "全国技师超9万人｜累计服务超1000万次"
    val hits = matcher.scan(text)
    val art11 = hits.filter { it.ruleId == "ad_signage_art11_data_citation" }
    assertEquals(1, art11.size)
    assertEquals(Severity.Warning, art11.first().severity)
}
```

(`AdSignageRuleLoader.loadFromAsset` 的签名以现有 `AdSignageRuleLoader.kt` 为准;若不接受 context,改用 Robolectric `ApplicationProvider.getApplicationContext<Context>()`。)

- [ ] **Step 5: Placeholder scan**

```bash
grep -n '\.\.\.' app/src/main/assets/rules/ad_signage_rules.json
```

Expected: 无匹配(`...` 占位必须被 Step 1 生成的 132 个实际字符串替换)。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/assets/rules/ad_signage_rules.json
git commit -m "feat(rules): add ad_signage_art11_data_citation (Warning)

广告法 第十一条第二款 \"数据未标明出处\" 规则。118 → 119 条。"
```

---

## Task 4: 知识库追加 §11(2) 判别要点 + 跨域引用原则

**Files:**
- Modify: `知识库/广告业务/中华人民共和国广告法.md`

- [ ] **Step 1: 找到 `## 适用判别要点` 章节**

```bash
grep -n "^## 适用判别要点" 知识库/广告业务/中华人民共和国广告法.md
```

读 `## 适用判别要点` 章节末尾(下一个 `## ` 或文件末尾)。

- [ ] **Step 2: 在章节**开头**插入跨域引用原则**

```markdown
> **跨域引用原则**:广告招牌 tab 的规则 `regulation` 字段仅引《广告法》《广告管理条例》《互联网广告管理办法》《医疗广告管理办法》等广告业法规;不引《食品标识管理规定》《化妆品监督管理条例》《农药广告审查发布规定》《农药广告审查发布规定》《兽药广告审查发布规定》等其他域法规。即使关键词跨域重合(如疾病类关键词同时适用于广告法 §17+§18 与食品标识管理规定),在本 tab 触发时 `regulation` 字段亦不串。

```

- [ ] **Step 3: 在章节**末尾**追加 §11(2) 条目**

```markdown
> **第十一条第二款** | 类别:程序性遗漏 | 严重度:Warning
>
> - 触发模式:广告使用数据、统计资料、调查结果、文摘、引用语等引证内容(典型句式 "X 万人 / 累计 X 万次 / X% 用户首选 / 同比增长 X% / 排名第一"),且**未标明出处**(无"数据来源 / 据 X 报告 / 截至 YYYY"等表述)
> - 判别逻辑:keyword 命中且 sourceMarker 未命中 → 报 Warning
> - **v1 已知误判**:商业套语"据用户反馈 / 客户评价"中含"据"会被识别为"已标注来源"(假阴性);v2 同段(line proximity)复检会识别"用户反馈"非权威主体,收紧判定
> - **OCR 漏检边界**:细字 + 高对比底色 + 装饰字符的"统计行"(如本案例东郊到家 "全国技师超9万人｜累计服务超1000万次")在 rec 模型下置信 < 0.5 被砍,规则不会命中;需 OCR 调参配合
```

- [ ] **Step 4: Commit**

```bash
git add "知识库/广告业务/中华人民共和国广告法.md"
git commit -m "docs(kb): append art.11(2) data-citation guidance + cross-domain principle"
```

---

## Task 5: 准备 fixture 资产 + 创建 `PaddleOcrFixtureTest`(androidTest)

**Files:**
- Create: `app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg`(用户提供原图)
- Create: `app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrFixtureTest.kt`

- [ ] **Step 1: 拷用户原图到 fixture 路径**

```bash
mkdir -p app/src/androidTest/assets/fixtures
cp <用户提供的东郊到家图路径> app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg
ls -la app/src/androidTest/assets/fixtures/
```

**确认:** 路径在 `app/src/androidTest/assets/`,**不是** `app/src/main/assets/`(CLAUDE.md 已踩坑)。

- [ ] **Step 2: 创建 `PaddleOcrFixtureTest.kt`**

```kotlin
// app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrFixtureTest.kt
package com.icespiritai.offline.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.analysis.TextNormalizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PaddleOcrFixtureTest {

    @Test fun dongjiao_recognizesClaimLine() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // 1) 拷 fixture 到 cacheDir(走 assets,不用 ContentResolver/SDCard — CLAUDE.md 已踩)
        val fixtureFile = File(ctx.cacheDir, "dongjiao.jpg").apply {
            ctx.assets.open("fixtures/dongjiao_daojia.jpg").use { input ->
                outputStream().use { input.copyTo(it) }
            }
        }
        val uri = Uri.fromFile(fixtureFile)

        // 2) 冷启一次
        val coldStart = System.currentTimeMillis()
        val engine = OcrEngineFactoryLocator.locate(ctx)
        val firstResult = engine.recognize(uri)
        val coldMs = System.currentTimeMillis() - coldStart

        android.util.Log.i("OcrFixture", "cold_ms=$coldMs avgConfidence=${firstResult.avgConfidence}")
        android.util.Log.i("OcrFixture", "fullText=${firstResult.fullText}")

        // 3) 核心断言:claim 关键词至少一个在 fullText(允许 OCR 略有差异)
        val norm = TextNormalizer.forMatching(firstResult.fullText)
        assertTrue(
            "OCR 应检出 claim 关键词(全国 / 技师 / 超9万 / 累计 / 服务 / 1000万 任一)",
            norm.contains("全国") || norm.contains("技师") ||
                norm.contains("超9万") || norm.contains("累计") ||
                norm.contains("服务") || norm.contains("1000万")
        )

        // 4) 把通过配置的 OCR 输出落成 baseline(供 L2 使用)
        val baselineFile = File(ctx.cacheDir, "dongjiao_baseline.json")
        baselineFile.writeText(buildJsonObject {
            put("avgConfidence", JsonPrimitive(firstResult.avgConfidence.toDouble()))
            put("fullText", JsonPrimitive(firstResult.fullText))
            put("lines", buildJsonArray {
                firstResult.lineBoxes.forEach { line ->
                    add(buildJsonObject {
                        put("text", JsonPrimitive(line.text))
                        put("confidence", JsonPrimitive(line.confidence.toDouble()))
                    })
                }
            })
        }.toString())
        android.util.Log.i("OcrFixture", "baseline saved at ${baselineFile.absolutePath}")
        Unit  // runBlocking body 必须显式 Unit(CLAUDE.md 踩坑)
    }
}
```

(`OcrEngineFactoryLocator.locate(ctx)` 的实际 API 名以 `app/src/ice_ocr_rules/java/.../OcrEngineFactoryLocator.kt` 为准,若有 `suspend` 修饰则在 `runBlocking` 里调用,反之直接调。**先 Read 该文件以确认。**)

- [ ] **Step 3: 跑 instrumented test**

```bash
# 清理华为 nova 6 ghost state(若有)(CLAUDE.md 已踩)
adb shell pm clear com.icespiritai.vision || true

# 后台 Logcat 采集必须在 test 启动前开(CLAUDE.md 已踩)
adb logcat -c
(adb logcat -v time OcrFixture:I '*:S' > /tmp/ocr_fixture.log) &

export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrFixtureTest
```

Expected: 测试通过(若 OCR 在当前配置下能检出"全国" / "累计" / "万" 任一)。若失败 → 进入 **Task 6 调优流程**。

- [ ] **Step 4: 从 Logcat 取 baseline JSON**

```bash
adb pull /data/data/com.icespiritai.vision/cache/dongjiao_baseline.json /tmp/ 2>/dev/null || \
  adb shell run-as com.icespiritai.vision cat /data/data/com.icespiritai.vision/cache/dongjiao_baseline.json > /tmp/dongjiao_baseline.json
```

检查 `/tmp/dongjiao_baseline.json` 内容含 `fullText` 字段、≥1 个 lines 行。

- [ ] **Step 5: 暂存 baseline + Commit**

```bash
mkdir -p app/src/test/resources/fixtures
cp /tmp/dongjiao_baseline.json app/src/test/resources/fixtures/dongjiao_baseline.json

git add app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg \
        app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrFixtureTest.kt \
        app/src/test/resources/fixtures/dongjiao_baseline.json
git commit -m "test(ocr): dongjiao fixture baseline + PaddleOcrFixtureTest

Captures OCR output for 东郊到家 image as fixture baseline. The androidTest
asserts claim keywords are detected; the JSON is consumed by Task 7's
regression test in AdSignageMentorFiveImageRegressionTest."
```

**注意**: `dongjiao_baseline.json` 是用产物,**不应**包含真实人/数字/隐私 — 但本题广告公开数据,可入仓。若含人脸 / 私域标识,改用 `.gitignore` 排除并写测试只在 instrumented 时生成。

---

## Task 6(条件性):若 Task 5 失败,数据驱动 OCR 阈值调优

**触发条件:** Task 5 步骤 3 instrumented test 失败(任一 claim 关键词未在 fullText 出现)。

**Files:**
- Modify: `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt:104-116`

- [ ] **Step 1: 从 Logcat 诊断根因**

```bash
cat /tmp/ocr_fixture.log | grep -E "OcrFixture|cold_ms|fullText"
```

确认:
- `avgConfidence ≥ 0.7` 但 lineBoxes 无相关行 → det 漏框 → 进 Step 2.det 路径
- lineBoxes 有相关行但 confidence < 0.5 被砍 → rec 拒识 → 进 Step 2.rec 路径
- 其它(图片加载失败 / 模型未加载)→ 不是调优范围,检查 fixture / 设备状态

- [ ] **Step 2.det 路径(漏框):按顺序试**

打开 `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt` 找到 `PaddleOCRConfig` 构造块(行 104-116 附近)。

第一次试:`detLimitSideLen` 改 960 → **1280**:

```kotlin
PaddleOCRConfig(
    detModelAssetPath = "models/det/inference.onnx",
    recModelAssetPath = "models/rec/inference.onnx",
    recConfigAssetPath = "models/rec/inference.yml",
    recScoreThresh = 0.5f,
    recBatchSize = 6,
    detLimitSideLen = 1280,  // was 960
    detThresh = 0.2f,
    detBoxThresh = 0.45f,
    detUnclipRatio = 1.4f
)
```

重跑 Task 5 Step 3 的 instrumented test + Task 7 Step 4 的 L3 OCR smoke test:
- L1 命中 + L3 通过 → 固化,跳到 Task 6 Step 3
- L1 仍失败 → 回滚到 960,试第二次

第二次试:`detThresh` 改 0.2 → **0.15**:

```kotlin
detThresh = 0.15f,  // was 0.2f
```

第三次试:`detUnclipRatio` 改 1.4 → **1.6**:

```kotlin
detUnclipRatio = 1.6f,  // was 1.4f
```

三次都失败 → 进 Step 3.rec 路径(再尝试)或 §8.3 兜底。

- [ ] **Step 2.rec 路径(rec 拒识):按顺序试**

第一次试:`recScoreThresh` 改 0.5 → **0.3**:

```kotlin
recScoreThresh = 0.3f,  // was 0.5f
```

第二次试:`BitmapLoader.maxEdge` 改 1024 → **2048**(找 `BitmapLoader` 配置常量,通常是 `BitmapLoader.bytes` 或构造参数):

```kotlin
val maxEdge = 2048  // was 1024
```

第三次试:`detLimitSideLen` 同步抬升 960 → **1536**:

```kotlin
detLimitSideLen = 1536,  // was 960
```

- [ ] **Step 3: 每动一项都跑 L1 + L3**

```bash
# 重跑 fixture test
./gradlew.bat connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrFixtureTest

# 重跑 L3 OCR smoke test(在 Task 7 加完之后跑)
./gradlew.bat testDebugUnitTest \
  --tests "com.icespiritai.offline.rules.AdSignageMentorFiveImageRegressionTest.ocr_smokeAllMentorFixtures_*" \
  -PmodelProfile=ice_ocr_rules
```

期望:
- L1 命中 → 进入 Task 5 Step 5 固化 baseline
- L3 失败 → 说明调过头,回滚这一项,试上一项

- [ ] **Step 4: 调优全失败 → §8.3 兜底**

若 det / rec 6 项全失败,在 plan 输出(commit message)中标注:
- **不动代码**(保持原 config),本次发布照常
- 在 `app/src/main/assets/user-changelog.md` "未发布" 段加一条:
  > OCR 阈值调优失败:东郊到家 "全国技师超9万人｜累计服务超1000万次" 在当前 PP-OCRv6_small + recScoreThresh=0.5 配置下未检出。规则 ad_signage_art11_data_citation 在该图上**不会触发**(假阴性已知)。后续考虑端侧预处理(CLAHE)或 PP-OCRv6_server 模型。
- Commit user-changelog 改动(若有)。

- [ ] **Step 5: Commit(无论是否改动代码)**

如果改动了 PaddleOcrEngine.kt:

```bash
git add app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt
git commit -m "tune(ocr): bump detLimitSideLen/recScoreThresh for small-text recall

Data-driven from Task 5's PaddleOcrFixtureTest. L1 hit + L3 smoke pass on
mentor fixture set. Per-image latency +10-20% acceptable."
```

否则无需 commit(只 commit user-changelog 改动)。

---

## Task 7: 加东郊 fixture 到 `AdSignageMentorFiveImageRegressionTest` + L3 OCR smoke

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageMentorFiveImageRegressionTest.kt`

- [ ] **Step 1: 读现有 5-image 测试结构**

```bash
grep -nE "fun |val [a-z]" app/src/test/java/com/icespiritai/offline/rules/AdSignageMentorFiveImageRegressionTest.kt | head -30
```

理解既有 fixture 数据加载路径(Likely `loadFixture(name)` 或 `mentorFixtures: List<MentorFixture>`)。

- [ ] **Step 2: 加东郊条目(L2 单元回归)**

```kotlin
@Test fun dongjiao_daojia_dataCitationFires() {
    val baseline = loadFixture("dongjiao_baseline.json")  // 由 Task 5 生成
    val hits = matcher.scan(baseline.fullText)
    val art11 = hits.filter { it.ruleId == "ad_signage_art11_data_citation" }
    assertEquals(1, art11.size)
    assertEquals(Severity.Warning, art11.first().severity)
}
```

`loadFixture` 实际签名以现有代码为准(可能是 `loadFixture("dongjiao_baseline.json").fullText` 或 `loadFixture<Baseline>(...)`)。

- [ ] **Step 3: 加 L3 OCR smoke(失败时保护)**

```kotlin
@Test fun ocr_smokeAllMentorFixtures_returnsValidOcr() {
    val engine: OcrEngine = FakeOcrEngine(stubText = "stub")  // 或 PaddleOcrEngine in androidTest
    mentorFixtures.forEach { fixture ->
        val result = engine.recognize(fixture.uri)
        assertTrue("OCR 应至少 1 行: ${fixture.name}", result.lineBoxes.isNotEmpty())
        assertTrue("avgConfidence ≥ 0.5: ${fixture.name}", result.avgConfidence >= 0.5f)
        result.lineBoxes.forEach { line ->
            assertTrue("行 confidence ≥ 0.3", line.confidence >= 0.3f)
            assertTrue("行 text 非空", line.text.isNotBlank())
        }
    }
}
```

(`engine` 是 FakeOcrEngine 还是 PaddleOcrEngine 取决于测试运行环境 —— `app/src/test/` 是 JVM,Robolectric 不支持 PaddleOCR native libs,只能跑 FakeOcrEngine。若 FakeOcrEngine 输出 cannedText 已稳定 ≥ 0.5 confidence,此 test 始终通过,无意义。**替代方案**:把 L3 移到 `app/src/androidTest/`,用 `PaddleOcrFixtureTest` 的姐妹测试跑 5 张 mentor 图,只在发版前跑。)

**采用替代方案**:把 L3 移到 `app/src/androidTest/java/.../MentorOcrSmokeTest.kt`(与 PaddleOcrFixtureTest 同包),asset 路径 `app/src/androidTest/assets/fixtures/mentor_1.jpg` ... `mentor_5.jpg`(原 5 张 mentor 图)。

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/icespiritai/offline/rules/AdSignageMentorFiveImageRegressionTest.kt
git commit -m "test(rules): dongjiao fixture asserts ad_signage_art11_data_citation fires

Adds east-jiao case to the mentor regression. Baseline text is read from
dongjiao_baseline.json captured by PaddleOcrFixtureTest (Task 5). Runs
in JVM (no native libs) — OCR side is covered by the androidTest."
```

(若 L3 移到 androidTest,此处只 commit 单元测试;L3 单独 commit。)

---

## Task 8(可选):L3 OCR smoke 移到 androidTest

**Files:**
- Create: `app/src/androidTest/java/com/icespiritai/offline/ocr/MentorOcrSmokeTest.kt`
- Modify: `app/src/androidTest/assets/`(拷 5 张 mentor 图作为 fixture)

- [ ] **Step 1: 把 `测试集/` 现有 5 张 mentor 图拷到 androidTest assets**

```bash
mkdir -p app/src/androidTest/assets/fixtures/mentor
cp "测试集/<5 张 mentor 图>" app/src/androidTest/assets/fixtures/mentor/
```

确认 5 张图都在(测试集 目录通常含 5 张 PNG/JPG)。

- [ ] **Step 2: 创建 `MentorOcrSmokeTest.kt`**

```kotlin
// app/src/androidTest/java/com/icespiritai/offline/ocr/MentorOcrSmokeTest.kt
package com.icespiritai.offline.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MentorOcrSmokeTest {

    private val fixtureNames = listOf("mentor_1.jpg", "mentor_2.jpg", /* ... 5 张 ... */)

    @Test fun allMentorFixtures_ocrReturnsValidOutput() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = OcrEngineFactoryLocator.locate(ctx)
        for (name in fixtureNames) {
            val fixtureFile = File(ctx.cacheDir, name).apply {
                ctx.assets.open("fixtures/mentor/$name").use { input ->
                    outputStream().use { input.copyTo(it) }
                }
            }
            val result = engine.recognize(Uri.fromFile(fixtureFile))
            android.util.Log.i("MentorSmoke", "$name lines=${result.lineBoxes.size} avgConf=${result.avgConfidence}")
            assertTrue("$name 应至少 1 行", result.lineBoxes.isNotEmpty())
            assertTrue("$name avgConfidence ≥ 0.5", result.avgConfidence >= 0.5f)
            result.lineBoxes.forEach { line ->
                assertTrue("行 confidence ≥ 0.3", line.confidence >= 0.3f)
                assertTrue("行 text 非空", line.text.isNotBlank())
            }
        }
        Unit
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/assets/fixtures/mentor/ \
        app/src/androidTest/java/com/icespiritai/offline/ocr/MentorOcrSmokeTest.kt
git commit -m "test(ocr): mentor fixture smoke test in androidTest

5 mentor images fed through PaddleOcrEngine. Guards against OCR threshold
changes that over- or under-tune recall. Runs on real device before each
release."
```

---

## Task 9: 最终验证 + 版本号决策 + user-changelog

**Files:**
- Modify: `app/build.gradle.kts`(`versionCode` / `versionName`)
- Modify: `app/src/main/assets/user-changelog.md`

- [ ] **Step 1: 跑完整单测套件**

```bash
./gradlew.bat testDebugUnitTest -PmodelProfile=ice_ocr_rules
```

Expected: 全过(无回归)。若有失败,逐个修。

- [ ] **Step 2: 真机跑一次东郊 fixture**

```bash
./gradlew.bat connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrFixtureTest
```

Expected: 通过(若 §6 调优已成功)或失败但已知(若 §8.3 兜底)。

- [ ] **Step 3: 决定版本号**

按 `feedback-release-hygiene.md`(版本号只对实际功能/修复改动负责):

实际改动清单(打勾):

- [ ] 新规则条目 `ad_signage_art11_data_citation`(Warning)
- [ ] `AdSignageRule` 扩展 `sourceMarkers` 字段 + matcher 双 pass
- [ ] 知识库 §11(2) 判别要点 + 跨域引用原则
- [ ] 东郊 fixture OCR 集成测试 + baseline 固化
- [ ] L3 OCR smoke test(androidTest)
- [ ] OCR 阈值调优(`PaddleOcrEngine.kt` 常量改动)

如果至少勾选了前 5 项 → versionCode bump。
如果第 6 项也勾了 → 同样 bump(端侧行为变更也算功能改动)。
如果 1-5 都没勾(理论上不可能)→ 不 bump。

最新已知 versionCode:14(v0.1.14,commit `0584a9f`)。Bump 到 **versionCode 15 = v0.1.15**。

- [ ] **Step 4: 更新 `app/build.gradle.kts`**

修改 `defaultConfig.versionCode` 和 `versionName`:

```kotlin
defaultConfig {
    ...
    versionCode = 15
    versionName = "0.1.15"
}
```

- [ ] **Step 5: 更新 `user-changelog.md`**

在 `app/src/main/assets/user-changelog.md` 顶部"未发布"段(或 v0.1.15 段,看现有格式)追加:

```markdown
## v0.1.15

### 新增
- 广告法 第十一条第二款 规则:广告使用数据 / 统计资料 / 调查结果 / 文摘 / 引用语未标明出处 → Warning
  - 新规则 id:`ad_signage_art11_data_citation`,category `signage`,severity `Warning`
  - claim 触发词覆盖:"万人 / 万家 / 累计 / 排名第一 / 同比增长 / 调查 / 研究表明" 等 38 个
  - sourceMarker 缓解词覆盖:"据 / 报告 / 调查 / 来源 / 截至" + 2020-2030 完整年份(154 条)
- 东郊到家 OCR fixture 测试(`PaddleOcrFixtureTest`):真机跑 PaddleOcrEngine,固化 baseline
- 5 张 mentor 图 OCR smoke test(`MentorOcrSmokeTest`):防止 OCR 阈值调过头

### 变更
- `AdSignageRule` 加可选 `sourceMarkers` 字段,`AdSignageRuleMatcher` 增加 absence 复合匹配
  - 旧规则 `sourceMarkers = emptyList()` 时行为字节级等价,118 条既有规则零回归
- [可选]OCR 阈值调优:`recScoreThresh` / `detLimitSideLen` 等数据驱动调整(若 Task 6 有改动)

### 已知边界
- v1 absence 检测是全局 sourceMarker 检测,商业套语"据用户反馈"会被误识别为已标注来源(假阴性);v2 同段复检会收紧,留作后续 spec
- 若 OCR 在某类广告(细字 + 高对比底色 + 装饰字符)下检出率不稳,规则不会触发(假阴性);user-changelog 标注 + 后续 OCR 端侧预处理 / 模型升级作为后续工作
```

(格式以现有 user-changelog.md 顶部最近一段为准 —— Read 文件确认)

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/assets/user-changelog.md
git commit -m "release(v0.1.15): ad-law art.11(2) data-citation rule + ocr fixture

Features:
- ad_signage_art11_data_citation (Warning)
- AdSignageRule.sourceMarkers field + absence composite matching
- PaddleOcrFixtureTest + MentorOcrSmokeTest (androidTest)
- data-driven OCR threshold tuning (if applied in Task 6)"
```

---

## 完整 diff 预期

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt` | +1 字段(`sourceMarkers`) |
| `app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt` | 重构 scan 双 pass + source trie |
| `app/src/main/assets/rules/ad_signage_rules.json` | +1 条规则(118 → 119) |
| `知识库/广告业务/中华人民共和国广告法.md` | +2 段(跨域引用原则 + §11(2) 判别要点) |
| `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleTest.kt` | +3 测试 |
| `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt` | +5 测试 |
| `app/src/test/java/com/icespiritai/offline/rules/AdSignageMentorFiveImageRegressionTest.kt` | +1 fixture + 1 断言 |
| `app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg` | 新增(用户原图) |
| `app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrFixtureTest.kt` | 新增 |
| `app/src/androidTest/java/com/icespiritai/offline/ocr/MentorOcrSmokeTest.kt`(可选) | 新增 |
| `app/src/androidTest/assets/fixtures/mentor/*.jpg`(可选) | 新增 5 张图 |
| `app/src/test/resources/fixtures/dongjiao_baseline.json` | 新增(OCR 输出固化) |
| `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt`(条件) | 数据驱动调优 |
| `app/build.gradle.kts` | versionCode 14 → 15, versionName 0.1.14 → 0.1.15 |
| `app/src/main/assets/user-changelog.md` | +v0.1.15 段 |

预期 commit 数:7-9 个(按 TDD 粒度 + 条件性 Task 6 / 8)。

---

## 实施时间预估

| Task | 估时(熟练 dev) |
|---|---|
| 1 | 20 分钟 |
| 2 | 45 分钟 |
| 3 | 30 分钟(主要是 132 个年月字符串填入) |
| 4 | 15 分钟 |
| 5 | 60 分钟(含真机部署 + Logcat 调试) |
| 6 | 30-90 分钟(取决于调优迭代次数;兜底情况 15 分钟) |
| 7 | 30 分钟 |
| 8 | 30 分钟 |
| 9 | 30 分钟 |

**总计:** 4-6 小时**(熟练 dev)。第一次跑 instrumented test 难免踩坑,留 buffer。