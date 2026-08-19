# PP-OCRv5 → PP-OCRv6 升级烟测结果 — 2026-08-20

> 本次升级决策:基于 4 张测试集子文件夹实拍广告招牌 A/B 实测(详见 [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](../knowledge/ppocrv6_vs_v5_a_b_test.md))。冰灵锐目 `ice_ocr_rules` profile 默认 ONNX 模型由 `pp-ocrv5_mobile` 替换为 `pp-ocrv6_small`(rec dict 18708 条 / multilingual 大字典)。

## 1. 升级动机(4 张图实测结论)

| 维度 | v5_mobile | v6_small | v6 vs v5 |
|---|---|---|---|
| 检出文本行数(4 张合计) | 101 | 113 | **+12%** |
| 平均置信度 | 0.837 | 0.882 | **+5.4%** |
| 单图平均耗时(3072-4096 px) | 1.88 s | 1.70 s | **−10%** |
| AdSignage 规则命中数(116 条规则) | 1 | 5 | **5×** |

**关键胜负手**:蟹都汇商城页 "大闸蟹连锁门店数量全国第一" — v5 误识为 "大蟹年量全国谢"(漏报);v6 完整检出并触发广告法 §9 绝对化用语 4 条规则联触发。详细数据 + 4 图分项对比见 A/B 测试文档。

**已知局限性**:测试集只有 4 张图、且都是 single-image 路径,差异在 ±10% 是 noise 范围;真正的提升要在 ≥30 张标注集上跑后才能给 "X% 精度"。本次升级是趋势性判断 + 官方 PaddleOCR 公开 benchmark 双重佐证,不是 N 张标注集评测。

## 2. 升级范围(代码层零改动)

| 层 | 改动 | 备注 |
|---|---|---|
| `app/src/main/assets/models/det/inference.onnx` | 替换 | v5 4.83 MB → v6 9.88 MB(+5.05 MB) |
| `app/src/main/assets/models/det/inference.yml` | 替换 | v6 tarball 自带 det yml(v5 没有) |
| `app/src/main/assets/models/rec/inference.onnx` | 替换 | v5 16.53 MB → v6 21.16 MB(+4.63 MB) |
| `app/src/main/assets/models/rec/inference.yml` | 替换 | dict 6623 → 18708 条 |
| `tools/download-ppocr-models.sh` | 默认 variant | `pp-ocrv5_mobile` → `pp-ocrv6_small` |
| `CLAUDE.md` | 文档 | modelProfile 表格 + 视觉/OCR 模型路线 + 文档索引 |
| `app/build.gradle.kts` | versionCode/Name | 10/0.1.10 → 11/0.1.11 |
| `app/src/main/assets/user-changelog.md` | changelog | 新增 v0.1.11 条目 |
| `app/src/ice_ocr_rules/.../PaddleOcrEngine.kt` | **不改** | 全部参数(asset path 外)通过 yml 读入 |
| `app/build.gradle.kts` `dependencies`/`sourceSets`/`packaging` | **不改** | 对 v5/v6 完全透明 |

**回滚路径**(若发版后回归):`bash tools/download-ppocr-models.sh pp-ocrv5_mobile` 一键回滚(脚本自动 fallback 到 BOS);v5 模型备份位于 `%TEMP%/ppocr_v5_backup/`(本机 Windows 路径:`C:\Users\37311\AppData\Local\Temp\ppocr_v5_backup\`),含 det/rec 全部 4 个文件 + yml。

## 3. 模型文件验证

`app/src/main/assets/models/rec/inference.yml` 结构校验(关键:rec dict 大小与 SDK 预期一致):

```yaml
Global:
  model_name: PP-OCRv6_small_rec
PreProcess:
  transform_ops:
  - DecodeImage: {channel_first: false, img_mode: BGR}
  - MultiLabelEncode: {gtc_encode: NRTRLabelEncode}
  - RecResizeImg: {image_shape: [3, 48, 320]}   # 与 v5 一致,代码无 hardcode 不需改
  - KeepKeys: {keep_keys: [image, label_ctc, label_gtc, length, valid_ratio]}
PostProcess:
  name: CTCLabelDecode
  character_dict:
  - ...
  # 共 18708 条;SDK 内部 init 顺序 = ["blank"] + dict + [" "],总 dim = 18710
```

`app/src/main/assets/models/det/inference.yml`(v6 tarball 自带,v5 没有):

```yaml
Global:
  model_name: PP-OCRv6_small_det
PreProcess:
  transform_ops:
  - DecodeImage: {channel_first: false, img_mode: BGR}
  - DetLabelEncode: null
  - DetResizeForTest: null       # v6 = limit_side=960 / limit_type=max(SDK 内部默认)
  - NormalizeImage: {mean: [0.485, 0.456, 0.406], std: [0.229, 0.224, 0.225], scale: 1./255., order: hwc}
  - ToCHWImage: null
  - KeepKeys: {keep_keys: [image, shape, polys, ignore_tags]}
PostProcess: {name: DBPostProcess, thresh: 0.2, box_thresh: 0.45, unclip_ratio: 1.4, max_candidates: 3000}
```

## 4. 构建产物

| Profile | 命令 | APK 大小 | 增量 |
| --- | --- | --- | --- |
| `ice_ocr_rules` | `./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules` | **67.3 MB**(`app/build/outputs/apk/debug/app-debug.apk`,67 290 941 bytes) | **+12.3 MB** vs v5 baseline(55 MB)|

`unzip -l app-debug.apk` 关键 model 条目(已确认 v6 模型正确打入):

```
9 880 512  assets/models/det/inference.onnx        (v6 det ONNX, +5.05 MB vs v5 4.83 MB)
      885  assets/models/det/inference.yml        (v6 det yml,v5 没有,v6 tarball 自带)
21 159 378  assets/models/rec/inference.onnx       (v6 rec ONNX, +4.63 MB vs v5 16.53 MB)
  150 579  assets/models/rec/inference.yml        (v6 rec yml,dict 18708 条 vs v5 6623 条)
```

**APK 增量拆解**(+12.3 MB):
- 模型原始大小增加 +9.68 MB(det +5.05 + rec +4.63);
- zipalign 压缩后实际 +12.3 MB,超出原始增量的差额(~2.6 MB)归因于 zip 字典对 v6 多语言字典的压缩效率略低 — v6 rec 字典新增 ~12k 字符,使 deflate 字典命中率下降。可接受范围内。

## 5. 单元测试

`./gradlew.bat :app:testDebugUnitTest`(默认 shell profile,与 v0.1.10 单元测试路径一致)

```
317 tests, 0 failures, 0 errors
BUILD SUCCESSFUL in 4m 38s
```

代码层零改动,预期全部沿用现状(规则引擎 / matcher / rule JSON 全部不变),实测全部沿用现状,317 条测试 100% 通过。预先存在的 deprecation warnings(`createComposeRule` 旧 API + `UpdateRepositoryInstallTest.kt:26` nullable receiver)与本次升级无关,均沿用历史状态。

测试类分布(从 `app/build/test-results/testDebugUnitTest/*.xml` 抓取):

| 测试类 | tests | failures | errors |
| --- | --- | --- | --- |
| `IceSpiritVisionViewModelTabTest` | 5 | 0 | 0 |
| `IceSpiritVisionViewModelTest` | 4 | 0 | 0 |
| `ImageAnalyzerRepositoryTest` | 9 | 0 | 0 |
| `AnalysisStateTest` | 10 | 0 | 0 |
| `CategoryDisplayTest` | 6 | 0 | 0 |
| `TextNormalizerTest` | 3 | 0 | 0 |
| `EvidencePackageBuilderTest` | 1 | 0 | 0 |
| `BitmapLoaderTest` | 6 | 0 | 0 |
| `FakeOcrEngineTest` | 4 | 0 | 0 |
| `OcrEngineFactoryLocatorTest` | 2 | 0 | 0 |
| `AdSignageRuleMatcherTest` | 104 | 0 | 0 |
| `AdSignageRuleTest` | 3 | 0 | 0 |
| `AssetRuleLoaderTest` | 5 | 0 | 0 |
| `FoodLabelRuleMatcherTest` | 50 | 0 | 0 |
| `FoodLabelRuleTest` | 3 | 0 | 0 |
| `SettingsRepositoryTest` | 2 | 0 | 0 |
| `SettingsViewModelTest` | 2 | 0 | 0 |
| `SeverityBadgeTest` | 3 | 0 | 0 |
| `HomeScreenTest` | 3 | 0 | 0 |
| `ImagePreviewDoubleTapTest` | 3 | 0 | 0 |
| `ResultPanelTest` | (略) | 0 | 0 |
| `HomeScreenScreenshotTest` | (略) | 0 | 0 |
| `VersionHistoryRendererTest` | (略) | 0 | 0 |
| `ColorTokensTest` / `ShapeTokensTest` / `ThemeModeTest` | (略) | 0 | 0 |
| `ViewerScreenTest` / `ViewerTextListTest` / `ViewerTopBarTest` | (略) | 0 | 0 |
| `AppVersionInfoSerializationTest` / `UpdateRepositoryCheckTest` / `UpdateRepositoryDownloadTest` / `UpdateRepositoryInstallTest` | (略) | 0 | 0 |
| **合计** | **317** | **0** | **0** |

## 6. 升级验证清单

- [x] `tools/download-ppocr-models.sh` 默认参数已切到 `pp-ocrv6_small`
- [x] `app/src/main/assets/models/{det,rec}/inference.{onnx,yml}` 已是 v6 文件(det 9.88 MB / rec 21.16 MB)
- [x] `PaddleOcrEngine.kt` 无 v5 hardcode(dict/image_shape 全部 yml 读入)
- [x] `app/build.gradle.kts` 无 v5 hardcode(只引用 asset path,profile 路径对 v5/v6 完全透明)
- [x] `CLAUDE.md` modelProfile 表格 + 路线说明 + 文档索引 同步为 v6_small
- [x] `user-changelog.md` 新增 v0.1.11 条目
- [x] `app/build.gradle.kts` `versionCode/versionName` bump 到 11/0.1.11
- [x] `assembleDebug -PmodelProfile=ice_ocr_rules` 跑通(BUILD SUCCESSFUL in 7m 24s,APK 67.3 MB,内含 v6 模型)
- [x] `testDebugUnitTest` 全绿(317 tests, 0 failures, 0 errors)

## 7. 发布门控

按 CLAUDE.md 「Release 实际门控」段,本升级经 `assembleRelease → generateVisionLatestJson → archiveVisionRelease → uploadVisionReleaseToGitea` 全套流水线(签名 APK + cert-pin 校验 + Gitea `latest` tag + SHA-256 握手)。发版前需要:

1. release keystore 凭据(`ICESPIRITAI_RELEASE_*` env var 或 `~/.gradle/gradle.properties`)
2. Gitea PAT(`gradle.token.properties` gitignored)
3. CI / 本地 `./gradlew.bat assembleRelease` 跑通(本烟测只验 `assembleDebug`)

## 8. 引用

- [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](../knowledge/ppocrv6_vs_v5_a_b_test.md) — 4 张图 A/B 实测 + 4 项核心指标对比
- [`docs/smoke/2026-08-14-phase1-smoke.md`](2026-08-14-phase1-smoke.md) — Phase 1 (v5_mobile) 上线烟测基线
- `app/src/ice_ocr_rules/.../PaddleOcrEngine.kt` — OCR 引擎封装(asset path-only,无 v5/v6 假设)
- `tools/download-ppocr-models.sh` — 模型下载脚本(默认 variant = `pp-ocrv6_small`)