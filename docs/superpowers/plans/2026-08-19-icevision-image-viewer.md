# 原图查看 + OCR 文本逐行滚动 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `HomeScreen` 图片区加双击 → 进入全屏 Viewer 路由;Viewer 上半用 Telephoto 实现双指缩放/单指拖动/双击切换,下半 LazyColumn 逐行展示 OCR 文本,命中关键词行高亮。

**Architecture:** 新增独立 `Routes.VIEWER = "viewer"` 全屏路由;`ViolationReport` 追加 `lineBoxes: List<TextLine>` 字段(默认 `emptyList()`,向后兼容),由 `ImageAnalyzerRepository` 在构造 report 时从 `ocrResult.lineBoxes` 装入;`ImagePreview` 加可选 `onOpenViewer` / `enableViewerTap` 双击参数;`HomeScreen` 注入 `navController`,触发导航。Telephoto 库(`me.saket.telephoto:zoomable-image-coil`)处理缩放/拖动/双击缩放,OCR 高亮框通过 `HighlightOverlay` 与 zoom transform 复合。

**Tech Stack:** Compose BOM 2026.08.00 / Material 3 / Coil 2.7.0 / Telephoto 0.13.x(待实现时确认最终版本) / Navigation Compose 2.8.0 / Robolectric 4.13 / JUnit 4 / Kotlin 2.4.10。

---

## 关联 spec 与文件改动一览

Spec: `docs/superpowers/specs/2026-08-19-icevision-image-viewer-design.md`(commit `ca07b19`)

文件结构改动一览(决策一次性锁):
- **新建**:`app/src/main/java/com/icespiritai/offline/ui/viewer/{ViewerScreen,ViewerTopBar,ViewerImage,ViewerTextList,ViewerEmpty}.kt`
- **新建**:`app/src/test/java/com/icespiritai/offline/ui/viewer/{ViewerScreenTest,ViewerTextListTest}.kt`
- **新建**:`app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`
- **修改**:`gradle/libs.versions.toml`、`app/build.gradle.kts`、`app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt`、`app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`、`app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`、`app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`、`app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`、`app/src/main/res/values/strings.xml`、`app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt`

---

## Task 1: 添加 Telephoto 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 在 `libs.versions.toml` 加 version + library**

在 `[versions]` 段加:
```toml
telephoto = "0.13.0"  # 实现时核对最新版本
```

在 `[libraries]` 段加:
```toml
telephoto-zoomable-image-coil = { module = "me.saket.telephoto:zoomable-image-coil", version.ref = "telephoto" }
```

- [ ] **Step 2: 在 `app/build.gradle.kts` `dependencies {}` 加 implementation**

在 OCR engine 依赖之前加一行:
```kotlin
implementation(libs.telephoto.zoomable.image.coil)
```

- [ ] **Step 3: 编译两个 profile,确认 Telephoto 加入 classpath**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat assembleDebug -PmodelProfile=shell
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

预期:两个 BUILD SUCCESSFUL;若 0.13.0 与 Compose BOM 2026.08.00 不兼容,改用最近一个兼容版本再重跑。

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(deps): 引入 telephoto-zoomable-image-coil 0.13.0 供 Viewer 缩放"
```

---

## Task 2: 给 `ViolationReport` 加 `lineBoxes` 字段

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt`
- Test: `app/src/test/java/com/icespiritai/offline/domain/AnalysisStateTest.kt`

- [ ] **Step 1: 在 `AnalysisStateTest.kt` 写新测试**

在文件末尾追加:
```kotlin
@Test
fun violationReport_lineBoxesDefaultsToEmptyForBackwardCompat() {
    val report = ViolationReport(StubUri(), "text", emptyList(), 1L)
    assertEquals(emptyList<TextLine>(), report.lineBoxes)
}

@Test
fun violationReport_carriesLineBoxes() {
    val boxes = listOf(TextLine("a", Rect(0, 0, 10, 10), 0.9f))
    val report = ViolationReport(StubUri(), "a", emptyList(), 1L, lineBoxes = boxes)
    assertEquals(boxes, report.lineBoxes)
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.domain.AnalysisStateTest.violationReport_lineBoxesDefaultsToEmptyForBackwardCompat"
```

预期: FAIL,`Unresolved reference: lineBoxes`。

- [ ] **Step 3: 在 `AnalysisState.kt` 给 `ViolationReport` 加字段**

在 `avgConfidence` 之后、`)` 之前加:
```kotlin
    /**
     * Per-line OCR text + bounding boxes from the run that produced [ocrText].
     * Drives the Viewer's per-line text list and the overlay hit boxes.
     * Defaults to `emptyList()` so legacy / programmatic constructions still
     * compile (the Viewer shows an empty text list and skips the overlay).
     */
    val lineBoxes: List<TextLine> = emptyList(),
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.domain.AnalysisStateTest"
```

预期: PASS(全部 `AnalysisStateTest` 用例)。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/domain/AnalysisState.kt app/src/test/java/com/icespiritai/offline/domain/AnalysisStateTest.kt
git commit -m "feat(domain): ViolationReport 追加 lineBoxes 字段(默认 emptyList, 向后兼容)"
```

---

## Task 3: 让 `ImageAnalyzerRepository` 把 `lineBoxes` 装入 `ViolationReport`

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`
- Test: `app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt`

- [ ] **Step 1: 在 `ImageAnalyzerRepositoryTest.kt` 写新测试**

读 `FakeOcrEngine` 的构造看 `lineBoxes` 是否已经产生(`ocrDone.lineBoxes.size == 1` 已经在文件里验证过)。在末尾追加:
```kotlin
@Test
fun `complete state's ViolationReport carries the same lineBoxes as OcrDone`() = runTest {
    val uri = StubUri()
    val states = repo().analyze(uri, matcher).toList()
    val ocrDone = states[1] as AnalysisState.OcrDone
    val complete = states[4] as AnalysisState.Complete
    assertEquals(ocrDone.lineBoxes, complete.report.lineBoxes)
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.analysis.ImageAnalyzerRepositoryTest.complete state's ViolationReport carries the same lineBoxes as OcrDone"
```

预期: FAIL,`expected: <[…]> but was: <[]>`(默认 `emptyList()`)。

- [ ] **Step 3: 修改 `ImageAnalyzerRepository.kt` 把 `lineBoxes` 传入 report**

`emit(AnalysisState.Complete(...))` 块替换为:
```kotlin
        emit(
            AnalysisState.Complete(
                ViolationReport(
                    imageUri = uri,
                    ocrText = ocrResult.fullText,
                    hits = hits,
                    timestampMs = System.currentTimeMillis(),
                    avgConfidence = ocrResult.avgConfidence,
                    lineBoxes = ocrResult.lineBoxes,
                )
            )
        )
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.analysis.ImageAnalyzerRepositoryTest"
```

预期: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt app/src/test/java/com/icespiritai/offline/analysis/ImageAnalyzerRepositoryTest.kt
git commit -m "feat(analysis): ImageAnalyzerRepository 把 ocrResult.lineBoxes 装入 ViolationReport"
```

---

## Task 4: 添加 Viewer 相关 strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 在 `strings.xml` 末尾追加**

```xml
    <string name="viewer_title">原图查看</string>
    <string name="viewer_image_cd">原图</string>
    <string name="viewer_gesture_hint">双指缩放 · 双击切换 · 单指拖动</string>
    <string name="viewer_empty">无可查看结果,请先拍照或选图识别</string>
    <string name="viewer_image_load_error">图片加载失败</string>
    <string name="viewer_lines_count">共 %1$d 行文字</string>
    <string name="viewer_hits_count">命中 %1$d 处</string>
```

- [ ] **Step 2: 编译,确认 R.string.viewer_* 可解析**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat assembleDebug -PmodelProfile=shell
```

预期: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(strings): 添加 Viewer 相关中文文案"
```

---

## Task 5: 创建 `ViewerTopBar`

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTopBar.kt`
- Test: `app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTopBarTest.kt`

- [ ] **Step 1: 写失败测试 `ViewerTopBarTest.kt`**

```kotlin
package com.icespiritai.offline.ui.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerTopBarTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `renders given title`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerTopBar(title = "测试标题", onBack = {})
            }
        }
        composeRule.onNodeWithText("测试标题").assertExists()
    }

    @Test
    fun `back arrow click invokes onBack`() {
        var backCount = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerTopBar(title = "x", onBack = { backCount++ })
            }
        }
        composeRule.onNodeWithContentDescription("返回").performClick()
        assert(backCount == 1)
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerTopBarTest"
```

预期: FAIL,`Unresolved reference: ViewerTopBar`。

- [ ] **Step 3: 创建 `ViewerTopBar.kt`**

```kotlin
package com.icespiritai.offline.ui.viewer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    )
}
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerTopBarTest"
```

预期: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTopBar.kt app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTopBarTest.kt
git commit -m "feat(viewer): ViewerTopBar — 返回箭头 + 标题(Material 3 TopAppBar)"
```

---

## Task 6: 创建 `ViewerTextList`

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTextList.kt`
- Test: `app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTextListTest.kt`

- [ ] **Step 1: 写失败测试 `ViewerTextListTest.kt`**

```kotlin
package com.icespiritai.offline.ui.viewer

import android.graphics.Rect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
import com.icespiritai.offline.ui.theme.LightIceChatOnBg
import com.icespiritai.offline.ui.theme.LightIceChatPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerTextListTest {

    @get:Rule val composeRule = createComposeRule()

    private val lines = listOf(
        TextLine("本店专治糖尿病", Rect(0, 0, 100, 20), 0.92f),
        TextLine("100% 有效", Rect(0, 30, 100, 50), 0.81f),
        TextLine("联系电话 12345", Rect(0, 60, 100, 80), 0.30f),
    )

    private val hits = listOf(
        RuleHit("medical-001", "专治", "medical-claim", "广告法 §16", Severity.Violation),
        RuleHit("absolute-001", "100% 有效", "absolute-claim", "广告法 §28", Severity.Warning),
    )

    @Test
    fun `renders one row per line`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerTextList(lineBoxes = lines, hits = hits)
            }
        }
        composeRule.onAllNodesWithText("本店专治糖尿病").assertCountEquals(1)
        composeRule.onAllNodesWithText("100% 有效").assertCountEquals(1)
        composeRule.onAllNodesWithText("联系电话 12345").assertCountEquals(1)
    }

    @Test
    fun `renders header with line and hit counts`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerTextList(lineBoxes = lines, hits = hits)
            }
        }
        composeRule.onNodeWithText("共 3 行文字").assertExists()
        composeRule.onNodeWithText("命中 2 处").assertExists()
    }

    @Test
    fun `empty lines renders header only`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerTextList(lineBoxes = emptyList(), hits = emptyList())
            }
        }
        composeRule.onNodeWithText("共 0 行文字").assertExists()
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerTextListTest"
```

预期: FAIL,`Unresolved reference: ViewerTextList`。

- [ ] **Step 3: 创建 `ViewerTextList.kt`**

```kotlin
package com.icespiritai.offline.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.domain.TextNormalizer

@Composable
fun ViewerTextList(
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
) {
    // Match by normalized text so highlight tracks HighlightOverlay's logic
    // ("100%有效" and the hit's "100% 有效" both normalize to the same key).
    val hitByNormalizedKey = remember(hits) {
        hits.associateBy { TextNormalizer.forMatching(it.matchedText) }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            ViewerTextHeader(lineCount = lineBoxes.size, hitCount = hits.size)
        }
        itemsIndexed(items = lineBoxes, key = { i, line -> "$i-${line.text.hashCode()}" }) { i, line ->
            val key = TextNormalizer.forMatching(line.text)
            val hit = hitByNormalizedKey.entries.firstOrNull { key.contains(it.key) }?.value
            TextLineRow(index = i + 1, line = line, hit = hit)
        }
        item(key = "footer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ViewerTextHeader(lineCount: Int, hitCount: Int) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = stringResource(R.string.viewer_lines_count, lineCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.viewer_hits_count, hitCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextLineRow(index: Int, line: TextLine, hit: RuleHit?) {
    val bg = if (hit != null)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
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
    Text(
        text = "${(confidence * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerTextListTest"
```

预期: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerTextList.kt app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerTextListTest.kt
git commit -m "feat(viewer): ViewerTextList — LazyColumn 逐行 OCR 文本, 命中行高亮 + 置信度徽章"
```

---

## Task 7: 创建 `ViewerImage`

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerImage.kt`

注:Telephoto 渲染需要 painter,Robolectric 不渲染真实位图。ViewerImage 的可测试部分是「hint chip 显示」「无 boxes 时不画 overlay」。完整手势行为留手动验证(本仓库不接 Espresso 真机流水线)。

- [ ] **Step 1: 创建 `ViewerImage.kt`(直接写实现,无单元测试)**

```kotlin
package com.icespiritai.offline.ui.viewer

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.ui.home.HighlightOverlay
import me.saket.telephoto.zoomable.coil.ZoomableImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

private data class FitTransform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
)

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
fun ViewerImage(
    imageUri: Uri,
    lineBoxes: List<TextLine>,
    hits: List<RuleHit>,
    modifier: Modifier = Modifier,
) {
    val state = rememberZoomableImageState()
    val painter = rememberAsyncImagePainter(model = imageUri)
    var boxSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val fit = remember(painter, boxSize) { computeFitTransform(painter, boxSize) }
    // Compose fit transform with Telephoto's user-driven zoom transform.
    // Telephoto 0.13.x exposes the transform via state.transformableState.transform
    // (a ZoomableTransform with ScaleFactor + Offset); the read is `by` delegated
    // so this Composable recomposes when the user pans / zooms.
    val zoomTransform = state.transformableState.transform
    Box(
        modifier = modifier.onSizeChanged { boxSize = it },
        contentAlignment = Alignment.Center,
    ) {
        ZoomableImage(
            painter = painter,
            contentDescription = stringResource(R.string.viewer_image_cd),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        if (lineBoxes.isNotEmpty()) {
            HighlightOverlay(
                lines = lineBoxes,
                hits = hits,
                scaleX = fit.scaleX * zoomTransform.scaleX,
                scaleY = fit.scaleY * zoomTransform.scaleY,
                offsetX = fit.offsetX + zoomTransform.offset.x,
                offsetY = fit.offsetY + zoomTransform.offset.y,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.viewer_gesture_hint),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: 编译确认 Telephoto API 名称正确**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat assembleDebug -PmodelProfile=shell
```

预期: BUILD SUCCESSFUL。若 `state.transformableState.transform` 实际 API 不同(例如 `state.transform: ScaleFactor + Offset`),对照 Telephoto 源码/Maven Central 文档修正字段名后重跑。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerImage.kt
git commit -m "feat(viewer): ViewerImage — Telephoto ZoomableImage + HighlightOverlay 联动缩放"
```

---

## Task 8: 创建 `ViewerEmpty`

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerEmpty.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.icespiritai.offline.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

@Composable
fun ViewerEmpty(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.viewer_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: 编译确认**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat assembleDebug -PmodelProfile=shell
```

预期: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerEmpty.kt
git commit -m "feat(viewer): ViewerEmpty — state != Complete 时占位"
```

---

## Task 9: 创建 `ViewerScreen`(组装)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt`
- Test: `app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerScreenTest.kt`

- [ ] **Step 1: 写失败测试 `ViewerScreenTest.kt`**

```kotlin
package com.icespiritai.offline.ui.viewer

import android.graphics.Rect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.TextLine
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun completeReport(): ViolationReport {
        val uri = android.net.Uri.parse("content://stub")
        val lines = listOf(TextLine("本店专治", Rect(0, 0, 100, 20), 0.9f))
        val hits = listOf(
            RuleHit("medical-001", "专治", "medical-claim", "广告法 §16", Severity.Violation)
        )
        return ViolationReport(uri, "本店专治", hits, 1L, lineBoxes = lines)
    }

    @Test
    fun `renders ViewerEmpty when state is Idle`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerScreen(
                    state = AnalysisState.Idle,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("无可查看结果,请先拍照或选图识别").assertExists()
    }

    @Test
    fun `renders ViewerEmpty when state is Error`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerScreen(
                    state = AnalysisState.Error("x", com.icespiritai.offline.domain.ErrorCode.OCR_FAILED),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("无可查看结果,请先拍照或选图识别").assertExists()
    }

    @Test
    fun `renders top bar title and text header when state is Complete`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ViewerScreen(
                    state = AnalysisState.Complete(completeReport()),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("原图查看").assertExists()
        composeRule.onNodeWithText("共 1 行文字").assertExists()
        composeRule.onNodeWithText("命中 1 处").assertExists()
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerScreenTest"
```

预期: FAIL,`Unresolved reference: ViewerScreen`。

- [ ] **Step 3: 创建 `ViewerScreen.kt`**

签名直接接受 `state: AnalysisState`(避免在测试里 stub 整个 ViewModel)。

```kotlin
package com.icespiritai.offline.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.AnalysisState

@Composable
fun ViewerScreen(
    state: AnalysisState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = (state as? AnalysisState.Complete)?.report
    if (report == null) {
        ViewerEmpty(modifier = modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ViewerTopBar(title = stringResource(R.string.viewer_title), onBack = onBack)
        ViewerImage(
            imageUri = report.imageUri,
            lineBoxes = report.lineBoxes,
            hits = report.hits,
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth(),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ViewerTextList(
            lineBoxes = report.lineBoxes,
            hits = report.hits,
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.viewer.ViewerScreenTest"
```

预期: PASS。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt app/src/test/java/com/icespiritai/offline/ui/viewer/ViewerScreenTest.kt
git commit -m "feat(viewer): ViewerScreen — 组装 ViewerTopBar + ViewerImage + ViewerTextList"
```

---

## Task 10: 让 `ImagePreview` 支持双击触发回调

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt`
- Test: `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`

- [ ] **Step 1: 写失败测试 `ImagePreviewDoubleTapTest.kt`**

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.ui.theme.DarkIceChatOnBg
import com.icespiritai.offline.ui.theme.DarkIceChatPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImagePreviewDoubleTapTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `double-tap invokes onOpenViewer when enabled`() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ImagePreview(
                    imageUri = android.net.Uri.parse("content://stub"),
                    lineBoxes = emptyList(),
                    hits = emptyList(),
                    modifier = Modifier.size(200.dp),
                    onOpenViewer = { openCount++ },
                    enableViewerTap = true,
                )
            }
        }
        composeRule.onNodeWithContentDescription("待分析图片")
            .doubleClick()
        assert(openCount == 1)
    }

    @Test
    fun `double-tap does NOT invoke onOpenViewer when disabled`() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkIceChatPanel, onSurface = DarkIceChatOnBg)) {
                ImagePreview(
                    imageUri = android.net.Uri.parse("content://stub"),
                    lineBoxes = emptyList(),
                    hits = emptyList(),
                    modifier = Modifier.size(200.dp),
                    onOpenViewer = { openCount++ },
                    enableViewerTap = false,
                )
            }
        }
        composeRule.onNodeWithContentDescription("待分析图片")
            .doubleClick()
        assert(openCount == 0)
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.home.ImagePreviewDoubleTapTest"
```

预期: FAIL,`Unknown arg` 或 `openCount == 0`(因为没有传入 `onOpenViewer` 参数)。

- [ ] **Step 3: 修改 `ImagePreview.kt` 加可选参数 + 双击检测**

在 `ImagePreview` 函数签名后追加两个参数 + KDoc,并把 `Box` 改成链式挂载 pointerInput:

```kotlin
@Composable
fun ImagePreview(
    imageUri: Uri?,
    lineBoxes: List<TextLine> = emptyList(),
    hits: List<RuleHit> = emptyList(),
    modifier: Modifier = Modifier,
    onOpenViewer: (() -> Unit)? = null,
    enableViewerTap: Boolean = false,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    var boxSize by remember { mutableStateOf(IntSize.Z) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    val baseModifier = modifier
        .fillMaxSize()
        .semantics { contentDescription = a11y }
        .onSizeChanged { boxSize = it }
    val gestureModifier = if (enableViewerTap && onOpenViewer != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onOpenViewer() })
        }
    } else Modifier
    Box(
        modifier = baseModifier.then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null) {
            Text(...)
        } else {
            AsyncImage(...)
            if (lineBoxes.isNotEmpty()) { HighlightOverlay(...) }
        }
    }
}
```

完整文件最终形态:

```kotlin
package com.icespiritai.offline.ui.home

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    lineBoxes: List<TextLine> = emptyList(),
    hits: List<RuleHit> = emptyList(),
    modifier: Modifier = Modifier,
    /**
     * Optional double-tap callback. When both this and [enableViewerTap] are
     * set, a double-tap on the image area fires the callback (HomeScreen wires
     * it to navigate to `Routes.VIEWER`). Default `null` keeps existing callers
     * untouched.
     */
    onOpenViewer: (() -> Unit)? = null,
    /**
     * Gate for [onOpenViewer]. Default `false` keeps existing call sites (tests
     * that don't care about navigation) untouched. HomeScreen sets this to
     * `true` only when the current state has an analysable report.
     */
    enableViewerTap: Boolean = false,
) {
    val a11y = stringResource(R.string.image_preview_desc)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    val gestureModifier = if (enableViewerTap && onOpenViewer != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onOpenViewer() })
        }
    } else Modifier
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = a11y }
            .onSizeChanged { boxSize = it }
            .then(gestureModifier),
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

- [ ] **Step 4: 运行测试,确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.home.ImagePreviewDoubleTapTest"
```

预期: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/ImagePreview.kt app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt
git commit -m "feat(home): ImagePreview 加 onOpenViewer/enableViewerTap 可选双击回调(向后兼容)"
```

---

## Task 11: 让 `HomeScreen` 接受 navController 并触发导航

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`

- [ ] **Step 1: 修改 `HomeScreen` 函数签名**

把:
```kotlin
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
```

改成:
```kotlin
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenViewer: () -> Unit = {},
) {
```

- [ ] **Step 2: 修改 `ImagePreview` 调用,注入 onOpenViewer 与 enableViewerTap**

把 `ImagePreview(...)` 块替换为:
```kotlin
        ImagePreview(
            imageUri = pendingUri,
            lineBoxes = if (showLineBoxes) lineBoxes else emptyList(),
            hits = hits,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            onOpenViewer = onOpenViewer,
            enableViewerTap = state is AnalysisState.OcrDone || state is AnalysisState.Complete,
        )
```

- [ ] **Step 3: 编译确认(本任务暂不引入 navController,只是让签名兼容)**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat assembleDebug -PmodelProfile=shell
```

预期: BUILD SUCCESSFUL。

- [ ] **Step 4: 跑 `HomeScreenTest` 确认现有断言不破**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HomeScreenTest"
```

预期: PASS(测试走的是 `HomeScreenBare`,未受影响)。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt
git commit -m "feat(home): HomeScreen 接受 onOpenViewer 回调, enableViewerTap 跟随 state"
```

---

## Task 12: 注册 `Routes.VIEWER` 并接入导航

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`

- [ ] **Step 1: 在 `Routes` 中加 `VIEWER`**

```kotlin
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CHANGELOG = "changelog"
    const val VIEWER = "viewer"
}
```

- [ ] **Step 2: 修改 `HomeScreen` 调用与新增 `ViewerScreen` 路由**

`composable(Routes.HOME) { ... }` 改为:
```kotlin
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenViewer = { nav.navigate(Routes.VIEWER) },
                )
            }
```

在 `composable(Routes.CHANGELOG)` 之后新增:
```kotlin
            composable(Routes.VIEWER) {
                val viewModel: IceSpiritVisionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val state by viewModel.state.collectAsState()
                com.icespiritai.offline.ui.viewer.ViewerScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                )
            }
```

顶部 import 加:
```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.IceSpiritVisionViewModel
```

- [ ] **Step 3: 编译确认**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat assembleDebug -PmodelProfile=shell
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

预期: 两个 BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt
git commit -m "feat(nav): Routes.VIEWER 注册到 NavHost, HomeScreen 注入 onOpenViewer 导航"
```

---

## Task 13: 全量测试 + 双 profile 编译 + 手动验证清单

- [ ] **Step 1: 跑全量单元测试**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest
```

预期: BUILD SUCCESSFUL,全部测试通过。重点确认:
- `com.icespiritai.offline.domain.AnalysisStateTest`(lineBoxes 默认 + 装载)
- `com.icespiritai.offline.analysis.ImageAnalyzerRepositoryTest`(lineBoxes 从 OcrDone 传递到 report)
- `com.icespiritai.offline.ui.viewer.*`(3 个 viewer 测试类)
- `com.icespiritai.offline.ui.home.ImagePreviewDoubleTapTest`
- `com.icespiritai.offline.ui.home.HomeScreenTest`(确认未破)

- [ ] **Step 2: 跑两个 profile 的 assembleDebug**

```bash
./gradlew.bat assembleDebug -PmodelProfile=shell
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

预期: 都 BUILD SUCCESSFUL。

- [ ] **Step 3: 手动验证清单(本仓库不接 Espresso 真机流水线,开发机自查)**

- [ ] 拍照/选图 → OCR 完成 → 双击图片区 → 推入 Viewer 全屏
- [ ] Viewer 内图片:双指缩放、单指拖动、双击切换缩放(`1.0 ↔ 2.5×`)都能工作
- [ ] Viewer 底部 OCR 文本:逐行显示;命中关键词的行用 `errorContainer` 高亮
- [ ] Viewer 顶部「双指缩放 · 双击切换 · 单指拖动」hint chip 显示
- [ ] 命中关键词的 OCR 框在缩放/拖动后仍跟随图片(HighlightOverlay 联动正常)
- [ ] 返回箭头与系统返回键都能回到 HomeScreen
- [ ] `ThemeMode.DARK` / `LIGHT` 下 hint chip + 行文本对比度达标
- [ ] `state = Idle` 时进入 Viewer(理论上 NavGraph 不会发生;若发生)看到 ViewerEmpty

---

## 验收标准(对应 spec §9)

- [ ] `./gradlew.bat assembleDebug -PmodelProfile=shell` 与 `-PmodelProfile=ice_ocr_rules` 均成功
- [ ] `./gradlew.bat testDebugUnitTest` 全绿
- [ ] HomeScreen 双击图片 → 进入 Viewer;返回箭头 / 系统返回键回 Home
- [ ] Viewer 内大图支持双指缩放 / 单指拖动 / 双击切换 `1.0 ↔ 2.5×`
- [ ] Viewer 底部 OCR 文本为逐行 `LazyColumn`,可滚动到底看到 footer
- [ ] 命中关键词的行用错误容器色高亮,与 `HitCard` 视觉一致
- [ ] 浅色 / 深色 `ThemeMode` 下文字 / hint chip 对比度达标
- [ ] OCR 高亮框随图片缩放/拖动保持对齐
- [ ] state ≠ Complete 时进入 Viewer 不会崩,展示空态
- [ ] 旋转屏幕后 `ZoomableImageState` 通过 `rememberSaveable` 恢复