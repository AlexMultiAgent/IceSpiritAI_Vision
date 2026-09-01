# Settings 关于区三行堆叠 + 查看更新日志 Card 统一 + 首页标题字号 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 三处纯 UI 渲染微调 — 设置页「关于」区堆叠三行文案(冰灵⚡锐目 / 版本: X / 哈尔滨市市场监管局)、「查看更新日志」Card 框样式与外观/更新 Card 在两主题下完全一致、首页顶部标题字号从 `titleLarge` 降到 `titleMedium`。

**Architecture:** 不动 ViewModel / state / 路由 / 调色板 / Typography token;只改 Compose 渲染层 + 新增 1 条 string 资源。每个改动 1 个 atomic commit,便于 revert。

**Tech Stack:** Android Compose (Material3)、JUnit + Robolectric(已有,仅用于确保现有测试不破)、Gradle 9.7 / AGP 9.3 / Kotlin 2.4.10、shell profile 构建验证(per CLAUDE.md)。

---

## File Structure

| 文件 | 改动 | 职责 |
|---|---|---|
| `app/src/main/res/values/strings.xml` | Modify (L60 后新增 1 行) | 新增 `settings_about_org` |
| `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt` | Modify (L18 import 调整 + L94-106 Card 结构 + L108-112 三行堆叠) | 关于区 + 查看更新日志 Card 渲染 |
| `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt` | Modify (L84/88/94 `titleLarge` → `titleMedium`) | 首页顶部标题字号 |
| `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` | Modify (L373 `titleLarge` → `titleMedium`) | HomeScreenBare 测试 bare 路径同步 |

新文件:无。

---

## Task 1: 新增 `settings_about_org` 字符串

**Files:**
- Modify: `app/src/main/res/values/strings.xml:60` (在 `settings_view_changelog` 之后插入)

- [ ] **Step 1: 编辑 strings.xml**

在 `app/src/main/res/values/strings.xml` 第 60 行 `settings_view_changelog` 之后插入新行:

```xml
    <string name="settings_about_org">哈尔滨市市场监管局</string>
```

插入后的 L59-L62 应为:

```xml
    <string name="settings_about_version">版本: %1$s</string>
    <string name="settings_about_org">哈尔滨市市场监管局</string>
    <string name="settings_view_changelog">查看更新日志</string>
    <string name="settings_view_changelog_hint">查看每个版本的修改变动</string>
```

(注:新行加在 L59 后,使得所有 `settings_about_*` 集中,顺序 = `version` → `org` → `view_changelog`。)

- [ ] **Step 2: 验证 XML 合法**

Run: `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:processDebugResources -PmodelProfile=shell --quiet`
Expected: BUILD SUCCESSFUL(若失败查看 XML 转义错误)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(strings): 新增 settings_about_org = 哈尔滨市市场监管局"
```

---

## Task 2: SettingsScreen.kt — 关于区三行堆叠

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt:108-112`

- [ ] **Step 1: 替换 关于区单 Text 为 Column 三行**

将 L107-112 的 `Spacer + Text` 段:

```kotlin
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
```

替换为:

```kotlin
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_about_org),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
```

(注意:`Spacer(modifier = Modifier.height(8.dp))` 保留不变,只把后面的单 `Text` 包成 `Column`。)

- [ ] **Step 2: 验证构建(shell profile)**

Run: `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): 设置页关于区三行堆叠(冰灵⚡锐目 / 版本 / 哈尔滨市市场监管局)"
```

---

## Task 3: SettingsScreen.kt — 查看更新日志 Card 替换 ListItem 为 Row+clickable

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt:18, 94-106`

- [ ] **Step 1: 调整 imports**

在 L18 删除:
```kotlin
import androidx.compose.material3.ListItem
```

在 L5-6 之间(`Column` import 之后)新增:
```kotlin
import androidx.compose.foundation.layout.Row
```

在 L25 后(`Modifier` import 之后)新增:
```kotlin
import androidx.compose.ui.Alignment
```

最终的 L1-L35 imports 应包含:`clickable`, `Arrangement`, `Column`, `Row`, `Spacer`, `fillMaxSize`, `fillMaxWidth`, `height`, `padding`, `Alignment`(无 `ListItem`)。其余不变。

- [ ] **Step 2: 替换 Card 内 ListItem 为 Row**

将 L94-106:

```kotlin
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_view_changelog)) },
                    supportingContent = { Text(stringResource(R.string.settings_view_changelog_hint)) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onOpenChangelog),
                )
            }
```

替换为:

```kotlin
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenChangelog),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_view_changelog))
                        Text(
                            text = stringResource(R.string.settings_view_changelog_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            }
```

(将 `clickable` 从 `ListItem.modifier` 移到外层 `Card.modifier`,移除 `ListItem`,内层用 `Row + Column(weight=1f)` 两行 Text + 右侧 chevron Icon,结构与 [AppearanceSection.kt](app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt) padding 模式对齐。)

- [ ] **Step 3: 验证编译**

Run: `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell --quiet`
Expected: BUILD SUCCESSFUL(若失败查看 `ListItem` 引用是否清理干净 / `Alignment` / `Row` / `Column.weight` import 是否到位)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): 查看更新日志 Card 去掉 ListItem,改 Row+clickable 与外观/更新 Card 视觉一致"
```

---

## Task 4: HomeTopBar.kt + HomeScreen.kt — 首页标题字号下调

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt:84, 88, 94`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt:373`

- [ ] **Step 1: HomeTopBar.kt — 三段 Text style `titleLarge` → `titleMedium`**

将 L82-95:

```kotlin
                    Text(
                        text = stringResource(R.string.app_name_prefix),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.app_name_bolt),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_name_suffix),
                        style = MaterialTheme.typography.titleLarge,
                    )
```

替换为(仅 `style` 改为 `titleMedium`,其余字段不变):

```kotlin
                    Text(
                        text = stringResource(R.string.app_name_prefix),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.app_name_bolt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_name_suffix),
                        style = MaterialTheme.typography.titleMedium,
                    )
```

(文字内容 / bolt 颜色 / 间距 全部保持不变。)

- [ ] **Step 2: HomeScreen.kt — HomeScreenBare 同步 `titleLarge` → `titleMedium`**

将 L373:

```kotlin
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
```

替换为:

```kotlin
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
```

(这是 `@VisibleForTesting` 的 bare 路径,无 bullet bolt 拆分。同步是为了测试视觉与生产一致。)

- [ ] **Step 3: 验证编译**

Run: `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt
git commit -m "feat(ui): 首页顶部标题字号 titleLarge→titleMedium (22sp→16sp)"
```

---

## Task 5: 现有测试 + 完整构建验证

**Files:** 无(只跑测试和构建)

- [ ] **Step 1: 跑现有单元测试(确保没破任何 contract)**

Run: `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat testDebugUnitTest -PmodelProfile=shell`
Expected: BUILD SUCCESSFUL,所有现有测试通过(无新增 / 删除任何 test)。若有失败,**先 revert 找到引入回归的 commit**,不修测试。

- [ ] **Step 2: 完整 shell profile 构建**

Run: `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat assembleDebug -PmodelProfile=shell --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 自检清单 — 4 个 commit 全部干净**

Run: `git log -4 --oneline`
Expected: 看到以下 4 条 atomic commit(无 Claude trailer,author = AlexMultiAgent):

1. `feat(strings): 新增 settings_about_org = 哈尔滨市市场监管局`
2. `feat(ui): 设置页关于区三行堆叠(冰灵⚡锐目 / 版本 / 哈尔滨市市场监管局)`
3. `feat(ui): 查看更新日志 Card 去掉 ListItem,改 Row+clickable 与外观/更新 Card 视觉一致`
4. `feat(ui): 首页顶部标题字号 titleLarge→titleMedium (22sp→16sp)`

Run: `git log -4 --format='%B' | grep -i 'Co-Authored-By'`
Expected: 空输出(无 Claude trailer)

- [ ] **Step 4: 不 commit — 本任务为验证关**

若全部通过,无需 commit。若发现需要微调(比如某个 commit 漏改文件),补一个 fix commit 而不是 amend。

---

## 自检(写完后回看 spec)

- [x] **Spec coverage**: spec §3.1 关于区三行 → Task 2;§3.2 Card 统一 → Task 3;§3.3 新字符串 → Task 1;§3.4 首页标题字号 → Task 4;§4 不动的东西 → 所有 task 守边界
- [x] **Placeholder scan**: 无 TBD / TODO / "类似 Task N" / 步骤描述无代码块。每个 Step 都给了具体代码或命令
- [x] **Type consistency**: Task 2-3 都改 `SettingsScreen.kt`,Task 3 的 import 删除 `ListItem` 与正文替换一致;Task 4 同时改 `HomeTopBar.kt` + `HomeScreen.kt` 一致
- [x] **Commit 一致性**: 4 commit 主题主题分别对应 4 个独立改动,可单独 revert

---

## 不在 plan 范围内

- 跑 `ice_ocr_rules` profile 构建(per CLAUDE.md,CI / release skill 才跑)
- Release pipeline(`/icevision-release` skill 发版 + 三段式打标)
- 真机烟测(等发版前 `icevision-release` skill 覆盖)
- 改 `R.string.app_name` 字符串内容(launcher / manifest 仍读 "冰灵锐目")
- 改 Theme.kt / Color.kt / IceSpiritTypography