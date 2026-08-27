# 冰灵锐目 — v0.1.33 规则扩充 + 案例归档 + 发版 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 2026-08-27 累积的 91 项 working tree 改动合成 v0.1.33 发版:4 个 commit 拆分(知识库迁移 / 规则扩充 / 案例归档 / 自动化基础设施)+ Release 三段式打标(versionCode 32→33 + user-changelog.md + git tag v0.1.33 + push latest ref)+ icevision-release 流水线(assembleRelease + Gitea 上传 + smoke)。

**Architecture:** **没有代码改动**。本次仅是 4 类累积工作的发版收尾动作;新规则 +1 条、新文档 / 新 skill / 新 fixture 全部已落盘在 working tree,只待 commit + release。

**Tech Stack:** Git / Gradle 9.7 / JDK 17(已 stage `/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`)/ Gitea 1.22.x / cert-pin `signerCertSha256=4a21f4...3043`。

**Spec:** `docs/superpowers/specs/2026-08-27-icevision-v0.1.33-rules-archive-release.md`

**Pre-flight checklist(每个 task 开始前确认):**

- [ ] `git config user.name` = `AlexMultiAgent`, `git config user.email` = `zhangven@gmail.com`
- [ ] 当前在 `main` 分支,最近 commit = spec commit `68789d8`(或更新)
- [ ] `git status` 包含 91 项 working tree 改动(spec 已 commit 后剩余)

---

## 文件结构

修改文件(working tree 已存在):

| 路径 | 改动 | commit |
|---|---|---|
| `知识库/已废止/`(2 个新建) | 新建目录 + 2 份迁移 md | 1 |
| `知识库/广告业务/母乳代用品销售管理办法_广告法§22实质替代.md` | 新建(改名版) | 1 |
| `知识库/广告业务/兽药广告审查发布规定_广告业务广告审查发布标准修订发布.md` | 新建(改名版) | 1 |
| `知识库/食品标识/GB_28050-2011_预包装食品标签通则_2027-03-16废止.md` | 新建(2027 新版占位) | 1 |
| `知识库/食品标识/GB_7718-2011_预包装食品营养标签通则_2027-03-16废止.md` | 新建(2027 新版占位) | 1 |
| `知识库/食品标识/食品标识管理规定_2027-03-16废止.md` | 新建(2027 新版占位) | 1 |
| 上述对应的 8 份旧 md(7 份来自 `知识库/广告业务/` 与 `知识库/食品标识/`) | 删除 | 1 |
| `app/src/main/assets/rules/ad_signage_rules.json` | 修改(+28 行,新规则 `ad_signage_signage_food_safety_implication`) | 2 |
| `违规案例/01_*.jpg` ~ `66_*.{jpg,png}`(66 张) | 新建 | 3 |
| `违规案例/_违规档案总册.md`(926 行) | 新建 | 3 |
| `违规案例/_plan.md` | 新建 | 3 |
| `违规案例/_rule_ids.json` | 新建 | 3 |
| `违规案例/_text_plan.md` | 新建 | 3 |
| `违规案例/_rename_map.md` | 新建 | 3 |
| 5 个旧 jpg(`absolute_xieduhui_01.jpg` / `food_internet_zytmhqs_01.jpg` / `medical_daojia_01.jpg` / `medical_store_01.jpg` / `outdoor_durex_01.jpg`) | 删除 | 3 |
| `CLAUDE.md` | 修改(规则计数 + 知识库时效性段 + 自动化表) | 4 |
| `.claude/skills/project-commit/SKILL.md` | 修改(Release 三段式打标段) | 4 |
| `.claude/hooks/pre-tool-use.js` | 修改(Rule 3 防误删 .aar) | 4 |
| `.claude/skills/icevision-release/SKILL.md` | 新建 | 4 |
| `app/build.gradle.kts`(第 78-79 行) | 修改:`versionCode = 33` + `versionName = "0.1.33"` | 5 |
| `app/src/main/assets/user-changelog.md` | 修改(顶部新增 `## v0.1.33 · 2026-08-27` 条目) | 5 |

不动文件:
- 任何 `app/src/main/java/...` Kotlin 代码
- OCR 模型 / PaddleOCR SDK
- 既有规则库条目(只新增 1 条)
- 既有 `food_label_rules.json`
- 启动图标 / Gradle Wrapper

---

## Task 1: 知识库时效性整理 — 14 文件迁移 / 新建 / 删除

**Files:**
- New: `知识库/已废止/户外广告登记管理规定_2016工商总局令86号废止.md`
- New: `知识库/已废止/母乳代用品销售管理办法_2017卫计委令17号废止.md`
- New: `知识库/广告业务/母乳代用品销售管理办法_广告法§22实质替代.md`
- New: `知识库/广告业务/兽药广告审查发布规定_广告业务广告审查发布标准修订发布.md`
- New: `知识库/食品标识/GB_28050-2011_预包装食品标签通则_2027-03-16废止.md`
- New: `知识库/食品标识/GB_7718-2011_预包装食品营养标签通则_2027-03-16废止.md`
- New: `知识库/食品标识/食品标识管理规定_2027-03-16废止.md`
- Del: `知识库/广告业务/户外广告登记管理规定.md`
- Del: `知识库/广告业务/母乳代用品销售管理办法.md`
- Del: `知识库/广告业务/兽药广告审查发布规定.md`
- Del: `知识库/食品标识/GB_28050-2011_预包装食品标签通则.md`
- Del: `知识库/食品标识/GB_7718-2011_预包装食品营养标签通则.md`
- Del: `知识库/食品标识/食品标识管理规定.md`
- Del: `知识库/食品标识/母乳代用品销售管理办法.md`

- [ ] **Step 1.1: 验证 working tree 中知识库文件齐全**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
ls "知识库/已废止/"  # 期望 2 个 .md
ls "知识库/广告业务/" | grep -E "母乳代用品|兽药广告"  # 期望 2 个新版 md
ls "知识库/食品标识/" | grep "2027-03-16废止"  # 期望 3 个新版 md
git status --short "知识库/" | wc -l  # 期望 ≈ 14 项 (新建 + 删除)
```

期望输出:`已废止/` 2 个 .md + `广告业务/` 2 个新版 + `食品标识/` 3 个新版 + git status ≈ 14 项。

- [ ] **Step 1.2: 显式 git add(避免 `git add -A` 触发 hook)**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git add "知识库/已废止/"
git add "知识库/广告业务/母乳代用品销售管理办法_广告法§22实质替代.md"
git add "知识库/广告业务/兽药广告审查发布规定_广告业务广告审查发布标准修订发布.md"
git add "知识库/食品标识/GB_28050-2011_预包装食品标签通则_2027-03-16废止.md"
git add "知识库/食品标识/GB_7718-2011_预包装食品营养标签通则_2027-03-16废止.md"
git add "知识库/食品标识/食品标识管理规定_2027-03-16废止.md"
git add -u "知识库/广告业务/"  # 旧文件删除(simple rename)
git add -u "知识库/食品标识/"  # 旧文件删除(simple rename)
git status --short "知识库/"
```

期望输出:8 个 `D` (旧文件删除) + 7 个 `A` / `M` (新建 / 重命名);**无任何 `??` untracked 残留**。

> **若出现 R(renamed)状态而非 D+A**:git 把 rename 自动检测为 single rename,这时只需 `git add -A "知识库/"`,hook Rule 1 会拦截 `git add -A`,改用 `git add -u "知识库/"` (update tracked files)。

- [ ] **Step 1.3: 验证 staged 列表正确**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git diff --cached --name-only | wc -l  # 期望 14 项
git diff --cached --name-only | grep -E "^知识库/" | wc -l  # 期望 14 项
git diff --cached --name-only | grep -v "^知识库/" | wc -l  # 期望 0(无外部文件被误纳)
```

期望输出:staged 仅含 `知识库/` 下 14 项。

- [ ] **Step 1.4: Commit(无 Co-Authored-By trailer)**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git commit -m "docs(knowledge): 知识库时效性整理 2026-08-27

整理范围:
- 2 份已废止法规迁入 知识库/已废止/:户外广告登记管理规定(2016 工商总局令第 86 号废止)、母乳代用品销售管理办法(2017 卫计委令第 17 号废止)
- 1 份广告业务改名:母乳代用品销售管理办法 → 母乳代用品销售管理办法_广告法§22实质替代
- 1 份广告业务改名:兽药广告审查发布规定 → 兽药广告审查发布规定_广告业务广告审查发布标准修订发布
- 3 份食品标识 2027-03-16 新版占位(GB 28050-2011 / GB 7718-2011 / 食品标识管理规定)

政策:新增规则或扩规则时,先用 WebSearch 确认 regulation 字段所引法规仍现行,规则 JSON 不得指已废止法规。"
git log -1 --format='%B' | grep -i 'Co-Authored-By' && echo "VIOLATION" || echo "OK"
```

期望:commit SHA + `OK`(无 Co-Authored-By trailer)。

---

## Task 2: 规则扩充 — ad_signage_signage_food_safety_implication

**Files:**
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(line 1198-1225,新增 1 条规则)

- [ ] **Step 2.1: 验证 staged 修改**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git diff app/src/main/assets/rules/ad_signage_rules.json | head -40
```

期望输出:`+ ad_signage_signage_food_safety_implication` + 20 keywords(`安全放心`、`无毒副作用`、`无依赖` 等)+ `severity: Violation`。

- [ ] **Step 2.2: 验证 _rule_ids.json 包含新规则 ID**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
grep "ad_signage_signage_food_safety_implication" 违规案例/_rule_ids.json
```

期望输出:1 行匹配(确认 fixture 元数据与规则 ID 一致)。

- [ ] **Step 2.3: Commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git add app/src/main/assets/rules/ad_signage_rules.json
git commit -m "feat(rules): ad_signage_signage_food_safety_implication 暗示安全性规则

新增 1 条规则覆盖保健食品广告「暗示安全性」违规情形:
- 法源:《广告法》第十八条第(一)项 + 《药品、医疗器械、保健食品、特殊医学用途配方食品广告审查管理暂行办法》第十一条第(五)项 + 第五十八条
- 关键词:安全放心、无毒副作用、无依赖、100% 安全、绝对安全、零添加、天然无添加 等 20 词
- 触发案例:#49 仁和氨糖软骨素钙片「安全放心」+ #52 北大荒蜂胶软胶囊「✅纯天然」暗示天然 → 安全保证
- 广告招牌规则数 118 → 121"
git log -1 --format='%B' | grep -i 'Co-Authored-By' && echo "VIOLATION" || echo "OK"
```

期望:commit SHA + `OK`。

---

## Task 3: 案例归档 — 66 张图 + 总册 + 5 旧图删除

**Files:**
- New: `违规案例/01_*.jpg` ~ `66_*.{jpg,png}`(共 66 张)
- New: `违规案例/_违规档案总册.md`
- New: `违规案例/_plan.md`
- New: `违规案例/_rule_ids.json`
- New: `违规案例/_text_plan.md`
- New: `违规案例/_rename_map.md`
- Del: `违规案例/absolute_xieduhui_01.jpg`
- Del: `违规案例/food_internet_zytmhqs_01.jpg`
- Del: `违规案例/medical_daojia_01.jpg`
- Del: `违规案例/medical_store_01.jpg`
- Del: `违规案例/outdoor_durex_01.jpg`

- [ ] **Step 3.1: 验证 case 库结构**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
ls 违规案例/*.jpg 违规案例/*.png 2>/dev/null | wc -l  # 期望 66
ls 违规案例/_*.md 2>/dev/null | wc -l  # 期望 5(总册 + _plan + _text_plan + _rename_map + 加上 _rule_ids.json 是 JSON)
ls 违规案例/_rule_ids.json 2>/dev/null  # 期望存在
git status --short 违规案例/ | wc -l  # 期望 ≈ 71+ 项(66 张图 + 5 个新 md + 5 个旧 jpg 删除)
```

期望:`66` 张图 + `5` 个新元数据文件 + 5 个旧 jpg 删除(staged 后是 `D` 状态)。

- [ ] **Step 3.2: 显式 git add**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git add 违规案例/_违规档案总册.md
git add 违规案例/_plan.md
git add 违规案例/_rule_ids.json
git add 违规案例/_text_plan.md
git add 违规案例/_rename_map.md
git add 违规案例/[0-9][0-9]_*.jpg 违规案例/[0-9][0-9]_*.png  # 66 张编号案例
git add -u 违规案例/  # 5 个旧 jpg 删除
git status --short 违规案例/ | head -20
```

期望输出:5 个旧 jpg 为 `D` 状态;66 张编号图 + 5 个 _*.md 为 `A` 状态。

> **若 glob `[0-9][0-9]_*.jpg` 在 Git Bash 不展开**:逐张 `git add 违规案例/01_*.jpg` 等 66 次,或用 `find 违规案例 -maxdepth 1 -regex '.*/[0-9][0-9]_.*\.\(jpg\|png\)' -exec git add {} +`。

- [ ] **Step 3.3: 验证 staged 全部在 违规案例/ 下**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git diff --cached --name-only | grep -v "^违规案例/" | wc -l  # 期望 0
git diff --cached --name-only | grep "^违规案例/" | wc -l  # 期望 ≈ 76(66 图 + 5 md + 5 旧图删除)
```

- [ ] **Step 3.4: 验证总册内容完整**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
wc -l 违规案例/_违规档案总册.md  # 期望 ≈ 926 行
grep -c "^## [0-9][0-9]" 违规案例/_违规档案总册.md  # 期望 66(66 个案例章节)
grep -c "^| 桶分类" 违规案例/_违规档案总册.md  # 期望 ≥ 1
```

期望:`926` 行左右 + `66` 个 `## NN` 案例章节。

- [ ] **Step 3.5: Commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git commit -m "feat(cases): 违规案例图片归档 — 66 张 + 总册

归档 2026-08-27 真实公开广告违规图片 66 张(01-66 编号),覆盖 14 个桶:
absolute / education / food_function_claim / food_disease_target / weight_loss /
realestate / medical / pestvet / cosmetics / signage / internet_ad / data_citation /
fake_data / agricultural。

_违规档案总册.md(926 行)含每张图的:原始违法广告语 / 违规情形 / 违反法条 /
桶分类 / 严重度 / 处理建议 / 备注;附桶汇总表 + 处理优先级。

重要发现:
- #06 商业借用军政形象营销(加油站八一海报)
- #49 + #52 保健食品暗示安全性违规(已触发新规则 ad_signage_signage_food_safety_implication)
- #63 重大虚假医疗宣传(吃喝不忌口血糖不再高)

66 张全部存在不同程度违规,无任何合规参照样本。Critical×59 / Warning×7 / Info×0。

辅助元数据:
- _plan.md 桶采集计划
- _rule_ids.json 90 rule IDs(规则引擎覆盖度基线)
- _text_plan.md 文字描述模板
- _rename_map.md 旧 slug → 新 slug 重命名映射

5 个旧 jpg 删除(被新编号规范替换)。"
git log -1 --format='%B' | grep -i 'Co-Authored-By' && echo "VIOLATION" || echo "OK"
```

---

## Task 4: 自动化基础设施 + versionCode bump

**Files:**
- Modify: `CLAUDE.md`(规则计数 + 知识库时效性段 + 自动化表)
- Modify: `.claude/skills/project-commit/SKILL.md`(Release 三段式打标段)
- Modify: `.claude/hooks/pre-tool-use.js`(Rule 3 防误删 .aar)
- New: `.claude/skills/icevision-release/SKILL.md`
- Modify: `app/build.gradle.kts`(第 78-79 行,`versionCode = 33` + `versionName = "0.1.33"`)
- Modify: `app/src/main/assets/user-changelog.md`(顶部新增 `## v0.1.33 · 2026-08-27` 条目)

- [ ] **Step 4.1: 验证 staged 修改**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git status --short CLAUDE.md .claude/skills/ .claude/hooks/ app/build.gradle.kts app/src/main/assets/user-changelog.md
```

期望:6 个 `M` (CLAUDE.md / project-commit SKILL.md / pre-tool-use.js / build.gradle.kts / user-changelog.md)+ 1 个 `??` (icevision-release/SKILL.md 待 add)。

- [ ] **Step 4.2: git add 显式路径**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git add CLAUDE.md .claude/skills/project-commit/SKILL.md .claude/hooks/pre-tool-use.js .claude/skills/icevision-release/SKILL.md app/build.gradle.kts app/src/main/assets/user-changelog.md
git status --short | grep -v "^.D " | head -10  # 确认 staged 完整
```

期望:全部转为 `A` 或 `M` 状态,无未追踪残留。

- [ ] **Step 4.3: 验证 versionCode bump 与 user-changelog.md 顶部**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
grep -E 'versionCode|versionName' app/build.gradle.kts | head -2  # 期望 versionCode = 33 + versionName = "0.1.33"
head -1 app/src/main/assets/user-changelog.md  # 期望 "# 用户更新日志"
sed -n '3,5p' app/src/main/assets/user-changelog.md  # 期望 "## v0.1.33 · 2026-08-27"
```

期望:`versionCode = 33` + `versionName = "0.1.33"` + user-changelog.md 顶部首段 = `v0.1.33`。

- [ ] **Step 4.4: 验证 icevision-release skill 内容**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
wc -l .claude/skills/icevision-release/SKILL.md  # 期望 ≥ 120 行
grep -E "Pre-flight|流水线|smoke" .claude/skills/icevision-release/SKILL.md | head -5
```

期望:存在 5 步 pre-flight + 4 步流水线 + smoke 验证段。

- [ ] **Step 4.5: Commit**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git commit -m "chore(automation): skills + hooks + versionCode bump → v0.1.33

自动化基础设施:
- CLAUDE.md:规则计数 118→121 / 116→121 + 新增「知识库时效性整理」段(规则 JSON regulation 字段必须指现行法规)+ 新增「Claude Code 自动化」表(2 skill + 1 hook 职责边界)
- .claude/skills/project-commit/SKILL.md:新增「Release 三段式打标」段(versionCode bump + user-changelog.md 顶部 + git tag + push latest ref 同步执行,避免 v0.1.14 drift)
- .claude/hooks/pre-tool-use.js:新增 Rule 3(防误删 app/libs/*.aar,PaddleOCR SDK 70 MB,误删会导致 ice_ocr_rules profile 静默崩)
- .claude/skills/icevision-release/SKILL.md:新建(5 步 pre-flight + 4 步流水线 + Gitea 1.22.x APK 404 workaround + 大文件 POST 超时恢复 + 发版后 smoke 校验)

版本号 bump:
- versionCode 32 → 33
- versionName 0.1.32 → 0.1.33
- user-changelog.md 顶部新增 v0.1.33 条目"
git log -1 --format='%B' | grep -i 'Co-Authored-By' && echo "VIOLATION" || echo "OK"
```

---

## Task 5: git tag + push latest ref

**Files:** git operations only

- [ ] **Step 5.1: 验证 commit 链**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git log --oneline -5  # 期望:spec commit → docs(knowledge) → feat(rules) → feat(cases) → chore(automation)
git status --short  # 期望:clean(无 working tree 改动)
```

- [ ] **Step 5.2: git tag v0.1.33**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git tag v0.1.33
git tag --list "v0.1.33"  # 期望:1 行匹配
git rev-parse v0.1.33  # 期望:返回 chore(automation) commit SHA
```

- [ ] **Step 5.3: push v0.1.33 + 移 latest ref**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
git push origin v0.1.33  # 上传 tag
git tag -f latest  # 本地 latest 移到当前 commit
git push origin :latest  # 删除远端旧 latest ref(若存在)
git push origin latest  # 上传新 latest ref
git ls-remote --tags origin | grep -E "v0.1.33|latest"  # 期望:v0.1.33 + refs/tags/latest 都存在
```

期望:tag 上传成功 + latest ref 指向 v0.1.33 SHA。

- [ ] **Step 5.4: 验证 tag ↔ commit ↔ versionCode 三对齐**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
TAG_SHA=$(git rev-parse v0.1.33)
COMMIT_SHA=$(git log --format='%H' -1)
grep versionCode app/build.gradle.kts | head -1  # 期望:versionCode = 33
[[ "$TAG_SHA" == "$COMMIT_SHA" ]] && echo "TAG = COMMIT ✓" || echo "MISMATCH ✗"
```

期望:`TAG_SHA == COMMIT_SHA` + versionCode = 33。

---

## Task 6: icevision-release pre-flight 5 项检查

**Files:** N/A — 走 `icevision-release` skill

> **INVOKE SKILL**: `/icevision-release`(或调用 `.claude/skills/icevision-release/SKILL.md` 内步骤)

- [ ] **Step 6.1: JDK 17 stage**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
$JAVA_HOME/bin/java -version  # 期望:openjdk version 17.x
```

期望:Java 17 输出。

- [ ] **Step 6.2: v1 signing 启用**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
grep -A 2 "enableV1Signing" app/build.gradle.kts | head -5
```

期望:`enableV1Signing = true`。

- [ ] **Step 6.3: Gitea PAT**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
test -f "gradle.token.properties" && echo "PAT file exists" || echo "MISSING"
grep -E "gitea.token|TOKEN" gradle.token.properties 2>/dev/null  # 期望:有 token
```

期望:PAT 文件存在 + 包含 token。

- [ ] **Step 6.4: AAR + ONNX 模型**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
test -f "app/libs/ppocr-sdk.aar" && echo "AAR OK" || echo "MISSING (run tools/build-ppocr-sdk.sh)"
test -f "app/src/main/assets/models/det/inference.onnx" && echo "det ONNX OK" || echo "MISSING (run tools/download-ppocr-models.sh)"
test -f "app/src/main/assets/models/rec/inference.onnx" && echo "rec ONNX OK" || echo "MISSING (run tools/download-ppocr-models.sh)"
```

期望:3 个文件全部存在。

- [ ] **Step 6.5: cert-pin 锚点**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
grep -E "signerCertSha256" .claude/skills/icevision-release/SKILL.md  # 期望:4a21f4...3043
grep -E "signerCertSha256|cert" ~/.gradle/gradle.properties 2>/dev/null | head -5  # 期望:本地签名配置
```

期望:cert-pin `4a21f4...3043` 在 skill + release 配置中匹配。

---

## Task 7: assembleRelease + generateVisionLatestJson + archiveVisionRelease

**Files:** N/A — gradle task 链

- [ ] **Step 7.1: clean release outputs**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat clean assembleRelease -PmodelProfile=ice_ocr_rules 2>&1 | tail -30
```

期望:`BUILD SUCCESSFUL` + APK at `app/build/outputs/apk/release/app-release.apk`。

- [ ] **Step 7.2: 验证 APK 签名 v1+v2**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
APK="app/build/outputs/apk/release/app-release.apk"
ls -la "$APK"
# 使用 apksigner 验证
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$APK" 2>&1 | head -10  # 期望:v1 + v2 scheme 都有
```

期望:APK 签名包含 v1 scheme(jar signature,`META-INF/CERT.RSA`)。

- [ ] **Step 7.3: generateVisionLatestJson**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat generateVisionLatestJson 2>&1 | tail -15
```

期望:`BUILD SUCCESSFUL` + `vision-latest.json` at `app/build/outputs/apk/release/vision-latest.json`。

- [ ] **Step 7.4: 验证 JSON 内容**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
JSON="app/build/outputs/apk/release/vision-latest.json"
cat "$JSON" | python -c "
import sys, json
d = json.load(sys.stdin)
assert d['versionCode'] == 33, f\"versionCode={d['versionCode']}\"
assert d['versionName'] == '0.1.33', f\"versionName={d['versionName']}\"
assert d['signerCertSha256'].startswith('4a21f4'), f\"cert_sha={d['signerCertSha256'][:20]}\"
print('JSON OK:', d['versionCode'], d['versionName'], d['signerCertSha256'][:24])
"
```

期望:`JSON OK: 33 0.1.33 4a21f4...`。

- [ ] **Step 7.5: archiveVisionRelease**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat archiveVisionRelease 2>&1 | tail -15
ls -la build/generated/release-staging/  # 期望:staged files
```

期望:`BUILD SUCCESSFUL` + `build/generated/release-staging/` 含 APK + JSON。

---

## Task 8: uploadVisionReleaseToGitea (with 1.22.x workaround)

**Files:** N/A — gradle task + Gitea API

- [ ] **Step 8.1: uploadVisionReleaseToGitea (run gradle task)**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat uploadVisionReleaseToGitea 2>&1 | tail -40
```

期望:包含 "POST JSON" + "POST APK" + "attachment UUID" + "rewriting apkUrl" 日志。

- [ ] **Step 8.2: 验证 Gitea release page 上 JSON 可达**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
# Gitea 1.22.x:releases/download/latest/<file>.json 可达
URL_JSON="http://125.211.45.14:3000/api/v1/repos/icespiritai/vision/releases/download/latest/vision-latest.json"
curl -sSL "$URL_JSON" 2>&1 | python -c "
import sys, json
d = json.load(sys.stdin)
assert d['versionCode'] == 33
print('JSON OK via Gitea:', d['versionCode'])
"
```

期望:`JSON OK via Gitea: 33`。

- [ ] **Step 8.3: 验证 APK download URL(走 attachments/<uuid> 路径)**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
JSON_URL="http://125.211.45.14:3000/api/v1/repos/icespiritai/vision/releases/download/latest/vision-latest.json"
APK_URL=$(curl -sSL "$JSON_URL" | python -c "import sys,json; print(json.load(sys.stdin)['apkUrl'])")
echo "APK URL: $APK_URL"
# 期望 URL 含 attachments/<uuid>(Gitea 1.22.x workaround)
curl -sSL --head "$APK_URL" 2>&1 | head -5  # 期望:HTTP 200 + Content-Length
```

期望:`apkUrl` 含 `attachments/<uuid>`(不是 `releases/download/latest/...apk`)+ HTTP 200。

- [ ] **Step 8.4: APK SHA256 校验**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
JSON_URL="http://125.211.45.14:3000/api/v1/repos/icespiritai/vision/releases/download/latest/vision-latest.json"
EXPECTED_SHA=$(curl -sSL "$JSON_URL" | python -c "import sys,json; print(json.load(sys.stdin)['sha256'])")
LOCAL_SHA=$(sha256sum "app/build/outputs/apk/release/app-release.apk" | awk '{print $1}')
echo "expected: $EXPECTED_SHA"
echo "local:    $LOCAL_SHA"
[[ "$EXPECTED_SHA" == "$LOCAL_SHA" ]] && echo "SHA256 MATCH ✓" || echo "SHA256 MISMATCH ✗"
```

期望:`SHA256 MATCH ✓`。

---

## Task 9: Post-release smoke(真机拉 update dialog)

**Files:** N/A — 真机验证

- [ ] **Step 9.1: 记录发版元数据到本地 archive**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
mkdir -p build/generated/release-staging/v0.1.33
cp app/build/outputs/apk/release/app-release.apk build/generated/release-staging/v0.1.33/
cp app/build/outputs/apk/release/vision-latest.json build/generated/release-staging/v0.1.33/
sha256sum app/build/outputs/apk/release/app-release.apk > build/generated/release-staging/v0.1.33/SHA256
ls -la build/generated/release-staging/v0.1.33/
```

期望:`v0.1.33/` 目录含 APK + JSON + SHA256 文件。

- [ ] **Step 9.2: 真机拉 update dialog 验证**

```bash
# 真机 USB 连接 + IceSpiritAI_Vision APK 已安装
adb shell pm clear com.icespiritai.vision  # 清理 ghost state(踩坑记录见 CLAUDE.md)
adb install app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.icespiritai.vision/.MainActivity  # 启动 app
adb logcat -c
adb logcat -d 2>&1 | grep -E "update|version"  # 期望:看到 v0.1.33 update dialog
```

期望:真机显示 v0.1.33 更新提示。

- [ ] **Step 9.3: 标记发版完成**

```bash
cd "d:/GitHub/IceSpiritAI_Vision"
echo "$(date -Iseconds) | v0.1.33 released | commit $(git rev-parse v0.1.33) | tag=v0.1.33 | latest_ref=$(git rev-parse latest)" >> build/generated/release-staging/v0.1.33/HISTORY
cat build/generated/release-staging/v0.1.33/HISTORY
```

期望:HISTORY 文件末尾追加 1 行发版时间 + commit SHA + tag。

---

## 验收标准

- [ ] Task 1 commit SHA 已生成,`git log -1 --format='%B' | grep Co-Authored-By` 为空
- [ ] Task 2 commit 同上,`ad_signage_rules.json` 121 条规则
- [ ] Task 3 commit 同上,`违规案例/` 66 张 + 总册 926 行
- [ ] Task 4 commit 同上,`versionCode = 33` + user-changelog.md 顶部 = `v0.1.33`
- [ ] Task 5:`git tag v0.1.33` + `git tag latest` 指向同一 commit SHA
- [ ] Task 6:pre-flight 5 项检查全通过(JDK 17 / v1 signing / Gitea PAT / AAR+ONNX / cert-pin)
- [ ] Task 7:`assembleRelease` 成功 + APK 签名 v1+v2 + JSON `versionCode=33` + cert `4a21f4...3043`
- [ ] Task 8:Gitea JSON 200 OK + APK URL 含 `attachments/<uuid>` 路径 + SHA256 匹配
- [ ] Task 9:真机拉 update dialog 显示 v0.1.33

## 风险 & 缓解

| 风险 | 缓解 |
|---|---|
| Gitea 1.22.x `releases/download/latest/<file>.apk` 404 | 走 `attachments/<uuid>` workaround(CLAUDE.md / icevision-release skill 已有记录) |
| 大文件 POST 超时(HTTP 100 卡死) | 先 POST JSON(小,~1s)再 POST APK(`--max-time 900`) |
| `gradle.token.properties` 误 commit | PreToolUse hook Rule 2 拦截;`git add` 显式路径 + `git status` 二次校验 |
| `app/libs/*.aar` 误删 | PreToolUse hook Rule 3 拦截;`tools/build-ppocr-sdk.sh` 重新生成 |
| commit 1-4 任一 commit 有 `Co-Authored-By` trailer | 每个 commit 后立即 `git log -1 \| grep -i Co-Authored-By` 校验 |
| 知识库迁移中文路径 UTF-8 | git 已 UTF-8;但 `git add` 显式路径避免 `git add -A`(hook 拦截) |
| 91 项 working tree 一次 `git add -A` | PreToolUse hook Rule 1 拦截,强制分 4 个 commit 显式 add |