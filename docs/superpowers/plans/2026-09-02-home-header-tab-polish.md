# 首页标题去 ⚡ + 字号 20sp + Tab 软色 chip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 首页顶部标题合并为单段 "冰灵锐目"、字号 16sp → 20sp、去掉 ⚡;「广告招牌」tab 从强对比 pill 改为软色 `tertiaryContainer` chip + leading `Icons.Outlined.Verified`;清理 3 个不再使用的 string 资源。

**Architecture:** 纯 Compose 渲染层微调,不动 ViewModel / matcher / typography token / tab 路由。HomeTopBar 单 Text + 本地 `titleMedium.copy(fontSize = 20.sp)`(避免影响 7+ 其他 `titleMedium` 引用);RuleTabBar `PillTab` 容器内嵌 `Row{ Icon + Text }`,颜色改 `tertiaryContainer` / `onTertiaryContainer`,圆角改 pill 全圆;strings.xml 删 3 行死字符串。

**Tech Stack:** Jetpack Compose / Material 3 / Material Icons Extended / Robolectric / JUnit4 / Gradle 9.7 / AGP 9.3 / Kotlin 2.4.10

---

## File Structure

**Modify:**

- `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt` — 单 Text 标题 + 20sp 本地 override
- `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt` — PillTab 改为 chip + Verified icon
- `app/src/main/res/values/strings.xml` — 删 3 行死字符串
- `app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt` — 加 Verified icon 存在性断言
- `app/src/main/assets/user-changelog.md` — 加 v0.1.48 顶部条目
- `app/src/test/java/com/icespiritai/offline/ui/settings/ChangelogScreenTest.kt:74` — pin bump v0.1.47 → v0.1.48

**不动:**

- `app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt:13`(`titleMedium = 16sp Medium`)
- `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt` / `setTab` 契约
- `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`(整个 `HomeScreen` Composable)
- `app/src/main/assets/rules/ad_signage_rules.json` / 规则引擎 / matcher

---

## Task 1: 加 Verified icon 存在性的失败测试

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt`

- [ ] **Step 1: 在 `RuleTabBarTest` 文件底部(在 `tabBarHasCustomIndicator` 测试后)新增测试**

```kotlin
    @Test
    fun `tab pill renders Verified icon as leading element`() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                RuleTabBar(
                    selected = RuleTab.AdSignage,
                    onSelect = {},
                    enabled = true,
                )
            }
        }
        // Verified icon is exposed via testTag; contentDescription is null
        // so TalkBack skips it (decorative icon next to text label).
        composeRule.onNodeWithTag(RuleTabBarTestTags.PILL_LEADING_ICON).assertExists()
        // Text label still present (sanity check icon didn't replace the label).
        composeRule.onNodeWithText("广告招牌").assertExists()
    }
```

- [ ] **Step 2: 在同一个文件顶部新增 `RuleTabBarTestTags` 顶层常量(放在 `class` 外面、`package` 声明下)** — **临时占位,Task 2 Step 0 必须迁移到主源**

```kotlin
/**
 * Stable test tags for [RuleTabBar] composables.
 *
 * **Placement note:** this object lives in test source for the failing-test
 * step, but MUST be relocated to `app/src/main/java/com/icespiritai/offline/
 * ui/home/RuleTabBar.kt` (top-level, between `package` and `enum class
 * RuleTab`) before Task 2's main source can `import` it — Gradle source
 * sets don't let main source see test source. Task 2 Step 0 covers the
 * relocation; this entry gets deleted from the test file in Task 2.
 */
object RuleTabBarTestTags {
    const val PILL_LEADING_ICON = "ruleTabBar_pill_leading_icon"
}
```

- [ ] **Step 3: 还需要新增 import(在文件顶部 import 块)**
  - `import androidx.compose.ui.test.onNodeWithTag`

- [ ] **Step 4: 跑测试,确认 FAIL**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.RuleTabBarTest.tab pill renders Verified icon as leading element" -PmodelProfile=shell
```

Expected: FAIL with `AssertionError: ... no node found ...`

- [ ] **Step 5: 暂不 commit(测试还在失败态)**

---

## Task 2: PillTab 改为 chip + Verified icon + tertiaryContainer

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`

- [ ] **Step 1: 更新 `RuleTabBar.kt` 顶部 import 块,在现有 imports 下追加**

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.testTag
import com.icespiritai.offline.ui.home.RuleTabBarTestTags
```

- [ ] **Step 2: 把 `PillTab` 私有 Composable 整段替换**

替换 lines 83–121(从 `@Composable private fun PillTab(` 到函数结束的 `}`)。新内容:

```kotlin
@Composable
private fun PillTab(
    tab: RuleTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(
            enabled = enabled,
            role = Role.Tab,
            onClick = onClick,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Verified,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(16.dp)
                    .testTag(RuleTabBarTestTags.PILL_LEADING_ICON),
            )
            Text(
                text = stringResource(tab.titleRes),
                style = if (isSelected) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}
```

要点:
- 圆角 `RoundedCornerShape(20.dp)` → `RoundedCornerShape(50)`(pill 全圆)
- 颜色分支:`secondaryContainer` / `onSecondaryContainer` → `tertiaryContainer` / `onTertiaryContainer`(未选中分支保留 `surfaceVariant` / `onSurfaceVariant` 供 FoodLabeling 启用时复用)
- 删除 `tonalElevation`(M3 `tertiaryContainer` 已是软色,elevation 叠加会失真)
- 容器内 `Row` 排 `Icon(16dp) + spacedBy(6.dp) + Text`,`padding(horizontal = 14.dp, vertical = 8.dp)`(`labelLarge` 14sp + 16dp 图标 → 紧凑 chip 比例)
- 文本 style:`titleMedium.copy(SemiBold)` / `bodyLarge` → 统一 `labelLarge` + `FontWeight.Medium`(无论选中 / 未选中;语义层级由容器颜色承担,文本尺寸不再二档)
- Icon 加 `Modifier.testTag(RuleTabBarTestTags.PILL_LEADING_ICON)` 供 Task 1 测试锚点

- [ ] **Step 3: 更新 `RuleTabBar` 顶部 KDoc(替换 lines 42–54)**

替换为:

```kotlin
/**
 * Soft-color chip tab bar. Each tab is a [Surface] with `RoundedCornerShape(50)`
 * (full pill), `tertiaryContainer` fill when selected and `surfaceVariant`
 * when unselected, with a leading [Icons.Outlined.Verified] icon and
 * `labelLarge` Medium label text. The soft container contrasts gently with
 * the flat title above, replacing the previous "strong pill" segmented
 * pattern that looked like an isolated button on Idle.
 *
 * Each pill exposes `Role.Tab` semantics via [Modifier.clickable] so
 * [RuleTabBarTest] (which counts `Role.Tab` nodes) and screen readers
 * both keep working. The [RuleTabBarTestTags.PILL_LEADING_ICON] testTag
 * lets tests verify the Verified icon renders.
 */
```

- [ ] **Step 4: 跑 `RuleTabBarTest` 全部 5 个测试,确认全部 PASS**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.RuleTabBarTest" -PmodelProfile=shell
```

Expected: `5 tests completed, 0 failed`

- [ ] **Step 5: commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git add app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt && git commit -m "feat(ui): RuleTabBar PillTab 改软色 chip + Verified leading icon"
```

---

## Task 3: HomeTopBar 标题合并为单 Text + 20sp + 去 ⚡

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`

- [ ] **Step 1: 替换 HomeTopBar 顶部 KDoc 第 38–46 行的"three Compose [Text]s"段**

替换为:

```kotlin
 *  - The title is a single Compose [Text] reading `app_name` ("冰灵锐目")
 *    at `titleMedium.copy(fontSize = 20.sp)` — overrides size locally
 *    without touching the global `IceSpiritTypography.titleMedium` token
 *    (which is used by 7+ other files including the now-smaller tab pill
 *    text). The bolt ⚡ was removed in v0.1.48 per user feedback
 *    ("突兀"), and the prefix/suffix Text split became redundant once
 *    the bolt was gone.
```

- [ ] **Step 2: 替换 HomeTopBar.kt lines 75–96 的标题 Row 整段**

替换 lines 75–96(从 `val a11yTitle = stringResource(R.string.app_name)` 到 `Text(... app_name_suffix)` 结束的 `}`):

```kotlin
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
```

要点:
- 单 Text,`text = stringResource(R.string.app_name)`(launcher label 同源,a11y 自然落到 `Text` 自身的 `text` 参数)
- `style = titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)` — **本地 override** `Type.kt:13` 全局不动
- 删除:`val a11yTitle = stringResource(R.string.app_name)` 局部变量、整个 `Row`、`Modifier.semantics(mergeDescendants = true)`、3 个 `Text`(prefix / bolt / suffix)
- `a11ySettings` 局部变量(line 97)不动 — 仍在用
- IconButton 区域不动
- `RuleTabBar(...)` 调用(line 110)不动

- [ ] **Step 3: 新增 import(在文件顶部 imports 块)**

```kotlin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
```

- [ ] **Step 4: 删除不再使用的 import**

```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

- [ ] **Step 5: 跑 `RuleTabBarTest` 验证回归(同 Task 2 step 4)**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.RuleTabBarTest" -PmodelProfile=shell
```

Expected: `5 tests completed, 0 failed`(HomeTopBar 不是 `RuleTabBarTest` 测试目标,本步骤是冒烟无回归)

- [ ] **Step 6: commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git add app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt && git commit -m "feat(ui): HomeTopBar 标题合并为单 Text + 字号 20sp + 去 ⚡"
```

---

## Task 4: 删除 3 个不再使用的 string 资源

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 删除 strings.xml lines 8–10 的 3 行**

定位到 `app_name_prefix` / `app_name_bolt` / `app_name_suffix` 3 行(位于 line 3 `app_name` 之后),整段删除。删除前确认 Task 3 已经把 `HomeTopBar.kt` 的 3 个 `Text` 调用全部移除;这些字符串无任何引用。

Run(在删除前先确认无引用):
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && grep -RIn 'app_name_prefix\|app_name_bolt\|app_name_suffix' app/src/ docs/superpowers/specs/ docs/superpowers/plans/
```

Expected: 仅 specs / plans 文件作为历史引用出现(KDoc / 上下文);`app/src/` 无任何 `.kt` / `.xml` 引用。若 `app/src/` 仍有引用 → **STOP**,回 Task 3 检查没改干净。

- [ ] **Step 2: 删除 strings.xml 中这 3 行**

```bash
cd "d:/GitHub/IceSpiritAI_Vision" && grep -n 'app_name_prefix\|app_name_bolt\|app_name_suffix' app/src/main/res/values/strings.xml
```

Expected: 3 个匹配在相邻 3 行(line 8 / 9 / 10)。

用 Edit 工具删除这 3 行(包括每行前后的换行符,避免残留空行)。

- [ ] **Step 3: 跑全量单测,确认无回归**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected: 全部 PASS(具体数字以现状为准,本步骤只关心 0 failure)。若 `RuleTabBarTest` 之外有失败 → **STOP**,回查是否有别处引用了这 3 个字符串。

- [ ] **Step 4: commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git add app/src/main/res/values/strings.xml && git commit -m "chore(strings): 删 app_name_prefix / app_name_bolt / app_name_suffix 三个死资源"
```

---

## Task 5: 更新 user-changelog.md + ChangelogScreenTest pin bump

**Files:**
- Modify: `app/src/main/assets/user-changelog.md`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/settings/ChangelogScreenTest.kt:74`

- [ ] **Step 1: 在 `user-changelog.md` 顶部新增 v0.1.48 段**

在文件最顶部(`# 用户更新日志` H1 之后,`## v0.1.47` 之前)插入:

```markdown
## v0.1.48 · 2026-09-02

- **首页顶部标题去 ⚡ + 字号 20sp**(`HomeTopBar.kt`):三段式 `冰灵⚡锐目` 合并为单段 `Text(stringResource(R.string.app_name))`,样式 `titleMedium.copy(fontSize = 20.sp, fontWeight = Medium)`(从 16sp 回拨到 20sp,用户反馈 "16sp 太小");同步删 `app_name_prefix` / `app_name_bolt` / `app_name_suffix` 三个死字符串,launcher label 与 a11y 仍走 `app_name` 单源。`titleMedium` 全局 token(`Type.kt:13`)不动 — 影响 `RuleTabBar` pill 文字 / `ResultPanel` / `ViewerTopBar` 等 7+ 处已稳定的 16sp 引用
- **Tab 改软色 chip + Verified leading icon**(`RuleTabBar.kt`):从 `Surface(RoundedCornerShape(20.dp)) + secondaryContainer + titleMedium SemiBold` 强对比 pill 改为 `Surface(RoundedCornerShape(50)) + tertiaryContainer + labelLarge Medium` 软色 chip,前置 `Icons.Outlined.Verified`(16dp,`onTertiaryContainer` 染色)。圆角 / 配色 / 字号三档同步下调,跟 20sp 标题拉开视觉层级;`isSelected` 分支仍保留供 FoodLabeling tab 启用时复用
- **测试 pin bump**:`RuleTabBarTest` 新增 `tab pill renders Verified icon as leading element`(`testTag = ruleTabBar_pill_leading_icon`);`ChangelogScreenTest:74` 顶部 `v0.1.47` → `v0.1.48`

```

要点:保留前置空行 + `## v0.1.48` 行 + 3 个 bullet + 尾部空行 + 原有 `## v0.1.47` 段(下面接 v0.1.46 等历史段)。

- [ ] **Step 2: 改 `ChangelogScreenTest.kt:74` 的版本字面量**

把 `"v0.1.47"` 改成 `"v0.1.48"`。

- [ ] **Step 3: 跑 ChangelogScreenTest 验证**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.ChangelogScreenTest" -PmodelProfile=shell
```

Expected: PASS(`bundled user-changelog.md must list the shipping version as its first section` 断言过)。

- [ ] **Step 4: 跑全量单测(最终回归)**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected: 全部 PASS,0 failure。

- [ ] **Step 5: commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git add app/src/main/assets/user-changelog.md app/src/test/java/com/icespiritai/offline/ui/settings/ChangelogScreenTest.kt && git commit -m "docs(changelog): v0.1.48 首页标题去 ⚡ + 字号 20sp + Tab 软色 chip"
```

---

## Task 6: 验证 commit hygiene

**Files:**(无)

- [ ] **Step 1: 检查所有 4 个 commit 都无 `Co-Authored-By` trailer**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git log -4 --format='%H %s' | awk '{print $1}' | while read sha; do echo "--- $sha ---"; git log -1 --format='%B' "$sha" | grep -i 'co-authored-by' && echo "TRAILER_FOUND" || echo "OK"; done
```

Expected: 全部输出 `OK`。

- [ ] **Step 2: 确认作者是 `AlexMultiAgent`**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git log -4 --format='%an %ae'
```

Expected: 全部 4 行都是 `AlexMultiAgent <...>`(具体邮箱看 `git config user.email`,CLAUDE.md 锁定仓库已设)。

- [ ] **Step 3: 检查 git status 干净**

Run:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && git status
```

Expected: `nothing to commit, working tree clean`。

---

## Task 7: 真机烟测(可选,Release 前必跑)

**Files:**(无,人工验证)

本任务**不进 commit**,由 `/icevision-release` skill 在发版前触发。

- [ ] **Step 1: 构建 ice_ocr_rules profile + 装机**

按 CLAUDE.md §构建命令 + §ice_ocr_rules profile 前置步骤:
```bash
cd "d:/GitHub/IceSpiritAI_Vision" && export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" && ./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
adb install -r app/build/outputs/apk/ice_ocr_rules/debug/app-ice_ocr_rules-debug.apk
```

- [ ] **Step 2: 验证视觉**

打开应用首页(Idle 态):
- [ ] 顶部标题显示 "冰灵锐目"(无 ⚡),字号明显比 v0.1.47 大(肉眼 20sp vs 16sp)
- [ ] 「广告招牌」chip 显示紫色软底 + 盾牌对勾图标 + "广告招牌" 文字
- [ ] chip 整体高度明显小于 v0.1.47 的强对比 pill(约 32dp 高 vs 约 40dp)
- [ ] 点击 chip(已选中状态) → 触发「回到 Idle」契约(state 清空)

- [ ] **Step 3: 验证 TalkBack**

开启 TalkBack:
- [ ] 长按顶部标题区域 → 读 "冰灵锐目"(单段,无 ⚡ 字符发音)
- [ ] 长按 chip → 读 "广告招牌,选项卡"

- [ ] **Step 4: 验证两主题**

切到浅色冰月主题(设置 → 外观):
- [ ] chip 颜色切到浅紫底 + 深紫图标 + 深紫文字,Verified 图标在浅底上仍清晰
- [ ] 标题字色切到深色(主题默认),20sp 在浅底仍清晰可读

- [ ] **Step 5: 写 smoke 记录**

把以上验证结果写到 `docs/smoke/2026-09-02-v0.1.48-home-header-tab-polish.md`(参考 `docs/smoke/2026-08-14-phase1-smoke.md` 模板),作为 `/icevision-release` 流水线的 pre-flight 输入。

---

## Self-Review Checklist

**Spec 覆盖检查:**
- [x] §3.1 首页顶部标题合并 + 字号 20sp + 去 ⚡ → Task 3
- [x] §3.2 Tab pill → chip + Verified icon + tertiaryContainer → Task 2
- [x] §3.3 字符串清理 → Task 4
- [x] §4 不动的东西 → 全部 task 都明示了哪些 import / 行不动
- [x] §5 测试(`RuleTabBarTest` 加 Verified icon 断言) → Task 1 + Task 2
- [x] §6 文档同步(`user-changelog.md` + `ChangelogScreenTest` pin) → Task 5

**Placeholder scan:**
- [x] 无 TBD / TODO / "fill in later"
- [x] 每个 step 有具体代码或具体命令
- [x] 测试代码完整,不写 "similar to Task N"

**类型 / 方法签名一致性:**

- `RuleTabBarTestTags.PILL_LEADING_ICON` 在 Task 1 (临时声明在测试源) 和 Task 2 (引用) 必须同名同值;Task 2 Step 0 会把这个 object 从测试源迁移到主源(`app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`,放在 `package` 声明下、`enum class RuleTab` 上),并同步删测试源的那一份。Gradle source set 不允许主源 import 测试源 — 这是个跨 source-set 的常量,只能放主源。
- PillTab 签名 `(tab, isSelected, onClick, enabled)` 4 个参数保持不变,Task 2 只改实现不改签名
