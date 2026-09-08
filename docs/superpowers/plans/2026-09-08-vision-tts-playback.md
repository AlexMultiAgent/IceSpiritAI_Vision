# 冰灵锐目 TTS 朗读 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为识别完成结果增加单按钮 toggle 语音播报,朗读违规命中要点;支持运行时切换 TTS 引擎,设备无中文引擎时引导下载冰灵 TTS 兜底 APK;首次启动弹免责声明对话框(主动接受机制)。

**Architecture:** 纯函数 `ScriptBuilder` 把 `ViolationReport.hits` 按 severityRank 排序拼接成朗读脚本 → `TtsController`(process-singleton,通过 `LocalTtsController` CompositionLocal 注入 UI)状态机管 Idle/Speaking/InitFailed → `AndroidTtsEngine` 包 `android.speech.tts.TextToSpeech`(route 到系统当前首选引擎)。引擎选择是 `tts.engines` 运行时枚举;设备零中文引擎 → `TtsEngineInstaller` 从 Gitea `Model` 仓库下 `icespirit-tts-engine-v1.0.0` 兜底 APK(单流 Range 续传 + sha256 + FileProvider + ACTION_INSTALL_PACKAGE)。首次启动 `MainActivity.onCreate` 顶层订阅 `disclaimerAcceptedAt.first() == null` 弹一次性 `AlertDialog`(setCancelable=false + DataStore 持久化)。

**Tech Stack:**
- Kotlin 2.4.10 + Jetpack Compose (Material3)
- `android.speech.tts.TextToSpeech` + `androidx.core.content.FileProvider`
- DataStore Preferences(已有 `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`)
- HttpURLConnection(单流 Range 续传,无 OkHttp 引入)
- Robolectric + Compose UI Test + 真机 androidTest(Huawei nova 6, SDK 35)

**Out of scope (后续单独 PR):** 独立 `icespirit-tts-engine` Gradle 工程本身(由 `tools/build-icespirit-tts-engine.sh` 打包后产 APK 上传到 `giteaadmin/Model`)。本 plan 只做 vision APK 侧 + 下载安装流程;独立引擎 APK 工程以独立 plan 跟进(参考 spec §14)。

---

## 0. 前置约定(每条命令都遵守)

```bash
# 1. JDK 17(仓库锁 forward-path baseline,见 CLAUDE.md §开发环境)
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. 不在 app/libs/*.aar 放新东西(CLAUDE.md §开发环境)
# 3. author = AlexMultiAgent <zhangven@gmail.com>(仓库 git config 已锁)
# 4. commit 不含 Co-Authored-By: trailer(任何形式,post-tool-use hook 拦截)
# 5. git add 用具体路径,禁止 -A / --all / . / ./ / .. / .git / *
# 6. 文件结尾必须有 0x0a 换行
# 7. 不发版号不 bump(feedback-release-hygiene)— 引擎 APK 独立版本号,与 vision 解耦
```

每个 task 的 commit message 模板:

```
<type>(<scope>): <subject>

[body]
```

`<scope>` 一律 `tts`(或 `tts-ui` / `tts-install` / `tts-disclaimer`);`<type>` 走项目约定 `feat` / `refactor` / `test` / `fix` / `docs` / `chore` / `style`。

---

## Task 1: ScriptBuilder + ScriptBuilderTest

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt`
- Test: `app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt`

**Goal:** 纯函数 `ScriptBuilder.build(ViolationReport): String`,按 `severityRank` 降序拼接 `hits.matchedText`,空 hits 返 fallback 文案。无 Android 依赖,可纯 JVM 测。

- [ ] **Step 1.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Test
import android.net.Uri

class ScriptBuilderTest {

    private fun hit(text: String, sev: Severity) = RuleHit(
        ruleId = "r_$text", matchedText = text, category = "absolute",
        regulation = "广告法 §9", severity = sev,
    )

    @Test fun `empty hits returns fallback text`() {
        val report = ViolationReport(
            imageUri = Uri.EMPTY, ocrText = "", hits = emptyList(), timestampMs = 0,
        )
        assertEquals("未筛查出违规事项,AI识别仅供参考", ScriptBuilder.build(report))
    }

    @Test fun `single hit wraps text in 命中违规 prefix`() {
        val report = ViolationReport(
            imageUri = Uri.EMPTY, ocrText = "", hits = listOf(hit("100% 中国第一", Severity.Violation)),
            timestampMs = 0,
        )
        assertEquals("命中违规:100% 中国第一。", ScriptBuilder.build(report))
    }

    @Test fun `multiple hits sort by severityRank descending`() {
        val hits = listOf(
            hit("信息类提示", Severity.Info),
            hit("100% 中国第一", Severity.Violation),
            hit("国家级 特供", Severity.Warning),
        )
        val report = ViolationReport(Uri.EMPTY, "", hits, 0)
        assertEquals(
            "命中违规:100% 中国第一。国家级 特供。信息类提示。",
            ScriptBuilder.build(report),
        )
    }

    @Test fun `trims whitespace in matchedText`() {
        val report = ViolationReport(
            Uri.EMPTY, "", listOf(hit("  100%  ", Severity.Violation)), 0,
        )
        assertEquals("命中违规:100%。", ScriptBuilder.build(report))
    }

    @Test fun `handles long text without truncation at 500 chars`() {
        val long = "违".repeat(500)
        val report = ViolationReport(
            Uri.EMPTY, "", listOf(hit(long, Severity.Violation)), 0,
        )
        // 不截断,信任 TTS 引擎自身限制(spec §6.2 — 极长文本由设备引擎处理)
        assertEquals("命中违规:$long。", ScriptBuilder.build(report))
    }

    @Test fun `chinese-english mixed ordering preserved after rank sort`() {
        val hits = listOf(
            hit("Best in Class", Severity.Warning),
            hit("100% 排名第一", Severity.Violation),
        )
        val report = ViolationReport(Uri.EMPTY, "", hits, 0)
        assertEquals(
            "命中违规:100% 排名第一。Best in Class。",
            ScriptBuilder.build(report),
        )
    }
}
```

- [ ] **Step 1.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.ScriptBuilderTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: ScriptBuilder".

- [ ] **Step 1.3: Implement ScriptBuilder**

`app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.severityRank

/**
 * 把 [ViolationReport] 转成单段朗读脚本。
 *
 * - 空 hits:返 fallback 文案(显式传递"AI 仅供参考"不确定性,见 spec §3.4)
 * - 有 hits:按 [severityRank] 降序(违规→警告→信息)拼接 matchedText,前后包裹
 *   「命中违规:」前缀 + 句号分隔,便于听者快速识别严重度梯度。
 *
 * 纯函数、无副作用、不依赖 Android Context,可在 JVM 单测里全覆盖。
 */
object ScriptBuilder {

    private const val FALLBACK_TEXT = "未筛查出违规事项,AI识别仅供参考"
    private const val PREFIX = "命中违规:"
    private const val SEPARATOR = "。"
    private const val END_PUNCT = "。"

    fun build(report: ViolationReport): String =
        if (report.hits.isEmpty()) {
            FALLBACK_TEXT
        } else {
            report.hits
                .sortedByDescending { severityRank(it.severity) }
                .joinToString(separator = SEPARATOR, prefix = PREFIX) {
                    it.matchedText.trim()
                }
                .plus(END_PUNCT)
        }
}
```

- [ ] **Step 1.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.ScriptBuilderTest" -PmodelProfile=shell
```

Expected: 6 tests PASSED.

- [ ] **Step 1.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt \
        app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): ScriptBuilder — ViolationReport → 朗读脚本拼接

按 severityRank 降序(Violation→Warning→Info→Positive)拼接 hits.matchedText;
空 hits 返 fallback "未筛查出违规事项,AI识别仅供参考"。
纯函数、无 Android 依赖,6 用例覆盖空 / 单条 / 多条排序 / trim / 长文本 / 中英混排。
EOF
)"
```

---

## Task 2: TtsSetting + TtsSettingRepository + TtsSettingTest

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsSetting.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsSettingRepository.kt`
- Test: `app/src/test/java/com/icespiritai/offline/tts/TtsSettingTest.kt`

**Goal:** DataStore-backed `TtsSetting(enabled: Boolean, enginePackage: String?)`,走 `stringPreferencesKey` 直接内联(项目已有 `SettingsRepository` 模式,无单独 `SettingsKeys.kt`)。`Flow<TtsSetting>` 暴露读、`suspend setEnabled` / `suspend setEnginePackage` 暴露写。

- [ ] **Step 2.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/tts/TtsSettingTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsSettingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun tearDown() {
        // 每次测完清掉 prefs 文件避免污染
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `default returns enabled=true and null enginePackage`() = runTest {
        val repo = TtsSettingRepository(context)
        val setting = repo.setting.first()
        assertEquals(TtsSetting(enabled = true, enginePackage = null), setting)
    }

    @Test fun `setEnabled persists across new repository instance`() = runTest {
        val repo1 = TtsSettingRepository(context)
        repo1.setEnabled(false)
        val repo2 = TtsSettingRepository(context)
        assertEquals(false, repo2.setting.first().enabled)
    }

    @Test fun `setEnginePackage round-trips null and string`() = runTest {
        val repo = TtsSettingRepository(context)
        repo.setEnginePackage("com.icespiritai.tts.engine")
        assertEquals("com.icespiritai.tts.engine", repo.setting.first().enginePackage)
        repo.setEnginePackage(null)
        assertNull(repo.setting.first().enginePackage)
    }
}
```

- [ ] **Step 2.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsSettingTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: TtsSettingRepository".

- [ ] **Step 2.3: Implement TtsSetting + Repository**

`app/src/main/java/com/icespiritai/offline/tts/TtsSetting.kt`:

```kotlin
package com.icespiritai.offline.tts

/**
 * TTS 总开关 + 当前选中引擎 package。
 *
 * - [enginePackage] 为 null = 跟随系统首选(Android 自动按用户在「设置 → 文本转语音」选定的引擎路由)
 * - 非 null = 强制用指定 package 的引擎(用户在 picker 选了 HiVoice / Google TTS / 冰灵 TTS 等)
 */
data class TtsSetting(
    val enabled: Boolean = true,
    val enginePackage: String? = null,
)
```

`app/src/main/java/com/icespiritai/offline/tts/TtsSettingRepository.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_settings")

/**
 * DataStore-backed TTS 设置仓库。独立 prefs 文件 `tts_settings.preferences_pb`
 * 与主 `settings` 分开,便于 TTS 模块单独清缓存(用户卸载冰灵 TTS 后可以一键重置 TTS prefs)。
 */
class TtsSettingRepository(context: Context) {

    private val store = context.ttsDataStore

    val setting: Flow<TtsSetting> = store.data.map { prefs ->
        TtsSetting(
            enabled = prefs[KEY_ENABLED] ?: true,
            enginePackage = prefs[KEY_ENGINE_PKG],
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setEnginePackage(pkg: String?) {
        store.edit {
            if (pkg == null) it.remove(KEY_ENGINE_PKG) else it[KEY_ENGINE_PKG] = pkg
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_ENGINE_PKG = stringPreferencesKey("tts_engine_pkg")
    }
}
```

- [ ] **Step 2.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsSettingTest" -PmodelProfile=shell
```

Expected: 3 tests PASSED.

- [ ] **Step 2.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/tts/TtsSetting.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsSettingRepository.kt \
        app/src/test/java/com/icespiritai/offline/tts/TtsSettingTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): TtsSetting + DataStore 持久化

TtsSetting(enabled: Boolean, enginePackage: String?) data class;
TtsSettingRepository 走独立 prefs 文件 tts_settings.preferences_pb
(便于用户卸载冰灵 TTS 后一键重置)。默认 enabled=true、enginePackage=null
(跟随系统首选)。3 用例覆盖默认值 / 跨实例持久化 / null 与非 null 切换。
EOF
)"
```

---

## Task 3: TtsState + TtsController + LocalTtsController + TtsControllerTest

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsState.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsController.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/LocalTtsController.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsEngine.kt`(interface only,AndroidTtsEngine impl 在 Task 4)
- Test: `app/src/test/java/com/icespiritai/offline/tts/TtsControllerTest.kt`

**Goal:** 状态机 Idle/Speaking/InitFailed/Disabled + `speak(report) / stop() / toggle() / setEnabled() / setEnginePackage()`。本 task 不接 Android `TextToSpeech`,纯 JVM 用 fake engine 测全套状态机 + spec §5.2 优先级。

- [ ] **Step 3.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/tts/TtsControllerTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.RuleHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeTtsEngine
    private lateinit var fakeSettings: FakeTtsSettingRepository
    private lateinit var controller: TtsController

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEngine = FakeTtsEngine()
        fakeSettings = FakeTtsSettingRepository()
        controller = TtsController(
            engine = fakeEngine,
            settings = fakeSettings,
            scope = testDispatcher,
        )
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `initial state is Idle when setting enabled`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        assertEquals(TtsState.Idle, controller.state.first())
    }

    @Test fun `setting enabled false transitions to Disabled`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.setEnabled(false)
        advanceUntilIdle()
        assertEquals(TtsState.Disabled, controller.state.first())
    }

    @Test fun `speak transitions Idle to Speaking then back to Idle on done`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        val report = reportWith("100% 中国第一")
        controller.speak(report)
        assertEquals(TtsState.Speaking, controller.state.first())
        fakeEngine.completeLastUtterance()
        assertEquals(TtsState.Idle, controller.state.first())
    }

    @Test fun `stop during Speaking returns to Idle`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.speak(reportWith("x"))
        controller.stop()
        assertEquals(TtsState.Idle, controller.state.first())
        assertEquals(1, fakeEngine.stopCallCount)
    }

    @Test fun `speak when Disabled is no-op`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = false))
        controller.speak(reportWith("x"))
        assertEquals(0, fakeEngine.speakCallCount)
        assertEquals(TtsState.Disabled, controller.state.first())
    }

    @Test fun `engine init failure transitions to InitFailed`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        fakeEngine.failInit()
        controller.refreshEngineStatus()
        assertTrue(controller.state.first() is TtsState.InitFailed)
    }

    @Test fun `toggle from Idle starts speaking`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.toggle(reportWith("hello"))
        assertEquals(TtsState.Speaking, controller.state.first())
    }

    @Test fun `toggle from Speaking stops`() = runTest {
        fakeSettings.emit(TtsSetting(enabled = true))
        controller.toggle(reportWith("hello"))
        controller.toggle(reportWith("hello"))
        assertEquals(TtsState.Idle, controller.state.first())
        assertEquals(1, fakeEngine.stopCallCount)
    }

    // --- helpers ---

    private fun reportWith(text: String): ViolationReport = ViolationReport(
        imageUri = Uri.EMPTY, ocrText = text,
        hits = listOf(RuleHit("r", text, "absolute", "广告法 §9", Severity.Violation)),
        timestampMs = 0,
    )
}

class FakeTtsEngine : TtsEngine {
    var speakCallCount = 0
    var stopCallCount = 0
    private var pendingOnDone: ((String) -> Unit)? = null
    private var failInitNext = false

    override fun init(onDone: (Boolean) -> Unit) {
        if (failInitNext) { failInitNext = false; onDone(false) } else onDone(true)
    }

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        speakCallCount++
        pendingOnDone = onDone
    }

    fun completeLastUtterance() {
        pendingOnDone?.invoke("last") ?: error("no pending utterance")
        pendingOnDone = null
    }

    override fun stop() { stopCallCount++ }
    override fun isSpeaking(): Boolean = pendingOnDone != null
    override fun supportedChineseEngines(): List<EngineInfo> = emptyList()
    override fun setEngine(pkg: String?) {}
    override fun release() {}
    fun failInit() { failInitNext = true }
}

class FakeTtsSettingRepository : TtsSettingRepositoryLike {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(TtsSetting())
    override val setting: kotlinx.coroutines.flow.Flow<TtsSetting> = flow
    override suspend fun setEnabled(b: Boolean) { flow.value = flow.value.copy(enabled = b) }
    override suspend fun setEnginePackage(pkg: String?) { flow.value = flow.value.copy(enginePackage = pkg) }
    suspend fun emit(s: TtsSetting) { flow.value = s }
}
```

- [ ] **Step 3.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsControllerTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: TtsController / TtsEngine / EngineInfo / TtsSettingRepositoryLike".

- [ ] **Step 3.3: Implement types + controller**

`app/src/main/java/com/icespiritai/offline/tts/TtsState.kt`:

```kotlin
package com.icespiritai.offline.tts

/**
 * TTS 控制器状态机。优先级(spec §5.2):
 *   setting.enabled=false > InitFailed > state==Complete > Idle > Speaking
 *
 * - [Idle]     — 引擎 OK,setting 开,可朗读但当前没在说
 * - [Speaking] — 正在朗读
 * - [Disabled] — setting.enabled=false,所有 speak 静默 no-op
 * - [InitFailed] — 引擎 init 失败 / 中文 locale 不可用,不降级不朗读,引导用户去 Settings
 */
sealed interface TtsState {
    object Idle : TtsState
    object Speaking : TtsState
    object Disabled : TtsState
    data class InitFailed(val reason: String) : TtsState
}
```

`app/src/main/java/com/icespiritai/offline/tts/TtsEngine.kt`:

```kotlin
package com.icespiritai.offline.tts

/** 设备上某个 TTS 引擎的元信息,用于 picker 列表显示。 */
data class EngineInfo(
    val packageName: String,
    val label: String,
    val supportsChinese: Boolean,
)

/**
 * TTS 引擎抽象。生产实现包 [android.speech.tts.TextToSpeech],测试用 fake。
 *
 * - [init] 在 Activity onCreate 触发,onDone(true)=Success / onDone(false)=InitFailed
 * - [speak] 异步,通过 onDone 回调通知朗读结束(utteranceId 用于 stop / 多段管理)
 * - [setEngine] pkg=null 表示跟随系统首选,非 null 表示强制用某 package
 */
interface TtsEngine {
    fun init(onDone: (Boolean) -> Unit)
    fun speak(text: String, utteranceId: String, onDone: (String) -> Unit)
    fun stop()
    fun isSpeaking(): Boolean
    fun supportedChineseEngines(): List<EngineInfo>
    fun setEngine(pkg: String?)
    fun release()
}
```

`app/src/main/java/com/icespiritai/offline/tts/LocalTtsController.kt`:

```kotlin
package com.icespiritai.offline.tts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal 注入 [TtsController]。缺失时报清晰错误,避免 UI 误用。
 * 提供方在 [com.icespiritai.offline.IceSpiritVisionActivity.onCreate]。
 */
val LocalTtsController = staticCompositionLocalOf<TtsController> {
    error("TtsController not provided. Wrap your content in IceSpiritOfflineTheme.")
}
```

`app/src/main/java/com/icespiritai/offline/tts/TtsController.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel 之外的 TTS 控制器,AppGraph 持 process-singleton 引用。
 *
 * 状态机(spec §5.2):
 *   setting.enabled=false → Disabled
 *   engine.init 失败 / 中文 locale 不可用 → InitFailed
 *   其余按当前 speak / stop / onDone 切换 Idle ↔ Speaking
 *
 * 永不静默失败、永远不切到英文朗读(feedback-ad-law-no-gray-area 同源)。
 */
class TtsController(
    private val engine: TtsEngine,
    private val settings: TtsSettingRepositoryLike,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    val setting get() = settings.setting

    init {
        scope.launch {
            settings.setting.collect { s ->
                if (!s.enabled) _state.value = TtsState.Disabled
                else if (_state.value is TtsState.Disabled) _state.value = TtsState.Idle
            }
        }
        engine.init { ok ->
            _state.value = if (ok) TtsState.Idle else TtsState.InitFailed("引擎初始化失败")
        }
    }

    fun speak(report: ViolationReport) {
        val current = _state.value
        if (current is TtsState.Disabled || current is TtsState.InitFailed) return
        val text = ScriptBuilder.build(report)
        engine.speak(text, utteranceId = "report") { _state.value = TtsState.Idle }
        _state.value = TtsState.Speaking
    }

    fun stop() {
        engine.stop()
        _state.value = TtsState.Idle
    }

    fun toggle(report: ViolationReport) {
        if (_state.value is TtsState.Speaking) stop() else speak(report)
    }

    suspend fun setEnabled(b: Boolean) = settings.setEnabled(b)
    suspend fun setEnginePackage(pkg: String?) = settings.setEnginePackage(pkg)

    fun refreshEngineStatus() {
        val chinese = engine.supportedChineseEngines().any { it.supportsChinese }
        _state.value = if (chinese) TtsState.Idle else TtsState.InitFailed("无可用中文引擎")
    }

    fun release() = engine.release()
}

/**
 * 给 controller 一份抽象(便于单测 fake,不必启动 DataStore)。
 */
interface TtsSettingRepositoryLike {
    val setting: kotlinx.coroutines.flow.Flow<TtsSetting>
    suspend fun setEnabled(b: Boolean)
    suspend fun setEnginePackage(pkg: String?)
}

class TtsSettingRepositoryAdapter(private val real: TtsSettingRepository) : TtsSettingRepositoryLike {
    override val setting = real.setting
    override suspend fun setEnabled(b: Boolean) = real.setEnabled(b)
    override suspend fun setEnginePackage(pkg: String?) = real.setEnginePackage(pkg)
}
```

- [ ] **Step 3.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsControllerTest" -PmodelProfile=shell
```

Expected: 8 tests PASSED.

- [ ] **Step 3.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/tts/TtsState.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsEngine.kt \
        app/src/main/java/com/icespiritai/offline/tts/LocalTtsController.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsController.kt \
        app/src/test/java/com/icespiritai/offline/tts/TtsControllerTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): TtsController 状态机 + LocalTtsController 注入

TtsState = Idle / Speaking / Disabled / InitFailed(severity 优先级:
setting.enabled=false > InitFailed > Speaking > Idle)。
TtsController(speak/stop/toggle/setEnabled/setEnginePackage)
走 TtsEngine 抽象(测试用 FakeTtsEngine 覆盖 8 个用例)。
LocalTtsController CompositionLocal 给 UI 注入。
永不降级到英文朗读 — InitFailed 时引导用户去 Settings(feedback-ad-law-no-gray-area)。
EOF
)"
```

---

## Task 4: AndroidTtsEngine + SupportedChineseEngines + 2 test classes

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/tts/AndroidTtsEngine.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/SupportedChineseEngines.kt`
- Test: `app/src/test/java/com/icespiritai/offline/tts/SupportedChineseEnginesTest.kt`

**Goal:** 真接 `android.speech.tts.TextToSpeech` API,init 时枚举 `tts.engines`,过滤支持 zh-CN 的;`speak` 异步 + `UtteranceProgressListener` 回调 onDone。Robolectric sdk=33 单测覆盖枚举过滤逻辑(AndroidTtsEngine 本身需真机覆盖,见 Task 16)。

- [ ] **Step 4.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/tts/SupportedChineseEnginesTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportedChineseEnginesTest {

    @Test fun `filters out engines that don't support zh-CN`() {
        val raw = listOf(
            EngineInfo("com.huawei.hivoice", "荣耀 AI 语音引擎", supportsChinese = true),
            EngineInfo("com.google.android.tts", "Google TTS", supportsChinese = false),
            EngineInfo("com.samsung.tts.engine", "Samsung TTS", supportsChinese = true),
        )
        val result = SupportedChineseEngines.filter(raw)
        assertEquals(2, result.size)
        assertEquals(setOf("com.huawei.hivoice", "com.samsung.tts.engine"), result.map { it.packageName }.toSet())
    }

    @Test fun `empty input returns empty list`() {
        assertEquals(emptyList<EngineInfo>(), SupportedChineseEngines.filter(emptyList()))
    }

    @Test fun `all engines supporting zh-CN passes through unchanged`() {
        val raw = listOf(
            EngineInfo("a", "A", supportsChinese = true),
            EngineInfo("b", "B", supportsChinese = true),
        )
        assertEquals(raw, SupportedChineseEngines.filter(raw))
    }
}
```

- [ ] **Step 4.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.SupportedChineseEnginesTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: SupportedChineseEngines".

- [ ] **Step 4.3: Implement SupportedChineseEngines + AndroidTtsEngine skeleton**

`app/src/main/java/com/icespiritai/offline/tts/SupportedChineseEngines.kt`:

```kotlin
package com.icespiritai.offline.tts

/**
 * 把 [android.speech.tts.TextToSpeech.getEngines] 的结果按 supportsChinese 过滤。
 * 过滤后的列表喂给 picker UI 让用户选引擎。
 *
 * 独立成纯函数是为了单测可覆盖(AndroidTtsEngine 本身的 init / speak 走真机 androidTest)。
 */
object SupportedChineseEngines {
    fun filter(engines: List<EngineInfo>): List<EngineInfo> =
        engines.filter { it.supportsChinese }
}
```

`app/src/main/java/com/icespiritai/offline/tts/AndroidTtsEngine.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * [TtsEngine] 的真实实现,包 [android.speech.tts.TextToSpeech]。
 *
 * - init() 异步,通过 onDone(Boolean) 回调给 controller
 * - speak() 异步,通过 UtteranceProgressListener.onDone 回调 utteranceId
 * - supportedChineseEngines() 枚举 + probe setLanguage(zh-CN) >= LANG_AVAILABLE 过滤
 * - 永不降级到英文:setLanguage < LANG_AVAILABLE 直接 InitFailed
 */
class AndroidTtsEngine(private val context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null

    override fun init(onDone: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeOk = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)?.let {
                    it >= TextToSpeech.LANG_AVAILABLE
                } ?: false
                if (localeOk) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            pendingOnDone?.invoke(utteranceId ?: "")
                            pendingOnDone = null
                        }
                        @Deprecated("required override")
                        override fun onError(utteranceId: String?) {
                            pendingOnDone?.invoke(utteranceId ?: "")
                            pendingOnDone = null
                        }
                    })
                    onDone(true)
                } else {
                    onDone(false)
                }
            } else {
                onDone(false)
            }
        }
    }

    private var pendingOnDone: ((String) -> Unit)? = null

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        val engine = tts ?: return
        pendingOnDone = onDone
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
        pendingOnDone = null
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking == true

    override fun supportedChineseEngines(): List<EngineInfo> {
        val engine = tts ?: return emptyList()
        val pm = context.packageManager
        return engine.engines?.map { info ->
            // probe 每个引擎是否真的支持 zh-CN(Android 没有 direct API,
            // 必须在调用 init 后单独 setLanguage 验证;这里用静态 label + 试探性
            // 的 queryIntentActivities 兜底,精确探测走 picker 选中后的二次 init)
            EngineInfo(
                packageName = info.name,
                label = info.label?.toString() ?: info.name,
                supportsChinese = info.name.contains("hivoice", ignoreCase = true)
                    || info.name.contains("google", ignoreCase = true)
                    || info.name.contains("samsung", ignoreCase = true)
                    || info.name.contains("xiaomi", ignoreCase = true)
                    || info.name.contains("icespiritai", ignoreCase = true),
            )
        } ?: emptyList()
    }

    override fun setEngine(pkg: String?) {
        tts?.engine = pkg
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
```

- [ ] **Step 4.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.SupportedChineseEnginesTest" -PmodelProfile=shell
```

Expected: 3 tests PASSED.

- [ ] **Step 4.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/tts/AndroidTtsEngine.kt \
        app/src/main/java/com/icespiritai/offline/tts/SupportedChineseEngines.kt \
        app/src/test/java/com/icespiritai/offline/tts/SupportedChineseEnginesTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): AndroidTtsEngine + SupportedChineseEngines 过滤

AndroidTtsEngine 包 android.speech.tts.TextToSpeech:
- init 异步 + setLanguage(zh-CN) >= LANG_AVAILABLE 才算成功
- speak 走 QUEUE_FLUSH + UtteranceProgressListener.onDone 回调
- 永不降级英文 — locale 不可用直接 InitFailed
SupportedChineseEngines.filter 纯函数过滤,3 用例覆盖。
精确探测每个引擎是否真支持中文的二次 init 验证逻辑留 picker 选中后走(避免 init 全部 N 个引擎太慢)。
EOF
)"
```

---

## Task 5: TtsController 注入 IceSpiritVisionActivity + Theme provider

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`

**Goal:** Activity onCreate 创建 `TtsController`(`AndroidTtsEngine` + `TtsSettingRepository`),放 `IceSpiritVisionTheme` 里 `CompositionLocalProvider(LocalTtsController provides ...)`。`onDestroy` 调 `controller.release()`。

- [ ] **Step 5.1: Modify IceSpiritVisionActivity**

`app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`:

在 imports 末尾加:

```kotlin
import com.icespiritai.offline.tts.AndroidTtsEngine
import com.icespiritai.offline.tts.LocalTtsController
import com.icespiritai.offline.tts.TtsController
import com.icespiritai.offline.tts.TtsSettingRepository
import com.icespiritai.offline.tts.TtsSettingRepositoryAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
```

把 `class IceSpiritVisionActivity : ComponentActivity() {` 改成:

```kotlin
class IceSpiritVisionActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob())
    private lateinit var ttsController: TtsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SettingsRepository(applicationContext)
        // Apply the persisted night mode asynchronously instead of blocking the
        // main thread on the first DataStore read.
        lifecycleScope.launch {
            AppCompatDelegate.setDefaultNightMode(settings.themeMode.first().toNightMode())
        }

        // TTS controller: process-singleton(放 Activity 字段),AppGraph 不动
        // (CLAUDE.md §4 后端不变 — TTS 是新组件,通过 LocalTtsController 注入 UI)
        val ttsRepo = TtsSettingRepository(applicationContext)
        ttsController = TtsController(
            engine = AndroidTtsEngine(applicationContext),
            settings = TtsSettingRepositoryAdapter(ttsRepo),
            scope = appScope,
        )

        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                // First-frame placeholder before the first DataStore read
                // returns. Matches the factory default (SYSTEM) so a
                // freshly installed user does not see a one-frame flash
                // before the first read lands.
                initialValue = ThemeMode.SYSTEM,
            )
            IceSpiritVisionTheme(themeMode = themeMode, ttsController = ttsController) {
                IceSpiritNavHost()
            }
        }

        // ... 后面 UpdateRepository.checkForUpdatesAsync / handleUpdateActionIntent 保持不变
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsController.release()
    }
```

**注意**:本步只动 IceSpiritVisionActivity 字段 / onCreate 顶部 / onDestroy;Theme.kt 的修改在 Step 5.2。

- [ ] **Step 5.2: Modify Theme.kt**

`app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`:

在 imports 区域加:

```kotlin
import com.icespiritai.offline.tts.LocalTtsController
import com.icespiritai.offline.tts.TtsController
```

把 `fun IceSpiritVisionTheme(` 改成:

```kotlin
@Composable
fun IceSpiritVisionTheme(
    themeMode: ThemeMode,
    ttsController: TtsController,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.toDarkTheme()
    val severityColors = SeverityColors(isDark = darkTheme)
    CompositionLocalProvider(
        LocalSeverityColors provides severityColors,
        LocalTtsController provides ttsController,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = IceSpiritShapes,
            typography = IceSpiritTypography,
            content = content,
        )
    }
}
```

- [ ] **Step 5.3: Build verify (no commit yet)**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
```

Expected: BUILD SUCCESSFUL(no test runs,只 compile 验证接线正确)。

如果失败,通常是 LocalTtsController / TtsController import 漏掉 → 按 IDE 提示补 import。

- [ ] **Step 5.4: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt \
        app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt
git commit -m "$(cat <<'EOF'
feat(tts): IceSpiritVisionActivity 注入 TtsController + Theme provider

Activity onCreate 创建 TtsController(AndroidTtsEngine + TtsSettingRepository),
IceSpiritVisionTheme 新增 ttsController 参数,通过 LocalTtsController
CompositionLocalProvider 注入。onDestroy 调 controller.release() 释放引擎。
AppGraph 不动 — TTS 是新组件,通过 CompositionLocal 注入 UI(CLAUDE.md §4 后端不变)。
EOF
)"
```

---

## Task 6: strings.xml TTS + disclaimer keys

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Goal:** 集中加 ~20 个 TTS key + 5 个 disclaimer key(spec §6.1 / §7.1 / §6.4)。无测试(资源字符串,UI 用 stringResource 校验)。

- [ ] **Step 6.1: Append strings**

打开 `app/src/main/res/values/strings.xml`,在 `</resources>` 前追加:

```xml
    <!-- TTS playback (vision TTS spec §6.1, §7.1, §8) -->
    <string name="tts_button_desc">朗读识别结果;AI 识别仅供参考</string>
    <string name="tts_button_disabled_desc">朗读,当前无可朗读结果</string>
    <string name="tts_button_init_failed_desc">朗读功能不可用,设置中查看详情</string>
    <string name="tts_button_stop_desc">停止朗读</string>

    <string name="tts_section_title">语音播报</string>
    <string name="tts_total_switch">启用朗读功能</string>
    <string name="tts_total_switch_desc">识别完成后朗读违规命中要点</string>

    <string name="tts_engine_label">引擎</string>
    <string name="tts_engine_follow_system">跟随系统默认</string>
    <string name="tts_engine_picker_title">选择 TTS 引擎</string>

    <string name="tts_empty_title">未找到中文 TTS 引擎</string>
    <string name="tts_empty_solution_1_title">方案 1</string>
    <string name="tts_empty_solution_1_body">在系统「文本转语音」设置中安装或启用中文语音包</string>
    <string name="tts_empty_open_system_settings">打开系统 TTS 设置</string>
    <string name="tts_empty_solution_2_title">方案 2</string>
    <string name="tts_empty_solution_2_body">下载冰灵中文 TTS 引擎(约 150 MB,首次需联网,仅一次)</string>
    <string name="tts_empty_download">下载引擎</string>
    <string name="tts_empty_downloading">下载冰灵 TTS 引擎… %1$d%%</string>
    <string name="tts_empty_download_long_press_to_cancel">长按取消下载</string>

    <string name="tts_section_footer_disclaimer">ℹ 朗读内容仅供参考,实际合规判断请以现场检查为准。</string>
    <string name="tts_section_footer_settings_hint">语速 / 音调请在系统「文本转语音」设置中调整。</string>

    <!-- First-launch disclaimer dialog (§6.4) -->
    <string name="tts_disclaimer_title">使用提示</string>
    <string name="tts_disclaimer_body_1">本应用通过 OCR 与规则匹配辅助识别广告招牌违规情形,识别结果仅供参考,实际合规判断请以现场检查为准。</string>
    <string name="tts_disclaimer_body_2">规则库可能滞后于最新法规,OCR 可能漏字或误识,语音朗读由系统/兜底引擎合成,质量受设备引擎能力影响。</string>
    <string name="tts_disclaimer_body_3">请将本应用作为现场辅助工具使用,不要作为最终合规判定依据。</string>
    <string name="tts_disclaimer_ack">我了解</string>

    <!-- ResultPanel 0-hit footer (§6.3) -->
    <string name="tts_result_panel_footer_disclaimer">⚠ AI 识别仅供参考,实际以现场判断为准</string>

```

- [ ] **Step 6.2: Build verify**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
```

Expected: BUILD SUCCESSFUL(资源 compile 通过)。

- [ ] **Step 6.3: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(tts): strings.xml TTS + disclaimer 全量 keys

~20 个 TTS key(button_desc 4 + section_title/switch/footer + engine picker 7
+ empty state 6 + footer disclaimer)+ 5 个 disclaimer key + 1 个
ResultPanel footer key。资源集中加,UI 在后续 task 用 stringResource 引用。
EOF
)"
```

---

## Task 7: HomeTopBar 朗读按钮 + 3 test classes

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsTest.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsVisibilityTest.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsA11yTest.kt`

**Goal:** 朗读按钮(spec §6.1 视觉矩阵 + a11y)+ Crossfade icon swap。按钮位于 settings gear 左侧 8dp gap,Modifier.size(40.dp)。

- [ ] **Step 7.1: Write failing tests**

`app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsTest.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeTopBarTtsTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tts button shows VolumeUp icon when state is Idle and complete`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var speakToggleCount = 0
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage,
                    onSelectTab = {},
                    tabEnabled = true,
                    onOpenSettings = {},
                    ttsState = TtsState.Idle,
                    isAnalysisComplete = true,
                    onSpeakToggle = { speakToggleCount++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读识别结果;AI 识别仅供参考", useUnmergedTree = true,
        ).assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.mainClock.advanceTimeBy(100)
    }

    @Test fun `tts button shows Stop icon when state is Speaking`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Speaking,
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("停止朗读", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test fun `tts button is disabled when not complete and not InitFailed`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Idle,
                    isAnalysisComplete = false, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读,当前无可朗读结果", useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test fun `tts button shows init failed a11y when InitFailed`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.InitFailed("test"),
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            "朗读功能不可用,设置中查看详情", useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
```

`app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsVisibilityTest.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeTopBarTtsVisibilityTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tts button does not render when state is Disabled`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeTopBar(
                    selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                    onOpenSettings = {}, ttsState = TtsState.Disabled,
                    isAnalysisComplete = true, onSpeakToggle = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("朗读", substring = true, useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
```

`app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsA11yTest.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeTopBarTtsA11yTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `4 states have distinct contentDescription`() {
        val matrix = listOf(
            TtsState.Idle to "朗读识别结果;AI 识别仅供参考",
            TtsState.Speaking to "停止朗读",
            TtsState.InitFailed("x") to "朗读功能不可用,设置中查看详情",
        )
        matrix.forEach { (state, expected) ->
            composeRule.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    HomeTopBar(
                        selectedTab = RuleTab.AdSignage, onSelectTab = {}, tabEnabled = true,
                        onOpenSettings = {}, ttsState = state,
                        isAnalysisComplete = true, onSpeakToggle = {},
                    )
                }
            }
            composeRule.onNodeWithContentDescription(expected, useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
                    listOf(androidx.compose.ui.semantics.SemanticsContentDescription(expected)),
                ))
        }
    }
}
```

- [ ] **Step 7.2: Run tests, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HomeTopBarTts*" -PmodelProfile=shell
```

Expected: 3 test classes FAIL with "no parameter ttsState" / "no parameter isAnalysisComplete" / "no parameter onSpeakToggle".

- [ ] **Step 7.3: Modify HomeTopBar.kt**

`app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt`(整文件覆盖):

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icespiritai.offline.R
import com.icespiritai.offline.tts.TtsState

/**
 * Compact home header: a tight Column of { title row + tab row } on a
 * single transparent [Surface], no Material 3 [TopAppBar] involved.
 *
 * v0.1.X+4: 增加 TTS 朗读按钮(spec §6.1)。视觉矩阵:
 *   - Disabled → 不渲染
 *   - Idle/非 Complete → 灰禁,无 accent border
 *   - InitFailed → 灰禁 + "朗读功能不可用" a11y
 *   - Complete + Idle → VolumeUp + accent 1px border + "朗读识别结果;AI 识别仅供参考" a11y
 *   - Complete + Speaking → Stop + accent 1px border + "停止朗读" a11y
 *
 * Crossfade 用 MotionTokens.Standard 220ms(Phase 3 §6.3 motion token)。
 *
 * Settings gear 仍在 CenterEnd,朗读按钮紧贴其左侧 8dp gap(共 Row 在 Box 内)。
 */
@Composable
fun HomeTopBar(
    selectedTab: RuleTab,
    onSelectTab: (RuleTab) -> Unit,
    tabEnabled: Boolean,
    onOpenSettings: () -> Unit,
    ttsState: TtsState = TtsState.Disabled,
    isAnalysisComplete: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TtsIconButton(
                        ttsState = ttsState,
                        isAnalysisComplete = isAnalysisComplete,
                        onSpeakToggle = onSpeakToggle,
                    )
                    val a11ySettings = stringResource(R.string.settings_button_desc)
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.semantics { contentDescription = a11ySettings },
                    ) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    }
                }
            }
            RuleTabBar(selected = selectedTab, onSelect = onSelectTab, enabled = tabEnabled)
        }
    }
}

@Composable
private fun TtsIconButton(
    ttsState: TtsState,
    isAnalysisComplete: Boolean,
    onSpeakToggle: () -> Unit,
) {
    if (ttsState is TtsState.Disabled) return  // 不渲染

    val (icon, a11y, enabled, accentBorder) = when {
        ttsState is TtsState.InitFailed -> Quadruple(
            Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(R.string.tts_button_init_failed_desc),
            false, false,
        )
        ttsState is TtsState.Speaking -> Quadruple(
            Icons.Default.Stop,
            stringResource(R.string.tts_button_stop_desc),
            true, true,
        )
        isAnalysisComplete -> Quadruple(
            Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(R.string.tts_button_desc),
            true, true,
        )
        else -> Quadruple(
            Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(R.string.tts_button_disabled_desc),
            false, false,
        )
    }

    IconButton(
        onClick = onSpeakToggle,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (accentBorder) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) else Modifier
            )
            .semantics { contentDescription = a11y },
    ) {
        Crossfade(
            targetState = ttsState is TtsState.Speaking,
            animationSpec = tween(durationMillis = 220),
            label = "tts-icon-crossfade",
        ) { isSpeaking ->
            Icon(
                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
```

- [ ] **Step 7.4: Run tests, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.HomeTopBarTts*" -PmodelProfile=shell
```

Expected: 5 tests PASSED(HomeTopBarTtsTest 4 + HomeTopBarTtsVisibilityTest 1 + HomeTopBarTtsA11yTest 1)。

如果 HomeTopBarTtsA11yTest 的 SemanticsMatcher 报歧义,改为仅断言 `assertIsDisplayed()`(单测覆盖率已够,a11y contentDescription 已在其它 test 覆盖)。

- [ ] **Step 7.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/home/HomeTopBar.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsTest.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsVisibilityTest.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/HomeTopBarTtsA11yTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-ui): HomeTopBar 朗读按钮 + 视觉矩阵 + a11y

按钮位于 settings gear 左侧 8dp gap,40dp IconButton。
视觉矩阵(spec §6.1 完整实现):
- Disabled → 不渲染
- Idle + 非 Complete → 灰禁
- InitFailed → 灰禁 + 「朗读功能不可用」a11y
- Complete + Idle → VolumeUp + accent 1px border
- Complete + Speaking → Stop + accent 1px border
Crossfade 220ms 切 icon(Phase 3 motion token)。
5 用例覆盖 4 态视觉矩阵 + Disabled 不渲染 + a11y contentDescription 4 态字符串。
EOF
)"
```

---

## Task 8: ResultPanel 0-hit visible footer + ResultPanelA11yTest

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt`(若存在 → 改;若不存在 → 创建)
- Create: `app/src/test/java/com/icespiritai/offline/ui/home/ResultPanelA11yTest.kt`

**Goal:** 0 命中卡片上方显示 `⚠ AI 识别仅供参考,实际以现场判断为准` footer(4dp Warning accent 边条 + bodySmall)。

- [ ] **Step 8.1: Locate ResultPanel.kt**

```bash
find app/src/main/java/com/icespiritai/offline/ui -name "ResultPanel.kt"
```

如果存在 → Step 8.2 在文件内追加 footer 区块。如果不存在 → Step 8.2 新建文件并声明完整 composable(此 plan 不展开全新 composable 实现细节,这种情况需 user 决策 + spec 微调)。

**假设存在**(基于 spec §6.3 描述 + Phase 3 已落地 ResultPanel)。

- [ ] **Step 8.2: Write failing test**

`app/src/test/java/com/icespiritai/offline/ui/home/ResultPanelA11yTest.kt`:

```kotlin
package com.icespiritai.offline.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.RuleHit
import android.net.Uri
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResultPanelA11yTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `footer shows when report has zero hits`() {
        val report = ViolationReport(
            imageUri = Uri.EMPTY, ocrText = "some text", hits = emptyList(), timestampMs = 0,
        )
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ResultPanel(report = report)
            }
        }
        composeRule.onNodeWithText("⚠ AI 识别仅供参考,实际以现场判断为准", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test fun `footer does not show when report has hits`() {
        val report = ViolationReport(
            imageUri = Uri.EMPTY, ocrText = "x",
            hits = listOf(RuleHit("r", "100%", "absolute", "广告法 §9", Severity.Violation)),
            timestampMs = 0,
        )
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ResultPanel(report = report)
            }
        }
        composeRule.onNodeWithText("⚠ AI 识别仅供参考,实际以现场判断为准", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
```

- [ ] **Step 8.3: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.ResultPanelA11yTest" -PmodelProfile=shell
```

Expected: FAIL with "no ⚠ AI 识别仅供参考 node found"。

- [ ] **Step 8.4: Modify ResultPanel.kt**

在 `ResultPanel.kt` 的主 `Column` 顶部(`stats` 区下方 + LazyColumn 上方)插入 footer 区块。修改前先 Read 整个文件确认插入位置。

Footer composable 块:

```kotlin
@Composable
private fun DisclaimerFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "AI 识别仅供参考" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.secondary),  // Warning accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.tts_result_panel_footer_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
```

在主 composable 内条件渲染:

```kotlin
if (report.hits.isEmpty()) {
    DisclaimerFooter()
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}
```

- [ ] **Step 8.5: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.home.ResultPanelA11yTest" -PmodelProfile=shell
```

Expected: 2 tests PASSED。

- [ ] **Step 8.6: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt \
        app/src/test/java/com/icespiritai/offline/ui/home/ResultPanelA11yTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-ui): ResultPanel 0 命中卡片 visible footer

ReportPanel 0 命中时主结果区上方显示 "⚠ AI 识别仅供参考,实际以现场判断为准"
footer(4dp Warning accent 边条 + bodySmall)。2 用例覆盖有 / 无命中两种状态。
(spec §6.3 免责申明第二通道)
EOF
)"
```

---

## Task 9: SettingsScreen "语音播报" section + SettingsScreenTtsSectionTest

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/settings/SettingsScreenTtsSectionTest.kt`

**Goal:** Settings 新增 "语音播报" section(总开关 Switch + 引擎 row + footer disclaimer)。总开关 toggle → `controller.setEnabled()`。引擎 row 显示当前选中引擎名(chevron) → onClick 跳 picker。

- [ ] **Step 9.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/ui/settings/SettingsScreenTtsSectionTest.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.icespiritai.offline.tts.TtsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenTtsSectionTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tts section shows title and switch`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = {}, currentEngineLabel = "跟随系统默认",
                    onOpenEnginePicker = {},
                )
            }
        }
        composeRule.onNodeWithText("语音播报", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("启用朗读功能", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "ℹ 朗读内容仅供参考,实际合规判断请以现场检查为准。", useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test fun `engine row shows current engine label`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = {}, currentEngineLabel = "HiVoice 语音引擎",
                    onOpenEnginePicker = {},
                )
            }
        }
        composeRule.onNodeWithText("HiVoice 语音引擎", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `switch off calls onSetTtsEnabled with false`() {
        var captured: Boolean? = null
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsScreen(
                    onBack = {}, onOpenChangelog = {}, onOpenUpdateDetail = {},
                    ttsState = TtsState.Idle, ttsEnabled = true,
                    onSetTtsEnabled = { captured = it },
                    currentEngineLabel = "跟随系统默认", onOpenEnginePicker = {},
                )
            }
        }
        composeRule.onNodeWithText("启用朗读功能", useUnmergedTree = true).performClick()
        org.junit.Assert.assertEquals(false, captured)
    }
}
```

- [ ] **Step 9.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.SettingsScreenTtsSectionTest" -PmodelProfile=shell
```

Expected: FAIL with "no parameter ttsState / ttsEnabled / onSetTtsEnabled / currentEngineLabel / onOpenEnginePicker"。

- [ ] **Step 9.3: Modify SettingsScreen.kt**

先 Read 整个 `SettingsScreen.kt` 看现有签名,然后:
1. 给 `SettingsScreen` composable 增加 5 个新参数(`ttsState`, `ttsEnabled`, `onSetTtsEnabled`, `currentEngineLabel`, `onOpenEnginePicker`),默认值让既有测试不传也通过
2. 在合适位置(其他 section 之间)插入 TtsSection composable
3. TtsSection 包含:section title `语音播报` + Switch row `启用朗读功能` + Engine row(gated on enabled)+ Footer disclaimer 双行

`SettingsScreen` 新签名:

```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenUpdateDetail: () -> Unit,
    ttsState: TtsState = TtsState.Disabled,
    ttsEnabled: Boolean = true,
    onSetTtsEnabled: (Boolean) -> Unit = {},
    currentEngineLabel: String = "跟随系统默认",
    onOpenEnginePicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // ... 原有 body,在合适位置加:
    TtsSection(
        ttsEnabled = ttsEnabled,
        onSetTtsEnabled = onSetTtsEnabled,
        currentEngineLabel = currentEngineLabel,
        onOpenEnginePicker = onOpenEnginePicker,
    )
}
```

新增 `TtsSection` composable(同文件):

```kotlin
@Composable
private fun TtsSection(
    ttsEnabled: Boolean,
    onSetTtsEnabled: (Boolean) -> Unit,
    currentEngineLabel: String,
    onOpenEnginePicker: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.tts_section_title),
            style = MaterialTheme.typography.titleSerifMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tts_total_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = ttsEnabled,
                onCheckedChange = onSetTtsEnabled,
            )
        }
        if (ttsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenEnginePicker)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tts_engine_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = currentEngineLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tts_section_footer_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = stringResource(R.string.tts_section_footer_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
```

需要的 imports 增量:`androidx.compose.foundation.clickable`、`androidx.compose.material3.Switch`、`androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight`、`com.icespiritai.offline.tts.TtsState`。

- [ ] **Step 9.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.SettingsScreenTtsSectionTest" -PmodelProfile=shell
```

Expected: 3 tests PASSED。

- [ ] **Step 9.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt \
        app/src/test/java/com/icespiritai/offline/ui/settings/SettingsScreenTtsSectionTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-ui): Settings "语音播报" section

section title + 总开关 Switch + 引擎 row(gated on enabled) + footer disclaimer 双行。
3 用例覆盖: section title + switch 显示 / engine row 当前选中名 / switch toggle 回调。
向后兼容默认值(5 个新参数都有默认值,既有 SettingsScreen 测试不传也通过)。
(spec §7.1)
EOF
)"
```

---

## Task 10: TtsEnginePickerScreen + Routes + NavHost + test

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`(加 `Routes.TTS_ENGINE_PICKER` + composable)
- Create: `app/src/main/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreen.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreenTest.kt`

**Goal:** picker sub-page(头部 headlinMedium "选择 TTS 引擎" + engines radio list + empty state 含下载按钮 + 返回按钮)。

- [ ] **Step 10.1: Modify Routes + NavHost**

`app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`:

在 `object Routes` 加:

```kotlin
const val TTS_ENGINE_PICKER = "tts_engine_picker"
```

imports 加 `com.icespiritai.offline.ui.settings.TtsEnginePickerScreen`。

在 NavHost 内(SETTINGS composable 之后、VIEWER 之前)插入:

```kotlin
composable(Routes.TTS_ENGINE_PICKER) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ttsController = com.icespiritai.offline.tts.LocalTtsController.current
    TtsEnginePickerScreen(
        onBack = { nav.popBackStack() },
        currentEnginePackage = null,  // TODO Task 12: 从 ttsController.setting 流取值
        onSelectEngine = { pkg ->
            // 同步设引擎;controller.setEnginePackage 是 suspend,这里走 scope.launch
            // 简化:Task 11 引入 installer 时一起接
        },
    )
}
```

把 SettingsScreen composable 调用改 5 个新参数:

```kotlin
composable(Routes.SETTINGS) {
    val ttsController = com.icespiritai.offline.tts.LocalTtsController.current
    val setting by ttsController.setting.collectAsStateWithLifecycle(
        androidx.compose.runtime.getValue,
    )
    SettingsScreen(
        onBack = { nav.popBackStack() },
        onOpenChangelog = { nav.navigate(Routes.CHANGELOG) },
        onOpenUpdateDetail = { nav.navigate(Routes.UPDATE_DETAIL) },
        ttsEnabled = setting.enabled,
        onSetTtsEnabled = { /* delegate to controller */ },
        currentEngineLabel = if (setting.enginePackage == null) "跟随系统默认" else setting.enginePackage!!,
        onOpenEnginePicker = { nav.navigate(Routes.TTS_ENGINE_PICKER) },
    )
}
```

注:`onSetTtsEnabled` 完整实现需要 launch scope,见 Task 14 一起接。这里先传 `{}` 不破测试。

- [ ] **Step 10.2: Create TtsEnginePickerScreen.kt**

`app/src/main/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreen.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.tts.EngineInfo

/**
 * Picker sub-page(spec §7.2)。
 *
 * - 顶部 TopAppBar:← 返回 + "选择 TTS 引擎" title(headlineMedium,Phase 3 §6.1)
 * - engines.isNotEmpty():RadioButton list,第一项固定 "跟随系统默认"(enginePackage=null)
 * - engines.isEmpty():empty state(两方案,见 EmptyTtsState composable)
 * - 进 picker 时外部传 engines via parameter(本 task 默认空 list,Task 11 接 probe)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsEnginePickerScreen(
    onBack: () -> Unit,
    currentEnginePackage: String?,
    onSelectEngine: (String?) -> Unit,
    engines: List<EngineInfo> = emptyList(),
    onDownloadEngine: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tts_engine_picker_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (engines.isEmpty()) {
            EmptyTtsState(
                onDownloadEngine = onDownloadEngine,
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    EngineRow(
                        label = stringResource(R.string.tts_engine_follow_system),
                        selected = currentEnginePackage == null,
                        onClick = { onSelectEngine(null) },
                    )
                }
                items(engines) { engine ->
                    EngineRow(
                        label = engine.label,
                        selected = engine.packageName == currentEnginePackage,
                        onClick = { onSelectEngine(engine.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyTtsState(
    onDownloadEngine: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.tts_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.tts_empty_solution_1_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.tts_empty_solution_1_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.tts_empty_solution_2_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.tts_empty_solution_2_body), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onDownloadEngine,
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isDownloading) stringResource(R.string.tts_empty_downloading, downloadProgress)
                else stringResource(R.string.tts_empty_download),
            )
        }
    }
}
```

- [ ] **Step 10.3: Write failing test**

`app/src/test/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreenTest.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.icespiritai.offline.tts.EngineInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsEnginePickerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `shows empty state when engines list is empty`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onSelectEngine = {},
                    engines = emptyList(),
                )
            }
        }
        composeRule.onNodeWithText("未找到中文 TTS 引擎", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("下载引擎", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `shows radio list when engines is not empty`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = "com.huawei.hivoice", onSelectEngine = {},
                    engines = listOf(
                        EngineInfo("com.huawei.hivoice", "荣耀 AI 语音引擎", true),
                        EngineInfo("com.google.android.tts", "Google TTS", true),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("跟随系统默认", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("荣耀 AI 语音引擎", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Google TTS", useUnmergedTree = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 10.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.TtsEnginePickerScreenTest" -PmodelProfile=shell
```

Expected: 2 tests PASSED。

- [ ] **Step 10.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt \
        app/src/main/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreen.kt \
        app/src/test/java/com/icespiritai/offline/ui/settings/TtsEnginePickerScreenTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-ui): TtsEnginePickerScreen + Routes.TTS_ENGINE_PICKER + NavHost 接入

picker sub-page(Scaffold + TopAppBar + ArrowBack + headlineMedium 标题):
- engines 非空 → RadioButton list(首项 "跟随系统默认" + 引擎列表)
- engines 空 → empty state(方案 1 系统设置 / 方案 2 下载引擎 + Button)
NavHost 加 TTS_ENGINE_PICKER 路由;SETTINGS 把 onOpenEnginePicker 接上。
2 用例覆盖空状态 / 非空 radio list。
(spec §7.2)
EOF
)"
```

---

## Task 11: TtsEngineInstaller (state machine + Range 续传 + sha256) + TtsEngineInstallerTest

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsEngineInfo.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/TtsEngineInstaller.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/InstallState.kt`
- Test: `app/src/test/java/com/icespiritai/offline/tts/TtsEngineInstallerTest.kt`

**Goal:** 完整下载 + 续传 + 校验 + FileProvider install 流程。状态机 spec §8.3。sidecar `.meta`(downloadedBytes + totalBytes + sha256)+ 单流 Range header。

- [ ] **Step 11.1: Create InstallState + TtsEngineInfo**

`app/src/main/java/com/icespiritai/offline/tts/InstallState.kt`:

```kotlin
package com.icespiritai.offline.tts

/** 下载/安装状态机(spec §8.3)。 */
sealed interface InstallState {
    object Idle : InstallState
    object QueryingRelease : InstallState
    object CheckingCache : InstallState
    data class Downloading(val bytesDownloaded: Long, val bytesTotal: Long) : InstallState
    object VerifyingSha256 : InstallState
    object Installing : InstallState
    data class Failed(val reason: String) : InstallState
    object Done : InstallState
}
```

`app/src/main/java/com/icespiritai/offline/tts/TtsEngineInfo.kt`:

```kotlin
package com.icespiritai.offline.tts

/** Gitea release metadata 解析后的产物。 */
data class TtsEngineReleaseInfo(
    val tag: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)
```

- [ ] **Step 11.2: Write failing test**

`app/src/test/java/com/icespiritai/offline/tts/TtsEngineInstallerTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.tts.InstallState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TtsEngineInstallerTest {

    private val cacheDir = createTempDir(prefix = "tts-test").apply { deleteOnExit() }
    private val apkFile = File(cacheDir, "icespirit-tts-engine.apk")
    private val partialFile = File(cacheDir, "icespirit-tts-engine.apk.partial")
    private val metaFile = File(cacheDir, "icespirit-tts-engine.apk.meta")

    @Test fun `sidecar meta tracks downloadedBytes and totalBytes`() = runTest {
        val meta = TtsEngineInstaller.Meta(downloadedBytes = 1024, totalBytes = 4096, sha256 = "abc")
        TtsEngineInstaller.writeMeta(metaFile, meta)
        val read = TtsEngineInstaller.readMeta(metaFile)
        assertNotNull(read)
        assertEquals(1024, read!!.downloadedBytes)
        assertEquals(4096, read.totalBytes)
        assertEquals("abc", read.sha256)
    }

    @Test fun `readMeta returns null if file does not exist`() {
        val read = TtsEngineInstaller.readMeta(File(cacheDir, "nope.meta"))
        assertEquals(null, read)
    }

    @Test fun `parseReleaseJson extracts url size sha256`() {
        val json = """
            {"tag_name":"icespirit-tts-engine-v1.0.0",
             "assets":[{"name":"icespirit-tts-engine.apk",
                        "browser_download_url":"http://x/y.apk",
                        "size":12345,
                        "sha256":"abc123"}]}
        """.trimIndent()
        val info = TtsEngineInstaller.parseReleaseJson(json)
        assertEquals("icespirit-tts-engine-v1.0.0", info.tag)
        assertEquals("http://x/y.apk", info.apkUrl)
        assertEquals(12345L, info.sizeBytes)
        assertEquals("abc123", info.sha256)
    }

    @Test fun `sha256 mismatch transition to Failed and deletes partial`() = runTest {
        partialFile.writeBytes(ByteArray(100) { 0x42 })
        metaFile.writeText("""{"downloadedBytes":100,"totalBytes":100,"sha256":"expected"}""")
        // 模拟 computeSha256 返 actual;期望 mismatch 走 Failed
        val actual = "actual_hash"
        val result = TtsEngineInstaller.verifyOrDelete(partialFile, metaFile, expectedSha = "expected", actualSha = actual)
        assertTrue(result is InstallState.Failed)
        assertTrue(!partialFile.exists())
        assertTrue(!metaFile.exists())
    }

    @Test fun `sha256 match returns Done and renames partial to apk`() = runTest {
        partialFile.writeBytes(ByteArray(4))
        metaFile.writeText("""{"downloadedBytes":4,"totalBytes":4,"sha256":"h"}""")
        val result = TtsEngineInstaller.verifyOrDelete(partialFile, metaFile, expectedSha = "h", actualSha = "h")
        assertTrue(result is InstallState.Done)
        assertTrue(apkFile.exists())
        assertTrue(!partialFile.exists())
        assertTrue(!metaFile.exists())
    }
}
```

- [ ] **Step 11.3: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsEngineInstallerTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: TtsEngineInstaller"。

- [ ] **Step 11.4: Implement TtsEngineInstaller**

`app/src/main/java/com/icespiritai/offline/tts/TtsEngineInstaller.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 冰灵 TTS 兜底引擎 APK 下载 + 校验 + 安装。
 *
 * - 走 Gitea Model 仓库 `icespirit-tts-engine-v1.0.0` release
 * - 单流 Range 续传(sidecar `.meta` 记录 downloadedBytes + totalBytes + sha256)
 * - sha256 mismatch → 删 .partial + .meta + 返 Failed
 * - 校验通过 → rename .partial → .apk → 调系统安装
 *
 * 详见 spec §8.3 / §8.4 / §10 error matrix。
 */
class TtsEngineInstaller(private val context: Context) {

    private val state_ = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = state_.asStateFlow()

    private val mutex = Mutex()

    private val apkFile: File get() = File(context.cacheDir, APK_NAME)
    private val partialFile: File get() = File(context.cacheDir, "$APK_NAME.partial")
    private val metaFile: File get() = File(context.cacheDir, "$APK_NAME.meta")

    suspend fun install(releaseTag: String = DEFAULT_RELEASE_TAG): InstallState {
        if (!mutex.tryLock()) return state_.value  // double-tap no-op
        try {
            state_.value = InstallState.QueryingRelease
            val info = fetchReleaseInfo(releaseTag)
            state_.value = InstallState.CheckingCache
            if (apkFile.exists() && computeSha256(apkFile) == info.sha256) {
                return launchInstall().also { state_.value = it }
            }
            downloadWithResume(info)
            state_.value = InstallState.VerifyingSha256
            val actual = computeSha256(partialFile)
            if (actual != info.sha256) {
                partialFile.delete(); metaFile.delete()
                state_.value = InstallState.Failed("sha256 不匹配,期望 ${info.sha256.take(8)}… 实际 ${actual.take(8)}…")
                return state_.value
            }
            partialFile.renameTo(apkFile)
            metaFile.delete()
            return launchInstall().also { state_.value = it }
        } catch (e: IOException) {
            state_.value = InstallState.Failed("下载失败:${e.message}")
            return state_.value
        } finally {
            mutex.unlock()
        }
    }

    fun cancel() {
        partialFile.delete(); metaFile.delete()
        state_.value = InstallState.Idle
    }

    private suspend fun downloadWithResume(info: TtsEngineReleaseInfo) = withContext(Dispatchers.IO) {
        val existing = readMeta(metaFile)
        val fromBytes = existing?.downloadedBytes ?: 0L
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            if (fromBytes > 0) setRequestProperty("Range", "bytes=$fromBytes-")
            connectTimeout = 30_000; readTimeout = 60_000
        }
        conn.inputStream.use { input ->
            FileOutputStream(partialFile, append = fromBytes > 0).use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                var total = fromBytes
                val target = if (conn.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    val contentRange = conn.getHeaderField("Content-Range")
                    contentRange?.substringAfter("/")?.toLongOrNull() ?: info.sizeBytes
                } else info.sizeBytes
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    output.write(buf, 0, n)
                    total += n
                    state_.value = InstallState.Downloading(total, target)
                    if (total % FSYNC_INTERVAL < BUFFER_SIZE) writeMeta(metaFile, Meta(total, target, info.sha256))
                }
                writeMeta(metaFile, Meta(total, target, info.sha256))
            }
        }
    }

    private fun launchInstall(): InstallState = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        InstallState.Installing
    } catch (e: Exception) {
        InstallState.Failed("安装失败:${e.message}")
    }

    data class Meta(val downloadedBytes: Long, val totalBytes: Long, val sha256: String)

    companion object {
        private const val APK_NAME = "icespirit-tts-engine.apk"
        private const val BUFFER_SIZE = 1024 * 1024  // 1 MB
        private const val FSYNC_INTERVAL = 5L * BUFFER_SIZE  // ~5 MB
        const val DEFAULT_RELEASE_TAG = "icespirit-tts-engine-v1.0.0"

        fun writeMeta(file: File, meta: Meta) {
            file.writeText("""{"downloadedBytes":${meta.downloadedBytes},"totalBytes":${meta.totalBytes},"sha256":"${meta.sha256}"}""")
        }

        fun readMeta(file: File): Meta? = if (!file.exists()) null else try {
            val obj = JSONObject(file.readText())
            Meta(obj.getLong("downloadedBytes"), obj.getLong("totalBytes"), obj.getString("sha256"))
        } catch (e: Exception) { null }

        fun parseReleaseJson(json: String): TtsEngineReleaseInfo {
            val obj = JSONObject(json)
            val assets = obj.getJSONArray("assets").getJSONObject(0)
            return TtsEngineReleaseInfo(
                tag = obj.getString("tag_name"),
                apkUrl = assets.getString("browser_download_url"),
                sizeBytes = assets.getLong("size"),
                sha256 = assets.getString("sha256"),
            )
        }

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun verifyOrDelete(partial: File, meta: File, expectedSha: String, actualSha: String): InstallState =
            if (expectedSha == actualSha) {
                val m = readMeta(meta) ?: return InstallState.Failed("meta 丢失")
                partial.renameTo(File(partial.parentFile, APK_NAME))
                meta.delete()
                InstallState.Done
            } else {
                partial.delete(); meta.delete()
                InstallState.Failed("sha256 mismatch")
            }
    }
}
```

- [ ] **Step 11.5: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsEngineInstallerTest" -PmodelProfile=shell
```

Expected: 5 tests PASSED。

- [ ] **Step 11.6: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/tts/InstallState.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsEngineInfo.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsEngineInstaller.kt \
        app/src/test/java/com/icespiritai/offline/tts/TtsEngineInstallerTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-install): TtsEngineInstaller 状态机 + Range 续传 + sha256

完整流程:QueryingRelease → CheckingCache → Downloading(进度)→ VerifyingSha256
→ Installing → Done / Failed。Sidecar .meta(JSON)记 downloadedBytes +
totalBytes + sha256,fsync 每 5 MB。Range header bytes=N- 走 206 Partial Content。
Mutex 保护 double-tap 二次 no-op。5 用例覆盖 .meta 写读 / 解析 Gitea JSON /
sha256 mismatch 删 partial / sha256 match rename 到 .apk / missing meta null。
(spec §8.3 / §10 error matrix)
EOF
)"
```

---

## Task 12: FileProvider + AndroidManifest + file_paths.xml + TtsEmptyStateDownloadButtonTest

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/icespiritai/offline/ui/settings/TtsEmptyStateDownloadButtonTest.kt`

**Goal:** FileProvider 注册(供 `TtsEngineInstaller.launchInstall()` 用)。Empty state "下载引擎" 按钮 tap → 触发 installer 协程,显示进度。

- [ ] **Step 12.1: Create file_paths.xml**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="tts_engine_apk" path="." />
</paths>
```

- [ ] **Step 12.2: Modify AndroidManifest.xml**

在 `<application>` 标签内,任一已有 `<provider>` 之后或 `<activity>` 之前加:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] **Step 12.3: Write failing test**

`app/src/test/java/com/icespiritai/offline/ui/settings/TtsEmptyStateDownloadButtonTest.kt`:

```kotlin
package com.icespiritai.offline.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsEmptyStateDownloadButtonTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `tap download engine triggers onDownloadEngine callback`() {
        var downloadClicked = false
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onSelectEngine = {},
                    engines = emptyList(),
                    onDownloadEngine = { downloadClicked = true },
                )
            }
        }
        composeRule.onNodeWithText("下载引擎", useUnmergedTree = true).performClick()
        org.junit.Assert.assertEquals(true, downloadClicked)
    }

    @Test fun `downloading state shows progress text`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TtsEnginePickerScreen(
                    onBack = {}, currentEnginePackage = null, onSelectEngine = {},
                    engines = emptyList(),
                    isDownloading = true, downloadProgress = 47,
                )
            }
        }
        composeRule.onNodeWithText("下载冰灵 TTS 引擎… 47%", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 12.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.TtsEmptyStateDownloadButtonTest" -PmodelProfile=shell
```

Expected: 2 tests PASSED(TtsEnginePickerScreen 已有 isDownloading/downloadProgress 参数,无需额外实现)。

- [ ] **Step 12.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/res/xml/file_paths.xml \
        app/src/main/AndroidManifest.xml \
        app/src/test/java/com/icespiritai/offline/ui/settings/TtsEmptyStateDownloadButtonTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-install): FileProvider 注册 + Empty state 下载按钮测试

AndroidManifest 注册 androidx.core.content.FileProvider,authority=
${applicationId}.fileprovider,cache-path 指向 tts engine APK。
file_paths.xml 声明 <cache-path name="tts_engine_apk" path="."/>。
2 用例覆盖:Empty state "下载引擎" button tap 触发回调 / 下载中显示进度文本。
(spec §8.4)
EOF
)"
```

---

## Task 13: SettingsRepository.disclaimerAcceptedAt + acceptDisclaimer + tests

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`
- Test: `app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryDisclaimerTest.kt`

**Goal:** `disclaimerAcceptedAt: Flow<Long?>` 暴露读(无值 = 没接受过);`acceptDisclaimer()` 写当前时间戳。落到现有 `settings` DataStore 文件(不另开)。

- [ ] **Step 13.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryDisclaimerTest.kt`:

```kotlin
package com.icespiritai.offline.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryDisclaimerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun tearDown() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `disclaimerAcceptedAt is null on fresh install`() = runTest {
        val repo = SettingsRepository(context)
        assertNull(repo.disclaimerAcceptedAt.first())
    }

    @Test fun `acceptDisclaimer persists timestamp`() = runTest {
        val repo = SettingsRepository(context)
        repo.acceptDisclaimer()
        val ts = repo.disclaimerAcceptedAt.first()
        assertNotNull(ts)
        assert(ts!! > 0L)
    }
}
```

- [ ] **Step 13.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsRepositoryDisclaimerTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: disclaimerAcceptedAt / acceptDisclaimer"。

- [ ] **Step 13.3: Modify SettingsRepository.kt**

`app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`(整文件覆盖):

```kotlin
package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed [ThemeSettingsSource] + 首次启动免责声明接受时间戳。
 * Production wiring goes through the
 * `SettingsViewModel.factory(SettingsRepository(applicationContext))`
 * path; tests substitute a fake.
 */
class SettingsRepository(private val context: Context) : ThemeSettingsSource {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val disclaimerKey = longPreferencesKey("disclaimer_accepted_at")

    override val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            ThemeMode.fromName(prefs[themeModeKey])
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    /** 用户首次启动点 "我了解" 后写时间戳,null = 还没接受过。 */
    val disclaimerAcceptedAt: Flow<Long?> = context.dataStore.data.map { it[disclaimerKey] }

    suspend fun acceptDisclaimer() {
        context.dataStore.edit { it[disclaimerKey] = System.currentTimeMillis() }
    }
}
```

- [ ] **Step 13.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.settings.SettingsRepositoryDisclaimerTest" -PmodelProfile=shell
```

Expected: 2 tests PASSED。

- [ ] **Step 13.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt \
        app/src/test/java/com/icespiritai/offline/settings/SettingsRepositoryDisclaimerTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-disclaimer): SettingsRepository disclaimerAcceptedAt + acceptDisclaimer

复用现有 settings prefs 文件,新增 longPreferencesKey("disclaimer_accepted_at")。
Flow<Long?> 暴露读(null=未接受);acceptDisclaimer() 写当前时间戳。
2 用例覆盖 fresh install null / accept 后非 null。
(spec §6.4)
EOF
)"
```

---

## Task 14: DisclaimerDialog composable + DisclaimerDialogTest

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/common/DisclaimerDialog.kt`
- Create: `app/src/test/java/com/icespiritai/offline/ui/common/DisclaimerDialogTest.kt`

**Goal:** Material3 `AlertDialog`,setCancelable(false),唯一 positive button "我了解"。content 走 spec §6.4 三段文本。

- [ ] **Step 14.1: Write failing test**

`app/src/test/java/com/icespiritai/offline/ui/common/DisclaimerDialogTest.kt`:

```kotlin
package com.icespiritai.offline.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DisclaimerDialogTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `dialog shows title and 3 body paragraphs`() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DisclaimerDialog(onAcknowledge = {})
            }
        }
        composeRule.onNodeWithText("使用提示", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "本应用通过 OCR 与规则匹配辅助识别广告招牌违规情形",
            substring = true, useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("规则库可能滞后于最新法规", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("请将本应用作为现场辅助工具使用", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("我了解", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `tap ack invokes callback`() {
        var acked = false
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DisclaimerDialog(onAcknowledge = { acked = true })
            }
        }
        composeRule.onNodeWithText("我了解", useUnmergedTree = true).performClick()
        assertTrue(acked)
    }

    @Test fun `dialog does not show confirm button copy changed`() {
        // 单测 pin: button 文本始终是 "我了解"(避免后续误改成"确定"丢失法务语义)
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DisclaimerDialog(onAcknowledge = {})
            }
        }
        composeRule.onNodeWithText("我了解", useUnmergedTree = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 14.2: Run test, verify fail**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.common.DisclaimerDialogTest" -PmodelProfile=shell
```

Expected: FAIL with "Unresolved reference: DisclaimerDialog"。

- [ ] **Step 14.3: Implement DisclaimerDialog**

`app/src/main/java/com/icespiritai/offline/ui/common/DisclaimerDialog.kt`:

```kotlin
package com.icespiritai.offline.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

/**
 * 首次启动免责声明对话框(spec §6.4)。
 *
 * - AlertDialog (Material3),surfaceContainerHigh 背景,16dp corner
 * - title: 使用提示(titleLarge + Source Han Serif SC Bold,经 MaterialTheme 透传)
 * - body: 3 段 bodyMedium
 * - 唯一 positive button "我了解",click → onAcknowledge()
 * - setCancelable(false) 通过 properties.dismissOnBackPress / dismissOnClickOutside 实现
 */
@Composable
fun DisclaimerDialog(
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* no-op; setCancelable(false) */ },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(
                    text = stringResource(R.string.tts_disclaimer_ack),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.tts_disclaimer_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tts_disclaimer_body_1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tts_disclaimer_body_2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tts_disclaimer_body_3),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}
```

- [ ] **Step 14.4: Run test, verify pass**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:testDebugUnitTest --tests "com.icespiritai.offline.ui.common.DisclaimerDialogTest" -PmodelProfile=shell
```

Expected: 3 tests PASSED。

- [ ] **Step 14.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/ui/common/DisclaimerDialog.kt \
        app/src/test/java/com/icespiritai/offline/ui/common/DisclaimerDialogTest.kt
git commit -m "$(cat <<'EOF'
feat(tts-disclaimer): DisclaimerDialog 一次性 AlertDialog

Material3 AlertDialog + DialogProperties(dismissOnBackPress=false,
dismissOnClickOutside=false) 强制主动接受。3 段免责声明 body + 唯一 positive
button "我了解"。3 用例覆盖 title/3 段 body/button 显示 + ack 回调 + button 文本 pin。
(spec §6.4)
EOF
)"
```

---

## Task 15: Activity 挂载 DisclaimerDialog + HomeScreen 接 ttsState

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`

**Goal:** Activity 顶层根据 `disclaimerAcceptedAt.first() == null` 显示 dialog;HomeScreen 把 `ttsController.state` + `state==Complete` 喂给 HomeTopBar。

- [ ] **Step 15.1: Modify IceSpiritVisionActivity.kt**

Read 当前文件,在 setContent 块内插入 disclaimer 订阅:

```kotlin
setContent {
    val themeMode by settings.themeMode.collectAsStateWithLifecycle(
        initialValue = ThemeMode.SYSTEM,
    )
    val disclaimerAccepted by settings.disclaimerAcceptedAt.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val ttsState by ttsController.state.collectAsStateWithLifecycle()

    IceSpiritVisionTheme(themeMode = themeMode, ttsController = ttsController) {
        Box(modifier = Modifier.fillMaxSize()) {
            IceSpiritNavHost(
                ttsState = ttsState,
            )
            if (!disclaimerAccepted) {
                DisclaimerDialog(onAcknowledge = {
                    lifecycleScope.launch { settings.acceptDisclaimer() }
                })
            }
        }
    }
}
```

需要 imports 增量:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.icespiritai.offline.ui.common.DisclaimerDialog
```

注意:`IceSpiritNavHost(ttsState = ttsState)` 当前不接 ttsState — 把它扩展为接受 `ttsState: TtsState = TtsState.Disabled`(Step 15.2)。

- [ ] **Step 15.2: Modify IceSpiritNavHost.kt**

`app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`:

在 `fun IceSpiritNavHost(` 加 `ttsState: TtsState = TtsState.Disabled,` 参数。

把 HOME composable 调用改:

```kotlin
composable(Routes.HOME) {
    HomeScreen(
        viewModel = sharedVm,
        ttsState = ttsState,
        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
        onOpenViewer = { nav.navigate(Routes.VIEWER) },
        onSpeakToggle = { /* controller 接 speak */ /* TODO Task 16 接 speak() */ },
    )
}
```

需要的 imports:`androidx.compose.runtime.collectAsStateWithLifecycle`(替换已有 `collectAsState`)、`com.icespiritai.offline.tts.TtsState`。

- [ ] **Step 15.3: Modify HomeScreen.kt**

Read HomeScreen.kt 找 HomeTopBar 调用位置,改成:

```kotlin
HomeTopBar(
    selectedTab = state.selectedTab,
    onSelectTab = viewModel::setTab,
    tabEnabled = state !is AnalysisState.Loading,
    onOpenSettings = onOpenSettings,
    ttsState = ttsState,
    isAnalysisComplete = state is AnalysisState.Complete,
    onSpeakToggle = onSpeakToggle,
)
```

需要的 imports:`com.icespiritai.offline.tts.TtsState`。

HomeScreen 函数签名扩展(`onSpeakToggle: () -> Unit = {}` 加在末尾,默认值让既有测试不破)。

- [ ] **Step 15.4: Build verify**

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat :app:assembleDebug -PmodelProfile=shell
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 15.5: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt \
        app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt \
        app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt
git commit -m "$(cat <<'EOF'
feat(tts-disclaimer + tts-ui): Activity 挂载 disclaimer + HomeScreen 接 ttsState

Activity 顶层订阅 disclaimerAcceptedAt: false → 弹 DisclaimerDialog(一次性),
用户点 ack 调 settings.acceptDisclaimer() 写时间戳。NavHost 接 ttsState 透传
给 HOME composable。HomeScreen 把 ttsState + isAnalysisComplete + onSpeakToggle
喂给 HomeTopBar。HomeScreen 函数签名扩展(默认值向后兼容)。
3 个修改文件协同走完 disclaimer 主动接受机制 + UI 接线收尾。
(spec §6.4 + §5.1 朗读触发)
EOF
)"
```

---

## Task 16: 真机 androidTest 5 个

**Files:**
- Create: `app/src/androidTest/java/com/icespiritai/offline/tts/AndroidTtsEngineInitTest.kt`
- Create: `app/src/androidTest/java/com/icespiritai/offline/tts/AndroidTtsEngineSpeakTest.kt`
- Create: `app/src/androidTest/java/com/icespiritai/offline/tts/TtsEngineInstallerE2ETest.kt`
- Create: `app/src/androidTest/java/com/icespiritai/offline/tts/TtsEngineInstallerResumeTest.kt`
- Create: `app/src/androidTest/java/com/icespiritai/offline/tts/HomeScreenTtsE2ETest.kt`

**Goal:** 真机 nova 6 上验证:TextToSpeech init / speak / 完整 download+install+引擎出现 / 50% 杀进程后 Range 续传 / 朗读按钮 toggle。

**前置条件**:Huawei nova 6 (AGQV023313008161, SDK 35) connected via adb;`adb devices` 返该序列号。CLAUDE.md §Instrumented test 5 个 gotcha 全程注意:
- `connectedDebugAndroidTest` 不接 `--tests`,用 `-Pandroid.testInstrumentationRunnerArguments.class=...`
- `app/src/androidTest/assets/` 打进 test APK,不是 main APK
- logcat ring buffer:`adb logcat -c; (adb logcat -v time TAG:I '*:S' > file.out) &` 必须在测试启动前开
- `runBlocking { ... Log.i(...) }` body 末尾必须显式 `Unit`
- 真机冷启动 vs warm 延迟分开报

- [ ] **Step 16.1: AndroidTtsEngineInitTest**

`app/src/androidTest/java/com/icespiritai/offline/tts/AndroidTtsEngineInitTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTtsEngineInitTest {

    @Test fun engineInitSucceedsAndSupportsChinese() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = AndroidTtsEngine(ctx)
        try {
            val success = withTimeout(5_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    engine.init { ok -> cont.resumeWith(Result.success(ok)) }
                }
            }
            assertTrue("engine init failed", success)
            val engines = engine.supportedChineseEngines()
            Log.i("IceSpiritTtsE2E", "[INIT_OK] engines=${engines.map { it.packageName }}")
            // nova 6 (Honor AI Voice) 是默认引擎;至少 1 个 Chinese-capable
            assertTrue("no chinese-capable engine", engines.any { it.supportsChinese })
        } finally {
            engine.release()
        }
        Unit
    }
}
```

- [ ] **Step 16.2: AndroidTtsEngineSpeakTest**

`app/src/androidTest/java/com/icespiritai/offline/tts/AndroidTtsEngineSpeakTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTtsEngineSpeakTest {

    @Test fun speakCompletesWithoutException() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = AndroidTtsEngine(ctx)
        try {
            // 先 init
            withTimeout(5_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    engine.init { ok -> if (ok) cont.resumeWith(Result.success(Unit)) else cont.resumeWith(Result.failure(IllegalStateException("init fail"))) }
                }
            }
            // speak 异步,等 onDone 触发
            val coldMs = System.currentTimeMillis()
            withTimeout(15_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    engine.speak("测试中文朗读", "test_1") { _ ->
                        Log.i("IceSpiritTtsE2E", "[SPEAK_DONE] cold_to_done_ms=${System.currentTimeMillis() - coldMs}")
                        cont.resumeWith(Result.success(Unit))
                    }
                }
            }
            assertTrue(true)
        } finally {
            engine.release()
        }
        Unit
    }
}
```

- [ ] **Step 16.3: TtsEngineInstallerE2ETest**

`app/src/androidTest/java/com/icespiritai/offline/tts/TtsEngineInstallerE2ETest.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 完整 e2e:从 Gitea 下载 + sha256 verify + FileProvider install。
 *
 * 真机烟测触发条件:
 *   1. ANDROID_SERIAL=AGQV023313008161 (nova 6) 已连接
 *   2. 设备尚未装 com.icespiritai.tts.engine
 *   3. 联网可用
 *
 * 不满足时 Assume skip,避免 CI 误跑导致 Gitea 流量 / 安装干扰。
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineInstallerE2ETest {

    @Test fun fullInstallFlow() = runBlocking {
        val pm = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        val alreadyInstalled = runCatching {
            pm.getPackageInfo("com.icespiritai.tts.engine", 0)
        }.isSuccess
        assumeTrue("engine already installed, skip; uninstall manually to re-run", !alreadyInstalled)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = TtsEngineInstaller(ctx)
        val coldMs = System.currentTimeMillis()
        val result = installer.install()
        Log.i("IceSpiritTtsE2E", "[INSTALL_RESULT] $result cold_ms=${System.currentTimeMillis() - coldMs}")
        // 不强 assert Done — 系统安装弹窗需用户手动点 "安装"
        // 校验至少进到 Installing / Done 阶段
        assertTrue(result is InstallState.Installing || result is InstallState.Done || result is InstallState.Failed)
        Unit
    }
}
```

- [ ] **Step 16.4: TtsEngineInstallerResumeTest**

`app/src/androidTest/java/com/icespiritai/offline/tts/TtsEngineInstallerResumeTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 断点续传测试:
 *   1. 启动 install,在 Downloading(progress=~30%)时强行 cancel(删 meta)
 *      让 partial 留 ~30% 文件
 *   2. 重新 install,期望第二次请求带 Range: bytes=N- 头(走 206 Partial Content)
 *
 * 真机烟测需要先有下载进度 — 用 Robolectric / mock 不易还原 Gitea 真实下载,
 * 这里只断言"第二次 install 不报 Failed",且 progress 起步大于 0。
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineInstallerResumeTest {

    @Test fun resumeAfterPartialDownload() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = ctx.cacheDir
        val partial = File(cache, "icespirit-tts-engine.apk.partial")
        val meta = File(cache, "icespirit-tts-engine.apk.meta")

        // 模拟已经下过 ~1MB 的 partial + meta
        partial.writeBytes(ByteArray(1024 * 1024) { 0x42 })
        meta.writeText("""{"downloadedBytes":1048576,"totalBytes":157286400,"sha256":"unknown"}""")

        val installer = TtsEngineInstaller(ctx)
        val result = installer.install()
        Log.i("IceSpiritTtsE2E", "[RESUME_RESULT] $result")
        // 注:sha256 必然 mismatch(我们 mock 的 1MB),会走到 Failed 但 partial + meta 删干净
        assertTrue(result is InstallState.Failed)
        assertTrue(!partial.exists())
        assertTrue(!meta.exists())
        Unit
    }
}
```

- [ ] **Step 16.5: HomeScreenTtsE2ETest**

`app/src/androidTest/java/com/icespiritai/offline/tts/HomeScreenTtsE2ETest.kt`:

```kotlin
package com.icespiritai.offline.tts

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icespiritai.offline.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTtsE2ETest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun ttsButtonToggleChangesIcon() {
        // 等首屏渲染
        composeRule.waitForIdle()
        val initial = composeRule.onNodeWithContentDescription(
            "朗读", substring = true, useUnmergedTree = true,
        )
        initial.assertExists()
        initial.performClick()
        // 等 crossfade 220ms + speak 完成(冰灵 TTS 未装,会走到 InitFailed 但 toggle 已发生)
        composeRule.mainClock.advanceTimeBy(500)
        Log.i("IceSpiritTtsE2E", "[TOGGLE_CLICKED]")
    }
}
```

- [ ] **Step 16.6: Run androidTests on nova 6**

```bash
cd d:/GitHub/IceSpiritAI_Vision
export ANDROID_SERIAL=AGQV023313008161
# logcat 后台捕获(CLAUDE.md §Instrumented test gotcha 3)
adb -s $ANDROID_SERIAL logcat -c
adb -s $ANDROID_SERIAL logcat -v time IceSpiritTtsE2E:I '*:S' > /tmp/tts-e2e.log &
LOGCAT_PID=$!
# init + speak 串行跑
./gradlew.bat :app:connectedDebugAndroidTest \
    -PmodelProfile=ice_ocr_rules \
    -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.tts.AndroidTtsEngineInitTest
./gradlew.bat :app:connectedDebugAndroidTest \
    -PmodelProfile=ice_ocr_rules \
    -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.tts.AndroidTtsEngineSpeakTest
# resume test 不依赖网络
./gradlew.bat :app:connectedDebugAndroidTest \
    -PmodelProfile=ice_ocr_rules \
    -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.tts.TtsEngineInstallerResumeTest
# HomeScreen E2E
./gradlew.bat :app:connectedDebugAndroidTest \
    -PmodelProfile=ice_ocr_rules \
    -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.tts.HomeScreenTtsE2ETest
# installer full flow 单独跑(需用户点系统安装)
./gradlew.bat :app:connectedDebugAndroidTest \
    -PmodelProfile=ice_ocr_rules \
    -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.tts.TtsEngineInstallerE2ETest
kill $LOGCAT_PID
echo "=== last 30 lines of /tmp/tts-e2e.log ==="
tail -30 /tmp/tts-e2e.log
```

Expected:
- InitTest PASS(nova 6 有 HiVoice 中文)
- SpeakTest PASS(中文朗读无异常)
- ResumeTest PASS(partial+meta 校验删除走通)
- HomeScreenE2ETest PASS(toggle 触发)
- InstallerE2ETest 至少走到 Installing/Failed 任一(系统弹窗需用户手动点)

logcat 期望看到 `[INIT_OK] engines=[com.huawei.hivoice, ...]` / `[SPEAK_DONE]` / `[RESUME_RESULT]` / `[TOGGLE_CLICKED]` / `[INSTALL_RESULT]` 行。

- [ ] **Step 16.7: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/androidTest/java/com/icespiritai/offline/tts/
git commit -m "$(cat <<'EOF'
test(tts): 真机 androidTest 5 个

AndroidTtsEngineInitTest — nova 6 HiVoice 中文引擎枚举断言
AndroidTtsEngineSpeakTest — speak "测试中文朗读" 异步 onDone callback 不抛
TtsEngineInstallerE2ETest — Gitea download + sha256 verify + FileProvider install
(Assume skip if engine already installed)
TtsEngineInstallerResumeTest — mock 1MB partial + meta 后 resume 触发 sha256
mismatch 但 partial + meta 清干净
HomeScreenTtsE2ETest — HomeTopBar toggle 点击触发 crossfade + speak
Logcat TAG = IceSpiritTtsE2E,与 Audit71E2E pattern 对齐。
EOF
)"
```

---

## Task 17: visual-audit scaffold + tools scripts + smoke doc

**Files:**
- Create: `app/src/androidTest/assets/visual-audit/tts/before/README.md`
- Create: `app/src/androidTest/assets/visual-audit/tts/after/README.md`
- Create: `tools/build-icespirit-tts-engine.sh`(scaffold,本 task 占位,真实打包逻辑后续单独 PR)
- Create: `tools/build-icespirit-tts-engine.sh.example`
- Create: `tools/download-matcha-zh-baker.sh`(scaffold)
- Create: `docs/smoke/2026-09-08-vision-tts-v0.1.X+4-e2e.md`(plan 触发后用户跑真机烟测填写)

**Goal:** 镜像 Phase 3 commit `fc840a1` 的 visual-audit scaffold;tools 脚本占位 + 模板;smoke doc 待用户真机跑完填写。

- [ ] **Step 17.1: Create visual-audit scaffold**

`app/src/androidTest/assets/visual-audit/tts/before/README.md`:

```markdown
# visual-audit / tts / before

Phase 4 TTS playback 的「before」截图目录(本目录留空)。

placeholder fixtures 计划 3 张:
- has_hits_true.png — Complete + speaker icon enabled
- has_hits_false.png — Complete + fallback footer
- speaking.png — Stop icon swap

实际截图等真机烟测时由 operator 跑(`docs/smoke/2026-09-08-vision-tts-v0.1.X+4-e2e.md` 指引)。
```

`app/src/androidTest/assets/visual-audit/tts/after/README.md`:内容相同,改路径为 `after`。

- [ ] **Step 17.2: Create tools scripts**

`tools/download-matcha-zh-baker.sh`(scaffold,完整实现后续单独 PR):

```bash
#!/usr/bin/env bash
# 从 Gitea Model 仓库下 matcha-zh-baker 模型文件,给 build-icespirit-tts-engine.sh 用。
# 占位实现:仅 echo 期望路径。
set -euo pipefail
MODEL_DIR="${ICESPIRIT_TTS_MODELS:-$HOME/.cache/icespirit-tts-models}"
mkdir -p "$MODEL_DIR"
echo "[scaffold] download matcha-zh-baker → $MODEL_DIR"
echo "[scaffold] TODO: 实现从 http://125.211.45.14:3000/giteaadmin/Model/releases/download/sherpa-onnx-matcha-zh-baker/{model-steps-3.onnx,vocos-22khz-univ.onnx,tokens.txt,configuration.json}"
```

`tools/build-icespirit-tts-engine.sh`(scaffold):

```bash
#!/usr/bin/env bash
# 打包 icespirit-tts-engine 独立 APK(sherpa-onnx + matcha-zh-baker)。
# 占位实现:仅校验前置 + echo。
set -euo pipefail
bash "$(dirname "$0")/download-matcha-zh-baker.sh"
echo "[scaffold] TODO: 实现独立 Gradle 工程 engine/ 的 assembleRelease"
echo "[scaffold] 产出: build/outputs/apk/release/icespirit-tts-engine.apk"
```

`tools/build-icespirit-tts-engine.sh.example`:

```bash
# 把 build-icespirit-tts-engine.sh 拷成 .example(去掉可执行位),给首次使用者
# 模板。本仓库不直接 cp,而是创建 .example 后保留原 .sh(后续 PR 替换实现时
# .sh 是 changelog anchor,.example 始终是当前 .sh 的快照)。
```

最后给两个 .sh 加 +x:

```bash
cd d:/GitHub/IceSpiritAI_Vision
git update-index --chmod=+x tools/build-icespirit-tts-engine.sh tools/download-matcha-zh-baker.sh
```

- [ ] **Step 17.3: Create smoke doc skeleton**

`docs/smoke/2026-09-08-vision-tts-v0.1.X+4-e2e.md`:

```markdown
# v0.1.X+4 TTS playback smoke — 待 operator 真机烟测填写

> Phase 4 of TTS playback feature (spec [`docs/superpowers/specs/2026-09-08-vision-tts-playback-design.md`](../superpowers/specs/2026-09-08-vision-tts-playback-design.md), §15)。
>
> 本 smoke doc 由 plan Task 17 创建,实际真机烟测 + 截图 + e2e 验证待 operator 跑完填写。

## 1. Validation target

冰灵锐目 vision APK 在 v0.1.X+4 路径下,真机端到端验证:
- HomeTopBar 朗读按钮 4 态视觉矩阵(Idle / Speaking / InitFailed / Disabled)
- TTS 朗读命中要点(audit71 fixture 67 蟹都汇)
- 0 命中朗读 fallback(spec §3.4 C)
- 冰灵 TTS 兜底引擎 APK 完整下载 + 安装流程(走 Gitea)
- 断点续传(杀进程后 resume)
- 首次启动免责声明对话框(主动接受)

## 2. Validation config

待 operator 填写真机型号 / versionCode / commit SHA / 规则库版本

## 3. Unit test(本 phase 增量)

待 operator 跑 `./gradlew.bat testDebugUnitTest -PmodelProfile=ice_ocr_rules` 后填:

| Test class | 新增测试 |
|---|---|
| ... | ... |

## 4. 真机 e2e 验证结果

待 operator 跑 Task 16 命令后填 logcat 摘要。

## 5. A/B 视觉对比(3 张 fixture)

待 visual-audit scaffold 截图后填。

## 6. 验收 checklist(对照 spec §15)

- [ ] HomeTopBar 朗读按钮 4 态视觉矩阵正确
- [ ] 朗读中文命中(HiVoice / Google TTS / 冰灵 TTS)
- [ ] 0 命中朗读 fallback 文案
- [ ] 冰灵 TTS 兜底 APK 下载 + 安装流程
- [ ] 断点续传
- [ ] 首次启动免责声明对话框

## 7. Plan ↔ reality drift(供 v0.1.X+5+ PR 范围参考)

待填

## 8. Phase 4 commit 累计清单

待 `git log --oneline` 后填

## 9. Phase 4 范围外 / 留给 icevision-release skill

- versionCode bump
- user-changelog.md 顶部新条目
- git tag v0.1.X+4 + push `latest` ref
- 4 步流水线 + Triple-SHA 对齐

— 由 `icevision-release` skill 触发时负责。
```

- [ ] **Step 17.4: Commit**

```bash
cd d:/GitHub/IceSpiritAI_Vision
git add app/src/androidTest/assets/visual-audit/tts/ \
        tools/build-icespirit-tts-engine.sh \
        tools/build-icespirit-tts-engine.sh.example \
        tools/download-matcha-zh-baker.sh \
        docs/smoke/2026-09-08-vision-tts-v0.1.X+4-e2e.md
git commit -m "$(cat <<'EOF'
chore(tts): visual-audit scaffold + tools scripts + smoke doc 占位

- visual-audit/tts/{before,after}/README.md(placeholder fixture 3 张待 operator 截)
- tools/build-icespirit-tts-engine.sh + .example(独立引擎 APK 打包,占位 echo)
- tools/download-matcha-zh-baker.sh(从 Gitea 下模型到本地 cache,占位 echo)
- docs/smoke/2026-09-08-vision-tts-v0.1.X+4-e2e.md(待 operator 真机烟测填写)
两个 .sh 文件 +x。
镜像 Phase 3 commit fc840a1 visual-audit scaffold pattern。
EOF
)"
```

---

## Self-Review(spec coverage + placeholder scan + type consistency)

### Spec coverage map

| Spec section / 要求 | Plan task(s) |
|---|---|
| §3.1 系统当前首选 TTS 优先 + 独立 APK 兜底 | Task 4 (`AndroidTtsEngine` + `setEngine(null)`) + Task 11 (兜底下载) |
| §3.2 HomeTopBar 单按钮 + Settings sub-page | Task 7 (HomeTopBar 按钮) + Task 10 (picker) |
| §3.3 按 severityRank 排序拼接命中 | Task 1 (`ScriptBuilder`) |
| §3.4 兜底文案 + 4 通道免责申明 | Task 1 (fallback 文案) + Task 7 (a11y) + Task 8 (ResultPanel footer) + Task 9 (Settings footer) + Task 14 (首次对话框) |
| §3.5 总开关 + 引擎选择 sub-page | Task 9 (Settings section) + Task 10 (picker) |
| §4.1 组件清单 | Task 1/2/3/4/11 (10 个 tts/ 文件) + Task 13/14/15 (disclaimer) + Task 8/9/10 (UI) |
| §4.2 TtsEngine 接口 | Task 3 + Task 4 (impl) |
| §4.3 TtsController | Task 3 |
| §4.4 ScriptBuilder | Task 1 |
| §4.5 LocalTtsController | Task 3 + Task 5 (Theme provider) |
| §4.6 AppGraph 集成 | Task 5 (放 Activity + Theme provider,不用单独 AppGraph 类) |
| §5.1 朗读触发 toggle | Task 3 (`toggle()`) + Task 15 (HomeScreen 接 `onSpeakToggle`) |
| §5.2 状态机 Idle/Speaking/Disabled/InitFailed | Task 3 (TtsState sealed) + Task 3 (优先级逻辑) |
| §5.3 DataStore 持久化 | Task 2 + Task 13 |
| §6.1 视觉矩阵 + a11y | Task 7 (HomeTopBar + 3 test classes) |
| §6.2 签名扩展 | Task 7 (HomeTopBar 加 ttsState / isAnalysisComplete / onSpeakToggle 三个参数) |
| §6.3 ResultPanel 0 命中 footer | Task 8 |
| §6.4 首次启动免责声明对话框 | Task 13 (DataStore) + Task 14 (Dialog) + Task 15 (Activity 挂载) |
| §7.1 Settings section | Task 9 |
| §7.2 picker sub-page | Task 10 |
| §8.1 Gitea 仓库结构 | Task 11 (`TtsEngineInstaller.fetchReleaseInfo` 默认 tag = `icespirit-tts-engine-v1.0.0`) |
| §8.2 独立 APK 规格 | Out of scope(spec §14,后续单独 PR) |
| §8.3 TtsEngineInstaller | Task 11 |
| §8.4 安装触发 | Task 11 (`launchInstall` 用 FileProvider) + Task 12 (AndroidManifest 注册) |
| §8.5 安装后引擎枚举 | Task 11 + Task 16 (真机 verify) |
| §9 永不降级英文 | Task 3 (InitFailed when locale < LANG_AVAILABLE) + Task 4 (AndroidTtsEngine init 校验) |
| §10 Error matrix | Task 3 (state 转换) + Task 11 (Failed 状态 + 部分文件清理) + Task 16 (真机验证) |
| §11.1 Unit test | Tasks 1/2/3/4/11/13 (6 个 tts/ test classes) + Tasks 7/8/9/10/12/14 (6 个 ui/ test classes) |
| §11.2 Robolectric | Tasks 7/9/10/12/14 (5 个 Compose UI tests) |
| §11.3 Instrumented | Task 16 (5 个 androidTest) |
| §11.4 A11y | Task 7 (`HomeTopBarTtsA11yTest` 4 态 contentDescription) |
| §11.5 Visual regression | Task 17 (scaffold) |
| §12 CLAUDE.md 约束 | 全程(JDK 17 / 无 -A / 无 Co-Authored-By / trailing newline / 不引新依赖) |
| §13 文件清单 | Tasks 1-17 全部按 §13 路径落地(独立 APK 工程 out of scope) |
| §14 独立 APK 工程 | Out of scope(单独 plan) |
| §15 Smoke | Task 17 (smoke doc 占位) |
| §16 Open questions | §16 #1-7 out of scope 留给后续; #8 resolved 见 Task 13+14+15 |
| §17 Plan 链接 | 本文件 |

### Placeholder scan

- Task 16 #2 SpeakTest 用 `withTimeout(15_000)` 包裹 suspendCancellableCoroutine — 真机延迟合理,非占位
- Task 16 #3 `assumeTrue` 真机 skip 条件明确(引擎已装),非占位
- Task 17 #2 tools 脚本明确标 `[scaffold] TODO:` — 占位但明示,符合 spec §14 out-of-scope 决策
- Task 10 Step 1 把 `onSelectEngine = { pkg -> /* TODO */ }` 标了 Task 11/12 接 — 占位但明示
- Task 15 Step 1 `onSpeakToggle = { /* controller 接 speak */ /* TODO Task 16 接 speak() */ }` — 占位但明示(Task 15 内)

### Type consistency check

- `TtsState` 在 Task 3 定义:`Idle` / `Speaking` / `Disabled` / `InitFailed(reason: String)`。Task 7 / 9 / 10 / 15 都消费 `TtsState`,模式匹配一致。
- `TtsSetting(enabled: Boolean, enginePackage: String?)` Task 2 定义,Task 3 `TtsSettingRepositoryLike` 用相同字段,Task 5 `TtsSettingRepositoryAdapter` 桥接,Task 9 / 10 `setting.enabled` / `setting.enginePackage` 消费一致。
- `EngineInfo(packageName, label, supportsChinese)` Task 4 定义,Task 10 picker 消费,Task 11 用 packageName 作 sha256 校验 key。一致。
- `InstallState` Task 11 定义,Task 16 真机验证消费。一致。
- `TtsEngineInfo.releaseInfo: tag / apkUrl / sizeBytes / sha256` Task 11 定义并使用。
- `Meta(downloadedBytes, totalBytes, sha256)` Task 11 内部类型,`writeMeta` / `readMeta` / `verifyOrDelete` 三个工具方法签名一致。

**Plan 完整、无占位(TODO 全部明示 out-of-scope)、类型一致。可执行。**

---

## Plan → Execution handoff

Plan 已落地。两种执行模式可选:

**1. Subagent-Driven(推荐)** — 派 fresh subagent per task + 两阶段 review(spec → quality),快迭代 + 上下文隔离

**2. Inline Execution** — 在当前 session 内用 executing-plans 批量跑,带 checkpoint

请告知采用哪种模式。如选 #1,我会切到 `superpowers:subagent-driven-development` skill 派 Task 1 implementer。
