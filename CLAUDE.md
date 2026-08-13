# 冰灵锐目 (IceSpiritAI_Vision) — 项目协作约定

本文件记录本项目独有的、与 `~/.claude/CLAUDE.md` 默认约定不同的内容。

## 命名一致性(三项目统一)

| 项 | 值 |
| --- | --- |
| 源码 namespace | `com.icespiritai.offline` |
| Gradle rootProject name | `IceSpirit` |
| 主题样式名 | `Theme.IceSpiritOffline` |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| ABI | `arm64-v8a` only |
| Gradle Wrapper | 8.10.2 |
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| Maven 镜像 | Aliyun 主 + Tencent/Huawei 备 |

**三项目唯一不同的字段是 `applicationId`(对应设备上的独立包名身份):
- 冰灵慧语 `com.icespiritai.chat`
- 冰灵智译 `com.icespiritai.translate`
- 冰灵锐目 `com.icespiritai.vision`

## modelProfile 系统

Gradle property `modelProfile` 控制当前构建启用哪个模型配置:

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | **默认 / 首版** | 不引入视觉模型,仅展示骨架 |
| `ice_vision_minimal` | 未来 | 轻量二分类模型 |
| `ice_vision` | 未来 | 多标签 + 法规依据 |

切换方式:`./gradlew assembleDebug -PmodelProfile=<name>`

## 视觉模型选型(尚未决定)

候选:GGUF (llama.cpp Android port) / ONNX Runtime / MNN / MediaPipe Tasks。
选型依据应来自具体判违需求(分类 vs 检测 vs 分割),首版不 hardcode。

## 构建命令

```bash
# 默认
./gradlew.bat assembleDebug -PmodelProfile=shell

# 清理
./gradlew.bat clean
```

## 仓库布局

参见 [`README.md`](README.md) 与 [`docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`](docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md)。