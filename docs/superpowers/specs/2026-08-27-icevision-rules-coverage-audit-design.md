# 冰灵锐目 — 违规案例全面审计 + 规则覆盖补强 + 档案总册演化

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-27 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联阶段 1 设计 | `docs/superpowers/specs/2026-08-13-icevision-phase1-ocr-rules-design.md` |
| 关联案例采集 spec | `docs/superpowers/specs/2026-08-24-doubletap-fix-and-violation-cases-design.md`(子项目 B) |
| 关联最近 规则 commit | `79090bd feat(rules): ad_signage_signage_food_safety_implication 暗示安全性规则` |
| 关联最近 案例归档 commit | `6290379 feat(cases): 违规案例图片归档 — 66 张 + 总册(14 桶 / Critical×59 + Warning×7)` |

本文档覆盖三步复合任务的统一设计:

1. **全面审计 66 张违规案例图** — 逐张覆盖状态分析
2. **对应更新违规档案总册** — 加 `关联规则 ID` 列 + §审计日志
3. **完善 ad_signage_rules.json** — 补新规则 + 强化两条弱规则 + 跨覆盖矩阵

OCR 模型 + 规则引擎后端逻辑 + 构建系统 + sourceSet 拆分**完全保持现状**,不动。

---

## 1. 背景与目标

### 1.1 现状

- `违规案例/` 下 66 张违规广告图(扁平布局,14 个逻辑桶),commit `6290379` 归档
- `app/src/main/assets/rules/ad_signage_rules.json` v8,**121 条规则**(commit `79090bd` 之后)
- `_违规档案总册.md` 922 行,8 列表格(文件名 / 原始违法广告语 / 违规情形 / 违反法条 / 桶分类 / 严重度 / 处理建议 / 备注)
- 仅 **3 处 pin 了具体 ruleId**(`ad_signage_signage_food_function_claim` / `ad_signage_signage_food_safety_implication` / `cosmetic_art9_abs_extended`),其余按桶分类
- 弱规则:`ad_signage_art22_tob_alc`(restricted/Info, n=2,无 negation)、`ad_signage_art10_minor`(minor/Info, n=2,无 negation)
- 空桶:`internet_ad`(规则 9 条已存在,无示例图)、`finance`(规则 13 条已存在,无示例图)

### 1.2 跨覆盖 gap(预判)

| 桶 | gap 类型 | 备注 |
|---|---|---|
| signage × 白酒 | 弱覆盖 | 仅 `art22_tob_alc`(Info)兜底,应新增白酒场景专项规则或强化 |
| minor × 黑尊牛「送领导」 | 未覆盖 | 现有 minor 规则锚「未成年人」,不锚「送领导 / 上级 / 老板」 |
| internet_ad × APP 数据 | 弱覆盖 | 现有 `art11_data_citation` Warning,但无 APP 专属锚 |
| 跨域 | cross-cite | 既有 `ad_signage_signage_food_disease_target` 的 `regulation` 字段跨域引用食品标识(参见 `memory/followup-ad-signage-cross-cite.md`),新增规则应避免再犯 |

### 1.3 目标

- 66 张图全部审到,产出覆盖状态清单 `_audit_gaps.md`
- ad_signage_rules.json v8 → v9,新增 3-5 条规则 + 强化 2 条弱规则
- 总册加 `关联规则 ID` 列(每张图填到具体 ruleId)
- 产出 `_coverage_matrix.md` 双向矩阵(规则 ↔ 示例图)
- 不破坏现存 4 张测试集回归(commit `d53aa02` 锚定的 `text_medical_ykzp_01` 等)

### 1.4 非目标(本期)

- ❌ 不重归类总册桶(审计保守原则)
- ❌ 不为新增规则写 JUnit matcher 单元测试(项目只有子集规则有测试)
- ❌ 不自动化生成 `_coverage_matrix.md`(避免引入 JSON 反向解析工具)
- ❌ 不删除空桶(internet_ad / finance)
- ❌ 不动 `food_label_rules.json`(本轮 focus 是 ad_signage)
- ❌ 不动 OCR 模型 / sourceSet / build 系统

---

## 2. 总体方案:顺序流水线(Approach A)

按 Phase 1 → 4 顺序执行,每 Phase 完成后跑验收(§6)再进下一 Phase。

| Phase | 产出 | 依赖 |
|---|---|---|
| 1. Audit | `_audit_gaps.md`(66 节)+ 新规则候选清单 | 现行归档 |
| 2. Rule expansion | `ad_signage_rules.json` v9 + 新增 `text_*.md` × N | Phase 1 |
| 3. 总册演化 | `_违规档案总册.md`(8 → 9 列 + §审计日志 + §桶汇总 2 行)+ `_rule_ids.json` 同步 | Phase 2 |
| 4. Coverage matrix | `_coverage_matrix.md`(双向矩阵 + §3 统计) | Phase 3 |

> 备选 Approach(已否决):B 按桶迭代(13 次循环,跨桶规则重复劳动)/ C 矩阵先行(121×66 格,信号稀释)。

---

## 3. Phase 1 — Audit(图像审计)

### 3.1 输入

- `违规案例/` 66 张图(扁平,文件名 NN_subject_violation.ext)
- 现行 `ad_signage_rules.json` v8(121 条)
- 现行 `_违规档案总册.md`(922 行,8 列)
- 现行 `_rule_ids.json`(90 条)

### 3.2 输出:`_audit_gaps.md`

每张图一节(共 66 节),字段:

| 字段 | 说明 |
|---|---|
| 文件名 | NN_subject_violation.ext |
| 桶分类 | 沿用总册 |
| 违规描述 | 1-2 句精简 |
| 现行覆盖规则 | ruleId 列表,空 = 未覆盖 |
| 覆盖状态 | `已覆盖` / `弱覆盖(关键词薄)` / `未覆盖` |
| 建议动作 | `补新规则` / `强化现规则 keywords` / `保持` |
| 关联法条 | 沿用总册 |

### 3.3 审计方法

- Read 工具逐张读图(支持图像视觉呈现)
- 识别违规模式后交叉对照 121 条规则 keywords
- 重点核查总册 `备注` 列的 post-hoc 修正痕迹(已观察到「修正原『合规参照样本』误判」「修正文件 slug 严重错配」),确认桶归类已稳定

### 3.4 自检门槛

- 66 张图全部审到,每张图对应一节
- 至少识别 3-5 张需新规则的图(白酒 / 黑尊牛送领导 / APP 数据等)
- 至少识别 2 条需强化的弱规则(`art22_tob_alc` / `art10_minor`)
- 桶分类与总册一致(不擅自重归类,有异议写在 `建议动作` 行)

### 3.5 风险与边界

- OCR 难检的小字漏检 → `覆盖状态` 标 `可能漏检`,留真机 A/B 二次确认
- 桶重分类保守 → 审计阶段不擅自动总册桶归类,有异议写在 `建议动作` 行,留 commit 时确认

### 3.6 Phase 1 验收

- `ls 违规案例/_audit_gaps.md` 存在
- `grep -c '^## ' _audit_gaps.md` ≥ 66
- 每节 `建议动作` 字段非空
- 新规则候选清单覆盖至少 3-5 个 gap

---

## 4. Phase 2 — Rule Expansion(`ad_signage_rules.json` v9)

### 4.1 输入

- Phase 1 产出 `_audit_gaps.md`(新规则候选清单)

### 4.2 预判候选清单(audit 完成后会修订)

| ruleId | category | severity | 触发场景 | 预期关联图 |
|---|---|---|---|---|
| `ad_signage_signage_alcohol_drink_xxx`(或强化 `art22_tob_alc`) | restricted | Violation | 白酒/酒类广告未限场景 | #18 白酒 |
| `ad_signage_signage_gift_to_leader` | minor | Warning | 「送领导 / 送上级 / 送老板」 | #20 黑尊牛 |
| `ad_signage_signage_app_data_citation` | internet_ad | Warning | APP 内的"用户量 / 下载量 / 排名"数据未注明出处 | #62 APP 数据 |

> audit 阶段实际扫描后可能增删这张表(也可能补全缺失的「医疗」「教育」桶规则)。

### 4.3 强化的两条弱规则

| ruleId | 现状 | 强化方向 |
|---|---|---|
| `ad_signage_art22_tob_alc` | restricted/Info, n=2, 无 negation | keywords 加 `白酒 / 啤酒 / 红酒 / 黄酒 / 洋酒 / 酒类`;negation 加 `不向未成年人` |
| `ad_signage_art10_minor` | minor/Info, n=2, 无 negation | keywords 加 `未成年人 / 儿童 / 小学生 / 中学生 / 宝宝 / 婴儿`;negation 加 `非儿童 / 不含未成年人` |

### 4.4 Schema 约束

- 每个新规则必须有完整字段:`ruleId` / `regulation`(引 `知识库/<域>/<现行法规>.md`,CLAUDE.md 红线)/ `category` / `severity` / `keywords`(≥ 3)/ `negation`(适用时)/ `description` / `examples`(可选)
- 弱规则强化保持 `ruleId` 不变(不删不重建),只改 `keywords` / `negation` 数组
- `version` 字段:`8 → 9`
- 沿用现有 JSON 风格(顶层 `version` / `rules` 双键,规则按 ruleId 字典序或类别分组均可)
- 新规则的 `regulation` 必须引 `知识库/广告业务/<现行法规>.md`(或 `已废止` 中明确标为「现行有效」过渡条款的),不得引已废止法规(2026-08-27 知识库时效性整理已执行,见 CLAUDE.md 「知识库时效性整理」)

### 4.5 测试与文档约定

- 不强制为新规则加 JUnit matcher 单元测试(项目目前 121 条规则也只有子集有 `text_*.md`)
- 沿用现有约定:为每个新增 / 强化规则配一份 `text_<ruleId>.md` 分析文件,放 `违规案例/` 下(与已有 43 个对称)
- 这些 `text_*.md` 是「违规原文 vs 规则命中」的手工对照表,后续可作为回归 pin

### 4.6 Phase 2 验收

- `python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json'))"` 合法
- `version == 9`
- 规则数 ≥ 121 + 新增数
- 弱规则 keywords 数 ≥ 3(`grep -A 5 '"ad_signage_art22_tob_alc"'` 验证)
- 弱规则 negation 数组非空

### 4.7 风险与回滚

- audit 阶段发现某 gap 不需要新规则(例如桶已归错)→ 允许「建议动作:保持」并直接跳过对应新规则
- 新增规则的 keywords 与既有规则重叠导致误命中 → 回滚该规则,keywords 收紧到更具区分度的锚词

---

## 5. Phase 3 — 总册演化(`_违规档案总册.md`)

### 5.1 变更范围

| 文件 | 动作 | 详细 |
|---|---|---|
| `_违规档案总册.md` | 加列 | 现有 8 列表格第 5 位插入 `关联规则 ID`(在「违反法条」和「桶分类」之间);空值用 `—`(em dash),有多个用 `, ` 分隔 |
| `_违规档案总册.md` §桶汇总 | 加行 | 在 13 桶汇总表下补 2 行:`internet_ad` / `finance`(标 `规则已就位, 待补示例图` + 规则条数) |
| `_违规档案总册.md` | 顶部加节 | 新增 `## 审计日志` 一节,记录本次审计日期、扫描图数、识别 gap 数、新增/强化规则数 |
| `_audit_gaps.md` | 新增 | Phase 1 产出 |
| `_coverage_matrix.md` | 新增 | Phase 4 产出 |
| `_rule_ids.json` | 更新 | 与新规则保持同步(从 90 → N) |
| `text_<new_rule_id>.md` | 新增 N 份 | 每条新增 / 强化规则配一份分析文件 |

### 5.2 新增列字段定义

`关联规则 ID`:直接对应 `ad_signage_rules.json` 的 `ruleId`。判定逻辑:

- 图被现行 121 条规则覆盖 → 填入 ruleId(可多值)
- 图未被覆盖但已新增规则 → 填入新 ruleId,加 `(new)` 后缀
- 图既无现规则也无新规则 → 填 `—`,但应在 `备注` 列写明 `审计建议:新增 XXX 类规则`(audit 阶段识别但本轮未落地的 gap,留作 backlog)

### 5.3 §桶汇总新增 2 行的格式

```
| internet_ad | 0 | 0 | 0 | 规则已就位(9 条), 待补示例图 |
| finance | 0 | 0 | 0 | 规则已就位(13 条), 待补示例图 |
```

### 5.4 §审计日志新增节格式

```
## 审计日志

- 审计日期:2026-08-27
- 扫描图数:66
- 已覆盖(无变更):N
- 弱覆盖(强化后):N
- 新增规则:M
- 强化规则:K
- 未覆盖 backlog:L
```

### 5.5 §备注列的边界(关键)

- `备注` 列**只承载总册自身的 post-hoc 修正痕迹**(已观察到「修正原『合规参照样本』误判」「修正文件 slug 严重错配」这类行)
- 新规则 backlog **不挤进** `备注` 列,而是去 `_audit_gaps.md` 的 `建议动作` 行
- 这样 `备注` 列的语义不被本次审计污染,后续 git blame 仍可读
- **关于已 pin ruleId 的 3 张图**(见 §1.1):新列填入 ruleId 后,原 `备注` 列里已有的规则引用文本**保留**(作为上下文 / 修正来源,非冗余删除)。`关联规则 ID` 列才是 ruleId 的权威来源

### 5.6 commit 拆分

- 一个 commit:总册列变更 + 桶汇总补充 + 审计日志(纯 markdown)
- 另起 commit:新增 / 强化规则 + `_rule_ids.json` 同步(`feat(rules): ...` 沿用约定)
- 再起 commit:`_coverage_matrix.md` 新增
- 三个 commit 单独 revert 各自不破坏其他

### 5.7 Phase 3 验收

- 总册 diff 仅第 5 列变化 + §桶汇总 2 行 + §审计日志新增,其他列 / 行不动
- 总册 §审计日志数据自洽(扫描图数 66,新增 / 强化数 = Phase 2 实际产出)
- `_rule_ids.json` 长度 = ad_signage_rules.json 实际 `rules.length`

---

## 6. Phase 4 — Coverage Matrix(`_coverage_matrix.md`)

### 6.1 目标

单一文档,双向映射「规则 ↔ 示例图」,让覆盖率一眼可读,backlog 留作下次审计入口。

### 6.2 文件结构(三节)

#### §1 规则 → 示例图

| ruleId | category | severity | 示例图数 | 文件名列表 |
|---|---|---|---:|---|
| `ad_signage_art11_data_citation` | signage | Warning | 1 | #62_xxx.jpg |
| `ad_signage_art22_tob_alc` | restricted | Info | 1 | #18_xxx.jpg |

排序: severity 降序(Violation → Warning → Info)→ 字典序 ruleId

#### §2 示例图 → 规则

| 文件名 | 桶 | 严重度 | 关联规则数 | 规则 ID 列表 | 状态 |
|---|---|---|---:|---|---|
| 18_xxx.jpg | signage | Critical | 1 | ad_signage_art22_tob_alc | 已覆盖(Info,弱) |
| 20_xxx.jpg | minor | Critical | 0 | — | 未关联 |

排序: 文件名升序

状态枚举: `已覆盖` / `弱覆盖(关键词薄)` / `未覆盖` / `未关联(桶空)`(后者指 internet_ad / finance)

#### §3 覆盖率统计

```
- 规则总数:N
- 有示例图的规则:X / N (Y%)
- 无示例图的规则(backlog):(N-X) / N
- 示例图总数:66
- 被多规则覆盖的图:X / 66
- 无规则覆盖的图(backlog):Y / 66
- 弱覆盖(关键词薄)的图:Z / 66
```

### 6.3 生成方法

- 手动填写(本轮只有 66 张图 + N 条规则,121×66 矩阵手填可行)
- 不写脚本(避免引入「新规则 JSON 解析 → 反向校验」的工具依赖)
- 后续若规则数膨胀(> 200)再考虑自动化

### 6.4 与 `_audit_gaps.md` 的关系

- `_audit_gaps.md` 是「过程产物」:每张图的覆盖分析 + 建议动作
- `_coverage_matrix.md` 是「快照产物」:最终覆盖率快照
- 两者关系: audit gaps 走完后,coverage matrix 是其结果的总和;后续规则再增删,只动 coverage matrix,不重写 audit gaps(后者保持「本次审计」语义)

### 6.5 commit

- 单独 commit:`docs(cases): _coverage_matrix.md 首次建立规则↔示例图 双向矩阵`
- 不与总册演化 commit 合并(各自独立 revert)

### 6.6 Phase 4 验收

- 双向统计加和 = 121 + M(新规则)
- `已覆盖 + 弱覆盖 + 未覆盖 = 66`
- `_coverage_matrix.md` §3 统计行算式复核无矛盾

---

## 7. 整体验收(全部 commit 后)

| 项 | 命令 / 检查 | 通过标准 |
|---|---|---|
| JSON 规则文件可被 ServiceLoader 装载 | `./gradlew.bat testDebugUnitTest --tests *AdSignageRuleLoader*` | 通过(loader 不崩) |
| 现存 4 张测试集回归 | 重跑 `docs/smoke/2026-08-20-icevision-v6-upgrade.md` 的 4 张子文件夹 A/B | 命中数 ≥ v6 baseline |
| 总册 + 矩阵互相 cross-check 总册 `关联规则 ID` 列 + 矩阵 §2 列表 | diff | 一致 |
| `git status` 干净 | 无未跟踪 / 未提交残留 | 干净 |
| commit hygiene | `git log -1 --format='%B' \| grep -i 'Co-Authored-By'` | 空(无 Claude trailer) |

### 验收失败应对

| 失败类型 | 应对 |
|---|---|
| JSON 非法 | revert 该 commit,回到上一 phase 重做 |
| 4 张测试集命中数下降 | revert 规则扩展 commit,保留 audit + 总册;Phase 2 重做时 keywords 加保守 |
| 总册 §审计日志数据不自洽 | 修日志,不动其他 phase |
| 跨覆盖矩阵双向加和不闭合 | 重算矩阵;不重启 phase |

### 边界声明(再次明确)

- ❌ 不重归类总册桶(审计保守原则)
- ❌ 不为新增规则写 JUnit matcher 单元测试
- ❌ 不自动化生成 `_coverage_matrix.md`
- ❌ 不删除空桶
- ❌ 不动 `food_label_rules.json`
- ❌ 不动 OCR 模型 / sourceSet / build 系统

### 真机回归(可选)

若用户希望做端侧 A/B,可在所有 commit 后跑一次 `connectedDebugAndroidTest`,在 4 张测试集上跑 OCR + 新规则,记录 cold_ms / warm_avg_ms / 命中数。但这不阻塞整体验收。

---

## 8. 风险登记

| 风险 | 触发条件 | 应对 |
|---|---|---|
| 新增规则 keywords 与既有规则重叠导致误命中 | Phase 2 audit 后跑 4 张测试集 | 回滚该规则,keywords 收紧 |
| 总册列插入破坏 markdown 表格渲染 | 第 5 列插入未对齐 | 手工 diff + 渲染检查(用 VSCode markdown preview) |
| `_audit_gaps.md` 与 `_coverage_matrix.md` 数据漂移 | Phase 3 / 4 之间被人手修改 | §6.4 边界声明:audit gaps 不动 |
| 弱规则强化后命中数上升,真实违规也命中(良性) | Phase 2 commit 后跑 4 张测试集 | 接受,这是补强的本意 |
| 新规则的 `regulation` 引到已废止法规 | WebSearch 未做 | Phase 2 commit 前必须 WebSearch 现行性(沿用 CLAUDE.md「知识库时效性整理」流程) |

---

## 9. 与既有规范的对齐

| 既有约束 | 本文如何遵守 |
|---|---|
| CLAUDE.md:规则 JSON `regulation` 必须引 `知识库/<域>/<现行法规>.md` | §4.4 Schema 约束 |
| CLAUDE.md:知识库时效性(2026-08-27 整理) | §4.4 Schema 约束 + §8 风险登记 |
| CLAUDE.md:commit 作者 AlexMultiAgent, 无 Claude trailer | §7 commit hygiene |
| CLAUDE.md:显式 `git add`、禁 `git add -A` | §5.6 / §6.5 拆分 + PreToolUse hook 兜底 |
| memory `feedback-release-hygiene.md`:版本号只对实际功能 / 修复改动负责 | 本次不发版,不动 `versionCode` |
| memory `followup-ad-signage-cross-cite.md`:cross-cite 待决 | §1.2 跨域 gap 列入预判;§4.4 提醒避免再犯 |
| memory `followup-tab-reset-initial-page.md`:Tab → 初始页 | 与本任务无关,不展开 |

---

## 10. 后续(本任务范围外)

- 端侧 A/B 验证(可选,见 §7)
- 弱规则命中数回归报告(若做 A/B)
- cross-cite 拆分决策(参见 `followup-ad-signage-cross-cite.md`)
- food_label_rules.json 同模式审计(待 ad_signage 跑稳后)
- 空桶 internet_ad / finance 的示例图补全(backlog)

---

## 11. 验收签字栏

| 角色 | 决定 | 时间 |
|---|---|---|
| Spec author | 提交评审 | 2026-08-27 |
| Project owner(AlexMultiAgent) | 待签字 | — |