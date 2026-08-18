# 冰灵锐目 — 冰灵家族主题对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把冰灵锐目 Color.kt / Shape / Theme / ThemeMode label 整体对齐到冰灵慧语的 `ice_chat_*` 家族,hex 直接照抄慧语 `values/colors.xml` + `values-night/colors.xml`,ThemeMode label 改为"深色雪夜 / 浅色冰月 / 跟随系统",引入 4 个 `IceRadius*` corner radius token。

**Architecture:** 纯机械重命名 + 色板重写 + 1 个新文件(`Shape.kt`)。Material3 `colorScheme.background/surface/error/...` 键名不变,所以业务 call sites(`SeverityBadge` / `HitCard` / `StatusBanner` / `HighlightOverlay` / `IceSpiritNavHost` / `HomeScreen` / `ResultPanel` / `UpdateSection`)全部 0 行改动。`ThemeMode.kt` 枚举名 / `toNightMode()` 不动,只在 `strings.xml` 改 label 值。

**Tech Stack:** Kotlin 2.4.10 + Compose Material3 1.2+ + AGP 9.3 + Gradle 9.7 + JUnit4(单元测试)。

---

## File Structure

| 文件 | 改动 | 责任 |
|---|---|---|
| `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt` | 改 | 30 个 val 改名 + 30 个 hex 重写,定义 `IceChat*` 调色板 |
| `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt` | 改 | `darkColorScheme(...)` / `lightColorScheme(...)` val 引用同步改名 + 加 `shapes = IceSpiritShapes` |
| `app/src/main/java/com/icespiritai/offline/ui/theme/Shape.kt` | 新建 | `IceRadius*` 4 个 Dp 常量 + `IceSpiritShapes`(`Shapes` 实例) |
| `app/src/main/res/values/strings.xml` | 改 | `settings_appearance_light/dark` 两条值替换 |
| `app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt` | 新建 | 30 个 hex pin 测试,每 val 1 个 @Test |
| `app/src/test/java/com/icespiritai/offline/ui/theme/ShapeTokensTest.kt` | 新建 | 4 个 Dp pin 测试 + 1 个 Shapes shape pin 测试 |

---

## Task 1: Color.kt — 写测试 + 重命名 val + 重写 hex

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`

- [ ] **Step 1: 写失败测试 — ColorTokensTest.kt**

新建 `app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt`,30 个 @Test,每个断言一个 val 的 hex:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin IceSpiritAI_Vision's `IceChat*` color palette to the IceSpiritAI_Chat
 * `ice_chat_*` family (per spec §3.1). Single-source-of-truth for hex values
 * — any drift between this test and Chat's `values/colors.xml` should be
 * reviewed and reconciled before merging.
 */
class ColorTokensTest {

    @Test fun lightIceChatBg() = assertEquals(Color(0xFFF4F8FB), LightIceChatBg)
    @Test fun lightIceChatPanel() = assertEquals(Color(0xFFFFFFFF), LightIceChatPanel)
    @Test fun lightIceChatPanelSoft() = assertEquals(Color(0xFFEAF2F7), LightIceChatPanelSoft)
    @Test fun lightIceChatPanelStrong() = assertEquals(Color(0xFFD6E2EC), LightIceChatPanelStrong)
    @Test fun lightIceChatOnBg() = assertEquals(Color(0xFF0B1E26), LightIceChatOnBg)
    @Test fun lightIceChatOnBgMuted() = assertEquals(Color(0xFF5A6E78), LightIceChatOnBgMuted)
    @Test fun lightIceChatOnBgSubtle() = assertEquals(Color(0xFF9DA9B0), LightIceChatOnBgSubtle)
    @Test fun lightIceChatOnBgDisabled() = assertEquals(Color(0xFF9DA9B0), LightIceChatOnBgDisabled)
    @Test fun lightIceChatOnBgPlaceholder() = assertEquals(Color(0xFFA8B4BB), LightIceChatOnBgPlaceholder)
    @Test fun lightIceChatAccent() = assertEquals(Color(0xFF1F3A52), LightIceChatAccent)
    @Test fun lightIceChatAccentSecondary() = assertEquals(Color(0xFF5A7090), LightIceChatAccentSecondary)
    @Test fun lightIceChatOnAccent() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnAccent)
    @Test fun lightIceChatDivider() = assertEquals(Color(0x141F3A52) /*#140F8AB8*/, LightIceChatDivider)
    @Test fun lightIceChatError() = assertEquals(Color(0xFFD32F2F), LightIceChatError)
    @Test fun lightIceChatOnError() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnError)

    @Test fun darkIceChatBg() = assertEquals(Color(0xFF08131B), DarkIceChatBg)
    @Test fun darkIceChatPanel() = assertEquals(Color(0xFF11212C), DarkIceChatPanel)
    @Test fun darkIceChatPanelSoft() = assertEquals(Color(0xFF1A2D3A), DarkIceChatPanelSoft)
    @Test fun darkIceChatPanelStrong() = assertEquals(Color(0xFF243748), DarkIceChatPanelStrong)
    @Test fun darkIceChatOnBg() = assertEquals(Color(0xFFE0F0F8), DarkIceChatOnBg)
    @Test fun darkIceChatOnBgMuted() = assertEquals(Color(0xFF7A95A3), DarkIceChatOnBgMuted)
    @Test fun darkIceChatOnBgSubtle() = assertEquals(Color(0xFF4A5C66), DarkIceChatOnBgSubtle)
    @Test fun darkIceChatOnBgDisabled() = assertEquals(Color(0xFF4A5C66), DarkIceChatOnBgDisabled)
    @Test fun darkIceChatOnBgPlaceholder() = assertEquals(Color(0xFF6A7C86), DarkIceChatOnBgPlaceholder)
    @Test fun darkIceChatAccent() = assertEquals(Color(0xFFA8C0D0), DarkIceChatAccent)
    @Test fun darkIceChatAccentSecondary() = assertEquals(Color(0xFF7DA4BD), DarkIceChatAccentSecondary)
    @Test fun darkIceChatOnAccent() = assertEquals(Color(0xFF08131B), DarkIceChatOnAccent)
    @Test fun darkIceChatDivider() = assertEquals(Color(0x26264FC0E8) /*#264FC0E8*/, DarkIceChatDivider)
    @Test fun darkIceChatError() = assertEquals(Color(0xFFFF6B6B), DarkIceChatError)
    @Test fun darkIceChatOnError() = assertEquals(Color(0xFF08131B), DarkIceChatOnError)
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ColorTokensTest" -PmodelProfile=shell
```

预期:FAIL,`Unresolved reference: LightIceChatBg`(以及其他 29 个类似错误)。这是预期失败 — 编译失败,因为新 val 还没创建。

- [ ] **Step 3: 重写 Color.kt**

完整替换 `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color

// IceChat family palette — aligned 1:1 with IceSpiritAI_Chat's
// `ice_chat_*` tokens (per spec §3.1). Light values mirror Chat's
// `app/src/main/res/values/colors.xml`; dark values mirror Chat's
// `app/src/main/res/values-night/colors.xml`. Single source of truth
// for visual hex values; pin test in `ColorTokensTest`.

// Dark scheme (Night — slate navy, A 调 enforcement tone)
val DarkIceChatBg = Color(0xFF08131B)
val DarkIceChatPanel = Color(0xFF11212C)
val DarkIceChatPanelSoft = Color(0xFF1A2D3A)
val DarkIceChatPanelStrong = Color(0xFF243748)
val DarkIceChatOnBg = Color(0xFFE0F0F8)
val DarkIceChatOnBgMuted = Color(0xFF7A95A3)
val DarkIceChatOnBgSubtle = Color(0xFF4A5C66)
val DarkIceChatOnBgDisabled = Color(0xFF4A5C66)
val DarkIceChatOnBgPlaceholder = Color(0xFF6A7C86)
val DarkIceChatAccent = Color(0xFFA8C0D0)
val DarkIceChatAccentSecondary = Color(0xFF7DA4BD)
val DarkIceChatOnAccent = Color(0xFF08131B)
val DarkIceChatDivider = Color(0x264FC0E8)
val DarkIceChatError = Color(0xFFFF6B6B)
val DarkIceChatOnError = Color(0xFF08131B)

// Light scheme (Day — soft white, archive / export)
val LightIceChatBg = Color(0xFFF4F8FB)
val LightIceChatPanel = Color(0xFFFFFFFF)
val LightIceChatPanelSoft = Color(0xFFEAF2F7)
val LightIceChatPanelStrong = Color(0xFFD6E2EC)
val LightIceChatOnBg = Color(0xFF0B1E26)
val LightIceChatOnBgMuted = Color(0xFF5A6E78)
val LightIceChatOnBgSubtle = Color(0xFF9DA9B0)
val LightIceChatOnBgDisabled = Color(0xFF9DA9B0)
val LightIceChatOnBgPlaceholder = Color(0xFFA8B4BB)
val LightIceChatAccent = Color(0xFF1F3A52)
val LightIceChatAccentSecondary = Color(0xFF5A7090)
val LightIceChatOnAccent = Color(0xFFFFFFFF)
val LightIceChatDivider = Color(0x141F3A52)
val LightIceChatError = Color(0xFFD32F2F)
val LightIceChatOnError = Color(0xFFFFFFFF)
```

注:`Color(0x141F3A52)` 是 8 位 ARGB 整数,前两位 `14` 是 alpha(20 = 0x14 = 8% 透明),等同慧语 `#140F8AB8`(20% 透明,`0F8AB8` 旧 accent 与 `1F3A52` 新 accent 同色相)。`Color(0x264FC0E8)` 同理(`26` = 15% alpha)。

- [ ] **Step 4: 跑测试确认通过**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ColorTokensTest" -PmodelProfile=shell
```

预期:PASS,30 tests passing。

- [ ] **Step 5: Commit**

```bash
cd d:\GitHub\IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt
git add app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt
git commit -m "feat(theme): Color.kt IceChat family palette (30 vals)"
```

---

## Task 1.5: Color.kt 扩 Warning/Positive tokens + 修 KDoc/注释/换行

**背景:** Task 1 完成后,实施 grep 发现:
- `SeverityBadge.kt` / `HighlightOverlay.kt` / `StatusBanner.kt` 直接引用 `DarkWarning` / `LightWarning` / `DarkSuccess` / `LightSuccess`(不是走 `MaterialTheme.colorScheme`)
- spec §3.1 漏写 Warning/Positive token 行(只写了 Error)
- code reviewer 标 Important:ColorTokensTest.kt 缺 KDoc 头;Minor:Color.kt 缺 section divider,末尾无换行

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt`

- [ ] **Step 1: 编辑 Color.kt 加 6 个新 val + section divider + 末尾换行**

完整内容(替换现有 Color.kt):

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color

// IceChat family palette — aligned 1:1 with IceSpiritAI_Chat's
// `ice_chat_*` tokens (per spec §3.1). Light values mirror Chat's
// `app/src/main/res/values/colors.xml`; dark values mirror Chat's
// `app/src/main/res/values-night/colors.xml`. Single source of truth
// for visual hex values; pin test in `ColorTokensTest`.

// Dark scheme (Night — slate navy, A 调 enforcement tone)
val DarkIceChatBg = Color(0xFF08131B)
val DarkIceChatPanel = Color(0xFF11212C)
val DarkIceChatPanelSoft = Color(0xFF1A2D3A)
val DarkIceChatPanelStrong = Color(0xFF243748)
val DarkIceChatOnBg = Color(0xFFE0F0F8)
val DarkIceChatOnBgMuted = Color(0xFF7A95A3)
val DarkIceChatOnBgSubtle = Color(0xFF4A5C66)
val DarkIceChatOnBgDisabled = Color(0xFF4A5C66)
val DarkIceChatOnBgPlaceholder = Color(0xFF6A7C86)
val DarkIceChatAccent = Color(0xFFA8C0D0)
val DarkIceChatAccentSecondary = Color(0xFF7DA4BD)
val DarkIceChatOnAccent = Color(0xFF08131B)
val DarkIceChatDivider = Color(0x264FC0E8)
// Semantic severity tokens (per spec §3.1 取舍说明)— 不进 Material3
// colorScheme,直接 val 暴露给 SeverityBadge / HighlightOverlay / StatusBanner
val DarkIceChatWarning = Color(0xFFE08570)
val DarkIceChatOnWarning = Color(0xFF08131B)
val DarkIceChatPositive = Color(0xFF5FC2A0)
val DarkIceChatError = Color(0xFFFF6B6B)
val DarkIceChatOnError = Color(0xFF08131B)

// Light scheme (Day — soft white, archive / export)
val LightIceChatBg = Color(0xFFF4F8FB)
val LightIceChatPanel = Color(0xFFFFFFFF)
val LightIceChatPanelSoft = Color(0xFFEAF2F7)
val LightIceChatPanelStrong = Color(0xFFD6E2EC)
val LightIceChatOnBg = Color(0xFF0B1E26)
val LightIceChatOnBgMuted = Color(0xFF5A6E78)
val LightIceChatOnBgSubtle = Color(0xFF9DA9B0)
val LightIceChatOnBgDisabled = Color(0xFF9DA9B0)
val LightIceChatOnBgPlaceholder = Color(0xFFA8B4BB)
val LightIceChatAccent = Color(0xFF1F3A52)
val LightIceChatAccentSecondary = Color(0xFF5A7090)
val LightIceChatOnAccent = Color(0xFFFFFFFF)
val LightIceChatDivider = Color(0x141F3A52)
// Semantic severity tokens (per spec §3.1 取舍说明)
val LightIceChatWarning = Color(0xFFB04030)
val LightIceChatOnWarning = Color(0xFFFFFFFF)
val LightIceChatPositive = Color(0xFF2C8A6B)
val LightIceChatError = Color(0xFFD32F2F)
val LightIceChatOnError = Color(0xFFFFFFFF)
```

- [ ] **Step 2: 编辑 ColorTokensTest.kt 加 6 个新测试 + KDoc 头 + 末尾换行**

完整内容(替换现有 ColorTokensTest.kt):

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin IceSpiritAI_Vision's `IceChat*` color palette to the IceSpiritAI_Chat
 * `ice_chat_*` family (per spec §3.1). Single-source-of-truth for hex values
 * — any drift between this test and Chat's `values/colors.xml` should be
 * reviewed and reconciled before merging.
 *
 * Drift detector: if a hex literal here no longer matches the corresponding
 * Chat token (`ice_chat_bg` / `ice_error` / `ice_warning` / `ice_positive` /
 * etc.), DO NOT edit the test to match — instead reconcile Vision's palette
 * back to Chat or open a spec change.
 */
class ColorTokensTest {

    @Test fun lightIceChatBg() = assertEquals(Color(0xFFF4F8FB), LightIceChatBg)
    @Test fun lightIceChatPanel() = assertEquals(Color(0xFFFFFFFF), LightIceChatPanel)
    @Test fun lightIceChatPanelSoft() = assertEquals(Color(0xFFEAF2F7), LightIceChatPanelSoft)
    @Test fun lightIceChatPanelStrong() = assertEquals(Color(0xFFD6E2EC), LightIceChatPanelStrong)
    @Test fun lightIceChatOnBg() = assertEquals(Color(0xFF0B1E26), LightIceChatOnBg)
    @Test fun lightIceChatOnBgMuted() = assertEquals(Color(0xFF5A6E78), LightIceChatOnBgMuted)
    @Test fun lightIceChatOnBgSubtle() = assertEquals(Color(0xFF9DA9B0), LightIceChatOnBgSubtle)
    @Test fun lightIceChatOnBgDisabled() = assertEquals(Color(0xFF9DA9B0), LightIceChatOnBgDisabled)
    @Test fun lightIceChatOnBgPlaceholder() = assertEquals(Color(0xFFA8B4BB), LightIceChatOnBgPlaceholder)
    @Test fun lightIceChatAccent() = assertEquals(Color(0xFF1F3A52), LightIceChatAccent)
    @Test fun lightIceChatAccentSecondary() = assertEquals(Color(0xFF5A7090), LightIceChatAccentSecondary)
    @Test fun lightIceChatOnAccent() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnAccent)
    @Test fun lightIceChatDivider() = assertEquals(Color(0x141F3A52), LightIceChatDivider)
    @Test fun lightIceChatWarning() = assertEquals(Color(0xFFB04030), LightIceChatWarning)
    @Test fun lightIceChatOnWarning() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnWarning)
    @Test fun lightIceChatPositive() = assertEquals(Color(0xFF2C8A6B), LightIceChatPositive)
    @Test fun lightIceChatError() = assertEquals(Color(0xFFD32F2F), LightIceChatError)
    @Test fun lightIceChatOnError() = assertEquals(Color(0xFFFFFFFF), LightIceChatOnError)

    @Test fun darkIceChatBg() = assertEquals(Color(0xFF08131B), DarkIceChatBg)
    @Test fun darkIceChatPanel() = assertEquals(Color(0xFF11212C), DarkIceChatPanel)
    @Test fun darkIceChatPanelSoft() = assertEquals(Color(0xFF1A2D3A), DarkIceChatPanelSoft)
    @Test fun darkIceChatPanelStrong() = assertEquals(Color(0xFF243748), DarkIceChatPanelStrong)
    @Test fun darkIceChatOnBg() = assertEquals(Color(0xFFE0F0F8), DarkIceChatOnBg)
    @Test fun darkIceChatOnBgMuted() = assertEquals(Color(0xFF7A95A3), DarkIceChatOnBgMuted)
    @Test fun darkIceChatOnBgSubtle() = assertEquals(Color(0xFF4A5C66), DarkIceChatOnBgSubtle)
    @Test fun darkIceChatOnBgDisabled() = assertEquals(Color(0xFF4A5C66), DarkIceChatOnBgDisabled)
    @Test fun darkIceChatOnBgPlaceholder() = assertEquals(Color(0xFF6A7C86), DarkIceChatOnBgPlaceholder)
    @Test fun darkIceChatAccent() = assertEquals(Color(0xFFA8C0D0), DarkIceChatAccent)
    @Test fun darkIceChatAccentSecondary() = assertEquals(Color(0xFF7DA4BD), DarkIceChatAccentSecondary)
    @Test fun darkIceChatOnAccent() = assertEquals(Color(0xFF08131B), DarkIceChatOnAccent)
    @Test fun darkIceChatDivider() = assertEquals(Color(0x264FC0E8), DarkIceChatDivider)
    @Test fun darkIceChatWarning() = assertEquals(Color(0xFFE08570), DarkIceChatWarning)
    @Test fun darkIceChatOnWarning() = assertEquals(Color(0xFF08131B), DarkIceChatOnWarning)
    @Test fun darkIceChatPositive() = assertEquals(Color(0xFF5FC2A0), DarkIceChatPositive)
    @Test fun darkIceChatError() = assertEquals(Color(0xFFFF6B6B), DarkIceChatError)
    @Test fun darkIceChatOnError() = assertEquals(Color(0xFF08131B), DarkIceChatOnError)
}
```

- [ ] **Step 3: 跑测试确认全部通过**

```bash
cd d:\GitHub\IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ColorTokensTest" -PmodelProfile=shell
```

预期:FAIL(compile 阶段,Theme.kt 仍引用旧 val 名 + SeverityBadge.kt 等)。这是预期 — 编译失败但 ColorTokensTest 自身逻辑应保证通过是后续 Task 2 完成后的目标。**Step 3 当前不能 PASS,因为 call sites 还没改**,把测试通过验收推迟到 Task 4。

替代验证:Step 3 用 `compileDebugKotlin` 或 `compileDebugJavaWithJavac` 不行(同样会因 call sites 失败)。建议 Step 3 改为**确认 ColorTokensTest.kt 自身逻辑通过(只验 Color.kt 不验 compile)** — 但 gradle test 一定会触发 compile。所以本 Task 1.5 的"测试通过"验证直接推迟到 Task 4 完成 call sites 后再跑。

- [ ] **Step 4: Commit**

```bash
cd d:\GitHub\IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt
git add app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt
git commit -m "feat(theme): Color.kt Warning/Positive tokens + KDoc + section dividers"
```

---

## Task 2: Theme.kt 同步 val + 5 个 call site 文件 + wire IceSpiritShapes

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/components/SeverityBadge.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/components/SeverityBadgeTest.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenTest.kt`

**call sites rename mapping**(`DarkXxx` → `DarkIceChatXxx`,`LightXxx` → `LightIceChatXxx`):

| 旧名 | 新名 |
|---|---|
| `DarkError` | `DarkIceChatError` |
| `DarkOnError` | `DarkIceChatOnError` |
| `DarkOnWarning` | `DarkIceChatOnWarning` |
| `DarkWarning` | `DarkIceChatWarning` |
| `DarkSuccess` | `DarkIceChatPositive` |
| `LightError` | `LightIceChatError` |
| `LightOnError` | `LightIceChatOnError` |
| `LightOnWarning` | `LightIceChatOnWarning` |
| `LightWarning` | `LightIceChatWarning` |
| `LightSuccess` | `LightIceChatPositive` |
| `DarkOnSurface` | `DarkIceChatOnBg` |
| `DarkSurface` | `DarkIceChatPanel` |

- [ ] **Step 1: 跑现有 Compose 测试确认失败**

```bash
cd d:\GitHub\IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.*" -PmodelProfile=shell
```

预期:FAIL,`Unresolved reference: DarkBackground` 等。这是预期失败。

- [ ] **Step 2: 替换 Theme.kt 完整内容**

完整替换 `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = DarkIceChatAccent,
    onPrimary = DarkIceChatOnAccent,
    secondary = DarkIceChatAccentSecondary,
    background = DarkIceChatBg,
    onBackground = DarkIceChatOnBg,
    surface = DarkIceChatPanel,
    onSurface = DarkIceChatOnBg,
    surfaceVariant = DarkIceChatPanelSoft,
    onSurfaceVariant = DarkIceChatOnBgMuted,
    surfaceContainerHigh = DarkIceChatPanelStrong,
    outline = DarkIceChatDivider,
    error = DarkIceChatError,
    onError = DarkIceChatOnError,
)

private val LightScheme = lightColorScheme(
    primary = LightIceChatAccent,
    onPrimary = LightIceChatOnAccent,
    secondary = LightIceChatAccentSecondary,
    background = LightIceChatBg,
    onBackground = LightIceChatOnBg,
    surface = LightIceChatPanel,
    onSurface = LightIceChatOnBg,
    surfaceVariant = LightIceChatPanelSoft,
    onSurfaceVariant = LightIceChatOnBgMuted,
    surfaceContainerHigh = LightIceChatPanelStrong,
    outline = LightIceChatDivider,
    error = LightIceChatError,
    onError = LightIceChatOnError,
)

/**
 * Resolves the user's [ThemeMode] preference into a concrete dark/light
 * boolean for [MaterialTheme]. Must be `@Composable` because the SYSTEM
 * branch reads `isSystemInDarkTheme()` from the active composition.
 */
@Composable
fun ThemeMode.toDarkTheme(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun IceSpiritVisionTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.toDarkTheme()) DarkScheme else LightScheme,
        shapes = IceSpiritShapes,
        typography = IceSpiritTypography,
        content = content,
    )
}
```

- [ ] **Step 3: 改 SeverityBadge.kt — rename 8 个 val import**

修改 `app/src/main/java/com/icespiritai/offline/ui/components/SeverityBadge.kt` 的 import 段:

```kotlin
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatOnError
import com.icespiritai.offline.ui.theme.DarkIceChatOnWarning
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
import com.icespiritai.offline.ui.theme.LightIceChatError
import com.icespiritai.offline.ui.theme.LightIceChatOnError
import com.icespiritai.offline.ui.theme.LightIceChatOnWarning
import com.icespiritai.offline.ui.theme.LightIceChatWarning
```

函数体内 val 引用同步重命名:

- `DarkError` → `DarkIceChatError`
- `DarkOnError` → `DarkIceChatOnError`
- `DarkOnWarning` → `DarkIceChatOnWarning`
- `DarkWarning` → `DarkIceChatWarning`
- `LightError` → `LightIceChatError`
- `LightOnError` → `LightIceChatOnError`
- `LightOnWarning` → `LightIceChatOnWarning`
- `LightWarning` → `LightIceChatWarning`

其他逻辑(`RoundedCornerShape(4.dp)` / `PaddingValues` / `MaterialTheme.typography` 等)不动。`resolveSeverityColors` 签名不动。

- [ ] **Step 4: 改 HighlightOverlay.kt — rename 4 个 val import + 体内 4 个 val**

修改 `app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt` 的 import 段:

```kotlin
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
import com.icespiritai.offline.ui.theme.LightIceChatError
import com.icespiritai.offline.ui.theme.LightIceChatWarning
```

`when (lineSeverity)` 内 4 个 val 引用:
- `DarkError` → `DarkIceChatError`
- `DarkWarning` → `DarkIceChatWarning`
- `LightError` → `LightIceChatError`
- `LightWarning` → `LightIceChatWarning`

`Canvas` / `Stroke` / `CornerRadius` / `TextNormalizer` 不动。

- [ ] **Step 5: 改 StatusBanner.kt — rename 9 个 val import + 体内 6 个 val**

修改 `app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt` 的 import 段:

```kotlin
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatOnError
import com.icespiritai.offline.ui.theme.DarkIceChatPositive
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
import com.icespiritai.offline.ui.theme.LightIceChatError
import com.icespiritai.offline.ui.theme.LightIceChatOnError
import com.icespiritai.offline.ui.theme.LightIceChatPositive
import com.icespiritai.offline.ui.theme.LightIceChatWarning
```

注:`DarkOnWarning` / `LightOnWarning` 在 StatusBanner 不直接使用(只引 Warning 当 fg,无 OnWarning),所以 8 个 import。

`when (kind)` 内 val 引用:
- `DarkError` → `DarkIceChatError`
- `DarkSuccess` → `DarkIceChatPositive`
- `DarkWarning` → `DarkIceChatWarning`
- `LightError` → `LightIceChatError`
- `LightSuccess` → `LightIceChatPositive`
- `LightWarning` → `LightIceChatWarning`

`Box` / `background` / `padding` 不动。

- [ ] **Step 6: 改 SeverityBadgeTest.kt — rename 3 个 val import + 体内 3 个 val**

修改 `app/src/test/java/com/icespiritai/offline/ui/components/SeverityBadgeTest.kt` 的 import 段:

```kotlin
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatOnWarning
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
```

3 个 `darkColorScheme(primary = ...)` 内的 val:
- `DarkError` → `DarkIceChatError`
- `DarkOnWarning` → `DarkIceChatOnWarning`(2 处)
- `DarkWarning` → `DarkIceChatWarning`(2 处)

测试函数体 / `onNodeWithText` / `assertExists` 不动。

- [ ] **Step 7: 改 HomeScreenTest.kt — rename 2 个 val import + 体内 2 个 val**

修改 `app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenTest.kt` 的 import 段:

```kotlin
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
```

2 个 `darkColorScheme(surface = ..., onSurface = ...)` 内的 val:
- `DarkOnSurface` → `DarkIceChatOnBg`
- `DarkSurface` → `DarkIceChatPanel`

`HomeScreenBare` / `performClick` / `assertExists` 不动。

- [ ] **Step 8: 跑测试确认 Theme.kt 引用通过(call sites 改完)**

```bash
cd d:\GitHub\IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.*" -PmodelProfile=shell
```

预期:FAIL,`Unresolved reference: IceSpiritShapes`(Theme.kt Step 2 引用 `IceSpiritShapes`,但 Shape.kt 还没建)。这是预期失败 — Task 3 完成后才能通过。把"测试全过"验收推迟到 Task 4。

- [ ] **Step 9: Commit(只 commit main/ 下 5 个 call site 文件,Theme.kt 留到 Task 3 一起 commit)**

```bash
cd d:\GitHub\IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt
git add app/src/main/java/com/icespiritai/offline/ui/components/SeverityBadge.kt
git add app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt
git add app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt
git add app/src/test/java/com/icespiritai/offline/ui/components/SeverityBadgeTest.kt
git add app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenTest.kt
git commit -m "feat(theme): Theme.kt val rename + 5 call sites + wire IceSpiritShapes"
```

---

## Task 3: Shape.kt — 新建文件 + 写测试

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/theme/Shape.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/theme/ShapeTokensTest.kt`

- [ ] **Step 1: 写失败测试 — ShapeTokensTest.kt**

新建 `app/src/test/java/com/icespiritai/offline/ui/theme/ShapeTokensTest.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin IceSpiritAI_Vision's `IceRadius*` corner-radius tokens to the
 * IceSpiritAI_Chat `ice_radius_*` family (per spec §3.3). Aligned to:
 *   ice_radius_card   12dp
 *   ice_radius_chip   16dp
 *   ice_radius_dialog 20dp
 *   ice_radius_pill   24dp
 */
class ShapeTokensTest {

    @Test fun iceRadiusCard() = assertEquals(12.dp, IceRadiusCard)
    @Test fun iceRadiusChip() = assertEquals(16.dp, IceRadiusChip)
    @Test fun iceRadiusDialog() = assertEquals(20.dp, IceRadiusDialog)
    @Test fun iceRadiusPill() = assertEquals(24.dp, IceRadiusPill)

    @Test fun iceSpiritShapes_extraSmall_isChipRadius() {
        assertEquals(RoundedCornerShape(16.dp), IceSpiritShapes.extraSmall)
    }

    @Test fun iceSpiritShapes_small_isCardRadius() {
        assertEquals(RoundedCornerShape(12.dp), IceSpiritShapes.small)
    }

    @Test fun iceSpiritShapes_medium_isCardRadius() {
        assertEquals(RoundedCornerShape(12.dp), IceSpiritShapes.medium)
    }

    @Test fun iceSpiritShapes_large_isDialogRadius() {
        assertEquals(RoundedCornerShape(20.dp), IceSpiritShapes.large)
    }

    @Test fun iceSpiritShapes_extraLarge_isPillRadius() {
        assertEquals(RoundedCornerShape(24.dp), IceSpiritShapes.extraLarge)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ShapeTokensTest" -PmodelProfile=shell
```

预期:FAIL,`Unresolved reference: IceRadiusCard` 等。预期失败。

- [ ] **Step 3: 创建 Shape.kt**

新建 `app/src/main/java/com/icespiritai/offline/ui/theme/Shape.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * IceChat family corner-radius tokens — aligned 1:1 with
 * IceSpiritAI_Chat's `ice_radius_*` dimens (per spec §3.3). Single source
 * of truth for shape sizes; pin test in `ShapeTokensTest`.
 *
 *   ice_radius_card   12dp — surface / card
 *   ice_radius_chip   16dp — Chip widget
 *   ice_radius_dialog 20dp — AlertDialog / BottomSheet
 *   ice_radius_pill   24dp — pill / capsule surfaces
 */
val IceRadiusCard: Dp = 12.dp
val IceRadiusChip: Dp = 16.dp
val IceRadiusDialog: Dp = 20.dp
val IceRadiusPill: Dp = 24.dp

internal val IceSpiritShapes = Shapes(
    extraSmall = RoundedCornerShape(IceRadiusChip),
    small      = RoundedCornerShape(IceRadiusCard),
    medium     = RoundedCornerShape(IceRadiusCard),
    large      = RoundedCornerShape(IceRadiusDialog),
    extraLarge = RoundedCornerShape(IceRadiusPill),
)
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ShapeTokensTest" -PmodelProfile=shell
```

预期:PASS,9 tests passing。

- [ ] **Step 5: Commit**

```bash
cd d:\GitHub\IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/theme/Shape.kt
git add app/src/test/java/com/icespiritai/offline/ui/theme/ShapeTokensTest.kt
git commit -m "feat(theme): Shape.kt IceChat family radius (4 tokens + Shapes)"
```

---

## Task 4: 跑全部 Theme 测试验证 Theme.kt wiring

**Files:** (无 — 验证 Task 2 + 3 集成)

- [ ] **Step 1: 跑全部 ui 包单测**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.*" -PmodelProfile=shell
```

预期:PASS(ShapeTokensTest + ColorTokensTest + 现有 Compose 测试,无 `Unresolved reference`)。如果失败,回到 Task 2 检查 val 引用是否漏改。

- [ ] **Step 2: 跑全量 testDebugUnitTest 兜底**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest -PmodelProfile=shell
```

预期:PASS。

---

## Task 5: strings.xml — 改 ThemeMode label

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 编辑 strings.xml**

定位到第 48-50 行(基于之前的摸底结果):

```xml
<string name="settings_appearance_system">跟随系统</string>
<string name="settings_appearance_dark">深色</string>
<string name="settings_appearance_light">浅色</string>
```

改为:

```xml
<string name="settings_appearance_system">跟随系统</string>
<string name="settings_appearance_dark">深色雪夜</string>
<string name="settings_appearance_light">浅色冰月</string>
```

- [ ] **Step 2: 跑 build 确认资源编译过**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat assembleDebug -PmodelProfile=shell
```

预期:SUCCESS,资源编译过(`androidResources` task OK)。

- [ ] **Step 3: Commit**

```bash
cd d:\GitHub\IceSpiritAI_Vision
git add app/src/main/res/values/strings.xml
git commit -m "feat(theme): ThemeMode label 雪夜 / 冰月"
```

---

## Task 6: 最终验证(构建 + 全测 + 视觉清单)

**Files:** (无 — 验证)

- [ ] **Step 1: 跑全量单元测试**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest -PmodelProfile=shell
```

预期:PASS。

- [ ] **Step 2: 跑 shell profile debug build**

```bash
cd d:\GitHub\IceSpiritAI_Vision
./gradlew.bat assembleDebug -PmodelProfile=shell
```

预期:SUCCESS,APK 产出在 `app/build/outputs/apk/debug/`。

- [ ] **Step 3: 安装到设备 / 模拟器并过视觉清单**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.icespiritai.vision/com.icespiritai.offline.IceSpiritVisionActivity
```

**视觉清单**(逐项打勾,跟 spec §6 对齐):

- [ ] **ThemeMode 跟随系统** — 进设置切到 SYSTEM,确认 `settings_appearance_dark/light/system` 三条 label 渲染为"深色雪夜 / 浅色冰月 / 跟随系统"
- [ ] **强制浅色** — 切到 LIGHT,关键屏清单肉眼可读:
  - [ ] HomeScreen 顶栏、状态条、命中卡片堆叠,背景 `#F4F8FB`(冷白偏蓝)、表面 `#FFFFFF`
  - [ ] HitCard 严重度三色:Error 红(`#D32F2F`)/ Warning 琥珀(暂未单独定义,沿用 MD3 默认或 error 系)/ Success 绿(沿用 MD3 默认 — 本设计未引入单独的 Success/SuccessContainer 派生,留意视觉是否缺)
  - [ ] ResultPanel 错误文字在亮背景上对比度足
  - [ ] UpdateSection 四种 container(secondary/tertiary/primary/error)在亮下不撞色
- [ ] **强制深色** — 切到 DARK,关键屏清单肉眼可读:
  - [ ] HomeScreen 顶栏、状态条、命中卡片堆叠,背景 `#08131B`(深墨 navy)、表面 `#11212C`
  - [ ] HitCard 严重度三色在深背景上对比度足(Error `#FFFF6B6B` on bg `#08131B` ~5.4:1)
  - [ ] ResultPanel 错误文字在暗背景上对比度足
  - [ ] UpdateSection 四种 container 在暗下不撞色
- [ ] **Corner radius** — `HitCard` 圆角从原值改为 `IceRadiusCard=12dp`,对比旧视觉若有 ±2dp 偏差用户现场签收

- [ ] **Step 4: 跨项目对照(可选,但推荐)**

拉冰灵慧语同屏截图(`HomeScreen` / `SettingsScreen` 三态),与冰灵锐目视觉风格肉眼一致 — 背景白度、表面饱和度、深墨主色、红色错误色,4 项肉眼过。

- [ ] **Step 5: Commit 最终 spec 文档**

```bash
cd d:\GitHub\IceSpiritAI_Vision
git add docs/superpowers/specs/2026-08-18-icevision-icechat-family-theme-design.md
git commit -m "docs(spec): 冰灵家族主题对齐 spec"
git add docs/superpowers/plans/2026-08-18-icevision-icechat-family-theme.md
git commit -m "docs(plan): 冰灵家族主题对齐 implementation plan"
```

---

## 风险与权衡(从 spec §7 摘录)

| 风险 | 缓释 |
|---|---|
| Vision 原 slate 系(`#0F172A`)用户已习惯,改 ice 系(`#F4F8FB` / `#08131B`)视觉变化明显 | Task 6 Step 3 视觉清单必走;spec §5 已钉 WCAG 对比度 |
| `HitCard` 若硬编码 shape dp,改 shape 时漏掉 | Task 6 Step 3 视觉清单 — corner radius 那行肉眼对 |
| `MaterialTheme.shapes.medium` 现有 HitCard 等用 `RoundedCornerShape(...)` 硬编码的,需手动改 | grep `RoundedCornerShape` 在 `app/src/main/.../ui/` 引用核对(plan 外补) |
| HitCard 严重度三色中 Warning / Success 暂用 Material3 默认派生,若视觉缺则需 Task 6.5 加 `IceChatSuccessContainer` 派生 | Task 6 Step 3 视觉清单发现缺色则补 |

---

## Self-Review

**1. Spec 覆盖:**
- §3.1 Color token ✓ Task 1
- §3.2 Theme.kt 引用 + §3.3 Shape.kt ✓ Task 2 + Task 3
- §3.4 strings.xml ✓ Task 5
- §5 WCAG 自检 ✓ Task 6 Step 3 视觉清单 + 测试 hex pin
- §6 验证 ✓ Task 6
- §4 影响范围 4 文件 + 2 测试 ✓ Task 1/2/3/5

**2. Placeholder 扫描:** 无 TBD / TODO / "fill in details";Step 1/3 都给了完整代码;Step 2/4 给了精确命令与预期输出。

**3. 类型一致性:**
- Color.kt val 名:`LightIceChatBg` / `DarkIceChatBg` ... 全 30 个,在 Theme.kt Step 2 与 ColorTokensTest Step 1 三处一致
- Shape.kt val 名:`IceRadiusCard` / `IceRadiusChip` / `IceRadiusDialog` / `IceRadiusPill` / `IceSpiritShapes`,在 ShapeTokensTest Step 1 与 Theme.kt Step 2 两处一致
- Material3 slot 键名:`surfaceContainerHigh` 仅在 Theme.kt 与原 HitCard.kt 两处使用,slot 名 Material3 1.2+ 标准,无自定义

**4. 任务依赖:** Task 2 依赖 Task 1(Theme.kt 引用 Color.kt 新 val);Task 3 不依赖 Task 1(Task 3 自给自足);Task 4 依赖 Task 1 + 2 + 3;Task 5 独立;Task 6 依赖全部。执行顺序 1 → 3 → 2 → 4 → 5 → 6(Task 2 Step 2 依赖 Task 3 完成,因为引用 `IceSpiritShapes`)。

---

## Execution Handoff

Plan 落盘到 `docs/superpowers/plans/2026-08-18-icevision-icechat-family-theme.md`,共 6 个 Task,15 个 commit。规模小,适合 Inline Execution。Subagent-Driven 也可,每个 Task 一个 subagent。**选择执行方式:**
- Subagent-Driven:每 Task 一个 fresh subagent + 两阶段 review
- Inline Execution:本会话内 batch 执行 + 中间 checkpoint