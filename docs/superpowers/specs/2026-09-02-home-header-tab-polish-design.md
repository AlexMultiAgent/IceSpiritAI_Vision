# 冰灵锐目 — 首页标题去除 ⚡ + 字号微调 + Tab 重设计为软色 chip 设计规范

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-09-02 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 UI spec | `docs/superpowers/specs/2026-08-15-icevision-ui-design.md` |
| 叠加关系 | 承接 [2026-09-02-settings-about-block-design.md §3.4](2026-09-02-settings-about-block-design.md#34-首页顶部标题字号--homeScreenkttopbarkt84) — 上一版把 `titleLarge` 22sp 下调为 `titleMedium` 16sp 后,用户反馈"16sp 太小",本次回拨到 20sp;同时把前次保留的 ⚡ 去掉,把突兀的 pill tab 改为软色 chip |

本文档**叠加**在既有 UI spec 之上,**仅**涉及首页顶部三处纯 UI 渲染调整:标题文案 / 标题字号 / Tab pill 视觉。后端逻辑(ViewModel / state / matcher / tab 路由)与构建系统(AGP / Kotlin / Gradle)完全保持现状,不动。

---

## 1. 背景与目标

### 1.1 现状

[HomeTopBar.kt](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt) 首页顶部标题三段式 `prefix("冰灵") + bolt("⚡") + suffix("锐目")` 当前 `titleMedium`(16sp,定义见 [Type.kt:13](../../app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt#L13)),中间 bolt 单独 `colorScheme.tertiary` 染色 + 4dp 横向 padding。用户反馈 **16sp 太小、bolt 也不需要了**。

[RuleTabBar.kt](../../app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt) tab 当前是 `PillTab` 私有 Composable:`Surface(RoundedCornerShape(20.dp))` + 选中态 `secondaryContainer` + `titleMedium.copy(SemiBold)`(lines 100–118)。因为 `visibleTabs = listOf(RuleTab.AdSignage)`(line 40,CLAUDE.md §产品方向 锁定),**只有一个永远选中的 tab**,但仍然渲染成强对比的"segmented control 样式",在 idle 界面上像孤立按钮、突兀。

### 1.2 目标

1. 首页顶部标题合并为单段 "冰灵锐目"(去掉 ⚡),字号 16sp → 20sp
2. 「广告招牌」tab 从突兀的 pill 改为 **软色 chip + leading icon(Verified)**,跟 20sp 标题拉开视觉层级
3. 清理不再使用的字符串资源(`app_name_prefix` / `app_name_bolt` / `app_name_suffix`),减少 string table 体积

### 1.3 非目标(本期)

- 改 `R.string.app_name` 内容(launcher / manifest / TalkBack 仍读 "冰灵锐目")
- 启用 `FoodLabeling` tab(`visibleTabs` 仍按 CLAUDE.md 锁定为 `listOf(RuleTab.AdSignage)`)
- 改 `IceSpiritTypography` token(不新增、不修改 — 仅在 HomeTopBar 内本地 override 字号)
- 改 tab 路由 / `setTab` 3-state 契约 / `IceSpiritVisionViewModelTabTest`
- 改吉祥物(mascot)、`ImagePreview`、KPI bar / ResultPanel / HitCard 等其他首页组件
- 调整 chip 选中态分支逻辑(`isSelected` 仍按 KDoc 保留,FoodLabeling 启用时这个分支还要用)

---

## 2. 总体方案

**纯 Compose 渲染层三处微调,不动 ViewModel / 不动 typography token / 不动 ViewModelTest。**

3 个候选对比与排除理由(每个变更独立评估):

### 2.1 字号 → 20sp

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — HomeTopBar 内本地 `titleMedium.copy(fontSize = 20.sp)`(选定)** | 仅 1 个 Composable 受影响,1 行 style 调整;`Type.kt:13` 不动 | — |
| B — `Type.kt:13` 全局 `titleMedium` 16sp → 20sp | 单点改动 | 影响面 7+ 文件:`RuleTabBar.kt:114`(pill 文字变成 20sp,过粗)、`ResultPanel.kt:54/65/139`(三处 section 标题过大)、`ViewerTopBar.kt:33`、`ChangelogScreen.kt:95`、`UpdateDetailScreen.kt:98`、`AppearanceSection.kt:34`、`UpdateSection.kt:105` — 全部连锁需调整,超出本期 scope |
| C — 新增 typography style `appTitleLarge` 20sp | 语义清晰 | YAGNI — 当前仅 1 处需要 20sp;新增 token 后未来无人用,徒增维护成本 |

### 2.2 去除 ⚡ + 文案合并

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — 3 段 Text 合并为 1 段 Text,使用 `app_name` 字符串(选定)** | 删 3 个字符串;Row 内单 Text 简洁;TalkBack 仍读 "冰灵锐目" | — |
| B — 保留 `app_name_prefix` + `app_name_suffix` 两段拼接 | 不动 string table | 失去了 bolt 之后这 2 个字符串无任何拼接语义,留存是死代码 |
| C — 只删 bolt Text,字符串保留 | 不动 string table | bolt 字符串 `app_name_bolt` 完全无引用,留作死资源 |

### 2.3 Tab pill → chip + Verified icon

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — 保留 `PillTab` Surface 自绘 + `Icons.Outlined.Verified` leading + `tertiaryContainer` 配色(选定)** | M3 canonical 配色 + 自定义容器 shape;`Role.Tab` 语义保留;`isSelected` 分支保留供 FoodLabeling 启用时复用 | — |
| B — 用 `AssistChip` / `FilterChip` 等 M3 组件 | 标准 M3 组件 | 项目中其他 chip/Card 均自绘 + `Role.Tab` 测试锚点依赖 `PillTab` 节点结构,改 M3 标准 chip 会让 `RuleTabBarTest` 节点断言需重新对齐,且 `Material Icons Extended` 已有 `Verified`,无必要绕开 |
| C — 硬编 `tertiary.copy(alpha = 0.14f)` 贴 mockup | 颜色与 mockup 像素级一致 | 非 canonical 颜色 token,主题切换 / 后续 token 调整时易 drift;`tertiaryContainer` 已经表达"软 tertiary"语义 |

---

## 3. 详细改动

### 3.1 首页顶部标题文案 + 字号 — [HomeTopBar.kt:75-96](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt#L75)

**当前** `Row(semantics(mergeDescendants = true) { contentDescription = a11yTitle })` 内嵌 3 个 `Text`:`app_name_prefix`("冰灵")+ `app_name_bolt`("⚡",`tertiary` 色 + 4dp padding)+ `app_name_suffix`("锐目"),均 `titleMedium` 16sp。

**改为** 单 `Text(stringResource(R.string.app_name))`,`style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)`。

`Text` 自带语义标签(`text` 直接作为 a11y content),**删除**原来 `Row` 上的 `Modifier.semantics(mergeDescendants = true) { contentDescription = a11yTitle }`(单 child 时为 no-op,删掉避免冗余)。TalkBack 仍读 "冰灵锐目" — 来源是 `Text` 自身的 `text` 参数。

### 3.2 Tab pill → chip — [RuleTabBar.kt:83-121](../../app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt#L83) `PillTab`

**当前** `Surface(shape = RoundedCornerShape(20.dp), tonalElevation = if (isSelected) 2.dp else 0.dp)` 包单 `Text`,选中态 `secondaryContainer` + `onSecondaryContainer` + `titleMedium.copy(SemiBold)`,未选中 `surfaceVariant` + `onSurfaceVariant` + `bodyLarge`。

**改为**:

- `Surface(shape = RoundedCornerShape(50))`(pill 完全圆角),删除 `tonalElevation`(`tertiaryContainer` 已经是软色,elevation 叠加会失真)
- 容器内 `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp))`:
  - 前置 `Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)`
  - 后置 `Text(stringResource(tab.titleRes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)`
- 颜色:选中态 `tertiaryContainer` + `onTertiaryContainer`,未选中态保留 `surfaceVariant` + `onSurfaceVariant`(FoodLabeling 启用时复用)
- 内 padding:`padding(horizontal = 14.dp, vertical = 8.dp)`(`labelLarge` 14sp + 16dp 图标 → 略小于当前 20dp horizontal,但跟 20sp 标题层拉开距离)
- 导入新增:`import androidx.compose.material.icons.outlined.Verified`(已在 [build.gradle.kts:917](../../app/build.gradle.kts#L917) `compose.material.icons.extended` 依赖内,无需新增 dep)

`clickable(role = Role.Tab, ...)` 不动(`RuleTabBarTest` 节点计数锚点)。`isSelected` 参数保留。

### 3.3 字符串清理 — [strings.xml](../../app/src/main/res/values/strings.xml#L8-L10)

删除 3 行:

```xml
<string name="app_name_prefix">冰灵</string>
<string name="app_name_bolt">⚡</string>
<string name="app_name_suffix">锐目</string>
```

`app_name`(line 3)保留 — launcher label / a11y / 新标题统一来源。

---

## 4. 不动的东西

- `R.string.app_name`(line 3,值仍为 "冰灵锐目")
- `R.string.tab_ad_law`(line 40,chip 文本内容)
- `R.string.tab_food_label`(line 41,FoodLabeling 启用时的 chip 文本)
- `R.string.tab_switch_desc`(line 76,Row 容器 a11y)
- `R.string.settings_button_desc`(line 75,HomeTopBar 齿轮按钮 a11y)
- `IceSpiritTypography` token(`Type.kt:13` `titleMedium = 16sp Medium` 不动)
- `RuleTabBar.visibleTabs = listOf(RuleTab.AdSignage)`(line 40,CLAUDE.md §产品方向 锁定)
- `PillTab.isSelected` 分支逻辑与 `Role.Tab` 语义
- `IceSpiritVisionViewModel.setTab` 3-state 契约 / `IceSpiritVisionViewModelTabTest`
- ThemeMode / Color.kt 调色板 / 主题切换逻辑
- ViewModel / state / Repository / matcher / 规则引擎
- `HomeScreenBare` 测试 fallback `Text(stringResource(R.string.app_name), style = titleMedium)`([HomeScreen.kt:373](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt#L373),沿用 16sp 作为最小测试标题,不与生产 20sp 视觉绑定)

---

## 5. 测试

只改 UI 渲染,不动 ViewModel / state。

- **新增单测**:`RuleTabBarTest` 已存在(锚点 `Role.Tab` 节点数 = 1),增加 1 条断言 `Icon(Icons.Outlined.Verified) 渲染存在`(用 `composeRule.onNodeWithContentDescription(null).assertExists()` 或显式 `testTag` 锚点 — 倾向 `testTag`,contentDescription 为 null 时 TalkBack 行为更明确)
- **既有单测**:
  - `IceSpiritVisionViewModelTabTest` 3 条 `setTab` 契约测试不动
  - `RuleTabBarTest` `Role.Tab` 节点数 = 1 断言不动
- **真机烟测**(Release pipeline / `/icevision-release`):
  - 深夜雪夜主题:首页标题 20sp 渲染、无 ⚡;"广告招牌" chip 显示紫色 `tertiaryContainer` 容器 + Verified 盾牌图标 + 14sp Medium 文字;点击 chip 仍触发「同 tab → 非 Loading → reset to Idle」契约
  - 浅色冰月主题:chip 颜色切到浅紫,Verified 图标 onTertiaryContainer 在浅底仍清晰
  - TalkBack 长按:首页标题读 "冰灵锐目";chip 读 "广告招牌,选项卡"
- **回归检查**:
  - 整体首页首屏信息密度没有视觉塌陷:20sp 标题 vs 14sp chip vs 13sp 拍照提示,3 档层级清晰
  - chip 选中态在「食品标识」tab 未启用场景下永远显示(`isSelected = true`),未选中态样式仅作 KDoc 锚点保留

---

## 6. 文档同步

- 本 spec 叠加在 [2026-09-02-settings-about-block-design.md §3.4](2026-09-02-settings-about-block-design.md) 之上,旧版 §3.4 的 16sp 决策被本文档 §3.1 替代(字号从 16sp 回到 20sp + 去除 bolt + 合并 3 段为 1 段)
- Release changelog 在发版时由 `/project-commit` skill 自动同步:`首页标题去掉 ⚡ + 字号 20sp`、`Tab 改为软色 chip + Verified 图标`、`清理 app_name_prefix/bolt/suffix 3 个字符串`
