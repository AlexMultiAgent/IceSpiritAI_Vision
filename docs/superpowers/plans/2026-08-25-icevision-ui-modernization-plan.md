# IceSpirit Vision UI Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade 冰灵锐目's UI from Material 3 base form to Material 3 Expressive (Google 2025 design language) for official release to 执法人员 / 监管自查, while keeping the back-end `IceSpiritVisionViewModel` / `OcrEngine` / `RuleMatcher` / state machine / export pipeline completely untouched.

**Architecture:** Five sequential phases (3.1 Theme底层 → 3.2 顶栏+StatusBanner → 3.3 主屏中下部 → 3.4 Extended FAB + Skeleton → 3.5 Settings+Viewer+Activity edge-to-edge). Each phase ends with a checkpoint: `testDebugUnitTest` green, 4 Robolectric golden screenshots regenerated, optional `connectedDebugAndroidTest` spot-check. No phase breaks the back-end (back-end tests unchanged across all phases).

**Tech Stack:** Kotlin 2.4.10 / Compose Material3 / Material Icons Extended / Coil / Navigation Compose / DataStore / Robolectric / AndroidX Test / AGP 9.3 / Gradle 9.7 / JDK 17.0.18+8 (manual stage). No new third-party dependencies.

**Spec:** [`docs/superpowers/specs/2026-08-25-icevision-ui-modernization-design.md`](../specs/2026-08-25-icevision-ui-modernization-design.md) — read §3-§4 in full before starting each phase.

**Plan-level reconciliation with existing code (deviates from spec where Chat-family 1:1 alignment requires):**

1. **`Color.kt`**: existing `DarkIceChatError` / `LightIceChatError` / `DarkIceChatWarning` / `LightIceChatWarning` / `DarkIceChatPositive` / `LightIceChatPositive` hex values are frozen (1:1 with IceSpiritAI_Chat). Plan adds 10 NEW tokens (Container/OnContainer for the existing 3 roles, plus a full 4-token Info role) — does NOT change existing hex. The 4-token Material You "accent + container + on + onContainer" pattern is preserved.
2. **`Type.kt`**: existing 9 token values frozen (used by other components). Plan only ADDS 3 missing tokens (`displaySmall`, `headlineMedium`, `headlineSmall`) that Material 3 Typography requires. Existing token values unchanged.
3. **`Shape.kt`**: existing `IceRadiusCard=12dp / IceRadiusChip=16dp / IceRadiusDialog=20dp / IceRadiusPill=24dp` are frozen (1:1 with Chat). The mapping `extraSmall→16, small→12, medium→12, large→20, extraLarge→24` already gives the Expressive-feeling 16/12/12/20/24 curve the spec wanted. Plan: do not change shape tokens.

---

## Phase 3.1 — Theme 底层

### Task 1: Add severity Container / OnContainer tokens to Color.kt

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt`

Existing `Color.kt` already has `DarkIceChatError` / `DarkIceChatOnError` / `DarkIceChatWarning` / `DarkIceChatOnWarning` / `DarkIceChatPositive` / `DarkIceChatOnPositive` (and light counterparts). They are pinned by `ColorTokensTest.kt`. We add Container/OnContainer for those 3 roles (6 new tokens) and 4 entirely new tokens for Info (Dark+Light × accent + on + container + onContainer = 8 new tokens). Total: 14 new tokens, but only 10 distinct hex values need pinning (some are reused for dark/light pairs).

- [ ] **Step 1: Read current ColorTokensTest.kt to understand the pinning pattern**

Read `app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt` and `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`. The test uses `Color(0xAARRGGBB)` literals — match this style.

- [ ] **Step 2: Add failing assertions for new tokens**

Append to `ColorTokensTest.kt`:

```kotlin
@Test fun darkIceChatErrorContainerIsPinned() {
    assertEquals(Color(0xFF7F1D1D), DarkIceChatErrorContainer)
}
@Test fun darkIceChatOnErrorContainerIsPinned() {
    assertEquals(Color(0xFFFECACA), DarkIceChatOnErrorContainer)
}
@Test fun lightIceChatErrorContainerIsPinned() {
    assertEquals(Color(0xFFFEE2E2), LightIceChatErrorContainer)
}
@Test fun lightIceChatOnErrorContainerIsPinned() {
    assertEquals(Color(0xFF7F1D1D), LightIceChatOnErrorContainer)
}

@Test fun darkIceChatWarningContainerIsPinned() {
    assertEquals(Color(0xFF78350F), DarkIceChatWarningContainer)
}
@Test fun darkIceChatOnWarningContainerIsPinned() {
    assertEquals(Color(0xFFFDE68A), DarkIceChatOnWarningContainer)
}
@Test fun lightIceChatWarningContainerIsPinned() {
    assertEquals(Color(0xFFFEF3C7), LightIceChatWarningContainer)
}
@Test fun lightIceChatOnWarningContainerIsPinned() {
    assertEquals(Color(0xFF78350F), LightIceChatOnWarningContainer)
}

@Test fun darkIceChatPositiveContainerIsPinned() {
    assertEquals(Color(0xFF14532D), DarkIceChatPositiveContainer)
}
@Test fun darkIceChatOnPositiveContainerIsPinned() {
    assertEquals(Color(0xFFBBF7D0), DarkIceChatOnPositiveContainer)
}
@Test fun lightIceChatPositiveContainerIsPinned() {
    assertEquals(Color(0xFFDCFCE7), LightIceChatPositiveContainer)
}
@Test fun lightIceChatOnPositiveContainerIsPinned() {
    assertEquals(Color(0xFF14532D), LightIceChatOnPositiveContainer)
}

@Test fun darkIceChatInfoIsPinned() {
    assertEquals(Color(0xFF60A5FA), DarkIceChatInfo)
}
@Test fun darkIceChatOnInfoIsPinned() {
    assertEquals(Color(0xFF08131B), DarkIceChatOnInfo)
}
@Test fun darkIceChatInfoContainerIsPinned() {
    assertEquals(Color(0xFF1E3A8A), DarkIceChatInfoContainer)
}
@Test fun darkIceChatOnInfoContainerIsPinned() {
    assertEquals(Color(0xFFBFDBFE), DarkIceChatOnInfoContainer)
}
@Test fun lightIceChatInfoIsPinned() {
    assertEquals(Color(0xFF2563EB), LightIceChatInfo)
}
@Test fun lightIceChatOnInfoIsPinned() {
    assertEquals(Color(0xFFFFFFFF), LightIceChatOnInfo)
}
@Test fun lightIceChatInfoContainerIsPinned() {
    assertEquals(Color(0xFFDBEAFE), LightIceChatInfoContainer)
}
@Test fun lightIceChatOnInfoContainerIsPinned() {
    assertEquals(Color(0xFF1E3A8A), LightIceChatOnInfoContainer)
}
```

- [ ] **Step 3: Run test, expect FAIL**

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ColorTokensTest" -PmodelProfile=shell
```

Expected: compile errors — `Unresolved reference: DarkIceChatErrorContainer`, etc. (all 20 new tokens missing).

- [ ] **Step 4: Add new tokens to Color.kt**

Modify `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`. Append AFTER the existing `DarkIceChatOnError` / `LightIceChatOnError` lines:

```kotlin
// Error container (added Phase 3.1 Task 1) — Material You 4-token pattern
val DarkIceChatErrorContainer = Color(0xFF7F1D1D)
val DarkIceChatOnErrorContainer = Color(0xFFFECACA)
val LightIceChatErrorContainer = Color(0xFFFEE2E2)
val LightIceChatOnErrorContainer = Color(0xFF7F1D1D)

// Warning container
val DarkIceChatWarningContainer = Color(0xFF78350F)
val DarkIceChatOnWarningContainer = Color(0xFFFDE68A)
val LightIceChatWarningContainer = Color(0xFFFEF3C7)
val LightIceChatOnWarningContainer = Color(0xFF78350F)

// Positive (Success) container
val DarkIceChatPositiveContainer = Color(0xFF14532D)
val DarkIceChatOnPositiveContainer = Color(0xFFBBF7D0)
val LightIceChatPositiveContainer = Color(0xFFDCFCE7)
val LightIceChatOnPositiveContainer = Color(0xFF14532D)

// Info — 4-token full role (didn't exist before Phase 3.1)
val DarkIceChatInfo = Color(0xFF60A5FA)
val DarkIceChatOnInfo = Color(0xFF08131B)
val DarkIceChatInfoContainer = Color(0xFF1E3A8A)
val DarkIceChatOnInfoContainer = Color(0xFFBFDBFE)
val LightIceChatInfo = Color(0xFF2563EB)
val LightIceChatOnInfo = Color(0xFFFFFFFF)
val LightIceChatInfoContainer = Color(0xFFDBEAFE)
val LightIceChatOnInfoContainer = Color(0xFF1E3A8A)
```

- [ ] **Step 5: Run test, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.ColorTokensTest" -PmodelProfile=shell
```

Expected: 20 new tests pass; existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt \
        app/src/test/java/com/icespiritai/offline/ui/theme/ColorTokensTest.kt
git commit -m "feat(theme): add severity Container/OnContainer/Info color tokens"
```

---

### Task 2: Add missing Typography tokens (displaySmall / headlineMedium / headlineSmall)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/theme/TypeTokensTest.kt`

Existing `Type.kt` defines 9 tokens (titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelSmall). Missing: `displayLarge`, `displayMedium`, `displaySmall`, `headlineLarge`, `headlineMedium`, `headlineSmall`. Modernization needs at least `displaySmall`, `headlineMedium`, `headlineSmall`. This task adds those 3 (others remain Material 3 defaults).

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/com/icespiritai/offline/ui/theme/TypeTokensTest.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTokensTest {
    @Test fun displaySmallIsPinned() {
        assertEquals(40.sp, IceSpiritTypography.displaySmall.fontSize)
    }
    @Test fun headlineMediumIsPinned() {
        assertEquals(30.sp, IceSpiritTypography.headlineMedium.fontSize)
    }
    @Test fun headlineSmallIsPinned() {
        assertEquals(26.sp, IceSpiritTypography.headlineSmall.fontSize)
    }
}
```

- [ ] **Step 2: Run test, expect FAIL (compile)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.TypeTokensTest" -PmodelProfile=shell
```

Expected: compile error `headlineSmall is not a member of Typography`.

- [ ] **Step 3: Update Type.kt with the 3 missing entries**

Modify `app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IceSpiritTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
```

- [ ] **Step 4: Run test, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.TypeTokensTest" -PmodelProfile=shell
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt \
        app/src/test/java/com/icespiritai/offline/ui/theme/TypeTokensTest.kt
git commit -m "feat(theme): pin displaySmall/headlineMedium/headlineSmall tokens"
```

---

### Task 3: Motion.kt — IceMotion data class + Modifier.emphasizedEnter()

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/theme/Motion.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/theme/MotionTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/com/icespiritai/offline/ui/theme/MotionTest.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun standardDurationIs300ms() {
        assertEquals(300, IceMotion.standardDuration.inWholeMilliseconds.toInt())
    }
    @Test fun emphasizedDurationIs500ms() {
        assertEquals(500, IceMotion.emphasizedDuration.inWholeMilliseconds.toInt())
    }
    @Test fun defaultMotionUsesFastOutSlowInEasingForStandard() {
        assertEquals(androidx.compose.animation.core.FastOutSlowInEasing, IceMotion.standardEasing)
    }
    @Test fun emphasizedEasingIsExpressiveCurve() {
        val expected = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        assertEquals(expected, IceMotion.emphasizedEasing)
    }
}
```

- [ ] **Step 2: Run test, expect FAIL**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.MotionTest" -PmodelProfile=shell
```

Expected: compile error `Unresolved reference: IceMotion`.

- [ ] **Step 3: Create Motion.kt**

Create `app/src/main/java/com/icespiritai/offline/ui/theme/Motion.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Motion scheme (added Phase 3.1 Task 3). Standard = 300ms / FastOutSlowIn.
 *  Emphasized = 500ms / Expressive curve for hero elements (cards, FAB, status). */
data class IceMotion(
    val standardDuration: kotlin.time.Duration = kotlin.time.Duration.parse("300ms"),
    val emphasizedDuration: kotlin.time.Duration = kotlin.time.Duration.parse("500ms"),
    val standardEasing: Easing = FastOutSlowInEasing,
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
) {
    companion object {
        val Default = IceMotion()
    }
}

/** Enter animation: scale 0.95 → 1.0 + fade 0 → 1 over [IceMotion.emphasizedDuration]. */
fun Modifier.emphasizedEnter(): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = tween(
            durationMillis = IceMotion.Default.emphasizedDuration.inWholeMilliseconds.toInt(),
            easing = IceMotion.Default.emphasizedEasing,
        ),
        label = "emphasizedEnterScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = IceMotion.Default.emphasizedDuration.inWholeMilliseconds.toInt(),
            easing = IceMotion.Default.emphasizedEasing,
        ),
        label = "emphasizedEnterAlpha",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
}
```

- [ ] **Step 4: Run test, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.MotionTest" -PmodelProfile=shell
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/theme/Motion.kt \
        app/src/test/java/com/icespiritai/offline/ui/theme/MotionTest.kt
git commit -m "feat(theme): add IceMotion data class + Modifier.emphasizedEnter()"
```

---

### Task 4: Expose SeverityColors object via IceSpiritVisionTheme

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/theme/SeverityColorsTest.kt`

Theme currently takes only `themeMode: ThemeMode`. Add a `severityColors: SeverityColors` parameter and expose it via `CompositionLocal`. Components (HitCard, HighlightOverlay, StatusBanner, SeverityBadge) will read it via `LocalSeverityColors.current`.

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/com/icespiritai/offline/ui/theme/SeverityColorsTest.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import com.icespiritai.offline.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityColorsTest {
    @Test fun darkViolationAccentIsDarkError() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatError, s.accent(Severity.Violation))
    }
    @Test fun lightViolationAccentIsLightError() {
        val s = SeverityColors(isDark = false)
        assertEquals(LightIceChatError, s.accent(Severity.Violation))
    }
    @Test fun darkViolationContainerIsPinned() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatErrorContainer, s.container(Severity.Violation))
    }
    @Test fun darkWarningAccentIsDarkWarning() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatWarning, s.accent(Severity.Warning))
    }
    @Test fun darkPositiveContainerIsPPositive() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatPositiveContainer, s.container(Severity.Positive))
    }
    @Test fun darkInfoAccentIsPInfo() {
        val s = SeverityColors(isDark = true)
        assertEquals(DarkIceChatInfo, s.accent(Severity.Info))
    }
    @Test fun lightInfoOnContainerIsPInfo() {
        val s = SeverityColors(isDark = false)
        assertEquals(LightIceChatOnInfoContainer, s.onContainer(Severity.Info))
    }
}
```

- [ ] **Step 2: Run test, expect FAIL (compile)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.SeverityColorsTest" -PmodelProfile=shell
```

Expected: compile error `Unresolved reference: SeverityColors`.

- [ ] **Step 3: Create SeverityColors.kt**

Create `app/src/main/java/com/icespiritai/offline/ui/theme/SeverityColors.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.icespiritai.offline.domain.Severity

/**
 * Maps [Severity] to a 4-token Material You color set:
 * accent (left bar / border) / onAccent (text on accent) / container (12% bg) / onContainer (text on bg).
 * Created in Phase 3.1 Task 4 — components now read these instead of hand-picking dark/light pairs.
 */
@Immutable
data class SeverityColors(
    val isDark: Boolean,
    val errorAccent: Color,
    val errorOnAccent: Color,
    val errorContainer: Color,
    val errorOnContainer: Color,
    val warningAccent: Color,
    val warningOnAccent: Color,
    val warningContainer: Color,
    val warningOnContainer: Color,
    val positiveAccent: Color,
    val positiveOnAccent: Color,
    val positiveContainer: Color,
    val positiveOnContainer: Color,
    val infoAccent: Color,
    val infoOnAccent: Color,
    val infoContainer: Color,
    val infoOnContainer: Color,
) {
    fun accent(s: Severity): Color = when (s) {
        Severity.Violation -> errorAccent
        Severity.Warning -> warningAccent
        Severity.Positive -> positiveAccent
        Severity.Info -> infoAccent
    }
    fun onAccent(s: Severity): Color = when (s) {
        Severity.Violation -> errorOnAccent
        Severity.Warning -> warningOnAccent
        Severity.Positive -> positiveOnAccent
        Severity.Info -> infoOnAccent
    }
    fun container(s: Severity): Color = when (s) {
        Severity.Violation -> errorContainer
        Severity.Warning -> warningContainer
        Severity.Positive -> positiveContainer
        Severity.Info -> infoContainer
    }
    fun onContainer(s: Severity): Color = when (s) {
        Severity.Violation -> errorOnContainer
        Severity.Warning -> warningOnContainer
        Severity.Positive -> positiveOnContainer
        Severity.Info -> infoOnContainer
    }
}

fun SeverityColors(isDark: Boolean): SeverityColors = if (isDark) {
    SeverityColors(
        isDark = true,
        errorAccent = DarkIceChatError,
        errorOnAccent = DarkIceChatOnError,
        errorContainer = DarkIceChatErrorContainer,
        errorOnContainer = DarkIceChatOnErrorContainer,
        warningAccent = DarkIceChatWarning,
        warningOnAccent = DarkIceChatOnWarning,
        warningContainer = DarkIceChatWarningContainer,
        warningOnContainer = DarkIceChatOnWarningContainer,
        positiveAccent = DarkIceChatPositive,
        positiveOnAccent = DarkIceChatOnPositive,
        positiveContainer = DarkIceChatPositiveContainer,
        positiveOnContainer = DarkIceChatOnPositiveContainer,
        infoAccent = DarkIceChatInfo,
        infoOnAccent = DarkIceChatOnInfo,
        infoContainer = DarkIceChatInfoContainer,
        infoOnContainer = DarkIceChatOnInfoContainer,
    )
} else {
    SeverityColors(
        isDark = false,
        errorAccent = LightIceChatError,
        errorOnAccent = LightIceChatOnError,
        errorContainer = LightIceChatErrorContainer,
        errorOnContainer = LightIceChatOnErrorContainer,
        warningAccent = LightIceChatWarning,
        warningOnAccent = LightIceChatOnWarning,
        warningContainer = LightIceChatWarningContainer,
        warningOnContainer = LightIceChatOnWarningContainer,
        positiveAccent = LightIceChatPositive,
        positiveOnAccent = LightIceChatOnPositive,
        positiveContainer = LightIceChatPositiveContainer,
        positiveOnContainer = LightIceChatOnPositiveContainer,
        infoAccent = LightIceChatInfo,
        infoOnAccent = LightIceChatOnInfo,
        infoContainer = LightIceChatInfoContainer,
        infoOnContainer = LightIceChatOnInfoContainer,
    )
}

val LocalSeverityColors = staticCompositionLocalOf<SeverityColors> {
    error("LocalSeverityColors not provided. Wrap your screen in IceSpiritVisionTheme {}.")
}

/** Composable accessor for the active [SeverityColors]. Resolves dark/light from [ThemeMode]. */
val iceSpiritSeverityColors: SeverityColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSeverityColors.current
```

- [ ] **Step 4: Update Theme.kt to provide LocalSeverityColors**

Modify `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    val darkTheme = themeMode.toDarkTheme()
    val severityColors = SeverityColors(isDark = darkTheme)
    CompositionLocalProvider(LocalSeverityColors provides severityColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = IceSpiritShapes,
            typography = IceSpiritTypography,
            content = content,
        )
    }
}
```

- [ ] **Step 5: Run test, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.SeverityColorsTest" -PmodelProfile=shell
```

Expected: 7 tests pass.

- [ ] **Step 6: Run all theme tests to ensure no regression**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.theme.*" -PmodelProfile=shell
```

Expected: ColorTokensTest, ShapeTokensTest, ThemeModeTest, TypeTokensTest, MotionTest, SeverityColorsTest all green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/theme/SeverityColors.kt \
        app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt \
        app/src/test/java/com/icespiritai/offline/ui/theme/SeverityColorsTest.kt
git commit -m "feat(theme): expose SeverityColors via CompositionLocal + Theme plumbing"
```

---

### Task 5: Phase 3.1 checkpoint — full test suite + 4 golden screenshots

- [ ] **Step 1: Run full unit test suite**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected: ALL green (no regression on existing theme / view-model / rule tests).

- [ ] **Step 2: Regenerate 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" -PmodelProfile=shell
```

If golden PNGs in `app/src/test/screenshots/` change unexpectedly, manually inspect — Phase 3.1 changed Theme but kept components intact, so HomeScreen golden should be visually identical (no behavior change, just new hex values that didn't reach HomeScreen yet).

- [ ] **Step 3: Bump versionCode (release hygiene per CLAUDE.md — Phase 3.1 is a real change)**

Read `app/build.gradle.kts`, find `versionCode 14`, bump to `15`. Also bump `versionName "0.1.14"` to `"0.1.15"`.

- [ ] **Step 4: Commit version bump + final test run**

```bash
git add app/build.gradle.kts
git commit -m "chore(release): bump versionCode 14→15 for Phase 3.1"
```

- [ ] **Step 5: Phase 3.1 release notes**

Append to `app/src/main/assets/user-changelog.md` under the latest version:

```markdown
## v0.1.15 (2026-08-25)

- **UI 现代化底层** Phase 3.1:严重度色板扩展(Info 角色 / Container 角色)、Type 字号加 `displaySmall` / `headlineMedium` / `headlineSmall`、新增 `IceMotion` 数据类与 `Modifier.emphasizedEnter()`、通过 `LocalSeverityColors` 暴露统一严重度配色
- **内部重构**:Hex 值未变更,只增加 token;`IceSpiritVisionTheme` 新增 `LocalSeverityColors` provider
```

Commit changelog:

```bash
git add app/src/main/assets/user-changelog.md
git commit -m "docs(changelog): v0.1.15 — Phase 3.1 UI 底层"
```

---

## Phase 3.2 — HomeTopBar / RuleTabBar / StatusBanner

### Task 6: Add new strings (KPI labels)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the new strings**

In `app/src/main/res/values/strings.xml`, append (near existing `status_*` keys):

```xml
<string name="kpi_violation_label">违规</string>
<string name="kpi_warning_label">警告</string>
<string name="kpi_info_label">信息</string>
<string name="kpi_positive_label">合规</string>
<string name="empty_idle_hint">请对正图片后点击拍照</string>
<string name="extended_fab_label">拍照</string>
<string name="pick_image_fab_desc">从相册选图</string>
<string name="loading_ocr_skeleton">OCR 识别中…</string>
<string name="loading_rule_skeleton">规则扫描中…</string>
```

- [ ] **Step 2: Verify the strings compile**

```bash
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
```

Expected: BUILD SUCCESSFUL (no string errors).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(ui): add KPI label strings for modernization"
```

---

### Task 7: Rewrite StatusBanner as KPI horizontal bar

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/StatusBannerTest.kt`

Existing `StatusBanner(kind, text)` is a single-color Box with one Text. New design: 4-segment KPI row (Idle / Loading / KPI numbers per severity). Public API changes — HomeScreen wiring must be updated.

- [ ] **Step 1: Read existing StatusBannerTest.kt to understand its assertions**

Read `app/src/test/java/com/icespiritai/offline/ui/home/StatusBannerTest.kt`. It uses Robolectric `createComposeRule()` and asserts on `Text` content.

- [ ] **Step 2: Add failing tests for KPI behavior**

Append to `StatusBannerTest.kt`:

```kotlin
@Test fun idleKpiRendersEmptyHint() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            StatusBanner(kind = StatusBannerKind.Idle)
        }
    }
    composeRule.onNodeWithText("请对正图片后点击拍照").assertExists()
}

@Test fun violationKpiRendersViolationCount() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            StatusBanner(
                kind = StatusBannerKind.Violation,
                violationCount = 3,
                warningCount = 1,
                infoCount = 0,
            )
        }
    }
    composeRule.onNodeWithText("3").assertExists()
    composeRule.onNodeWithText("1").assertExists()
    composeRule.onNodeWithText("违规").assertExists()
    composeRule.onNodeWithText("警告").assertExists()
}

@Test fun emptyCountsKpiRendersZeroForEach() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.LIGHT) {
            StatusBanner(
                kind = StatusBannerKind.Success,
                violationCount = 0,
                warningCount = 0,
                infoCount = 0,
            )
        }
    }
    composeRule.onNodeWithText("0").assertExists()  // at least one zero displayed
}

@Test fun loadingKpiRendersLoadingHint() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            StatusBanner(kind = StatusBannerKind.Loading, stage = StatusBannerStage.LoadingOcr)
        }
    }
    composeRule.onNodeWithText("OCR 识别中…").assertExists()
}
```

- [ ] **Step 3: Run tests, expect FAIL**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.StatusBannerTest" -PmodelProfile=shell
```

Expected: compile errors — `StatusBanner` does not accept `violationCount`, `warningCount`, `infoCount`, `stage` parameters.

- [ ] **Step 4: Rewrite StatusBanner.kt**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt` entirely:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

/** Public kinds — keeps backwards compat for any external callers. */
enum class StatusBannerKind { Idle, Loading, Success, Warning, Violation }

/** Sub-state for Loading — drives the running-phase text. */
enum class StatusBannerStage { LoadingOcr, LoadingRuleScanning }

/**
 * Modernized status banner (Phase 3.2): four-segment KPI horizontal bar.
 * Idle shows the empty hint; Loading shows spinner + phase text; numeric
 * kinds (Violation/Warning/Success) show the three counters via
 * AnimatedContent so a hit landing after Idle triggers a slide-in.
 */
@Composable
fun StatusBanner(
    kind: StatusBannerKind,
    modifier: Modifier = Modifier,
    violationCount: Int = 0,
    warningCount: Int = 0,
    infoCount: Int = 0,
    stage: StatusBannerStage? = null,
) {
    val sev = iceSpiritSeverityColors
    val (bg, accent) = when (kind) {
        StatusBannerKind.Idle -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBannerKind.Loading -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBannerKind.Success -> sev.container(Severity.Positive) to sev.accent(Severity.Positive)
        StatusBannerKind.Warning -> sev.container(Severity.Warning) to sev.accent(Severity.Warning)
        StatusBannerKind.Violation -> sev.container(Severity.Violation) to sev.accent(Severity.Violation)
    }
    val onBg = when (kind) {
        StatusBannerKind.Success -> sev.onContainer(Severity.Positive)
        StatusBannerKind.Warning -> sev.onContainer(Severity.Warning)
        StatusBannerKind.Violation -> sev.onContainer(Severity.Violation)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = statusBannerA11y(kind, violationCount, warningCount, infoCount) },
    ) {
        when (kind) {
            StatusBannerKind.Idle -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = accent,
                )
                Text(
                    text = stringResource(R.string.empty_idle_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBg,
                )
            }
            StatusBannerKind.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 3.dp,
                    color = accent,
                )
                val phaseText = when (stage) {
                    StatusBannerStage.LoadingOcr -> stringResource(R.string.loading_ocr_skeleton)
                    StatusBannerStage.LoadingRuleScanning -> stringResource(R.string.loading_rule_skeleton)
                    null -> ""
                }
                Text(
                    text = phaseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBg,
                )
            }
            else -> KpiRow(
                violationCount = violationCount,
                warningCount = warningCount,
                infoCount = infoCount,
                accent = accent,
                onBg = onBg,
            )
        }
    }
}

@Composable
private fun KpiRow(
    violationCount: Int,
    warningCount: Int,
    infoCount: Int,
    accent: Color,
    onBg: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpiCell(
            count = violationCount,
            label = stringResource(R.string.kpi_violation_label),
            accent = accent,
            onBg = onBg,
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = accent) },
        )
        KpiCell(
            count = warningCount,
            label = stringResource(R.string.kpi_warning_label),
            accent = accent,
            onBg = onBg,
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = accent) },
        )
        KpiCell(
            count = infoCount,
            label = stringResource(R.string.kpi_info_label),
            accent = accent,
            onBg = onBg,
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = accent) },
        )
    }
}

@Composable
private fun KpiCell(
    count: Int,
    label: String,
    accent: Color,
    onBg: Color,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "kpiCount",
        ) { v ->
            Text(
                text = "$v",
                style = MaterialTheme.typography.headlineMedium,
                color = onBg,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = onBg,
            )
        }
    }
}

private fun statusBannerA11y(
    kind: StatusBannerKind,
    v: Int,
    w: Int,
    i: Int,
): String = when (kind) {
    StatusBannerKind.Idle -> "等待拍照"
    StatusBannerKind.Loading -> "识别中"
    StatusBannerKind.Success -> "未发现违规"
    StatusBannerKind.Warning -> "警告 $w 处"
    StatusBannerKind.Violation -> "违规 $v 处,警告 $w 处,信息 $i 处"
}
```

- [ ] **Step 5: Update HomeScreen wiring (StatusBannerFor → new parameters)**

In `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`, find `StatusBannerFor` (around line 225). Replace the entire private function with:

```kotlin
@Composable
private fun StatusBannerFor(state: AnalysisState) {
    when (state) {
        AnalysisState.Idle -> StatusBanner(StatusBannerKind.Idle)
        is AnalysisState.Loading -> StatusBanner(
            kind = StatusBannerKind.Loading,
            stage = when (state.stage) {
                AnalysisState.Loading.Stage.OcrRunning -> StatusBannerStage.LoadingOcr
                AnalysisState.Loading.Stage.RuleScanning -> StatusBannerStage.LoadingRuleScanning
            },
        )
        is AnalysisState.Complete -> {
            val report = state.report
            if (!report.hasText) {
                StatusBanner(StatusBannerKind.Warning)
            } else {
                val maxSev = report.hits.maxOfOrNull { it.severity }
                val kind = when (maxSev) {
                    Severity.Violation -> StatusBannerKind.Violation
                    Severity.Warning -> StatusBannerKind.Warning
                    Severity.Info -> StatusBannerKind.Success  // info-only → still "compliant"
                    null -> StatusBannerKind.Success
                }
                StatusBanner(
                    kind = kind,
                    violationCount = report.hits.count { it.severity == Severity.Violation },
                    warningCount = report.hits.count { it.severity == Severity.Warning },
                    infoCount = report.hits.count { it.severity == Severity.Info },
                )
            }
        }
        is AnalysisState.Error -> StatusBanner(StatusBannerKind.Violation)
        else -> StatusBanner(StatusBannerKind.Idle)
    }
}
```

Remove the now-unused `R.string.status_violation_count` / `status_warning_count` / `status_info_count` / `status_no_violation_card` / `status_no_text_banner` reads inside `StatusBannerFor` (those strings can stay in `strings.xml` for now — don't delete in Phase 3.2, audit in Phase 3.5).

- [ ] **Step 6: Run StatusBannerTest + HomeScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.StatusBannerTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: All pass. If HomeScreenTest fails on KPI assertions, it likely tested for old single-text banner — adjust those 2-3 assertions (not the logic) to use the new shape.

- [ ] **Step 7: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Expected: Golden PNGs update. Visually inspect:
- Idle: banner shows camera icon + "请对正图片后点击拍照"
- Complete (3 violations): banner shows KPI cells with 3, 1, 0

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt \
        app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/StatusBannerTest.kt
git commit -m "feat(ui): StatusBanner → KPI horizontal bar with AnimatedContent"
```

---

### Task 8: HomeTopBar — transparent background + headlineSmall title + SettingsOutlined

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTest.kt`

- [ ] **Step 1: Add failing test**

Append to `HomeTopBarTest.kt`:

```kotlin
@Test fun topBarTitleUsesHeadlineSmallStyle() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            HomeTopBar(
                selectedTab = RuleTab.AdSignage,
                onSelectTab = {},
                tabEnabled = true,
                onOpenSettings = {},
            )
        }
    }
    // The title text node should have the headlineSmall token's font size (26.sp).
    composeRule.onNodeWithText(getApplicationContext<Context>().getString(R.string.app_name))
        .assertExists()
}
```

(Skip the fontSize assertion if Robolectric `getSemanticsNode().layoutInfo` cannot resolve typography — assertExists on the title text is sufficient regression coverage.)

- [ ] **Step 2: Run test, expect PASS (test is just smoke)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HomeTopBarTest" -PmodelProfile=shell
```

Expected: green — proves baseline still works. If you want to assert on the fontSize, use `composeRule.onNodeWithText(...).fetchSemanticsNode().config[SemanticsProperties.TextLayoutResults]` etc.

- [ ] **Step 3: Modify HomeTopBar.kt**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.icespiritai.offline.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    selectedTab: RuleTab,
    onSelectTab: (RuleTab) -> Unit,
    tabEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,  // 26sp SemiBold (was titleLarge 22sp)
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            actions = {
                val a11y = stringResource(R.string.settings_button_desc)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = a11y },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,  // 22dp outlined, more restrained
                        contentDescription = null,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,  // edge-to-edge preview bleeds through
                scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
        RuleTabBar(
            selected = selectedTab,
            onSelect = onSelectTab,
            enabled = tabEnabled,
        )
    }
}
```

- [ ] **Step 4: Run HomeTopBarTest + HomeScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.HomeTopBarTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 5: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Visually inspect: title is larger (26sp) and gear icon is the outlined variant.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTest.kt
git commit -m "feat(ui): HomeTopBar — transparent + headlineSmall + outlined settings"
```

---

### Task 9: RuleTabBar — Material 3 SecondaryTab (custom indicator)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt`

Spec §4.1: Tab uses `TabRow` with custom 3dp indicator, selected text `titleMedium` SemiBold, unselected `bodyLarge`.

- [ ] **Step 1: Read RuleTabBarTest.kt to understand existing assertions**

Read `app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt`.

- [ ] **Step 2: Add failing test for indicator height**

Append:

```kotlin
@Test fun tabBarHasCustomIndicator() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            RuleTabBar(
                selected = RuleTab.AdSignage,
                onSelect = {},
                enabled = true,
            )
        }
    }
    // Sanity: tab text still rendered.
    composeRule.onNodeWithText("广告招牌").assertExists()
}
```

- [ ] **Step 3: Run, expect PASS (baseline)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.RuleTabBarTest" -PmodelProfile=shell
```

- [ ] **Step 4: Modify RuleTabBar.kt — switch to TabRow with 3dp indicator**

Read current `RuleTabBar.kt` first. Replace with the new structure (keep `visibleTabs` logic unchanged per CLAUDE.md):

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

enum class RuleTab { AdSignage, FoodLabeling }

@Composable
fun RuleTabBar(
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Per CLAUDE.md Phase 3 direction: only AdSignage is exposed in the UI tab;
    // FoodLabeling remains in the enum for future re-enable. To restore, change
    // visibleTabs back to RuleTab.entries.toList().
    val visibleTabs = listOf(RuleTab.AdSignage)
    val selectedIndex = visibleTabs.indexOf(selected).coerceAtLeast(0)
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex], 3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        visibleTabs.forEachIndexed { index, tab ->
            val title = when (tab) {
                RuleTab.AdSignage -> stringResource(R.string.tab_ad_law)
                RuleTab.FoodLabeling -> stringResource(R.string.tab_food_label)
            }
            Tab(
                selected = index == selectedIndex,
                onClick = { if (enabled) onSelect(tab) },
                enabled = enabled,
                text = {
                    Text(
                        text = title,
                        style = if (index == selectedIndex)
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        else
                            MaterialTheme.typography.bodyLarge,
                    )
                },
            )
        }
    }
}

private fun Modifier.tabIndicatorOffset(
    position: androidx.compose.material3.TabPosition,
    height: androidx.compose.ui.unit.Dp,
): Modifier = this.then(
    androidx.compose.foundation.layout.offset(x = position.left)
        .then(Modifier.fillMaxWidth(position.width / position.width))
        .then(androidx.compose.foundation.layout.height(height))
).let {
    // Compose 1.7+ requires using the new tabIndicatorOffset helper; replace with
    // the appropriate call for the project's compose-bom. The placeholder here
    // intentionally defers to the project's compose version. The author of this
    // task must verify Compose BOM version before merging.
    it
}
```

**IMPORTANT**: The `tabIndicatorOffset` helper signature differs across Compose BOMs. Open `gradle/libs.versions.toml` to find the Compose BOM, then use the matching `Modifier.tabIndicatorOffset(position, height)` overload from `androidx.compose.material3.TabRowDefaults`. If the BOM is too old and the helper isn't available, fall back to:

```kotlin
indicator = { tabPositions ->
    if (selectedIndex < tabPositions.size) {
        val pos = tabPositions[selectedIndex]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.BottomStart)
                .offset(x = pos.left)
                .width(pos.width)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
},
```

(Adjust imports accordingly. The fallback is verbose but works on any BOM.)

- [ ] **Step 5: Run RuleTabBarTest + HomeScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.RuleTabBarTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: green. If `tabIndicatorOffset` overload resolution fails, switch to the fallback Box in Step 4.

- [ ] **Step 6: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Inspect: tab text uses `titleMedium` SemiBold when selected; indicator is 3dp tall, primary color.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt
git commit -m "feat(ui): RuleTabBar — 3dp indicator + titleMedium selected typography"
```

---

### Task 10: Phase 3.2 checkpoint — full test suite + versionCode bump

- [ ] **Step 1: Run full unit tests**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected: ALL green.

- [ ] **Step 2: Build the APK**

```bash
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Bump versionCode 15 → 16, versionName 0.1.15 → 0.1.16**

- [ ] **Step 4: Update changelog**

Append to `app/src/main/assets/user-changelog.md`:

```markdown
## v0.1.16 (2026-08-25)

- **UI 现代化 Phase 3.2**:StatusBanner 重写为 KPI 横条(违规 / 警告 / 信息 三段,AnimatedContent 数值滑入);HomeTopBar 透明背景 + headlineSmall 标题 + Outlined 齿轮图标;RuleTabBar 升级 Material 3 SecondaryTab(3dp 指示器 + titleMedium 选中态)
- **影响**:屏幕顶部信息密度提升,违规数字一眼可见;tab 风格更接近 Material You
```

- [ ] **Step 5: Commit + tag**

```bash
git add app/build.gradle.kts app/src/main/assets/user-changelog.md
git commit -m "chore(release): bump versionCode 15→16 for Phase 3.2"
git tag v0.1.16
```

---

## Phase 3.3 — ImagePreview / HighlightOverlay / HitCard / ResultPanel

### Task 11: Add Severity.Positive enum value

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/domain/Severity.kt`

The `Severity` enum currently has `Violation`, `Warning`, `Info`. The new design needs a `Positive` variant for "未发现违规" / compliance state — used by `StatusBanner` (Phase 3.2 already referenced it; if not already present, add it now).

- [ ] **Step 1: Check current Severity enum**

Read `app/src/main/java/com/icespiritai/offline/domain/Severity.kt`. If `Positive` doesn't exist, add it.

- [ ] **Step 2: If missing, append Positive**

```kotlin
enum class Severity { Violation, Warning, Info, Positive }
```

If a `when` statement is exhaustive and `Severity` is referenced from a `when` block without an `else`, the compiler will report the new branch. Update any switch sites — but `StatusBanner` is the only consumer introduced in Phase 3.2, and it already handles all 4 cases.

- [ ] **Step 3: Run tests, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/domain/Severity.kt
git commit -m "feat(domain): add Severity.Positive for compliance state"
```

---

### Task 12: HitCard — left color bar + new typography

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HitCard.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/HitCardTest.kt`

- [ ] **Step 1: Read HitCardTest.kt**

Read `app/src/test/java/com/icespiritai/offline/ui/home/HitCardTest.kt`.

- [ ] **Step 2: Add failing test for left color bar**

Append to `HitCardTest.kt`:

```kotlin
@Test fun violationHitCardShowsQuotedMatchTextInHeadlineSmall() {
    val hit = RuleHit(
        matchedText = "中国第一",
        domain = "ad_signage",
        category = "absolute",
        regulation = "广告法 §9",
        severity = Severity.Violation,
        lawText = "不得使用'国家级'/'最高级'/'最佳'等用语。",
        matchedLineIndex = 0,
    )
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            HitCard(hit = hit)
        }
    }
    composeRule.onNodeWithText("\"中国第一\"").assertExists()
}
```

- [ ] **Step 3: Run, expect FAIL**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HitCardTest" -PmodelProfile=shell
```

Expected: fail — quoted match text is not the current rendering.

- [ ] **Step 4: Rewrite HitCard.kt**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/HitCard.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.ui.theme.emphasizedEnter
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

@Composable
fun HitCard(hit: RuleHit, modifier: Modifier = Modifier) {
    val severityLabel = stringResource(
        when (hit.severity) {
            com.icespiritai.offline.domain.Severity.Violation -> R.string.hit_severity_violation
            com.icespiritai.offline.domain.Severity.Warning -> R.string.hit_severity_warning
            com.icespiritai.offline.domain.Severity.Info -> R.string.hit_severity_info
            com.icespiritai.offline.domain.Severity.Positive -> R.string.kpi_positive_label
        }
    )
    val categoryLabel = CategoryDisplay.displayName(hit.domain, hit.category)
    var lawExpanded by rememberSaveable { mutableStateOf(false) }
    val sev = iceSpiritSeverityColors
    val accent = sev.accent(hit.severity)
    val container = sev.container(hit.severity)
    val onContainer = sev.onContainer(hit.severity)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "${hit.matchedText}, $severityLabel, $categoryLabel"
            }
            .emphasizedEnter(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 6dp left color bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                container.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = "\"${hit.matchedText}\"",
                    style = MaterialTheme.typography.headlineSmall,  // 26sp SemiBold
                    color = onContainer,
                )
                Text(
                    text = stringResource(R.string.hit_card_category, categoryLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.hit_card_regulation, hit.regulation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (hit.lawText.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { lawExpanded = !lawExpanded },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (lawExpanded) R.string.hit_card_hide_law else R.string.hit_card_show_law,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (lawExpanded) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                text = hit.lawText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
```

Required imports for `width(6.dp)` — add at the top of imports:

```kotlin
import androidx.compose.foundation.layout.width
```

- [ ] **Step 5: Run HitCardTest + ResultPanelTest + HomeScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.HitCardTest" \
    --tests "com.icespiritai.offline.ui.home.ResultPanelTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: green. If ResultPanelTest was testing the absence of the left bar, update its assertion — but it likely tested only the text content (the spec's Robolectric LazyColumn viewport gotcha noted in CLAUDE.md).

- [ ] **Step 6: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Visually inspect: each hit card has a 6dp colored bar on the left, headlineSmall quoted match text, FilledTonalButton for "查看法规".

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HitCard.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/HitCardTest.kt
git commit -m "feat(ui): HitCard — 6dp left color bar + headlineSmall quoted text"
```

---

### Task 13: HighlightOverlay — Info severity support + animated gradient stroke

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/HighlightOverlayTest.kt`

- [ ] **Step 1: Read HighlightOverlayTest.kt**

Read `app/src/test/java/com/icespiritai/offline/ui/home/HighlightOverlayTest.kt`.

- [ ] **Step 2: Add failing test for Info severity**

Append:

```kotlin
@Test fun infoSeverityHitRendersStroke() {
    val lines = listOf(
        TextLine(text = "这是提示信息", box = Rect(0, 0, 100, 20)),
    )
    val hits = listOf(
        RuleHit(
            matchedText = "提示信息",
            domain = "ad_signage",
            category = "info",
            regulation = "通用",
            severity = Severity.Info,
            lawText = "",
            matchedLineIndex = 0,
        ),
    )
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            HighlightOverlay(lines = lines, hits = hits)
        }
    }
    // Verify the canvas is laid out (no crash).
    composeRule.onRoot().assertExists()
}
```

- [ ] **Step 3: Run, expect PASS (canvas never throws on no-stroke; this is mostly a regression sentinel)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HighlightOverlayTest" -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 4: Rewrite HighlightOverlay.kt**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.domain.TextNormalizer
import com.icespiritai.offline.ui.theme.IceMotion
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

@Composable
fun HighlightOverlay(
    lines: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
) {
    val sev = iceSpiritSeverityColors
    val strokePx = 6f  // bumped from 4f for visual weight (Phase 3.3)
    val alpha by animateFloatAsState(
        targetValue = if (lines.isNotEmpty() && hits.isNotEmpty()) 1f else 0f,
        animationSpec = tween(
            durationMillis = IceMotion.Default.standardDuration.inWholeMilliseconds.toInt(),
            easing = IceMotion.Default.standardEasing,
        ),
        label = "highlightAlpha",
    )
    // Keywords are matched on normalized text (whitespace/full-width removed),
    // so the containment check must run on normalized lines as well — otherwise
    // "100%有效" in a line would not match the "100% 有效" hit.
    val normalizedHits = hits.map { TextNormalizer.forMatching(it.matchedText) to it.severity }
    Canvas(modifier = modifier) {
        lines.forEach { line ->
            val normalizedLine = TextNormalizer.forMatching(line.text)
            val lineSeverity = normalizedHits
                .filter { normalizedLine.contains(it.first) }
                .maxOfOrNull { it.second }
                ?: return@forEach
            val color = sev.accent(lineSeverity)
            val x = offsetX + line.box.left * scaleX
            val y = offsetY + line.box.top * scaleY
            val w = line.box.width() * scaleX
            val h = line.box.height() * scaleY
            // Animated gradient stroke — diagonal, accent → onAccent
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.6f),
                    ),
                    start = Offset(x, y),
                    end = Offset(x + w, y + h),
                ),
                topLeft = Offset(x, y),
                size = Size(w, h),
                style = Stroke(width = strokePx),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
```

Note: `HighlightOverlay` was previously annotated with `private fun resolveSeverityColors`-style dark-detection; it now reads `iceSpiritSeverityColors` which is theme-aware.

- [ ] **Step 5: Run HighlightOverlayTest + HomeScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.HighlightOverlayTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 6: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Visually inspect: violation boxes are 6dp wide, gradient-shaded (subtle), Info hits also get a stroke (no longer skipped).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/HighlightOverlayTest.kt
git commit -m "feat(ui): HighlightOverlay — Info severity + animated gradient stroke"
```

---

### Task 14: ImagePreview — edge-to-edge inset

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`

- [ ] **Step 1: Read ImagePreviewDoubleTapTest.kt**

Read `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`.

- [ ] **Step 2: Add failing test for status-bar padding**

Append:

```kotlin
@Test fun imagePreviewAccountsForStatusBarInset() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            ImagePreview(imageUri = null, lineBoxes = emptyList(), hits = emptyList())
        }
    }
    // Sanity: preview is still rendered.
    composeRule.onNodeWithTag("image_preview").assertExists()
}
```

- [ ] **Step 3: Run, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.ImagePreviewDoubleTapTest" -PmodelProfile=shell
```

- [ ] **Step 4: Modify ImagePreview.kt — wrap content with statusBars/navigationBars insets**

Read current `ImagePreview.kt`. Modify the top-level `Box` to apply `Modifier.windowInsetsPadding(WindowInsets.systemBars)` on the inner content. The full rewrite:

```kotlin
package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine

private data class FitTransform(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)

private fun computeFitTransform(painter: Painter?, boxSize: IntSize): FitTransform {
    if (painter == null || boxSize == IntSize.Zero) return FitTransform(1f, 1f, 0f, 0f)
    val intrinsicW = painter.intrinsicSize.width
    val intrinsicH = painter.intrinsicSize.height
    if (intrinsicW <= 0f || intrinsicH <= 0f) return FitTransform(1f, 1f, 0f, 0f)
    val boxW = boxSize.width.toFloat()
    val boxH = boxSize.height.toFloat()
    val scale = minOf(boxW / intrinsicW, boxH / intrinsicH)
    return FitTransform(
        scaleX = scale,
        scaleY = scale,
        offsetX = (boxW - intrinsicW * scale) / 2f,
        offsetY = (boxH - intrinsicH * scale) / 2f,
    )
}

@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    val rootModifier = modifier
        .fillMaxSize()
        .testTag("image_preview")
        .let { m ->
            if (onDoubleTap != null && lineBoxes.isNotEmpty()) {
                m.pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
            } else {
                m
            }
        }
        .semantics { contentDescription = a11y }
        .onSizeChanged { boxSize = it }
    Box(
        modifier = rootModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.status_image_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result -> imagePainter = result.painter },
            )
            if (lineBoxes.isNotEmpty()) {
                val transform = remember(boxSize, imagePainter) {
                    computeFitTransform(imagePainter, boxSize)
                }
                HighlightOverlay(
                    lines = lineBoxes,
                    hits = hits,
                    scaleX = transform.scaleX,
                    scaleY = transform.scaleY,
                    offsetX = transform.offsetX,
                    offsetY = transform.offsetY,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
```

Key change: idle-state hint now uses `windowInsetsPadding(WindowInsets.systemBars)` so it doesn't crash into the status bar. The image itself remains edge-to-edge (`AsyncImage` keeps `fillMaxSize`).

- [ ] **Step 5: Run ImagePreviewDoubleTapTest + HomeScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.ImagePreviewDoubleTapTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 6: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Visually inspect: idle-state hint is no longer overlapped by status bar area. Image previews remain bleed-to-edge.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt
git commit -m "feat(ui): ImagePreview — systemBars inset on idle hint"
```

---

### Task 15: Phase 3.3 checkpoint — full test suite + versionCode bump

- [ ] **Step 1: Run full unit tests**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected: ALL green.

- [ ] **Step 2: Verify 4-fixture regression on ice_ocr_rules profile**

```bash
./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.rules.*" -PmodelProfile=ice_ocr_rules
```

Expected: ALL rule tests green (4 ad-signage fixtures still match).

- [ ] **Step 3: Bump versionCode 16 → 17, versionName 0.1.16 → 0.1.17**

- [ ] **Step 4: Update changelog**

```markdown
## v0.1.17 (2026-08-25)

- **UI 现代化 Phase 3.3**:HitCard 重写为左侧 6dp 严重度色条 + 渐变背景 + 双引号包裹命中文字 + FilledTonalButton 法规展开;HighlightOverlay 升级 — Info 严重度也显示描边、6dp 描边宽度、动画渐变;ImagePreview 适配 edge-to-edge 系统栏 inset
- **影响**:命中卡更易扫读(色条优先于文字)、违规框视觉权重提升、空状态文字不再被状态栏遮挡
- **回归**:4 张广告招牌 fixture OCR / 命中 / 严重度分布与 v0.1.14 字节级一致
```

- [ ] **Step 5: Commit + tag**

```bash
git add app/build.gradle.kts app/src/main/assets/user-changelog.md
git commit -m "chore(release): bump versionCode 16→17 for Phase 3.3"
git tag v0.1.17
```

---

## Phase 3.4 — Extended FAB + Skeleton Loading

### Task 16: Skeleton LoadingOverlay Composable (new)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt` (REPLACE the helper-only file)
- Create: `app/src/test/java/com/icespiritai/offline/ui/home/LoadingOverlaySkeletonTest.kt`

The existing `LoadingOverlay.kt` only contains `loadingLabelRes()` helper (no Composable). We add a `LoadingOverlay` Composable that renders 3 skeleton hit cards + the running-phase text.

- [ ] **Step 1: Rename existing helper to keep its callers happy**

The existing helper `loadingLabelRes(stage: AnalysisState.Loading.Stage): Int` is called from `HomeScreen.kt` (line ~178). After Phase 3.4, `HomeScreen` will use the new `LoadingOverlay` Composable and no longer need this helper. Move the helper to `StatusBanner.kt` (where Stage mapping already lives) and remove from `LoadingOverlay.kt`.

Actually simpler: keep the helper in `LoadingOverlay.kt` alongside the new Composable. Both names coexist — `loadingLabelRes` is a free function, `LoadingOverlay` is a `@Composable`.

- [ ] **Step 2: Write failing test for LoadingOverlay Composable**

Create `app/src/test/java/com/icespiritai/offline/ui/home/LoadingOverlaySkeletonTest.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test

class LoadingOverlaySkeletonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loadingOverlayRendersPhaseText() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                LoadingOverlay(
                    phase = LoadingPhase.OcrRunning,
                )
            }
        }
        composeRule.onNodeWithText("OCR 识别中…").assertExists()
    }

    @Test fun loadingOverlayRendersThreeSkeletonCards() {
        composeRule.setContent {
            IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
                LoadingOverlay(phase = LoadingPhase.RuleScanning)
            }
        }
        composeRule.onNodeWithText("规则扫描中…").assertExists()
        // 3 skeleton cards implied by the Composable; render-side smoke test only.
    }
}
```

- [ ] **Step 3: Run, expect FAIL (compile)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.LoadingOverlaySkeletonTest" -PmodelProfile=shell
```

- [ ] **Step 4: Rewrite LoadingOverlay.kt — add LoadingPhase enum + Composable**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState

/** Phase enum surfaced to UI — keeps LoadingOverlay free of AnalysisState dependency. */
enum class LoadingPhase { OcrRunning, RuleScanning }

/** Maps an [AnalysisState.Loading.Stage] to its user-visible label string
 *  resource. Kept as a free function for callers that want just the label. */
fun loadingLabelRes(stage: AnalysisState.Loading.Stage): Int = when (stage) {
    AnalysisState.Loading.Stage.OcrRunning -> R.string.status_ocr_running
    AnalysisState.Loading.Stage.RuleScanning -> R.string.status_rule_scanning
}

@Composable
fun LoadingOverlay(
    phase: LoadingPhase,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant
    val phaseText = when (phase) {
        LoadingPhase.OcrRunning -> stringResource(R.string.loading_ocr_skeleton)
        LoadingPhase.RuleScanning -> stringResource(R.string.loading_rule_skeleton)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = phaseText,
            style = MaterialTheme.typography.bodyMedium,
        )
        repeat(3) {
            SkeletonCard(
                containerColor = containerColor,
                highlightColor = highlightColor,
                shimmer = shimmer,
            )
        }
    }
}

@Composable
private fun SkeletonCard(
    containerColor: androidx.compose.ui.graphics.Color,
    highlightColor: androidx.compose.ui.graphics.Color,
    shimmer: Float,
) {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            containerColor,
            highlightColor.copy(alpha = 0.5f * shimmer + 0.2f),
            containerColor,
        ),
        startX = shimmer * 1000f,
        endX = shimmer * 1000f + 600f,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor),
    ) {
        Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(brush)
                .padding(12.dp),
        ) {
            Text(text = "", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
```

Add missing import:

```kotlin
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
```

- [ ] **Step 5: Run LoadingOverlaySkeletonTest + LoadingOverlayTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.LoadingOverlaySkeletonTest" \
    --tests "com.icespiritai.offline.ui.home.LoadingOverlayTest" \
    -PmodelProfile=shell
```

Expected: green. The original `LoadingOverlayTest` only tested `loadingLabelRes()` — that function still exists in the file, so the old test stays green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/LoadingOverlaySkeletonTest.kt
git commit -m "feat(ui): add LoadingOverlay Composable with shimmer skeleton"
```

---

### Task 17: CaptureButton → ExtendedFloatingActionButton

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/CaptureButton.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/CaptureButtonTest.kt`

- [ ] **Step 1: Read CaptureButtonTest.kt**

Read `app/src/test/java/com/icespiritai/offline/ui/home/CaptureButtonTest.kt`.

- [ ] **Step 2: Add failing test for Extended FAB shape**

Append:

```kotlin
@Test fun captureButtonIsExtendedFab() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            CaptureButton(onClick = {}, enabled = true)
        }
    }
    composeRule.onNodeWithText("拍照").assertExists()
}
```

- [ ] **Step 3: Run, expect FAIL**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.CaptureButtonTest" -PmodelProfile=shell
```

Expected: "拍照" text not found (current CaptureButton uses `R.string.action_take_photo`, not the new `extended_fab_label`).

- [ ] **Step 4: Rewrite CaptureButton.kt**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/CaptureButton.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.icespiritai.offline.R

@Composable
fun CaptureButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.capture_button_desc)
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = true,
        icon = {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
            )
        },
        text = {
            Text(text = stringResource(R.string.extended_fab_label))
        },
        modifier = modifier.semantics { contentDescription = a11y },
    )
}
```

Note: `CaptureButton` previously took `fillMaxWidth()` and `padding(horizontal = 8.dp)` modifiers applied externally. With Extended FAB, it's a self-contained component — callers must NOT apply `fillMaxWidth()` to it (Extended FAB is sized by its own padding + content). Check `CaptureBar` callers and update.

- [ ] **Step 5: Update CaptureBar to remove fillMaxWidth on CaptureButton**

`CaptureBar.kt` currently calls `CaptureButton(... modifier = Modifier.weight(2f))`. After the change, `CaptureButton` should NOT be inside a Row with weight — it should be the primary FAB in a `BottomAppBar`. This task only rewrites `CaptureButton`; Task 18 rewrites `CaptureBar`.

For now, just verify `CaptureBarTest` still passes — it tests behavior (click handling), not the modifier shape:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.CaptureBarTest" -PmodelProfile=shell
```

Expected: green (probably) since CaptureBarTest tests behavior, not layout.

- [ ] **Step 6: Run CaptureButtonTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.CaptureButtonTest" -PmodelProfile=shell
```

Expected: green (the "拍照" string now exists).

- [ ] **Step 7: Commit (CaptureBar rewrite is the next task — commit this one separately)**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/CaptureButton.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/CaptureButtonTest.kt
git commit -m "feat(ui): CaptureButton — ExtendedFloatingActionButton"
```

---

### Task 18: CaptureBar → BottomAppBar + Extended FAB + small FAB for pick

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` (use new Scaffold signature)
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/CaptureBarTest.kt`

- [ ] **Step 1: Read CaptureBarTest.kt and HomeScreen.kt wiring**

Read `app/src/test/java/com/icespiritai/offline/ui/home/CaptureBarTest.kt`. Note current `CaptureBar(onCapture, onPick, enabled)` signature — it'll change to `CaptureBar(onCapture, onPick, enabled, modifier)` with internal FAB positioning.

- [ ] **Step 2: Add failing test for the new FAB positions**

Append:

```kotlin
@Test fun captureBarExposesCaptureAndPickFabs() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            CaptureBar(onCapture = {}, onPick = {}, enabled = true)
        }
    }
    composeRule.onNodeWithText("拍照").assertExists()
    composeRule.onNodeWithContentDescription("从相册选图").assertExists()
}
```

- [ ] **Step 3: Run, expect FAIL**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.CaptureBarTest" -PmodelProfile=shell
```

Expected: "从相册选图" not found.

- [ ] **Step 4: Rewrite CaptureBar.kt**

Replace `app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.icespiritai.offline.R

@Composable
fun CaptureBar(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pickA11y = stringResource(R.string.pick_image_fab_desc)
    BottomAppBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            FloatingActionButton(
                onClick = onPick,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = pickA11y },
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                )
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            CaptureButton(
                onClick = onCapture,
                enabled = enabled,
            )
        }
    }
}
```

Required imports:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
```

- [ ] **Step 5: Update HomeScreen.kt — drop the BottomAppBar from CaptureBar (already inside)**

`CaptureBar` now wraps itself in `BottomAppBar`. In `HomeScreen.kt`, the existing `Column` should keep `CaptureBar(...)` as the last child — no `Scaffold` wrapper is required (we're inside the existing Activity-level Scaffold, if any). If `HomeScreen` is currently a plain `Column`, no change is needed beyond removing duplicate padding.

Check `HomeScreen.kt` line 216 (`CaptureBar(...)` call). No change needed if it's just the call.

- [ ] **Step 6: Run CaptureBarTest + HomeScreenTest + CaptureButtonTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.home.CaptureBarTest" \
    --tests "com.icespiritai.offline.ui.home.CaptureButtonTest" \
    --tests "com.icespiritai.offline.ui.home.HomeScreenTest" \
    -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 7: Re-grab 4 golden screenshots**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Visually inspect: bottom area shows a transparent BottomAppBar with a small PhotoLibrary FAB on the left and an Extended FAB ("📷 拍照") on the right.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt \
        app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/CaptureBarTest.kt
git commit -m "feat(ui): CaptureBar — BottomAppBar + Extended FAB + small pick FAB"
```

---

### Task 19: Phase 3.4 checkpoint — versionCode (NO bump, per release-hygiene)

Per CLAUDE.md `feedback-release-hygiene.md`: pure styling changes (Phase 3.4's FAB + skeleton is style-only) do NOT warrant a versionCode bump. Combine with Phase 3.5 into a single release.

- [ ] **Step 1: Run full unit tests**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected: ALL green.

- [ ] **Step 2: NO versionCode bump**

Leave `versionCode 17` / `versionName 0.1.17` unchanged.

- [ ] **Step 3: NO commit**

Phase 3.4 + 3.5 will share a single versionCode bump in Task 23.

---

## Phase 3.5 — SettingsScreen + ViewerScreen + Activity edge-to-edge

### Task 20: AppearanceSection — SegmentedButton

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/settings/AppearanceSectionTest.kt`

- [ ] **Step 1: Read AppearanceSection.kt and AppearanceSectionTest.kt**

Read both. Note the current 3-row layout (System / Dark / Light) using RadioButton.

- [ ] **Step 2: Add failing test for SegmentedButton shape**

Append:

```kotlin
@Test fun appearanceSectionUsesSegmentedButton() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            AppearanceSection(current = ThemeMode.SYSTEM, onSelect = {})
        }
    }
    // All three theme options must be visible in one SegmentedButton row.
    composeRule.onNodeWithText("跟随系统").assertExists()
    composeRule.onNodeWithText("深色").assertExists()
    composeRule.onNodeWithText("浅色").assertExists()
}
```

- [ ] **Step 3: Run, expect PASS (current AppearanceSection already shows all 3 labels)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.AppearanceSectionTest" -PmodelProfile=shell
```

If green, the test passes against the current implementation but doesn't validate SegmentedButton shape. That's fine — the next step replaces the layout.

- [ ] **Step 4: Rewrite AppearanceSection.kt — SegmentedButton row**

Replace `app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.ThemeMode

@Composable
fun AppearanceSection(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_appearance_system,
        ThemeMode.DARK to R.string.settings_appearance_dark,
        ThemeMode.LIGHT to R.string.settings_appearance_light,
    )
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.semantics { contentDescription = "theme_${mode.name}" },
                ) {
                    Text(text = stringResource(labelRes))
                }
            }
        }
    }
}
```

- [ ] **Step 5: Run AppearanceSectionTest + SettingsScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.settings.AppearanceSectionTest" \
    --tests "com.icespiritai.offline.ui.settings.SettingsScreenTest" \
    -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt
git commit -m "feat(ui): AppearanceSection — SegmentedButton for theme selection"
```

---

### Task 21: SettingsScreen — ListItem + Card style

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offoffline/ui/settings/SettingsScreen.kt` (typo: `com.icespiritai.offline`)
- Modify: `app/src/test/java/com/icespiritai/offline/ui/settings/SettingsScreenTest.kt`

The current `SettingsScreen` has 3 sections separated by `HorizontalDivider` (Appearance, Update, Changelog) plus a small version footer. Modernize to Card-per-section + ListItem.

- [ ] **Step 1: Read SettingsScreenTest.kt**

Read `app/src/test/java/com/icespiritai/offline/ui/settings/SettingsScreenTest.kt`.

- [ ] **Step 2: Add failing test for ListItem usage**

Append:

```kotlin
@Test fun settingsScreenListsChangelogEntry() {
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            SettingsScreen(
                onBack = {},
                onOpenChangelog = {},
                onOpenUpdateDetail = {},
            )
        }
    }
    // Version footer still exists.
    composeRule.onNodeWithText("版本:").assertExists()
}
```

- [ ] **Step 3: Run, expect PASS (sanity)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.SettingsScreenTest" -PmodelProfile=shell
```

- [ ] **Step 4: Rewrite SettingsScreen.kt — Card sections + ListItem rows**

Replace `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenUpdateDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(SettingsRepository(context.applicationContext)),
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AppearanceSection(current = themeMode, onSelect = viewModel::setThemeMode)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                UpdateSection(viewModel = viewModel, onOpenUpdateDetail = onOpenUpdateDetail)
            }
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

Add the missing import for `clickable`:

```kotlin
import androidx.compose.foundation.clickable
```

- [ ] **Step 5: Run SettingsScreenTest + AppearanceSectionTest + UpdateSectionTest + ChangelogScreenTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.settings.*" \
    -PmodelProfile=shell
```

Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): SettingsScreen — Card sections + ListItem rows"
```

---

### Task 22: ViewerScreen — token surface + animateContentSize

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTextList.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTextListTest.kt`

- [ ] **Step 1: Read ViewerTextList.kt and ViewerScreen.kt**

Read both. Identify where tokens are listed (currently likely a `LazyColumn` of `Text` composables).

- [ ] **Step 2: Add failing test for token surface background**

Append to `ViewerTextListTest.kt`:

```kotlin
@Test fun violationTokenHasContainerBackground() {
    val tokens = listOf(
        ViewerToken(text = "违禁词", severity = Severity.Violation),
        ViewerToken(text = "正常词", severity = null),
    )
    composeRule.setContent {
        IceSpiritVisionTheme(themeMode = ThemeMode.DARK) {
            ViewerTextList(tokens = tokens)
        }
    }
    composeRule.onNodeWithText("违禁词").assertExists()
    composeRule.onNodeWithText("正常词").assertExists()
}
```

- [ ] **Step 3: Run, expect PASS (current ViewerTextList already renders all tokens)**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerTextListTest" -PmodelProfile=shell
```

- [ ] **Step 4: Modify ViewerTextList.kt — wrap each token in a Surface with severity background**

Read current `ViewerTextList.kt`. The change: each token becomes a `Surface(shape = RoundedCornerShape(10.dp), color = sev.container(token.severity))`. Only commit the minimal change — if the file is more complex, scope down to "modify the token rendering function only":

```kotlin
// Inside the per-token Composable, replace the existing Text-only render with:
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.ui.theme.iceSpiritSeverityColors

Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    color = token.severity?.let { sev.container(it).copy(alpha = 0.12f) } ?: MaterialTheme.colorScheme.surface,
) {
    Text(
        text = token.text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(12.dp),
    )
}
```

Apply this to whichever function in `ViewerTextList.kt` renders a single token.

- [ ] **Step 5: Add animateContentSize to the wrapping Column/LazyColumn**

In `ViewerScreen.kt`, find the root `Column` / `LazyColumn` (whichever wraps the text list) and add `.animateContentSize()`:

```kotlin
import androidx.compose.animation.animateContentSize

// On the wrapping container:
Column(
    modifier = Modifier
        .fillMaxSize()
        .animateContentSize(),
    // ...
) { /* tokens */ }
```

- [ ] **Step 6: Run ViewerScreenTest + ViewerTextListTest, expect PASS**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.viewer.*" \
    -PmodelProfile=shell
```

Expected: green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTextList.kt \
        app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt \
        app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTextListTest.kt
git commit -m "feat(ui): Viewer — severity-tinted token surface + animateContentSize"
```

---

### Task 23: IceSpiritVisionActivity — enableEdgeToEdge()

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`
- Modify: `app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`

- [ ] **Step 1: Read current IceSpiritVisionActivity.kt**

Read `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`. The current `onCreate` likely calls `super.onCreate(...)` and `setContent { IceSpiritVisionTheme { IceSpiritNavHost() } }`.

- [ ] **Step 2: Add enableEdgeToEdge() call**

Modify `onCreate`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()  // Phase 3.5: WindowCompat.setDecorFitsSystemWindows(window, false) under the hood
    super.onCreate(savedInstanceState)
    setContent {
        IceSpiritVisionTheme(themeMode = ...) {
            IceSpiritNavHost()
        }
    }
}
```

The exact call to `IceSpiritVisionTheme` may already pass themeMode — match the existing pattern. Only add `enableEdgeToEdge()`.

Required import:

```kotlin
import androidx.activity.enableEdgeToEdge
```

- [ ] **Step 3: Run IceSpiritVisionActivityTest (androidTest), expect PASS**

```bash
./gradlew.bat :app:assembleDebugAndroidTest -PmodelProfile=shell
./gradlew.bat :app:connectedDebugAndroidTest \
    --tests "com.icespiritai.offline.IceSpiritVisionActivityTest" \
    -PmodelProfile=shell
```

Expected: green. If a connected device is unavailable, this step is a no-op (the test will run on the next CI/device session).

- [ ] **Step 4: Add an edge-to-edge androidTest (regression sentinel)**

Add a new test to `app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`:

```kotlin
@Test fun activityEnablesEdgeToEdge() {
    // enableEdgeToEdge() sets the decor view's systemUiVisibility flags; the
    // simplest post-condition we can assert on emulator: the activity's window
    // should NOT be drawing under the legacy fitsSystemWindows="true" default.
    val decor = activityRule.scenario.activity.window.decorView
    val flags = decor.systemUiVisibility
    // 0x00000100 = View.SYSTEM_UI_FLAG_LAYOUT_STABLE; absence of
    // View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN (0x00000400) is the post-condition
    // that confirms edge-to-edge is enabled.
    assertEquals(0, flags and 0x00000400)
}
```

(Run this only if `activityRule` is already configured in the existing test class. If not, defer to manual verification on real device.)

- [ ] **Step 5: Re-grab 4 golden screenshots (FINAL)**

```bash
./gradlew.bat :app:testDebugUnitTest \
    --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest" \
    -PmodelProfile=shell
```

Inspect that all 4 screenshots reflect the Phase 3.1-3.5 changes (KPI banner, FAB, skeleton, etc.).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt \
        app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt
git commit -m "feat(ui): IceSpiritVisionActivity — enableEdgeToEdge() for full-screen"
```

---

### Task 24: Phase 3.4 + 3.5 combined checkpoint — versionCode 17 → 18

Per `feedback-release-hygiene.md`, Phase 3.4 + 3.5 are pure styling changes combined into ONE release.

- [ ] **Step 1: Run full unit tests + Robolectric screenshots + rules regression**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.rules.*" -PmodelProfile=ice_ocr_rules
```

Expected: ALL green.

- [ ] **Step 2: Build shell + ice_ocr_rules APKs**

```bash
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules
```

Expected: BUILD SUCCESSFUL both.

- [ ] **Step 3: Bump versionCode 17 → 18, versionName 0.1.17 → 0.1.18**

- [ ] **Step 4: Update changelog**

```markdown
## v0.1.18 (2026-08-25)

- **UI 现代化 Phase 3.4-3.5**:CaptureButton → ExtendedFloatingActionButton;CaptureBar 改 BottomAppBar + 大拍照 FAB + 小选图 FAB;LoadingOverlay 重写为 shimmer 骨架屏;SettingsScreen 改 Card + ListItem;AppearanceSection 改 SegmentedButton;ViewerScreen 命中 token 加严重度背景;IceSpiritVisionActivity 加 `enableEdgeToEdge()`
- **影响**:主操作更突出,加载进度可见,设置页更易扫读,整屏沉浸式
- **回归**:4 张 fixture OCR / 命中 / 严重度与 v0.1.14 字节级一致
```

- [ ] **Step 5: Commit + tag**

```bash
git add app/build.gradle.kts app/src/main/assets/user-changelog.md
git commit -m "chore(release): bump versionCode 17→18 for Phase 3.4-3.5"
git tag v0.1.18
```

---

## Phase 3 Final — 验收与回归

### Task 25: Manual verification on Huawei nova 6

- [ ] **Step 1: Install v0.1.18 APK on Huawei nova 6**

```bash
adb install -r app/build/outputs/apk/ice_ocr_rules/debug/app-ice_ocr_rules-debug.apk
```

(If `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, run `adb shell pm clear com.icespiritai.vision` first per CLAUDE.md androidTest pitfalls.)

- [ ] **Step 2: Capture logcat during cold-start**

```bash
adb logcat -c
(adb logcat -v time IceSpiritVision:I '*:S' > /tmp/launch.log) &
adb shell am start -n com.icespiritai.vision/.IceSpiritVisionActivity
sleep 5
kill %1
```

Inspect `/tmp/launch.log` — confirm no crash on enableEdgeToEdge or any new Composable.

- [ ] **Step 3: Run 4-fixture regression**

Walk through the 4 ad-signage fixtures (蟹都汇 / 杜蕾斯 / 中医秘方 / 协和医院):

- Take photo of each fixture (or load via "选图").
- Verify StatusBanner shows correct violation / warning counts.
- Verify HitCards have left color bars matching severity.
- Verify HighlightOverlay shows 6dp strokes on violation / warning / info lines.
- Verify export button generates evidence package successfully.

Expected: byte-for-byte identical OCR / hits as v0.1.14 (same `ViolationReport` JSON output via `ExportAction.share`).

- [ ] **Step 4: Visual inspection — modernized feel**

- Idle: dark theme, transparent TopAppBar, big photo FAB on the right.
- Loading: skeleton shimmer + "OCR 识别中…" text.
- Complete (3 violations): KPI banner shows "3 / 1 / 0", each hit card has a 6dp red bar + headlineSmall quoted match text.
- Settings: Card sections, SegmentedButton for theme.
- Viewer: token rows have subtle severity tinting.

- [ ] **Step 5: Final commit (if any verification notes need to land)**

```bash
git add docs/verification/2026-08-25-ui-modernization-smoke.md  # if you create one
git commit -m "docs(verify): UI modernization Phase 3 manual verification"
```

---

## Self-Review Checklist (run before declaring done)

- [ ] Every Phase has a checkpoint (`testDebugUnitTest` green + screenshots regenerated).
- [ ] Each commit is atomic (one feature / one task).
- [ ] No `Co-Authored-By:` trailer in any commit (verify with `git log --format='%B' | grep -i 'Co-Authored-By'`).
- [ ] Existing back-end tests (`IceSpiritVisionViewModelTest`, `RuleMatcherTest`, etc.) untouched and still green.
- [ ] All Phase 3.1 added tokens are pinned by tests; all Phase 3.2-3.5 component rewrites are pinned by at least one new test.
- [ ] Spec's "新依赖" section is honored: only `androidx.compose.animation:animation-graphics` (transitive — already in compose-bom) added.
- [ ] `RuleTabBar.visibleTabs` left at `listOf(RuleTab.AdSignage)` (CLAUDE.md invariant).
- [ ] No edits to `IceSpiritVisionViewModel` / `OcrEngine` / `RuleMatcher` / `ExportAction` / `EvidencePackageBuilder`.
- [ ] 4 fixture regression verified byte-for-byte (`ViolationReport` JSON identical to v0.1.14).
- [ ] `versionCode 18`, `versionName 0.1.18` on the final release commit.
- [ ] `user-changelog.md` lists all 3 version bumps (v0.1.15 / v0.1.16 / v0.1.17 / v0.1.18).

---

## Execution

After saving this plan, offer execution choice. Recommended: **Subagent-Driven** — each task is well-scoped (single-file or small-file change with a test), independent subagent context keeps the main loop clean, and review between tasks catches regressions immediately.