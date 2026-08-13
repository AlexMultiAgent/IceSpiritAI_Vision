# 冰灵锐目 (IceSpiritAI_Vision) — 初始骨架设计

**日期**：2026-08-13
**状态**：Approved — 按此设计搭建
**目标**：建立可编译的 Android 离线视觉 App 骨架,首版 `modelProfile="shell"` 仅产出空 Activity;为后续接入视觉模型判断现场照片违规违法预留架构。

---

## 1. 项目定位

冰灵锐目是"冰灵"家族的第三个离线 AI 应用,核心职责:

- 输入:用户拍摄或选择的现场照片
- 离线视觉模型判断:照片是否包含违规违法场景(侵占、违建、隐患、作业不规范等)
- 输出:违规类型 + 置信度 + 法规依据提示

定位与冰灵慧语(对话)、冰灵智译(翻译)并列,三者共享同一套离线 + Android Kotlin + Gradle 8.10.2 工程基线。

---

## 2. 命名与一致性

| 项 | 值 | 备注 |
| --- | --- | --- |
| 中文名 | 冰灵锐目 | 字面:锐利目光,贴合"视觉判违"语义 |
| 仓库文件夹 | `IceSpiritAI_Vision` | 大写 V 与 Chat/Translate 保持 |
| Application ID | `com.icespiritai.vision` | 三项目中唯一不同,体现独立分发 |
| 源码 namespace | `com.icespiritai.offline` | **三项目统一**,允许共享 utility 模块后续抽出 |
| Gradle rootProject | `IceSpirit` | 同 Translate,简写 |
| app_name 字符串 | 冰灵锐目 | 中文界面 |
| 主题样式 | `Theme.IceSpiritOffline` | 与 Chat/Translate 同名,Material3 baseline |

**为什么不沿用 `com.icespiritai.offline` 作为 applicationId?**
applicationId 是 Android 设备上的"包名身份",**必须唯一**(同设备不可装两个同 ID 的 APK)。Chat / Translate / Vision 三个 App 在用户机器上共存,故 applicationId 必须不同。但源码 namespace(`R` 类生成位置、internal package)可以共享——这一分离让未来抽出共享 Kotlin 模块时不需要重命名。

---

## 3. 架构决策

### 3.1 modelProfile 系统

首版**只产出 shell**(空 Activity,Hello World),但 build.gradle.kts 已预留 `modelProfile` Gradle property 通路:

```kotlin
val modelProfile = providers.gradleProperty("modelProfile")
    .getOrElse("shell")
```

| Profile | 状态 | 含义 |
| --- | --- | --- |
| `shell` | **首版启用** | 不引入视觉模型,App 仅展示骨架界面 |
| `ice_vision` | 未来 | 引入量化视觉模型(~150-300 MB),GGUF/ONNX/MNN 任一 |
| `ice_vision_minimal` | 未来 | 引入轻量分类模型(~30-80 MB),仅判断"是否违规"二分类 |

切换 profile 用:`./gradlew assembleDebug -PmodelProfile=ice_vision`。
首版 CI/local 默认 `shell`,保证构建轻量。

### 3.2 为什么先 shell?
视觉模型选型(GGUF / ONNX / MNN / MediaPipe)依赖具体的判违需求(分类/检测/分割),在需求细化前 hardcode 一个模型没有价值。先把工程基线立住,后续接入模型的 PR 只需:
1. 添加模型 bundle 到 `app/src/main/assets/models/<bundle>/`
2. 启用对应 `buildFeatures`
4. 在 `IceSpiritVisionActivity` 装载并推理

---

## 4. 工程基线(与 Chat/Translate 对齐)

| 项 | 值 | 备注 |
| --- | --- | --- |
| AGP | 8.5.2 | 与 Chat/Translate 一致 |
| Kotlin | 1.9.24 | 与 Chat/Translate 一致 |
| Gradle Wrapper | 8.10.2 | 与 Chat/Translate 一致 |
| compileSdk / targetSdk | 35 | |
| minSdk | 26 | Android 8.0+,覆盖 ≥99% 设备 |
| ABI | `arm64-v8a` 单架构 | 离线模型单架构分发 |
| versionCode | 1 | |
| versionName | `0.1.0` | SemVer 3 段,初始 |
| JVM args | `-Xmx3072m -Dfile.encoding=UTF-8` | WIN runner 8 GiB 实测配置 |
| workers.max | 1 | 镜像 Translate 轻量配置 |
| nonTransitiveRClass | true | |
| nonFinalResIds | true | |
| Maven 镜像 | Aliyun 主 + Tencent/Huawei 备 | 镜像 Translate 设置 |

---

## 5. 仓库目录(初始)

```
D:\GitHub\IceSpiritAI_Vision\
├── .gitignore
├── .gitattributes
├── README.md
├── CLAUDE.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/icespiritai/offline/
│       │   └── IceSpiritVisionActivity.kt
│       └── res/values/
│           ├── strings.xml
│           └── themes.xml
├── docs/                              (空目录,预留规范 / 手册 / specs)
└── 发布版历史存档/                      (空目录,本地 APK 备份,与 Translate 对齐)
```

---

## 6. 首版交付物

1. 上述目录树全部创建并 `git add`
2. `./gradlew.bat assembleDebug -PmodelProfile=shell` 编译成功,产物 `app/build/outputs/apk/debug/app-debug.apk`
3. 在 Android 设备/模拟器上启动,看到"冰灵锐目"标题与 "Hello Vision" 占位文案

---

## 7. 后续路线(非本 spec 范围)

- v0.2.0: 接入拍照/选图 + 预览 UI
- v0.3.0: 选型 + 接入 `ice_vision_minimal` profile(Y/N 二分类)
- v0.4.0+: 多标签分类 + 法规依据 prompt 模板 + 历史记录本地存储

每个里程碑单独 brainstorm + spec + plan,不与本骨架 spec 耦合。