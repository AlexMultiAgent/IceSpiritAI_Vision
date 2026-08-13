# 冰灵锐目 (IceSpiritAI_Vision)

离线视觉判违 Android 应用 —— 使用本地视觉模型判断现场照片是否包含违规违法场景。

## 状态

**v0.1.0** — 可编译骨架 (`modelProfile="shell"`,仅展示 Activity,未接入视觉模型)。

后续版本将引入:
- 拍照/选图 UI
- `ice_vision_minimal` profile(Y/N 二分类,~30-80 MB 模型)
- `ice_vision` profile(多标签分类 + 法规依据,~150-300 MB 模型)

详细设计见 [`docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`](docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md)。

## 工程基线

- Android Gradle Plugin 8.5.2 / Kotlin 1.9.24 / Gradle Wrapper 8.10.2
- compileSdk 35 / targetSdk 35 / minSdk 26
- ABI: `arm64-v8a` 单架构
- 命名空间 `com.icespiritai.offline`(与冰灵慧语/冰灵智译一致)
- Application ID `com.icespiritai.vision`(独立分发)
- Maven 镜像:Aliyun 主 + Tencent/Huawei 备

## 构建

```bash
# 默认 shell profile(空骨架)
./gradlew.bat assembleDebug -PmodelProfile=shell
```

产物:`app/build/outputs/apk/debug/app-debug.apk`

## 三项目对照

| 项目 | 仓库 | Application ID | 核心职责 |
| --- | --- | --- | --- |
| 冰灵慧语 | IceSpiritAI_Chat | com.icespiritai.chat | 离线对话 |
| 冰灵智译 | IceSpiritAI_Translate | com.icespiritai.translate | 中俄离线翻译 |
| 冰灵锐目 | IceSpiritAI_Vision | com.icespiritai.vision | 离线视觉判违 |