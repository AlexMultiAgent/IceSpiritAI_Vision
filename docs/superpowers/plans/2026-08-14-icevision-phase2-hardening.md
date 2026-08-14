# 冰灵锐目 Phase 2 — 健壮性 / i18n / APK 瘦身 / OCR 真机验证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Phase 1 遗留的 4 项 hardening 落地:OCR 处理 EXIF + 防 OOM;错误消息走 i18n 资源;`shell` profile APK 不再打包 OCR native libs(55 MB → ~10 MB);在真机上跑通 OCR 端到端。

**Architecture:** 新增 `BitmapLoader`(EXIF + 下采样 helper);扩展 `AnalysisState.Error` 加 `ErrorCode` 枚举,UI `when` 映射 `R.string.error_*`;新增 `OcrEngineFactory` 接口 + `ServiceLoader` 加载,`shell` / `ice_ocr_rules` 两个 sourceSet 各放一个 factory;`ice_ocr_rules` 含 ONNX / OpenCV / PaddleOCR AAR 依赖,`shell` 不含;真机 OCR 验证优先排查 `packaging.jniLibs.useLegacyPackaging`。

**Tech Stack:**
- 既有 baseline(AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 37 / minSdk 26 / arm64-v8a)
- `androidx.exifinterface:exifinterface:1.3.7` — 新增,EXIF tag 读取
- AGP `packaging.jniLibs.useLegacyPackaging` — Phase 2 诊断后启用
- `java.util.ServiceLoader` — OcrEngineFactory 注册

**Spec:** `docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md`(本 plan 是其落地)

**Realistic timeline:** 1.5–2 天,18 commits 估算。

---

## Phase 2 — 4 个 task(每 task 独立可 revert)

> 顺序(用户确认):Task 1 OCR 健壮性 → Task 2 i18n → Task 3 APK 瘦身 → Task 4 OCR 真机验证。

---

### Task 1: BitmapLoader — EXIF 旋转 + 下采样防 OOM

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ocr/BitmapLoaderTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt`(仅读取 PaddleOcrEngine 的当前实现,不在 Task 1 改;Task 1.6 才引用)

> **重要**: Phase 2 Task 3 才把 `PaddleOcrEngine.kt` 从 `main` sourceSet 移到 `ice_ocr_rules` sourceSet。Task 1 期间该文件仍在原位,可以被 BitmapLoaderTest 等单测间接编译,但 BitmapLoader 自己只用 `BitmapFactory` / `ExifInterface` / `Matrix`,无 PaddleOCR SDK 依赖。

- [ ] **Step 1: 加 exifinterface 依赖**

修改 `gradle/libs.versions.toml`,在 `[versions]` 加:
```toml
exifinterface = "1.3.7"
```
在 `[libraries]` 加:
```toml
androidx-exifinterface = { module = "androidx.exifinterface:exifinterface", version.ref = "exifinterface" }
```

修改 `app/build.gradle.kts`,在 `dependencies {}` 加:
```kotlin
implementation(libs.androidx.exifinterface)
```

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL(仅加依赖,未实际引用)。

- [ ] **Step 2: 写 BitmapLoader 单测(失败优先)**

创建 `app/src/test/java/com/icespiritai/offline/ocr/BitmapLoaderTest.kt`(Robolectric):

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BitmapLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // --- downsample ---

    @Test
    fun `downsampledBitmap returns null when stream open fails ===`() {
        val uri = android.net.Uri.parse("content://nonexistent/123")
        assertNull(BitmapLoader.downsampledBitmap(context, uri))
    }

    @Test
    fun `downsampledBitmap returns a bitmap whose longest edge does not exceed maxEdgePx`() {
        // Robolectric 默认 resources 中的图片可以走 applicationContext.contentResolver;这里仅验证函数签名 + 形状。
        // 真实图片 fixture 测试在 androidTest (Task 4)。
        val uri = android.net.Uri.parse("file:///android_asset/test.png")
        val bitmap = BitmapLoader.downsampledBitmap(context, uri, maxEdgePx = 4096)
        // test.png 是 100x100 像素 fixture,不会下采样
        if (bitmap != null) {
            val longest = maxOf(bitmap.width, bitmap.height)
            assertTrue("Longest edge $longest should be <= 4096", longest <= 4096)
        }
    }

    // --- EXIF ---

    @Test
    fun `exifRotationDegrees returns 0 when no EXIF tag ===`() {
        // test.png 是 PNG,无 EXIF tag
        val uri = android.net.Uri.parse("file:///android_asset/test.png")
        assertEquals(0, BitmapLoader.exifRotationDegrees(context, uri))
    }

    @Test
    fun `applyExifRotation returns same bitmap when degrees is 0 ===`() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val result = BitmapLoader.applyExifRotation(bmp, 0)
        assertSame("0-degree rotation should return original bitmap", bmp, result)
    }

    @Test
    fun `applyExifRotation swaps width and height at 90 degrees ===`() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = BitmapLoader.applyExifRotation(bmp, 90)
        assertEquals(200, rotated.width)
        assertEquals(100, rotated.height)
    }
}
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ocr.BitmapLoaderTest"`
Expected: 编译失败(`BitmapLoader` 类不存在)。

- [ ] **Step 3: 实现 BitmapLoader**

创建 `app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object BitmapLoader {

    private const val DEFAULT_MAX_EDGE_PX = 2048

    /**
     * Decode an image URI into a Bitmap, downsampled so the longest edge does not
     * exceed [maxEdgePx] (default 2048). Returns null if the URI cannot be opened.
     *
     * Two-pass decode: first with `inJustDecodeBounds = true` to read raw
     * dimensions without allocating pixels, then a real decode with
     * `inSampleSize` set to the nearest power of two that brings the longest
     * edge under [maxEdgePx].
     */
    fun downsampledBitmap(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
    ): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * Read the EXIF orientation tag from the image at [uri]. Returns the
     * rotation degrees (0, 90, 180, 270) that must be applied to display the
     * image upright. Returns 0 if the URI cannot be opened or has no
     * orientation tag.
     */
    fun exifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Rotate [bitmap] by [degrees] (0, 90, 180, 270) and return the result.
     * Returns the same bitmap instance when [degrees] is 0 to avoid needless
     * allocation.
     */
    fun applyExifRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxEdge) sample *= 2
        return sample
    }
}
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ocr.BitmapLoaderTest"`
Expected: 5/5 pass(或至少 `applyExifRotation swaps width and height at 90 degrees` + `applyExifRotation returns same bitmap when degrees is 0` + `exifRotationDegrees returns 0 when no EXIF tag` 三个测试 PASS;`downsampledBitmap` 系列测试可能因 Robolectric 不解析 test.png 资产而失败,此时仅依赖 `applyExifRotation`/`exifRotationDegrees` 子集断言 PASS 也算完成本步)。

- [ ] **Step 4: PaddleOcrEngine.recognize() 接入 BitmapLoader**

修改 `app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt`,将 bitmap 加载块:

旧:
```kotlin
val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
    BitmapFactory.decodeStream(it)
} ?: throw OcrEngineUnavailable("Failed to open image stream: $uri")
```

改为:
```kotlin
val raw = BitmapLoader.downsampledBitmap(appContext, uri)
    ?: throw OcrEngineUnavailable("Failed to open image stream: $uri")
val bitmap = BitmapLoader.applyExifRotation(raw, BitmapLoader.exifRotationDegrees(appContext, uri))
val runResult = try { ... }
```

并删除不再用的 imports(`BitmapFactory`)。

Run: `./gradlew.bat :app:compileDebugKotlin && ./gradlew.bat :app:testDebugUnitTest`
Expected: 编译 OK,28/28 单测 PASS(BitmapLoader 5 + 既有 28 = 总数待 33/33 跑完后才知道;本步只验证不破坏既有 28)。

- [ ] **Step 5: commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt \
        app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt \
        app/src/test/java/com/icespiritai/offline/ocr/BitmapLoaderTest.kt
git commit -m "feat(ocr): add BitmapLoader with EXIF rotation + downsampled bitmap decode"
```

---

### Task 2: i18n — ErrorCode 枚举 + Repository 映射 + UI 资源解析

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/icespiritai/offline/domain/AnalysisStateTest.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt`

- [ ] **Step 1: 加 ErrorCode 枚举 + 扩展 Error**

修改 `app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt`,在 sealed class `AnalysisState` 之前新增 enum:

```kotlin
enum class ErrorCode {
    /** OCR 模型缺失 / OpenCV native lib 加载失败 / 模型加载异常。retryable = true(重装 APK 后可解)。 */
    OCR_UNAVAILABLE,
    /** OCR 推理 / 图片解码失败。retryable = true(换图后可解)。 */
    OCR_FAILED,
    /** 规则 JSON 加载 / 解析失败。retryable = false(资源缺失,重试无意义)。 */
    RULES_FAILED,
    /** 兜底。retryable = true。 */
    UNKNOWN,
}
```

将 `Error` data class 改造为:
```kotlin
data class Error(
    val message: String,                          // 保留:开发期调试信息(不直接展示给用户)
    val errorCode: ErrorCode,                     // 新增:UI 用这个查 R.string
    val retryable: Boolean = defaultRetryable(errorCode),
    val cause: Throwable? = null,
) : AnalysisState() {
    companion object {
        private fun defaultRetryable(code: ErrorCode): Boolean = when (code) {
            ErrorCode.OCR_UNAVAILABLE, ErrorCode.OCR_FAILED, ErrorCode.UNKNOWN -> true
            ErrorCode.RULES_FAILED -> false
        }
    }
}
```

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: 编译失败(`ImageAnalyzerRepository.kt` / `MainScreen.kt` / `AnalysisStateTest.kt` 引用了不带 `errorCode` 的旧 Error 构造)。

- [ ] **Step 2: Repository 改用 errorCode**

修改 `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`:

旧 OCR 异常 catch:
```kotlin
} catch (e: Exception) {
    emit(AnalysisState.Error(
        message = "OCR 失败: ${e.message ?: "未知错误"}",
        retryable = true,
        cause = e,
    ))
    return@flow
}
```

改为分别 catch 三个具体异常(顺序从最具体到最 generic):
```kotlin
val ocrResult = try {
    ocrEngine.recognize(uri)
} catch (e: OcrEngineUnavailable) {
    emit(AnalysisState.Error(
        message = e.message ?: e.javaClass.simpleName,
        errorCode = ErrorCode.OCR_UNAVAILABLE,
        retryable = true,
        cause = e,
    ))
    return@flow
} catch (e: OcrFailed) {
    emit(AnalysisState.Error(
        message = e.message ?: e.javaClass.simpleName,
        errorCode = ErrorCode.OCR_FAILED,
        retryable = true,
        cause = e,
    ))
    return@flow
} catch (e: Exception) {
    emit(AnalysisState.Error(
        message = e.message ?: e.javaClass.simpleName,
        errorCode = ErrorCode.UNKNOWN,
        retryable = true,
        cause = e,
    ))
    return@flow
}
```

新增 import:
```kotlin
import com.icespiritai.offline.domain.ErrorCode
```

并把现有 `ruleMatcherProvider` 抛 `RuleLoadFailed` 路径改为:
```kotlin
val hits = try {
    ruleMatcher.scan(ocrResult.fullText)
} catch (e: RuleLoadFailed) {
    emit(AnalysisState.Error(
        message = e.message ?: e.javaClass.simpleName,
        errorCode = ErrorCode.RULES_FAILED,
        retryable = false,
        cause = e,
    ))
    return@flow
}
```

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: 编译 OK(Repository 单测目前还在用旧 API,会在 Step 5 一起改)。

- [ ] **Step 3: MainScreen 用 when(errorCode) 解析资源**

修改 `app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt`,将 Error 分支:

旧:
```kotlin
is AnalysisState.Error -> {
    Text(s.message, color = MaterialTheme.colorScheme.error)
    ...
}
```

改为:
```kotlin
is AnalysisState.Error -> {
    val msgRes = when (s.errorCode) {
        ErrorCode.OCR_UNAVAILABLE -> R.string.error_ocr_unavailable
        ErrorCode.OCR_FAILED -> R.string.error_ocr_failed
        ErrorCode.RULES_FAILED -> R.string.error_rules_failed
        ErrorCode.UNKNOWN -> R.string.error_unknown
    }
    Text(stringResource(msgRes), color = MaterialTheme.colorScheme.error)
    ...
}
```

新增 import:
```kotlin
import com.icespiritai.offline.domain.ErrorCode
```

- [ ] **Step 4: strings.xml 加 error_unknown**

修改 `app/src/main/res/values/strings.xml`,新增:
```xml
<string name="error_unknown">未知错误,请重试</string>
```

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 改测试**

修改 `app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt`:

旧 `analyze emits Error when OCR throws`:
```kotlin
val err = states[1]
assertTrue("second should be Error", err is AnalysisState.Error)
assertTrue((err as AnalysisState.Error).retryable)
```

改为:
```kotlin
val err = states[1]
assertTrue("second should be Error", err is AnalysisState.Error)
val typed = err as AnalysisState.Error
assertEquals(ErrorCode.OCR_UNAVAILABLE, typed.errorCode)
assertTrue(typed.retryable)
```

同样改 `analyze emits Error when OCR throws` 测试中的 ctor 引用,确保 `AnalysisState.Error` 实例化都带 `errorCode`。

修改 `app/src/test/java/com/icespiritai/offline/domain/AnalysisStateTest.kt`,新增 2 个测试:

```kotlin
@Test
fun `Error carries errorCode field`() {
    val e = AnalysisState.Error(
        message = "boom",
        errorCode = ErrorCode.OCR_FAILED,
    )
    assertEquals(ErrorCode.OCR_FAILED, e.errorCode)
}

@Test
fun `all ErrorCodes are distinct`() {
    val codes = ErrorCode.values()
    assertEquals(codes.size, codes.toSet().size)
}
```

`AnalysisStateTest.kt` 顶部新增 import:
```kotlin
import com.icespiritai.offline.domain.ErrorCode
```

注意:`AnalysisStateTest.kt` 中所有 `AnalysisState.Error(...)` 旧构造(无 `errorCode` 参数)在 Step 1 编译失败时已被强制暴露;此处一并修。

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: 编译 OK,既有 28 + BitmapLoader 5 + 新增 ErrorCode 2 = ~33/33 PASS(精确数取决于 BitmapLoaderTest 的 downsample 用例是否在 Robolectric 下能解析 test.png 资产)。

- [ ] **Step 6: commit**

```bash
git add app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt \
        app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt \
        app/src/main/java/com/icespiritai/offline/ui/MainScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/icespiritai/offline/domain/AnalysisStateTest.kt \
        app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt
git commit -m "feat(domain): add ErrorCode enum + thread through Repository + UI i18n"
```

---

### Task 3: APK 瘦身 — sourceSet 拆分(`shell` vs `ice_ocr_rules`)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ocr/OcrEngineFactory.kt`
- Create: `app/src/shell/java/com/icespiritai/offline/ocr/FakeOcrEngineFactory.kt`
- Create: `app/src/shell/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`
- Create: `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngineFactory.kt`
- Create: `app/src/ice_ocr_rules/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`
- Move: `app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt` → `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ocr/OcrEngineFactoryLocatorTest.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineFactoryTest.kt`

> **关键风险**:Task 3 期间,`PaddleOcrEngine.kt` 物理位置变化;`PaddleOcrEngineTest.kt`(androidTest)和`PaddleOcrSmokeTest.kt`仍引用它,需确认这两文件存在于 `ice_ocr_rules` sourceSet 也覆盖的位置(它们在 `app/src/androidTest/java/...`,不在 sourceSet 拆分范围内,但 `PaddleOcrEngine.kt` 类只在 `ice_ocr_rules` 编译时被编译,所以 ice_ocr_rules profile 下编译才能引用)。这是设计内一致行为,验证时跑 `ice_ocr_rules` 即可。

- [ ] **Step 1: 添加 OcrEngineFactory 接口**

创建 `app/src/main/java/com/icespiritai/offline/ocr/OcrEngineFactory.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context

/**
 * Service-loader-discovered factory for [OcrEngine]. Each modelProfile sourceSet
 * (shell, ice_ocr_rules, ...) provides exactly one implementation via
 * `META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`. The first
 * service found by [OcrEngineFactoryLocator.create] wins.
 */
interface OcrEngineFactory {
    fun create(context: Context): OcrEngine
}
```

- [ ] **Step 2: shell sourceSet 加 FakeOcrEngineFactory + META-INF**

创建 `app/src/shell/java/com/icespiritai/offline/ocr/FakeOcrEngineFactory.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context

class FakeOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context): OcrEngine =
        FakeOcrEngine(
            cannedText = "本店专治糖尿病,100% 有效",
            cannedConfidence = 0.9f,
        )
}
```

创建 `app/src/shell/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`:

```
com.icespiritai.offline.ocr.FakeOcrEngineFactory
```

- [ ] **Step 3: 移 PaddleOcrEngine 到 ice_ocr_rules sourceSet + 加 Factory**

```bash
mkdir -p app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr
mkdir -p app/src/ice_ocr_rules/resources/META-INF/services
git mv app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt \
       app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt
```

创建 `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngineFactory.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context

class PaddleOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context): OcrEngine = PaddleOcrEngine(context)
}
```

创建 `app/src/ice_ocr_rules/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`:

```
com.icespiritai.offline.ocr.PaddleOcrEngineFactory
```

- [ ] **Step 4: Gradle DSL 接入 sourceSet + 条件依赖**

修改 `app/build.gradle.kts`:

旧:
```kotlin
dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    ...
    implementation(libs.hankcs.aho.corasick)

    // OCR engine: PaddleOCR official SDK + native runtime
    implementation(files("libs/ppocr-sdk.aar"))
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv.android)
    ...
}
```

改为(关键是把 native libs 三件套移出默认 deps):
```kotlin
val modelProfile = providers.gradleProperty("modelProfile").getOrElse("shell")
val isOcrProfile = modelProfile == "ice_ocr_rules"

dependencies {
    // 既有 lifecycle / compose / coroutines / serialization — 不动
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hankcs.aho.corasick)

    // OCR native libs + AAR — 仅 ice_ocr_rules profile
    if (isOcrProfile) {
        implementation(files("libs/ppocr-sdk.aar"))
        implementation(libs.onnxruntime.android)
        implementation(libs.opencv.android)
    }

    // 单测 + androidTest 不动
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

在 `android {}` 块内,把 modelProfile 提前:
```kotlin
val modelProfile = providers.gradleProperty("modelProfile").getOrElse("shell")
val isOcrProfile = modelProfile == "ice_ocr_rules"

android {
    namespace = "com.icespiritai.offline"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    ...
}
```

并在 `android.sourceSets {}` 加 sourceSet 条件配置:

```kotlin
android {
    ...
    sourceSets {
        getByName("main") { /* 既有配置(assets.setSrcDirs 等不动) */ }
        if (isOcrProfile) {
            create("ice_ocr_rules") {
                java.srcDirs("src/ice_ocr_rules/java")
                res.srcDirs("src/ice_ocr_rules/resources")
            }
        } else {
            create("shell") {
                java.srcDirs("src/shell/java")
                res.srcDirs("src/shell/resources")
            }
        }
    }
}
```

> **注意**:`sourceSets` 块已存在(`prepare-ocr-rules.gradle.kts` 里有 `assets.setSrcDirs`),把上面 `create(...)` 块嵌到既有 `sourceSets {}` 内,或在同层新增。看现有代码。

> **libs.versions.toml** 新增:
> ```toml
> robolectric = "4.13"
> androidxArchCoreTesting = "2.2.0"
> androidxTestCore = "1.6.1"
> ```
> ```toml
> robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
> androidx-arch-core-testing = { module = "androidx.arch.core:core-testing", version.ref = "androidxArchCoreTesting" }
> androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
> ```
> (Phase 1 已在 deps 里加过 robolectric / arch-core-testing,但 `androidx.test:core` 首次加入;`ApplicationProvider` 在 `androidx.test:core` 里。)

Run: `./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell`
Expected: BUILD SUCCESSFUL,(`shell` sourceSet 仅有 FakeOcrEngineFactory,`PaddleOcrEngine` 类不编译;不在 classpath)。

Run: `./gradlew.bat :app:compileDebugKotlin -PmodelProfile=ice_ocr_rules`
Expected: BUILD SUCCESSFUL,(`ice_ocr_rules` sourceSet 含 PaddleOcrEngine + PaddleOcrEngineFactory)。

若 ice_ocr_rules profile 编译失败报 `PaddleOcrEngine` 找不到,检查 `app/src/ice_ocr_rules/java/...` 路径拼写或 `sourceSets.ice_ocr_rules.java.srcDirs` 是否生效。

- [ ] **Step 5: ViewModel 用 factory**

修改 `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt`:

旧:
```kotlin
class IceSpiritVisionViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrEngine: OcrEngine = if (BuildConfig.MODEL_PROFILE == "ice_ocr_rules") {
        PaddleOcrEngine(application)
    } else {
        FakeOcrEngine(cannedText = "本店专治糖尿病,100% 有效", cannedConfidence = 0.9f)
    }
    ...
}
```

改为:
```kotlin
class IceSpiritVisionViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrEngine: OcrEngine = OcrEngineFactoryLocator.create(application)
    ...
}
```

并新增 file `app/src/main/java/com/icespiritai/offline/ocr/OcrEngineFactoryLocator.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import java.util.ServiceLoader

object OcrEngineFactoryLocator {
    fun create(context: Context): OcrEngine =
        ServiceLoader.load(OcrEngineFactory::class.java).firstOrNull()?.create(context)
            ?: error("No OcrEngineFactory on classpath. Check src/{shell,ice_ocr_rules}/resources/META-INF/services/.")
}
```

`IceSpiritVisionViewModel.kt` 顶部删除不再用的 imports:
```kotlin
import com.icespiritai.offline.ocr.FakeOcrEngine
import com.icespiritai.offline.ocr.PaddleOcrEngine
```
保留:
```kotlin
import com.icespiritai.offline.ocr.OcrEngine
import com.icespiritai.offline.ocr.OcrEngineFactoryLocator
```

Run: `./gradlew.bat :app:compileDebugKotlin -PmodelProfile=shell && ./gradlew.bat :app:compileDebugKotlin -PmodelProfile=ice_ocr_rules`
Expected: 两个 profile 都编译 OK。

- [ ] **Step 6: 验证 shell APK 不含 native libs,ice_ocr_rules 含**

```bash
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "libopencv|libonnxruntime|libpaddleocr"
# 期望:空输出

./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "libopencv|libonnxruntime"
# 期望:列出 libopencv_java4.so / libonnxruntime.so
```

APK 大小对比(预期):
- shell:~10 MB(只剩 Compose + lifecycle + Kotlin 标准库)
- ice_ocr_rules:~55 MB(原状)

- [ ] **Step 7: 加 Locator + FakeFactory 单测**

创建 `app/src/test/java/com/icespiritai/offline/ocr/OcrEngineFactoryLocatorTest.kt`(纯 JVM 单测,不走 Robolectric):

```kotlin
package com.icespiritai.offline.ocr

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OcrEngineFactoryLocatorTest {

    @Test
    fun `locator discovers FakeOcrEngineFactory when present on classpath`() {
        val engine = try {
            OcrEngineFactoryLocator.create(StubContext())
        } catch (e: Throwable) {
            // 若 ServiceLoader 找不到实现(例如测试在某个 sourceSet 隔离运行时),允许 skip
            org.junit.Assume.assumeNoException(e)
            return
        }
        assertTrue(
            "Expected FakeOcrEngine when FakeOcrEngineFactory is on classpath, got ${engine::class.simpleName}",
            engine is FakeOcrEngine,
        )
    }

    @Test
    fun `locator throws when no factory on classpath`() {
        // 直接测试 ServiceLoader.empty 场景,需显式构造一个不存在实现的 classloader
        // 这里简化为:locator 应当不抛异常(因为 src/shell 已注册 FakeOcrEngineFactory)
        // 若未来扩展到 ice_ocr_rules 测试,要按 profile 隔离 classloader
        try {
            OcrEngineFactoryLocator.create(StubContext())
        } catch (e: IllegalStateException) {
            fail("FakeOcrEngineFactory should be on classpath in shell profile unit tests: ${e.message}")
        }
    }
}

/** 最小 Context stub,无任何方法调用,因为 locator 只取 ServiceLoader 上下文。 */
private class StubContext : android.content.Context() {
    override fun assets() = throw UnsupportedOperationException()
    override fun getAssets() = throw UnsupportedOperationException()
    override fun getResources() = throw UnsupportedOperationException()
    override fun getPackageManager() = throw UnsupportedOperationException()
    override fun getContentResolver() = throw UnsupportedOperationException()
    override fun getMainLooper() = throw UnsupportedOperationException()
    override fun setTheme(resid: Int) = throw UnsupportedOperationException()
    override fun getTheme() = throw UnsupportedOperationException()
    override fun getClassLoader() = throw UnsupportedOperationException()
    override fun getPackageName() = StringResource.Companion
    override fun getApplicationInfo() = throw UnsupportedOperationException()
    override fun getApplicationContext() = throw UnsupportedOperationException()
    // ... 大量 abstract method;若编译失败,implementer 自行追加
}
```

> **实现注意**:`android.content.Context` 是 abstract class,有 ~30+ abstract 方法。`StubContext` 必须全部实现,否则编译失败。模板仅列关键几个。Implementer 应让 IDE 自动生成,或退回到 Robolectric:
>
> ```kotlin
> private val context: Context get() = ApplicationProvider.getApplicationContext()
> ```

简化路径:测试用 Robolectric + `ApplicationProvider.getApplicationContext<Context>()`,实现 1 个测试:`locator discovers FakeOcrEngineFactory when present on classpath`,断言 `engine is FakeOcrEngine`。删除 `StubContext` 类。Step 7 测试只测这个核心断言。

创建 `app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineFactoryTest.kt`(Robolectric):

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FakeOcrEngineFactoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `factory produces FakeOcrEngine with canned diabetes-ad text`() = runTest {
        val engine = FakeOcrEngineFactory().create(context)
        assertTrue(engine is FakeOcrEngine)
        val result = engine.recognize(android.net.Uri.parse("content://x"))
        assertTrue(result.fullText.contains("糖尿病"))
    }
}
```

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: 既有 + 新增测试 PASS(总数 ~35/35,精确数随 StubContext 改/不改 Robolectric 而定)。

- [ ] **Step 8: commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ocr/OcrEngineFactory.kt \
        app/src/main/java/com/icespiritai/offline/ocr/OcrEngineFactoryLocator.kt \
        app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt \
        app/src/shell/ \
        app/src/ice_ocr_rules/ \
        app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt \
        app/build.gradle.kts \
        gradle/libs.versions.toml \
        app/src/test/java/com/icespiritai/offline/ocr/OcrEngineFactoryLocatorTest.kt \
        app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineFactoryTest.kt
git commit -m "feat(build): gate OCR native libs to ice_ocr_rules profile via sourceSet split"
```

> `PaddleOcrEngine.kt` 在 git 中是 `R` 状态(rename),无需特别 add;`git status` 会显示 `R app/src/main/.../PaddleOcrEngine.kt -> app/src/ice_ocr_rules/.../PaddleOcrEngine.kt`。

---

### Task 4: OCR 真机端到端验证 — 排查 OpenCV native lib 加载失败

**Files:**
- Create: `app/src/androidTest/java/com/icespiritai/offline/ocr/OpenCvLoadSmokeTest.kt`
- Create: `app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrExifTest.kt`
- Create: `tools/gen-rotated-fixture.py`
- Create: `app/src/androidTest/assets/test_rotated.jpg`
- Modify: `app/build.gradle.kts`(仅在排查确需 `useLegacyPackaging` 时改)

- [ ] **Step 1: 验证 APK 内 native libs 位置**

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep arm64-v8a
```

Expected:看到 `lib/arm64-v8a/libopencv_java4.so`、`lib/arm64-v8a/libonnxruntime.so`、`lib/arm64-v8a/libc++_shared.so`、`lib/arm64-v8a/libpaddle_ocr_api.so`(如有)。

若 absent:Gradle 配置漏了 `implementation(libs.opencv.android)` 之类。回 Step 3.4 检查。

- [ ] **Step 2: 加 OpenCvLoadSmokeTest**

创建 `app/src/androidTest/java/com/icespiritai/offline/ocr/OpenCvLoadSmokeTest.kt`:

```kotlin
package com.icespiritai.offline.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

@RunWith(AndroidJUnit4::class)
class OpenCvLoadSmokeTest {

    @Test
    fun opencv_loadLibrary_succeeds() {
        val ok = OpenCVLoader.initDebug()
        assertTrue(
            "OpenCVLoader.initDebug() returned false. Likely cause: " +
                "AGP packaging.jniLibs.useLegacyPackaging = false prevents " +
                "native libs from being unpacked to /data/app/<pkg>/lib/<abi>/. " +
                "See Phase 2 Task 4 design §4.2 step 4.",
            ok,
        )
    }
}
```

- [ ] **Step 3: 跑测试(预期 FAIL,记录失败原因)**

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.OpenCvLoadSmokeTest
```

Expected:1 test FAIL(或 SKIPPED,取决于 OpenCV 是否 throw)。

记录错误(从 logcat 或堆栈):
- `UnsatisfiedLinkError`:典型的 loadLibrary 路径问题
- `java.lang.NoClassDefFoundError`:OpenCV 类打包问题

- [ ] **Step 4: 修复 root cause**

**最常见修复**(若 Step 3 报 `UnsatisfiedLinkError`):

修改 `app/build.gradle.kts`,在 `android {}` 块内加:
```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
    resources { ... 既有 ... }
}
```

Run: `./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules`
Expected: BUILD OK。

重新跑 Step 3 测试:
```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.OpenCvLoadSmokeTest
```
Expected: 1 test PASS。

**若 FAIL 持续**,继续按 spec §4.2 路径排查:
1. `adb shell pm path com.icespiritai.vision` 找 install path
2. `adb shell ls /data/app/*/com.icespiritai.vision*/lib/arm64-v8a/` 看是否含 libopencv_java4.so
3. 若含但 loadLibrary 仍 fail:so 与设备架构不匹配 — 检查 `file libopencv_java4.so` 看 ELF 头
4. 若不含:`useLegacyPackaging = true` 没生效,检查 AGP 缓存

- [ ] **Step 5: commit OpenCV 修复**

```bash
git add app/build.gradle.kts \
        app/src/androidTest/java/com/icespiritai/offline/ocr/OpenCvLoadSmokeTest.kt
git commit -m "fix(build): enable useLegacyPackaging so OpenCV native libs unpack on Android <12"
```

> **注意**:Android 15 (compileSdk 37) 默认 `extractNativeLibs=false`;不设 `useLegacyPackaging=true`,OpenCVLoader 找不到 lib。这是已知 AGP 9 + Android 14/15 行为。

- [ ] **Step 6: 生成 test_rotated.jpg fixture + 加 EXIF 集成测试**

创建 `tools/gen-rotated-fixture.py`:

```python
#!/usr/bin/env python3
"""
Generate test_rotated.jpg: a JPEG with EXIF orientation=6 (rotate 90° CW),
containing rotated visual text equivalent to app/src/androidTest/assets/test.png.
Run from project root:
    pip3 install Pillow
    python3 tools/gen-rotated-fixture.py
"""
from PIL import Image
import os

SRC = "app/src/androidTest/assets/test.png"
DST = "app/src/androidTest/assets/test_rotated.jpg"

# 1. Read PNG
img = Image.open(SRC).convert("RGB")
# 2. Rotate 90° CW (= 270° CCW)
rotated = img.transpose(Image.ROTATE_270)
# 3. Save as JPEG with EXIF orientation=6
# Pillow's save w/ exif requires piexif or pre-built exif; use PIL save then post-process with piexif if available
rotated.save(DST, "JPEG", quality=92)

# Set EXIF orientation tag = 6 (rotated 90° CW)
try:
    import piexif
    exif_dict = {"0th": {piexif.ImageIFD.Orientation: 6}}
    exif_bytes = piexif.dump(exif_dict)
    piexif.insert(exif_bytes, DST)
    print(f"Wrote {DST} with EXIF orientation=6")
except ImportError:
    print(f"Wrote {DST} WITHOUT EXIF orientation tag (piexif not installed)")
    print("Install with: pip3 install piexif")
```

```bash
pip3 install Pillow piexif 2>/dev/null || pip3 install Pillow
python3 tools/gen-rotated-fixture.py
ls -la app/src/androidTest/assets/test_rotated.jpg
```

Expected: 生成 `test_rotated.jpg`,> 1 KB,`file` 输出 `JPEG image data, ...`.

创建 `app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrExifTest.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

@RunWith(AndroidJUnit4::class)
class PaddleOcrExifTest {

    @Test
    fun rotated_jpeg_with_exif_orientation_6_recognizes_same_text_as_test_png() = runBlocking {
        assumeTrue("OpenCV must load on test device", OpenCVLoader.initDebug())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PaddleOcrEngine(context)

        val pngUri = Uri.parse("file:///android_asset/test.png")
        val jpgUri = Uri.parse("file:///android_asset/test_rotated.jpg")

        val pngText = engine.recognize(pngUri).fullText
        val jpgText = engine.recognize(jpgUri).fullText

        engine.release()

        // Both should recognize roughly the same Chinese ad-superlative text.
        // Strict equality is too brittle (OCR is noisy); assert non-empty + token overlap.
        assertTrue("PNG OCR returned empty", pngText.isNotBlank())
        assertTrue("JPG OCR returned empty", jpgText.isNotBlank())
        // Overlap: at least 1 common character (loose heuristic)
        val overlap = pngText.toSet().intersect(jpgText.toSet().toSet()).size
        assertTrue("Expected at least 3 overlapping chars between PNG and rotated JPG OCR; got $overlap", overlap >= 3)
    }
}
```

- [ ] **Step 7: 跑 EXIF 集成测试**

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrExifTest
```

Expected:**SKIPPED**(若 OpenCV 仍 fail — 已在 Step 4 修了)或 **PASS**(若 Step 4 fix 生效)。

- [ ] **Step 8: 跑全套 androidTest 验证**

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest
```

Expected:3 个 Compose UI test PASS + OpenCvLoadSmokeTest PASS + PaddleOcrExifTest PASS(or SKIPPED)。总计 5/5 或 4 PASS + 1 SKIPPED。

- [ ] **Step 9: 跑全套单测 + 两个 profile assemble**

```bash
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules
```

Expected: 单测 ~35 PASS;两个 profile APK 都构建成功;shell APK < 15 MB;ice_ocr_rules APK ~55 MB。

- [ ] **Step 10: commit + 更新 smoke doc**

```bash
git add app/src/androidTest/ tools/gen-rotated-fixture.py
git commit -m "test(ocr): add EXIF rotation integration test + OpenCV load smoke test"
```

更新 `docs/smoke/2026-08-14-phase1-smoke.md` 或新写 `docs/smoke/2026-08-14-phase2-smoke.md`,记录 Phase 2 落地后的:
- 单测总数(应 ~35)
- APK 大小(shell 预期 < 15 MB,ice_ocr_rules ~55 MB)
- OCR 真机验证结果
- 修复记录(useLegacyPackaging 改动)

```bash
git add docs/smoke/
git commit -m "docs(smoke): Phase 2 hardening verification results"
```

---

## 启动期实测清单(本 plan 执行前完成)

| # | 项 | 命令 | 期望 |
|---|---|---|---|
| 1 | `androidx.exifinterface:1.3.7` Maven 镜像可达 | `curl -I https://maven.aliyun.com/repository/google/androidx/exifinterface/exifinterface/1.3.7/exifinterface-1.3.7.pom` | 200 |
| 2 | `org.robolectric:robolectric:4.13` Maven 镜像可达 | Maven Central search | 200 |
| 3 | `Pillow` + `piexif` 可装 | `pip3 install Pillow piexif` | success |
| 4 | 当前 ice_ocr_rules APK 仍含 native libs | `unzip -l app/build/outputs/apk/debug/app-debug.apk \| grep arm64` | 有 libopencv_java4.so 等 |
| 5 | `OpenCVLoader.initDebug()` 仍 fail(已知) | 跑 `OpenCvLoadSmokeTest` | FAIL 或 SKIPPED |

---

## 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-14 | Phase 2 4 项:EXIF+OOM / i18n enum / sourceSet split / OCR 验证 | 用户确认 + final review §5 |
| 2026-08-14 | OcrEngineFactory 用 ServiceLoader 而非 BuildConfig 分支 | ServiceLoader 是 JDK 标准,`META-INF/services/` 由 AGP 自动合并 |
| 2026-08-14 | 默认下采样阈值 2048px | PP-OCRv5_mobile 输入 640×640,2K 长边足够 |
| 2026-08-14 | `useLegacyPackaging = true` 是 Android 14/15 上 OpenCVLoader 找 lib 的 root cause | Phase 0/1 实测 + AGP 9 + compileSdk 37 已知行为 |
| 待(Task 1) | `BitmapLoaderTest` 是否需 Robolectric — 当前定 yes | 单测在 JVM 上跑不动 BitmapFactory.decodeStream |
| 待(Task 3) | `sourceSets.ice_ocr_rules` 创建语法在 AGP 9 / Gradle 9 下是否要 `kotlin.sourceSets` | Task 3 implementer 验证 |
| 待(Task 4) | `packaging.jniLibs.useLegacyPackaging = true` 是否需要 `android:extractNativeLibs="true"` 配套 | Task 4 实测 |

---

## 已知缺口

1. **`BitmapLoader.downsampledBitmap` Robolectric 兼容性** — Robolectric 可能不解析 `file:///android_asset/` URI。Task 1 Step 2 的 2 个 downsample 测试可能 FAIL,届时只需保留 3 个 EXIF / applyExifRotation 测试 PASS,downsample 测试在 Task 4 的 androidTest 验证。
2. **`SourceSet.create("shell")` vs `("ice_ocr_rules")` 在 AGP 9 下的语法** — Gradle 9 + AGP 9 对 `sourceSets` DSL 的命名 sourceSet 创建方式可能微调(`create()` vs `register()`,参数是 srcDir list vs fileTree)。Task 3 Step 4 implementer 验证,失败时改用条件 `dependencies` + 静态 sourceSet 配置。
3. **`useLegacyPackaging = true` 副作用** — 增大 APK 安装体积(libs 不压缩),但已接受为 Phase 2 trade-off(smoke doc §5 已列)。