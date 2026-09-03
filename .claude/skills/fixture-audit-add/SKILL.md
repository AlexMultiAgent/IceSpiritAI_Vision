---
name: fixture-audit-add
description: Adds N violation case fixtures with OCR-based filename verification + 真机 e2e 校验 + coverage_matrix 文档化 — bundled from CLAUDE.md §"违规案例 fixture 工作流". Use when adding 违规案例 / audit{N} batch (audit66 / audit71 / 未来 audit{N}). User-only — invoke via /fixture-audit-add because it modifies files + runs 真机 e2e.
disable-model-invocation: true
---

# Fixture Audit Add (IceSpiritAI_Vision)

Adds N violation case fixtures using the 5-stage workflow codified in
CLAUDE.md §"违规案例 fixture 工作流 + audit71 真机 e2e (v0.1.49 落地)".
Bundles:

- OCR-based filename verification (catches "filename 与图像牛头不对马嘴")
- Synchronized rename in `违规案例/` + `app/src/androidTest/assets/fixtures/audit{N}/`
- 真机 e2e (`connectedDebugAndroidTest` + `AdSignageAudit{N}ImageE2ETest`)
- `coverage_matrix.md` generation from logcat `[OCR_HIT]/[OCR_NO_HIT]` regex
- Empirical `ANY_HIT ≥ 60/N` threshold + 同发版号内 v(N+1) 二阶段扩展 strategy

## When to invoke

- User adds N new violation case images to `违规案例/`
- User asks "扩 N 张 fixture" / "audit{N} 起来了" / "fixture audit"
- After any batch add where filenames need OCR-verified before commit

## Hard constraints

- N 张 fixture 必须**全部**走过阶段 2(OCR 比对),否则可能再次出现"新增图命名牛头不对马嘴"
- 真机仅 Huawei nova 6 (AGQV023313008161, SDK 35)— AGP 9 + Kotlin 2.4.10 上稳定
- 阈值 `ANY_HIT ≥ 60/N` 是经验值;不达标时按阶段 6 二阶段扩展(v0.1.49 经验:v11 59/71 → v12 65/71)
- 不删 fixture(失败时保留,作为 OCR 召回上限的 evidence)

## Workflow (5 stages + optional stage 6)

### Stage 1 — Staging

Images land in `违规案例/<NN>_<品牌>_<违规情形>_<类别>.jpg`.

Filename convention:

| NN 范围 | 前缀格式 | 例子 |
|---|---|---|
| < 100 | 02d | `67_品牌_情形_类别.jpg` |
| ≥ 100 | 03d | `100_品牌_情形_类别.jpg` |

4 个 `_`-separated fields, no trailing `_`. 类别 from CategoryDisplay 中 keys
(`医疗病种` / `暗示安全性` / `绝对化用语` 等)。

### Stage 2 — OCR-based filename audit

For each fixture, run 真机 OCR + AdSignageRuleMatcher and compare OCR text
to filename. The audit surface:

| Mismatch 类型 | 处理 |
|---|---|
| 品牌名错 (e.g., 文件名"兰泽"但 OCR 是"名泽") | 按 OCR 重命名 |
| 违规情形错 (e.g., "蟹都汇" vs "蟹凰宫") | 按 OCR 重命名 |
| 完全不匹配 (e.g., 88 实为 89 内容) | 整张 swap 序号 |
| OCR 召回空 (<10 字) | 仍命名,标注到 coverage_matrix.md 的 miss 段 |

详细 audit 脚本可参考 `tools/ocr-audit66-fixtures.py`(旧版 audit66 用)。

### Stage 3 — Rename sync (BOTH directories)

After identifying mismatches, rename in BOTH:

- `违规案例/<old>.jpg` → `违规案例/<new>.jpg`(plain `mv`,gitignored)
- `app/src/androidTest/assets/fixtures/audit{N}/<old>.jpg` → `<new>.jpg`(`git mv`,tracked)

Verify with:

```bash
ls app/src/androidTest/assets/fixtures/audit{N}/ | wc -l   # N+1 (含 coverage_matrix.md)
```

### Stage 4 — 真机 e2e

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"

# (recommended) clear PackageManager ghost state
adb shell pm clear com.icespiritai.vision || true

# Background logcat capture (ring buffer is ~minutes; capture BEFORE test)
adb logcat -c
adb logcat -v time Audit{N}E2E:I '*:S' > /tmp/audit{N}_e2e_$(date +%Y%m%d_%H%M%S).log &
echo $! > /tmp/logcat.pid

# Single-class invocation (connectedDebugAndroidTest doesn't accept --tests)
./gradlew.bat connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAudit{N}ImageE2ETest

# Wait for gradle to finish, then kill logcat
wait
kill $(cat /tmp/logcat.pid) || true
```

harness anchors:

- logcat TAG: `Audit71E2E` (audit71) / `Audit{N}E2E` (future)
- line markers: `[COLD] / [WARM] / [HITS] / [OCR_HIT] / [OCR_NO_HIT] / RESULT_JSON`
- 行 schema in `app/src/androidTest/java/com/icespiritai/offline/rules/AdSignageAudit71ImageE2ETest.kt`

### Stage 5 — coverage_matrix.md generation

Parse `/tmp/audit{N}_e2e_*.log` with regex:

```python
WARM = r'\[WARM\] (\S+) bytes=\d+ ms=\d+ lines=\d+ avg_conf=[\d.]+ text_chars=\d+ hits=(\d+)'
HIT  = r'\[OCR_HIT\] (\S+) hits=([\w,\-]+)'
```

For each fixture file, write a §2 table row:

```
| <filename> | <bucket> | <severity> | <hits_count> | <rule_id_list> | <status> |
```

`status = "已覆盖"` if `hits_count > 0` else `"未覆盖"`.

miss 段单独 §3 listing,注释"OCR 召回限制(<10 字 / 严重错位 / 视觉符号)"。

### Stage 6 — 二阶段扩展(可选,ANY_HIT < 60/N)

If `ANY_HIT < 60/N` after Stage 5, do a v(N+1) expansion **in the same 发版号**:

1. Read `/tmp/audit{N}_e2e_*.log` for which rules/keywords were missing
2. Use `add-rule-entry` skill 加新规则(1-2 per missing cluster)
3. 扩既有规则关键词(AC substring 命中兜底 OCR 漏字 — v0.1.49 经验:
   加 `中国第一` / `中国第一品牌` 命中 OCR 漏「品牌」二字的 `中国第品牌`)
4. Re-run Stage 4 e2e
5. Update coverage_matrix.md with v(N+1) results

Historical anchor: v0.1.49 = v10 → v11 (+15 rules, 59/71) → v12 (+2 rules + 3 keywords, 65/71)。

## Required reads

- CLAUDE.md §"违规案例 fixture 工作流 + audit71 真机 e2e (v0.1.49 落地)"
- CLAUDE.md §"Instrumented test / 真机 A/B (androidTest)" (5 gotchas)
- CLAUDE.md §"Unit test 踩坑(2026-08-21 v0.1.14)"
- `app/src/androidTest/assets/fixtures/audit71/coverage_matrix.md` (template)
- `app/src/androidTest/java/com/icespiritai/offline/rules/AdSignageAudit71ImageE2ETest.kt` (harness)
- `tools/ocr-audit66-fixtures.py` (旧 OCR audit 脚本,模式参考)

## Required writes

- `违规案例/<renamed>.jpg` (N files, gitignored)
- `app/src/androidTest/assets/fixtures/audit{N}/<renamed>.jpg` (N files, committed)
- `app/src/androidTest/assets/fixtures/audit{N}/coverage_matrix.md`

## Output

```markdown
# Fixture Audit Add Report

## Input
- 暂存目录: `违规案例/`
- 目标 audit dir: `app/src/androidTest/assets/fixtures/audit{N}/`
- N: <count>

## Stage 2: filename audit
- 总图: N
- filename ↔ OCR mismatch: M (重命名 R 张,swap S 对,完全无匹配 0)
- miss(OCR 召回限制): L 张

## Stage 4: 真机 e2e
- device: Huawei nova 6 (AGQV023313008161, SDK 35)
- harness: `...AdSignageAudit{N}ImageE2ETest`
- ANY_HIT: K / N (≥ 60/N ✅ / ❌ 需 Stage 6)
- miss: <list>

## Stage 5: coverage_matrix.md
- written: `app/src/androidTest/assets/fixtures/audit{N}/coverage_matrix.md`

## Stage 6 (if 触发)
- v(N) → v(N+1): +X new rules / +Y keywords on existing
- 二次 e2e: ANY_HIT = K2 / N (≥ 60/N ✅)

## Returns
- Renamed files: <list>
- coverage_matrix path
- e2e log path (local /tmp/audit{N}_e2e_*.log)
```

## Hard rules (CLAUDE.md + memory)

- 所有 commit 作者必须是 `AlexMultiAgent`(仓库 git config 已锁)
- 绝不要加 `Co-Authored-By: Claude ...` trailer(含 `Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>` 隐性形式)
- 敏感文件 `gradle.token.properties` / `local.properties` / `~/.gradle/gradle.properties` 不入索引
- 用显式 file paths in `git add`(PreToolUse hook 拦截 `-A` / `.` / `*`)
- 真机 e2e 之前必须 export `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"`
- 不动 `app/libs/*.aar`(PreToolUse hook 拦截 destructive ops)
- Gitea push / curl 走 `git -c http.proxy= -c https.proxy= <cmd>`(memory `reference-gitea-proxy-bypass.md`)
- GitHub push 走 SSH 形式(memory `reference-github-ssh-workaround.md`)