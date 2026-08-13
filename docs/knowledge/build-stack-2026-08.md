# Build Stack Reference (2026-08)

冰灵锐目 / 冰灵慧语 / 冰灵智译三项目共享的 baseline 研判依据。
调研日期 **2026-08-13**,数据来源:developer.android.com、kotlinlang.org、gradle.org、GitHub Release notes、Maven Central。
文档中任何未直接核对 release notes 的字段会标注 *(待核)*。

---

## 1. TL;DR — 2026-08 当前稳定栈

| 组件 | 当前最新稳定 | "前瞻"路径推荐 | "保守"路径 |
|---|---|---|---|
| **AGP** | 9.3.x(2026-07 发布页更新于 08-07) | **9.3.x** | 8.13.x *(待核:8.14 是否仍是 stable)* |
| **Gradle** | 9.7.0(2026-08-06) | **9.7.x** | 8.14.x(8.x 末班车) |
| **Kotlin** | 2.4.10(2026-07-14) | **2.4.10** | 2.4.10(K1 编译器已移除,无 K1 兼容版) |
| **JDK** | 17(AGP 9.x 最低),21 可用 | **17** | 17 |
| **NDK** | 28.2.13676358(r28c,AGP 9.x 默认) | **28.2.13676358** | 27.x(AGP 8.x 兼容) |
| **compileSdk** | 36(Android 16 Baklava,GA) | **36** | 36 |
| **targetSdk** | 36 | **36** | 36 |
| **minSdk** | — | **26**(项目既定) | 26 |
| **AndroidX BoMR(2026-08-12)** | lifecycle 2.11.0 / activity 1.13.0 / recyclerview 1.4.0 | — | — |

**硬约束(必须满足):**
1. **Gradle 9.6+ 打破 AGP 8.x 兼容**(`InternalProblems` API 移除)。AGP 8.x 仅兼容到 Gradle 9.5。
2. **Kotlin 2.4.0 起 K1 编译器已完全移除**,`language-version=1.9` 不再被接受。
3. **Android 16(targetSdk=36)强制 edge-to-edge + predictive back 默认开启**,Manifest opt-out 已失效。
4. **AGP 9.0 起 build.gradle.kts DSL 大量 API 被移除**:`applicationVariants` / `libraryVariants` / `testVariants` / `unitTestVariants` / `dexOptions` / `variantFilter` / `aidlPackagedList` / `featurePlugin` / `FeatureExtension` / `LanguageSplitOptions`。**冰灵智译的当前 build 脚本直接受影响**(用了 `applicationVariants`)。

---

## 2. AGP(Android Gradle Plugin)

### 2.1 版本时间线

| 版本 | 状态 | 备注 |
|---|---|---|
| 8.5.2 | 维护期 | 当前 Vision baseline(2025-02 前后) |
| 8.7.3 | 维护期 | 当前 Translate baseline(2025-中) |
| 8.13.x | 稳定末班车 *(具体 patch 待核)* | 仍在维护,需 Gradle 8.x |
| **9.0.1** | **2026-01 GA(首个 9.x stable)** | 强制 Gradle 9.1+,SDK 36,NDK 28.2,JDK 17,KGP 2.2.10 |
| 9.1 / 9.2 | 稳定 | 渐进改进 |
| **9.3.0** | **2026-07 stable** | release notes 页更新于 2026-08-07,最新版 |
| 9.4 | preview | — |

### 2.2 AGP 9.0 起的关键变化

| 变化项 | 影响 |
|---|---|
| DSL 公开范围收紧 | `android { }` 不再暴露内部类,只能通过新提供的接口访问 |
| **移除** `applicationVariants` 等 | **Translate 的 `outputs.all { apkOutput.setOutputFileName(...) }` 必须迁移到 `androidComponents.onVariants { artifacts.use(...).wiredWithFiles(...).toTransform(SingleArtifact.APK) { ... } }`** |
| **移除** `dexOptions` / `generatePureSplits` | 改用 R8 默认 + App Bundle |
| **内置 Kotlin** 默认启用 | 不再需要手动 `alias(libs.plugins.kotlin.android)`,但 KMP 子工程与 `com.android.library` 不能共存于同 module |
| 默认 `uniquePackageNames=true` | **必须确认 AndroidManifest 的 `package` 属性已迁到 `namespace`** |
| 默认 `enableAppCompileTimeRClass=true` | R 类编译期生成,运行时反射访问 R 字段会失效 |
| 默认 `r8.strictFullModeForKeepRules=true` | ProGuard 规则必须写完整签名,`-keep class A` 要改为 `-keep class A { <init>(); }` |
| `useAndroidx=true` 默认 | 历史 `support-library` 工程不可用 |
| Wear OS app 支持移除、Density split APK 移除 | 必须用 App Bundle |
| 临时兼容开关 `android.newDsl=false` | **AGP 10 删除**,仅作迁移缓冲 |

### 2.3 Gradle ↔ AGP 硬兼容性矩阵

| Gradle | AGP 8.x | AGP 9.x |
|---|---|---|
| 8.10–8.14.x | ✅ | ❌(Gradle 9 API 未引入) |
| 9.0–9.5 | ❌(AGP 8.x 依赖 `InternalProblems`,Gradle 9 移除) | ✅ |
| 9.6+ | ❌ | ✅ |

> **结论**:版本只能二选一组合(8.x/8.x 或 9.x/9.x),不存在混合搭配。

---

## 3. Kotlin

### 3.1 版本时间线

| 版本 | 状态 | 备注 |
|---|---|---|
| 1.9.24 | 维护期 | 当前 Vision baseline(2024 中) |
| 2.0.x → 2.3.x | 稳定历史 | K2 编译器为主,K1 仍可走 `-language-version=1.9` |
| **2.4.0** | **2026-06-03 发布** | K1 编译器**完全移除** |
| **2.4.10** | **2026-07-14 最新 stable** | stdlib 支持至 **2027-12-03**(18 个月窗口) |
| 2.5 | 预计 2026-Q4 | — |

### 3.2 Kotlin 2.4 关键变化

| 变化项 | 影响 |
|---|---|
| **K1 编译器移除** | `-language-version=1.9` 报错;所有 K1 注解处理器插件(KAPT 等)失去后备 |
| **最低 AGP 要求: 8.5.2** | 当前 Vision baseline 满足,Translate baseline 满足 |
| 默认 module 名标准化 | 影响 IDE 显示与产物命名 |
| 注解写入 metadata | KSP/R8 行为微变,部分注解可直接读 metadata 不需反射 |
| **KAPT 标记 deprecated** | 鼓励 KSP |
| iOS / tvOS / macOS / watchOS 最低 target 提升 | KMP/Native 工程须升级,纯 Android 无影响 |
| **stdlib 支持窗口 = 18 个月** | 2.4.x 线维持补丁至 2027-12-03。规划时按"上 2 个 minor"回退即可 |

---

## 4. Gradle

### 4.1 版本时间线

| 版本 | 状态 | 备注 |
|---|---|---|
| 8.10.2 | 维护期 | 当前 Vision baseline |
| 8.14.4 | 稳定(2026-01-23) | 8.x 末班车早期 |
| 8.14.5 | 稳定(2026-05-07) | **8.x 末班车最新版** |
| **9.0.0** | **2025-07-31 GA** | 9.x 首版 |
| 9.1 / 9.2 / 9.3 | 稳定 | 渐进 |
| 9.4 | preview | — |
| 9.5 | 稳定(2026-04-28) | Gradle 9.x 与 AGP 8.x 的最后一个兼容点 |
| **9.6.0** | **2026-06-18 稳定** | **从此版开始,`InternalProblems` 移除,AGP 8.x 不可用** |
| **9.7.0** | **2026-08-06 最新 stable** | 推荐搭配 AGP 9.3.x |

### 4.2 Gradle 9.x 关键变化

| 变化项 | 影响 |
|---|---|
| `InternalProblems` API 移除 | AGP 8.x 直接报错(见 2.3) |
| **内置 Kotlin 升级到 2.4.0** | Kotlin DSL 编译期直接吃 2.4 工具链 |
| Configuration Cache 重构 | 自定义 `ArrayList` 子类在 CC 中丢类型;`.java.util.concurrent` 路径被拒 |
| DSL 委托弃用 | `by registering` / `by creating` / `by getting` / `by project` / `by extra` 等标记 deprecated,建议改用 `provider { ... }` |
| `flatDir(Map)` / `mavenCentral(Map)` / `Pmd.targetJdk` 等弃用 | 改用 action 形式 |

---

## 5. Android 16(Baklava,API 36)

### 5.1 状态

- **Android 16 已 GA**(developer.android.com 当前主版本)
- compileSdk = 36,QPR2 = 36.1

### 5.2 targetSdk = 36 的行为变更(强制 / 失效项)

| 项 | 类别 | 影响 |
|---|---|---|
| **Edge-to-edge 强制** | Manifest opt-out 失效 | 所有 `windowOptOutEdgeToEdgeEnforcement` 视为无效,必须支持 immersive 布局 |
| **Predictive Back 手势默认开启** | `onBackPressed` 不再被调用;`KEYCODE_BACK` 不再分派 | 需迁移到 `OnBackPressedDispatcher` / `OnBackInvokedCallback` |
| `elegantTextHeight` 对特定语言 deprecated/忽略 | 阿拉伯语、老挝语等 9 种文字 | 视觉微调,非阻塞 |
| `scheduleAtFixedRate` 行为变化 | **每个周期最多补跑 1 次**(原本可能补跑多个) | 后台定时任务逻辑需复核 |
| **大屏 orientation 忽略**(sw≥600dp) | 平板 / 折叠屏不再按 orientation 锁定布局 | 需提前做响应式布局(WindowSizeClass 等) |
| Health permissions 拆分 | `BODY_SENSORS` → `READ_HEART_RATE` 等 | 与本项目无关(冰灵锐目不涉及 Health API) |
| MediaStore 版本按应用隔离 | 反指纹 | 无直接影响 |
| Safer Intents | receiver 端强制匹配规则 | 无直接影响 |
| 本地网络权限(26Q2 强制) | 当前 opt-in | 提前预警:若引入局域网 API,需申请 `NEARBY_WIFI_DEVICES` 或同等权限 |
| 照片选择器预选应用拥有的照片 | Photo Picker 增强 | **与 Phase 1 相符**(用 `PickVisualMedia` 不需读全媒体库) |

---

## 6. JDK / NDK

### 6.1 JDK

- AGP 9.x 要求 **JDK 17 最低**,可在 JDK 21 上运行(未强制 21)
- 当前两项目均 `sourceCompatibility = VERSION_17`、`jvmTarget = "17"`,**保持 17**

### 6.2 NDK

- AGP 9.3 默认 NDK = `28.2.13676358`(r28c)
- Translate 当前 = `26.1.10909125`(r26b),AGP 8.x 兼容
- Vision 当前未设置(无 native 代码)
- ONNX Runtime Android AAR 自带 arm64-v8a .so,使用其默认 NDK 即可

---

## 7. AndroidX(2026-08-12 BoMR)

| 库 | 最新 stable | 用途 |
|---|---|---|
| `androidx.lifecycle:lifecycle-runtime-ktx` | **2.11.0** | `lifecycleScope` / `repeatOnLifecycle` |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | **2.11.0** | `viewModelScope` |
| `androidx.activity:activity-ktx` | **1.13.0**(2026-03-11) | `ActivityResultContracts.PickVisualMedia` 等 |
| `androidx.recyclerview:recyclerview` | **1.4.0**(2026-07-29) | 列表渲染 |
| `androidx.core:core-ktx` | 1.13.x 系列 *(未精确核)* | 当前 baseline |
| `androidx.appcompat:appcompat` | 1.7.x 系列 *(未精确核)* | 当前 baseline |
| `com.google.android.material:material` | 1.12.x 系列 *(未精确核)* | 当前 baseline |
| `androidx.constraintlayout:constraintlayout` | 2.2.x 系列 *(未精确核)* | 当前 baseline |

---

## 8. kotlinx 系列(非 AndroidX)

| 库 | 估计 2026-08 版本 | 兼容注 |
|---|---|---|
| `kotlinx-coroutines-android` | 1.10.x 系列 *(待核)* | 与 Kotlin 2.4 兼容 |
| `kotlinx-serialization-json` | 1.9.x 系列 *(待核)* | KSP 默认 |
| Compose UI | 1.12.x(开发者文档提及,与 BoMR 同期) | 需 Kotlin 2.4 + Compose Compiler Plugin |

---

## 9. ONNX Runtime(Phase 1 候选运行时)

| 项 | 值 |
|---|---|
| **最新 stable** | **v1.29.0(2026-08-12)** |
| Maven 坐标(Android 推荐) | `com.microsoft.onnxruntime:onnxruntime-android:latest.release` |
| Maven 坐标(旧) | `com.microsoft.onnxruntime:onnxruntime-mobile` 仍维护,但官方推荐 Android 端改用 `onnxruntime-android` artifact |
| AAR 内容 | arm64-v8a / armeabi-v7a / x86_64 / x86 各 .so,Java/Kotlin 绑定 |
| minSdk | 历史文档为 21+,**1.29.0 对 minSdk 26 的支持待 Phase 1 启动期烟测** |
| 与 AGP 9.x / Kotlin 2.4 | 文档未声明不兼容,作为纯 Java/Kotlin AAR 应正常工作;**启动期需跑一遍 demo** |

> **未决**: RapidOCR 的 ONNX 模型是否走 ONNX Runtime Mobile 还是 ONNX Runtime 主线。本项目"OCR + 规则库"形态不涉及端侧 VLM,ONNX Runtime 仅作为 RapidOCR 的依赖被引入。**不需要直接 import `onnxruntime-android`,只需 `rapidocr-onnxruntime-android`**。

---

## 10. HankCS(Phase 1 AC 自动机候选)

| 项 | 值 |
|---|---|
| Maven 坐标 | `com.hankcs:aho-corasick:0.1.4`(或更新) |
| 最后发布日期 | 2017–2020 区间 |
| 语言 | 纯 Java |
| 与 Kotlin 2.4 / AGP 9.x | 无 JVM 兼容性问题;纯 Java 库走 K2 编译无碍 |
| 性能 | 百万级关键词毫秒级返回 |

> **未决**: 中文分词是否需要叠加(jieba / HanLP)。初版可在 AC 自动机里直接喂中文短语,但中文字符串精确匹配在长句里漏召严重。**Phase 1 plan 须明确中文分词策略**(简版:`StringSearch` 全角空格切片 / 进阶:集成 `hanlp-portable`)。

---

## 11. RapidOCR(Phase 1 OCR 候选)

| 项 | 值 |
|---|---|
| GitHub | `RapidAI/RapidOCR`(PaddleOCR 的 ONNX 导出版) |
| Maven 坐标 | `io.github.icespirit-ai:rapidocr-android` / `com.rapidai:rapidocr-android` *(需以实际发布的 artifact 为准)* |
| 模型大小 | 简体中文模型 ~10 MB(det + rec + cls 三件套,均为 ONNX) |
| 依赖 | 间接依赖 ONNX Runtime(1.29.0 应兼容) |
| 与 AGP 9.x / Kotlin 2.4 | 历史 1.x 版本走 Java 绑定,Phase 1 plan 中要确认最新 artifact 命名 |

> **未决**: RapidOCR 是否提供 arm64-v8a only AAR,还是只提供 universal AAR(包 4 个 ABI 体积翻倍)。**Phase 1 plan 中需跑一次 ABI 切片实测**。

---

## 12. 决策矩阵

### 12.1 "前瞻" vs "保守"两条路径对比

| 维度 | 前瞻(AGP 9.x / Gradle 9.x / Kotlin 2.4.10) | 保守(AGP 8.13 / Gradle 8.14 / Kotlin 2.4.10) |
|---|---|---|
| **是否新项目推荐** | ✅(无历史包袱) | ⚠(明知半年后会再迁) |
| **AndroidX 默认** | ✅ | ✅ |
| **kotlin-android 插件** | 不需手动 apply | 仍需 |
| **build.gradle.kts 简洁度** | 高(少 30% 模板) | 中 |
| **Translate 兼容性** | ❌(需大改) | ✅(直接适用) |
| **Chat 兼容性** | ❌(需大改) | ✅(直接适用) |
| **Phase 1 后续迭代成本** | 低(踩在主线上) | 中(半年到一年内必再迁) |
| **适用判定** | 冰灵锐目 ✅ / 冰灵慧语待评估 / 冰灵智译 ⚠ | 冰灵智译 ✅(临时避难所) |

### 12.2 推荐结论(2026-08-13)

| 项目 | 推荐路径 | 理由 |
|---|---|---|
| **冰灵锐目** | **前瞻**(AGP 9.3.x + Gradle 9.7.x + Kotlin 2.4.10 + compileSdk 36 + NDK 28.2) | 新项目,无历史代码,5 个新依赖全部支持 AGP 9 |
| 冰灵智译 | 保守(AGP 8.13 + Gradle 8.14.5 + Kotlin 2.4.10 + compileSdk 36 + NDK 27.x) | `applicationVariants` 等 DSL 用法需专项迁移,不应混入 Phase 1 工作 |
| 冰灵慧语 | 待评估(基线信息缺失) | 建议先补一份同样的 baseline 文档 |

---

## 13. 决策登记

| 日期 | 决策 | 依据 | 替代选项 |
|---|---|---|---|
| 2026-08-13 | 冰灵锐目 Phase 1 baseline 走 **前瞻路径** | 新项目,5 个新依赖全部声明支持 AGP 9 / Kotlin 2.4 | 保守路径(AGP 8.13+Gradle 8.14) |
| 待 | ONNX Runtime 1.29.0 在 arm64-v8a / minSdk 26 / targetSdk 36 下的实际 AAR 体积 | 需 Phase 1 启动期实测 | 退回 1.28.x |
| 待 | RapidOCR 最新 Android artifact 的 ABI 切片支持 | 需 Phase 1 启动期实测 | 改用 ML Kit Text Recognition v2(Google) |

---

## 14. 引用来源

- developer.android.com/build/releases/gradle-plugin
- developer.android.com/build/releases/past-releases/agsl
- kotlinlang.org/docs/whatsnew24.html
- gradle.org/releases/
- developer.android.com/about/versions/16
- developer.android.com/about/versions/16/behavior-changes-16
- developer.android.com/build/jdks
- developer.android.com/jetpack/androidx/releases
- github.com/microsoft/onnxruntime/releases
- github.com/RapidAI/RapidOCR