# 冰灵锐目 v0.1.13 项目深度审计报告

**审计日期**: 2026-08-20
**项目状态**: 已发版 v0.1.13(广告招牌规则 v5 / PP-OCRv6_small / PaddleOCR v3.7.0)
**审计范围**: 4 维度 × 深度只读扫描 + 关键 P0 现场复核
**审计输入**: 109 个项目内 .kt(剔除 vendored `tools/paddleocr/`)+ 全部 `.kts`/`.xml`/`.json`/`.gitignore` + git history + buildSrc

---

## 总览

| 维度 | subagent 报告 | 去重合并后 | P0 | P1 | P2 | P3 |
|------|---------------|------------|----|----|----|----|
| 架构 smell | 15 | 15 | 0 | 4 | 7 | 4 |
| 发版合规 | 6 | 5 | 2 | 2 | 1 | 0 |
| 代码质量 | 11 | 11 | 0 | 3 | 4 | 4 |
| 测试覆盖 | 25 | 25 | 3 | 4 | 9 | 9 |
| **去重合并** | **57** | **~52** | **5** | **~13** | **~20** | **~14** |

P0 关键路径已 5 项现场复核(ApkSignatureVerifier 文件缺失 / HTTP 明文 / cleartextTrafficPermitted=true / UpdateRepository.kt 无 META-INF 引用 / build.gradle.kts L76 URL)全部坐实。

---

## P0 — 必须修(5 条)

### P0-1. 运行时 cert-pin 校验类不存在

**维度**: release-hygiene + arch-smell
**位置**: `app/src/main/java/com/icespiritai/offline/updater/`(整个目录无 `ApkSignatureVerifier.kt`)+ `UpdateRepository.kt:166` + `UpdateSection.kt:install()`
**摘要**: build.gradle.kts L407 注释承诺 `ApkSignatureVerifier.readFirstSignerCert` 与之同步,但被引用方从未实现。下载完 APK 后 `requestInstall` 直接 `Intent.ACTION_VIEW`,任意 APK 都会被系统安装。
**验证**: Glob 仅3 个 .kt(UpdateRepository/AppVersionInfo/UpdateState),UpdateRepository.kt 199 行 grep `META-INF/CERT` 零命中。
**修复要点**: 新增 `ApkSignatureVerifier.kt`(用 JarFile + CertificateFactory 读 META-INF/CERT.{RSA,DSA,EC} → SHA-256),在 `_state = ReadyToInstall(file)` 之前调用,与 `BuildConfig.PINNED_RELEASE_CERT_SHA256` 比对。BuildConfig 必须新增 `buildConfigField` 注入 SHA-256。

### P0-2. 更新通道全走明文 HTTP

**维度**: release-hygiene + arch-smell
**位置**: `app/build.gradle.kts:76,447,502` + `app/src/main/res/xml/network_security_config.xml:3`
**摘要**: `UPDATE_JSON_URL`、`giteaBaseUrl`、`apkUrl` 全是 `http://125.211.45.14:3000/...`,`network_security_config.xml` 又显式 `<domain-config cleartextTrafficPermitted="true">`。与 P0-1 叠加 = 完整 MITM 投 APK 路径。
**验证**: Read network_security_config.xml: `<domain-config cleartextTrafficPermitted="true">`。
**修复要点**: 短期切 HTTPS(Gitea 前置 TLS 自签 + network_security_config 加 `<pin-set>`);或保持 HTTP 但必须 P0-1 落地 + cleartext 白名单收缩到 IP 级。

### P0-3. ExportAction.share 整条 share-intent 流无任何测试

**维度**: test-coverage
**位置**: `app/src/main/java/com/icespiritai/offline/export/ExportAction.kt`
**摘要**: unitTest + androidTest 均无 ExportActionTest。证据导出是用户主动操作,任一环节坏(FileProvider / cache 写入 / Intent.createChooser / MIME)用户无法导出。
**修复要点**: 在 `androidTest/` 加 `ExportActionShareTest`:ActivityScenario 启动 + Robolectric 模拟 Context + 验证 `cacheDir/evidence/*.zip` 落地 + `Intent.ACTION_SEND` + MIME=`application/zip` + `FLAG_GRANT_READ_URI_PERMISSION`。

### P0-4. AdSignageRuleLoader.load() 失败分支无测试

**维度**: test-coverage
**位置**: `app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleLoader.kt`
**摘要**: AssetRuleLoaderTest 仅做合成 JSON 解析,从未构造 loader 实例触发 Context.assets.open 失败 / RuleLoadFailed 包装路径。这是 v0.1.x 历史上 ErrorCode.RULES_FAILED 的真实来源。
**修复要点**: 加 `AdSignageRuleLoaderTest`:Robolectric 喂正常 assets 路径 + 不存在的 path + 损坏的 JSON,断言 load() 抛 `RuleLoadFailed` 且 message 含 `assets/`。

### P0-5. FoodLabelRuleLoader 与 AdSignageRuleLoader 对称,但无 context 级测试

**维度**: test-coverage
**位置**: `app/src/main/java/com/icespiritai/offline/rules/FoodLabelRuleLoader.kt`
**修复要点**: 对称加 `FoodLabelRuleLoaderTest`,覆盖正常 assets / 缺文件 / 损坏 JSON 三分支。

---

## P1 — 高优先级(~13 条)

### release-hygiene(2)

#### P1-1. vision-latest.json schema 缺 signerCertSha256 字段
**位置**: `app/src/main/java/com/icespiritai/offline/updater/AppVersionInfo.kt:6-14`
即便补上 ApkSignatureVerifier 也无值可比对(`apkSha256` 校验文件级完整性,无法防 APK 被替换;签证书校验才是关键)。

#### P1-2. build.gradle.kts 注释承诺同步 ApkSignatureVerifier,但被引用方不存在
**位置**: `app/build.gradle.kts:379,407`
后续移植 sync 时会困惑。

### arch-smell(4)

#### P1-3. FakeOcrEngine.kt 位于 src/main/,违反对称设计
**位置**: `app/src/main/java/com/icespiritai/offline/ocr/FakeOcrEngine.kt`
与 `PaddleOcrEngine` 在 `src/ice_ocr_rules/` 的对称设计相违。ice_ocr_rules APK 始终编入不会被用的 FakeOcrEngine。修复:把 FakeOcrEngine.kt 移到 `src/shell/java/com/icespiritai/offline/ocr/`(BitmapLoader 留 main/共享)。

#### P1-4. ServiceLoader 注册双源冗余
**位置**: `app/src/shell/resources/META-INF/services/...` + `app/src/ice_ocr_rules/resources/META-INF/services/...` + `app/prepare-ocr-rules.gradle.kts:177-220`
同一 FQN 既被 sourceSet 写入又被 `buildProfileServicesJar` 写入,今日一致但未来分歧则 `firstOrNull` 行为不定。修复:删除 sourceSet 的 META-INF/services,以 JAR 为唯一权威。

#### P1-5. cert-pin + APK 内容 SHA-256 双门防御未落地
**位置**: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:166-198`
当前仅 apkSha256(可被 MITM 重算),签证书校验空缺。修复:接 P0-1 + P1-1 的 ApkSignatureVerifier 后,在 `_state = ReadyToInstall(file)` 之前双重校验。

#### P1-6. ThemeSettingsSource 接口与 SettingsRepository 实现同文件,接口抽象价值未独立呈现
**位置**: `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt:15`
**注**: 此条已升级到 P1(原报告列在 P2),理由:SettingsViewModel 显式构造 `SettingsViewModel(source: ThemeSettingsSource)` 暗示接口是有意设计的 seam。

### code-quality(3)

#### P1-7. LoadingOverlay composable 整段是死代码
**位置**: `app/src/main/java/com/icespiritai/offline/ui/home/LoadingOverlay.kt:17-37`
HomeScreen 用内联 `Text(stringResource(loadingLabelRes(...)))` 实现,从未 import 这个 composable。Grep `LoadingOverlay` 仅命中自身文件。同文件 `loadingLabelRes` + `AnalysisStateLoadingStage` 仍被 HomeScreen.kt 用,保留。

#### P1-8. 陈旧 TODO 注释
**位置**: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:108`
注释 `// requestInstall lands in Task 6.` — `requestInstall` 已在 L196 实现。

#### P1-9. 未使用的 import
**位置**: `app/src/main/java/com/icespiritai/offline/ui/settings/UpdateSection.kt:3`
`import android.content.Intent`。

### test-coverage(4)

#### P1-10. 118 条 ad_signage 规则中 24 条无 matcher 单测用例
**位置**: `app/src/main/assets/rules/ad_signage_rules.json`
未覆盖: `ad_signage_art10_minor`、`art16_med_abs/health`、`art22_tob_alc`、`art23_alcohol_relief`、`art24_edu_guar`、`art25_fin_prm`、`art26_re_prm`、`art9_abs_pct/edu_abs`、`edu_art24_recommendation`、`fin_art25_unlawful`、`med_art7_compare/technicality`、`medical_art6_producer/registerno`、`outdoor_art10_misleading`、`outdoor_city_art32_heritage/municipal`、`re_art26_price_violation`、`signage_art30_self_publish`、`signage_otc_label`、`veterinary_art4_endorsement`、`veterinary_art5_deprecate`。

#### P1-11. 66 条 food_label 规则中 24 条无单测用例
**位置**: `app/src/main/assets/rules/food_label_rules.json`
未覆盖: `food_art16_animal_origin_name/flavor_imitation_name`、`art19_weigh_mark`、`art21_standard_code`、`art22_license_no`、`art23_warning_mark`、`art24_qrcode_mismatch`、`art28_health_name_format`、`art35_platform_responsibility`、`art36_health_disclaimer/special_food_zone`、`art39_jargon/net_weight_format/nutrition_format/other_minor/traditional/typo`、`art7_absolute_exaggerate/disease_treatment`、`fsmp_register_required`、`gb7718_disease_claim/truthfulness_extreme`、`health_claim_unapproved`、`infant_formula_milk_register`。

#### P1-12. ImageBytesProvider.from() ContentResolver 桥接无单测
**位置**: `app/src/main/java/com/icespiritai/offline/export/ImageBytesProvider.kt`
`IllegalArgumentException('Cannot open URI')` 路径也未测。

#### P1-13. ExportAction 失败回退 Toast('导出失败')路径无测试
**位置**: `app/src/main/java/com/icespiritai/offline/export/ExportAction.kt`
`ImageBytesProvider` 抛 `IllegalArgumentException` + cache 写文件抛 `IOException` 两个回退分支都无单测。

---

## P2 — 可清理(~20 条)

### release-hygiene(1)

#### P2-1. .gitignore 缺通配符覆盖
**位置**: `.gitignore:96-101`
当前只覆盖具体名(`gradle.token.properties`、`app/libs/ppocr-sdk.aar` 等),新增 `foo.keystore` 或 `bar.token.properties` 不会被自动忽略。修复:加通用模式 `*.aar`、`*.keystore`、`*.token.properties`、`gradle.properties.local`、`release.keystore`。

#### P2-2. Commit trailer 实质违反 CLAUDE.md 第 141 行
**位置**: 全部历史 commit
**证据**: `git log -20 --pretty=format:"%B" | grep "Co-Authored-By" | sort -u` → `Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>`
CLAUDE.md 明确"绝不要加 `Co-Authored-By: Claude` trailer"。这条字面虽非 "Claude" 但邮箱是 anthropic,精神上仍是 AI agent 留下的 trailer。修复:在 `.claude/settings.local.json` 关闭 Claude auto-trailer(对未来 commit 生效),历史 commit 不改(避免 force push 风险)。

### arch-smell(7)

#### P2-3. BuildConfig.MODEL_PROFILE 字段无人读取
**位置**: `app/build.gradle.kts:73`
仅在 `IceSpiritVisionViewModel.kt:32` 的 doc 注释里被引用。gating 实际走 ServiceLoader。修复:删 buildConfigField 或在 OcrEngineFactoryLocator.create 里读后断言(防御性)。

#### P2-4. 测试 resources 的 META-INF/services 文件冗余
**位置**: `app/src/test/resources/META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`
default-profile test classpath 已经从 src/shell/resources 继承。修复:删除。

#### P2-5. GlobalScope.launch(Dispatchers.IO) 反模式
**位置**: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:98,161`
两处。Activity销毁后不能取消。修复:接受 CoroutineScope 注入(ApplicationScopeProvider)或 lifecycleScope。

#### P2-6. UpdateRepository 是 process-wide 单例 + MutableStateFlow
**位置**: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:33`
与单 Activity 架构勉强兼容,多 Activity / 多窗口会串味。短期可不动。

#### P2-7. ACCESS_NETWORK_STATE 权限声明但代码无直接使用
**位置**: `app/src/main/AndroidManifest.xml:10`
唯一消费方是 Coil 2.7 NetworkObserver。建议加注释说明。

#### P2-8. ImageAnalyzerRepository.analyze 第二个阶段硬编码 Dispatchers.IO
**位置**: `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt:86`
Aho-Corasick 扫描本身 CPU bound,IO 切换无收益。修复:改 Dispatchers.Default 或保持原 dispatcher。

### code-quality(4)

#### P2-9. 13 个孤儿 string 资源
**位置**: `app/src/main/res/values/strings.xml`
`action_analyze` / `action_exit` / `action_export_report` / `action_report_issue` / `action_reshoot` / `action_grant_permission` / `error_permission_denied` / `hit_card_severity` / `settings_about` / `status_no_violation` / `status_violations_count` / `status_idle` / `viewer_gesture_hint`。

#### P2-10. UpdateCheckResult.Failed.reasonTag 字段定义但从未被读取
**位置**: `app/src/main/java/com/icespiritai/offline/updater/UpdateState.kt:19`

#### P2-11. 6 个 Color token 跨项目保留,生产代码 0 处使用
**位置**: `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`
`DarkIceChatOnBgSubtle/Disabled/Placeholder` 与 `LightIceChatOnBgSubtle/Disabled/Placeholder`。Color.kt KDoc 自陈跨项目保留(冰灵慧语同款)。清理前先 cross-project 确认。

#### P2-12. BitmapLoader.downsampledBitmap() 公开便利方法仅被测试使用
**位置**: `app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt:29`
生产代码走 `downsampledBitmapWithScale()`。

#### P2-13. AnalysisStateLoadingStage 是 UI 层冗余枚举
**位置**: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt:300`
与 `domain.AnalysisState.Loading.Stage` 用例 1:1,中间叠一层 `toLoadingStage()` 映射。

### test-coverage(9)

#### P2-14. NavHost 4 路由注册 + Viewer 与 Home 共享 ViewModel 契约无测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/nav/IceSpiritNavHost.kt`
v0.1.11 关键集成。

#### P2-15. UpdateSection 7 个 UpdateState 分支渲染无 UI 测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/settings/UpdateSection.kt`

#### P2-16. SettingsScreen 整屏(AppearanceSection + UpdateSection + 导航 Changelog)无 Compose 测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`

#### P2-17. ChangelogScreen + onBack 回调无测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/settings/ChangelogScreen.kt`

#### P2-18. 主题模式切换 UI 无测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/settings/AppearanceSection.kt`

#### P2-19. HomeScreen 7 个子组件均无独立 Compose 测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/home/{CaptureBar,CaptureButton,LoadingOverlay,HighlightOverlay,HitCard,StatusBanner,HomeTopBar}.kt`
HighlightOverlay 的 bounding box 坐标映射尤关键。

#### P2-20. ViewerEmpty + ViewerImage 两个 Viewer 子组件无独立测试
**位置**: `app/src/main/java/com/icespiritai/offline/ui/viewer/{ViewerEmpty,ViewerImage}.kt`

#### P2-21. visibleTabs = listOf(RuleTab.AdSignage) 单焦点策略无单测守护
**位置**: `app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt`
CLAUDE.md 强调产品方向。

#### P2-22. ImageAnalyzerRepository 内置的 toLoadingStage / 路径分派无单测
**位置**: `app/src/main/java/com/icespiritai/offline/analysis/ImageAnalyzerRepository.kt`

---

## P3 — 可选(~14 条)

### arch-smell(4)

#### P3-1. POST_NOTIFICATIONS 权限声明但当前实现未发任何 NotificationCompat
**位置**: `app/src/main/AndroidManifest.xml:12`

#### P3-2. EvidencePackageBuilder.build 默认 appVersion 硬编码 "0.1.0"
**位置**: `app/src/main/java/com/icespiritai/offline/export/EvidencePackageBuilder.kt:23`
与 BuildConfig.VERSION_NAME 不一致。证据导出会写死 0.1.0。

#### P3-3. packaging.jniLibs.useLegacyPackaging=true 无条件开启
**位置**: `app/build.gradle.kts:264`
对 shell profile 是 no-op 但语义未对齐"仅 ice_ocr_rules 需要"。

#### P3-4. proguard-rules.pro 几乎为空
**位置**: `app/proguard-rules.pro`
若日后启用 R8 / `isMinifyEnabled=true`,ServiceLoader 注册的工厂类会被 shrink/optimize 误删。

### code-quality(4)

#### P3-5. HomeScreenBare 顶层 public composable 仅被 HomeScreenTest / HomeScreenScreenshotTest 引用
**位置**: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt:305`

#### P3-6. buildInstallIntent 公开 fun,但只被同文件 requestInstall 调用
**位置**: `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:185`

#### P3-7. HomeScreen 双 when 分支结构稍乱
**位置**: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt:198`
同 state 被两个 when 独立 switch。

### test-coverage(9)

#### P3-8. AdSignageMentorFiveImageRegressionTest 中 `assertTrue(true)` 是空测试
**位置**: `app/src/test/java/com/icespiritai/offline/rules/AdSignageMentorFiveImageRegressionTest.kt:230`

#### P3-9. FakeOcrEngineTest.release() 无断言
**位置**: `app/src/test/java/com/icespiritai/offline/ocr/FakeOcrEngineTest.kt:46`

#### P3-10. AssetRuleLoaderTest.invalidJson_throwsSerializationError 末尾 assertTrue(true) 占位
**位置**: `app/src/test/java/com/icespiritai/offline/rules/AssetRuleLoaderTest.kt:126`

#### P3-11. TextNormalizer 仅 3 个测试,未覆盖 Latin 大小写化与 NFKC 边界
**位置**: `app/src/test/java/com/icespiritai/offline/domain/TextNormalizerTest.kt`

#### P3-12. Activity onCreate 内 startup checkForUpdatesAsync fire-and-forget 无断言测试
**位置**: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`

#### P3-13. ICE SPIRIT 双击接通 Viewer 流无 androidTest
**位置**: `app/src/androidTest/java/com/icespiritai/offline/IceSpiritVisionActivityTest.kt`

---

## Hygiene 扫描补充(2026-08-20)

| 项 | 状态 | 证据 |
|---|---|---|
| `signingConfigs.release.enableV1Signing` | ✅ | `app/build.gradle.kts:165` |
| git config user.name | ✅ | `AlexMultiAgent` |
| git config user.email | ✅ | `zhangven@gmail.com` |
| gradle.properties 敏感信息 | ✅ 干净 | 只有 JVM args + Kotlin config |
| strings.xml 敏感字符串 | ✅ 干净 | grep 无 token/password/secret |
| versionCode / versionName 一致 | ✅ | `13` / `0.1.13` 与 git HEAD 一致 |
| .gitignore 通配符覆盖 | ⚠️ | P2-1:仅具体名,无 `*.aar` / `*.keystore` / `*.token.properties` |
| Commit trailer 实质合规 | ⚠️ | P2-2:全部历史 commit 带 `Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>`,精神违反 CLAUDE.md 第 141 行 |

---

## 亮点(4 维度都确认)

- **0 TODO/FIXME/XXX/HACK 标记**(grep全文 .kt/.kts/.xml 全空)
- **0 god file**(最大 HomeScreen.kt 316 行,远低于 500阈值)
- **命名零变体**:`Theme.IceSpiritOffline` 与 `com.icespiritai.{offline,vision}` 全字匹配
- **KDoc 与 v0.1.13 实际实现完全对齐**(PP-OCRv6_small / PaddleOCRConfig / floor-based sampleSize)
- **signingConfigs.release v9.5 fail-closed 机制完整**(4 个环境变量 + Provider.isPresent 检测 + 非 release 任务 graceful)
- **sourceSet 隔离正确**:`app/src/main/` 反向 grep `onnxruntime|opencv|paddle` 零命中;`src/shell/` 与 `src/ice_ocr_rules/` 互不引用

---

## 修复优先级建议

### 第一波(防 P0 安全洞 + 单测底线,~3-5 天)
- **P0-1 + P1-1 + P1-2 + P1-5**:`ApkSignatureVerifier.kt` 全套实现(schema 扩字段、build.gradle.kts 写 JSON、client-side 校验、ReadyToInstall 之前 double-gate)
- **P0-2**:更新通道切 HTTPS(Gitea 前置 TLS 自签 + network_security_config 加 `<pin-set>`)或保持 HTTP 但配 cert-pin + cleartext 白名单收缩
- **P0-3**:`ExportActionShareTest` androidTest
- **P0-4 + P0-5**:`AdSignageRuleLoaderTest` + `FoodLabelRuleLoaderTest`(Robolectric)

### 第二波(测试覆盖 P1,~2-3 天)
- **P1-10 + P1-11**:48 条规则单测补全(按 v2/v3/v4/v5 fanfou 模式)
- **P1-12 + P1-13**:`ImageBytesProviderTest` + `ExportActionTest` Robolectric

### 第三波(代码清理 P1 + P2,~1-2 天)
- **P1-3**:FakeOcrEngine.kt 移至 `src/shell/`
- **P1-4**:ServiceLoader 双源去除 sourceSet 一侧
- **P1-7**:`LoadingOverlay` 删除(或被 HomeScreen 调用)
- **P1-8**:`UpdateRepository` 死 TODO 注释删除
- **P1-9**:UpdateSection.kt 未用 import 清理
- **P2-9 + P2-10**:孤儿 string + reasonTag 字段清理

### 第四波(架构 debt,~可选)
- **P2-3 ~ P2-8**:GlobalScope / ThemeSettingsSource 拆分 / ImageAnalyzerRepository dispatcher 等
- **P3-4**:proguard R8 keep 规则预防性补
- **P3-2**:`EvidencePackageBuilder.appVersion` 默认值硬编码修复(易修可顺手)

---

## Hygiene 修项(本次会话内可立刻处理)

下列为"发版前 hygiene"小件,**无副作用、可立刻 commit**:

| 项 | 文件 | 动作 | 风险 |
|---|---|---|---|
| P2-1 | `.gitignore` | 加 `*.aar`/`*.keystore`/`*.token.properties`/`gradle.properties.local`/`release.keystore` | 0 |
| P1-9 | `app/src/main/java/com/icespiritai/offline/ui/settings/UpdateSection.kt` | 删未用 `import android.content.Intent` | 0 |
| P1-8 | `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:108` | 删死 TODO 注释 | 0 |
| P2-2(部分) | `.claude/settings.local.json` | 关闭 Claude auto-trailer(未来 commit 不再带 Co-Authored-By) | 0 |
| P3-2 | `app/src/main/java/com/icespiritai/offline/export/EvidencePackageBuilder.kt:23` | 删默认值 `"0.1.0"`,调用方显式传 `BuildConfig.VERSION_NAME` | 低(改函数签名) |
| P3-6 | `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt:185` | `buildInstallIntent` 改 `private` | 0 |

下列为 hygiene 但属大件,**不在本次单会话范围**:

- **P0-1 + P1-1 + P1-2 + P1-5**:ApkSignatureVerifier 全套实现
- **P0-2**:更新通道切 HTTPS / 加 cert-pin
- **P2-2 历史 commit 改 trailer**:需 force push,通常不动