# v0.1.13 审计报告 4-Wave 修复 完成记录

> **审计报告**: [`2026-08-20-icevision-v0.1.13-audit.md`](2026-08-20-icevision-v0.1.13-audit.md)
> **修复计划**: [`../superpowers/plans/2026-08-20-v0.1.13-audit-remediation.md`](../superpowers/plans/2026-08-20-v0.1.13-audit-remediation.md)
> **完成时间**: 2026-08-21

## 总览

`./gradlew.bat testDebugUnitTest -PmodelProfile=shell` 全绿,**473 个 test** 全部通过(本批修复后实测)。

`git log -1 --format='%B' | grep -i 'Co-Authored-By'` 在每个 commit 上均为空(commit 作者均为 `AlexMultiAgent`,无 AI agent trailer)。

## Wave 1: P0 安全洞

发版前置。cert-pin + HTTPS 完整链路上线。具体 commits 已在 v0.1.13 release 流程前 ship,本记录不再展开。

## Wave 2: P0/P1/P2 测试覆盖

### P0-3/4/5: ImageBytesProvider + ExportAction 单测 + androidTest

- `app/src/test/java/com/icespiritai/offline/export/ImageBytesProviderTest.kt` — Factory + 不可读 URI 路径
- `app/src/test/java/com/icespiritai/offline/export/ExportActionTest.kt` — Happy / 异常 / 写失败
- `app/src/androidTest/java/com/icespiritai/offline/export/ExportActionShareTest.kt` — 真机 ACTION_SEND chooser 验证

`espresso-intents` 已加为 `androidTestImplementation`。

### P1-3/4: 配置文件测试

- `AdSignageRuleLoaderTest` / `FoodLabelRuleLoaderTest` — 两条 loader 的 happy + 错误路径
- `48 条规则单测` — ad_signage / food_label 各 24 条新增规则

### P2-14..22: Compose UI 测试

共 11 个 Compose UI test 文件,统一采用 `RobolectricTestRunner + @Config(sdk = [33])` 模式(targetSdk=37 > Robolectric 4.13 maxSdk=34):

| Plan ID | 文件 | 覆盖范围 |
|---|---|---|
| P2-14 | `IceSpiritNavHostTest.kt` | Routes 常量契约 |
| P2-15 | `UpdateSectionTest.kt` | "更新" section title + Idle "检查更新" 按钮 |
| P2-16 | `SettingsScreenTest.kt` | 设置整屏 7 项 |
| P2-17 | `ChangelogScreenTest.kt` | 更新日志渲染 + back |
| P2-18 | `AppearanceSectionTest.kt` | 3 主题选项 + selected + click |
| P2-19 | 7 个 home 子组件 | RuleTabBar / CaptureButton / LoadingOverlay / HighlightOverlay / CaptureBar / StatusBanner / HitCard / HomeTopBar |
| P2-20 | `ViewerEmptyAndImageTest.kt` | Empty + load-error 两个分支 |
| P2-21 | (在 RuleTabBarTest 中) | visibleTabs 单焦点策略 |
| P2-22 | (deferred) | ImageAnalyzerRepository toLoadingStage 路径分派 |

## Wave 3: 代码清理(8 tasks, 8 commits)

| Task | 改动 |
|---|---|
| P1-7 + P2-13 | 删 `LoadingOverlay` 死 composable + `AnalysisStateLoadingStage` 冗余枚举 |
| P2-9 | 删 13 个孤儿 string 资源(action_analyze / status_idle 等) |
| P2-10 | 删 `UpdateCheckResult.Failed.reasonTag` 字段 |
| P2-12 | 删 `BitmapLoader.downsampledBitmap` 便利方法 |
| P3-5 | `HomeScreenBare` 改 `internal` + `@VisibleForTesting` |
| P3-8/9/10 | 3 处 `assertTrue(true)` 替换为真断言 / 删除无断言测试 |
| P3-1 | 删未用 `POST_NOTIFICATIONS` 权限 |

## Wave 4: 架构 debt(12 tasks, 12 commits)

| Task | 改动 |
|---|---|
| P1-3 | `FakeOcrEngine.kt` 移到 shell sourceSet |
| P1-4 | 删两个 profile 目录下的 `META-INF/services/...` 重复文件(buildProfileServicesJar 是唯一权威) |
| P2-3 | `OcrEngineFactoryLocator` 加 `BuildConfig.MODEL_PROFILE` 防御性断言 |
| P2-4 | 删 `test/resources/META-INF/services/...` 重复 |
| P2-5 | `UpdateRepository.checkForUpdatesAsync` + `downloadApk` 改 caller-owned `CoroutineScope`(SettingsViewModel 传 `viewModelScope`) |
| P1-6 | `ThemeSettingsSource` interface 拆独立文件 |
| P2-6 | KDoc 加 multi-window / multi-Activity 风险说明 |
| P2-7 | AndroidManifest 加 `ACCESS_NETWORK_STATE` 注释(Coil 2.7 NetworkObserver) |
| P2-8 | AC scan `Dispatchers.IO` → `Dispatchers.Default`(CPU-bound) |
| P2-11 | Color.kt KDoc 强化跨项目对齐约束 |
| P3-4 | `proguard-rules.pro` 加 ServiceLoader + kotlinx.serialization keep rules |
| P2-12 | `ThemeSettingsSourceContractTest` — 接口契约 3 项 |

## 未决项 / 已知缺口

1. **P2-22 ImageAnalyzerRepository 路径分派测试**:已 deferred 至 Wave 4 之后,因 toLoadingStage 私有且依赖 `BitmapLoader` 全链路。
2. **ice_ocr_rules profile 编译验证**:本批修复都基于 `shell` profile。`./gradlew.bat assembleIceOcrRulesDebug -PmodelProfile=ice_ocr_rules` 验证需先跑 `bash tools/download-ppocr-models.sh && bash tools/build-ppocr-sdk.sh`(模型 / SDK 不入仓)。
3. **真机 androidTest 完整跑通**:ExportActionShareTest 在华为 nova 6 上验证过 ACTION_SEND chooser;其余 androidTest 占位待 v0.1.14 release 前置 CI 流程跑。
4. **P2-9 13 个孤儿 string 删除后,翻译资源文件(values-en / values-zh-rTW 等)如存在也需要同步清理**:本仓库当前只有 `values/strings.xml` 一份,无需处理。

## 发版前阻塞项

按 plan spec:
- Wave 1 全部完成 ✅(已在 v0.1.13 release 流程前 ship)
- Wave 2 P0-3/4/5 完成 ✅(ImageBytesProvider + ExportAction + androidTest)

发版可进行;Wave 2 剩余 + Wave 3 + Wave 4 是 technical debt,可在 v0.1.14 之后 PR 分批落地(本批已落地完成)。
