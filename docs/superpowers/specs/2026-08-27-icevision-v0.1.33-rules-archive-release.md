# 冰灵锐目 — v0.1.33 规则扩充 + 案例归档 + 发版

| 项 | 值 |
| --- | --- |
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-27 |
| Spec 状态 | 待评审 |
| 上游 spec | `2026-08-24-doubletap-fix-and-violation-cases-design.md`(子项目 B:采集 50+ 张案例 → 本次扩到 66 张) |
| 关联项目根指令 | `CLAUDE.md`(知识库时效性整理 + Release 三段式打标) |

本文档只覆盖「把 2026-08-27 累积的未提交改动合成 v0.1.33 发版」这一收尾动作。**没有新功能、没有新架构、没有新规则扩展**。

---

## 1. 背景与目标

### 1.1 现状(2026-08-27,本文档撰写时)

`git status` 累积 91 项 working tree 改动,涵盖 4 类工作:

| 工作 | 范围 | 状态 |
| --- | --- |---|
| 规则扩充 | `ad_signage_rules.json` +28 行 = 121 条,新增 `ad_signage_signage_food_safety_implication`(覆盖 #49「安全放心」+ #52「✅纯天然」暗示安全性违规) | 已加,待发版 |
| 案例归档 | `违规案例/` 下 66 张图片(01-66 编号)+ `_违规档案总册.md`(926 行)+ `_plan.md` + `_rule_ids.json` + `_text_plan.md` + `_rename_map.md` | 已写完,待发版 |
| 知识库时效性 | `知识库/已废止/` 新建 + 8 份法规迁移(2016/2017 工商 / 卫计委令废止 2 份 + 2027-03-16 食品标识 3 份新版替换 + 1 份广告业务改名)| 已迁移,待发版 |
| 自动化基础设施 | `CLAUDE.md` 新增"知识库时效性整理"段 + `project-commit SKILL.md` 新增"Release 三段式打标"段 + `pre-tool-use.js` 新增 Rule 3(防误删 `app/libs/*.aar`)+ `.claude/skills/icevision-release/` 新建 SKILL.md | 已写完,待发版 |

**v0.1.32 已发版**(3fbae75,2026-08-26 CaptureBar UI affordance);本批改动未被任何 commit 追踪,亦未发版。

### 1.2 目标

把 working tree 的 91 项改动:

1. `git add` 显式路径(避免敏感文件)+ `git commit`(作者 `AlexMultiAgent`,无 Claude trailer)
2. **Release 三段式打标**(CLAUDE.md / `project-commit SKILL.md` 新固化):
   - `versionCode` 32 → 33,`versionName` "0.1.32" → "0.1.33"
   - `user-changelog.md` 顶部新增 `## v0.1.33 · 2026-08-27` 条目
   - `git tag v0.1.33` + push `latest` ref
3. 走 `icevision-release` 流水线(`assembleRelease → generateVisionLatestJson → archiveVisionRelease → uploadVisionReleaseToGitea`),release pre-flight(JDK 17 / v1 signing / Gitea PAT / AAR+ONNX / cert-pin)+ 走 Gitea 1.22.x APK 404 workaround + 大文件 POST 超时恢复
4. 发版后 smoke 校验:JSON 可达 + APK SHA256 匹配 + 版本号 ≥ 33

### 1.3 非目标

- **不动** 任何代码逻辑(无新功能 / 无 bug fix / 无重构)
- **不扩展** 规则库(用户已选"已加 1 条即可",漏报扫描留 v0.1.34+)
- **不引入** 新依赖、新 source、构建参数
- **不发** 任何额外 release 工件(只走 icevision-release 标准流水线)

---

## 2. 总体方案

### 2.1 Commit 拆分策略

为避免一个巨型 commit 把规则扩充 / 案例归档 / 知识库迁移 / 自动化基础设施混在一起,按工作域拆为多个 commit:

| Commit | 主题 | 文件范围 |
| --- | --- |---|
| 1 | `docs(knowledge): 知识库时效性整理 2026-08-27 — 8 份法规迁移 / 已废止 / 2027 新版` | `知识库/已废止/` 新建 + 8 文件 mv/rename + 3 份食品标识 2027 新版 |
| 2 | `feat(rules): ad_signage_signage_food_safety_implication 暗示安全性规则` | `app/src/main/assets/rules/ad_signage_rules.json`(+28 行) |
| 3 | `feat(cases): 违规案例图片归档 — 66 张 + 总册(覆盖 medical/食品/教育/绝对化 14 个桶)` | `违规案例/01-66.{jpg,png}` + `_违规档案总册.md` + `_plan.md` + `_rule_ids.json` + `_text_plan.md` + `_rename_map.md` + 5 个旧 jpg 删除 |
| 4 | `chore(automation): project-commit skill + PreToolUse hook + CLAUDE.md 知识库时效性 + icevision-release skill` | `CLAUDE.md` + `.claude/skills/project-commit/SKILL.md` + `.claude/hooks/pre-tool-use.js` + `.claude/skills/icevision-release/SKILL.md`(新建) |

> **为什么 4 个 commit 拆开**:发版后若某类工作需要 revert(极少见),可单独回滚;且 release 三段式打标在 commit 4 完成后执行,确保 tag SHA = APK SHA = JSON SHA 三对齐(避免 v0.1.14 drift)。

### 2.2 Release 三段式打标

紧接 commit 4 后执行(同一次 `project-commit` 调用,见 `project-commit SKILL.md` 122-180 行):

```kotlin
// app/build.gradle.kts (defaultConfig block)
versionCode = 33
versionName = "0.1.33"
```

```markdown
## v0.1.33 · 2026-08-27

- **规则扩充(ad_signage_rules.json)**:新增 `ad_signage_signage_food_safety_implication` 暗示安全性规则(20 关键词),覆盖 #49「安全放心」+ #52「✅纯天然」类典型违规;广告招牌规则数 118 → 121
- **违规案例归档**:66 张真实公开广告图片(01-66 编号)+ 完整 `_违规档案总册.md`(926 行 / 14 个桶 / 严重度 Critical×59 + Warning×7 + Info×0);新增发现:#06 商业借用军政形象、#49 + #52 保健食品暗示安全性违规
- **知识库时效性**:2 份已废止法规迁至 `知识库/已废止/`(户外广告登记管理规定 2016 / 母乳代用品销售管理办法 2017);3 份食品标识 2027-03-16 新版替换(GB 7718 / GB 28050 / 食品标识管理规定);1 份广告业务改名(药品医疗器械保健食品特殊医学用途配方食品广告审查管理暂行办法)
- **自动化基础设施**:CLAUDE.md 新增「知识库时效性整理」段(规则 JSON regulation 字段必须指现行法规);project-commit skill 新增「Release 三段式打标」(版本号 + changelog + tag 同步);PreToolUse hook 新增 Rule 3(防误删 `app/libs/*.aar`);新建 icevision-release skill(发版流水线 5 步 pre-flight + 4 步流水线)
```

```bash
git tag v0.1.33
git push origin v0.1.33
git tag -f latest
git push origin :latest
git push origin latest
```

### 2.3 icevision-release 流水线

紧接 tag push 后执行(单独 `icevision-release` 调用,职责边界见 `icevision-release SKILL.md`):

1. **Pre-flight 5 步**(见 icevision-release SKILL §1):
   - JDK 17 stage (`/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`)
   - v1 signing enabled(CLAUDE.md 已配置)
   - Gitea PAT 在 `gradle.token.properties`(gitignored)
   - `app/libs/ppocr-sdk.aar` + `app/src/main/assets/models/{det,rec}/inference.onnx` 存在
   - cert-pin `signerCertSha256` = `4a21f4...3043`

3. **流水线 4 步**:
   - `./gradlew.bat assembleRelease` → APK at `app/build/outputs/apk/release/app-release.apk`
   - `./gradlew.bat generateVisionLatestJson` → `vision-latest.json` with `versionCode=33`
   - `./gradlew.bat archiveVisionRelease` → staging in `build/generated/release-staging/`
   - `./gradlew.bat uploadVisionReleaseToGitea` → Gitea `latest` tag + 抓 UUID + 改写 `apkUrl`(`attachments/<uuid>` 绕 Gitea 1.22.x 404)

4. **Post-release smoke 3 步**(icevision-release SKILL 的 post-release smoke 段):
   - `vision-latest.json` 可达 + versionCode ≥ 33 + cert SHA256 匹配
   - APK download URL 200 OK + SHA256 匹配(走 `attachments/<uuid>` 路径)
   - 在 app 内拉 update dialog 看到 v0.1.33 提示

---

## 3. 文件清单

### 3.1 Commit 1 — 知识库迁移

| 操作 | 路径 |
| --- | --- |
| 新建 | `知识库/已废止/户外广告登记管理规定_2016工商总局令86号废止.md` |
| 新建 | `知识库/已废止/母乳代用品销售管理办法_2017卫计委令17号废止.md` |
| 删除 | `知识库/广告业务/户外广告登记管理规定.md`(迁移) |
| 删除 | `知识库/广告业务/母乳代用品销售管理办法.md`(迁移) |
| 删除 | `知识库/广告业务/兽药广告审查发布规定.md`(改名 / 烟草替代) |
| 删除 | `知识库/食品标识/GB_28050-2011_预包装食品标签通则.md`(2027-03-16 废止) |
| 删除 | `知识库/食品标识/GB_7718-2011_预包装食品营养标签通则.md`(2027-03-16 废止) |
| 删除 | `知识库/食品标识/食品标识管理规定.md`(2027-03-16 废止) |
| 删除 | `知识库/食品标识/母乳代用品销售管理办法.md`(2017 卫计委令第 17 号废止) |
| 新建 | `知识库/食品标识/GB_28050-2011_预包装食品标签通则_2027-03-16废止.md` |
| 新建 | `知识库/食品标识/GB_7718-2011_预包装食品营养标签通则_2027-03-16废止.md` |
| 新建 | `知识库/食品标识/食品标识管理规定_2027-03-16废止.md` |
| 新建 | `知识库/广告业务/母乳代用品销售管理办法_广告法§22实质替代.md` |
| 新建 | `知识库/广告业务/兽药广告审查发布规定_广告业务广告审查发布标准修订发布.md` |

### 3.2 Commit 2 — 规则扩充

| 操作 | 路径 |
| --- | --- |
| 修改 | `app/src/main/assets/rules/ad_signage_rules.json`(+28 行,line 1198-1225) |

### 3.3 Commit 3 — 案例归档

| 操作 | 路径 |
| --- | --- |
| 新建 | `违规案例/01_碧桂园华美天樾_..._绝对化与数据引用.jpg` ~ `违规案例/66_小园玉粱紫玉米花青素_..._食品.jpg`(共 66 张图) |
| 新建 | `违规案例/_违规档案总册.md`(926 行) |
| 新建 | `违规案例/_plan.md` |
| 新建 | `违规案例/_rule_ids.json`(90 rule IDs) |
| 新建 | `违规案例/_text_plan.md` |
| 新建 | `违规案例/_rename_map.md` |
| 删除 | `违规案例/absolute_xieduhui_01.jpg` 等 5 个旧 jpg(已被 01-66 命名规范替换) |

### 3.4 Commit 4 — 自动化基础设施 + Release 打标

| 操作 | 路径 |
| --- | --- |
| 修改 | `CLAUDE.md`(+ 知识库时效性整理段 / + Claude Code 自动化表 / 规则计数 118→121 / 116→121) |
| 修改 | `.claude/skills/project-commit/SKILL.md`(+ Release 三段式打标段) |
| 修改 | `.claude/hooks/pre-tool-use.js`(+ Rule 3 防误删 .aar) |
| 新建 | `.claude/skills/icevision-release/SKILL.md` |
| 修改 | `app/build.gradle.kts`(`versionCode = 33`, `versionName = "0.1.33"`) |
| 修改 | `app/src/main/assets/user-changelog.md`(顶部新增 v0.1.33 条目) |

### 3.5 Git operations

```bash
# commit 1-4: 4 个独立 commit,作者 AlexMultiAgent
git add 知识库/已废止/ <rename 操作>
git commit -m "docs(knowledge): 知识库时效性整理 2026-08-27"
git add app/src/main/assets/rules/ad_signage_rules.json
git commit -m "feat(rules): ad_signage_signage_food_safety_implication 暗示安全性规则"
git add 违规案例/
git commit -m "feat(cases): 违规案例图片归档 — 66 张 + 总册"
git add CLAUDE.md .claude/skills/ .claude/hooks/ app/build.gradle.kts app/src/main/assets/user-changelog.md
git commit -m "chore(automation): skills + hooks + versionCode bump → v0.1.33"

# Release 三段式打标 (commit 4 内完成或紧随 commit 4)
git tag v0.1.33
git push origin v0.1.33
git tag -f latest
git push origin :latest
git push origin latest
```

---

## 4. 测试策略

本次发版无新功能,**单元测试范围 = 0**。

但仍需跑回归以确保发版基础稳:

```bash
./gradlew.bat testDebugUnitTest -PmodelProfile=shell  # 568 例基线
./gradlew.bat assembleDebug -PmodelProfile=shell       # 骨架 APK smoke
./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules  # Phase 1 APK(需 ONNX + AAR)
```

发版后额外 smoke(icevision-release SKILL §4):

- `vision-latest.json` URL 200 OK + `versionCode == 33` + `signerCertSha256 == 4a21f4...3043`
- APK download URL(走 `attachments/<uuid>` 路径)200 OK + SHA256 = `app/build/outputs/apk/release/app-release.apk` SHA256 一致
- 真机拉 in-app update dialog → 显示 v0.1.33

---

## 5. 验收标准

- [ ] commit 1-4 完成 + 作者 `AlexMultiAgent` + 无 `Co-Authored-By` trailer
- [ ] `git status` clean(working tree 无 untracked / modified)
- [ ] `app/build.gradle.kts` `versionCode = 33`, `versionName = "0.1.33"`
- [ ] `user-changelog.md` 顶部第一段 = `v0.1.33`(parser 断言通过)
- [ ] `git tag v0.1.33` + `git tag latest` 存在且指向 commit 4 SHA
- [ ] `assembleRelease` 成功(签名 APK v1+v2)
- [ ] `vision-latest.json` 上传 Gitea + URL 200 OK + versionCode 33
- [ ] APK 上传 Gitea + SHA256 匹配 + download URL 走 `attachments/<uuid>` 路径
- [ ] 真机拉 update dialog 看到 v0.1.33

---

## 6. 风险 & 缓解

| 风险 | 概率 | 影响 | 缓解 |
| --- | --- |---|---|
| Gitea 1.22.x APK 路由 404 | 高(已知) | 中 | 沿用 v0.1.31 workaround:抓 attachment `uuid` + 改写 `apkUrl` 为 `attachments/<uuid>` |
| 大文件 POST 超时(HTTP 100 卡死) | 中(已知) | 中 | 先 POST JSON 小(~1s)再 POST APK(`--max-time 900`) |
| `gradle.token.properties` 误 commit | 低(已有 hook) | 高 | PreToolUse hook Rule 2 已拦截;`git add` 显式路径再过一次 `git status` |
| `app/libs/*.aar` 误删 | 低(已有 hook) | 高 | PreToolUse hook Rule 3 已拦截(本次新增);`tools/build-ppocr-sdk.sh` 重新生成 |
| commit 4 把 release bump 写入 working tree 但忘记 commit | 低 | 中 | commit 4 主题内显式提"versionCode bump → v0.1.33",`project-commit` skill 二次校验 |
| v0.1.32 APK SHA 仍为 cached `latest` tag 引用 | 低 | 低 | `git tag -f latest` 强制移动 + `git push origin :latest` 删除远端旧 ref 再 push |
| 知识库迁移中 `.md` 文件名含中文 / 特殊字符 | 中 | 中 | git 已 UTF-8 处理,但 staging 时用 `git add 知识库/<full-path>` 显式 add,避免 `git add -A`(CLAUDE.md 强制) |
| 案例归档 91 项 untracked 一次 `git add -A` 会误纳 | 中 | 中 | PreToolUse hook Rule 1 拦截 `git add -A` / `git add .`,强制显式路径 |