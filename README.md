# 冰灵锐目 (IceSpiritAI_Vision)

离线视觉判违 Android 应用 —— 使用本地视觉模型判断现场照片是否包含违规违法场景。

## 状态

**v0.6.0** — 激活码签发工具独立 `.exe`(v0.6.0 发布内容见 [user-changelog](app/src/main/assets/user-changelog.md))。此后一轮审计修复:`RegionOcrRunner` 坐标空间(放大识别新增的行,图上高亮框现在落回**原图**坐标)+ 眼镜 Wi-Fi 诊断补运行时权限门控(定位 / 附近 Wi-Fi 设备,失败提示直接点名缺哪个权限)+ `UpdateDownloadService` 把 `startForeground` 提到 DataStore 读之前(消除 5 s 崩溃窗口)+ TTS 模型 sha256 锚定 `BuildConfig` + 删除 TTS 引擎 APK 明文下载安装通道、`GlassesScan` 与 14 MB jieba 词典死资源 + 单测与 `modelProfile` 解耦、CI 增编 `ice_ocr_rules`。

(历史发版见 [app/src/main/assets/user-changelog.md](app/src/main/assets/user-changelog.md))

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | 默认 / 空骨架 | ShellStub OCR + slim rules,APK 不带模型 |
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
