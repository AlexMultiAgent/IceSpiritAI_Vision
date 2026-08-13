# 冰灵锐目 Phase 1 设计规范 — OCR + 规则库文字审核

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-13 |
| Phase | 1 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline 库 | `docs/knowledge/build-stack-2026-08.md`、`docs/knowledge/cross-project-implications.md` |
| 关联 init spec | `docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`(本规范**部分取代**其 §6 Phase 1 视觉二分类路线) |

---

## 1. 背景与目标

### 1.1 项目定位

冰灵锐目(IceSpiritAI_Vision)是冰灵家族的**离线端侧内容审核 Android 应用**,与冰灵慧语(LLM 对话)、冰灵智译(离线翻译)同代。

### 1.2 总体产品范围

理论目标覆盖三类违规:文字类违规(《广告法》《商标法》等)、视觉类违规(裸露、暴恐、违禁品)、混合违规(文字 + 图像)。**Phase 1 仅交付文字类违规的离线端侧判定**。

### 1.3 Phase 1 目标

用户拍摄或上传一张带文字的海报 / 截图 / 广告图片 → 在设备本地完成:
1. OCR 抽取图片内文字
2. 与内置规则库进行匹配(AC 自动机)
3. 输出违规清单 + 法规条款引用

**全程离线**(无网络 IO,模型与规则均在 APK 内)。

### 1.4 与 init spec 的关系

init spec `2026-08-13-icespirit-vision-init-design.md` 的 §7 后续路线中"v0.3.0 = 视觉二分类(Y/N)"路线与本规范方向不同。本规范**取代**该路线,改走"OCR + 规则库"。init spec 的其他章节(项目骨架、命名空间、minSdk、ABI、目录布局、交付物)继续有效。

> 注:init spec §4 工程基线(AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.10.2 / compileSdk 35)也被本规范 §2 取代,改为前瞻路径(AGP 9.3 / Kotlin 2.4.10 / Gradle 9.7 / compileSdk 36)。init spec 后续若需更新基线段落,可引用本规范 §2。

### 1.5 非目标(本期不做)

- 视觉违规判定(裸体 / 暴恐 / 违禁品)
- 端侧 VLM(MiniCPM-V / Qwen2.5-VL 等)
- 多语种支持(本期仅中文)
- 规则库热更新(本期仅 APK 内置)
- 服务端协同、用户数据上传

---

## 2. 技术 baseline(Section A,锁定)

### 2.1 工具链(前瞻路径)

| 项 | 值 |
|---|---|
| AGP | **9.3.x** |
| Gradle Wrapper | **9.7.x** |
| Kotlin | **2.4.10** |
| JDK | **17** |
| NDK | **28.2.13676358**(r28c) |
| compileSdk / targetSdk | **36 / 36** |
| minSdk | **26** |
| ABI | arm64-v8a only |
| Maven 镜像 | Aliyun 主 → Tencent → Huawei → `mavenCentral()` → `google()` |

**依据:** 见 `docs/knowledge/build-stack-2026-08.md`。Gradle 9.6+ 与 AGP 8.x 不兼容(硬约束);Kotlin 2.4 移除 K1 编译器;AGP 9 内置 Kotlin。

### 2.2 Phase 1 新增依赖

| 依赖 | 版本 | 用途 |
|---|---|---|
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | `lifecycleScope` / `repeatOnLifecycle` |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.11.0 | `viewModelScope` |
| `androidx.activity:activity-ktx` | 1.13.0 | `PickVisualMedia` / `TakePicture` |
| `kotlinx-coroutines-android` | 1.10.x *(启动期锁)* | 协程 |
| `kotlinx-serialization-json` | 1.9.x *(启动期锁)* | 规则 JSON 解析 |
| RapidOCR Android artifact | latest *(启动期核 artifact 名)* | OCR 引擎 |
| `com.hankcs:aho-corasick` | 0.1.4 | AC 自动机 |

> 未精确锁定的 patch 版本在 plan 启动期跑 `gradle dependencies` 校验。

### 2.3 Gradle 构建关键开关

- AGP 9 默认 `kotlin-android` 插件**不再手动 apply**
- `testOptions { unitTests.isReturnDefaultValues = true }`(对齐 Translate)
- `viewBinding` 不开(用 Compose)
- `buildConfig = true`(为 `BuildConfig.MODEL_PROFILE` 留口)
- `abiFilters = listOf("arm64-v8a")`

### 2.4 gradle.properties(沿用 init spec,不变)

| 项 | 值 | 备注 |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx3072m -Dfile.encoding=UTF-8` | WIN runner 8 GiB 实测配置 |
| `org.gradle.workers.max` | `1` | 镜像 Translate 轻量配置 |
| `android.nonTransitiveRClass` | `true` | AGP 9 默认同向 |
| `android.nonFinalResIds` | `true` | AGP 9 默认同向 |

### 2.5 modelProfile 系统重定义

| Profile | 含义 | Phase 1 启用 | APK 内含 |
|---|---|---|---|
| `shell`(默认) | UI 骨架,无模型 | ✅ | placeholder |
| `ice_ocr_rules` | OCR + 规则库 = **Phase 1 主形态** | ✅ | ONNX 模型 + `ad_law_rules.json` |
| `ice_vision` | 端侧 VLM(后续 Phase) | ❌ | — |
| ~~`ice_vision_minimal`~~ | 原"视觉二分类"语义不准确 | **弃用** | — |

**Gradle 任务** `prepareOcrRulesAssets`:
- `shell` → 不打包 ONNX 模型 + 不打包完整规则;仅 `rules/placeholder.json`
- `ice_ocr_rules` → 打包 ONNX(~10MB)+ `ad_law_rules.json`
- `ice_vision` → 不打包 OCR 规则

---

## 3. 架构与数据流(Section B,锁定)

### 3.1 顶层架构

```
IceSpiritVisionActivity (Compose UI 渲染 + 系统交互)
    ↓ [imageUri]
IceSpiritVisionViewModel (持有 StateFlow<AnalysisState>)
    ↓ [imageUri]
ImageAnalyzerRepository
    ├── OcrEngine (RapidOcrEngine | FakeOcrEngine)
    ├── AssetRuleLoader (assets/rules/ → List<AdLawRule>)
    └── AdLawRuleMatcher (内部 AC 自动机)
```

### 3.2 组件边界

| 组件 | 职责 | 依赖 |
|---|---|---|
| `IceSpiritVisionActivity` | Compose 渲染 + 拍照/选图/权限 + State 收集 | ViewModel + Activity Result API |
| `IceSpiritVisionViewModel` | 持有 `StateFlow<AnalysisState>`;`startAnalysis(imageUri)` | Repository |
| `ImageAnalyzerRepository` | 编排 OCR + 规则匹配;返回 `ViolationReport` | OcrEngine + RuleMatcher |
| `OcrEngine`(interface) | `suspend fun recognize(uri: Uri): OcrResult` | — |
| `RapidOcrEngine`(生产) | 调用 RapidOCR Android | RapidOCR + ONNX Runtime |
| `FakeOcrEngine`(测试 + shell profile) | 返回固定文本 | — |
| `AssetRuleLoader` | 从 APK 内 `assets/rules/<file>.json` 反序列化 | kotlinx-serialization + Context |
| `AdLawRuleMatcher` | `scan(text): List<RuleHit>` | 内部 HankCS AC 自动机 |
| `AdLawRule`(`@Serializable`) | `id, category, regulation, keywords, severity` | kotlinx-serialization |

### 3.3 数据流

```
User 点 "选图" / "拍照"
    → ActivityResultContracts.PickVisualMedia / TakePicture → imageUri
    → ViewModel.startAnalysis(imageUri)
        → AnalysisState.Loading(Stage.OcrRunning)
        → OcrEngine.recognize(uri) → OcrResult(text, confidence, lineBoxes)
        → AnalysisState.OcrDone(text, confidence, lineBoxes)        [emit 进度]
        → AnalysisState.Loading(Stage.RuleScanning)
        → AdLawRuleMatcher.scan(text) → List<RuleHit>
        → AnalysisState.RuleScanned(hits)                            [emit 进度]
        → 构造 ViolationReport(imageUri, ocrText, hits, timestampMs)
        → AnalysisState.Complete(report)
    (异常路径) → AnalysisState.Error(message, cause)
```

### 3.4 核心数据类型

```kotlin
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
    data class Complete(val report: ViolationReport) : AnalysisState()
    data class Error(
        val message: String,
        val retryable: Boolean = false,
        val cause: Throwable? = null
    ) : AnalysisState()
}

data class OcrResult(
    val fullText: String,
    val lineBoxes: List<TextLine>,
    val avgConfidence: Float
)
data class TextLine(val text: String, val box: Rect, val confidence: Float)
data class RuleHit(
    val ruleId: String,
    val matchedText: String,
    val category: String,
    val regulation: String,
    val severity: Severity
)
enum class Severity { Info, Warning, Violation }
data class ViolationReport(
    val imageUri: Uri,
    val ocrText: String,
    val hits: List<RuleHit>,
    val timestampMs: Long
)
```

### 3.5 UI 框架(锁定)

- **Jetpack Compose 1.12.x** + `LazyColumn` + Coil 3.x
- Compose Compiler Plugin 与 Kotlin 2.4.10 配套
- `StateFlow<AnalysisState>` → `collectAsStateWithLifecycle()` → 单一 Composable 渲染分支

### 3.6 权限

- `CAMERA`(拍照;Android 13+ 走 `TakePicture` 实际不需 CAMERA,但声明以备传统路径)
- `READ_MEDIA_IMAGES`(Android 13+)
- `READ_EXTERNAL_STORAGE` `maxSdkVersion=32`(旧版本回退)
- 拍照走 `ActivityResultContracts.TakePicture`(无须 CAMERA 即可拍照);选图走 `PickVisualMedia`(Android 13+ 系统级 Photo Picker,无须 READ_MEDIA_IMAGES 也可工作 — 但声明以保证 fallback)

---

## 4. 错误处理与测试(Section C,锁定)

### 4.1 错误处理矩阵

| 错误源 | 处理方式 | UI 表现 |
|---|---|---|
| 权限拒绝(CAMERA / READ_MEDIA_IMAGES) | Activity 检测 → `shouldShowRequestPermissionRationale` 引导 | 友好提示 + 跳转系统设置按钮 |
| 图片选择取消 | 无操作 | UI 回到 Idle |
| OCR 模型加载失败 | Repository 抛 `OcrEngineUnavailable` | Error("OCR 模型加载失败,请检查 APK 是否完整") + 退出按钮 |
| OCR 识别异常 / 超时 | Repository 抛 `OcrFailed(cause)` | Error("图片识别失败,请换一张清晰图重试") + 重试按钮 |
| OCR 置信度过低(<0.5) | 不阻断,`OcrDone.lowConfidence = true` | 提示"识别置信度低,结果仅供参考" |
| 规则 JSON 解析失败 | Repository 抛 `RuleLoadFailed` | Error("规则库加载失败") + 退出按钮 |
| 无命中规则 | Complete(report, hits=[]) | "未发现违规用语" + 仍展示 OCR 全文 |
| 协程取消 | `CancellationException` 透传不吞 | 无 UI |

### 4.2 取消策略

- ViewModel `onCleared()` 取消所有进行中的 Job
- Activity `repeatOnLifecycle(STARTED)` 停止 collect,但 `viewModelScope` 不受影响,后台分析继续到 Complete 才 emit
- 用户中途换图 → `startAnalysis` 时 `previousJob.cancel()`

### 4.3 日志

- 所有错误 `Log.e(TAG, "...", throwable)`,TAG = 组件名
- 文案本地化(`strings.xml`),不暴露技术细节
- 图片内容不上传(全内存处理,无网络 IO)

### 4.4 测试策略

| 层级 | 内容 | 工具 |
|---|---|---|
| 单元 | `AdLawRuleMatcherTest`:关键词 / 短语 / 大小写 / 多规则冲突 | JUnit 5 + 真 AC 自动机 |
| 单元 | `AdLawRuleTest` / `AssetRuleLoaderTest`:JSON 字段映射 | JUnit 5 + kotlinx-serialization |
| 单元 | `ImageAnalyzerRepositoryTest`:`FakeOcrEngine` + 真 `AdLawRuleMatcher`,验证整条分析链 | JUnit 5 + kotlinx-coroutines-test |
| 集成(设备) | RapidOCR 真实 ONNX + 真实规则,验证模型加载 | `connectedAndroidTest`(本地跑) |
| UI | Compose UI Test:模拟选图 → 点击 → State emit 顺序断言 | `androidx.compose.ui:ui-test-junit4` |
| Smoke | `assembleDebug -PmodelProfile=ice_ocr_rules` → 装机 → 拍照 → 看到结果 | 手动 |
| Smoke | `assembleDebug -PmodelProfile=shell` → 装机 → UI 可启动 + "分析"置灰 | 手动 |

> 单测上 CI;`connectedAndroidTest` 因设备碎片化**不上 CI**,开发期本地跑。

### 4.5 性能基线

| 项 | 目标 |
|---|---|
| APK 体积(ice_ocr_rules) | < 25MB(含 ONNX 模型 ~10MB) |
| 启动到可交互 | < 1.5s(冷启动) |
| 单次分析延迟 | < 3s(1080p 图片) |
| 内存峰值 | < 200MB |

### 4.6 测试语料

- 公开数据集为主(CC0 / 已标注的违规广告海报样本)
- 自建 golden case:广告法核心 6 类,每类 10–20 条短语
- 实拍 / 截图各 5–10 张作冒烟

---

## 5. 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-13 | Phase 1 走 OCR + 规则库,**取代** init spec v0.3.0 视觉二分类 | 用户用例 = "广告用语违法",违规点全在文字,见 [[project-scope-and-phase-1-direction]] |
| 2026-08-13 | Baseline 走前瞻路径(AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10) | `docs/knowledge/build-stack-2026-08.md` §12 |
| 2026-08-13 | UI 框架 = Jetpack Compose 1.12.x | 与 Kotlin 2.4 同代,State 驱动与 StateFlow 契合 |
| 2026-08-13 | `ice_vision_minimal` profile **弃用**,新增 `ice_ocr_rules` 为 Phase 1 主形态 | init spec v0.3.0 语义不再适用 |
| 2026-08-13 | 三项目 baseline **不同步**;智译另起 spec 迁 AGP 9 | `docs/knowledge/cross-project-implications.md` §3 |
| 待(plan 阶段) | ONNX Runtime 1.29.0 在 arm64-v8a / minSdk 26 / targetSdk 36 实际可用 | 启动期烟测 |
| 待(plan 阶段) | RapidOCR Android artifact 的 ABI 切片支持 | 启动期烟测 |
| 待(plan 阶段) | 中文分词策略(直接 AC 匹配 / 叠加 jieba / HanLP portable) | golden case 召回率决定 |
| 待(plan 阶段) | 规则 JSON 的字段细化(关键词 / 同义词 / 上下文例外词) | golden case 误召率决定 |

---

## 6. 待办(plan 阶段)

下列项**不**在本 spec 范围,转入 writing-plans skill 阶段单独决策:

1. 规则 JSON 的 schema 细化(版本号 / 同义词 / 上下文例外词 / 命中权重)
2. 中文分词策略(直接 AC / jieba / HanLP portable)的实证选型
3. APK 体积优化(模型量化?ONNX → TFLite?模型按需下载?)
4. UI 细节(选图 → 预览 → 二次确认的分析闸)
5. 多语种扩展(英语、日语规则)的预留位
6. 智译 build 模板的抽取(从 Translate 抽 `signing-config` / `version-from-git` / `model-profile-assets`)
7. 启动期实测清单(ONNX Runtime 兼容性、RapidOCR ABI)

---

## 7. 引用

- `CLAUDE.md`(项目根,三项目统一字段)
- `docs/knowledge/README.md`
- `docs/knowledge/build-stack-2026-08.md`
- `docs/knowledge/cross-project-implications.md`
- `docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`(本规范部分取代其 §6.3)
- `app/build.gradle.kts`(当前骨架)
- `gradle/libs.versions.toml`(当前依赖基线)

---

**Spec 状态: 待评审(自审 → 用户审 → 进入 writing-plans)**

变更追踪:
- v0.1.0(2026-08-13):初版,基于 brainstorming 三段对话(Section A/B/C)整合成完整规范。