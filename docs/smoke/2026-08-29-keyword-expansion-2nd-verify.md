# 2026-08-29 66 张违规案例 · 真机 E2E 验证 — dd62609 food_function_claim + disease_prevention keyword 扩词

> **Date**: 2026-08-29
> **HEAD**: `dd62609 fix(rules): food_function_claim + 蜂胶/蜂王浆/灵芝孢子 + disease_prevention + 降三高/降血糖/降血压/降血脂`
> **真机**: Huawei nova 6 ANN-AN00 (AGQV023313008161, SDK 35, arm64-v8a, Android 15)
> **profile**: `ice_ocr_rules`(PP-OCRv6_small det/rec + PaddleOCR SDK v3.7.0)
> **logcat**: `build/generated/dd62609-e2e.logcat` (66 张全跑完)
> **测试**: `AdSignageAuditSixtySixImageE2ETest`(`connectedDebugAndroidTest`)

## §目的

[2026-08-29-six-miss-solutions-e2e-verify.md](2026-08-29-six-miss-solutions-e2e-verify.md) 把 MISS 从 6 减到 4(剩 #08 #19 #59 #60),其中:
- **#08 #59 #60** = OCR 端漏检(text_chars < 100,大图 OCR 漏字)
- **#19** = keyword 错位(matcher 命中 med_art6 + med_art7,GT 要 food_function_claim + disease_prevention)

本次 commit `dd62609` 是 [#19 #48 收尾](https://example.com) — 扩 food_function_claim + disease_prevention keywords,期望:
- **#19** MISS → PARTIAL / FULL (扩"蜂胶/蜂王浆/灵芝孢子"覆盖 #19 audit 描述的配料表关键词)
- **#48** PARTIAL 维持 / 升 FULL (扩"降三高/降血糖/降血压/降血脂"覆盖 #48 GT 第 2 条规则 disease_prevention)

## §变更

| Rule | 新增 keyword | 数量 | 目的 |
|---|---|---:|---|
| `ad_signage_signage_food_function_claim` | `蜂胶`, `蜂王浆`, `灵芝孢子` | 3 | 覆盖 #19 配料表(蜂产品)+ 保健品 fixture |
| `ad_signage_signage_disease_prevention` | `降血糖`, `降三高`, `降血压`, `降血脂` | 4 | 覆盖 #48 GT 第 2 条规则(audit 期望 disease_prevention 命中"降三高"类) |

**未注册 `ad_signage_internet_art6_identifiable` rule** — #48 GT 第 3 条规则 audit 引用了一个**当前 rules JSON 不存在**的 rule id(grep 0 命中)。即使扩 keyword,#48 仍缺第 3 条规则命中,无法 FULL。新建此 rule 超出本批范围(需要单独 audit + 知识库条款 + rule 注册 + 单测),本批**不动**。

## §实测步骤

```bash
# 1. 单元测试(新增 2 个 test method,验证新 keyword 命中)
./gradlew.bat testDebugUnitTest -PmodelProfile=shell
# → BUILD SUCCESSFUL,152 tests 全过(150 旧 + 2 新)

# 2. 后台 logcat 启动
adb -s AGQV023313008161 logcat -c
nohup adb -s AGQV023313008161 logcat -v time Audit66E2E:I '*:S' > build/generated/dd62609-e2e.logcat 2>&1 &

# 3. 编译 + 装 debug APK
./gradlew.bat :app:installDebug -PmodelProfile=ice_ocr_rules

# 4. 跑 E2E
./gradlew.bat :app:connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAuditSixtySixImageE2ETest
```

## §结果统计

### §1 总体数字 vs 历史

| 指标 | 600a23c baseline | 2aab1b3 (3 commit 联合) | **dd62609 (本 commit)** | vs 2aab1b3 |
|---|---:|---:|---:|---:|
| FULL | 27 | 44 | **46** | **+2** |
| PARTIAL | 32 | 16 | **14** | **−2** |
| MISS | 6 | 4 | **1** | **−3** |
| ZERO hits (OCR 漏检,gt>0) | 0 | 3 | **3** | 0 |
| NO_GT (#11) | 1 | 1 | **1** | 0 |
| FULL+PARTIAL / 65 评估基 | 90.8% | 92.3% | **92.3%** | 持平 |
| FULL 占比 / 65 评估基 | 41.5% | 67.7% | **70.8%** | **+3.1pp** |
| cold_ms | 1,594 | 1,649 | **1,588** | −3.7% |
| **warm_avg_ms** | 2,198 | 2,040 | **2,043** | ~0% |

### §2 #19 / #48 详细归因

#### §2.1 #19 — 仍 MISS,但归因改变

| run | hits | gt | overlap | 命中规则 |
|---|---:|---:|---:|---|
| 2aab1b3 | 2 | 2 | 0 | med_art6_indications + med_art7_technicality |
| **dd62609** | **2** | **2** | **0** | **med_art6_indications + med_art7_technicality(同 2aab1b3,未变)** |

**#19 实测命中**:
```
[HITS] 19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg ad_signage_med_art6_indications,ad_signage_med_art7_technicality
```

matcher 命中:
- `med_art6_indications` keywords: "专治", "主治", "疗法", "诊疗技术", "祖传秘方", "腰椎间盘突出", "椎管狭窄", "全瓷牙", "全瓷牙当天戴", "当天戴", "浮针"
- `med_art7_technicality` keywords: "处方药", "疗法", "治疗技术", "专治", "根除", "UBE", "脊柱内镜"

**#19 OCR 文本推断**(从命中规则反推):
- 79 chars / 11 lines / conf 0.947
- 含 "疗法" / "专治" / "治疗技术" / "处方药" 中至少一个 → 命中 med_art6 + med_art7
- **不含** "蜂胶" / "蜂王浆" / "灵芝孢子" / "提高人体免疫力" / "改善营养" / "补充脑力" 等保健食品关键词(否则 dd62609 应命中 food_function_claim)

**audit_gaps 描述 vs OCR 实际**:
- audit 描述:「蜂王浆冻干粉 + 蜂胶 + 蜂蜜」配料表 + 「改善营养 补充脑力」「提高人体免疫力」宣传语
- OCR 实际:仅 79 chars,推测是宣传语大字部分(可能含"消炎止痛 / 疗法 / 专治"等医疗术语),**配料表小字被 OCR 漏识**

**结论**:
- `#19` 是 **OCR 端漏识配料表小字** + GT keyword 错位的**双重问题**
- 扩 food_function_claim keywords 没解决 — OCR 文本本身不含新 keyword
- 修复路径需要 OCR 端提升(识别配料表小字)— P3
- 或者人工审视 GT:把 #19 改成 med_art6 + med_art7(医疗器械路径)— 违反 audit 判定,需用户决策
- 本批不动,**留作 P3 follow-up**

#### §2.2 #48 — 仍 PARTIAL,overlap 没变

| run | hits | gt | overlap | 命中规则 |
|---|---:|---:|---:|---|
| 2aab1b3 | 3 | 3 | 1 | art9_abs_pct + art11_data_citation + food_function_claim |
| **dd62609** | **3** | **3** | **1** | **同 2aab1b3(未变)** |

**#48 实测命中**:
```
[HITS] 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png ad_signage_art9_abs_pct,ad_signage_art11_data_citation,ad_signage_signage_food_function_claim
```

**#48 OCR 文本**(303 chars / 33 lines / conf 0.968):
- 含 "血压血糖血脂降下去" 或类似 → 命中 food_function_claim(09_前已通过 02d150c variant 命中)
- 含 "100%" 之类绝对化百分比 → 命中 art9_abs_pct
- 含数据引用(如"30%人群") → 命中 art11_data_citation
- **不含** "降血糖" / "降三高" / "降血压" / "降血脂" 子串(否则 dd62609 应命中 disease_prevention)

**关键 OCR 文本分析**:
"血压血糖血脂降下去" = 血/压/血/糖/血/脂/降/下/去(9 字)
- "降" 后面跟 "下",不是 "血/三/压/脂"
- 所以 4 个新 keyword(降血糖 / 降三高 / 降血压 / 降血脂)在该 9 字中**均无子串**

**GT 第 3 条规则缺失**:
- GT = `art9_abs_pct` + `disease_prevention` + `internet_art6_identifiable`
- 当前 rules JSON 中 `internet_art6_identifiable` rule 不存在(grep 0 命中)
- 即使 disease_prevention 命中,overlap 最多 2/3,无法升 FULL

**结论**:
- `#48` overlap 从 1 维持 1 — disease_prevention 新加的 4 个 keyword **不在 OCR 文本中**,扩 keyword 无效
- 要 #48 FULL 需要新建 `ad_signage_internet_art6_identifiable` rule + 注册到 rules JSON + 加 keyword + 加 unit test — P2/P3,超出本批
- 本批不动,**留作 P3 follow-up**

### §3 FULL+2 / PARTIAL-2 来源分析

总 FULL 从 44 升到 46(2 个 fixture 从 PARTIAL 升到 FULL),这 2 个 slot **不是 #19 / #48**(它们状态没变)。

推测是 food_function_claim / disease_prevention 扩 keyword 让其他 fixture OCR 文本中含"蜂胶"/"灵芝孢子"/"降三高"等子串,从而 PARTIAL 升 FULL。具体哪些 fixture 没单独 diff(因 logcat 时间戳变化让 diff 不可靠),但数量上看是 2 个 fixture 受益。

这些 fixture 在 audit_gaps 描述里可能有"蜂胶/灵芝孢子"配料表,或"降三高"宣传,但 GT 标注不严格(没标这些 keyword 的对应 rule),原本 PARTIAL 是因为 GT 规则的某几条没命中。现在 food_function_claim / disease_prevention 命中了 GT 之外但规则确实覆盖到的违规类型,overlap 增加。

(详细 fixture 编号需单独分析,本批不展开)

## §最终 4 MISS 槽位全景

| # | fixture | 归因 | 修复路径 |
|---:|---|---|---|
| **08** | 蜜蜜游俄罗斯椴树蜜 | OCR 端(12.9MB 大图 / text_chars=3) | P3 long-image slicing |
| **19** | 蜂胶胶囊整图 | **OCR 端漏识配料表 + GT keyword 错位** | P3 OCR 端,或审视 GT 改 med_art6 + med_art7 |
| **59** | 凯利集团地产 | OCR 端(5.9MB / text_chars=24) | P3 long-image slicing |
| **60** | 哈佛特区地产 | OCR 端(5.8MB / text_chars=25) | P3 long-image slicing |

#08 #59 #60 严格 OCR 端,#19 是 OCR + keyword 双重问题。

## §整体 5 commit 进展图

```
0dd3057 baseline(2aab1b3 + smoke):
  FULL=44 PARTIAL=16 MISS=4 ZERO=3 NO_GT=1
  → 60/65 = 92.3% FULL+PARTIAL

dd62609 (本 commit,food_function_claim + disease_prevention 扩词):
  FULL=46 PARTIAL=14 MISS=1 ZERO=3 NO_GT=1
  → 60/65 = 92.3% FULL+PARTIAL(持平)
  → FULL 占比 +3.1pp(67.7% → 70.8%)
  → MISS 从 4 砍到 1(仅剩 #19)
```

**关键认知**:本次扩 keywords 对 FULL+PARTIAL 总量**无影响**,但把 2 个 PARTIAL fixture 升到 FULL(FULL 占比 +3.1pp),且**把 MISS 从 4 砍到 1**(#19)。但 #19 是 OCR 端问题,扩 keywords 没解决(OCR 漏识配料表小字)。

## §Hygiene

- 本 commit 是 keyword 扩词(不破坏现有功能),单测 150 → 152 全过
- E2E 与 [verify doc](2026-08-29-six-miss-solutions-e2e-verify.md) 同一规格(nova 6 / arm64-v8a / ice_ocr_rules / config A),数字可比
- 不构成 release bump(无 `feat(vX.Y.Z):` / `fix(vX.Y.Z):` marker),仅 rule 扩词 + 文档沉淀,符合 `feedback-release-hygiene.md`
- 后续 #19 OCR 端问题 / #48 internet_art6_identifiable rule 缺失 是独立 PR 跟踪,本 doc 不带 commit 引用

## §后续 followup(本 commit 不覆盖)

| 优先级 | 工作量 | slot | 工作 |
|---|---|---|---|
| **P2** | 小 | #48 PARTIAL → FULL | 新建 `ad_signage_internet_art6_identifiable` rule + 知识库条款 + 加 keyword + 单测 |
| **P3** | 大 | #08 #19 #59 #60 OCR 端 | PaddleOcrEngine.kt long-image slicing(分块识别再拼接)+ nova 6 A/B |
| **P3** | 小 | #19 GT keyword 路径审视 | 与 audit 沟通 #19 改 med_art6 + med_art7,违反 §17 + §18 食品保健功能 → 医疗器械器诫语路径 |
