# ad_signage 域 判别表面 — 2026-08-19 快照

> v0.1.10 正式 release(`release id=187` @ Gitea tag `latest`)后,广告招牌域规则在
> 端侧可判别的违规面完整快照。用于打磨方向立项 / 未来维护者 onboarding /
> 跨项目对照(冰灵慧语 / 冰灵智译)。

## 0. 总览

| 字段 | 值 |
|---|---|
| 规则 JSON 版本 | 4 |
| modelProfile | `ice_ocr_rules`(PaddleOCR v3.7.0 + ONNX Runtime + OpenCV) |
| 规则总条数 | **116** |
| category 数 | **13** |
| severity 等级 | **3**(`Violation` / `Warning` / `Info`) |
| 匹配器 | HankCS Aho-Corasick double-array trie |
| 归一化 | `TextNormalizer.forMatching`(全/半角 + 空格 + 繁简) |
| 输入形态 | OCR 文本字符串 |
| 触发条件 | 归一化后关键词命中规则 `keywords` 任一 |
| 命中产物 | `RuleHit(ruleId, matchedText, category, regulation, lawText, severity, domain="ad")` |

## 1. 匹配管线

入口:[AdSignageRuleMatcher.kt](app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt)

```
OCR 文本
  └─ TextNormalizer.forMatching(归一化全/半角 + 空格 + 繁简)
       └─ AhoCorasickDoubleArrayTrie.parseText(归一化文本)
            └─ 每个 (ruleId ∈ matched) 产出 RuleHit
                 └─ 去重:同 (ruleId, matchedText) 只记一次
```

- **关键词共享**:同一关键词被多条规则引用时(如 `"第一"` 同时在 education + absolute 两条规则),所有适用规则都记一条 hit,不去重
- **同规则内 dedup**:同一 `ruleId` 在同一文本里多次命中同一 `matchedText` 只记一次
- **跨规则不 dedup**:一张招牌可同时命中 5–12 条不同规则的关键词
- **不做上下文/否定/反讽判断**:输 "本店不做医疗" 也会命中 "医疗" 之类关键词

## 2. 规则覆盖总览

### 2.1 category × severity 矩阵

| category | Violation | Warning | Info | 总计 |
|---|---:|---:|---:|---:|
| medical | 5 | 10 | 3 | 18 |
| finance | 3 | 9 | 1 | 13 |
| cosmetic | 3 | 6 | 3 | 12 |
| outdoor | 0 | 11 | 0 | 11 |
| pesticide | 2 | 8 | 0 | 10 |
| veterinary | 2 | 8 | 0 | 10 |
| internet_ad | 4 | 5 | 0 | 9 |
| signage | 2 | 6 | 1 | 9 |
| absolute | 3 | 4 | 0 | 7 |
| realestate | 0 | 7 | 0 | 7 |
| education | 1 | 3 | 0 | 4 |
| restricted | 3 | 0 | 1 | 4 |
| minor | 1 | 0 | 1 | 2 |
| **合计** | **29** | **77** | **10** | **116** |

### 2.2 各 category 法规依据与典型规则 id

| category | 法规依据 | 典型规则 id(节选) |
|---|---|---|
| **medical** | 《广告法》§16 + 《医疗广告管理办法》§6/§7/§11/§13 | `ad_signage_med_art6_indications`, `med_art7_technicality`, `med_art7_compare`, `med_art7_army`, `med_art11_qualifications`, `med_art13_newsform` |
| **finance** | 《广告法》§25 招商有投资回报预期 | `ad_signage_art25_fin_prm`, `ad_signage_fin_art25_endorsement`, `ad_signage_fin_art25_unlawful` |
| **cosmetic** | 《化妆品监督管理条例》§23/§25 | `cosmetic_art23_medical_explicit`, `cosmetic_art20_claim_basis` |
| **outdoor** | 《广告法》§14/§32/§46 + 户外广告登记规定 | `ad_signage_outdoor_art14_cert_no` |
| **pesticide** | 农药管理条例 + 农药广告管理办法 | `ad_signage_pesticide_art2_unregistered` |
| **veterinary** | 兽药管理条例 + 兽药广告管理办法 | `ad_signage_veterinary_art3_prohibited` |
| **internet_ad** | 《互联网广告管理办法》 | `internet_art6_identifiable` |
| **signage** | 招牌本身规约(医疗标志、OTC 等) | `ad_signage_signage_medicine_flag` |
| **absolute** | 《广告法》§9 绝对化用语 + §11 行政许可 + §12 专利 + §28 虚假广告 | `ad_signage_art9_abs_top`, `art9_abs_pct`, `art9_abs_emblem`, `art9_abs_authority`, `art9_abs_superstition`, `art12_fake_patent`, `art28b_fake_data` |
| **realestate** | 《广告法》§26 + 房地产广告发布规定 | `ad_signage_art26_re_prm` |
| **education** | 《广告法》§24 | `ad_signage_art24_edu_guar`, `edu_art24_recommendation`, `edu_art24_test_authority` |
| **restricted** | 《广告法》§22/§23 + 烟草广告管理办法 | `ad_signage_art22_tob_alc` |
| **minor** | 《广告法》§10 + 食品标识监督管理办法 §8 | `ad_signage_art10_minor` |

## 3. 命中样例(假设 OCR 文本)

| 招牌文本 | 命中规则 |
|---|---|
| "本店根治 XX 疾病,治愈率 100%" | `ad_signage_art16_med_abs` (V) + `art16_med_health` (V) + `art9_abs_pct` (W) |
| "稳赚不赔,无风险,保本高收益" | `ad_signage_art25_fin_prm` (V) |
| "国家级 / 最高级 / 第一" | `ad_signage_art9_abs_top` (W) + `art9_abs_pct` (W) |
| "本台讯:某医院专治 XX" | `ad_signage_med_art13_newsform` (W) + `med_art6_indications` (W) |
| "儿童专用 / 宝宝必备" | `ad_signage_art10_minor` (I) |
| "国家专利 ZL..." | `ad_signage_art12_fake_patent` (W) |
| "学区房包入学 / 升值回报" | `ad_signage_art26_re_prm` (W) |
| "解放军医院 / 部队医院" | `ad_signage_med_art7_army` (W) |
| "销量第一 / 全网第一" | `ad_signage_art28b_fake_data` (W) |
| "考试命题人 / 阅卷老师授课" | `ad_signage_edu_art24_test_authority` (W) |
| "买二送一,买三送一,买五送二" | `ad_signage_art9_abs_top` (W) — "第一" 误命中坑 |
| "本店不做医疗" | `ad_signage_med_art6_indications` (W) — negation 缺位 |

## 4. 能力边界

能做:
- 关键词命中识别违规声称
- 多规则 co-occurrence(同一招牌可同时命中多类违规)
- 法规条文 + 罚款额度回溯(`regulation` + `lawText` 字段)
- 严重度三级分流(`Violation` / `Warning` / `Info`)

不能做:
- 上下文/否定/反讽判断
- 图文不符 / 视觉比对
- 版面识别(竖排、表格线、密集招牌)
- 二分类软识别(给定图片 → 是否含违规)
- 同类别内归一化严重度到具体罚款金额(当前只暴露 V/W/I,法规对应罚款金额为 §55/§57/§58/§59 各条,未自动反查)

## 5. 关联文件

| 路径 | 用途 |
|---|---|
| [app/src/main/assets/rules/ad_signage_rules.json](app/src/main/assets/rules/ad_signage_rules.json) | 116 条规则源(v4) |
| [app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt](app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt) | Aho-Corasick 匹配器 |
| [app/src/main/java/com/icespiritai/offline/domain/TextNormalizer.kt](app/src/main/java/com/icespiritai/offline/domain/TextNormalizer.kt) | 全/半角 + 空格 + 繁简归一化 |
| [app/src/main/java/com/icespiritai/offline/domain/CategoryDisplay.kt](app/src/main/java/com/icespiritai/offline/domain/CategoryDisplay.kt) | category → 中文显示标签 |
| [app/src/main/java/com/icespiritai/offline/domain/RuleHit.kt](app/src/main/java/com/icespiritai/offline/domain/RuleHit.kt) | 命中结构 |
| [app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt](app/src/main/java/com/icespiritai/offline/rules/AdSignageRule.kt) | 规则数据类 |
| [app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt](app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt) | 关键词命中 / 去重测试 |
| [知识库/广告业务/](知识库/广告业务/) | 13 份法规 markdown KB(`regulation` / `lawText` 字段摘抄自此) |
| [docs/smoke/2026-08-14-phase1-smoke.md](docs/smoke/2026-08-14-phase1-smoke.md) | Phase 1 PaddleOCR SDK 上手 + 真实设备烟测 |

## 6. 打磨方向(参考,未立项)

| 维度 | 当前 | 候选 |
|---|---|---|
| 严重度 | 3 级硬编码 | 映射到具体罚款金额区间(《广告法》§55/§57/§58/§59 各条) |
| negation | 无 | 否定/反讽上下文检测(降低 "本店不做医疗" 之类误报) |
| co-occurrence | 平铺 | 输出 "违规面摘要"(同招牌多规则 → 1 段话) |
| 现实密度 | 关键词命中即触发 | 频率阈值(同关键词 3 次以上 vs 1 次) |
| 类别 | 13 类固定 | 拆 / 合 / 重命名(依据现场观测) |
| 关键词 | 116 条规则一次性 grep | 负样本回归测试集(对每个规则构造 1 条反例) |
| 视觉 | 不做 | 图片整体性(竖排 / 表格线 / 密集招牌)→ Phase 2 |

---

## 变更记录

| 日期 | 事件 |
|---|---|
| 2026-08-19 | v0.1.10 release 后落地;对照 116 条规则 v4 生成当前快照 |
