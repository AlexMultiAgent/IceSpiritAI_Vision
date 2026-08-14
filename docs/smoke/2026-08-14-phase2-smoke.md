# Phase 2 烟测结果 — 2026-08-14

> Task 4 (Phase 2 最后一项):落地 `packaging.jniLibs.useLegacyPackaging = true` 修复 OpenCV native lib 在测试真机上加载失败的问题;落地 `BitmapLoader` EXIF 测试 + 集成测试;产物与本地构建/单测结果。

## 1. Phase 2 落地汇总

| Task | 标题 | commit |
| --- | --- | --- |
| 1 | BitmapLoader — EXIF 旋转 + 下采样防 OOM | `155985b feat(ocr): add BitmapLoader with EXIF rotation + downsampled bitmap decode` |
| 2 | i18n — ErrorCode 枚举 + Repository 映射 + UI 资源 | `acc9ad9 feat(domain): add ErrorCode enum + thread through Repository + UI i18n` |
| 3 | APK 瘦身 — sourceSet 拆分(`shell` vs `ice_ocr_rules`) | `d9e341b feat(build): gate OCR native libs to ice_ocr_rules profile via sourceSet split` |
| 4 | OCR 真机验证 — OpenCV native lib 加载 | `a7a7b4f fix(build): enable useLegacyPackaging so OpenCV native libs unpack on Android 14/15` |

## 2. 单元测试

`./gradlew.bat :app:testDebugUnitTest`

```
40 tests, 0 failures, 0 errors
```

按测试类:

| 测试类 | 数量 |
| --- | --- |
| `IceSpiritVisionViewModelTest` | 4 |
| `ImageAnalyzerRepositoryTest` | 8 |
| `AnalysisStateTest` | 6 |
| `BitmapLoaderTest` | 6 |
| `FakeOcrEngineTest` | 4 |
| `OcrEngineFactoryLocatorTest` | 2 |
| `AdLawRuleMatcherTest` | 5 |
| `AdLawRuleTest` | 3 |
| `AssetRuleLoaderTest` | 2 |
| **Total** | **40** |

Phase 1 末(24 测试) → Phase 2 末(40 测试):+16(BitmapLoader 6 + ErrorCode 2 + Repository 6 + Locator 2 等)。

## 3. 构建产物

`./gradlew.bat :app:assembleDebug -PmodelProfile=<profile>`

| Profile | APK 大小 | 内容 | `libopencv_java4.so` |
| --- | --- | --- | --- |
| `shell` (default) | 50.1 MB | UI + Compose + Activity + Lifecycle + FakeOcrEngine;**不含** PaddleOCR/ONNX/OpenCV AAR | ❌ absent |
| `ice_ocr_rules` | 57.2 MB | shell 之上 + ONNX Runtime + OpenCV + PaddleOCR AAR + 模型文件 | ✅ present(17.1 MB, uncompressed) |

### Native libs 状况(`ice_ocr_rules` profile APK,`lib/arm64-v8a/`)

| 文件 | 大小(uncompressed) |
| --- | --- |
| `libopencv_java4.so` | 17.1 MB |
| `libonnxruntime.so` | 17.9 MB |
| `libc++_shared.so` | 6.2 MB |
| `libonnxruntime4j_jni.so` | 100.6 KB |
| `libandroidx.graphics.path.so` | 10.1 KB(Compose 自带) |

## 4. Phase 2 关键改动

### 4.1 `BitmapLoader` — EXIF + OOM 防护

文件:`app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt`

- `bytes(context, uri)` — 一次性读全部字节,避免重复打开 stream
- `downsampledBitmap(bytes, maxEdgePx=2048)` — 两段式解码(bounds 探测 → `inSampleSize` 下采样),防止 20 MP+ 大图 OOM
- `exifRotationDegrees(bytes)` — 读取 EXIF orientation tag,返回 0/90/180/270
- `applyExifRotation(bitmap, degrees)` — `Matrix.postRotate` 旋转;`degrees == 0` 时返回原 bitmap 实例(零拷贝)

`PaddleOcrEngine.recognize()` 已接入(`ice_ocr_rules` sourceSet):先 bytes → downsampledBitmap → applyExifRotation → `ocr.recognize(bitmap)`。

### 4.2 ErrorCode — i18n

文件:`app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt`

`AnalysisState.Error` 新增 `errorCode: ErrorCode` 字段 + 默认 `retryable` 计算。`MainScreen` 用 `when(errorCode)` 映射 `R.string.error_*`。`R.string.error_unknown` 已新增。

`ImageAnalyzerRepository` 三段 catch:`OcrEngineUnavailable → OCR_UNAVAILABLE`、`OcrFailed → OCR_FAILED`、generic `Exception → UNKNOWN`,以及 `RuleLoadFailed → RULES_FAILED`(retryable=false)。

### 4.3 sourceSet 拆分 — APK 瘦身

文件:`app/src/main/java/com/icespiritai/offline/ocr/OcrEngineFactory.kt` + `OcrEngineFactoryLocator.kt`(主 sourceSet)+ `app/src/shell/java/.../FakeOcrEngineFactory.kt` + `app/src/ice_ocr_rules/java/.../PaddleOcrEngineFactory.kt` + `META-INF/services/...`

`PaddleOcrEngine.kt` 从 `main` 移到 `ice_ocr_rules` sourceSet。`shell` profile 不再打包 `libs/ppocr-sdk.aar` / `onnxruntime` / `opencv-android` AAR,故 shell APK 不再含 `libopencv_java4.so` / `libonnxruntime.so` / `libc++_shared.so`(占用 ~41 MB)。

注:Phase 1 末 shell APK 估算 ~15 MB 是乐观值;实际 shell APK 仍为 ~50 MB,差异主要来自 Compose + Material3 + Activity + Lifecycle 完整依赖。这部分优化留 Phase 3(R8 minify + dynamic feature module)。

### 4.4 OpenCV native lib 加载修复

文件:`app/build.gradle.kts`

```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
    ...
}
```

**根因**:AGP 9 默认 `useLegacyPackaging = false`,在 Android 14/15(compileSdk 37,`extractNativeLibs` 默认 false)上,`System.loadLibrary("opencv_java4")` 在 `lib/arm64-v8a/` 下找不到未解压的 .so(`app-debug.apk` 中 .so 是压缩的)。

**修复**:开启 `useLegacyPackaging = true`,AGP 在打包时不再压缩 .so,使 `System.loadLibrary` 能正确 `dlopen` 路径下的文件。副作用:APK 中 .so 文件大小 = 未压缩字节数(因而不被压缩)。

**验证点**:
- ice_ocr_rules APK 中 `lib/arm64-v8a/libopencv_java4.so` 现以 17,109,960 bytes 完整存储(uncompressed)
- `OpenCvLoadSmokeTest` 在真机上应 PASS(用户跑命令;见 §5)

## 5. Android Instrumentation(待用户验证)

> 本环境无设备,instrumented 测试需用户在真机/AVD 上跑。

### OpenCV native lib 加载(关键!)

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.OpenCvLoadSmokeTest
```

**期望**:1 test PASS。`OpenCVLoader.initDebug()` 返回 true。如果仍 FAIL,说明 `useLegacyPackaging = true` 在 Android 15 真机上不够,需要继续按 §6 排查路径走。

### EXIF 旋转集成测试

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrExifTest
```

**期望**:1 test PASS。`test_rotated.jpg`(EXIF orientation=6)与 `test.png` OCR 文本有 ≥ 3 个共同字符。前提:OpenCV native lib 加载成功(§5.1)。

### PaddleOcrEngine 端到端(替换原有 SKIP)

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrEngineTest
```

**期望**:1 test PASS(原本 SKIP)。真实端到端跑 PaddleOCR SDK + BitmapLoader。

### 全部 instrumentation

```bash
ANDROID_SERIAL=AGQV023313008161 ./gradlew.bat :app:connectedDebugAndroidTest
```

**期望**:Compose UI test(3)+ OpenCvLoadSmokeTest(1)+ PaddleOcrExifTest(1)+ PaddleOcrEngineTest(1)= 6 PASS(若 OpenCV 加载成功);或 4 PASS + 2 SKIPPED(若 OpenCV 仍 fail — 此时 §4.4 修复未生效,需进一步排查)。

## 6. 已知未结清项 / 跟进

1. **OpenCV native lib 真机验证未在本环境跑出** — 需用户在 `AGQV023313008161` 上跑 §5.1 命令确认 PASS。若 FAIL,继续排查:
   - `adb shell pm path com.icespiritai.vision` 找 install path
   - `adb shell ls /data/app/*/com.icespiritai.vision*/lib/arm64-v8a/` 看 .so 是否解压
   - 若解压但仍 fail:`file` 看 ELF 头 + `adb shell getprop ro.product.cpu.abi` 对照 arm64-v8a
   - 若未解压:clean build + `useLegacyPackaging = true` 重设;清 AGP 缓存 `./gradlew.bat clean`
2. **shell APK 体积 ~50 MB** — 当前 shell APK 仍含完整 Compose + Material3 + Activity 依赖。后续优化方向:R8 minify(预期 -30%)、dynamic feature module 把 OCR 拆出(预期 -40 MB if 也走冰灵慧语 / 冰灵智译共用)。
3. **`BitmapLoader` 单测在 Robolectric 下读取 androidTest assets 的边界** — 当前 `BitmapLoaderTest` 的 downsample 测试在 Robolectric 跑通(exif / 旋转测试 6/6 PASS)。
4. **`PaddleOcrSmokeTest` 已被 `PaddleOcrEngineTest` 替代为生产路径** — 保留 `PaddleOcrSmokeTest.kt` 不删作为 SDK 直通烟测;两条路径独立验证。

## 7. Phase 2 验收 sign-off

| 标准 | 状态 |
| --- | --- |
| BitmapLoader 单测 PASS | ✅(6/6) |
| ErrorCode 枚举 + UI 资源映射 PASS | ✅(单测覆盖) |
| sourceSet 拆分 — shell APK 不含 OCR native libs | ✅(`unzip -l ... | grep arm64` 仅 `libandroidx.graphics.path.so`) |
| sourceSet 拆分 — ice_ocr_rules APK 含 OCR native libs | ✅(libopencv/onnxruntime/c++_shared 全在) |
| 40/40 单测 PASS | ✅ |
| `assembleDebugAndroidTest -PmodelProfile=ice_ocr_rules` 编译成功 | ✅ |
| `useLegacyPackaging = true` 落地,APK 中 .so uncompressed | ✅ |
| OpenCVLoader 真机 PASS | ⏳ 待用户跑 §5.1 |
| PaddleOcrExifTest 真机 PASS | ⏳ 待用户跑 §5.2 |
| PaddleOcrEngineTest 真机 PASS(替换原 SKIP) | ⏳ 待用户跑 §5.3 |

**结论**:Phase 2 编译、单测、APK 产物均在本环境验证通过;OpenCV 真机端到端验证是最后一道关,需用户在设备上跑 §5.1/§5.2/§5.3 三条命令确认。