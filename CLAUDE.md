# 冰灵锐目 (IceSpiritAI_Vision) — 项目协作约定

本项目独有的协作约定:命名规范、构建系统(modelProfile / sourceSet 拆分 / AGP 9 限制)、模型路线、命令与文档索引。

## 命名一致性(三项目统一)

| 项 | 值 |
| --- | --- |
| 源码 namespace | `com.icespiritai.offline` |
| Gradle rootProject name | `IceSpirit` |
| 主题样式名 | `Theme.IceSpiritOffline` |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| ABI | `arm64-v8a` only |
| Gradle Wrapper | 9.7 / AGP 9.3 / Kotlin 2.4.10 (forward-path baseline,锁定于 2026-08 stack) |
| Maven 镜像 | Aliyun 主 + Tencent/Huawei 备 |

**三项目唯一不同的字段是 `applicationId`(对应设备上的独立包名身份):
- 冰灵慧语 `com.icespiritai.chat`
- 冰灵智译 `com.icespiritai.translate`
- 冰灵锐目 `com.icespiritai.vision`

## 产品方向(v0.1.10 起):广告招牌 单一焦点

**v0.1.10 起,UI 层只暴露「广告招牌」tab;「食品标识」tab 入口暂时对用户隐藏**。原因与可复制模式边界:

| 维度 | 状态 |
|---|---|
| UI tab 渲染 | [RuleTabBar.kt](app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt) 内部 `visibleTabs = listOf(RuleTab.AdSignage)`,`TabRow` 只渲染一项;`RuleTab.FoodLabeling` enum 项保留 |
| ViewModel 路由 | [IceSpiritVisionViewModel.kt](app/src/main/java/com/icespiritai/offline/IceSpiritVisionViewModel.kt) `matcherFor(tab)` 双分支保留,`FoodLabelRuleMatcher` 仍可路由;UI 不暴露入口即可 |
| 规则 + 加载器 | `AdSignageRuleLoader` + `FoodLabelRuleLoader` 双装载入口保留;`food_label_rules.json`(66 条 / v4)+ `ad_signage_rules.json`(129 条 / v10,14 个类别:absolute / agricultural / cosmetic / education / finance / internet_ad / medical / minor / outdoor / pesticide / realestate / restricted / signage / veterinary)均随 APK 出 |
| 知识库 | [知识库/](知识库/) 双域 markdown 完整保留;`广告业务/` 是当前打磨中的成熟参考,`食品标识/` 是待后续套用的对象 |
| 测试 | [FoodLabelRuleMatcherTest.kt](app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleMatcherTest.kt) + [IceSpiritVisionViewModelTabTest.kt](app/src/test/java/com/icespiritai/offline/IceSpiritVisionViewModelTabTest.kt) 双 tab 路由断言保留 |
| 打磨策略 | ad_signage_rules.json 关键词命中 / 严重度分级 / category 显示 / 证据包导出全部以"可复制到下一个视觉判别域"为标尺优化,达标后再以同样模式启用 FoodLabeling tab |

**为什么保留 FoodLabeling 代码路径不删**:FoodLabeling 是「广告招牌模式 → 其他视觉判别域」的可复制模板,把 `FoodLabelRuleMatcher` + domain 字段 + 知识库 + 类别显示一并删除等于砍掉这条扩展路。`RuleTabBar.kt` 顶部注释明示"恢复时把 `visibleTabs` 改回 `RuleTab.entries.toList()` 即可"。

### Tab → 初始页功能规范(✅ 已实现,7d5485c,2026-08-29)

**用户场景**:识别完一张图片后(state = `Complete`,ResultPanel + 高亮叠加显示中),用户想「从头再来一次」(换张图 / 再拍一张),目前必须手动点右下角的相机/相册按钮。期望:**再次点击「广告招牌」tab**(已经是选中态)直接回到 Idle 初始页**(清空 pendingUri + state 清成 Idle,无需用户再去点拍照/选图按钮之外的"返回"入口)。

**实现行为**(2026-08-29 落地,3-state 契约):

- 当 `selectedTab == tab` **且** `state !is Loading` 时,`setTab` 视为「回到初始」调用 `reset()`(等于把 selectedTab 复位不变,但 state 走回 Idle)
- 当 `selectedTab == tab` **且** `state is Loading` 时,保持原 no-op(防误触打断正在跑的 OCR / 规则扫描)
- 当 `selectedTab != tab` 时(等 FoodLabeling 解锁后才有意义),维持现状的"切换 matcher,保留 state"

**为什么不绑去拍照/相册按钮**:相机/相册按钮本身有"开始新一次分析"的语义(自动调 `setPendingUri + startAnalysis`),不适合复用为"回到初始"。Tab 点击是更纯净的「清空」入口。

**测试锚点**:`IceSpiritVisionViewModelTabTest.kt` 已落 3 个 setTab 契约测试 — (a) `setTab_sameTab_nonLoadingState_resetsToIdle`;(b) `setTab_sameTab_loadingState_isNoOp`;(c) `setTab_tabSwitch_doesNotReset`。Loading 路径用反射设 `_state=Loading` 验证不被误清,稳定。

## UI 严重度模型 + v0.1.41 微调 (Phase 3.5 / v0.1.41)

锁定于 v0.1.36 enum 重排后的 Severity contract,所有 UI 严重度排序都走同一份 domain 层 helper(避免 ui.home → ui.viewer 反向依赖 + 多处 ordinal 误用)。

### 严重度模型 — Severity enum + rank contract

- `enum class Severity { Violation, Warning, Info, Positive }` — Positive 故意放末尾,任何 ordinal-based `maxOfOrNull` 退化路径仍会把 Violation 排在 Positive 前面(深度防御)
- `fun severityRank(severity: Severity): Int` 在 `domain/AnalysisState.kt` 顶层 function(Violation=3 / Warning=2 / Info=1 / Positive=0)
- **Positive 必须永远不能升级显示** — 当 Violation 与 Positive 同图共存,filter Positive 后由 Violation 保住「最严重」;`ui.home.StatusBannerFor` 与 `ui.viewer.ViewerTextList.worstSeverityForLine` 都消费同一份 contract

### Phase 3.5 严重度感知 UI (v0.1.40)

四联 UI 调整(广告招牌 tab,首次把 Severity 作为一级分类):

1. **HitCard 全卡片染色** — 整张卡片背景走 `sev.container(hit.severity)`,右上角挂 `SeverityChip`("违规" / "警告" / "信息")做无颜色可读的兜底标识;`rule category`(广告文案 / 绝对化用语等)整条删除 — category 是引擎内部概念,用户只需看严重度;`依据`(regulation)+ 可折叠 `法条原文` 保留
2. **ResultPanel 按 rank 分组** — 3 个 section(`违规 (N)` / `警告 (N)` / `信息 (N)`),从最严重往下排,空 section 不渲染
3. **KPI bar 长按提示** — `StatusBanner` KpiCell 包 `TooltipBox` 单句 tooltip(违规 = 广告法明文禁止需立即下架 / 警告 = 需结合语境判断 / 信息 = 合规资质相关需另行核实)
4. **Viewer 全屏图叠命中框** — `ViewerImage` 走 `HighlightOverlay`(同款红/琥珀/蓝染色)+ Telephoto `ZoomableAsyncImage`,pinch / pan / 双击 zoom 时框同步缩放,与 home `ImagePreview` 共用 `computeFitTransform`

### v0.1.41 用户反馈 6 点微调

| # | 改动 | 文件 / 函数 |
| --- | --- | --- |
| 1 | **KPI 提示由长按改为点击** — `rememberCoroutineScope` + `tooltipState.show()/dismiss()` 显式 toggle,persistent tooltip 直到用户再点 | `ui/home/StatusBanner.kt` `KpiCell` |
| 2 | **CaptureBar 2 / 3 按钮动态布局** — `hasHits=false` 显 2 按钮(选图 + 拍照各半宽),`hasHits=true` 显 3 按钮(三等分 `Modifier.weight(1f)`);`enabled=false` 同步禁掉拍照与导出 | `ui/home/CaptureBar.kt` |
| 3 | **导出按钮仅在有命中时显示** — 0 命中时整个中间槽位消失,不显禁用灰按钮 / 不留空位 | `ui/home/CaptureBar.kt` `hasHits: Boolean` 参数 |
| 4 | **导出按钮文字缩为「导出」** — visible label 2 字 + 图标 `Icons.Default.Save`;TalkBack 描述走单独 string `export_button_desc = "导出取证包"`(`Modifier.semantics { contentDescription = ... }`) | `strings.xml` `action_export` + `export_button_desc` |
| 5 | **HitCard matched text 字号降一档** — `headlineSmall`(24sp) → `titleLarge`(22sp),仍略大于「广告招牌」tab 标签(`titleMedium` 16sp) | `ui/home/HitCard.kt` |
| 6 | **Viewer 文字列表命中行 + 子串高亮** — 每行若命中 → 整行背景染**最严重桶**的 container color(对应图片红/琥珀/蓝框);行内**命中子串**用 `AnnotatedString + SpanStyle(background = sev.container(severity))` 在原文打底色。`mapNormRangeToOriginal` helper 把归一化字符串索引映射回原始文本偏移,子串 span 与显示文字严格对齐;`worstSeverityForLine` 与 home `HighlightOverlay` 共用 `severityRank` + `TextNormalizer.forMatching`,确保「图上框在 A 行 / 列表染色在 B 行」的 drift 不会发生 | `ui/viewer/ViewerTextList.kt` `worstSeverityForLine` + `highlightMatchedSubstrings` + `mapNormRangeToOriginal` |

## modelProfile 系统

Gradle property `modelProfile` 控制当前构建启用哪个模型配置:

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | **默认 / 首版** | 仅展示骨架;UI 可跑,Fake OCR + slim rules,APK 不带模型 |
| `ice_ocr_rules` | Phase 1(shipped) | **PP-OCRv6_small**(2026-08-20 升级,rec dict 18708 条)经 PaddleOCR v3.7.0 SDK(走 ONNX Runtime + OpenCV)+ AdSignageRuleMatcher + FoodLabelRuleMatcher 已接入;rules JSON 从 `assets/rules/ad_signage_rules.json`(广告招牌 129 条 / v10 / 14 类,含 2026-08-27 新增 `ad_signage_signage_food_safety_implication` 暗示安全性规则)与 `assets/rules/food_label_rules.json`(食品标识 66 条 / v4)出;ONNX 模型(bundled in APK)在 `assets/models/{det,rec}/inference.onnx` + `inference.yml` |
| `ice_vision` | 未来 | 多标签 + 法规依据的端侧 VLM |

切换方式:`./gradlew assembleDebug -PmodelProfile=<name>`

### profile → sourceSet 拆分

每个 profile 独占一个 `src/<profile>/java/` 目录,放该 profile 的 `OcrEngineFactory` 实现(`PaddleOcrEngine` 与 `FakeOcrEngine` 互斥编译):

| Profile | sourceSet | 注册方式 |
| --- | --- | --- |
| `shell` | `app/src/shell/java/` | `FakeOcrEngineFactory` 走 `src/shell/resources/META-INF/services/...` |
| `ice_ocr_rules` | `app/src/ice_ocr_rules/java/` | `PaddleOcrEngineFactory` 走 `src/ice_ocr_rules/resources/META-INF/services/...` |

`PaddleOcrEngine` 本身放在 `src/ice_ocr_rules/java/`(不是 `main/`),确保 `shell` APK 不依赖 ONNX Runtime / OpenCV / PaddleOCR AAR。

**AGP 9.x 限制:** `sourceSets { create("name") }` 已被拒绝,改用 `androidComponents.onVariants { variant.sources.java?.addStaticSourceDirectory(...) }` 挂载。详见 `app/build.gradle.kts` 中 `onVariants` 块的 KDoc。

**ServiceLoader 注册:** AGP 9 的 `res`/`assets` sourceSet 会丢弃 `META-INF/services/`(非 Android 资源类型)。解决:把 `META-INF/services/<FQN>` 打进一个一行 JAR,加为 `runtimeOnly files(...)`。AGP `processJavaResources` 抽到 APK 根目录,ServiceLoader 才能找到。任务 `buildProfileServicesJar` 在 `app/prepare-ocr-rules.gradle.kts`,按当前 `modelProfile` 输出对应的服务声明。

### native lib 打包(ice_ocr_rules)

`packaging.jniLibs.useLegacyPackaging = true` 必须显式开启:AGP 9 默认 `false` 会把 `.so` 压缩在 APK 里,Android 14/15 + `extractNativeLibs=false` 下 `System.loadLibrary` 找不到 lib。开启后 native libs 在安装时解压到 `/data/app/<pkg>/lib/<abi>/`,容量略大但能 load。`shell` profile 无 native lib,此配置对它无影响。

## 知识库时效性整理(2026-08-27)

`知识库/` 是规则引擎的法源。每个规则 JSON 条目的 `regulation` 字段必须能指回 `知识库/<域>/<现行法规>.md`,不得指已废止法规。

- `知识库/<域>/*.md` = **现行有效**的法规,规则 JSON 引用走这里
- `知识库/已废止/*.md` = 已废止 / 被上位法替代 / 过渡期已结束的法规,仅作历史溯源用
- 政策:新增规则或扩规则时,先用 WebSearch 确认 `regulation` 字段所引法规仍现行(2026-08-27 已完成批量清理:户外广告登记规定、母乳代用品销售管理办法、烟草广告管理暂行办法 → 已废止 / 实质替代;GB 7718-2011 / GB 28050-2011 / 食品标识管理规定 → 已废止并替换为 2025/2027 新版)

### 规则库时效性更新(v0.1.58,2026-09-04)

v0.1.58 是首个把"规则库时效性扫描"作为发版前置条件的发版号(commit `f254049`)。v0.1.57 follow-up workflow Phase 2 synthesis 报告(见 [`_reports/v0157_synthesis_plan.md`](_reports/v0157_synthesis_plan.md))扫出 ad_signage_rules.json 全 175 条规则的 `regulation` 字段,标识出 3 P0 drift(regulation / lawText 字段字符串变化,keywords / severity / category / sourceMarkers / categoryAnchors 全部不变,OCR 命中行为字节级一致 — 法规文本变化不应当连带影响 OCR 命中行为):

| Rule | 原引用 → 现行引用 | Drift 形态 | KB 同步 |
|---|---|---|---|
| `ad_signage_restricted_tobacco_health_relief` | 《烟草广告管理暂行办法》§6 → 《广告法》§22 + §57 | **已废止**(2016-02-01 施行 / 2016-12 实质被《广告法》§22 吸收 / 2018-10 已废止) | 烟草广告管理暂行办法 `git mv` 到 [`知识库/已废止/烟草广告管理暂行办法_2016工商总局令86号废止.md`](知识库/已废止/烟草广告管理暂行办法_2016工商总局令86号废止.md) |
| `ad_signage_restricted_tobacco_buy_gift_promotion` | 《烟草专卖法》§19(1991 原版) → §18(2015 第三次修正版现行) | **错号**(§18 = 广告禁令 / §19 = 商标注册,2015 修正版整体重排) | [`知识库/广告业务/中华人民共和国烟草专卖法(广告节选).md`](知识库/广告业务/中华人民共和国烟草专卖法(广告节选).md) 加 v0.1.58 编号同步 section |
| `ad_signage_signage_duty_free_unauthorized` | 《反不正当竞争法》§8(2017/2019 版「虚假宣传」)→ §9(2025-10-15 修订版主席令第五十号) | **整体重排**(原 §8 → 现 §9;现 §8 是商业贿赂与广告无关) | 新建 [`知识库/广告业务/中华人民共和国反不正当竞争法.md`](知识库/广告业务/中华人民共和国反不正当竞争法.md) + [`中华人民共和国海关法(广告节选).md`](知识库/广告业务/中华人民共和国海关法(广告节选).md) |

**drift 三种形态**(命名规范化,后续发版沿用):

- **(A) 已废止** — 法规被上位法整体替代 / 过渡期已结束 / 明确废止令。处置:`git mv` 到 `知识库/已废止/`,README 链接同步换 `../已废止/`。
- **(B) 错号** — 法规仍现行,但规则 JSON 引用的条款编号是旧版本结构(如 1991 原版 vs 2015 修正版)。处置:KB 加版本同步 section 解释现行编号。
- **(C) 整体重排** — 法规经历实质修订,条款编号在修订版中整体前移/后移/合并/拆分(如《反不正当竞争法》2025 修订版 §8→§9)。处置:KB 新建 markdown(若是新引法规)或加修订版现行编号 section。

**KB 同步范围(本地,gitignored)**:`知识库/` 目录整体 `.gitignore`(2026-09-02 起,体积大 + 含未授权转载的法源原文,本地留存供规则引擎引用)。dev/research 引用锚点(KB markdown)本地留存,但**不进 git,也不进 APK**;真正进 APK 的是 `app/src/main/assets/rules/*.json` 的 `regulation` + `lawText` 字段。本档作为"v0.1.58 以后发版前必须做的事"清单:

1. **必跑** `.claude/agents/regulation-freshness-checker.md` agent(扫所有规则 `regulation` 字段 + WebSearch 法规新鲜度)
2. **修 P0 drift**(已废止 / 错号 / 整体重排)→ 单个 commit 走 `fix(rules): ad_signage <X> 法规新鲜度 <原→现>` 格式
3. **KB 同步**(新建 + 编号 sync + git mv 到 `已废止/`)→ 本地,gitignored
4. **真机 e2e 不破**:`./gradlew.bat testDebugUnitTest` 必须全过(法规文本变化不影响 keywords / OCR 命中)
5. **Triple-SHA 对齐**:tag = HEAD = gitea `latest` ref,见 §"发布流水线踩坑" 末尾

**已知遗留 / 后续 PR 范围**(synthesis P1+):

- 仍引《广告法》§17+§58 的 `ad_signage_signage_food_disease_target` 跨域引用(ad → food),按 [`feedback-followup-ad-signage-cross-cite`](.claude/agents/feedback-followup-ad-signage-cross-cite.md) 已 closed,根本 domain 拆分留单独 PR
- v0.1.57 落地的 `ad_signage_internet_art34_live_ecommerce_fake` / `ad_signage_internet_art37_ai_digital_human` 引《直播电商监督管理办法》,但 `知识库/广告业务/` 主目录 .md 缺失(只在 `_tmp_convert/` 有)→ v0.1.58 已从 scratch 迁主目录并加 canonical 头(本地)
- 剩余 fixture 验证 / 真机 e2e audit75 扩展 → 留给后续发版号

## 视觉/OCR 模型路线(2026-08 锁定)

Phase 1 走 OCR + 规则库路线(**PP-OCRv6_small** + PaddleOCR 官方 SDK v3.7.0 + HankCS AC 自动机)。候选从 PaddleOCR-slim / Paddle-Lite / ONNX Runtime / MediaPipe Tasks 收敛到:**PaddleOCR 官方 SDK** 走 **ONNX Runtime + OpenCV**(Android 端 nn 推理),不再 hardcode 视觉模型路线。

二分类 / 多标签视觉模型留 Phase 2+,首版不引入。

**PP-OCRv6_small 升级决策(2026-08-20)**:`ice_ocr_rules` profile 默认 ONNX 模型由 `pp-ocrv5_mobile` 替换为 `pp-ocrv6_small`(rec dict 18708 条,det +5MB / rec +5MB APK 体积增长)。4 张测试集子文件夹 A/B 实测:v6 vs v5 = **+12% 检出行 / +5.4% 平均置信 / −10% 端侧耗时 / 5× 规则命中数**(核心案例:蟹都汇"大闸蟹连锁门店数量全国第一"v6 检出,v5 漏报并误识为 "全国谢")。`tools/download-ppocr-models.sh` 默认 variant 已切到 `pp-ocrv6_small`。详细数据与 4 图分项对比见 [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](docs/knowledge/ppocrv6_vs_v5_a_b_test.md)。

## 构建命令

```bash
# 默认(骨架 APK,Fake OCR + slim rules)
./gradlew.bat assembleDebug -PmodelProfile=shell

# Phase 1 shipped(PP-OCRv6_small + PaddleOCR v3.7.0 + 广告招牌 129 条 / 食品标识 66 条 + ONNX 模型)
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules

# 单元测试 / Lint
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug

# 清理
./gradlew.bat clean
```

**CI 仅跑 `shell` profile** — 仓库 .github/workflows 不会构建 `ice_ocr_rules`(避免拉 70 MB AAR + 30 MB ONNX + 跑 5 步 pre-flight + 真机烟测)。`ice_ocr_rules` profile 的端侧回归由 [`/icevision-release`](.claude/skills/icevision-release/SKILL.md) skill 的 5 步 pre-flight + 真机烟测覆盖(local-only,见 [docs/smoke/](docs/smoke/))。本地构建任一 profile 都需要先 export `JAVA_HOME` 到 JDK 17(`§开发环境`)。

## Lint vital/analyze 已禁用(AGP 9.3 + Kotlin 2.4.10 FIR 崩)

`lint*Analyze*` 与 `lint*Vital*` 任务在 [`app/build.gradle.kts`](app/build.gradle.kts) 中通过

```kotlin
tasks.matching {
    it.name.startsWith("lint") && (it.name.contains("Vital") || it.name.contains("Analyze"))
}.configureEach { enabled = false }
```

禁用。原因:lint 32.3.0 + Kotlin 2.4.10 的 KAA/FIR 集成在解析 `.gradle.kts` 时崩(`findFirCompiledSymbol only works on compiled declarations`),AGP 9.x `Lint` DSL 没有 `checkBuildScripts` 开关、lint CLI 也没有 `--ignore-build-scripts` 标志。完整根因 + 复现见 [`docs/knowledge/lint-vital-fir-crash.md`](docs/knowledge/lint-vital-fir-crash.md)。[`app/lint.xml`](app/lint.xml) 已就绪(GradleDetector + CommentDetector + AppBundleLocaleChangesDetector + ByteOrderMarkDetector 全部 disable),上游 fix 落地后只需删 `tasks.matching` 那一段即可恢复 lint。

**Release 实际门控:** 不是 lint,而是 `assembleRelease → generateVisionLatestJson → archiveVisionRelease → uploadVisionReleaseToGitea` 流水线(签名 APK + cert-pin 校验 + Gitea `latest` tag + SHA-256 握手)。详细见 [`docs/smoke/2026-08-14-phase1-smoke.md`](docs/smoke/2026-08-14-phase1-smoke.md)。

## ice_ocr_rules profile 前置步骤

该 profile 需要的 ONNX 模型与 SDK 默认不入仓(`.gitignore` 排除 `app/src/main/assets/models/**/*.onnx`)。首次构建前:

```bash
bash tools/download-ppocr-models.sh   # 下载 det/rec inference.onnx + inference.yml
bash tools/build-ppocr-sdk.sh # 产出 app/libs/ppocr-sdk.aar
```

两个脚本幂等,执行后即可 `./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules`。

**AAR 体积异常澄清(v0.1.53)**:PaddleOCR 官方 SDK v3.7.0 的 `app/libs/ppocr-sdk.aar` 是 **91K classes-only**(无 `jni/` 或 `libs/` 子目录),native .so 来自 `onnxruntime-android` + `opencv-android` 两个依赖 AAR(合计 ~70 MB,贡献最终 APK 58 MB)。`tools/build-ppocr-sdk.sh` 的 16KB alignment gate 在 .so 列表为空时静默 exit 0(已知脚本 quirk,但不阻断发版)。预编译环境看到 91K AAR **不要误判为 stub**;真机 OCR 走 PaddleOCR Java SDK → ONNX Runtime native。

## Instrumented test / 真机 A/B (androidTest)

跑 `connectedDebugAndroidTest` 在华为 nova 6(ANN-AN00,SDK 35)上踩过的坑,后人不要重蹈:

- **`connectedDebugAndroidTest` 不接 `--tests`**(常规 JUnit 平台参数 AGP 不透传)。单 class 过滤用 Gradle property:`-Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.Xxx`(AGP 透传为 `-e class <FQN>` 到 AndroidJUnitRunner)。
- **`app/src/androidTest/assets/` 打进的是 test APK,不是 main APK**。Fixture 图必须放这里,**不要** `adb push /sdcard/` + ContentResolver — 流程不稳,`openInputStream` 走不通。要么 assets,要么 staging 到 `appCtx.cacheDir` 后从 test APK assets 拷入。
- **Logcat ring buffer 几分钟内轮转 — 测试后再 `adb logcat -d` 拉不到自己 tag 的数据**。配套模式:`adb logcat -c; (adb logcat -v time TAG:I '*:S' > file.out) &; ./gradlew connectedDebugAndroidTest ...` 后台捕获必须在测试启动前开,test harness 用 `Log.i("MyTag", ...)` 落数据。
- **`@Test fun foo() = runBlocking { ... Log.i(...) }` 返回 Int**(Log.i 的返回),JUnit 校验拒绝("Method should be void"),`runBlocking` body 末尾必须显式 `Unit`。
- **真机冷启动 vs warm 延迟必须分开报**。`PaddleOCR.create()` 模型加载一次性 ~5s,per-image warm 平均 ~2.6s(华为 nova 6 ARM64 + 4-thread),混在一起看不出趋势。harness 模式:1 次 cold + N 次 warm,分别计 `cold_ms` / `warm_total_ms` / `warm_avg_ms`。
- **华为 nova 6 PackageManager ghost state**:`adb install -r` 可能 `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ... signatures do not match`,但 `pm list packages` 看不到该包。`pm uninstall` / `pm uninstall -k` 均 `DELETE_FAILED_INTERNAL_ERROR`;`pm clear` 报 "Failed" 但 exit 0。**workaround**:`adb shell pm clear com.icespiritai.vision` 后再 `adb install -r APK` 即可 — `pm clear` 虽报错但会把 ghost state 清掉。

## 违规案例 fixture 工作流 + audit71 真机 e2e (v0.1.49 落地)

新增 N 张广告违规案例夹具的复用模式(audit66 / audit71 已稳定):

| 阶段 | 操作 |
| --- | --- |
| 1. 暂存 | 图入 `违规案例/`(gitignored 本地暂存) |
| 2. 命名审核 | 真机 OCR + AdSignageRuleMatcher 跑一遍,逐张对照 OCR 文本与 filename,标记 mismatch |
| 3. 重命名 | 同步改 `违规案例/` + `app/src/androidTest/assets/fixtures/audit71/`(canonical test version,committed) |
| 4. e2e 校验 | `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAudit71ImageE2ETest` |
| 5. 覆盖率文档 | `app/src/androidTest/assets/fixtures/audit71/coverage_matrix.md` 由真机 `[OCR_HIT]/[OCR_NO_HIT]` 行 regex 匹配生成 filename ↔ rule_id 表格 |

**filename 约定**:`NN_品牌_违规情形_类别.jpg`,NN 前缀 02d(<100) / 03d(≥100),缺失位补 0。

**audit71 e2e 锚点**:Huawei nova 6 (AGQV023313008161, SDK 35),logcat TAG=`Audit71E2E`,行标记 `[COLD]/[WARM]/[HITS]/[OCR_HIT]/[OCR_NO_HIT]/RESULT_JSON`,harness 在 [AdSignageAudit71ImageE2ETest.kt](app/src/androidTest/java/com/icespiritai/offline/rules/AdSignageAudit71ImageE2ETest.kt),真机烟测记录见 [docs/smoke/2026-09-02-audit71-v11-rules-e2e.md](docs/smoke/2026-09-02-audit71-v11-rules-e2e.md)。

**经验阈值 `ANY_HIT ≥ 60/N`**(v0.1.49 实测):单轮扩展不到位时,在**同一发版号内**做两阶段扩展 — 第二阶段加新规则 + 扩既有规则关键词(AC substring 命中兜底 OCR 漏字)。v10 49/71 → v11 59/71(差 1)→ v12 65/71(达标)。

**OCR 召回上限**:miss 图均为 OCR 召回限制(文本 <10 字 / 严重错位 / 视觉符号未被 OCR),规则引擎 100% 召回,扩展规则无法补回缺失字符(v0.1.49 miss 6 张:99/103/109/110/120/124 — 分别对应 OCR 召回为空 / 仅 21 字 / 严重错位 / 文本无违规 / 视觉符号 I❤Harbin 未被 OCR / 仅 5 字)。

## Unit test 踩坑(2026-08-21 v0.1.14)

- **Robolectric + Compose `LazyColumn` viewport 太小,首屏 item 不一定 compose**:`composeRule.onNodeWithText("v0.1.X", substring=true).assertExists()` 在 LazyColumn 渲染的列表上稳定失败,即使 LazyColumn 顶部第一个 entry 也没被合成(Robolectric 默认 Activity metrics 太小,LazyColumn 没到 viewport 就跳过 item 合成)。**不要给 LazyColumn 的滚动断言加 `waitForIdle` / `performScrollTo`** — flaky 不会消失。替代方案:把"最新版渲染正确"这种断言改成 **parser-level unit test**(JVM,无 Compose):
  ```kotlin
  val ctx = ApplicationProvider.getApplicationContext<Context>()
  val md = ctx.assets.open("user-changelog.md").bufferedReader().use { it.readText() }
  val entries = VersionHistoryRenderer.parse(md)
  assertEquals("v0.1.X", entries.first().version)
  ```
  这样断言的是 `VersionHistoryRenderer.parse` 的契约(纯函数),不依赖 Compose 合成 / LazyColumn viewport,稳定 + 快;适用场景:任何"asset 第一段 = shipping version"的回归 pin(每个版本 bump 都要改这里的字面量)。
- **ViewModel 里 `while(true) { … delay(N) }` 会让 `runTest` 永久卡死(不是超时,是真卡死)**:`SettingsViewModel` 的 `stallDetectorJob`(v0.1.45 `7038274` 加的下载停滞轮询)在**构造期**就起了一条无终止的 `delay(30_000)` 循环;测试用 `Dispatchers.setMain(UnconfinedTestDispatcher())` + `runTest`,收尾的 `advanceUntilIdle` 会把虚拟时间一路推进 —— 每次 `delay` 立即完成又立刻重新排程,**永远不 idle**。更糟的是 `runTest` 的 60s 超时本身也调度在同一个 test scheduler 上,`advanceUntilIdleOr` 循环里根本没机会被检查,表现为 gradle 任务挂 26 分钟不动。**修法:空闲时不要挂定时器,改成 `updateState.first { it is Downloading }` 挂起等待** —— 协程停在"等 flow 发射"上不算 pending task,`advanceUntilIdle` 立刻返回;顺带省掉整个 VM 生命周期内每 30s 一次的无意义唤醒。同类坑:任何 eager 启动的轮询 / 心跳 / 重试循环都一样,VM 构造期只该挂起、不该定时醒。

## 开发环境

- **JDK 17**:buildSrc 锁定 `jvmToolchain(17)`(forward-path baseline)。WIN runner 默认 `JAVA_HOME` 是 JDK 25(找不到匹配 toolchain,build 启动失败)。本仓库已手动 stage 的路径:`/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`(OpenJDK 17.0.18+8)。运行命令前必须显式 `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"`。**`gradle.properties` 不开 `auto-download` / `auto-detect` 兜底**:仓库没有 foojay-resolver-convention plugin,这两个 flag 在无 plugin 时是纯噪音(Gradle 启动报 "could not resolve toolchain"),且 foojay 镜像在 CN 受限;实际工具链解析靠用户手动 export 的 `JAVA_HOME`。v0.1.42 显式移除该 flag。
- **NDK 28.2.13676358**(r28c):Android SDK Manager 安装,版本由 `app/build.gradle.kts` 的 `ndkVersion` 锁定。
- **JVM 堆**:Gradle daemon `-Xmx3072m`(`gradle.properties`);本机若启用 R8 + Lint + native build,建议 ≥8 GiB 可用内存,避免 daemon OOM。

## 签名(v1 必须开启)

`signingConfigs.release` 必须 `enableV1Signing = true`(AGP 默认 v2-only)。in-app update verifier 用 `JarFile` + `META-INF/CERT.RSA` 读 v1 证书做 cert-pin 校验,若 APK 仅 v2/v3 签名,verifier 返回 `null`,所有合法更新都会被拒。Release 凭据在 `~/.gradle/gradle.properties`(gitignored),Gitea PAT 在 `gradle.token.properties`(gitignored,见 `gradle.token.properties.example` 模板)。

## Commit 策略(必读)

- 所有 commit 作者必须是 `AlexMultiAgent`(仓库 git config 已锁)。**绝不要** 加 `Co-Authored-By: Claude` trailer——也包括 `Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>` 这种把 `user.name` 替换成 `AlexMultiAgent` 但保留 anthropic 邮箱的隐性 AI agent trailer(2026-08-20 audit 发现历史 commit 全部命中此形式)。提交前 `git log -1 --format='%B' | grep -i 'Co-Authored-By'` 应为空。
  - **例外**:2026-08-21 之前的历史 commit 保留 trailer。**不** force-rewrite 是因为会破坏已发布版本的 `tag SHA ↔ APK SHA ↔ JSON SHA` 对齐(vision 自 2026-08-14 起有 in-app update 链路强依赖);live gate 是新 commit 的 pre-flight。
- `gradle.token.properties`(Gitea PAT)、`~/.gradle/gradle.properties`(release signing)已在 `.gitignore`,不要尝试 commit 它们。
- 提交前 `git status` 检查是否包含敏感文件;`git add` 用具体路径,避免 `git add -A`。

## Claude Code 自动化(skills + hooks)

仓库内置 7 个 user-invocable skill + 3 个 hook(`pre-tool-use` + 2× `post-tool-use` + `user-prompt-submit`)+ 2 个 hookify rule + 5 个自定义 agent,把"发版 / 提交 / 防误删 / 规则扩写 / fixture audit / 真机 adb / 资源再生 / 规则编辑期校验 / 法规新鲜度 / 覆盖率分析"十条最容易出错的路径固化成 bash 闸门 + 文档化清单:

| Skill / Hook | 触发方式 | 职责边界 |
|---|---|---|
| `/icevision-release` (`.claude/skills/icevision-release/SKILL.md`) | 用户说 `/icevision-release` 或"发版" / "走发布流水线" | 5 步 pre-flight(JDK 17 / v1 signing / Gitea PAT / AAR+ONNX / cert-pin)+ 4 步流水线(`assembleRelease → generateVisionLatestJson → archiveVisionRelease → uploadVisionReleaseToGitea`)+ Gitea 1.22.x APK 404 绕路 + 大文件 POST 卡死恢复 + 发版后 smoke 校验。**不放开版打标**(那是 commit 阶段的事) |
| `/project-commit` (`.claude/skills/project-commit/SKILL.md`) | 用户说 `/project-commit` / "commit" / "提交" | 8 步提交 hygiene(作者 AlexMultiAgent 校验 / 无 Claude trailer / 显式 `git add` / 敏感文件扫描 / JDK 17 / build 校验)+ 当 commit 主题含 release marker(`feat(v0.1.X):` / `fix(v0.1.X):` / "发版")时同步执行 **Release 三段式打标**:`versionCode` bump + `user-changelog.md` 顶部条目 + `git tag v0.1.X` + push `latest` ref |
| `/add-rule-entry` (`.claude/skills/add-rule-entry/SKILL.md`) | 用户说"扩 X 规则" / "加规则" / "把 X 法规落地" | 把一条 stub 法规扩成 `知识库/<域>/<reg>.md` + rule JSON 条目 + matcher 单测 + changelog 条目 |
| `/fixture-rename-sync` (`.claude/skills/fixture-rename-sync/SKILL.md`) | 用户说"同步 fixture 重命名" / "audit66 fixture 同步" | 同步 androidTest fixture 子文件夹的文件名重命名 + .md fixture 描述 |
| `/fixture-audit-add` (`.claude/skills/fixture-audit-add/SKILL.md`,v0.1.49 落地) | 用户说"扩 N 张 fixture" / "audit{N} 起来了" / "/fixture-audit-add" | 5 阶段 fixture workflow 打包(暂存 → OCR 命名审核 → 双目录 rename → 真机 e2e → coverage_matrix)+ `ANY_HIT ≥ 60/N` 阈值 + 同发版号 v(N+1) 二阶段扩展(v0.1.49 经验:59/71 → 65/71) |
| `/regen-mascot` (`.claude/skills/regen-mascot/SKILL.md`,v0.1.49 落地) | 用户说"重新生成吉祥物" / "重做 mascot" / "换 mascot 素材" | 包 `tools/generate_mascot_asset.py` + canonical params(`--engine isnet` + `--smooth 1.4` + `--max-dim 480`)+ `#11212C` 深色 surface 验收清单(右镜片 / 衣领 / 白前襟 / 腿间隙)。三种色度启发式在此图均失败,只能走 rembg isnet-general-use,详见 [docs/knowledge/mascot-ui-asset.md](docs/knowledge/mascot-ui-asset.md) |
| `/regen-launcher-icon` (`.claude/skills/regen-launcher-icon/SKILL.md`,v0.1.49 落地) | 用户说"重新生成 launcher 图标" / "调启动图标" / "换图标素材" | 包 `tools/generate_launcher_icon.py` + canonical params(`--tolerance 28` + `--crop-fraction 0.7474` → 下沿 1550px)+ 下沿像素 ↔ fraction 换算表。launcher 走泛洪去底(衬衫饱和度高 + 描边深色)即可,与 mascot 反差。详见 [docs/knowledge/launcher-icon-generation.md](docs/knowledge/launcher-icon-generation.md) |
| `.claude/hooks/pre-tool-use.js` | Claude Code 每次 Bash 调用前自动跑 | Rule 1:`git add -A` / `git add --all` / `git add .` / `git add ./` / `git add ..` / `git add .git` / `git add *`(含 `git -C x add` 全局 flag 变体)拦截;Rule 2:`gradle.token.properties` / `~/.gradle/gradle.properties` / `local.properties` 等敏感文件的 git 操作拦截;Rule 3:`app/libs/*.aar`(PaddleOCR SDK,~70 MB,不被每次 build 重新生成)的破坏性操作(`rm` / `rm -rf` / `mv` / `del` / `unlink` / `truncate` / `git rm` / `find ... -delete`)拦截。规则命中 exit 2 + stderr 解释(v0.1.43 regex 加固 23 case 自测通过) |
| `.claude/hooks/post-tool-use.js`(v0.1.49 落地,匹配 Bash) | Claude Code 每次 Bash `git commit` 后自动跑 | 拦截任何 `Co-Authored-By:` trailer(含隐性 `AlexMultiAgent <noreply@anthropic.com>` 形式,2026-08-20 audit 发现历史 commit 全部命中)。规则命中 exit 2 + stderr 给 amend 命令。**PostToolUse 而非 PreToolUse** — trailer 检查必须等 commit 落地后看 git log,PreToolUse 看不到 heredoc commit msg。**与 user-level `~/.claude/hooks/block-claude-coauthor.py` 互补**:user-level 走 `Claude` 字符串正则(PreToolUse 拦截显性 `Co-Authored-By: Claude`),本项目 hook 走 `Co-Authored-By:` 行匹配(PostToolUse 拦截任何形式 — 包括 2026-08-20 audit 发现的 anthropic-email 隐性形式) |
| `.claude/hooks/validate-rule-json.js`(v0.1.49 落地,匹配 Edit\|Write\|MultiEdit) | Claude Code 每次 Edit/Write 修改 `app/src/main/assets/rules/*.json` 后自动跑 | 落地后回读磁盘文件做 4 项校验:(1) JSON 语法 (2) 顶层 `version` 整数 (3) `rules` 非空数组 (4) `id` 全文件唯一。规则命中 exit 2 + stderr。比 `testDebugUnitTest` 早 ~30s 抓到错误(从 Edit 落地到测试运行之间),常见失误:误删版本号 / 复制粘贴时 id 撞车 |
| `.claude/hooks/user-prompt-submit.js` | Claude Code 每次 user prompt 提交时自动跑 | `.remember/` 历史 buffer / today-*.md / recent.md / archive.md / core-memories.md 搜索触发器(用户消息含 "history" / "remember" 时返回上下文锚点) |
| `.claude/hookify.block-coauthor-trailer.local.md`(v0.1.49 落地,gitignored) | hookify 声明式规则,PreToolUse Bash | `git commit ...` 命令字符串含 `Co-Authored-By` 时拦截 exit 2(显性 `Claude <x@y>` + 隐性 `AlexMultiAgent <anthropic.com>` 都拦)。**与上述 3 层互补形成 defense-in-depth**:user-level `block-claude-coauthor.py`(PreToolUse,`Claude` 字符串) + 本项目 hookify(PreToolUse,任意 Co-Authored-By 正则) + 本项目 `post-tool-use.js`(PostToolUse,commit 落地后回扫)— 3 层同时存在才能挡住所有已知 + 隐式形式 |
| `.claude/hookify.warn-gradlew-clean.local.md`(v0.1.49 落地,gitignored) | hookify 声明式规则,PreToolUse Bash | `gradlew[.bat] ... clean` 命令 warn(不 block,留出口)+ 提示合法触发条件(`modelProfile` 切换 / `gradle.properties` 改 / AGP bump / corrupt `.gradle/` 恢复)与替代方案(`assembleDebug` 增量 / `--stop` daemon / `clean<Task>` 单 task) |
| `compliance-checker` agent (`.claude/agents/compliance-checker.md`) | Claude-only — release 前自动 dispatch | 11 项 release hygiene 审计:v1 signing / cert-pin / PAT 未 stage / Co-Authored-By / 作者身份 / assembleRelease 链路 / Gradle+AGP 版本 / JDK 17 / 规则库新鲜度 |
| `rule-expander` agent (`.claude/agents/rule-expander.md`) | Claude-only — 规则扩写时 dispatch | `add-rule-entry` skill 的研究 + 实现 + 测试验证完整 10 阶段流程 |
| `adb-runner` agent (`.claude/agents/adb-runner.md`,v0.1.49 落地) | Claude-only — 真机操作时 dispatch | 封装 CLAUDE.md §"Instrumented test" 5 个 adb gotcha(ghost state / logcat ring buffer / cold vs warm / runBlocking Unit / `connectedDebugAndroidTest` 不接 `--tests`)+ Gitea proxy bypass + GitHub SSH 路由。`/fixture-audit-add` Stage 4 内部消费此 agent |
| `regulation-freshness-checker` agent (`.claude/agents/regulation-freshness-checker.md`,v0.1.49 落地) | Claude-only — 法规新鲜度审计时 dispatch | 扫 `ad_signage_rules.json` + `food_label_rules.json` 全部 `regulation` 字段,确保都引 `知识库/<域>/<现行>.md` 而非 `知识库/已废止/`。WebSearch 优先级:`flk.npc.gov.cn` > `samr.gov.cn` > `openstd.samr.gov.cn` > `gov.cn`。2026-08-27 批量清理后无人值守,新法规上线 / 旧法规过渡期到期都可能造成 drift |
| `rule-coverage-analyzer` agent (`.claude/agents/rule-coverage-analyzer.md`,v0.1.49 落地) | Claude-only — 覆盖率分析时 dispatch | 给定 audit{N},从 `coverage_matrix.md` + `ad_signage_rules.json` 聚合 per-rule fixture_count 与 per-fixture hit_count,产出 P0-P4 优先级扩展队列(未覆盖 category cluster / Severity=Violation 未覆盖 / Severity=Warning 未覆盖 / Severity=Info 未覆盖)。audit71 = 71 fixtures × 129 rules ≈ 9159 cell,30 分钟手扫压成一次聚合 |

**关键边界**:`icevision-release` 发版后 smoke 校验通过 → 才调用 `project-commit` 走 Release 三段式(确保 tag SHA = APK SHA = JSON SHA,避免 v0.1.14 那种 APK live 但 JSON 旧版本的 drift)。

## 发布流水线踩坑

完整恢复步骤(curl 级)与 Gitea 1.22.x 404 绕路在 [.claude/skills/icevision-release/SKILL.md](.claude/skills/icevision-release/SKILL.md),这里只保留症状 + 修复指针,避免 CLAUDE.md 与 skill 内容漂移。

- **2026-08-21 v0.1.14 — `uploadVisionReleaseToGitea` 大文件 POST 卡住(HTTP 100)**:`POST .../assets` 上传 APK 一直返回 HTTP 100,`--max-time 600` 触发超时。**不要回滚代码**,纯 Gitea 端瞬时问题。**当前 APK-first 顺序**(v0.1.42 起):先 POST APK(`--max-time 900`)抓 response `uuid`,把 staged `vision-latest.json` 的 `apkUrl` 改写为 `http://125.211.45.14:3000/attachments/<uuid>`(task-local 临时文件,不 mutate staging),再 POST 改写后的 JSON(~1.4 KB,瞬时完成)。详 → `icevision-release` "大文件 POST 超时恢复"段
- **2026-08-26 v0.1.31 — Gitea 1.22.x `releases/download/latest/<file>.apk` 返 404**:JSON 200 但 APK URL 404,改名也不解决。workaround:从 POST response 抓 `uuid`,把 `apkUrl` 改成 `http://125.211.45.14:3000/attachments/<uuid>`,cert-pin gate 不变。详 → `icevision-release` "Gitea 1.22.x APK 404 workaround"段
- **Cert-pin 锚点**:`signerCertSha256` 必须 = `4a21f4...3043`。release 凭据在 `~/.gradle/gradle.properties`(gitignored),Gitea PAT 在 `gradle.token.properties`(gitignored,见 `gradle.token.properties.example` 模板)。stage 路径:`build/generated/release-staging/`(per memory `feedback-no-release-history-archive.md`,已不再写 `发布版历史存档/`)

- **Cert-pin /凭据 sourcing(v0.1.53)**:发版前 `source ~/.gradle/release-env.sh` 导出 `ICESPIRITAI_RELEASE_{STORE_FILE,STORE_PASSWORD,KEY_ALIAS,KEY_PASSWORD,CERT_SHA256}` + `JAVA_HOME`(绑定 JDK 17),**不可**手动 `~/.gradle/release.jks` 试 keytool ——实际 keystore 在 `/d/keystores/icespiritai-release.keystore`,由 `ICESPIRITAI_RELEASE_STORE_FILE` 指向(`.claude/skills/icevision-release/SKILL.md` Pre-flight 5 示例路径是过时的)。

- **Triple-SHA 对齐(release 后必跑)**:`tag SHA ↔ commit SHA ↔ APK SHA-256 ↔ vision-latest.json apkSha256` 四者必须一致:
  ```bash
  TAG_COMMIT=$(git rev-parse v0.1.X^{})          # 剥 annotated-tag 对象 SHA
  APK_SHA=$(sha256sum app/build/generated/release-staging/icespiritai-vision.apk | awk '{print $1}')
  JSON_SHA=$(curl -s http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['apkSha256'])")
  test "$TAG_COMMIT" = "$(git rev-parse HEAD)" && test "$APK_SHA" = "$JSON_SHA" && echo "ALIGNED" || echo "DRIFT"
  ```
  注:`git rev-parse v0.1.X` 返回 annotated tag **对象** SHA(≠commit SHA),必须 `^{}` 剥到 commit;JSON 字段是 `apkSha256` 不是 `sha256`。

- **Gitea 1.22.x `releases/download/<tag>/<filename>` 对 `.apk` 文件名 404**(发布仓库 `giteaadmin/vision-app` 实测**健康**,in-app update 完全工作):
  - **症状**:apk URL `releases/download/latest/icespiritai-vision.apk` 持续 404,但同 tag 下 `vision-latest.json` 200 — 触发取决于 release tag 与 filename,attachment `GET /attachments/<uuid>` 正常
  - **绕过**:`app/build.gradle.kts` 的 `uploadVisionReleaseToGitea` 3a/3b/3c 步 — POST APK 抓 response `uuid`,把 staged `vision-latest.json` 的 `apkUrl` 改写为 `http://125.211.45.14:3000/attachments/<uuid>` 再 POST,客户端 cert-pin gate 不变
  - **双 repo 分工**:代码仓库 `giteaadmin/IceSpiritAI_Vision`(git remote `gitea`)— release route **broken**(`/releases/download/<tag>/` 对 .apk + .json 都 404),但客户端从不上这里下载;发布仓库 `giteaadmin/vision-app`(`giteaRepo = "giteaadmin/vision-app"` + `BuildConfig.UPDATE_JSON_URL`)—完全健康
  - 完整证据 / nginx reverse proxy 候选配置见 [docs/knowledge/gitea-1.22x-release-route-broken.md](docs/knowledge/gitea-1.22x-release-route-broken.md)

## 文档索引

| 文档 | 用途 |
|---|---|
| [`README.md`](README.md) | 仓库入口说明 |
| [`docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md`](docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md) | **Phase 1 草案(SUPERSEDED,PaddleOCR 路径取代 RapidOCR)** — §5/§6/§7 仍作 OCR + 规则库端到端契约参考 |
| [`docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`](docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md) | 初版 init 规范,仅骨架 / 命名空间 / 目录布局仍生效 |
| [`docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md`](docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md) | Phase 2 硬化设计 |
| [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](docs/superpowers/specs/2026-08-15-icevision-ui-design.md) | UI 设计稿 |
| [`docs/knowledge/build-stack-2026-08.md`](docs/knowledge/build-stack-2026-08.md) | 命名一致性表(AGP / Kotlin / Gradle / SDK / NDK)的版本依据 |
| [`docs/knowledge/cross-project-implications.md`](docs/knowledge/cross-project-implications.md) | 本 baseline 对冰灵慧语 / 智译两个项目的迁移含义 |
| [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md) | 启动图标裁切 / 重生成 |
| [`docs/knowledge/lint-vital-fir-crash.md`](docs/knowledge/lint-vital-fir-crash.md) | AGP 9 + Kotlin 2.4.10 + lint 32.3.0 在 `.gradle.kts` 上崩的根因 + 绕过 |
| [`docs/knowledge/gitea-1.22x-release-route-broken.md`](docs/knowledge/gitea-1.22x-release-route-broken.md) | Gitea 1.22.x `releases/download/<tag>/` 对 .apk + .json 404 的根因 + `/attachments/<uuid>` 绕路 + 双 repo(代码仓库 `giteaadmin/IceSpiritAI_Vision` broken / 发布仓库 `giteaadmin/vision-app` 健康)分工;候选 nginx reverse proxy 配置在 §5 |
| [`docs/smoke/2026-08-14-phase1-smoke.md`](docs/smoke/2026-08-14-phase1-smoke.md), [`docs/smoke/2026-08-14-phase2-smoke.md`](docs/smoke/2026-08-14-phase2-smoke.md) | 烟测记录 |
| [`docs/smoke/2026-09-02-icevision-v0.1.47-release.md`](docs/smoke/2026-09-02-icevision-v0.1.47-release.md) | v0.1.47 完整发版 smoke record(pre-flight + 4 步流水线 + post-release 三段断言 + APK SHA 一致性,作 v0.1.53+ 发版参考模板) |
| [`docs/smoke/2026-08-20-icevision-v6-upgrade.md`](docs/smoke/2026-08-20-icevision-v6-upgrade.md) | PP-OCRv5→v6 升级烟测记录(2026-08-20) |
| [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](docs/knowledge/ppocrv6_vs_v5_a_b_test.md) | v6_small vs v5_mobile 在 4 张实拍广告招牌上的 A/B 实测 + 决策依据 |
| [`docs/knowledge/mascot-ui-asset.md`](docs/knowledge/mascot-ui-asset.md) | 应用内吉祥物素材(去底 PNG)生成 / 选型 |

## 启动图标

图标由 `冰灵（男）.png` 经 `tools/generate_launcher_icon.py` 生成(去白底 → 顶部对齐裁切 → 自适应前景 + 各密度回退)。当前裁切为 `y=0..1550`。构图调整、参数换算与重新生成命令见 [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md)。

## 应用内吉祥物素材

首页 Idle 预览区的胸像占位图由 `冰灵（男）形象/戴智能眼镜.jpg` 经 `tools/generate_mascot_asset.py` 生成(**rembg `isnet-general-use` 出 matte** → 补洞 → 去阶梯 → 边缘带反解 JPEG 混色 → 紧裁缩放),输出 `app/src/main/res/drawable-nodpi/mascot_glasses_bust.png`(透明底),由 `ImagePreview` 以**固定 120dp**(`IdleMascotSize`)居中显示 —— 空态装饰图用固定 dp 而不是容器百分比,否则平板 / 折叠屏上会变成广告牌(旧实现 45% 容器高 = 258dp / 774px)。**这张图不能用颜色泛洪抠**(白衬衫前襟 / 半透明镜片会被咬穿,三种阈值法均已实测失败),脚本内的 `--engine chroma` 只是别的素材的应急回退;选型理由与验收清单见 [`docs/knowledge/mascot-ui-asset.md`](docs/knowledge/mascot-ui-asset.md)。
