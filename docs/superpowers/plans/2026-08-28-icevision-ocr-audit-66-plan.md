# 冰灵锐目 — 66 张违规案例真 OCR 端到端实测 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 paddleocr 3.7.0 + PP-OCRv6_small 跑 66 张违规案例图,把 OCR 输出存为 fixture;新增 JVM 测试类加载 fixture + 当前 129 条规则,产出全量 66 张命中报告(无硬断言);记录到 smoke 文档。

**Architecture:** Python 一次性录制 fixture(落 gitignored 目录)+ Kotlin 测试类消费 fixture 跑规则引擎,报告落 `build/reports/`。两阶段解耦,fixture 录制跑一次,测试可反复秒级跑。

**Tech Stack:** Python 3.12 + paddleocr 3.7.0(原生推理,非 ONNX)、Kotlin(JVM 单元测试)、JUnit 4、kotlinx.serialization、AdSignageRuleMatcher(现有)、TextNormalizer(现有)。

**Spec:** [`docs/superpowers/specs/2026-08-28-icevision-ocr-audit-66-design.md`](../specs/2026-08-28-icevision-ocr-audit-66-design.md)

---

## 文件结构

**新建**:
- `tools/ocr-audit66-fixtures.py` — Python 一次性录制脚本(66 张图 → 66 个 .txt + manifest.json)
- `app/src/test/java/com/icespiritai/offline/rules/AdSignageOcrImageAudit66Test.kt` — JVM 测试类,加载 fixture + 跑规则 + 落报告
- `docs/smoke/2026-08-28-ocr-audit66.md` — smoke 文档,含 runtime 差异说明 + 结果统计 + 结论

**修改**:
- `.gitignore` — 加 `app/src/test/resources/fixtures/audit66_ocr/`(录制产物不入仓)

**录制产物**(gitignored,不写文件创建步骤):
- `app/src/test/resources/fixtures/audit66_ocr/<NN>_<slug>.txt` × 66(每张图一份 OCR 文本)
- `app/src/test/resources/fixtures/audit66_ocr/manifest.json`(paddleocr 版本 + 模型 + 时间戳 + 每张图行/字符数)

**复用现有组件**(不改):
- `app/src/main/assets/rules/ad_signage_rules.json`(129 条 / v9)
- `app/src/test/java/.../rules/AdSignageImageAuditSixtySixRegressionTest.kt` 中的 `parseCoverageMatrix` 解析逻辑(本任务 copy-paste 复用,不抽公共 helper)
- `违规案例/_coverage_matrix.md` §2(ground truth 来源)
- `app/src/main/java/.../domain/TextNormalizer.kt`(forMatching)
- `app/src/main/java/.../rules/AdSignageRuleMatcher.kt`

**fixture 文件名构造规则**(Python 与 Kotlin 双方必须严格一致):
- 原文件:`01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg`
- NN 前缀:`01`(数字前缀)
- 中段 slug:`碧桂园华美天樾_中国地产三强_绝对化与数据引用`(`_` → `-`)
- fixture 文件名:`01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt`

---

## Task 1: 更新 .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: 读 .gitignore 当前内容**

Run:
```bash
cat .gitignore
```

确认当前无 `audit66_ocr` 相关行。

- [ ] **Step 2: 在 .gitignore 末尾追加新行**

追加内容(保留末尾换行):
```
app/src/test/resources/fixtures/audit66_ocr/
```

- [ ] **Step 3: 验证 .gitignore 末尾已含新行**

Run:
```bash
tail -3 .gitignore
```

Expected: 最后两行包含 `app/src/test/resources/fixtures/audit66_ocr/`。

- [ ] **Step 4: Commit**

```bash
git add .gitignore
git commit -m "chore(gitignore): ignore audit66_ocr OCR fixture dir"
```

---

## Task 2: 写 Python fixture 录制脚本

**Files:**
- Create: `tools/ocr-audit66-fixtures.py`

- [ ] **Step 1: 创建 tools/ocr-audit66-fixtures.py 完整内容**

完整代码(直接复制):

```python
#!/usr/bin/env python3
"""一次性录制 66 张违规案例图的 OCR 文本为 fixture。

输出:
  app/src/test/resources/fixtures/audit66_ocr/<NN>_<slug>.txt × 66
  app/src/test/resources/fixtures/audit66_ocr/manifest.json

用法:
  python tools/ocr-audit66-fixtures.py            # 跑全部
  python tools/ocr-audit66-fixtures.py --only 49  # 只跑 49 号
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

# 强制 UTF-8 stdout(Windows cp936 默认会炸中文)
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CASES_DIR = PROJECT_ROOT / "违规案例"
FIXTURES_DIR = PROJECT_ROOT / "app" / "src" / "test" / "resources" / "fixtures" / "audit66_ocr"
LOG_PATH = PROJECT_ROOT / "build" / "reports" / "audit66_ocr_fixtures.log"

IMAGE_EXTS = (".jpg", ".jpeg", ".png")


def build_fixture_filename(stem: str) -> str:
    """原文件名 stem(如 '01_碧桂园华美天樾_中国地产三强_绝对化与数据引用')
    → fixture 文件名 '01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt'"""
    # 取数字前缀
    i = 0
    while i < len(stem) and stem[i].isdigit():
        i += 1
    prefix = stem[:i]
    rest = stem[i:].lstrip("_")  # 去前缀后的下划线
    slug = rest.replace("_", "-")
    return f"{prefix}_{slug}.txt" if prefix else f"{slug}.txt"


def collect_image_files() -> list[Path]:
    files: list[Path] = []
    for p in sorted(CASES_DIR.iterdir()):
        if not p.is_file():
            continue
        if not p.name.lower().endswith(IMAGE_EXTS):
            continue
        if not p.name[0].isdigit():
            continue
        files.append(p)
    return files


def init_paddleocr():
    """paddleocr 3.7.0 API,失败时给清晰报错。"""
    try:
        from paddleocr import PaddleOCR  # type: ignore
    except ImportError:
        print("ERROR: paddleocr not installed. Run: pip install paddleocr==3.7.0", file=sys.stderr)
        sys.exit(2)
    print("[init] loading PP-OCRv6_small model (first run downloads to ~/.paddleocr/)...", file=sys.stderr)
    t0 = time.time()
    # 3.7.0 API: ocr_version 字段选 v6_small;若版本不支持则 fallback 到默认
    try:
        ocr = PaddleOCR(use_angle_cls=True, lang="ch", ocr_version="PP-OCRv6_small", show_log=False)
    except TypeError:
        # 老版本 paddleocr 不支持 ocr_version 字段,降级
        print("[init] WARNING: ocr_version param not supported, falling back to default model", file=sys.stderr)
        ocr = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
    print(f"[init] model loaded in {time.time()-t0:.1f}s", file=sys.stderr)
    return ocr


def ocr_one(ocr, image_path: Path) -> list[tuple[str, tuple]]:
    """paddleocr 返回 [(box, (text, conf)), ...];统一为 [(text, y_top), ...] 按 y 排序"""
    raw = ocr.ocr(str(image_path), cls=True)
    out: list[tuple[str, float]] = []
    if not raw:
        return out
    # 3.7.0 嵌套结构 raw = [page];page = [(box, (text, conf)), ...]
    for page in raw:
        for item in page or []:
            if not item or len(item) < 2:
                continue
            box, payload = item[0], item[1]
            if not box or not payload:
                continue
            # payload 兼容 (text, conf) 或直接 text
            if isinstance(payload, (list, tuple)) and len(payload) >= 1:
                text = str(payload[0])
            else:
                text = str(payload)
            # box = [[x1,y1],[x2,y2],[x3,y3],[x4,y4]],y_top = box[0][1]
            try:
                y_top = float(box[0][1])
            except (TypeError, ValueError, IndexError):
                y_top = 0.0
            out.append((text.strip(), y_top))
    out.sort(key=lambda x: x[1])
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=str, default=None,
                        help="只跑指定编号(如 49),调试用")
    args = parser.parse_args()

    if not CASES_DIR.exists():
        print(f"ERROR: {CASES_DIR} 不存在", file=sys.stderr)
        sys.exit(2)

    FIXTURES_DIR.mkdir(parents=True, exist_ok=True)
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOG_PATH.write_text("", encoding="utf-8")

    images = collect_image_files()
    if args.only:
        images = [p for p in images if p.name.startswith(f"{args.only}_")]
    print(f"[scan] found {len(images)} image(s) in {CASES_DIR}", file=sys.stderr)

    if not images:
        print("ERROR: no images matched", file=sys.stderr)
        sys.exit(2)

    ocr = init_paddleocr()

    manifest = {
        "paddleocr_version": _safe_version("paddleocr"),
        "model_name": "PP-OCRv6_small",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "fixture_dir": str(FIXTURES_DIR.relative_to(PROJECT_ROOT)),
        "files": {},
    }

    successes = 0
    failures = 0
    for img in images:
        stem = img.stem
        fixture_name = build_fixture_filename(stem)
        fixture_path = FIXTURES_DIR / fixture_name
        print(f"[ocr] {img.name} -> {fixture_name}", file=sys.stderr)
        try:
            t0 = time.time()
            lines = ocr_one(ocr, img)
            text = "\n".join(t for t, _ in lines)
            fixture_path.write_text(text, encoding="utf-8")
            manifest["files"][img.name] = {
                "fixture": fixture_name,
                "lines": len(lines),
                "chars": len(text),
                "ms": int((time.time() - t0) * 1000),
            }
            successes += 1
            print(f"  -> {len(lines)} lines, {len(text)} chars, {int((time.time()-t0)*1000)}ms", file=sys.stderr)
        except Exception as e:  # noqa: BLE001
            print(f"  ERROR: {type(e).__name__}: {e}", file=sys.stderr)
            manifest["files"][img.name] = {"fixture": fixture_name, "error": f"{type(e).__name__}: {e}"}
            failures += 1

    (FIXTURES_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\n[done] {successes} success, {failures} fail", file=sys.stderr)
    print(f"[done] fixtures: {FIXTURES_DIR}", file=sys.stderr)
    print(f"[done] manifest: {FIXTURES_DIR / 'manifest.json'}", file=sys.stderr)
    if failures > 0:
        sys.exit(1)


def _safe_version(pkg: str) -> str:
    try:
        from importlib.metadata import version
        return version(pkg)
    except Exception:
        return "unknown"


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 验证脚本语法**

Run:
```bash
python tools/ocr-audit66-fixtures.py --help
```

Expected: 打印 argparse help(`usage: ocr-audit66-fixtures.py [-h] [--only ONLY]`),无 traceback。

- [ ] **Step 3: 抽样单图跑通,验证 fixture 文件名构造 + 内容格式**

Run:
```bash
python tools/ocr-audit66-fixtures.py --only 49
```

Expected:
- stderr: `[scan] found 1 image(s) ...`、`[ocr] 49_...png -> 49_仁和氨糖软骨素钙片手机详情页-保健暗示安全-违规.txt`、行/字符数、`[done] 1 success, 0 fail`
- `app/src/test/resources/fixtures/audit66_ocr/49_仁和氨糖软骨素钙片手机详情页-保健暗示安全-违规.txt` 存在
- 内容应含肉眼可读的中文 OCR 文本

- [ ] **Step 4: 删除抽样 fixture**

Run:
```bash
rm "app/src/test/resources/fixtures/audit66_ocr/49_仁和氨糖软骨素钙片手机详情页-保健暗示安全-违规.txt"
rm "app/src/test/resources/fixtures/audit66_ocr/manifest.json"
```

- [ ] **Step 5: Commit**

```bash
git add tools/ocr-audit66-fixtures.py
git commit -m "feat(tools): ocr-audit66-fixtures.py — paddleocr 录制 66 张图 OCR"
```

---

## Task 3: 跑完整 66 张录制

**Files:**
- (产物落 gitignored 目录,不入仓)

- [ ] **Step 1: 跑全量录制**

Run:
```bash
python tools/ocr-audit66-fixtures.py 2>&1 | tail -80
```

Expected:
- 打印 `[scan] found 66 image(s) ...`
- 逐张打 `[ocr] NN_xxx.png -> NN_xxx.txt`, 行/字符数, 耗时(单张通常 2-10s)
- 最后 `[done] 66 success, 0 fail`(若个别图失败 ≥ 1 张可接受,但报告里要标)
- 总耗时 2-10 分钟(取决于机器)

- [ ] **Step 2: 验证 fixture 数量**

Run:
```bash
ls app/src/test/resources/fixtures/audit66_ocr/*.txt | wc -l
ls app/src/test/resources/fixtures/audit66_ocr/manifest.json
```

Expected:
- 第一个命令输出 `66` 或接近 66(允许少量失败)
- 第二个命令输出 manifest.json 路径

- [ ] **Step 3: 抽样验证 fixture 内容(2 张)**

Run:
```bash
echo "=== #01 碧桂园 ==="; head -3 "app/src/test/resources/fixtures/audit66_ocr/01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt"
echo "=== #66 小园玉粱 ==="; head -3 "app/src/test/resources/fixtures/audit66_ocr/66_小园玉粱-紫玉米花青素-增强免疫糖尿病安心-食品.txt"
echo "=== manifest summary ==="
python -c "import json; m=json.load(open('app/src/test/resources/fixtures/audit66_ocr/manifest.json',encoding='utf-8')); print('files:', len(m['files']), '| version:', m['paddleocr_version'], '| model:', m['model_name']); print('errors:', sum(1 for f in m['files'].values() if 'error' in f))"
```

Expected:
- 每张图首 3 行含肉眼可读中文(可含 OCR 错字)
- manifest summary 输出 `files: 66 | version: 3.7.0 | model: PP-OCRv6_small`,`errors: 0`

- [ ] **Step 4: 验证 .gitignore 隔离生效**

Run:
```bash
git status --short app/src/test/resources/fixtures/audit66_ocr/ | head -5
git check-ignore -v "app/src/test/resources/fixtures/audit66_ocr/01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt"
```

Expected:
- 第一个命令:**无输出**(整个目录被忽略,git 不跟踪)
- 第二个命令输出:`.gitignore:<行号>:<列> app/src/test/resources/fixtures/audit66_ocr/ ...`

- [ ] **Step 5: 不 commit(产物 gitignored)**

无 commit 步骤。fixture 目录被 .gitignore 隔离,无需 commit。

---

## Task 4: Kotlin 测试类骨架

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/rules/AdSignageOcrImageAudit66Test.kt`

- [ ] **Step 1: 创建测试类骨架(空 test 方法 + 现有 helper copy)**

完整代码:

```kotlin
package com.icespiritai.offline.rules

import com.icespiritai.offline.domain.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 66 张违规案例的真 OCR 端到端实测:从图出发,过 OCR(由 tools/ocr-audit66-fixtures.py 提前录制),
 * 再过 AdSignageRuleMatcher,与 _coverage_matrix.md §2 ground truth 比对。
 *
 * 与 AdSignageImageAuditSixtySixRegressionTest 差异:
 *   - 后者 fixture = 文件名提示词 + audit 违规描述 + 关联法条(假设 OCR 完美)
 *   - 本测试 fixture = 真 OCR 输出(paddleocr Python 录制)
 *
 * 输出:
 *   - stdout 实时打每张图状态
 *   - build/reports/audit66_ocr_<timestamp>.md 落盘报告
 *
 * 无硬断言 — 全量报告交付,人来看。fixture 缺失时 skip(提示先跑 Python 脚本)。
 */
class AdSignageOcrImageAudit66Test {

    private fun projectRoot(): File {
        val candidates = listOf(
            File("."),
            File(".."),
            File("../.."),
            File("../../.."),
        )
        return candidates.firstOrNull { File(it, "违规案例").exists() && File(it, "违规案例").isDirectory }
            ?: error("project root with 违规案例/ not found (cwd=${System.getProperty("user.dir")})")
    }

    private fun rulesFile(root: File): File {
        return listOf(
            File(root, "app/src/main/assets/rules/ad_signage_rules.json"),
            File(root, "src/main/assets/rules/ad_signage_rules.json"),
            File(root, "app/build/generated/assets/rules/ad_signage_rules.json"),
        ).firstOrNull { it.exists() && it.length() > 100 }
            ?: error("ad_signage_rules.json not found under ${root.absolutePath}")
    }

    private fun loadRules(root: File): List<AdSignageRule> {
        val raw = rulesFile(root).readText(Charsets.UTF_8)
        return kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true; isLenient = true
        }.decodeFromString(AdSignageRuleSet.serializer(), raw).rules
    }

    /** 原文件 stem → fixture 文件名(与 tools/ocr-audit66-fixtures.py 同规则) */
    internal fun buildFixtureFilename(stem: String): String {
        var i = 0
        while (i < stem.length && stem[i].isDigit()) i++
        val prefix = stem.substring(0, i)
        val rest = stem.substring(i).trimStart('_')
        val slug = rest.replace("_", "-")
        return if (prefix.isNotEmpty()) "${prefix}_${slug}.txt" else "${slug}.txt"
    }

    /** _coverage_matrix.md §2 解析(从 AdSignageImageAuditSixtySixRegressionTest.copy) */
    private fun parseCoverageMatrix(file: File): Map<String, Pair<List<String>, String>> {
        val text = file.readText(Charsets.UTF_8)
        val map = linkedMapOf<String, Pair<List<String>, String>>()
        var inSection2 = false
        val ruleIdRe = Regex("^(ad_signage|cosmetic|finance|internet)_")
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("## §2")) { inSection2 = true; continue }
            if (line.startsWith("## §3")) { inSection2 = false; continue }
            if (!inSection2) continue
            if (!line.startsWith("| `")) continue
            val cols = line.split("|").map { it.trim() }
            if (cols.size < 8) continue
            val filename = cols[1].removePrefix("`").removeSuffix("`")
            if (!filename.endsWith(".jpg") && !filename.endsWith(".png") && !filename.endsWith(".jpeg")) continue
            val rulesCell = cols[5]
            val rules = if (rulesCell == "—" || rulesCell.isBlank()) emptyList()
                else rulesCell.split(",").map { it.trim() }
                    .map { it.replace("`", "").replace("*(new)*", "").trim() }
                    .filter { ruleIdRe.containsMatchIn(it) }
            val status = cols[6].removePrefix("`").removeSuffix("`").trim()
            map[filename] = rules to status
        }
        return map
    }

    private data class ImageResult(
        val filename: String,
        val auditStatus: String,
        val groundTruth: List<String>,
        val actualHits: List<String>,
        val ocrText: String,
    ) {
        val overlap: List<String> get() = actualHits.filter { it in groundTruth }
        val missedGt: List<String> get() = groundTruth.filter { it !in actualHits }
        val extraHits: List<String> get() = actualHits.filter { it !in groundTruth }
        val fullCoverage: Boolean get() = groundTruth.isNotEmpty() && actualHits.containsAll(groundTruth)
        val partialCoverage: Boolean get() = groundTruth.isNotEmpty() && overlap.isNotEmpty() && !fullCoverage
        val noOverlap: Boolean get() = groundTruth.isNotEmpty() && overlap.isEmpty()
        val noGroundTruth: Boolean get() = groundTruth.isEmpty()
    }

    @Test
    fun ocrAudit66ImageRuleEngine() {
        val root = projectRoot()
        val coverageMap = parseCoverageMatrix(File(root, "违规案例/_coverage_matrix.md"))
        assertTrue("coverage matrix 解析为空", coverageMap.isNotEmpty())

        val fixturesDir = File(root, "app/src/test/resources/fixtures/audit66_ocr")
        if (!fixturesDir.exists() || fixturesDir.listFiles { f -> f.extension == "txt" }?.isEmpty() == true) {
            println("⚠️ fixtures not found at ${fixturesDir.absolutePath}")
            println("   请先跑: python tools/ocr-audit66-fixtures.py")
            return
        }

        val rules = loadRules(root)
        val ruleIds = rules.map { it.id }.toSet()
        val matcher = AdSignageRuleMatcher(rules)

        println("===== 66 image OCR rule-engine audit START =====")
        val results = mutableListOf<ImageResult>()

        for ((filename, gtAndStatus) in coverageMap.toSortedMap()) {
            val (groundTruth, auditStatus) = gtAndStatus
            groundTruth.forEach { rid ->
                assertTrue("[$filename] 引用未知规则 $rid", rid in ruleIds)
            }
            val stem = filename.substringBeforeLast(".")
            val fixtureFile = File(fixturesDir, buildFixtureFilename(stem))
            val ocrText = if (fixtureFile.exists()) fixtureFile.readText(Charsets.UTF_8) else ""
            val normalized = TextNormalizer.forMatching(ocrText)
            val hits = matcher.scan(normalized).map { it.ruleId }.distinct()
            val r = ImageResult(filename, auditStatus, groundTruth, hits, ocrText)
            results.add(r)

            val status = when {
                r.fullCoverage -> "FULL"
                r.partialCoverage -> "PARTIAL(${r.overlap.size}/${r.groundTruth.size})"
                r.noOverlap -> "MISS"
                r.noGroundTruth && hits.isEmpty() -> "no-gt/no-hit"
                r.noGroundTruth -> "no-gt/hit=${hits.size}"
                else -> "?"
            }
            val sample = hits.take(6).joinToString(",") + if (hits.size > 6) ",…" else ""
            val ocrPreview = ocrText.replace("\n", " ").take(60).ifEmpty { "(empty)" }
            println("[${status.padEnd(20)}] $filename  gt=${groundTruth.size} actual=${hits.size} [$sample]")
            println("    OCR: $ocrPreview")
        }

        val full = results.count { it.fullCoverage }
        val partial = results.count { it.partialCoverage }
        val miss = results.count { it.noOverlap }
        val noGt = results.count { it.noGroundTruth }

        println("===== 66 image OCR rule-engine audit SUMMARY =====")
        println("总数: ${results.size}")
        println("完全覆盖(actual ⊇ ground truth):        $full")
        println("部分覆盖(actual ∩ gt ≠ ∅ 但不全):       $partial")
        println("未覆盖(actual ∩ gt = ∅):                $miss")
        println("无 ground truth 规则:                   $noGt")

        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val reportDir = File(root, "build/reports")
        reportDir.mkdirs()
        val report = File(reportDir, "audit66_ocr_$ts.md")
        report.writeText(buildReport(results, full, partial, miss, noGt, ruleIds.size, rulesFile(root)))
        println("报告: ${report.absolutePath}")
        // 无硬断言 — 测试永远过;报告是 deliverable
    }

    private fun buildReport(
        results: List<ImageResult>,
        full: Int,
        partial: Int,
        miss: Int,
        noGt: Int,
        rulesTotal: Int,
        rulesFile: File,
    ): String = buildString {
        appendLine("# 66 张违规案例 · 真 OCR 规则识别实测报告")
        appendLine()
        appendLine("- 生成时间: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        appendLine("- 规则 JSON: ${rulesFile.relativeTo(rulesFile.parentFile.parentFile.parentFile)}")
        appendLine("- 规则总数: $rulesTotal")
        appendLine("- OCR 引擎: paddleocr 3.7.0 + PP-OCRv6_small(脚本: tools/ocr-audit66-fixtures.py)")
        appendLine("- fixture 来源: app/src/test/resources/fixtures/audit66_ocr/")
        appendLine("- ground truth 来源: 违规案例/_coverage_matrix.md §2")
        appendLine()
        appendLine("## §1 覆盖统计")
        appendLine()
        appendLine("| 类别 | 张数 | 占比 |")
        appendLine("|---|---:|---:|")
        val total = results.size
        appendLine("| 完全覆盖 (actual ⊇ ground truth) | $full | ${"%.1f".format(full * 100.0 / total)}% |")
        appendLine("| 部分覆盖 (actual ∩ gt ≠ ∅ 但不全) | $partial | ${"%.1f".format(partial * 100.0 / total)}% |")
        appendLine("| 未覆盖 (actual ∩ gt = ∅) | $miss | ${"%.1f".format(miss * 100.0 / total)}% |")
        appendLine("| 无 ground truth 规则 | $noGt | ${"%.1f".format(noGt * 100.0 / total)}% |")
        appendLine()
        appendLine("## §2 全量命中清单(按文件名升序)")
        appendLine()
        appendLine("| # | 文件名 | audit 状态 | ground truth | 实际命中 | 状态 | OCR 前 60 字 |")
        appendLine("|---:|---|---|---|---|---|---|")
        for ((i, r) in results.withIndex()) {
            val status = when {
                r.fullCoverage -> "✅ 完全覆盖"
                r.partialCoverage -> "⚠️ 部分覆盖(${r.overlap.size}/${r.groundTruth.size})"
                r.noOverlap -> "❌ 未覆盖"
                r.noGroundTruth && r.actualHits.isEmpty() -> "— 无规则无命中"
                r.noGroundTruth -> "— 无规则 hit=${r.actualHits.size}"
                else -> "?"
            }
            val gtStr = if (r.groundTruth.isEmpty()) "—" else r.groundTruth.joinToString(", ")
            val actualStr = if (r.actualHits.isEmpty()) "—" else r.actualHits.joinToString(", ")
            val ocrPreview = r.ocrText.replace("\n", " ").replace("|", "\\|").take(60).ifEmpty { "(empty)" }
            appendLine("| ${i + 1} | `${r.filename}` | ${r.auditStatus} | $gtStr | $actualStr | $status | $ocrPreview |")
        }
        appendLine()
        appendLine("## §3 关键 gap 列表(audit 标「已覆盖」但真 OCR 未覆盖或仅部分覆盖)")
        appendLine()
        val keyGaps = results.filter { it.auditStatus == "已覆盖" && !it.fullCoverage }
        if (keyGaps.isEmpty()) {
            appendLine("无(audit 标「已覆盖」的图实测全部命中 ground truth)。")
        } else {
            appendLine("| 文件名 | ground truth | 实际命中 | 漏命中 | OCR 前 60 字 |")
            appendLine("|---|---|---|---|---|")
            for (r in keyGaps.sortedBy { it.filename }) {
                val missed = if (r.missedGt.isEmpty()) "—" else r.missedGt.joinToString(", ")
                val actual = if (r.actualHits.isEmpty()) "—" else r.actualHits.joinToString(", ")
                val ocrPreview = r.ocrText.replace("\n", " ").replace("|", "\\|").take(60).ifEmpty { "(empty)" }
                appendLine("| `${r.filename}` | ${r.groundTruth.joinToString(", ")} | $actual | $missed | $ocrPreview |")
            }
        }
        appendLine()
    }
}
```

- [ ] **Step 2: 验证编译**

Run:
```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat compileDebugUnitTestKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`,无 Kotlin compile error。

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/icespiritai/offline/rules/AdSignageOcrImageAudit66Test.kt
git commit -m "test(rules): AdSignageOcrImageAudit66Test — 真 OCR 端到端实测"
```

---

## Task 5: 跑测试 + 验证报告

**Files:**
- (产物落 build/reports/,不入仓)

- [ ] **Step 1: 跑测试**

Run:
```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageOcrImageAudit66Test" 2>&1 | tail -30
```

Expected:
- `BUILD SUCCESSFUL`
- 测试通过(`1 test completed, 0 failed`)
- stdout 含 66 行 `[FULL/PARTIAL/MISS] ...` 状态行 + 66 行 `OCR: ...` preview 行
- 最后打印 `报告: ...build/reports/audit66_ocr_<timestamp>.md`

- [ ] **Step 2: 验证报告落盘**

Run:
```bash
ls -la build/reports/audit66_ocr_*.md | tail -3
echo "---"
wc -l build/reports/audit66_ocr_*.md | tail -3
```

Expected:
- 至少 1 个 audit66_ocr_*.md 文件
- 行数 ≥ 80(§1 统计 + §2 全量清单 66 行 + §3 gap)

- [ ] **Step 3: 验证报告 §1 统计有数字**

Run:
```bash
grep -E "完全覆盖|部分覆盖|未覆盖|无 ground" build/reports/audit66_ocr_*.md | head -10
```

Expected: 4 行(每行带一个分类 + 数字 + 百分比)。

- [ ] **Step 4: 验证报告 §2 全量清单有 66 行**

Run:
```bash
grep -c "^| [0-9]\+ |" build/reports/audit66_ocr_*.md
```

Expected: `66`(精确数字,可能 ±1 因为 §1 / §3 也用 `| NN |` 模式,grep 一下排除)。

更精确:
```bash
awk '/^## §2/{flag=1; next} /^## §3/{flag=0} flag && /^\| [0-9]+ \|/' build/reports/audit66_ocr_*.md | wc -l
```

Expected: `66`。

- [ ] **Step 5: 不 commit(报告在 build/reports/ 不入仓)**

无 commit 步骤。

---

## Task 6: 写 smoke 文档

**Files:**
- Create: `docs/smoke/2026-08-28-ocr-audit66.md`

- [ ] **Step 1: 从报告里读出统计数,写 smoke 文档**

先读关键统计:
```bash
REPORT=$(ls build/reports/audit66_ocr_*.md | sort | tail -1)
echo "Report: $REPORT"
grep -A 1 "完全覆盖\|部分覆盖\|未覆盖\|无 ground" "$REPORT" | head -10
```

然后创建 smoke 文档完整内容(把下面模板里 `<...>` 占位符替换为 Step 1 实际值):

```markdown
# 66 张违规案例真 OCR 端到端识别实测 · smoke

| 项 | 值 |
|---|---|
| 日期 | 2026-08-28 |
| 关联 spec | `docs/superpowers/specs/2026-08-28-icevision-ocr-audit-66-design.md` |
| 关联 plan | `docs/superpowers/plans/2026-08-28-icevision-ocr-audit-66-plan.md` |
| 关联测试 commit | (由 Task 5 之后补) |
| 报告路径 | (由 Task 5 之后补,`build/reports/audit66_ocr_<ts>.md`) |

## §目的

回答「用项目使用的 PP-OCRv6_small OCR 模型 + 129 条 v9 规则,在 66 张违规案例图上能不能准确识别主要违规点」。

与 `AdSignageImageAuditSixtySixRegressionTest`(commit 前置)差异:
- 后者 fixture = audit 文档文本(假设 OCR 完美),本质是规则引擎审计
- 本测试 fixture = paddleocr Python 真 OCR 输出,反映 OCR 还原度 + 规则引擎串联后的真实识别能力

## §环境

| 项 | 值 |
|---|---|
| OS | Windows 11 |
| Python | 3.12.10 |
| paddleocr | 3.7.0(原生 paddlepaddle 推理,**非** ONNX) |
| 模型 | PP-OCRv6_small_det / PP-OCRv6_small_rec |
| JDK | 17.0.18+8 |
| Gradle | (gradle --version) |
| Android OCR runtime | ONNX Runtime + OpenCV(仅作对比说明,本次未跑) |

## §runtime 差异说明

paddleocr Python(本测试用)和 Android(用户实际用)加载同一族 PP-OCRv6_small 模型,但走不同 runtime:

| 维度 | paddleocr Python | Android (app) |
|---|---|---|
| 推理后端 | paddlepaddle 原生 | ONNX Runtime |
| 图像处理 | paddle 内置 cv2 | OpenCV 4.x |
| 输入格式 | BGR / RGB | Bitmap |
| rec dict | 18708 | 18708 |
| det 模型 | PP-OCRv6_small_det | PP-OCRv6_small_det |

**预期**:文字识别结果高度重合,差异主要在极端 case(小字 / 旋转 / 模糊)。报告结论反映 paddleocr Python 在本机 Windows 上的识别能力,**不完全等于** Android 端能力,但可作为基线参考。

## §录制步骤

```bash
# 一次性录制
python tools/ocr-audit66-fixtures.py
# 产物落 app/src/test/resources/fixtures/audit66_ocr/(gitignored)
```

本次录制总耗时约 N 分钟 / 66 张图(由 Task 3 实际填)。

## §实测步骤

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.rules.AdSignageOcrImageAudit66Test"
# 报告落 build/reports/audit66_ocr_<timestamp>.md
```

## §结果统计

(从 Task 5 报告 §1 抄)

| 类别 | 张数 | 占比 |
|---|---:|---:|
| 完全覆盖(actual ⊇ ground truth) | X | xx% |
| 部分覆盖(actual ∩ gt ≠ ∅ 但不全) | X | xx% |
| 未覆盖(actual ∩ gt = ∅) | X | xx% |
| 无 ground truth 规则 | X | xx% |

**总命中率**(命中 ≥ 1 个 ground truth):X / 66

## §关键 gap

(从报告 §3 抄,audit 标「已覆盖」但真 OCR 未覆盖或仅部分覆盖)

> 完整列表见报告 §3。这里只列**前 5 张**(按严重度)。

| 文件名 | ground truth | 实际命中 | 漏命中 |
|---|---|---|---|
| ... | ... | ... | ... |

## §结论

(根据结果写)

- 若命中率 ≥ 75%(≥ 50 / 66):OCR 端到端能力可接受,主要违规点能识别
- 若命中率 < 50%:OCR 还原度是主要瓶颈,需 followup 真机 baseline 或 ONNX Runtime Java
- 若部分覆盖占比大:规则 keywords 漏词,需 followup 扩 keywords(独立 spec 范畴)

## §下一步 followup(可选)

- 若 gap 集中于 OCR 漏字:考虑录制真机 OCR baseline(在华为 nova 6 上跑 ice_ocr_rules profile + 拉输出)
- 若 gap 集中于规则:扩 keywords(独立 spec,本期不动 ad_signage_rules.json)
- 若想精确反映 Android 端能力:考虑 ONNX Runtime Java 集成 JVM 跑同一份 .onnx
```

- [ ] **Step 2: Commit**

```bash
git add docs/smoke/2026-08-28-ocr-audit66.md
git commit -m "docs(smoke): 66 张图真 OCR 端到端识别实测记录"
```

---

## 验收(全部满足 = plan 完成)

| # | 条件 | 验证 |
|---|---|---|
| 1 | `.gitignore` 含 audit66_ocr | `grep audit66_ocr .gitignore` |
| 2 | `tools/ocr-audit66-fixtures.py` 存在 + `--help` 通过 | `python tools/ocr-audit66-fixtures.py --help` |
| 3 | 66 个 fixture .txt + manifest.json 落盘 | `ls app/src/test/resources/fixtures/audit66_ocr/*.txt \| wc -l` |
| 4 | .gitignore 隔离生效 | `git status --short app/src/test/resources/fixtures/audit66_ocr/` 无输出 |
| 5 | 测试类编译过 | `./gradlew.bat compileDebugUnitTestKotlin` 成功 |
| 6 | 测试跑过 + 报告生成 | `./gradlew.bat testDebugUnitTest --tests AdSignageOcrImageAudit66Test` exit 0 + report 存在 |
| 7 | 报告含 §1 / §2 / §3 | `grep -E '^## §[123]' report` |
| 8 | smoke 文档落盘 | `ls docs/smoke/2026-08-28-ocr-audit66.md` |
| 9 | 6 个 commit 落地 | `git log --oneline -6` |

预期 commit 序列:
```
docs(spec): 66 张违规案例真 OCR 端到端实测 spec            (前序)
chore(gitignore): ignore audit66_ocr OCR fixture dir
feat(tools): ocr-audit66-fixtures.py — paddleocr 录制 66 张图 OCR
test(rules): AdSignageOcrImageAudit66Test — 真 OCR 端到端实测
docs(smoke): 66 张图真 OCR 端到端识别实测记录
```

(实际是 5 个 commit,前序 spec commit 在 plan 启动前已有。)
