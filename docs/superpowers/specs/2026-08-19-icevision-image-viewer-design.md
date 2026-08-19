# 冰灵锐目 — 原图查看 / OCR 文本滚动 设计规范

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-19 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline 库 | `docs/knowledge/build-stack-2026-08.md` |
| 关联 UI spec | `docs/superpowers/specs/2026-08-15-icevision-ui-design.md` |
| 关联 Phase 1 spec | `docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md` |
| 关联 Phase 2 spec | `docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md` |

本文档**叠加**在 Phase 1 / Phase 2 / 既有 UI spec 之上,**仅**涉及图片查看能力(缩放/拖动/双击切换)+ OCR 文本按行可滚动。后端逻辑(OcrEngine / RuleMatcher / Repository)与构建系统(AGP / Kotlin / Gradle / NDK)完全保持现状,不动。

---

## 1. 背景与目标

### 1.1 现状

[HomeScreen.kt](../../app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt) 是单页直入式 UI:上 `ImagePreview`(Coil `AsyncImage` + Canvas `HighlightOverlay`,**无缩放/无拖动**),下 `ResultPanel`(`LazyColumn`,头部 `Text(ocrText)` 一段 + `HitCard` 列表)。

关键事实:
- 全代码库**没有任何** `Modifier.pointerInput` / `detectTransformGestures` / `transformable`
- **没有任何**第三方缩放库依赖
- 图片来源(`android.net.Uri`)由 `HomeScreen` 的本地 `pendingUri` 持有
- OCR 数据来自 `AnalysisState.Complete.report: ViolationReport`,字段含 `ocrText: String` + `hits: List<RuleHit>` + `imageUri: Uri`,**不**含 `lineBoxes`
- `OcrDone.lineBoxes: List<TextLine>` 是 transient,ViewModel 进入 `RuleScanning` / `Complete` 后被丢弃

### 1.2 目标

现场执法人员需要"看清图 + 看清字"的体验:

1. 双击 `HomeScreen` 图片区 → 进入**全屏 Viewer 路由**
2. Viewer 内大图支持**双指缩放 / 单指拖动 / 双击切换缩放**(`1.0 ↔ 2.5×`)
3. Viewer 底部 OCR 文本以**逐行独立项**呈现,`LazyColumn` 滑到底能看到全部
4. 命中关键词的行用错误容器色高亮(同 `HitCard` 视觉语言)

### 1.3 非目标(本期)

- EXIF 旋转修正(`BitmapLoader.exifRotationDegrees` 已存在但 Viewer 不应用,留独立工单)
- OCR 框在 2048 下采样 vs 全分辨率显示的坐标错位(pre-existing bug,不在本 PR 修复)
- 12 MP+ 大图 OOM 防护(超出范围)
- 点 `TextLineRow` → 高亮 ViewerImage 中对应框(留未来扩展;本 PR 仅做镜像渲染)
- 历史记录 / 多图浏览 / 画中画 / 沉浸式 status bar 隐藏

---

## 2. 总体方案

**全屏新 Viewer 路由 + `ViolationReport` 自包含 `lineBoxes` + Telephoto 库处理缩放/拖动手势**。

3 个候选方案对比与排除理由:

| 方案 | 摘要 | 排除理由 |
|---|---|---|
| **A — Viewer 路由 + ViolationReport.lineBoxes(选定)** | 推入 `Routes.VIEWER`,Viewer 读 `viewModel.state`,`ViolationReport` 追加 `lineBoxes` 字段 | — |
| B — Viewer 路由 + ViewModel 单独 `lastLineBoxes: StateFlow` | 不动 domain | reset / 切 tab 时需手动清空;状态可能 stale;测试 stub 麻烦 |
| C — HomeScreen 内模式切换(`AnimatedContent` + `BottomSheetScaffold`) | 零 NavGraph 改动 | 图像查看体验受限;`BottomSheet` 抢空间;与"打开原图"语义偏离 |

---

## 3. 架构

### 3.1 路由与导航

- 新增 `Routes.VIEWER = "viewer"`,**无 nav args**(符合现有 `Routes.HOME` 不接参数的模式;参数从 ViewModel state 读)
- `IceSpiritNavHost` 注册 `composable(Routes.VIEWER) { ViewerScreen(onBack = { nav.popBackStack() }) }`
- `HomeScreen` 通过 `navController.navigate(Routes.VIEWER)` 进入 Viewer;返回箭头 + 系统返回键都回 `Routes.HOME`
- 现有 `Routes.HOME / SETTINGS / CHANGELOG` 不动

### 3.2 数据流

```
拍照/选图 → HomeScreen.pendingUri = Uri
                ↓ startAnalysis(uri)
AnalysisState.Loading(OcrRunning)
                ↓ OcrEngine.recognize(uri)
AnalysisState.OcrDone(text, confidence, lineBoxes)
                │  ViewModel 私有 var _lastLineBoxes = lineBoxes
                ↓ RuleMatcher.scan(text)
AnalysisState.Loading(RuleScanning)
                ↓ 规则命中
AnalysisState.RuleScanned(hits)
                ↓ 装配 report
AnalysisState.Complete(report = ViolationReport(
    imageUri, ocrText, hits, timestampMs,
    avgConfidence,
    lineBoxes = _lastLineBoxes,  ← 新增,默认 emptyList()
))
                │
                ↓ User 双击 ImagePreview → navigate(VIEWER)
ViewerScreen 读 viewModel.state: Complete → 渲染 Viewer
```

`ViolationReport` 改造最小化:
```kotlin
data class ViolationReport(
    val imageUri: Uri,
    val ocrText: String,
    val hits: List<RuleHit>,
    val timestampMs: Long,
    val avgConfidence: Float = 0f,
    val lineBoxes: List<TextLine> = emptyList(),  // ← 新增
)
```
默认 `emptyList()` 保证所有现存构造调用点(test fixture / Preview / HomeScreen)零改动。

### 3.3 组件树

```
ViewerScreen(viewModel, onBack)
└── Column(fillMaxSize, background = colorScheme.background)
    ├── ViewerTopBar(title="原图查看", onBack)            ← TopAppBar
    ├── ViewerImage(imageUri, lineBoxes, hits)           weight 0.55f
    │   └── Box(onSizeChanged → boxSize, contentAlignment = Center)
    │       ├── ZoomableImage(painter, state)            ← Telephoto
    │       ├── HighlightOverlay(lines, hits,
    │       │     scaleX/Y = fit ⊗ zoom,
    │       │     offsetX/Y = fit + zoom)
    │       └── Hint Chip(底部居中,labelSmall)
    ├── HorizontalDivider(outlineVariant)
    └── ViewerTextList(lineBoxes, hits)                   weight 0.45f,独立滚动
        └── LazyColumn
            ├── item { ViewerTextHeader(count, hits) }
            ├── itemsIndexed(lineBoxes) { TextLineRow(...) }
            └── item { Spacer(16.dp) }
```

---

## 4. 依赖与库集成

### 4.1 新增坐标

`gradle/libs.versions.toml`:
```toml
[versions]
telephoto = "0.13.0"  # ← 待 Maven Central 在实现时确认最新 Compose-1.7+ 兼容版

[libraries]
telephoto-zoomable-image-coil = { module = "me.saket.telephoto:zoomable-image-coil", version.ref = "telephoto" }
```

`app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.telephoto.zoomable.image.coil)
    // 既有依赖保持不变
}
```

`shell` 与 `ice_ocr_rules` 两个 `modelProfile` 同步生效(Telephoto 是纯 Compose,与 OCR/规则无关)。

### 4.2 ZoomableImage API

```kotlin
val state = rememberZoomableImageState()

ZoomableImage(
    painter = rememberAsyncImagePainter(model = imageUri),
    contentDescription = stringResource(R.string.viewer_image_cd),
    state = state,
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.Fit,
)
```

Telephoto 内置支持双指缩放 / 单指拖动 / 双击 1.0 ↔ 2.5× / fling / 边界钳制。不需要写 `pointerInput`。

### 4.3 缩放 transform 与 HighlightOverlay 联动

`HighlightOverlay` 已接收 `scaleX, scaleY, offsetX, offsetY`,代表 fit transform。Viewer 中叠加 Telephoto 的缩放 transform:

```kotlin
val fit = computeFitTransform(painter.intrinsicSize, boxSize)  // 既有函数
val zoom = state.transform                                     // Telephoto 提供
HighlightOverlay(
    lines, hits,
    scaleX = fit.scaleX * zoom.scaleX,
    scaleY = fit.scaleY * zoom.scaleY,
    offsetX = fit.offsetX + zoom.offset.x,
    offsetY = fit.offsetY + zoom.offset.y,
)
```
实现时需对照 Telephoto 0.13.x 的 `ZoomableImageState` API,确认 `transform` 字段具体形态(`ZoomableTransform` / `ScaleFactor + Offset`);以最终 dependency 文档为准。

---

## 5. 组件规范

### 5.1 `ViewerScreen.kt` (新建)

文件路径:`app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt`

```kotlin
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    viewModel: IceSpiritVisionViewModel = viewModel(),
) {
    val current by viewModel.state.collectAsState()
    val report = (current as? AnalysisState.Complete)?.report
    if (report == null) {
        ViewerEmpty(onBack = onBack, reason = current)
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ViewerTopBar(title = stringResource(R.string.viewer_title), onBack = onBack)
        ViewerImage(
            imageUri = report.imageUri,
            lineBoxes = report.lineBoxes,
            hits = report.hits,
            modifier = Modifier.weight(0.55f).fillMaxWidth(),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ViewerTextList(
            lineBoxes = report.lineBoxes,
            hits = report.hits,
            modifier = Modifier.weight(0.45f).fillMaxWidth(),
        )
    }
}
```

### 5.2 `ViewerTopBar.kt` (新建)

```kotlin
@Composable
fun ViewerTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    )
}
```

### 5.3 `ViewerImage.kt` (新建)

```kotlin
@Composable
fun ViewerImage(
    imageUri: Uri,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
) {
    val state = rememberZoomableImageState()
    val painter = rememberAsyncImagePainter(model = imageUri)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val fit = remember(painter.intrinsicSize, boxSize) {
        computeFitTransform(painter.intrinsicSize, boxSize)
    }
    val zoom = state.transform                            // ← Telephoto API,实现时核对
    Box(modifier = modifier.onSizeChanged { boxSize = it }, contentAlignment = Alignment.Center) {
        ZoomableImage(painter = painter, state = state, contentDescription = "原图",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (lineBoxes.isNotEmpty()) {
            HighlightOverlay(lines = lineBoxes, hits = hits,
                scaleX = fit.scaleX * zoom.scaleX, scaleY = fit.scaleY * zoom.scaleY,
                offsetX = fit.offsetX + zoom.offset.x, offsetY = fit.offsetY + zoom.offset.y,
                modifier = Modifier.fillMaxSize())
        }
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
            Text(stringResource(R.string.viewer_gesture_hint),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

### 5.4 `ViewerTextList.kt` (新建)

```kotlin
@Composable
fun ViewerTextList(lineBoxes: List<TextLine>, hits: List<RuleHit>, modifier: Modifier = Modifier) {
    val hitMap = remember(hits) { hits.associateBy { it.matchedText } }
    LazyColumn(modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("header") { ViewerTextHeader(lineCount = lineBoxes.size, hitCount = hits.size) }
        itemsIndexed(items = lineBoxes, key = { i, line -> "$i-${line.text.hashCode()}" }) { i, line ->
            TextLineRow(index = i + 1, line = line, hit = hitMap[line.text])
        }
        item("footer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TextLineRow(index: Int, line: TextLine, hit: RuleHit?) {
    val bg = if (hit != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
             else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Text("$index", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp))
            Text(line.text, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            ConfidenceBadge(line.confidence)
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.85f -> MaterialTheme.colorScheme.primary
        confidence >= 0.50f -> MaterialTheme.colorScheme.tertiary
        else                 -> MaterialTheme.colorScheme.error
    }
    Text("${(confidence * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.padding(start = 8.dp))
}
```

### 5.5 `ImagePreview.kt` (修改)

新增可选参数,保持向后兼容:
```kotlin
@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine> = emptyList(),
    hits: List<RuleHit> = emptyList(),
    showLineBoxes: Boolean = true,
    modifier: Modifier = Modifier,
    onOpenViewer: (() -> Unit)? = null,
    enableViewerTap: Boolean = false,
) {
    // 既有 ImagePreview 内部:
    // - AsyncImage(...)
    // - HighlightOverlay(...)
    // 在外层 Box 增加:
    if (enableViewerTap && onOpenViewer != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onOpenViewer() })
        }
    } else Modifier
    // ...
}
```
现有调用点(除 `HomeScreen` 外)零改动。

### 5.6 `HomeScreen.kt` (修改)

- `ImagePreview` 调用处加 `onOpenViewer = { navController.navigate(Routes.VIEWER) }, enableViewerTap = current is AnalysisState.OcrDone || current is AnalysisState.Complete`
- 新增 `navController: NavController` 形参(其他 caller 同步加默认 `null`,与现有 `onOpenSettings` 模式一致)

### 5.7 `IceSpiritNavHost.kt` (修改)

- `Routes.VIEWER = "viewer"`
- `composable(Routes.VIEWER) { ViewerScreen(onBack = { nav.popBackStack() }) }`

### 5.8 `IceSpiritVisionViewModel.kt` (修改)

```kotlin
private var _lastLineBoxes: List<TextLine> = emptyList()

fun startAnalysis(uri: Uri) {
    viewModelScope.launch {
        _state.value = AnalysisState.Loading(Stage.OcrRunning)
        val ocr = ocrEngine.recognize(uri)
        _lastLineBoxes = ocr.lineBoxes                       // ← 缓存
        _state.value = AnalysisState.OcrDone(ocr.text, ocr.confidence, ocr.lineBoxes)
        _state.value = AnalysisState.Loading(Stage.RuleScanning)
        val hits = matcherFor(_currentTab.value).scan(ocr.text)
        _state.value = AnalysisState.RuleScanned(hits)
        _state.value = AnalysisState.Complete(ViolationReport(
            imageUri = uri, ocrText = ocr.text, hits = hits,
            timestampMs = System.currentTimeMillis(),
            avgConfidence = ocr.confidence,
            lineBoxes = _lastLineBoxes,                       // ← 装入 report
        ))
    }
}

fun reset() {
    _state.value = AnalysisState.Idle
    _lastLineBoxes = emptyList()                             // ← 同步清空
}
```
(具体代码段以现有 ViewModel 实现为准,这里描述意图:lineBoxes 必须在 `OcrDone` 之后保留直到 `reset()`。)

### 5.9 `strings.xml` 追加

```xml
<string name="viewer_title">原图查看</string>
<string name="viewer_image_cd">原图</string>
<string name="viewer_gesture_hint">双指缩放 · 双击切换 · 单指拖动</string>
<string name="viewer_empty">无可查看结果,请先拍照或选图识别。</string>
<string name="viewer_image_load_error">图片加载失败</string>
```

---

## 6. 错误处理 & 边界

| 场景 | 行为 |
|---|---|
| `state` ≠ `Complete`(`Idle`/`Error`/`Loading`) | `ViewerScreen` 渲染 `ViewerEmpty`,返回箭头 + 系统返回键立即 `popBackStack` |
| `report.imageUri` Coil 解码失败 | `ViewerImage` fallback 到 `Icons.Outlined.BrokenImage` + "图片加载失败";底部文本仍可滚动 |
| `report.lineBoxes.isEmpty()`(规则命中但 OCR 无 boxes) | Viewer 仅显示图片 + 底部"未识别到文字"占位 |
| `report.lineBoxes` 大但 `hits` 为空 | 全部 `TextLineRow` 用 `surfaceContainerHigh` 背景(无错误高亮) |
| Viewer 期间 `ViewModel.reset()` 被调用 | ViewModel state 变 `Idle` → `ViewerScreen` 检测到 `report == null` → 弹短 toast "结果已重置" → `popBackStack` |
| 12 MP+ 大图 OOM | 不在本 PR 范围。Coil 默认按 intrinsic 解码;OOM 时系统会杀进程 |
| EXIF 旋转 | Viewer **不**应用 EXIF 旋转(与 HomeScreen 一致);独立工单 |
| OCR 框错位(2048 下采样 vs 全分辨率) | 已知 pre-existing;独立工单 |
| 横竖屏旋转 | `rememberSaveable` 保存 `ZoomableImageState`(scale + offset) |
| 进程被杀 | 默认回 `Routes.HOME`(用户预期) |

---

## 7. 测试策略

### 7.1 单元测试 (JVM, Robolectric)

**`IceSpiritVisionViewModelTest.kt`**(新增):
- `OcrDone(lineBoxes=[…]) → Complete` 后,`report.lineBoxes == 原始 list`
- `reset()` 后 state == Idle,且 `lastLineBoxes` 已清空(`startAnalysis(新 URI)` 后 lineBoxes 是新的)

**`ViolationReportTest.kt`**(若不存在则新建):
- 默认 `lineBoxes = emptyList()` 验证
- `copy()` 不破坏其他字段

### 7.2 Compose UI 测试 (Robolectric + `createAndroidComposeRule`)

**`ImagePreviewDoubleTapTest.kt`**(新增):
- 双击触发 `onOpenViewer` lambda 调用 1 次;单击不触发
- `enableViewerTap = false` 时双击不触发

**`ViewerScreenTest.kt`**(新增):
- `state = Complete(report)` → 渲染 `ViewerTopBar` + `ViewerImage` + `ViewerTextList`;LazyColumn `itemCount == report.lineBoxes.size`
- `state = Idle` → 渲染 `ViewerEmpty`,返回箭头触发 `onBack`
- 返回箭头触发 `onBack` lambda 1 次

**`ViewerTextListTest.kt`**(新增):
- 行数 == lineBoxes.size
- 命中关键词的行用 `errorContainer.copy(alpha=0.35f)` 背景;其余用 `surfaceContainerHigh`
- 顺序与 lineBoxes 一致
- `confidence >= 0.85` / `0.5..0.85` / `<0.5` 三档分别显示 primary / tertiary / error

### 7.3 手动验证

- 沙箱图 3-5 张(全分辨率竖/横),双指缩放 / 双击切换 / 单指拖动手感
- OCR 文本拖到底能看到 footer `Spacer`,无截断
- `ThemeMode.DARK` / `LIGHT` 下 overlay 颜色与文字对比度
- `modelProfile=shell` 与 `ice_ocr_rules` 都编译通过

### 7.4 回归

- `testDebugUnitTest`(项目既有,涵盖 `HomeScreen` / `ViewModel` / 各 rule matcher)
- `./gradlew.bat assembleDebug -PmodelProfile=shell` 与 `-PmodelProfile=ice_ocr_rules`

---

## 8. 文件清单

### 8.1 新增

| 路径 | 用途 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt` | 路由入口 |
| `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTopBar.kt` | 顶栏 |
| `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerImage.kt` | Telephoto + HighlightOverlay + hint chip |
| `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTextList.kt` | LazyColumn 逐行 OCR 文本 |
| `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerEmpty.kt` | state ≠ Complete 时占位 |
| `app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerScreenTest.kt` | UI 测试 |
| `app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTextListTest.kt` | UI 测试 |
| `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt` | UI 测试 |

### 8.2 修改

| 路径 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | 新增 `telephoto` 版本 + `telephoto-zoomable-image-coil` library |
| `app/build.gradle.kts` | `implementation(libs.telephoto.zoomable.image.coil)` |
| `app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt` | `ViolationReport` 追加 `lineBoxes: List<TextLine> = emptyList()` |
| `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt` | 缓存 `_lastLineBoxes`,装入 report;`reset()` 清空 |
| `app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt` | 新参数 `onOpenViewer` / `enableViewerTap`,`Modifier.pointerInput` 双击 |
| `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` | 注入 `navController`,`onOpenViewer` lambda |
| `app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt` | 新增 `Routes.VIEWER`,注册 `composable` |
| `app/src/main/res/values/strings.xml` | 追加 `viewer_*` 字符串 |
| `app/src/test/java/com/icespiritai/offline/IceSpiritVisionViewModelTest.kt` | 新增 lineBoxes 转移断言;`reset()` 断言 |

### 8.3 不动

- OcrEngine / RuleMatcher / Repository(Phase 1)
- Phase 2 拆分(`OcrDone` / `RuleScanned` / `Complete` state machine 形状不变,仅多装一个字段)
- AGP 9.3 / Kotlin 2.4.10 / Gradle 9.7 / Compose BOM 2026.08.00 / NDK 28.2.13676358 / minSdk 26 / targetSdk 37
- 既有 `Routes.HOME / SETTINGS / CHANGELOG`
- 签名 / release 流水线 / Gitea 上传

---

## 9. 验收标准

- [ ] `./gradlew.bat assembleDebug -PmodelProfile=shell` 与 `-PmodelProfile=ice_ocr_rules` 均成功
- [ ] `./gradlew.bat testDebugUnitTest` 全绿
- [ ] `HomeScreen` 双击图片 → 进入 Viewer;返回箭头 / 系统返回键回 Home
- [ ] Viewer 内大图支持双指缩放 / 单指拖动 / 双击切换 1.0 ↔ 2.5×
- [ ] Viewer 底部 OCR 文本为逐行 LazyColumn,可滚动到底看到 footer
- [ ] 命中关键词的行用错误容器色高亮,与 `HitCard` 视觉一致
- [ ] 浅色 / 深色 `ThemeMode` 下文字 / hint chip 对比度达标
- [ ] OCR 高亮框随图片缩放/拖动保持对齐
- [ ] state ≠ Complete 时进入 Viewer 不会崩,展示空态
- [ ] 旋转屏幕后 `ZoomableImageState` 通过 `rememberSaveable` 恢复