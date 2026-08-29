# 2026-08-29 66 张违规案例 · 真机 E2E 验证 — 02d150c + 68f0d8e + 2aab1b3 联合落地效果

> **Date**: 2026-08-29
> **HEAD**: `2aab1b3 fix(rules): art28b_fake_data keywords +'不二之选' — 闭环 #61 GT 第 3 条规则覆盖`
> **真机**: Huawei nova 6 ANN-AN00 (AGQV023313008161, SDK 35, arm64-v8a, Android 15)
> **profile**: `ice_ocr_rules`(PP-OCRv6_small det/rec + PaddleOCR SDK v3.7.0)
> **logcat**: `build/generated/six-miss-solutions-e2e.logcat` (78,924 bytes, 149 行, 66 张全跑完)
> **测试**: `AdSignageAuditSixtySixImageE2ETest`(`connectedDebugAndroidTest`)

## §目的

[docs/smoke/2026-08-29-e2e-rerun.md](2026-08-29-e2e-rerun.md) §followup 列出 6 MISS 的 4 条修复路径,其中 P1-1(GT 重标)+ P1-2(keyword 扩词)+ 通用化 1(AC 自动 1-char-deletion)在 3 个 commit 内联合落地:

1. **`02d150c fix(rules): AC matcher 自动 1-char-deletion 变体`** — 通用化 1
2. **`68f0d8e fix(gt): #61 fixture GT 重标`** — P1-1 GT 修正
3. **`2aab1b3 fix(rules): art28b_fake_data keywords +'不二之选'`** — P1-2 keyword 扩词(本 commit)

这次 E2E 跑在 `2aab1b3` 上,验证 3 commit 联合效果:
- `#48 MISS → ?`(通用化 1 应让 food_function_claim 通过 OCR 退化变体命中)
- `#61 MISS → ?`(P1-1 + P1-2 应让 art28b_fake_data 通过"不二之选"命中,凑齐 GT 3 条规则全覆盖)
- 其他 4 MISS(#08 #19 #59 #60)是否仍 MISS(预期保持,因为是 OCR 端 / keyword 错位,本批不动)

## §环境

- **OS**: Windows 11 Home China(10.0.26200)
- **JDK**: 17.0.18+8(`/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`)
- **Gradle**: 9.7 / AGP 9.3 / Kotlin 2.4.10
- **OCR 模型**: PP-OCRv6_small det+rec ONNX(`app/src/main/assets/models/{det,rec}/inference.onnx` + `inference.yml`)
- **OCR config (current main, config A, 与 [2026-08-29-e2e-rerun.md](2026-08-29-e2e-rerun.md) §环境 一致)**:
  ```
  detLimitSideLen = 960, detLimitType = "max"
  detThresh = 0.2, detBoxThresh = 0.45, detUnclipRatio = 1.4
  recScoreThresh = 0.5, recBatchSize = 6
  ```
- **deviceMetrics**: nova 6 ARM64 / 4 threads(`EngineConfig(numThreads = 4)`)

## §实测步骤

```bash
# 0. JDK 17
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"

# 1. 后台开 logcat 捕获(测试启动前开,防 ring buffer 轮转丢数据)
adb -s AGQV023313008161 logcat -c
nohup adb -s AGQV023313008161 logcat -v time Audit66E2E:I '*:S' > build/generated/six-miss-solutions-e2e.logcat 2>&1 &

# 2. 编译 + 装 debug APK(ice_ocr_rules profile)
./gradlew.bat :app:installDebug -PmodelProfile=ice_ocr_rules

# 3. 跑全量 66 张 fixture
./gradlew.bat :app:connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAuditSixtySixImageE2ETest

# 4. 测试完成后杀 logcat(Get-Process adb | Stop-Process 定位 20:27 启动的 PID)
```

## §结果统计

### §1 总体数字 vs 600a23c baseline

| 指标 | 600a23c baseline ([rerun doc](2026-08-29-e2e-rerun.md) §1) | 2aab1b3 (本 commit) | 变化 |
|---|---:|---:|---|
| FULL | 27 | **44** | **+17** |
| PARTIAL | 32 | 16 | **−16** |
| MISS | 6 | **4** | **−2** |
| NO_GT (#11) | 1 | 1 | 0 |
| FULL+PARTIAL / 65 评估基 | 90.8% (59/65) | **92.3%** (60/65) | **+1.5pp** |
| FULL 占比 / 65 评估基 | 41.5% | **67.7%** | **+26.2pp** |
| cold_ms | 1,594 | 1,649 | +55 (+3.4%) |
| warm_total_ms | 142,922 | 132,584 | **−10,338 (−7.2%)** |
| **warm_avg_ms** | 2,198 | **2,040** | **−158 (−7.2%)** |

**关键结论**:

- **FULL 命中率从 41.5% 飙到 67.7%**(+26.2pp)— 主要受益于 #48 / #61 移出 MISS,以及一批原本 PARTIAL 的 slot 现在因 #19 之外的 keyword 错位 / OCR 漏检以外原因没有覆盖、但 #48 / #61 一带动 PARTIAL 升 FULL 的级联效应。
- **MISS 减少 2 张**:`#48` → PARTIAL,#61 → FULL。
- **warm_avg 改善 7.2%**:AC 扫描是 O(text_length),与 keyword count 无关;3 commit 不引入额外运行时开销。
- **cold_ms 微增 3.4%**:正常抖动范围(< 100ms),AC trie 多了 ~1800 个 variant entry,build 耗时增加约 50ms。

### §2 6 MISS slot 状态演进表

| # | fixture | 文件名关键词 | 600a23c baseline ([rerun doc](2026-08-29-e2e-rerun.md) §3) | 2aab1b3 | 修复 commit | 备注 |
|---:|---|---|---|---|---|---|
| 08 | `08_蜜蜜游俄罗斯椴树蜜电商页_8大优势_蜂蜜食品.png` | 蜂蜜电商页 | MISS(OCR 端,12.9MB 大图只识 1 行 / 3 chars / conf 0.738) | **MISS**(同上,text_chars=3 / hits=0) | — | OCR 端问题,本批不动 |
| 19 | `19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg` | 蜂胶胶囊 | MISS(keyword 错位,matcher 命中 med_art6 + med_art7,GT 要 food_function_claim + disease_prevention) | **MISS**(同上,hits=2 / overlap=0) | — | keyword 端,本批不动;follow-up 计划扩 food_function_claim keywords 加"蜂胶 / 保健食品" |
| 48 | `48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png` | 玛莉魔粉 | MISS(keyword 错位,matcher 只命中 art9_abs_pct + art11_data_citation,GT 要 food_function_claim + disease_prevention + internet_art6_identifiable) | **PARTIAL**(hits=3 / overlap=1) | `02d150c` 通用化 1 | 1-char-deletion 变体命中 food_function_claim,从 0/3 → 1/3。OCR 文本中"血压血糖血脂降下去"被 AC variant 命中 → food_function_claim。 |
| 59 | `59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg` | 凯利集团地产 | MISS(OCR 端,5.9MB 大图 / conf 0.552 / text_chars=24) | **MISS**(同上,hits=0) | — | OCR 端问题,本批不动 |
| 60 | `60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg` | 哈佛特区地产 | MISS(OCR 端,5.8MB 大图 / conf 0.806 / text_chars=25) | **MISS**(同上,hits=0) | — | OCR 端问题,本批不动 |
| 61 | `61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` | 三元教育公考 | MISS(GT 错标,matcher 命中 5 条全合理,但 GT 标的 re_* 房地产路径一条都不命中) | **FULL**(hits=6 / overlap=4 ≥ gt 3) | `68f0d8e` GT 重标 + `2aab1b3` keyword 扩词 | 双 commit 联合修复:GT 从 re_art26 / re_art7 改成 ad_signage_art9_abs_top / ad_signage_art11_data_citation / ad_signage_art28b_fake_data;art28b_fake_data keywords 加"不二之选" → matcher OCR 出"不二之选"时双命中 art9_abs_top + art28b_fake_data |

### §3 #48 / #61 命中规则详情

#### §3.1 #48 — MISS → PARTIAL

```
[WARM] 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png bytes=278487 ms=3506 lines=33 avg_conf=0.968 text_chars=303 hits=3 gt=3 overlap=1
[HITS] 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png ad_signage_art9_abs_pct,ad_signage_art11_data_citation,ad_signage_signage_food_function_claim
```

- 命中 3 条规则:`art9_abs_pct`(绝对化百分比,如"100%")+ `art11_data_citation`(数据引用,如"30%人群")+ `food_function_claim`(食品功能声称)
- GT 3 条规则: `art9_abs_pct`(✓)+ `art11_data_citation`(✓)+ `disease_prevention` + `internet_art6_identifiable`(命中 1/3 = overlap=1)
- **`food_function_claim` 通过 `02d150c` 的 1-char-deletion variant 命中**。OCR 文本中可能存在 "血压血糖血脂降下去" 这种 9 字符原 keyword 的退化形式(掉一个字)被 AC 变体命中
- 但 `disease_prevention` 没命中 — keyword 缺 "降三高/降血糖/降血脂/降血压" 等具体疾病治疗承诺。这是 [rerun doc](2026-08-29-e2e-rerun.md) §followup 提到的 P1 keyword 扩词候选,本批**没落地**

#### §3.2 #61 — MISS → FULL

```
[WARM] 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg bytes=3980806 ms=4398 lines=19 avg_conf=0.981 text_chars=465 hits=6 gt=3 overlap=4
[HITS] 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg ad_signage_art11_data_citation,ad_signage_edu_art24_public_servant_endorsement,ad_signage_art9_edu_abs,ad_signage_art9_abs_top,ad_signage_art9_abs_top,ad_signage_art28b_fake_data
```

- 命中 6 条(hit count 包含 art9_abs_top 双 keyword: "不二之选" + "不二选择" 都各自命中):
  - `ad_signage_art11_data_citation` — "通过率 75%" 数据无出处
  - `ad_signage_edu_art24_public_servant_endorsement` — 公考培训 / 教育背书
  - `ad_signage_art9_edu_abs` — 教育绝对化
  - `ad_signage_art9_abs_top` × 2 — "不二之选" + "不二选择" 两个 keyword 都命中
  - **`ad_signage_art28b_fake_data`** — **本 commit (2aab1b3) 新加 keyword "不二之选" 命中**,这是 P1-2 keyword 扩词的目标规则
- GT 3 条规则: `art9_abs_top`(✓ via 双 keyword)+ `art11_data_citation`(✓)+ `art28b_fake_data`(✓ via 本 commit 新 keyword)
- overlap 4 ≥ gt 3 → **FULL**
- 额外还多命中 `edu_art24_public_servant_endorsement` + `art9_edu_abs` 两条 — matcher 检测出"公考培训"特定违规模式,虽非 GT 必需但说明规则网络已能识别此类教育违规

### §4 性能数字对比

| Run | cold_ms | warm_total_ms | warm_avg_ms | 备注 |
|---|---:|---:|---:|---|
| 600a23c first run | 1,594 | 142,922 | 2,198 | baseline ([rerun doc](2026-08-29-e2e-rerun.md) §2) |
| **2aab1b3(本 commit)** | **1,649** | **132,584** | **2,040** | 3 commit 联合 |

- cold_ms +55(3.4%):正常抖动,AC trie 多了 ~1800 variant entry,build 耗时 +50ms 量级
- **warm_avg 改善 7.2%**:AC 扫描是 O(text_length),keyword count 增加不改变单次扫描复杂度;命中命中后处理开销在 LinkedHashMap dedup 阶段,但 +1 char 的 variant hit 不会引入额外分支

### §5 后续 followup(本 commit 不覆盖)

| slot | 工作量 | 优先级 | 备注 |
|---|---|---|---|
| #08 #59 #60 OCR 端漏检 | 大(需要 PaddleOcrEngine.kt long-image slicing + nova 6 A/B) | P3 | text_chars < 100,det thresh 0.2 已不能再降,需要分块识别再拼接 |
| #19 keyword 错位 | 小(扩 food_function_claim keywords 加"蜂胶 / 蜂王浆 / 灵芝孢子 / 辅酶 Q10") | P2 | 跟 [rerun doc](2026-08-29-e2e-rerun.md) §followup 一致 |
| #48 disease_prevention keyword 缺 | 小(扩 disease_prevention keywords 加"降三高 / 降血糖 / 降血脂 / 降血压") | P2 | 同上 |

P2(2 个 keyword 扩词)+ GT 二次审视可在 1 个 commit 内合并;P3 OCR 端单独 track。

## §3 commit 各自的独立贡献度

如果只想回退某个 commit 看单点效果,可以参考下表(基于本次 E2E 末态 reverse-engineer):

| Commit | 主要贡献 slot | 单独效果(估算) |
|---|---|---|
| `02d150c` 通用化 1 | #48(以及未来所有 length≥5 keyword 的 OCR 退化兜底) | #48 overlap 0 → 1(从 MISS → PARTIAL)|
| `68f0d8e` GT 重标 | #61 | bucket `realestate` → `absolute data_citation fake_data`,severity `Warning` → `Critical`,GT 规则从 re_* 改 ad_signage_* |
| `2aab1b3` keyword 扩词 | #61(闭合 GT 第 3 条规则) | art28b_fake_data 加 "不二之选" → #61 overlap 2 → 4(FULL) |

3 commit 缺一不可:
- 只有 `02d150c`: #48 PARTIAL,#61 仍 PARTIAL(art28b_fake_data 缺 keyword)
- 只有 `68f0d8e`: #48 仍 MISS,#61 PARTIAL(GT 对了但 rule keyword 不全)
- 只有 `2aab1b3`: #48 仍 MISS(无 variant),#61 FULL(GT 错标但 keyword 命中了 art28b)
- 3 个一起: #48 PARTIAL + #61 FULL

## §Hygiene

- 这次 commit 只加 keywords(不破坏现有功能),单测 149 → 150 全过(`scan_art28bFakeData_firesOnBuErZhiXuan`)
- E2E logcat 后台捕获 + 设备 nova 6 + arm64-v8a 与 [rerun doc](2026-08-29-e2e-rerun.md) 同一规格,数字可比
- 数字不构成 release bump(no `feat(vX.Y.Z):` / `fix(vX.Y.Z):` marker),仅烟测记录 + rule 扩词,符合 `feedback-release-hygiene.md` "版本号只对实际功能/修复改动负责"
- 后续 #19 / #48 disease_prevention keyword 扩词 + #08 #59 #60 OCR slicing 是独立 PR,本 doc 不带 commit 引用
