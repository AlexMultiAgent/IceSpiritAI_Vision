# 冰灵锐目 — 启用食品标签 tab + 设置层功能可见性开关 + 样式层独立性原则

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-09-10 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | [`CLAUDE.md`](../../CLAUDE.md) |
| 关联 UI spec | [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](2026-08-15-icevision-ui-design.md), [`docs/superpowers/specs/2026-09-02-home-header-tab-polish-design.md`](2026-09-02-home-header-tab-polish-design.md) |
| 关联规则扩写 spec | [`docs/superpowers/specs/2026-08-27-icevision-rules-coverage-audit-design.md`](2026-08-27-icevision-rules-coverage-audit-design.md), [`docs/superpowers/specs/2026-08-28-icevision-ocr-audit-66-design.md`](2026-08-28-icevision-ocr-audit-66-design.md) |
| 关联 release 流水线 | [`/icevision-release`](../../.claude/skills/icevision-release/SKILL.md) skill |
| 关联规则扩写 skill | [`/add-rule-entry`](../../.claude/skills/add-rule-entry/SKILL.md), [`/fixture-audit-add`](../../.claude/skills/fixture-audit-add/SKILL.md) |
| 关联 agent | [`regulation-freshness-checker`](../../.claude/agents/regulation-freshness-checker.md), [`rule-coverage-analyzer`](../../.claude/agents/rule-coverage-analyzer.md), [`adb-runner`](../../.claude/agents/adb-runner.md), [`rule-expander`](../../.claude/agents/rule-expander.md) |

本文档**叠加**在既有 UI spec + 规则扩写 spec 之上,涉及 4 件事:**启用食品标签 tab**(完整对齐广告招牌 UI)+ **设置层「功能可见性」开关**(**默认全开 + VM enforce 至少一个可见**)+ **`food_label_rules.json` v4 → v5(~95 条)两阶段扩写** + **CLAUDE.md 顺手同步过期数字**。后端逻辑(`/project-commit` / `/icevision-release` / ServiceLoader / OCR engine / ThemeMode)与既有构建栈保持现状。

---

## 1. 背景与目标

### 1.1 现状

[RuleTabBar.kt:55](../../app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt#L55) 当前 `visibleTabs = listOf(RuleTab.AdSignage)` 硬编码,**[食品标签全部代码层已就位但 UI 不暴露](../../知识库/食品标签/README.md)**:

- `FoodLabelRule.kt` / `FoodLabelRuleLoader.kt` / `FoodLabelRuleMatcher.kt`([`app/src/main/java/com/icespiritai/offline/rules/`](../../app/src/main/java/com/icespiritai/offline/rules/))— 完整实现
- `CategoryDisplay.FoodLabelCategory.displayName`([CategoryDisplay.kt:61-75](../../app/src/main/java/com/icespiritai/offline/domain/CategoryDisplay.kt#L61))— 10 项已声明
- `IceSpiritVisionViewModel.matchers + matcherFor(tab)`([IceSpiritVisionViewModel.kt:70-80](../../app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt#L70))— `FoodLabeling -> foodMatcher` 已挂上
- 单元测试 `FoodLabelRuleMatcherTest.kt` / `FoodLabelRuleTest.kt` / `FoodLabelRuleLoaderTest.kt` 平行覆盖
- 规则 JSON [`app/src/main/assets/rules/food_label_rules.json`](../../app/src/main/assets/rules/food_label_rules.json) — `version: 4`、`66 条规则、10 个 category`

[SettingsRepository.kt](../../app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt) DataStore 当前只有两个 key:`theme_mode`(string)+ `disclaimer_accepted_at`(long)。无 per-feature visibility 概念。

[CLAUDE.md](../../CLAUDE.md) §"产品方向" 段描述 `ad_signage 129 条 / v10` 与现状(189 / v20)漂移;`食品标识管理规定` / `GB 7718-2011` / `GB 28050-2011` "已 git mv 到 已废止/" 是 v0.1.58 误记 — 实际三份仍在 `知识库/食品标签/` 主目录(`_2027-03-16废止.md` 后缀,过渡期现行,合规)。

### 1.2 目标

1. **启用食品标签 tab** — `RuleTabBar.visibleTabs` 由 `listOf(AdSignage)` 改为参数化接收 `Set<RuleTab>`,由 ViewModel 注入(`{AdSignage, FoodLabeling}` 默认全开)
2. **设置层「功能可见性」开关** — SettingsScreen 新 Card「功能可见性」,每行一个 Switch(广告招牌 / 食品标签),`SettingsViewModel.setFeatureVisible(tab, visible)` 在 size<=1 时拒绝 + snackbar
3. **`food_label_rules.json` v4 → v5(~95 条)** — 覆盖 GB 7718-2025 致敏原 8 强制 / 食品标识监督管理办法 §7-§40 / 食品安全法 §80/§81/§125 / GB 28050 / GB 13432 / 婴幼儿乳粉。重点 gap,不全条款穷举
4. **样式层独立性原则** — 声明 `HighlightOverlay` / `SeverityChip` / `CaptureBar(hasHits)` / `ViewerTextList` 子串高亮 / `StatusBanner` KPI tooltip 等 UI 组件应当不耦合 `RuleTab`,只接 `Severity` / `List<Hit>` 输入。本期落地声明 + 现有合规性审计;后续样式优化集中在「样式层」,一处改、全部 domain 受益
5. **CLAUDE.md 顺手同步** — 数字过期(ad_signage 129→189)+ 食品标签 KB 描述修正(过渡期现行,合规)
6. **食品标签 KB README Changelog** — 记录 v0.1.69 / v0.1.70 扩写

### 1.3 非目标(本期)

- 不删 `RuleTab.FoodLabeling` enum / `FoodLabelRule*` 文件 — CLAUDE.md §产品方向 已声明保留
- 不动 KB 三份 `_2027-03-16废止.md`(过渡期现行,合规)
- 不改 `AdSignageRuleMatcher` / `Loader` / `Rule` / `ad_signage_rules.json`(已合规)
- 不改 `RuleTabBar` 内 PillTab 视觉(配色 / 字号 / 间距)— 仅加 `visibleTabs` 参数 + `RuleTab.iconRes` 字段
- 不改 OCR engine / ServiceLoader / Profile 装配
- 不引新依赖 / 不写 i18n 资源 / 不写 onboarding dialog / 不加「重置默认」按钮
- 不实现 TTS 详情播报(列 TODO,见 §8)

---

## 2. 总体方案

4 件改动独立评估候选。

### 2.1 食品标签 tab 解锁

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — `RuleTabBar` 加 `visibleTabs: Set<RuleTab>` 参数,由 ViewModel 注入(选定)** | 改动 1 个文件签名 + 1 个调用方 + 1 个测试;`visibleTabs` 不再硬编码;`PillTab` 渲染循环遍历 | — |
| B — `RuleTabBar` 内部读 DataStore(Repository locator 注入) | UI 层直接消费持久化 | 违反 CLAUDE.md "模块边界" 原则 — UI 不持有状态,由 ViewModel 注入;测试需 mock Repository |
| C — 完全删 `RuleTab.FoodLabeling` enum,纯 shell | 最低工作量 | 违背 CLAUDE.md "保留 FoodLabeling 模板可复制性" |

### 2.2 「至少一个可见」enforce 位置

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — `SettingsViewModel.setFeatureVisible` 校验 size<=1 时拒绝 + snackbar(选定)** | 与现有 `themeMode` 行为模式一致;单元测试覆盖三 case;`SettingsScreen` 只触发事件 | — |
| B — SettingsScreen Switch `onCheckedChange` 校验 | 单文件改动小 | 可被外部代码(测试 / 深链)绕过 |
| C — 不 enforce,允许 0 可见 + HomeScreen 显示「请启用」占位 | 与用户原话「至少一个可见」直接冲突 |

### 2.3 规则扩写范围

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — 仅查新 + 补 Gaps,v5 目标 ~95 条(选定)** | 覆盖 GB 7718-2025 致敏原 / 食品标识监督管理办法 / 食品安全法 / GB 28050 / GB 13432 / 婴幼儿乳粉 重点条款 | — |
| B — 查新 + 重点章节详覆盖,~120-140 条 | 覆盖更广 | 工作量 4-5 发版号;v0.1.49 经验:90+ 条已可上线,后续按 fixture gap 渐进扩展 |
| C — 查新 + 条款穷举,~150-180 条 | 覆盖最全 | YAGNI,工作量大;fixture 召回上限可能让穷条零命中 |
| D — 仅查新不补缺 | 最小工作量 | "查漏补缺" 错位 |

### 2.4 样式层独立性原则落地

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — 现状审计 + 声明 + 抽离"Style Layer" 设计意图(选定)** | §3.5 列出现状已天然 domain-agnostic 组件;§3.6 列出本期"顺手抽样式"清单(若有);§8 列后续样式优化集中点 | — |
| B — 本次方案大范围 refactor UI 抽样式层 | 抽 `SeverityContainer` / `SeverityColors` 等公共 API | YAGNI;现状 UI 已基本解耦;大 refactor 容易引入回归,延后到样式真正"批量优化"时再做 |
| C — 不声明,不抽离 | 维持现状 | 后续样式优化时无法定位「一处改、全部受益」锚点 |

---

## 3. 详细改动

### 3.1 `RuleTabBar` 签名扩展 — [RuleTabBar.kt](../../app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt)

**当前** (L39-42, L55, L71-96):

```kotlin
enum class RuleTab(val titleRes: Int) {
    AdSignage(R.string.tab_ad_law),
    FoodLabeling(R.string.tab_food_label),
}

private val visibleTabs: List<RuleTab> = listOf(RuleTab.AdSignage)

@Composable
fun RuleTabBar(
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
)
```

**改为**:

```kotlin
enum class RuleTab(val titleRes: Int, val tabIcon: ImageVector) {
    AdSignage(R.string.tab_ad_law, Icons.Outlined.Verified),
    FoodLabeling(R.string.tab_food_label, Icons.Outlined.LocalDining),
}

@Composable
fun RuleTabBar(
    visibleTabs: Set<RuleTab>,           // 新参数,VM 注入
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
)
```

- `tabIcon` 直接 inline `Icons.Outlined.*`(沿用 [CLAUDE.md L917](../../CLAUDE.md) 已声明的 `compose.material.icons.extended` 依赖,不引新 dep,不写矢量 drawable)
- `Icon(imageVector = Icons.Outlined.Xxx, ...)` 改为 `Icon(imageVector = tab.tabIcon, ...)`(L130-137)
- `visibleTabs.forEach` 保持遍历 — 遍历顺序 = `RuleTab.entries`(enum 声明顺序,AdSignage → FoodLabeling),即食品标签 tab 永远在广告招牌右侧
- `PillTab` 渲染循环遍历 — `size==0` 边界由 ViewModel 保证(§6 兜底表)

### 3.2 `SettingsViewModel.enforce` 算法 — [SettingsViewModel.kt](../../app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt)

**新增**:

```kotlin
sealed class SettingsSnackbar {
    object LastFeatureCannotHide : SettingsSnackbar()
    data class PersistFailed(val cause: Throwable) : SettingsSnackbar()
}

private val _snackbar = MutableSharedFlow<SettingsSnackbar>(extraBufferCapacity = 4)
val snackbar: SharedFlow<SettingsSnackbar> = _snackbar.asSharedFlow()

fun setFeatureVisible(tab: RuleTab, visible: Boolean) {
    viewModelScope.launch {
        val current = visibleFeatures.value
        if (!visible && current.size <= 1) {
            _snackbar.tryEmit(SettingsSnackbar.LastFeatureCannotHide)
            return@launch
        }
        val next = if (visible) current + tab else current - tab
        runCatching { settingsRepository.setVisibleFeatures(next) }
            .onFailure { _snackbar.tryEmit(SettingsSnackbar.PersistFailed(it)) }
    }
}
```

`SettingsScreen.kt` `LaunchedEffect` 消费 `snackbar`:

```kotlin
LaunchedEffect(vm) {
    vm.snackbar.collect { msg ->
        when (msg) {
            SettingsSnackbar.LastFeatureCannotHide ->
                snackbarHostState.showSnackbar("至少保留一个功能可见")
            is SettingsSnackbar.PersistFailed ->
                snackbarHostState.showSnackbar("设置保存失败")
        }
    }
}
```

### 3.3 持久化 — [SettingsRepository.kt](../../app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt)

**新增**:

```kotlin
private val VISIBLE_FEATURES = stringSetPreferencesKey("visible_features")

val visibleFeatures: Flow<Set<RuleTab>> = dataStore.data
    .catch { emit(emptyPreferences()) }
    .map { prefs ->
        val raw = prefs[VISIBLE_FEATURES]
            ?: return@map RuleTab.entries.toSet()         // key 缺失 → 默认全开
        raw.mapNotNullTo(mutableSetOf()) { name ->
            RuleTab.entries.firstOrNull { it.name == name }
        }.ifEmpty {
            Log.w("SettingsRepository", "visible_features 反序列化空集, fallback 默认全开")
            RuleTab.entries.toSet()                        // 反序列化失败 → 兜底全开
        }
    }

suspend fun setVisibleFeatures(value: Set<RuleTab>) {
    dataStore.edit { it[VISIBLE_FEATURES] = value.map(RuleTab::name).toSet() }
}
```

**为什么 `RuleTab.name` 而非 ordinal**:ordinal 在 enum 重排时漂移;`name` 是稳定 key,沿用 `ThemeMode.name` 模式(参考 [`SettingsRepository.kt:34-35`](../../app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt#L34) 既有 `theme_mode` key 序列化)。

### 3.4 食品标签规则扩写 — [food_label_rules.json](../../app/src/main/assets/rules/food_label_rules.json)

**v0.1.69 v5 ~95 条**:

| 来源 | 新增 | 重点条款 |
|---|---|---|
| GB 7718-2025 致敏原 | 5-8 条 | §5 八大类强制(每类一 rule)+ 四类推荐 |
| 食品标识监督管理办法(SAMR 令100 号) | 12-15 条 | §7 第(一)-(四)项 + §15-§40 核心条款(强制标示内容 / 生产日期 / 保质期 / 储存条件 / 生产者信息 / 联系方式 / 营养成分 / 致敏原 / 转基因 / 辐照 / 地理标志 / 特殊食品) |
| 食品安全法 §80 / §81 / §125 | 3-5 条 | §80(预包装食品标签)/ §81(婴幼儿乳粉 + 特殊膳食用食品)/ §125(违法处罚要点) |
| GB 28050-2011 营养标签 | 3-5 条 | 能量核心 / 蛋白质 NRV / 碳水 / 钠 / 强化的营养成分声称 |
| GB 13432-2013 特殊膳食用食品 | 2-3 条 | 分类 / 适用人群 / 食用方法 |
| 婴幼儿配方乳粉产品配方注册管理办法 | 2-3 条 | 注册号格式 / 段位声称限制 |

**v0.1.70 v5.1**(同发版号二阶段,沿用 v0.1.49 经验):fixture 命中 < 60/N 时扩 +5-10 条 + 既有规则关键词扩。

**v0.1.71+**(本期预留,不实现):fixture coverage 扩展 + `rule-coverage-analyzer` 产出 P0-P4 队列。

### 3.5 样式层独立性原则 — 现状审计

| 组件 | 当前耦合点 | domain-agnostic 状态 |
|---|---|---|
| `HighlightOverlay` ([ui.home](../../app/src/main/java/com/icespiritai/offline/ui/home/) / [ui.viewer](../../app/src/main/java/com/icespiritai/offline/ui/viewer/)) | 仅消费 `List<Hit>` + 图片 URI | 已天然解耦 — 加 `Severity` 入参 |
| `SeverityChip` | 仅消费 `Severity` | 完全解耦 |
| `CaptureBar(hasHits)` | 仅消费 `Boolean` | 完全解耦 |
| `StatusBanner` KPI tooltip | 仅消费 `Severity` | 完全解耦 |
| `HitCard` | 消费 `Hit`(`category` 显示走 `CategoryDisplay` 适配) | 轻微耦合 — `CategoryDisplay` 是集中适配器,新增 domain 改 1 个文件 |
| `ViewerTextList` `worstSeverityForLine` + `highlightMatchedSubstrings` | 消费 `Severity` + 行文本 | 已天然解耦(同 `severityRank` + `TextNormalizer`) |
| `ResultPanel` | 消费 `List<Hit>` + `severityRank` | 已天然解耦 |
| `EvidencePackageBuilder` | 消费 `Rule` + `CategoryDisplay` | 轻微耦合 — 同 `HitCard` |

**结论**:现状 UI 已基本 domain-agnostic,主要耦合点是 [`CategoryDisplay.kt`](../../app/src/main/java/com/icespiritai/offline/domain/CategoryDisplay.kt) 这个适配器(集中点,新增 domain 改 1 个文件即可)。**后续样式优化的核心入口 = `CategoryDisplay` + `HighlightOverlay` + `CaptureBar` + `ViewerTextList`**。

### 3.6 样式层独立性 — 本期落地

- `CategoryDisplay.kt` 新增 `FoodLabelCategory` 10 项已声明(L61-75)— **本期不动**
- `RuleTab.iconRes` 字段(§3.1)— 保证不同 domain tab 视觉差异化
- `HighlightOverlay` 已天然解耦,**本期不动**
- §8 列后续样式优化集中点

### 3.7 `CategoryDisplay.kt` 适配路径(食品标签启用后)

- 食品标签 tab 选中 → `IceSpiritVisionViewModel.matchers[FoodLabeling]` → 命中 → `List<Hit>` 含 `category: FoodLabelCategory` → `HitCard` 调 `CategoryDisplay.displayName(FoodLabelCategory.X)` 渲染 label
- 走通路径已存在,本期不需改 `CategoryDisplay.kt`

### 3.8 CLAUDE.md 顺手同步

| 行号 | 改前 | 改后 |
|---|---|---|
| L39 / L97 | `ad_signage_rules.json` 129 条 / v10 / 14 类别 | ad_signage_rules.json 189 条 / v20 / 14 类别 |
| L97 | "GB 7718-2011 / GB 28050-2011 / 食品标识管理规定 已 git mv 到 已废止/" | 三份 KB 仍在 `知识库/食品标签/` 主目录,`_2027-03-16废止.md` 后缀表示「过渡期现行」;2027-03-16 过渡期满再统一迁移 |
| L49 | "FoodLabeling tab 入口当前不向用户暴露,可见 `RuleTabBar.kt` 内部 `visibleTabs = listOf(RuleTab.AdSignage)`" | 改为"`v0.1.69 起食品标签 tab 由 `visibleFeatures` 默认全开启用,`RuleTabBar.kt:visibleTabs` 参数化接收 ViewModel 注入的可见集合;`FoodLabeling` enum 项 / `FoodLabelRule*` 完整代码路径仍保留`" |

### 3.9 食品标签 KB README Changelog — [知识库/食品标签/README.md](../../知识库/食品标签/README.md)

**新增 section**:

```markdown
## Changelog

### v0.1.69 (2026-09-XX)
- 规则 v4 → v5:66 → ~95 条
- 新增覆盖:GB 7718-2025 致敏原 8 强制 / 食品标识监督管理办法 §7-§40 核心 / 食品安全法 §80/§81/§125 / GB 28050 / GB 13432 / 婴幼儿乳粉重点 gap
- 真机 fixture:food_labelaudit{N} (N = v0.1.69 阶段)

### v0.1.70 (2026-09-XX)
- 规则 v5 → v5.1:fixture 命中 < 60/N 时扩 +5-10 条
- (同发版号二阶段,沿用 v0.1.49 经验)
```

---

## 4. 不动的东西

- `AdSignageRuleMatcher` / `Loader` / `Rule` / `ad_signage_rules.json`(已合规,189 / v20)
- `FoodLabelRuleMatcher` / `Loader` / `Rule`(已实现,本期不动实现,只扩 JSON)
- `AhoCorasickMatcher` 基类 / `OcrEngineFactory` / `ServiceLoader` / Profile 装配
- `RuleTabBar` 内 PillTab 视觉(配色 / 字号 / 间距)— 仅加 `visibleTabs` 参数 + `RuleTab.iconRes` 字段
- `IceSpiritVisionViewModel.setTab` 3-state 契约 / `IceSpiritVisionViewModelTabTest`
- `ThemeMode` / `Color.kt` 调色板 / 主题切换逻辑
- KB 三份 `_2027-03-16废止.md`(过渡期现行,合规)
- `R.string.app_name` / `R.string.tab_ad_law` / `R.string.tab_food_label` / `R.string.tab_switch_desc`
- `IceSpiritTypography` token(`Type.kt`)
- `HomeTopBar.kt` / `HomeScreen.kt` / `ImagePreview` / `mascot_glasses_bust`
- TTS 引擎 / APK pivot(详见 [`project-tts-engine-apk-pivot.md`](../../.claude/projects/d--GitHub-IceSpiritAI-Vision/memory/project-tts-engine-apk-pivot.md))

---

## 5. 测试

### 5.1 单元测试(JVM,JUnit + Truth + 既有 Robolectric)

| 测试文件 | 改动 |
|---|---|
| `SettingsViewModelTest.kt`(新) | `setFeatureVisible` 三 case(正常 disable / 拒绝 disable last / 正常 enable)+ DataStore 反序列化兜底 + 写入失败抛异常 |
| `RuleTabBarTest.kt`(扩) | `visibleTabs` 参数化渲染(0 / 1 / 2 元素)+ 选中/未选 + Role.Tab 计数 + 新增 PILL_LEADING_ICON per-tab 区分(`_<tabName>` 后缀) |
| `FoodLabelRuleMatcherTest.kt`(扩) | v5 新规则的 keyword / regulation / lawText / severity / category 断言;`MIN_KEYWORD_FOR_VARIANTS ≥ 5 char` 边界 |
| `IceSpiritVisionViewModelTabTest.kt`(扩) | `visibleFeatures = {AdSignage}` / `{FoodLabeling}` / `{AdSignage, FoodLabeling}` 三路由;enforce 边界(`setFeatureVisible(FoodLabeling, false)` 当 size==1 触发 snackbar) |
| `FoodLabelRuleLoaderTest.kt`(改) | v5 反序列化版本号断言;id 全文件唯一 |
| `SettingsRepositoryTest.kt`(新) | DataStore 写入 / 读取 / 损坏兜底 |

### 5.2 端到端(真机 androidTest,沿用 audit71 harness 模式)

- `FoodLabelAudit{N}ImageE2ETest.kt`(新,N = v0.1.69 阶段)— harness 模式 = 1 cold + N warm,真机 Huawei nova 6 (AGQV023313008161, SDK 35);harness TAG=`FoodLabelAudit{N}E2E`;行标记 `[COLD]/[WARM]/[HITS]/[OCR_HIT]/[OCR_NO_HIT]/RESULT_JSON`(同 [`AdSignageAudit71ImageE2ETest.kt`](../../app/src/androidTest/java/com/icespiritai/offline/rules/AdSignageAudit71ImageE2ETest.kt))
- `app/src/androidTest/assets/fixtures/food_label_audit{N}/coverage_matrix.md`(新)— 自动生成 fixture ↔ rule_id 命中表
- `docs/smoke/2026-09-XX-food-labeling-v0.1.69-e2e.md`(新)— 真机烟测记录

**阈值**:沿用 v0.1.49 `ANY_HIT ≥ 60/N`(参考 [`docs/knowledge/2026-09-02-audit71-v11-rules-e2e.md`](../../docs/smoke/2026-09-02-audit71-v11-rules-e2e.md))。v0.1.69 第一轮不达标,v0.1.70 v5.1 同发版号二阶段扩展。

### 5.3 Hook 自动化(沿用现有,不修改)

- `.claude/hooks/validate-rule-json.js` 在 `Edit/Write` 落 `food_label_rules.json` 时跑 4 项校验
- `.claude/hooks/post-tool-use.js` 在 `git commit` 后扫 Co-Authored-By trailer
- `regulation-freshness-checker` agent 在 `git commit` 含 release marker(`feat(v0.1.69)`)时由 `/project-commit` skill 自动 dispatch
- `rule-coverage-analyzer` agent 在 v0.1.71+ 阶段自动消费

---

## 6. 边界 / 错误处理

| 场景 | 行为 | 兜底位置 |
|---|---|---|
| DataStore 损坏(读取 IOException) | `catch { emit(emptyPreferences()) }` + logcat warn + 默认全开 | `SettingsRepository.visibleFeatures` |
| DataStore 保存 `visibleFeatures` 写入失败 | `runCatching` + snackbar"设置保存失败";UI 不刷新 | `SettingsViewModel.setFeatureVisible` |
| 反序列化 enum name 不在 `RuleTab.entries` | `mapNotNull` 跳过 → ifEmpty fallback 全开 + 一次性 logcat warn | `SettingsRepository.visibleFeatures` |
| 用户保存了空 set(理论绕过 enforce) | 启动时 `ifEmpty { RuleTab.entries.toSet() }` 兜底 | `SettingsRepository.visibleFeatures` |
| `food_label_rules.json` 反序列化失败 | 沿用 `ad_signage_rules.json` 既有兜底(显示"规则加载失败,请重新安装") | `FoodLabelRuleLoader.load()` |
| `FoodLabelRuleMatcher` 初始化失败 | ViewModel `_matcherError: SharedFlow<Throwable>` 暴露,UI 显示"分析失败"占位 | `IceSpiritVisionViewModel` |
| `visibleFeatures.size == 1` 且 setTab 切到不可见 tab(race) | VM `setTab` 校验 → no-op + logcat warn | `IceSpiritVisionViewModel.setTab` |
| 食品标签 tab 启用但 OCR 返回空 | 同广告招牌:展示"未识别到文字"占位 + 0 hit 状态 | `IceSpiritVisionViewModel` |
| 真机 e2e fixture OCR 召回< 60/N | v0.1.70 v5.1 同发版号二阶段扩展 | `/fixture-audit-add` skill |
| 食品标签 KB 引用 `已废止/` | v0.1.69 前必跑 `regulation-freshness-checker`,修 P0 drift | agent 自动 dispatch |
| Tab 渲染 `visibleTabs.size == 0`(VM 兜底破缺) | ViewModel 兜底,UI 永不触发 | `IceSpiritVisionViewModel.visibleFeatures` 派生 |

---

## 7. 文档同步

- 本 spec 叠加在 [`2026-09-02-home-header-tab-polish-design.md`](2026-09-02-home-header-tab-polish-design.md) §3.2 之上,旧版 §3.2 "FoodLabeling 启用时这个分支还要用" 的承诺在本文档 §3.1 兑现
- 本 spec 叠加在 [`2026-08-27-icevision-rules-coverage-audit-design.md`](2026-08-27-icevision-rules-coverage-audit-design.md) 之上,沿用 `ANY_HIT ≥ 60/N` 阈值 + 同发版号二阶段扩展模式
- CLAUDE.md 顺手同步 3 处过期(§3.8)
- 食品标签 KB README Changelog(§3.9)
- Release changelog 发版时由 `/project-commit` skill 自动同步

---

## 8. 后续 TODO(本期不实现)

| TODO | 优先级 | 触发条件 |
|---|---|---|
| **TTS 详情播报** — 当前只播 summary(命中 X 项违规 / Y 项警告 / Z 项信息),优化为依次播报每条命中(违规:品牌 X,命中『最佳』『绝对化用语』,依据《广告法》第四条;警告: ...) + 多 domain 切换提示(广告招牌 → 食品标签 时提示"切到食品标签") | P1 | 用户反馈 "语音播报太粗" 时 |
| **样式层批量优化** — 抽 `SeverityContainer` / `SeverityColors` / `DomainHitComponents` 等公共 API(本期 §3.5 审计为基本解耦,推迟到大批量样式优化时再抽);同时 TTS 播报详情化可与样式优化并行做 | P2 | 真正有「批量样式优化」需求时(任何 domain 都受益) |
| **多语言资源 i18n** — 本期仅 zh | P3 | 接到国际版需求时 |
| **重置默认按钮** — 设置层「功能可见性」加一键恢复全开 | P3 | 用户多次反馈 "误关找不到恢复入口" 时 |
| **Onboarding / 教学 dialog** — 首次启用食品标签 tab 弹轻量说明 | P3 | 反馈启用率低时 |
| **v0.1.71+ fixture coverage 扩展** — `rule-coverage-analyzer` agent 产出 P0-P4 扩展队列 | P2 | v0.1.70 落地后 |
| **食品标签 KB 2027-03-16 迁移** — 过渡期满,三份 `_2027-03-16废止.md` → `知识库/已废止/` | 计划中 | 2027-03-16 过渡期满 |

---

## 9. 发版路线

- **v0.1.69** — §3.1-§3.9 全部落地:
  - 规则 v4 → v5,~95 条
  - 食品标签 tab 默认全开启用
  - 设置层「功能可见性」Card + VM enforce
  - CLAUDE.md 顺手同步过期数字
  - 食品标签 KB README Changelog
  - 真机 fixture `food_label_audit{N}` (ANY_HIT ≥ 60/N)
- **v0.1.70** — fixture 命中不达标时 v5.1 同发版号二阶段扩展(+5-10 条)
- **v0.1.71+** — `rule-coverage-analyzer` agent 产出 P0-P4 扩展队列,后续发版号渐进