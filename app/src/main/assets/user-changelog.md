# 用户更新日志

## v0.1.38 · 2026-08-31

- **规则改进(广告招牌 tab):Phase 2.5 同 ruleId 子串去重**(`AdSignageRuleMatcher`):在原 Phase 2 (ruleId + originalKeyword 维度,折叠 1-char-deletion 变体) 与 Phase 3 (absence rule 维度) 之间插入子串合并阶段 — 同 ruleId 内,若一条 hit 的 `matchedText` 是另一条更长 hit 的子串,**双向**:`case A` 较短候选是已 kept 较长条目的子串 → 丢弃;`case B` 较长候选包含已 kept 较短条目 → 反向删除较短条目,保留较长。LinkedHashMap 插入序非长度序,必须 case B 兜底。覆盖 3 类重叠模式:
  - **关键词子串**(同规则 keywords 列表里有 `增强免疫`(4) + `增强免疫力`(5)两条独立关键词,OCR 仅 `增强免疫力` 一处时原先生成 2 个 hit,现在合并成 1)
  - **变体误中**(`呵护心血管`(5) → 1-char-deletion 变体 `护心血管`(4) substring 匹配另一独立关键词 `保护心血管`(5),原 Phase 2 折叠变体后两条仍共存,Phase 2.5 再按子串丢短)
  - **相邻 claim 短语**(同规则 `控糖`(2) + `稳血糖`(3) + `控糖稳血糖`(5) 共存于一段 OCR,只留最长)
- **跨 ruleId 子串不去重**(守护):不同法源即便词条互含(如 medical 类规则的 `心血管` 与 food_function_claim 的 `保护心血管`)各自保留;**已知 trade-off**:同 ruleId 内即便短 keyword 在文本其它位置独立出现(如 `增强免疫` 独立 + `增强免疫力` 独立)仍按子串去重 → 用户从 ResultPanel OCR 原文即可看到短表述,RuleHit 列表只保留最长。配 6 unit test pin 契约(3 类覆盖模式 + 跨 ruleId 守护 + 同长度互不包含 + 短 keyword 独立出现仍去重)
- **4 条旧测试适配**:同 ruleId 内把"每个独立关键词各记 1 hit"语义换成"子串去重只保留最长",相应放宽断言。涉及 `finance_art25_endorsement_reinforced`(经济学家推荐 ⊂ 首席经济学家推荐)/ `signage_food_disease_target`(糖尿病 ⊂ 糖尿病患者等 4 对)/ `finance_316_art3_internet`(直播带单 ⊂ 快手直播带单)/ `mentorReview_crabMall`(全国第一 同时 ⊂ 销量全国第一 / 连锁门店数量全国第一)。每条加 `Phase 2.5 (2026-08-31)` 注释,详细 dedup 模式见 `AdSignageRuleMatcher.kt` Phase 2.5 KDoc
- **UI 调整(广告招牌 tab)**: 顶部标题 `冰灵⚡锐目` 三段 `headlineSmall` → `titleLarge`(小一号,降低顶部占用);KPI bar(`10 违规 / 0 警告 / 0 信息` 数字 + 标签)vertical padding 12.dp → 6.dp + 数字字号 `headlineMedium` → `headlineSmall`(整体压矮,数字 + 标签紧凑);ResultPanel weight 1f → 1.6f(图文区高度比从 1:1 改为 1:1.6,文字区更高更易读);导出取证包按钮 padding 微调(top 0→8.dp,底 16.dp 保留)从 ResultPanel 滑出。**图片区高度不变**(仍 weight 1f)
- **测试覆盖**: 599 tests / 0 failures / 100% successful(`testDebugUnitTest -PmodelProfile=shell`,569 → 599,新增 6 Phase 2.5 子串去重契约 + 4 条旧测试放宽断言)

## v0.1.37 · 2026-08-29

- **新功能:Tab → 初始页 reset 行为**(CLAUDE.md §Tab → 初始页 spec 落地):识别完成后用户再次点已选中的「广告招牌」tab 直接回到 Idle 初始页(清 pendingUri + state 走回 Idle),无需手动点右下角拍照/相册按钮。3-state 契约:`tab 切换 → 保留 state(预留 FoodLabeling 解锁后);同 tab + Loading → no-op(防误触打断正在跑的 OCR / 规则扫描);同 tab + !Loading → reset 回 Idle`。配 3 unit test pin(`setTab_sameTab_nonLoadingState_resetsToIdle` / `setTab_sameTab_loadingState_isNoOp` / `setTab_tabSwitch_doesNotReset`)— 反射设 `_state=Loading` 验证 Loading 路径不被误清,稳定
- **规则改进(广告招牌 tab)**:
  - **通用化 AC 1 字 OCR 退化兜底**(`AdSignageRuleMatcher` auto-decomposition):length≥5 keyword 自动注册所有 1-char-deletion variants 到 AC trie,容忍 PP-OCRv6_small 在密集文字上 1 字漏检(`#48` OCR 实际 `高压血糖血脂降下去` → 8-char variant 命中 keyword `血压血糖血脂降下去` 9-char 原词)。L≥5 阈值 cross-check:`抗病毒`(3 chars)不会被分解 → `#13`/`#26` 豌豆/无筋豆种子「抗病高产」plant-disease 描述不会误命中 `disease_prevention`(L=3 会引发回归,实证已规避)
  - `art28b_fake_data` keywords 扩 `不二之选`(闭环 `#61` GT 第 3 条规则覆盖)
  - `food_function_claim` 扩 `蜂胶` / `蜂王浆` / `灵芝孢子` — 覆盖 `#19` 配料表
  - `disease_prevention` 扩 `降血糖` / `降三高` / `降血压` / `降血脂` — 覆盖 `#48` GT 第 2 条
- **代码同步(食品标识 tab)**: `FoodLabelRuleMatcher` 同步 `AdSignageRuleMatcher` 的 AC auto-decomposition + 3-phase scan(longest-match)模式。FoodLabelRule 当前没有 `sourceMarkers` 字段,absence rule 暂不落地(若未来有需求,先扩 `FoodLabelRule` 数据类)
- **Bug 修复**: `variantOrigins` pre-pass bug — 当 keyword A 的 1-char-deletion variant 恰好等于另一条规则独立注册的 keyword B 时(如 `反式脂肪` 是 `反式脂肪酸` 的 variant 又是独立 keyword),原本会把 B 错标为变体导致 Phase 2 dedup 把两条独立命中合并成一个。修复:init 加 `allNormalizedKeywords` pre-pass,生成 variant 时多 guard `variant !in allNormalizedKeywords`。AdSignage 同源同步修复(现有 AdSignage ruleset 未踩坑,提前规避未来新增规则时引入)
- **性能 regression guard**: 新增 `MatcherPerformanceRegressionTest`(4 tests,直接读 `src/main/assets/rules/*.json` 绕过 shell profile 空 assets),pin matcher construction < 800 ms / scan < 30 ms(实测 ~30-80 ms / ~0.5-2 ms,10× 余量,只 catch 算法退化,不被 CI 性能抖动 false positive)
- **E2E 验证(nova 6 / arm64-v8a / ice_ocr_rules)**: `FULL 27 → 46(+19)`,`MISS 6 → 1`(剩 `#19` OCR 端漏识配料表 + GT keyword 路径问题,扩词不能解,需 audit 决定);`warm_avg_ms 2198 → 2043`(−7%)
- **零碎清理**:
  - `docs/knowledge/ocr-long-image-slicing-evaluation.md`: OCR 长图 / 高密度小字 评估 — 4 方案对比(block slicing / two-pass zoom / maxEdge 4096 / 不动),**结论不动**,等 `ice_vision` profile / PP-OCRv7 multi-scale / VLM 路线自然覆盖。`PaddleOcrEngine.kt` 不修改,现有 det 参数是经过 2026-08-29 净负回滚验证的最优帕累托
  - memory `followup-ad-signage-cross-cite` 标 closed(regulation 字段 2026-08-21 后已被收窄为仅《广告法》§17+§58,lawText 仍提《食品标识管理规定》是教育性背景,有意保留)
- **测试覆盖**: 158 tests / 0 failures / 100% successful(`testDebugUnitTest -PmodelProfile=shell`,152 → 158,新增 6:3 tab + 2 food variant + 1 perf empty)

## v0.1.36 · 2026-08-28

- **修复 APK 下载进度条卡在 totalBytes=0**(audit finding #2): `ApkDownloader` 新增 `onMetadata: (Long) -> Unit = {}` 回调,Content-Length 已知(非 -1)时一次性触发;`UpdateDownloadService.handleDownload` 用 mutable `liveRecord = record`,`onMetadata` 写入 StateFlow + DataStore,`onSuccess` 用 `liveRecord.copy(totalBytes = liveRecord.totalBytes)` —— 之前 `totalBytes` 只在 recreate 时从持久化的 `AppVersionInfo.totalBytes` 读,如果下载过程中网络层提前传了 Content-Length 也不会更新,进度条一直 0%。配 2 个 unit test pin(`onMetadata_fires_once_with_total_bytes_before_body` / `onMetadata_skipped_when_content_length_unknown`)
- **修复 UpdateAvailable 触发 POST_NOTIFICATIONS 权限请求**(audit finding #3): `UpdateSection.kt` API33+ 在 UpdateAvailable 卡片首次出现时调 `rememberLauncherForActivityResult(RequestPermission())`,dedup via `promptedForNotif` state(避免每次重新 compose 都弹),rationale string 复用 `update_notification_rationale`。**死代码清理**: `currentVersionString(versionCode)` 的 `versionCode` 参数从未被使用,删除
- **修复 Severity 排序显式化**(audit finding #4): `Severity` enum 重排 `{Violation, Warning, Info, Positive}` + KDoc 解释「Positive 放末位是 guard」;`HomeScreen` 改用显式 `severityRank` 函数(Violation=3 / Warning=2 / Info=1 / Positive=0),过滤 Positive 取 `worstViolationOrWarning`(替代原 `maxOfOrNull { it.severity }` —— 该隐式 ordinal 比较把 Info > Positive 误当成更高严重度)。配 `HomeScreenSeverityRankingTest` 6 tests pin 契约(纯 JVM,无 Compose 渲染,稳定)
- **buildSrc helper 三合一**(audit finding #5): `app/build.gradle.kts` 删除 inline `groovy.json.JsonSlurper` 块 / `FileInputStream` copy / `sha256HexForBuild` 函数(共 -56 行,加 import 4 行),改用 `LatestJsonGenerator.buildLatestJson(...pretty=true)` + `ArchiveVision.archiveForUpload(...)` + `LatestJsonGenerator.sha256Hex(apk)`。`buildSrc:helper` 三处职责收口在 `LatestJsonGeneratorTest` + `ArchiveVisionTest` JVM 单测
- **零碎清理**:
  - `FakeRuleMatcher` 加 `@VisibleForTesting internal` 修饰符(lint 现在能识别其 test-only 性质)
  - `prepare-ocr-rules.gradle.kts` 注释 `116 rules / v4` → `129 rules / v9` + drift warning
  - `network_security_config.xml` 注释澄清 `apkSha256` 是 forensic / debugging 用途,**`signerCertSha256` + BuildConfig.UPDATE_EXPECTED_CERT_SHA256 才是真正的 trust anchor**(server 只能明文 HTTP 下载 APK,所以 `apkSha256` 无法在传输层验证)
  - `SettingsViewModel.kt` `download()` KDoc 更新描述 FGS 推送 `downloadId` 到 `UpdateRepository.onDownloadProgress` 的契约
  - `PaddleOcrRealDeviceAbTest.kt` fixture 重构:`test_set/img1/2/3.jpg` 与 `fixtures/mentor/mentor_1_5/2_6/3_9_2011.jpg` 字节一致(SHA-256 验证),改共享同一份,避免两份独立 fixture 漂移;`img4.jpg`(signage-11-2011)独有,保留在 `test_set/`
  - `README.md` v0.1.18 → v0.1.35 → v0.1.36(shipping version banner 同步)
  - `ChangelogScreenTest` shipping-version pin `v0.1.35 → v0.1.36`
- **测试覆盖**: 574 tests / 0 failures / 2 skipped / 100% successful(`testDebugUnitTest -PmodelProfile=shell` + `:buildSrc:test`)

## v0.1.35 · 2026-08-28

- **修复 v0.1.34 case #13 miss — bare「高产」seed 广告**(《广告法》第二十七条第(一)项「科学上无法验证的断言」+ 第(二)项「表示功效的断言或者保证」):`ad_signage_art27_seed_yield_guarantee` keyword 列表扩 3 个 bare 产量承诺 keyword `高产` / `丰产` / `稳产`(原 14 keyword 全部围绕「保证 / 承诺 / 必 / 确保 / 效益保证」短语,bare「高产」命中不了)。即使放实物照片也不能直接宣称「高产」——果实饱满特写 ≠ 整田丰产保证,无对照组 / 区域条件 / 品种试验即科学上无法验证。配 fixture `违规案例/text_signage_pea_01.md` 锁住 case #13 命中集合 `{ad_signage_art27_seed_yield_guarantee, Violation}`,AdSignageTextFixtureRegressionTest 命中集合精确 pin
- **测试 pin 修复**: `ChangelogScreenTest.bundled asset first section matches the shipping version` 版本断言 `v0.1.33 → v0.1.35`(v0.1.34 release 时漏 bump test pin,本次连同 v0.1.35 一起对齐)

## v0.1.34 · 2026-08-27

- **规则扩充 v8→v9**(`ad_signage_rules.json`,121 → 129):
  - 新增 8 条规则,覆盖审计发现的 6 张未覆盖案例 + 弱覆盖强化: `ad_signage_signage_alcohol_drink_scenario`(酒类通用场景)、`ad_signage_signage_gift_to_leader`(送领导/客户公务商务送礼诱导)、`ad_signage_signage_military_political_marketing`(商业借用军政形象营销,§9(七))、`ad_signage_signage_weight_loss_food_claim`(普通食品减肥/保健宣称,§17+§18)、`ad_signage_edu_art24_public_servant_endorsement`(在职公务员代言教育,§9(二)+§24(三))、`ad_signage_signage_weight_loss_data_commitment`(虚构减重数据,§17+§18+§28)、`ad_signage_signage_food_lung_health_claim`(食品肺部保健宣称,§17+§18)、`ad_signage_signage_food_beneficiary_count_claim`(亿国人夸大受益人群,§9(三)+§28)
  - 强化 2 条规则: `ad_signage_art22_tob_alc` 加 `白酒/啤酒/红酒/黄酒/洋酒/酒类/酒精度数` 7 keyword;`ad_signage_art10_minor` 加 `未成年人/小学生/中学生`(去掉 `婴儿/幼儿`,避免与 infant_milk 规则抢命中)
- **违规案例归档**: 42 个 text fixture 全覆盖 16 桶,新增 8 fixture(text_signage_alcohol / gift / military / weightloss / weightloss_data / lung / beneficiary + text_education_official),全部走政府站一手「处罚通报」类文本,AdSignageTextFixtureRegressionTest 命中集合精确 pin
- **`_违规档案总册.md` 演化**: 加 `关联规则 ID` 列(66 节 × ≥1 ruleId)、§审计日志、§桶汇总(本次审计,主桶计入)
- **`_coverage_matrix.md` 双向矩阵建立**: §1 规则→示例图 129 行、§2 示例图→规则 66 行、§3 覆盖率 60/66 已覆盖 + 6/66 backlog + 17/66 弱覆盖
- **测试回归**: AdSignageTextFixtureRegressionTest 命中集合 pin(收紧过度关键词 + fixture expected 同步)+ AssetRuleLoaderTest v9 版本断言(8→9)。568 tests / 0 failures / 2 skipped / 100% successful

## v0.1.33 · 2026-08-27

- **规则扩充(ad_signage_rules.json)**:
  - 新增 `ad_signage_signage_food_safety_implication` 暗示安全性规则(20 关键词),覆盖 #49「安全放心」+ #52 暗示天然无害类典型违规;广告招牌规则数 118 → 121
  - 法规依据:广告法第十八条 + 药品医疗器械保健食品特殊医学用途配方食品广告审查管理暂行办法第十一条第五项、第五十八条
- **违规案例归档**:66 张真实公开广告图片(01-66 编号)+ 完整违规档案总册(926 行 / 14 个桶 / 严重度 Critical×59 + Warning×7 + Info×0)
  - 新发现:#06 商业借用军政形象、#49 + #52 保健食品暗示安全性违规
- **知识库时效性**:2 份已废止法规迁移至 `知识库/已废止/`(户外广告登记管理规定 2016 / 母乳代用品销售管理办法 2017);3 份食品标识 2027-03-16 新版替换(GB 7718 / GB 28050 / 食品标识管理规定);1 份广告业务改名(药品医疗器械保健食品特殊医学用途配方食品广告审查管理暂行办法)
- **自动化基础设施**:CLAUDE.md 新增「知识库时效性整理」段;project-commit skill 新增「Release 三段式打标」;PreToolUse hook 新增 Rule 3(防误删 `app/libs/*.aar`);新建 icevision-release skill(发版流水线 5 步 pre-flight + 4 步流水线)

## v0.1.32 · 2026-08-26

- **优化:底部操作栏 affordance 强化**(`CaptureBar.kt`)
  - 左「选图」:从单图标 `FloatingActionButton(40dp)` 升级为 `ExtendedFloatingActionButton` + 可见"选图"文字标签 + PhotoLibrary 图标。按用户规格"icon左边加上「选图」"实现 text-then-icon(图右)顺序 — 借助 Extended FAB 的 `icon` 槽故意空、`text` 槽放自定义 `Row { Text; Icon }`,绕开 FAB 内部 icon→text 硬编码(无原生 text-then-icon 的 Extended FAB 变体)
  - 右「拍照」:同 `ExtendedFloatingActionButton` 组件(`CaptureButton.kt` 已封装),新加 `Modifier.fillMaxWidth()` 让它拉满 BottomAppBar 的右半区 — 视觉上明显比左边的「选图」长,主操作 affordance 更显眼
  - `enabled = false` 仍只影响 capture FAB(pick 仍是 Loading 期间的逃生口)
- 单测更新 + 全套测试通过:
  - `CaptureBarTest.kt` 加 `onNodeWithText("选图", useUnmergedTree = true).assertExists()`(原断言只检 a11y desc,现在检可见文字)
  - `HomeScreenTest.kt` 同步注释 + 加 `onNodeWithText("选图", ...)` 断言
  - `ChangelogScreenTest.kt` shipping-version pin `v0.1.31 → v0.1.32`(常规 bump 同步)
  - `testDebugUnitTest -PmodelProfile=shell`:568 通过 / 2 skipped / 0 failures

## v0.1.31 · 2026-08-26

- **修复:首页「红框位置标错了」三次复盘(真机 A/B 验证 v0.1.30 修复未闭环)** — `HighlightOverlay` 矩形在 `ice_ocr_rules` profile 上对 `sampleSize > 1` 的图仍错位。2026-08-26 烟测 3 图:截图 1(竖图)框在文字上、截图 2(竖图)单框碰巧落文字,但截图 3(横图,真机拍摄公交车 + durex 广告)红框完全落在右上角空白处,与 OCR 检出的「激情公益红 守护爱始终」「durex」「创维汽车」等文字完全不重叠
  - **新根因(独立于 v0.1.29 EXIF 双重旋转 / v0.1.30 imageSize 透传)**:v0.1.30 加的 imageSize 链路在「消费契约」层面对了(`computeFitTransform` 优先 `imageSize` + `ImagePreviewFitTransformTest` 6 例 Robolectric pin 住 `imageSize=IntSize(3024, 4032)` → `scale=0.248`),但**生产端契约**漏了一半 —— `PaddleOcrEngine.recognize` 设 `imageWidth = bitmap.width`(下采样 bitmap 尺寸,典型 `sampleSize=2` 时 = `2016×1512`),不是全分辨率 display-oriented 尺寸(4032×3024 / 3024×4032)。而 box 坐标已在 `OCRBox.toBoundingRect` 里被乘以 `loaded.sampleSize` 投到全分辨率空间,`imageSize` 与 `line.box` 活在两个不同坐标系,`computeFitTransform` 用错误的 `imageSize` 当 `refW/refH` → `scale` 偏约 2× → box 整体飘到 canvas 右下方 letterbox
  - **截图 1 vs 截图 3 行为差异解释**:`BitmapLoader.sampleSize(longestEdge, maxEdge=2048)` 对 `longEdge ≤ 2048` 返回 1 → `bitmap.width × 1 = bitmap.width`(等于全分辨率),bug 静默;`longEdge > 2048`(典型手机相机 4032+ 像素)才 `sampleSize=2` 触发 bug。截图 1 长边可能较小 → `sampleSize=1` → 框对;截图 3 真机拍摄 4032×3024 横图 → `sampleSize=2` → bug 全显。截图 2 视觉上框在文字上,实际也已经偏移 `sampleSize²` 倍(典型 2×),只是「东莞·福州·宁波·济南」该行横向铺满中部不易察觉
  - **修复**:`PaddleOcrEngine.recognize` 设 `imageWidth = bitmap.width * loaded.sampleSize`、`imageHeight = bitmap.height * loaded.sampleSize`,与 `OCRBox.toBoundingRect` 已有的 sampleSize 乘法对齐,保证 `imageSize` 和 `line.box` 在同一坐标系。注释同步更新(从「BitmapFactory 看到的就是 FULL bitmap」改成「bitmap 是下采样版,× sampleSize 才回到 box 坐标空间」)
  - **回归 pin**:`PaddleOcrEngineTest` +1 例 androidTest —— `recognize_imageSize_isFullResDisplayDims_notDownsampled`,用 `fixtures/dongjiao_daojia.jpg`(1.5 MB 真机照片,`longEdge > 2048` → `sampleSize=2`,必触发 bug;`test.png` `longEdge ≤ 2048` → `sampleSize=1`,OLD 错代码也过,必须换 fixture):① `BitmapFactory.decodeFile(inJustDecodeBounds=true)` + `BitmapLoader.exifRotationDegrees` 计算期望全分辨率 display-oriented 尺寸(避免分配 4032×3024 ARGB_8888 bitmap 占 ~50 MB 测试设备内存)② 断言 `result.imageWidth == expectedW && result.imageHeight == expectedH` ③ 交叉断言每个 `line.box.right/bottom <= imageWidth/imageHeight`(OLD 错代码下每个 box 越界直接 fail)④ sanity guard —— fixture `longEdge` 必须 > 2048,否则 `sampleSize=1`、pin 失牙。androidTest 跑前需 `connectedDebugAndroidTest` + 真机 + `ice_ocr_rules` profile 配齐 ONNX 模型(同 `PaddleOcrExifTest` / `PaddleOcrFixtureTest` 路径,CLAUDE.md 已踩)
  - **残余未 pin**(同 v0.1.29 / v0.1.30 的限制):PaddleOCR SDK 是 native + ONNX Runtime + OpenCV,Robolectric 跑不动,androidTest + 真机烟测把关;Compose runtime 真渲染 canvas letterbox + Painter ContentScale.Fit + Coil 额外下采样的协同层也只能真机烟测(2026-08-26 烟测 截图 1 + 3 已显式验证)
- v0.1.30 / v0.1.29 的修复保留,本版本不撤销 —— 那两条在 Robolectric 不可复现的 API 24+ 路径上仍然必要,只是不足以独立闭环红框位置问题
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,总 568 / 0 failures —— unit test 未动)

## v0.1.30 · 2026-08-26

- **修复:首页「红框位置标错了」二次复盘(真机 A/B 验证 v0.1.29 修复未生效)** — `HighlightOverlay` 矩形在 `ice_ocr_rules` profile 上依然落不到文字上(其他真机复核 8 命中 / 8 全错位,与 v0.1.29 同症状)
  - **新根因(独立于 v0.1.29 的 EXIF 双重旋转 bug)**:`PaddleOcrEngine.recognize()` 喂给 `PaddleOCR.recognize(bitmap)` 的是 `BitmapLoader.downsampledBitmapWithScale(bytes).bitmap`(`maxEdgePx=2048` floor-based 下采样后,maxEdge=4032 仍走 `sampleSize=1` 不下采样,得到 API 24+ 自动 EXIF 旋转过的 3024×4032 全分辨率 display-oriented bitmap),PaddleOCR 返回的 bbox 坐标就在这 3024×4032 空间。但 `AsyncImage` + Coil 按 layout 约束(典型 800×1000 px)做了下采样,`painter.intrinsicSize` 反映的是下采样后的 bitmap 尺寸(800×1000),不是 3024×4032。`computeFitTransform` 误把 800×1000 当参考 → `scale = min(800/800, 1000/1000) = 1.0`,`offset = (0, 0)` → 3024×4032 空间的 bbox 直接画到 800×1000 canvas → 整块飘出右边/下边
  - **修复**:把 OCR 跑过的全分辨率 display-oriented bitmap 尺寸沿数据流透传:`PaddleOcrEngine` 取 `bitmap.width/height` 写进 `OcrResult.imageWidth/imageHeight` → `ImageAnalyzerRepository` 透传到 `AnalysisState.OcrDone.imageWidth/imageHeight` 与 `ViolationReport.imageWidth/imageHeight` → `HomeScreen` 派生 `imageSize: IntSize?` 传入 → `ImagePreview.computeFitTransform(painter, boxSize, imageSize)` **优先**用 `imageSize` 当参考,fallback 才回到 `painter.intrinsicSize`(shell profile / OcrDone 到达前用)。附带:`imagePainter` race 也消失,transform 不再依赖 painter 加载完成
  - 7 处文件改动(纯增量,旧 API 全部加默认值,既有的 9 处 `OcrResult(...)` / `OcrDone(...)` / `ViolationReport(...)` 构造点零回归):`OcrResult` / `AnalysisState.OcrDone` / `ViolationReport` 加 `imageWidth: Int = 0` + `imageHeight: Int = 0`;`PaddleOcrEngine` 填入 `bitmap.width/height`;`ImageAnalyzerRepository` 透传;`ImagePreview.computeFitTransform` 提升为 `internal` + `@VisibleForTesting`,签名加 `imageSize: IntSize?`;`ImagePreview` 增加 `imageSize: IntSize?` 参数;`HomeScreen` 派生并传入
  - **回归 pin**:`ImagePreviewFitTransformTest` 6 例 Robolectric(SDK 33)锁住 transform 契约:① `imageSize` 优先于 `painter.intrinsicSize`(玉米广告 repro:3024×4032 全 + 800×1000 layout → scale = min(800/3024, 1000/4032) ≈ 0.248,letterbox 居中,box at (1500,2000,1700,2100) 落点 x=397 y=496 — 这次实测在 800×1000 canvas 内)② `imageSize=null` fallback `painter.intrinsicSize` ③ `imageSize=IntSize(0,0)`(默认值 sentinel)也 fallback,不把 0 当真实尺寸 ④ box 与 image 同尺寸 → identity ⑤ `painter=null` & `imageSize=null` → safe identity ⑥ 横向 letterbox 居中
  - `computeFitTransform` 同时补 `Float.isFinite()` / `> 0` 防御:之前 `intrinsicSize` 为 NaN/0/负时会返回 `scale = NaN` 让 Canvas 静默崩(同 v0.1.29 那条防御的覆盖范围扩展到 `imageSize` 路径)
  - **链路级 pin**:`ImageAnalyzerRepositoryTest` 增 2 例 — `OcrResult.imageWidth/imageHeight` 必须沿 `OcrDone` 与 `ViolationReport` 双向透传(`HomeScreen` 在 OcrDone 阶段从 OcrDone 读,在 Complete 阶段从 ViolationReport 读 — 任一断点都让 computeFitTransform 拿到 0 → fallback 到 painter.intrinsicSize → 红框漂走);`HomeScreenImageSizeDerivationTest` 4 例纯 JVM pin 派生契约 — OcrDone 优先、report 兜底、双零/null 返回 null、负值/单零视为不可用
  - **残余未 pin**:`PaddleOcrEngine.recognize` 内部 `imageWidth = bitmap.width` 这一行 — PaddleOCR SDK 是 native,Robolectric 跑不动,只能 androidTest + 真机烟测把关(v0.1.29 的 helper-level pin 同等限制,沿用)
- v0.1.29 的 EXIF 双重旋转修复与本版本独立,本版本不撤销 v0.1.29 — 那条修复在 Robolectric 不可复现的 API 24+ 路径上仍然必要,只是不足以独立闭环红框位置问题
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,新增 ImagePreviewFitTransformTest 6 + ImageAnalyzerRepositoryTest 2 + HomeScreenImageSizeDerivationTest 4,总 568 / 0 failures)

## v0.1.29 · 2026-08-26

- **修复:首页「红框位置标错了」** — `HighlightOverlay` 矩形在 `ice_ocr_rules` profile 上落不到文字上(实测 8 命中 / 8 全错位:大框飘到图片右上角、小框散落到 OCR 文字面板与底部拍照按钮区)
  - 根因:`PaddleOcrEngine` 在 `BitmapFactory.decodeByteArray` 之后又调了一次 `BitmapLoader.applyExifRotation`。Phase 2 设计文档假设 BitmapFactory 不应用 EXIF 旋转(API < 24 的行为),但 minSdk=26 已在 API 24+ 路径上,BitmapFactory JNI 内置 `applyOrientation()`,返回的就是 display-orientation bitmap。手动再转一次 = 双重旋转,OCR 返回的 bbox 在「被再转 90°/180° 的位图」坐标系里,而 Coil 画的是 BitmapFactory 的 display-orientation 坐标系,bbox 经 `computeFitTransform` 映射后整块错位
  - 修复:`PaddleOcrEngine.recognize()` 移除 `applyExifRotation` 调用,直接喂 `BitmapLoader.downsampledBitmapWithScale(bytes).bitmap` 给 `PaddleOCR.recognize`。`BitmapLoader.applyExifRotation` / `exifRotationDegrees` 保留为 utility(`BitmapLoaderTest` 还要测),但不再走 OCR 路径
  - 回归 pin:`BitmapLoaderExifRotationTest` 4 例 Robolectric(SDK 33)用 `test_rotated.jpg`(PIL 生成,带 EXIF Orientation tag 的 JPEG fixture)锁住 `BitmapLoader` 的旋转 utility:`exifRotationDegrees` 必须与 `ExifInterface` 直读一致(0/90/180/270);`applyExifRotation(0)` 必须 no-op(避免 EXIF=1 截图每次都多分配一张 bitmap);90° / 270° 必须交换 W↔H、180° 必须保尺寸。Robolectric 当前 SDK 33 的 `BitmapFactory.decodeByteArray` 不应用 EXIF(API < 24 表现),API 24+ 的双重旋转 bug 不能在 unit test 里复现 — 端到端真机回归留 `PaddleOcrExifTest` androidTest + 设备烟测把关,这套 unit test 仅防 helper 层退路。未来 Robolectric 升级若开始模拟 EXIF,`bitmapFactory_underRobolectric_decodesRawDimensions_evenWhenExifPresent` 会先 fail(标桩信号),届时 v0.1.29 的"别手动再转一次"假设就可以在 unit test 层闭环
  - `computeFitTransform` 顺手补 `Float.isFinite()` / `> 0` 防御:之前 `intrinsicSize` 为 NaN/0/负时会返回 `scale = NaN` 让 Canvas 静默崩
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,新增 BitmapLoaderExifRotationTest 4 例,总 556 / 0 failures)

## v0.1.28 · 2026-08-26

- **修复:`latest` 上发布的安装包未携带 OCR 模型 + 规则引擎,违规识别全部失效**
  - 根因:v0.1.27 走的是 `shell` profile,产物 APK 内嵌 `FakeOcrEngine` + 空的 `ad_signage_rules.json`(`{"version":1,"rules":[]}`)。任何图片 OCR 出文字后规则表为空,UI 永远显示「未发现违规用语」。本草专治糖尿病、东郊到家等真实广告招牌验证均复现该症状
  - 修复:本次发布切换到 `ice_ocr_rules` profile(59 MB,含 PP-OCRv6_small ONNX 模型 + `ad_signage_rules.json` 120 条 + `food_label_rules.json` 66 条 + ONNX Runtime + OpenCV),OCR 真跑 PaddleOCR,规则真跑 `AdSignageRuleMatcher` AC 匹配
  - 真机端到端验证(华为 nova 6,SDK 35,arm64-v8a):
    - 本草专治糖尿病 100%有效 → **8 违规**(医药 + 绝对化用语命中)
    - 东郊到家(技师 9 万人 / 累计 1000 万次) → **1 警告**(《广告法》第十一条第二款,引证数据未标出处)
- **流水线修复:`./gradlew.bat assembleRelease` 不再需要手动 export 5 个 `ICESPIRITAI_RELEASE_*` env var**
  - 凭据写入 `~/.gradle/gradle.properties`(gitignored),`signingConfigs.release` 走 `providers.gradleProperty(...)` fallback 路径,CI / 本地都不再卡凭据缺失 GradleException
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,551 tests / 0 failures)

## v0.1.27 · 2026-08-25

- **首页标题居中 + ⚡ accent**
  - 「冰灵锐目」从左对齐移到屏幕正中。布局:单个 `Surface` 内的 `Box`,中央放「冰灵⚡锐目」`Row`,右上放设置 `IconButton`。`Box` 而非 `Row + weighted spacer` 是为了不让 settings 按钮的宽度影响居中精度
  - ⚡ 单独一个 `Text` 渲染,颜色用 `colorScheme.tertiary`,跟两侧的「冰灵」「锐目」视觉区分开。三个 `Text` 用 `mergeDescendants = true` 合到同一个 a11y 节点,`contentDescription = app_name("冰灵锐目")`,TalkBack 仍按一个品牌名播报,不会拆成「冰灵」「闪电」「锐目」三段
  - 启动器 label(`AndroidManifest.applicationLabel`)继续是「冰灵锐目」无 ⚡,`app_name` 字符串不动,只新增 `app_name_prefix` / `app_name_bolt` / `app_name_suffix` 三个拼接用的资源
- **shell profile 上传图片崩溃修复**:v0.1.26 用户实测反馈「上传图片后报错!」
  - 根因:`AdSignageRuleMatcher` 初始化时若 keywords 为空(shell profile 发的 `{"version":1,"rules":[]}`),`AhoCorasickDoubleArrayTrie` 不调 `build()`;之后 `scan()` 无条件 `keywordTrie.parseText(...)` 触发 HankCS 库内部 `Cannot load from int array because "this.base" is null` NPE,被 `ImageAnalyzerRepository` 的 catch-all 块捕获为 `ErrorCode.UNKNOWN`,UI 展示「未知错误,请重试」
  - 修复:init 时把 `hasKeywordTrie` / `hasSourceMarkerTrie` 两个布尔记下来,scan 时按这两个 flag 守 `parseText`。empty rules 不再触发 NPE
  - `ice_ocr_rules` profile 行为不变(规则 JSON 118 条非空,`hasKeywordTrie = true` 走原来分支),0 条规则只在 shell profile 出现
- **回归 pin**:`ShellProfileRegressionTest` — `FakeOcrEngine + AdSignageRuleMatcher(emptyList())` 跑完整 analyze flow,断言无 `AnalysisState.Error` 发射 + 终态 `Complete.ocrText = "本店专治糖尿病,100% 有效"` + `hits = emptyList()`。这条测试在 v0.1.26 跑会失败(reproduced the bug),v0.1.27 跑过
- `HomeTopBarTest` 重写:旧版断言 `onNodeWithText("冰灵锐目")` 还能命中单个 Text;新结构是三个 Text,改成断言三段各自存在 + `onAllNodesWithContentDescription(R.string.app_name)` 计数为 1(merged semantics 生效)
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`)

## v0.1.26 · 2026-08-25

- **首页 tab pill 化 + 标题间距收紧**
  - `RuleTabBar` 重写为 pill 风格:每个 tab 是 `RoundedCornerShape(20.dp)` 的 `Surface`,选中态填充 `secondaryContainer` + `titleMedium` SemiBold,未选中态 `surfaceVariant` + `bodyLarge`。原本 tab 与「冰灵锐目」标题都是平面文字,字体区别度不够;现在 pill 容器与平铺标题形成强对比,后续启用「食品标识」tab 时风格一致
  - `HomeTopBar` 移除 `TopAppBar`,改为单个 `Surface` 内的 `Column` 直接堆叠标题行 + tab 行。标题与 tab 间距由 ~16dp 收到 12dp,标题更靠近 tab
- **Viewer 滚动崩溃修复**:`ViewerTextList` LazyColumn 之前用 `key = { it.text }`,当 OCR 识别出两条相同文字的 TextLine(广告招牌常见,例如「门店」出现两处)时,合成可见 item 触发 `IllegalArgumentException: Key X was already used` 闪退。改用 `itemsIndexed` + `key = { index, _ -> index }` 走 index-based identity(OCR 每次重扫都整列表替换,position-based 稳定)
- **回归 pin**:`ViewerTextListTest.ViewerTextList does not crash when TextLines share identical text` — 三条相同「门店」TextLine 入参,断言 `setContent` 不抛异常 + 行数标题「共 3 行文字」正确显示
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,551 tests / 0 failures)

## v0.1.25 · 2026-08-25

- **ad_signage `signage` 分类显示名修订**:HitCard 「分类」行原显示「门店招牌」,对短视频 / 互联网广告截图误导。改为「广告文案」,媒介中性。`category` JSON 字段值不变(仍为 `"signage"`),只改 `CategoryDisplay.kt` 映射 + 测试
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`)

## v0.1.24 · 2026-08-25

- **规则库 v8 — ad_signage regulation 字段清理**:4 条 ad_signage 规则之前串了食品标识 / 婴幼儿配方注册 / GB 7718 等非广告法规。修正为只引《广告法》对应条款,HitCard 「依据」行不再出现食品标识相关法规
  - `ad_signage_art10_minor`:→《广告法》第十条(二) + 第五十七条
  - `ad_signage_signage_infant_milk`:→《广告法》第二十条 + 第五十七条
  - `ad_signage_signage_food_function_claim`:→《广告法》第十七条 + 第五十八条
  - `ad_signage_signage_food_disease_target`:→《广告法》第十七条 + 第五十八条
- **回归 pin**:`AdSignageRuleLoaderTest.load_realAssets_adSignageRulesCiteOnlyAdvertisingLaws` — 每条规则的 regulation 字段不得含 `食品标识 / GB 7718 / 配方注册 / 婴幼儿配方 / 特殊医学用途 / 保健食品 / 蓝帽子 / 国食健字`,且必须含广告法规信号(`《广告法》` / `广告审查` / `广告发布` / `广告登记`)
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`)

## v0.1.23 · 2026-08-25

- **规则库 v7 — 食品功能宣称补漏**:实地拍摄小园玉米紫玉米花青素广告时,OCR 把「抗氧化」识别为独立 TextLine,但 `ad_signage_signage_food_function_claim` v6 keywords 没收,HighlightOverlay 没有红框。v7 把 `抗氧化` 加入 keywords 列表(38 → 39 条),填补真实场景漏报
- **影响**:包含「抗氧化 / 抗衰老 / 延缓衰老」等保健功效的小广告现在会正确触发 §17 食品功能宣称违规
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`)

## v0.1.22 · 2026-08-25

- **UI 现代化 Phase 3.4-3.5**:CaptureButton → ExtendedFloatingActionButton;CaptureBar 改 BottomAppBar + 大拍照 FAB + 小选图 FAB;LoadingOverlay 重写为 shimmer 骨架屏;SettingsScreen 改 Card + ListItem;AppearanceSection 改 SegmentedButton;ViewerScreen 命中行加 token Surface + animateContentSize;IceSpiritVisionActivity 已开启 `enableEdgeToEdge()`(Phase 3.5 之前已合入)
- **影响**:主操作更突出,加载进度可见,设置页更易扫读,Viewer 命中行质感更立体
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`)

## v0.1.21 · 2026-08-25

- **UI 现代化 Phase 3.3**:HitCard 重写为左侧 6dp 严重度色条 + 纵向渐变背景 + 双引号包裹命中文字 + FilledTonalButton 法规展开;HighlightOverlay 升级 — Info 严重度也显示描边、6dp 描边宽度、动画渐变 alpha;ImagePreview 适配 edge-to-edge 系统栏 inset(idle 提示不再被状态栏遮挡,预览图保持 edge-to-edge)
- **影响**:命中卡更易扫读(色条优先于文字)、违规框视觉权重提升、空状态文字不再被状态栏遮挡
- **回归**:4 张广告招牌 fixture OCR / 命中 / 严重度分布与 v0.1.14 字节级一致
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,545 tests / 0 failures / 2 skipped)

## v0.1.20 · 2026-08-25

- **UI 现代化 Phase 3.2**:StatusBanner 重写为 KPI 横条(违规 / 警告 / 信息 三段,AnimatedContent 数值滑入);HomeTopBar 透明背景 + headlineSmall 标题 + Outlined 齿轮图标;RuleTabBar 升级 Material 3 SecondaryTab(3dp 指示器 + titleMedium 选中态)
- **影响**:屏幕顶部信息密度提升,违规数字一眼可见;tab 风格更接近 Material You

## v0.1.19 · 2026-08-25

- **UI 现代化底层** Phase 3.1:严重度色板扩展(Info 角色 / Container 角色)、Type 字号加 `displaySmall` / `headlineMedium` / `headlineSmall`、新增 `IceMotion` 数据类与 `Modifier.emphasizedEnter()`、通过 `LocalSeverityColors` 暴露统一严重度配色
- **内部重构**:Hex 值未变更,只增加 token;`IceSpiritVisionTheme` 新增 `LocalSeverityColors` provider
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`,544 tests / 0 failures / 2 skipped)
- `versionCode 18→19`,`versionName 0.1.18→0.1.19`

## v0.1.18 · 2026-08-22

- **断点续传修复**:`UpdateResumeWorker` 触发的冷启动续传,`UpdateDownloadService` 现在会从 DataStore 记录重建下载 URL / 目标路径 / 签名证书。此前缺失这三种 extra 会直接 `return`,导致「上划杀进程 → 重新打开」后续传不生效(可能触发 `ForegroundServiceDidNotStartInTimeException`)
- **Android 12+ 后台 FGS 兜底**:WorkManager 在后台唤醒进程时启动前台服务若抛 `ForegroundServiceStartNotAllowedException`,worker 会 `retry()` 等待前台后重试,不再崩溃 / ANR
- **更新通道信任锚点**:客户端在 `BuildConfig` 中固定签名证书 SHA-256,后续下载校验不再信任明文 HTTP 下发的 `signerCertSha256`,抵御 MITM 下发自洽的「JSON + APK」伪造对;`vision-latest.json` 缺失该字段时也按客户端固定值校验
- **并发 / 健壮性**:`UpdateDownloadService.inFlight` 改用 `ConcurrentHashMap` 线程安全集合,并清除 `return` 路径上的幻影 id 泄漏;`cleanup()` 去掉阻塞式 `runBlocking`,改为挂起删除
- **安全 / 文档 / CI**:禁用应用备份(`allowBackup=false`,App 保存实拍图与取证包);README 与版本目录注释对齐真实构建栈(AGP 9.3 / Kotlin 2.4.10 / Gradle 9.7 / compileSdk 37);新增 GitHub Actions CI(跑 `assembleDebug` + `testDebugUnitTest`)
- `versionCode 17→18`,`versionName 0.1.17→0.1.18`

## v0.1.17 · 2026-08-22

- **应用内更新支持后台下载 + 锁屏不掉线**(Foreground Service,`foregroundServiceType="dataSync"`,Android 14+ 红线)
  - 进入设置 → [下载更新] 后即便立刻锁屏,下载仍在通知栏 + App 内 `UpdateSection` 双通道进度持续推进
  - 通知三通道:进行中 / 可安装 / 失败;每条通知带 `取消 / 安装 / 稍后 / 重试` 动作按钮
- **断点续传**:HTTP `Range: bytes=N-` + `If-Range: <etag>`;FGS 退避 2 / 4 / 8 s,最多 3 次;落地后由 `DownloadStateStore`(DataStore Preferences)持久化到进程被杀也活得下来
- **冷启动自动续传**:Application.onCreate 里跑 `UpdateResumeCoordinator.scanAndDispatch()`,正在下载的 partial 自动入队 `UpdateResumeWorker`(WorkManager,`NetworkType.CONNECTED` 约束),无需用户点重试
- **签名校验失败 / 取消 / 退避耗尽** 三种 Failed subtype 在 `UpdateSection` 区分文案(「签名校验失败,请联系开发者」/「已取消」/「网络不可达,请重试」),失败自动清理 partial + DataStore,不留垃圾
- **真机回归 4 项**(`CancelFromNotificationTest` / `UpdateResumeCoordinatorAndroidTest` / `ProcessKillResumeTest` / `UpdateDownloadServiceColdTest`),覆盖 cancel → cleanup、Coordinator → Worker 入队、force-stop → 重启续传、cold + warm 启动时延(实测 cold_ms=4 / warm_avg_ms=3,均 <5 s)
- **`POST_NOTIFICATIONS` / `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` / `WAKE_LOCK` 运行时权限** + `<service android:foregroundServiceType="dataSync">` 已在 `AndroidManifest.xml` 声明
- 烟测场景(锁屏 / Wi-Fi 切换 / 飞行模式 / 上划杀进程 / 通知权限拒绝 / 签名校验失败)见 `docs/smoke/2026-08-22-update-fgs-resume.md`
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`);真机 androidTest 4/4 pass(华为 nova 6)
- `versionCode 16→17`,`versionName 0.1.16→0.1.17`

## v0.1.16 · 2026-08-21

- **新增规则:广告法 第二十七条 · 农作物种子 / 种养殖广告无根据的产量 / 效益保证**(severity `Violation`)
  - 新规则 id `ad_signage_art27_seed_yield_guarantee`,category `agricultural`(新增 CategoryDisplay 中文 label 「农业投入品广告」)
  - 触发模式:种子 / 种苗 / 农药 / 兽药 / 饲料 / 化肥广告中含「必增产 / 保证增产 / 确保增产 / 承诺增产 / 产量保证 / 产量承诺 / 高产保证 / 保证丰产 / 保证稳产 / 效益保证 / 效益承诺 / 增产达 / 亩产保证 / 科学上无法验证」等无根据的产量 / 效益断言或保证性承诺
  - 法规依据:《广告法》第二十七条(原文已在 `知识库/广告业务/中华人民共和国广告法.md` L185-193,适用判别要点已扩 §27 条目)+ 第五十八条处罚条款
  - 触发案例:玉米种子高速广告「必增产」现在报 Violation(此前 0 命中)
- **`AdSignageRule` 规则库 v5 → v6**(118 → 120 条)
- **fixture 转写修正**:`AdSignageMentorFiveImageRegressionTest` 中 5_2011 玉米种子广告 fixture 文本「增产必选」修正为「必增产」(与原图 OCR 一致;此前手转写笔误)
- 单元测试全绿(`testDebugUnitTest -PmodelProfile=shell`)
- `versionCode 15→16`,`versionName 0.1.15→0.1.16`

## v0.1.15 · 2026-08-21

- **新增规则：广告法 第十一条第二款 · 数据未标明出处**（severity `Warning`）
  - 新规则 id `ad_signage_art11_data_citation`，category `signage`
  - 触发模式：广告使用数据 / 统计资料 / 调查结果 / 文摘 / 引用语（典型句式 "X 万人 / 累计 X 万次 / 同比增长 X% / 排名第一"），且**未标明出处**（无 "数据来源 / 据 X 报告 / 截至 YYYY" 等表述）
  - claim 触发词 38 个；sourceMarker 缓解词 21 个 + 2020-2030 完整年份（154 条）→ 命中 claim 且全无 sourceMarker 即报 Warning
  - 触发案例：东郊到家 "全国技师超 9 万人 | 累计服务超 1000 万次" 现在报 Warning（此前 0 命中）
- **`AdSignageRule` 扩展 `sourceMarkers` 字段 + `AdSignageRuleMatcher` 增加 absence 复合匹配**
  - 旧规则 `sourceMarkers = emptyList()` 时行为字节级等价，118 条既有规则零回归
- **东郊到家 OCR 真机 fixture 测试**（`PaddleOcrFixtureTest`，androidTest）：真机跑 PaddleOcrEngine，固化 baseline 至 `app/src/test/resources/fixtures/dongjiao_baseline.json`；端侧可检出 + 触发 §11(2)
- **5 张 mentor 图 OCR smoke test**（`MentorOcrSmokeTest`，androidTest）：防止 OCR 阈值调过头；预发版跑
- **知识库 `中华人民共和国广告法.md`**：适用判别要点加跨域引用原则（广告招牌 tab 不引食品标识等非广告业法规）+ §11(2) 判别要点
- 单元测试全绿（`testDebugUnitTest -PmodelProfile=shell`）；真机 androidTest 已过（华为 nova 6）
- 已知边界：v1 absence 检测是全局 sourceMarker 检测，商业套语 "据用户反馈" 会被误识别为已标注来源（假阴性）；v2 同段复检会收紧，留作后续 spec
- `versionCode 14→15`，`versionName 0.1.14→0.1.15`

## v0.1.14 · 2026-08-21

- 修复 v0.1.13 的 UI 缺陷:设置页「检查更新」中,新版本可用卡片的下载按钮被展开的 changelog 挤出屏幕、点不到
- 新增更新详情独立页面:点卡片上「查看更新详情」进入,可滚动查看完整 changelog
- 优化:广告招牌规则库 v4 → v5(118 条);紫玉米 0 → 8 Violation、蟹都汇 0 → 7 Warning,完整规则扩展清单见详情页
- 升级链路:旧版本客户端可正常升级到 v0.1.14(vision-latest.json 字段后向兼容 + 签名证书不变)
- `versionCode 13→14`,`versionName 0.1.13→0.1.14`

## v0.1.13 · 2026-08-20

- **ad_signage 规则升级:v4 (116 条) → v5 (118 条)**(基于导师视角 5 张测试集现场照片复核发现 3 类系统漏报):
  - **扩展 `ad_signage_art9_abs_top` 关键词 +6**(`ad_signage_art9_abs_top` 关键词已从 5 升至 11):`首个` / `首家` / `首选` / `领导品牌` / `领军品牌` / `首屈一指` — 不再只属于化妆品行业,任何行业的极限词都进通用 §9(三) 规则。
  - **扩展 `ad_signage_art28b_fake_data` 关键词 +15**(原 5 升至 20):`全国第一` / `全国销量第一` / `全国门店数量第一` / `全国连锁数量第一` / `行业第一` / `全网销量第一` / `市场占有率领先` / `销量遥遥领先` / `全国第一品牌` + 6 个 OCR 倒序复合形式(`销量全国第一` / `门店数量全国第一` / `连锁门店数量全国第一` / `门店数量第一` / `连锁数量全国第一` / `门店数量行业第一`) — 不再只属于农药 / 兽药行业,任何行业的虚假销量 / 排名数据都进通用 §28(二) 规则。
  - **调整类目归属**:`ad_signage_pesticide_art6_endorsement` / `ad_signage_veterinary_art7_endorsement` 移除 `全国第一`(挪到通用 `art28b_fake_data`),避免类目误归(蟹都汇礼券 "全国第一" 不再被归为农药 / 兽药)。
  - **新增 2 条 signage 普通食品医疗宣传规则**(类目 `signage` / 严重度 `Violation`):
    - `ad_signage_signage_food_function_claim`(38 kw):保健功能宣称 — `增强免疫` / `调节免疫` / `提高免疫力` / `调节血糖` / `控糖稳血糖` / `降血糖` / `稳血糖` / `降血压` / `稳血压` / `调节血脂` / `保护心血管` / `软化血管` / `清理血管` / `保护视力` / `保护眼睛` / `护眼` / `护双眼` / `促进消化` / `调理肠胃` / `修复胃黏膜` / `养胃` / `护胃` / `调节内分泌` / `排毒` / `排毒养颜` / `抗衰老` / `延缓衰老` / `降三高` / `调节三高` / `降糖稳糖` 等。
    - `ad_signage_signage_food_disease_target`(20 kw):疾病指向 — `糖尿病患者` / `糖尿病人的` / `高血压患者` / `癌症病人` / `肿瘤病人` / `冠心病患者` / `心脑血管病人` / `关节炎患者` / `骨质疏松患者` / `便秘患者` / `痔疮患者` / `前列腺患者` / `男性健康` / `妇科疾病` / `妇科炎症` / `白癜风` / `牛皮癣` / `抗癌` / `防癌` / `抗癌防癌`。
    - 法规依据:《广告法》第十七条 + 第十八条 + 《食品标识监督管理办法》— 普通食品 / 保健食品广告不得涉及疾病预防、治疗功能,不得作保健功能宣称。
- **5 张测试集现场照片回归命中数**(v4 → v5):
  - 紫玉米 0 → **8 Violation hit**(`food_function_claim` × 7 + `food_disease_target` × 1)
  - 蟹都汇 0 → **7 Warning hit**(`art28b_fake_data` × 4 + `art9_abs_top` × 2 + `art9_edu_abs` × 1)
  - 杜蕾斯 0 → **2 Warning hit**(`art9_abs_top` × 1 + `cosmetic_art9_abs_extended` × 1)
  - 玉米种子 0 → 0(OCR 无标准违规短语;扩展需新增农业投入品域规则)
  - 东郊到家 0 → 0(OCR 无标准违规短语;扩展需新增上门服务域规则)
- **新增 `AdSignageMentorFiveImageRegressionTest`**:5 张现场采集照片的 fixture OCR 转写 + 导师硬期望断言(每张图必命中 ruleId + 必命中 matchedText + 必命中 severity);与 `AdSignageRuleMatcherTest` 9 个 v5 用例协同,覆盖 v5 关键词扩展、2 条新规则、规则间 dedupe、不与现有 `disease_prevention` shadow、跨 category 共存。
- **泛化保证**:本次 v5 关键词扩展均针对同类真实广告常见写法(不针对测试图片);后续类似广告图片(其他食品 / 保健食品 / 其他行业极限词 / 其他行业虚假排名)将自动命中。
- 单元测试全绿(338 条),`testDebugUnitTest -PmodelProfile=shell` 路径不变。
- `versionCode 12→13`, `versionName 0.1.12→0.1.13`。

## v0.1.12 · 2026-08-20

- **修复 v0.1.11 的"桌面 A/B ≠ 真机行为"隐患**(SDK 反编译确认):
  - v0.1.11 `PaddleOcrEngine` 传 `PaddleOCRConfig()`(SDK v3.7.0 默认值 = `detLimitSideLen=64, detLimitType="min", detThresh=0.3, detBoxThresh=0.6, detUnclipRatio=1.5, recScoreThresh=0.0, recBatchSize=1`),与 v6 模型卡(960/max/0.2/0.45/1.4)不一致,会吃掉一部分 v6 小字召回优势;桌面 A/B 是 960/max 路径,但真机 SDK 走 64/min + 2048 px 全分辨率 BitmapLoader 输出,两套 pipeline 行为差 4.5 倍像素量。
  - v0.1.12 显式对齐 v6 模型卡:`PaddleOCRConfig(detLimitSideLen=960, detLimitType="max", detThresh=0.2f, detBoxThresh=0.45f, detUnclipRatio=1.4f)`。
  - 顺手把 `recScoreThresh` 从 0.0 提到 0.5(v6 实测平均置信 0.882,0.5 过滤低置信噪声但不丢真实文字);`recBatchSize` 从 1 提到 6(行宽差异大时 padding waste 较 8 小,1 vs 6 实际提速待真机验证,已在 smoke 记录里标注为 Phase 2 项)。
- **修复 `BitmapLoader.sampleSize` 2049→1024 像素悬崖**:原算法 `while (longest/sample > maxEdge) sample *= 2` 是 ceiling-based,2048 px 图保持原分辨率,2049 px 图直接砍到 ~1024 px(50% 信息丢失);4096 px → 2048 px,4097 px → 1024 px(同样 50% 跳变)。改为 floor-based:2048 / 2049 都保持 inSampleSize=1,4096 / 4097 都保持 inSampleSize=2,bitmap 偶尔略超 maxEdge(最多 +1 px 量级)但从不悬崖。`sampleSize` 仍为准确的 box 坐标映射因子。
- **文档修正**:`docs/smoke/2026-08-20-icevision-v6-upgrade.md` 中"DetResizeForTest: null # v6 = limit_side=960 / limit_type=max(SDK 内部默认)"是错的(实际 SDK 不读 det yml),已改为"SDK 内部默认 — 实际不读此 yml"+ v0.1.12 修复说明;`det/inference.yml` 标注为"死资产"(SDK 反编译确认 `models/det/inference.yml` 字符串不在 classes.jar 任何位置)。
- `versionCode 11→12`, `versionName 0.1.11→0.1.12`。
- 单元测试沿用(v0.1.11 全 317 条绿),预期全绿;真机 instrumentation A/B 是下一阶段门控项,当前未引入。
- 真机验证缺口(2026-08-20 当前未跑):`recBatchSize=6` 实际加速;`detLimitSideLen=960` vs `1536` vs 全分辨率的延迟-检出取舍;`recScoreThresh=0.5` 是否误杀真实文字 — 这些都依赖标注集或真机 log,Phase 2 + 桌面 A/B 工具 `D:\tmp\ocr_compare\` 复用可降低工作量。

## v0.1.11 · 2026-08-20

- **OCR 模型升级:PP-OCRv5_mobile → PP-OCRv6_small**(基于 4 张实拍广告招牌 A/B 实测:见 [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](docs/knowledge/ppocrv6_vs_v5_a_b_test.md)):
  - **检出文本行数 +12%**(101 → 113 行 / 4 张合计);**平均置信度 +5.4%**(0.837 → 0.882);**单图平均耗时 −10%**(1.88 s → 1.70 s)。
  - **关键胜负手**:蟹都汇"大闸蟹连锁门店数量全国第一"v5 误识为"大蟹年量全国谢"导致漏报;v6 完整检出"全国第一",并经 AC 自动机触发广告法 §9 绝对化用语 4 条规则联触发(art9_abs_top / art9_edu_abs / pesticide_art6 / veterinary_art7);**AdSignage 规则命中数:1 → 5(5×)**。
  - 模型文件:`det/inference.onnx` 4.83 MB → 9.88 MB(+5.05 MB);`rec/inference.onnx` 16.53 MB → 21.16 MB(+4.63 MB);rec 字典 6623 → 18708 条(PaddleOCR 官方 v6 multilingual 大字典)。
  - 代码层无任何改动:`PaddleOcrEngine.kt` 全部参数通过 yml 读入(asset path 之外无 dict/image_shape hardcode);`app/build.gradle.kts` `ice_ocr_rules` profile 路径对 v5/v6 完全透明;`tools/download-ppocr-models.sh` 默认 variant 切换即可。v5 模型文件已备份至 `%TEMP%/ppocr_v5_backup/` 供回滚。
  - `tools/download-ppocr-models.sh` 默认参数 `pp-ocrv5_mobile` → `pp-ocrv6_small`(命令行显式传 `pp-ocrv5_mobile` 仍可一键回滚)。
  - `app/src/main/assets/models/{det,rec}/inference.{onnx,yml}` 5 个文件随发布 APK 体积约 +9.7 MB(解包 + 压缩后);native libs(jniLibs)未变。
- **决策依据 + 局限性**(写在 [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](docs/knowledge/ppocrv6_vs_v5_a_b_test.md) 顶部,供后续维护者复核):测试集 4 张图不构成完整评测集,差异是趋势性的 ±10% noise 范围,但官方 PaddleOCR 公开 benchmark 与 v6_small 中文场景精度提升已与本次实测一致。后续如需做 ≥30 张标注集评测,已留 benchmark 脚本 `D:\tmp\ocr_compare\compare.py` + `D:\tmp\ocr_compare\match_rules.py` 可直接复用。
- `versionCode 10→11`, `versionName 0.1.10→0.1.11`。
- 单元测试全绿(代码层无变更,纯 asset 替换);release pipeline 待发版时走 `assembleRelease → generateVisionLatestJson → archiveVisionRelease → uploadVisionReleaseToGitea` 全套。

## v0.1.10 · 2026-08-19

- **产品方向调整**:UI 层只暴露「广告招牌」tab;「食品标识」tab 入口暂时对用户隐藏(原因与可复制模式边界见 CLAUDE.md 「产品方向(v0.1.10 起):广告招牌 单一焦点」段)。
- 实现层细节:
  - [RuleTabBar.kt](app/src/main/java/com/icespiritai/offline/ui/home/RuleTabBar.kt) 内部 `visibleTabs = listOf(RuleTab.AdSignage)`,`TabRow` 只渲染一项;`RuleTab.FoodLabeling` enum 项保留,顶部 KDoc 明示"恢复时把 `visibleTabs` 改回 `RuleTab.entries.toList()` 即可"。
  - 完整代码路径保留:`FoodLabelRuleMatcher` + `FoodLabelRuleLoader` + ViewModel `matcherFor(tab)` 双分支 + `food_label_rules.json`(66 条 / v4)+ `知识库/食品标识/` 9 份 markdown + `FoodLabelRuleMatcherTest` + `IceSpiritVisionViewModelTabTest` 双 tab 路由断言均完整保留,后续广告招牌模式打磨成熟后可直接复用模板启用食品标识 tab。
  - `app/src/main/res/values/strings.xml` 的 `tab_food_label="食品标识"` 字符串保留(代码 enum 引用)。
  - `知识库/` 双域 markdown 完整保留;广告业务目录是当前打磨中的成熟参考,食品标识目录是待后续套用的对象。
- 打磨策略:ad_signage_rules.json 关键词命中 / 严重度分级 / category 显示 / 证据包导出全部以"可复制到下一个视觉判别域"为标尺优化,达标后再以同样模式启用 FoodLabeling tab。
- `versionCode 9→10`, `versionName 0.1.9→0.1.10`。
- 单元测试全绿(`./gradlew.bat testDebugUnitTest -PmodelProfile=shell` 路径不变);`IceSpiritVisionViewModelTabTest` 双 tab 路由断言保留,持续覆盖代码路径不被静默损坏。

## v0.1.9 · 2026-08-19

- 修复 v0.1.8 引入的 6 处文档/规则准确性问题(全部为 OCR 命中后援引法规条文不应误导):
  1. `ad_signage_art11_fake_patent`:`lawText` 拼接伪条文(严重)。原条文把专利条款塞进第十一条 + 杜撰"应当标明许可批准文号"句 — 实际**现行《广告法》第十二条**才是专利条款(三款:标明专利号和专利种类 / 不得谎称取得 / 禁止未授予 + 已终止 + 已撤销 + 无效专利作广告);**第十一条**是行政许可内容相符 + 引证内容(数据 / 统计资料 / 调查结果 / 文摘 / 引用语)。`regulation` 字段同步改为"《广告法》第十二条 + 第五十九条第一款第(三)项",删除"第十一条"误导引用;`lawText` 改写为现行第十二条三款原文 + 第五十九条第一款第(三)项罚则,并增注:专利违法对应 §59(1)(三) 不是 §11。
  2. `food_art28_function_claim_unauthorized`:`lawText` 拼接《食品安全法》§75 / §76 / §78 错引(严重)。实际**《食品安全法》§75 = 保健功能声称应科学依据 + 不得对人体产生急性、亚急性或慢性危害**(不涉及疾病预防治疗);**§76 = 保健食品注册与备案**(不是"其他食品不得声称保健功能");**§78 = 保健食品的标签、说明书不得涉及疾病预防、治疗功能**(但这条对应的是保健食品而非其他食品);**"保健食品之外的其他食品不得声称具有保健功能"** 实际在《食品安全法实施条例》第三十八条,不是《食品安全法》。`regulation` 改为"食品标识监督管理办法 第七条第二款 + 第四十三条(依食品安全法实施条例第六十八条处罚) + 食品安全法实施条例 第三十八条 + GB 7718-2011 §3.6";`lawText` 删除 §75 / §76 / §78 错引,改为实施条例 §38 原文 + 监督办法 §7(2) + §43 + GB 7718-2011 §3.6 + 注:§75 / §76 与本规则主题"其他食品不得声称保健功能"无直接对应关系。
  3. `food_health_claim_unapproved` `lawText` 错别字 "虎底条款" → "兜底条款"(食品标识监督管理办法第七条第一款第五项为兜底条款,本身无明示罚则)。
  4. `food_gb7718_art4_1_4_allergen_disclose` 规则 id 重命名 → `food_gb7718_art4_4_3_allergen_disclose`(原 id 仍用 §4.1.4 配料的定量标示,与 rule body 已对齐的 §4.4.3 致敏原推荐性标示编号不一致;现按内容正确编号命名)。同步更新 `知识库/食品标识/GB_7718-2025_致敏原强制标示.md` L69 表格 + `app/src/test/.../FoodLabelRuleMatcherTest.kt` L522 测试。
  5. `知识库/广告业务/中华人民共和国广告法.md` `## 原文` L75 / L81 编号错位修正(原档 1994 年旧编号标"第十一条"填的是 2015 / 2021 现行专利条款,应改为第十二条;原档"第十二条"占位填的是 2015 / 2021 现行第十一条行政许可 + 引证内容,应改为第十一条),`## 适用判别要点` L469 同步改"广告法 第十一条"为"广告法 第十二条"并补"第五十九条第一款第(三)项,对广告主处十万元以下的罚款"罚则说明;`§19 / §20` 经核对实际 L129 / L131 已是现行正确编号,不存在 agent 历史批注描述的"互换"问题,本批未动。
  6. **食品标识域 知识库 10 处 stub 全部回填**(`食品标识管理规定.md` 3 处 + `GB_7718-2011_预包装食品标签通则.md` 5 处 + `GB_13432-2013_预包装特殊膳食用食品标签通则.md` 2 处)。`食品标识管理规定.md` 全文 5 章 42 条逐字回填(国务院公报 2008 年第 14 号刊载页 + 2009 总局令第 123 号修订合并版),头部加注 2027-03-16 起被《食品标识监督管理办法》替代。`GB_7718-2011_预包装食品标签通则.md` §4.3 标示豁免 + §4.4 推荐标示(含 §4.4.3 致敏物质 a-h 完整清单)+ 附录 A / B / C 逐字回填(NHC 官方 PDF)。`GB_13432-2013_预包装特殊膳食用食品标签通则.md` §4.6 包装最大表面面积 < 10 cm² 豁免条款逐字回填(食品伙伴网法规中心比对解读引文 + 多源交叉印证)。`curl -sL -A "Mozilla/5.0"` 绕过 WebFetch 域验证阻断策略实证可用。
- 单元测试 36 + 新增测试 0 条,沿用现有测试覆盖:`scan_gb7718Art4Allergen_firesOn含花生` 因 id 改名需同步更新 rule id 字段,`scan_art28FunctionClaimUnauthorized_firesOn辅助降血脂` 因 regulation 字段引用已重写为现行 §7(2) + §43 + 实施条例 §38,同步更新。
- `versionCode 8→9`, `versionName 0.1.8→0.1.9`。
- 无新增功能,无对外 UI 改动。
- **(2026-08-19 +1 追订)** `知识库/食品标识/食品标识管理规定.md` `## 适用判别要点` 章节 off-by-one 修正:判别要点 §30-§37 原误对齐到原文 §31-§38(全部 -1 错位),§26 误改为"兜底引用"占位(原文 §26 实际是"未附加标识 1 万元以下"的具体罚款条款),§28 漏掉"第九条"(日期与保质期),§36(违反第二十二条第一款多件混合独立包装)缺失,§37 误指 §38(执法监管条款)。本批按原文 153-179 行 §26-§38 逐条重写判别要点章节,off-by-one 全部消除;关键词集合保持向下兼容(规则引擎 JSON 沿用原关键词未改);`## 数据采集说明(2026-08-19)` 末段改写为修正批注。版本号不变,KB 文档微调不单独 bump。
- **(2026-08-19 +2 追订)** 4 处微小瑕疵清理:
  1. `ad_signage_art11_fake_patent` 规则 id 重命名 → `ad_signage_art12_fake_patent`(原 id `art11` 与实际引用的现行《广告法》第十二条编号不一致);同步更新 `app/src/test/.../AdSignageRuleMatcherTest.kt` L165 测试。
  2. `ad_signage_art12_fake_patent` `lawText` 引用《广告法》第五十九条第一款第(三)项时,"由市场监督管理部门责令改正" → "由市场监督管理部门**责令停止发布广告**"(法条原文用词)。
  3. `知识库/广告业务/中华人民共和国广告法.md` 末尾 `## 数据采集说明(2026-08-19)` 第 3 条 `结构性遗留` L515 / L516 过时批注改写:明示 v0.1.8 commit `0fe7902` 阶段状态 + v0.1.9 commit `6c45879` 阶段修正结果(§11 / §12 编号已对齐,§19 / §20 经 grep 复核不存在互换)。
  4. `food_art28_function_claim_unauthorized` `lawText` 末尾"本批已删除先前错引"过程性元注释清理,改为"第七十五条 / 第十六条 / 第七十八条均针对保健食品本身,与本规则主题'其他食品不得声称保健功能'无直接对应关系"。
- 版本号保持 0.1.9(规则 id 重命名不影响规则匹配;KB / lawText 措辞微调属文档层调整)。

## v0.1.8 · 2026-08-19

- 修复 v0.1.7 引入的 7 处文档/规则准确性问题(全部为 OCR 命中后援引法规条文不应误导):
  1. `food_gb7718_art4_1_4_allergen_disclose`:清除 "**GB 7718-2025 §5 致敏原十一大类强制**" 误判,该强制性依然为原八大类(麸质谷物 / 甲壳类 / 鱼类 / 蛋类 / 花生 / 大豆 / 乳 / 坚果);芹菜 / 芥末 / 芝麻 / 二氧化硫及亚硫酸盐为 GB 7718-2025 §5 推荐性标识非强制,OCR 命中"含芝麻""含芹菜"等不应误判为强制标示缺失。同时修正 `regulation` 字段:GB 7718-2011 §4.1.4 实为"配料的定量标示"(与致敏原无关),致敏原原文在 §4.4.3。
  2. `food_health_claim_unapproved`:`regulation` 字段 `第四十四条` → `第四十三条`(依食品安全法实施条例第 68 条处罚);补充注:第七条第一款第五项为兜底条款本身无明示罚则,通过 §7 第二款 + §43 处理。
  3. `food_art28_function_claim_unauthorized`:`regulation` 字段 `第二十八条 + 第四十四条` → 主体改引 **食品安全法 §75 / §76 + 食品标识监督管理办法 §7 第二款 + 第四十三条 + GB 7718-2011 §3.6**(第二十八条实际是"保健食品名称标注格式",非本规则主题);删除 `第二十八条` 误导引用。
  4. `food_gb13432_infant_breastmilk_substitute`:`lawText` 食品安全法 §81 句原误将"特殊医学用途婴儿配方食品" 拼入 §81 主体(应属 §80),现拆分清楚:§81 主体是 0~6 月龄婴幼儿配方乳粉 / 婴幼儿配方液态乳配方注册 + 不得分装;同时新增 **广告法 §20** 母乳代用品禁令引用。
  5. `app/prepare-ocr-rules.gradle.kts` 头注释修复:prompt 仍写「10 golden rules / 6 golden rules」→ 「116 rules / 66 rules / v4」。
  6. `知识库/食品标识/GB_7718-2025_致敏原强制标示.md` 整份重写:删去"十一大类强制"主线,改为「8 类强制 + 4 类推荐(芹菜 / 芥末 / 芝麻 / 二氧化硫及亚硫酸盐)」;`food_gb7718_art4_1_4_allergen_disclose` 与 JSON 主体一致;增"关键正误"段,明示先前误判。
  7. `知识库/食品标识/README.md` 第 17 行 GB 7718-2025 条目同步为"**强制清单仍为八大类**,芹菜 / 芥末 / 芝麻 / 亚硫酸盐为推荐性非强制;替换 GB 7718-2011 §4.4.3 推荐性标示"。
- 顺带:**广告业务知识库 43 处 stub 全部回填**(`中华人民共和国广告法.md` 18 处 + `广告管理条例.md` 2 处 + `药品医疗器械保健食品特殊医学用途配方食品婴幼儿配方乳粉广告管理办法.md` 9 处 + `房地产广告发布规定.md` 1 处 + `医疗广告管理办法.md` 0 处新增 + `城市市容和环境卫生管理条例.md` 5 处 + `农药广告审查发布规定.md` 2 处),逐条引用 flk.npc.gov.cn / gov.cn 国务院公报 / 食品伙伴网 原文校对;Agent 实证可用 `curl -sL` 绕过 WebFetch 域验证阻断。
- `app/src/main/assets/rules/food_label_rules.json` 总条数保持 66 条(仅 lawText / regulation 字段修正,无新增/删除规则)。
- `versionCode 7→8`, `versionName 0.1.7→0.1.8`。
- 无新增功能,无对外 UI 改动。

## v0.1.7 · 2026-08-19

- 修复 7 处文档/规则准确性问题(OCR 命中后援引的法律条文不再误导):
  1. `ad_signage_rules.json` `ad_signage_art10_minor` 删除 2 句与广告法 §10 无关的伪条文(原文仅一句「广告不得损害未成年人和残疾人的身心健康」);`lawText` 现仅保留 1 句《广告法》第十条 + 1 句《食品标识监督管理办法》第八条。
  2. `food_label_rules.json` 将 `food_infant_formula_unregistered`(原错误地将食品安全法 §80 特殊医学用途与 §81 婴幼儿配方乳粉合并)拆为 2 条:`food_fsmp_register_required` (§80 + 食品标识监督管理办法 §31) 与 `food_infant_formula_milk_register` (§81 + 总局令第 80 号《婴幼儿配方乳粉产品配方注册管理办法》);`food_label_rules.json` v3 → v4,总 65 → 66 条。
  3. `food_art30_infant_claim` 移除「第四十一条(依食品安全法第 125 条第 1 款处罚)」引用(§41 处罚列表不含 §30,属错误归责);改为「(依 GB 13432-2013 §3.c 联合落地)」+ lawText 内显式注:第三十条未设明示罚则,涉嫌虚假/引人误解宣传并入第七条第一款第二项查处。
  4. `food_health_claim_unapproved` + `food_art28_function_claim_unauthorized` 两处曾引用的《食品标识管理规定》§19 为整档 stub [未检索到全文] 的占位引用,统一改为《食品标识监督管理办法》第七条第一款第五项+第二款 + 第四十四条(已落地 KB);`food_art28_function_claim_unauthorized` 同步把 §41 改为 §44(§28 实际落在 §44 罚则列表,§41 不含 §28)。
  5. `food_gb13432_infant_breastmilk_substitute` regulation 字段原写「卫生部令第 1 号」修正为「卫妇发〔1995〕第 5 号」并标注 2017-12-13 已废止 + 现行替代文件(食药监食监一〔2013〕214 号);`food_gb7718_art4_1_4_allergen_disclose` regulation 原错误引用 GB 31644-2018(实际是《食品安全国家标准 食品添加剂 天然胡萝卜素》),改为 GB/T 23779-2009(2025-03-28 废止) + GB 7718-2025 §5(致敏原强制标示,2027-03-16 施行)联合引用。
  6. `知识库/广告业务/README.md` 第 11 行《中华人民共和国广告法》条目从「2023年修正」改为「2018 第一次修正 / 2021 第二次修正」(2023 年系误植,实际两轮修正分别在 2018-10 与 2021-04)。
  7. `CLAUDE.md` + `app/build.gradle.kts` + `知识库/广告业务/医疗广告管理办法.md` 三处过时引用 `ad_law_rules.json` / 「10 条 golden rules」全部更新为 `ad_signage_rules.json` / `food_label_rules.json` + 116/66 条 + AdSignageRuleMatcher + FoodLabelRuleMatcher。
- 同步知识库 4 处修正:`知识库/广告业务/药品医疗器械保健食品特殊医学用途配方食品婴幼儿配方乳粉广告管理办法.md` header 由「管理办法(拟修订名)+ 婴幼儿配方乳粉未并入 + 总局令第 21 号(2019-12-24 / 2020-03-01) + 暂行办法」替代「总局令第 60 号(2023 拟订)未检索到」占位文本;新增 3 份 markdown:`母乳代用品销售管理办法.md`(卫妇发〔1995〕第 5 号 + 2017-12-13 废止说明)/ `GB_7718-2025_致敏原强制标示.md`(国家卫健委 + SAMR 联合发布,十一大类清单)/ `婴幼儿配方乳粉产品配方注册管理办法.md`(总局令第 80 号 现行版,替代第 26 号令);`知识库/食品标识/README.md` 索引同步追加 3 条。
- `AssetRuleLoaderTest.load_parsesActualBundledFoodLabelAssetShape` 同步:`assertEquals(3, ...)` → `assertEquals(4, ...)`,最低规则数断言同步上调。
- 无新增功能,无对外 UI 改动。

## v0.1.6 · 2026-08-19

- 「广告招牌」域规则库 v3 → v4:再扩 31 条,总 116 条(85 既有 v3 + 31 v4)。
- 覆盖 3 部新增法规:①《化妆品监督管理条例》(国务院令第727号,2021-01-01 施行)12 条 — §23 第(一)-(七)项必载内容缺失(特殊化妆品注册证号/普通化妆品备案号/注册人名称地址/生产许可证号/全成分/净含量/使用期限+使用方法+安全警示)/ §17 特殊化妆品分类(染发/烫发/祛斑美白/防晒/防脱/新功效)/ §20 功效宣称科学依据 / §25 第二款 医疗作用明示暗示 + 第一款 虚假或引人误解;②《关于进一步规范金融营销宣传行为的通知》(银发〔2019〕316 号,央行等四部门,2020-01-25 施行)10 条 — 第三条第(一)-(七)项"八个不得"完整覆盖(超范围/欺诈+保证性承诺/利用监管机构名义/损害知情权/损害公平竞争/利用政府公信力/利用互联网不当营销/违规发送营销信息)+ 强化版 §25(二)代言禁止 + §9(三)+§25 投资广告绝对化用语;③《互联网广告管理办法》(SAMR 令第72号,2023-05-01 施行)9 条 — §6 可识别性 + §6(2) 软文/测评/分享需显著标明"广告" / §21 竞价排名 / §15 一键关闭 + 弹窗广告 / §9(2) 健康养生变相发布医疗药品广告 / §8(1) 互联网发布烟草电子烟 + 处方药 / §7 医疗药品医疗器械等事前审查 / §22 算法推荐告知义务。
- 「食品标识」域规则库 v2 → v3:再扩 29 条,总 65 条(36 既有 v2 + 29 v3)。
- 覆盖 3 部法规细化落地:①《GB 28050-2011 预包装食品营养标签通则》12 条 — §4.1 强制标示内容 + §4.4 反式脂肪酸强制标示 + §5.2 含糖声称(无糖/低糖)+ §5.3 脂肪声称(低脂/脱脂)+ §5.4 钠声称(低盐/低钠/无盐)+ §5.5 膳食纤维声称 + §5.6 矿物质声称(钙/铁/锌)+ §5.7 蛋白质声称 + §5.8 比较声称(减少/增加)+ §6 营养成分功能声称(有助于/促进/补充等标准用语)+ §3.2 中外文对照 + §3.6 最小销售单元;②《GB 13432-2013 预包装特殊膳食用食品标签》6 条 — §3.a 不得涉及疾病预防治疗 + §3.c 0-6 月龄婴儿配方不得做含量声称和功能声称 + §4.2 食品名称合规 + §4.3 能量与营养成分标示 + §4.4 适用人群与不适宜人群必标 + §3.c + 《母乳代用品销售管理办法》联合落地婴幼儿配方"代替母乳"宣称禁令;③《GB 7718-2011 预包装食品标签通则》8 条 — §4.1.4.1 配料表按递减顺序 + §4.1.3 食品添加剂具体名称 + §4.1.4 过敏原标识 + §4.1.7 生产批号 + §4.1.6 进口食品原产国/进口商 + §4.1.5 贮存条件 + §4.1.6.2 生产日期与保质期格式 + §4.1.5.3 净含量;另有 3 条食品标识监督管理办法 + 食品安全法细化(质量等级/特殊人群/中外文翻译/未经注册的功能声称)。
- 新增 2 个 AdSignageCategory 中文 label:「化妆品广告」+「互联网广告」(原 11 个键 → 13 个键)。
- 新增 62 条单元测试覆盖每条新规则的关键词命中 + 1 条多规则联触发用例。
- 知识库增 3 份新 markdown + 广告业务 README 索引同步:广告业务目录下现有 15 份法规(原 12 份 + 化妆品监督管理条例/互联网广告管理办法/关于进一步规范金融营销宣传行为的通知)。
- 严重度分布(广告招牌 116 条):42 Violation + 60 Warning + 14 Info;严重度分布(食品标识 65 条):29 Violation + 25 Warning + 11 Info。

## v0.1.5 · 2026-08-19

- 「广告招牌」域规则库 v2 → v3:再扩 43 条,总 85 条(10 既有 v1 + 32 v2 + 43 v3)。
- 覆盖 4 部新增法规:①《医疗器械广告审查发布标准》(国家工商总局/卫生部/食药监局令第40号,2009)8 条 — 个人自用必标提示语 / 禁忌必标 / 必载生产企业+注册证号+广告批准文号 / 7 类禁止内容(功效断言/治愈率/比较/科研机构推荐/无效退款承诺);②《农药广告审查发布规定》(国家工商总局令第81号 + SAMR 令第31号修订,2015/2020)10 条 — 未经批准不得发布 / 不得超出登记范围 / 5 类禁止内容 / 贬低同类 / 综合性评价 / 承诺禁止 / 批准文号必标;③《兽药广告审查发布规定》(国家工商总局令第82号 + SAMR 令第31号修订,2015/2020)10 条 — 4 类不得发布 / 5 类禁止内容 / 不得贬低 / 不得绝对化("最高技术"/"包治百病")/ 综合性评价 / 承诺禁止 / 批准文号必标;④《城市市容和环境卫生管理条例》(国务院令第101号,1992/2017/2020)结合《广告法》§32 落地 8 条户外广告细化 — 国家机关/学校医院/交通设施/楼顶/文物古迹/市政设施/风景名胜区/机场净空区域均禁设。
- 同步补全广告法 §21(农药兽药饲料添加剂广告) / §29(互联网广告可识别性 + 一键关闭) / §30(广告主义务与资质) / §32(户外广告设置禁区) / §44(互联网信息服务提供者审查义务) / §46(发布前审查义务) 共 4 条补漏规则 + 1 条通用法规引致规则。
- 新增 2 个 AdSignageCategory 中文 label:「农药类广告」+「兽药类广告」(原 9 个键 → 11 个键)。
- 新增 32 条单元测试覆盖每条新规则的关键词命中 + 1 条多规则联触发用例。
- 知识库增 4 份新 markdown + 1 份扩展 + README 索引同步:广告业务目录下现有 12 份法规(广告法/广告管理条例/城市市容和环境卫生管理条例/户外广告登记管理规定/房地产广告发布规定/医疗广告管理办法/医疗器械广告审查发布标准/药品医疗器械保健食品特殊医学用途配方食品广告审查管理暂行办法/农药广告审查发布规定/兽药广告审查发布规定/烟草广告管理暂行办法/城市市容和环境卫生管理条例)。
- 严重度分布(总 85 条):20 Violation(硬性禁令) + 58 Warning(程序/格式/必载) + 7 Info(瑕疵提示)。

## v0.1.4 · 2026-08-19

- 「广告招牌」域规则库扩充 32 条:基于知识库《广告法》《医疗广告管理办法》《房地产广告发布规定》《户外广告登记管理规定》《药品、医疗器械、保健食品、特殊医学用途配方食品广告审查管理暂行办法》《烟草广告管理暂行办法》《广告管理条例》全 7 份现行法规落地 OCR 触发关键词,覆盖广告法 §9 5 项绝对禁止(国旗/国徽/国家机关/赌博迷信淫秽)、§11 假专利、§17 跨界疾病治疗、§20 母乳代用品禁止、§22 烟草广告禁播媒介、§23 酒类广告禁驾 + 暗示功效、§25 投资回报广告禁代言、§26 房地产广告 3 项禁止;医疗广告 §6/§7/§11/§13 八项限定 + 7 类禁 + 必载审查证明 + 新闻形式禁止;房地产广告 §4/§7/§8 面积必须为建筑面积 + 必载预售许可证号 + 禁止迷信;户外广告 §4/§10/§14 未登记不得发布 + 必载登记证号 + 内容真实合法;保健食品/非处方药广告必载警示语与蓝帽子;每条 `regulation` 字段串联到对应处罚条款(§42/§43/§55/§57/§58/§59)。
- 规则 JSON 版本号 1 → 2;现有规则继续保留(增量模式,不覆盖既有 10 条);新增 26 条单元测试覆盖新规则的关键词命中。
- 严重度分布:12 Violation(硬性禁令) + 26 Warning(程序/格式) + 4 Info(瑕疵提示)。

## v0.1.3 · 2026-08-19

- 「食品标识」域规则库扩充 30 条:基于《食品标识监督管理办法》(市场监管总局令第 100 号,2027-03-16 起施行)全 54 条落地 OCR 触发关键词,覆盖第七条 5 项禁止内容(疾病治疗 / 绝对化 / 封建迷信 / 特供专供 / 保健功能)、第八条未成年人标称、第十六条食品名称 3 类情形、第十八条分装标注、第十九条计量称重、第二十一至二十三条强制标示内容、第二十六至二十八条保健食品、第二十八条保健食品名称、第三十条婴幼儿配方、第三十三至三十六条销售标示要求、第三十九条 5 类瑕疵认定 + 第四十一至四十九条对应处罚区间。
- 知识库《食品标识监督管理办法》全文 7 章 54 条已落地(SAMR lawId `4818998214a5419f983c177727527282` + OCR + pymupdf 文本层双重核对);配套下载脚本 `tools/download-samr-laws.py` 已落地,后续 PM 抓新规章可走 `python tools/download-samr-laws.py fetch <lawId> --dst 知识库/<域>/`。
- 规则 JSON 版本号 1 → 2;现有规则继续保留(增量模式,不覆盖既有 6 条)。

## v0.1.2 · 2026-08-18

- 「拍照」按钮接入相机:点击直接拍照,不再跳到相册选图(首次会请求相机权限)
- 移除了首页左下角与预览区重复的「请对正图片后点击拍照」提示,只保留预览区居中那一处
- 首页顶栏「冰灵锐目」标题居中显示

## v0.1.1 · 2026-08-18

- 设置中新增「查看更新日志」入口:从这里可以查看每个版本的功能调整与修复
- 设置项重排:「外观」提前,「检查更新」放到「外观」下方,逻辑更连贯

## v0.1.0 · 2026-08-14

- Phase 1 上线:PaddleOCR v3.7.0 + 广告法违规识别规则库(10 条 golden rules)
- 完整工作流:选图/拍照 → OCR → 规则扫描 → 取证包导出
- 广告法违规按严重等级(信息/警告/违规)分类展示,支持展开查看法条原文
- 离线取证实景:整个识别与判定流程完全本地,不依赖云端
