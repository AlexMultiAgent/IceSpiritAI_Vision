# 冰灵锐目 UI 现代化 — Material 3 Expressive 视觉刷新

| 项 | 值 |
|---|---|
| 文档版本 | v0.2.0(v0.1.0 重构) |
| 日期 | 2026-08-25 |
| Spec 状态 | 待评审 |
| 上一版 UI spec | [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](2026-08-15-icevision-ui-design.md) |
| 关联项目根指令 | [`CLAUDE.md`](../../CLAUDE.md) |
| 关联 plan | [`docs/superpowers/plans/2026-08-25-icevision-ui-modernization-plan.md`](../plans/2026-08-25-icevision-ui-modernization-plan.md) |

本文档记录冰灵锐目 UI 从 Material 3 基础形态升级到 **Material 3 Expressive**(Google 2025 设计语言)的**设计决策**。实施细节(字符串 / 依赖 / Phase 切分 / 测试路径)在 plan 中。

---

## 1. 背景与目标

**目标用户**:执法人员(市监 / 城管 / 工商局)— 与 2026-08-15 UI spec 一致,本 spec 不重新界定。

**正式发布给员工使用场景下的具体问题**:

- 圆角偏小、字号偏小 → 现场强光下扫一眼识别度差
- 命中卡严重度靠小药丸传达 → 注意力被分散到非关键信息
- 状态条无数字 KPI → 违规数要切卡片才能看到
- 加载态只有 spinner → 进度不可见
- 拍照按钮与选图按钮挤在一行 → 主操作不突出
- 内容四周留白,未做 edge-to-edge → 视觉张力不足

**目标**:在"取证的克制气质"与"2026 年的现代感"之间平衡。

**非目标**:后端逻辑 / OCR / 规则库 / 状态机 / 导出 / 历史记录 / 批量审图 / 食品标签 OCR(全部不动)。

---

## 2. 设计约束(在任何决策之前必须知道)

1. **跨项目 1:1 对齐**(CLAUDE.md):`Color.kt` / `Type.kt` / `Shape.kt` 中**已存在**的 token 值被 IceSpiritAI_Chat 1:1 对齐,本 spec **只允许新增 token,不动现有值**。任何修改现有 token 的提议必须先与 Chat 项目同步。
2. **后端不变**:`IceSpiritVisionViewModel` / `OcrEngine` / `RuleMatcher` / `ExportAction` / 状态机完全不动。
3. **AdSignage Tab 优先**:`RuleTabBar.visibleTabs = listOf(RuleTab.AdSignage)` 不变(FoodLabeling 模板保留,CLAUDE.md `产品方向`)。
4. **深色优先 + 浅色归档**(memory `feedback-dual-theme`):默认深色适配现场,设置切浅色适配归档;不使用冰灵家族蓝。

---

## 3. 方向选择(从 4 选 1)

| 方向 | 取舍 | 选定 |
|---|---|---|
| **A:Material 3 Expressive** | 圆角 + 排版 + 动效全面升级;现代感与执法严肃感之间最平衡 | ✓ |
| B:Editorial(Apple News / Notion 风格) | 大字号 + 法规默认展开;偏轻,现场快看不够快 | |
| C:仪表盘式(运维监控) | 信息密集;不是"现代"而是"专业",偏离诉求 | |
| D:仅微调(圆角/字号小幅调) | 风险最低,但几乎无可见变化 | |

**为什么不选 B**:法规默认展开会让命中列表变很长,现场翻不到关键信息;且整体风格"太轻",取证严肃感弱化。
**为什么不选 C**:更像内部工具 v2,反而不像现代化 UI;现场强光下小字号可读性下降。
**为什么不选 D**:用户已表态"更化"是核心诉求,微调无法兑现。

---

## 4. 视觉系统

### 4.1 严重度色板(4 角色 × 4 token,只新增)

每个严重度角色需要 4 个 Material You token:accent(色条 / 描边)/ onAccent(accent 上的文字)/ container(背景 12% 染色)/ onContainer(背景上的文字)。

| 角色 | accent 深 / 浅 | container 深 / 浅 | onContainer 深 / 浅 |
|---|---|---|---|
| Violation (Error) | `#f87171` / `#dc2626` | `#7f1d1d` / `#fee2e2` | `#fecaca` / `#7f1d1d` |
| Warning | `#fbbf24` / `#d97706` | `#78350f` / `#fef3c7` | `#fde68a` / `#78350f` |
| Positive (合规) | `#86efac` / `#16a34a` | `#14532d` / `#dcfce7` | `#bbf7d0` / `#14532d` |
| Info | `#60a5fa` / `#2563eb` | `#1e3a8a` / `#dbeafe` | `#bfdbfe` / `#1e3a8a` |

**复用规则**:Violation / Warning / Positive 的 `accent` 复用现有 `DarkIceChatError` / `LightIceChatError` 等(hex 不动)。Info 是**完全新增**的 4 token。共新增 12 个 token。

**4 角色而非 3 角色**的理由:现有 `Severity` enum 缺 Positive;"未发现违规" 与 "信息" 是不同语义的两种"无问题"状态 — Positive 表示扫描结果合规(没有任何命中),Info 表示"有命中但级别低"。混用会让成功状态失真。

**为什么不进 `MaterialTheme.colorScheme`**:严重度色不属于品牌主色,放进去会污染通用组件(如 `Button.error` 会被覆盖)。暴露为独立的 `LocalSeverityColors` CompositionLocal。

### 4.2 字号(只新增缺失的,不动现有)

`Type.kt` 现有 9 个 token 值全部冻结(被 Chat 1:1 对齐)。**新增** 3 个 Material 3 Typography 缺失 token:

| Token | 值 | 用途 |
|---|---|---|
| `displaySmall` | 40sp SemiBold | 启动屏 / 极少用 |
| `headlineMedium` | 30sp SemiBold | KPI 数字 |
| `headlineSmall` | 26sp SemiBold | 命中卡标题、顶栏标题 |

### 4.3 形状(完全冻结)

`Shape.kt` 现有 16dp / 12dp / 12dp / 20dp / 24dp 已被 Chat 1:1 对齐。该曲线已偏 Material You Expressive(16dp `extraSmall` 已大于 Material 默认 4dp)。**本 spec 不做形状修改**。

### 4.4 动效

新增 `IceMotion` data class:

- `standardDuration = 300ms`(FastOutSlowIn)— 数字过渡、状态条入场
- `emphasizedDuration = 500ms`(CubicBezier(0.2, 0, 0, 1))— 命中卡入场、FAB 反馈

新增 `Modifier.emphasizedEnter()`:scale 0.95 → 1.0 + alpha 0 → 1,500ms,给命中卡 / 状态条用。

---

## 5. 严重度配色分发

通过 `LocalSeverityColors` (CompositionLocal) 在 `IceSpiritVisionTheme` 中注入。组件读 `iceSpiritSeverityColors.accent(Severity)` / `.container(Severity)` / `.onContainer(Severity)`。

**为什么用 CompositionLocal 而不是 `MaterialTheme.colorScheme`**:严重度色不属于品牌,4 角色 × 4 token 已经 16 个,塞进 `colorScheme` 会撑爆角色槽位且语义不清。

---

## 6. 组件改动(按用户旅程)

### 6.1 Idle / Capture 状态

| 元素 | 设计 |
|---|---|
| 状态条 | 相机图标 + "请对正图片后点击拍照"(`bodyMedium`) |
| 顶栏 | 透明背景 + `headlineSmall` 居中标题 + Outlined 齿轮图标(22dp) |
| CaptureBar | `BottomAppBar`(透明背景)+ 左侧 40dp 小 `PhotoLibrary` FAB + 右侧 56dp `ExtendedFloatingActionButton`(📷 + "拍照" label) |

**为什么 Extended FAB**:Material 3 Hero element 原则 — 拍照是主操作,需要占位 + 视觉权重。Row + Button 看起来"按钮组",FAB 看起来"动作"。BottomAppBar 比 Row 更现代,占 12% 屏高,让主屏图片延伸到屏底。

### 6.2 Loading 状态

- 状态条:`CircularProgressIndicator(20dp, strokeWidth=3dp)` + "OCR 识别中…" / "规则扫描中…"
- 命中区:3 张 shimmer 骨架卡(高度 / 圆角 / 间距与真实 HitCard 一致),左侧 6dp 占位色条
- shimmer 用 `Brush.horizontalGradient` + `rememberInfiniteTransition`(1200ms 循环)

### 6.3 Complete 状态(有命中)

- **状态条**改为 KPI 横条,3 段:**违规 N** / **警告 N** / **信息 N**。每段:`headlineMedium`(30sp SemiBold)数字 + `bodySmall` 标签 + 严重度图标。数字用 `AnimatedContent`(slideIn + fadeIn)滑入。背景随 `maxSeverity` 着色(Violation → violationContainer,Warning → warningContainer,Info → successContainer)。
- **命中卡**:
  - 左侧 6dp 严重度色条(从顶到底)
  - 卡片背景渐变(`Brush.verticalGradient(container@12% → surfaceContainerHigh)`)
  - `headlineSmall`(26sp SemiBold)引号包住的命中文字(`"中国第一"`)
  - 类别 / 法规摘要 `bodyMedium`,不折叠
  - `FilledTonalButton` 查看法规全文,默认折叠
  - `Modifier.emphasizedEnter()` 入场
- **违规框描边**(`HighlightOverlay`):
  - 描边宽度 4dp → 6dp
  - `Brush.linearGradient`(accent → accent@60%)从左上到右下的轻微渐变
  - 300ms 描边动画(`AnimatedFloat` 透明度)
  - Info 严重度现在也显示描边(原代码跳过)

**为什么 KPI 横条**:扫一眼即可知违规数(不需要切卡片)。数字用 `AnimatedContent` 而非纯 fadeIn,数值变化有方向感(从下方滑入)。

**为什么色条不用徽章**:6dp 色条是"线性信号",徽章是"点状信号" — 现场视线扫描时,色条先于文字进入视觉。

**为什么违规框描边变粗**:视觉权重与"违规严重度"成正比 — 6dp 描边 + 严重度色 + 渐变让违规区域在远处也能识别。

### 6.4 Complete 状态(无命中)

- 状态条:Positive container 着色,3 段全 0
- 命中区:`OcrTextHeader` + "未发现违规用语" 文字 + `headlineSmall` 排版

### 6.5 Error 状态

- 状态条:Violation container 着色 + 错误提示
- 错误面板:`ErrorPanel`(`error` color 文字 + 重试 / 返回按钮)— 沿用,只调排版

### 6.6 SettingsScreen

- 三段式 → Card-per-section + ListItem 包裹每行
- 主题三选一 → `SegmentedButton`(跟随系统 / 深色 / 浅色)
- 顶栏标题左对齐 + `headlineSmall`

### 6.7 ViewerScreen

- OCR token 列表:每个 token 包裹在 `Surface(shape = RoundedCornerShape(10.dp), color = severityContainer.copy(alpha = 0.12f))`
- 根容器加 `Modifier.animateContentSize()` — token 列表变化平滑过渡
- 双指缩放 / 双击切换(已存在,保留)

### 6.8 IceSpiritVisionActivity

- `onCreate` 加 `enableEdgeToEdge()`(androidx.activity 1.9+)
- 状态栏 / 手势区 inset 由各 `Scaffold` / `TopAppBar` / `BottomAppBar` 内部消费
- 主屏预览图顶到状态栏后;顶栏背景透明(`Color.Transparent`),文字 White / Black 自适应

---

## 7. 验收标准

- [ ] **视觉**:深色主屏 idle / complete,浅色主屏 idle / complete — 4 张 Robolectric golden 与人工目测一致
- [ ] **行为**:4 张广告招牌 fixture(蟹都汇 / 杜蕾斯 / 中医秘方 / 协和医院)`ice_ocr_rules` profile — OCR 行数 / 命中数 / 严重度分布与 v0.1.14 字节级一致
- [ ] **测试**:`testDebugUnitTest` 全绿,既有后端测试(VieModel / RuleMatcher / Export)未变更
- [ ] **真机**:华为 nova 6 + emulator API 26 跑 `connectedDebugAndroidTest`,edge-to-edge 不被系统栏 / 手势区遮挡
- [ ] **回归**:`versionCode 18`,`versionName 0.1.18`;`user-changelog.md` 列出 v0.1.15 / 0.1.16 / 0.1.17 / 0.1.18 四个版本
- [ ] **约束**:CLAUDE.md Chat 1:1 约束保持 — `Color.kt` / `Type.kt` / `Shape.kt` 现有 token hex 不变

---

## 8. 决策登记

| 决策 | 依据 |
|---|---|
| 选 Material 3 Expressive 而非 Editorial / 仪表盘 | 用户在 4 个候选中选 A |
| 全量刷新而非重点页改版 / 仅微调 | 用户确认 |
| 后端 / 状态机 / 导出完全不动 | 设计原则 |
| 现有 Type / Shape token 不动,只新增缺失的 3 个 Type | CLAUDE.md 跨项目 1:1 约束 |
| 严重度色通过 `LocalSeverityColors` 注入 | 避免每组件手挑 dark/light |
| 4 角色严重度(Violation/Warning/Positive/Info) | Positive 与 Info 是不同语义 |
| CaptureBar → Extended FAB + BottomAppBar | Material 3 Expressive Hero element |
| LoadingOverlay → Skeleton + shimmer | 现代 Android 加载态惯例 |
| HitCard 左侧 6dp 色条而非顶部色块 | 现场视线扫描线性优先 |
| 违规框描边 4dp → 6dp + 渐变 | 视觉权重与严重度成正比 |
| `enableEdgeToEdge()` 配 `WindowInsets.systemBars` 处理 | Material 3 Expressive 视觉张力 |
| 实施分 5 Phase,3.4+3.5 合并 bump | CLAUDE.md `feedback-release-hygiene`(纯样式不 bump) |

---

## 9. 不在本次范围内

- 后端逻辑 / OCR / 规则库 / 状态机 / 导出 — 不动
- 历史记录 / 列表页(本地 SQLite)— 留 Phase 4+
- 食品标签 OCR — 占位 Tab 切换启用(CLAUDE.md 已规划)
- 批量审图 — 留 Phase 5+
- 多语种 i18n / 语音播报 / 云端同步 — 留 Phase 4+
- 跨项目 Chat 同步(token 修改类)— 不在本次范围,Chat 项目应保持现状