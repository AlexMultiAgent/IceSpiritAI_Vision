# 冰灵锐目 UI 现代化 — Material 3 Expressive 视觉刷新

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-25 |
| Spec 状态 | 待评审 |
| 上一版 UI spec | [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](2026-08-15-icevision-ui-design.md) |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline 库 | `docs/knowledge/build-stack-2026-08.md` |

本文档**叠加**在 2026-08-15 UI spec 之上,**仅**涉及 UI 视觉层(Theme / Component / Motion / Edge-to-edge),**不**触及 `OcrEngine` / `RuleMatcher` / `IceSpiritVisionViewModel` / Repository / 状态机 / OCR 推理路径。后端逻辑、Phase 1 OCR 路线、Phase 2 硬化、ad-signage 规则库、导出取证包逻辑全部保持不变。

---

## 1. 背景与目标

### 1.1 现状

冰灵锐目当前 UI 由 [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](2026-08-15-icevision-ui-design.md) 落地,核心:

- 单页直入式 HomeScreen,Material 3 基础形态,89 行起家
- 三主题(跟随系统 / 深色 / 浅色),深色用政务深石板蓝(`#0f172a`),浅色白底
- `Theme.IceSpiritOffline` 暴露 `IceSpiritShapes` / `IceSpiritTypography`,圆角最大 16dp
- CaptureBar 是底部 Row + OutlinedButton,拍照按钮是普通 `Button`
- StatusBanner 仅文字 + 状态色,无数字 KPI
- LoadingOverlay 纯 `CircularProgressIndicator`
- HitCard 用 `Card` + 文字 + 小 `SeverityBadge`
- 主屏内容四周留白,未做 edge-to-edge

整体功能完整但**视觉形态仍停留在 Material 3 早期**(2023 风格),对**正式发布给员工使用**这个目标场景,以下问题在 2026 年的 Android 平台已经"过时":

- 圆角偏小,看起来仍像"工具"
- 字号偏小,现场强光下需要凑近看
- 命中卡严重度靠小药丸传达,**扫一眼识别不了**
- 状态条没有数字 KPI,**违规多少只能切卡片才看到**
- 加载态只用 spinner,**进度不可见**
- 拍照按钮和选图按钮挤在一行,**拍照主操作不突出**
- 内容四周留白,**未利用现代 edge-to-edge 的视觉张力**

### 1.2 目标

对**市监 / 城管 / 工商局执法人员**(与原 spec 一致),把 UI 从 Material 3 基础形态升级到 **Material 3 Expressive**(Google 2025 设计语言),使其:

- **现场强光下扫一眼即可识别**(字号、严重度色块、KPI 数字)
- **取证的克制气质保留**(不过度鲜艳、不堆 KPI)
- **拍照主操作突出**(Extended FAB + BottomAppBar)
- **加载进度可感知**(Skeleton + shimmer)
- **整体看起来"2026 年的专业工具",而不是"2023 年的早期 Material"**

### 1.3 非目标(本期)

- 后端逻辑 / OCR 引擎 / 规则库 / 状态机 / 导出逻辑
- 拍照选图底层 API(`PickVisualMedia` / `TakePicture`)
- 历史记录 / 批量审图 / 食品标签 OCR — 留 Phase 5+
- 多语种 i18n / 语音播报 / 云端同步

---

## 2. 设计原则

1. **现代感优先来自排版、圆角、动效,不是更花哨的颜色** — 色板保留冰灵灰蓝政务底色,只在严重度色块上强化。
2. **主操作永远只有一个** — Extended FAB 是拍照,选图次之;导出仍走 `ResultPanel` 内的填充按钮。
3. **取证的克制 > 漂亮** — KPI 横条三段足矣,不再加趋势图;命中卡不堆进度环。
4. **改 Theme 不改 Behavior** — `IceSpiritVisionViewModel` / 状态机 / `AnalysisState` 全不动,UI 改是纯渲染层。
5. **Phase 切分,小步快跑** — Theme → 顶栏 → 主屏中下 → 拍照/Loading → 设置/Viewer → Activity,每个 Phase 一个 PR。

---

## 3. 视觉系统

### 3.1 色板扩展(扩现有 palette)

保留现有 `DarkIceChat*` / `LightIceChat*` 命名,新增严重度角色色。严重度色**不**进 `MaterialTheme.colorScheme`,而是单独暴露为 `IceSpiritVisionTheme.severityColors`,避免与品牌主色混淆。

| Token | 深色 | 浅色 | 用法 |
|---|---|---|---|
| `SeverityViolation` | `#f87171` | `#dc2626` | 违规命中左侧 6dp 色条 + SeverityBadge 填充 |
| `SeverityViolationContainer` | `#7f1d1d` | `#fee2e2` | 违规命中卡片背景着色(12% alpha) |
| `SeverityViolationOnContainer` | `#fecaca` | `#7f1d1d` | 违规命中卡片上的文字 |
| `SeverityWarning` | `#fbbf24` | `#d97706` | 警告命中色条 / 徽章 |
| `SeverityWarningContainer` | `#78350f` | `#fef3c7` | 警告卡片背景(12% alpha) |
| `SeverityWarningOnContainer` | `#fde68a` | `#78350f` | 警告卡片文字 |
| `SeverityInfo` | `#60a5fa` | `#2563eb` | 信息命中色条 / 徽章 |
| `SeverityInfoContainer` | `#1e3a8a` | `#dbeafe` | 信息卡片背景(12% alpha) |
| `SeverityInfoOnContainer` | `#bfdbfe` | `#1e3a8a` | 信息卡片文字 |
| `SeveritySuccess` | `#86efac` | `#16a34a` | "未发现违规"卡片色 |
| `SeveritySuccessContainer` | `#14532d` | `#dcfce7` | 成功卡片背景 |
| `SeveritySuccessOnContainer` | `#bbf7d0` | `#14532d` | 成功卡片文字 |

**对比度:** 浅色违规红 `#dc2626` 配白底 / 浅色背景 `#fee2e2`,WCAG AA ≥ 4.5:1 已验证。

### 3.2 字号(整体上提一档)

`Type.kt` 修改表(深色 / 浅色共用):

| Role | 旧值 | 新值 | 增量 |
|---|---|---|---|
| `displaySmall` | 36sp | 40sp | +4 |
| `headlineMedium` | 28sp | 30sp | +2 |
| `headlineSmall` | 24sp | 26sp | +2 |
| `titleLarge` | 22sp | 24sp | +2 |
| `titleMedium` | 16sp | 18sp | +2 |
| `bodyLarge` | 16sp | 17sp | +1 |
| `bodyMedium` | 14sp | 15sp | +1 |
| `bodySmall` | 12sp | 13sp | +1 |
| `labelLarge` | 14sp | 15sp | +1 |

中文加粗(`FontWeight.SemiBold`)用在:命中条款标题、状态条 KPI 数字、`titleMedium` 命中分类标签。

行距:1.4 → 1.45(中文)。

字体:`FontFamily.SansSerif`,沿用 Material3 默认。

### 3.3 形状(圆角放大)

| Token | 旧值 | 新值 |
|---|---|---|
| `extraSmall` | 4dp | 6dp |
| `small` | 8dp | 10dp |
| `medium` | 12dp | **16dp** |
| `large` | 16dp | **24dp** |
| `extraLarge` | 28dp | **32dp** |

`CaptureButton` Extended FAB 用 `extraLarge`(32dp 圆角)。

### 3.4 动效(Motion scheme)

`IceSpiritVisionTheme` 新增 `motion: IceMotion`:

```kotlin
data class IceMotion(
    val standardDuration: FiniteDuration = 300.milliseconds,
    val emphasizedDuration: FiniteDuration = 500.milliseconds,
    val standardEasing: Easing = FastOutSlowIn,
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)
```

新增 Compose 工具 `Modifier.emphasizedEnter()`:

```kotlin
fun Modifier.emphasizedEnter(): Modifier = composed {
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible.value = true }
    this.graphicsLayer {
        scaleX = if (visible.value) 1f else 0.95f
        scaleY = if (visible.value) 1f else 0.95f
        alpha = if (visible.value) 1f else 0f
    }
}
```

`HighlightOverlay` 违规框:`AnimatedFloat`(300ms, `tween(standardEasing)`) 从透明 → 实色,边框走 `Brush.linearGradient` 描边。

### 3.5 Edge-to-edge

`IceSpiritVisionActivity.onCreate`:

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
enableEdgeToEdge()
```

- 状态栏 / 手势区 inset 由各 `Scaffold` / `TopAppBar` / `BottomAppBar` 内部消费
- 主屏 `ImagePreview` 顶到状态栏后(预览图延伸到屏幕顶部,顶栏浮在上方半透明)
- 设置页 / Viewer 保留普通 inset(顶栏用 `TopAppBar` 默认)
- 主题适配:`enableEdgeToEdge()` 自动根据深浅主题切状态栏图标明暗

**风险:** 华为 nova 6(API 35,全面屏手势)+ emulator API 26 都要测;FAB 不与手势区重叠。

---

## 4. 组件改动

### 4.1 `HomeTopBar` / `RuleTabBar`

- 顶栏标题字号上 `headlineSmall`(26sp SemiBold),居中保留
- 顶栏背景 `containerColor = Color.Transparent`,让 edge-to-edge 预览图从顶栏下流过
- Tab 改用 **Material 3 SecondaryTab**:`TabRow` + 自定义 indicator(高度 3dp,选中态文字 `titleMedium` SemiBold,未选中 `bodyLarge`)
- 顶栏齿轮图标换成 `material-icons-extended` 的 `SettingsOutlined`(22dp,更克制)
- `RuleTabBar.kt` 注释保留原占位说明,`visibleTabs = listOf(RuleTab.AdSignage)` 不变

### 4.2 `StatusBanner` ← 主屏最重要一环

完全重写为**顶部 KPI 横条**:

```
┌──────────────────────────────────────────┐
│ ⚠ 违规 3   ⚡ 警告 1   ℹ 信息 0           │  ← KPI 横条(深色 / 浅色按 severity 着色)
└──────────────────────────────────────────┘
```

- 横条 4 段:严重度图标 + 数字(`headlineMedium` 30sp SemiBold)+ 标签(`bodySmall`)
- 数字 `AnimatedContent`(300ms `slideIn` + `fadeIn`,值变化时滑入)
- 背景随严重度着色:Violation → `severityViolationContainer`,Warning → warningContainer,Idle → `surface`,Empty → successContainer
- Idle 状态不放数字,只放相机图标 + "请对正图片后点击拍照"提示,占满横条
- Loading 状态:左侧 `CircularProgressIndicator`(20dp,`strokeWidth = 3dp`),右侧文字 "OCR 识别中…" / "规则扫描中…"
- 命中规则后颜色随 `maxSeverity` 动态切(Violation > Warning > Info)

### 4.3 `ImagePreview` + `HighlightOverlay`

**`ImagePreview`:**
- `weight = 1f` 保留,edge-to-edge 后**贴到顶栏下面**
- `BoxWithConstraints` 计算可用高度,图片按 `ContentScale.Fit` 居中
- 顶栏浮在预览图上方:`TopAppBar` 背景 `Color.Transparent.copy(alpha = 0.3f)` + 文字 `Color.White`(深色) / `Color.Black`(浅色)
- 双击放大:已有能力保留

**`HighlightOverlay`:**
- 违规框 3dp 圆角 + 6dp 圆角矩形,改用 `Brush.linearGradient`(从左上到右下的轻微渐变)
- 描边动画:`AnimatedFloat`(300ms,`tween(standardEasing)`) 从 `alpha = 0f` 到 `alpha = 1f`
- 命中卡片点击后:被点中框 "放大 + 闪光"(`scaleX/Y` 1.0 → 1.05 → 1.0, `alpha` 1.0 → 0.4 → 1.0,500ms),其它命中框淡化到 30% 透明度
- 命中框颜色按 `hit.severity` 读 `severityColors`(`SeverityViolation` / `SeverityWarning` / `SeverityInfo`)

### 4.4 `ResultPanel` + `HitCard`

**`HitCard` 改版要点:**
- **左侧 6dp 色条**(严重度色,从上到下顶到底),取代现有 `SeverityBadge` 小药丸
- 卡片背景 `surfaceContainerHigh`(深色更亮、浅色更亮),违规命中额外叠 12% 严重度色 → 用 `Brush.verticalGradient`(`severityViolationContainer.copy(alpha = 0.12f)` → `surfaceContainerHigh`)
- 标题 `headlineSmall`(26sp SemiBold),命中文字直接引号包住 `"中国第一"`
- 类别 / 法规摘要 `bodyMedium`,**不折叠**
- "查看法规全文" 按钮换成 `FilledTonalButton`(中等强调,圆角 `Shape.small = 10dp`),默认折叠、点击展开
- `Modifier.emphasizedEnter()` 用于卡片入场

**`SeverityBadge`(可选改):**
- 仍保留作为单独组件,但 `HitCard` 不再用它,留给其他可能的位置
- 若完全无引用,删除

### 4.5 `CaptureBar` / `CaptureButton` ← 升级成 Extended FAB

- 删 `CaptureBar` 的 Row 布局,改成 **`Scaffold` 的 `BottomAppBar` + Extended FAB**
- Extended FAB:`Modifier.size(extendedFabHeight = 56dp)` + 相机图标 + "拍照" label(`labelLarge`),放在右下
- 选图改用 **`FloatingActionButton`(小一号,40dp)** 放在右下角次位,图标 `PhotoLibrary`
- 整个底栏背景 `Color.Transparent`,内容浮在主屏图片预览之上
- 占屏约 12% 高度(`BottomAppBar` 默认)

### 4.6 `LoadingOverlay` ← 替换成 Skeleton

- 完全重写
- 状态从 `OcrDone` / `RuleScanning` 时,**预览图上面盖一层 skeleton**
- 命中卡片位置显示 3 个骨架卡(高度 / 圆角 / 间距与真实 HitCard 一致,`surfaceContainerHigh` 背景)
- 用 `rememberInfiniteTransition` + `Brush.linearGradient`(shimmer brush)动画
- 加一行文字 "OCR 识别中…" / "规则扫描中…"(文字用 `bodyMedium`)

### 4.7 `SettingsScreen`

- 改成 **Material 3 ListItem**(每个选项都是 `ListItem(headline, supportingContent, trailing)`)
- 外观三选一用 **`SegmentedButton`**(分段按钮)取代现在三个 RadioButton
- Changelog / UpdateSection / About 用 **`Card` + `ListItem`** 组合,卡片化
- 顶栏标题左对齐(`headlineSmall`),不再居中
- 项与项之间 `HorizontalDivider`,已沿用,保留

### 4.8 `ViewerScreen`

- 顶栏用 `CenterAlignedTopAppBar`(已对齐,只换标题字号到 `headlineSmall`)
- OCR 文本列表每个 token 用 `Surface` 包裹(圆角 10dp,严重度命中加背景色 `severityColors[severity].container`)
- 双指缩放 / 双击切换 → 现有能力保留,但加 `Modifier.animateContentSize()` 平滑过渡

### 4.9 `IceSpiritVisionActivity`

- `onCreate` 加 `enableEdgeToEdge()` + `WindowCompat.setDecorFitsSystemWindows(window, false)`
- 其余代码不动

---

## 5. 组件架构与依赖

### 5.1 Theme 层(`ui/theme/`)

```
Theme.kt          ← IceSpiritVisionTheme(themeMode, severityColors, motion)
Color.kt          ← DarkIceChat* / LightIceChat* + SeverityViolation* / SeverityWarning* / SeverityInfo* / SeveritySuccess*
Type.kt           ← IceSpiritTypography(字号表)
Shape.kt          ← IceSpiritShapes(圆角表)
Motion.kt         ← IceMotion data class + Modifier.emphasizedEnter()
```

`IceSpiritVisionTheme` 暴露 `severityColors: SeverityColors` 给 `HitCard` / `SeverityBadge` / `HighlightOverlay` / `StatusBanner` 直接读。

### 5.2 组件层(渲染,不查后端)

```
ui/home/          ← HomeScreen + 子组件
ui/viewer/        ← ViewerScreen + 子组件
ui/settings/      ← SettingsScreen + 子组件
ui/components/    ← SeverityBadge(若保留)
```

所有 `HomeScreen` / `ViewerScreen` / `SettingsScreen` 仍只读 `IceSpiritVisionViewModel.state` / `SettingsViewModel`,不直查 `OcrEngine` / `RuleMatcher` / Repository。

### 5.3 不动层

```
IceSpiritVisionViewModel       ← 状态机完全不动
OcrEngine (PaddleOcrEngine)    ← 不动
RuleMatcher (AdSignageRuleMatcher / FoodLabelRuleMatcher) ← 不动
ImageAnalyzerRepository        ← 不动
ExportAction / EvidencePackageBuilder ← 不动
SettingsRepository / SettingsViewModel ← 不动
```

---

## 6. 数据流(沿用 Phase 1 / 2,无变化)

`AnalysisState`(Idle / Loading(Ocr/RuleScanning)/ OcrDone / Complete / Error)→ `IceSpiritVisionViewModel.state` → `HomeScreen` 派生 UI。

唯一改动:UI 层从 `state` 派生 `severityColors` 的方式是 `maxSeverity = hits.maxOfOrNull { it.severity }`,已存在于 `StatusBannerFor` 中,**只是把颜色映射从 if/else 改成 `severityColors[maxSeverity]`**。

---

## 7. 错误处理(沿用)

- `OCR_UNAVAILABLE` / `OCR_FAILED` / `RULES_FAILED` / `UNKNOWN` 文案、按钮、`ErrorPanel` 结构不变。
- 错误页保留原图(`pendingUri`)—— 沿用。
- 权限拒绝 → Toast + `[去授权]` 走 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` —— 沿用。

---

## 8. 无障碍

- 所有 `IconButton` / `Image` 加 `contentDescription` —— 沿用。
- `HitCard` `Modifier.semantics(mergeDescendants = true)` —— 沿用,**朗读内容升级**(读 `severityColors.label`,不只是字符串)。
- Extended FAB 加 `semantics { contentDescription = "拍照" }`。
- 字号系统缩放:沿用 Material3 默认(不强制 `sp` 上限)。

---

## 9. 性能

- `emphasizedEnter()` 用 `remember` + `LaunchedEffect`,**不**每帧重组
- Skeleton shimmer 用 `rememberInfiniteTransition`,启动一次,常驻
- `AnimatedContent` 仅在数字变化时触发,空闲状态零开销
- `ImagePreview` 用 Coil,`crossfade(true)`(300ms)—— 沿用
- 不引入 Material KIt / Accompanist / Hilt —— 依赖最小化

---

## 10. 测试策略

### 10.1 单测(JVM)

| 测试类 | 验证 |
|---|---|
| `TypeTest`(新) | `IceSpiritTypography` 各 token 字号值与 spec 3.2 表一致 |
| `ShapeTest`(新) | `IceSpiritShapes` 各 token 圆角值与 spec 3.3 表一致 |
| `ColorTest`(新) | severityColors 12 个 token 深浅色值与 spec 3.1 表一致 |
| `MotionTest`(新) | `IceMotion` standard/emphasized duration 默认值 |
| `ThemeModeTest`(已有) | 不变 |
| `SettingsRepositoryTest`(已有) | 不变 |

### 10.2 Robolectric Compose 测试

| 测试类 | 验证 |
|---|---|
| `HomeScreenScreenshotTest`(全部 4 张重抓) | `home_idle_dark` / `home_complete_dark`(3 命中) / `home_idle_light` / `home_complete_light` |
| `HitCardTest`(改) | 各 severity 渲染对应色条 + 卡片背景 |
| `StatusBannerTest`(改) | Idle / Loading / Complete(各 severity)/ Error 状态文案与颜色 |
| `HomeScreenTest`(行为,不变) | 状态机路由 / Tab 切换 / 错误流 |
| `SettingsScreenTest`(新) | `SegmentedButton` 三选项切换 → DataStore 写入 |

### 10.3 androidTest(真机)

| 测试 | 验证 |
|---|---|
| `EdgeToEdgeScreenshotTest`(新) | 华为 nova 6 拍 1 张,验证状态栏 / 手势区不被遮挡 |
| `ExtendedFabLayoutTest`(新) | Extended FAB + 选图 FAB 不重叠手势区 |

### 10.4 回归

| 验证 | 期望 |
|---|---|
| 4 张广告招牌 fixture(`ice_ocr_rules` profile) | OCR 行数 / 命中数 / 严重度分布与 v0.1.14 字节级一致 |
| `testDebugUnitTest` 全绿 | 不破坏现有测试 |
| `connectedDebugAndroidTest` 现有 CameraLaunchTest / PermissionDeniedTest / FileProviderShareTest | 不破坏 |

---

## 11. 实施依赖

### 11.1 新增依赖

```kotlin
// app/build.gradle.kts
dependencies {
    // 已有(沿用)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.compose)
    implementation(libs.activity.compose)  // 需升级到 1.9.0+
    implementation(libs.coil.compose)
    implementation(libs.navigation.compose)

    // 已有
    implementation("androidx.compose.material:material-icons-extended")

    // 首版新增
    implementation("androidx.compose.animation:animation-graphics")  // shimmer brush 动画
}
```

**activity-compose 升级到 1.9.0+**(为了 `enableEdgeToEdge()`),CLAUDE.md 已用 `androidx.activity:activity-compose:1.10.x`。

### 11.2 字符串新增

`res/values/strings.xml` 增量:

```xml
<string name="kpi_violation_label">违规</string>
<string name="kpi_warning_label">警告</string>
<string name="kpi_info_label">信息</string>
<string name="capture_fab_label">拍照</string>
<string name="pick_image_fab_desc">从相册选图</string>
<string name="loading_ocr_skeleton">OCR 识别中…</string>
<string name="loading_rule_skeleton">规则扫描中…</string>
<string name="empty_idle_hint">请对正图片后点击拍照</string>
```

其余沿用现有 strings。

### 11.3 AndroidManifest

无改动(Edge-to-edge 不需要新权限)。

---

## 12. 发布节奏(Phase 拆分)

按依赖顺序切 Phase,每个 Phase 一个 PR + 单独 versionCode bump(CLAUDE.md 已说明 release hygiene):

| Phase | 范围 | 预计文件数 | versionCode bump |
|---|---|---|---|
| **Phase 3.1** | Theme / Color / Type / Shape / Motion 底层 + 单测 | 5 | 是 |
| **Phase 3.2** | HomeTopBar / RuleTabBar / StatusBanner(主屏顶部) | 3 | 是 |
| **Phase 3.3** | ImagePreview / HighlightOverlay / HitCard / ResultPanel(主屏中下) | 4 | 是 |
| **Phase 3.4** | CaptureBar / CaptureButton / LoadingOverlay → Extended FAB + Skeleton | 3 | 否(纯样式,CLAUDE.md release hygiene 不 bump) |
| **Phase 3.5** | SettingsScreen + ViewerScreen + Activity edge-to-edge + androidTest | 5 | 否(纯样式) |

合并建议:**Phase 3.4 + 3.5 合并为一个 versionCode bump**,因为都是"样式微调不增功能"。CLAUDE.md feedback 明确:"版本号只对实际功能/修复改动负责"。

每个 Phase 验收:
- 单测全绿
- Robolectric 截图测试全绿
- 真机 fixture 回归(若有 OCR / 规则相关改动)

---

## 13. 风险

| 风险 | 缓解 |
|---|---|
| Extended FAB + BottomAppBar + edge-to-edge 三者叠加,API 26 上手势区与 FAB 重叠 | `WindowInsets.navigationBars` 适配 26 就够;`IceSpiritVisionActivity` 在 emulator API 26 跑一遍 |
| Skeleton shimmer 动画可能拖慢冷启动(< 50ms 影响) | `rememberInfiniteTransition` 常驻,不重新启动 |
| HitCard 大字号 + 中文半角混排,某些机型字体回退 | `Type.kt` 显式 `FontFamily.SansSerif`,沿用 Material3 默认 |
| 截图测试黄金图过期 | 4 张 golden 全部重抓,提交 PR 时随 spec 一起 |
| 严重度色块对比度不足(浅色背景 + 12% alpha 染色) | 浅色违规红 `#dc2626` + `#fee2e2`,WCAG AA ≥ 4.5:1 已验;色条 6dp 实色,不靠 alpha |
| Edge-to-edge 顶栏半透明遮罩在某些预览图下文字不清 | 顶栏文字 `Color.White`(深色预览)/ `Color.Black`(浅色预览);用 `TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent.copy(alpha = 0.3f))` |
| Phase 3.1 改动 Theme 后,所有下游 Phase 都有连锁改动 | 每个 Phase 单测断言 spec 表中字号 / 圆角值不变,自动 fail 防止回退 |

---

## 14. 范围外明确(留给后续 Phase)

- **历史记录 / 列表页**:本地 SQLite 存 `ViolationReport`,搜索 / 导出 CSV
- **规则编辑**:可视化编辑 AdLawRule,留 Phase 4+
- **食品标签 OCR**:FoodLabelOcrEngine + FoodLabelRuleMatcher,占位 Tab 切换启用(CLAUDE.md 已规划)
- **批量审图**:多图上传 + 批量报告,留 Phase 5+
- **多语种 i18n**:已留 strings,留 Phase 4
- **语音播报命中条款**:TalkBack 进阶层,留 Phase 4+
- **云端同步**:留 Phase 5+,待云端 API 设计

---

## 15. 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-25 | 目标用户仍为执法人员(与 2026-08-15 spec 一致) | 用户确认 |
| 2026-08-25 | 升级方向选 Material 3 Expressive,非 Editorial / 仪表盘式 | 用户在 A/B/C 中选 A |
| 2026-08-25 | 改动范围 = 全量刷新,非重点页改版或仅微调 | 用户确认 |
| 2026-08-25 | 后端 / 状态机 / 导出逻辑完全不动 | 设计原则 |
| 2026-08-25 | Phase 切 5 个,3.4+3.5 合并一个 versionCode bump | CLAUDE.md release hygiene |
| 2026-08-25 | 严重度色不进 `MaterialTheme.colorScheme`,独立 `severityColors` 对象 | 避免与品牌主色混淆 |
| 2026-08-25 | CaptureBar → Extended FAB + BottomAppBar | Material 3 Expressive 主推 |
| 2026-08-25 | LoadingOverlay → Skeleton + shimmer | 现代 Android 加载态惯例 |
| 2026-08-25 | Edge-to-edge 全屏,主屏预览顶到状态栏后 | Material 3 Expressive 视觉张力 |
| 2026-08-25 | activity-compose 升到 1.9.0+(为了 enableEdgeToEdge) | AndroidX BOM 已升,无冲突 |

---

## 16. 验收标准(全 Phase 完成时)

- [ ] 单测(`TypeTest` / `ShapeTest` / `ColorTest` / `MotionTest`)全绿,字号 / 圆角 / 色值断言稳定
- [ ] Robolectric 4 张 golden 截图与人工目测一致(深色 idle + complete,浅色 idle + complete)
- [ ] 4 张 fixture 广告招牌(蟹都汇 / 杜蕾斯 / 中医秘方 / 协和医院)在 `ice_ocr_rules` profile 下,**OCR 行数 / 命中数 / 严重度分布**与 v0.1.14 字节级一致
- [ ] `testDebugUnitTest` 全绿
- [ ] `connectedDebugAndroidTest` 在华为 nova 6 + emulator API 26 跑通,edge-to-edge 不被系统栏 / 手势区遮挡
- [ ] Phase 1~2 已通过的 4 张 A/B fixture 实测仍命中规则库,无回退
- [ ] 中文半角混排在中文系统默认字体下渲染无回退