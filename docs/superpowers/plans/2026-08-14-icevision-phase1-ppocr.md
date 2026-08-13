# 冰灵锐目 Phase 1 — PaddleOCR v3.7.0 + 规则库文字审核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Supersedes `2026-08-13-icevision-phase1-ocr-rules.md`(原 RapidOCR / JitPack 路径)与 `2026-08-14-icevision-phase1-paddle-ocr.md`(误把 PaddleOCR-slim 当 Paddle Lite 源码 build)。

**Goal:** 用户拍/选图 → 本地 PaddleOCR 官方 SDK 抽文字 → AC 自动机匹配《广告法》规则 → 输出违规清单 + 法规条款引用。

**Architecture:** 单 Activity(Compose UI)+ ViewModel(StateFlow<AnalysisState>)+ Repository 编排 PaddleOcrEngine(包 PaddleOCR 官方 SDK)+ AdLawRuleMatcher(HankCS AC 自动机)。PaddleOCR 官方 `ppocr-sdk` AAR 通过 ONNX Runtime + OpenCV 推理预量化 ONNX 模型,无需自建 NDK。AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 36 / targetSdk 36 / minSdk 26。

**Tech Stack:**
- Jetpack Compose 1.12.x, kotlinx-coroutines 1.10.x, kotlinx-serialization 1.9.x
- **`com.paddle.ocr:ppocr-sdk`**(v3.7.0,本地 build AAR)— PaddleOCR 官方 SDK,内含 ONNX Runtime + OpenCV 推理
- **`com.microsoft.onnxruntime:onnxruntime-android:1.21.1`** — PaddleOCR SDK 用,但不通过 `api()` 暴露,需 app 直接声明
- **OpenCV Android**(`com.quickbirdstudios:opencv:4.5.3` 或 PaddleOCR SDK 自带版本)— 同上
- **PP-OCRv5_mobile** 或 **PP-OCRv6_small** ONNX 模型(预量化,直接下载)
- **`com.hankcs:aho-corasick-double-array-trie:1.2.3`**(Maven Central)— AC 自动机

**Spec:** `docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md`(本 plan 不重写 spec,仅 §2.2 / §3.2 改 `RapidOCR` 为 PaddleOCR 官方 SDK,其余不动)

**Realistic timeline:** **3-5 天**(AAR build + 模型下载 + Kotlin 集成)。NDK 路径已废弃,本 plan 显著短于 `2026-08-14-icevision-phase1-paddle-ocr.md` 的"1-2 周"误估。

---

## Phase 0 — PaddleOCR 官方 SDK + 模型(4 task)

> 本 Phase 在执行 Phase 1 之前完成。所有 build / 下载产物 `.gitignore`(`tools/paddleocr/`、`app/libs/ppocr-sdk.aar`、`app/src/main/assets/models/*.onnx`)。

### Task 0.1: PaddleOCR 仓库 clone + tools 脚本骨架

**Files:**
- Create: `tools/build-ppocr-sdk.sh`
- Create: `tools/download-ppocr-models.sh`
- Modify: `.gitignore`(添加工具/产物 gitignore 条目)
- Create: `app/libs/.gitkeep`
- Create: `app/src/main/assets/models/.gitkeep`

- [ ] **Step 1: 写 build-ppocr-sdk.sh**

`tools/build-ppocr-sdk.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Build PaddleOCR official ppocr-sdk AAR (v3.7.0).
# Output: app/libs/ppocr-sdk.aar (gitignored).
# Run from project root.

REPO_DIR="tools/paddleocr"
REPO_URL="https://github.com/PaddlePaddle/PaddleOCR.git"
REPO_TAG="v3.7.0"
SDK_DIR="${REPO_DIR}/deploy/ppocr-android/ppocr-sdk"
AAR_OUT="app/libs/ppocr-sdk.aar"

if [ ! -d "$REPO_DIR" ]; then
    echo "[build-ppocr-sdk] Cloning PaddleOCR ${REPO_TAG} (sparse)..."
    git clone --depth 1 --branch "$REPO_TAG" --filter=blob:none --sparse "$REPO_URL" "$REPO_DIR"
    git -C "$REPO_DIR" sparse-checkout set deploy/ppocr-android
fi

echo "[build-ppocr-sdk] Building AAR..."
cd "$SDK_DIR"
./gradlew :ppocr-sdk:assembleRelease --no-daemon

AAR_BUILT=$(find . -name 'ppocr-sdk-release.aar' -path '*/outputs/aar/*' | head -1)
if [ -z "$AAR_BUILT" ]; then
    echo "[build-ppocr-sdk] ERROR: AAR not found in build outputs" >&2
    exit 1
fi

mkdir -p "$(git rev-parse --show-toplevel)/app/libs"
cp "$AAR_BUILT" "../../../../$(git rev-parse --show-toplevel)/${AAR_OUT}"

echo "[build-ppocr-sdk] Done. AAR at ${AAR_OUT}"
```

- [ ] **Step 2: 写 download-ppocr-models.sh**

`tools/download-ppocr-models.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Download PP-OCRv5_mobile ONNX models into app/src/main/assets/models/.
# Run from project root.

MODEL_VARIANT="${1:-pp-ocrv5_mobile}"  # or pp-ocrv6_small / pp-ocrv6_tiny
HF_BASE="https://huggingface.co/PaddlePaddle"
BOS_BASE="https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0"

DET_DIR="app/src/main/assets/models/det"
REC_DIR="app/src/main/assets/models/rec"

mkdir -p "$DET_DIR" "$REC_DIR"

download() {
    local name="$1" hf_path="$2" bos_path="$3"
    local target="$4"

    if [ -f "$target" ]; then
        echo "[download] Skip existing: $target"
        return
    fi

    if curl -fsSL --max-time 60 "${HF_BASE}/${hf_path}" -o "$target"; then
        echo "[download] OK from HF: $target"
        return
    fi

    echo "[download] HF failed, trying BOS..."
    if curl -fsSL --max-time 120 "${BOS_BASE}/${bos_path}" -o "${target}.tar"; then
        tar -xf "${target}.tar" -C "$(dirname "$target")"
        rm -f "${target}.tar"
        echo "[download] OK from BOS: $target"
    else
        echo "[download] ERROR: both HF and BOS failed for $name" >&2
        exit 1
    fi
}

case "$MODEL_VARIANT" in
    pp-ocrv5_mobile)
        download "PP-OCRv5_mobile_det" \
            "PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx" \
            "PP-OCRv5_mobile_det_onnx_infer.tar" \
            "${DET_DIR}/inference.onnx"

        download "PP-OCRv5_mobile_rec_model" \
            "PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" \
            "PP-OCRv5_mobile_rec_onnx_infer.tar" \
            "${REC_DIR}/inference.onnx"

        download "PP-OCRv5_mobile_rec_config" \
            "PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml" \
            "" \
            "${REC_DIR}/inference.yml"
        ;;
    pp-ocrv6_small|pp-ocrv6_tiny)
        SUFFIX=$(echo "$MODEL_VARIANT" | tr 'a-z-' 'A-Z_')  # pp-ocrv6_small -> PP-OCRV6_SMALL
        download "${SUFFIX}_det" \
            "${SUFFIX}_det_onnx/resolve/main/inference.onnx" \
            "${SUFFIX}_det_onnx_infer.tar" \
            "${DET_DIR}/inference.onnx"

        download "${SUFFIX}_rec_model" \
            "${SUFFIX}_rec_onnx/resolve/main/inference.onnx" \
            "${SUFFIX}_rec_onnx_infer.tar" \
            "${REC_DIR}/inference.onnx"

        download "${SUFFIX}_rec_config" \
            "${SUFFIX}_rec_onnx/resolve/main/inference.yml" \
            "" \
            "${REC_DIR}/inference.yml"
        ;;
    *)
        echo "Unknown variant: $MODEL_VARIANT" >&2
        exit 2
        ;;
esac

echo "[download] All models staged under app/src/main/assets/models/"
```

- [ ] **Step 3: 加 gitignore 条目**

修改项目根 `.gitignore`,追加:

```gitignore
# PaddleOCR SDK build artifacts (regenerable via tools/build-ppocr-sdk.sh)
tools/paddleocr/
app/libs/ppocr-sdk.aar
app/libs/ppocr-sdk-release.aar

# ONNX model weights (regenerable via tools/download-ppocr-models.sh)
app/src/main/assets/models/det/*.onnx
app/src/main/assets/models/det/*.tar
app/src/main/assets/models/rec/*.onnx
app/src/main/assets/models/rec/*.tar
app/src/main/assets/models/rec/*.yml
```

> 已有的 `app/libs/`、`app/src/main/assets/models/` 保留 `.gitkeep`。

- [ ] **Step 4: chmod + commit**

Run:
```bash
chmod +x tools/build-ppocr-sdk.sh tools/download-ppocr-models.sh
git add tools/build-ppocr-sdk.sh tools/download-ppocr-models.sh .gitignore
git commit -m "chore: add PaddleOCR SDK build + model download scripts"
```

### Task 0.2: Build `ppocr-sdk.aar`

**Files:**
- Generated: `app/libs/ppocr-sdk.aar`(gitignored)

- [ ] **Step 1: 运行 build 脚本**

Run:
```bash
./tools/build-ppocr-sdk.sh
```

Expected: AAR 出现在 `app/libs/ppocr-sdk.aar`,文件大小 5-15MB(SDK + 预编译 ONNX Runtime + OpenCV native libs)。首次 clone + build **5-15 分钟**(后续 build 因 Gradle 缓存 < 1 分钟)。

- [ ] **Step 2: 验证 AAR 内容**

Run:
```bash
unzip -l app/libs/ppocr-sdk.aar | head -30
```

Expected: 看到 `AndroidManifest.xml`、`classes.jar`、`jni/arm64-v8a/libpaddle_ocr_jni.so`(或类似 native 库名)、`res/`。若 `jni/x86_64/` 也存在则正常(PaddleOCR SDK 是 multi-ABI,AAR 体积会比仅 arm64 大)。

- [ ] **Step 3: 记录 AAR 路径到 plan 决策登记**

AAR 实际大小、文件 hash、native 库列表 → 写入本 plan 末尾决策登记。

### Task 0.3: 下载 ONNX 模型

**Files:**
- Generated: `app/src/main/assets/models/det/inference.onnx`
- Generated: `app/src/main/assets/models/rec/inference.onnx`
- Generated: `app/src/main/assets/models/rec/inference.yml`(均 gitignored)

- [ ] **Step 1: 运行下载脚本(默认 PP-OCRv5_mobile)**

Run:
```bash
./tools/download-ppocr-models.sh pp-ocrv5_mobile
```

Expected: 3 个文件出现在 `app/src/main/assets/models/`。下载时间 1-3 分钟(取决于镜像源速度)。HuggingFace 优先,失败 fallback 到 BOS 镜像。

- [ ] **Step 2: 验证模型文件**

Run:
```bash
ls -la app/src/main/assets/models/det/ app/src/main/assets/models/rec/
file app/src/main/assets/models/det/inference.onnx
```

Expected: det/inference.onnx ~5-10MB,rec/inference.onnx ~10-15MB,rec/inference.yml ~5KB。`file` 命令显示 "data" 或 "ONNX model"(二进制 magic)。

- [ ] **Step 3: 若 PP-OCRv6 想要更小体积,改用 v6_small 重下**

Run:
```bash
rm -rf app/src/main/assets/models/
./tools/download-ppocr-models.sh pp-ocrv6_small
```

Expected: v6_small 模型总体积比 v5_mobile 小约 30-40%(更轻量但准确率略低)。

> **决策**:Phase 1 默认 **PP-OCRv5_mobile**(准确率优先,体积 25MB 内可接受)。v6_small 作为后续 Phase 优化项。

### Task 0.4: 验证 SDK + 模型集成烟测

**Files:**
- Create: `app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrSmokeTest.kt`(临时,Phase 1 Task 7 后改/删)

- [ ] **Step 1: 临时集成测试**

> **注意**:此 task 在 Phase 1 Task 7(PaddleOcrEngine)完成**之前**作为可行性验证。若 SDK 已能 load 模型 + recognize 单张图,后续 Task 7 直接基于此 API 包 OcrEngine interface。

`app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrSmokeTest.kt`:

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paddle.ocr.PaddleOCR
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class PaddleOcrSmokeTest {

    @Test
    fun sdk_canLoadAndRecognize_testImage() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ocr = PaddleOCR.create(
            context = context,
            detModelAssetPath = "models/det/inference.onnx",
            recModelAssetPath = "models/rec/inference.onnx",
            recConfigAssetPath = "models/rec/inference.yml",
        )
        // 测试图:Phase 1 后期会加入,这里用项目内任意 1 张图(或放在 androidTest/assets/test.png)
        val bitmap = android.graphics.BitmapFactory.decodeStream(
            context.assets.open("test.png")
        )
        val result = ocr.recognize(bitmap)
        assertNotNull(result.results)
        assertTrue("Expected at least 1 line", result.results.isNotEmpty())
        ocr.release()
    }
}
```

> **Phase 0 烟测通过标准**:此测试跑过 → SDK 集成路径验证成功 → 进入 Phase 1 Task 7 写 OcrEngine interface + PaddleOcrEngine 实现。
> **失败处理**:若 `PaddleOCR.create()` 抛 `OCRError.ModelNotFound` → 检查 `app/src/main/assets/models/` 路径;若抛 `OCRError.ModelLoadFailed` → 检查 ONNX Runtime 依赖版本;若测试图未提供 → 后续补一张到 `app/src/androidTest/assets/test.png`。

- [ ] **Step 2: commit 烟测**

Run:
```bash
git add app/src/androidTest/
git commit -m "test: add PaddleOCR SDK smoke test (Phase 0 verification)"
```

---

## Phase 1 — Kotlin app(15 task,沿用原 plan 主体)

> Phase 0 完成(PaddleOCR SDK + 模型 ready)后,执行本 Phase。Task 1-6、8-13 与原 plan `2026-08-13-icevision-phase1-ocr-rules.md` 几乎一致;**Task 7 由 `RapidOcrEngine 桩` 改为 `PaddleOcrEngine`(官方 SDK)**。

**Reference:** `docs/superpowers/plans/2026-08-13-icevision-phase1-ocr-rules.md` Task 1-15(保留 Kotlin app 主体结构,本 plan 仅对依赖 / Task 7 做改动)。

### Task 1: 升级 baseline 到前瞻路径

**变更**(相对原 plan Task 1):
- ✅ 保留: AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 36 / minSdk 26
- ✅ 保留: NDK 28.2.13676358(`gradle/libs.versions.toml` 一致性,即使 Vision Phase 1 暂不用 NDK)
- ➕ **新增**: `implementation(files("libs/ppocr-sdk.aar"))`
- ➕ **新增**: `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")`
- ➕ **新增**: OpenCV Android 依赖(版本见 PaddleOCR v3.7.0 的 `libs.versions.toml`,大概率 `com.quickbirdstudios:opencv:4.5.3`)
- ❌ **删除**: 原 ONNX Runtime 直接依赖(已通过 PaddleOCR SDK 间接引入;再直接声明一份确保版本可见)

> **abiFilters**:PaddleOCR SDK 的 AAR 内置 `arm64-v8a` + `armeabi-v7a` + `x86_64` 多 ABI native lib。若 Vision 仍只发 arm64-v8a,保持 `abiFilters = listOf("arm64-v8a")`,AGP 会只打包 arm64 slice(其他 ABI 在 APK 中剔除)。

### Task 2: AnalysisState + domain types

**Files:** `app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt`

- 与原 plan Task 2 一致(sealed class + 5 个状态 + domain 数据类)。
- **不变**:`OcrResult` / `TextLine` / `RuleHit` / `Severity` / `ViolationReport` 数据类签名保持。

### Task 3: AdLawRule @Serializable

- 与原 plan Task 3 一致。

### Task 4: AssetRuleLoader

- 与原 plan Task 4 一致。

### Task 5: RuleMatcher + AdLawRuleMatcher + FakeRuleMatcher

- 与原 plan Task 5 一致,**import 修正**:
  - `import com.hankcs.aho_corasick.AhoCorasickDoubleArrayTrie`(主类名,不是 `AhoCorasick`)

### Task 6: OcrEngine interface + FakeOcrEngine

- 与原 plan Task 6 一致。

### Task 7: PaddleOcrEngine(替换原 RapidOcrEngine 桩)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt`
- Delete(若存在): `app/src/main/java/com/icespiritai/offline/ocr/RapidOcrEngine.kt`

- [ ] **Step 1: 写 PaddleOcrEngine**

```kotlin
package com.icespiritai.offline.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.icespiritai.offline.domain.OcrResult
import com.icespiritai.offline.domain.TextLine
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PaddleOcrEngine(context: Context) : OcrEngine {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var paddleOcr: PaddleOCR? = null

    override suspend fun recognize(uri: Uri): OcrResult = mutex.withLock {
        val ocr = paddleOcr ?: PaddleOCR.create(
            context = appContext,
            detModelAssetPath = "models/det/inference.onnx",
            recModelAssetPath = "models/rec/inference.onnx",
            recConfigAssetPath = "models/rec/inference.yml",
        ).also { paddleOcr = it }

        val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw OCRError.InvalidImage()

        val result = try {
            ocr.recognize(bitmap)
        } catch (e: OCRError) {
            throw when (e) {
                is OCRError.ModelLoadFailed -> OcrEngineUnavailable("OCR 模型加载失败:${e.message}", e)
                is OCRError.ModelNotFound -> OcrEngineUnavailable("OCR 模型未打包:${e.message}", e)
                is OCRError.ConfigParseFailed -> OcrEngineUnavailable("OCR 配置解析失败:${e.message}", e)
                is OCRError.InferenceFailed -> OcrFailed(e.message ?: "OCR 推理失败", e)
                is OCRError.InvalidImage -> OcrFailed("图片无法识别", e)
                is OCRError.DecodeError -> OcrFailed("OCR 解码失败:${e.message}", e)
            }
        }

        OcrResult(
            fullText = result.results.joinToString("\n") { it.text },
            lineBoxes = result.results.map { TextLine(it.text, it.box.toRect(), it.score) },
            avgConfidence = if (result.results.isEmpty()) 0f
                           else result.results.map { it.score }.average().toFloat()
        )
    }

    override suspend fun release() = withContext(Dispatchers.IO) {
        paddleOcr?.release()
        paddleOcr = null
    }
}
```

> **依赖 PaddleOCR SDK 模型类**(`OCRBox`、`OCRResult`、`OCRError`)— 实际字段名以 PaddleOCR v3.7.0 源码为准。Task 7 implementer 需先读 `com/paddle/ocr/model/OCRResult.kt` 确认 `text / score / box` 字段名,如有差异按实际调整。本 plan 假设命名一致(基于 PaddleOCR 仓库 README 用法推得)。

- [ ] **Step 2: 单测**

`app/src/test/java/com/icespiritai/offline/ocr/PaddleOcrEngineTest.kt`(androidTest,因需 Context):

```kotlin
@RunWith(AndroidJUnit4::class)
class PaddleOcrEngineTest {
    @get:Rule val rule = kotlinx.coroutines.test.MainDispatcherRule()

    @Test
    fun recognize_realImage_returnsText() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val engine = PaddleOcrEngine(ctx)
        val uri = Uri.parse("file:///android_asset/test.png")
        val result = engine.recognize(uri)
        assertTrue(result.fullText.isNotEmpty())
        engine.release()
    }
}
```

- [ ] **Step 3: 验证**

Run: `./gradlew.bat :app:connectedDebugAndroidTest`
Expected: 1 test passes。

- [ ] **Step 4: commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt \
        app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrEngineTest.kt
git rm app/src/main/java/com/icespiritai/offline/ocr/RapidOcrEngine.kt 2>/dev/null || true
git commit -m "feat(ocr): add PaddleOcrEngine wrapping official PaddleOCR SDK"
```

### Task 8: ImageAnalyzerRepository

- 与原 plan Task 8 一致(用 `FakeOcrEngine` 或 `PaddleOcrEngine` 实例化,Repository 通过构造注入)。

### Task 9: IceSpiritVisionViewModel

- 与原 plan Task 9 一致。

### Task 10: AndroidManifest 权限

- 与原 plan Task 10 一致。

### Task 11: Compose UI + Activity 重写

- 与原 plan Task 11 一致,根据 `AnalysisState` 渲染分支。

### Task 12: 规则 assets

- 与原 plan Task 12 一致(`app/src/main/assets/rules/ad_law_rules.json`)。

### Task 13: prepareOcrRulesAssets Gradle 任务

- 与原 plan Task 13 一致:
  - `shell` profile → 不打包 ONNX(模型文件被 `prepareOcrModelsAssets` task 跳过);仅 `rules/placeholder.json`
  - `ice_ocr_rules` profile → 打包 `assets/models/{det,rec}/inference.onnx` + `rules/ad_law_rules.json`(模型文件 gitignored,本地脚本下载后存在)
  - `ice_vision` profile → 不打包

> **关键**:`.gitignore` 已排除 `assets/models/det/*.onnx` 等。`prepareOcrRulesAssets` 任务需检测模型文件是否存在,缺失则 **不 fail**(用户未跑 `download-ppocr-models.sh` 时),仅 warn。

### Task 14: Compose UI test

- 与原 plan Task 14 一致(用 FakeOcrEngine + FakeRuleMatcher 测整条 State 链)。

### Task 15: 手动 smoke checklist

- 与原 plan Task 15 一致。

---

## 启动期实测清单

| # | 项 | 结果 |
|---|---|---|
| 1 | Gradle 9.7.0 Tencent 镜像 | ✅ 200(`gradle-9.7.0-bin.zip`) |
| 2 | AGP 9.3.0 Aliyun | ✅ 200 |
| 3 | Kotlin 2.4.10 Aliyun | ✅ 200 |
| 4 | kotlinx-coroutines 1.10.2 | ✅ |
| 5 | kotlinx-serialization 1.9.0 | ✅ |
| 6 | Compose BOM 2026.08.00 | ✅ Aliyun 200 |
| 7 | Compose Compiler 2.4.10 | ✅ Aliyun 200 |
| 8 | HankCS 官方包 `com.hankcs:aho-corasick-double-array-trie:1.2.3` | ✅ Maven Central |
| 9 | ~~RapidOCR Android JitPack~~ | ❌ 弃用,不入清单 |
| 10 | ~~ONNX Runtime 直接依赖~~ | ❌ 改走 PaddleOCR SDK 间接 |
| 11 | **ONNX Runtime 1.21.1**(由 PaddleOCR SDK 引入) | ✅ Aliyun / Maven Central |
| 12 | **OpenCV Android 4.5.3**(`com.quickbirdstudios:opencv` 或 PaddleOCR 自带) | ✅ Aliyun |
| 13 | **PaddleOCR v3.7.0 release tag** | ✅ 2026-06-11 |
| 14 | **PP-OCRv5_mobile ONNX**(HuggingFace + BOS 双源) | ✅ 可下载 |
| 15 | **PP-OCRv6_small / tiny ONNX** | ✅ 可下载(可选) |

---

## 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-13 | Phase 1 走 OCR + 规则库,取代 init spec v0.3.0 视觉二分类 | 用户用例 = "广告用语违法" |
| 2026-08-13 | Baseline 走前瞻路径(AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk 36 / NDK 28.2) | `docs/knowledge/build-stack-2026-08.md` §12 |
| 2026-08-13 | UI 框架 = Jetpack Compose 1.12.x | 与 Kotlin 2.4 同代 |
| 2026-08-14 | **OCR 引擎锁定 PaddleOCR 官方 SDK**(走 ONNX Runtime + OpenCV) | 调研:用户明确要求 + PaddleOCR v3.7.0 官方已迁到 ONNX Runtime + Paddle-Lite 已 4 年未更新 + PaddleOCR-slim 是量化工具非引擎 + 官方 AAR 可直接 build |
| 2026-08-14 | **模型默认 PP-OCRv5_mobile** | 准确率优先,体积可接受(总 ~25MB) |
| 2026-08-14 | **AAR 通过本地 build + 文件引用**,不走 Maven 仓库发布 | PaddleOCR 官方无 maven 坐标,本地 build 是官方推荐方式 |
| 2026-08-14 | **AC 自动机用 `com.hankcs:aho-corasick-double-array-trie:1.2.3`** | hankcs 官方包,Maven Central |
| 2026-08-14 | 取消上一 plan 误估的 NDK build 1-2 周工作量 | PaddleOCR-slim 路径理解错误,实际是预量化 ONNX + ONNX Runtime,3-5 天可完成 |
| 待(Task 0.2) | `ppocr-sdk.aar` 实际大小 + native 库清单 | Task 0.2 Step 3 |
| 待(Task 0.3) | 模型下载源实际可用性(HF vs BOS 镜像在中国大陆可达性) | Task 0.3 Step 1-2 |
| 待(Task 1) | OpenCV Android 坐标 + 版本号(以 PaddleOCR v3.7.0 `libs.versions.toml` 为准) | Task 1 implementer |
| 待(Task 7) | PaddleOCR SDK 内 `OCRResult` / `OCRBox` / `OCRError` 实际字段名 | Task 7 implementer 读源码 |

---

## 已知缺口

1. **PaddleOCR 官方 Android SDK 不发 Maven 仓库** — 必须在本地 build AAR。`tools/build-ppocr-sdk.sh` 是首次必须跑的(对应 Task 0.2),且 git submodule 不能复用(官方无 submodule 路径)。
2. **ONNX 模型不在 git** — 同样本地脚本下载(对应 Task 0.3)。新克隆仓库的开发者需先后跑 `build-ppocr-sdk.sh` + `download-ppocr-models.sh` 才能 `assembleDebug`。
3. **OpenCV Android 版本与 PaddleOCR SDK 锁定的版本** — 若 PaddleOCR 仓库的 `libs.versions.toml` 用 `org.opencv:opencv:4.5.3` 或 `com.quickbirdstudios:opencv:4.5.3`,Task 1 implementer 需读 `deploy/ppocr-android/gradle/libs.versions.toml` 对齐。
4. **Task 7 PaddleOcrEngine 字段名假设** — `it.text / it.score / it.box` 基于 PaddleOCR README 用法推测,实际字段以 v3.7.0 源码为准。Task 7 implementer 需先读 `ppocr-sdk/src/main/java/com/paddle/ocr/model/OCRResult.kt` + `OCRBox.kt`。
5. **中文分词 / 规则库召回率** — 与 PaddleOCR 模型无关,本 plan 沿用原 plan 的 AC 自动机直接匹配策略。

---

## 自审(Spec 覆盖)

| Spec 章节 | 覆盖 Task |
|---|---|
| §1 背景与目标 | Phase 0 + Phase 1 |
| §2 baseline | Task 1 |
| §2.2 新增依赖 | Task 1(ONNX Runtime + OpenCV + PaddleOCR SDK)+ Task 5(HankCS)|
| §2.5 modelProfile | Task 13(`prepareOcrRulesAssets` 逻辑) |
| §3.1 顶层架构 | Task 8(Repository)+ Task 7(PaddleOcrEngine)|
| §3.2 组件边界 | Task 6 / 7 / 8 / 9 |
| §3.3 数据流 | Task 9(ViewModel 驱动 AnalysisState)|
| §3.4 核心数据类型 | Task 2 |
| §3.5 UI 框架 | Task 11(Compose + StateFlow)|
| §3.6 权限 | Task 10 |
| §4 错误处理矩阵 | Task 7(OCRError → OcrEngineUnavailable/OcrFailed)+ Task 9(Error state)|
| §4.4 测试策略 | Task 14(Compose UI)+ Task 7(androidTest)|
| §4.5 性能基线 | Task 15(smoke + 性能记录)|

**变更说明(相对 spec):**
- spec §2.2 `RapidOCR Android artifact` → `com.paddle.ocr:ppocr-sdk`(本地 AAR)
- spec §3.2 `RapidOcrEngine` → `PaddleOcrEngine`(包 PaddleOCR 官方 SDK)
- spec §2.5 `ice_ocr_rules` profile 含义不变(模型 + 规则)
- spec 其他章节不动