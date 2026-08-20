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
| 规则 + 加载器 | `AdSignageRuleLoader` + `FoodLabelRuleLoader` 双装载入口保留;`food_label_rules.json`(66 条 / v4)+ `ad_signage_rules.json`(118 条 / v5)均随 APK 出 |
| 知识库 | [知识库/](知识库/) 双域 markdown 完整保留;`广告业务/` 是当前打磨中的成熟参考,`食品标识/` 是待后续套用的对象 |
| 测试 | [FoodLabelRuleMatcherTest.kt](app/src/test/java/com/icespiritai/offline/rules/FoodLabelRuleMatcherTest.kt) + [IceSpiritVisionViewModelTabTest.kt](app/src/test/java/com/icespiritai/offline/IceSpiritVisionViewModelTabTest.kt) 双 tab 路由断言保留 |
| 打磨策略 | ad_signage_rules.json 关键词命中 / 严重度分级 / category 显示 / 证据包导出全部以"可复制到下一个视觉判别域"为标尺优化,达标后再以同样模式启用 FoodLabeling tab |

**为什么保留 FoodLabeling 代码路径不删**:FoodLabeling 是「广告招牌模式 → 其他视觉判别域」的可复制模板,把 `FoodLabelRuleMatcher` + domain 字段 + 知识库 + 类别显示一并删除等于砍掉这条扩展路。`RuleTabBar.kt` 顶部注释明示"恢复时把 `visibleTabs` 改回 `RuleTab.entries.toList()` 即可"。

## modelProfile 系统

Gradle property `modelProfile` 控制当前构建启用哪个模型配置:

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | **默认 / 首版** | 仅展示骨架;UI 可跑,Fake OCR + slim rules,APK 不带模型 |
| `ice_ocr_rules` | Phase 1(shipped) | **PP-OCRv6_small**(2026-08-20 升级,rec dict 18708 条)经 PaddleOCR v3.7.0 SDK(走 ONNX Runtime + OpenCV)+ AdSignageRuleMatcher + FoodLabelRuleMatcher 已接入;rules JSON 从 `assets/rules/ad_signage_rules.json`(广告招牌 118 条 / v5)与 `assets/rules/food_label_rules.json`(食品标识 66 条 / v4)出;ONNX 模型(bundled in APK)在 `assets/models/{det,rec}/inference.onnx` + `inference.yml` |
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

## 视觉/OCR 模型路线(2026-08 锁定)

Phase 1 走 OCR + 规则库路线(**PP-OCRv6_small** + PaddleOCR 官方 SDK v3.7.0 + HankCS AC 自动机)。候选从 PaddleOCR-slim / Paddle-Lite / ONNX Runtime / MediaPipe Tasks 收敛到:**PaddleOCR 官方 SDK** 走 **ONNX Runtime + OpenCV**(Android 端 nn 推理),不再 hardcode 视觉模型路线。

二分类 / 多标签视觉模型留 Phase 2+,首版不引入。

**PP-OCRv6_small 升级决策(2026-08-20)**:`ice_ocr_rules` profile 默认 ONNX 模型由 `pp-ocrv5_mobile` 替换为 `pp-ocrv6_small`(rec dict 18708 条,det +5MB / rec +5MB APK 体积增长)。4 张测试集子文件夹 A/B 实测:v6 vs v5 = **+12% 检出行 / +5.4% 平均置信 / −10% 端侧耗时 / 5× 规则命中数**(核心案例:蟹都汇"大闸蟹连锁门店数量全国第一"v6 检出,v5 漏报并误识为 "全国谢")。`tools/download-ppocr-models.sh` 默认 variant 已切到 `pp-ocrv6_small`。详细数据与 4 图分项对比见 [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](docs/knowledge/ppocrv6_vs_v5_a_b_test.md)。

## 构建命令

```bash
# 默认(骨架 APK,Fake OCR + slim rules)
./gradlew.bat assembleDebug -PmodelProfile=shell

# Phase 1 shipped(PP-OCRv6_small + PaddleOCR v3.7.0 + 广告招牌 116 条 / 食品标识 66 条 + ONNX 模型)
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules

# 单元测试 / Lint
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug

# 清理
./gradlew.bat clean
```

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

## Instrumented test / 真机 A/B (androidTest)

跑 `connectedDebugAndroidTest` 在华为 nova 6(ANN-AN00,SDK 35)上踩过的坑,后人不要重蹈:

- **`connectedDebugAndroidTest` 不接 `--tests`**(常规 JUnit 平台参数 AGP 不透传)。单 class 过滤用 Gradle property:`-Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.Xxx`(AGP 透传为 `-e class <FQN>` 到 AndroidJUnitRunner)。
- **`app/src/androidTest/assets/` 打进的是 test APK,不是 main APK**。Fixture 图必须放这里,**不要** `adb push /sdcard/` + ContentResolver — 流程不稳,`openInputStream` 走不通。要么 assets,要么 staging 到 `appCtx.cacheDir` 后从 test APK assets 拷入。
- **Logcat ring buffer 几分钟内轮转 — 测试后再 `adb logcat -d` 拉不到自己 tag 的数据**。配套模式:`adb logcat -c; (adb logcat -v time TAG:I '*:S' > file.out) &; ./gradlew connectedDebugAndroidTest ...` 后台捕获必须在测试启动前开,test harness 用 `Log.i("MyTag", ...)` 落数据。
- **`@Test fun foo() = runBlocking { ... Log.i(...) }` 返回 Int**(Log.i 的返回),JUnit 校验拒绝("Method should be void"),`runBlocking` body 末尾必须显式 `Unit`。
- **真机冷启动 vs warm 延迟必须分开报**。`PaddleOCR.create()` 模型加载一次性 ~5s,per-image warm 平均 ~2.6s(华为 nova 6 ARM64 + 4-thread),混在一起看不出趋势。harness 模式:1 次 cold + N 次 warm,分别计 `cold_ms` / `warm_total_ms` / `warm_avg_ms`。
- **华为 nova 6 PackageManager ghost state**:`adb install -r` 可能 `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ... signatures do not match`,但 `pm list packages` 看不到该包。`pm uninstall` / `pm uninstall -k` 均 `DELETE_FAILED_INTERNAL_ERROR`;`pm clear` 报 "Failed" 但 exit 0。**workaround**:`adb shell pm clear com.icespiritai.vision` 后再 `adb install -r APK` 即可 — `pm clear` 虽报错但会把 ghost state 清掉。

## 开发环境

- **JDK 17**:buildSrc 锁定 `jvmToolchain(17)`(forward-path baseline)。WIN runner 默认 `JAVA_HOME` 是 JDK 25(找不到匹配 toolchain,build 启动失败)。本仓库已手动 stage 的路径:`/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`(OpenJDK 17.0.18+8)。运行命令前必须显式 `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"`。`gradle.properties` 另开 `org.gradle.java.installations.auto-download=true` 兜底(foojay 镜像在 CN 受限,通常需手动 stage)。
- **NDK 28.2.13676358**(r28c):Android SDK Manager 安装,版本由 `app/build.gradle.kts` 的 `ndkVersion` 锁定。
- **JVM 堆**:Gradle daemon `-Xmx3072m`(`gradle.properties`);本机若启用 R8 + Lint + native build,建议 ≥8 GiB 可用内存,避免 daemon OOM。

## 签名(v1 必须开启)

`signingConfigs.release` 必须 `enableV1Signing = true`(AGP 默认 v2-only)。in-app update verifier 用 `JarFile` + `META-INF/CERT.RSA` 读 v1 证书做 cert-pin 校验,若 APK 仅 v2/v3 签名,verifier 返回 `null`,所有合法更新都会被拒。Release 凭据在 `~/.gradle/gradle.properties`(gitignored),Gitea PAT 在 `gradle.token.properties`(gitignored,见 `gradle.token.properties.example` 模板)。

## Commit 策略(必读)

- 所有 commit 作者必须是 `AlexMultiAgent`(仓库 git config 已锁)。**绝不要** 加 `Co-Authored-By: Claude` trailer——也包括 `Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>` 这种把 `user.name` 替换成 `AlexMultiAgent` 但保留 anthropic 邮箱的隐性 AI agent trailer(2026-08-20 audit 发现历史 commit 全部命中此形式)。提交前 `git log -1 --format='%B' | grep -i 'Co-Authored-By'` 应为空。
- `gradle.token.properties`(Gitea PAT)、`~/.gradle/gradle.properties`(release signing)已在 `.gitignore`,不要尝试 commit 它们。
- 提交前 `git status` 检查是否包含敏感文件;`git add` 用具体路径,避免 `git add -A`。

## 文档索引

| 文档 | 用途 |
|---|---|
| [`README.md`](README.md) | 仓库入口说明 |
| [`docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md`](docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md) | **Phase 1 实际规范**(OCR + 规则库),取代 init spec 的 §6 视觉二分类与 §4 工具链基线 |
| [`docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`](docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md) | 初版 init 规范,仅骨架 / 命名空间 / 目录布局仍生效 |
| [`docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md`](docs/superpowers/specs/2026-08-14-icevision-phase2-hardening-design.md) | Phase 2 硬化设计 |
| [`docs/superpowers/specs/2026-08-15-icevision-ui-design.md`](docs/superpowers/specs/2026-08-15-icevision-ui-design.md) | UI 设计稿 |
| [`docs/knowledge/build-stack-2026-08.md`](docs/knowledge/build-stack-2026-08.md) | 命名一致性表(AGP / Kotlin / Gradle / SDK / NDK)的版本依据 |
| [`docs/knowledge/cross-project-implications.md`](docs/knowledge/cross-project-implications.md) | 本 baseline 对冰灵慧语 / 智译两个项目的迁移含义 |
| [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md) | 启动图标裁切 / 重生成 |
| [`docs/knowledge/lint-vital-fir-crash.md`](docs/knowledge/lint-vital-fir-crash.md) | AGP 9 + Kotlin 2.4.10 + lint 32.3.0 在 `.gradle.kts` 上崩的根因 + 绕过 |
| [`docs/smoke/2026-08-14-phase1-smoke.md`](docs/smoke/2026-08-14-phase1-smoke.md), [`docs/smoke/2026-08-14-phase2-smoke.md`](docs/smoke/2026-08-14-phase2-smoke.md) | 烟测记录 |
| [`docs/smoke/2026-08-20-icevision-v6-upgrade.md`](docs/smoke/2026-08-20-icevision-v6-upgrade.md) | PP-OCRv5→v6 升级烟测记录(2026-08-20) |
| [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](docs/knowledge/ppocrv6_vs_v5_a_b_test.md) | v6_small vs v5_mobile 在 4 张实拍广告招牌上的 A/B 实测 + 决策依据 |

## 启动图标

图标由 `冰灵（男）.png` 经 `tools/generate_launcher_icon.py` 生成(去白底 → 顶部对齐裁切 → 自适应前景 + 各密度回退)。当前裁切为 `y=0..1550`。构图调整、参数换算与重新生成命令见 [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md)。
