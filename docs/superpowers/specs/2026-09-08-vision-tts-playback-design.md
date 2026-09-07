# 冰灵锐目 识别结果语音播放 — Android TTS + 兜底引擎

| 项 | 值 |
|---|---|
| 文档版本 | v1.0 |
| 日期 | 2026-09-08 |
| Spec 状态 | 待评审 |
| 上一版 UI spec | [`docs/superpowers/specs/2026-09-07-vision-editorial-redesign-design.md`](2026-09-07-vision-editorial-redesign-design.md)(Editorial 重塑) |
| 关联项目根指令 | [`CLAUDE.md`](../../CLAUDE.md) |
| 关联 plan | 待写作 `docs/superpowers/plans/2026-09-08-vision-tts-playback.md` |

本文档记录冰灵锐目 **识别结果语音播放功能** 的设计决策:TTS 抽象 + 系统当前首选 TTS(Android `TextToSpeech` API 路由到用户在系统设置选定的引擎)+ 兜底引擎 APK 下载/安装(用于设备零中文 TTS 引擎场景)。实施细节(文件清单 / 依赖 / 发版切分 / 测试路径 / 引擎 APK 打包脚本)在后续 plan 中。

---

## 1. 背景与目标

**用户场景**:市监/城管执法人员对一张广告招牌拍照识别完成后(state = Complete),现场需要快速"听一遍命中"以判断是否当场需要整改 — 视线不离招牌(可能在墙上、在车上、在 1.5m 外),需要声音反馈命中要点。

**当前痛点**:

- 识别结果只能看不能听 — 视线必须切到屏幕才能知道命中了哪些违规
- 严重度高(Violation)的命中需要在现场「口头提醒商家」,但凭记忆复述容易漏
- 0 命中时希望快速听到「无问题」以便进入下一张

**目标**:在识别完成后,提供**单按钮 toggle** 语音播报识别结果:
- 有命中:按严重度顺序朗读每条 `matchedText`
- 无命中:朗读"未筛查出违规事项,AI识别仅供参考"
- 中文朗读,设备无中文 TTS 时引导用户下载冰灵中文 TTS 引擎

**非目标**(本 spec **不动**):

- OCR / PaddleOCR 模型 / 规则库 / 状态机 / 导出 / Viewer
- Phase 3 Editorial 重塑的视觉(本 spec 在 Phase 3 之上叠加,不破坏已有设计语言)
- AdSignage 之外 tab 的 TTS(FoodLabeling 仍走 `RuleTabBar.visibleTabs` 锁定单一焦点,不动)
- 语速 / 音调控制 — 走 Android 系统 TTS 设置(`Settings.ACTION_TEXT_TO_SPEECH_SETTINGS`),不在 app 内重复实现
- 视觉合成 — 仅 TTS,不动 Sora/PaddleOCR-VL 等图像生成路线
- 多语种朗读 — 仅中文;英语 / 俄语等不在 v1 范围
- 流式 TTS(边说边识别)— 整段朗读,非增量
- 后台朗读保活 — 切后台 TTS 自然停止,不做保活(`FeedbackReleaseHygiene` 同样原则:不加非必要的后台逻辑)

---

## 2. 设计约束(在任何决策之前必须知道)

1. **Phase 3 Editorial 视觉不破坏**:Source Han Serif SC 字体 + Editorial palette + 透明按钮 + accent 文字 + Hairline 1px 分隔线 — 朗读按钮按此语言设计,不引入新颜色 token。
2. **后端不变**:`IceSpiritVisionViewModel` / `OcrEngine` / `RuleMatcher` / `ExportAction` / 状态机完全不动 — TTS 是 ViewModel 之外的新组件,通过 `LocalTtsController` 接入 UI。
3. **CLAUDE.md 三项目一致性**:`namespace` / `applicationId` / `theme name` / `minSdk/targetSdk` / `ABI arm64-v8a` / `Gradle Wrapper 9.7 + AGP 9.3 + Kotlin 2.4.10` 全部冻结。新增代码必须在 `com.icespiritai.offline.*` 下,不动 `Theme.IceSpiritOffline`。
4. **CJK 一致性**:`docs/superpowers/specs/` 与 `知识库/` 中所有文档中文写作;`code-level comments` 与 `KDoc` 中文,与 CLAUDE.md 一致。
5. **不引入新依赖**:sherpa-onnx + ONNX Runtime 仅在**独立引擎 APK**(`icespirit-tts-engine`)中,vision APK 不嵌入。vision APK 不增加任何 TTS 相关 AAR 依赖。
6. **Privacy**:朗读内容仅包含识别结果的违规要点,**不包含用户照片 / 位置 / 个人数据**。引擎 APK 离线工作,不上传任何数据。

---

## 3. 方向选择(从 3 选 1)

### 3.1 TTS 引擎选择

**术语统一**(本 spec 全文遵循):

- "**系统当前首选 TTS**" = `android.speech.tts.TextToSpeech` API 抽象,实际运行时路由到 `Settings → 语言和输入法 → 文字转语音 → 首选引擎` 当前用户选定的那个 — 华为 nova 6 = 荣耀 AI 语音引擎 / Pixel = Google TTS / 三星 = Samsung TTS / 小米 = Xiaomi TTS 等。**不是** 特指 "Google TTS" 或 "Android OS 内嵌引擎"。
- "**跟随系统首选**" = `enginePackage = null`,Android 自动按用户首选引擎路由

| 方向 | 取舍 | 选定 |
|---|---|---|
| A:仅系统当前首选 TTS | 零开发,直接调 `TextToSpeech`;但用户设备必须装有中文引擎,否则降级到英文或无声 | |
| B:集成 sherpa-onnx 端侧 ONNX TTS 到 vision APK | 完全离线 + 中文;但 vision APK 体积 +150MB(`sherpa-onnx AAR` + `ONNX Runtime` + `matcha-zh-baker 123MB 模型`),与 `feedback-release-hygiene`(不发版号不 bump + 不膨胀 APK)冲突 | |
| **C:系统当前首选 TTS 优先 + 独立 `icespirit-tts-engine` APK 兜底** | v1 APK 不增体积;设备有中文引擎走系统,没有走兜底 APK(下载 ~150MB 装上后与系统引擎统一体验) | ✓ |

**为什么不选 A**:`giteaadmin/Model` 仓库 `sherpa-onnx-matcha-zh-baker` 已存在,但用户原始诉求 `http://125.211.45.14:3000/giteaadmin/Model/releases` 明确指向该模型。仅 A 等于绕过用户给出的兜底意图。

**为什么不选 B**:vision APK 已 58MB(`ice_ocr_rules` profile,含 ONNX Runtime + OpenCV),再加 150MB 变 200MB+。`feedback-release-hygiene` memory 强调"不膨胀 APK"。B 强制用户在下载 vision APK 时就把 TTS 模型下下来,违反"按需下载"原则。

**为什么选 C**:

- v1 vision APK 不增加任何 TTS 相关 AAR / 模型,体积不变
- 设备有原生中文 TTS(华为 HiVoice / 小米 TTS / 三星 TTS)→ 零下载立即可用,匹配最常见使用场景
- 设备没中文引擎 → 用户手动点 "下载引擎" → 一次性 ~150MB 安装 → 后续与原生引擎统一体验
- 后续 Phase 若想内置 ONNX TTS 到 vision APK(消除下载步骤),`TtsEngine` 抽象允许直接 swap-in 实现,不动 UI 层

### 3.2 UI 形态选择

| 方案 | 取舍 | 选定 |
|---|---|---|
| A:独立 FullScreen 屏(TTS 控制器 / 语速 / 引擎选择) | 完整控制;但与"识别后快速听命中"的轻量诉求冲突,且 1 tab 1 sub-screen 多余 | |
| **B:HomeTopBar 单图标按钮 + Settings 内 sub-page** | 主屏只暴露播放入口,所有配置下沉到 Settings,与 Phase 3 顶栏语言一致 | ✓ |
| C:CaptureBar 第 4 槽 | 与拍照/选图并排;但 CaptureBar 是"开始新分析"语义,Toggle 不属于该语义,且 4 按钮均分导致每按钮空间窄 | |
| D:Floating FAB 右下 | 重型进入;与 Phase 3 clean 顶栏语言冲突,且 FAB 遮住底部 hit 卡片 | |

**为什么选 B**:Phase 3 HomeTopBar 已承载"tab + settings gear",朗读按钮与 gear 同侧,语义都是「持久入口」;1px accent border 描边表明它是 actionable,与 tab/severity 色条语言一致。

### 3.3 朗读内容选择

| 方案 | 取舍 | 选定 |
|---|---|---|
| A:全文 OCR 文字 | 全,但无关文字(地址/电话)也被朗读,噪音大 | |
| **B:仅违规命中(hits.matchedText)按 severityRank 排序拼接** | 命中要点集中,符合现场快听诉求 | ✓ |
| C:违规命中 + 法规依据 | 完整;但法规条文朗读拖时长,现场用不上 | |
| D:让用户每次选择朗读范围 | 灵活;但增加每次点击次数,违反"现场一次拍立得"诉求 | |

**为什么选 B**:命中要点就是用户需要口头复述给商家的内容;法规依据屏幕可见,无需朗读。

### 3.4 朗读兜底文案

| 方案 | 取舍 | 选定 |
|---|---|---|
| A:沉默 | 0 命中时按钮灰禁 | |
| B:朗读"识别完成,未发现违规" | 简短;但与"合规"语义不同 — 可能漏命中 | |
| **C:朗读"未筛查出违规事项,AI识别仅供参考"** | 显式表达"AI 仅供参考,可能漏" — 不让用户过度信任结果 | ✓ |

**为什么选 C**:`feedback-zero-hit-not-true-negative` memory 教训:规则匹配 0 hit 不等于真负例。朗读文案必须传递"仅供参考"的不确定性,避免执法人员把 AI 结果当最终结论。

**为什么 C 不构成充分免责申明**(2026-09-08 评审补充):仅 TTS 朗读在法律告知层面**不充分**,原因是(1)现场嘈杂用户可能听错;(2)有命中时**不读**"AI 仅供参考",但 OCR 漏字 / 规则库 gap 同样会让结果不完整;(3)免责申明通常需要"明确告知 + 主动接受"机制。**C 必须搭配**:

- **ResultPanel 0 命中卡片 visible footer**:`⚠ AI 识别仅供参考,实际以现场判断为准`(见 §6.3)
- **HomeTopBar 朗读按钮 a11y contentDescription**:`朗读识别结果;AI 识别仅供参考`(见 §6.1)
- **Settings "语音播报" section footer**:`ℹ 朗读内容仅供参考,实际合规判断请以现场检查为准`(见 §7.1)
- **(可选)首次启动免责声明对话框**:`AlertDialog`,用户必须点 "我了解" — 详见 §16 Open question 8

### 3.5 Settings 集成粒度

| 方案 | 取舍 | 选定 |
|---|---|---|
| A:无 Settings 集成,全部走 Android 系统 TTS 设置 | 最 YAGNI;但 HomeTopBar 朗读按钮无开关,部分用户可能想永久关掉 | |
| **B:总开关 + 引擎选择 sub-page** | 用户可关闭 + 可切换引擎(系统默认 / HiVoice / Google TTS / 冰灵 TTS);引擎列表 100% 运行时枚举 | ✓ |
| C:总开关 + 引擎 + 语速 + 音调 | 全功能;但与 Android 系统 TTS 设置重复实现,且偏离 YAGNI | |

**为什么选 B**:

- 引擎选择是必要的 — 用户装了冰灵 TTS 后需要切回去,或装了 Google TTS 中文包后想优先用它
- 语速/音调已存在于系统设置(`Settings.ACTION_TEXT_TO_SPEECH_SETTINGS`),在 app 内重复是反模式
- 总开关对应 CLAUDE.md `feedback-release-hygiene` 精神 — 不希望某功能的用户可关闭它

---

## 4. 架构

### 4.1 组件清单

```
vision APK 新增(全部 com.icespiritai.offline.tts.*):
  ├── TtsEngine (interface)
  ├── AndroidTtsEngine (impl) — wraps android.speech.tts.TextToSpeech
  ├── TtsController (process-singleton, AppGraph 持有)
  ├── ScriptBuilder (pure function)
  ├── TtsSetting (data class, DataStore 持久化)
  ├── TtsEngineInstaller (引擎 APK 下载 + 校验 + 安装)
  ├── TtsEngineInfo (probe helper, 枚举设备引擎)
  └── LocalTtsController (CompositionLocal)

vision APK 新增 UI:
  ├── ui/home/HomeTopBar.kt (MODIFIED — 新增 ttsState / canSpeak / onSpeakToggle 参数)
  ├── ui/settings/SettingsScreen.kt (MODIFIED — 新增 "语音播报" section)
  ├── ui/settings/TtsEnginePickerScreen.kt (NEW — 引擎选择 sub-page)
  └── ui/nav/IceSpiritNavHost.kt (MODIFIED — 新增 Routes.TTS_ENGINE_PICKER 路由)

vision APK 新增 strings.xml keys:
  ├── tts_section_title, tts_total_switch, tts_total_switch_desc
  ├── tts_engine_label, tts_engine_picker_title, tts_engine_follow_system
  ├── tts_empty_title, tts_empty_download, tts_empty_settings
  ├── tts_button_desc, tts_button_disabled_desc
  └── ... (a11y desc 全部单独 key)

独立引擎 APK(icespirit-tts-engine, 独立 Gradle project):
  ├── src/main/java/com/icespiritai/tts/engine/
  │   ├── IceSpiritTtsService.kt — TextToSpeechService 实现
  │   └── IceSpiritTtsEngine.kt — wraps sherpa-onnx OfflineTts
  ├── src/main/AndroidManifest.xml — 注册 TTS_SERVICE intent filter
  └── src/main/assets/models/
      ├── tokens.txt, configuration.json, model-steps-3.onnx, vocos-22khz-univ.onnx

Gitea Model 仓库:
  └── 新 release tag: icespirit-tts-engine-v1.0.0
      └── asset: icespirit-tts-engine.apk
```

### 4.2 TtsEngine 接口

```kotlin
interface TtsEngine {
    fun speak(text: String, utteranceId: String, onDone: (String) -> Unit)
    fun stop()
    fun isSpeaking(): Boolean
    fun supportedChineseEngines(): List<EngineInfo>
    fun setEngine(packageName: String?)
    fun release()
}

data class TtsState(
    val mode: Mode,                     // Idle | Speaking | Disabled | InitFailed
    val canSpeak: Boolean,              // mode==Idle && setting.enabled && engine 仍可用
    val initFailureReason: String? = null,
)

enum class Mode { Idle, Speaking, Disabled, InitFailed }

data class TtsSetting(
    val enabled: Boolean = true,
    val enginePackage: String? = null,  // null = 跟随系统默认
)
```

### 4.3 TtsController

```kotlin
class TtsController(
    private val engine: TtsEngine,
    private val settings: TtsSettingRepository,
    private val scope: CoroutineScope,  // Application scope
) {
    val state: StateFlow<TtsState>
    val setting: StateFlow<TtsSetting>
    
    fun speak(report: RecognitionReport)
    fun stop()
    fun toggle()
    suspend fun setEnabled(b: Boolean)
    suspend fun setEngine(pkg: String?)
}
```

构造于 `MainActivity.onCreate`,释放于 `onDestroy`。AppGraph 持 process-singleton 引用。

### 4.4 ScriptBuilder(pure function)

```kotlin
object ScriptBuilder {
    fun build(report: RecognitionReport): String =
        if (report.hits.isEmpty()) FALLBACK_TEXT
        else report.hits
            .sortedByDescending { severityRank(it.severity) }
            .joinToString(separator = "。", prefix = "命中违规:") { it.matchedText.trim() }
            .plus("。")
    
    private const val FALLBACK_TEXT = "未筛查出违规事项,AI识别仅供参考"
}
```

### 4.5 LocalTtsController(CompositionLocal)

```kotlin
val LocalTtsController = staticCompositionLocalOf<TtsController> {
    error("TtsController not provided. Wrap your content in IceSpiritOfflineTheme.")
}
```

`IceSpiritOfflineTheme` 提供 `CompositionLocalProvider(LocalTtsController provides appGraph.ttsController) { ... }`。模式与 `LocalMotion` / `LocalSeverityColors` / `LocalSpacing` 一致(Phase 3 引入)。

### 4.6 AppGraph 集成

在 `app/src/main/java/com/icespiritai/offline/AppGraph.kt` 新增 `ttsController: TtsController by lazy { TtsController(AndroidTtsEngine(context), settings.ttsRepo, appScope) }`,模式与 `updateRepository` / `exportAction` 一致(无 Hilt,无 DI 框架)。

---

## 5. 数据流

### 5.1 朗读触发

```
HomeTopBar tap (state == Complete && setting.enabled):
  TtsController.toggle()
    ├─ state.mode == Idle   → engine.speak(ScriptBuilder.build(report), onDone = { _state = Idle })
    │                          state.mode = Speaking
    └─ state.mode == Speaking → engine.stop()
                                state.mode = Idle
```

### 5.2 状态机

优先级:`setting.enabled=false` > `mode==InitFailed` > `state==Complete` > 其他。

```
              setting.enabled=false (任何时候)
                  |
                  v
              [Disabled]  ───── HomeTopBar 按钮不渲染 ─────
                  ^
                  | setEnabled(false)
                  |
        setting.enabled=true + 其他状态
                  |
        +---------+----------------------+--------------+
        v         v                      v
   [InitFailed]  [Idle] ──state==Complete─> [Speaking]
        ^         ^ ^                            |
        |         | |                            | onDone / stop
        |         | |                            v
        |         | |                         [Idle]
        |         | |
        |         | +-- state=Loading (auto stop)
        |         |
        |         +-- 引擎丢失 zh-CN / 引擎被卸载
        |             setLanguage < LANG_AVAILABLE
        |
        +---- engine 安装 / 修复 -> 重 enumerate -> 重新进 [Idle]
```

### 5.3 DataStore 持久化

- `SettingsRepository` 新增 `ttsEnabled: Flow<Boolean>` + `ttsEnginePackage: Flow<String?>`
- 写时 `dataStore.edit { it[KEY_ENABLED] = b; it[KEY_ENGINE_PKG] = pkg }`
- 读时 `dataStore.data.map { TtsSetting(it[KEY_ENABLED] ?: true, it[KEY_ENGINE_PKG]) }`
- 损坏 fallback 到 `TtsSetting(enabled=true, enginePackage=null)` + Logcat warn

---

## 6. UI: HomeTopBar 朗读按钮

### 6.1 视觉矩阵

| 条件 | 图标 | 颜色 | 边框 | a11y contentDescription |
|---|---|---|---|---|
| `setting.enabled=false` | 不渲染 | — | — | — |
| `setting.enabled && state≠Complete && ttsState.mode!=InitFailed` | `Icons.AutoMirrored.Filled.VolumeUp` | `onSurface.copy(alpha=0.38f)` (灰禁) | 无 | "朗读,当前无可朗读结果" |
| `setting.enabled && ttsState.mode==InitFailed` | 同上 | grey | 无 | "朗读功能不可用,设置中查看详情" |
| `setting.enabled && state==Complete && ttsState.mode==Idle` | `VolumeUp` | `colorScheme.primary` (accent) | 1px accent border | "朗读识别结果;AI 识别仅供参考" |
| `setting.enabled && state==Complete && ttsState.mode==Speaking` | `Icons.Default.Stop` | `colorScheme.primary` | 1px accent border | "停止朗读" |

**位置**:HomeTopBar 右侧 Row,settings gear 左侧 8dp gap。`Modifier.size(40.dp)`,`IconButton` 包 `Modifier.semantics(mergeDescendants=true) { contentDescription = ... }`(Phase 3 Task 4 belt-and-braces pattern)。

**图标 swap 动画**:`Crossfade(targetState = isSpeaking, animationSpec = tween(MotionTokens.Standard))`,220ms(Phase 3 §6.3 motion token)。

### 6.2 签名扩展

```kotlin
@Composable
fun HomeTopBar(
    selectedTab: RuleTab,
    onSelectTab: (RuleTab) -> Unit,
    tabEnabled: Boolean,
    onOpenSettings: () -> Unit,
    ttsState: TtsState = TtsState.Disabled,
    onSpeakToggle: () -> Unit = {},
)
```

向后兼容(默认值让既有 Robolectric 测试不传也通过)。

### 6.3 ResultPanel 0 命中卡片 visible footer(免责申明第二通道)

**触发**:`state==Complete && report.hits.isEmpty()` 时,ResultPanel 主结果区上方显示 footer。

**视觉**(沿用 Phase 3 Hairline section + Warning accent 左侧条):

```
识别完成
─────────────────────────────────────────
✓ 未发现违规
─────────────────────────────────────────
⚠ AI 识别仅供参考,实际以现场判断为准
─────────────────────────────────────────
```

- 整段用 `MaterialTheme.typography.bodySmall` + `LocalSpacing.s`
- `⚠` 图标 + 文本 `AI 识别仅供参考,实际以现场判断为准`
- 左侧 4dp Warning accent 边条(severity 系统提供 `sev.container(Warning)` token,与 HitCard 左侧色条语言一致)
- 位于 ResultPanel 顶部 stats 区下方,HitCard LazyColumn 上方,**用户视线扫结果时必看**

**为什么不只用 TTS**:见 §3.4 "为什么 C 不构成充分免责申明"。屏幕可见 + 声音 + Settings footer 三通道强化。

**测试**:`ResultPanelA11yTest` 加 case — 0 命中时 footer 节点存在且 `contentDescription` 含 "AI 识别仅供参考"。

### 6.4 首次启动免责声明对话框(主动接受机制)

**触发**:`MainActivity.onCreate` 后,`SettingsRepository.disclaimerAcceptedAt: Flow<Long?>` 首次发射 `null` 时,根 composable 顶层弹 `AlertDialog`。

**视觉**:

```
┌────────────────────────────────────────┐
│  使用提示                                │
├────────────────────────────────────────┤
│  本应用通过 OCR 与规则匹配辅助识别广告招牌违│
│  规情形,识别结果仅供参考,实际合规判断请以│
│  现场检查为准。                          │
│                                        │
│  规则库可能滞后于最新法规,OCR 可能漏字或  │
│  误识,语音朗读由系统/兜底引擎合成,质量受  │
│  设备引擎能力影响。                      │
│                                        │
│  请将本应用作为现场辅助工具使用,不要作为  │
│  最终合规判定依据。                      │
│                                        │
├────────────────────────────────────────┤
│            [    我了解    ]              │
└────────────────────────────────────────┘
```

- `Material3 AlertDialog`,背景 `colorScheme.surfaceContainerHigh`,corner 16dp
- title `使用提示`(titleLarge + Source Han Serif SC Bold)
- body 三段(bodyMedium)
- positive button 唯一,`[ 我了解 ]`,click 后写 `disclaimerAcceptedAt = System.currentTimeMillis()` 到 DataStore 并 dismiss
- `setCancelable(false)`(按 back 不关) + `setCanceledOnTouchOutside(false)`
- 强制视觉走 Phase 3 Editorial palette + Hairline + accent border(不破坏既有设计语言)

**为什么强制一次性**:DataStore `Flow<Long?>` first() 仅取首次,后续启动跳过;`setCancelable(false)` 防 back 键绕过。这提供法律层面"主动接受"证据 — 比单纯 Settings footer 强很多。

**测试**:
- `DisclaimerDialogTest`(Robolectric):(a) 首次启动 dialog 显示;(b) 点 "我了解" 后 `disclaimerAcceptedAt` 非 null;(c) 后续启动 dialog 不显示;(d) back 键不关闭

**v1 文件清单增量**:
- `ui/common/DisclaimerDialog.kt`(composable,接 `SettingsRepository`)
- `data/settings/SettingsRepository.kt` 新增 `disclaimerAcceptedAt: Flow<Long?>` + `acceptDisclaimer(): Unit`
- `data/settings/SettingsKeys.kt` 新增 `DISCLAIMER_ACCEPTED_AT`
- `strings.xml` 新增 `tts_disclaimer_title` / `tts_disclaimer_body_*`(3 段)/ `tts_disclaimer_ack`
- `app/src/test/java/com/icespiritai/offline/ui/common/DisclaimerDialogTest.kt`

---

## 7. UI: Settings "语音播报" section

### 7.1 主屏布局(沿用 Phase 3 Card → Hairline section)

```
语音播报                                 (titleSerifMedium)

  ┌ 总开关
  │ 启用朗读功能                  [Switch ●─]
  └────────────────────────────────────────

  ┌ 引擎                                   (gated on enabled=true)
  │ 当前:<设备具体引擎名 或 "跟随系统默认">   [chevron]
  │                                       onClick → navigateToTtsEnginePicker()
  └────────────────────────────────────────

  ┌ (footer muted, BodySmallMuted, 两行)
  │ ℹ 朗读内容仅供参考,实际合规判断请以现场检查为准。
  │ 语速 / 音调请在系统「文本转语音」设置中调整。
  └────────────────────────────────────────
```

总开关 toggle → `ttsController.setEnabled(false)` → HomeTopBar 朗读按钮消失。
引擎 row disabled when 总开关 off。

### 7.2 TtsEnginePickerScreen(新 sub-page, Routes.TTS_ENGINE_PICKER)

```
选择 TTS 引擎                              (headlineMedium, Phase 3 §6.1)

正常情况(supportedChineseEngines().isNotEmpty()):
  ◯ 跟随系统默认
  ◯ <Engine 1>                              ← 运行时 fill from tts.engines
  ◯ <Engine 2>                                label 字段

Zero 引擎情况:
  ┌─ 未找到中文 TTS 引擎
  │
  │  方案 1
  │  在系统「文本转语音」设置中
  │  安装或启用中文语音包
  │  [打开系统 TTS 设置]
  │
  │  方案 2
  │  下载冰灵中文 TTS 引擎
  │  (约 150 MB,首次需联网,仅一次)
  │  [下载引擎]
  │
  └────────────────────────────────────────

下载中(progress state):
  ◯ 跟随系统默认
  ◯ <Engine 1>
  ⏺ 下载冰灵 TTS 引擎... 47%   (按钮变进度条,原地)
  ◯ <Engine 2>

下载完成(verify + install state):
  ⏺ 校验中...                              (短瞬)
  ⏺ 正在调用系统安装...                    (短瞬)
  → 自动跳系统安装弹窗
  → 用户点 "安装"
  → 系统返回 → picker 自动 refresh:
  ◯ 跟随系统默认
  ◯ <Engine 1>
  ◯ 冰灵 TTS                              ← 新出现
  ◯ <Engine 2>
  ◯ (current) 冰灵 TTS                    ← 默认选中最新安装
```

---

## 8. UI: 引擎 APK 下载 / 安装(断点续传)

### 8.1 Gitea 仓库结构

| 项 | 值 |
|---|---|
| 仓库 | `giteaadmin/Model`(复用,public) |
| Release tag | `icespirit-tts-engine-v1.0.0` |
| Asset name | `icespirit-tts-engine.apk` |
| Asset URL | `http://125.211.45.14:3000/giteaadmin/Model/releases/download/icespirit-tts-engine-v1.0.0/icespirit-tts-engine.apk` |
| Asset size | ~150 MB(模型 123.5MB + sherpa-onnx + ONNX Runtime + native) |
| Asset sha256 | release JSON `assets[0].sha256` 字段(必填,上传时算) |
| API endpoint | `GET /api/v1/repos/giteaadmin/Model/releases/tags/icespirit-tts-engine-v1.0.0` |

### 8.2 独立引擎 APK 规格(`icespirit-tts-engine`)

| 项 | 值 |
|---|---|
| packageName | `com.icespiritai.tts.engine` |
| App label | "冰灵 TTS" |
| minSdk / targetSdk | 26 / 37 |
| ABI | arm64-v8a |
| Version | 1.0.0(code 1) |
| 注册 | `<service android:name=".IceSpiritTtsService" android:exported="true" android:permission="android.permission.BIND_TTS_SERVICE"><intent-filter><action android:name="android.intent.action.TTS_SERVICE"/></intent-filter><meta-data android:name="android.service.texttospeech.TextToSpeechService"/></service>` |
| `onGetLanguage()` | `Loc("zh", "CN", "")` |
| `onIsLanguageAvailable("zh", "CN", "")` | `2`(`LANG_COUNTRY_AVAILABLE`) |
| assets 模型打包 | `app/src/main/assets/models/{tokens.txt, configuration.json, model-steps-3.onnx, vocos-22khz-univ.onnx}` |
| `androidResources.noCompress += "models/**"` | true(sherpa-onnx mmap 直接读 raw bytes) |

**模型数据来源**:从 `giteaadmin/Model/releases/download/sherpa-onnx-matcha-zh-baker/{model-steps-3.onnx, vocos-22khz-univ.onnx, tokens.txt, configuration.json}` 下载,经 `tools/build-icespirit-tts-engine.sh` 打入引擎 APK assets。

### 8.3 TtsEngineInstaller(vision APK)

**职责**:
1. 查 Gitea release metadata(`GET /api/v1/repos/giteaadmin/Model/releases/tags/<tag>`)
2. 解析 asset:download_url + size + sha256 + digest
3. 断点续传下载到 `appCtx.cacheDir/icespirit-tts-engine.apk`(单流 Range)
4. 校验 sha256
5. 触发 Android 系统安装弹窗(FileProvider + ACTION_VIEW + ACTION_INSTALL_PACKAGE)

**状态机**:

```
Idle ─tap─► QueryingRelease ─ok─► CheckingCache
                                       │
                ┌──────────────────────┼──────────────────────┐
                ▼                      ▼                      ▼
            Completed                Idle              Downloading ─progress─►
            (already exists                                                       │
             + sha256 ok)                                                         ▼
            → Installing → Done                       VerifySha256 ─ok─► VerifySha256
                                                              │            │
                                                              │ mismatch    ▼
                                                              ▼         Installing
                                                          Failed ─tap─► (retry full)
```

**State sealed class**:

```kotlin
sealed interface InstallState {
    object Idle : InstallState
    object QueryingRelease : InstallState
    object CheckingCache : InstallState
    data class Downloading(val bytesDownloaded: Long, val bytesTotal: Long) : InstallState
    object VerifyingSha256 : InstallState
    object Installing : InstallState
    data class Failed(val reason: String) : InstallState
}
```

**断点续传机制**(sidecar `.meta` 与 `.partial` 写于 `appCtx.cacheDir`,**不入 assets 不进 git**):

| 项 | 实现 |
|---|---|
| 续传判定 | sidecar `cacheDir/icespirit-tts-engine.apk.meta` 记录 `downloadedBytes` + `totalBytes` + `digest` |
| Range 请求 | `Range: bytes=N-` header → Gitea/nginx 返 206 Partial Content + `Content-Range` |
| 写入策略 | 1 MB buffer,append 到 `cacheDir/icespirit-tts-engine.apk.partial`;每次写入后更新 `.meta`(fsync every 5s) |
| ETag 校验 | Gitea release asset `digest` 字段(git blob SHA)变更 → 弃用 `.partial` 重新下 |
| 单流 vs 多流 | v1 单流(~40s @ 5MB/s);多流 Range 后续版本 |
| 并发互斥 | Mutex 保护同一文件,double-tap 第二次 no-op |
| 取消 | long-press 1.5s → 删 `.partial` + `.meta` → 返 Idle |
| 失败重试 | 按钮回 Idle 可重试(每次重试走完整 1-8) |
| `.gitignore` | **无需新增**:`cacheDir` 本就在 `/data/data/<pkg>/cache/`,git 永不见;临时文件不污染工作树 |

### 8.4 安装触发

```kotlin
val apkFile = File(context.cacheDir, "icespirit-tts-engine.apk")
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
}
context.startActivity(intent)
```

`AndroidManifest.xml` 注册 `FileProvider`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths"/>
</provider>
```

`res/xml/file_paths.xml`:

```xml
<paths>
    <cache-path name="tts_engine_apk" path="."/>
</paths>
```

### 8.5 安装后的引擎枚举

```
install 完成 → 用户在系统弹窗点 "安装"
  → PackageManager 装上 com.icespiritai.tts.engine
  → TtsEnginePickerScreen 检测 installedPackages 包含该 pkg
  → 重新 supportedChineseEngines() → 引擎列表多出 "冰灵 TTS"
  → UI refresh,默认选中 "冰灵 TTS"
  → 返回 HomeScreen → HomeTopBar 朗读按钮 enabled
```

---

## 9. 降级策略 — 永不降级到英文朗读

**核心原则**(feedback-ad-law-no-gray-area 同源):**永远不静默失败,永远不切到错误状态(英文朗读)的"灰色出口"**。

```
speak(text):
  if !setting.enabled                       → no-op (mode=Disabled)
  if state.mode == InitFailed               → no-op + Logcat debug
  if state.mode == Disabled                 → no-op

  val status = engine.setLanguage(zh-CN)
  if status >= LANG_AVAILABLE:
    engine.speak(text, onDone = { _mode = Idle })
    _mode = Speaking
    return

  // 中文不可用 — 不切其他 locale,直接停
  Logcat warn "TTS engine $pkg lost zh-CN support"
  _mode = InitFailed
  // UI 显示:HomeTopBar 按钮 disable + a11y 提示用户去 Settings 看 empty state
```

用户行动路径(InitFailed 时):

1. HomeTopBar 朗读按钮 disable,a11y desc "朗读功能不可用,设置中查看详情"
2. 点 settings gear → "语音播报" → "引擎" → 进入 TtsEnginePickerScreen
3. 看到 Empty state:"未找到中文 TTS 引擎"
4. 走方案 1(系统设置启用)或方案 2(下载冰灵 TTS)
5. 装完返回 HomeScreen → 按钮 enabled

---

## 10. Error handling matrix

| 场景 | 触发 | state | UI | 恢复 |
|---|---|---|---|---|
| `OnInitListener.onInit != SUCCESS` | engine init 失败 | InitFailed | 按钮 disable,a11y 提示 | 重启 app / 切引擎 |
| `setLanguage(zh-CN) < LANG_AVAILABLE` | 引擎中文包被禁用 | InitFailed | 同上 | 在系统 TTS 设置重新下载中文包 |
| `speak()` 在 Disabled | setting.enabled=false | 不变 | 不响应点击 | 在 Settings 开启 |
| `speak()` 在 InitFailed | — | 不变 | 不响应点击 | 修引擎或下载冰灵 TTS |
| `speak()` 在 state=Loading | VM 状态切换 | auto stop | 按钮灰禁 | 等识别完成 |
| Tap 打断正在 speak | — | Speaking→Idle | icon swap | — |
| Gitea API 非 2xx | 服务器挂 / 网络断 | Failed("下载失败") | 重试按钮 | 重试走完整流程 |
| API JSON 缺 assets[0].sha256 | 上传配置错 | Failed("下载源配置错误") | 重试 | 上传方修 |
| Range 请求非 206 | 服务器不支持 Range(理论 Gitea 全支持) | Failed | 删 .partial + .meta,重试 | 自动恢复 |
| ETag(`digest`)变更 | Gitea release 文件被替换 | Failed | 删 .partial + .meta | 自动从头下 |
| sha256 mismatch | corruption / 投毒 | Failed("校验失败") | 删 .partial + .meta | 重试 |
| 下载中磁盘满 | IOException ENOSPC | Failed("存储空间不足") | 不删 .partial | 用户清空间后续传 |
| 下载中网络断 | SocketTimeoutException | Failed("网络中断") | 显示已下载字节 | 重试自动续传 |
| Mutex 失败 | 用户 double-tap | no-op | 第二次点击不响应 | 第一次完成按钮自动恢复 |
| long-press 1.5s 取消 | 用户主动取消 | Idle | 删 .partial + .meta | 重新点下载 |
| `ActivityNotFoundException` | 设备没装 package installer | Failed | 重试 | 重试 |
| 未知来源未授权 | 系统拦截 | Idle | Snackbar "请在系统设置中允许安装未知来源" | 用户授权后重试 |
| 用户在安装弹窗点取消 | — | Idle | 按钮回 "下载引擎" | 重试 |
| 安装后 `tts.engines` 不含该 pkg | 系统索引延迟 | Failed("引擎未识别") | 重试 | 重新 enumerate |
| DataStore 损坏 | prefs 文件破坏 | default setting | Logcat warn | 重启自动恢复 |
| Setting saved enginePackage 但引擎被卸 | 用户后续手动卸载 | fallback to null | Logcat warn,写回 DataStore | 自动恢复 |
| App 进程被杀(下载中) | OOM / 系统回收 | — | .meta 持久化 | 重启走续传 |
| Activity 销毁 | onDestroy | engine.shutdown() | — | 下次 onCreate 重建 |

---

## 11. Testing strategy

### 11.1 Unit (JVM, `app/src/test/java/.../tts/`)

| Test class | 覆盖 |
|---|---|
| `ScriptBuilderTest` | 8 cases:空 hits / 1 hit / 多 hits 按 severityRank 排序 / matchedText 含标点处理 / 含换行 / 含中英混排 / 极长文本截断(> 500 字) |
| `TtsControllerTest` | fake `TtsEngine`:speak→Speaking / onDone→Idle / stop during Speaking / setEnabled(false)→Disabled 拒绝 speak / setEngine 转发 / 状态机循环 |
| `TtsSettingTest` | DataStore 序列化 round-trip / 默认值 / null enginePackage |
| `TtsEngineInstallerTest` | mock HttpURLConnection:.meta 写入读取 / Range header 验证 / sha256 mismatch 删 .partial / ETag 变更重新下 / Mutex 互斥 / long-press 取消清理 |
| `SupportedChineseEnginesTest` | fake `TextToSpeech`:engines=[HiVoice(可用), Google TTS(不可用)] → 过滤剩 HiVoice |

### 11.2 Robolectric (sdk=33, `app/src/test/java/.../ui/`)

| Test class | 覆盖 |
|---|---|
| `HomeTopBarTtsTest` | 5 cases × 4 state 矩阵:button 存在性 / icon / enabled / accent border / a11y contentDescription / Crossfade targetState |
| `HomeTopBarTtsVisibilityTest` | setting.enabled=false → button 不渲染;enabled=true + state≠Complete → 灰禁;state==Complete → enabled |
| `TtsEnginePickerScreenTest` | engines=[a,b] → 2 radio row;empty → empty state + 下载按钮可见;radio tap 写 `ttsEnginePackage` |
| `TtsEmptyStateDownloadButtonTest` | tap → 触发 installer 协程,显示进度;long-press 取消 |
| `SettingsScreenTtsSectionTest` | 总开关 toggle → setEnabled(false) → 引擎 row 消失 |

### 11.3 Instrumented (真机 nova 6, sdk=35)

| Test class | 覆盖 |
|---|---|
| `AndroidTtsEngineInitTest` | `OnInitListener.onInit(SUCCESS)` / locale `>= LANG_AVAILABLE` / ≥1 engine available / 装冰灵 APK 后 `tts.engines` 多出 "冰灵 TTS" |
| `AndroidTtsEngineSpeakTest` | `speak("测试")` 不抛异常 / `UtteranceProgressListener.onDone` 触发(走冰灵 APK) |
| `TtsEngineInstallerE2ETest` | full flow:download → sha256 verify → FileProvider install → 系统弹窗 → 装完 → 引擎出现 → speak 一句中文 |
| `TtsEngineInstallerResumeTest` | 模拟下载 50% 后杀进程 → 重启 → verify Range header(`bytes=N-`)→ 续传成功 |
| `HomeScreenTtsE2ETest` | 识别完成 → tap 朗读按钮 → state=Speaking → tap → state=Idle;Loading 期间按钮 disable |

Logcat TAG = `IceSpiritTtsE2E`,仿 `Audit71E2E` pattern。

### 11.4 A11y

- `HomeTopBarTtsA11yTest` — 4 state 下 `contentDescription` 分别为:
  - enabled=true, state≠Complete → "朗读,当前无可朗读结果"
  - state=Complete, mode=Idle → "朗读识别结果"
  - state=Complete, mode=Speaking → "停止朗读"
  - mode=InitFailed → "朗读功能不可用,设置中查看详情"
- TalkBack focus order:TopBar 从右到左(TTS → Settings),符合 LTR 习惯

### 11.5 Visual regression

`app/src/androidTest/assets/visual-audit/tts/{before,after}/` scaffold(仿 editorial-redesign pattern commit `fc840a1`):
- 3 fixture:hasHits=true (Complete + speaker icon) / hasHits=false (Complete, fallback 朗读) / Speaking (stop icon)
- README.md 指向本 spec

---

## 12. CLAUDE.md 约束保持

- 不改 CaptureBar / ResultPanel / HitCard / StatusBanner / Viewer / ViewerTopBar / 规则库 / OCR / 状态机 / 导出
- 不 bump 发版号(`feedback-release-hygiene.md`)— 引擎 APK 是独立版本号 `1.0.0`,不与 vision APK 联动
- 新 strings 进 `strings.xml`,a11y desc 单独 key
- 不在 `app/libs/*.aar` 放新东西(sherpa-onnx 在独立引擎 APK,不在 vision APK)
- 新工具脚本 `tools/build-icespirit-tts-engine.sh` + `.sh.example`(独立引擎 APK 打包脚本,仿 `tools/build-ppocr-sdk.sh`)
- 不在主仓提交 `icespirit-tts-engine.apk`(在 `.gitignore`)
- 作者 `AlexMultiAgent <zhangven@gmail.com>`,无 `Co-Authored-By:` trailer(post-tool-use hook 拦截)
- 新增 dot file 权限:`tools/build-icespirit-tts-engine.sh` +x
- 私有不入仓:`gradle.token.properties` / `~/.gradle/gradle.properties` / `local.properties` — 已 `.gitignore`,不需新加

---

## 13. 文件清单(vision APK)

**新建**(`app/src/main/java/com/icespiritai/offline/tts/`):

- `TtsEngine.kt`
- `AndroidTtsEngine.kt`
- `TtsController.kt`
- `TtsState.kt`
- `TtsSetting.kt`
- `TtsSettingRepository.kt`
- `TtsEngineInstaller.kt`
- `SupportedChineseEngines.kt`
- `LocalTtsController.kt`
- `ScriptBuilder.kt`

**新建**(`app/src/main/java/com/icespiritai/offline/ui/common/`):

- `DisclaimerDialog.kt`(§6.4 首次启动免责声明对话框,Material3 AlertDialog + 一次性 DataStore)

**新建**(`app/src/test/java/com/icespiritai/offline/ui/common/`):

- `DisclaimerDialogTest.kt`(Robolectric,4 用例:首次显示 / 点 ack 写时间戳 / 二次启动不显示 / back 键不关)

**新建**(`app/src/test/java/com/icespiritai/offline/tts/`):

- `ScriptBuilderTest.kt`
- `TtsControllerTest.kt`
- `TtsSettingTest.kt`
- `TtsEngineInstallerTest.kt`
- `SupportedChineseEnginesTest.kt`

**新建**(`app/src/test/java/com/icespiritai/offline/ui/`):

- `HomeTopBarTtsTest.kt`
- `HomeTopBarTtsVisibilityTest.kt`
- `TtsEnginePickerScreenTest.kt`
- `TtsEmptyStateDownloadButtonTest.kt`
- `SettingsScreenTtsSectionTest.kt`
- `HomeTopBarTtsA11yTest.kt`

**新建**(`app/src/androidTest/java/com/icespiritai/offline/tts/`):

- `AndroidTtsEngineInitTest.kt`
- `AndroidTtsEngineSpeakTest.kt`
- `TtsEngineInstallerE2ETest.kt`
- `TtsEngineInstallerResumeTest.kt`
- `HomeScreenTtsE2ETest.kt`

**新建**(`app/src/main/res/xml/`):

- `file_paths.xml`

**新建**(`app/src/androidTest/assets/visual-audit/tts/`):

- `before/README.md`, `after/README.md`, fixture 3 张(占位 PNG)

**新建**(`tools/`):

- `build-icespirit-tts-engine.sh`(独立引擎 APK 打包脚本)
- `build-icespirit-tts-engine.sh.example`(模板)
- `download-matcha-zh-baker.sh`(从 Gitea 下模型到本地,给 build 脚本消费)

**修改**:

- `app/src/main/java/com/icespiritai/offline/AppGraph.kt` — 新增 `ttsController: TtsController by lazy { ... }`
- `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt` — `IceSpiritOfflineTheme` 新增 `LocalTtsController` provider
- `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt` — 新增 `ttsState` / `onSpeakToggle` 参数
- `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` — 把 `ttsState` 喂给 HomeTopBar
- `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt` — 新增 "语音播报" section
- `app/src/main/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreen.kt` — 新建(整个 screen)
- `app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt` — 新增 `Routes.TTS_ENGINE_PICKER` 路由 + transition
- `app/src/main/java/com/icespiritai/offline/ui/nav/Routes.kt` — 新增 `TTS_ENGINE_PICKER` const
- `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt` — 新增 `ttsEnabled` + `ttsEnginePackage` + `disclaimerAcceptedAt: Flow<Long?>` + `acceptDisclaimer()` + DataStore key
- `app/src/main/java/com/icespiritai/offline/MainActivity.kt` — `onCreate` 顶层订阅 `disclaimerAcceptedAt.first() == null` 触发 DisclaimerDialog 显示
- `app/src/main/res/values/strings.xml` — 新增 ~10 个 TTS key + `tts_disclaimer_title` + `tts_disclaimer_body_*`(3 段)+ `tts_disclaimer_ack`
- `app/src/main/AndroidManifest.xml` — 新增 FileProvider 注册
- `app/build.gradle.kts` — 无新依赖(FileProvider 已由 androidx.core 提供,代码已存在)
- `.gitignore` — **无新增**(cacheDir 文件不入 git)

**文件总数**(概数,以最终 plan 任务清单为准):新建 ~30 个文件(test + source + androidTest + tools + visual-audit scaffold)+ 修改 ~10 个 vision APK 文件 + 独立引擎 APK 工程 1 个。

---

## 14. 独立引擎 APK 工程结构(独立仓库或 monorepo 子目录)

```
icespirit-tts-engine/                       (独立 Gradle project)
├── settings.gradle.kts
├── build.gradle.kts                         (Application plugin + sherpa-onnx + onnxruntime-android)
├── gradle.properties
├── gradle/wrapper/...
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/icespiritai/tts/engine/
│   │   ├── IceSpiritTtsService.kt
│   │   └── IceSpiritTtsEngine.kt
│   ├── res/values/strings.xml
│   └── assets/models/
│       ├── tokens.txt
│       ├── configuration.json
│       ├── model-steps-3.onnx
│       └── vocos-22khz-univ.onnx
└── tools/
    └── build.sh                              (本地打包脚本)
```

**Build 依赖**(`build.gradle.kts` 占位 — **精确坐标由 plan-writing 阶段到 sherpa-onnx 官网核实**):

```kotlin
dependencies {
    // sherpa-onnx Android AAR — 见 https://k2-fsa.github.io/sherpa/onnx/install.html
    // 坐标待 plan 阶段确认(可能为 com.k2fsa.sherpa-onnx:sherpa-onnx-android:1.13.5 等)
    implementation("com.k2fsa.sherpa-onnx:<artifact>:<version>")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:<version>")
    implementation("androidx.core:core-ktx:<version>")
}
```

**仓库位置**(待协调):
- 方案 A:独立 Git 仓库 `giteaadmin/icespirit-tts-engine`,发版 push APK + release tag
- 方案 B:作为本仓 monorepo 子目录(`engine/`),发版走子模块 — **更复杂,不推荐**
- 方案 C:`giteaadmin/Model` 仓库新增 release tag,源代码与 vision 同仓(根 `engine/` 子目录)— **本 spec 默认选 C,需与 vision 仓 push 流程协调**

**选 方案 C 的理由**:用户原始诉求明确指向 `giteaadmin/Model/releases`,沿用该仓库的下载 URL 形态(`/releases/download/<tag>/<file>.apk`)与 vision-app 发布仓库模式一致(`docs/knowledge/gitea-1.22x-release-route-broken.md` 描述的健康下载模式)。引擎 APK 源代码以 `engine/` 子目录形式与 vision 同仓(根目录有 `app/` + `engine/` 两个 Gradle 项目),打包脚本 `tools/build-icespirit-tts-engine.sh` 产出 APK 上传到 `giteaadmin/Model` 仓库。

---

## 15. Smoke / e2e 验收

`docs/smoke/2026-09-08-vision-tts-v0.1.X+4-e2e.md`(由 plan 触发写作):

1. JDK 17 / 真机 nova 6 / `modelProfile=ice_ocr_rules`(不变)
2. `bash tools/build-icespirit-tts-engine.sh` 跑通 → 产出 `icespirit-tts-engine.apk`(~150MB)
3. 上传到 `giteaadmin/Model/releases/tag/icespirit-tts-engine-v1.0.0` + 算 sha256 + 填 release notes
4. `./gradlew.bat testDebugUnitTest` 全绿(预计 +30 tests)
5. 真机 e2e 流程:
   - 卸载冰灵 TTS(若有)
   - 启动 vision app → HomeTopBar 朗读按钮灰禁
   - 进 Settings → 语音播报 → 引擎 → 看到 Empty state
   - 点 "下载冰灵中文 TTS 引擎" → 看进度条 0→100%
   - 自动跳系统安装弹窗 → 点 "安装"
   - 系统返回 → picker 自动 refresh,出现 "冰灵 TTS"
   - 选 "冰灵 TTS" → 退出 Settings
   - HomeTopBar 朗读按钮 enabled
   - 拍照识别一张有命中图(用 audit71 fixture 67 蟹都汇)
   - tap 朗读按钮 → icon swap → 听发音(命中违规:100% 中国排名第一...)
   - tap → 停止
   - 拍一张无命中图(用 audit71 fixture 122 之类的) → tap → 听"未筛查出违规事项,AI识别仅供参考"
   - **断点续传测试**:删除冰灵 TTS,下载引擎 APK,下到 30% 杀进程 → 重启 → 自动续传到 100%

---

## 16. Open questions(给 plan 写作前确认)

1. **引擎 APK 工程位置**:选 §14 方案 A/B/C 中的哪一个?需与 vision 仓所有者确认 push 流程
2. **`tools/build-icespirit-tts-engine.sh` 触发者**:CI 自动跑还是本地手动跑?(本地手动可避免 CI 误推 ~150MB asset)
3. **冰灵 TTS 引擎 APK 的 Gitea PAT 拥有者**:谁有 push 权限到 `giteaadmin/Model`?(CLAUDE.md `feedback-release-hygiene` 提示凭据已 gitignored,需手动 stage)
4. **是否需要在冰灵 TTS APK 内支持拼音 / 多音字 / 数字读法**:matcha-zh-baker 是 TTS 模型,无内置文本正则化。是否要预 ITN(inverse text normalization)?v1 不做,留后续
5. **CLAUDE.md 是否需要补"vision + 引擎 APK 双仓"工作流章节**:等 plan 落地后再补
6. **sherpa-onnx Android AAR 的精确 Maven 坐标**:版本、group/artifact id 需 plan 阶段去 k2-fsa 官方文档核实,不在 spec 阶段锁死
7. **断点续传测试的真机可行性**:nova 6 网络限速下是否能可靠触发 50% 杀进程场景?若不可靠,改用 `mockk` / `Robolectric` 模拟 Range 行为
8. **首次启动免责对话框 v1 落地** ✅ resolved 2026-09-08:在 `MainActivity.onCreate` 一次性弹 `AlertDialog`(`setCancelable(false).setPositiveButton("我了解")`),记录到 DataStore `disclaimerAcceptedAt: Long?` 仅弹一次。详见 §6.4。

---

## 17. Plan 链接

待写作 `docs/superpowers/plans/2026-09-08-vision-tts-playback.md`(由 writing-plans skill 产出)。
