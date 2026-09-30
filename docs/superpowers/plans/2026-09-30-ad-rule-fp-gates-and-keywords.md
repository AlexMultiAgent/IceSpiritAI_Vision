# 广告规则 FP gate + keyword 补盲区 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 2 个 ad 规则加 `categoryAnchorsAbsent` gate 消除「参照样本」误命中,给 4 个规则扩 keyword 覆盖 fixture #55 / #13 / #23 / #11 / #15,补 fixture #68 / #100 到 `违规案例/`。

**Architecture:**
- 主修改点是 [`app/src/main/assets/rules/ad_signage_rules.json`](../../app/src/main/assets/rules/ad_signage_rules.json) v20 → v21,version 字段同步 bump
- 每个规则改动先写 TDD 测试(`AdSignageRuleMatcherTest.kt`),跑通再 commit
- gate 走 AhoCorasick + categoryAnchorsAbsent 反向 anchor(模板:[`ad_signage_non_medical_institution_disease_advertisement`](../../app/src/main/java/com/icespiritai/offline/rules/AdSignageRuleMatcher.kt) KDoc)
- fixture 同步走 `cp`(`audit71/` → `违规案例/`)

**Tech Stack:**
- Kotlin 2.2.2 + AGP 9.3 + Gradle 9.7
- 序列化:[`kotlinx.serialization`](https://github.com/Kotlin/kotlinx.serialization)
- AhoCorasick:[`com.hankcs:algorithm`](https://github.com/hankcs/AhoCorasickDoubleArrayTrie)
- 测试:JUnit 4(`@Test` / `assertTrue`)+ Robolectric(JVM 端跑 AC matcher,不需要 device)

**关联文档:**
- Spec:[`docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md`](../../superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md)
- 主君 audit 报告(无独立文档,在 conversation 历史里)
- 既有 gate 模板规则:[`ad_signage_non_medical_institution_disease_advertisement`](../../app/src/main/assets/rules/ad_signage_rules.json)(line ~880)
- 测试基准:[`AdSignageRuleMatcherTest.kt`](../../app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt)

---

## Task 1: `signage_national_political_symbol_misuse` 加 `categoryAnchorsAbsent` 阻断政府/新闻出版/公益

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`(新增 2 组 `@Test`)
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(给 `ad_signage_signage_national_political_symbol_misuse` 加 `categoryAnchorsAbsent` 字段)

- [ ] **Step 1.1: 写 #113 fixture 不命中测试(期望当前版本 FAIL)**

打开 [`AdSignageRuleMatcherTest.kt`](../../app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt),在文件末尾新增以下 `@Test` 函数(从既有 `matcher` 变量拿,先看文件最前面的 `lateinit var matcher` 或 `private val matcher = ...` 形式复用):

```kotlin
@Test fun `113 政府公益图书惠民展 不应命中 national_political_symbol_misuse`() {
    // Fixture #113 真实文本:OCR 应检出「庆国庆」「国庆图书惠民」
    // 改造前:命中(规则无 gate)
    // 改造后:不命中(categoryAnchorsAbsent 含「政府/新闻出版/公益/惠民/图书惠民」等)
    val text = "哈尔滨新闻出版局 国庆图书惠民公益展 庆国庆"
    val hits = matcher.scan(text)
    assertTrue(
        "#113 政府公益展不应被识别为国家标志商业滥用",
        hits.none { it.ruleId == "ad_signage_signage_national_political_symbol_misuse" }
    )
}

@Test fun `127 人民咖啡馆国庆立牌 应命中 national_political_symbol_misuse gate 不阻断商业`() {
    // Fixture #127 真实文本:商业品牌「人民咖啡馆」借天安门元素
    // 改造前后都应命中(「人民咖啡馆」不在 absent 列表中)
    val text = "人民咖啡馆 天安门 国庆立牌 庆国庆"
    val hits = matcher.scan(text)
    assertTrue(
        "#127 商业品牌借国家标志应继续命中",
        hits.any { it.ruleId == "ad_signage_signage_national_political_symbol_misuse" }
    )
}
```

- [ ] **Step 1.2: 跑测试验证 #113 当前确实误命中(期望 FAIL)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.113 政府公益图书惠民展 不应命中 national_political_symbol_misuse"
```

**Expected**:`FAILED` — `#113 政府公益展不应被识别为国家标志商业滥用`。既有问题:规则无 gate,「庆国庆」即命中。

- [ ] **Step 1.3: 在 `ad_signage_rules.json` 给该规则加 `categoryAnchorsAbsent`**

打开 [`ad_signage_rules.json`](../../app/src/main/assets/rules/ad_signage_rules.json),定位到 `"id": "ad_signage_signage_national_political_symbol_misuse"` 块。在 `"keywords"` 数组之后,`"severity"` 字段之前插入(注意 JSON 末尾逗号):

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
      ],
```

(共 28 个 absent anchor,JSON 字段在 `"keywords"` 数组闭合的 `]` 后加 `,`,在 `"severity"` 前)

> **验证**:`.claude/hooks/validate-rule-json.js`(PostToolUse hook)会在 Edit 落地后自动跑,校验 JSON 语法 / version 整数 / rules 数组 / id 唯一 4 项。命中规则会 exit 2。

- [ ] **Step 1.4: 同步 bump `version` 字段**

在 `ad_signage_rules.json` 文件最顶部找到 `"version": 20`,改为 `"version": 21`(v20 → v21,标识本批改造)。

- [ ] **Step 1.5: 跑全部 AdSignageRuleMatcherTest case 验证 #113 不命中 + #127 仍命中 + 既有 case 全过**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`,全部 `AdSignageRuleMatcherTest` 通过(包括本批新增 2 组 + 既有 ~30 组 case)。

- [ ] **Step 1.6: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/assets/rules/ad_signage_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git -c user.name="AlexMultiAgent" -c user.email="AlexMultiAgent@users.noreply.github.com" commit -m "$(cat <<'EOF'
fix(rules): national_political_symbol_misuse 加 categoryAnchorsAbsent 阻断政府公益误命中

v20 → v21

28 个 absent anchor 阻断合法公共活动场景(政府/新闻出版/公益/教育/文艺
5 类):fixture #113 哈尔滨新闻出版局国庆图书惠民公益展 不再误判为 Violation。

正向命中不阻断:
- fixture #127 人民咖啡馆国庆立牌(商业品牌借天安门)— 「人民咖啡馆」不在 absent 列表
- fixture #06 八一建军节(哈尔滨胜利加油站借军政)— 「哈尔滨胜利加油站」不在 absent 列表

测试:
- 113 政府公益图书惠民展 不应命中 national_political_symbol_misuse
- 127 人民咖啡馆国庆立牌 应命中(商业品牌,gate 不阻断)

关联:docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md §2.1.1
EOF
)"
```

---

## Task 2: `signage_alcohol_drink_scenario` 加 `categoryAnchorsAbsent` 阻断零售/门头/烟酒行

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`(新增 2 组 `@Test`)
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(给 `ad_signage_signage_alcohol_drink_scenario` 加 `categoryAnchorsAbsent` 字段)

- [ ] **Step 2.1: 写 #86 fixture 不命中测试**

在 `AdSignageRuleMatcherTest.kt` 末尾继续加(承接 Task 1):

```kotlin
@Test fun `86 烟酒零售门头 不应命中 alcohol_drink_scenario`() {
    // Fixture #86 真实文本:OCR 应检出「酒类」「白酒」,但属零售类目标识
    // 改造前:命中(规则无 gate)
    // 改造后:不命中(categoryAnchorsAbsent 含「零售/门头/招牌/烟酒行」等)
    val text = "兰泽烟酒零售门头 白酒 酒类销售 招牌"
    val hits = matcher.scan(text)
    assertTrue(
        "#86 烟酒零售门头不应被识别为酒类广告违规",
        hits.none { it.ruleId == "ad_signage_signage_alcohol_drink_scenario" }
    )
}

@Test fun `18 白酒电商页闻香 应命中 alcohol_drink_scenario gate 不阻断饮用场景`() {
    // Fixture #18 真实文本:电商页文案「闻香 天然桦树清香 与酒香交织 入口」
    // 改造前后都应命中(「电商页」「闻香」「入口」不在 absent 列表)
    val text = "白酒 闻香 天然桦树清香 与酒香交织 入口 层次丰富 清爽顺滑 淘宝"
    val hits = matcher.scan(text)
    assertTrue(
        "#18 白酒电商页应继续命中",
        hits.any { it.ruleId == "ad_signage_signage_alcohol_drink_scenario" }
    )
}
```

- [ ] **Step 2.2: 跑测试验证 #86 当前确实误命中(期望 FAIL)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.86 烟酒零售门头 不应命中 alcohol_drink_scenario"
```

**Expected**:`FAILED` — `#86 烟酒零售门头不应被识别为酒类广告违规`。既有问题:规则无 gate,「白酒」「酒类」即命中。

- [ ] **Step 2.3: 在 `ad_signage_rules.json` 给该规则加 `categoryAnchorsAbsent`**

定位到 `"id": "ad_signage_signage_alcohol_drink_scenario"` 块。在 `"keywords"` 数组之后,`"severity"` 字段之前插入:

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
      ],
```

(共 25 个 absent anchor)

- [ ] **Step 2.4: 跑测试验证通过(注意 version 已在 Task 1 改 21,本任务不再 bump)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`。

- [ ] **Step 2.5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/assets/rules/ad_signage_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git -c user.name="AlexMultiAgent" -c user.email="AlexMultiAgent@users.noreply.github.com" commit -m "$(cat <<'EOF'
fix(rules): alcohol_drink_scenario 加 categoryAnchorsAbsent 阻断零售门头误命中

25 个 absent anchor 阻断合法商业标识(零售/批发/门头/招牌/烟酒行/连锁店
25 个商业类目 marker):fixture #86 兰泽烟酒零售门头 不再被误判为酒类广告违规。

正向命中不阻断:
- fixture #18 白酒电商页(闻香/清爽顺滑/入口)— 「淘宝/电商」不在 absent 列表
- fixture #90 花园酒中华老字号(布鲁塞尔蒙特金奖)— 「穿越千年/中华老字号」不在 absent 列表

测试:
- 86 烟酒零售门头 不应命中 alcohol_drink_scenario
- 18 白酒电商页闻香 应命中(饮用场景,gate 不阻断)

关联:docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md §2.1.2
EOF
)"
```

---

## Task 3: `signage_food_disease_target` 扩 keyword 覆盖 fixture #55 前列腺养护

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`(新增 1 组 `@Test`)
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(给 `ad_signage_signage_food_disease_target` keyword 数组追加 7 个)

- [ ] **Step 3.1: 写 #55 fixture 命中测试**

在 `AdSignageRuleMatcherTest.kt` 末尾继续加:

```kotlin
@Test fun `55 前列腺养护 命中 food_disease_target`() {
    // Fixture #55 京东京造番茄红素沙棘果油
    // 改造前:miss(规则 keyword 仅有「前列腺患者」,不含「前列腺养护」「护前列腺炎」)
    // 改造后:命中(新增 keyword 7 个)
    val text = "京东京造番茄红素沙棘果油 前列腺养护 护前列腺炎 尿频尿急 男性生活伴侣 增强免疫力"
    val hits = matcher.scan(text)
    assertTrue(
        "#55 前列腺养护应命中 food_disease_target",
        hits.any { it.ruleId == "ad_signage_signage_food_disease_target" }
    )
}
```

- [ ] **Step 3.2: 跑测试验证当前 miss(期望 FAIL)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.55 前列腺养护 命中 food_disease_target"
```

**Expected**:`FAILED` — `#55 前列腺养护应命中 food_disease_target`。既有问题:keyword 仅有「前列腺患者」,fixture #55 文本用「前列腺养护」「护前列腺炎」「尿频尿急」「男性生活伴侣」,全部 miss。

- [ ] **Step 3.3: 在 `ad_signage_rules.json` 追加 keyword**

定位到 `"id": "ad_signage_signage_food_disease_target"` 块的 `"keywords"` 数组,在 `"抗癌防癌"` 之后追加(逗号分隔):

```json
        "前列腺养护",
        "护前列腺炎",
        "尿频尿急",
        "男性生活伴侣",
        "前列腺健康",
        "前列腺保健",
        "泌尿健康"
```

(共追加 7 个,v20 当前 20 → 27 个)

- [ ] **Step 3.4: 跑测试验证通过**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`。

- [ ] **Step 3.5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/assets/rules/ad_signage_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git -c user.name="AlexMultiAgent" -c user.email="AlexMultiAgent@users.noreply.github.com" commit -m "$(cat <<'EOF'
feat(rules): food_disease_target +7 keyword 覆盖 fixture #55 前列腺养护

新增 keyword 7 个:
- 前列腺养护 / 护前列腺炎 / 尿频尿急 / 男性生活伴侣
- 前列腺健康 / 前列腺保健 / 泌尿健康

覆盖 fixture #55 京东京造番茄红素沙棘果油「前列腺养护 护前列腺炎
尿频尿急 男性生活伴侣」(普通食品疾病治疗暗示,v0.1.49 起 0 hit)。

不影响既有命中(#34/#35/#36/#44/#45/#47/#50/#66 keyword 集合不变)。

测试:
- 55 前列腺养护 命中 food_disease_target

关联:docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md §2.2.1
EOF
)"
```

---

## Task 4: `art27_seed_yield_guarantee` 扩 keyword 覆盖 fixture #13 豌豆饱满 + #23 黑旋风冬瓜

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`(新增 2 组 `@Test`)
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(给 `ad_signage_art27_seed_yield_guarantee` keyword 数组追加 10 个)

- [ ] **Step 4.1: 写 #13 / #23 命中测试**

在 `AdSignageRuleMatcherTest.kt` 末尾继续加:

```kotlin
@Test fun `13 豌豆多且饱满 命中 seed_yield_guarantee`() {
    // Fixture #13 v8 时 weak(关键词薄),v20 仍未扩
    // 改造前:miss(规则无「饱满」「多且饱满」「籽粒饱满」「油亮饱满」)
    // 改造后:命中
    val text = "豌豆种子 多且饱满 高产 4 大豆种 籽粒饱满 油亮饱满"
    val hits = matcher.scan(text)
    assertTrue(
        "#13 豌豆饱满应命中 seed_yield_guarantee",
        hits.any { it.ruleId == "ad_signage_art27_seed_yield_guarantee" }
    )
}

@Test fun `23 黑旋风冬瓜 命中 seed_yield_guarantee`() {
    // Fixture #23 v8 时 weak
    // 改造前:仅「高产」可触发;「瓜型好」「心小肉厚」「新改良」miss
    // 改造后:命中
    val text = "黑旋风冬瓜 瓜型好 心小肉厚 高产 新改良"
    val hits = matcher.scan(text)
    assertTrue(
        "#23 黑旋风冬瓜形态描述应命中 seed_yield_guarantee",
        hits.any { it.ruleId == "ad_signage_art27_seed_yield_guarantee" }
    )
}
```

- [ ] **Step 4.2: 跑测试验证 #13 当前 miss(期望 FAIL)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.13 豌豆多且饱满 命中 seed_yield_guarantee"
```

**Expected**:`FAILED`。

- [ ] **Step 4.3: 在 `ad_signage_rules.json` 追加 keyword**

定位到 `"id": "ad_signage_art27_seed_yield_guarantee"` 块的 `"keywords"` 数组,在 `"全国适宜"` 之后追加:

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

(共追加 10 个,v20 当前 25 → 35 个)

- [ ] **Step 4.4: 跑测试验证通过**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`。

- [ ] **Step 4.5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/assets/rules/ad_signage_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git -c user.name="AlexMultiAgent" -c user.email="AlexMultiAgent@users.noreply.github.com" commit -m "$(cat <<'EOF'
feat(rules): seed_yield_guarantee +10 keyword 覆盖 #13 豌豆饱满 + #23 冬瓜形态

新增 keyword 10 个:
- 饱满/多且饱满/籽粒饱满/油亮饱满/颗粒饱满/饱满度高
- 瓜型好/心小肉厚/新改良/改良

覆盖 fixture #13 豌豆多且饱满 + #23 黑旋风冬瓜(v8 时期 weak,v20 未补)。

不影响既有命中(#12/#22/#26 等「高产/抗病/早熟」类 fixture 继续命中)。

测试:
- 13 豌豆多且饱满 命中 seed_yield_guarantee
- 23 黑旋风冬瓜 命中 seed_yield_guarantee

关联:docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md §2.3.1
EOF
)"
```

---

## Task 5: `edu_art24_test_authority` 扩 keyword 覆盖 fixture #11 公安专项

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`(新增 1 组 `@Test`)
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(给 `ad_signage_edu_art24_test_authority` keyword 数组追加 11 个)

- [ ] **Step 5.1: 写 #11 命中测试**

在 `AdSignageRuleMatcherTest.kt` 末尾继续加:

```kotlin
@Test fun `11 公安专项 命中 test_authority`() {
    // Fixture #11 v8 时未覆盖,v20 实测 4 keyword 完全不覆盖公安类
    // 改造前:miss
    // 改造后:命中
    val text = "公安专项 秋考刷题班 高效提分 警员培训 公安系统"
    val hits = matcher.scan(text)
    assertTrue(
        "#11 公安专项应命中 test_authority",
        hits.any { it.ruleId == "ad_signage_edu_art24_test_authority" }
    )
}
```

- [ ] **Step 5.2: 跑测试验证当前 miss(期望 FAIL)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.11 公安专项 命中 test_authority"
```

**Expected**:`FAILED` — 当前 keyword 仅有「考试命题人/阅卷老师/考官亲自授课/教育部推荐」。

- [ ] **Step 5.3: 在 `ad_signage_rules.json` 追加 keyword**

定位到 `"id": "ad_signage_edu_art24_test_authority"` 块的 `"keywords"` 数组,在 `"教育部推荐"` 之后追加:

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

- [ ] **Step 5.4: 跑测试验证通过**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`。

- [ ] **Step 5.5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/assets/rules/ad_signage_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git -c user.name="AlexMultiAgent" -c user.email="AlexMultiAgent@users.noreply.github.com" commit -m "$(cat <<'EOF'
feat(rules): edu_art24_test_authority +11 keyword 覆盖 #11 公安专项

新增 keyword 11 个:
- 公安专项/公安类/公安岗/公安系统/公安联考/公安院校
- 警校/警员培训/警察考试/警考培训/政法干警

覆盖 fixture #11 公安专项秋考刷题班(v8 时期未覆盖,v20 实测 4 keyword
完全不含公安类)。

不影响既有命中(「考试命题人/阅卷老师/考官亲自授课/教育部推荐」继续
有效)。

测试:
- 11 公安专项 命中 test_authority

关联:docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md §2.3.2
EOF
)"
```

---

## Task 6: `re_art26_planned_facility` 扩 keyword 覆盖 fixture #15 银泰集茶巷

**Files:**
- Modify: `app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt`(新增 1 组 `@Test`)
- Modify: `app/src/main/assets/rules/ad_signage_rules.json`(给 `ad_signage_re_art26_planned_facility` keyword 数组追加 9 个)

> **注意**:`art26_re_prm` v20 实测 12 keyword 已含「财富启航/创富/品牌加冕/国茶文化/银泰商圈/主题商街/城芯现铺/坐拥群力」,**fixture #15 已被该规则覆盖**。本任务只补 `re_art26_planned_facility`。

- [ ] **Step 6.1: 写 #15 命中测试**

在 `AdSignageRuleMatcherTest.kt` 末尾继续加:

```kotlin
@Test fun `15 国潮茶文化 命中 re_art26_planned_facility`() {
    // Fixture #15 银泰集茶巷 OCR 召回「主题商街」「国潮茶文化」「茶巷」
    // 改造前:`re_art26_planned_facility` miss(8 keyword 不含这些);
    //         `art26_re_prm` 命中(已含「财富启航/主题商街/银泰商圈」)
    // 改造后:`re_art26_planned_facility` 也命中(双规则命中,total 命中数提升)
    val text = "银泰集茶巷 品牌加冕 财富实力启航 主题商街 国潮茶文化"
    val hits = matcher.scan(text)
    assertTrue(
        "#15 国潮茶文化应命中 re_art26_planned_facility",
        hits.any { it.ruleId == "ad_signage_re_art26_planned_facility" }
    )
}
```

- [ ] **Step 6.2: 跑测试验证当前 miss(期望 FAIL)**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest.15 国潮茶文化 命中 re_art26_planned_facility"
```

**Expected**:`FAILED` — 当前 8 keyword「地铁直达/学区确定/规划学校/规划医院/未来 X 号线/智慧健康/体检区/健康体检区」不命中「主题商街」「国潮茶文化」「茶巷」。

- [ ] **Step 6.3: 在 `ad_signage_rules.json` 追加 keyword**

定位到 `"id": "ad_signage_re_art26_planned_facility"` 块的 `"keywords"` 数组,在 `"健康体检区"` 之后追加:

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

(共追加 9 个,v20 当前 8 → 17 个)

- [ ] **Step 6.4: 跑测试验证通过**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`。

- [ ] **Step 6.5: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git add app/src/main/assets/rules/ad_signage_rules.json \
        app/src/test/java/com/icespiritai/offline/rules/AdSignageRuleMatcherTest.kt
git -c user.name="AlexMultiAgent" -c user.email="AlexMultiAgent@users.noreply.github.com" commit -m "$(cat <<'EOF'
feat(rules): re_art26_planned_facility +9 keyword 覆盖 #15 国潮茶文化

新增 keyword 9 个:
- 主题商街/国潮茶文化/茶巷/商街/商圈核心
- 国潮/国潮街区/茶文化/国潮主题

覆盖 fixture #15 银泰集茶巷 OCR 召回「主题商街」「国潮茶文化」「茶巷」。

art26_re_prm 不动(v20 实测 12 keyword 已含「财富启航/创富/品牌加冕/
国茶文化/银泰商圈/主题商街/城芯现铺/坐拥群力」,#15 已被该规则覆盖)。

测试:
- 15 国潮茶文化 命中 re_art26_planned_facility

关联:docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md §2.3.3
EOF
)"
```

---

## Task 7: 复制 fixture #68 / #100 从 audit71 到 违规案例/

**Files:**
- Add: `违规案例/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg`
- Add: `违规案例/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg`

- [ ] **Step 7.1: 复制前校验 md5 + 大小**

```bash
cd /d/GitHub/IceSpiritAI_Vision
ls -la "app/src/androidTest/assets/fixtures/audit71/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg"
ls -la "app/src/androidTest/assets/fixtures/audit71/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg"
md5sum "app/src/androidTest/assets/fixtures/audit71/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg" \
       "app/src/androidTest/assets/fixtures/audit71/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg"
```

**Expected**:两张 jpg 存在(已确认 audit71/ 列表),记录 md5 + size 用于复制后校验。

- [ ] **Step 7.2: 复制到 违规案例/**

```bash
cd /d/GitHub/IceSpiritAI_Vision
cp "app/src/androidTest/assets/fixtures/audit71/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg" \
   "违规案例/"
cp "app/src/androidTest/assets/fixtures/audit71/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg" \
   "违规案例/"
```

- [ ] **Step 7.3: 复制后校验 md5 + fixture 数**

```bash
cd /d/GitHub/IceSpiritAI_Vision
md5sum "违规案例/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg" \
       "违规案例/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg"
ls 违规案例/ | grep -E "\.(jpg|jpeg|png|JPG|JPEG|PNG)$" | wc -l
```

**Expected**:
- 复制后的 md5 与 Step 7.1 一致
- `违规案例/` 图片总数 **135 → 137**(本任务后)

- [ ] **Step 7.4: Commit**

```bash
cd /d/GitHub/IceSpiritAI_Vision
# 验证 gitignore 是否排除 违规案例/ (CLAUDE.md 说 .gitignore 排除)
git check-ignore -v "违规案例/68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg" \
                  "违规案例/100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg"
git status --short "违规案例/"
```

**Expected**:
- `git check-ignore` 输出 `<path> <gitignore-pattern>` 表明两张图都被 .gitignore 排除
- `git status --short` 不显示这两张图

> **关键发现**:根据 CLAUDE.md,`违规案例/` 在 `.gitignore` 内,本步复制后**不会进 git**。这是正常的(`违规案例/` 是本地 staging,不入仓)。

- [ ] **Step 7.5: 在本地 备份审计

输出 复制后两地的步骤说明(本任务不留 git commit 痕迹,只本地落盘)。本步骤作为「checklist 完成确认」,非新 commit。

```bash
echo "违规案例/ fixture 同步完成:135 → 137 张图(新增 #68 + #100)"
echo "本步骤不入 git(违规案例/ 在 .gitignore 内,本地 staging 用)"
```

---

## Task 8: 全量回归验证 — 所有 8 个新增 case + 既有 case + 5 个回归测试文件

**Files:**
- 不修改文件,只跑测试

- [ ] **Step 8.1: 跑 `AdSignageRuleMatcherTest` 全量**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleMatcherTest"
```

**Expected**:`BUILD SUCCESSFUL`,全部 case 通过(含本批 8 组新增)。

- [ ] **Step 8.2: 跑其他 4 个相关回归测试**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageRuleLoaderTest" \
        --tests "com.icespiritai.offline.rules.AdSignageTextFixtureRegressionTest" \
        --tests "com.icespiritai.offline.rules.AdSignageImageAuditSixtySixRegressionTest" \
        --tests "com.icespiritai.offline.rules.AdSignageMentorFiveImageRegressionTest"
```

**Expected**:`BUILD SUCCESSFUL`,既有 4 个回归测试文件全部通过(新增 keyword / gate 不得破坏既有命中)。

- [ ] **Step 8.3: 跑全量 `testDebugUnitTest`**

```bash
cd /d/GitHub/IceSpiritAI_Vision
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest
```

**Expected**:`BUILD SUCCESSFUL`,全部 JVM 单元测试通过(注意:不要加 `-PmodelProfile=ice_ocr_rules`,CLAUDE.md 明确说单测走默认 profile)。

- [ ] **Step 8.4: 检查 git log 确认 6 个 commit 落地 + 无 `Co-Authored-By` trailer**

```bash
cd /d/GitHub/IceSpiritAI_Vision
git log -6 --format='%B' | grep -i "Co-Authored-By" || echo "OK: no Co-Authored-By trailer"
git log -6 --oneline
```

**Expected**:
- 无 `Co-Authored-By` trailer 输出(由 hook 拦截保证)
- 6 个新 commit 落地(每个 Task 1 commit 1 个)

- [ ] **Step 8.5: 最终 commit 报告**

无新 commit。总结 6 个 commit 内容:

| Task | Commit 主题 | 修改文件 |
|---|---|---|
| 1 | fix(rules): national_political_symbol_misuse 加 categoryAnchorsAbsent | rules.json + matcher test |
| 2 | fix(rules): alcohol_drink_scenario 加 categoryAnchorsAbsent | rules.json + matcher test |
| 3 | feat(rules): food_disease_target +7 keyword 覆盖 #55 | rules.json + matcher test |
| 4 | feat(rules): seed_yield_guarantee +10 keyword 覆盖 #13 #23 | rules.json + matcher test |
| 5 | feat(rules): edu_art24_test_authority +11 keyword 覆盖 #11 | rules.json + matcher test |
| 6 | feat(rules): re_art26_planned_facility +9 keyword 覆盖 #15 | rules.json + matcher test |

(无 Task 7 commit — `违规案例/` 在 .gitignore)

---

## Self-Review

**Spec coverage 检查**:

| Spec § | Task |
|---|---|
| §2.1.1 national_political_symbol_misuse gate | Task 1 ✓ |
| §2.1.2 alcohol_drink_scenario gate | Task 2 ✓ |
| §2.2.1 food_disease_target +7 keyword | Task 3 ✓ |
| §2.3.1 seed_yield_guarantee +10 keyword | Task 4 ✓ |
| §2.3.2 edu_art24_test_authority +11 keyword | Task 5 ✓ |
| §2.3.3 re_art26_planned_facility +9 keyword | Task 6 ✓ |
| §3 fixture sync | Task 7 ✓ |
| §4 测试策略(8 组 case) | Task 1-6 各 1-2 组 ✓ |
| §5 不在范围 | 未补 ✓ (留作下个发版号/用户动作项)|
| §6 风险对策 | 已融入 Task 1-8 各步骤(既有回归测试 + TDD)|
| §7 实施步骤 | 整体覆盖 ✓ |

**Placeholder scan**:无 TBD / TODO / 「实现待定」/ 「类似 Task N」。所有代码块完整。

**Type consistency**:
- 规则 ID 全程一致:`ad_signage_signage_national_political_symbol_misuse` / `ad_signage_signage_alcohol_drink_scenario` / `ad_signage_signage_food_disease_target` / `ad_signage_art27_seed_yield_guarantee` / `ad_signage_edu_art24_test_authority` / `ad_signage_re_art26_planned_facility`
- 测试函数名规范:Backticks 中文名 — 与既有 `AdSignageRuleMatcherTest` 内 case 命名一致
- `matcher` 变量复用既有 — 假设文件前 8 行有 `private val matcher = ...` 或 `lateinit var matcher`

**潜在 gap**:
- Task 1-6 Step 5 都 `grep "Co-Authored-By"` 但只在 Task 8 Step 8.4 全量检查 — OK 因为 PostToolUse hook 会自动拦截
- Task 7 Step 7.4 `git check-ignore` 验证 `.gitignore` — 防御性,如果 ignore pattern 改了会立即发现

**计划就绪**。