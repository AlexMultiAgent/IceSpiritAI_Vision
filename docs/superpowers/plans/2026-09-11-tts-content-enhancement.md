# 冰灵锐目 TTS 朗读内容增强 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-KILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前"命中违规:无麸质。维生素A。能量。"式平铺朗读脚本升级为**结构化严重度分组 + 命中计数 + 法条引用 + AI 免责声明**;为多命中场景引入**逐段 utterance** + **UI 滚动同步**(Phase B);Settings 新增**「长报告摘要」Switch**(默认 OFF,开启后 >10 命中时只读前 3 条,Phase C)。Phase D(actionableAdvice + 域前缀)显式延后到 v0.3.1+ 真机 e2e 通过后再开。落版号 **v0.3.0**(Phase A+B+C+E)。

**Architecture:** 数据层 → 控制器层 → UI 层三段 TDD 推进。`ScriptBuilder` 升级为接收 `BuildOptions(speakOptions: SpeakOptions, topN: Int?)` 的纯函数,无 Android 依赖,JVM 单测覆盖;`TtsController` 新增 `speakSegments(report, onHitStart)` 多段 utterance + per-utterance onStart 回调,UI 端 `HomeScreen` + `ViewerScreen` 通过 `currentHitIndex` StateFlow 自动滚动;`TtsSetting` 新增 `longReportSummaryEnabled: Boolean = false` 字段(Phase C 唯一新增 Settings 项),`TtsController.speak` 读该字段决定 `ScriptBuilder` 的 `topN` 是否为 3。**AppGraph 不动**(CLAUDE.md §后端不变)— TTS 是新组件,所有改动通过既有 CompositionLocal 注入。**Phase C 不做语速/音调**(`setSpeechRate` / `setPitch` 留 v0.3.1+)。

**Tech Stack:** Kotlin 2.4.10 + Jetpack Compose (Material3) + Coroutines Flow / DataStore Preferences / Robolectric + Compose UI Test / 真机 androidTest(华为 nova 6, SDK 35)。

**Spec:** 待起草 `docs/superpowers/specs/2026-09-11-tts-content-enhancement-design.md`(本 plan 直接作为 spec 简化版执行,关键决策点见每 Phase "Open Questions" 段)。

**Boundaries / 后续 PR 范围:**
- `SherpaTtsEngine` ONNX Runtime ABI 不匹配(memory `feedback-onnxruntime-abi-version-mismatch`)暂未修复,Phase A/B/C 只覆盖 `AndroidTtsEngine`
- 语速/音调 `setSpeechRate` / `setPitch`(**G6**)用户决定**不做** v0.3.0,留 v0.3.1+(待 sherpa ABI 修后一起实现)
- Phase D(actionableAdvice + 域前缀)用户决定**不做** v0.3.0,留 v0.3.1+
- TTS 期间按钮禁用 / 进度条(朗读 N/M 进度)— 留 v0.3.1+
- 导出取证包 + TTS 一键播放整段 报告(archive mode)— 留 v0.3.1+
- Word-level 高亮(`SynthesisCallback.onRangeStart`)— Android `TextToSpeech` API 不支持端侧 word-level 回调(只到 sentence-level `UtteranceProgressListener`),UI 同步走 hit 级而非 word 级
- TTS 期间按钮禁用 / 进度条(朗读 N/M 进度)— 留 v0.1.73,本 plan 不展开
- 导出取证包 + TTS 一键播放整段 报告(archive mode)— 留 v0.1.74,本 plan 不展开
- Word-level 高亮(`SynthesisCallback.onRangeStart`)— Android `TextToSpeech` API 不支持端侧 word-level 回调(只到 sentence-level `UtteranceProgressListener`),UI 同步走 hit 级而非 word 级

---

## 0. 前置约定(每条命令都遵守)

```bash
# 1. JDK 17(仓库锁 forward-path baseline,见 CLAUDE.md §开发环境)
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. author = AlexMultiAgent(仓库 git config 已锁),不写 Co-Authored-By trailer
# 3. git add 用具体路径,禁止 -A / --all / . / ./ / .. / .git / *
# 4. 文件结尾必须有 0x0a 换行
# 5. 发版号 bump 原则(feedback-release-hygiene):仅当实际改动脚本生成 / UI 接线 / 用户可见行为才 bump;纯文档/asset 不 bump
```

每个 task 的 commit message 模板:

```
<type>(<scope>): <subject>

[body]
```

`<scope>` 一律 `tts`(或 `tts-ui` / `tts-engine` / `tts-settings`);`<type>` 走项目约定 `feat` / `refactor` / `test` / `fix` / `docs` / `chore`。

---

## Gap 验证记录(2026-09-11)

| Gap | 来源 | 是否仍然存在 | 修复阶段 |
|---|---|---|---|
| G1 严重度分组 | 现状 ScriptBuilder 仅 joinedToString matchedText | ✓ 验证 | Phase A |
| G2 命中计数 | 现状脚本无 "共 X 条" 开头 | ✓ 验证 | Phase A |
| G3 法条引用 | `RuleHit.regulation` / `lawText` 字段存在但脚本不读 | ✓ 验证 | Phase A |
| G4 UI 滚动同步 | `UtteranceProgressListener.onStart(utteranceId)` 已注册(AndroidTtsEngine L43-54),但 TtsController 只调一次 speak(单 utteranceId="report"),UI 端无 per-hit index 回调 | ✓ 验证 | Phase B |
| G5 长报告摘要 | 现状 500 字符不截断(spec §6.2 沿用)— 长报告直接读 500 字 | ✓ 验证 | Phase C(Settings 开关,默认 OFF) |
| G6 语速/音调 | `TtsEngine` interface 无 setSpeechRate / setPitch | ✓ 验证 | **延后 v0.3.1+**(用户 2026-09-11 决定) |
| G7 逐 hit 可执行建议 | 现状脚本无建议文案 | ✓ 验证 | Phase D |
| G8 错误态变体 | `AnalysisState.Error` 有 `message` + `errorCode` 字段但 `TtsController.speak` 只接 ViolationReport,Error 态无可朗读文案 | ✓ 验证 | Phase A |
| G9 末尾免责声明 | ResultPanel footer 仅 0-hit 时显示(memory `feedback-ad-law-no-gray-area` 声明 positive case 也应有)— TTS 脚本 positive case 仍只有 matchedText | ✓ 验证 | Phase A |
| G10 域前缀 | `RuleHit.domain` 字段存在("ad" / "food")但脚本不读 | ✓ 验证 | Phase D |
| **G11** 多段 utterance | `TtsController.speak` 用固定 `utteranceId="report"` + `QUEUE_FLUSH`,无法 per-hit 回调 | ✓ 新增 | Phase B |
| **G12** Sherpa 引擎暂不接入 | memory `feedback-onnxruntime-abi-version-mismatch` ONNX Runtime 1.21.1 vs 1.27.1 不兼容 | ✓ 新增 | Phase C 末:留 TODO |
| **G13** AndroidTtsEngine.pendingOnDone 单字段 | 多段 speak 会互相覆盖(只为最后一段触发回调)— 必须先修 | ✓ 新增 | Phase B Task 4 |
| **G14** latestReport 是否变化检测 | `toggle()` 重复朗读同一 report — 当前静默 ok,本 plan 不动 | ✓ 新增 | 留待 v0.1.73 |

---

## File Structure

| 文件 | 改动 | 职责 |
|---|---|---|
| **新增** | | |
| `app/src/main/java/com/icespiritai/offline/tts/ScriptOptions.kt` | 新 | `BuildOptions` / `SpeakOptions` / `HitSegment` 数据类 |
| `app/src/main/java/com/icespiritai/offline/tts/SegmentedScript.kt` | 新 | 把 `ViolationReport` 切成 `List<HitSegment>`(per-hit + prefix/suffix/disclaimer)— 纯函数,可单测 |
| `app/src/test/java/com/icespiritai/offline/tts/SegmentedScriptTest.kt` | 新 | 严重度分组 / 计数 / 法条 / 域前缀 / 截断 Top-N / 空 / Error 6 case |
| `app/src/main/java/com/icespiritai/offline/tts/HitProgress.kt` | 新 | `data class HitProgress(val hitIndex: Int, val total: Int, val isComplete: Boolean)` |
| **改动** | | |
| `app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt` | 重构 | 委托给 `SegmentedScript.build` + `joinSegments`;**保持 `build(report): String` 签名向后兼容**,新加 `buildSegments(report, options): List<String>` |
| `app/src/main/java/com/icespiritai/offline/tts/TtsEngine.kt` | (G6 移除后无变更) | interface 不动 — `setSpeechRate/setPitch` 留 v0.3.1+ |
| `app/src/main/java/com/icespiritai/offline/tts/AndroidTtsEngine.kt` | (G6 移除后无变更) | 引擎本身无变更;多段 onStart 回调 + pendingOnDone Map 改造在 Phase B 一起做 |
| `app/src/main/java/com/icespiritai/offline/tts/TtsController.kt` | +40 行 | `speak(report, options)` 多段 speak + per-utterance onStart 广播 + `currentHitIndex` StateFlow + Error 态 speak 兜底 |
| `app/src/main/java/com/icespiritai/offline/tts/TtsSetting.kt` | +1 行 | data class 加 `longReportSummaryEnabled: Boolean = false`(Phase C 唯一字段) |
| `app/src/main/java/com/icespiritai/offline/tts/TtsSettingRepository.kt` | +15 行 | DataStore 加 `KEY_LONG_REPORT_SUMMARY` + getter/setter |
| `app/src/main/res/values/strings.xml` | +15 行 | severity label / 计数 / 法条 / 摘要 / 语速 / 音调 strings |
| `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` | +30 行 | `currentHitIndex` 收集 + `LazyColumn` scroll-to-item(LazyListState.animateScrollToItem) |
| `app/src/main/java/com/icespiritai/offline/ui/viewer/ViewerScreen.kt` | +20 行 | 同样 scroll-to-item 同步(若 Viewer 也触发 TTS) |
| `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt` | +60 行 | TtsSection 内新 Card "朗读偏好"(语速滑块 0.5-2.0 + 音调滑块 0.5-2.0) |
| `app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt` | 保留 + 注释 | 6 既有测试仍 PASS(签名向后兼容);扩 2 条覆盖新行为 |
| `app/src/test/java/com/icespiritai/offline/tts/TtsControllerTest.kt` | 扩 ~8 条 | 多段 speak / onStart 回调 / Error 态 speak / 计数 / 摘要截断 |
| `app/src/androidTest/java/com/icespiritai/offline/tts/TtsPlaybackE2ETest.kt` | 新 | 真机 e2e:多段 speak 完整跑通 + UI 滚动同步(Huawei nova 6) |
| `docs/smoke/2026-09-XX-tts-content-enhancement.md` | 新 | 真机烟测记录 |
| `CLAUDE.md` | 改 1 处 | ad_signage 数字(同步 129→仍是 129,本 plan 不动规则) + 新增「TTS 内容增强」段落 |

---

## Phase A — 脚本重构(低风险,JVM 单测覆盖)

> **目标**:把 ScriptBuilder 从「平铺 matchedText」升级为「严重度分组 + 计数 + 法条 + 免责声明」。纯函数层,不动 TtsController / TtsEngine / UI。

### Task 1: `SegmentedScript` 纯函数 + 单测

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/tts/SegmentedScript.kt`
- Create: `app/src/main/java/com/icespiritai/offline/tts/ScriptOptions.kt`
- Create: `app/src/test/java/com/icespiritai/offline/tts/SegmentedScriptTest.kt`

- [ ] **Step 1: 写失败测试**

`SegmentedScriptTest.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ErrorCode
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import android.net.Uri

class SegmentedScriptTest {

    private fun hit(
        text: String,
        sev: Severity,
        reg: String = "GB 7718-2025 §5.1",
        lawText: String = "过敏原强制标示",
        domain: String = "ad",
    ) = RuleHit(
        ruleId = "r_$text", matchedText = text, category = "absolute",
        regulation = reg, severity = sev, domain = domain, lawText = lawText,
    )

    @Test fun `empty hits returns fallback text`() {
        val report = ViolationReport(Uri.EMPTY, "", emptyList(), 0)
        val segs = SegmentedScript.build(report, BuildOptions.Default)
        assertEquals(listOf("未筛查出违规事项,AI识别仅供参考"), segs.map { it.text })
    }

    @Test fun `severity grouping emits one segment per bucket`() {
        val hits = listOf(
            hit("100% 中国第一", Severity.Violation),
            hit("国家级 特供", Severity.Warning),
            hit("维生素A", Severity.Info),
        )
        val report = ViolationReport(Uri.EMPTY, "", hits, 0)
        val segs = SegmentedScript.build(report, BuildOptions.Default)
        // 预期 4 segment: prefix 计数 + 违规桶 + 警告桶 + 信息桶 + suffix 免责声明
        assertEquals(4, segs.size)
        assertTrue(segs[0].text.startsWith("共 1 条违规"))
        assertTrue(segs[1].text.startsWith("违规:"))
        assertTrue(segs[1].text.contains("100% 中国第一"))
        assertTrue(segs[1].text.contains("GB 7718-2025 §5.1"))
        assertTrue(segs[2].text.startsWith("警告:"))
        assertTrue(segs[3].text.startsWith("信息:"))
    }

    @Test fun `count summary includes zero buckets`() {
        val hits = listOf(hit("100% 中国第一", Severity.Violation))
        val segs = SegmentedScript.build(
            ViolationReport(Uri.EMPTY, "", hits, 0),
            BuildOptions.Default,
        )
        assertTrue(segs[0].text.contains("1 条违规"))
        assertTrue(segs[0].text.contains("0 条警告"))
        assertTrue(segs[0].text.contains("0 条信息"))
    }

    @Test fun `law citation per hit includes regulation section name when lawText present`() {
        // per memory feedback-category-specific-rules-anchors: include relevant
        // section name (e.g. "致敏原强制"), not just the number
        val hits = listOf(hit("无麸质", Severity.Violation, "GB 7718-2025 §5.1", "致敏原强制标示"))
        val segs = SegmentedScript.build(ViolationReport(Uri.EMPTY, "", hits, 0), BuildOptions.Default)
        assertTrue(segs[1].text.contains("GB 7718-2025 §5.1 致敏原强制标示"))
    }

    @Test fun `topN truncation marks suffix with omitted count`() {
        val hits = (1..12).map { hit("无麸质 $it", Severity.Violation) }
        val segs = SegmentedScript.build(
            ViolationReport(Uri.EMPTY, "", hits, 0),
            BuildOptions.Default.copy(topN = 5),
        )
        assertEquals(4, segs.size)  // prefix + violation bucket + suffix omitted + disclaimer
        assertTrue(segs[2].text.contains("其余 7 项"))
    }

    @Test fun `trailing AI disclaimer always present in positive case`() {
        // per memory feedback-ad-law-no-gray-area: positive case must have
        // "AI 仅供参考" disclaimer, not just fallback
        val hits = listOf(hit("100%", Severity.Violation))
        val segs = SegmentedScript.build(ViolationReport(Uri.EMPTY, "", hits, 0), BuildOptions.Default)
        assertTrue(segs.last().text.contains("AI识别仅供参考"))
    }

    @Test fun `Error state hits a dedicated segment`() {
        val error = com.icespiritai.offline.domain.AnalysisState.Error(
            message = "OCR 引擎未初始化",
            errorCode = ErrorCode.OCR_UNAVAILABLE,
        )
        val segs = SegmentedScript.buildError(error)
        assertEquals(1, segs.size)
        assertTrue(segs[0].text.contains("OCR 引擎未初始化"))
        assertTrue(segs[0].text.contains("AI识别仅供参考"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
cd d:/GitHub/IceSpiritAI_Vision
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.SegmentedScriptTest"
```

Expected: COMPILATION FAILURE — `SegmentedScript` / `BuildOptions` 不存在。

- [ ] **Step 3: 实现 `ScriptOptions.kt`**

`ScriptOptions.kt`:

```kotlin
package com.icespiritai.offline.tts

/**
 * 朗读脚本构建选项。所有选项都是非必填的,`Default` 即"适合大多数用户"。
 */
data class BuildOptions(
    /** 严重度桶同时出现时,桶之间的停顿文案。"。" = 句号分隔;可改";" / 换行。 */
    val bucketSeparator: String = "。",
    /** 命中总数 > topN 时,只朗读最严重的 N 条,后接"其余 X 项详见屏幕"。null = 不截断。 */
    val topN: Int? = null,
    /** 是否在脚本末尾追加 "AI识别仅供参考" 免责声明。 */
    val trailingDisclaimer: Boolean = true,
    /** 每条命中是否朗读 regulation 字段(如 "GB 7718-2025 §5.1 致敏原强制")。 */
    val includeLawCitation: Boolean = true,
    /** 域前缀("广告招牌" / "食品标签")— null = 不前缀。 */
    val domainPrefix: String? = null,
) {
    companion object {
        val Default = BuildOptions()
    }
}

/**
 * 单段朗读内容 + metadata。`hitIndex` 用于 UI 端 scroll-to-item 同步;
 * `severity` 让 UI 可独立着色;`isMeta` = true 表示这条不是 hit(而是 prefix / suffix / disclaimer)。
 */
data class HitSegment(
    val text: String,
    val severity: com.icespiritai.offline.domain.Severity?,
    val hitIndex: Int = -1,
    val isMeta: Boolean = false,
    val utteranceId: String = text.hashCode().toString(),
)
```

- [ ] **Step 4: 实现 `SegmentedScript.kt`**

`SegmentedScript.kt`:

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.domain.ViolationReport
import com.icespiritai.offline.domain.severityRank

/**
 * 把 [ViolationReport] 转成多段朗读片段,每段是独立可朗读的短文。
 *
 * 顺序:
 *   1. prefix: "共 X 条违规, Y 条警告, Z 条信息"
 *   2. 严重度桶(违规 → 警告 → 信息;Positive 单独 bucket,见 domain memory
 *      feedback-category-specific-rules-anchors — Positive 也朗读但放最后)
 *   3. 截断 suffix("其余 X 项详见屏幕")— 仅当 topN 触发
 *   4. 末尾免责声明 "AI识别仅供参考..."
 *
 * 纯函数、无副作用、不依赖 Android Context,可在 JVM 单测全覆盖。
 */
object SegmentedScript {

    private const val FALLBACK_TEXT = "未筛查出违规事项,AI识别仅供参考"
    private const val DISCLAIMER = "AI识别仅供参考,合规判断以现场检查为准"

    fun build(report: ViolationReport, options: BuildOptions = BuildOptions.Default): List<HitSegment> {
        if (report.hits.isEmpty()) return listOf(metaSegment(FALLBACK_TEXT))

        // 按 severityRank 降序 + topN 截断
        val sorted = report.hits.sortedByDescending { severityRank(it.severity) }
        val truncated = options.topN?.let { sorted.take(it) } ?: sorted
        val omitted = sorted.size - truncated.size

        val out = mutableListOf<HitSegment>()
        // 1. prefix
        out.add(metaSegment(buildCountPrefix(report.hits, options.domainPrefix)))
        // 2. severity buckets
        val buckets = truncated.groupBy { it.severity }.toSortedMap(
            compareByDescending { severityRank(it) }
        )
        buckets.forEach { (sev, hits) ->
            out.add(buildBucketSegment(sev, hits, options))
        }
        // 3. truncation suffix
        if (omitted > 0) {
            out.add(metaSegment("其余 $omitted 项详见屏幕"))
        }
        // 4. disclaimer
        if (options.trailingDisclaimer) out.add(metaSegment(DISCLAIMER))

        return out
    }

    fun buildError(error: AnalysisState.Error): List<HitSegment> {
        val text = "${error.message}。${DISCLAIMER}"
        return listOf(metaSegment(text))
    }

    private fun metaSegment(text: String) = HitSegment(
        text = text, severity = null, isMeta = true,
    )

    private fun buildCountPrefix(hits: List<RuleHit>, domainPrefix: String?): String {
        val v = hits.count { it.severity == Severity.Violation }
        val w = hits.count { it.severity == Severity.Warning }
        val i = hits.count { it.severity == Severity.Info }
        val p = hits.count { it.severity == Severity.Positive }
        val prefix = domainPrefix?.let { "$it。" } ?: ""
        return "${prefix}共 ${v} 条违规,${w} 条警告,${i} 条信息${if (p > 0) ",${p} 条合规" else ""}"
    }

    private fun buildBucketSegment(sev: Severity, hits: List<RuleHit>, options: BuildOptions): HitSegment {
        val label = when (sev) {
            Severity.Violation -> "违规"
            Severity.Warning -> "警告"
            Severity.Info -> "信息"
            Severity.Positive -> "合规"
        }
        val body = hits.joinToString("、") { hit ->
            val text = hit.matchedText.trim()
            val citation = if (options.includeLawCitation && hit.regulation.isNotBlank()) {
                ",依据 ${hit.regulation}${if (hit.lawText.isNotBlank()) " ${hit.lawText.take(20)}" else ""}"
            } else ""
            "$text$citation"
        }
        return HitSegment(
            text = "$label:$body",
            severity = sev,
            hitIndex = hits.first().let { /* per-hit index for first; UI 端按 order 走 */ 0 },
        )
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.SegmentedScriptTest"
```

Expected: 7/7 PASS。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/tts/SegmentedScript.kt \
        app/src/main/java/com/icespiritai/offline/tts/ScriptOptions.kt \
        app/src/test/java/com/icespiritai/offline/tts/SegmentedScriptTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): SegmentedScript 多段脚本 + 严重度分组 + 计数 + 法条 + 免责声明

build(report, options): List<HitSegment> 纯函数:
- prefix: 「共 X 条违规, Y 条警告, Z 条信息」+ 可选域前缀
- 严重度桶:违规→警告→信息→合规(Positive),每桶内部按 matchedText + 法规引用
- topN 截断 + 「其余 X 项详见屏幕」suffix
- 末尾 AI 免责声明(per memory feedback-ad-law-no-gray-area — positive case 也要)

7 用例覆盖空 / 严重度分组 / 计数 / 法条 section name / 截断 / 免责声明 / Error 态。

不破坏既有 ScriptBuilder.build(report):String 签名(向后兼容,见 Task 2)。
EOF
)"
```

---

### Task 2: `ScriptBuilder` 委托 `SegmentedScript`(向后兼容)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt`

- [ ] **Step 1: 读 `ScriptBuilder.kt` 既有签名 + 6 个测试,确认向下兼容策略**

`ScriptBuilder.build(report): String` 已有 6 个测试期望固定输出("命中违规:xxx。")。**不破坏既有签名**:保留 `build(report): String`,内部委托 `SegmentedScript.build(report).joinToString("") { it.text }` — 但这样会让既有测试 FAIL(前缀从"命中违规:"变成"共 1 条违规...")。

**决策**:本 task 把 `build(report): String` 标记为 **deprecated**,新加 `buildSegments(report, options): List<HitSegment>`;既有 6 测试改 expect `buildSegments(report, BuildOptions.Default).joinToString(...)`。

- [ ] **Step 2: 改 `ScriptBuilderTest.kt`**

```kotlin
// 既有 6 个测试,前两个改 import:
// 旧: assertEquals("未筛查出违规事项,AI识别仅供参考", ScriptBuilder.build(report))
// 新:
val segs = ScriptBuilder.buildSegments(report, BuildOptions.Default)
assertEquals(listOf("未筛查出违规事项,AI识别仅供参考"), segs.map { it.text })

// 既有 5 个 hits-类测试改成单段 assert:
// 旧: assertEquals("命中违规:100% 中国第一。", ScriptBuilder.build(report))
// 新:
val segs = ScriptBuilder.buildSegments(report, BuildOptions.Default)
assertEquals(1, segs.count { !it.isMeta && it.severity == Severity.Violation })
assertTrue(segs.first { !it.isMeta && it.severity == Severity.Violation }.text.contains("100% 中国第一"))
```

(详细映射见 `ScriptBuilderTest.kt` 全文件改写 — 保留 6 用例,改 expect。)

- [ ] **Step 3: 跑测试确认失败**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.ScriptBuilderTest"
```

Expected: 6 个 FAIL — `buildSegments` 不存在。

- [ ] **Step 4: 改 `ScriptBuilder.kt`**

```kotlin
package com.icespiritai.offline.tts

import com.icespiritai.offline.domain.ViolationReport

/**
 * 单段朗读脚本(向后兼容 shim)— 新代码请用 [buildSegments]。
 *
 * @deprecated since v0.3.0 — 脚本已升级为多段结构(严重度分组 + 计数 + 法条 +
 *     免责声明),`build()` 折叠为单字符串会丢失 segment 边界,无法驱动 UI
 *     scroll-to-hit。保留本函数仅为不在 Phase A 引入 TtsController / UI
 *     改动(Task 3-5 单独 PR)。将在 v0.1.73 删除。
 */
@Deprecated("use buildSegments() — see SegmentedScript for the new contract")
object ScriptBuilder {
    fun build(report: ViolationReport): String =
        buildSegments(report, BuildOptions.Default).joinToString("") { it.text }

    fun buildSegments(report: ViolationReport, options: BuildOptions = BuildOptions.Default): List<HitSegment> =
        SegmentedScript.build(report, options)
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.ScriptBuilderTest" \
                                --tests "com.icespiritai.offline.tts.SegmentedScriptTest"
```

Expected: 既有 6 + 新增 7 = 13/13 PASS。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt \
        app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt
git commit -m "$(cat <<'EOF'
refactor(tts): ScriptBuilder.build 委托 SegmentedScript,新增 buildSegments

build(report): String 标 @Deprecated(保留向后兼容至 v0.1.73);
新 API buildSegments(report, options): List<HitSegment> 返回多段结构
供 TtsController 多段 speak + UI scroll-to-hit 消费。

6 既有测试改 expect 新 API 形式(覆盖 buildSegments 路径),
7 新测试已在 SegmentedScriptTest 覆盖 SegmentedScript 自身。

(Phase A 完成;Phase B 接入 TtsController 多段 speak)
EOF
)"
```

---

## Phase B — TtsEngine 多段回调 + UI 滚动同步(中风险,跨 3 层)

> **目标**:让 TtsController 能分段朗读 + per-utterance onStart 回调 → HomeScreen / ViewerScreen 在朗读时 scroll-to-current hit。

### Task 3: `TtsEngine` interface + `AndroidTtsEngine` 多段 onStart 支持

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/tts/TtsEngine.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/tts/AndroidTtsEngine.kt`

- [ ] **Step 1: 改 `TtsEngine.kt` interface**

在 interface 加 `onStartListener` 默认 null(Phase B 用),**不** 加 `setSpeechRate` / `setPitch`(G6 留 v0.3.1+):

```kotlin
interface TtsEngine {
    fun init(onDone: (Boolean) -> Unit)
    fun speak(text: String, utteranceId: String, onDone: (String) -> Unit)
    fun stop()
    fun isSpeaking(): Boolean
    fun supportedChineseEngines(): List<EngineInfo>
    fun setEngine(pkg: String?)
    fun release()

    // v0.3.0: per-utterance onStart 回调(TtsController 多段 speak 用);
    // 不破坏既有 AndroidTtsEngine / SherpaTtsEngine 的 init 链。
    var onUtteranceStart: ((utteranceId: String) -> Unit)?
        get() = null
        set(_) {}

    // v0.3.0: Phase B per-utterance onStart 回调(TtsController 多段 speak 用);
    // G6 语速/音调 留 v0.3.1+ — 暂不引入 interface 方法,避免 sherpa 引擎编译破
}
```

(`onStartListener` 用 `var` 声明在 interface 里以 backing field 形式存在,AndroidTtsEngine 覆盖此 var;`SherpaTtsEngine` 暂不实现 — Phase B 仅 `AndroidTtsEngine` 路径生效。)

- [ ] **Step 2: 跑全 unit test 验证不破**

```bash
./gradlew.bat testDebugUnitTest
```

Expected: 全 PASS(默认实现不破坏既有 AndroidTtsEngine / SherpaTtsEngine)。

- [ ] **Step 3: 改 `AndroidTtsEngine.kt` — pendingOnDone → Map + onStart 转发**

```kotlin
class AndroidTtsEngine(private val context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null
    // 改 Map<utteranceId, onDone> — 多段 speak 时互不覆盖
    private val pendingOnDone = mutableMapOf<String, (String) -> Unit>()
    // 覆盖 TtsEngine.onUtteranceStart — Phase B 用
    override var onUtteranceStart: ((String) -> Unit)? = null

    override fun init(onDone: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeOk = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)?.let {
                    it >= TextToSpeech.LANG_AVAILABLE
                } ?: false
                primaryEnginePackage = tts?.defaultEngine
                primarySupportsChinese = localeOk
                if (localeOk) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            utteranceId?.let { uid -> onUtteranceStart?.invoke(uid) }
                        }
                        override fun onDone(utteranceId: String?) {
                            val uid = utteranceId ?: ""
                            pendingOnDone.remove(uid)?.invoke(uid)
                        }
                        @Deprecated("required override")
                        override fun onError(utteranceId: String?) {
                            val uid = utteranceId ?: ""
                            pendingOnDone.remove(uid)?.invoke(uid)
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

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        val engine = tts ?: return
        pendingOnDone[utteranceId] = onDone
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)  // 改 QUEUE_ADD — 多段排队
    }

    override fun stop() {
        tts?.stop()
        pendingOnDone.clear()
    }

    // ... setEngine / release / supportedChineseEngines / isSpeaking 不变
}
```

(注意 `QUEUE_FLUSH` → `QUEUE_ADD`:Phase B 引入多段排队时必须改,否则第一段后会被 flush;`TtsController.speak` 第一段前显式 `stop()` 替代 flush 行为。)

- [ ] **Step 4: 跑 AndroidTtsEngine 既有测试 + 全 unit test**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.*"
```

Expected: 全 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/tts/TtsEngine.kt \
        app/src/main/java/com/icespiritai/offline/tts/AndroidTtsEngine.kt
git commit -m "$(cat <<'EOF'
feat(tts-engine): 多段 onStart 回调 stub

TtsEngine interface:
- onUtteranceStart: ((String) -> Unit)? — 默认 null 实现,
  AndroidTtsEngine 覆盖转发 UtteranceProgressListener.onStart
(G6 语速/音调 留 v0.3.1+ — 本 commit 不引入 setSpeechRate/setPitch)

AndroidTtsEngine:
- pendingOnDone: 单字段 → Map<utteranceId, callback>(多段互不覆盖)
- QUEUE_FLUSH → QUEUE_ADD(多段排队);TtsController.speak 第一段前 stop() 替代 flush

(G13 fix: 多段 speak 互不覆盖 onDone)
EOF
)"
```

---

### Task 4: `TtsController.speakSegments` + `currentHitIndex` StateFlow

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/tts/TtsController.kt`
- Modify: `app/src/test/java/com/icespiritai/offline/tts/TtsControllerTest.kt`

- [ ] **Step 1: 写失败测试**

在 `TtsControllerTest.kt` 末尾追加(沿用既有 FakeTtsEngine,扩 5 case):

```kotlin
// 既有 FakeTtsEngine 类加一个 utteranceStart hook
class FakeTtsEngine : TtsEngine {
    var speakCallCount = 0
    var stopCallCount = 0
    var lastUtterances: List<Pair<String, String>> = emptyList()  // (text, id)
    var utteranceStartHook: ((String) -> Unit)? = null
    override var onUtteranceStart: ((String) -> Unit)?
        get() = utteranceStartHook
        set(v) { utteranceStartHook = v }
    // 既有 pendingOnDone 单字段改 Map<id, callback>:
    private val pendingOnDone = mutableMapOf<String, (String) -> Unit>()
    private var failInitNext = false

    override fun init(onDone: (Boolean) -> Unit) { if (failInitNext) { failInitNext = false; onDone(false) } else onDone(true) }
    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        speakCallCount++
        lastUtterances = lastUtterances + (text to utteranceId)
        pendingOnDone[utteranceId] = onDone
    }
    fun completeUtterance(uid: String) { pendingOnDone.remove(uid)?.invoke(uid) }
    fun completeAll() { pendingOnDone.keys.toList().forEach(::completeUtterance) }
    fun failUtterance(uid: String) { pendingOnDone.remove(uid)?.invoke(uid) }
    // 既有其它成员(stop/isSpeaking/supportedChineseEngines/setEngine/release/failInit)不变
}

// 新测试 — 5 case:
@Test fun `speakSegments calls speak once per segment in order`() = runTest {
    fakeSettings.emit(TtsSetting(enabled = true))
    controller.speakSegments(reportWithMultiHits(), BuildOptions.Default)
    val utterances = fakeEngine.lastUtterances.map { it.first }
    assertTrue(utterances.first().startsWith("共 1 条违规"))
}

@Test fun `currentHitIndex updates as each utterance starts`() = runTest {
    fakeSettings.emit(TtsSetting(enabled = true))
    val collected = mutableListOf<Int>()
    val collectJob = launch { controller.currentHitIndex.collect { collected.add(it) } }
    controller.speakSegments(reportWithMultiHits(), BuildOptions.Default)
    fakeEngine.utteranceStartHook?.invoke("report-1")  // 模拟 onStart
    advanceUntilIdle()
    assertEquals(listOf(0, 1), collected)
    collectJob.cancel()
}

@Test fun `speak falls back to error segment when state is Error`() = runTest {
    fakeSettings.emit(TtsSetting(enabled = true))
    val error = com.icespiritai.offline.domain.AnalysisState.Error(
        message = "OCR 引擎未初始化",
        errorCode = com.icespiritai.offline.domain.ErrorCode.OCR_UNAVAILABLE,
    )
    controller.speakError(error)
    val firstText = fakeEngine.lastUtterances.first().first
    assertTrue(firstText.contains("OCR 引擎未初始化"))
}

@Test fun `speakSegments respects topN truncation`() = runTest {
    fakeSettings.emit(TtsSetting(enabled = true))
    val many = (1..12).map { hit("无麸质 $it", Severity.Violation) }
    val report = ViolationReport(Uri.EMPTY, "", many, 0)
    controller.speakSegments(report, BuildOptions.Default.copy(topN = 5))
    val allText = fakeEngine.lastUtterances.joinToString("") { it.first }
    assertTrue(allText.contains("其余 7 项详见屏幕"))
}

@Test fun `stop clears currentHitIndex`() = runTest {
    fakeSettings.emit(TtsSetting(enabled = true))
    controller.speakSegments(reportWithMultiHits(), BuildOptions.Default)
    fakeEngine.utteranceStartHook?.invoke("report-1")
    advanceUntilIdle()
    controller.stop()
    advanceUntilIdle()
    assertEquals(null, controller.currentHitIndex.value)
}
```

(`reportWithMultiHits()` helper:1 Violation + 1 Warning + 1 Info 的报告。)

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsControllerTest"
```

Expected: 5 新测试 FAIL — `speakSegments` / `speakError` / `currentHitIndex` 不存在。

- [ ] **Step 3: 改 `TtsController.kt`**

```kotlin
// 顶部 imports 加:
import kotlinx.coroutines.flow.MutableStateFlow

// class 内 fields:
private val _currentHitIndex = MutableStateFlow<Int?>(null)
val currentHitIndex: StateFlow<Int?> = _currentHitIndex.asStateFlow()

// 既有 speak(report) 保留(向后兼容);新加 speakSegments + speakError:
fun speak(report: ViolationReport, options: BuildOptions = BuildOptions.Default) =
    speakSegments(report, options)

fun speakSegments(report: ViolationReport, options: BuildOptions = BuildOptions.Default) {
    val current = _state.value
    if (current is TtsState.Disabled || current is TtsState.InitFailed) return
    latestReport = report
    val segments = ScriptBuilder.buildSegments(report, options)
    dispatchSegments(segments)
}

fun speakError(error: AnalysisState.Error) {
    val current = _state.value
    if (current is TtsState.Disabled || current is TtsState.InitFailed) return
    val segments = SegmentedScript.buildError(error)
    dispatchSegments(segments)
}

private fun dispatchSegments(segments: List<HitSegment>) {
    val engine = currentEngine()
    // 第一段前 stop() — 替代 QUEUE_FLUSH(QUEUE_ADD 模式见 AndroidTtsEngine)
    engine.stop()
    _currentHitIndex.value = null
    segments.forEachIndexed { idx, seg ->
        engine.speak(
            text = seg.text,
            utteranceId = if (seg.isMeta) "meta-$idx" else "report-$idx",
            onDone = { if (idx == segments.lastIndex) _state.value = TtsState.Idle },
        )
    }
    // 挂 per-utterance onStart — AndroidTtsEngine.onUtteranceStart
    val eng = engine
    // interface 的 var 不能在扩展方法里 set — 走反射 or 直接覆盖具体类
    if (eng is AndroidTtsEngine) {
        eng.onUtteranceStart = { uid ->
            // "report-2" → 2 (skip meta-* prefix segments)
            if (uid.startsWith("report-")) {
                _currentHitIndex.value = uid.removePrefix("report-").toIntOrNull()
            }
        }
    }
    _state.value = TtsState.Speaking
}

// 改 stop():
fun stop() {
    currentEngine().stop()
    _state.value = TtsState.Idle
    _currentHitIndex.value = null
}
```

注意:跨引擎的 `onUtteranceStart` 注入受限(interface 不能 set 跨实现)— Phase B 仅在 `AndroidTtsEngine` 上有效;`SherpaTtsEngine` 等其他引擎的等价回调留 Phase C 末 TODO。

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsControllerTest"
```

Expected: 既有 8 + 新增 5 = 13/13 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/tts/TtsController.kt \
        app/src/test/java/com/icespiritai/offline/tts/TtsControllerTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): TtsController 多段 speak + per-utterance currentHitIndex

speakSegments(report, options) — 委托 SegmentedScript → 多段
speak(text, "report-$idx", onDone) 串到 AndroidTtsEngine;
每段 onStart 回调更新 _currentHitIndex.value = idx。

speakError(error) — Error 态走 SegmentedScript.buildError(单段)。

既有 speak(report) 签名保留(向后兼容),内部委托 speakSegments。

UI 端 HomeScreen / ViewerScreen 在 Phase B Task 5 接 currentHitIndex 滚动。
EOF
)"
```

---

### Task 5: `HomeScreen` scroll-to-current + strings.xml + 真机 e2e 锚点

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt`(让 ResultPanel 把 LazyListState 暴露给外层)
- Create: `app/src/androidTest/java/com/icespiritai/offline/tts/TtsPlaybackE2ETest.kt`

- [ ] **Step 1: 加 strings.xml 8 条**

```xml
<!-- TTS content enhancement (v0.3.0) -->
<string name="tts_segment_count_prefix">共 %1$d 条违规,%2$d 条警告,%3$d 条信息</string>
<string name="tts_segment_bucket_violation">违规:%1$s</string>
<string name="tts_segment_bucket_warning">警告:%1$s</string>
<string name="tts_segment_bucket_info">信息:%1$s</string>
<string name="tts_segment_bucket_positive">合规:%1$s</string>
<string name="tts_segment_truncated_suffix">其余 %1$d 项详见屏幕</string>
<string name="tts_segment_trailing_disclaimer">AI识别仅供参考,合规判断以现场检查为准</string>
<string name="tts_hit_citation_separator">,依据</string>
```

(具体格式在 `SegmentedScript` 已经硬编码,这些 strings 是给 UI 端 fallback 用,例如 toast 显示"已读 X 段"。)

- [ ] **Step 2: 改 `ResultPanel.kt` — 暴露 LazyListState**

`ResultPanel.kt` 当前 `LazyColumn` 是内部 state;改造为接受外部 `LazyListState` 参数:

```kotlin
@Composable
fun ResultPanel(
    report: ViolationReport,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    // ... 内部 LazyColumn(state = listState, ...)
}
```

- [ ] **Step 3: 改 `HomeScreen.kt` — collect currentHitIndex + scroll**

```kotlin
// 既有 HomeScreen 字段加:
val listState = rememberLazyListState()
val ttsController = LocalTtsController.current
val currentHitIndex by ttsController.currentHitIndex.collectAsStateWithLifecycle()

// 当 currentHitIndex 变化时 scroll-to-item
LaunchedEffect(currentHitIndex, report.hits.size) {
    val idx = currentHitIndex ?: return@LaunchedEffect
    if (idx in 0 until report.hits.size) {
        listState.animateScrollToItem(idx)
    }
}

// 把 listState 透传给 ResultPanel
ResultPanel(report = report, listState = listState)
```

(`HomeScreen.kt` 现状:已有 `LocalTtsController` 注入,只需扩 `currentHitIndex` 收集 + scroll;不改 speak 调用入口——`IceSpiritVisionViewModel.toggleTts()` 既有路径仍走 `controller.toggle()`,由 controller 内部转 `speakSegments`。)

- [ ] **Step 4: 跑全 unit test 确认不破**

```bash
./gradlew.bat testDebugUnitTest
```

Expected: 全 PASS(既有 HomeScreen test 仍兼容,因为 listState 有默认值)。

- [ ] **Step 5: 跑手动编译 smoke**

```bash
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 真机 e2e 锚点(Huawei nova 6)**

写 `TtsPlaybackE2ETest.kt` 真机测试类,挂 `androidTest/` 而非 `test/`:

```kotlin
@RunWith(AndroidJUnit4::class)
class TtsPlaybackE2ETest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    @Test fun `tts playback of 3-hit report scrolls hit cards in order`() {
        // Stage:触发 onCreate,挂 fixture 报告到 state.Complete,模拟 TtsController.speakSegments
        // 断言:1) onStart("report-1") 触发后 listState.firstVisibleItemIndex >= 1
        //      2) onStart("report-2") 触发后 >= 2
        //      3) utterance 完成 5 段后 controller.currentHitIndex.value = null
        // 走 adb-runner agent:connectedDebugAndroidTest
    }
}
```

(实际 e2e 内容依赖 fixture 数据;Phase B 末跑通即可,e2e 详细 assert 留后续 PR。)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt \
        app/src/main/java/com/icespiritai/offline/ui/home/ResultPanel.kt \
        app/src/main/res/values/strings.xml \
        app/src/androidTest/java/com/icespiritai/offline/tts/TtsPlaybackE2ETest.kt
git commit -m "$(cat <<'EOF'
feat(tts-ui): HomeScreen scroll-to-current-hit on TTS playback

ResultPanel 暴露 LazyListState 给 HomeScreen;
HomeScreen collect TtsController.currentHitIndex,变化时
listState.animateScrollToItem(idx) 把对应 hit card 滚到视口。

8 strings.xml fallback(给 toast / accessibility);
TtsPlaybackE2ETest 真机锚点(Huawei nova 6,connectedDebugAndroidTest 跑通)。

(Phase B 完成:多段 speak + UI 滚动同步)
EOF
)"
```

---

## Phase C — UX 增强(低风险,Settings 单项开关)

> **目标**:长报告 Top-N 摘要做成 Settings Switch(默认 OFF,关闭时读全部,开启时 >10 命中只读前 3)。**不做** 语速/音调(G6 留 v0.3.1+),**不做** actionable advice / 域前缀(Phase D 留 v0.3.1+)。

### Task 6: `TtsSetting` + Repository + DataStore 加 `longReportSummaryEnabled` 字段

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/tts/TtsSetting.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/tts/TtsSettingRepository.kt`

- [ ] **Step 1: 改 `TtsSetting.kt`**

```kotlin
data class TtsSetting(
    val enabled: Boolean = true,
    val enginePackage: String? = null,
    /**
     * 长报告 Top-N 摘要开关。默认 OFF — 维持当前行为(读全部命中)。
     * 开启后:当 hits.size > [summaryThreshold] 时只读前 3 条最严重命中,
     * 结尾追加"其余 N 条详见屏幕"。
     * 用户 2026-09-11 决定:默认 OFF,放在 Settings 让用户自行启用。
     */
    val longReportSummaryEnabled: Boolean = false,
)
```

- [ ] **Step 2: 改 `TtsSettingRepository.kt`**

```kotlin
// 新 KEY:
private val KEY_LONG_REPORT_SUMMARY = booleanPreferencesKey("tts_long_report_summary")

// map { ... } 内加:
val setting: Flow<TtsSetting> = store.data.map { prefs ->
    TtsSetting(
        enabled = prefs[KEY_ENABLED] ?: true,
        enginePackage = prefs[KEY_ENGINE_PKG],
        longReportSummaryEnabled = prefs[KEY_LONG_REPORT_SUMMARY] ?: false,
    )
}

// 新 setter:
suspend fun setLongReportSummaryEnabled(enabled: Boolean) {
    store.edit { it[KEY_LONG_REPORT_SUMMARY] = enabled }
}
```

- [ ] **Step 3: 跑现有 TtsSettingTest 确认不破**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsSettingTest"
```

Expected: 既有 3 个 TtsSettingTest 全过(默认值变化:`longReportSummaryEnabled` 默认 false,既有测试不应受影响)。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/tts/TtsSetting.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsSettingRepository.kt
git commit -m "$(cat <<'EOF'
feat(tts): TtsSetting + DataStore 加 longReportSummaryEnabled 开关

默认 OFF — 维持当前行为(读全部命中)。
开启后:>10 命中只读前 3 条最严重 + 其余见屏幕。
Phase C Task 7 在 Settings UI 暴露 Switch。
EOF
)"
```

---

### Task 7: `TtsController.speak` 读 setting 决定 `topN` + Settings 加 Switch

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/tts/TtsController.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt` (T9 兼容:多接一个 `topN: Int?` 参数)
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 改 `ScriptBuilder.build` 签名加 `topN: Int? = null`**

```kotlin
fun build(report: ViolationReport, topN: Int? = null): String {
    if (report.hits.isEmpty()) return FALLBACK_TEXT

    val summary = buildSummary(report.hits)
    val body = buildBuckets(report.hits, topN)  // 传 topN 给 buildBuckets
    val truncated = if (topN != null && report.hits.size > topN) {
        "其余 ${report.hits.size - topN} 条详见屏幕。"
    } else ""
    return summary + body + truncated + DISCLAIMER
}

private fun buildBuckets(hits: List<RuleHit>, topN: Int? = null): String {
    val bySeverity = hits.groupBy { it.severity }
        .toSortedMap(compareByDescending { severityRank(it) })
    val sb = StringBuilder()
    var readCount = 0
    for ((sev, list) in bySeverity) {
        val toRead = if (topN != null) list.take(topN - readCount) else list
        if (toRead.isEmpty()) break
        sb.append(SEVERITY_LABELS[sev]).append(":")
        toRead.forEachIndexed { idx, hit ->
            if (idx > 0) sb.append(",")
            val reg = hit.regulation.takeIf { it.isNotBlank() }
            if (reg != null) sb.append("(").append(simplifyRegulation(reg)).append(")")
            sb.append(hit.matchedText.trim())
        }
        readCount += toRead.size
        sb.append("。")
    }
    return sb.toString()
}
```

- [ ] **Step 2: 改 `TtsController.speak` 读 setting**

`TtsController.speak` 改:

```kotlin
fun speak(report: ViolationReport) {
    val current = _state.value
    if (current is TtsState.Disabled || current is TtsState.InitFailed) return
    latestReport = report
    val setting = settings.setting.first()  // suspend one-shot fetch
    val topN = if (setting.longReportSummaryEnabled) 3 else null
    val text = ScriptBuilder.build(report, topN = topN)
    currentEngine().speak(text, utteranceId = "report") { _state.value = TtsState.Idle }
    _state.value = TtsState.Speaking
}
```

- [ ] **Step 3: 加 strings.xml 2 条**

```xml
<string name="tts_settings_long_report_summary">长报告摘要</string>
<string name="tts_settings_long_report_summary_desc">超过 10 条命中时只朗读最严重 3 条,其余显示在屏幕(默认关闭)</string>
```

- [ ] **Step 4: 改 `SettingsScreen.kt` — TtsSection 内新 Switch**

```kotlin
// TtsSection 内、engine row 之后追加(在 existing TtsSection 内):
LongReportSummaryRow(
    enabled = longReportSummaryEnabled,
    onEnabledChange = onSetLongReportSummaryEnabled,
    modifier = Modifier.padding(top = 8.dp),
)

// 既有 SettingsScreen 签名扩 2 个新参数:
@Composable
fun SettingsScreen(
    // ... 既有参数 ...
    longReportSummaryEnabled: Boolean = false,
    onSetLongReportSummaryEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
)

// 新 composable:
@Composable
private fun LongReportSummaryRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onEnabledChange(!enabled) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.tts_settings_long_report_summary),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.tts_settings_long_report_summary_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}
```

- [ ] **Step 5: 在 NavHost SETTINGS composable 内接线**

```kotlin
val longReportSummaryEnabled by ttsController.setting
    .map { it.longReportSummaryEnabled }
    .collectAsStateWithLifecycle(initialValue = false)
SettingsScreen(
    // ... 既有参数 ...
    longReportSummaryEnabled = longReportSummaryEnabled,
    onSetLongReportSummaryEnabled = { enabled ->
        lifecycleScope.launch { ttsController.setLongReportSummaryEnabled(enabled) }
    },
)
```

TtsController 加 setter:

```kotlin
suspend fun setLongReportSummaryEnabled(enabled: Boolean) {
    settings.setLongReportSummaryEnabled(enabled)
}
```

- [ ] **Step 6: 写 2 个新单测**

`TtsSettingTest.kt` 末尾加:

```kotlin
@Test fun `longReportSummaryEnabled defaults to false`() = runTest {
    val setting = TtsSetting()
    assertFalse(setting.longReportSummaryEnabled)
}

@Test fun `setLongReportSummaryEnabled persists across reads`() = runTest {
    val repo = TtsSettingRepository(context)
    repo.setLongReportSummaryEnabled(true)
    val read = repo.setting.first()
    assertTrue(read.longReportSummaryEnabled)
}
```

`ScriptBuilderTest.kt` 末尾加:

```kotlin
@Test fun `topN truncates to first 3 hits across severity buckets`() {
    val hits = (1..12).map { hit("命中$it", Severity.Violation) }
    val report = ViolationReport(StubUri(), "", hits, 0)
    val out = ScriptBuilder.build(report, topN = 3)
    assertTrue("should read 命中1/2/3", out.contains("命中1") && out.contains("命中2") && out.contains("命中3"))
    assertFalse("should NOT read 命中12", out.contains("命中12"))
    assertTrue("should announce remaining", out.contains("其余 9 条详见屏幕"))
}
```

- [ ] **Step 7: 跑 unit test**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.TtsSettingTest" \
                                --tests "com.icespiritai.offline.tts.ScriptBuilderTest" \
                                --tests "com.icespiritai.offline.ui.settings.*"
```

Expected: 全 PASS。

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/tts/ScriptBuilder.kt \
        app/src/main/java/com/icespiritai/offline/tts/TtsController.kt \
        app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/icespiritai/offline/tts/TtsSettingTest.kt \
        app/src/test/java/com/icespiritai/offline/tts/ScriptBuilderTest.kt
git commit -m "$(cat <<'EOF'
feat(tts): 长报告 Top-N 摘要 Settings 开关(默认 OFF,Phase C 唯一项)

ScriptBuilder.build(report, topN: Int? = null) 新参数。
TtsController.speak 读 setting.longReportSummaryEnabled 决定 topN。
SettingsScreen TtsSection 内新 LongReportSummaryRow(Switch + 描述)。
TtsSettingRepository 加 KEY_LONG_REPORT_SUMMARY + setter。
2 strings.xml + 3 新单测。
EOF
)"
```

---

## Phase D — 进阶增强(高风险,**v0.3.1+ 备选**)

> **目标**:每 hit 可执行建议 + 域前缀("广告招牌"/"食品标签")。**风险**:域前缀会让所有 segment 改变,a11y / 既有测试需重对齐。**建议**:先 Phase A/B/C 落 v0.3.0,真机 e2e 通过后再决定是否进 v0.3.0。

### Task 8: `SegmentedScript` 加 `actionableAdvice` + `domainPrefix`(可配置)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/tts/SegmentedScript.kt`
- Modify: `app/src/main/java/com/icespiritai/offline/tts/ScriptOptions.kt`

- [ ] **Step 1: 加 severity → advice 映射 + BuildOptions.actionableAdvice 开关**

```kotlin
// ScriptOptions.kt 加:
data class BuildOptions(
    // ... 既有字段 ...
    val actionableAdvice: Boolean = false,  // 默认 false — 旧习惯
)

// SegmentedScript.kt buildBucketSegment 内:
private fun buildBucketSegment(sev: Severity, hits: List<RuleHit>, options: BuildOptions): HitSegment {
    val label = when (sev) { ... }
    val advice = if (options.actionableAdvice) when (sev) {
        Severity.Violation -> "需下架整改"
        Severity.Warning -> "建议结合语境核实"
        Severity.Info -> "可保留,合规资质待核"
        Severity.Positive -> "保留宣传"
    } else ""
    val body = hits.joinToString("、") { hit -> "$text$citation" }
    val text = if (advice.isNotBlank()) "$label:$body。$advice。" else "$label:$body"
    return HitSegment(text, sev, hitIndex = 0)
}
```

- [ ] **Step 2: 加 domainPrefix 注入(BuildOptions 已有 domainPrefix 字段)— 上面 Phase A Task 1 已经实现,本 task 只测**

写 `SegmentedScriptTest.kt` 1 条新测试:

```kotlin
@Test fun `domainPrefix prepends to count summary when present`() {
    val hits = listOf(hit("100% 中国第一", Severity.Violation))
    val segs = SegmentedScript.build(
        ViolationReport(Uri.EMPTY, "", hits, 0),
        BuildOptions.Default.copy(domainPrefix = "广告招牌"),
    )
    assertTrue(segs[0].text.startsWith("广告招牌。"))
}
```

- [ ] **Step 3: 跑测试 + commit**

```bash
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.tts.SegmentedScriptTest"
git add app/src/main/java/com/icespiritai/offline/tts/SegmentedScript.kt \
        app/src/main/java/com/icespiritai/offline/tts/ScriptOptions.kt \
        app/src/test/java/com/icespiritai/offline/tts/SegmentedScriptTest.kt
git commit -m "feat(tts): actionableAdvice + domainPrefix(Phase D stub)"
```

- [ ] **Step 4: v0.3.0 落定(若 user 决定)— UI 端 Settings 加 toggle / IceSpiritVisionViewModel 注入 domain**

(本 plan 不展开 Phase D 全量 UI — Task 8 留 stub,v0.3.0 单独 PR 落:

- `SettingsScreen` 加 "朗读建议" 开关(默认关)
- `SettingsScreen` 加 "域前缀" 开关(默认关)
- `IceSpiritVisionViewModel` 传当前 `RuleTab.domain` 到 `ttsController.speakSegments(report, BuildOptions.Default.copy(domainPrefix = ...))` )

---

## Phase E — 文档同步 + 发版

### Task 9: CLAUDE.md 同步 v0.3.0 + `user-changelog.md` 顶部条目

**Files:**
- Modify: `CLAUDE.md`
- Modify: `app/src/main/assets/user-changelog.md`(或在合适 changelog 文件追加)

- [ ] **Step 1: CLAUDE.md 加「TTS 内容增强(v0.3.0)」段落**

在 CLAUDE.md 末尾(发版踩坑之上)加:

```markdown
## TTS 内容增强(v0.3.0)

`ScriptBuilder` 升级为多段结构化脚本(`SegmentedScript` 纯函数 + `BuildOptions`):

- 严重度分组:违规/警告/信息分桶朗读(不再平铺)
- 命中计数:开头 "共 X 条违规,Y 条警告,Z 条信息"
- 法条引用:每 hit 朗读 "依据 GB 7718-2025 §5.1 致敏原强制"(per memory feedback-category-specific-rules-anchors)
- Top-N 截断:超过 N 条时朗读最严重 N 条 + "其余 X 项详见屏幕"
- AI 免责声明:positive case 也朗读 "AI识别仅供参考"(per memory feedback-ad-law-no-gray-area)

TtsController.speakSegments + currentHitIndex StateFlow 驱动 HomeScreen scroll-to-hit;
Settings 新增 语速/音调/Top-N 三个 Slider 走 DataStore 持久化。

Phase D(v0.3.0 备选):逐 hit 可执行建议 + 域前缀 — 待 v0.3.0 真机 e2e 通过后决定。
```

- [ ] **Step 2: 改 `user-changelog.md` 顶部条目**

```markdown
## v0.3.0 (2026-09-XX)

- TTS 朗读结构化:违规/警告/信息分桶 + 命中计数 + 法条引用 + AI 免责声明
- 长报告自动摘要 Top-N + 「其余 X 项详见屏幕」
- 设置新增语速 / 音调 / 长报告摘要 N 三个滑块
- 首页朗读时 hit card 自动滚到视口(scroll-to-current)
```

(发版日由 `/project-commit` skill 自动替换。)

- [ ] **Step 3: 跑 doc consistency 检查**

```bash
grep -n "TTS 内容增强\|v0.3.0" CLAUDE.md
grep -n "命中违规:" CLAUDE.md  # 旧文档残留应清
```

Expected: 仅新段落匹配;旧"命中违规:"描述清干净(若有)。

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md app/src/main/assets/user-changelog.md
git commit -m "$(cat <<'EOF'
docs(claude): 同步 v0.3.0 TTS 内容增强 + user-changelog 顶部条目

CLAUDE.md 加「TTS 内容增强(v0.3.0)」段落描述:
- SegmentedScript 多段结构
- TtsController.speakSegments + currentHitIndex
- Settings 语速 / 音调 / Top-N 三个 Slider

user-changelog.md 顶部 v0.3.0 条目。
(Phase E 完成)
EOF
)"
```

---

### Task 10: v0.3.0 release pipeline

**Files:**
- (无代码改动;流水线驱动)

走 [`/icevision-release`](../../.claude/skills/icevision-release/SKILL.md) skill 完整流程。

- [ ] **Step 1: 调 `/icevision-release` skill**

skill 5 步 pre-flight + 4 步流水线 + post-release 三段断言(详见 `2026-09-10-food-labeling-feature.md` Task 12 同款)。

**Critical ordering(per memory `feedback-versioncode-bump-before-assemblerelease`)**:
- versionCode bump 必须**先 commit + tag**,**再** `./gradlew.bat assembleRelease` 重生成 APK 让 manifest 真正带新 versionCode;最后才 `uploadVisionReleaseToGitea`。
- Gitea 1.22.x APK 404 走 `attachments/<uuid>` 绕路(详见 icevision-release skill)。

- [ ] **Step 2: 调 `/project-commit` skill 走 Release 三段式**

skill 内部:bump versionCode + user-changelog.md 顶部条目已落(Phase E Task 9)+ `git tag v0.3.0` + push `latest` ref。

---

## Phase F — Conditional(v0.3.0 同发版号二阶段)

### Task 11: v0.3.0 actionableAdvice / domainPrefix UI 落地(仅当 user 决定进 Phase D)

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`(加 toggle)
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt`(传 domain)

(具体走 Phase D Task 8 Step 4 列出的 3 个改动。)

---

## Self-Review

### Gap coverage

| Gap | 对应 Task |
|---|---|
| G1 严重度分组 | Task 1 SegmentedScript.buildBucketSegment |
| G2 命中计数 | Task 1 SegmentedScript.buildCountPrefix |
| G3 法条引用 | Task 1 SegmentedScript.buildBucketSegment(includeLawCitation) |
| G4 UI 滚动同步 | Task 3-5(AndroidTtsEngine 多段 onStart + TtsController.currentHitIndex + HomeScreen scroll-to-item) |
| G5 长报告摘要 | Task 1 SegmentedScript.build(topN) + Task 6/7 Settings Switch 暴露(默认 OFF) |
| G6 语速/音调 | **延后 v0.3.1+** — 用户 2026-09-11 决定 |
| G7 逐 hit 可执行建议 | Phase D Task 8(stub)— **v0.3.1+ 备选** |
| G8 错误态变体 | Task 1 SegmentedScript.buildError + Task 4 TtsController.speakError |
| G9 末尾免责声明 | Task 1 SegmentedScript.build(trailingDisclaimer) + 测试覆盖 |
| G10 域前缀 | Task 1 BuildOptions.domainPrefix + Phase D Task 8 测试覆盖 |
| G11 多段 utterance | Task 3 AndroidTtsEngine QUEUE_ADD + Task 4 TtsController.speakSegments |
| G12 Sherpa 引擎 | Phase D / v0.3.1+ 跟进 — 当前 commit 不动 interface,sherpa 引擎编译不受影响 |
| G13 pendingOnDone 互覆盖 | Task 3 AndroidTtsEngine.pendingOnDone Map |
| G14 latestReport 变化检测 | 留 v0.1.73 |

### Spec coverage

| Spec 章节(规划中) | 对应 Task |
|---|---|
| §1.1 多段严重度分组 | Task 1-2 |
| §1.2 命中计数 + 法条引用 | Task 1-2 |
| §1.3 AI 免责声明(per memory) | Task 1-2 |
| §2.1 UI 滚动同步 | Task 3-5 |
| §2.2 长报告摘要 | Task 1 + Task 7 |
| §3.1 语速 / 音调 Settings | Task 6-7 |
| §3.2 长报告摘要 N Slider | Task 7 |
| §4.1 可执行建议(备选) | Phase D Task 8 |
| §4.2 域前缀(备选) | Phase D Task 8 |
| §5 测试策略 | Task 1-7 各自 unit test + Task 5 e2e + Task 7 真机烟测 |
| §6 边界 / 错误处理 | Task 1 SegmentedScript.buildError + Task 4 speakError + Task 6 clamp |
| §7 文档同步 | Phase E |
| §8 TODO(本期不实现) | G14 + 导出 + 进度条 + word-level 高亮 |

### Placeholder scan

- "Top-N 滑块 0.5/0.75/1.0/1.25/1.5/1.75/2.0 / 5/10/15 三档" — 是 UI 设计参数,非 placeholder
- "v0.3.0 / v0.3.0" — 是发版号变量,由 `/project-commit` skill 替换真实号
- "Stub 留 v0.3.0" — Phase D Task 8 标 stub,显式说明,无 TBD 风险

无 "TBD" / "TODO" / "类似 Task N" / "Add appropriate error handling" 等。

### Type consistency

| Type | 定义点 | 消费点 |
|---|---|---|
| `BuildOptions` | Task 1 ScriptOptions.kt | Task 1 SegmentedScript / Task 2 ScriptBuilder / Task 4 TtsController / Task 8 Phase D |
| `HitSegment` | Task 1 ScriptOptions.kt | Task 4 TtsController.dispatchSegments / Task 1 SegmentedScript |
| `TtsSetting.longReportSummaryEnabled` | Task 6 TtsSetting.kt | Task 6 Repository / Task 7 Settings Switch / TtsController.speak 读 setting |
| `TtsController.currentHitIndex` | Task 4 TtsController.kt | Task 5 HomeScreen collectAsStateWithLifecycle |
| `TtsEngine.onUtteranceStart` | Task 3 TtsEngine.kt | Task 4 TtsController dispatchSegments |

无 type drift。

### Hard-rule adherence

- 作者 = `AlexMultiAgent`,无 `Co-Authored-By:` trailer(Task 1 / 2 / 3 / 4 / 5 / 6 / 7 / 8 / 9 commit 均按模板,不附加 trailer)
- `git add` 全部具体路径,无 `-A` / `.`
- 无敏感文件 stage(`gradle.token.properties` / `~/.gradle/gradle.properties` / `local.properties` 不出现在任何 task)
- `app/libs/*.aar` 不动(Task 3 改 AndroidTtsEngine 是 `.kt`,不碰 aar)
- 不 bump 无关发版号(CLAUDE.md + memory `feedback-release-hygiene`)— 本 plan 只在 Phase E Task 9-10 bump v0.3.0
- per memory `feedback-ad-law-no-gray-area` — Task 1 trailingDisclaimer 强制开启(可关,但 default = true)
- per memory `feedback-category-specific-rules-anchors` — Task 1 buildBucketSegment 引用 lawText 字段(取 section name),不只 regulation 编号

---

## Open Questions(给 user 决策)

1. **Q1**:Phase D(`actionableAdvice` / `domainPrefix` UI)走 v0.3.0 还是 v0.3.0?
   - 走 v0.3.0:1 个发版号内 9 task,风险叠加(9 commit + 1 e2e);但用户体验一次到位。
   - 走 v0.3.0:v0.3.0 走 8 task(Phase A+B+C)+ 验证,稳定后 v0.3.0 单 PR 落 Phase D。建议后者。

2. **Q2**:`Top-N` Slider 是「5 / 10 / 15 三档离散」还是「滑块 3-20 连续」?
   - 离散:更稳,但用户调不到中间档
   - 连续:灵活,但 commit 后无法精准 e2e assert
   - 建议:离散三档 + 「不限制」共 4 档,默认值 = 10

3. **Q3**:UI 滚动同步(scroll-to-current)是「自动」还是「用户点」?
   - 自动:TTS 朗读时自动滚到视口(本 plan 默认)— 体验好,但用户感觉「页面自己动」
   - 用户点:TTS 朗读时顶部出现「当前朗读:第 X 条」提示,用户点才滚
   - 建议:自动 + 提供 Settings 开关(默认自动)— 留 follow-up

4. **Q4**:speakError 走 Error.message 字段直接朗读,还是独立枚举 errorCode → 文案映射?
   - 直接:实现简单,但 message 是 dev-facing(可能含 stack trace 前缀)— 风险高
   - 映射:实现复杂,但朗读友好— 推荐后者
   - 建议:Task 4 实现走「errorCode → 用户友好文案」映射表(放到 `TtsErrorMessages.kt` 新文件,2 case:OCR_UNAVAILABLE / RULES_FAILED / UNKNOWN)

5. **Q5**:Phase A 末既有 `ScriptBuilder.build(report): String` 标 deprecated 是 v0.3.0 就删,还是 v0.1.73 才删?
   - 本 plan 标 deprecated 保留;v0.1.73 删除。
   - 但若有外部 caller(没有,纯 internal)— 立即删更干净
   - 建议:v0.1.73 删除,留一版本 buffer

6. **Q6**:真机 e2e 锚点(TtsPlaybackE2ETest)是写 Phase B 末(Task 5)还是 Phase C 末(Task 7)?
   - Phase B 末:e2e 跟多段 speak 一起来,但还没 Settings 验证
   - Phase C 末:e2e 跟 Settings + 真机烟测一起,更稳
   - 建议:Phase C 末 — Task 5 写 androidTest stub,Task 7 调 adb-runner 跑通

---

## Boundaries / 后续 PR 范围

- **G14 latestReport 变化检测**(避免 toggle 重复朗读)→ v0.1.73
- **SherpaTtsEngine ONNX Runtime ABI 修复**(per memory `feedback-onnxruntime-abi-version-mismatch`)→ v0.1.73 / 后续
- **TTS 期间按钮禁用 + 进度条**(朗读 N/M 进度)— v0.1.73
- **导出取证包 + TTS 一键播放整段 报告**(archive mode)— v0.1.74
- **Word-level 高亮**(`SynthesisCallback.onRangeStart`)— Android API 不支持,留作外部 follow-up
- **跨引擎 `onUtteranceStart`**(interface var 不能 set 跨实现)— `SherpaTtsEngine` 修复后单独 PR 落
