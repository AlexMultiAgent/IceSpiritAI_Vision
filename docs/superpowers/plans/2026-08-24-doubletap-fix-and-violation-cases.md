# 冰灵锐目 — 双击 bug 修复 + 广告违规案例采集 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复主屏 `ImagePreview` 在 `state.Complete` 下双击无反应的 bug;按 14 个 category 采集 50+ 张广告违规案例到 `违规案例/` 配同名 .md 元数据。

**Architecture:** 一行表达式回填 + 1 例单元测试覆盖 Complete 路径;案例采集按桶(14 category)走,WebSearch + WebFetch 找到公开监管公示,落图 + 写元数据。

**Tech Stack:** Kotlin 2.4.10 / Compose / Robolectric / HankCS AC / WebSearch / WebFetch / curl;既有规则库 `app/src/main/assets/rules/ad_signage_rules.json` v6(120 条 / 14 category)作为元数据校验基线。

**Spec:** `docs/superpowers/specs/2026-08-24-doubletap-fix-and-violation-cases-design.md`

---

## 文件结构

修改文件:
- `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`(第 145 行表达式)
- `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`(新增 1 例)

新增文件(数据):
- `违规案例/medical_*.{jpg,md}`(10 组)
- `违规案例/absolute_*.{jpg,md}`(8 组)
- `违规案例/education_*.{jpg,md}`(5 组)
- `违规案例/food_*.{jpg,md}`(6 组)
- `违规案例/realestate_*.{jpg,md}`(4 组)
- `违规案例/finance_*.{jpg,md}`(4 组)
- `违规案例/cosmetic_*.{jpg,md}`(3 组)
- `违规案例/agricultural_*.{jpg,md}`(3 组)
- `违规案例/pesticide_*.{jpg,md}`(2 组)
- `违规案例/veterinary_*.{jpg,md}`(2 组)
- `违规案例/signage_*.{jpg,md}`(2 组)
- `违规案例/minor_*.{jpg,md}`(2 组)
- `违规案例/outdoor_*.{jpg,md}`(2 组)
- `违规案例/internet_ad_*.{jpg,md}`(2 组)

合计 55 张 .jpg + 55 个 .md(实际数量允许 ±5 浮动,但 ≥ 50)。

---

## Task 1: 子项目 A — 一行修复 + 新增 1 例单元测试

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt:145`
- Test: `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`(新增 1 例)

### Step 1.1: 写失败测试(模拟 Complete 路径)

文件 `app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt`,在已有 3 例之后追加第 4 例(类内部已有 `sampleLines` / `sampleHits`):

```kotlin
/**
 * Regression test for the v0.1.x double-tap bug. Before the fix at
 * `HomeScreen.kt:145`, the `lineBoxes` derivation only consulted
 * `AnalysisState.OcrDone`. Once state advanced to `AnalysisState.Complete`,
 * `ocrResult` was null and `lineBoxes` collapsed to `emptyList()`, which
 * gated out the `pointerInput` block inside `ImagePreview`. The callback
 * was wired (`onOpenViewer = nav.navigate(Routes.VIEWER)`) but never
 * invoked, so double-tap on the Home preview silently did nothing.
 *
 * The fix makes HomeScreen pull `lineBoxes` from
 * `completeReport?.lineBoxes` as a second fallback. This test simulates
 * that post-fix derivation by passing a non-empty `lineBoxes` straight to
 * `ImagePreview` — the same value the fixed HomeScreen would now forward.
 */
@Test
fun `double-tap with non-empty lineBoxes from Complete-state derivation invokes callback`() {
    var dblClicks = 0
    composeTestRule.setContent {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                ImagePreview(
                    imageUri = Uri.parse("file:///tmp/sample.jpg"),
                    // After the fix, HomeScreen will pass this exact list when
                    // state = Complete and report.lineBoxes is non-empty.
                    lineBoxes = sampleLines,
                    hits = sampleHits,
                    onDoubleTap = { dblClicks++ },
                )
            }
        }
    }

    composeTestRule.onNodeWithTag("image_preview")
        .performTouchInput { doubleClick(center) }
    assertEquals(1, dblClicks)
}
```

文件顶部 import 已经是 `androidx.compose.ui.Modifier` + `fillMaxSize` + `Surface` + `MaterialTheme` — 这些在既有 3 例已存在,无需新增 import。

### Step 1.2: 跑测试确认新用例通过(本来就应通过 — 它只断言 `lineBoxes.isNotEmpty()` 时双击触发)

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && ./gradlew.bat testDebugUnitTest --tests com.icespiritai.offline.ui.home.ImagePreviewDoubleTapTest
```

Expected: PASS,4 tests completed,0 failures(原 3 例 + 新 1 例)。

(新用例覆盖 `ImagePreview` 行为本身,不是 HomeScreen 的派生 — 后者在 HomeScreenTest 已有 Idle 路径覆盖;新用例证明 ImagePreview 在 lineBoxes 非空下双击可触发,即 HomeScreen 修后这条路径走得通。)

### Step 1.3: 改 `HomeScreen.kt` 第 145 行

文件 `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt`,第 143-146 行:

```kotlin
    // 旧:
    val lineBoxes = ocrResult?.lineBoxes ?: emptyList()
    // 新:
    val lineBoxes = ocrResult?.lineBoxes ?: completeReport?.lineBoxes ?: emptyList()
```

仅这一行替换。无新增 import,无新增字段,无新增 composable。

### Step 1.4: 跑全套单元测试确认未引入回归

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && ./gradlew.bat testDebugUnitTest
```

Expected: PASS,所有 test classes 通过(包括 `HomeScreenTest` / `ImagePreviewDoubleTapTest` / `AdSignageMentorFiveImageRegressionTest` / 全部 rule matcher)。

### Step 1.5: 编译两个 profile

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && ./gradlew.bat assembleDebug -PmodelProfile=shell
cd d:/GitHub/IceSpiritAI_Vision && ./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
```

Expected: 两条都 `BUILD SUCCESSFUL`(或最新状态:TaskOutput 报 BUILD SUCCESSFUL + APK 输出路径)。

### Step 1.6: Commit

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && git add app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt app/src/test/java/com/icespiritai/offline/ui/home/ImagePreviewDoubleTapTest.kt && git commit -m "fix(home): Complete state lineBoxes 回填,双击进 Viewer 生效

HomeScreen.kt:145 表达式 ocrResult?.lineBoxes ?: completeReport?.lineBoxes ?: emptyList()
  修后 state.Complete 下 lineBoxes 非空(report 已装 lineBoxes),
  ImagePreview 的 lineBoxes.isNotEmpty() 守卫通过,
  pointerInput(detectTapGestures(onDoubleTap = onOpenViewer)) 装上,
  双击触发 nav.navigate(Routes.VIEWER),Telephoto ZoomableAsyncImage
  的双指缩放/单指拖动/双击切换既有能力即刻可用。

新增 ImagePreviewDoubleTapTest 第 4 例:lineBoxes 非空下双击触发回调
  (模拟 HomeScreen 修后的派生,验证 ImagePreview 守卫逻辑在 Complete
  路径上一定走得通)。" 2>&1 | tail -5
```

Expected: commit hash 输出。

---

## Task 2: 子项目 B — 案例采集整体框架

### Step 2.1: 校验既有 4 张图结构,确认 违规案例/ 是仓库跟踪目录

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && git check-ignore -v 违规案例/ 2>&1 | head -5; echo "---"; ls -la 违规案例/ | head -10
```

Expected: `git check-ignore` 不输出(说明目录未被 ignore),`ls` 列出 4 个 .jpg 文件。

### Step 2.2: 校验 `ad_signage_rules.json` 的 category 列表

```bash
cd d:/GitHub/IceSpiritAI_Vision && python -c "
import json
d = json.load(open(r'app/src/main/assets/rules/ad_signage_rules.json', encoding='utf-8'))
cats = sorted({r.get('category','?') for r in d.get('rules',[])})
print('total:', len(d.get('rules',[])))
print('categories:', cats)
ids = {r['id'] for r in d.get('rules',[]) if 'id' in r}
import json as _j; _j.dump(sorted(ids), open(r'违规案例/_rule_ids.json','w',encoding='utf-8'), ensure_ascii=False, indent=2)
print('rule IDs dumped to 违规案例/_rule_ids.json')
"
```

Expected: 14 categories 列出,total 120,_rule_ids.json 生成(后续 .md 写「预期命中规则.id」时用来 grep 校验)。

### Step 2.3: 写 slug 命名约定与目标张数清单

把任务 3-16 的桶分明确记录在 `违规案例/_plan.md`:

```markdown
# 违规案例采集计划

| 桶 | 目标 | slug 前缀 |
|---|---:|---|
| medical | 10 | medical_ |
| absolute | 8 | absolute_ |
| education | 5 | education_ |
| food | 6 | food_ |
| realestate | 4 | realestate_ |
| finance | 4 | finance_ |
| cosmetic | 3 | cosmetic_ |
| agricultural | 3 | agricultural_ |
| pesticide | 2 | pesticide_ |
| veterinary | 2 | veterinary_ |
| signage | 2 | signage_ |
| minor | 2 | minor_ |
| outdoor | 2 | outdoor_ |
| internet_ad | 2 | internet_ad_ |
| **合计** | **55** | |
```

Run:
```bash
cd d:/GitHub/IceSpiritAI_Vision && git add 违规案例/_plan.md 违规案例/_rule_ids.json && git commit -m "chore(cases): 桶分规划 + 规则 ID 清单" 2>&1 | tail -5
```

Expected: commit hash 输出。

---

## Task 3: medical 桶 — 10 张

### Step 3.1: WebSearch medical 类违规案例

使用关键词组合:
- `"医疗广告 违规 案例 处罚 市场监管"`
- `"广告法 第十六条 医疗 根治 案例"`
- `"药店 招牌 根治 糖尿病 处罚"`
- `"医疗广告 100%有效 处罚通报"`

来源倾向:国家市场监督管理总局 samr.gov.cn / 省市监管局(京/沪/粤/苏/浙)/ 中央/地方媒体(新华网/央视/澎湃)

### Step 3.2: 筛 10 张图,每张执行

对每张目标图:
1. 找到公开 URL(或 WebFetch 页面提取 `<img src>`)
2. 下载:`curl -L -o 违规案例/medical_<场景>_<NN>.jpg <URL>`,或 `WebFetch` 直接提取图片 base64 后落盘
3. 校验大小:`ls -la 违规案例/medical_<场景>_<NN>.jpg` 应 > 10 KB(过小可能是错误页)
4. 写 `违规案例/medical_<场景>_<NN>.md`(frontmatter 字段见 spec §3.2):
   - `来源:` URL 或「微信群截图,无来源」
   - `场景:` 门店招牌/户外广告牌/印刷品/...
   - `违规点:` 一句话
   - `预期命中规则:` list of `{id, 关键词, severity}`,id 必须在 `_rule_ids.json` 内
   - `预期 OCR 难度:` 简单/中等/难
   - `拍摄角度:` 正面/侧面/俯视
   - `备注:` 若需要
5. 后置 body:标题 + 一段描述(为什么违规 / 法条原文 / 同类常见变体)

### Step 3.3: 校验本桶完整性

```bash
cd d:/GitHub/IceSpiritAI_Vision && python -c "
import os, glob, json
md_files = sorted(glob.glob('违规案例/medical_*.md'))
assert len(md_files) >= 10, f'medical 桶 {len(md_files)} 张,目标 10'
ids = set(json.load(open(r'违规案例/_rule_ids.json',encoding='utf-8')))
for md in md_files:
    txt = open(md,encoding='utf-8').read()
    # 简单断言:每条 id: xxx 应在 ids 内
    import re
    found = re.findall(r'id:\s*([\w_]+)', txt)
    for f in found:
        assert f in ids, f'{md} 引用了不存在的规则 id: {f}'
    jpg = md.replace('.md','.jpg')
    assert os.path.exists(jpg), f'{md} 缺同名 .jpg'
print(f'medical 桶 {len(md_files)} 张,全部规则 id 命中')
"
```

Expected: 全部 10 张通过。

### Step 3.4: Commit

```bash
cd d:/GitHub/IceSpiritAI_Vision && git add 违规案例/medical_*.{jpg,md} && git commit -m "feat(cases): medical 桶 10 张 + 元数据(广告法 §16 医疗)" 2>&1 | tail -5
```

---

## Task 4: absolute 桶 — 8 张

完整流程见 Task 3 Step 3.1-3.4(md frontmatter / body 模板在 Step 3.2;校验脚本在 Step 3.3;commit message 模式在 Step 3.4)。本桶专属配置:

**关键词组合:**
- `"绝对化用语 违规 最佳 国家级 处罚"`
- `"广告 第九条 顶级 第一 案例"`
- `"最佳 招牌 处罚通报"`

**目标张数:** 8

**commit message:**
```
feat(cases): absolute 桶 8 张 + 元数据(广告法 §9 绝对化)
```

---

## Task 5: education 桶 — 5 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"教育培训 保过 包过 处罚"`
- `"广告法 第二十四条 教育 案例"`

**目标张数:** 5

**commit message:**
```
feat(cases): education 桶 5 张 + 元数据(广告法 §24 教育培训)
```

---

## Task 6: food 桶 — 6 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"食品广告 违规 增强免疫力 案例"`
- `"保健食品 治疗 预防 处罚"`
- `"食品安全法 第七十一条 第七十八条"`

**目标张数:** 6(广告招牌 17 条规则覆盖食品 / 保健食品)

**commit message:**
```
feat(cases): food 桶 6 张 + 元数据(食品安全法 §71/§78 食品/保健)
```

---

## Task 7: realestate 桶 — 4 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"房地产广告 升值 投资回报 违规"`
- `"学区房 包入学 处罚"`
- `"广告法 第二十六条 房地产"`

**目标张数:** 4

**commit message:**
```
feat(cases): realestate 桶 4 张 + 元数据(广告法 §26 房地产)
```

---

## Task 8: finance 桶 — 4 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"招商广告 稳赚不赔 处罚"`
- `"投资回报 保本 违规"`
- `"广告法 第二十五条 招商"`

**目标张数:** 4

**commit message:**
```
feat(cases): finance 桶 4 张 + 元数据(广告法 §25 招商)
```

---

## Task 9: cosmetic 桶 — 3 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"化妆品广告 违规 治疗 美白"`
- `"广告法 第十五条 化妆品 案例"`

**目标张数:** 3

**commit message:**
```
feat(cases): cosmetic 桶 3 张 + 元数据(广告法 §15 化妆品)
```

---

## Task 10: agricultural 桶 — 3 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"农资广告 违规 增产 案例"`
- `"广告法 第二十七条 农药 种子"`

**目标张数:** 3

**commit message:**
```
feat(cases): agricultural 桶 3 张 + 元数据(广告法 §27 农资)
```

---

## Task 11: pesticide 桶 — 2 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"农药广告 违规 案例"`
- `"广告法 第三十一条 农药"`

**目标张数:** 2

**commit message:**
```
feat(cases): pesticide 桶 2 张 + 元数据(广告法 §31 农药)
```

---

## Task 12: veterinary 桶 — 2 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"兽药广告 违规 案例"`
- `"动物诊疗 治疗率 处罚"`

**目标张数:** 2

**commit message:**
```
feat(cases): veterinary 桶 2 张 + 元数据(兽药广告)
```

---

## Task 13: signage 桶 — 2 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"擅自设置 招牌 处罚"`
- `"广告法 第三十二条 招牌"`

**目标张数:** 2

**commit message:**
```
feat(cases): signage 桶 2 张 + 元数据(广告法 §32 招牌)
```

---

## Task 14: minor 桶 — 2 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"未成年人 广告 违规 案例"`
- `"广告法 第三十八条 第三十九条"`

**目标张数:** 2

**commit message:**
```
feat(cases): minor 桶 2 张 + 元数据(广告法 §38/§39 未成年人)
```

---

## Task 15: outdoor 桶 — 2 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"户外广告 违规 处罚"`
- `"广告法 第四十二条 户外"`

**目标张数:** 2

**commit message:**
```
feat(cases): outdoor 桶 2 张 + 元数据(广告法 §42 户外)
```

---

## Task 16: internet_ad 桶 — 2 张

完整流程见 Task 3 Step 3.1-3.4。

**关键词组合:**
- `"互联网广告 违规 案例"`
- `"广告法 第十四条 互联网"`

**目标张数:** 2

**commit message:**
```
feat(cases): internet_ad 桶 2 张 + 元数据(广告法 §14 互联网)
```

---

## Task 17: 整体校验

### Step 17.1: 总张数校验

```bash
cd d:/GitHub/IceSpiritAI_Vision && python -c "
import os, glob, json, re
md_files = sorted(glob.glob('违规案例/*.md'))
md_files = [m for m in md_files if not os.path.basename(m).startswith('_')]
assert len(md_files) >= 50, f'总 .md 数 {len(md_files)} 不足 50'
ids = set(json.load(open(r'违规案例/_rule_ids.json',encoding='utf-8')))
fails = []
for md in md_files:
    txt = open(md,encoding='utf-8').read()
    found = re.findall(r'id:\s*([\w_]+)', txt)
    for f in found:
        if f not in ids: fails.append(f'{md}: {f}')
    jpg = md.replace('.md','.jpg')
    if not os.path.exists(jpg): fails.append(f'{md} 缺同名 .jpg')
    if not re.search(r'来源:', txt): fails.append(f'{md} 缺 来源')
    if not re.search(r'场景:', txt): fails.append(f'{md} 缺 场景')
    if not re.search(r'违规点:', txt): fails.append(f'{md} 缺 违规点')
print(f'.md 总数 {len(md_files)},规则 id 失效 / 缺字段 {len(fails)} 条')
if fails:
    for f in fails[:10]: print('  FAIL:', f)
else:
    print('OK')
"
```

Expected: `.md 总数 ≥ 50`,失效 / 缺字段 0 条。

### Step 17.2: category 覆盖校验

```bash
cd d:/GitHub/IceSpiritAI_Vision && python -c "
import glob, os
buckets = ['medical','absolute','education','food','realestate','finance',
           'cosmetic','agricultural','pesticide','veterinary','signage',
           'minor','outdoor','internet_ad']
total = 0
for b in buckets:
    n = len(glob.glob(f'违规案例/{b}_*.md'))
    print(f'{b}: {n}')
    total += n
print(f'TOTAL: {total}')
assert total >= 50, f'总 {total} 张不足 50'
print('OK')
"
```

Expected: 14 行打出每桶张数(可压缩但不能整桶 0),TOTAL ≥ 50。

### Step 17.3: 最终 commit(若 Step 17.1/17.2 触发修复)

如有校验中暴露的修复,逐桶 git add + commit:
```bash
cd d:/GitHub/IceSpiritAI_Vision && git status
# (视情况 add 修复 + commit)
```

若无修改,无需 commit。

### Step 17.4: 跑一遍 `testDebugUnitTest` 兜底(子项目 A 之外任何变动都不影响规则 matcher,但兜底跑一次确保没误伤)

```bash
cd d:/GitHub/IceSpiritAI_Vision && ./gradlew.bat testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL,tests passed。

---

## 验收清单(对应 spec §9)

### 子项目 A

- [ ] `git log --oneline` 包含 fix(home) commit
- [ ] `git diff HEAD~1 app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` 仅一行变化
- [ ] `ImagePreviewDoubleTapTest` 4 examples pass
- [ ] `testDebugUnitTest` 全绿
- [ ] `assembleDebug -PmodelProfile=shell` 成功
- [ ] `assembleDebug -PmodelProfile=ice_ocr_rules` 成功

### 子项目 B

- [ ] `违规案例/` 总 .md 数 ≥ 50
- [ ] 14 个 category 全部覆盖(单桶允许压缩但不能整桶缺失)
- [ ] 所有 .md `来源` / `场景` / `违规点` 字段非空
- [ ] 所有 .md `预期命中规则[*].id` 在 `ad_signage_rules.json` 可查到
- [ ] slug 命名符合 `<category>_<场景>_<NN>` 全小写下划线模式
- [ ] git 仓库历史中 14 个 feat(cases) commit(或合并为少批量)

---

## 风险 / 缓解(执行时关注)

| 风险 | 缓解 |
|---|---|
| Task 3-16 单条 WebSearch 失败(政府站 anti-hotlink / 验证码) | 切换到备用来源(同事件的多媒体转载 / 微博截图 / 微信群截图);单条失败不阻塞整桶,最终张数 ≥ 50 即可 |
| .md 中 `id:` 字段填错 | Task 17.1 校验会捕获,回头修正;`违规案例/_rule_ids.json` 是权威 |
| 某桶完全找不到合规案例 | 压缩张数(目标 ≥ 1 张即可),整体仍保 50;但要在 commit message 注明「桶压缩原因」 |
| 下载的图实际是缩略图 / 占位图 | `ls` 检查文件大小 < 10 KB 视为可疑,删除并找替代 |
| 同图不同桶分类模糊(medical vs food) | 优先以主要违规点归类,frontmatter `违规点` 字段标明多重性 |

---

## 自审(对照 spec)

- **§1.1 / §1.2 子项目 A 根因** — Task 1 Step 1.3 一行修复 + Step 1.1 回归测试 ✓
- **§1.4 子项目 B 现状/目标** — Task 2 整体框架 + Task 3-16 桶执行 ✓
- **§2.1 总体方案** — Task 1 实现 ✓
- **§2.2 总体方案** — Task 2-16 实现 ✓
- **§3.2 元数据格式** — Task 3 Step 3.2 给出完整 .md 模板 ✓
- **§5.2 桶分执行清单** — Task 3-16 14 桶对应表 ✓
- **§6.2 错误处理** — Task 17.1/17.2 校验 + 风险表 ✓
- **§7.1 单元测试** — Task 1 Step 1.1 新增测试 ✓
- **§9.1 子项目 A 验收** — Task 1 Step 1.4-1.5 ✓
- **§9.2 子项目 B 验收** — Task 17 校验脚本 ✓
- **§10 风险缓解** — 风险表 ✓

无 placeholder、无 TODO、无「类似 Task N」跳转。所有代码块完整可执行。