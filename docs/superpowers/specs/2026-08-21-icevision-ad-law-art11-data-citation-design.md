# 冰灵锐目 — 广告法 第十一条第二款(数据未标明出处)规则 + OCR 阈值调优

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-21 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline 库 | `docs/knowledge/build-stack-2026-08.md` |
| 关联现行 spec | `2026-08-13-icevision-phase1-ocr-rules-design.md`(OCR + 规则库主规范,本文 §3 扩展其 matcher;§5/§6 扩展其 OCR 接入) |
| 关联知识库 | `知识库/广告业务/中华人民共和国广告法.md` |

---

## 1. 背景与目标

### 1.1 触发案例

2026-08-21 用户在重庆某小区电梯内拍摄东郊到家广告招牌,广告文字含:

> 全国技师超9万人｜累计服务超1000万次

该段**未标明数据出处与统计截止时间**,违反《广告法》第十一条第二款:

> 广告使用数据、统计资料、调查结果、文摘、引用语等引证内容的,应当真实、准确,并表明出处。引证内容有适用范围和有效期限的,应当明确表示。

用户当场指出两件事:
1. 当前 OCR 没有识别出"全国技师超9万人｜累计服务超1000万次"这段文字,下游规则匹配不到
2. 想知道是否能给"统计数据未标明出处"这类规则落库

### 1.2 目标

- **G1**:补齐《广告法》第十一条第二款规则,让"使用数据未标明出处"的广告在 OCR 检出后能命中规则并展示 Warning
- **G2**:修复/调优 OCR,使东郊案"全国技师超9万人"段可被检出;固化回归 fixture
- **G3**:为后续规则扩展(数据引证 / 调查引用 / 时效期限)留下 absence 判定的可复用模式

### 1.3 非目标(本期不做)

- v2 同段(line proximity)复检精度提升 — 留作后续 spec
- 跨域法规引用修正(`ad_signage_signage_food_disease_target` 的 `regulation` 字段当前串到食品标识) — 仅在知识库补一句原则声明,代码与现有规则不动,见 §10 已知后续工作
- 跨语种 OCR / 数字以外的字符识别增强
- OCR 模型重训 / 端侧预处理(对比度拉伸、CLAHE) — 仅在调优失败兜底时考虑

---

## 2. 决策摘要(brainstorming 2026-08-21 锁定)

| 问题 | 选项 | 决策 |
|---|---|---|
| 规则粒度 | A 仅关键词 / B 数字声明 + 来源标记联合 / C 占位记录 | **B**(扩展 matcher 做 absence 判定) |
| 来源识别范围 | 全局 / 同段邻近 / 两阶段 v1 全局 + v2 同段 | **两阶段 v1 全局 + v2 同段**(本期 v1,v2 留作 §10 后续) |
| OCR 调试范围 | 合并到本次 / 仅加规则 / 先建 fixture 测试 | **合并到本次**(同一 spec) |
| 来源标记词表 | 11 个中性词 / 严格表述 / 你说了算 | **11 个中性词 + 完整年份**(2020-2030 × 4 变体 ≈ 150 个字符串) |
| 严重度 | Warning / Violation / 两档 | **Warning**(程序性遗漏,不与 §16/§17+18 同档) |
| 跨域引用处理 | 仅文档原则 / 修复规则 / 知识库补说明 | **知识库补一句原则声明**(代码与现有规则不动) |
| 架构方案 | 扩展 AdSignageRule / 新 AbsenceClaimRule / 双规则 + 去重 | **扩展 AdSignageRule**(最小侵入,单一规则类型) |

---

## 3. 架构总览

7 个边界清晰的单元,任一可独立测试/回滚:

| # | 单元 | 角色 |
|---|---|---|
| 1 | `AdSignageRule` (data class) | 加可选字段 `sourceMarkers` |
| 2 | `AdSignageRuleMatcher.scan()` | 增加 absence 复合逻辑 |
| 3 | `ad_signage_rules.json` | 新增第 119 条规则 |
| 4 | `知识库/广告业务/中华人民共和国广告法.md` | 适用判别要点补一段 + 跨域引用原则 |
| 5 | `AdSignageRuleMatcherTest.kt` | 新增 absence 复合匹配测试组 |
| 6 | `app/src/androidTest/.../PaddleOcrFixtureTest.kt`(新) | 真机跑 OCR 锁 baseline |
| 7 | `PaddleOcrEngine.kt` 配置常量 | 按 §6 流程按需微调 |

依赖方向:1 → 2(数据驱动),3 由 4 生成,5 单测覆盖 2,6 验证 7 调优效果,7 调优后回跑 6 锁定 baseline。

---

## 4. 数据模型 — `AdSignageRule` 扩展

### 4.1 当前形态(`AdSignageRule.kt:6-31`)

```kotlin
@Serializable
data class AdSignageRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity,
    val lawText: String = ""
)
```

### 4.2 扩展后

```kotlin
@Serializable
data class AdSignageRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity,
    val lawText: String = "",
    val sourceMarkers: List<String> = emptyList()  // NEW
)
```

### 4.3 关键约束

- `@Serializable` + 默认值 `emptyList()` → 反序列化旧 JSON 自动得到空列表,**117 条旧规则零改动**
- Loader 已开 `Json { ignoreUnknownKeys = true; isLenient = true }`(双保险)
- `category` 值沿用 `signage`(已在 `CategoryDisplay.kt:36-53` 注册),不开新类别
- `sourceMarkers` 走与 `keywords` 相同的 `TextNormalizer.forMatching()` 归一化路径(全角数字 fold、空白剥离、小写归一)

---

## 5. Matcher 复合逻辑

### 5.1 当前形态(`AdSignageRuleMatcher.kt:34-62`)

单一 AC pass,所有 `keywords` 命中即返回 RuleHit,dedup 键为 `(ruleId, matchedText)`。

### 5.2 扩展后

```kotlin
override fun scan(text: String): List<RuleHit> {
    if (text.isEmpty()) return emptyList()
    val normalized = TextNormalizer.forMatching(text)
    if (normalized.isEmpty()) return emptyList()

    val claimHits = mutableListOf<RuleHit>()
    val sourceMarkerHitRules = mutableSetOf<String>()
    val hasAnyAbsenceRule = ruleById.values.any { it.sourceMarkers.isNotEmpty() }

    // 两条 trie 各持一个 handler,回调天然区分(§5.4)
    val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
        for (ruleId in ruleIds) {
            val rule = ruleById[ruleId] ?: continue
            val matched = normalized.substring(begin, end)
            if (claimHits.none { it.ruleId == rule.id && it.matchedText == matched }) {
                claimHits += RuleHit(rule.id, matched, rule.category,
                    rule.regulation, rule.lawText, rule.severity, CategoryDisplay.DOMAIN_AD)
            }
        }
    }
    val sourceHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
        for (ruleId in ruleIds) sourceMarkerHitRules += ruleId
    }

    // Pass 1: claim trie
    keywordTrie.parseText(normalized, keywordHandler)
    // Pass 2: 仅当存在 absence 规则时跑 source trie
    if (hasAnyAbsenceRule) {
        sourceMarkerTrie!!.parseText(normalized, sourceHandler)
    }

    // absence 过滤:对 sourceMarkers 非空的规则,仅保留"claim命中 且 source未命中"
    return claimHits.filter { hit ->
        val rule = ruleById[hit.ruleId]!!
        if (rule.sourceMarkers.isEmpty()) true
        else rule.id !in sourceMarkerHitRules
    }
}
```

### 5.3 关键设计点

- 两条独立 trie(`keywordTrie` / `sourceMarkerTrie`),一次构造。`sourceMarkerTrie` 仅在"任一规则 sourceMarkers 非空"时才构建;现有 117 条规则全部为空 → `sourceMarkerTrie == null` → 与原实现**字节级等价**
- `matchedText` 仍记录 claim 关键词(原 hit 字段),UI 提示为"广告用了 X 数据,未标明出处",而非"广告里有出处"
- dedup 沿用现有 `(ruleId, matchedText)` 策略
- 线程调度保持现有 `Dispatchers.Default`(commit `04ed750 refactor(analysis): AC scan on Dispatchers.Default, not IO` 锁定)

### 5.4 trie 共享字典的标识方式

两条 trie 的 value 类型都是 `List<String>`(rule id 列表)。在 handler 内部区分"这次回调来自哪条 trie"的做法:

**方案**:把 `keywordTrie` 与 `sourceMarkerTrie` 用不同的 wrapper 类持有,handler 闭包分别捕获两条 trie 的引用,各自走自己的逻辑分支:

```kotlin
val keywordHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds -> ... }
val sourceHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { begin, end, ruleIds ->
    for (ruleId in ruleIds) sourceMarkerHitRules += ruleId
}
keywordTrie.parseText(normalized, keywordHandler)
if (sourceMarkerTrie != null) sourceMarkerTrie.parseText(normalized, sourceHandler)
```

这是 HankCS AC 库的 native 做法,不引入额外类型,代码最简。

### 5.5 数字年份处理

按 brainstorming 决策(范围最大,~150 个),具体取 2020-2030 × 3 变体:
```
20XX           (11 个)
20XX年         (11 个)
20XX年M月      (11 × 12 = 132 个,M ∈ 1..12)
```
合计 154 个字符串,贴近用户 ~150 的预期。具体生成方式:在 spec 实现期用一个 init 块或 build 脚本生成字符串列表(避免手写出错),列表静态写进 `ad_signage_art11_data_citation` 的 `sourceMarkers` 字段。

不引 4 变体("-MM" / "年MM月"):实际广告语料极少用 ISO 格式与前导零月份,引入会膨胀词表且无明显收益。如真实命中案例出现再追加。

---

## 6. 新规则条目 `ad_signage_art11_data_citation`

贴在 `app/src/main/assets/rules/ad_signage_rules.json` 现有 118 条之后:

```json
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
    "截至", "截止", "截止到", "统计于"
    // 数字年份 154 条:由 build 脚本生成 2020-2030 × ["", "年", "年M月"]
  ],
  "severity": "Warning"
}
```

### 6.1 关键设计点

1. **keywords 与 sourceMarkers 重叠是有意的**:同一短语(如"调查"、"研究表明")既是 claim 触发器也是 source 缓解器;当同一条规则的 claim 与 source 都在 fullText 命中 → absence 不成立 → 不发 hit。v1 absence 模型的天然正确性
2. **"据" 在 sourceMarkers 是用户接受的中性词**:会被"据用户反馈"等商业套语误判为"已标注来源"(假阴性)。v1 接受此 trade-off;§10 已知后续工作中标注 v2 同段复检会收紧
3. **数字年份精确列举**:约 66 个字符串(2020-2030 × 5 变体),AC trie 内存约几 KB,远小于现有 118 条规则的 1000+ 关键词,无性能问题
4. **`regulation` 仅引《广告法》**:遵循 §10 跨域引用原则
5. **`severity: Warning`**:程序性遗漏,与 §9 极限词同档,与 §16 医疗效果 / §17+18 食品疾病(Violation)不同档

### 6.2 冲突预演

| 案文 | claim 命中 | source 命中 | 结果 |
|---|---|---|---|
| "全国技师超9万人｜累计服务超1000万次" | 全国超 / 累计 / 万次 | 无 | ✓ 报 Warning |
| "据艾瑞 2024 年报告,本品牌市场占有率第一" | 第一 | 据 / 报告 / 2024年 | ✓ 不报 |
| "据用户反馈满意度100%" | % | 据 | ✗ v1 不报(假阴性,v2 同段复检会识别"用户反馈"非权威主体) |
| "调查显示 90% 用户首选" | 调查 / 首选 | 调查 / 研究 | ✓ v1 不报 |

---

## 7. OCR Fixture 测试基础设施

### 7.1 测试分层

| 层 | 位置 | 频率 | 干什么 |
|---|---|---|---|
| **L1 真机集成** | `app/src/androidTest/.../PaddleOcrFixtureTest.kt`(新) | 发版前 / OCR 调参后 | 真 PaddleOcrEngine 在东郊图,断言"全国" / "技师" / "超9万" / "累计" 任意一个在 fullText 出现 |
| **L2 单元回归** | `app/src/test/.../AdSignageMentorFiveImageRegressionTest.kt` 加东郊条目 | 每个 PR | 固化的 baseline OCR 文本喂 matcher,断言 `ad_signage_art11_data_citation` 命中 |
| **L3 OCR 退化 smoke**(新) | `app/src/test/.../AdSignageMentorFiveImageRegressionTest.kt` | 每次调参后 | 6 张图 OCR 全跑一次,校 `avgConfidence > 0.5` 且每行 `confidence ≥ 0.3` |

L1 验证 OCR 仍能检到;L2 验证规则仍能命中;L3 验证调参没退化其他图。**三者解耦**:OCR 模型换版/参数调时跑 L1 + L3;规则/JSON 改动跑 L2。

### 7.2 L1 真机测试 fixture

**Fixture 源**: 用户提供的东郊图(本次 session 的 PNG/JPG)。固化到:
```
app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg
```

(沿用 CLAUDE.md 已确认的 `app/src/androidTest/assets/` 路径,打进 test APK,ContentResolver 不稳)。

**测试骨架**(踩 CLAUDE.md 已列的坑:跑完必须显式 `Unit`、Logcat 后台采集在测试前开、华为 nova 6 ghost state 走 `pm clear`):

```kotlin
@RunWith(AndroidJUnit4::class)
class PaddleOcrFixtureTest {
    // fixture 从 assets 拷到 cacheDir,无 CAMERA 权限需求

    @Test fun dongjiao_recognizesClaimLine() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureFile = File(ctx.cacheDir, "dongjiao.jpg").apply {
            ctx.assets.open("fixtures/dongjiao_daojia.jpg").use { input ->
                outputStream().use { input.copyTo(it) }
            }
        }
        val uri = Uri.fromFile(fixtureFile)

        val coldStart = System.currentTimeMillis()
        val engine = OcrEngineFactoryLocator.locate(ctx)  // → PaddleOcrEngine
        val firstResult = engine.recognize(uri)
        val coldMs = System.currentTimeMillis() - coldStart

        Log.i("OcrFixture", "cold_ms=$coldMs avgConfidence=${firstResult.avgConfidence}")
        Log.i("OcrFixture", "fullText=${firstResult.fullText}")

        // 核心断言:claim 关键词必须在 fullText 里(允许 OCR 略有差异,只要能识别出"9万"或"累计"语义)
        val norm = TextNormalizer.forMatching(firstResult.fullText)
        assertTrue(
            "OCR 应检出 claim 关键词(全国 / 技师 / 超9万 / 累计 / 服务 / 1000万 任一)",
            norm.contains("全国") || norm.contains("技师") ||
                norm.contains("超9万") || norm.contains("累计") ||
                norm.contains("服务") || norm.contains("1000万")
        )

        // 把通过配置的 OCR 输出落成 baseline,供 L2 使用
        val baselineFile = File(ctx.cacheDir, "dongjiao_baseline.json")
        baselineFile.writeText(buildJson {
            put("avgConfidence", firstResult.avgConfidence)
            put("fullText", firstResult.fullText)
            putJsonArray("lines") {
                firstResult.lineBoxes.forEach { line ->
                    addJsonObject {
                        put("text", line.text)
                        put("confidence", line.confidence)
                    }
                }
            }
        }.toString())
        Log.i("OcrFixture", "baseline saved at ${baselineFile.absolutePath}")
        Unit  // runBlocking body 必须显式 Unit(CLAUDE.md 踩坑)
    }
}
```

**怎么传 fixture-image path 给 connected task**(CLAUDE.md 已踩,Gradle property 而非 `--tests`):
```bash
./gradlew.bat connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrFixtureTest
```

### 7.3 Baseline 固化路径

L1 跑通后,把 `baselineFile` 内容拷到:
```
app/src/test/resources/fixtures/dongjiao_baseline.json
```

L2 在 `AdSignageMentorFiveImageRegressionTest` 加 fixture 条目:
```kotlin
@Test fun dongjiao_daojia_dataCitationFires() {
    val baselineFullText = loadFixture("dongjiao_baseline.json").fullText
    val hits = matcher.scan(baselineFullText)
    val art11 = hits.filter { it.ruleId == "ad_signage_art11_data_citation" }
    assertEquals(1, art11.size)
    assertEquals(Severity.Warning, art11.first().severity)
}
```

L1 + L2 互为备份:OCR 退化时 L1 失败、规则改坏时 L2 失败。

### 7.4 L3 OCR 退化 smoke(调参后跑)

```kotlin
@Test fun ocr_smokeAllMentorFixtures_returnsValidOcr() {
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

调参后此 test 失败 → 说明调过头,要回退。

---

## 8. OCR 阈值调优策略

### 8.1 当前配置(`PaddleOcrEngine.kt:104-116`)

```kotlin
val maxEdge = 1024           // BitmapLoader 缩放下界
detLimitSideLen = 960
detThresh = 0.2f
detBoxThresh = 0.45f
detUnclipRatio = 1.4f
recScoreThresh = 0.5f       // 行级硬阈值(0.5 以下被砍)
recBatchSize = 6
```

### 8.2 调优流程

```
L1 fixture 跑一次(§7.2)
  ↓ 捕获 baseline logcat
  ├─ "全国" / "技师" / "超9万" / "累计" 任意一个在 fullText → OCR 已 OK,无需调,固化 baseline
  └─ 全部缺失 → 看 avgConfidence + lineBoxes + 各行 confidence:
      ├─ avgConfidence ≥ 0.7 但 lineBoxes 无相关行 → det 漏框,顺序试:
      │   1. detLimitSideLen: 960 → 1280
      │   2. detThresh: 0.2 → 0.15
      │   3. detUnclipRatio: 1.4 → 1.6
      └─ lineBoxes 有相关行但 confidence < 0.5 被砍 → rec 拒识,顺序试:
          1. recScoreThresh: 0.5 → 0.3
          2. BitmapLoader.maxEdge: 1024 → 2048
          3. detLimitSideLen: 960 → 1536
```

每动一项重跑 L1 + L3:命中则固化,未命中回滚上一项。

### 8.3 调优失败兜底

若三个旋钮全试完仍无法检出"全国技师超9万人":
- 不进入模型重训 / 端侧预处理(超出本期范围)
- 在 §10 已知后续工作记录 "OCR 在某类广告(细字 + 高对比底色 + 装饰字符)检出不稳;后续考虑端侧预处理(CLAHE)或换 rec 模型"
- 本次发布照常,v1 规则允许"未检出"的边界场景漏报(已在 §9 知识库更新中标注)

---

## 9. 知识库更新

### 9.1 `知识库/广告业务/中华人民共和国广告法.md` 适用判别要点追加

在 `## 适用判别要点` 章节末尾追加:

> **第十一条第二款** | 类别:程序性遗漏 | 严重度:Warning
>
> - 触发模式:广告使用数据、统计资料、调查结果、文摘、引用语等引证内容(典型句式 "X 万人 / 累计 X 万次 / X% 用户首选 / 同比增长 X% / 排名第一"),且**未标明出处**(无"数据来源 / 据 X 报告 / 截至 YYYY"等表述)
> - 判别逻辑:keyword 命中且 sourceMarker 未命中 → 报 Warning
> - **v1 已知误判**:商业套语"据用户反馈 / 客户评价"中含"据"会被识别为"已标注来源"(假阴性);v2 同段(line proximity)复检会识别"用户反馈"非权威主体,收紧判定
> - **OCR 漏检边界**:细字 + 高对比底色 + 装饰字符的"统计行"(如本案例东郊到家 "全国技师超9万人｜累计服务超1000万次")在 rec 模型下置信 < 0.5 被砍,规则不会命中;需 OCR 调参配合

### 9.2 跨域引用原则声明

在同一文件 `## 适用判别要点` 章节开头加:
> **跨域引用原则**:广告招牌 tab 的规则 `regulation` 字段仅引《广告法》《广告管理条例》《互联网广告管理办法》《医疗广告管理办法》等广告业法规;不引《食品标识管理规定》《化妆品监督管理条例》《农药广告审查发布规定》等其他域法规。即使关键词跨域重合(如疾病类关键词同时适用于广告法 §17+§18 与食品标识管理规定),在本 tab 触发时 `regulation` 字段亦不串。

(此声明**不动代码与现有规则**——仅作为下次触碰相关规则时的设计约束,详见 §10。)

---

## 10. 已知后续工作

### 10.1 v2 同段(line proximity)复检

把 v1 的全局 sourceMarker 检测收紧为"同一 TextLine 或相邻 line 内出现 sourceMarker"。需要:
- `TextLine.box`(已有,目前仅 ViewerView 消费)
- matcher 扩展 `scan(text: String, lines: List<TextLine>): List<RuleHit>` 重载
- `OcrResult.lineBoxes` 排序鲁棒性(`box.centerY` 重排)

留待 v0.1.15+ 单独 spec。

### 10.2 跨域引用修正(`ad_signage_signage_food_disease_target`)

`ad_signage_rules.json:1198-1225` 当前 `regulation` 字段 `"《广告法》第十七条 + 第十八条 / 《食品标识管理规定》"` 在广告招牌 tab 上下文属跨域引用。本次仅在知识库补原则声明(§9.2),代码与现有规则不动。

下次触碰该规则(关键词增删 / severity 调整 / 衍生 rule)时,**优先处理**:
- 拆为两条独立规则:一条 `category: signage` 仅引《广告法》§17+§18;一条 `category: food_labeling`(目前 UI 隐藏)仅引《食品标识管理规定》
- 或保留单条但 `regulation` 字段收窄为仅《广告法》§17+§18

处理前需:
- 复核 v0.1.x 版本 user-changelog 是否提及此规则
- 跑 `AdSignageMentorFiveImageRegressionTest` 5 张图确认行为不变

(已同步写入 project memory:`followup-ad-signage-cross-cite.md`,跨 session 跟踪。)

### 10.3 OCR 漏检端侧预处理

若 §8 调优失败,后续考虑:
- BitmapLoader 增加 CLAHE(对比度受限自适应直方图均衡)
- 或换 PP-OCRv6_server 版 rec 模型(精度更高,4× 体积,需评估 APK 体积)

留待 OCR 退化案例 ≥ 3 张时启动。

---

## 11. 测试策略

| 层 | 文件 | 内容 |
|---|---|---|
| 单元 | `AdSignageRuleMatcherTest.kt` 新增 `scan_art11Absence_*` 系列 | absence 复合匹配 6 个 case:claim-only / source-only / claim+source / 空 sourceMarkers(向后兼容)/ 多 claim / 同 rule 多 match |
| 单元 | `AdSignageMentorFiveImageRegressionTest.kt` 加 fixture 6 | 东郊固化文本 + `ad_signage_art11_data_citation` 命中断言 |
| 单元 | `AdSignageMentorFiveImageRegressionTest.kt` 加 L3 OCR smoke | 6 张图 OCR `avgConfidence ≥ 0.5` + 行级 `confidence ≥ 0.3` |
| 集成 | `app/src/androidTest/.../PaddleOcrFixtureTest.kt`(新) | L1:东郊图真机 OCR 断言 claim 关键词出现 |
| 资产 | `app/src/test/resources/fixtures/dongjiao_baseline.json` | L1 通过后固化 |
| 资产 | `app/src/androidTest/assets/fixtures/dongjiao_daojia.jpg` | 用户提供的原图 |

---

## 12. 版本号节奏

按 `feedback-release-hygiene.md`(版本号只对实际功能/修复改动负责):
- 本次含功能改动(新规则 + 扩展 matcher + 新测试)→ versionCode bump
- 若 §8 OCR 调参改动了 `recScoreThresh` / `detLimitSideLen` / `detUnclipRatio` → 同样算功能改动(影响端侧行为),一并 bump
- 若仅写知识库声明 + fixture 提交 → 不 bump,但记录到 `app/src/main/assets/user-changelog.md` "未发布" 段

具体 bump 到 v0.1.15 由 implementation plan 末尾根据实际改动清单确定。

---

## 13. 实施计划入口

Spec 经评审通过后,转入 `superpowers:writing-plans` skill 输出实施计划。计划应包含:
- §3 列出的 7 个单元的工作分解
- §11 测试策略对应的具体测试用例列表
- §8 调优流程的判断树式 checklist
- §12 版本号决定的输入(根据实际改动列出 bump 决策依据)