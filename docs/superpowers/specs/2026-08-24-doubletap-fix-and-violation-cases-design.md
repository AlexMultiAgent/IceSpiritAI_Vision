# 冰灵锐目 — 双击图片 bug 修复 + 广告违规案例采集规范

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-24 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 Image Viewer spec | `docs/superpowers/specs/2026-08-19-icevision-image-viewer-design.md`(回填 `ViolationReport.lineBoxes` 即来源) |
| 关联 UI spec | `docs/superpowers/specs/2026-08-15-icevision-ui-design.md` |

本文档覆盖两个独立子项目:

- **A — 双击 `ImagePreview` 无反应 bug 修复**(单点改动)
- **B — 采集 50+ 张广告违规案例**(数据扩充)

两者均为 Phase 1 OCR + 规则库的运营工作,后端逻辑 / 构建系统 / OCR 模型 / 规则 schema **完全保持现状**,不动。

---

## 1. 背景与目标

### 1.1 子项目 A 现状

`HomeScreen.ImagePreview` 在用户双击后没有任何反应。已在主屏见到"违规 8 处"的状态下复现:`HomeScreen.kt:145` 的 `lineBoxes` 计算只从 `OcrDone` 取:

```kotlin
val ocrResult = (state as? AnalysisState.OcrDone)
val lineBoxes = ocrResult?.lineBoxes ?: emptyList()  // ← state=Complete 时 ocrResult=null → 永远空
val hits = completeReport?.hits ?: emptyList()
val showLineBoxes = (state is AnalysisState.OcrDone) || completeReport != null
```

当 state 走到 `Complete`(用户看到 8 处违规的常态):

- `ocrResult == null` → `lineBoxes = emptyList()`
- `showLineBoxes == true`(因为 `completeReport != null`)
- `ImagePreview(lineBoxes=emptyList(), onDoubleTap=onOpenViewer)` 内部:

  ```kotlin
  if (onDoubleTap != null && lineBoxes.isNotEmpty()) {  // ← 第二个条件假
      m.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
  }
  ```

  → 手势检测器 **根本没装** → 双击无反应 → 无法进 Viewer → 无法缩放 / 拖动 / 双击切换。

但 `ViolationReport.lineBoxes` 字段在 2026-08-19 的 Viewer spec 已经备好(`AnalysisState.kt:97`),`ViewerScreen` 也能从 `completeReport.lineBoxes` 取 — 只是 `HomeScreen` 本地这个派生漏写回填。

### 1.2 子项目 A 目标

`state.Complete` 状态下双击 `ImagePreview` → 进入 `Routes.VIEWER` → Viewer 内 Telephoto `ZoomableAsyncImage` 支持双指缩放 / 单指拖动 / 双击切换(既有能力)。回归覆盖:既有 `ImagePreviewDoubleTapTest` 三例 + 一例新增(`state.Complete + lineBoxes 非空 → 双击触发`)。

### 1.3 子项目 B 现状

`违规案例/` 当前 4 张图(`微信图片_20260819100008_5_2011.jpg` 等,2026-08-19 用户微信截图),无元数据。规则库 `ad_signage_rules.json` v6 已扩到 **120 条 / 14 category**:

```
absolute / agricultural / cosmetic / education / finance / internet_ad /
medical / minor / outdoor / pesticide / realestate / restricted /
signage / veterinary
```

→ OCR 模型 + 规则库的评测集偏小(4 张实拍),规则迭代缺乏覆盖广度。

### 1.4 子项目 B 目标

补充 **50+ 张广告招牌违规案例** 到 `违规案例/`,覆盖 14 个 category 的高频场景。每张图配同名 `.md` 元数据,字段包含:来源 URL、拍摄场景、违规点、预期命中规则 ID、OCR 难度标注。服务于:

- 规则迭代回归测试(`AdSignageMentorFiveImageRegressionTest` 风格的扩展)
- OCR 模型评测(PP-OCRv6_small 升级下一阶段的输入)
- 「广告招牌 → 其他视觉判别域」模板可复制性的参考样本

### 1.5 非目标(本期)

- 子项目 A:不动 `ImagePreview` 守卫(`lineBoxes.isEmpty()` 仍不该让进空 Viewer,守卫语义保留);不动 Viewer / Telephoto;不动 nav graph
- 子项目 B:不引入自动化采集脚本(政府站 anti-hotlink / 验证码 / 偶发 HTTP 100 卡住等坑已在 `CLAUDE.md`「发布流水线踩坑」记录,50 张规模人工更稳);不动规则库 JSON 自身;不修改 OCR 模型

---

## 2. 总体方案

### 2.1 子项目 A — 一行回填

`HomeScreen.kt:145` 单一表达式扩展,补上 `Complete` 来源:

```kotlin
// 旧:
val lineBoxes = ocrResult?.lineBoxes ?: emptyList()
// 新:
val lineBoxes = ocrResult?.lineBoxes ?: completeReport?.lineBoxes ?: emptyList()
```

`ViolationReport.lineBoxes` 在 `IceSpiritVisionViewModel.startAnalysis` 已经从 `_lastLineBoxes` 装入(`AnalysisState.kt:97` 默认 `emptyList()`,`ImageAnalyzerRepository` 真实填充) — 改完即刻生效。

### 2.2 子项目 B — 人工采集 + 同名 .md

按 category 分桶,逐桶 WebSearch + WebFetch 找到公开监管公示 / 媒体曝光 / 微信群截图,下载到 `违规案例/`,写同名 .md 元数据。优先级排序:高频违规类(医疗 / 绝对化 / 教育 / 食品 / 房地产)先填,长尾类(化妆品 / 兽药 / 户外 / 互联网)后填。

---

## 3. 架构 & 数据流

### 3.1 子项目 A 数据流

```
AnalysisState.Complete(report)
   ├── report.imageUri      → pendingUri  → ImagePreview.imageUri
   ├── report.hits          → hits        → ImagePreview.hits → ResultPanel
   └── report.lineBoxes     ←── 之前漏接 ── 修后 → ImagePreview.lineBoxes
                                                         │
                                                         ↓ lineBoxes.isNotEmpty() == true
                                                         ↓ pointerInput(detectTapGestures(onDoubleTap = onOpenViewer))
                                                         ↓ onOpenViewer = { nav.navigate(Routes.VIEWER) }
Routes.VIEWER (ViewerScreen)
   ├── ZoomableAsyncImage(imageUri) ← Telephoto 双指缩放 / 单指拖动 / 双击切换
   └── ViewerTextList(lineBoxes, hitsCount)
```

### 3.2 子项目 B 元数据格式

每张图 `<slug>.jpg` 配同名 `<slug>.md`,例如 `medical_ykzp_01.jpg` + `medical_ykzp_01.md`:

```markdown
---
来源: <URL 或 "微信群截图,无来源">
场景: <门店招牌 / 户外广告牌 / 互联网广告截图 / 印刷品 / ...>
违规点: <一句话>
预期命中规则:
  - id: <ad_signage_art16_med_abs>
    关键词: ["根治"]
    severity: Violation
预期 OCR 难度: <简单/中等/难 — 字体、字号、对比度、模糊度、角度等>
拍摄角度: <正面/侧面/俯视>
实测 OCR 置信度(可选): <0.0-1.0 或 "未跑过">
备注: <若需要>
---

# <场景标题>

<一段正文描述:为什么这是违规 / 对应法条原文 / 同类常见变体>
```

`来源` 必填(URL 或明确"无来源");`预期命中规则` ID 必须能在 `app/src/main/assets/rules/ad_signage_rules.json` 查得到;否则 metadata 视为无效,采集需重做。

---

## 4. 依赖与库集成

### 4.1 子项目 A

零新依赖。纯逻辑修复。

### 4.2 子项目 B

零新依赖(采集流程不引入代码,纯运营操作)。后续若把 case 接进 `AdSignageMentorFiveImageRegressionTest`,需要 `app/src/test/resources/cases/...` 但属另一工单。

---

## 5. 组件规范

### 5.1 子项目 A — `HomeScreen.kt` 改动

文件路径:`app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`

仅第 145 行,改一处表达式:

```kotlin
val lineBoxes = ocrResult?.lineBoxes ?: completeReport?.lineBoxes ?: emptyList()
```

无新增 import,无新增字段,无新增 composable。

### 5.2 子项目 B — 案例采集执行清单

按 category 拆分,每桶目标张数:

| Category | 目标 | 备注 |
|---|---:|---|
| medical(医疗) | 10 | §16 「根治 / 100% 有效 / 彻底治愈」高频,优先级最高 |
| absolute(绝对化) | 8 | §9 「最佳 / 第一 / 顶级 / 国家级」 |
| education(教育) | 5 | §24 「保过 / 包过 / 不过退款」 |
| food(食品 / 保健) | 6 | 食品安全法 §71 / §78(规则库 `ad_signage_art17_food_*`) |
| realestate(房地产) | 4 | §26 「升值回报 / 学区房包入学」 |
| finance(金融 / 招商) | 4 | §25 「稳赚不赔 / 无风险 / 保本高收益」 |
| cosmetic(化妆品) | 3 | §15(规则库 `ad_signage_art15_cos_*`) |
| agricultural(农资) | 3 | §27 |
| pesticide(农药) | 2 | §31 |
| veterinary(兽药) | 2 | §26 |
| signage(招牌本身) | 2 | §32 「未经同意 / 强行发布」 |
| minor(未成年人) | 2 | §38 / §39 |
| outdoor(户外) | 2 | §42 |
| internet_ad(互联网) | 2 | §14 |

合计:55 张(略超 50 缓冲;若长尾类缺素材,可压缩到 50)。

**采集流程**(每个 category 一轮):

1. **WebSearch**:关键词组合(category 名 + 「违规 案例 处罚」+ 「市场监管」/「广告法」);来源倾向:
   - 国家市场监督管理总局 `samr.gov.cn`(处罚公示)
   - 省市级监管局(京 / 沪 / 粤 / 苏 / 浙 公开通报)
   - 中央 / 地方媒体(央视、新华网、澎湃等曝光案例)
   - 公开学术 / 行业研究(广告合规白皮书等)
   - 微信 / 微博公开转载的现场执法图(无明确来源但有违规点)
2. **筛选**:必须能看到具体广告内容(招牌 / 海报 / 印刷品 / 屏幕截图);不接受纯文字处罚通报
3. **下载 / 截图**:URL 直链 → `curl -o 违规案例/<slug>.jpg`;若是页面嵌入 → `WebFetch` 取页面 → 解析 `<img src>` 后下载;若是新闻图 → 同
4. **写 .md**:字段填写完整,`预期命中规则.id` 验证存在于 `ad_signage_rules.json`
5. **冒烟**:对每张图本地 `QuickOcrEngine` 跑一次(若已实现)或人工标注预期 OCR 文字(为后续规则测试 fixture 用)

**slug 命名**:`<category 缩写>_<场景>_<序号>`,全小写、英文下划线;例:`medical_ykzp_01`、`absolute_best_03`。

**采集完整度检查**:每桶完成后 `python -c "import json,os,glob; ..."` 校验:
- 每桶张数 ≥ 目标
- 每张图有同名 .md
- .md `预期命中规则.id` 全部存在于 rules JSON
- 总张数 ≥ 50

---

## 6. 错误处理 & 边界

### 6.1 子项目 A

| 场景 | 行为 |
|---|---|
| `state == Idle` | `lineBoxes = emptyList()`(回退兜底) → `ImagePreview` 守卫不通过 → 双击无反应 → 预期(没结果可看) |
| `state == Loading(OcrRunning)` | 同上,预期 |
| `state == OcrDone(lineBoxes=非空)` | `lineBoxes = ocrResult.lineBoxes` → 双击工作(既有) |
| `state == Complete(report.lineBoxes=非空)` | 修复后:`lineBoxes = report.lineBoxes` → 双击工作(新增覆盖) |
| `state == Complete(report.lineBoxes=空)`(罕见:规则命中但 OCR 无 boxes) | `lineBoxes = emptyList()` → 双击无反应 → 防御性兜底,不进空 Viewer |
| `state == Error(...)` | `lineBoxes = emptyList()` → 双击无反应 → 预期(没结果) |

回退链 `ocrResult?.lineBoxes ?: completeReport?.lineBoxes ?: emptyList()` 保证任何 state 都有定义值,不会 NPE。

### 6.2 子项目 B

| 场景 | 处理 |
|---|---|
| 目标 URL 404 / 死链 | 跳过,记录到 `违规案例/_download_log.md`(可选,不阻塞其他桶) |
| anti-hotlink / 验证码 | 切换到备用来源(新闻转载 / 微博截图) |
| 素材是视频截图 | 用 `ffmpeg` 抽帧,落 `<slug>.jpg` |
| 找不到某 category 案例 | 该桶压缩张数,整体仍保 50+ |
| .md `预期命中规则.id` 找不到 | 重写,或调规则库(超出本期,留工单) |

---

## 7. 测试策略

### 7.1 子项目 A — 单元测试 (JVM, Robolectric)

**`ImagePreviewDoubleTapTest.kt`**(修改):既有三例不变,新增 1 例:

```kotlin
@Test
fun `double-tap on Complete state with lineBoxes invokes onDoubleTap`() {
    // 模拟 state.Complete(report.lineBoxes=[...非空...]) 走完整 HomeScreen 派生链
    // (HomeScreenTest 风格 — 不直接调用 ImagePreview,而是把 lineBoxes
    //  设为 report.lineBoxes 同值,验证守卫通过)
    val completeLineBoxes = listOf(
        TextLine(text = "根治糖尿病", box = AndroidRect(0,0,100,20), confidence = 0.95f),
    )
    var dblClicks = 0
    composeTestRule.setContent {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                ImagePreview(
                    imageUri = Uri.parse("file:///tmp/sample.jpg"),
                    lineBoxes = completeLineBoxes,  // ← 模拟 HomeScreen 修后的派生
                    hits = emptyList(),
                    onDoubleTap = { dblClicks++ },
                )
            }
        }
    }
    composeTestRule.onNodeWithTag("image_preview")
        .performTouchInput { doubleClick(center) }
    assertEquals(1, dblClicks)
}
```

测试 `ImagePreview` 在 lineBoxes 非空时双击触发回调 — 现有三例断言的也是同一契约,新增这一例证明「Complete 路径下 lineBoxes 一定非空,HomeScreen 修后这条路径走得通」。

**`HomeScreenTest.kt`**(若存在):补 1 例断言「`state = Complete(report with lineBoxes)` 时,ImagePreview 收到 lineBoxes 非空」。否则新增最小 fixture 文件。

### 7.2 子项目 B — 元数据完整性测试(JVM, JUnit)

新增 `app/src/test/java/com/icespiritai/offline/rules/ViolationCaseMetadataTest.kt`(可选,不阻塞):

- 遍历 `违规案例/*.md`(解析 frontmatter)
- 校验 `来源` / `场景` / `违规点` 字段非空
- 校验 `预期命中规则[*].id` 全部存在于 `ad_signage_rules.json`
- 校验总数 ≥ 50

(本测试夹具稳健性低于规则 matcher 测试,可作为运营 checklist 工具,不上 CI gate。)

### 7.3 手动验证(子项目 A)

- 真机 / 模拟器跑现有 APK,拍照或选图触发 `state.Complete`(8 处违规那条路径),双击图片区 → 进 Viewer → 双指缩放 / 单指拖动 / 双击切换全部正常
- 真机冷启动状态下双击 → 验证仍无效(预期:Idle 状态 lineBoxes 空)
- 横竖屏切换 → Viewer 内 `ZoomableImageState` 通过 `rememberSaveable` 恢复

### 7.4 回归

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug -PmodelProfile=shell`
- `./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules`

---

## 8. 文件清单

### 8.1 子项目 A

| 路径 | 改动 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` | 第 145 行:表达式补 `completeReport?.lineBoxes` 回退 |
| `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt` | 新增 1 例:Complete 路径下双击触发 |

### 8.2 子项目 B

新增内容到仓库(共 55 张图 + 55 个 .md):

| Category | 文件数 | 路径模式 |
|---|---:|---|
| medical | 10 | `违规案例/medical_*.{jpg,md}` |
| absolute | 8 | `违规案例/absolute_*.{jpg,md}` |
| education | 5 | `违规案例/education_*.{jpg,md}` |
| food | 6 | `违规案例/food_*.{jpg,md}` |
| realestate | 4 | `违规案例/realestate_*.{jpg,md}` |
| finance | 4 | `违规案例/finance_*.{jpg,md}` |
| cosmetic | 3 | `违规案例/cosmetic_*.{jpg,md}` |
| agricultural | 3 | `违规案例/agricultural_*.{jpg,md}` |
| pesticide | 2 | `违规案例/pesticide_*.{jpg,md}` |
| veterinary | 2 | `违规案例/veterinary_*.{jpg,md}` |
| signage | 2 | `违规案例/signage_*.{jpg,md}` |
| minor | 2 | `违规案例/minor_*.{jpg,md}` |
| outdoor | 2 | `违规案例/outdoor_*.{jpg,md}` |
| internet_ad | 2 | `违规案例/internet_ad_*.{jpg,md}` |

(实际张数按采集结果可能微调,但总 ≥ 50。)

**注意**:`违规案例/` 当前已在仓库内跟踪(`.gitignore` 未排除),新增内容走正常 `git add 违规案例/<slug>.{jpg,md}`。

### 8.3 不动

- 后端:`OcrEngineFactory` / `ImageAnalyzerRepository` / `RuleMatcher` / `RulesRepository`
- UI:`ImagePreview` / `ViewerScreen` / `ViewerImage` / `Telephoto` 集成
- 构建:AGP 9.3 / Kotlin 2.4.10 / Gradle 9.7 / Compose BOM 2026.08.00 / Telephoto 0.13.0 / NDK 28.2.13676358 / minSdk 26 / targetSdk 37
- 规则库:`ad_signage_rules.json` v6 / `food_label_rules.json` v4 不动
- 签名 / release 流水线 / Gitea 上传

---

## 9. 验收标准

### 9.1 子项目 A

- [ ] `./gradlew.bat testDebugUnitTest` 全绿(既有 + 新增 1 例)
- [ ] `./gradlew.bat assembleDebug -PmodelProfile=shell` 与 `-PmodelProfile=ice_ocr_rules` 均成功
- [ ] 真机 `state.Complete`(8 处违规)双击图片 → 进 Viewer → 双指缩放 / 单指拖动 / 双击切换 1.0 ↔ 2.5× 全部正常
- [ ] 真机 `state.Idle` / `state.Loading` / `state.Error` 双击图片 → 无反应(守卫保留)
- [ ] 横竖屏切换后 Viewer 内缩放状态恢复

### 9.2 子项目 B

- [ ] `违规案例/` 总文件数 ≥ 100(= 50 张 .jpg + 50 个 .md)
- [ ] 14 个 category 全部覆盖(长尾类允许压缩张数,但不能整桶缺失)
- [ ] 所有 .md `来源` / `场景` / `违规点` 字段非空
- [ ] 所有 .md `预期命中规则[*].id` 在 `ad_signage_rules.json` 可查到
- [ ] slug 命名符合 `<category>_<场景>_<序号>` 全小写下划线模式
- [ ] 至少抽样 5 张图跑一遍 OCR(若 `QuickOcrEngine` 已存在),预期文本与 .md `预期命中规则` 关键词一致

---

## 10. 风险 & 缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 子项目 A 修了之后 `OcrDone` 路径回归 | 低 | 中 | 既有 `ImagePreviewDoubleTapTest` 三例覆盖 OcrDone 路径(单 tap 不触发 / lineBoxes 空时不触发);跑测试即可捕获 |
| 子项目 B 采集的图片版权 / 来源合规 | 中 | 中 | 优先政府公示 / 媒体曝光(发布于公开渠道);非公开来源标注「微信群截图,无来源」即可,不伪造 URL;案例仅用于内部 OCR + 规则测试,不二次分发 |
| 子项目 B 政府站 anti-hotlink / 验证码 | 中 | 低 | 备用来源切换(同事件的新闻转载 / 微博);单桶失败不影响整体 |
| 子项目 B `.md` `预期命中规则.id` 与现行 JSON 不匹配 | 中 | 中 | 写入前 grep 验证;每桶完成后跑 `ViolationCaseMetadataTest`(若本规范加了该测试) |
| 子项目 B 案例数量实际达不到 55 | 低 | 低 | 长尾类压缩,总张数仍保 50+ |