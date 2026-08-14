# 冰灵锐目 Phase 2 — 健壮性 / i18n / APK 瘦身 / OCR 真机验证 设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收紧 Phase 1 遗留问题——OCR 处理 EXIF + 防 OOM;错误消息走 i18n 资源;`shell` profile APK 不再打包 OCR native libs(55 MB → ~10 MB);在真机上跑通 OCR 端到端。

**Architecture:** 在 Phase 1 `domain.AnalysisState` 上扩展 `ErrorCode` 枚举;`PaddleOcrEngine` 加 EXIF 旋转 + bitmap 下采样 helper;新增 `OcrEngineFactory` 接口,shell/ice_ocr_rules 两个 sourceSet 各放一个工厂实现;`shell` sourceSet 仅含 `FakeOcrEngineFactory`(不引 ONNX/OpenCV/PaddleOCR AAR);`ice_ocr_rules` 含 `PaddleOcrEngineFactory`(引 native libs)。

**Tech Stack:**
- 既有 baseline(AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 37 / minSdk 26 / arm64-v8a)
- `androidx.exifinterface:exifinterface:1.3.7` — 新增,EXIF 旋转检测
- Gradle 多 sourceSet(`shell` / `ice_ocr_rules`) + 条件 deps
- JUnit 4 / Robolectric 4.13 / AndroidJUnitRunner(沿用)

**Reference:** `docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md`(本 spec 是其增量,**不重写** Phase 1,仅描述 Phase 2 的 4 个增量改造)

**Realistic timeline:** 1.5–2 天(都是局部改造,无新架构)。

---

## §1 OCR 健壮性:EXIF 旋转 + bitmap 下采样

### 1.1 现状

`PaddleOcrEngine.recognize(uri)` 当前:
```kotlin
val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
    BitmapFactory.decodeStream(it)
} ?: throw OcrEngineUnavailable("Failed to open image stream: $uri")
```

两个隐患:
- **EXIF 方向**:竖屏拍的照片 JPEG 里 EXIF tag `orientation=6`(旋转 90°),`BitmapFactory.decodeStream` 返回的 bitmap **未旋转**。OCR 引擎收到横的图,文字识别率显著降低。
- **OOM**:20 MP 图片(5000×4000 像素)解码后 ARGB_8888 占 80 MB,容易 OOM。

### 1.2 设计

新增 helper,职责明确:
- `exifRotationDegrees(uri, context): Int` — 读 EXIF `TAG_ORIENTATION`,返回 0/90/180/270
- `applyExifRotation(bitmap, degrees): Bitmap` — 用 `Matrix.postRotate` 旋转
- `downsampledBitmap(uri, context, maxEdgePx = 2048): Bitmap` — 用 `BitmapFactory.Options(inJustDecodeBounds = true)` 拿原始尺寸,计算 `inSampleSize`,再 decode 一次

`PaddleOcrEngine.recognize()` 改造:
```kotlin
val bitmap = downsampledBitmap(uri, appContext) ?: throw OcrEngineUnavailable(...)
val rotated = applyExifRotation(bitmap, exifRotationDegrees(uri, appContext))
val runResult = ocr.recognize(rotated)
```

### 1.3 测试

**androidTest**:`PaddleOcrExifTest`
- 准备 2 张 fixture(已在 `androidTest/assets/` 下):
  - `test.png` — 已存在(无 EXIF,直向,作为基线)
  - `test_rotated.jpg` — 新增,含 EXIF `orientation=6`,内容是 test.png 的旋转版本(可用 Python PIL 生成)
- 跑 `PaddleOcrEngine.recognize(uri)`,断言识别文本与 `test.png` 跑出来的文本一致(若 OpenCV 在测试设备可用)
- 若 OpenCV 不可用,`assumeTrue` skip(同 `PaddleOcrSmokeTest`)

**单测**:`BitmapDownsampleTest`(Robolectric)
- mock `ContentResolver.openInputStream` 返回预置 `InputStream`
- 验证 `inSampleSize` 计算正确(5000×4000 → inSampleSize = 2 → 解码出 2500×2000)
- 验证最大边长 ≤ `maxEdgePx`(默认 2048)

### 1.4 风险

- EXIF helper 在 `BitmapFactory.decodeStream` 二次调用,有微小的 IO 开销(一次 decodeBounds + 一次 decode)。可接受。
- `applyExifRotation` 在 `degrees == 0` 时应跳过旋转,直接返回原 bitmap(避免无谓的内存分配)。

---

## §2 i18n:错误消息改用 enum + 资源解析

### 2.1 现状

`ImageAnalyzerRepository.kt` 硬编码中文:
```kotlin
emit(AnalysisState.Error(
    message = "OCR 失败: ${e.message ?: "未知错误"}",
    retryable = true,
    cause = e,
))
```

`strings.xml` 已有 `error_ocr_unavailable` / `error_ocr_failed` / `error_rules_failed`,**但没人用**。

### 2.2 设计

`AnalysisState.Error` 加一个 `errorCode` 字段,枚举所有可识别错误:

```kotlin
enum class ErrorCode {
    OCR_UNAVAILABLE,    // 模型缺失 / OpenCV 加载失败 / 内容解析失败
    OCR_FAILED,         // 图片解码失败 / 推理失败 / ImageFormat 异常
    RULES_FAILED,       // rules JSON 加载 / 解析失败(包装失败)
    UNKNOWN,            // 兜底
}

data class Error(
    val message: String,        // 保留:开发期调试信息(cause.class.simpleName)
    val errorCode: ErrorCode,   // 新增:UI 用这个查 R.string
    val retryable: Boolean = false,
    val cause: Throwable? = null,
) : AnalysisState()
```

`ImageAnalyzerRepository.kt` 改为:

```kotlin
} catch (e: OcrEngineUnavailable) {
    emit(AnalysisState.Error(
        message = e.message ?: e.javaClass.simpleName,
        errorCode = ErrorCode.OCR_UNAVAILABLE,
        retryable = true,  // 模型/资源问题重装 APK 后重试
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
} catch (e: RuleLoadFailed) {
    emit(AnalysisState.Error(
        message = e.message ?: e.javaClass.simpleName,
        errorCode = ErrorCode.RULES_FAILED,
        retryable = false,  // 资源缺失,重试无意义
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

`MainScreen.kt` 渲染分支:
```kotlin
is AnalysisState.Error -> {
    val msg = when (s.errorCode) {
        ErrorCode.OCR_UNAVAILABLE -> stringResource(R.string.error_ocr_unavailable)
        ErrorCode.OCR_FAILED -> stringResource(R.string.error_ocr_failed)
        ErrorCode.RULES_FAILED -> stringResource(R.string.error_rules_failed)
        ErrorCode.UNKNOWN -> stringResource(R.string.error_unknown)
    }
    Text(msg, color = MaterialTheme.colorScheme.error)
    // ... retry button ...
}
```

`strings.xml` 加 `error_unknown`: `<string name="error_unknown">未知错误</string>`

### 2.3 测试

`AnalysisStateTest` 加 2 个用例:
- `error_carriesErrorCode` — Error 包含 `errorCode` 字段
- `allErrorCodes_areDistinct` — 每个 `ErrorCode` 值唯一,无缺漏

`ImageAnalyzerRepositoryTest` 更新现有 2 个测试,断言 `errorCode` 字段正确(不只是 `retryable`)。

### 2.4 风险

- `message` 字段保留,纯调试用(显示给开发者或日志,不显示给用户)。UI 不渲染 `message`,只渲染 `errorCode` 解析的 `R.string`。这点要在 code review 时重点确认。
- 枚举扩展性:新增错误类型时只需加一个 `ErrorCode` 值 + 一个 `strings.xml` 条目 + Repository 一个分支。模式稳定。

---

## §3 APK 瘦身:sourceSet 拆分(`shell` vs `ice_ocr_rules`)

### 3.1 现状

55 MB APK 包含:`libopencv_java4.so` (17 MB) + `libonnxruntime.so` (18 MB) + `libc++_shared.so` (6 MB)。即便 shell profile 不调 OCR, native libs 仍然打包——因为它们在 `app/build.gradle.kts` 的统一 `dependencies { implementation(libs.onnxruntime.android) }` 里。

### 3.2 设计

**新增 `OcrEngineFactory` 接口**(放在 `app/src/main/java/...`):

```kotlin
package com.icespiritai.offline.ocr

interface OcrEngineFactory {
    fun create(context: Context): OcrEngine
}
```

**`IceSpiritVisionViewModel`** 改为:
```kotlin
class IceSpiritVisionViewModel(application: Application) : AndroidViewModel(application) {
    private val ocrEngine: OcrEngine = OcrEngineFactoryLocator.create(application)
    // ...其余不变
}

object OcrEngineFactoryLocator {
    fun create(context: Context): OcrEngine =
        ServiceLoader.load(OcrEngineFactory::class.java).firstOrNull()?.create(context)
            ?: error("No OcrEngineFactory found on classpath for modelProfile=${BuildConfig.MODEL_PROFILE}")
}
```

`ServiceLoader` 用 `META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory` 注册。

**两个 sourceSet 各放一个 Factory 实现**:

`app/src/shell/java/com/icespiritai/offline/ocr/FakeOcrEngineFactory.kt`:
```kotlin
class FakeOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context) =
        FakeOcrEngine(cannedText = "本店专治糖尿病,100% 有效", cannedConfidence = 0.9f)
}
```

`app/src/shell/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`:
```
com.icespiritai.offline.ocr.FakeOcrEngineFactory
```

`app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngineFactory.kt`:
```kotlin
class PaddleOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context) = PaddleOcrEngine(context)
}
```

`app/src/ice_ocr_rules/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`:
```
com.icespiritai.offline.ocr.PaddleOcrEngineFactory
```

`PaddleOcrEngine.kt` 从 `app/src/main/java/...` **移到** `app/src/ice_ocr_rules/java/...`。

**Gradle DSL**:

`app/build.gradle.kts` 改造:
- `dependencies` 里 ONNX Runtime / OpenCV / AAR 全部从 `implementation` 改为仅在 `ice_ocr_rules` profile 引入
- 用 `androidComponents.beforeVariants` 或 `sourceSets` 按 profile 激活 sourceSet

`AGP 9.x` 实际形式(`sourceSets { create("name") }` 在 AGP 9 已被拒绝,只能通过 `androidComponents.onVariants` 挂 source dir):

```kotlin
androidComponents {
    onVariants { variant ->
        val javaDir = when (modelProfile) {
            "ice_ocr_rules" -> "src/ice_ocr_rules/java"
            else -> "src/shell/java"
        }
        variant.sources.java?.addStaticSourceDirectory(
            project.projectDir.resolve(javaDir).absolutePath,
        )
    }
}

dependencies {
    // 既有 lifecycle / compose / coroutines / serialization — 不动
    // 仅 ice_ocr_rules 才有:
    if (modelProfile == "ice_ocr_rules") {
        implementation(files("libs/ppocr-sdk.aar"))
        implementation(libs.onnxruntime.android)
        implementation(libs.opencv.android)
    }
    // ServiceLoader: META-INF/services/... 打成 JAR 走 runtimeOnly ——
    // AGP 9 不支持把 META-INF/services/ 挂到 res sourceSet。详情见
    // app/prepare-ocr-rules.gradle.kts 的 `buildProfileServicesJar` KDoc。
    runtimeOnly(files(layout.buildDirectory.dir("generated/services-jar/ocr-engine-services.jar").get().asFile))
}
```

`META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory` 走 `buildProfileServicesJar` 任务打包到 `build/generated/services-jar/ocr-engine-services.jar`,然后在 `dependencies` 里加为 `runtimeOnly files(...)` — AGP 的 `processJavaResources` 会把 JAR 里的 `META-INF/services/...` 抽到 APK 根目录,ServiceLoader 就能找到。`res`/`assets` sourceSet 路径在 AGP 9 会被跳过(`META-INF/` 不是 Android 资源类型)。
    // hankcs AC automaton — main 始终需要
    implementation(libs.hankcs.aho.corasick)
}
```

### 3.3 测试
- 现有 28 单测不动(`FakeOcrEngine` 测试照常跑——`FakeOcrEngine` 仍可在 `main/` 里,**不需要 sourceSet 拆分**)
- 新增:`OcrEngineFactoryLocatorTest`(Robolectric)
  - mock `ServiceLoader.load(OcrEngineFactory::class.java)` 返回 `FakeOcrEngineFactory`
  - 断言 `locator.create(context) is FakeOcrEngine`
- 新增:`FakeOcrEngineFactoryTest`(纯单测)
  - 断言 `factory.create(context).recognize(uri)` 返回 canned OCR result
- shell profile 下编译 `assembleDebug` 应通过,且 `lib/arm64-v8a/libopencv_java4.so` **不在** APK 中
- ice_ocr_rules profile 下编译 `assembleDebug` 通过,`libopencv_java4.so` **在** APK 中

### 3.4 风险

- **ServiceLoader 行为依赖 `META-INF/services/` 被打包**。`app/src/{shell,ice_ocr_rules}/resources/META-INF/services/` 必须被 AGP 正确合并到主 APK。
- **`PaddleOcrEngine.kt` 物理位置变化**:git rename 而非 delete + add,保留 history(用 `git mv`)。
- **`FakeOcrEngine` 留在 `main/` 是有意为之**:shell 依赖它,ice_ocr_rules profile 编译时 `FakeOcrEngine` 类在 classpath(无 native lib 引用)也无害,但 ice_ocr_rules profile 不应通过 `ServiceLoader` 加载它——靠 META-INF/services 注册表隔离。
- **`OcrEngine` 接口本身在 `main/`**(否则两边都引用不了)。

---

## §4 OCR 真机端到端验证:排查 OpenCV native lib 加载失败

### 4.1 现状

- `PaddleOcrSmokeTest` 在真机 `AGQV023313008161` (Android 15) 上 `assumeTrue(OpenCVLoader.initDebug())` 返回 false → SKIPPED
- `PaddleOcrEngineTest` 同样 SKIPPED
- 同样设备 Compose UI 测试(无 OCR 调用)PASS

### 4.2 设计

排查路径(按优先级):

1. **APK 内 `lib/arm64-v8a/` 实际内容**:
   ```bash
   ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep arm64-v8a
   ```
   期望:`libopencv_java4.so` / `libonnxruntime.so` / `libc++_shared.so` 都在。

2. **APK 安装后设备上文件位置**:
   ```bash
   adb shell pm path com.icespiritai.vision
   adb shell run-as com.icespiritai.vision ls /data/data/com.icespiritai.vision/lib/
   # 或(若 allowBackup)
   adb shell ls /data/app/$(adb shell pm path com.icespiritai.vision | sed 's/package://' | head -c 8)
   ```

3. **APK 解压并手动 loadLibrary**:
   - 用 `apktool` 或 `unzip` 抽出 `lib/arm64-v8a/`
   - 在设备上 `adb push` 到 `/data/local/tmp/`
   - `adb shell cd /data/local/tmp && ./libopencv_java4.so` 看 dlopen 错误

4. **AGP `useLegacyPackaging` 检查**:
   - AGP 9.x 默认 `useLegacyPackaging = false`(即 `extractNativeLibs=false`,libs **不**解压到 `/data/app/*/lib/`)
   - OpenCVLoader 期望路径是 `/data/app/*/lib/<abi>/libopencv_java4.so`
   - 若 AGP 改为压缩存储在 APK 内,需 `android:extractNativeLibs="true"` 或 `packaging.jniLibs.useLegacyPackaging = true`

5. **`abiFilters` 冲突**:
   - 当前 `ndk { abiFilters += listOf("arm64-v8a") }`
   - 但 OpenCV AAR 可能含多个 ABI 的 so,APK 实际打包哪个 ABI 取决于 `abiFilters` 和 AAR 内容交集
   - 用 `unzip -l` 确认 arm64-v8a so **确实在**

**最可能修复**:`packaging.jniLibs.useLegacyPackaging = true` —— 在 AGP 9.x 默认是 false,导致 native libs 留在 APK 内而非解压到 `/data/app/*/lib/`,OpenCVLoader 的 `System.loadLibrary("opencv_java4")` 找不到文件。

### 4.3 测试

新增 instrumented test `OpenCvLoadSmokeTest`:
```kotlin
@Test
fun opencv_loads_on_real_device() {
    val loaded = OpenCVLoader.initDebug()
    assertTrue("OpenCVLoader.initDebug() returned false on this device. " +
        "Check packaging.jniLibs.useLegacyPackaging + APK lib/ contents.", loaded)
}
```

跑 `assembleDebug -PmodelProfile=ice_ocr_rules` + 在真机上跑此测试。

### 4.4 风险

- `useLegacyPackaging = true` 会增大安装包体积(libs 不压缩),但已是我们能接受的代价(目前 APK 55 MB,会变成 ~80 MB 但能跑)
- 若排查路径发现是其他问题(如 `System.loadLibrary` 找不到),需要单独的 PR 修;Phase 2 本 spec 只承诺"排查 + 修复一个根因"

---

## §5 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-14 | Phase 2 走 4 项增量:EXIF+OOM / i18n / APK 瘦身 / OCR 验证 | 用户确认 + final review §5 优先 |
| 2026-08-14 | EXIF 旋转走 `Matrix.postRotate`(不用 SurfaceTexture 重新渲染) | 简单;bitmap 已加载完,旋转是 O(1) |
| 2026-08-14 | 下采样阈值默认 2048px(最长边) | OCR 模型 PP-OCRv5_mobile 输入 640×640,2K 长边足够覆盖识别 |
| 2026-08-14 | i18n 走 enum `ErrorCode` 而非 Context.getString | domain 层零 Android 依赖;未来多语言切换零成本 |
| 2026-08-14 | sourceSet 拆分只拆 OcrEngine,`FakeOcrEngine` 留在 `main/` | 用户选择;AdLawRuleMatcher 共享 |
| 2026-08-14 | OcrEngineFactory 通过 `ServiceLoader` 加载 | 标准 JDK 模式,无反射;`META-INF/services/` 是 AGP 资源合并标配 |
| 2026-08-14 | OCR 真机验证优先排查 `useLegacyPackaging` | Phase 0/1 实测已确认 native libs 在 APK,缺解压 |
| 待(Task 1) | EXIF rotation fixture(test_rotated.jpg)生成脚本 | Task 1 implementer |
| 待(Task 3) | sourceSet 拆分后,Gradle `dependency` 条件化语法(AGP 9 是否支持 `if` 在 `dependencies {}`) | Task 3 implementer 验证 |
| 待(Task 4) | `useLegacyPackaging` 是否真的是 root cause | Task 4 implementer 实测 |

---

## §6 实施顺序(用户已确认)

1. **OCR 健壮性**(EXIF + 下采样)— 局部改动,无架构风险
2. **i18n**(enum ErrorCode)— domain 层扩展,影响 Repository + UI
3. **APK 瘦身**(sourceSet split)— build infra 改造,最重
4. **OCR 真机验证**(`useLegacyPackaging` 排查)— 依赖前三项落地后做

每节独立可测、独立可 revert、独立 commit。