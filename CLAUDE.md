# 冰灵锐目 (IceSpiritAI_Vision) — 项目协作约定

本文件记录本项目独有的、与 `~/.claude/CLAUDE.md` 默认约定不同的内容。

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
# 默认
./gradlew.bat assembleDebug -PmodelProfile=shell

# 清理
./gradlew.bat clean
```

## 仓库布局

参见 [`README.md`](README.md) 与 [`docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`](docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md)。