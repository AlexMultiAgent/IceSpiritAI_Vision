# Cross-Project Implications — 冰灵智译 / 冰灵慧语

[`build-stack-2026-08.md`](./build-stack-2026-08.md) 的结论对其他两个项目的具体含义。
调研日期 **2026-08-13**。

---

## 1. 冰灵智译(IceSpiritAI_Translate)

### 1.1 当前基线(已读 `app/build.gradle.kts` 摘要)

| 项 | 值 |
|---|---|
| AGP | **8.7.3**(2025 中) |
| Gradle | 8.x 系列(未精确读版本号) |
| Kotlin | 未精确读版本号 |
| NDK | **26.1.10909125**(r26b) |
| JDK | 17 |
| ABI | arm64-v8a only |
| Maven 镜像 | Aliyun 主 + Tencent / Huawei 备 |
| `applicationVariants` | **使用**(`outputs.all { apkOutput.setOutputFileName(...) }` + `applicationVariants.all { ... }` 模式) |
| 自定义 Gradle 任务 | `prepareModelProfileAssets` / `filterPackagedModelAssets`(modelProfile 资源门控) |
| 测试 | `testOptions.unitTests.isReturnDefaultValues = true` 已设 |
| 签名 | `signingConfigs.release` 有 fail-closed `GradleException` 模式 |
| Git 版本号 | `gitVersionCode` / `gitVersionName` / `gitPreviousReleaseVersionCode` 自动生成 |

### 1.2 升 AGP 9.x 的影响范围

| 受影响点 | 工作量评估 |
|---|---|
| `applicationVariants { outputs.all { apkOutput.setOutputFileName(...) } }` | **大**:要迁移到 `androidComponents.onVariants { artifacts.use(...).wiredWithFiles(...).toTransform(SingleArtifact.APK) { setVersion("icespiritai-translate-v\${versionName}.apk") } }`。Artifact API 比 `apkOutput` 复杂得多 |
| `applicationVariants.all { ... }` 内部的自定义资源门控(`prepareModelProfileAssets` / `filterPackagedModelAssets`) | **大**:逻辑必须重写到 AGP 9 的 variants 体系(可能要走 `onVariants { ... }` + `artifacts` API) |
| NDK | **小**:26.1 → 28.2,sherpa-onnx 重新编译应无问题 |
| Gradle Wrapper | **小**:版本号调整 |
| Kotlin | **小**:`kotlin-android` 插件不再需要手动 apply |
| R8 规则 | **小**:补全 keep 规则签名 |
| 调试时间 | **大**:整套迁移应作为独立 PR / Spec,不应混入 Vision Phase 1 工作 |

### 1.3 建议

- **不在 Vision Phase 1 期间同步升 Translate**:迁移工作量足够开一个独立 Spec
- Translate 应另起一份 spec:`docs/superpowers/specs/2026-XX-XX-translate-agp9-migration-design.md`
- 短期(2026 H2):Translate 留在 AGP 8.x 末班车(8.13) + Gradle 8.14.5 + Kotlin 2.4.10
- 中期(2027 H1 前):Translate 必须迁移到 AGP 9(否则 Gradle 9.6+ 起来后无法 build)
- 现有 `applicationVariants` 资产是 Translate 独有 — 不要在 Vision 重写这套逻辑,以免分散精力

### 1.4 可借鉴资产

- Translate 已有 `signingConfigs.release` fail-closed 模式 → Vision 可直接复用思路
- Translate 已有 git-based 版本号生成 → Vision 后续版本管理可借鉴
- Translate 的 modelProfile 资源门控 → Vision Phase 1 中**OCR 模型**与**规则 JSON**两类资产可用同样模式门控(随 modelProfile 切换是否打包)

---

## 2. 冰灵慧语(IceSpiritAI_Chat)

### 2.1 信息缺口

- 当前未读取 Chat 的 `app/build.gradle.kts` / `libs.versions.toml`
- 推断:Chat 大概率与 Vision / Translate 同代 baseline,但具体未知

### 2.2 建议

- Phase 1 之前不必单独为 Chat 做 baseline 调研;若后续 Chat 也要升级,套用 `build-stack-2026-08.md` 决策矩阵即可
- 若 Chat 仍用 Kotlin 1.9.x 时代 baseline,迁移到 Kotlin 2.4 的破坏面 = K1 移除 → 必须逐步把 `kotlin-android` 注解 / 反射 / KAPT 处理器迁到 KSP
- Chat 的 LLM 端侧推理(若用 llama.cpp / GGUF)对 NDK 版本敏感 — 升 NDK 28.2 时要验证 gguf-jni 的 ABI 兼容

---

## 3. 三项目同步策略

| 时点 | 行动 |
|---|---|
| **2026 H2** | **冰灵锐目**先迁前瞻路径(Phase 1 baseline 即按 AGP 9.3 / Gradle 9.7 / Kotlin 2.4.10 / compileSdk **37** / targetSdk **37**);**冰灵智译**留在 AGP 8.x 末班车;**冰灵慧语**视情况 |
| **2027 H1** | **冰灵智译**起独立迁移 spec → AGP 9.x(以 Vision 经验为参考) |
| **2027 H2** | **冰灵慧语**起迁移 spec(若需要) |

### 3.1 为什么不让三项目同步跳

- Translate 的 `applicationVariants` 是 1500+ 行 build 脚本里的核心模式,**改动一处 = 全套重测**
- Vision 没有这种包袱,先冲最简版本能验证 AGP 9 / Kotlin 2.4 的可行性
- 经验复用到 Translate 时,Vision 的 build 脚本可作为模板(避免重复踩坑)
- Chat 信息不全,无法做精确评估

### 3.2 共享资产沉淀

跨项目可复用的资产建议放进 `IceSpiritAI_Vision/build-templates/`(或类似):
- `signing-config.gradle.kts`(release fail-closed 模板)
- `version-from-git.gradle.kts`(自动 versionCode/versionName)
- `model-profile-assets.gradle.kts`(modelProfile 资源门控)
- `baseline-checklist.md`(升 baseline 前的检查项)

Translate 可在迁移时引用,避免重新设计。

---

## 4. 行动清单(2026-08-13 注册)

| # | 项目 | 行动 | 期望时点 |
|---|---|---|---|
| 1 | 冰灵锐目 | Phase 1 baseline = 前瞻路径 | 本 Spec 落地时 |
| 2 | 冰灵锐目 | ~~验证 ONNX Runtime 1.29.0~~ — **CLOSED 2026-08-13**:实际切到 **ONNX Runtime 1.21.1**(PaddleOCR v3.7.0 SDK 锁定版本),目标 1.29.0 无对应 Paddle 二进制 → 关闭 | — |
| 3 | 冰灵锐目 | ~~验证 RapidOCR Android artifact~~ — **CLOSED 2026-08-13**:OCR 路线从 RapidOCR 改为 **PaddleOCR v3.7.0 官方 SDK**(走 ONNX Runtime + OpenCV),RapidOCR 评估作废 → 关闭 | — |
| 4 | 冰灵锐目 | 沉淀共享 build 模板到 `build-templates/` | Phase 1 实施末 |
| 5 | 冰灵智译 | 留在 AGP 8.13 + Gradle 8.14.5 + Kotlin 2.4.10 + NDK 27.x | 维持 |
| 6 | 冰灵智译 | 独立 AGP 9 迁移 spec(以 Vision 经验为模板) | 2027 H1 |
| 7 | 冰灵慧语 | 补 baseline 调研 | 视 Vision Phase 1 进度 |

---

## 5. 决策登记

| 日期 | 决策 | 依据 |
|---|---|---|
| 2026-08-13 | 三项目 baseline 不同步 | 避免 Translate 的 `applicationVariants` 大改动卷入 Vision Phase 1 |
| 2026-08-13 | 共享 build 模板沉淀到 Vision 仓库 | Vision 是 2026-08 最新 baseline 的首个落点,经验最干净 |
| 待 | 共享 build 模板是否独立建仓(如 `IceSpiritAI_Templates`) | 视三项目体量与同步频率决定 |