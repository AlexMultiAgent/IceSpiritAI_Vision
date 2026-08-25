# 冰灵锐目 — 文本类违规案例 fixture 采集 + 规则回归测试规范

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-25 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联初版 spec | [2026-08-24-doubletap-fix-and-violation-cases-design.md](2026-08-24-doubletap-fix-and-violation-cases-design.md)(子项目 B 因图像采集受阻暂停) |
| 关联规则测试样板 | `app/src/test/java/com/icespiritai/offline/rules/AdSignageMentorFiveImageRegressionTest.kt` |

本文档在 2026-08-24 spec 子项目 B 暂停后,引入 **path A 文本 fixture 路线** — 用政府站「处罚通报」类一手文本案例替换「实拍招牌」图片路径,直接喂 `AdSignageRuleMatcher.scan(text)`,实现规则回归自动化。

---

## 1. 背景与目标

### 1.1 现状

- `app/src/main/assets/rules/ad_signage_rules.json` v6 = 122 条规则 / 14 category(`absolute / agricultural / cosmetic / education / finance / internet_ad / medical / minor / outdoor / pesticide / realestate / restricted / signage / veterinary`)
- `违规案例/` 当前 4 张微信实拍 + `medical_store_01.jpg` + 同名 `medical_store_01.md` 一套成例
- 2026-08-24 spec 子项目 B 暂停原因:政府站 anti-hotlink / CDN / 偶发 HTTP 100 阻挡使图像采集实际产出仅 1 张真实图,无法达到 50+ 目标
- 既有 `AdSignageMentorFiveImageRegressionTest.kt` 已验证「加载真实 JSON + 跑 RuleMatcher + 断言命中」模式可行,新测试复用其 File-resolution + JSON 加载 pattern

### 1.2 目标

- 采集 **30 条一手政府站「处罚通报」类文本案例**,写入 `违规案例/text_<bucket>_<NN>.md`(无 .jpg)
- 新增 `AdSignageTextFixtureRegressionTest`(JVM JUnit,无 Robolectric / 无 Compose / 无 OCR),跑 `testDebugUnitTest` 即可
- 测试断言:每条 fixture 的 `AdSignageRuleMatcher.scan(原始违法广告语)` 命中规则 ID 集合 == fixture frontmatter `预期命中规则[*].id` 集合(**精确 set match**)
- 整套路径覆盖 14 个 category 的高频违规场景

### 1.3 非目标(本期)

- 不动 `ad_signage_rules.json` / `food_label_rules.json` 自身
- 不动 OCR / 图像采集流程(`ice_ocr_rules` profile 全部维持现状)
- 不动 UI / ViewModel / NavHost
- 不引 YAML 解析依赖 — 手写 frontmatter 解析器(~50 行,支持本规范定义的 8 个字段类型)
- 不引自动化采集脚本 — 仍走人工 WebSearch + WebFetch(沿用 CLAUDE.md「不引入自动化采集脚本」约束)
- 不动 `medical_store_01.{jpg,md}` 既有内容

---

## 2. 总体方案

按 14 category 分桶手工采集,每桶 WebSearch 关键词组合(`<category 中文名>` + 「处罚 通报」+「市场监管」),WebFetch 提取页面文本中的「违法广告语」段落,写 frontmatter + body markdown 文件。每桶完成后跑 `AdSignageTextFixtureRegressionTest` 验证。

采集 → 验证闭环:

```
WebSearch(<bucket> 处罚通报) → 一手 URL 列表
        ↓
WebFetch(URL) → 页面文本 → 抽取「违法广告语」+「法条」+「处罚结果」
        ↓
手写 违规案例/text_<bucket>_<NN>.md
        ↓
./gradlew.bat testDebugUnitTest
        ↓
AdSignageTextFixtureRegressionTest → 30 条全绿 / 14 category 覆盖 / 规则白名单通过
```

---

## 3. 架构 & 数据流

```
┌─────────────────────────────────────────────────────────────┐
│  [一手政府站 URL] (samr.gov.cn / 京沪粤苏浙监管局 / creditchina)  │
└────────────────────────┬────────────────────────────────────┘
                         │ WebFetch
                         ↓
        [抽取「违法广告语原文」+「法条」+「处罚结果」]
                         │ 手写
                         ↓
       ┌────────────────────────────────────────────┐
       │  违规案例/text_<bucket>_<NN>.md              │
       │  (frontmatter: 来源/场景/违规点/法律依据/     │
       │   原始违法广告语/预期命中规则/处罚结果/备注)    │
       └────────────────────┬───────────────────────┘
                            │ ClassLoader / File
                            ↓
       ┌────────────────────────────────────────────┐
       │  TextFixtureLoader.loadAll(违规案例/text_*.md) │
       │  → List<TextFixture>                         │
       └────────────────────┬───────────────────────┘
                            │
                            ↓
       ┌────────────────────────────────────────────┐
       │  AdSignageRuleMatcher(rules).scan(text)      │
       │  → List<RuleHit>                              │
       └────────────────────┬───────────────────────┘
                            │
                            ↓
       ┌────────────────────────────────────────────┐
       │  assert(实际命中规则 ID 集合 == 预期命中规则 ID 集合)│
       │  (精确 set match)                              │
       └────────────────────────────────────────────┘
```

---

## 4. Fixture 格式

文件:`违规案例/text_<bucket>_<NN>.md`(与既有 `medical_store_01.jpg + .md` 同目录,但无 .jpg;`text_` 前缀区分纯文本 fixture)。

```yaml
---
来源: https://www.samr.gov.cn/... # 一手 URL,必填
场景: 处罚通报 # 处罚通报 / 监管公示 / 媒体曝光
违规点: <一句话总结>
法律依据: 广告法 §16 # 广告法 §X / 食品安全法 §X / 部门规章 §X
原始违法广告语: |       # RuleMatcher.scan() 的直接输入(多行)
  <完整违法广告文本,保留原始排版>
预期命中规则:           # 数组,精确 set match 的期望集合
  - id: ad_signage_art16_med_abs
    severity: Violation
处罚结果: 罚款 10 万元,责令停止发布
备注: <可选,法条原文摘抄或同类变体说明>
---

# <场景标题>

<背景:为何构成违规 / 法条原文摘抄 / 同类常见变体>
```

**字段说明:**

| 字段 | 必填 | 类型 | 解析器处理 |
|---|---|---|---|
| `来源` | ✓ | 单行字符串 | 直接 trim |
| `场景` | ✓ | 单行字符串 | 直接 trim |
| `违规点` | ✓ | 单行字符串 | 直接 trim |
| `法律依据` | ✓ | 单行字符串 | 直接 trim |
| `原始违法广告语` | ✓ | 多行块(`\|`) | 收集到下一非缩进行 |
| `预期命中规则` | ✓ | 嵌套列表 | 每项提 `id` + `severity` |
| `处罚结果` | ✓ | 单行字符串 | 直接 trim |
| `备注` | ✗ | 单行字符串 | 可缺省 |

**slug 命名**:`text_<category 缩写>_<场景>_<NN>.md`,全小写下划线。例:`text_medical_ykzp_01.md`(药店)、`text_absolute_best_03.md`、`text_education_baoguo_01.md`。

---

## 5. 桶分配(30 条 / 13 桶,pesticide/veterinary 合桶)

| Category | 目标 | slug 缩写 | 桶内场景示例 |
|---|---:|---|---|
| medical(医疗) | 5 | `medical_<场景>` | ykzp 药店、zszn 诊所、wzyl 互联网医疗、ylqx 医疗器械、tjyp 体检预约 |
| absolute(绝对化) | 4 | `absolute_<场景>` | best 最佳、first 第一、top 顶级、zjzl 国家级 |
| education(教育) | 3 | `education_<场景>` | baoguo 保过、tuijian 院校推荐、zyzs 职业证书 |
| food(食品/保健) | 3 | `food_<场景>` | bjsp 保健食品、tssj 特殊膳食、sldz 散装食品 |
| realestate(房地产) | 3 | `realestate_<场景>` | sz 升值、xqf 学区房、wzj 无证销售 |
| finance(金融/招商) | 3 | `finance_<场景>` | bbxj 保本高收益、szb 数字币、dzp 电子盘 |
| cosmetic(化妆品) | 2 | `cosmetic_<场景>` | zlbp 治疗痤疮、qxb 祛斑美白 |
| agricultural(农资) | 2 | `agricultural_<场景>` | yz 种子、nz 农药 |
| signage(招牌本身) | 1 | `signage_<场景>` | wsb 未审查 |
| minor(未成年人) | 1 | `minor_<场景>` | et 儿童产品 |
| outdoor(户外) | 1 | `outdoor_<场景>` | ld 楼顶大牌 |
| internet_ad(互联网) | 1 | `internet_ad_<场景>` | rwz 软文种草 |
| pesticide+veterinary 合桶 | 1 | `pestvet_<场景>` | ny 农药 + sy 兽药 |
| **合计** | **30** | | |

**合桶理由**:一手政府站 `pesticide`(农药广告)和 `veterinary`(兽药广告)两类月均公开通报 < 2 条,合桶既达 30 又不堆凑数。

---

## 6. 来源优先级(只取一手政府站)

| 优先级 | 来源 | 备注 |
|---|---|---|
| 1 | 国家市场监督管理总局 `samr.gov.cn` 处罚通报栏目 | 权威,优先 |
| 2 | 省/市监管局(京 / 沪 / 粤 / 苏 / 浙)同栏目 | 高频曝光,常含地方典型案例 |
| 3 | 信用中国 `creditchina.gov.cn` 失信黑名单 / 行政处罚 | 聚合,便于跨省检索 |
| 4 | 同事件的地方监管局转发 / 公告(一手但转载) | 仅当一手机构无公开通报时 |

**不取**:商业法律网站、广告合规咨询公司案例库、社交媒体截图、新闻类二手报道(其引用的案件仍以一手机构 URL 为「来源」字段值)。

---

## 7. 组件规范

### 7.1 `违规案例/text_<bucket>_<NN>.md`

新增 30 个文件。每桶完成后人工抽查:
- `来源` URL 域名是 `.gov.cn`
- `原始违法广告语` 字段含至少 1 个 `预期命中规则[*].id` 在 JSON 中声明的关键词
- `预期命中规则[*].id` 在 `ad_signage_rules.json` 白名单内(`违规案例/_rule_ids.json` 已就位)

### 7.2 `AdSignageTextFixtureRegressionTest.kt`

路径:`app/src/test/java/com/icespiritai/offline/rules/AdSignageTextFixtureRegressionTest.kt`

职责:JVM JUnit 测试,跑 `./gradlew.bat testDebugUnitTest`。**零 Robolectric / 零 Compose / 零 OCR 依赖**(沿用 `AdSignageMentorFiveImageRegressionTest` 的 pure-JUnit 模式)。

```kotlin
class AdSignageTextFixtureRegressionTest {
    /**
     * 沿用 AdSignageMentorFiveImageRegressionTest 的多路径候选策略,
     * 兼容 Gradle 跑测试时 cwd 不一定是项目根的情况。
     */
    private fun loadRealRules(): List<AdSignageRule> {
        val candidates = listOf(
            File("src/main/assets/rules/ad_signage_rules.json"),
            File("app/src/main/assets/rules/ad_signage_rules.json"),
            File("app/build/generated/assets/rules/ad_signage_rules.json"),
            File("../src/main/assets/rules/ad_signage_rules.json"),
        )
        val jsonFile = candidates.firstOrNull { it.exists() && it.length() > 100 }
            ?: error("expected ad_signage_rules.json at: ${candidates.joinToString { it.absolutePath }}")
        val raw = jsonFile.readText(Charsets.UTF_8)
        return Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(AdSignageRuleSet.serializer(), raw).rules
    }

    private fun fixturesDir(): File {
        val candidates = listOf(
            File("违规案例"),
            File("../违规案例"),
            File("app/../违规案例"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("expected 违规案例/ dir at: ${candidates.joinToString { it.absolutePath }}")
    }

    @Test
    fun `every text fixture has matching rule hits (exact set)`() {
        val ruleIds = loadRealRules().map { it.id }.toSet()
        val matcher = AdSignageRuleMatcher(loadRealRules())
        val cases = TextFixtureLoader.loadAll(fixturesDir())

        assertTrue("no text fixtures found", cases.isNotEmpty())

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
                "fixture ${c.slug} rule hit mismatch",
                expectedIds, actualIds,
            )
        }
    }

    @Test
    fun `minimum 30 text fixtures collected`() {
        val cases = TextFixtureLoader.loadAll(fixturesDir())
        assertTrue("got ${cases.size} fixtures, need >= 30", cases.size >= 30)
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

**断言语义说明(精确 set match 的取舍)**:

- `expectedIds == actualIds`:**任何**规则改动(新增命中关键词 / 删除关键词 / 调整 severity)若让 fixture 命中规则集合变化,测试即失败
- 优点:捕捉意外规则漂移(如 v5 → v6 时把「全国第一」挪到通用 `ad_signage_art28b_fake_data` 后,蟹都汇 fixture 命中集合随之变化 — 必须显式更新 fixture)
- 代价:每次规则迭代都需要审视 fixture 集合 — 这正是「规则回归」的目的
- 与 `AdSignageMentorFiveImageRegressionTest` 的 subset 风格形成互补:mentor test 做覆盖率 pin(必须命中),本测试做 set 完整性 pin(命中集合不能漂)

### 7.3 `TextFixtureLoader.kt`

路径:`app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoader.kt`

职责:解析 `违规案例/text_*.md` 的 frontmatter 块。**手写 minimal 解析器,不引 snakeyaml / jackson-yaml**。

```kotlin
package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.Severity
import java.io.File

data class TextFixture(
    val slug: String,
    /** 桶 category,从 slug 第二段推断(text_<category>_<scene>_<NN> → 例如 medical) */
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
        // frontmatter is the first block between --- markers
        val parts = content.split("---", limit = 3)
        require(parts.size >= 3) { "${file.name}: missing --- markers" }
        val fm = parts[1].trim()
        val lines = fm.lines()
        val slug = file.nameWithoutExtension
        // text_<category>_<scene>_<NN> → category = 第二段
        // 例:text_medical_ykzp_01 → ["text", "medical", "ykzp", "01"] → "medical"
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

    private fun extractString(lines: List<String>, key: String): String { ... }
    private fun extractStringOrNull(lines: List<String>, key: String): String? { ... }
    private fun extractMultiline(lines: List<String>, key: String): String { ... }
    private fun extractRuleList(lines: List<String>, key: String): List<ExpectedRule> { ... }
}
```

**预期体量**:`TextFixtureLoader.kt` 总长 ~80-100 行(含 data class + 4 个 extract 函数 + 主 parse 函数)。无新依赖。

---

## 8. 错误处理

| 场景 | 处理 |
|---|---|
| 一手 URL 404 / 失效 | 跳过,在 `违规案例/_text_plan.md` 备注;改用同事件其他一手 URL |
| 某 category 当月通报 < 1 | 该桶压缩张数,从相邻桶挪配额,总仍保 30 |
| `预期命中规则.id` 在 JSON 找不到 | 测试启动白名单校验失败 → fixture 即时修正(改名 / 删 fixture) |
| `原始违法广告语` 字段含 `:` 字符导致解析误判 | 解析器按行处理,只识别 `key:` 起首位置;冒号在内嵌文本中安全 |
| frontmatter 含未声明字段(例如 `拍摄角度`) | 解析器忽略非本规范字段,不报错(向后兼容 image 案例 metadata) |
| fixture 文件超过 1 个 `---` 段(正文里也有 ---) | 用 `split("---", limit = 3)` 只取前两段,正文区段被忽略 |

---

## 9. 测试策略

| 测试 | 类型 | 覆盖 | 运行命令 |
|---|---|---|---|
| `AdSignageTextFixtureRegressionTest` | JVM JUnit(纯,无 Robolectric) | 30 条 fixture 精确 set match + ≥30 张数 + 14 category 覆盖 | `./gradlew.bat testDebugUnitTest` |
| 既有 `AdSignageRuleMatcherTest` | JVM JUnit | 单条规则触发 / 不触发单元逻辑 | 同上,不动 |
| 既有 `AdSignageMentorFiveImageRegressionTest` | JVM JUnit | 5 张现场采集图的端侧 OCR 文本回归 pin | 同上,不动 |
| 既有 `HomeScreen*` / `ImagePreview*` | Robolectric / Compose | UI 行为 | 同上,不动 |

**双测试分层**:

- `AdSignageMentorFiveImageRegressionTest`:**图像路径**,subset 风格,验证 OCR→规则端到端
- `AdSignageTextFixtureRegressionTest`:**文本路径**,exact-set 风格,验证规则逻辑完整性
- 两者互补 — mentor test 防 OCR 漏字,本测试防规则漂移

**测试运行时机**:

- 每次添加新 fixture:`./gradlew.bat testDebugUnitTest --tests "*AdSignageTextFixtureRegressionTest"`
- 每次修改 `ad_signage_rules.json`:同上,期望失败清单 = 受影响的 fixture 列表
- CI gate:`./gradlew.bat testDebugUnitTest` 全绿才能 release

---

## 10. 文件清单

### 10.1 新增(共 32 项)

| 路径 | 状态 | 说明 |
|---|---|---|
| `违规案例/text_medical_ykzp_01.md` ... `text_medical_ykzp_05.md` | 新增 5 个 | medical 桶 |
| `违规案例/text_absolute_best_01.md` ... `text_absolute_best_04.md` | 新增 4 个 | absolute 桶 |
| `违规案例/text_education_*.md` | 新增 3 个 | education 桶 |
| `违规案例/text_food_*.md` | 新增 3 个 | food 桶 |
| `违规案例/text_realestate_*.md` | 新增 3 个 | realestate 桶 |
| `违规案例/text_finance_*.md` | 新增 3 个 | finance 桶 |
| `违规案例/text_cosmetic_*.md` | 新增 2 个 | cosmetic 桶 |
| `违规案例/text_agricultural_*.md` | 新增 2 个 | agricultural 桶 |
| `违规案例/text_signage_*.md` | 新增 1 个 | signage 桶 |
| `违规案例/text_minor_*.md` | 新增 1 个 | minor 桶 |
| `违规案例/text_outdoor_*.md` | 新增 1 个 | outdoor 桶 |
| `违规案例/text_internet_ad_*.md` | 新增 1 个 | internet_ad 桶 |
| `违规案例/text_pestvet_*.md` | 新增 1 个 | pesticide+veterinary 合桶 |
| `违规案例/_text_plan.md` | 新增 | 采集日志(每桶 URL / 状态 / 失败原因) |
| `app/src/test/java/com/icespiritai/offline/rules/AdSignageTextFixtureRegressionTest.kt` | 新增 | JVM JUnit 测试 (~80 行) |
| `app/src/test/java/com/icespiritai/offline/rules/TextFixtureLoader.kt` | 新增 | Frontmatter 解析器 + data class (~100 行) |

合计新增 = 30 fixture + 1 plan log + 1 test + 1 loader = **33 项**。

### 10.2 不动

| 路径 | 状态 |
|---|---|
| `app/src/main/assets/rules/ad_signage_rules.json` v6 / `food_label_rules.json` v4 | 不动 |
| `app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt` / `AdSignageRuleMatcher.kt` / `RulesRepository.kt` | 不动 |
| `app/src/main/java/com/icespiritai/offline/ui/**` / `IceSpiritVisionViewModel.kt` | 不动 |
| `app/src/main/java/com/icespiritai/offline/ocr/**` / `ice_ocr_rules` profile 配置 | 不动 |
| `gradle/libs.versions.toml` / `app/build.gradle.kts` / `buildSrc/**` | 不动 |
| `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt` / `AdSignageMentorFiveImageRegressionTest.kt` | 不动 |
| `违规案例/medical_store_01.{jpg,md}` / 4 张微信实拍 | 不动 |
| 签名 / release 流水线 / Gitea 上传 | 不动 |

---

## 11. 验收标准

- [ ] `违规案例/text_*.md` 总数 = 30
- [ ] 30 条 fixture 的 `来源` URL 全部一手政府站域名(`.gov.cn`)
- [ ] `AdSignageTextFixtureRegressionTest` 三个 @Test 全绿
- [ ] 30 条 fixture 命中规则集合 == 预期集合(精确 set match,零偏差)
- [ ] 13 桶全部覆盖(pesticide/veterinary 合桶记为 1 桶)
- [ ] 所有 `预期命中规则.id` 在 `ad_signage_rules.json` 白名单内(122 条 ID 验证通过)
- [ ] `./gradlew.bat testDebugUnitTest` 全绿(含 mentor test / rule matcher unit test / 不动项)
- [ ] `./gradlew.bat assembleDebug -PmodelProfile=shell` 成功
- [ ] `./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules` 成功(若有 SDK + 模型)

---

## 12. 风险 & 缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 一手政府站某 category 当月通报 < 1 | 中 | 中 | 该桶压缩张数,相邻桶挪配额,总仍保 30 |
| 政府站 URL 长期失效 | 低 | 低 | fixture 内保留完整违法广告语原文,URL 是来源 pointer 不是唯一载体 |
| WebSearch 返回虚假 gov 站(钓鱼 / 镜像) | 低 | 中 | 人工核验 URL 域名(`.gov.cn`),遇可疑源拒收 |
| RuleMatcher 命中多于/少于预期(规则迭代后漂移) | 中 | 中 | exact-set match 即时失败 → fixture 同步更新 → fixture 评审同时是规则评审的「反向压力」 |
| fixture 文本含 `:` 等特殊字符触发解析器误判 | 低 | 低 | 解析器按行处理 + 缩进识别,内嵌文本安全;失败 fixture 单独 log |
| 30 条 fixture 累积后 `testDebugUnitTest` 跑得慢 | 低 | 低 | 30 条纯字符串扫描,实测预计 < 1 秒 |
| 长尾桶(pestvet / signage / outdoor / internet_ad)素材不足 | 中 | 中 | 合桶 + 跨 category 复用同一通报(若该案同时触发多类违规,合并为单 fixture 但桶归一) |

---

## 13. 后续(非本期)

- OCR 模型迭代阶段:把 `text_*.md` 的 `原始违法广告语` 作为「expected OCR text」pin,引入 OCR 引擎跑图像,做端到端一致性测试(在 `AdSignageMentorFiveImageRegressionTest` 体系下扩展,非新文件)
- 规则库 v7+ 时:`text_*.md` 自动升级工具(批量从 JSON 反向生成 fixture 模板) — 不在本期
- `FoodLabelRuleMatcher` 配套文本 fixture:另立 `违规案例/food_text_*.md`,沿用本规范格式,但 frontmatter 用 `food_label_rules.json` 字段名

---

**评审要求**:用户确认本设计后,进入 plan 阶段(由 writing-plans skill 编排 task breakdown),随后按 subagent-driven-development 实施。