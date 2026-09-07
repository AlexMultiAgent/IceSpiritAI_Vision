# 冰灵锐目 UI Editorial 重塑 — 思源宋体 + 编辑设计派 + 架构清理

| 项 | 值 |
|---|---|
| 文档版本 | v1.0 |
| 日期 | 2026-09-07 |
| Spec 状态 | 待评审 |
| 上一版 UI spec | [`docs/superpowers/specs/2026-08-25-icevision-ui-modernization-design.md`](2026-08-25-icevision-ui-modernization-design.md)(Material 3 Expressive) |
| 关联项目根指令 | [`CLAUDE.md`](../../CLAUDE.md) |
| 关联 plan | 待写作 `docs/superpowers/plans/2026-09-07-vision-editorial-redesign-plan.md` |

本文档记录冰灵锐目 UI 从 **Material 3 Expressive** 升级到 **Editorial 编辑设计派**(报纸/杂志/法规汇编气质)的**设计决策**。实施细节(文件清单 / 依赖 / 发版切分 / 测试路径)在后续 plan 中。

---

## 1. 背景与目标

**目标用户**:执法人员(市监 / 城管 / 工商局)— 沿用 2026-08-15 / 2026-08-25 UI spec,本 spec 不重新界定。

**当前 UI(Material 3 Expressive 形态)的问题**:

- HitCard 整面容器染色 + 严重度小药丸 → 现场强光下色块与色块之间容易混淆,「取证/审查」专业感被「App 通知感」覆盖
- HitCard matched text 字号 22sp 已略大于 tab label,但视觉上仍偏「App 提示框」,不像「法规引用」
- HomeTopBar 透明 + 字号 20sp + Outlined 齿轮 → 像 SaaS 应用,缺「报纸刊头」气质
- CaptureBar 走 BottomAppBar + Extended FAB → M3 推荐,但本 App 现场拍一次后只需切图 / 拍照,「FAB hero element」语义过强
- 大量 dp 字面量散落(`HomeScreen.kt` 419 行内至少有 30+ 处)→ 间距不统一,无法批量调整
- 无 spacing / elevation / dimens token 文件 → 设计与实现双层都难以系统化
- 思源宋体未引入,标题仍是系统默认 sans → 「广告招牌 / 法规依据」领域最该有的「印刷品质感」缺失
- `HomeScreen` 单体 419 行 = Idle/Loading/Complete/Error 全在一个 `Column { when(state) { ... } }` → 拆分是必然
- `LoadingOverlay.kt`(115L)完整实现但未挂载(`HomeScreen` 只调 `loadingLabelRes()` 出纯 Text)— 死代码
- `SeverityBadge.kt`(63L)手选 dark/light 色对,绕过 `LocalSeverityColors` — 与刚立的 severity 系统不一致
- `HighlightOverlay` 内有 `FIXME Task 11`(maxOfOrNull 顺序 vs Positive 桶)— 已知 bug 未修
- NavHost 5 路由无转场动画 — 切换生硬
- ViewerTopBar 硬编码英文 `"Back"`,全 App 其他 a11y 文案走 `R.string.action_back` — 一致性 bug

**目标**:把冰灵锐目从「合规的 App」变成「合规的报纸/法规汇编」 — 衬线主标、严重度信号改为色条、留白即纪律、品牌色退为辅助信号、动效只做「翻页」。

**非目标**:
- OCR / PaddleOCR 模型 / 规则库 / 状态机 / 导出 / 历史记录 / 食品标签 OCR — 全部不动
- 字体 license 谈判 / 自有字体设计 — 思源宋体 SIL OFL 1.1 可商用,直接引入
- 后端 / ViewModel 逻辑 — 完全不动(只动 UI 渲染层)
- Material You 动态取色 / wallpaper extraction — 不引入
- 多语种 i18n / 语音播报 / 云端同步 — 留 Phase 4+
- batch 审图 — 留 Phase 5+

---

## 2. 设计约束(在任何决策之前必须知道)

1. **跨项目 1:1 对齐**(CLAUDE.md + `theme/Color.kt` header):**已存在的** brand accent / bg / panel hexes 跟 IceSpiritAI_Chat 1:1 对齐(`#1F3A52` / `#5A7090` / `#A8C0D0` / `#7DA4BD`),本 spec **只允许新增 token,不动现有 brand accent**。任何修改 brand accent 的提议必须先与 Chat 项目同步。
2. **后端不变**:`IceSpiritVisionViewModel` / `OcrEngine` / `RuleMatcher` / `ExportAction` / 状态机完全不动。
3. **AdSignage Tab 优先**:`RuleTabBar.visibleTabs = listOf(RuleTab.AdSignage)` 不变(FoodLabeling 模板保留,CLAUDE.md `产品方向`)。
4. **Theme mode(更新)**:从「默认深色」翻为「默认浅色」(Editorial 报纸气质在亮色底才是主流)。Settings 内仍可切深色。**这是本次唯一一处违反既有 `feedback-dual-theme` 的 memory**,需用户接受。

---

## 3. 方向选择(从 4 选 1)

| 方向 | 取舍 | 选定 |
|---|---|---|
| A:Material 3 Expressive(现状) | 圆角 + 排版 + 动效全面升级;现代感与执法严肃感之间平衡,但 App 化气息浓 |  |
| **B:Editorial 编辑设计派** | 衬线 + 留白 + 黑白为主 + 严重度色作为唯一彩色信号;强调「取证/审查」专业严肃感,最不像普通 App | ✓ |
| C:Neo-Glass 软质玻璃派 | 半透磨砂 + 渐变 + 软阴影;移动端低端机渲染压力大,M3 缺原生 glass 组件需自建 |  |
| D:Functional 高密度工具派 | WCAG AAA + 信息密度高 + 极简装饰;视觉素,缺品牌记忆点 |  |

**为什么不选 A**:Material 3 Expressive 是上版选择的延续,但用户明确提出「取证/审查」专业严肃感诉求,M3 的 dynamic tonal palette / spring 动效 / Extended FAB 都不贴合。Editorial 在这个 domain 才是正解。
**为什么不选 C**:M3 没有原生 glass 组件,需要 `graphicsLayer` + `RenderEffect.createBlurEffect` 自建,跨 Android 版本兼容性 + 性能成本高,与「现场一次拍立得」的简洁诉求不符。
**为什么不选 D**:Functional 偏工程师审美,但 App 是给市监/城管执法人员用的,「工具感」过强会让人觉得粗糙,失去「专业」信号。

### 3.1 子方向选择(从 4 选 1)

| 子方向 | 取舍 | 选定 |
|---|---|---|
| 思源宋体 Source Han Serif | Adobe + Google 联合开源;竖排偏扁、横细竖粗对比强,「教科书 / 学术期刊」气质,严肃、可信 | ✓ |
| 霞鹜文楷 LXGW WenKai | 半正式半人文;亲切可读但「严肃」分量弱 |  |
| 思源黑体 + Source Serif Pro | 现代杂志配法;负担轻但缺中文衬线 |  |
| 思源宋体 Heavy + 公文风 | 严肃感拉到顶;美感被公文感吃掉 |  |

### 3.2 严重度信号选择(从 4 选 1)

| 方案 | 取舍 | 选定 |
|---|---|---|
| **左侧 4dp 色条 + 内文等级词** | 卡面中性,左色条 + kicker 文字传达严重度;零染色,纯排版纪律 | ✓ |
| 底部 hairline + kicker | 最编辑感;靠字号而非颜色 |  |
| 保留整面染色(当前) | 扫描最快但与 Editorial 气质不符 |  |
| 图标 + kicker(零背景) | 最克制;需配套良好图标 |  |

### 3.3 Tab 形态选择(从 4 选 1)

| 方案 | 取舍 | 选定 |
|---|---|---|
| **极浅灰色 pill** | 选中态 `#EFEDE6` / `#1F1F22`,轻微表面抬升;与严重度色条互不打架 | ✓ |
| 下划线 | 最静默;选中与未选中区分弱 |  |
| 黑底白字 pill(当前) | 视觉重;与左侧严重度色条竞争锚点 |  |
| 无背景字重差 | 最克制;选中态视觉强度最弱 |  |

### 3.4 动效哲学选择(从 3 选 1)

| 方案 | 取舍 | 选定 |
|---|---|---|
| **静默纸页(180-220ms fade)** | Idle → Loading → Complete 三态单纯 fade + 极小 slideY;无 spring,无回弹;Editorial 归宿 | ✓ |
| 克制的物理感 | mild spring + stagger;信息层次最丰富但仍克制 |  |
| 报纸节奏 | 完整 nav push/pop + 章标 stagger |  |

### 3.5 吉祥物角色选择(从 4 选 1)

| 方案 | 取舍 | 选定 |
|---|---|---|
| **保留 120dp 居中 + 副标题** | 仍是 Idle 主视觉,加衬线副标题「拍一下广告,几秒告诉你哪里要改」;改动最小 | ✓ |
| 缩为 56dp 角落印章 | 中部空给大标题;识别度弱化 |  |
| Idle 移除吉祥物 | 最编辑;丢失品牌识别 |  |
| 线描版吉祥物 | 需重制 PNG 素材 |  |

### 3.6 品牌色用途选择(可多选)

| 用途 | 取舍 | 选定 |
|---|---|---|
| **主 CTA 文字(拍照 / 导出取证包)** | 仅在「用户做事的入口」出现;最克制 | ✓ |
| **hairline 分隔线(14% alpha)** | 到处都在但几乎看不见;最编辑 | ✓ |
| **步骤指示 + 进度** | 「当前步骤」用品牌色填充,其余步骤空心 | ✓ |
| Viewer 高亮叠框 | 高亮保持严重度色 |  |

---

## 4. 视觉系统

### 4.1 色板(LIGHT, hex)

| Token | Hex | 角色 |
|---|---|---|
| `Bg` | `#FAFAF7` | App 背景(暖白) |
| `Panel` | `#FFFFFF` | 卡片 / KPI 背景 |
| `PanelSoft` | `#F2F0EA` | 微抬表面 / Loading skeleton |
| `PanelStrong` | `#E8E6E0` | 二级抬升 |
| `OnBg` | `#0B1E26` | 主文本(深墨水) |
| `OnBgMuted` | `#5A6E78` | 副文本 |
| `OnBgSubtle` | `#9C9A95` | 提示文本 |
| `OnBgDisabled` | `#B5B2A8` | 禁用文本 |
| `Accent` | `#1F3A52` | **品牌色**(IceChat 1:1 锁) |
| `AccentSecondary` | `#5A7090` | 品牌色副 |
| `OnAccent` | `#FFFFFF` | Accent 上的文字 |
| `Divider` | `#E0DDD3` | 1px hairline |
| `HairlineAccent` | `rgba(31,58,82,0.14)` | 品牌色 hairline |

### 4.2 色板(DARK, hex)

| Token | Hex | 角色 |
|---|---|---|
| `Bg` | `#0E0E10` | App 背景 |
| `Panel` | `#16161A` | 卡片 / KPI |
| `PanelSoft` | `#1A1A1C` | 微抬 |
| `PanelStrong` | `#232328` | 二级抬升 |
| `OnBg` | `#F5F2EA` | 主文本(暖白) |
| `OnBgMuted` | `#9C9A95` | 副文本 |
| `OnBgSubtle` | `#6B6B6B` | 提示 |
| `OnBgDisabled` | `#4A4A4F` | 禁用 |
| `Accent` | `#A8C0D0` | **品牌色**(IceChat 1:1 锁) |
| `AccentSecondary` | `#7DA4BD` | 品牌色副 |
| `OnAccent` | `#08131B` | Accent 上的文字 |
| `Divider` | `#232328` | 1px hairline |
| `HairlineAccent` | `rgba(168,192,208,0.18)` | 品牌色 hairline |

### 4.3 严重度色板(4 桶 × 4 token)

每个 severity = `base` + `container` + `onContainer` + `ruleColor`。其中 `ruleColor` 是新 token,给左侧 4dp 色条专用;`container` 用 8% alpha 而非 100% 给 ViewerTextList 命中行背景。

| 桶 | LIGHT base | LIGHT container | LIGHT ruleColor | DARK base | DARK container | DARK ruleColor |
|---|---|---|---|---|---|---|
| Violation | `#B83227` | `#FEE2E2` | `#B83227` | `#E2725B` | `#7F1D1D` | `#E2725B` |
| Warning | `#C9A227` | `#FEF3C7` | `#C9A227` | `#D4A847` | `#78350F` | `#D4A847` |
| Info | `#5B8DEF` | `#DBEAFE` | `#5B8DEF` | `#7BA7E8` | `#1E3A8A` | `#7BA7E8` |
| Positive | `#2C8A6B` | `#DCFCE7` | `#2C8A6B` | `#5FC2A0` | `#14532D` | `#5FC2A0` |

`SeverityColors` 增字段:
```kotlin
@Immutable data class SeverityColors(
    val accent: (Severity) -> Color,
    val onAccent: (Severity) -> Color,
    val container: (Severity) -> Color,
    val onContainer: (Severity) -> Color,
    val ruleColor: (Severity) -> Color,  // NEW — 4dp 色条专用
    val textColor: (Severity) -> Color   // NEW — kicker 文字色
)
```

### 4.4 字体

- **CJK**:思源宋体 SC(Source Han Serif SC),SIL OFL 1.1,Adobe + Google 联合开源
- **Latin serif**:Source Serif Pro / Source Serif 4,SIL OFL
- **数字 / KPI**(备):Source Han Sans SC + Inter

资源位置:`app/src/main/res/font/source_han_serif_sc_*.ttf`(6 字重:Light/Regular/Medium/SemiBold/Bold/Heavy)。License 可商用,APK 体积增约 1.2 MB。

### 4.5 字号(M3-aligned, Editorial 调音)

| Token | sp / weight / lineHeight / letterSpacing | 用途 |
|---|---|---|
| `Display` | 42 / 700 / 1.2 / -0.5 | KPI 数字 / 大标题 |
| `Headline` | 32 / 700 / 1.25 / -0.3 | 节标题 |
| `Title` | 22 / 600 / 1.3 / 0 | App 标题 / 命中文字 |
| `Subtitle` | 18 / 600 / 1.4 / 0 | 副标题 |
| `Body` | 16 / 400 / 1.6 / 0 | 正文 |
| `BodySmall` | 14 / 400 / 1.55 / 0.1 | 辅助正文 / 法规引用 |
| `Label` | 13 / 600 / 1.5 / 0.5 | 卡片标签 |
| `LabelSmall` | 11 / 700 / 1.4 / 1.5 | kicker / ALL-CAPS 严重度 |
| `Caption` | 12 / 400 / 1.4 / 0 | 注释 |

CJK lineHeight 全部按中文排版节奏(1.2-1.6),letterSpacing:大字 -0.5 / LabelSmall kicker +1.5。

### 4.6 间距 token(`Spacing`)

| Token | dp | 用途 |
|---|---|---|
| `xs` | 4 | 元素内微距 |
| `sm` | 8 | 行内 / chip 内 padding |
| `md` | 12 | 卡片间距 |
| `lg` | 20 | 卡片内 padding / 节段距 |
| `xl` | 32 | 大区块距 |
| `xxl` | 48 | 屏边距 / 留白 |

`@Immutable data class Spacing(...)` + `LocalSpacing` CompositionLocal。

### 4.7 抬升 token(`Elevation`)

| Token | 值 | 用途 |
|---|---|---|
| `level0` | 0 | 默认,无抬升 |
| `level1` | PanelSoft | 微抬表面(skeleton) |
| `level2` | PanelStrong | 二级抬升(选中 tab) |
| `level3` | 1px Divider 描边 | 卡片边界(取代阴影) |

`Elevation` = enum,不是 data class。无圆角阴影,符合 Editorial 排版纪律。

### 4.8 形状(`Shape`)

| Token | dp | 用途 |
|---|---|---|
| `xs` | 2 | 微圆角 |
| `sm` | 4 | UI 元素 |
| `md` | 8 | 卡片 |
| `lg` | 12 | 模态 |
| `pill` | 24 | 仅 tab pill |
| `full` | 50% | 头像 |

几乎无圆角,符合 Editorial 排版纪律。

### 4.9 动效 token(`Motion`)

| Token | 值 | 用途 |
|---|---|---|
| `Standard` | 220ms fadeIn + 8dp slideY, easeOut | 状态切换 |
| `StandardIn` | 200ms fadeIn | 进入 |
| `StandardOut` | 180ms fadeOut | 离开 |
| `SlideInY` | 220ms slideInVertically(from 8dp) + fade | 上滑入场 |
| `ReducedMotion` | 0ms all transitions | reduced-motion flag 触发 |

无 spring / 无 cubic-bezier / 无 stagger。reduced-motion 检测走 `LocalConfiguration` + `AccessibilityManager`。

### 4.10 固定尺寸 token(`Dimens`)

| Token | dp | 用途 |
|---|---|---|
| `Mascot` | 120 | Idle 吉祥物固定尺寸 |
| `SeverityRuleWidth` | 4 | 命中卡左侧色条 |
| `Hairline` | 1 | 分隔线 |
| `ScreenEdgePadding` | 20 | 屏边距 |
| `TabPillHeight` | 36 | tab 高度 |

---

## 5. 架构重组

### 5.1 HomeScreen 拆分

```
ui/home/
├── HomeScreen.kt          ← 仅 layout 编排 + state switch
├── HomeScreenState.kt     ← sealed interface { Idle, Loading, Complete, Error }
├── HomeStateIdle.kt       ← Idle body 子 Composable
├── HomeStateLoading.kt    ← Loading body 子 Composable
├── HomeStateComplete.kt   ← Complete body 子 Composable
└── HomeStateError.kt      ← Error body 子 Composable

`HomeScreenState` 是 sealed interface 4 值:`Idle / Loading(stage) / Complete(report) / Error(throwable)`。Tab→reset 触发 `state = Idle`,没有独立的 Reset state。
```

每个 `HomeStateXxx` 接收必要的 props(`state: HomeScreenState`, `imageUri: Uri?`, `viewModel: IceSpiritVisionViewModel`, ...),单一职责。Robolectric 单测可独立覆盖每态。

### 5.2 死代码清理

- **`LoadingOverlay.kt`(115L)**:完整但未挂载。**修法**:挂载到 `HomeStateLoading` 内,替换 HomeScreen 当前的裸 `Text(loadingLabelRes(stage))`。
- **`SeverityBadge.kt`(63L)**:手选 dark/light 色对,绕过 `LocalSeverityColors`。**修法**:删除整个文件,所有调用点改用 HitCard 内部的 SeverityChip 或新增的统一 `SeverityLabel`。

### 5.3 新增 theme 文件

```
ui/theme/
├── Color.kt           ← 现有,小幅增补
├── Theme.kt           ← 现有,默认 ThemeMode 改为 LIGHT
├── SeverityColors.kt  ← 现有,增 ruleColor + textColor 字段
├── Shape.kt           ← 现有,小幅调整(去掉大半径)
├── Type.kt            ← 现有,扩为完整 typography(加 fontFamily / lineHeight / letterSpacing)
├── ThemeMode.kt       ← 现有,默认 SYSTEM → LIGHT
├── Spacing.kt         ← 新增:@Immutable data class + LocalSpacing
├── Elevation.kt       ← 新增:enum + LocalElevation
├── Motion.kt          ← 现有,扩为 5 组常量 + reduced-motion 支持
└── Dimens.kt          ← 新增:固定尺寸 + LocalDimens
```

`Spacing` / `Elevation` / `Dimens` 用 `@Immutable data class` + `staticCompositionLocalOf`,与 `LocalSeverityColors` 平级。

### 5.4 NavHost 转场

`IceSpiritNavHost.kt` 当前 5 路由无转场。补:

| 跳转 | enter | exit |
|---|---|---|
| HOME ↔ VIEWER | `slideInHorizontally(260ms easeOut from +width/3) + fadeIn` | `slideOutHorizontally(200ms easeIn to -width/4) + fadeOut` |
| HOME → SETTINGS | `slideInHorizontally(260ms from +width/3) + fadeIn` | 同上 |
| SETTINGS → CHANGELOG | 同上 | 同上 |
| 返回(pop) | 反向 slideIn + fadeIn | 反向 slideOut + fadeOut |

转场期间:捕获 gestures 禁用、`Tab → reset` 回调挂起、全部走 `IceMotion.Standard` token。

### 5.5 字体资源

新增 `app/src/main/res/font/source_han_serif_sc_*.ttf`(思源宋体 SC 6 字重)。License:SIL OFL 1.1,可商用。`app/build.gradle.kts` 不动(AGP 自动把 res/font/ 包进 APK)。

---

## 6. 组件改动

### 6.1 `HitCard`

- 卡面 = Panel(LIGHT `#FFFFFF` / DARK `#16161A`),**无容器染色**(原 sev.container 全部移除)
- 左侧 4dp 实色色条 = severity ruleColor,贯穿整张卡高度
- 第一行:`LabelSmall 11sp 700 + 2px letter-spacing` 显示「违规 / 警告 / 信息 / 正面」= severity textColor;跟 matched text 一行,用 `Title 22sp 600 思源宋体`
- 第二行:`BodySmall 14px` OnBgMuted = 「依据 · 《广告法》§9 §28」
- 法条原文走可折叠 Disclosure,展开后 1px HairlineAccent 顶部分割 + `Caption 12px` OnBgMuted
- 内 padding `Spacing.lg`(20dp),卡间距 `Spacing.md`(12dp)
- 右上角不再挂 SeverityChip(色条 + kicker 已足够)

### 6.2 `StatusBanner`(KPI 条)

- 不再走 3 块色卡片。改成:
  - 一行 kicker「3 处命中 · 严重度分布」(`LabelSmall 11sp 700 + 1.5 letter-spacing`)
  - 1px HairlineAccent 分隔线
  - 一行「`Display 42sp 700 思源宋体`数字」+ 内联 `LabelSmall` 严重度 kicker(LIGHT 数字 OnBg + 严重度色 kicker,DARK 同理)
  - 间距:数字 → Spacing.sm(8dp) → kicker;桶间 Spacing.xl(32dp)
- 例:`3 违规   1 警告   0 信息`,数字之间 spacing.xl
- 数字用 `AnimatedContent` fade220ms 平滑过场(kicker 不动)
- 整条走 Panel 背景 + 上下 1px HairlineAccent 分隔线(不是色块)
- tooltip 触发:点击(不是长按,沿用 v0.1.41 契约),文字「违规 = 广告法明文禁止 / 警告 = 需结合语境 / 信息 = 合规资质」
- LiveRegion:`Modifier.semantics { liveRegion = LiveRegionMode.Polite }`,TalkBack 朗读「违规 N 处,警告 M 处」

### 6.3 `HighlightOverlay`(Viewer 叠框)

- 描边宽度 2px(原 6px 太厚,Editorial 收紧)
- 颜色 = severity ruleColor,**无内部填充**(改 fill:transparent)
- 圆角 2px
- 动画:enter fade 180ms
- 修 FIXME Task 11:`maxOfOrNull` 顺序 vs Positive 桶。改用 `severityRank` + `worstSeverityForLine`,与 `ViewerTextList` 复用同一函数

### 6.4 `RuleTabBar`(顶部 tab)

- 极浅灰 pill(LIGHT `#EFEDE6` / DARK `#1F1F22`)
- 未选中态:透明背景 + OnBgMuted 文字
- 选中态:pill bg + OnBg 文字 + `Source Han Serif SC 16sp 600`
- 切换动画:fade 180ms + slideX 4dp(easeOut,无 spring)
- 仍走 `visibleTabs = listOf(RuleTab.AdSignage)` 单 tab,保留 `RuleTab.FoodLabeling` enum

### 6.5 `CaptureBar`(底部行动条)

- hasHits=false:2 个等宽按钮(选图 / 拍照),中间不空缺
- hasHits=true:3 个等宽(选图 / 导出 / 拍照)
- 主 CTA「拍照 / 导出取证包」文字 = Accent `#1F3A52`(品牌色规则)
- 次要「选图」= 透明 + 1px Divider 描边 + OnBg 文字
- enabled=false:仅禁拍照 + 导出(选图永远可点,Loading 时仍可换图)

### 6.6 `ImagePreview`(Idle 预览区)

- Idle:吉祥物 120dp 居中 + 下方副标题
  - 副标题:`Title 22sp 600 思源宋体`「拍一下广告,几秒告诉你哪里要改」
  - 副标题下 Spacing.md(12dp)间距一行:`BodySmall 14px` OnBgMuted「取一张招牌 / 拍一张照片」
- Loading:ImagePreview 上挂 `LoadingOverlay`(挂载!) + ImagePreview 内 Skeleton 图占位
- Complete:原 HighlightOverlay 叠框 + 双击进 Viewer

### 6.7 `ViewerImage`

- 沿用 Telephoto `ZoomableAsyncImage` + HighlightOverlay
- 与 home `ImagePreview` 共用 `computeFitTransform`
- HighlightOverlay 2px 描边 + 无填充 + 圆角 2px

### 6.8 `ViewerTextList`

- 每行 OCR 文字,字号 `BodySmall 14px`
- 命中行背景 = severity container(8% alpha 而非 100%,避免染色过重)
- 命中子串用 `LabelSmall 11sp 700 + 1.5 letter-spacing` kicker 风格,色 = severity ruleColor 100%
- 整行高 24dp,行间距 Spacing.xs(4dp)

### 6.9 新增 `SeverityLabel`(统一严重度标签)

- `@Composable fun SeverityLabel(severity: Severity, modifier: Modifier = Modifier)`:走 `SeverityColors.textColor(severity)` + `LabelSmall` typography + severity 中文名「违规 / 警告 / 信息 / 正面」
- 用于:HitCard 第一行 kicker、ResultPanel section header、ViewerTextList 行 kicker、未来可能的导出取证包

### 6.10 新增 `Hairline`(统一 hairline)

- `@Composable fun Hairline(modifier: Modifier = Modifier, accent: Boolean = false)`:1dp 高 `Divider` 或 `Box`,可选 accent 14% alpha
- 替换所有内联 `HorizontalDivider()` + 自定义 alpha 调用

---

## 7. 屏幕布局与流转

### 7.1 HomeScreen — 5 态布局

每态共用 `HomeTopBar` + `RuleTabBar`,Body 由 state 切换:

```
┌─────────────────────────────┐
│  HomeTopBar                 │ ← 标题居中 + 右上 settings gear
│  RuleTabBar                 │  ← 极浅灰 pill "广告招牌"
├─────────────────────────────┤
│        Body                 │  ← ImagePreview + state 子 Composable
├─────────────────────────────┤
│  CaptureBar                 │
└─────────────────────────────┘
```

| 态 | Body | CaptureBar |
|---|---|---|
| **Idle** | ImagePreview (吉祥物 + 副标题) | 2 按钮:选图 / 拍照 |
| **Loading** | ImagePreview (用户上传图 + Skeleton) + LoadingOverlay | 选图 enabled / 拍照 disabled |
| **Complete** | ImagePreview (用户图 + HighlightOverlay) + StatusBanner + ResultPanel(scrollable) | 3 按钮:选图 / 导出 / 拍照 |
| **Error** | ErrorPanel 居中(衬线大标题 + BodySmall 原因 + 衬线「重试」按钮) | 2 按钮 enabled |

**关键约束**(沿用 v0.1.41 / CLAUDE.md):
- Tab 同 tab + 非 Loading → `reset()`(VM 内部)
- Tab 同 tab + Loading → no-op
- Tab 异 → 切 matcher,保留 state(FoodLabeling 解锁后才有意义)
- Idle ↔ Loading ↔ Complete 切换走 `IceMotion.Standard` fade 220ms + slideY 8dp easeOut
- Error → 重试 → Loading(不是 Idle)

### 7.2 ViewerScreen

```
┌─────────────────────────────┐
│  ViewerTopBar               │  ← ← 返回(改 R.string.action_back)+ 居中标题 "查看图片"
├─────────────────────────────┤
│     ViewerImage             │  ← 上半,pinch/pan/双击 zoom,HighlightOverlay 叠 2px 严重度色框
├─────────────────────────────┤
│  ViewerTextList │  ← 下半 LazyColumn,每行 OCR 文字
└─────────────────────────────┘
```

### 7.3 Settings / Changelog / UpdateDetail

- AppBar:← 返回 + 居中标题(思源宋体 Title 22/600)
- Settings:3 section(Appearance / Update / Changelog),section 间 1px Divider(不用阴影)
- Changelog/UpdateDetail:走 `VersionHistoryRenderer`,version 用 `Headline 32px 700 思源宋体` + BodySmall 14px 内容

### 7.4 NavHost 转场(见 §5.4)

---

## 8. 严重度语义层

### 8.1 4 桶定义(沿用现有 Severity enum)

- **Violation**:广告法明文禁止,需立即下架。RuleHit.severity 的最高级别
- **Warning**:需结合语境判断,可能合法但有风险
- **Info**:合规资质相关,需另行核实
- **Positive**:扫描结果合规,无任何命中时显示

Positive 必须永远不能升级显示(已被 v0.1.36 enum 重排固化,`Violation = 3 / Warning = 2 / Info = 1 / Positive = 0`,`severityRank` 函数在 `domain/AnalysisState.kt` 顶层)。

### 8.2 文案规则

- 严重度 kicker 文字 = 4 字固定:「违规 / 警告 / 信息 / 正面」
- 全部走 `LabelSmall` typography(ALL-CAPS 风格:letter-spacing +1.5)
- 颜色 = `SeverityColors.textColor(severity)`(新 token)
- 不再用 ALL-CAPS 英文(原 violation/warning/info/positive 的英文不用)

### 8.3 三处使用规则

| 位置 | 严重度视觉呈现 |
|---|---|
| HitCard 第一行 kicker | LabelSmall + severity textColor |
| ResultPanel section header | Headline + 桶计数 + HairlineAccent |
| ViewerTextList 行 | 8% container 背景 + ruleColor kicker 子串 |
| Viewer HighlightOverlay | ruleColor 2px 描边 |
| ViewerTextList 整行背景 | container 8% alpha |

---

## 9. 品牌色应用规则(3 处)

1. **主 CTA 文字**(拍照 / 导出取证包)= `Accent`
2. **Hairline 分隔线**= `HairlineAccent`(14% alpha)
3. **步骤指示 + 进度**(Home 5 态流转的 progress indicator)= `Accent`

不用:Tab pill 选中态背景(用 PanelSoft / PanelStrong)、Viewer 高亮叠框(用 severity ruleColor)、Idle / Complete body 背景(用 Bg / Panel)。

---

## 10. 可访问性

- **LiveRegion**: `StatusBanner` KPI 数字变化时 `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`,TalkBack 朗读「违规 2 处,警告 1 处」
- **Focus order**: ResultPanel 内 LazyColumn 焦点从顶部命中卡往下走,Tab → StatusBanner → 命中卡 → CaptureBar 顺序自然
- **StateDescription**: HomeScreen 顶层 `Modifier.semantics { stateDescription = stateLabel(state) }`,告诉 TalkBack 当前「空闲 / 识别中 / 完成 / 错误」
- **Reduced motion**: `LocalAccessibilityManager.current` + `LocalConfiguration.current` 检测 `prefersReducedMotion`,触发后 `IceMotion` 全局返回 0ms
- **Font scaling**: 所有 sp 跟随系统缩放,KPI Display 42 在 fontScale=1.3 时撑到 55px — 验证 ResultPanel 不破布局(必要时 clamp maxLines / single-line overflow)
- **Color contrast**:
  - 主文本 OnBg → Bg:LIGHT 16.4:1 / DARK 15.8:1(WCAG AAA)
  - 严重度 ruleColor → Panel:Violation 7.1 / Warning 4.6 / Info 5.2 / Positive 4.3(全部 AA,Warning 4.6 刚好过 AA 大字阈值)
- **minimumInteractiveComponentSize**: CaptureBar 按钮 ≥ 48dp,Tab pill ≥ 40dp,M3 默认
- **Mascot 描述**: `contentDescription = "冰灵锐目空闲状态装饰"`,仅当无其他文字时朗读
- **`ViewerTopBar` 修复**:原硬编码 `"Back"` 改 `R.string.action_back`

---

## 11. 迁移策略(3 个 minor bump)

CLAUDE.md `feedback-release-hygiene` 说版本号只对实际功能/修复负责。本次「视觉重塑 + 架构清理」跨 3 个 minor 是合适的:触及 31 个 UI 文件 + 5 路由 + 新增字体资源 + 旧 HomeScreen 单测需重写。

### 11.1 v0.1.X+1 — Foundation(地基)

- 新增 `theme/Spacing.kt` / `theme/Elevation.kt` / `theme/Dimens.kt` / `theme/Motion.kt` 扩为 5 组常量 + reduced-motion
- 新增 `app/src/main/res/font/source_han_serif_sc_*.ttf` 6 字重
- `theme/Type.kt` 改为基于思源宋体的完整 typography
- `theme/SeverityColors.kt` 加 `ruleColor` / `textColor` 字段
- HomeScreen 全部 UI 暂时用新 token,**视觉不变**(Spacing 4/8/12 等同现状、字体 fallback 到系统、动效时长接近)
- 测试:新增 token 单测、HomeScreen 现有测试零修改

### 11.2 v0.1.X+2 — Components + HomeSplit(主体)

- HitCard / StatusBanner / RuleTabBar / CaptureBar / ImagePreview / HighlightOverlay / ViewerImage / ViewerTextList 全部按 §6 重写
- 新增 SeverityLabel / Hairline
- HomeScreen.kt 拆分 5 态文件
- LoadingOverlay 挂载到 HomeStateLoading
- 删 SeverityBadge.kt,全调用点改用 SeverityLabel 或 HitCard 内 SeverityChip
- 修 HighlightOverlay FIXME Task 11
- 测试:HomeScreen 现有测试改为 per-state;ResultPanel 严重度分组测试断言改用 SeverityLabel.textColor

### 11.3 v0.1.X+3 — Polish + Theme Flip + NavHost(收尾)

- 主题默认 DARK → LIGHT(改 ThemeMode factory 默认 + Settings 加「上次使用主题」记忆,避免升级后用户被强制切到亮色)
- ImagePreview Idle 副标题「拍一下广告,几秒告诉你哪里要改」落地
- NavHost 转场补齐(§5.4)
- Settings / Changelog / UpdateDetail 走新 token
- Accessibility 全套(§10)
- 修 ViewerTopBar 硬编码 "Back" bug
- 真机 A/B 截图归档到 `docs/smoke/2026-MM-DD-vision-editorial-redesign.md`

### 11.4 每个发版号前必跑 5 项(沿用 v0.1.58)

1. `compliance-checker` agent 审计
2. `regulation-freshness-checker` 扫规则
3. `./gradlew.bat testDebugUnitTest` 全过
4. 真机 `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...Audit71ImageE2E` 全过
5. Triple-SHA 对齐(tag = HEAD = gitea latest)

---

## 12. 测试矩阵

| 类型 | 内容 | 工具 |
|---|---|---|
| Token 单测 | Spacing/Elevation/Dimens/Motion/SeverityColors 数值 | JUnit(Robolectric) |
| Per-state HomeScreen | Idle/Loading/Complete/Error 各自 Composable 测试 | Robolectric |
| Tab→Reset 契约 | 3-state 契约(同 tab / 同 tab+Loading / 异 tab) | JUnit 沿用 IceSpiritVisionViewModelTabTest |
| HighlightOverlay | severityRank + 2px 描边 + fade180ms | Robolectric + Canvas |
| ResultPanel 严重度分组 | 3 section 按 SeverityLabel.textColor 排序 | Robolectric |
| Accessibility | LiveRegion / stateDescription / fontScale=1.3 / reduced-motion | instrumented 真机(nova 6) |
| NavHost 转场 | 5 路由 slide + fade | instrumented 录屏手测 |
| 真机 OCR 命中回归 | audit71 fixture 71 张 | connectedDebugAndroidTest 沿用 AdSignageAudit71ImageE2E |

---

## 13. 验收标准

- [ ] **视觉**:浅色主屏 idle / loading / complete / error + Viewer + Settings 6 张 Robolectric golden 与人工目测一致
- [ ] **行为**:4 张广告招牌 fixture(蟹都汇 / 杜蕾斯 / 中医秘方 / 协和医院) `ice_ocr_rules` profile — OCR 行数 / 命中数 / 严重度分布与 v0.1.58 字节级一致
- [ ] **测试**:`testDebugUnitTest` 全绿,既有 ViewModel / RuleMatcher / Export 测试零修改
- [ ] **真机**:nova 6 + emulator API 26 跑 `connectedDebugAndroidTest`,LiveRegion / reduced-motion / fontScale=1.3 三场景过
- [ ] **回归**:不发版号不 bump(沿用 hygiene);3 个 minor 实际改动齐全
- [ ] **约束**:CLAUDE.md Chat 1:1 约束保持 — brand accent hex 不动
- [ ] **可访问性**:TalkBack 走查覆盖所有交互;色盲模拟器(protanopia / deuteranopia)下严重度可区分
- [ ] **资源**:font resource 编译进 APK,无 fallback 警告;res/font/ 不冲突 build

---

## 14. 决策登记

| 决策 | 依据 |
|---|---|
| Editorial 而非 M3 Expressive / Glass / Functional | §3 用户在 4 选 1 |
| 思源宋体而非文楷 / 思源黑体 / Heavy 公文 | §3.1 |
| 左侧 4dp 色条 + kicker 而非 hairline / 整面染色 / 图标 | §3.2 |
| 极浅灰 pill 而非下划线 / 黑底白字 / 字重差 | §3.3 |
| 静默纸页 fade 220ms 而非 spring / 报纸节奏 | §3.4 |
| 保留 120dp 居中 + 副标题「拍一下广告」 | §3.5 |
| 品牌色用在主 CTA / hairline / 步骤指示(3 处) | §3.6 |
| 主题默认 DARK → LIGHT | Editorial 报纸气质 + §2 第4条(违反既有 memory,已告知用户) |
| 严重度色通过 LocalSeverityColors 注入 + 新增 ruleColor / textColor | 沿用 v0.1.36 + §4.3 |
| HomeScreen 拆分 5 态文件 | §5.1 — 解决 419 行单体 |
| 删 SeverityBadge.kt | §5.2 — 与新 SeverityLabel 冲突 |
| 修 HighlightOverlay FIXME Task 11 | §6.3 — 改用 severityRank |
| 修 ViewerTopBar 硬编码 "Back" | §10 — 字符串一致性 |
| 跨 3 个 minor bump 而非一次性 | §11 — 控制发版风险 |
| 单 CTA 文字 = Accent(非 background) | §9 — Editorial 不用彩色填充按钮 |

---

## 15. 不在本次范围内

- 后端逻辑 / OCR / 规则库 / 状态机 / 导出 — 不动
- 历史记录 / 列表页(本地 SQLite)— 留 Phase 4+
- 食品标签 OCR Tab 启用 — 沿用 CLAUDE.md 规划
- 批量审图 — 留 Phase 5+
- 多语种 i18n / 语音播报 / 云端同步 — 留 Phase 4+
- 跨项目 Chat 同步(token 修改类)— 不在本次范围,Chat 项目应保持现状
- Material You 动态取色 / wallpaper extraction — 不引入
- 自有字体设计 / 字体 license 谈判 — 思源宋体 SIL OFL 1.1 可商用,直接引入

---

## 16. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 思源宋体 APK 体积 +1.2 MB | 中 | 用户感知小 | 单字重 fallback(只引 Regular + Bold 两档,体积 +0.4 MB);v0.1.X+1 验证 |
| 主题默认翻 LIGHT 引起老用户不适 | 中 | 体验变化 | v0.1.X+3 发版时 Settings 加「上次使用主题」记忆,首次升级尊重旧默认(仍是 DARK),用户首次手动切 LIGHT 后才记 LIGHT |
| 字体加载阻塞首屏 | 低 | 启动 200-500ms 慢 | Compose `FontFamily` 走异步加载,fallback 系统默认;v0.1.X+1 真机测首屏冷启动 |
| HighlightOverlay 2px 描边在缩略图下不可见 | 低 | 现场识别 | 在 ViewerImage 双击 zoom 后描边同步放大;v0.1.X+3 真机 A/B 验证 |
| Tab → reset 契约测试需要改 per-state | 中 | 测试改动 | v0.1.X+2 一次到位,Robolectric per-state 测试覆盖 |
| reduced-motion flag 检测 API 不可靠 | 低 | 无障碍降级 | 双 fallback:`AccessibilityManager.isHighTextContrastEnabled` + `Configuration.fontScale` 异常检测 |
| Audit71 71 张 fixture 在新 UI 下 OCR 行数变化 | 低 | 回归 | v0.1.X+3 前 `connectedDebugAndroidTest` 全过,否则回滚 |

---

## 17. 关联文档

| 文档 | 用途 |
|---|---|
| [`CLAUDE.md`](../../CLAUDE.md) | 项目根指令(命名 / 发版 hygiene / 跨项目约束) |
| [`docs/superpowers/specs/2026-08-25-icevision-ui-modernization-design.md`](2026-08-25-icevision-ui-modernization-design.md) | 上一版 UI spec(M3 Expressive),本 spec 是其继承与替代 |
| [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](2026-08-15-icevision-ui-design.md) | 初版 UI 设计稿 |
| [`docs/superpowers/specs/2026-09-02-home-header-tab-polish-design.md`](2026-09-02-home-header-tab-polish-design.md) | 上一轮 header/tab polish,本 spec 在其基础上深化 |
| [`docs/knowledge/build-stack-2026-08.md`](../../knowledge/build-stack-2026-08.md) | AGP / Kotlin / Gradle 版本矩阵 |
| [`docs/smoke/2026-09-02-icevision-v0.1.47-release.md`](../../smoke/2026-09-02-icevision-v0.1.47-release.md) | 发版 smoke record 模板 |
| [`.claude/agents/regulation-freshness-checker.md`](../../../.claude/agents/regulation-freshness-checker.md) | 法规新鲜度审计(每个发版号前必跑) |
| [`.claude/agents/compliance-checker.md`](../../../.claude/agents/compliance-checker.md) | 合规审计(每个发版号前必跑) |