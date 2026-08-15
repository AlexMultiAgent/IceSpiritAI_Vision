# 冰灵锐目 UI 实施计划 — 执法场景单页直入式

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重写冰灵锐目 UI 层,实现执法场景"单张即拍即审 + 顶栏 Tab + 深/浅双主题 + 高亮原图 + 导出取证包"。

**Architecture:** 单 Activity + 单 NavHost(home / settings);HomeScreen 单一 StateFlow 驱动,内嵌现有 `AnalysisState`;主题走 Activity 启动时一次性 `AppCompatDelegate.setDefaultNightMode` + Compose `MaterialTheme.colorScheme`;导出走 FileProvider + Intent.ACTION_SEND。**不动** `OcrEngine` / `RuleMatcher` / Repository(由 Phase 2 spec 锁定)。

**Tech Stack:**
- AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 37 / minSdk 26
- Compose BOM 2026.08.00 + material3
- Coil 2.7.0(图片预览)
- AndroidX Navigation Compose 2.8.0
- AndroidX DataStore Preferences 1.1.1
- material-icons-extended
- 沿用:Activity Compose / Lifecycle / Coroutines / Serialization

**Reference:**
- 设计:`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`
- 后端契约:Phase 1 spec + Phase 2 spec
- baseline 构建:`docs/knowledge/build-stack-2026-08.md`

---

## §0 实施顺序

1. **Foundation**(Tasks 1–3):升级版本目录,加依赖,加 manifest FileProvider,加 strings
2. **Theme + Settings**(Tasks 4–7):Color / Theme / ThemeMode / SettingsRepository / SettingsViewModel / SettingsScreen / NavHost / Activity 接线
3. **Home UI 组件**(Tasks 8–13):SeverityBadge / HitCard / LoadingOverlay / StatusBanner / CaptureButton / CaptureBar / ImagePreview / HighlightOverlay / ResultPanel / RuleTabBar / HomeTopBar
4. **HomeScreen 集成**(Task 14):重写 MainScreen(已集成 export 按钮)
5. **Export**(Tasks 15–16):EvidencePackageBuilder / ExportAction
6. **Tests**(Tasks 17–19):单测 / Compose UI 测试 / 截图测试

每任务独立可测、独立可 revert、独立 commit。

---

## Task 1: 加依赖 + manifest FileProvider + strings 增量

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_provider_paths.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 在 `libs.versions.toml` 加 4 个版本号 + 4 个库引用**

`gradle/libs.versions.toml`,在 `[versions]` 末尾追加:

```toml
coil = "2.7.0"
datastore = "1.1.1"
navigation = "2.8.0"
materialIconsExtended = "1.7.0"
```

在 `[libraries]` 末尾追加:

```toml
# Image loading
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

# Persistence
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Navigation
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }

# Icons
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended", version.ref = "materialIconsExtended" }
```

- [ ] **Step 2: 在 `app/build.gradle.kts` 加 4 个 implementation**

`app/build.gradle.kts`,在 `dependencies {}` 块的 // Compose 块中添加(放在 `compose-material3` 之后):

```kotlin
implementation(libs.compose.material.icons.extended)
implementation(libs.androidx.navigation.compose)
implementation(libs.coil.compose)
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: 创建 `file_provider_paths.xml`**

`app/src/main/res/xml/file_provider_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="evidence" path="evidence/" />
</paths>
```

- [ ] **Step 4: 在 `AndroidManifest.xml` 加 `<provider>` 声明**

`app/src/main/AndroidManifest.xml`,在 `<application>` 内 `<activity>` 之后追加:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_provider_paths" />
        </provider>
```

- [ ] **Step 5: 在 `strings.xml` 增量追加**

`app/src/main/res/values/strings.xml`,在 `</resources>` 之前追加:

```xml
    <string name="tab_ad_law">广告法</string>
    <string name="tab_food_label">食品标签</string>
    <string name="tab_disabled_toast">食品标签 OCR 即将上线</string>

    <string name="status_image_hint">请对正图片后点击拍照</string>
    <string name="status_no_violation_card">未发现违规用语</string>
    <string name="status_violation_count">违规 %1$d 处</string>
    <string name="status_warning_count">警告 %1$d 处</string>

    <string name="action_reshoot">重拍</string>
    <string name="action_export">导出取证包</string>
    <string name="action_export_report">导出报告</string>
    <string name="action_report_issue">上报问题</string>

    <string name="settings_title">设置</string>
    <string name="settings_appearance">外观</string>
    <string name="settings_appearance_system">跟随系统</string>
    <string name="settings_appearance_dark">深色</string>
    <string name="settings_appearance_light">浅色</string>
    <string name="settings_about">关于</string>
    <string name="settings_about_version">版本: %1$s</string>

    <string name="hit_severity_info">信息</string>
    <string name="hit_severity_warning">警告</string>
    <string name="hit_severity_violation">违规</string>

    <string name="image_preview_desc">待分析图片</string>
    <string name="capture_button_desc">拍照</string>
    <string name="select_image_button_desc">从相册选图</string>
    <string name="settings_button_desc">设置</string>
    <string name="tab_switch_desc">切换业务模式</string>

    <string name="export_share_subject">冰灵锐目 取证包</string>
    <string name="export_share_chooser">分享取证包</string>
```

- [ ] **Step 6: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`(依赖解析 + manifest 合并通过)。

- [ ] **Step 7: 提交**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/file_provider_paths.xml app/src/main/res/values/strings.xml
git commit -m "feat(ui): add Coil/Nav/DataStore deps + FileProvider + i18n strings"
```

---

## Task 2: Theme.kt — 颜色 + 字体 + MaterialTheme

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/themes.xml`

**ThemeMode 留到 Task 3,这里先做静态深/浅双 scheme + 默认 isSystemInDarkTheme 切换。**

- [ ] **Step 1: 写 `Color.kt`**

`app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.ui.graphics.Color

// Dark scheme (Deep slate — on-site enforcement)
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF020617)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkOutline = Color(0xFF334155)
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkPrimary = Color(0xFF3B82F6)
val DarkOnPrimary = Color(0xFFEFF6FF)
val DarkError = Color(0xFFF87171)
val DarkOnError = Color(0xFF7F1D1D)
val DarkWarning = Color(0xFFFBBF24)
val DarkOnWarning = Color(0xFF78350F)
val DarkSuccess = Color(0xFF86EFAC)
val DarkOnSuccess = Color(0xFF14532D)

// Light scheme (Enforcement white — archive / export)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFEFEFE)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOutline = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightPrimary = Color(0xFF1E40AF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFEE2E2)
val LightWarning = Color(0xFFD97706)
val LightOnWarning = Color(0xFFFEF3C7)
val LightSuccess = Color(0xFF16A34A)
val LightOnSuccess = Color(0xFFDCFCE7)
```

- [ ] **Step 2: 写 `Type.kt`**

`app/src/main/java/com/icespiritai/offline/ui/theme/Type.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IceSpiritTypography = Typography(
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

- [ ] **Step 3: 写 `Theme.kt`(静态双 scheme,ThemeMode 接线在 Task 4)**

`app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError,
)

@Composable
fun IceSpiritVisionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = IceSpiritTypography,
        content = content,
    )
}
```

- [ ] **Step 4: 改 `themes.xml` parent = `Theme.Material3.DayNight.NoActionBar`**

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.IceSpiritOffline" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

(`DayNight` parent 让 Activity 启动时 `AppCompatDelegate.setDefaultNightMode` 生效;Task 7 才接。)

- [ ] **Step 5: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/theme/ app/src/main/res/values/themes.xml
git commit -m "feat(ui): Material3 dark/light scheme + IceSpiritVisionTheme composable"
```

---

## Task 3: ThemeMode + SettingsRepository(DataStore)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/theme/ThemeMode.kt`
- Create: `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`

- [ ] **Step 1: 写 `ThemeMode.kt`**

`app/src/main/java/com/icespiritai/offline/ui/theme/ThemeMode.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT;

    fun toNightMode(): Int = when (this) {
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    }

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
```

- [ ] **Step 2: 写 `SettingsRepository.kt`**

`app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`:

```kotlin
package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromName(prefs[themeModeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/theme/ThemeMode.kt app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt
git commit -m "feat(ui): ThemeMode enum + SettingsRepository (DataStore)"
```

---

## Task 4: SettingsViewModel + 单测

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelTest.kt`:

```kotlin
package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `themeMode reflects repository flow`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val repo = FakeRepo(backing)
        val vm = SettingsViewModel(repo)

        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)

        backing.value = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode calls repository`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val repo = FakeRepo(backing)
        val vm = SettingsViewModel(repo)

        vm.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, backing.value)
    }

    private class FakeRepo(backing: MutableStateFlow<ThemeMode>) : SettingsRepositoryLike(backing)
}

abstract class SettingsRepositoryLike(backing: MutableStateFlow<ThemeMode>) {
    abstract val themeMode: kotlinx.coroutines.flow.Flow<ThemeMode>
    abstract suspend fun setThemeMode(mode: ThemeMode)
}
```

- [ ] **Step 2: 跑测试,确认失败**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.settings.SettingsViewModelTest"
```

Expected:COMPILE failure(`SettingsViewModel` 不存在)。

- [ ] **Step 3: 改测试用真 `SettingsRepository` 抽象**

`SettingsViewModelTest.kt` 重写:

```kotlin
package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `themeMode reflects repository flow`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val repo = FakeSettingsRepository(backing)
        val vm = SettingsViewModel(repo)

        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)

        backing.value = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode calls repository`() = runTest {
        val backing = MutableStateFlow(ThemeMode.SYSTEM)
        val repo = FakeSettingsRepository(backing)
        val vm = SettingsViewModel(repo)

        vm.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, backing.value)
    }

    private class FakeSettingsRepository(backing: MutableStateFlow<ThemeMode>) : SettingsRepository {
        override val themeMode: Flow<ThemeMode> = backing
        override suspend fun setThemeMode(mode: ThemeMode) { backing.value = mode }
    }
}
```

- [ ] **Step 4: 把 `SettingsRepository` 改成 open class**

`app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`,把 `class SettingsRepository` 改为 `open class SettingsRepository`:

```kotlin
open class SettingsRepository(protected val context: Context) {
```

(同时 `context` 改为 `protected`,这样子类可访问;`open` 让测试可继承并 override `themeMode` / `setThemeMode`。)

- [ ] **Step 5: 写 `SettingsViewModel`**

`app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`:

```kotlin
package com.icespiritai.offline.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }
}
```

- [ ] **Step 6: 跑测试,确认 PASS**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.settings.SettingsViewModelTest"
```

Expected:2 tests pass。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt app/src/test/java/com/icespiritai/offline/settings/SettingsViewModelTest.kt
git commit -m "feat(ui): SettingsViewModel + repo abstraction + unit tests"
```

---

## Task 5: SettingsRepository 单测

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: 写测试(Robolectric,真 DataStore)**

`app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryTest.kt`:

```kotlin
package com.icespiritai.offline.settings

import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @Test
    fun `default themeMode is SYSTEM`() = runTest {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun `setThemeMode persists then reads back`() = runTest {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }
}
```

- [ ] **Step 2: 跑测试,确认 PASS**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.settings.SettingsRepositoryTest"
```

Expected:2 tests pass。

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryTest.kt
git commit -m "test(ui): SettingsRepository default + persistence roundtrip"
```

---

## Task 6: AppearanceSection + SettingsScreen

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: 写 `AppearanceSection.kt`**

`app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.ThemeMode

@Composable
fun AppearanceSection(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            ThemeModeOption(ThemeMode.SYSTEM, R.string.settings_appearance_system, current, onSelect)
            ThemeModeOption(ThemeMode.DARK, R.string.settings_appearance_dark, current, onSelect)
            ThemeModeOption(ThemeMode.LIGHT, R.string.settings_appearance_light, current, onSelect)
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    labelRes: Int,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = (mode == current),
            onClick = { onSelect(mode) },
        )
        Text(
            text = stringResource(labelRes),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 2: 写 `SettingsScreen.kt`**

`app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.R
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(SettingsRepository(context.applicationContext)),
    )
    val themeMode by viewModel.themeMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_grant_permission),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppearanceSection(
                current = themeMode,
                onSelect = viewModel::setThemeMode,
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
```

- [ ] **Step 3: 给 `SettingsViewModel` 加 factory**

`SettingsViewModel.kt`,在文件末尾追加:

```kotlin
    companion object {
        fun factory(repository: SettingsRepository) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository) as T
            }
        }
    }
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`(若 `autoMirrored` 需要新版 icons,确认 `compose-material-icons-extended` 1.7.0 已含)。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/ app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt
git commit -m "feat(ui): SettingsScreen + AppearanceSection with theme picker"
```

---

## Task 7: IceSpiritNavHost + IceSpiritVisionActivity 接线

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`

- [ ] **Step 1: 写 `IceSpiritNavHost.kt`**

`app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`:

```kotlin
package com.icespiritai.offline.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icespiritai.offline.ui.home.HomeScreen
import com.icespiritai.offline.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun IceSpiritNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
```

- [ ] **Step 2: 改 `IceSpiritVisionActivity.kt`**

`app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`:

```kotlin
package com.icespiritai.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.ui.nav.IceSpiritNavHost
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IceSpiritVisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            val mode: ThemeMode = settings.themeMode.first()
            AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
        }

        setContent {
            IceSpiritVisionTheme {
                IceSpiritNavHost()
            }
        }
    }
}
```

- [ ] **Step 3: 把 `HomeScreen` 临时 stub 出来(完整实现是 Task 14)**

`app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.icespiritai.offline.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Text(
            text = "HomeScreen stub (full impl in Task 14)",
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
```

Expected:两个命令都 `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/nav/ app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt
git commit -m "feat(ui): NavHost + Activity wires theme + Settings into nav"
```

(原 `MainScreen.kt` 暂未删除 — Task 14 重写 HomeScreen 时一并删除,避免单独 delete commit。)

---

## Task 8: SeverityBadge + HitCard

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/components/SeverityBadge.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/HitCard.kt`

- [ ] **Step 1: 写 `SeverityBadge.kt`**

`app/src/main/java/com/icespiritai/offline/ui/components/SeverityBadge.kt`:

```kotlin
package com.icespiritai.offline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkOnError
import com.icespiritai.offline.ui.theme.DarkOnWarning
import com.icespiritai.offline.ui.theme.DarkWarning
import com.icespiritai.offline.ui.theme.LightError
import com.icespiritai.offline.ui.theme.LightOnError
import com.icespiritai.offline.ui.theme.LightOnWarning
import com.icespiritai.offline.ui.theme.LightWarning

@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) {
    val (bg: Color, fg: Color) = when (severity) {
        Severity.Info -> resolveSeverityColors(DarkWarning, DarkOnWarning, LightWarning, LightOnWarning)
        Severity.Warning -> resolveSeverityColors(DarkWarning, DarkOnWarning, LightWarning, LightOnWarning)
        Severity.Violation -> resolveSeverityColors(DarkError, DarkOnError, LightError, LightOnError)
    }
    val label = when (severity) {
        Severity.Info -> stringResource(R.string.hit_severity_info)
        Severity.Warning -> stringResource(R.string.hit_severity_warning)
        Severity.Violation -> stringResource(R.string.hit_severity_violation)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(PaddingValues(horizontal = 8.dp, vertical = 2.dp)),
    )
}

@Composable
private fun resolveSeverityColors(
    darkBg: Color,
    darkFg: Color,
    lightBg: Color,
    lightFg: Color,
): Pair<Color, Color> {
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    return if (isDark) darkBg to darkFg else lightBg to lightFg
}
```

- [ ] **Step 2: 写 `HitCard.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/HitCard.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.ui.components.SeverityBadge

@Composable
fun HitCard(hit: RuleHit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "违规条目: ${hit.matchedText}, 严重等级 ${hit.severity.name}"
            },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hit.matchedText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SeverityBadge(severity = hit.severity)
            }
            Text(
                text = stringResource(R.string.hit_card_category, hit.category),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.hit_card_regulation, hit.regulation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/components/SeverityBadge.kt app/src/main/java/com/icespiritai/offline/ui/home/HitCard.kt
git commit -m "feat(ui): SeverityBadge + HitCard with severity color + a11y"
```

---

## Task 9: LoadingOverlay + StatusBanner

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt`

- [ ] **Step 1: 写 `LoadingOverlay.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

@Composable
fun LoadingOverlay(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun loadingLabelRes(stage: AnalysisStateLoadingStage): Int = when (stage) {
    AnalysisStateLoadingStage.OcrRunning -> R.string.status_ocr_running
    AnalysisStateLoadingStage.RuleScanning -> R.string.status_rule_scanning
}

enum class AnalysisStateLoadingStage { OcrRunning, RuleScanning }
```

- [ ] **Step 2: 写 `StatusBanner.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkOnError
import com.icespiritai.offline.ui.theme.DarkSuccess
import com.icespiritai.offline.ui.theme.DarkWarning
import com.icespiritai.offline.ui.theme.LightError
import com.icespiritai.offline.ui.theme.LightOnError
import com.icespiritai.offline.ui.theme.LightSuccess
import com.icespiritai.offline.ui.theme.LightWarning

enum class StatusBannerKind { Idle, Loading, Success, Warning, Violation }

@Composable
fun StatusBanner(
    kind: StatusBannerKind,
    text: String,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    val (bg, fg) = when (kind) {
        StatusBannerKind.Idle -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBannerKind.Loading -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBannerKind.Success -> if (isDark) DarkSuccess.copy(alpha = 0.2f) to DarkSuccess else LightSuccess.copy(alpha = 0.2f) to LightSuccess
        StatusBannerKind.Warning -> if (isDark) DarkWarning.copy(alpha = 0.2f) to DarkWarning else LightWarning.copy(alpha = 0.2f) to LightWarning
        StatusBannerKind.Violation -> if (isDark) DarkError.copy(alpha = 0.2f) to DarkError else LightError.copy(alpha = 0.2f) to LightError
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt app/src/main/java/com/icespiritai/offline/ui/home/StatusBanner.kt
git commit -m "feat(ui): LoadingOverlay + StatusBanner (severity-tinted)"
```

---

## Task 10: CaptureButton + CaptureBar

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/CaptureButton.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt`

- [ ] **Step 1: 写 `CaptureButton.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/CaptureButton.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

@Composable
fun CaptureButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.capture_button_desc)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = a11y },
        colors = ButtonDefaults.buttonColors(),
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
            Text(text = stringResource(R.string.action_take_photo))
        }
    }
}
```

- [ ] **Step 2: 写 `CaptureBar.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

@Composable
fun CaptureBar(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val captureA11y = stringResource(R.string.capture_button_desc)
    val pickA11y = stringResource(R.string.select_image_button_desc)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureButton(
            onClick = onCapture,
            enabled = enabled,
            modifier = Modifier
                .weight(2f)
                .semantics { contentDescription = captureA11y },
        )
        OutlinedButton(
            onClick = onPick,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = pickA11y },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                Text(text = stringResource(R.string.action_pick_image))
            }
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/CaptureButton.kt app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt
git commit -m "feat(ui): CaptureButton + CaptureBar (capture primary, pick secondary)"
```

---

## Task 11: ImagePreview + HighlightOverlay

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt`

- [ ] **Step 1: 写 `HighlightOverlay.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkWarning
import com.icespiritai.offline.ui.theme.LightError
import com.icespiritai.offline.ui.theme.LightWarning

@Composable
fun HighlightOverlay(
    lines: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    val strokePx = 4f
    Canvas(modifier = modifier) {
        lines.forEach { line ->
            val lineSeverity = hits
                .filter { line.text.contains(it.matchedText) }
                .maxOfOrNull { it.severity }
                ?: return@forEach
            val color = when (lineSeverity) {
                Severity.Violation -> if (isDark) DarkError else LightError
                Severity.Warning -> if (isDark) DarkWarning else LightWarning
                Severity.Info -> return@forEach
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(line.box.left.toFloat(), line.box.top.toFloat()),
                size = Size(line.box.width().toFloat(), line.box.height().toFloat()),
                style = Stroke(width = strokePx),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }
}
```

(`hits` 通过子串匹配定位到具体行 — `RuleHit.matchedText` 是 AC 自动机从 line 中抽出的子串,只需 `line.text.contains(hit.matchedText)`。无子串匹配 → 该行不着色,避免误涂。)

- [ ] **Step 2: 写 `ImagePreview.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine

@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null) {
            Text(
                text = stringResource(R.string.status_image_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (lineBoxes.isNotEmpty()) {
                HighlightOverlay(
                    lines = lineBoxes,
                    hits = hits,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt
git commit -m "feat(ui): ImagePreview (Coil) + HighlightOverlay (Canvas OCR boxes)"
```

- [ ] **Step 2: 写 `ImagePreview.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine

@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine>,
    hitSeverities: Map<String, Severity>,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null) {
            Text(
                text = stringResource(R.string.status_image_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (lineBoxes.isNotEmpty()) {
                HighlightOverlay(
                    lines = lineBoxes,
                    severities = hitSeverities,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt app/src/main/java/com/icespiritai/offline/ui/home/HighlightOverlay.kt
git commit -m "feat(ui): ImagePreview (Coil) + HighlightOverlay (Canvas OCR boxes)"
```

---

## Task 12: ResultPanel

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt`

- [ ] **Step 1: 写 `ResultPanel.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.ViolationReport

@Composable
fun ResultPanel(
    report: ViolationReport,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.hit_card_category, report.ocrText.take(40)),
            style = MaterialTheme.typography.bodySmall,
        )
        if (report.hits.isEmpty()) {
            Text(
                text = stringResource(R.string.status_no_violation_card),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(report.hits) { hit -> HitCard(hit = hit) }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt
git commit -m "feat(ui): ResultPanel with LazyColumn of HitCards"
```

---

## Task 13: RuleTabBar + HomeTopBar

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`

- [ ] **Step 1: 写 `RuleTabBar.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.icespiritai.offline.R

enum class RuleTab(val titleRes: Int) {
    AdLaw(R.string.tab_ad_law),
    FoodLabel(R.string.tab_food_label),
}

@Composable
fun RuleTabBar(
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.tab_switch_desc)
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        RuleTab.entries.forEach { tab ->
            Tab(
                selected = (tab == selected),
                onClick = { if (enabled) onSelect(tab) },
                enabled = enabled || tab == selected,
                text = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}
```

- [ ] **Step 2: 写 `HomeTopBar.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                val a11y = stringResource(R.string.settings_button_desc)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = a11y },
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                }
            },
        )
        RuleTabBar(
            selected = selectedTab,
            onSelect = onSelectTab,
            enabled = tabEnabled,
        )
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt
git commit -m "feat(ui): RuleTabBar + HomeTopBar with settings action"
```

---

## Task 14: HomeScreen 完整重写

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`
- Delete: `app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt`

- [ ] **Step 1: 写完整 `HomeScreen.kt`**

`app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.export.ExportAction

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val viewModel: IceSpiritVisionViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(RuleTab.AdLaw) }
    // pendingUri persists across Loading→Complete so the image stays visible
    // before the analyzer finishes (AnalysisState.Loading has no URI field).
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            viewModel.startAnalysis(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* denied — UI shows banner below */ }

    fun ensurePermissionThenLaunchCamera() {
        val needsCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) != PackageManager.PERMISSION_GRANTED
        if (needsCamera) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // Phase 1 stub: PickVisualMedia instead of TakePicture (no extra
        // Activity result handling). Real camera capture is a follow-up.
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun pickFromGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun reset() {
        pendingUri = null
        viewModel.reset()
    }

    // Derive display state from AnalysisState
    val completeReport = (state as? AnalysisState.Complete)?.report
    val ocrResult = (state as? AnalysisState.OcrDone)
    val lineBoxes = ocrResult?.lineBoxes ?: emptyList()
    val hits = completeReport?.hits ?: emptyList()
    val showLineBoxes = (state is AnalysisState.OcrDone) || completeReport != null

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            selectedTab = selectedTab,
            onSelectTab = { tab ->
                if (tab == RuleTab.FoodLabel) {
                    Toast.makeText(context, R.string.tab_disabled_toast, Toast.LENGTH_SHORT).show()
                } else {
                    selectedTab = tab
                    reset()
                }
            },
            tabEnabled = state !is AnalysisState.Loading,
            onOpenSettings = onOpenSettings,
        )

        StatusBannerFor(state = state)

        ImagePreview(
            imageUri = pendingUri,
            lineBoxes = if (showLineBoxes) lineBoxes else emptyList(),
            hits = hits,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        when (val s = state) {
            AnalysisState.Idle -> {
                Text(
                    text = stringResource(R.string.status_image_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is AnalysisState.Loading -> {
                Text(
                    text = stringResource(loadingLabelRes(s.stage.toLoadingStage())),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is AnalysisState.Complete -> {
                ResultPanel(
                    report = s.report,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            is AnalysisState.Error -> {
                ErrorPanel(
                    code = s.errorCode,
                    retryable = s.retryable,
                    onRetry = ::reset,
                    onGrantPermission = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                )
            }
            else -> { /* OcrDone / RuleScanned bridge — transient */ }
        }

        when (val s = state) {
            is AnalysisState.Complete -> {
                Button(
                    onClick = { ExportAction.share(context, s.report) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.action_export))
                }
            }
            else -> {}
        }
        CaptureBar(
            onCapture = ::ensurePermissionThenLaunchCamera,
            onPick = ::pickFromGallery,
            enabled = state !is AnalysisState.Loading,
        )
    }
}

@Composable
private fun StatusBannerFor(state: AnalysisState) {
    when (state) {
        AnalysisState.Idle -> StatusBanner(StatusBannerKind.Idle, text = "")
        is AnalysisState.Loading -> StatusBanner(StatusBannerKind.Loading, text = "")
        is AnalysisState.Complete -> {
            val hits = state.report.hits
            val maxSev = hits.maxOfOrNull { it.severity }
            val kind = when (maxSev) {
                Severity.Violation -> StatusBannerKind.Violation
                Severity.Warning -> StatusBannerKind.Warning
                Severity.Info -> StatusBannerKind.Warning
                null -> StatusBannerKind.Success
            }
            val text = when (kind) {
                StatusBannerKind.Success -> stringResource(R.string.status_no_violation_card)
                else -> stringResource(R.string.status_violation_count, hits.size)
            }
            StatusBanner(kind = kind, text = text)
        }
        is AnalysisState.Error -> StatusBanner(StatusBannerKind.Violation, text = state.message)
        else -> StatusBanner(StatusBannerKind.Idle, text = "")
    }
}

@Composable
private fun ErrorPanel(
    code: ErrorCode,
    retryable: Boolean,
    onRetry: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    val msgRes = when (code) {
        ErrorCode.OCR_UNAVAILABLE -> R.string.error_ocr_unavailable
        ErrorCode.OCR_FAILED -> R.string.error_ocr_failed
        ErrorCode.RULES_FAILED -> R.string.error_rules_failed
        ErrorCode.UNKNOWN -> R.string.error_unknown
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(msgRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (retryable) {
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            TextButton(onClick = onGrantPermission) {
                Text(stringResource(R.string.action_grant_permission))
            }
        }
    }
}

private fun AnalysisState.Loading.Stage.toLoadingStage(): AnalysisStateLoadingStage = when (this) {
    AnalysisState.Loading.Stage.OcrRunning -> AnalysisStateLoadingStage.OcrRunning
    AnalysisState.Loading.Stage.RuleScanning -> AnalysisStateLoadingStage.RuleScanning
}
```

- [ ] **Step 2: 删旧 `MainScreen.kt`**

```bash
git rm app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`(若 `OcrDoneWithImage` 引用需 stub,见 KDoc)。

- [ ] **Step 4: 提交**

```bash
git add -A app/src/main/java/com/icespiritai/offline/ui/
git commit -m "feat(ui): HomeScreen full integration (single-page, top-bar, capture)"
```

---

## Task 15: EvidencePackageBuilder

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/export/EvidencePackageBuilder.kt`
- Create: `app/src/test/java/com/icespiritai/offline/export/EvidencePackageBuilderTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/icespiritai/offline/export/EvidencePackageBuilderTest.kt`:

```kotlin
package com.icespiritai.offline.export

import android.net.Uri
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class EvidencePackageBuilderTest {

    @Test
    fun `package contains image, report json, manifest`() {
        val rawImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)  // PNG magic
        val imageProvider = StubImageProvider(rawImage)
        val report = ViolationReport(
            imageUri = Uri.parse("file:///tmp/test.jpg"),
            ocrText = "本店专治糖尿病",
            hits = listOf(
                RuleHit(
                    ruleId = "AD_LAW_007",
                    matchedText = "100% 有效",
                    category = "绝对化用语",
                    regulation = "《广告法》第 9 条",
                    severity = Severity.Violation,
                ),
            ),
            timestampMs = 1_700_000_000_000L,
        )

        val out = ByteArrayOutputStream()
        EvidencePackageBuilder.build(
            report = report,
            imageProvider = imageProvider,
            out = out,
        )

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }

        assertTrue("image.jpg missing", "image.jpg" in entries)
        assertTrue("report.json missing", "report.json" in entries)
        assertTrue("manifest.txt missing", "manifest.txt" in entries)
        assertEquals(rawImage.size, entries.getValue("image.jpg").size)
        assertTrue(
            "report.json lacks matchedText",
            String(entries.getValue("report.json")).contains("100% 有效"),
        )
        assertTrue(
            "manifest.txt lacks version",
            String(entries.getValue("manifest.txt")).contains("IceSpiritAI_Vision"),
        )
    }

    private class StubImageProvider(private val bytes: ByteArray) : ImageBytesProvider {
        override fun open(uri: Uri): ByteArray = bytes
    }
}
```

- [ ] **Step 2: 跑测试,确认失败**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.export.EvidencePackageBuilderTest"
```

Expected:COMPILE failure(`EvidencePackageBuilder` 不存在)。

- [ ] **Step 3: 写 `ImageBytesProvider` + `EvidencePackageBuilder`**

`app/src/main/java/com/icespiritai/offline/export/ImageBytesProvider.kt`:

```kotlin
package com.icespiritai.offline.export

import android.content.Context
import android.net.Uri

fun interface ImageBytesProvider {
    fun open(uri: Uri): ByteArray

    companion object {
        fun from(context: Context): ImageBytesProvider = ImageBytesProvider { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot open URI: $uri")
        }
    }
}
```

`app/src/main/java/com/icespiritai/offline/export/EvidencePackageBuilder.kt`:

```kotlin
package com.icespiritai.offline.export

import com.icespiritai.offline.domain.ViolationReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidencePackageBuilder {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        report: ViolationReport,
        imageProvider: ImageBytesProvider,
        out: OutputStream,
        appVersion: String = "0.1.0",
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("image.jpg"))
            zip.write(imageProvider.open(report.imageUri))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("report.json"))
            val payload = mapOf(
                "timestampMs" to report.timestampMs,
                "ocrText" to report.ocrText,
                "hits" to report.hits.map { hit ->
                    mapOf(
                        "ruleId" to hit.ruleId,
                        "matchedText" to hit.matchedText,
                        "category" to hit.category,
                        "regulation" to hit.regulation,
                        "severity" to hit.severity.name,
                    )
                },
            )
            zip.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write(
                """
                IceSpiritAI_Vision evidence package
                Generated: ${report.timestampMs}
                AppVersion: $appVersion
                HitCount: ${report.hits.size}
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
    }

    fun toFile(
        report: ViolationReport,
        imageProvider: ImageBytesProvider,
        appVersion: String = "0.1.0",
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        build(report, imageProvider, buf, appVersion)
        return buf.toByteArray()
    }
}
```

- [ ] **Step 4: 跑测试,确认 PASS**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.export.EvidencePackageBuilderTest"
```

Expected:1 test pass。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/export/ app/src/test/java/com/icespiritai/offline/export/
git commit -m "feat(export): EvidencePackageBuilder (zip with image + report + manifest)"
```

---

## Task 16: ExportAction(FileProvider + ACTION_SEND)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/export/ExportAction.kt`

- [ ] **Step 1: 写 `ExportAction.kt`**

`app/src/main/java/com/icespiritai/offline/export/ExportAction.kt`:

```kotlin
package com.icespiritai.offline.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.ViolationReport
import java.io.File

object ExportAction {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val EVIDENCE_DIR = "evidence"
    private const val MAX_FILENAME_TS = 6  // 2026-08-15-123456 ≈ 6 chars ts slice

    fun share(
        context: Context,
        report: ViolationReport,
        appVersion: String = "0.1.0",
    ) {
        val bytes = EvidencePackageBuilder.toFile(
            report = report,
            imageProvider = ImageBytesProvider.from(context),
            appVersion = appVersion,
        )

        val dir = File(context.cacheDir, EVIDENCE_DIR).apply { mkdirs() }
        val file = File(dir, "evidence_${report.timestampMs}.zip")
        file.writeBytes(bytes)

        val authority = context.packageName + FILE_PROVIDER_SUFFIX
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_share_chooser)),
        )
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell
```

Expected:`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/export/ExportAction.kt
git commit -m "feat(export): ExportAction with FileProvider + ACTION_SEND"
```

---

## Task 17: 单元测试套件

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/ui/theme/ThemeModeTest.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/components/SeverityBadgeTest.kt`

- [ ] **Step 1: 写 `ThemeModeTest.kt`**

`app/src/test/java/com/icespiritai/offline/ui/theme/ThemeModeTest.kt`:

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `SYSTEM maps to FOLLOW_SYSTEM`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.toNightMode())
    }

    @Test
    fun `DARK maps to YES`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.toNightMode())
    }

    @Test
    fun `LIGHT maps to NO`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.toNightMode())
    }

    @Test
    fun `fromName falls back to SYSTEM on unknown`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("nonsense"))
    }

    @Test
    fun `fromName parses valid names`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromName("LIGHT"))
    }
}
```

- [ ] **Step 2: 写 `SeverityBadgeTest.kt`**

`app/src/test/java/com/icespiritai/offline/ui/components/SeverityBadgeTest.kt`:

```kotlin
package com.icespiritai.offline.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkOnError
import com.icespiritai.offline.ui.theme.DarkOnWarning
import com.icespiritai.offline.ui.theme.DarkWarning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeverityBadgeTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `Violation shows 违规 label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DarkError)) {
                SeverityBadge(severity = Severity.Violation)
            }
        }
        composeRule.onNodeWithText("违规").assertExists()
    }

    @Test
    fun `Warning shows 警告 label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DarkWarning, onPrimary = DarkOnWarning)) {
                SeverityBadge(severity = Severity.Warning)
            }
        }
        composeRule.onNodeWithText("警告").assertExists()
    }

    @Test
    fun `Info shows 信息 label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DarkWarning, onPrimary = DarkOnWarning)) {
                SeverityBadge(severity = Severity.Info)
            }
        }
        composeRule.onNodeWithText("信息").assertExists()
    }
}
```

- [ ] **Step 3: 跑全部 unit tests**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell
```

Expected:全部已通过 + 新加 8 tests pass。

- [ ] **Step 4: 提交**

```bash
git add app/src/test/java/com/icespiritai/offline/ui/
git commit -m "test(ui): ThemeMode mapping + SeverityBadge label rendering"
```

---

## Task 18: Compose UI 测试

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenTest.kt`

- [ ] **Step 1: 写 `HomeScreenTest.kt`**

`app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenTest.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.ui.theme.DarkOnSurface
import com.icespiritai.offline.ui.theme.DarkSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `home idle shows capture and pick buttons`() {
        var captured = 0
        var picked = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkSurface, onSurface = DarkOnSurface)) {
                HomeScreenBare(
                    onCapture = { captured++ },
                    onPick = { picked++ },
                )
            }
        }
        composeRule.onNodeWithText("拍照").assertExists()
        composeRule.onNodeWithText("选图").assertExists()
        composeRule.onNodeWithText("拍照").performClick()
        composeRule.onNodeWithText("选图").performClick()
        assert(captured == 1)
        assert(picked == 1)
    }

    @Test
    fun `home idle shows image hint`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkSurface, onSurface = DarkOnSurface)) {
                HomeScreenBare(onCapture = {}, onPick = {})
            }
        }
        composeRule.onNodeWithText("请对正图片后点击拍照").assertExists()
    }
}
```

- [ ] **Step 2: 抽出可测试的 `HomeScreenBare`**

`HomeScreen.kt` 末尾追加:

```kotlin
@Composable
fun HomeScreenBare(onCapture: () -> Unit, onPick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("冰灵锐目", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "请对正图片后点击拍照",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        CaptureBar(onCapture = onCapture, onPick = onPick)
    }
}
```

(可测试版本 — 绕开 `viewModel()` + state collaboration,只测静态布局。完整状态机测试放 instrumentation 测试。)

- [ ] **Step 3: 跑测试**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.ui.home.HomeScreenTest"
```

Expected:2 tests pass。

- [ ] **Step 4: 提交**

```bash
git add app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenTest.kt app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt
git commit -m "test(ui): HomeScreen idle layout + capture/pick click wiring"
```

---

## Task 19: 截图测试 + 仪器测试冒烟

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/ui/screenshot/HomeScreenScreenshotTest.kt`
- Create: `app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`(增量)

- [ ] **Step 1: 写截图测试**

`app/src/test/java/com/icespiritai/offline/ui/screenshot/HomeScreenScreenshotTest.kt`:

```kotlin
package com.icespiritai.offline.ui.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.ui.home.HomeScreenBare
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun home_idle_dark() {
        composeRule.setContent {
            IceSpiritVisionTheme(darkTheme = true) { HomeScreenBare(onCapture = {}, onPick = {}) }
        }
        composeRule.onRoot().captureToImage().writeToTestStorage("home_idle_dark")
    }

    @Test
    fun home_idle_light() {
        composeRule.setContent {
            IceSpiritVisionTheme(darkTheme = false) { HomeScreenBare(onCapture = {}, onPick = {}) }
        }
        composeRule.onRoot().captureToImage().writeToTestStorage("home_idle_light")
    }
}
```

需要在 `app/build.gradle.kts` 加:

```kotlin
testImplementation(libs.androidx.test.ext.junit)
```

(如没有。检查当前 `testImplementation` 列表。)

- [ ] **Step 2: 跑截图测试**

```bash
./gradlew.bat :app:testDebugUnitTest -PmodelProfile=shell --tests "com.icespiritai.offline.ui.screenshot.HomeScreenScreenshotTest"
```

Expected:2 tests pass + `app/build/outputs/unit_test_screenshots/home_idle_dark.png` 与 `home_idle_light.png` 生成。

- [ ] **Step 3: 增量仪器测试**

`app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`,在末尾追加:

```kotlin
    @Test
    fun homeScreen_opensAndShowsCapture() {
        val scenario = ActivityScenario.launch(IceSpiritVisionActivity::class.java)
        onView(withText("拍照")).check(matches(isDisplayed()))
        onView(withText("选图")).check(matches(isDisplayed()))
        scenario.close()
    }
```

(`androidx.test.espresso` 已在 `androidTestImplementation` 路径下;若缺,加 `androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")`。)

- [ ] **Step 4: 跑仪器测试(真机)**

```bash
ANDROID_SERIAL=<real-device>
./gradlew.bat :app:connectedDebugAndroidTest -PmodelProfile=shell
```

Expected:全部 pass,新增 `homeScreen_opensAndShowsCapture` PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/test/java/com/icespiritai/offline/ui/screenshot/ app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt
git commit -m "test(ui): screenshot dark/light + instrumented home opens"
```

---

## §实施完成检查清单

- [ ] `assembleDebug -PmodelProfile=shell` 通过
- [ ] `assembleDebug -PmodelProfile=ice_ocr_rules` 通过(若 native 资源齐全)
- [ ] `testDebugUnitTest` 全部 pass
- [ ] `connectedDebugAndroidTest` 全部 pass
- [ ] 4 张截图生成:`home_idle_dark.png`、`home_idle_light.png`(后续可加 `*_complete_*`)
- [ ] 深/浅双主题在真机上视觉验证(跟随系统 / 深 / 浅 三档切换)
- [ ] 拍照 → 加载 → 命中卡片 → 导出取证包 真实链路跑通
- [ ] 设计文档 `2026-08-15-icevision-ui-design.md` 提到过的决策点全部落地

## §风险登记

| 风险 | 缓解 |
|---|---|
| `Activity.onCreate` 内 `setDefaultNightMode` 会有一次重组闪烁 | Compose 之外实色边框 + SplashScreen 隐藏 |
| ExportAsync 写大文件可能 ANR | 先写到 `cacheDir`,UI 立即出分享,真异步 IO 由后续 PR 优化 |
| `MaterialTheme.colorScheme.background.red < 0.3f` 启发式判定暗/浅 | 跟随系统模式切换时可能误判;Phase 4 改用 `isSystemInDarkTheme()` 兜底 |
| `pendingUri` 只在 HomeScreen 内存,ViewModel 重建后丢失 | Compose 走 `remember` 不跨进程,ViewModel 重建由 Configuration change 触发,Activity 已 `configChanges` 默认行为;若要持久化,后续 PR 加 `SavedStateHandle` |
| `compose-material-icons-extended` 1.7.0 对 `AutoMirrored` 命名空间要求 | 已在 Task 6 Step 5 用 `AutoMirrored.Filled.ArrowBack`,若解析失败回退到 `Icons.Filled.ArrowBack` |
