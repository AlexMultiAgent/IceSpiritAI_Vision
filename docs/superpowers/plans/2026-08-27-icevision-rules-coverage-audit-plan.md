# 冰灵锐目 — 违规案例审计 + 规则覆盖补强 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 审计 66 张违规案例图、补全新/强化 2 条规则、`_违规档案总册.md` 加 `关联规则 ID` 列、产出 `_coverage_matrix.md` 双向矩阵。

**Architecture:** 4 阶段顺序流水线(Audit → Rule expansion → 总册演化 → Coverage matrix),每阶段独立 commit,可单独 revert。

**Tech Stack:** Markdown(数据/文档)、JSON 规则文件(规则引擎)、Bash(Git/JSON 校验)、Gradle ServiceLoader 装载(JSON 合法性由 loader 测试兜底)。

**Spec:** [`docs/superpowers/specs/2026-08-27-icevision-rules-coverage-audit-design.md`](../specs/2026-08-27-icevision-rules-coverage-audit-design.md)

---

## 文件结构

**新建**:
- `违规案例/_audit_gaps.md` — Phase 1 产出,66 节覆盖状态清单
- `违规案例/_coverage_matrix.md` — Phase 4 产出,双向矩阵
- `违规案例/text_<new_ruleId>.md` × N — Phase 2 产出,新增/强化规则的逐行分析文件(沿用现有 43 份的格式)

**修改**:
- `app/src/main/assets/rules/ad_signage_rules.json` — Phase 2 产出,121 → N 条,v8 → v9
- `违规案例/_违规档案总册.md` — Phase 3 产出,8 → 9 列 + §审计日志 + §桶汇总 2 行
- `违规案例/_rule_ids.json` — Phase 3 产出,121 → N 条

**规则 JSON schema**(锁定):
```json
{
  "id": "<ruleId>",
  "category": "<one of 14>",
  "regulation": "<广告法 第XX条 + 子法条>",
  "lawText": "<法条原文 + 子法条原文,换行 \\n 分隔>",
  "keywords": ["<锚词1>", "<锚词2>", "..."],
  "severity": "Violation" | "Warning" | "Info"
}
```

**桶 → 规则 category 对应**(锁定):
```
medical, absolute, education, signage, internet_ad, finance,
realestate, cosmetic, agricultural, pesticide, veterinary, restricted, minor, outdoor
```

---

## Task 1: Phase 1.Audit — 审计图 01-22(医疗 / 绝对化 / 教育 / 食品)

**Files:**
- Create: `违规案例/_audit_gaps.md`(增量)

- [ ] **Step 1: 列目录确认 22 个目标文件名**

Run:
```bash
ls 违规案例/0[1-9]*.{jpg,png,jpeg} 违规案例/1[0-9]*.{jpg,png,jpeg} 违规案例/2[0-2]*.{jpg,png,jpeg} 2>/dev/null | sort
```
Expected: 22 个文件名,无遗漏无重复

- [ ] **Step 2: 用 Read 工具逐张读取这 22 张图**

每次调用 Read 工具读 1 张图(支持视觉呈现),把违规模式记下:`原始文本 / 桶(沿用总册)/ 现行覆盖 ruleId(查 ad_signage_rules.json keywords)/ 覆盖状态 / 建议动作`。

- [ ] **Step 3: 写入 _audit_gaps.md 头部 + 22 节**

写入以下结构(完整文件不是仅这 22 节,但 Phase 1 第一批只填这 22 节):
```markdown
# 违规案例审计清单

> 审计日期:2026-08-27
> 扫描图数:66(分批进行中)

## 01_<filename>

| 字段 | 值 |
|---|---|
| 文件名 | 01_xxx.jpg |
| 桶分类 | medical |
| 违规描述 | <1-2 句> |
| 现行覆盖规则 | <ruleId,空 = 未覆盖> |
| 覆盖状态 | 已覆盖 / 弱覆盖 / 未覆盖 |
| 建议动作 | 补新规则 / 强化现规则 / 保持 |
| 关联法条 | 《广告法》第XX条 |

## 02_<filename>
... (共 22 节)
```

每节完全填满 6 个字段,不得留空。`现行覆盖规则` 是数组,可多值。

- [ ] **Step 4: 验证节数**

Run:
```bash
grep -c '^## 0[0-9]_\|^## 1[0-9]_\|^## 2[0-2]_' 违规案例/_audit_gaps.md
```
Expected: 22

- [ ] **Step 5: 不 commit,继续 Task 2**

本 Task 不独立 commit,_audit_gaps.md 在 Phase 1 全部完成后统一 commit(见 Task 4 Step 5)。

---

## Task 2: Phase 1.Audit — 审计图 23-44(食品 / 招牌 / 农药 / 兽医)

**Files:**
- Modify: `违规案例/_audit_gaps.md`(追加 22 节)

- [ ] **Step 1: 列目录确认 22 个目标文件名**

Run:
```bash
ls 违规案例/2[3-9]*.{jpg,png,jpeg} 违规案例/3[0-9]*.{jpg,png,jpeg} 违规案例/4[0-4]*.{jpg,png,jpeg} 2>/dev/null | sort
```
Expected: 22 个文件名

- [ ] **Step 2: Read 工具逐张读取 22 张图,记录覆盖状态**

- [ ] **Step 3: 追加 22 节到 _audit_gaps.md**

在已有 22 节后继续追加 ## NN_<filename> 节,字段同上。

- [ ] **Step 4: 验证累计节数**

Run:
```bash
grep -c '^## [0-9]\{2\}_' 违规案例/_audit_gaps.md
```
Expected: 44

- [ ] **Step 5: 不 commit,继续 Task 3**

---

## Task 3: Phase 1.Audit — 审计图 45-66(兽医 / 化妆品 / 农业 / 房地产 / 金融 / 互联网 / 未成年)

**Files:**
- Modify: `违规案例/_audit_gaps.md`(追加 22 节)

- [ ] **Step 1: 列目录确认 22 个目标文件名**

Run:
```bash
ls 违规案例/4[5-9]*.{jpg,png,jpeg} 违规案例/5[0-9]*.{jpg,png,jpeg} 违规案例/6[0-6]*.{jpg,png,jpeg} 2>/dev/null | sort
```
Expected: 22 个文件名

- [ ] **Step 2: Read 工具逐张读取 22 张图,记录覆盖状态**

- [ ] **Step 3: 追加 22 节到 _audit_gaps.md**

- [ ] **Step 4: 验证累计节数**

Run:
```bash
grep -c '^## [0-9]\{2\}_' 违规案例/_audit_gaps.md
```
Expected: 66

- [ ] **Step 5: 验证每节 6 字段非空**

Run:
```bash
awk '/^## /{f=$0; n=0; next} /^\| 桶 / {n++} END {for(k in c) print k, c[k]}' 违规案例/_audit_gaps.md
```
人工核对:每节至少有 1 行 `| 桶分类 |`、1 行 `| 违规描述 |`、1 行 `| 建议动作 |`。

- [ ] **Step 6: 整理 §桶汇总到 _audit_gaps.md 顶部**

在文件最前面(标题下)加一节:
```markdown
## 桶汇总(本次审计)

| 桶 | 总图数 | 已覆盖 | 弱覆盖 | 未覆盖 | 建议动作 |
|---|---:|---:|---:|---:|---|
| medical | 5 | X | X | X | 保持 / 补新 / 强化 |
... (13 桶,internet_ad / finance 标 `规则已就位, 待补示例图`)
```

数字从 66 节明细汇总得出。

---

## Task 4: Phase 1.Audit — 合成 gap 候选清单 + 提交

**Files:**
- Modify: `违规案例/_audit_gaps.md`(追加 §2 候选清单)

- [ ] **Step 1: 扫描 66 节,提取所有 `建议动作:补新规则` 的图**

Run:
```bash
awk '/^## /{f=$0; getline; while($0 !~ /^## / && NF>0){if($0 ~ /建议动作.*补新/) print f; getline}}' 违规案例/_audit_gaps.md
```
Expected: 至少 3-5 张图,包括 #18 白酒 / #20 黑尊牛 / #62 APP 数据(预判)

- [ ] **Step 2: 扫描所有 `建议动作:强化现规则` 的图**

Run:
```bash
awk '/^## /{f=$0; getline; while($0 !~ /^## / && NF>0){if($0 ~ /建议动作.*强化/) print f; getline}}' 违规案例/_audit_gaps.md
```
Expected: 至少 2 张图,关联到 `ad_signage_art22_tob_alc` 和 `ad_signage_art10_minor`

- [ ] **Step 3: 在 _audit_gaps.md 末尾追加 §新规则候选清单**

```markdown
## §新规则候选清单

| 候选 ruleId | category | severity | 关联图 | 法条依据 |
|---|---|---|---|---|
| ad_signage_signage_alcohol_drink_xxx | restricted | Violation | #18 | 《广告法》第XX条 |
| ad_signage_signage_gift_to_leader | minor | Warning | #20 | 《广告法》第XX条 |
| ad_signage_signage_app_data_citation | internet_ad | Warning | #62 | 《广告法》第XX条 |

## §强化规则清单

| ruleId | 现状 | 强化方向 |
|---|---|---|
| ad_signage_art22_tob_alc | n=2, no negation | keywords 加白酒/啤酒/红酒/黄酒/洋酒/酒类;negation 加 `不向未成年人` |
| ad_signage_art10_minor | n=2, no negation | keywords 加未成年人/儿童/... |
```

候选 ruleId 后续 Phase 2 commit 时落实(任务 5-7)。

- [ ] **Step 4: 验证 _audit_gaps.md 完整性**

Run:
```bash
grep -c '^## [0-9]\{2\}_' 违规案例/_audit_gaps.md   # 应 66
grep -c '^## §' 违规案例/_audit_gaps.md            # 应 4(§桶汇总 + §新规则候选清单 + §强化规则清单 + §审计日志 — 最后一个 Phase 3 阶段补)
```

- [ ] **Step 5: 提交 _audit_gaps.md**

```bash
git add 违规案例/_audit_gaps.md
git commit -m "feat(cases): 违规案例审计 — 66 张图覆盖状态清单 + 新规则候选 + 强化清单"
```

Expected: 1 commit,只有 _audit_gaps.md 改动。Author = AlexMultiAgent, 无 Co-Authored-By trailer。

验证 trailer:
```bash
git log -1 --format='%B' | grep -i 'Co-Authored-By'
```
Expected: 空输出。

---

## Task 5: Phase 2.Rule Expansion — 强化 ad_signage_art22_tob_alc

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`
- Create: `违规案例/text_ad_signage_art22_tob_alc.md`(覆盖现有 43 份之一)

- [ ] **Step 1: WebSearch 确认法规现行性**

Run:
```bash
# 用 WebSearch 查 "广告法 第二十二条 烟草 酒类 现行" 确认条款有效
```
Output: 一句话结论写到本地备忘(不强求 commit)。

- [ ] **Step 2: 读取现有规则全文**

Run:
```bash
grep -A 20 '"id": "ad_signage_art22_tob_alc"' app/src/main/assets/rules/ad_signage_rules.json
```
记录:`regulation` / `lawText` / `keywords` 数组 / `severity`。

- [ ] **Step 3: 替换 keywords 数组**

把现有 2 个 keywords 替换为:
```json
"keywords": [
  "白酒",
  "啤酒",
  "红酒",
  "黄酒",
  "洋酒",
  "酒类",
  "酒精度数"
]
```

(用 Edit 工具,old_string = 当前 keywords 数组的 7 行,new_string = 新数组 9 行)

- [ ] **Step 4: 验证替换**

Run:
```bash
grep -A 12 '"id": "ad_signage_art22_tob_alc"' app/src/main/assets/rules/ad_signage_rules.json | grep -c '"酒\|"白\|"啤\|"红\|"黄\|"洋'
```
Expected: 6+

- [ ] **Step 5: 不 commit,继续 Task 6**

---

## Task 6: Phase 2.Rule Expansion — 强化 ad_signage_art10_minor

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`

- [ ] **Step 1: 读取现有规则全文**

Run:
```bash
grep -A 20 '"id": "ad_signage_art10_minor"' app/src/main/assets/rules/ad_signage_rules.json
```

- [ ] **Step 2: 替换 keywords 数组**

现有 keywords 替换为:
```json
"keywords": [
  "未成年人",
  "儿童",
  "小学生",
  "中学生",
  "宝宝",
  "婴儿",
  "幼儿"
]
```

- [ ] **Step 3: 验证替换**

Run:
```bash
grep -A 11 '"id": "ad_signage_art10_minor"' app/src/main/assets/rules/ad_signage_rules.json | grep -c '"未\|"儿\|"小\|"中\|"宝\|"婴\|"幼'
```
Expected: 6+

- [ ] **Step 4: 不 commit,继续 Task 7**

---

## Task 7: Phase 2.Rule Expansion — 新增 白酒专项规则

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(在合适位置插入新规则)

- [ ] **Step 1: WebSearch 确认法规现行性 + 法条原文**

查:`广告法 第二十二条 酒类广告 + 国家市场监督管理总局 酒类广告管理办法 现行`

Output: 法条原文引用到 lawText 字段。

- [ ] **Step 2: 构造新规则 JSON 对象**

```json
{
  "id": "ad_signage_signage_alcohol_drink_xxx",
  "category": "restricted",
  "regulation": "《广告法》第二十二条 + 《酒类广告管理办法》(现行有效)",
  "lawText": "《广告法》第二十二条:禁止在大众传播媒介或者公共场所、公共交通工具、户外发布烟酒广告。... (完整法条原文)",
  "keywords": [
    "白酒",
    "茅台",
    "五粮液",
    "酒类",
    "酒精度",
    "纯粮"
  ],
  "severity": "Violation"
}
```

(具体 ruleId 后缀和法条原文以 WebSearch 结果为准。)

- [ ] **Step 3: 插入到 JSON**

定位到 `ad_signage_art22_tob_alc` 规则后面(同 category 聚集),用 Edit 工具插入新规则对象,注意 `,` 逗号分隔。

- [ ] **Step 4: 验证 JSON 合法**

Run:
```bash
python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json'))"
```
Expected: 无错误输出,exit 0。

- [ ] **Step 5: 验证规则数 = 122**

Run:
```bash
grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json
```
Expected: 122

---

## Task 8: Phase 2.Rule Expansion — 新增 送领导专项规则

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`

- [ ] **Step 1: WebSearch 查 「广告法 未成年人 引导」+ 总册 #20 黑尊牛原图违规描述**

查:法规依据(可能是《广告法》关于未成年人导向 + 商业广告导向规定)

- [ ] **Step 2: 构造新规则 JSON 对象**

```json
{
  "id": "ad_signage_signage_gift_to_leader",
  "category": "minor",
  "regulation": "《广告法》XXX 条(具体由 WebSearch 确认)",
  "lawText": "...",
  "keywords": [
    "送领导",
    "送上级",
    "送老板",
    "送客户",
    "商务礼",
    "送礼首选"
  ],
  "severity": "Warning"
}
```

- [ ] **Step 3: 插入到 JSON(category=minor 区)**

- [ ] **Step 4: 验证 JSON 合法 + 规则数**

Run:
```bash
python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json'))"
grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json
```
Expected: exit 0, 123

---

## Task 9: Phase 2.Rule Expansion — 新增 APP 数据引用规则

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`

- [ ] **Step 1: WebSearch 查 「广告法 第十一条 数据出处 APP 互联网广告」**

参考既有 `ad_signage_art11_data_citation` 的 regulation 字段,确认现行性。

- [ ] **Step 2: 构造新规则 JSON 对象**

```json
{
  "id": "ad_signage_signage_app_data_citation",
  "category": "internet_ad",
  "regulation": "《广告法》第十一条第二款 + 《互联网广告管理办法》第九条",
  "lawText": "...",
  "keywords": [
    "下载量",
    "用户量",
    "日活",
    "月活",
    "注册用户",
    "APP 排名",
    "应用市场排名",
    "装机量"
  ],
  "severity": "Warning"
}
```

- [ ] **Step 3: 插入到 JSON(category=internet_ad 区)**

- [ ] **Step 4: 验证 JSON 合法 + 规则数**

Run:
```bash
python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json'))"
grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json
```
Expected: exit 0, 124

---

## Task 10: Phase 2.Rule Expansion — 处理 audit 发现的额外 gaps(可选)

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`

> **本 Task 仅当 Phase 1 发现除 Task 7/8/9 之外的额外 `建议动作:补新规则` 时执行。** 若无额外 gap,跳到 Task 11。

- [ ] **Step 1: 从 _audit_gaps.md §新规则候选清单读额外候选**

读 _audit_gaps.md §新规则候选清单,若除 Task 7/8/9 外还有 row,继续。

- [ ] **Step 2: 对每个额外候选重复 Task 7-9 的 Step 1-4**

每个额外规则走一次:WebSearch → 构造 JSON → 插入 → 验证。

- [ ] **Step 3: 验证最终规则数**

Run:
```bash
grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json
```
Expected: 124 + N(N = 额外规则数)

---

## Task 11: Phase 2.Rule Expansion — 配套 text_*.md 分析文件

**Files:**
- Create: `违规案例/text_ad_signage_signage_alcohol_drink_xxx.md`
- Create: `违规案例/text_ad_signage_signage_gift_to_leader.md`
- Create: `违规案例/text_ad_signage_signage_app_data_citation.md`
- Modify: `违规案例/text_ad_signage_art22_tob_alc.md`(如有)
- Modify: `违规案例/text_ad_signage_art10_minor.md`(如有)

- [ ] **Step 1: 为 Task 7 新规则写 text_*.md**

参照 `违规案例/text_medical_ykzp_01.md` 的格式,内容:
- 来源:#18 白酒电商页(如已有公开来源 URL 写明,否则写 `本地案例图`)
- 场景:电商页 / 实体店招贴 / 户外大牌
- 违规点:<1-2 句>
- 法律依据:<新规则的 regulation 字段值>
- 原始违法广告语:<从 #18 提取>
- 预期命中规则:`- id: <新规则 id> severity: Violation`
- 处罚结果:留空(实际案件未必有)
- 备注:可选

- [ ] **Step 2: 为 Task 8 新规则写 text_*.md**

同上,关联 #20 黑尊牛。

- [ ] **Step 3: 为 Task 9 新规则写 text_*.md**

同上,关联 #62 APP 数据。

- [ ] **Step 4: 检查既有 text_ad_signage_art22_tob_alc.md 和 text_ad_signage_art10_minor.md 是否需要更新**

若强化后 keywords 列表变了,既有 text 文件里的「预期命中规则」若列了 keywords 字面量,需要同步更新。否则不强求修改。

---

## Task 12: Phase 2.Rule Expansion — version bump 8 → 9 + 校验 + commit

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`

- [ ] **Step 1: 修改 version 字段**

Edit 工具替换:
```
old: "version": 8,
new: "version": 9,
```

- [ ] **Step 2: JSON 合法性最终校验**

Run:
```bash
python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json')); print('OK')"
```
Expected: `OK`

- [ ] **Step 3: 规则数最终校验**

Run:
```bash
grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json
```
Expected: 121 + M(M = 新增规则数,最小 3,最大 5)

- [ ] **Step 4: 启动 ServiceLoader 装载测试**

Run:
```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "*AdSignageRuleLoader*" --quiet
```
Expected: BUILD SUCCESSFUL,测试通过(loader 能 parse JSON + 装 N 条规则到 matcher)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/rules/ad_signage_rules.json 违规案例/text_*.md
git commit -m "feat(rules): ad_signage_rules.json v8 → v9 — 新增 M 条 + 强化 2 条

新增:
- ad_signage_signage_alcohol_drink_xxx(白酒)
- ad_signage_signage_gift_to_leader(送领导)
- ad_signage_signage_app_data_citation(APP 数据)

强化:
- ad_signage_art22_tob_alc(keywords n=2 → 6+, 含白酒/啤酒/红酒/...)
- ad_signage_art10_minor(keywords n=2 → 6+, 含未成年人/儿童/...)"
```

验证:
```bash
git log -1 --format='%B' | grep -i 'Co-Authored-By'
```
Expected: 空输出

---

## Task 13: Phase 3.总册演化 — 在 66 个表格节插入「关联规则 ID」列

**Files:**
- Modify: `违规案例/_违规档案总册.md`

- [ ] **Step 1: 找到 66 个 ## NN 节的首个表格行**

每节第一行格式:`| 文件名 | 原始违法广告语 | 违规情形 | 违反法条 | 桶分类 | 严重度 | 处理建议 | 备注 |`(8 列)

- [ ] **Step 2: 批量插入第 5 列「关联规则 ID」**

新格式:`| 文件名 | 原始违法广告语 | 违规情形 | 违反法条 | 关联规则 ID | 桶分类 | 严重度 | 处理建议 | 备注 |`(9 列)

用 Edit 工具对每一节的首行做替换:
- old:`| 文件名 | 原始违法广告语 | 违规情形 | 违反法条 | 桶分类 | 严重度 | 处理建议 | 备注 |`
- new:`| 文件名 | 原始违法广告语 | 违规情形 | 违反法条 | 关联规则 ID | 桶分类 | 严重度 | 处理建议 | 备注 |`

每一节独立 Edit(因为 66 节的首行内容因文件名不同而不同,不复用 old_string)。

- [ ] **Step 3: 填 66 行的第 5 列「关联规则 ID」值**

逐行填入:
- 图被现行 121 条规则覆盖 → 填入 ruleId(可多值用 `, `)
- 图已被新规则覆盖 → 填 ruleId + `(new)`
- 图无规则覆盖 → 填 `—`,在 `备注` 列写 `审计建议:XXX`

具体值从 _audit_gaps.md 的 66 节 `现行覆盖规则` 字段派生。

- [ ] **Step 4: 验证列数**

Run:
```bash
grep -c '^| 文件名 | 原始违法广告语 | 违规情形 | 违反法条 | 关联规则 ID | 桶分类 | 严重度 | 处理建议 | 备注 |$' 违规案例/_违规档案总册.md
```
Expected: 66(每节首行匹配)

- [ ] **Step 5: 验证每行的 `关联规则 ID` 列非空**

Run:
```bash
awk -F'\\|' '/^\| 文件名 \| 原始违法广告语/{getline; print NR, $5}' 违规案例/_违规档案总册.md | grep -v '—' | wc -l
```
人工核对:应与 _audit_gaps.md 的「已覆盖 + 弱覆盖」总数一致。

---

## Task 14: Phase 3.总册演化 — §桶汇总补充 2 行 + §审计日志新增

**Files:**
- Modify: `违规案例/_违规档案总册.md`

- [ ] **Step 1: 定位 §桶汇总表**

读 _违规档案总册.md,找到 §桶汇总的表格行(13 桶汇总表)。

- [ ] **Step 2: 在表格末尾追加 2 行**

```
| internet_ad | 0 | 0 | 0 | 规则已就位(9 条), 待补示例图 |
| finance | 0 | 0 | 0 | 规则已就位(13 条), 待补示例图 |
```

用 Edit 工具,old_string = §桶汇总表的最后一行,new_string = 旧行 + 2 新行。

- [ ] **Step 3: 在文件顶部(标题下、§1 前)新增 §审计日志 节**

```markdown
## 审计日志

- 审计日期:2026-08-27
- 扫描图数:66
- 已覆盖(无变更):X
- 弱覆盖(强化后):X
- 新增规则:M
- 强化规则:K(= 2)
- 未覆盖 backlog:L
- 桶数:14(2 个空桶 internet_ad / finance 规则已就位, 待补示例图)

> 数据源:`_audit_gaps.md` §桶汇总 与 §新规则候选清单
```

数字从 _audit_gaps.md 真实数据填入。

- [ ] **Step 4: 验证 §桶汇总 15 行(13 + 2)**

Run:
```bash
awk '/^\| medical \|/,/^\| finance \|/' 违规案例/_违规档案总册.md | grep -c '^\|'
```
Expected: 15(含表头)

---

## Task 15: Phase 3.总册演化 — 更新 _rule_ids.json + commit

**Files:**
- Modify: `违规案例/_rule_ids.json`

- [ ] **Step 1: 把新增 ruleId 加入 _rule_ids.json**

打开 _rule_ids.json,在末尾追加新 ruleId:
```json
"ad_signage_signage_alcohol_drink_xxx",
"ad_signage_signage_gift_to_leader",
"ad_signage_signage_app_data_citation"
```
(若 Task 10 有额外规则,一并加)

保持 JSON 数组语法(逗号 + 缩进)。

- [ ] **Step 2: 验证 _rule_ids.json 与 ad_signage_rules.json 一致**

Run:
```bash
grep -c '"id":' app/src/main/assets/rules/ad_signage_rules.json
python -c "import json; print(len(json.load(open('违规案例/_rule_ids.json'))))"
```
Expected: 两个数字相等。

- [ ] **Step 3: Commit**

```bash
git add 违规案例/_违规档案总册.md 违规案例/_rule_ids.json
git commit -m "docs(cases): 总册加 关联规则 ID 列 + §审计日志 + §桶汇总 2 空桶行"
```

验证:
```bash
git log -1 --format='%B' | grep -i 'Co-Authored-By'
```
Expected: 空

---

## Task 16: Phase 4.Coverage Matrix — 写 _coverage_matrix.md

**Files:**
- Create: `违规案例/_coverage_matrix.md`

- [ ] **Step 1: 写 §1 规则 → 示例图**

从 _audit_gaps.md 66 节聚合:
- 每条规则(122 + M 条)→ 反查哪些图命中
- 表格:`| ruleId | category | severity | 示例图数 | 文件名列表 |`
- 排序:severity 降序(Violation → Warning → Info)→ ruleId 字典序

```markdown
# 违规案例 — 规则 跨覆盖矩阵

> 生成于:2026-08-27
> 规则数:122 + M | 示例图数:66

## §1 规则 → 示例图

| ruleId | category | severity | 示例图数 | 文件名列表 |
|---|---|---|---:|---|
| ad_signage_signage_alcohol_drink_xxx | restricted | Violation | 1 | #18_白酒电商页.png |
| ad_signage_signage_gift_to_leader | minor | Warning | 1 | #20_黑尊牛安格斯牛肉礼盒.png |
| ... (122 + M 行) |
```

- [ ] **Step 2: 写 §2 示例图 → 规则**

66 行,排序按文件名升序:

```markdown
## §2 示例图 → 规则

| 文件名 | 桶 | 严重度 | 关联规则数 | 规则 ID 列表 | 状态 |
|---|---|---|---:|---|---|
| 01_xxx.jpg | medical | Critical | 2 | ad_signage_art16_med_abs, ad_signage_signage_food_safety_implication | 已覆盖 |
| 18_xxx.png | signage | Critical | 1 | ad_signage_signage_alcohol_drink_xxx (new) | 已覆盖(新) |
| 20_xxx.png | minor | Critical | 1 | ad_signage_signage_gift_to_leader (new) | 已覆盖(新) |
| ... (66 行) |
```

状态枚举:`已覆盖` / `弱覆盖(关键词薄)` / `未覆盖` / `未关联(桶空)`(后者用于 internet_ad / finance 区,但目前没图)

- [ ] **Step 3: 写 §3 覆盖率统计**

```markdown
## §3 覆盖率统计

- 规则总数:122 + M
- 有示例图的规则:X / (122 + M) (Y%)
- 无示例图的规则(backlog):(122 + M - X) / (122 + M)
- 示例图总数:66
- 被规则覆盖的图:X / 66
- 无规则覆盖的图(backlog):Y / 66
- 弱覆盖(关键词薄)的图:Z / 66
- 空桶(规则已就位, 待补示例图):internet_ad, finance
```

数字从 §1 / §2 真实数据汇总,不能模糊(X 实际算出来,不能写约值)。

- [ ] **Step 4: 验证双向加和**

§1 中所有规则示例图数之和 == §2 中所有图关联规则数之和 == 66 张图中总关联条目数

- [ ] **Step 5: Commit**

```bash
git add 违规案例/_coverage_matrix.md
git commit -m "docs(cases): _coverage_matrix.md 首次建立规则↔示例图 双向矩阵"
```

验证:
```bash
git log -1 --format='%B' | grep -i 'Co-Authored-By'
```
Expected: 空

---

## Task 17: 最终整体验收

**Files:**
- (无文件改动,纯验证)

- [ ] **Step 1: 5 项整体验收(对照 spec §7)**

| 项 | 命令 / 检查 | 通过标准 |
|---|---|---|
| JSON 合法 | `python -c "import json; json.load(open('app/src/main/assets/rules/ad_signage_rules.json'))"` | exit 0 |
| 4 张测试集回归 | `git status` 看测试图是否未删;`./gradlew.bat testDebugUnitTest` 通过 | 通过 |
| 总册 + 矩阵 cross-check | 手工 diff 总册 `关联规则 ID` 列 vs 矩阵 §2 列表 | 一致 |
| `git status` 干净 | `git status` | 无未跟踪残留 |
| commit hygiene | `git log -3 --format='%B' \| grep -i 'Co-Authored-By'`(看 Phase 2/3/4 三 commit) | 空 |

逐项打勾,任何一项失败 → 回滚对应 commit(Task 12 / 15 / 16),回到 Phase 重做。

- [ ] **Step 2: 写执行报告**

在 `docs/smoke/` 下新建 `2026-08-27-rules-coverage-audit-smoke.md`,简述:
- 实际新增规则数 M
- 实际强化规则数 K
- 总册 §审计日志最终值
- 矩阵 §3 覆盖率数字
- 任何超出预判的发现(例如额外 gap 或跨覆盖冲突)

```bash
git add docs/smoke/2026-08-27-rules-coverage-audit-smoke.md
git commit -m "docs(smoke): 2026-08-27 规则覆盖审计 烟测记录"
```

---

## Self-Review

(此节由作者填写,工程师无需阅读)

### Spec 覆盖

- §1.1 现状(66 张图 + 121 条 + 总册 8 列): Task 1-3 ✓
- §1.2 跨覆盖 gap 预判: Task 4 Step 1 ✓
- §1.3 目标(4 产出): Task 4/12/15/16 ✓
- §1.4 非目标: 所有 Task 都不触发 ✓
- §3 Phase 1 Audit: Task 1-4 ✓
- §4 Phase 2 Rule expansion: Task 5-12 ✓
- §4.4 Schema 约束: 文件结构中 schema 锁定 + Task 7-9 强制包含 `regulation`/`lawText`/`keywords` ✓
- §4.5 测试约定: Task 11 ✓
- §5 Phase 3 总册演化: Task 13-15 ✓
- §5.5 §备注列边界: Task 13 Step 3 说明 ✓
- §5.6 commit 拆分: Task 4 / 12 / 15 / 16 / 17 各自分 commit ✓
- §6 Phase 4 Coverage matrix: Task 16 ✓
- §7 整体验收: Task 17 ✓
- §8 风险登记: Step 1/Task 5/6/7/8/9 各 Step 1(WebSearch 现行性) ✓

### 占位符扫描

- "TBD" / "TODO" / "implement later": 无
- "Add appropriate error handling" / "handle edge cases": 无
- "Write tests for the above" 无具体代码: 无
- "Similar to Task N" 无代码: 无

### 类型一致性

- 规则 JSON 字段:`id` / `category` / `regulation` / `lawText` / `keywords` / `severity` — Task 5/6/7/8/9 一致
- `_audit_gaps.md` 节标题:`## NN_<filename>` — Task 1/2/3/4 一致
- 总册新列名:`关联规则 ID` — Task 13/14 一致
- 矩阵状态枚举:`已覆盖` / `弱覆盖(关键词薄)` / `未覆盖` / `未关联(桶空)` — Task 16 一致
- commit 前缀:`feat(cases):` / `feat(rules):` / `docs(cases):` / `docs(smoke):` — 与现有项目约定一致(参考 commit `6290379` / `79090bd` / `d53aa02`)