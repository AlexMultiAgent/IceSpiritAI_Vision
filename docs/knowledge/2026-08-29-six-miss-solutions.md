# 2026-08-29 6 张 MISS 漏判解决方案 — 根因 / 代码改动 / 测试计划

> **Date**: 2026-08-29
> **Follows**: `docs/smoke/2026-08-29-e2e-rerun.md` §3 (6 MISS 逐张归因)
> **状态**: 仅方案文档,**所有代码改动均未提交**。P1/P2 可单独 commit,P3 单独 track。
> **范围**: 三类 — ① Rule keyword 扩词 ② OCR 端恢复路径 ③ GT 标注修正

## §0 总览

| 槽 | 现 GT | Nova 6 HITS | overlap | 类别 | 解法 | 工作量 | 风险 |
|---:|---|---|---:|---|---|---|---|
| 08 | food_function_claim + disease_prevention + internet_art6_identifiable | (空) | 0/3 | OCR 端漏检(12.9MB 长图 9.96:1) | **P3** OCR 恢复路径(bitmap slicing,recovery-only) | 1 新 helper + PaddleOcrEngine 改 + 1 单元测试 + 1 真机 A/B | 慢 20×,**仅 fast path 返回 < 50 chars 时触发** |
| 19 | food_function_claim + disease_prevention | med_art6 + med_art7 | 0/2 | **OCR 端漏检为主**(nova 6 仅 OCR 79 chars);fixture-level keyword 扩词被 cross-check 取消(误命中 #13 #26 种子) | **P3** 同 #08 OCR 切分(若 #19 也是长图);否则不可解 | 视 P3 范围 | — |
| 48 | food_function_claim + disease_prevention + internet_art6_identifiable | art9_abs_pct + art11_data_citation | 0/3 | Rule keyword **exact-substring 缺 OCR 退化变体** | **P1-2** keyword 拆 3 partial variant(cross-check 0 误命中) | 1 commit (rule 改 + 1 单测) | 误命中风险低(0 fixtures 命中) |
| 59 | art9_abs_top + re_art26_planned_facility + re_art26_price_violation | (空) | 0/3 | **OCR 端漏检(4000×3000,正常 4:3,sparse text)** | **P0 无解**(det widening 已试过 → 3pp regression) | — | — |
| 60 | art9_abs_top + art11_data_citation + art28b_fake_data | (空) | 0/3 | **OCR 端漏检(同上,4:3 5.8MB 大文件)** | **P0 无解** | — | — |
| 61 | re_art26_planned_facility + re_art7_license_no | art9_abs_top × 2 + art11_data_citation + art9_edu_abs + edu_art24_public_servant_endorsement | 0/2 | **GT 标错**(fixture 是公考培训,不是房地产) | **P1-1** GT 重标 → art9_abs_top + art11_data_citation + art28b_fake_data (Critical) | 1 commit (coverage_matrix.md 5 行改) | 无(违规案例 sync 副本已对) |

**预期收益**:P1-1 + P1-2 完成 → 60/65 = 92.3% FULL+PARTIAL(从 90.8% 升 1.5pp);P3 完成 → 62/65 = 95.4%(再升 3.1pp);#59 #60 不可解 → 上限 64/65 = 98.5%。

## §1 解决方案详 — 类别 A:Rule keyword 扩词(覆盖 #48 + 部分 #19)

### §1.1 关键 bug — #48 `高压血糖血脂降下去` 不被 `血压血糖血脂降下去` substring match

Nova 6 OCR 在 #48 检出 `玛莉魔粉手工现磨粉450g黄瓜芹菜葡萄籽粉 高压血糖血脂降下去`(掉了一个"血"字 → "高压"),但当前 keyword `血压血糖血脂降下去` 是 exact substring 匹配,匹配失败。

**3 个修法选项**:
- **(A) 把 keyword 拆成 3 partial variants** — 简单直接,无 false-positive 风险:`血糖血脂降下去` + `血压血脂降下去` + `压血糖血脂降下去`(OCR 变体)。但 keyword 增多,启动时 AC 自动机构建慢 1ms 量级。
- **(B) 用 partial match 模式** — `keyword.contains(text)` 而非 `text.contains(keyword)`。会触发大量 false-positive(任何包含"血糖"两字的文案都触发)。**否决**。
- **(C) AC 自动机改成 match-longest 模式 + 拆 partial variants** — 与 (A) 等价。

**推荐 (A)**:代码改动最小 + 风险最低。

### §1.2 #48 改动 — `app/src/main/assets/rules/ad_signage_rules.json` (L1421 附近)

```diff
@@ food_function_claim keywords @@
         "血压血糖血脂降下去",
+        "血糖血脂降下去",
+        "血压血脂降下去",
+        "压血糖血脂降下去",
         "增强肌体免疫",
```

(注意:加在"血压血糖血脂降下去"**之后**,保持原有 keyword 不删,避免破坏其它依赖原 keyword 的测试。)

### §1.3 #19 改动 — 6 个 fixture-level keyword 缺口(部分有效,经 cross-check 大部分取消)

Nova 6 在 #19 实际只 OCR 到 79 chars(Taobao 浏览器 chrome + 兽药 sidebar),**没看到** fixture 里的 `调节血脂` / `消炎` 等违规内容。但 fixture-level(PC 录制的更完整 OCR)显示 6 个 pattern 没被 catch。

**Cross-check 结论(对 66 张 fixture 全量扫描)**:

| Phrase | 目标 rule | 出现 fixture | Nova 6 是否见 | 误命中风险 | 决策 |
|---|---|---|---|---|---|
| `抑制眼部炎症` | disease_prevention | 0 张 | 否 | 0 | **不需加**(nova 6 没见) |
| `增加抗病` | disease_prevention | 3 张: #13 豌豆种子, #19 蜂胶, #26 四季无筋豆 | 否 | **高** | **不加** |
| `化脓性感染` | disease_prevention | 0 张 | 否 | 0 | **不需加**(nova 6 没见) |
| `感染` | disease_prevention | 1 张: #19 | 否 | 0 | **不需加** |
| `保健功能` | food_function_claim | 1 张: #19 | 否 | 0 | **不需加** |
| `减肥` 独立 | food_function_claim | 1 张: #19 | 否 | 0 | **不需加** |
| `燃脂排油` / `燃脂` / `排油` | weight_loss_food_claim | 1 张: #19 | 否 | 0 | **不需加** |

**`抗病` 误命中实证**(cross-check 输出):
- #13 `13_豌豆种子_多且饱满高产_种子广告.png` 出现 "抗病"(植物抗病性 — 种子学描述)
- #19 `19_蜂胶胶囊整图_...` 出现 "增加抗病"(动物用药 — 治疗性)
- #26 `26_四季无筋豆种子_高产南北方种植_种子广告.png` 出现 "抗病高产"(种子描述)

#13 和 #26 当前 GT 是 `ad_signage_art27_seed_yield_guarantee`(agricultural bucket)。如加 `抗病` 到 `disease_prevention`:
- #13 nova 6 OCR 含 "抗病" → 命中 `disease_prevention` (Warning)
- GT = `art27_seed_yield_guarantee` (Warning)
- overlap = 0 → **#13 从 FULL/PARTIAL 降级到 MISS(回归)**

**结论**:#19 的 keyword 扩词**全部放弃**。原因:
1. nova 6 在 #19 仅 OCR 79 chars(根本没看到这些 pattern,加不加都不命中)
2. 唯一有命中潜力的 `抗病` 误命中 #13 #26(回归)
3. #19 真正修复路径是 P3 OCR 切分(同 #08 处理方式)

### §1.4 Risk: 误命中评估

| 新增 keyword | 误命中风险 | 备注 |
|---|---|---|
| `血糖血脂降下去` | 极低 | "降下去"是承诺式表达,合规广告不会出现 |
| `血压血脂降下去` | 极低 | 同上 |
| `压血糖血脂降下去` | 极低 | OCR 退化形式,正常广告不会出现 |
| `抑制炎症` (如加) | 中 | "抑制炎症" 化妆品也用,但 disease_prevention severity=Warning,不影响核心判定 |
| `抗病` (如加) | 中 | 农药合规广告可用,但 ad_signage 主走 pesticide_* 路径,不冲突 |
| `保健功能` (如加) | 低 | 蓝帽子保健食品专用 header |
| `燃脂排油` / `燃脂` / `排油` | 中 | 减肥食品专项,新规则更合适 |

### §1.5 单元测试(必加)

`app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`:

```kotlin
@Test
fun food_function_claim_matches_ocr_degraded_variants() {
    val matcher = AdSignageRuleMatcher(loadRules())
    val cases = listOf(
        "血糖血脂降下去" to "ad_signage_signage_food_function_claim",
        "血压血脂降下去" to "ad_signage_signage_food_function_claim",
        "压血糖血脂降下去" to "ad_signage_signage_food_function_claim",  // OCR-mangled #48 case
        "血压血糖血脂降下去" to "ad_signage_signage_food_function_claim", // original
    )
    for ((text, expected) in cases) {
        val hits = matcher.scan(text).map { it.ruleId }
        assertTrue("text='$text' should match $expected, got $hits",
            expected in hits)
    }
}
```

## §2 解决方案详 — 类别 B:OCR 端恢复路径(覆盖 #08,部分 #19)

### §2.1 真实情况 — 只有 #08 是真正长图

| # | 文件 | 实际像素 | 比例 | 类别 |
|---:|---|---:|---:|---|
| 08 | 蜜蜜游椴树蜜 | **1872 × 18653** | 9.96:1 | **长图,slicing 有用** |
| 19 | 蜂胶胶囊 | (类似长图,e-commerce detail) | (待 PIL 测) | 可能是长图(类似 #08) |
| 59 | 凯利集团 | 4000 × 3000 | 1.33:1 | 正常 4:3,大文件,slicing 无效 |
| 60 | 哈佛特区 | 4000 × 3000 | 1.33:1 | 正常 4:3,大文件,slicing 无效 |

**关键修正**(调研 agent 产出):slicing 仅对 #08 有意义。#59 #60 是 **OCR 端 det 阈值 + 小招牌字 + 低对比度** 导致的漏检,det widening 路径已试过(600a23c)→ 3pp regression,**无可行解**。

### §2.2 PaddleOcrEngine.kt 改动 — 仅 recovery 路径,fast path 不变

位置:`app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt:158-163`

```diff
-            val loaded = BitmapLoader.downsampledBitmapWithScale(bytes)
-                ?: throw OcrFailed("Failed to decode image: $uri")
-            val bitmap = loaded.bitmap
+            val loaded = BitmapLoader.downsampledBitmapWithScale(bytes)
+                ?: throw OcrFailed("Failed to decode image: $uri")
+            val bitmap = loaded.bitmap
+
+            // Recovery path: only slice when fast path would return empty/short.
+            // Cost: ~20× slow for very long images (e.g. 1872×18653 e-commerce
+            // detail page), so we never auto-slice — only after seeing
+            // text_chars < RECOVERY_THRESHOLD on the first pass.
+            val firstPass = runRecognize(ocr, bitmap)
+            val firstChars = firstPass.results.sumOf { it.text.length }
+            val firstHeight = bitmap.height
+            val firstWidth = bitmap.width
+            val isLongImage = firstHeight > SLICE_TRIGGER_H && firstHeight / maxOf(1, firstWidth) > SLICE_TRIGGER_RATIO
+            if (firstChars < RECOVERY_THRESHOLD && isLongImage) {
+                Log.w("PaddleOcr", "recovery slicing: w=$firstWidth h=$firstHeight chars=$firstChars")
+                runResult = runSlicedRecognize(ocr, bitmap, firstPass)
+            } else {
+                runResult = firstPass
+            }
```

新加 companion 常量:

```kotlin
companion object {
    const val DEFAULT_REC_BATCH_SIZE = 6
    /** Recovery slicing: only invoke when first pass returns < this many chars. */
    private const val RECOVERY_THRESHOLD = 50
    /** Trigger slicing when bitmap is taller than this after downsample. */
    private const val SLICE_TRIGGER_H = 1500
    /** Aspect ratio: height / width must exceed this. */
    private const val SLICE_TRIGGER_RATIO = 2
    /** Slice height: matches detLimitSideLen so SDK doesn't further cap. */
    private const val SLICE_H = 960
    /** Overlap: 10% of slice height. */
    private const val SLICE_OVERLAP = 96
}
```

新加 helper(放在 companion object 之外,private):

```kotlin
/**
 * Slice a tall bitmap into vertical strips of [SLICE_H] px with [SLICE_OVERLAP]
 * overlap, recognize each strip, translate OCR box y-coords back to the
 * original bitmap coord space, and merge. Used only as a recovery path when
 * the first-pass OCR returned very few characters on a clearly long image.
 *
 * Cost: ~20× the single-call time for an 1872×18653 image (20 strips of
 * 1872×960 + 96 overlap). We only invoke this when the single-call path
 * has effectively failed (text_chars < 50), so the cost is acceptable as a
 * "we recovered what we couldn't see before" outcome.
 */
private fun runSlicedRecognize(ocr: PaddleOCR, bitmap: Bitmap, emptyFirst: OCRRunResult): OCRRunResult {
    val strips = mutableListOf<Bitmap>()
    val h = bitmap.height
    val w = bitmap.width
    val stride = SLICE_H - SLICE_OVERLAP
    var y = 0
    while (y < h) {
        val stripH = minOf(SLICE_H, h - y)
        strips.add(Bitmap.createBitmap(bitmap, 0, y, w, stripH))
        if (y + stripH >= h) break
        y += stride
    }
    val merged = mutableListOf<com.paddle.ocr.model.OCRResult>()
    strips.forEachIndexed { i, strip ->
        val r = ocr.recognize(strip)
        val yOffset = (i * stride).toFloat()
        r.results.forEach { line ->
            val translatedPoints = line.box.points.map { PointF(it.x, it.y + yOffset) }
            val translatedBox = com.paddle.ocr.model.OCRBox(translatedPoints)
            merged.add(line.copy(box = translatedBox))
        }
    }
    return OCRRunResult(merged)
}
```

### §2.3 BitmapLoader.kt 改动 — 无需改

`downsampledBitmapWithScale` 已经返回 `bitmap + sampleSize`,PaddleOcrEngine 已有 `loaded.bitmap` 和 `loaded.sampleSize`,不需要改 BitmapLoader。

### §2.4 单元测试 + 真机 A/B

`app/src/androidTest/.../PaddleOcrLongImageRecoveryTest.kt`(新):

```kotlin
@RunWith(AndroidJUnit4::class)
class PaddleOcrLongImageRecoveryTest {
    @Test
    fun longImageRecovers() = runBlocking {
        assumeTrue("OpenCV must load", OpenCVLoader.initLocal())
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val bytes = appCtx.assets.open("fixtures/audit66/08_蜜蜜游俄罗斯椴树蜜电商页_8大优势_蜂蜜食品.png").readBytes()
        val uri = Uri.fromFile(File(appCtx.cacheDir, "08_recovery.png").also { it.writeBytes(bytes) })

        val engine = PaddleOcrEngine(appCtx)
        val res = engine.recognize(uri)

        Log.i("RecoveryTest", "chars=${res.fullText.length} lines=${res.lineBoxes.size} avg_conf=${res.avgConfidence}")
        // Acceptance: text_chars >= 200 (currently < 30 without slicing)
        assertTrue("Recovery should produce >= 200 chars, got ${res.fullText.length}", res.fullText.length >= 200)
    }
}
```

真机 A/B(参考 `PaddleOcrRealDeviceAbTest.kt`):
- 1 cold + 3 warm 计时
- 对比 baseline warm_avg 2,198ms;接受线 warm_avg < 60s(recovery 仅 1 张,用户能接受)
- 验证 nova 6 上不 OOM(1872×18653 全 bitmap 在 det 输入阶段需 ~50MB 显存,SDK 自己切到 detector 内部 tile)

### §2.5 Risk

| 风险 | 概率 | 缓解 |
|---|---|---|
| Recovery 慢 20×,用户体验抖动 | 高 | 仅 fast path 返回 < 50 chars 时触发,正常图不受影响 |
| AC 自动机匹配箱(box y 坐标错位) | 中 | y_offset = i * stride,bitmap-local,不再 sampleSize 缩放,直接对接 toBoundingRect |
| 拼缝处文字重复检测 | 中 | 96px overlap 内允许 1 次重复;后续可加 text+pos dedup |
| nova 6 内存 OOM | 低 | 1872×18653 原始图 14MB;但 PaddleOcrEngine 输入仍是 downsample 后的 117×1165,ARGB_8888 占用 ~500KB,远低于 nova 6 应用堆 |

## §3 解决方案详 — 类别 C:GT 标注修正(覆盖 #61)

### §3.1 调研结论

#61 fixture `61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` 是**公考培训广告**,不是房地产:

- **OCR 内容**:三元教育(3 校址) / "是学生、家长公职培训的不二之选" / 哈尔滨排名第一 / 通过率 75%
- **Nova 6 HITS**(5 条,全部正确):
  - `ad_signage_art9_abs_top` × 2(命中"不二之选" / "排名第一")
  - `ad_signage_art11_data_citation`(命中"通过率")
  - `ad_signage_art9_edu_abs`(命中"第一")
  - `ad_signage_edu_art24_public_servant_endorsement`(命中"公职培训")
- **当前 GT 错**:`re_art26_planned_facility` + `re_art7_license_no`(房地产规则,文案里 0 命中)
- **正确 GT**:`art9_abs_top` + `art11_data_citation` + `art28b_fake_data`(Critical,与 `违规案例/_coverage_matrix.md:210` 一致)

### §3.2 改动 — `app/src/androidTest/assets/fixtures/audit66/coverage_matrix.md`

5 行改动,5 个 rule row 数量各 ±1 + 1 个 fixture row 重写:

```diff
@@ L51 — art11_data_citation @@
-| `ad_signage_art11_data_citation` | `signage` | Warning | 9 | `#01` 01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg, `#02` 02_名师教育申论班_龙江第一_绝对化用语.jpg, `#16` 16_蒙恩教育教师资格证_通过率85%_数据引用.png, `#17` 17_智行教育25省考申论保分_快速提分_教育承诺.png, `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#58` 58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#62` 62_东郊到家按摩APP_9万人1000万次_数据引用.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |
+| `ad_signage_art11_data_citation` | `signage` | Warning | 10 | `#01` 01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg, `#02` 02_名师教育申论班_龙江第一_绝对化用语.jpg, `#16` 16_蒙恩教育教师资格证_通过率85%_数据引用.png, `#17` 17_智行教育25省考申论保分_快速提分_教育承诺.png, `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#58` 58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#61` 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg, `#62` 62_东郊到家按摩APP_9万人1000万次_数据引用.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |

@@ L54 — art28b_fake_data @@
-| `ad_signage_art28b_fake_data` | `absolute` | Warning | 4 | `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#63` 63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |
+| `ad_signage_art28b_fake_data` | `absolute` | Warning | 5 | `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#61` 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg, `#63` 63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |

@@ L56 — art9_abs_top @@
-| `ad_signage_art9_abs_top` | `absolute` | Warning | 13 | `#01` ... `#59` ... `#60` ... `#65` ... |
+| `ad_signage_art9_abs_top` | `absolute` | Warning | 14 | `#01` ... `#59` ... `#60` ... `#61` ... `#65` ... |

@@ L90 — re_art26_planned_facility @@
-| `ad_signage_re_art26_planned_facility` | `realestate` | Warning | 2 | `#59` 59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg, `#61` 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg |
+| `ad_signage_re_art26_planned_facility` | `realestate` | Warning | 1 | `#59` 59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg |

@@ L94 — re_art7_license_no @@
-| `ad_signage_re_art7_license_no` | `realestate` | Warning | 1 | `#61` 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg |
+| `ad_signage_re_art7_license_no` | `realestate` | Warning | 0 | (backlog, 本批无案例) |

@@ L210 — #61 fixture row @@
-| `61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` | `realestate` | Warning | 2 | `ad_signage_re_art26_planned_facility`, `ad_signage_re_art7_license_no` | 弱覆盖(关键词薄) |
+| `61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` | `absolute data_citation fake_data` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_art11_data_citation`, `ad_signage_art28b_fake_data` | 已覆盖 |
```

### §3.3 同步副本

`违规案例/_coverage_matrix.md`(已对,与 fixture master 一致)用 `fixture-rename-sync` skill 流程保持单向同步,本文档**不改**该副本(它已正确)。

### §3.4 Risk

无。**0 误命中**风险,因为 nova 6 HITS 已证明这 3 个 rule 是 fixture 的实际命中;且 `违规案例/_coverage_matrix.md:210` 已对,改 fixture master 是"反向同步",不引入新 error。

### §3.5 单元测试 / E2E 验证

无新单元测试(GT 是 fixture 文件,不是代码)。**E2E 验证**:
```bash
# 重跑 AdSignageAuditSixtySixImageE2ETest on nova 6
./gradlew.bat :app:connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAuditSixtySixImageE2ETest

# 期望:#61 从 MISS 升到 FULL(因为 nova 6 HITS = 5 含全部 3 个 GT rule)
# 期望总 FULL+PARTIAL: 60/65 = 92.3% (从 90.8% 升 1.5pp)
```

## §4 实施顺序 + 风险评估

| 顺序 | 工作 | commit 范围 | 风险 | 期望增量 |
|---|---|---|---|---|
| **P1-1** | #61 GT 修正 | 1 commit (coverage_matrix.md 6 行改) | **极低** (同步副本已对,fixture md5 不变) | +1.5 pp (90.8% → 92.3%) |
| **P1-2** | #48 keyword 拆 3 partial(0 误命中,cross-check 验证) | 1 commit (rules JSON + 1 单测) | 低 (新 keyword 都很具体) | +3.1 pp(累计) |
| **P1-3** | **取消**(原计划扩 #19 keyword,经 cross-check `抗病` 误命中 #13 #26 种子广告导致回归) | — | — | — |
| **P3** | #08 OCR recovery slicing(若 #19 验证为长图可一并) | 1 commit (PaddleOcrEngine.kt + 1 测试) | 中 (慢 20× recovery path) | +3.1 pp(累计) |
| **P0** | #59 #60 | **无解** | — | — |

**最终可达上限**:64/65 = 98.5%(去掉 #59 #60,本 batch 全过)。

### §4.1 取消的 P1-3 详细原因

`#19 fixture-level keyword 扩词` 6 个候选 phrase 经 cross-check(对 66 张 OCR fixture 全量扫描):
- 4 个 (抑制炎症 / 化脓性感染 / 感染 / 保健功能) 仅 #19 fixture 出现 → 加不加对 nova 6 没差(nova 6 没见)
- 1 个 `抗病` 在 #13 豌豆种子 / #19 蜂胶 / #26 四季无筋豆 都出现 → 若加 `抗病` 到 `disease_prevention`:
  - #13 #26 nova 6 OCR 含 "抗病" → 命中 `disease_prevention` (Warning)
  - GT = `art27_seed_yield_guarantee` (Warning)
  - overlap = 0 → **#13 #26 从 FULL/PARTIAL 降级到 MISS(回归)**
- 1 个 `燃脂/排油/减肥` 仅 #19 出现 → nova 6 没见,加不加没差

**P1-3 全部放弃**。#19 的真实修复路径 = P3 OCR 切分(同 #08)。若 #19 不是长图(需要单独 PIL 测宽高),则 #19 不可解。

## §5 单元测试清单

| Commit | 新增单测 | 文件 |
|---|---|---|
| P1-1 | 无 | (E2E 验证) |
| P1-2 | `food_function_claim_matches_ocr_degraded_variants` | `AdSignageRuleMatcherTest.kt` |
| P3 | `PaddleOcrLongImageRecoveryTest.longImageRecovers` | `PaddleOcrLongImageRecoveryTest.kt`(新) |

## §6 Hygiene

- **不要 amend 600a23c**(已 push,改要 force-push,被禁)
- **commit 作者**:AlexMultiAgent(CLAUDE.md 强制)
- **不要 `Co-Authored-By: Claude ...`**(CLAUDE.md 强制)
- **不要 `git add -A`**(PreToolUse hook 拦截)
- **敏感文件不入 index**:`gradle.token.properties` / `local.properties` / `~/.gradle/gradle.properties`
- **PaddleOcrEngine.kt 改动需要 ice_ocr_rules profile 编译**(默认 shell profile 找不到 PaddleOcr 类)
- **coverage_matrix.md 改动需要同步 `违规案例/_coverage_matrix.md`**(实际已对,本 PR 是"反向同步",不影响 md5)
- **PaddleOcrEngine.kt 改后,在 nova 6 上跑 PaddleOcrRealDeviceAbTest 验证 baseline 不回归**(warm_avg 仍 < 3s)

## §7 Open Questions

- **#59 #60 真的无解吗?** 调研 agent 给出"det widening 3pp regression"的结论基于 600a23c 试过的 4 个 det 参数(1280/min/0.3/0.5/1.6)。也许还有别的参数组合(如 detMaxSideLimit / detMaxCandidates)能单点救 #59 #60 不影响其他 slot。值得在 P1 完成后再做一次 14-sample A/B 实验(参考 `AdSignageOcrConfigExperimentTest.kt` 已有框架)。
- **是否需要新增 `ad_signage_signage_weight_loss_food_claim` 规则**?当前 #19 fixture 的"减肥/燃脂/排油"概念没有专属 rule,只有 `food_function_claim`(泛保健功能)。如果 2026 年减肥食品违规案例增加,可以新加一条 rule。
- **#61 GT 修正后,e79d188 那条 commit 历史如何处理?** e79d188 把 #61 标成 `absolute × data_citation × fake_data`(正确),但 fixture/coverage_matrix.md 没同步。本 P1-1 commit 是把 fixture 同步到正确状态,与 e79d188 不冲突,但 git log 会显示 "GT 修正" 跨越 2 个 commit。可以在 e79d188 注释里补一句 "fixture/coverage_matrix.md 未同步,见 commit XXXX" 作为历史注脚。
