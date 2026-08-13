# 冰灵锐目 Phase 1 — OCR + 规则库文字审核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现冰灵锐目 Phase 1 — 用户拍/选图 → 本地 OCR 抽文字 → AC 自动机匹配《广告法》规则 → 输出违规清单 + 法规条款引用。

**Architecture:** 单 Activity(Compose UI)+ ViewModel(StateFlow<AnalysisState>)+ Repository 编排 OcrEngine + AdLawRuleMatcher。AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 36 / NDK 28.2。

**Tech Stack:** Jetpack Compose 1.12.x, kotlinx-coroutines 1.10.x, kotlinx-serialization 1.9.x, RapidOCR Android(待 artifact 名锁定), HankCS aho-corasick 0.1.4。

**Spec:** `docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md`

---

## 文件结构总览

### 新增源码

| 路径 | 职责 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt` | 持有 `StateFlow<AnalysisState>`,处理 startAnalysis / cancel |
| `app/src/main/java/com/icespiritai/offline/analysis/AnalysisState.kt` | 密封类,定义 Idle / Loading / OcrDone / RuleScanned / Complete / Error |
| `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt` | 编排 OCR + Rule 匹配,返回 `ViolationReport` |
| `app/src/main/java/com/icespiritai/offline/ocr/OcrEngine.kt` | interface `suspend fun recognize(uri): OcrResult` |
| `app/src/main/java/com/icespiritai/offline/ocr/OcrModels.kt` | `OcrResult`、`TextLine` data class |
| `app/src/main/java/com/icespiritai/offline/ocr/FakeOcrEngine.kt` | 测试桩,返回固定文本 |
| `app/src/main/java/com/icespiritai/offline/ocr/RapidOcrEngine.kt` | 生产实现,调用 RapidOCR Android |
| `app/src/main/java/com/icespiritai/offline/rules/RuleMatcher.kt` | interface `fun scan(text): List<RuleHit>` |
| `app/src/main/java/com/icespiritai/offline/rules/AdLawRule.kt` | `@Serializable` data class |
| `app/src/main/java/com/icespiritai/offline/rules/RuleHit.kt` | data class |
| `app/src/main/java/com/icespiritai/offline/rules/ViolationReport.kt` | data class |
| `app/src/main/java/com/icespiritai/offline/rules/AdLawRuleMatcher.kt` | AC 自动机实现 |
| `app/src/main/java/com/icespiritai/offline/rules/FakeRuleMatcher.kt` | 测试桩 |
| `app/src/main/java/com/icespiritai/offline/rules/AssetRuleLoader.kt` | 从 assets/rules/ 反序列化 JSON |
| `app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt` | Compose UI 入口,渲染 AnalysisState 分支 |

### 新增资源

| 路径 | 职责 |
|---|---|
| `app/src/main/assets/rules/ad_law_rules.json` | 真实规则(ice_ocr_rules profile 打包) |
| `app/src/main/assets/rules/placeholder.json` | 空规则(shell profile 打包) |
| `app/src/main/res/values/strings.xml` | 增加中文字串 |

### 修改文件

| 路径 | 改什么 |
|---|---|
| `app/build.gradle.kts` | NDK 28.2 + testOptions + buildFeatures(去掉 viewBinding,加 compose) |
| `app/src/main/AndroidManifest.xml` | 加 CAMERA / READ_MEDIA_IMAGES 权限 |
| `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt` | 改为 Compose host |
| `gradle/libs.versions.toml` | 加 lifecycle / coroutines / serialization / compose / hankcs / rapidocr 版本 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.7.x |
| `build.gradle.kts` | AGP 9.3.x,Kotlin 2.4.10 |
| `settings.gradle.kts` | 视情况加 Google 仓库(compose) |

### 新增测试

| 路径 | 覆盖 |
|---|---|
| `app/src/test/java/com/icespiritai/offline/rules/AdLawRuleTest.kt` | JSON 序列化 |
| `app/src/test/java/com/icespiritai/offline/rules/AssetRuleLoaderTest.kt` | 从 assets 加载 |
| `app/src/test/java/com/icespiritai/offline/rules/AdLawRuleMatcherTest.kt` | 关键词 / 大小写 / 多规则 |
| `app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt` | 整条分析链 |
| `app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt` | Compose UI 状态 |

### 新增 Gradle 任务

| 路径 | 职责 |
|---|---|
| `app/prepare-ocr-rules.gradle.kts` | `prepareOcrRulesAssets` 任务,按 modelProfile 门控 assets 打包 |

---

## Task 1: 升级 baseline 到前瞻路径

**Files:**
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 升级 AGP / Kotlin 到前瞻路径**

修改 `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

修改 `gradle/libs.versions.toml` 中 `[versions]` 段:

```toml
agp = "9.3.0"
kotlin = "2.4.10"
coreKtx = "1.15.0"
appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.1.4"
activityKtx = "1.13.0"
lifecycle = "2.11.0"
coroutines = "1.10.1"
serialization = "1.9.0"
hankcsAhoCorasick = "0.1.4"
composeBom = "2026.08.00"
junit = "4.13.2"
junitJupiter = "5.11.3"
mockitoKotlin = "5.4.0"
turbine = "1.2.0"
```

(以上版本号以启动期 `gradle dependencies` 实测为准,Kotlin/coroutines/serialization/composeBom 标"待核")

修改 `gradle/libs.versions.toml` 中 `[libraries]` 段新增:

```toml
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-ktx = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
hankcs-aho-corasick = { module = "com.hankcs:aho-corasick", version.ref = "hankcsAhoCorasick" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version = "3.2.0" }
```

修改 `gradle/libs.versions.toml` 中 `[plugins]` 段:

```toml
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: 升级 Gradle Wrapper 到 9.7.x**

修改 `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.7-bin.zip
```

(实际执行时确认 9.7.x 的精确 patch 与 Tencent 镜像 URL 拼接正确)

- [ ] **Step 3: 重写 `app/build.gradle.kts`**

完整替换为:

```kotlin
// app/build.gradle.kts — IceSpiritAI_Vision (Phase 1: OCR + rules).
//
// modelProfile is a Gradle property routed by `-PmodelProfile=<name>`.
// shell = UI skeleton only, no OCR model or rules
// ice_ocr_rules = full OCR + rules engine (Phase 1 main profile)
// ice_vision = end-side VLM (later Phase, not bundled here)

import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val modelProfile = providers.gradleProperty("modelProfile")
    .getOrElse("shell")

android {
    namespace = "com.icespiritai.offline"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.icespiritai.vision"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
            version = "28.2.13676358"
        }

        buildConfigField("String", "MODEL_PROFILE", "\"$modelProfile\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hankcs.aho.corasick)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.turbine)
}

// Hook the modelProfile-gated asset preparation task
apply(from = "prepare-ocr-rules.gradle.kts")
```

- [ ] **Step 4: 同步项目**

Run: `./gradlew.bat help`
Expected: BUILD SUCCESSFUL(若失败,先看"启动期实测"清单中的版本对齐问题,逐个解决)

- [ ] **Step 5: 提交**

```bash
git add build.gradle.kts gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties app/build.gradle.kts
git commit -m "build: bump to AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 + add Phase 1 deps"
```

---

## Task 2: AnalysisState 密封类 + domain types

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/analysis/AnalysisState.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ocr/OcrModels.kt`
- Create: `app/src/main/java/com/icespiritai/offline/rules/RuleHit.kt`
- Create: `app/src/main/java/com/icespiritai/offline/rules/ViolationReport.kt`
- Test: `app/src/test/java/com/icespiritai/offline/analysis/AnalysisStateTest.kt`

- [ ] **Step 1: 创建 OcrModels.kt**

`app/src/main/java/com/icespiritai/offline/ocr/OcrModels.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.graphics.Rect

data class TextLine(
    val text: String,
    val box: Rect,
    val confidence: Float
)

data class OcrResult(
    val fullText: String,
    val lineBoxes: List<TextLine>,
    val avgConfidence: Float
)
```

- [ ] **Step 2: 创建 RuleHit.kt**

`app/src/main/java/com/icespiritai/offline/rules/RuleHit.kt`:

```kotlin
package com.icespiritai.offline.rules

enum class Severity { Info, Warning, Violation }

data class RuleHit(
    val ruleId: String,
    val matchedText: String,
    val category: String,
    val regulation: String,
    val severity: Severity
)
```

- [ ] **Step 3: 创建 ViolationReport.kt**

`app/src/main/java/com/icespiritai/offline/rules/ViolationReport.kt`:

```kotlin
package com.icespiritai.offline.rules

import android.net.Uri
import com.icespiritai.offline.ocr.TextLine

data class ViolationReport(
    val imageUri: Uri,
    val ocrText: String,
    val ocrLines: List<TextLine>,
    val hits: List<RuleHit>,
    val timestampMs: Long
)
```

- [ ] **Step 4: 创建 AnalysisState.kt**

`app/src/main/java/com/icespiritai/offline/analysis/AnalysisState.kt`:

```kotlin
package com.icespiritai.offline.analysis

import android.net.Uri
import com.icespiritai.offline.ocr.TextLine
import com.icespiritai.offline.rules.RuleHit
import com.icespiritai.offline.rules.ViolationReport

sealed class AnalysisState {
    object Idle : AnalysisState()

    data class Loading(val stage: Stage) : AnalysisState() {
        enum class Stage { OcrRunning, RuleScanning }
    }

    data class OcrDone(
        val text: String,
        val confidence: Float,
        val lineBoxes: List<TextLine>,
        val lowConfidence: Boolean = confidence < 0.5f
    ) : AnalysisState()

    data class RuleScanned(val hits: List<RuleHit>) : AnalysisState()

    data class Complete(
        val report: ViolationReport,
        val previewUri: Uri?
    ) : AnalysisState()

    data class Error(
        val message: String,
        val retryable: Boolean = false,
        val cause: Throwable? = null
    ) : AnalysisState()
}
```

- [ ] **Step 5: 写 AnalysisState 模式匹配测试**

`app/src/test/java/com/icespiritai/offline/analysis/AnalysisStateTest.kt`:

```kotlin
package com.icespiritai.offline.analysis

import android.net.Uri
import com.icespiritai.offline.ocr.TextLine
import com.icespiritai.offline.rules.RuleHit
import com.icespiritai.offline.rules.Severity
import com.icespiritai.offline.rules.ViolationReport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisStateTest {
    @Test
    fun `Loading wraps OcrRunning stage`() {
        val s: AnalysisState = AnalysisState.Loading(AnalysisState.Loading.Stage.OcrRunning)
        assertTrue(s is AnalysisState.Loading)
        assertEquals(AnalysisState.Loading.Stage.OcrRunning, (s as AnalysisState.Loading).stage)
    }

    @Test
    fun `OcrDone flags lowConfidence when below threshold`() {
        val s = AnalysisState.OcrDone("hello", 0.3f, emptyList())
        assertTrue(s.lowConfidence)
    }

    @Test
    fun `OcrDone does not flag lowConfidence when above threshold`() {
        val s = AnalysisState.OcrDone("hello", 0.9f, emptyList())
        assertEquals(false, s.lowConfidence)
    }

    @Test
    fun `Complete carries report and preview`() {
        val hit = RuleHit("r1", "100% 有效", "medical", "《广告法》§16", Severity.Violation)
        val report = ViolationReport(Uri.parse("content://x"), "100% 有效", emptyList(), listOf(hit), 1L)
        val s = AnalysisState.Complete(report, Uri.parse("content://x"))
        assertEquals(1, s.report.hits.size)
        assertEquals("r1", s.report.hits[0].ruleId)
    }
}
```

- [ ] **Step 6: 运行测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.analysis.AnalysisStateTest"`
Expected: BUILD SUCCESSFUL,4 tests passed

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/analysis app/src/main/java/com/icespiritai/offline/ocr app/src/main/java/com/icespiritai/offline/rules
git add app/src/test/java/com/icespiritai/offline/analysis
git commit -m "feat(analysis): add AnalysisState sealed class + domain types"
```

---

## Task 3: AdLawRule 数据模型 + JSON 序列化

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/rules/AdLawRule.kt`
- Create: `app/src/test/java/com/icespiritai/offline/rules/AdLawRuleTest.kt`

- [ ] **Step 1: 创建 AdLawRule.kt**

`app/src/main/java/com/icespiritai/offline/rules/AdLawRule.kt`:

```kotlin
package com.icespiritai.offline.rules

import kotlinx.serialization.Serializable

@Serializable
data class AdLawRule(
    val id: String,
    val category: String,
    val regulation: String,
    val keywords: List<String>,
    val severity: Severity,
    val version: Int = 1
)

@Serializable
data class AdLawRulePack(
    val version: Int,
    val rules: List<AdLawRule>
)
```

- [ ] **Step 2: 写 JSON 序列化测试**

`app/src/test/java/com/icespiritai/offline/rules/AdLawRuleTest.kt`:

```kotlin
package com.icespiritai.offline.rules

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AdLawRuleTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `roundtrip preserves fields`() {
        val original = AdLawRule(
            id = "medical_absolute",
            category = "medical",
            regulation = "《广告法》§16",
            keywords = listOf("100% 有效", "根治"),
            severity = Severity.Violation,
            version = 2
        )
        val encoded = json.encodeToString(AdLawRule.serializer(), original)
        val decoded = json.decodeFromString(AdLawRule.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `pack roundtrip preserves list of rules`() {
        val pack = AdLawRulePack(
            version = 1,
            rules = listOf(
                AdLawRule("r1", "medical", "《广告法》§16", listOf("根治"), Severity.Violation),
                AdLawRule("r2", "education", "《广告法》§24", listOf("保过"), Severity.Warning)
            )
        )
        val encoded = json.encodeToString(AdLawRulePack.serializer(), pack)
        val decoded = json.decodeFromString(AdLawRulePack.serializer(), encoded)
        assertEquals(pack, decoded)
    }

    @Test
    fun `decoder ignores unknown keys`() {
        val jsonStr = """{"id":"r1","category":"medical","regulation":"x","keywords":["根治"],"severity":"Violation","version":1,"unknown":"foo"}"""
        val decoded = json.decodeFromString(AdLawRule.serializer(), jsonStr)
        assertEquals("r1", decoded.id)
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.rules.AdLawRuleTest"`
Expected: BUILD SUCCESSFUL,3 tests passed

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/rules/AdLawRule.kt app/src/test/java/com/icespiritai/offline/rules/AdLawRuleTest.kt
git commit -m "feat(rules): add AdLawRule @Serializable model + JSON tests"
```

---

## Task 4: AssetRuleLoader

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/rules/AssetRuleLoader.kt`
- Test: `app/src/test/java/com/icespiritai/offline/rules/AssetRuleLoaderTest.kt`

- [ ] **Step 1: 创建 AssetRuleLoader.kt**

`app/src/main/java/com/icespiritai/offline/rules/AssetRuleLoader.kt`:

```kotlin
package com.icespiritai.offline.rules

import android.content.Context
import kotlinx.serialization.json.Json

class AssetRuleLoader(
    private val context: Context,
    private val assetPath: String = "rules/ad_law_rules.json",
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): AdLawRulePack {
        val text = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString(AdLawRulePack.serializer(), text)
    }
}

class RuleLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

- [ ] **Step 2: 写 AssetRuleLoader 测试(用 mock Context)**

`app/src/test/java/com/icespiritai/offline/rules/AssetRuleLoaderTest.kt`:

```kotlin
package com.icespiritai.offline.rules

import android.content.Context
import android.content.res.AssetManager
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssetRuleLoaderTest {
    private val sampleJson = """
        {
          "version": 1,
          "rules": [
            {"id":"r1","category":"medical","regulation":"《广告法》§16","keywords":["根治","100%"],"severity":"Violation"},
            {"id":"r2","category":"education","regulation":"《广告法》§24","keywords":["保过","包过"],"severity":"Warning"}
          ]
        }
    """.trimIndent()

    @Test
    fun `load parses bundled rules`() {
        val assetManager: AssetManager = mock()
        whenever(assetManager.open("rules/ad_law_rules.json"))
            .thenReturn(ByteArrayInputStream(sampleJson.toByteArray(Charsets.UTF_8)))
        val context: Context = mock()
        whenever(context.assets).thenReturn(assetManager)

        val loader = AssetRuleLoader(context)
        val pack = loader.load()

        assertEquals(2, pack.rules.size)
        assertEquals("r1", pack.rules[0].id)
        assertEquals(Severity.Violation, pack.rules[0].severity)
    }

    @Test
    fun `load throws RuleLoadException on missing asset`() {
        val assetManager: AssetManager = mock()
        whenever(assetManager.open("rules/ad_law_rules.json"))
            .thenThrow(java.io.IOException("not found"))
        val context: Context = mock()
        whenever(context.assets).thenReturn(assetManager)

        val loader = AssetRuleLoader(context)
        assertFailsWith<Exception> { loader.load() }
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.rules.AssetRuleLoaderTest"`
Expected: BUILD SUCCESSFUL,2 tests passed

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/rules/AssetRuleLoader.kt app/src/test/java/com/icespiritai/offline/rules/AssetRuleLoaderTest.kt
git commit -m "feat(rules): add AssetRuleLoader with mocked-Context tests"
```

---

## Task 5: RuleMatcher interface + AdLawRuleMatcher + FakeRuleMatcher

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/rules/RuleMatcher.kt`
- Create: `app/src/main/java/com/icespiritai/offline/rules/AdLawRuleMatcher.kt`
- Create: `app/src/main/java/com/icespiritai/offline/rules/FakeRuleMatcher.kt`
- Test: `app/src/test/java/com/icespiritai/offline/rules/AdLawRuleMatcherTest.kt`

- [ ] **Step 1: 创建 RuleMatcher interface**

`app/src/main/java/com/icespiritai/offline/rules/RuleMatcher.kt`:

```kotlin
package com.icespiritai.offline.rules

interface RuleMatcher {
    fun scan(text: String): List<RuleHit>
}
```

- [ ] **Step 2: 创建 AdLawRuleMatcher 实现**

`app/src/main/java/com/icespiritai/offline/rules/AdLawRuleMatcher.kt`:

```kotlin
package com.icespiritai.offline.rules

import com.hankcs.aho_corasick.AhoCorasick

class AdLawRuleMatcher(rules: List<AdLawRule>) : RuleMatcher {

    private val ac: AhoCorasick<String> = buildAc(rules)
    private val ruleById: Map<String, AdLawRule> = rules.associateBy { it.id }

    private fun buildAc(rules: List<AdLawRule>): AhoCorasick<String> {
        val ac = AhoCorasick<String>()
        rules.forEach { rule ->
            rule.keywords.forEach { kw ->
                ac.add(kw, rule.id)
            }
        }
        ac.build()
        return ac
    }

    override fun scan(text: String): List<RuleHit> {
        val hits = mutableListOf<RuleHit>()
        ac.process(text) { match: MatchResult<String> ->
            val rule = ruleById[match.value] ?: return@process
            hits += RuleHit(
                ruleId = rule.id,
                matchedText = match.match,
                category = rule.category,
                regulation = rule.regulation,
                severity = rule.severity
            )
        }
        return hits.distinctBy { it.ruleId to it.matchedText }
    }
}
```

> **注意:** `com.hankcs.aho_corasick.AhoCorasick` 是 hankcs 0.1.4 实际发布包名。**执行 Task 5 前在 GitHub releases 复核一遍**,若包名不一致需调整 import + 同步更新测试。

- [ ] **Step 3: 创建 FakeRuleMatcher(测试桩)**

`app/src/main/java/com/icespiritai/offline/rules/FakeRuleMatcher.kt`:

```kotlin
package com.icespiritai.offline.rules

class FakeRuleMatcher(
    private val cannedHits: List<RuleHit> = emptyList()
) : RuleMatcher {
    override fun scan(text: String): List<RuleHit> = cannedHits
}
```

- [ ] **Step 4: 写 AdLawRuleMatcher 测试**

`app/src/test/java/com/icespiritai/offline/rules/AdLawRuleMatcherTest.kt`:

```kotlin
package com.icespiritai.offline.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdLawRuleMatcherTest {
    private val rules = listOf(
        AdLawRule("medical_root", "medical", "《广告法》§16", listOf("根治", "100% 有效"), Severity.Violation),
        AdLawRule("edu_baoguo", "education", "《广告法》§24", listOf("保过", "包过"), Severity.Warning)
    )

    private val matcher = AdLawRuleMatcher(rules)

    @Test
    fun `scan finds keyword hits`() {
        val hits = matcher.scan("本品可根治糖尿病,100% 有效")
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.ruleId == "medical_root" && it.matchedText == "根治" })
        assertTrue(hits.any { it.ruleId == "medical_root" && it.matchedText == "100% 有效" })
    }

    @Test
    fun `scan returns empty list when no hits`() {
        val hits = matcher.scan("本店今日开业,欢迎光临")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `scan dedupes overlapping hits`() {
        val hits = matcher.scan("保过保过")
        assertEquals(1, hits.size, "重复出现应去重到 (ruleId, matchedText)")
    }

    @Test
    fun `scan is case-insensitive for ASCII keywords`() {
        val rule = listOf(AdLawRule("test", "test", "x", listOf("GUARANTEED"), Severity.Info))
        val m = AdLawRuleMatcher(rule)
        val hits = m.scan("guaranteed results")
        assertEquals(1, hits.size)
        assertEquals("GUARANTEED", hits[0].matchedText)
    }

    @Test
    fun `scan finds multiple rules in one text`() {
        val hits = matcher.scan("根治糖尿病!英语六级保过!")
        val ruleIds = hits.map { it.ruleId }.toSet()
        assertTrue(ruleIds.containsAll(setOf("medical_root", "edu_baoguo")))
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.rules.AdLawRuleMatcherTest"`
Expected: BUILD SUCCESSFUL,5 tests passed

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/rules app/src/test/java/com/icespiritai/offline/rules/AdLawRuleMatcherTest.kt
git commit -m "feat(rules): add RuleMatcher interface + AdLawRuleMatcher (AC automaton) + tests"
```

---

## Task 6: OcrEngine interface + OcrModels + FakeOcrEngine

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ocr/OcrEngine.kt`
- Create: `app/src/main/java/com/icespiritai/offline/ocr/FakeOcrEngine.kt`
- Test: `app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineTest.kt`

- [ ] **Step 1: 创建 OcrEngine interface**

`app/src/main/java/com/icespiritai/offline/ocr/OcrEngine.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.net.Uri

interface OcrEngine {
    suspend fun recognize(uri: Uri): OcrResult
}

class OcrEngineUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class OcrFailed(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

- [ ] **Step 2: 创建 FakeOcrEngine**

`app/src/main/java/com/icespiritai/offline/ocr/FakeOcrEngine.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.net.Uri
import kotlinx.coroutines.delay

class FakeOcrEngine(
    private val cannedText: String = "",
    private val cannedConfidence: Float = 1.0f,
    private val simulatedDelayMs: Long = 0L
) : OcrEngine {
    override suspend fun recognize(uri: Uri): OcrResult {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
        if (cannedText.isEmpty()) {
            throw OcrEngineUnavailable("FakeOcrEngine has no canned text")
        }
        return OcrResult(
            fullText = cannedText,
            lineBoxes = listOf(TextLine(cannedText, android.graphics.Rect(), cannedConfidence)),
            avgConfidence = cannedConfidence
        )
    }
}
```

- [ ] **Step 3: 写 FakeOcrEngine 测试**

`app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineTest.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeOcrEngineTest {
    @Test
    fun `recognize returns canned text`() = runTest {
        val engine = FakeOcrEngine(cannedText = "根治糖尿病", cannedConfidence = 0.95f)
        val result = engine.recognize(Uri.parse("content://x"))
        assertEquals("根治糖尿病", result.fullText)
        assertEquals(0.95f, result.avgConfidence)
    }

    @Test
    fun `recognize throws when canned text empty`() = runTest {
        val engine = FakeOcrEngine()
        assertFailsWith<OcrEngineUnavailable> { engine.recognize(Uri.parse("content://x")) }
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ocr.FakeOcrEngineTest"`
Expected: BUILD SUCCESSFUL,2 tests passed

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ocr app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineTest.kt
git commit -m "feat(ocr): add OcrEngine interface + FakeOcrEngine"
```

---

## Task 7: RapidOcrEngine 桩(best-effort)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ocr/RapidOcrEngine.kt`

- [ ] **Step 1: 创建 RapidOcrEngine 桩**

`app/src/main/java/com/icespiritai/offline/ocr/RapidOcrEngine.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RapidOcrEngine(
    private val context: Context
) : OcrEngine {

    override suspend fun recognize(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    throw OcrFailed("无法解码图片: ${uri}")
                }
            } ?: throw OcrEngineUnavailable("无法打开图片流: ${uri}")
        } catch (e: OcrFailed) {
            throw e
        } catch (e: Exception) {
            throw OcrFailed("图片解码失败", e)
        }

        throw OcrEngineUnavailable(
            "RapidOCR Android artifact 尚未接入"
        )
    }
}
```

> **跟进:** RapidOcrEngine 当前是 stub,只做图片可解码性校验 + 抛 `OcrEngineUnavailable`。`ice_ocr_rules` profile 启用后,需要单独 PR 接入 RapidOCR Android API。Phase 1 默认依赖 FakeOcrEngine 让 UI 流程跑通。

- [ ] **Step 2: 验证编译**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/ocr/RapidOcrEngine.kt
git commit -m "feat(ocr): add RapidOcrEngine stub (artifact wiring deferred)"
```

> **跟进:** Task 12(gradle 资源门控)完成后,启动期实测 ONNX Runtime 1.29.0 在 arm64-v8a / minSdk 26 实际可用,再回头把 RapidOcrEngine.recognize 接入真实 RapidOCR Android API。这块由单独 PR 跟进,不在本 plan 范围。

---

## Task 8: ImageAnalyzerRepository

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`
- Test: `app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt`

- [ ] **Step 1: 创建 ImageAnalyzerRepository**

`app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`:

```kotlin
package com.icespiritai.offline.analysis

import android.net.Uri
import com.icespiritai.offline.ocr.OcrEngine
import com.icespiritai.offline.rules.AssetRuleLoader
import com.icespiritai.offline.rules.RuleMatcher
import com.icespiritai.offline.rules.ViolationReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ImageAnalyzerRepository(
    private val ocrEngine: OcrEngine,
    private val ruleLoader: AssetRuleLoader,
    private val ruleMatcher: RuleMatcher
) {
    fun analyze(uri: Uri): Flow<AnalysisState> = flow {
        emit(AnalysisState.Loading(AnalysisState.Loading.Stage.OcrRunning))
        val ocrResult = try {
            ocrEngine.recognize(uri)
        } catch (e: Exception) {
            emit(AnalysisState.Error(
                message = "OCR 失败: ${e.message ?: "未知错误"}",
                retryable = true,
                cause = e
            ))
            return@flow
        }
        emit(AnalysisState.OcrDone(
            text = ocrResult.fullText,
            confidence = ocrResult.avgConfidence,
            lineBoxes = ocrResult.lineBoxes
        ))

        emit(AnalysisState.Loading(AnalysisState.Loading.Stage.RuleScanning))
        val hits = ruleMatcher.scan(ocrResult.fullText)
        emit(AnalysisState.RuleScanned(hits))

        val report = ViolationReport(
            imageUri = uri,
            ocrText = ocrResult.fullText,
            ocrLines = ocrResult.lineBoxes,
            hits = hits,
            timestampMs = System.currentTimeMillis()
        )
        emit(AnalysisState.Complete(report, previewUri = uri))
    }
}
```

- [ ] **Step 2: 写 Repository 测试**

`app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt`:

```kotlin
package com.icespiritai.offline.analysis

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.rules.AssetRuleLoader
import com.icespiritai.offline.rules.FakeRuleMatcher
import com.icespiritai.offline.rules.RuleHit
import com.icespiritai.offline.rules.Severity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageAnalyzerRepositoryTest {
    private val cannedText = "本店专治糖尿病,100% 有效"
    private val cannedHits = listOf(
        RuleHit("r1", "100% 有效", "medical", "《广告法》§16", Severity.Violation)
    )
    private val context: Context = mock()
    private val loader = AssetRuleLoader(context)
    private val matcher = FakeRuleMatcher(cannedHits = cannedHits)
    private val ocr = FakeOcrEngine(cannedText = cannedText, cannedConfidence = 0.9f)

    private val repo = ImageAnalyzerRepository(ocr, loader, matcher)

    @Test
    fun `analyze emits Loading OcrRunning OcrDone RuleScanning Complete in order`() = runTest {
        repo.analyze(Uri.parse("content://test")).test {
            assertTrue(awaitItem() is AnalysisState.Loading)
            val ocrDone = awaitItem()
            assertTrue(ocrDone is AnalysisState.OcrDone)
            assertEquals(cannedText, (ocrDone as AnalysisState.OcrDone).text)
            assertTrue(awaitItem() is AnalysisState.Loading)
            val scanned = awaitItem()
            assertTrue(scanned is AnalysisState.RuleScanned)
            assertEquals(1, (scanned as AnalysisState.RuleScanned).hits.size)
            val complete = awaitItem()
            assertTrue(complete is AnalysisState.Complete)
            assertEquals(cannedText, (complete as AnalysisState.Complete).report.ocrText)
            awaitComplete()
        }
    }

    @Test
    fun `analyze emits Error when OCR throws`() = runTest {
        val failingOcr = FakeOcrEngine(cannedText = "", cannedConfidence = 0f)
        val failingRepo = ImageAnalyzerRepository(failingOcr, loader, matcher)
        failingRepo.analyze(Uri.parse("content://x")).test {
            assertTrue(awaitItem() is AnalysisState.Loading)
            val err = awaitItem()
            assertTrue(err is AnalysisState.Error)
            assertTrue((err as AnalysisState.Error).retryable)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.analysis.ImageAnalyzerRepositoryTest"`
Expected: BUILD SUCCESSFUL,2 tests passed

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt
git commit -m "feat(analysis): add ImageAnalyzerRepository with Flow emit + tests"
```

---

## Task 9: IceSpiritVisionViewModel

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt`

- [ ] **Step 1: 创建 IceSpiritVisionViewModel**

`app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt`:

```kotlin
package com.icespiritai.offline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.analysis.AnalysisState
import com.icespiritai.offline.analysis.ImageAnalyzerRepository
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.ocr.RapidOcrEngine
import com.icespiritai.offline.rules.AdLawRuleMatcher
import com.icespiritai.offline.rules.AssetRuleLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IceSpiritVisionViewModel(application: Application) : AndroidViewModel(application) {

    private val ruleLoader = AssetRuleLoader(application)
    private val ruleMatcher: com.icespiritai.offline.rules.RuleMatcher = AdLawRuleMatcher(ruleLoader.load().rules)
    private val ocrEngine = if (BuildConfig.MODEL_PROFILE == "ice_ocr_rules") {
        RapidOcrEngine(application)
    } else {
        FakeOcrEngine(cannedText = "本店专治糖尿病,100% 有效", cannedConfidence = 0.9f)
    }
    private val repository = ImageAnalyzerRepository(ocrEngine, ruleLoader, ruleMatcher)

    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    private var currentJob: Job? = null

    fun startAnalysis(uri: Uri) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            repository.analyze(uri).collect { _state.value = it }
        }
    }

    fun reset() {
        currentJob?.cancel()
        _state.value = AnalysisState.Idle
    }

    override fun onCleared() {
        currentJob?.cancel()
        super.onCleared()
    }
}
```

> **注:** ViewModel 的接线逻辑(取消上一个 Job、launch 新协程、写入 StateFlow)很薄。真正的 emit 顺序由 Task 8 的 Repository 测试覆盖。ViewModel 自身的 Android 框架依赖(Application 实例化)在 Task 14 的 `connectedAndroidTest` 套件里跑。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt
git commit -m "feat(ui): add IceSpiritVisionViewModel with StateFlow<AnalysisState>"
```

---

## Task 10: 权限 + AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 加权限**

完整替换 `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.IceSpiritOffline">

        <activity
            android:name=".IceSpiritVisionActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(manifest): add CAMERA + READ_MEDIA_IMAGES permissions"
```

---

## Task 11: 中文字串 + Compose UI

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`

- [ ] **Step 1: 加 strings**

`app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">冰灵锐目</string>
    <string name="action_pick_image">选图</string>
    <string name="action_take_photo">拍照</string>
    <string name="action_analyze">开始分析</string>
    <string name="status_idle">请选择或拍摄一张图片</string>
    <string name="status_ocr_running">识别图片文字…</string>
    <string name="status_rule_scanning">扫描违规规则…</string>
    <string name="status_low_confidence">识别置信度较低,结果仅供参考</string>
    <string name="status_no_violation">未发现违规用语</string>
    <string name="status_violations_count">发现 %1$d 处可疑内容</string>
    <string name="error_ocr_unavailable">OCR 模型加载失败,请检查 APK 是否完整</string>
    <string name="error_ocr_failed">图片识别失败,请换一张清晰图重试</string>
    <string name="error_rules_failed">规则库加载失败</string>
    <string name="action_retry">重试</string>
    <string name="action_exit">退出</string>
    <string name="error_permission_denied">需要相机 / 媒体权限才能分析图片</string>
    <string name="action_grant_permission">去授权</string>
</resources>
```

- [ ] **Step 2: 创建 MainScreen.kt**

`app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt`:

```kotlin
package com.icespiritai.offline.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.R
import com.icespiritai.offline.analysis.AnalysisState
import com.icespiritai.offline.rules.RuleHit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: IceSpiritVisionViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { viewModel.startAnalysis(it) } }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { /* preview URI flows through AnalysisState.Complete.previewUri */ }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result handled inline below */ }

    fun ensurePermissionThenPick() {
        val needsCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED
        if (needsCamera) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::ensurePermissionThenPick) {
                    Text(stringResource(R.string.action_pick_image))
                }
                OutlinedButton(onClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text(stringResource(R.string.action_take_photo))
                }
            }

            when (val s = state) {
                AnalysisState.Idle -> Text(stringResource(R.string.status_idle))

                is AnalysisState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    val label = when (s.stage) {
                        AnalysisState.Loading.Stage.OcrRunning -> R.string.status_ocr_running
                        AnalysisState.Loading.Stage.RuleScanning -> R.string.status_rule_scanning
                    }
                    Text(stringResource(label))
                }

                is AnalysisState.OcrDone -> {
                    if (s.lowConfidence) {
                        Text(
                            stringResource(R.string.status_low_confidence),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text("OCR: ${s.text}", style = MaterialTheme.typography.bodySmall)
                }

                is AnalysisState.RuleScanned -> Text(
                    text = context.getString(R.string.status_violations_count, s.hits.size),
                    style = MaterialTheme.typography.bodyMedium
                )

                is AnalysisState.Complete -> {
                    s.previewUri?.let { AsyncImage(model = it, contentDescription = null) }
                    if (s.report.hits.isEmpty()) {
                        Text(stringResource(R.string.status_no_violation))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.report.hits) { hit -> HitCard(hit) }
                        }
                    }
                }

                is AnalysisState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.retryable) {
                            Button(onClick = { /* re-pick required; user retries */ }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }) {
                            Text(stringResource(R.string.action_grant_permission))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: RuleHit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(hit.matchedText, style = MaterialTheme.typography.titleMedium)
            Text("分类: ${hit.category}", style = MaterialTheme.typography.bodySmall)
            Text("依据: ${hit.regulation}", style = MaterialTheme.typography.bodySmall)
            Text("严重等级: ${hit.severity}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

- [ ] **Step 3: 重写 IceSpiritVisionActivity 为 Compose host**

完整替换 `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`:

```kotlin
package com.icespiritai.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.icespiritai.offline.ui.MainScreen

class IceSpiritVisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainScreen() }
    }
}
```

> 注: `enableEdgeToEdge()` 是 Android 15+/16 强制要求的;AGP 9 + compileSdk 36 下默认可用。

- [ ] **Step 4: 验证编译**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt
git commit -m "feat(ui): add Compose MainScreen + rewire Activity as Compose host"
```

---

## Task 12: 规则 assets(modelProfile 门控待 Task 13)

**Files:**
- Create: `app/src/main/assets/rules/placeholder.json`
- Create: `app/src/main/assets/rules/ad_law_rules.json`

- [ ] **Step 1: 创建 placeholder.json(shell profile 用)**

`app/src/main/assets/rules/placeholder.json`:

```json
{
  "version": 1,
  "rules": []
}
```

- [ ] **Step 2: 创建 ad_law_rules.json(ice_ocr_rules profile 用,10 条 golden case)**

`app/src/main/assets/rules/ad_law_rules.json`:

```json
{
  "version": 1,
  "rules": [
    {
      "id": "medical_absolute",
      "category": "medical",
      "regulation": "《广告法》§16",
      "keywords": ["根治", "100% 有效", "彻底治愈", "无副作用"],
      "severity": "Violation"
    },
    {
      "id": "health_claim",
      "category": "medical",
      "regulation": "《广告法》§17",
      "keywords": ["疗效", "治愈率", "根治率"],
      "severity": "Violation"
    },
    {
      "id": "education_guarantee",
      "category": "education",
      "regulation": "《广告法》§24",
      "keywords": ["保过", "包过", "不过退款", "100% 通过"],
      "severity": "Violation"
    },
    {
      "id": "education_absolute",
      "category": "education",
      "regulation": "《广告法》§24",
      "keywords": ["最好", "最强师资", "第一"],
      "severity": "Warning"
    },
    {
      "id": "finance_promise",
      "category": "finance",
      "regulation": "《广告法》§25",
      "keywords": ["稳赚不赔", "无风险", "保本高收益"],
      "severity": "Violation"
    },
    {
      "id": "realestate_promise",
      "category": "realestate",
      "regulation": "《广告法》§26",
      "keywords": ["升值回报", "投资回报", "学区房包入学"],
      "severity": "Warning"
    },
    {
      "id": "absolute_top",
      "category": "absolute",
      "regulation": "《广告法》§9",
      "keywords": ["最佳", "最好", "第一", "顶级", "唯一"],
      "severity": "Warning"
    },
    {
      "id": "absolute_100",
      "category": "absolute",
      "regulation": "《广告法》§9",
      "keywords": ["100%", "百分百", "百分之百"],
      "severity": "Warning"
    },
    {
      "id": "tobacco_alcohol",
      "category": "restricted",
      "regulation": "《广告法》§22",
      "keywords": ["戒烟", "解酒"],
      "severity": "Info"
    },
    {
      "id": "minor_targeting",
      "category": "minor",
      "regulation": "《广告法》§28",
      "keywords": ["儿童专用", "宝宝必备"],
      "severity": "Info"
    }
  ]
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/assets/rules
git commit -m "feat(rules): add ad_law_rules.json + placeholder.json (10 golden rules)"
```

---

## Task 13: modelProfile 资产门控 Gradle 任务

**Files:**
- Create: `app/prepare-ocr-rules.gradle.kts`
- Modify: `app/build.gradle.kts`(已在 Task 1 中 `apply(from = "prepare-ocr-rules.gradle.kts")`)

- [ ] **Step 1: 创建 Gradle 任务脚本**

`app/prepare-ocr-rules.gradle.kts`:

```kotlin
// app/prepare-ocr-rules.gradle.kts — Gate OCR rule assets per modelProfile.
//
// shell          → assets/rules/placeholder.json only (UI skeleton)
// ice_ocr_rules  → full assets/rules/ad_law_rules.json + placeholder.json
// ice_vision     → empty (Phase 2+, not bundled here)
//
// This task rewrites assets/rules/ad_law_rules.json to {} when shell profile is
// active, so APK stays small. Real assets remain in source control.

val modelProfileValue = providers.gradleProperty("modelProfile").getOrElse("shell")

tasks.register("prepareOcrRulesAssets") {
    group = "build"
    description = "Gate OCR rule assets by modelProfile"
    val srcDir = file("src/main/assets/rules")
    val adLawRules = file("$srcDir/ad_law_rules.json")
    val placeholder = file("$srcDir/placeholder.json")

    doLast {
        when (modelProfileValue) {
            "shell" -> {
                if (adLawRules.exists()) {
                    val original = adLawRules.readText()
                    val slim = """{"version":1,"rules":[]}"""
                    adLawRules.writeText(slim)
                    logger.lifecycle("[prepareOcrRulesAssets] shell profile: slimmed ad_law_rules.json to placeholder")
                    project.extra.set("originalAdLawRules", original)
                }
            }
            "ice_ocr_rules" -> {
                val original = project.extra.get("originalAdLawRules") as? String
                if (original != null && adLawRules.exists()) {
                    adLawRules.writeText(original)
                    logger.lifecycle("[prepareOcrRulesAssets] ice_ocr_rules profile: restored full rules")
                }
            }
            "ice_vision" -> {
                if (adLawRules.exists()) {
                    val original = adLawRules.readText()
                    val slim = """{"version":1,"rules":[]}"""
                    adLawRules.writeText(slim)
                    logger.lifecycle("[prepareOcrRulesAssets] ice_vision profile: rules disabled")
                    project.extra.set("originalAdLawRules", original)
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareOcrRulesAssets")
}
```

- [ ] **Step 2: 验证 task 注册**

Run: `./gradlew.bat :app:tasks --group=build | grep -i prepare`
Expected: `prepareOcrRulesAssets` 在列表中

- [ ] **Step 3: 验证 shell profile 打包后 rules 被清空**

Run: `./gradlew.bat :app:prepareOcrRulesAssets -PmodelProfile=shell`
Expected: 看到 `[prepareOcrRulesAssets] shell profile: slimmed ad_law_rules.json to placeholder`

Run: `./gradlew.bat :app:prepareOcrRulesAssets -PmodelProfile=ice_ocr_rules`
Expected: 看到 `[prepareOcrRulesAssets] ice_ocr_rules profile: restored full rules`

- [ ] **Step 4: 提交**

```bash
git add app/prepare-ocr-rules.gradle.kts
git commit -m "build: add prepareOcrRulesAssets Gradle task gating rules per modelProfile"
```

---

## Task 14: Compose UI 测试

**Files:**
- Create: `app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`

- [ ] **Step 1: 创建 Compose UI 测试**

`app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`:

```kotlin
package com.icespiritai.offline

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IceSpiritVisionActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<IceSpiritVisionActivity>()

    @Test
    fun app_launches_with_idle_status() {
        composeRule.onNodeWithText("请选择或拍摄一张图片").assertExists()
    }

    @Test
    fun pick_image_button_is_present() {
        composeRule.onNodeWithText("选图").assertExists()
    }
}
```

- [ ] **Step 2: 本地跑 connectedAndroidTest**

Run: `./gradlew.bat :app:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL,2 tests passed(在真机 / 模拟器上)

- [ ] **Step 3: 提交**

```bash
git add app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt
git commit -m "test(ui): add Compose UI smoke test for MainScreen"
```

---

## Task 15: 手动 smoke 清单(写入 plan 即可,不写代码)

> 此 Task 无新增代码;把清单作为后续发布的 README 或 docs/smoke/ 文档原料。

- [ ] **Step 1: 验证两个 modelProfile 都能出 APK**

```bash
./gradlew.bat assembleDebug -PmodelProfile=shell
ls app/build/outputs/apk/debug/app-debug.apk   # 应存在
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
ls app/build/outputs/apk/debug/app-debug.apk   # 应存在,且体积 > shell 版
```

- [ ] **Step 2: 装机 smoke**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.icespiritai.vision/.IceSpiritVisionActivity
```

- [ ] **Step 3: 功能验证(checklist)**

| 项 | shell | ice_ocr_rules |
|---|---|---|
| App 启动到首屏 | ✅ | ✅ |
| "选图"按钮可点 | ✅ | ✅ |
| "分析"按钮可见 | ✅ | ✅ |
| 选图后状态从 Idle → Loading → OcrDone → Complete | ✅(Fake OCR) | ✅(RapidOCR — 若已接入) |
| hits 显示规则命中 | ✅(Fake rules) | ✅(真实规则) |
| OCR 失败时显示 Error + 重试按钮 | ✅(手动制造空 text) | ✅ |
| APK 体积 < 25MB | ✅(< 5MB) | ⚠ (取决于 OCR 模型) |

---

## 启动期实测清单(Plan 执行前完成)

下列项在 Plan 启动时**先**逐项验证,再进入 Task 1:

| # | 项 | 命令 | 期望 |
|---|---|---|---|
| 1 | Gradle 9.7.0 Tencent 镜像可达 | curl -I `https://mirrors.cloud.tencent.com/gradle/gradle-9.7-bin.zip` | 200 |
| 2 | AGP 9.3.0 Maven 镜像可达 | curl -I `https://maven.aliyun.com/repository/google/com/android/tools/build/gradle/9.3.0/gradle-9.3.0.pom` | 200 |
| 3 | Kotlin 2.4.10 Maven 镜像可达 | curl -I `https://maven.aliyun.com/repository/public/org/jetbrains/kotlin/kotlin-gradle-plugin/2.4.10/kotlin-gradle-plugin-2.4.10.pom` | 200 |
| 4 | kotlinx-coroutines 1.10.x 实际版本 | Maven Central search | 锁定 patch |
| 5 | kotlinx-serialization 1.9.x 实际版本 | Maven Central search | 锁定 patch |
| 6 | Compose BOM 2026.08.00 实际版本 | developer.android.com/jetpack/compose/bom | 锁定 |
| 7 | Compose Compiler 与 Kotlin 2.4.10 配套 | developer.android.com/jetpack/androidx/releases/compose-kotlin | 锁定 |
| 8 | HankCS Aho-Corasick 0.1.4 类路径 | GitHub release notes | 锁定 import |
| 9 | RapidOCR Android artifact 名 | GitHub RapidAI/RapidOCR releases | 锁定 |
| 10 | ONNX Runtime 1.29.0 arm64-v8a / minSdk 26 兼容 | Maven Central | 锁定 |

---

## 自审(Spec 覆盖)

| Spec 章节 | 覆盖 Task |
|---|---|
| §1 背景与目标 | Task 1, Task 12 |
| §2 baseline | Task 1 |
| §3 架构 | Task 2-9, Task 11 |
| §4 错误处理 | Task 6(OcrEngine exception type)+ Task 8(Repository catch)+ Task 11(UI 渲染) |
| §4 测试 | Task 2-9 单测,Task 14 Compose UI test,Task 15 手动 smoke |
| §5 决策 | Task 1(baseline)+ Task 11(Compose)+ Task 13(modelProfile) |
| §6 待办 | 启动期实测清单 + Task 7(RapidOCR 桩)|

**已知缺口:**
- Task 7 RapidOcrEngine 是桩,真实 RapidOCR Android API 接入为后续独立 PR
- Task 9 ViewModel 单元测试最小化,完整覆盖靠 Robolectric(未在本 plan)
- 启动期实测清单 10 项需在 Plan 执行前完成

---

## 决策登记

| 日期 | 决策 |
|---|---|
| 2026-08-13 | 14 task 实施 plan 锁定 |
| 待(RapidOCR 接入) | ONNX 模型打包策略(全打包 / 首次启动下载)|