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

## modelProfile 系统

Gradle property `modelProfile` 控制当前构建启用哪个模型配置:

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | **默认 / 首版** | 仅展示骨架;UI 可跑,Fake OCR + slim rules,APK 不带模型 |
| `ice_ocr_rules` | Phase 1(shipped) | PaddleOCR v3.7.0 SDK(走 ONNX Runtime + OpenCV)+ AdLawRuleMatcher 已接入;rules JSON 从 `assets/rules/ad_law_rules.json` 10 条 golden rules 出;ONNX 模型(bundled in APK)在 `assets/models/{det,rec}/inference.onnx` + `inference.yml` |
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

Phase 1 走 OCR + 规则库路线(PaddleOCR 官方 SDK v3.7.0 + HankCS AC 自动机)。候选从 PaddleOCR-slim / Paddle-Lite / ONNX Runtime / MediaPipe Tasks 收敛到:**PaddleOCR 官方 SDK** 走 **ONNX Runtime + OpenCV**(Android 端 nn 推理),不再 hardcode 视觉模型路线。

二分类 / 多标签视觉模型留 Phase 2+,首版不引入。

## 构建命令

```bash
# 默认(骨架 APK,Fake OCR + slim rules)
./gradlew.bat assembleDebug -PmodelProfile=shell

# Phase 1 shipped(PaddleOCR v3.7.0 + 10 条 golden rules + ONNX 模型)
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

## 开发环境

- **JDK 17**:buildSrc 锁定 `jvmToolchain(17)`(forward-path baseline)。WIN runner 默认 `JAVA_HOME` 是 JDK 25(找不到匹配 toolchain,build 启动失败)。本仓库已手动 stage 的路径:`/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`(OpenJDK 17.0.18+8)。运行命令前必须显式 `export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"`。`gradle.properties` 另开 `org.gradle.java.installations.auto-download=true` 兜底(foojay 镜像在 CN 受限,通常需手动 stage)。
- **NDK 28.2.13676358**(r28c):Android SDK Manager 安装,版本由 `app/build.gradle.kts` 的 `ndkVersion` 锁定。
- **JVM 堆**:Gradle daemon `-Xmx3072m`(`gradle.properties`);本机若启用 R8 + Lint + native build,建议 ≥8 GiB 可用内存,避免 daemon OOM。

## 签名(v1 必须开启)

`signingConfigs.release` 必须 `enableV1Signing = true`(AGP 默认 v2-only)。in-app update verifier 用 `JarFile` + `META-INF/CERT.RSA` 读 v1 证书做 cert-pin 校验,若 APK 仅 v2/v3 签名,verifier 返回 `null`,所有合法更新都会被拒。Release 凭据在 `~/.gradle/gradle.properties`(gitignored),Gitea PAT 在 `gradle.token.properties`(gitignored,见 `gradle.token.properties.example` 模板)。

## Commit 策略(必读)

- 所有 commit 作者必须是 `AlexMultiAgent`(仓库 git config 已锁)。**绝不要** 加 `Co-Authored-By: Claude` trailer。
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

## 启动图标

图标由 `冰灵（男）.png` 经 `tools/generate_launcher_icon.py` 生成(去白底 → 顶部对齐裁切 → 自适应前景 + 各密度回退)。当前裁切为 `y=0..1550`。构图调整、参数换算与重新生成命令见 [`docs/knowledge/launcher-icon-generation.md`](docs/knowledge/launcher-icon-generation.md)。
