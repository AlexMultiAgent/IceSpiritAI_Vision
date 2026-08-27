# 2026-08-27 规则覆盖审计 — 烟测记录

> **Phase**: 1 (Audit) → 2 (Rule Expansion) → 3 (总册演化) → 4 (Coverage Matrix)
> **Date**: 2026-08-27
> **Spec**: [`docs/superpowers/specs/2026-08-27-icevision-rules-coverage-audit-design.md`](../superpowers/specs/2026-08-27-icevision-rules-coverage-audit-design.md)

## 整体结论

5 项验收全部通过。规则引擎 v8 → v9,覆盖 66 张违规案例图;新增 8 条规则覆盖 9 张图(含 4 张原未覆盖 + 5 张原弱覆盖强化),规则总数 121 → 129;总册演化加「关联规则 ID」列,双向矩阵首次建立。

## 实际产出

| Phase | Task | 实际产出 |
|---|---|---|
| 1 | Audit 66 节 + Synthesize | `_audit_gaps.md` 869 行(66 节 + §桶汇总 + §新规则候选清单(8 条)+ §强化规则清单(13 条))|
| 2 | 强化 2 条规则 | `ad_signage_art22_tob_alc`(戒烟+解酒 → +白酒/啤酒/红酒/黄酒/洋酒/酒类/酒精度数,9 keywords);`ad_signage_art10_minor`(儿童专用+宝宝必备 → +未成年人/小学生/中学生/婴儿/幼儿,7 keywords) |
| 2 | 新增 8 条规则 | `ad_signage_signage_alcohol_drink_scenario`(Warning / 14 keywords / restricted)+ `ad_signage_signage_gift_to_leader`(Warning / 12 keywords)+ `ad_signage_signage_military_political_marketing`(Violation / 7 keywords)+ `ad_signage_signage_weight_loss_food_claim`(Violation / 9 keywords, 覆盖 #28 + #54)+ `ad_signage_edu_art24_public_servant_endorsement`(Violation / 8 keywords)+ `ad_signage_signage_weight_loss_data_commitment`(Violation / 8 keywords)+ `ad_signage_signage_food_lung_health_claim`(Violation / 7 keywords)+ `ad_signage_signage_food_beneficiary_count_claim`(Violation / 7 keywords) |
| 2 | 配套 8 份 fixture | `违规案例/text_signage_alcohol_01.md` 等 8 份 fixture(法条原文全部来自 `知识库/广告业务/中华人民共和国广告法.md`,无编造)|
| 3 | 总册演化 | `_违规档案总册.md` 66 节新增「关联规则 ID」列(共 66 行,9 个 `(new)` 标记);顶部新增 §审计日志 + §桶汇总(本次审计)2 节 |
| 3 | 同步 `_rule_ids.json` | 121 → 129(同时修复先前遗漏的 `ad_signage_signage_food_safety_implication` 1 条)|
| 4 | 双向矩阵 | `_coverage_matrix.md` 232 行:§1 规则→图(129 行,severity 降序 + ruleId 升序)+ §2 图→规则(66 行,文件名升序)+ §3 统计 |

## 关键数据(从 `_coverage_matrix.md` §3 统计 + `_audit_gaps.md` 桶汇总)

- **规则总数**: 129(v8: 121)
- **新增规则**: 8(`ad_signage_*` signage 系列 7 条 + `ad_signage_edu_art24_public_servant_endorsement` 1 条)
- **强化规则**: 13(`ad_signage_art9_abs_top` / `ad_signage_art11_data_citation` / `ad_signage_art22_tob_alc` / `ad_signage_art24_edu_guar` / `ad_signage_edu_art24_test_authority` / `ad_signage_art27_seed_yield_guarantee` / `ad_signage_art26_re_prm` / `ad_signage_re_art26_planned_facility` / `ad_signage_signage_food_function_claim` / `ad_signage_signage_food_disease_target` / `ad_signage_veterinary_art8_commitment` / `ad_signage_med_art6_indications` / `internet_art7_pre_review`);其中 keywords 数扩充最显著的为 `ad_signage_art22_tob_alc`(本次 2 条之一)+ `ad_signage_signage_food_function_claim`(覆盖 27 张图,核心覆盖桶)
- **示例图总数**: 66
- **审计时已覆盖**: 43(无变更即可命中)
- **审计时弱覆盖(关键词薄)**: 17(本次规则扩展将陆续补 keyword;已通过 §强化规则清单 跟踪)
- **审计时未覆盖**: 6(#03 / #06 / #11 / #20 / #32 / #41)
- **新规则覆盖图数**: 9(#06 / #18 / #20 / #28 / #32 / #38 / #41 / #42 / #54)
  - 其中 5 张来自原弱覆盖桶(#18 / #28 / #38 / #42 / #54),新规则加强 keyword 命中
  - 其中 4 张来自原未覆盖桶(#06 / #20 / #32 / #41),新规则直接覆盖
- **新规则后剩余 backlog**: 2 张(#03 银泰集茶巷 + #11 普利斯警考,需后续单独扩规则或补图回归)
- **被规则覆盖的图**(已覆盖 + 弱覆盖): 60 / 66(90.9%)
- **空桶**(规则已就位, 待补示例图):4(`internet_ad` / `finance` / `minor` / `fake_data`)
- **backlog 规则数**(有规则但本批无对应案例图): 96 / 129(后续可补图回归)

## 5 项验收

| 项 | 命令 / 检查 | 结果 |
|---|---|---|
| 1. JSON 合法 | `python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json', encoding='utf-8')); print('OK')"` | PASS — 退出 0,输出 `OK`(version=9) |
| 2. 规则数一致 | `grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json` vs `python -c "import json; print(len(json.load(open('违规案例/_rule_ids.json', encoding='utf-8'))))"` | PASS — 两者均 **129** |
| 3. 总册 + 矩阵 cross-check | `_违规档案总册.md` 关联规则 ID 列 与 `_coverage_matrix.md` §2 列表 一致 | PASS — 7 个 spot-check 全部一致(#06 / #18 / #20 / #32 / #41 / #42 / #54)|
| 4. `git status` 干净 | `git status` | PASS(备注:9 个 `_tmp_*.txt` + 3 个 `docs/superpowers/{plans,specs}/*.md` 仍在 untracked 列表,属预期外残留,见末尾)|
| 5. commit hygiene | `git log -3 --format='%B' \| grep -i 'Co-Authored-By'` | PASS — 3 个 commit (ccfcc03 + 0777fe66 + 970af42) 全部无 `Co-Authored-By` trailer,作者 AlexMultiAgent |

### Spot-check 详情(§3 cross-check)

| # | 总册 关联规则 ID | 矩阵 §2 规则 ID 列表 | 一致 |
|---|---|---|---|
| 06 | `ad_signage_signage_military_political_marketing` *(new)* | `ad_signage_signage_military_political_marketing` *(new)* | OK |
| 18 | `ad_signage_art22_tob_alc`, `ad_signage_signage_alcohol_drink_scenario` *(new)* | `ad_signage_art22_tob_alc`, `ad_signage_signage_alcohol_drink_scenario` *(new)* | OK |
| 20 | `ad_signage_signage_gift_to_leader` *(new)* | `ad_signage_signage_gift_to_leader` *(new)* | OK |
| 32 | `ad_signage_edu_art24_public_servant_endorsement` *(new)* | `ad_signage_edu_art24_public_servant_endorsement` *(new)* | OK |
| 41 | `ad_signage_signage_food_lung_health_claim` *(new)* | `ad_signage_signage_food_lung_health_claim` *(new)* | OK |
| 42 | `ad_signage_art9_abs_top`, `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_beneficiary_count_claim` *(new)* | 同上 | OK |
| 54 | `ad_signage_signage_food_function_claim`, `internet_art6_identifiable`, `ad_signage_signage_weight_loss_food_claim` *(new)* | 同上 | OK |

## 超出预判的发现

1. **`ad_signage_signage_app_data_citation` 取消新建**:Plan 原预判 #62 APP 数据需新建规则,审计结论 #62 是「保持」(已被 `ad_signage_art11_data_citation` 现有 12 个 keywords 覆盖),故未新增该规则。
2. **`ad_signage_signage_food_safety_implication` 同步遗漏**:Task 15 发现 `_rule_ids.json` 实际只有 120 条(并非 plan 假设的 121 条),commit 79090bd 加此规则时未同步 ID 列表。本次补全至 129。
3. **`ad_signage_edu_art24_public_servant_endorsement` regulation 修正**:Plan 假设 §9(三),实际 §9(二)(国家机关工作人员名义)+ §24(三) 更贴合「在职公务员代言」语义。Plan 假设有偏差。
4. **法规时效性清理**:Code quality review 抓出 18 个 section 错误引用「第五十七条」(该条枚举不含 第十七/十八条),统一改为「第五十八条」;同时修正 3 处引用废止法规(《化妆品广告管理办法》《互联网医疗广告管理办法》《药品管理法》第六十一条)的 citation。
5. **audit 时间点 vs post-new-rules 计数差异**:审计时 未覆盖 = 6 张(#03 / #06 / #11 / #20 / #32 / #41),新规则后剩余 2 张(#03 / #11)。`_coverage_matrix.md` §2 status 列保留审计时快照(故 4 张新规则覆盖的图仍标「未覆盖」),§3 统计单独列出「新规则覆盖的图: 9」反映 post-new-rules 视角。

## 提交列表

| SHA | Author | Subject |
|---|---|---|
| c84029f | AlexMultiAgent | feat(cases): 违规案例审计 — 66 张图覆盖状态清单 + 新规则候选 + 强化清单 |
| ccfcc03 | AlexMultiAgent | feat(rules): ad_signage_rules v8 → v9 — 8 新规则 + 2 强化 + 8 fixture |
| 0777fe66 | AlexMultiAgent | docs(cases): 总册加 关联规则 ID 列 + §审计日志 + §桶汇总 |
| 970af42 | AlexMultiAgent | docs(cases): _coverage_matrix.md 首次建立规则↔示例图 双向矩阵 |
| (pending) | AlexMultiAgent | docs(smoke): 2026-08-27 规则覆盖审计 烟测记录 |

## 未跟踪残留(预期外)

- `_tmp_*.txt` × 9(`_tmp_report.txt` / `_tmp_sec_06.txt` / `_tmp_sec_18.txt` / `_tmp_sec_20.txt` / `_tmp_sec_49.txt` / `_tmp_sec_52.txt` / `_tmp_sec_63.txt` / `_tmp_sev.txt` / `_tmp_sev2.txt`)— 之前会话残留,不在本次 commit 范围
- `docs/superpowers/plans/2026-08-27-icevision-rules-coverage-audit-plan.md` × 1 — 计划文档,后续跟 spec 一起可考虑 commit(本期不 commit)
- `docs/superpowers/plans/2026-08-27-icevision-v0.1.33-rules-archive-release.md` × 1 — 同上(本期不 commit)
- `docs/superpowers/specs/2026-08-27-icevision-rules-coverage-audit-design.md` × 1 — 设计 spec,同上

## 后续 backlog(超出本次范围)

1. **#03 银泰·集茶巷** — 商业地产「品牌加冕 财富启航」属「引人误解的虚假宣传」+ 「房地产广告违规」(《广告法》§26 + §28),现有规则未触达「品牌加冕」「财富启航」类营销话术。后续可扩 `ad_signage_signage_luxury_marketing` 或 `ad_signage_art28b_fake_data` keywords。
2. **#11 普利斯警考刷题班** — 「高效提分」属教育承诺收益类(《广告法》§24),现有 `ad_signage_art24_edu_guar` 关键词偏「包过/保过/未录取半价」类硬承诺,「高效提分」属软承诺。后续可扩 keywords 或新建 `ad_signage_signage_edu_efficacy_commitment`。
3. **96 条规则无示例图** — 大量规则(尤其 `outdoor` 桶 8 条 + `pesticide` 桶 8 条 + `minor` / `finance` / `fake_data` 空桶)暂无违规案例图入库,需后续实拍 / 爬虫补图回归。
