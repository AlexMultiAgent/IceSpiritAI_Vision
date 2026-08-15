# 冰灵锐目 UI 设计规范 — 执法场景单页直入式

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-15 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline 库 | `docs/knowledge/build-stack-2026-08.md` |
| 关联 Phase 1 spec | `docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md` |
| 关联 Phase 2 spec | `docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md` |

本文档**叠加**在 Phase 1 / Phase 2 之上,**仅**涉及 UI 层(COMPOSE + 组件 + 资源 + 主题 + 设置 + 导出),**不**触及 `OcrEngine` / `RuleMatcher` / Repository / Phase 2 拆分等已有设计。后端逻辑保持不变。

---

## 1. 背景与目标

### 1.1 现状

冰灵锐目目前 `MainScreen.kt` 89 行,基础形态:
- 顶栏仅标题"冰灵锐目"
- 顶部两个按钮"选图"+"拍照"
- 主体五状态文本流(Idle/Loading/OcrDone/RuleScanned/Complete/Error)
- **没有图片预览** — `Complete` 状态只显示 OCR 文本 + 命中卡片
- **没有高亮原图** — `OcrResult.lineBoxes` 已存在但未渲染
- **没有导出**
- **没有主题切换**
- **没有 Tab 切换(业务模式)**

### 1.2 目标

为执法场景设计一个**简明易用**的 UI,核心约束:
- 单张即拍即审(用户已经在前面 brainstorm 确认)
- 现场强光下可读(深色高对比)
- 归档/导出时适合截图/打印(浅色证件感)
- 多业务模式可扩展(广告法/食品标签,首版 Tab 切换骨架,食品标签 disabled)

### 1.3 非目标(本期)

- 历史记录 / 列表页
- 规则编辑 / 规则库 CRUD
- 多语种(仅中文,但 i18n 资源已就位)
- 云端同步 / 账号体系
- 食品标签 OCR(占位 Tab disabled)
- 导出后再编辑

---

## 2. 用户画像与场景

### 2.1 目标用户

**执法人员 / 监管自查** — 市监 / 城管 / 工商局现场执法,需快速取证式输出(违规清单 + 法规条款 + 可导出证据)。

### 2.2 场景

- **现场**:看到广告牌 → 拍照 → 几秒出结果 → 现场反馈
- **归档**:回办公室切浅色 → 输出取证包 → 截图归档 / 上传政务系统

### 2.3 关键约束

| 约束 | 推导 |
|---|---|
| 拍照到结果最短路径 | 单页直入式, 不跳转 |
| 强光下可读 | 深色高对比, 警示色突出 |
| 截图归档 | 浅色白底, 证件感 |
| 业务模式可扩展 | 顶栏 Tab(广告法/食品标签) |

---

## 3. 信息架构

### 3.1 主屏结构

```
┌──────────────────────────────────────────┐
│ 冰灵锐目        [广告法│食品标签]    ⚙ │  ← TopAppBar + Tab + 设置
├──────────────────────────────────────────┤
│  状态条:无 / 识别中 / 违规 3 处           │  ← 状态消息条(动态颜色)
├──────────────────────────────────────────┤
│                                          │
│          [ 图片预览 / 高亮原图 ]          │  ← 主体 60% 高度
│                                          │
│                                          │
├──────────────────────────────────────────┤
│                                          │
│   命中卡片区(结果时)/ 空状态引导          │  ← 主体 40% 高度
│                                          │
├──────────────────────────────────────────┤
│   [ 📷 拍照 ]    [ 选图 ]    [ 导出 ]    │  ← 底部操作栏(按状态切换)
└──────────────────────────────────────────┘
```

### 3.2 状态机

| 状态 | 顶栏 Tab | 主体上半 | 主体下半 | 底部 |
|---|---|---|---|---|
| `Idle` | 可用 | 占位图(提示对正图片) | 引导文案 | `[拍照] [选图]` |
| `Loading` | 禁用 | 已选图 + 半透明加载圈 | "OCR 识别中…" / "规则扫描中…" | 禁用 |
| `Complete` (无命中) | 可用 | 已选图 | 绿色"未发现违规"卡 | `[重拍] [导出报告]` |
| `Complete` (有命中) | 可用 | 已选图 + 红黄高亮 + 命中框 | 命中卡片列表(可滚动) | `[重拍] [导出取证包]` |
| `Error` | 可用 | 已选图(若有) | 错误提示 + 错误码 | `[重试] [去授权]` |

**Tab 切换规则:**
- `Loading` 状态时两个 Tab 都禁用(避免误操作)
- `食品标签` Tab 永远 disabled(占位)— 点击弹 Toast"食品标签 OCR 即将上线"
- `广告法` Tab 在非 Loading 时可点击

**Tab 切换时:**
- `viewModel.reset()` 清状态 → 回 `Idle`
- **不**释放 `OcrEngine`(沿用 Phase 2 决策 — process-wide native resources)
- 切换 `RuleMatcher` 实例(根据 Tab 选)

### 3.3 导航

单 Activity + 单 NavHost,两个 route:
- `home` — 主屏
- `settings` — 设置页

设置入口走顶栏齿轮图标。深链接暂不提供。

---

## 4. 视觉风格

### 4.1 主题三选一(设置切换)

| 模式 | 触发 | 用途 |
|---|---|---|
| **跟随系统**(默认) | `AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM` | 大多数用户无需设置 |
| **深色** | `MODE_NIGHT_YES` | 现场强光下可读 |
| **浅色** | `MODE_NIGHT_NO` | 归档/导出适合截图 |

### 4.2 配色

**深色(政务深色面板):**
- 背景:`#0f172a` (深石板蓝)
- 卡片:`#020617` (更深背景)
- 边框:`#334155` (中灰)
- 文字主:`#e2e8f0` (近白)
- 文字副:`#94a3b8` (中灰)
- 违规(红):`#f87171` / `#7f1d1d`
- 警告(黄):`#fbbf24` / `#78350f`
- 成功(绿):`#86efac` / `#14532d`

**浅色(执法白底):**
- 背景:`#ffffff`
- 卡片:`#fefefe` + 边框 `#e2e8f0`
- 文字主:`#0f172a`
- 文字副:`#64748b`
- 违规(红):`#dc2626` / `#fee2e2`
- 警告(黄):`#d97706` / `#fef3c7`
- 成功(绿):`#16a34a` / `#dcfce7`

**沿用现有 `Theme.IceSpiritOffline` parent `Theme.Material3.Light.NoActionBar`,Material3 动态色**禁用**(避免与冰灵灰蓝撞色)。

### 4.3 字体

- 默认 Roboto(中文用系统中文)
- 命中条款用 `MaterialTheme.typography.titleMedium`(中文加粗)
- 法规摘录用 `MaterialTheme.typography.bodySmall` + 灰色

---

## 5. 组件拆解

### 5.1 包结构

```
com.icespiritai.offline
├── ui/
│   ├── MainScreen.kt              ← 根 Composable(重写)
│   ├── nav/
│   │   └── IceSpiritNavHost.kt    ← NavHost(home / settings)
│   ├── theme/
│   │   ├── Theme.kt               ← Theme.IceSpiritOffline (深/浅 双 scheme)
│   │   ├── Color.kt               ← 深色 + 浅色 palette
│   │   ├── Type.kt                ← 字号/字重
│   │   └── ThemeMode.kt           ← ThemeMode enum + 持久化
│   ├── home/
│   │   ├── HomeScreen.kt          ← 主屏(原 MainScreen 改名)
│   │   ├── HomeTopBar.kt          ← 顶栏(标题 + Tab + 设置)
│   │   ├── RuleTabBar.kt          ← Tab(广告法 / 食品标签)
│   │   ├── CaptureBar.kt          ← 底部拍照+选图按钮
│   │   ├── ImagePreview.kt        ← 图片预览 + 高亮层
│   │   ├── HighlightOverlay.kt    ← Canvas 绘制 OCR 命中框
│   │   ├── StatusBanner.kt        ← 状态条(Idle/Loading/违规数)
│   │   ├── ResultPanel.kt         ← 结果区(命中卡片 + 法规)
│   │   ├── HitCard.kt             ← 命中卡片(从 MainScreen 抽出)
│   │   ├── CaptureButton.kt       ← 大拍照 FAB
│   │   └── LoadingOverlay.kt      ← 加载圈 + 文字
│   ├── settings/
│   │   ├── SettingsScreen.kt      ← 设置页
│   │   └── AppearanceSection.kt    ← 外观(跟随系统/深色/浅色)
│   └── components/
│       └── SeverityBadge.kt       ← 严重度徽章(信息/警告/违规)
├── export/
│   ├── ExportAction.kt            ← 导出取证包(协调)
│   └── EvidencePackageBuilder.kt  ← 构造 zip(图+JSON+时间戳)
├── settings/
│   └── SettingsRepository.kt      ← DataStore 持久化
```

### 5.2 关键组件职责

| 组件 | 职责 | 依赖 |
|---|---|---|
| `HomeScreen` | 监听 `state`, 分发到子组件 | `IceSpiritVisionViewModel.state` |
| `ImagePreview` | 用 Coil 加载选中图,叠加 `HighlightOverlay` | `Coil` |
| `HighlightOverlay` | 透明 Canvas, 按 `Severity` 着色画矩形 | `OcrResult.lineBoxes` |
| `HitCard` | 单条命中: 等级徽章 + 匹配文本 + 法规 + 类别 | `R.string` |
| `ExportAction` | 调 `EvidencePackageBuilder` → FileProvider + Intent | `EvidencePackageBuilder` |
| `SettingsScreen` | 读 `SettingsRepository`, 写回 DataStore | `SettingsViewModel` |

### 5.3 关注点分离

- `ui/` 只渲染, 不查 `OcrEngine` / `RuleMatcher`
- `export/` 不知道 Compose 存在, 接受 `ViolationReport` 返回 `File`
- `SettingsRepository` 不知道 ViewModel 存在, 暴露 `Flow<ThemeMode>`

---

## 6. 数据流

### 6.1 状态机驱动

```
[Idle]  ──拍照/选图──▶  [Loading.Ocr]
                            │
                            ▼
                       [Loading.RuleScanning]
                            │
                            ▼
                       [Complete(report)]  或  [Error(code)]
                            │
                  用户按[重拍] / 切Tab
                            │
                            ▼
                         [Idle]
```

### 6.2 协程流(IceSpiritVisionViewModel.startAnalysis)

```
拍照/选图 → Uri
    │
    ▼
ImageAnalyzerRepository.analyze(uri): Flow<AnalysisState>
    │
    ├──▶ Loading(OcrRunning)     ──►  UI: 半透明加载圈
    ├──▶ OcrDone(boxes, conf)    ──►  UI: 暂不显式渲染, 立即触发规则扫描
    ├──▶ Loading(RuleScanning)   ──►  UI: 加载圈文字变
    ├──▶ Complete(report)         ──►  UI: 显示高亮 + 命中卡片
    └──▶ Error(code)             ──►  UI: 错误提示 + 重试
```

**单飞保护:** `IceSpiritVisionViewModel.startAnalysis` 已有 `currentJob?.cancel()`,切 Tab 时 `viewModel.reset()` 清状态再切。

### 6.3 错误处理

| ErrorCode | 显示文案 | 重试按钮 | 其他动作 |
|---|---|---|---|
| `OCR_UNAVAILABLE` | "OCR 模型加载失败,请检查 APK 是否完整" | `[重试]` | — |
| `OCR_FAILED` | "图片识别失败,请换一张清晰图片" | `[重试]` | — |
| `RULES_FAILED` | "规则库加载失败(打包缺陷)" | — | `[上报问题]` |
| `UNKNOWN` | "未知错误,请重试" | `[重试]` | — |

**权限错误:** 拒绝相机权限 → 弹"需要相机 / 媒体权限"对话框 → `[去授权]` 走 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。

**错误页保留原图**(现场拍照后,用户能判断"是不是图片糊了"再决定重拍)。

### 6.4 设置持久化

```kotlin
// SettingsRepository.kt
enum class ThemeMode { SYSTEM, DARK, LIGHT }

class SettingsRepository(private val ds: DataStore<Preferences>) {
    val themeMode: Flow<ThemeMode> = ds.data.map { ... }
    suspend fun setThemeMode(mode: ThemeMode) { ds.edit { ... } }
}
```

**Activity 启动时一次性同步:**
```kotlin
class IceSpiritVisionActivity : ComponentActivity() {
    override fun onCreate(...) {
        lifecycleScope.launch {
            settings.themeMode.first().let { mode ->
                AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
            }
        }
        setContent {
            IceSpiritVisionTheme {
                IceSpiritNavHost()  // 来自 ui/nav/IceSpiritNavHost.kt
            }
        }
    }
}
```

**Compose 层:**
```kotlin
@Composable
fun IceSpiritVisionTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
```

---

## 7. 性能

- `ImagePreview` 用 Coil,默认 `crossfade(true)`(300ms),避免图片突变
- `HighlightOverlay` 用 `Canvas` + `drawRect`,不引入 Skia/2D 库
- 命中卡片用 `LazyColumn`,首屏只渲染可见项
- Tab 切换不重启 ViewModel,**只重置状态**(避免 `OcrEngine` 重新初始化,Phase 2 已确认)

---

## 8. 无障碍

**基础层(本版本做):**
- 所有 `IconButton` / `Image` 都加 `contentDescription`
- `CaptureButton` 大 FAB 加 `semantics { contentDescription = "拍照" }`
- `HitCard` 命中卡片用 `Modifier.semantics(mergeDescendants = true)` 整块朗读

**进阶层(本版本不做):**
- 命中条款的"朗读全文" — 留 Phase 4+
- 系统字号缩放自适应 — 沿用 Material3 默认

---

## 9. 测试策略

### 9.1 单测(Robolectric,纯 JVM)

| 测试类 | 验证 |
|---|---|
| `ThemeModeTest` | `ThemeMode.toNightMode()` 对 SYSTEM/DARK/LIGHT 映射正确 |
| `SettingsRepositoryTest` | DataStore 写入 → `themeMode` flow 发射新值 |
| `EvidencePackageBuilderTest` | 给定 `ViolationReport`, 输出 zip 包含 `image.jpg` + `report.json` + `manifest.txt` |
| `ImagePreviewTest` | Mock `OcrResult.lineBoxes`, 断言 `HighlightOverlay` 绘制 N 个矩形 |
| `HitCardTest` | 各 `Severity` 渲染对应徽章颜色 |
| `StatusBannerTest` | Idle/Loading/Complete/Error 状态切换文案 |

### 9.2 Compose UI 测试(`createAndroidComposeRule`)

| 用例 | 验证 |
|---|---|
| `home_idle_shows_capture_button` | 初始状态主屏看到 `[拍照]` + `[选图]` |
| `home_loading_shows_progress` | 拍照后(预填 Uri)看到 `CircularProgressIndicator` + OCR 文字 |
| `home_complete_shows_hits` | 模拟 3 条命中, 看到 3 张 `HitCard` + 高亮 |
| `home_complete_no_violation` | 0 命中, 看到"未发现违规"绿色提示 |
| `home_error_shows_retry` | 触发 `ErrorCode.OCR_FAILED`, 看到 `[重试]` 按钮 |
| `home_error_keeps_image` | 错误状态仍渲染 `ImagePreview` |
| `home_tab_clear_state` | 切 Tab → state 回到 Idle, 加载任务被取消 |
| `home_export_button_emits_intent` | 点 `[导出]` → `ExportAction.share()` 被调, Intent.ACTION_SEND 校验 |
| `home_settings_navigate` | 点齿轮 → 跳到 `SettingsScreen` |
| `settings_change_theme` | 改主题 → DataStore 写入 → Activity 重新创建后模式正确 |

### 9.3 截图测试

不引第三方库,沿用 `androidx.compose.ui.test` 的 `captureToImage`:

```kotlin
@Test
fun home_complete_dark_screenshot() {
    composeTestRule.setContent { IceSpiritVisionTheme(darkTheme = true) { HomeScreen(state = mockComplete) } }
    composeTestRule.onRoot().captureToImage().writeToTestStorage("home_complete_dark")
}
```

需要 4 张:
- `home_idle_dark.png`
- `home_complete_dark.png` (3 命中)
- `home_idle_light.png`
- `home_complete_light.png`

### 9.4 仪器测试(`androidTest`,真机)

| 用例 | 验证 |
|---|---|
| `CameraLaunchTest` | 主屏 `[拍照]` → 系统相机出现 → 拍完返回 → `state` 进入 Loading |
| `PermissionDeniedTest` | 拒绝相机权限 → 主屏提示 `[去授权]` |
| `FileProviderShareTest` | `[导出]` → `Intent.ACTION_SEND` 启动 → manifest 中 `<provider>` 声明解析成功 |

### 9.5 性能 / 启动

- `BenchmarkRule` 测 `IceSpiritVisionActivity.onCreate → setContent` 时间 < 300ms
- `MacroBenchmark` 测相机启动到拍照结果出现 < 5s(包含 PaddleOCR 推理)

---

## 10. 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-15 | 目标用户为执法人员/监管自查 | 用户澄清 |
| 2026-08-15 | 单张即拍即审 | 用户澄清 |
| 2026-08-15 | UI 流程走 A 单页直入式 | 用户视觉对比 |
| 2026-08-15 | 主屏拍照为主 + 选图为辅 | 用户确认 |
| 2026-08-15 | 顶栏 Tab 切换(广告法/食品标签) | 用户澄清 |
| 2026-08-15 | 三选主题:**跟随系统(默认) / 深色 / 浅色** | 用户澄清 + 偏好 |
| 2026-08-15 | 不用冰灵家族蓝 | 用户偏好 |
| 2026-08-15 | 导出走 FileProvider + ACTION_SEND | 推荐 |
| 2026-08-15 | ImagePreview 用 Coil | 推荐 |
| 2026-08-15 | 错误页保留原图 | 用户授权推荐 |
| 2026-08-15 | Tab 切换不释放 OcrEngine | 沿用 Phase 2 决策 |
| 2026-08-15 | 无障碍基础层(contentDescription) | 用户授权 |
| 2026-08-15 | 首版不引入历史记录 / 规则编辑 / 食品标签 OCR | YAGNI |

---

## 11. 实施依赖

### 11.1 新增依赖

```kotlin
// app/build.gradle.kts
dependencies {
    // 已有 Phase 1 / 2 依赖
    implementation(libs.lifecycle.compose)
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlin.serialization)
    implementation(libs.hankcs.aho.corasick)

    // 首版新增
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.compose.material:material-icons-extended")
}
```

### 11.2 AndroidManifest 增量

```xml
<manifest ...>
    <!-- 已有 -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- 已有 feature -->
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <!-- 新增:FileProvider 导出取证包 -->
    <application ...>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_provider_paths" />
        </provider>
    </application>
</manifest>
```

`res/xml/file_provider_paths.xml`:
```xml
<paths>
    <cache-path name="evidence" path="evidence/" />
</paths>
```

### 11.3 资源新增

`res/values/strings.xml` 增量:

```xml
<string name="tab_ad_law">广告法</string>
<string name="tab_food_label">食品标签</string>
<string name="tab_disabled_toast">食品标签 OCR 即将上线</string>

<string name="status_image_hint">请对正图片后点击拍照</string>
<string name="status_no_violation_card">未发现违规用语</string>
<string name="status_violation_count">违规 %1$d 处</string>
<string name="status_warning_count">警告 %1$d 处</string>

<string name="action_retry">重试</string>
<string name="action_reshoot">重拍</string>
<string name="action_export">导出取证包</string>
<string name="action_export_report">导出报告</string>
<string name="action_grant_permission">去授权</string>
<string name="action_open_settings">打开设置</string>
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

---

## 12. 风险

| 风险 | 缓解 |
|---|---|
| Coil 增加了 APK 体积(~250KB) | 可接受,独立 jar |
| OCR 引擎复用 + 多 RuleMatcher 切换,可能在 ice_ocr_rules profile 出现线程冲突 | 单 ViewModel 持有,单线程协程,线程安全 |
| FileProvider 在 Android 7+ 必须设置 grantUriPermissions | manifest 已声明 |
| 相册选图 URI 长期权限(API 33+) | 用 `PickVisualMedia` API 33+ Photo Picker,无需 READ_MEDIA_IMAGES |
| 浅色 + 浅色高亮对比不足 | 浅色违规红 `#dc2626` 配浅色背景 `#fee2e2`,对比度 4.5:1(WCAG AA) |

---

## 13. 范围外明确(留给后续 Phase)

- **历史记录页**:本地 SQLite 存 `ViolationReport` 列表,搜索/导出 CSV
- **规则编辑**:可视化编辑 AdLawRule,留 Phase 3
- **食品标签 OCR**:新增 `FoodLabelOcrEngine` + `FoodLabelRuleMatcher`,占位 Tab 切换启用
- **多语种 i18n**:已留 strings 资源,留 Phase 4
- **语音播报命中条款**:TalkBack 进阶层,留 Phase 4+
- **云端同步**:留 Phase 5+, 待云端 API 设计
