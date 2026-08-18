---
name: add-rule-entry
description: Expands a stub regulation (or new article) into 知识库/<域>/<reg>.md + corresponding rule JSON entries + matcher unit tests + changelog entry. Use when user wants to add / 扩 / 补 / 加上 any regulation or article to the OCR-based rule engine. Claude-only — invoked automatically when rule-engine expansion is the goal.
user-invocable: false
---

# Add Rule Entry Workflow

Use this skill whenever a new stub regulation, sub-regulation, or article needs
to be reflected in the OCR-based rule engine. This is the high-frequency
"扩" workflow that keeps the rule library synchronized with the knowledge base.

## Goal

Each new rule entry must produce:
1. **Markdown backfill** in `知识库/<域>/<reg>.md` with full provenance
   (发文字号 / 发文机关 / 通过 / 施行 / 有效性 / 官方 URL / 原文段 /
   适用判别要点).
2. **JSON rule entry** in `app/src/main/assets/rules/<domain>_rules.json`
   (or v<N+1>), incremental — never overwriting existing rules.
3. **Matcher unit test** in `app/src/test/.../rules/<Domain>RuleMatcherTest.kt`.
4. **`CategoryDisplay.kt`** mapping if introducing a new category key.
5. **`AssetRuleLoaderTest.kt`** version + threshold bumped.
6. **`user-changelog.md`** v<N+1> entry.
7. **`./gradlew.bat testDebugUnitTest -PmodelProfile=shell`** BUILD SUCCESSFUL.

Do NOT commit — return a summary; the user (or `project-commit` skill) handles
the commit.

## Domain routing

| User says... | Domain | Target JSON | Category enum |
|---|---|---|---|
| 广告 / 招牌 / 户外 / 医疗 / 药品 / 房地产 / 烟草 / 酒 / 金融 / 教育 / 互联网 / 化妆品 / 农药 / 兽药 | `ad` | `ad_signage_rules.json` | `AdSignageCategory` |
| 食品 / 预包装 / 营养 / 配料 / 过敏原 / 添加剂 / 保健食品 / 特殊膳食用食品 / GB 7718 / GB 28050 / GB 13432 / 食品安全 | `food` | `food_label_rules.json` | `FoodLabelCategory` |

If ambiguous, ask the user.

## Required reads before writing

1. `CLAUDE.md` — conventions (namespace / gradle / commit hygiene / 双主题)
2. `知识库/<域>/README.md` — domain taxonomy + existing markdown list
3. `app/src/main/java/com/icespiritai/offline/domain/CategoryDisplay.kt` — existing category keys
4. `app/src/main/assets/rules/<domain>_rules.json` — current version + rule shape
5. `app/src/test/.../rules/<Domain>RuleMatcherTest.kt` — existing test pattern
6. `app/src/test/.../rules/AssetRuleLoaderTest.kt` — assertion pattern
7. `app/src/main/java/com/icespiritai/offline/rules/<Domain>RuleMatcher.kt` — confirm normalization rules (TextNormalizer)

## Required writing order

### Step 1 — Markdown backfill

Write `知识库/<域>/<reg>.md` first. Schema:

```markdown
# <法规名>

- 发文字号: <例:国务院令第727号>
- 发文机关: <例:国务院 / 国家市场监管总局>
- 通过 / 公布日期: YYYY-MM-DD
- 施行日期: YYYY-MM-DD
- 当前有效性: 有效 / 部分有效 / 已废止
- 官方 URL: <flk.npc.gov.cn / samr.gov.cn / openstd.samr.gov.cn / gov.cn 优先>
- 替代 / 被替代关系: <可选,例:替代国家工商总局令第21号(已废止)>

> **检索状态**: 2026-MM-DD 通过 WebSearch + WebFetch/curl 多源交叉核对

## 原文

<条款逐条列出,缺文标 "[未检索到全文, 待补]">

## 适用判别要点

- <关键词触发点,与规则 keywords 一一对应>
- ...

## 关联法规

- <相关法规 markdown 链接>
```

If the user's regulation already has a markdown in `知识库/<域>/`, extend it
(append the new §N) — do not overwrite.

If the regulation is not yet indexed in `知识库/<域>/README.md`, append a
link entry at the bottom of README under `## 清单`.

### Step 2 — JSON rule entries

Append to the `rules` array in `app/src/main/assets/rules/<domain>_rules.json`.
Each entry:

```json
{
  "id": "<domain-prefix>_<regulation-abbr>_<article>_<short>",
  "category": "<existing-or-new-key>",
  "regulation": "《<法规名>》第N条第X项",
  "lawText": "第N条 ...<verbatim quote, ≥30 字>...",
  "keywords": ["k1", "k2", "k3"],
  "severity": "Violation" | "Warning" | "Info"
}
```

**id naming**: `<domain-prefix>_<regulation-abbr>_<article-or-section>_<semantic-short>`.
Examples:
- `cosmetic_art23_medical_claim`
- `food_gb28050_art5_2_low_sugar`

**id uniqueness**: every id must be unique across the JSON — duplicate ids
fail `AssetRuleLoaderTest`. Verify via:

```bash
node -e "const j=require('./app/src/main/assets/rules/<domain>_rules.json'); console.log('ids unique:', new Set(j.rules.map(r=>r.id)).size===j.rules.length, '|', j.rules.length)"
```

**version**: increment `version` by 1 when adding new rules.
`AssetRuleLoaderTest` asserts version.

### Step 3 — CategoryDisplay.kt extension (only if needed)

If a new category key is introduced (one not in `AdSignageCategory` or
`FoodLabelCategory` `when` arms), add:

```kotlin
"new_key" -> "中文显示"
```

to the appropriate enum. Without this, the rule's category displays as the
raw key string (fall-through).

### Step 4 — Matcher unit test

Append `@Test fun scan_<ruleId>_firesOn<typical-keyword>()` to
`app/src/test/.../rules/<Domain>RuleMatcherTest.kt`. Pattern:

```kotlin
@Test
fun scan_artNNX_firesOn() {
    val r = <Domain>Rule(
        "domain_art_NNX",          // exact ruleId from JSON
        "<category>",              // exact category from JSON
        "<regulation>",            // exact regulation string from JSON
        listOf("k1", "k2"),        // EXACT keywords list from JSON
        Severity.<exact severity>,
    )
    val hits = <Domain>RuleMatcher(listOf(r)).scan("<text containing all keywords as literal substrings>")
    assertEquals(<N>, hits.size)   // N = distinct-keywords-after-normalization
}
```

**Aho-Corasick substring semantics**: keywords match as literal substrings
after `TextNormalizer` strips whitespace + full-width variants. A keyword
list like `["A B", "AB"]` becomes `["AB", "AB"]` after normalization — the
AC trie dedupes, so the matcher returns 1 hit per **distinct normalized
keyword**, not per list entry. Always assert with the **distinct** count.

Use real or plausible OCR text (full-width / punctuation / mixed whitespace)
to verify normalization works.

### Step 5 — AssetRuleLoaderTest threshold

Edit `app/src/test/.../rules/AssetRuleLoaderTest.kt`:

```kotlin
val set = json.decodeFromString(<Domain>RuleSet.serializer(), src)
assertEquals(<N+1>, set.version)
assertTrue("...", set.rules.size >= <previous threshold + new count>)
```

### Step 6 — Version bump

Edit `app/build.gradle.kts`:

```kotlin
versionCode = <previous + 1>
versionName = "<X.Y.Z+1>"
```

Only bump when the JSON version changes OR when functional behavior changes.
Per release-hygiene memory: 措辞/文档/asset 微调不单独 bump 发版 —
co-bump with the JSON version.

### Step 7 — user-changelog.md v<N+1> entry

Prepend to `app/src/main/assets/user-changelog.md`:

```markdown
## v<X.Y.Z+1> · YYYY-MM-DD

- 「<domain>」域规则库 v<N> → v<N+1>:再扩 <count> 条,总 <cumulative> 条(<existing v<N> + new v<N+1>)。
- 覆盖 N 部法规:① <name>(<发文字号>,<施行日期>)... <each rule cluster with article references>
- 新增 N 个 <Domain>Category 中文 label:<key1> → <chinese1>, <key2> → <chinese2>。
- 新增 N 条单元测试覆盖每条新规则的关键词命中 + 1 条多规则联触发用例。
- 知识库增 N 份新 markdown + README 索引同步:<domain> 目录下现有 <total> 份法规。
- 严重度分布(总 <cumulative> 条):<N Violation> + <N Warning> + <N Info>。
```

### Step 8 — Build verification

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest -PmodelProfile=shell
```

Must end with `BUILD SUCCESSFUL`. If a test fails:

- `expected N but was M` where `M < N`: AC dedup after normalization —
  `assertEquals` should match distinct-keywords count, not list-size.
- `expected N but was M` where `M > N`: another rule in the matcher matched
  unexpectedly — check rule id uniqueness and overlapping keywords.
- JSON parse error: verify `keywords` is a list of strings, `severity` is
  one of `Violation`/`Warning`/`Info`.

### Step 9 — Do NOT commit

Return a summary of all files changed + the BUILD SUCCESSFUL confirmation.
Commit is the user's call (run `project-commit` skill or manually).

## Hard rules (CLAUDE.md + memory)

- All commits authored by `AlexMultiAgent` — never override `git config user.name`.
- Never add `Co-Authored-By: Claude ...` trailer.
- Sensitive files must not be staged: `gradle.token.properties`,
  `~/.gradle/gradle.properties`, `local.properties`.
- Use explicit file paths in `git add` (the PreToolUse hook blocks `-A` / `.`).

## References

- `知识库/<域>/README.md` — existing regulation index
- `app/src/main/java/com/icespiritai/offline/rules/<Domain>Rule.kt` — schema
- `app/src/main/java/com/icespiritai/offline/rules/<Domain>RuleSet.kt` — wrapper schema
- `app/src/main/java/com/icespiritai/offline/rules/TextNormalizer.kt` — normalization rules