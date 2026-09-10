# 食品标签功能启用 + 设置层可见性开关 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 启用食品标签 tab(完整对齐广告招牌 UI)+ 设置层「功能可见性」开关(默认全开 + VM enforce 至少一个可见)+ `food_label_rules.json` v4 → v5(~95 条)两阶段扩写,落地 v0.1.69 + v0.1.70。

**Architecture:** 数据层 → ViewModel 层 → UI 层三段 TDD 推进。DataStore `visible_features: Set<RuleTab>` 持久化,ViewModel enforce 边界,UI 接收注入。规则扩写按法规来源分 4 组,每组走 `/add-rule-entry` skill 落地。端到端沿用 audit71 harness + `/fixture-audit-add` skill。

**Tech Stack:** Kotlin 2.4.10 / Compose / Coroutines Flow / DataStore / JUnit + Truth + Robolectric / AGP 9.3 / Gradle 9.7 / JDK 17。

**Spec:** [`docs/superpowers/specs/2026-09-10-food-labeling-feature-design.md`](../specs/2026-09-10-food-labeling-feature-design.md)(commit `74e07cc`,2026-09-10)。本 plan 是 spec 的执行级分解。

---

## File Structure

| 文件 | 改动 | 职责 |
|---|---|---|
| **新增** | | |
| `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelTest.kt` | 新 | `setFeatureVisible` 三 case + DataStore 兜底 + 写入失败 |
| `app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryTest.kt` | 新 | DataStore 写入 / 读取 / 损坏兜底 |
| `app/src/androidTest/java/com/icespiritai/offline/rules/FoodLabelAudit{N}ImageE2ETest.kt` | 新 | 真机 e2e harness(N = v0.1.69 阶段 fixture 数) |
| `app/src/androidTest/assets/fixtures/food_label_audit{N}/coverage_matrix.md` | 新 | fixture ↔ rule_id 命中表 |
| `docs/smoke/2026-09-XX-food-labeling-v0.1.69-e2e.md` | 新 | 真机烟测记录 |
| **改动** | | |
| `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt` | +35 行 | VISIBLE_FEATURES key + Flow + setter |
| `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt` | +50 行 | visibleFeatures + setFeatureVisible + snackbar |
| `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt` | +30 行 | visibleFeatures StateFlow + setTab race 校验 |
| `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt` | 改签名 | `visibleTabs` 参数 + `RuleTab.tabIcon` |
| `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` | +5 行 | 调用 RuleTabBar 注入 visibleTabs |
| `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt` | +60 行 | 新 Card「功能可见性」 |
| `app/src/main/assets/rules/food_label_rules.json` | 改 version + +30 条 | v4 → v5 |
| `app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleMatcherTest.kt` | 扩 ~10 条 | v5 新规则断言 |
| `app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleLoaderTest.kt` | 扩 1 条 | v5 反序列化版本号 |
| `app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt` | 扩 3 条 | visibleTabs 参数化 + PILL_LEADING_ICON per-tab |
| `app/src/test/java/com/icespiritai/offline/IceSpiritVisionViewModelTabTest.kt` | 扩 4 条 | visibleFeatures 路由 + enforce 边界 |
| `CLAUDE.md` | 改 3 处 | ad_signage 数字(129→189)+ 食品标签 KB 描述 + tab 启用声明 |
| `知识库/食品标签/README.md` | 加 section | v0.1.69 / v0.1.70 Changelog |
| `app/src/main/res/values/strings.xml` | 加 2 条 | 「功能可见性」Card title + 「至少保留一个」 snackbar 文案 |

---

## Phase 1 — 数据 + ViewModel 层(TDD)

### Task 1: `SettingsRepository` — VISIBLE_FEATURES 持久化

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`
- Create: `app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

`SettingsRepositoryTest.kt`:

```kotlin
package com.icespiritai.offline.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class SettingsRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDataStore = TestDataStore(emptyPreferences())

    private fun repo() = SettingsRepository(testDataStore, testDispatcher)

    @Test fun `visibleFeatures defaults to all tabs when key missing`() = testScope.runTest {
        assertThat(repo().visibleFeatures.first()).isEqualTo(RuleTab.entries.toSet())
    }

    @Test fun `visibleFeatures roundtrips via setVisibleFeatures`() = testScope.runTest {
        val r = repo()
        r.setVisibleFeatures(setOf(RuleTab.AdSignage))
        assertThat(r.visibleFeatures.first()).isEqualTo(setOf(RuleTab.AdSignage))
    }

    @Test fun `visibleFeatures falls back to all tabs when DataStore throws`() = testScope.runTest {
        val brokenStore = ThrowingDataStore(IOException("disk full"))
        val r = SettingsRepository(brokenStore, testDispatcher)
        assertThat(r.visibleFeatures.first()).isEqualTo(RuleTab.entries.toSet())
    }

    @Test fun `visibleFeatures falls back when persisted enum name is stale`() = testScope.runTest {
        testDataStore.edit { it[stringSetPreferencesKey("visible_features")] = setOf("OldTabName") }
        assertThat(repo().visibleFeatures.first()).isEqualTo(RuleTab.entries.toSet())
    }
}
```

(test helpers `TestDataStore` / `ThrowingDataStore` 在 Step 5 引入。)

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsRepositoryTest"
```

Expected: COMPILATION FAILURE — `SettingsRepository` constructor signature 不匹配。

- [ ] **Step 3: 扩 `SettingsRepository` 构造函数 + visibleFeatures**

`SettingsRepository.kt`(顶部 imports + class 改):

```kotlin
package com.icespiritai.offline.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val VISIBLE_FEATURES = stringSetPreferencesKey("visible_features")

    val visibleFeatures: Flow<Set<RuleTab>> = dataStore.data
        .catch {
            Log.w("SettingsRepository", "DataStore 读取异常,fallback 默认全开", it)
            emit(emptyPreferences())
        }
        .map { prefs ->
            val raw = prefs[VISIBLE_FEATURES]
                ?: return@map RuleTab.entries.toSet()
            raw.mapNotNullTo(mutableSetOf()) { name ->
                RuleTab.entries.firstOrNull { it.name == name }
            }.ifEmpty {
                Log.w("SettingsRepository", "visible_features 反序列化空集, fallback 默认全开")
                RuleTab.entries.toSet()
            }
        }

    suspend fun setVisibleFeatures(value: Set<RuleTab>) {
        dataStore.edit { it[VISIBLE_FEATURES] = value.map(RuleTab::name).toSet() }
    }

    // ... 既有 themeMode / disclaimerAcceptedAt 不变
}
```

- [ ] **Step 4: 加 TestDataStore / ThrowingDataStore helper**

`app/src/test/java/com/icespiritai/offline/settings/TestDataStore.kt`:

```kotlin
package com.icespiritai.offline.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope

internal fun TestDataStore(initial: Preferences): DataStore<Preferences> {
    val flow = MutableStateFlow(initial)
    return object : DataStore<Preferences> {
        override val data = flow
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(flow.value).also { flow.value = it }
    }
}

internal fun ThrowingDataStore(throwable: Throwable): DataStore<Preferences> {
    return object : DataStore<Preferences> {
        override val data = kotlinx.coroutines.flow.flow {
            throw throwable
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            throw throwable
        }
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsRepositoryTest"
```

Expected: 4/4 PASS。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt \
        app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryTest.kt \
        app/src/test/java/com/icespiritai/offline/settings/TestDataStore.kt
git commit -m "feat(settings): DataStore 持久化 visible_features:Set<RuleTab>"
```

---

### Task 2: `SettingsViewModel` — `setFeatureVisible` enforce + snackbar

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

`SettingsViewModelTest.kt`:

```kotlin
package com.icespiritai.offline.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDataStore = TestDataStore(
        androidx.datastore.preferences.core.emptyPreferences()
    )
    private lateinit var repo: SettingsRepository
    private lateinit var vm: SettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = SettingsRepository(testDataStore, testDispatcher)
        vm = SettingsViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `setFeatureVisible enable always writes`() = runTest(testDispatcher) {
        vm.setFeatureVisible(RuleTab.FoodLabeling, true)
        assertThat(repo.visibleFeatures.first()).containsExactly(
            RuleTab.AdSignage, RuleTab.FoodLabeling
        )
    }

    @Test fun `setFeatureVisible disable non-last writes`() = runTest(testDispatcher) {
        vm.setFeatureVisible(RuleTab.FoodLabeling, false)
        assertThat(repo.visibleFeatures.first()).containsExactly(RuleTab.AdSignage)
    }

    @Test fun `setFeatureVisible disable last emits LastFeatureCannotHide`() = runTest(testDispatcher) {
        vm.setFeatureVisible(RuleTab.FoodLabeling, false)
        vm.snackbar.test {
            vm.setFeatureVisible(RuleTab.AdSignage, false)
            val msg = awaitItem()
            assertThat(msg).isInstanceOf(SettingsSnackbar.LastFeatureCannotHide::class.java)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.visibleFeatures.first()).containsExactly(RuleTab.AdSignage)
    }

    @Test fun `setFeatureVisible persists failure emits PersistFailed`() = runTest(testDispatcher) {
        val throwingRepo = SettingsRepository(ThrowingDataStore(IOException("disk full")), testDispatcher)
        val throwingVm = SettingsViewModel(throwingRepo)
        throwingVm.snackbar.test {
            throwingVm.setFeatureVisible(RuleTab.FoodLabeling, false)
            val msg = awaitItem()
            assertThat(msg).isInstanceOf(SettingsSnackbar.PersistFailed::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelTest"
```

Expected: COMPILATION FAILURE — `SettingsViewModel` 缺 `setFeatureVisible` + `snackbar` + `SettingsSnackbar`。

- [ ] **Step 3: 加 `SettingsSnackbar` + `snackbar` + `setFeatureVisible` 到 SettingsViewModel**

`SettingsViewModel.kt`(在 `themeMode` StateFlow 之后加):

```kotlin
sealed class SettingsSnackbar {
    object LastFeatureCannotHide : SettingsSnackbar()
    data class PersistFailed(val cause: Throwable) : SettingsSnackbar()
}

// In SettingsViewModel class:
val visibleFeatures: StateFlow<Set<RuleTab>> = settingsRepository.visibleFeatures
    .stateIn(viewModelScope, SharingStarted.Eagerly, RuleTab.entries.toSet())

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

(顶部 imports 加 `kotlinx.coroutines.flow.SharedFlow` / `asSharedFlow` / `MutableSharedFlow` / `stateIn` / `SharingStarted` + `com.icespiritai.offline.ui.home.RuleTab`)

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsViewModelTest"
```

Expected: 4/4 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt \
        app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): VM enforce 至少一个可见 + snackbar"
```

---

### Task 3: `IceSpiritVisionViewModel` — `visibleFeatures` StateFlow + `setTab` race 校验

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/IceSpiritVisionViewModelTabTest.kt`

- [ ] **Step 1: 扩 `IceSpiritVisionViewModelTabTest.kt` 写 4 条新测试**

在文件末尾追加:

```kotlin
@Test fun `setTab ignores switch to disabled tab`() = runTest {
    val disabledVisible = setOf(RuleTab.AdSignage)
    every { settingsRepository.visibleFeatures } returns flowOf(disabledVisible)
    vm = IceSpiritVisionViewModel(/* construct per existing test */)
    vm.setTab(RuleTab.FoodLabeling)
    assertThat(vm.selectedTab.value).isEqualTo(RuleTab.AdSignage) // unchanged
}

@Test fun `setTab same as enabled tab resets to Idle when not loading`() = runTest {
    every { settingsRepository.visibleFeatures } returns flowOf(RuleTab.entries.toSet())
    vm = IceSpiritVisionViewModel(/* construct per existing test */)
    vm.setPendingUri(mockUri)
    vm.startAnalysis()
    advanceUntilIdle()
    vm.setTab(RuleTab.AdSignage)
    assertThat(vm.state.value).isInstanceOf(AnalysisState.Idle::class.java)
}

@Test fun `matcherFor returns null when tab is disabled`() = runTest {
    every { settingsRepository.visibleFeatures } returns flowOf(setOf(RuleTab.AdSignage))
    vm = IceSpiritVisionViewModel(/* construct per existing test */)
    assertThat(vm.matcherFor(RuleTab.FoodLabeling)).isNull()
}

@Test fun `matcherFor returns matcher when tab is enabled`() = runTest {
    every { settingsRepository.visibleFeatures } returns flowOf(setOf(RuleTab.AdSignage))
    vm = IceSpiritVisionViewModel(/* construct per existing test */)
    assertThat(vm.matcherFor(RuleTab.AdSignage)).isNotNull()
}
```

(具体构造函数参数沿用 `IceSpiritVisionViewModelTabTest.kt` 既有 helper;`settingsRepository` mock 注入需要 factory 改造 — 参考 spec §6 兜底表 "VM 兜底破缺 UI 永不触发"。)

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.IceSpiritVisionViewModelTabTest"
```

Expected: 4 条新测试 FAIL — `visibleFeatures` 字段不存在 / `matcherFor` 不返回 null。

- [ ] **Step 3: 扩 `IceSpiritVisionViewModel`**

`IceSpiritVisionViewModel.kt`:

```kotlin
// 顶部 imports 加:
import com.icespiritai.offline.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

// class 内 fields:
val visibleFeatures: StateFlow<Set<RuleTab>> = settingsRepository.visibleFeatures
    .stateIn(viewModelScope, SharingStarted.Eagerly, RuleTab.entries.toSet())

// 改 matcherFor(tab) 加可见性校验:
fun matcherFor(tab: RuleTab): RuleMatcher? {
    val enabledTabs = visibleFeatures.value
    if (tab !in enabledTabs) {
        Log.w(TAG, "matcherFor($tab) requested but tab is disabled (visible=$enabledTabs)")
        return null
    }
    return when (tab) {
        RuleTab.AdSignage -> adSignageMatcher
        RuleTab.FoodLabeling -> foodMatcher
    }
}

// 改 setTab 加 race 校验:
fun setTab(tab: RuleTab) {
    if (tab !in visibleFeatures.value) {
        Log.w(TAG, "setTab($tab) ignored: tab is disabled")
        return
    }
    // ... 既有 3-state 契约不变
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.IceSpiritVisionViewModelTabTest"
```

Expected: 既有 3 条 + 新增 4 条 = 7 条全 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt \
        app/src/test/java/com/icespiritai/offline/IceSpiritVisionViewModelTabTest.kt
git commit -m "feat(vision): VM visibleFeatures StateFlow + setTab race 校验"
```

---

## Phase 2 — UI 层(TDD)

### Task 4: `RuleTabBar` 签名扩展 + `RuleTab.tabIcon`

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt`

- [ ] **Step 1: 扩 `RuleTabBarTest.kt` 写测试**

在文件末尾追加:

```kotlin
@Test fun `renders both tabs when visibleTabs has both`() {
    composeRule.setContent {
        RuleTabBar(
            visibleTabs = RuleTab.entries.toSet(),
            selected = RuleTab.AdSignage,
            onSelect = {},
        )
    }
    composeRule.onAllNodesWithRole(Role.Tab).assertCountEquals(2)
}

@Test fun `renders only AdSignage when FoodLabeling hidden`() {
    composeRule.setContent {
        RuleTabBar(
            visibleTabs = setOf(RuleTab.AdSignage),
            selected = RuleTab.AdSignage,
            onSelect = {},
        )
    }
    composeRule.onAllNodesWithRole(Role.Tab).assertCountEquals(1)
}

@Test fun `renders FoodLabeling with LocalDining icon when enabled`() {
    composeRule.setContent {
        RuleTabBar(
            visibleTabs = RuleTab.entries.toSet(),
            selected = RuleTab.FoodLabeling,
            onSelect = {},
        )
    }
    composeRule.onNodeWithTag("ruleTabBar_pill_leading_icon_food_labeling")
        .assertExists()
}
```

(具体 `composeRule.setUp()` + theme provider 沿用 `RuleTabBarTest.kt` 既有;PILL_LEADING_ICON 后缀从原 `_<tabName>` 改为 `_<tabName lowerCase>`。)

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.home.RuleTabBarTest"
```

Expected: 3 条新测试 FAIL — `visibleTabs` 参数不存在 / `RuleTab.tabIcon` 不存在 / `ruleTabBar_pill_leading_icon_food_labeling` testTag 不存在。

- [ ] **Step 3: 改 `RuleTabBar.kt`**

```kotlin
package com.icespiritai.offline.ui.home

// 新增 imports:
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource

// enum 改:
enum class RuleTab(val titleRes: Int, val tabIcon: ImageVector) {
    AdSignage(R.string.tab_ad_law, Icons.Outlined.Verified),
    FoodLabeling(R.string.tab_food_label, Icons.Outlined.LocalDining),
}

// 删除 file-level visibleTabs(L55)

// Composable 签名改:
@Composable
fun RuleTabBar(
    visibleTabs: Set<RuleTab>,           // 新
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) { /* 内部 visibleTabs.forEach, 删除 file-level visibleTabs 引用 */ }

// PillTab 内 Icon 改:
// 旧: Icon(imageVector = Icons.Outlined.Verified, modifier = Modifier.size(16.dp).testTag(PILL_LEADING_ICON))
// 新: Icon(imageVector = tab.tabIcon, modifier = Modifier.size(16.dp).testTag("${PILL_LEADING_ICON}_${tab.name.lowercase()}"))
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.home.RuleTabBarTest"
```

Expected: 既有 + 3 条新 = 全 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/RuleTabBarTest.kt
git commit -m "feat(ui): RuleTabBar visibleTabs 参数 + per-tab 图标"
```

---

### Task 5: `HomeScreen` 调用 RuleTabBar 注入 visibleTabs

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`

- [ ] **Step 1: 找现有 `RuleTabBar` 调用点**

```bash
grep -rn "RuleTabBar(" app/src/main/java/com/icespiritai/offline/
```

Expected: 1 个调用点(HomeScreen.kt 内 composable)。

- [ ] **Step 2: 改调用**

`HomeScreen.kt`(`RuleTabBar` 调用处):

```kotlin
// 旧: RuleTabBar(selected = vm.selectedTab.collectAsState().value, onSelect = vm::setTab)
// 新: RuleTabBar(
//     visibleTabs = vm.visibleFeatures.collectAsState().value,
//     selected = vm.selectedTab.collectAsState().value,
//     onSelect = vm::setTab,
// )
```

具体 collectAsState pattern 沿用 HomeScreen 既有;只加 `visibleTabs` 一行。

- [ ] **Step 3: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HomeScreen*"
```

Expected: 全 PASS。

- [ ] **Step 4: 跑手动编译 smoke**

```bash
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

Expected: BUILD SUCCESSFUL;APK 产物在 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt
git commit -m "feat(ui): HomeScreen 注入 visibleTabs 到 RuleTabBar"
```

---

### Task 6: `SettingsScreen` 新 Card「功能可见性」

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 加 strings.xml 2 条**

`app/src/main/res/values/strings.xml`(在末尾追加):

```xml
<string name="settings_feature_visibility_title">功能可见性</string>
<string name="settings_feature_visibility_desc">勾选显示的功能,至少保留一个</string>
<string name="settings_feature_last_cannot_hide">至少保留一个功能可见</string>
<string name="settings_feature_persist_failed">设置保存失败</string>
<string name="settings_feature_ad_signage_label">广告招牌</string>
<string name="settings_feature_food_label_label">食品标签</string>
```

- [ ] **Step 2: 找现有 Card 结构**

```bash
grep -n "Card\|titleRes\|titleLarge\|@string/settings" app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt | head -30
```

Expected: `AppearanceSection` / `UpdateSection` / `ChangelogRow` / `Tts` 4 个 Card。

- [ ] **Step 3: 写 `FeatureVisibilitySection` Composable**

`SettingsScreen.kt`(在文件内,`TtsSection` 之后):

```kotlin
@Composable
fun FeatureVisibilitySection(
    visible: Set<RuleTab>,
    onToggle: (RuleTab, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_feature_visibility_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_feature_visibility_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RuleTab.entries.forEach { tab ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(tab.titleRes),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = tab in visible,
                        onCheckedChange = { onToggle(tab, it) },
                    )
                }
            }
        }
    }
}
```

(顶部 imports 加 `androidx.compose.material3.Switch` / `androidx.compose.foundation.layout.Row` / `weight` / `fillMaxWidth` / `androidx.compose.runtime.collectAsState` / `androidx.compose.ui.Alignment` / `com.icespiritai.offline.ui.home.RuleTab`)

- [ ] **Step 4: 在 SettingsScreen 主 Composable 内挂上**

`SettingsScreen`(`TtsSection` 调用之后):

```kotlin
FeatureVisibilitySection(
    visible = vm.visibleFeatures.collectAsState().value,
    onToggle = vm::setFeatureVisible,
)

// 同时在 LaunchedEffect 收集 vm.snackbar:
val snackbarHostState = remember { SnackbarHostState() }
LaunchedEffect(vm) {
    vm.snackbar.collect { msg ->
        when (msg) {
            SettingsSnackbar.LastFeatureCannotHide ->
                snackbarHostState.showSnackbar(
                    message = snackbarHostState.context.getString(R.string.settings_feature_last_cannot_hide)
                )
            is SettingsSnackbar.PersistFailed ->
                snackbarHostState.showSnackbar(
                    message = snackbarHostState.context.getString(R.string.settings_feature_persist_failed)
                )
        }
    }
}
// Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, ...)
```

(具体 Scaffold 改造沿用 SettingsScreen 既有 `Scaffold` 结构。)

- [ ] **Step 5: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.SettingsScreen*"
```

Expected: 全 PASS(若有测试)。

- [ ] **Step 6: 跑手动编译 smoke**

```bash
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(settings): 功能可见性 Card + VM enforce snackbar"
```

---

## Phase 3 — 文档同步

### Task 7: CLAUDE.md 顺手同步 3 处

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 改 L39 ad_signage 计数**

```markdown
| 规则 + 加载器 | `AdSignageRuleLoader` + `FoodLabelRuleLoader` 双装载入口保留;`food_label_rules.json`(66 条 / v4)+ `ad_signage_rules.json`(189 条 / v20,14 个类别:...)
```

- [ ] **Step 2: 改 L97 食品标签 KB 描述**

把"GB 7718-2011 / GB 28050-2011 / 食品标识管理规定 已 git mv 到 `知识库/已废止/`"改为:

```markdown
**KB 同步范围(本地,gitignored)**:三份 KB(`GB 7718-2011` / `GB 28050-2011` / `食品标识管理规定`)在主目录以 `_2027-03-16废止.md` 后缀存在,**过渡期现行**(2027-03-16 起失效),合规;过渡期满时统一迁移到 `知识库/已废止/`。
```

- [ ] **Step 3: 改 L49 tab 启用声明**

把 "FoodLabeling tab 入口当前**不向用户暴露**" 整段改为:

```markdown
**v0.1.69 起**:`RuleTabBar.visibleTabs` 由 `listOf(RuleTab.AdSignage)` 改为参数化 `Set<RuleTab>`,由 `IceSpiritVisionViewModel.visibleFeatures` 注入(默认 `{AdSignage, FoodLabeling}` 全开,持久化在 DataStore `visible_features` key)。用户可在设置层「功能可见性」开关禁用食品标签;`FoodLabeling` enum 项 / `FoodLabelRule*` / `matcherFor` 路由 / `CategoryDisplay.FoodLabelCategory` 完整保留。
```

- [ ] **Step 4: 跑 doc consistency 检查**

```bash
grep -n "129 条" CLAUDE.md
grep -n "已 git mv" CLAUDE.md
grep -n "当前不向用户暴露" CLAUDE.md
```

Expected: 三条 grep 都为空(改前为 3 个 hit)。

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude): 同步 ad_signage 数字 + 食品标签 KB 描述 + tab 启用声明"
```

---

### Task 8: 食品标签 KB README Changelog

**Files:**
- Modify: `知识库/食品标签/README.md`

- [ ] **Step 1: 在 README.md 末尾追加 Changelog section**

```markdown
## Changelog

### v0.1.69 (2026-09-XX)
- 规则 v4 → v5:66 → ~95 条
- 新增覆盖:GB 7718-2025 致敏原 8 强制 / 食品标识监督管理办法 §7-§40 核心 / 食品安全法 §80/§81/§125 / GB 28050 / GB 13432 / 婴幼儿乳粉重点 gap
- 真机 fixture:food_label_audit{N} (N = v0.1.69 阶段)

### v0.1.70 (2026-09-XX)
- 规则 v5 → v5.1:fixture 命中 < 60/N 时扩 +5-10 条
- (同发版号二阶段,沿用 v0.1.49 经验)
```

(发版日 2026-09-XX 由 `/project-commit` skill 自动替换为真实发版日。)

- [ ] **Step 2: Commit**

```bash
git add 知识库/食品标签/README.md
git commit -m "docs(kb): 食品标签 README v0.1.69 / v0.1.70 Changelog"
```

> ⚠️ 注意:CLAUDE.md 声明 `知识库/` 整体 .gitignore;若 README.md 不进 git,这条 commit 会 empty。fallback:**只把 Changelog 段同步到 git-tracked 的 `docs/knowledge/food-labeling-changelog.md`(新文件)**,留 cross-link 给 KB README。本任务 end-state 取决于仓库实际 gitignore 配置。

---

## Phase 4 — 规则扩写(v4 → v5)

### Task 9: `food_label_rules.json` v4 → v5(~95 条)

**Files:**
- Modify: `app/src/main/assets/rules/food_label_rules.json`
- Modify: `app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleMatcherTest.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleLoaderTest.kt`

按法规来源分 4 个 sub-task。每 sub-task 走 [`/add-rule-entry`](../../.claude/skills/add-rule-entry/SKILL.md) skill 完整流程(法规新鲜度查新 → 知识库 `<域>/<reg>.md` → rule JSON 条目 → matcher 单测 → changelog)。

#### Sub-task 9.1: GB 7718-2025 致敏原强制 + 推荐(5-8 条)

- [ ] **Step 1: 走 `/add-rule-entry` skill 扩 5-8 条**

调用 `/add-rule-entry` skill,告诉它:
- 来源:`知识库/食品标签/GB_7718-2025_致敏原强制标示.md`
- 类别:`FoodLabelCategory.allergen`
- 严重度:Violation(强制标示,缺失即违法)
- 目标 8 大类强制 + 4 类推荐(每类一 rule)

skill 输出:`food_label_rules.json` 新增 12-13 条 + matcher 单测。

- [ ] **Step 2: 跑 v5 反序列化 + 关键词断言**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.FoodLabelRuleLoaderTest" \
                                --tests "com.icespiritai.offline.rules.FoodLabelRuleMatcherTest"
```

Expected: PASS。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/rules/food_label_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleMatcherTest.kt
git commit -m "feat(rules): food_label v5 - GB 7718-2025 致敏原 8+4 强制"
```

#### Sub-task 9.2: 食品标识监督管理办法 §7-§40(12-15 条)

- [ ] **Step 1: 走 `/add-rule-entry` skill 扩 12-15 条**

调用 `/add-rule-entry` skill,告诉它:
- 来源:`知识库/食品标签/食品标识监督管理办法.md`(SAMR 令第 100 号)
- 类别:覆盖 `FoodLabelCategory` 8 项(`label_form` / `product_name` / `ingredient` / `production_date` / `net_weight` / `additive` / `functional_claim` / `specific_food`)
- 严重度:Violation(主体)+ Warning(部分)
- 目标 §7 第(一)-(四)项 + §15-§40 核心条款

skill 输出:`food_label_rules.json` 新增 12-15 条 + matcher 单测。

- [ ] **Step 2-3: 同 9.1**

#### Sub-task 9.3: 食品安全法 §80 / §81 / §125(3-5 条)

- [ ] **Step 1: 走 `/add-rule-entry` skill 扩 3-5 条**

调用 `/add-rule-entry` skill,告诉它:
- 来源:`知识库/食品标签/中华人民共和国食品安全法(第4章).md`
- 类别:`label_form` / `specific_food`
- 严重度:Violation
- 目标 §80(预包装食品标签)/ §81(婴幼儿乳粉 + 特殊膳食用)/ §125(违法处罚要点)

- [ ] **Step 2-3: 同 9.1**

#### Sub-task 9.4: GB 28050 + GB 13432 + 婴幼儿乳粉(7-13 条)

- [ ] **Step 1: 走 `/add-rule-entry` skill 扩 7-13 条**

调用 `/add-rule-entry` skill,告诉它:
- 来源:三份 KB markdown
- 类别:`nutrition` / `specific_food`
- 严重度:Violation + Warning
- 目标 GB 28050(营养成分核心 + NRV + 强化营养成分声称 5-7 条)+ GB 13432(特殊膳食用食品分类 / 适用人群 / 食用方法 3 条)+ 婴幼儿配方乳粉产品配方注册管理办法(注册号格式 / 段位声称 2-3 条)

- [ ] **Step 2-3: 同 9.1**

#### 合并 v5 顶层 version

- [ ] **Step 5: 改 `food_label_rules.json` 顶层 `version: 4` → `version: 5`**

```json
{
  "version": 5,
  "rules": [ ... ]
}
```

- [ ] **Step 6: 跑全 unit test**

```bash
./gradlew.bat testDebugUnitTest
```

Expected: 全 PASS(.claude/hooks/validate-rule-json.js 自动校验 JSON 语法 / version 整数 / rules 非空 / id 唯一)。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/assets/rules/food_label_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleLoaderTest.kt
git commit -m "feat(rules): food_label v5 顶层 version bump 4→5"
```

---

### Task 10: KB 时效性扫描(`regulation-freshness-checker` agent)

**Files:**
- (无文件改动;agent 输出 audit 报告)

- [ ] **Step 1: 调 `regulation-freshness-checker` agent**

通过 `.claude/agents/regulation-freshness-checker.md`(agent 自动 dispatch):

```
请审计 app/src/main/assets/rules/food_label_rules.json v5 全部规则的 regulation 字段,确保都引 知识库/食品标签/ 现行 KB(无 知识库/已废止/ 引用)。WebSearch 优先级:flk.npc.gov.cn > samr.gov.cn > openstd.samr.gov.cn > gov.cn。
```

Expected: agent 输出 P0/P1/P2 drift 报告;P0 必修,P1/P2 留 v0.1.71+。

- [ ] **Step 2: 修 P0 drift(若有)**

按 agent 报告修改 `food_label_rules.json` 的 `regulation` + `lawText` 字段;走 `.claude/hooks/validate-rule-json.js` 自动校验。

- [ ] **Step 3: 跑全 unit test + commit**

```bash
./gradlew.bat testDebugUnitTest
git add app/src/main/assets/rules/food_label_rules.json
git commit -m "fix(rules): food_label v5 法规新鲜度 audit 修 P0 drift"
```

---

## Phase 5 — 真机端到端

### Task 11: 食品标签 fixture 落地 + e2e 跑通

**Files:**
- Create: `app/src/androidTest/java/com/icespiritai/offline/rules/FoodLabelAudit{N}ImageE2ETest.kt`
- Create: `app/src/androidTest/assets/fixtures/food_label_audit{N}/*.jpg`
- Create: `app/src/androidTest/assets/fixtures/food_label_audit{N}/coverage_matrix.md`
- Create: `docs/smoke/2026-09-XX-food-labeling-v0.1.69-e2e.md`

走 [`/fixture-audit-add`](../../.claude/skills/fixture-audit-add/SKILL.md) skill 完整 5 阶段 workflow。

- [ ] **Step 1: 调 `/fixture-audit-add` skill**

skill 路径:`.claude/skills/fixture-audit-add/SKILL.md`

skill 内部消费:
- Stage 1-2:从 `违规案例/食品标签/` 暂存,真机 OCR 命名审核
- Stage 3:同步到 `app/src/androidTest/assets/fixtures/food_label_audit{N}/`
- Stage 4:调 `adb-runner` agent 跑 `connectedDebugAndroidTest`
- Stage 5:生成 `coverage_matrix.md`

- [ ] **Step 2: 验证 `ANY_HIT ≥ 60/N`**

skill 输出 `coverage_matrix.md`,从中聚合 `hit_count / fixture_count`;要求 ≥60%。

- [ ] **Step 3: 不达标时回到 Task 13(v0.1.70 二阶段扩展)**

---

### Task 12: v0.1.69 release pipeline

**Files:**
- (无代码改动;流水线驱动)

走 [`/icevision-release`](../../.claude/skills/icevision-release/SKILL.md) skill 完整流程。

- [ ] **Step 1: 调 `/icevision-release` skill**

skill 5 步 pre-flight:
1. JDK 17:`export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"`
2. v1 signing:`grep -n "enableV1Signing" app/build.gradle.kts` 确认 true
3. Gitea PAT:`source ~/.gradle/release-env.sh`
4. AAR+ONNX:`bash tools/download-ppocr-models.sh` (幂等跳过) + `bash tools/build-ppocr-sdk.sh` (幂等跳过)
5. cert-pin:`echo $ICESPIRITAI_RELEASE_CERT_SHA256` = `4a21f4...3043`

skill 4 步流水线:
1. `assembleRelease -PmodelProfile=ice_ocr_rules`
2. `generateVisionLatestJson`
3. `archiveVisionRelease`
4. `uploadVisionReleaseToGitea`

skill post-release 三段断言:
- tag SHA = commit SHA
- APK SHA-256 = JSON `apkSha256`
- 客户端从 `giteaadmin/vision-app` 拉取 200 + cert-pin match

- [ ] **Step 2: Release 三段式打标(走 `/project-commit` skill)**

调 `/project-commit` skill,告诉它:
- commit msg:`feat(v0.1.69): ...`
- 触发 release 三段式:versionCode bump + user-changelog.md + `git tag v0.1.69` + push `latest` ref

skill 输出:新 commit + 新 tag + JSON 更新 + APK URL。

---

## Phase 6 — Conditional(v0.1.70 同发版号二阶段)

### Task 13: v0.1.70 v5.1 二阶段扩展(仅当 ANY_HIT < 60/N)

**Files:**
- Modify: `app/src/main/assets/rules/food_label_rules.json`
- Modify: `app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleMatcherTest.kt`

- [ ] **Step 1: 确认 v0.1.69 真机 e2e 结果 < 60/N**

读 `docs/smoke/2026-09-XX-food-labeling-v0.1.69-e2e.md`,聚合 ANY_HIT。

- [ ] **Step 2: 若 < 60/N,走 `/add-rule-entry` skill 扩 +5-10 条 + 扩既有规则关键词**

按 fixture 命中 gap 优先级(参考 `rule-coverage-analyzer` agent 输出):
- 未命中 category 优先级补全
- 命中但 keyword 太严的 → 扩既有规则的 keywords(AC substring 命中兜底 OCR 漏字)
- 优先级 P0 → P1 → P2,直到 ≥60/N

- [ ] **Step 3: 改 v5.1 顶层 version**

```json
{
  "version": 51,
  "rules": [ ... ]
}
```

(`51` 而非 `5.1`,JSON 顶层 `version` 字段约定为整数;参考 `ad_signage_rules.json` 现状 `version: 20` 单调递增整数模式 — 实际项目可能用 `5.1` 字符串子版本号,以现状为准。)

- [ ] **Step 4: 跑全 unit test + 真机 e2e**

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.FoodLabelAudit{N}ImageE2ETest
```

- [ ] **Step 5: 走 `/icevision-release` skill 发版 v0.1.70**

同 Task 12。

---

## Self-Review

### Spec coverage

| Spec 章节 | 对应 Task |
|---|---|
| §1.2 目标 1(tab 解锁) | Task 4, 5 |
| §1.2 目标 2(设置层开关) | Task 1, 2, 6 |
| §1.2 目标 3(规则扩写) | Task 9 |
| §1.2 目标 4(样式层独立性) | spec §3.5/§3.6 声明(无代码改动 task — 现状合规,见 §5.5 audit) |
| §1.2 目标 5(CLAUDE.md 同步) | Task 7 |
| §1.2 目标 6(KB README Changelog) | Task 8 |
| §3.1 RuleTabBar 签名 + 图标 | Task 4 |
| §3.2 ViewModel enforce | Task 2 |
| §3.3 DataStore 持久化 | Task 1 |
| §3.4 规则扩写 v4 → v5 | Task 9 |
| §5 测试策略 | Task 1-9 各自的 unit test + Task 11 e2e |
| §6 边界 / 错误处理 | Task 1 (DataStore 兜底) + Task 2 (snackbar) + Task 3 (race 校验) |
| §7 文档同步 | Task 7 + Task 8 |
| §8 TODO(本期不实现) | (留作 follow-up,不展开) |
| §9 发版路线 | Task 11 + Task 12 + Task 13 |

### Placeholder scan

- "N = v0.1.69 阶段" — 是 fixture 数量变量,不是 placeholder
- "2026-09-XX" — 是发版日变量,由 `/project-commit` skill 自动替换
- "skill 输出:`food_label_rules.json` 新增 N 条 + matcher 单测" — 是 skill 流程指引,工程师按 skill 落地,不是 plan placeholder

无 "TBD" / "TODO" / "类似 Task N" / "Add appropriate error handling" 等。

### Type consistency

- `SettingsRepository.visibleFeatures: Flow<Set<RuleTab>>`(Task 1)— Task 2 / Task 3 / Task 6 引用一致
- `SettingsViewModel.setFeatureVisible(tab: RuleTab, visible: Boolean)`(Task 2)— Task 6 调用一致
- `IceSpiritVisionViewModel.visibleFeatures: StateFlow<Set<RuleTab>>`(Task 3)— Task 5 HomeScreen collectAsState 引用一致
- `RuleTabBar(visibleTabs: Set<RuleTab>, ...)`(Task 4)— Task 5 调用一致
- `RuleTab.tabIcon: ImageVector` + `Icons.Outlined.Verified` / `LocalDining`(Task 4)— Task 6 settings list 引用一致
- `SettingsSnackbar.LastFeatureCannotHide` + `PersistFailed`(Task 2)— Task 6 LaunchedEffect collect 引用一致

无 type drift。

---