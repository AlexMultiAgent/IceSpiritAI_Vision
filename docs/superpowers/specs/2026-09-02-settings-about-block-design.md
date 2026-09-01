# 冰灵锐目 — 关于区三行堆叠 + 查看更新日志 Card 统一 + 首页标题字号 设计规范

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-09-02 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 UI spec | `docs/superpowers/specs/2026-08-15-icevision-ui-design.md` |

本文档**叠加**在既有 UI spec 之上,**仅**涉及三处纯 UI 渲染调整:设置页「关于」区文案堆叠、「查看更新日志」Card 框样式统一、首页顶部标题字号下调。后端逻辑(ViewModel / state / Changelog 路由)与构建系统(AGP / Kotlin / Gradle)完全保持现状,不动。

---

## 1. 背景与目标

### 1.1 现状(以浅色冰月主题截图为准)

[SettingsScreen.kt](../../app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt) 设置页底部仅一行「版本: 0.1.46」(`bodySmall` 纯文本,无 Card 包裹);「查看更新日志」项用 `Card` 套 Material3 `ListItem`,`ListItem` 内部用 `surfaceColorAtElevation` 自绘容器,**两个主题下都与外观/更新 Card 的 `surfaceContainerLow` 不一致**。

[HomeTopBar.kt](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt) 首页顶部标题三段式 `prefix + bolt + suffix` 已渲染为 "冰灵⚡锐目",当前 `titleLarge`(22sp),相对字号偏大,视觉重量压过了下面的 tab 行。

### 1.2 目标

1. 「关于」区在「版本: 0.1.46」上下各加一行同字号文案:`冰灵⚡锐目` 在上 / `哈尔滨市市场监管局` 在下
2. 「查看更新日志」Card 框样式与「外观」「更新」Card 在 `深夜雪夜` / `浅色冰月` 两个主题下视觉完全一致
3. 首页顶部标题字号下调一档(`titleLarge` → `titleMedium`),文字内容保持 "冰灵⚡锐目"

### 1.3 非目标(本期)

- 改 `R.string.app_name` 全局字符串(launcher / manifest / TalkBack 仍读 "冰灵锐目")
- 给「关于」区加 Card 容器(保持纯文本堆叠)
- 「冰灵⚡锐目」标题行加链接 / 跳转
- 改 ChangelogScreen 内容或入口路由
- 调整 AppearanceSection / UpdateSection / UpdateSection 内部 UpToDate Card 的样式

---

## 2. 总体方案

**纯 Compose 渲染层三处微调,不动 ViewModel / 不动 string resource 键名(仅新增 1 条)/ 不动主题 token。**

3 个候选对比与排除理由:

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — 三处独立改动 + 保留 ListItem 外层 Card 仅替换 ListItem→Row(选定)** | 最小侵入;每处改动 ≤ 15 行;复用现有 `app_name` + 新增 `settings_about_org` 单条 string | — |
| B — 改 ListItem `colors(containerColor = surfaceContainerLow)` | 1-2 行最小改 | `ListItem` 内部仍带 `surfaceColorAtElevation` 微差,两主题下与外观/更新 Card 仍有细微视觉差,达不到"完全一致"目标 |
| C — 整体重写 SettingsScreen 为 `ListItem` 列表 | 一次到位 | 远超本期 scope;外观/更新 Card 内已有复杂布局(SegmentedButtonRow / UpToDate inner Card),重构风险高 |

---

## 3. 详细改动

### 3.1 「关于」区三行堆叠 — [SettingsScreen.kt:108-112](../../app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt#L108)

**当前** 单 `Text` 显示版本号。

**改为** `Column` 包三行 `Text`,均 `MaterialTheme.typography.bodySmall`(与原版本字号一致),`padding(horizontal = 16.dp)`,顺序:

1. `冰灵⚡锐目` — 来自既有 `R.string.app_name`(字符串保持 "冰灵锐目",与 launcher / TalkBack 一致)
2. `版本: X` — 既有 `R.string.settings_about_version` + `BuildConfig.VERSION_NAME`
3. `哈尔滨市市场监管局` — **新增** `R.string.settings_about_org`

三行无额外间距(用 `Text` 默认 `lineHeight` 自然分开)。

### 3.2 「查看更新日志」Card 结构 — [SettingsScreen.kt:94-106](../../app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt#L94)

**当前** `Card` 套 Material3 `ListItem`,ListItem 内部自绘 `surfaceColorAtElevation` 容器,盖住外层 Card 颜色。

**改为** `Card` 直接套 `Row(Modifier.clickable { onOpenChangelog() })` + 内部 `padding(horizontal = 16.dp, vertical = 16.dp)`,结构与 [AppearanceSection.kt](../../app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt) padding 模式对齐。`Row` 内:

- `Column(Modifier.weight(1f))` 包两行 `Text`:标题 `查看更新日志` + 副标题 `查看每个版本的修改变动`(`bodySmall`)
- 右侧 `Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)`

外层 Card 默认 `surfaceContainerLow` container 色透出后,两个主题下与 外观 / 更新 Card 视觉完全一致。

### 3.3 新增字符串 — [strings.xml](../../app/src/main/res/values/strings.xml) L59 后

```xml
<string name="settings_about_org">哈尔滨市市场监管局</string>
```

### 3.4 首页顶部标题字号 — [HomeTopBar.kt:84, 88, 94](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt#L84)

三段 `Text`(`app_name_prefix` / `app_name_bolt` / `app_name_suffix`)的 `style = MaterialTheme.typography.titleLarge` → `style = MaterialTheme.typography.titleMedium`(22sp → 16sp)。文字不变;`Row` 上 `semantics(mergeDescendants = true) { contentDescription = a11yTitle }` 不变,TalkBack 仍读 "冰灵锐目"。

[HomeScreen.kt:373](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt#L373) 的 `HomeScreenBare`(测试 bare 路径)也同步 `titleLarge` → `titleMedium` 保持视觉一致。

---

## 4. 不动的东西

- `R.string.app_name` / `app_name_prefix` / `app_name_bolt` / `app_name_suffix` 字符串内容
- `AndroidManifest.xml` `android:label` 与 launcher 图标
- `AppearanceSection` / `UpdateSection` / UpdateSection 内 UpToDate Card 的 `secondaryContainer` 紫色
- ChangelogScreen 入口回调 `onOpenChangelog` 与 NavGraph 路由
- `IceSpiritTypography` token(不新增,复用现有 `bodySmall` / `titleMedium`)
- ThemeMode / Color.kt 调色板
- ViewModel / state / Repository

---

## 5. 测试

只改 UI 渲染,不动 ViewModel / state。无需新增单测。

Release pipeline 跑 [`/icevision-release`](../../../.claude/skills/icevision-release/SKILL.md) 走真机烟测覆盖:

- 浅色冰月主题:Settings 三个 Card 视觉一致;「关于」区三行文案正确堆叠
- 深夜雪夜主题:Settings 三个 Card 视觉一致
- 首页顶部标题字号明显小于 tab 字号,文字仍渲染为 "冰灵⚡锐目"(bolt 仍 `tertiary` 色)