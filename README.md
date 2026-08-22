# 冰灵锐目 (IceSpiritAI_Vision)

离线视觉判违 Android 应用 —— 使用本地视觉模型判断现场照片是否包含违规违法场景。

## 状态

**v0.1.18** — 离线 OCR + 规则库判违已接入(`modelProfile="ice_ocr_rules"`)。

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | 默认 / 空骨架 | Fake OCR + slim rules,APK 不带模型 |
| `ice_ocr_rules` | shipped | PP-OCRv6_small + PaddleOCR v3.7.0 SDK + 广告招牌/食品标识规则库 |
| `ice_vision` | 未来 | 端侧 VLM(多标签 + 法规依据) |

详细设计见 [`docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md`](docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md)。

## 工程基线

- Android Gradle Plugin 9.3.0 / Kotlin 2.4.10 / Gradle Wrapper 9.7.0
- compileSdk 37 / targetSdk 37 / minSdk 26
- ABI: `arm64-v8a` 单架构
- 命名空间 `com.icespiritai.offline`(与冰灵慧语/冰灵智译一致)
- Application ID `com.icespiritai.vision`(独立分发)
- Maven 镜像:Aliyun 主 + Tencent/Huawei 备
native libs 打包需 `packaging.jniLibs.useLegacyPackaging = true`(AGP 9 默认 `false`,Android 14/15 下 `System.loadLibrary` 找不到 `.so`)。

## 构建

```bash
# 默认 shell profile(空骨架)
./gradlew.bat assembleDebug -PmodelProfile=shell

# Phase 1 shipped(PP-OCRv6_small + 广告招牌/食品标识规则库 + ONNX 模型)
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

产物:`app/build/outputs/apk/debug/app-debug.apk`

## 三项目对照

| 项目 | 仓库 | Application ID | 核心职责 |
| --- | --- | --- | --- |
| 冰灵慧语 | IceSpiritAI_Chat | com.icespiritai.chat | 离线对话 |
| 冰灵智译 | IceSpiritAI_Translate | com.icespiritai.translate | 中俄离线翻译 |
| 冰灵锐目 | IceSpiritAI_Vision | com.icespiritai.vision | 离线视觉判违 |
