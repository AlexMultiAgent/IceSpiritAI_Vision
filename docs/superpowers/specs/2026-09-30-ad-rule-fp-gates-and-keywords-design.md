# 广告规则 FP gate + keyword 补盲区设计 — 2026-09-30

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-09-30 |
| Spec 状态 | **DRAFT — 待用户 review** |
| 关联项目根指令 | [`CLAUDE.md`](../../CLAUDE.md) |
| 关联审计报告 | 2026-09-30 主君 audit 报告(本批 audit output) |
| 关联 baseline | [`docs/knowledge/build-stack-2026-08.md`](../../knowledge/build-stack-2026-08.md) |
| 关联覆盖矩阵 | [`违规案例/_coverage_matrix.md`](../../../违规案例/_coverage_matrix.md) + [`app/src/androidTest/assets/fixtures/audit71/coverage_matrix.md`](../../app/src/androidTest/assets/fixtures/audit71/coverage_matrix.md) |
| 关联规则库 | [`app/src/main/assets/rules/ad_signage_rules.json`](../../app/src/main/assets/rules/ad_signage_rules.json) v20 / 158 条 |

本文档解决主君审计基准 6 项中前 4 项(P0 + P1 部分):
- **P0** 改造「参照样本」误命中的规则 gate(`categoryAnchorsAbsent`)
- **P0** 扩展 `signage_food_disease_target` keyword 覆盖 fixture #55
- **P1** 扩展 `art27_seed_yield_guarantee` / `edu_art24_test_authority` / `art26_re_prm` / `re_art26_planned_facility` keyword 覆盖 #13 / #23 / #11 / #15
- **P1** fixture 同步(audit71 → 违规案例/)补充 #68 / #100

---

## 1. 背景

主君 2026-09-30 对 `违规案例/` 137 张图像 fixture(01-66 旧审计集 + 67-137 新增集)+ 50 个 markdown 跑了一轮 mentor audit,产出 9 个发现。本 spec 仅处理其中 4 个 — 剩下 5 个(text_fixtures README、#112/#115 fixture 命名、12-19.jpg 原始素材复核、fixture-to-image 内容错位根因)留作后续发版号或用户动作项。

主君具体指示「按你的意见走」已锁定 P0+P1 范围(见 [`audit 报告 §7`](../mentors/audit-2026-09-30.md) 优先级建议)。

---

## 2. 改造清单

### 2.1 规则 gate 加固(`categoryAnchorsAbsent` 反向 anchor)

#### 2.1.1 `ad_signage_signage_national_political_symbol_misuse`(覆盖 fixture #113)

**现状**:13 个 keyword (天安门/盛世华诞/庆国庆/76th/建国75周年/国旗图案/国庆元素/华表/人民大会堂/国家标志商业/国庆立牌/建国周年),无 gate,severity=Violation。

**问题**:OCR 检测到「庆国庆」「建国」即命中。但 fixture #113 是**哈尔滨新闻出版局组织的国庆图书惠民公益展**,不是商业品牌滥用国家标志 — 误判为 Violation 风险高。

**改造**:加 `categoryAnchorsAbsent` 反向 anchor,文本若含**政府 / 新闻出版 / 公益 / 教育 / 文联 / 主办**等 markers,说明是合法公共活动,规则不放行。

```json
"categoryAnchorsAbsent": [
  "政府",
  "新闻出版",
  "出版局",
  "出版社",
  "中共",
  "党办",
  "党政",
  "宣传部",
  "主办",
  "政府主办",
  "公益",
  "惠民",
  "图书惠民",
  "书展",
  "图书展",
  "书市",
  "图书馆",
  "书店",
  "纪念馆",
  "博物馆",
  "文联",
  "作协",
  "书协",
  "美协",
  "教育局",
  "学校",
  "校园",
  "师生"
]
```

(共 28 个 anchor,远超 `non_medical_institution_disease_advertisement` 的 17 个模板,因「合法国家符号使用」场景类别更多 — 政府/出版/教育/文艺/公益 五大类)

**预期影响**:
- fixture #113 命中数从 1 → 0(不再误判)
- fixture #127 人民咖啡馆国庆立牌(商业品牌「人民咖啡馆」使用天安门元素) — **不应被 gate 阻断**(「人民咖啡馆」不在 absent 列表中),继续正确命中 ✓
- fixture #06 八一建军节(「哈尔滨胜利加油站」商业借用军政)— 「哈尔滨胜利加油站」不在 absent 列表中,继续命中 ✓

#### 2.1.2 `ad_signage_signage_alcohol_drink_scenario`(覆盖 fixture #86)

**现状**:14 个 keyword(白酒/茅台/五粮液/.../酒类/酒精度/纯粮/闻香/清爽顺滑/商务宴请/陈酿),无 gate,severity=Warning,category=restricted。

**问题**:OCR 检测到「酒类」即命中。但 fixture #86 是**「兰泽烟酒零售门头」** — 商业标识标注经营类目,不是「酒类广告」违规场景(广告法 §23 酒类广告 = 喝得不得诱导、怂恿、出现饮酒动作等)。零售类目不在 §23 规制范围。

**改造**:加 `categoryAnchorsAbsent` 反向 anchor,文本若含**零售 / 门头 / 招牌 / 烟酒行 / 销售**等 markers,说明是合法商业标识而非酒类广告。

```json
"categoryAnchorsAbsent": [
  "零售",
  "批发",
  "门店",
  "门头",
  "招牌",
  "标识",
  "经销",
  "经销商",
  "代理",
  "加盟店",
  "连锁店",
  "总店",
  "分店",
  "商行",
  "烟酒行",
  "名酒行",
  "便利店",
  "超市",
  "酒行",
  "酒庄",
  "直营",
  "加盟",
  "专营",
  "专卖店",
  "烟酒专卖"
]
```

(共 25 个 anchor)

**预期影响**:
- fixture #86 命中数从 1 → 0(零售门头不再误判)
- fixture #18 白酒电商页(「闻香 入口 层次丰富」电商页文案)— 「电商页」「淘宝」「天猫」「京东」「小程序」不在 absent 列表,继续正确命中 ✓
- fixture #90 花园酒中华老字号(「布鲁塞尔蒙特金奖」 + 「穿越千年」国际奖项)— 「穿越千年」「中华老字号」不在 absent 列表,继续命中 ✓

### 2.2 规则 keyword 扩展(`signage_food_disease_target` 覆盖 fixture #55)

#### 2.2.1 `ad_signage_signage_food_disease_target` 扩词

**现状**:20 个 keyword(糖尿病患者/高血压患者/癌症病人/.../前列腺患者/男性健康/妇科疾病/妇科炎症/白癜风/牛皮癣/抗癌/防癌/抗癌防癌),severity=Violation,category=signage。

**问题**:fixture #55「京东京造番茄红素沙棘果油前列腺养护」OCR 召回「前列腺养护」「护前列腺炎」「尿频尿急」「男性生活伴侣」,**均不在当前 keyword**。需要新增。

**改造**:在 keyword 列表追加:

```json
"前列腺养护",
"护前列腺炎",
"尿频尿急",
"男性生活伴侣",
"前列腺健康",
"前列腺保健",
"泌尿健康"
```

(共追加 7 个 keyword,v20 → 27 个)

**预期影响**:
- fixture #55 命中数从 0 → ≥1(真机 OCR 后预期命中 `signage_food_disease_target` + `med_art6_indications`)
- 不影响既有 #34 / #35 / #36 / #44 / #45 / #47 / #50 / #66 命中(已命中这些 keyword 的图继续命中)

### 2.3 旧 weak case keyword 扩展(#13 / #23 / #11 / #15)

#### 2.3.1 `ad_signage_art27_seed_yield_guarantee` 加 #13 / #23 keyword

**现状**(v8 时 weak):fixture #13「豌豆多且饱满 高产」OCR 召回「饱满」「多且饱满」「籽粒饱满」「油亮饱满」,无对应 keyword;fixture #23「黑旋风冬瓜 高产 新改良」OCR 召回「瓜型好」「心小肉厚」「新改良」「改良」,无对应 keyword。

**改造**:在 keyword 列表追加(注意:这些 keyword 与 #15「粮食类高产」类似,但 legal 框架一致 — 都是「种子广告对产量 / 形态 / 改良的无依据承诺」):

```json
"饱满",
"多且饱满",
"籽粒饱满",
"油亮饱满",
"瓜型好",
"心小肉厚",
"新改良",
"改良",
"颗粒饱满",
"饱满度高"
```

(共追加 10 个 keyword,v20 当前 25 → 35 个)

#### 2.3.2 `ad_signage_edu_art24_test_authority` 加 #11 keyword

**现状(v20 实测)**:4 个 keyword — 考试命题人 / 阅卷老师 / 考官亲自授课 / 教育部推荐。**完全不覆盖「公安专项 / 警考」** — fixture #11「公安专项秋考刷题班 高效提分」OCR 召回「公安专项」「警员培训」「公安类」全部 miss。

**改造**:追加 11 个 keyword:

```json
"公安专项",
"公安类",
"警校",
"警员培训",
"公安岗",
"公安系统",
"警察考试",
"警考培训",
"政法干警",
"公安联考",
"公安院校"
```

(共追加 11 个,v20 当前 4 → 15 个)

> **命名说明**:audit 报告「[_audit_gaps.md §强化规则清单](../../../违规案例/_audit_gaps.md)」line 854 写的「扩充 ad_signage_edu_art24_test_authority 加 警察老师主讲/公安专项/警考」是 v8 时期的规划文本,实际 v20 该规则 keywords 未落地。本次按 v20 实测 4 个 keyword 落地,直接补到 15 个,与规划意图对齐。

#### 2.3.3 `ad_signage_re_art26_planned_facility` 加 #15 keyword

**现状(v20 实测)**:
- `art26_re_prm`:12 个 keyword(120/含「财富启航」「创富」「主题商街」「城芯现铺」「坐拥群力」等),fixture #15「银泰集茶巷」核心 keyword「品牌加冕 / 财富启航 / 国茶文化 / 银泰商圈」**已覆盖** — #15 不需要扩 `art26_re_prm`
- `re_art26_planned_facility`:8 个 keyword(地铁直达/学区确定/规划学校/规划医院/未来 X 号线/智慧健康/体检区/健康体检区),**不含「主题商街 / 国潮茶文化 / 茶巷」** — fixture #15 OCR 召回这些词会 miss

**改造**:**仅**给 `re_art26_planned_facility` 追加 9 个 keyword:

```json
"主题商街",
"国潮茶文化",
"茶巷",
"商街",
"国潮",
"国潮街区",
"茶文化",
"国潮主题",
"商圈核心"
```

(v20 当前 8 → 17 个)

`art26_re_prm` 不动(已覆盖 #15)。

> **修正说明**:audit 报告 §7 P1-4 写的「`art26_re_prm` 加『财富启航 / 财富实力 / 创富』」是 v8 时期规划文本 — 实际 v20 该规则已包含这些词(继承 v10/v11 扩词)。本次只补 `re_art26_planned_facility` 缺失的「主题商街」类。

---

## 3. fixture 同步

**问题**:`违规案例/` 缺 #68 (德伦堡短保啤酒) 和 #100 (哈药牌钙铁锌口服液),但 `app/src/androidTest/assets/fixtures/audit71/` 有这两张图。fixture 编号不对等。

**改造**:从 `audit71/` 复制两张图到 `违规案例/`:

```bash
cp "app/src/androidTest/assets/fixtures/audit71/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg" \
   "违规案例/"
cp "app/src/androidTest/assets/fixtures/audit71/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg" \
   "违规案例/"
```

复制后 `违规案例/` 135 → 137 张图,与 audit71 71 张共同覆盖 #01-#137 全部 137 个 fixture。

**为什么不删除 audit71**:audit71 是 androidTest 端 fixture(`assets/fixtures/audit71/`),走 `connectedAndroidTest` 真机 e2e 路径;`违规案例/` 是用户本地 staging 目录(`.gitignore`)。两者用途不同,不能合并。

---

## 4. 测试策略

### 4.1 JVM 单元测试(`AdSignageRuleMatcherTest.kt` 新增 case)

新增 4 组 case 验证新增的 gate / keyword:

```kotlin
// Case A: #113 政府公益展不应命中 national_political_symbol_misuse
@Test fun `113 国庆图书惠民公益展 不命中 national_political_symbol_misuse`() {
    val text = "哈尔滨新闻出版局 国庆图书惠民公益展 庆国庆"
    val hits = matcher.scan(text)
    assertTrue(hits.none { it.ruleId == "ad_signage_signage_national_political_symbol_misuse" })
}

// Case B: #127 人民咖啡馆国庆立牌应命中 (商业品牌,gate 不阻断)
@Test fun `127 人民咖啡馆国庆立牌 命中 national_political_symbol_misuse`() {
    val text = "人民咖啡馆 庆国庆 天安门 国庆立牌"
    val hits = matcher.scan(text)
    assertTrue(hits.any { it.ruleId == "ad_signage_signage_national_political_symbol_misuse" })
}

// Case C: #86 烟酒零售门头不应命中 alcohol_drink_scenario
@Test fun `86 兰泽烟酒零售门头 不命中 alcohol_drink_scenario`() {
    val text = "兰泽烟酒零售门头 酒类销售 招牌"
    val hits = matcher.scan(text)
    assertTrue(hits.none { it.ruleId == "ad_signage_signage_alcohol_drink_scenario" })
}

// Case D: #18 白酒电商页应命中 (闻香等真实饮用场景)
@Test fun `18 白酒电商页 命中 alcohol_drink_scenario`() {
    val text = "白酒 闻香 天然桦树清香 与酒香交织 入口 层次丰富 清爽顺滑"
    val hits = matcher.scan(text)
    assertTrue(hits.any { it.ruleId == "ad_signage_signage_alcohol_drink_scenario" })
}

// Case E: #55 前列腺养护应命中 food_disease_target
@Test fun `55 前列腺养护 命中 food_disease_target`() {
    val text = "京东京造番茄红素沙棘果油 前列腺养护 护前列腺炎 尿频尿急"
    val hits = matcher.scan(text)
    assertTrue(hits.any { it.ruleId == "ad_signage_signage_food_disease_target" })
}

// Case F: #13 豌豆饱满应命中 seed_yield_guarantee
@Test fun `13 豌豆多且饱满 命中 seed_yield_guarantee`() {
    val text = "豌豆种子 多且饱满 高产 籽粒饱满"
    val hits = matcher.scan(text)
    assertTrue(hits.any { it.ruleId == "ad_signage_art27_seed_yield_guarantee" })
}

// Case G: #11 公安专项应命中 edu_art24_test_authority
@Test fun `11 公安专项 命中 test_authority`() {
    val text = "公安专项 秋考刷题班 高效提分 警员培训"
    val hits = matcher.scan(text)
    assertTrue(hits.any { it.ruleId == "ad_signage_edu_art24_test_authority" })
}

// Case H: #15 国潮茶文化应命中 re_art26_planned_facility
@Test fun `15 国潮茶文化 命中 planned_facility`() {
    val text = "银泰集茶巷 品牌加冕 财富实力启航 主题商街 国潮茶文化"
    val hits = matcher.scan(text)
    assertTrue(hits.any { it.ruleId == "ad_signage_re_art26_planned_facility" })
}
```

(共 8 组 case,每组对应 1 个 fixture 的预期行为变化)

### 4.2 既有回归测试

`AdSignageRuleMatcherTest` + `AdSignageTextFixtureRegressionTest` + `AdSignageImageAuditSixtySixRegressionTest` / `AdSignageMentorFiveImageRegressionTest` 必须仍然通过 — 新增的 gate 不得破坏既有命中。

### 4.3 不动真机回归

**真机 e2e 回归(connectedAndroidTest + PaddleOCRv6 真模型)留作下个发版号的 smoke**。本 spec 不包含真机回归(JVM 单元测试覆盖规则层;真机 OCR 召回由下个发版号回归保证)。

---

## 5. 不在范围内(留作下个发版号或用户动作项)

| 项 | 描述 | 处理 |
|---|---|---|
| #112 fixture 命名校正 | 真机 OCR 出「亚冬会 / 冰雪同梦」与 slug「茶饮本草」不一致 | 用户动作项 — 待用户核对 fixture #112 真实内容后改 slug |
| #115 fixture 命名校正 | 4 类规则同时命中异常,需人工核对 | 用户动作项 |
| text_fixtures README | 39 个 text_*.md fixture 缺目的说明 | 下个发版号补 |
| 12-19.jpg 原始素材复核 | 17 张底层 jpg 与命名错位 | 用户动作项 — 不可自动化,需逐图核对 |
| 真机回归 #55 / #11 / #13 / #23 / #86 / #112 / #113 / #115 | 跑 connectedAndroidTest | 下个发版号 smoke |

---

## 6. 风险与对策

| 风险 | 概率 | 对策 |
|---|---|---|
| `categoryAnchorsAbsent` 阻断过多,既有命中掉 | 低 | §4.2 既有回归测试 + 4 case 覆盖空集/命中集双向 |
| 新 keyword 与既有 rule 撞词,触发误命中 | 低 | keyword 加完跑全量 JVM 回归(`./gradlew.bat testDebugUnitTest`) |
| 用户 fixture 编号混乱(#68/#100 sync 错位) | 低 | 复制前 md5 校验 + 复制后 fixture 数比对(135 → 137)|
| 真机回归发现新 FP | 中 | §5 留作下个发版号 smoke,不阻塞本次规则改造 |
| 用户原 12-19.jpg 素材错位影响 audit71 fixture 内容 | 高(但本次不涉及) | 留作用户动作项 |

---

## 7. 实施步骤概要

1. 编辑 [`ad_signage_rules.json`](../../app/src/main/assets/rules/ad_signage_rules.json) — 给 #113 / #86 加 `categoryAnchorsAbsent`,给 #55 / #13 / #23 / #11 / #15 扩 keyword
2. 编辑 [`AdSignageRuleMatcherTest.kt`](../../app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt) — 加 8 组 case(§4.1)
3. 复制 fixture `audit71/` → `违规案例/`(#68 / #100)
4. 跑 `./gradlew.bat testDebugUnitTest` 验证全量 JVM 单元测试 + 新增 case 全部通过
5. 跑 `./gradlew.bat lintDebug`(可选,lint vital 已禁用)
6. commit(作者 AlexMultiAgent,无 Co-Authored-By trailer)
7. push + 触发 release 流水线(本次非 release,只 commit + push 即可)

---

## 8. 关联文档

- [主君 2026-09-30 audit 报告](../mentors/audit-2026-09-30.md)(本 spec 是它的实施 counterpart)
- [CLAUDE.md](../../CLAUDE.md) §"Commit 策略(必读)" — 作者 / trailer 约束
- [CLAUDE.md](../../CLAUDE.md) §"模型 profile 系统" — `ice_ocr_rules` profile 提示
- [CLAUDE.md](../../CLAUDE.md) §"每次发版 / 每次 commit 必跑" — JDK 17 export
- [CLAUDE.md](../../CLAUDE.md) §"Claude Code 自动化" — `validate-rule-json.js` hook + `add-rule-entry` skill