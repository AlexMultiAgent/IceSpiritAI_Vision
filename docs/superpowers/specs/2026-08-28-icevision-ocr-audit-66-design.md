# 冰灵锐目 — 66 张违规案例真 OCR 端到端识别实测

| 项 | 值 |
|---|---|
| 文档版本 | v0.1.0 |
| 日期 | 2026-08-28 |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联审计 spec | `docs/superpowers/specs/2026-08-27-icevision-rules-coverage-audit-design.md` |
| 关联 v9 规则 commit | `8dc7163 fix(v0.1.36): updater progress + POST_NOTIFICATIONS + severity rank + release dedup` |
| 关联最近 案例归档 commit | `6290379 feat(cases): 违规案例图片归档 — 66 张 + 总册(14 桶 / Critical×59 + Warning×7)` |

本文档规范一项**真 OCR 端到端识别实测**任务:用项目使用的 PP-OCRv6_small OCR 模型 + 当前 129 条 v9 规则,在已归档的 66 张违规案例图上跑完整识别流水线,产生 main violation 命中清单报告。

OCR 模型路线、规则引擎、构建系统、sourceSet 拆分、APK 流水线**完全保持现状**,不动。

---

## 1. 背景与目标

### 1.1 现状

- `违规案例/` 下 66 张违规广告图(commit `6290379` 归档)
- `app/src/main/assets/rules/ad_signage_rules.json` v9,**129 条规则**
- `_违规档案总册.md` 每张图含「**关联规则 ID**」列(由 `2026-08-27` 审计写入,作为 ground truth)
- `_coverage_matrix.md` §2 = 每张图的 ground truth 规则 ID 集合 + audit 标的状态(已覆盖 / 弱覆盖 / 未覆盖)
- **已有** `AdSignageImageAuditSixtySixRegressionTest`:用 **audit 文档** 拼 fixture(文件名 + 违规描述 + 关联法条),跑规则引擎,产出 markdown 报告 — 这是**规则引擎审计**,**不验 OCR 还原度**
- OCR 模型现状:app 用 **PP-OCRv6_small_det / PP-OCRv6_small_rec**(`app/src/main/assets/models/{det,rec}/inference.onnx`),走 PaddleOCR Android SDK v3.7.0(ONNX Runtime + OpenCV)
- 本机 Python 环境:**paddleocr 3.7.0 已装**(走 paddlepaddle 原生推理,不是 ONNX)

### 1.2 现状 gap

`AdSignageImageAuditSixtySixRegressionTest` 用 audit 文本作 fixture,等价于「假设 OCR 能完美读出所有字段」 — 无法回答:

- 真实 OCR 在这 66 张图上能不能读出关键词
- 关键词没读出来是因为 OCR 漏字、还是规则 keywords 不全
- 关键词读出来了规则不命中,是 OCR 噪音导致还是 AC trie 边界问题

需要一个**真 OCR 端到端实测**:从图出发,过 OCR,再过规则引擎,与 ground truth 比对,识别能力 gap 必须能定位到 OCR 或规则层。

### 1.3 目标

- 用本机 paddleocr 3.7.0 + PP-OCRv6_small 跑 66 张图,导出 OCR 文本为 fixture
- 新增 `AdSignageOcrImageAudit66Test`,加载 OCR fixture,跑真实规则引擎,产出全量 66 张报告
- 报告含:覆盖统计 + 全量命中清单(ground truth / 实际命中 / OCR 文本前 60 字)+ 与 audit 文本 fixture 的对比段
- 全量报告形式输出(不设硬断言,人来看)
- 写 smoke 文档记录结果与 runtime 差异说明

### 1.4 非目标(本期)

- ❌ **不**改 `ad_signage_rules.json`(用户没要求扩规则;扩规则是单独 spec 的事)
- ❌ **不**改 `food_label_rules.json`(focus 是 ad_signage)
- ❌ **不**改 / 删 / 替代 `AdSignageImageAuditSixtySixRegressionTest`(两者价值不同,audit 文本测的是「规则引擎自身」)
- ❌ **不**做 Android instrumented test(已排除真机 / emulator 路径)
- ❌ **不**写硬断言(用户选「全量报告」)
- ❌ **不**自动化把报告 commit 到 git(report 落 `build/reports/`,已在 `.gitignore`)
- ❌ **不**改总册 / `_coverage_matrix.md` / `_audit_gaps.md`(它们的 ground truth 来源就是审计结果,本身不被新测试改变)
- ❌ **不**录制 Android 真机 OCR baseline(用户选 Python paddleocr;runtime 差异在 smoke 文档里注明)

---

## 2. 总体方案:Python OCR 录制 + JVM 实测(Approach B)

| Phase | 产出 | 依赖 |
|---|---|---|
| 1. Fixture 录制 | `app/src/test/resources/fixtures/audit66_ocr/<NN>_<slug>.txt` × 66 + `manifest.json` + `tools/ocr-audit66-fixtures.py` | 现行归档 |
| 2. JVM 实测类 | `app/src/test/java/com/icespiritai/offline/rules/AdSignageOcrImageAudit66Test.kt` + `.gitignore` 增 fixture dir | Phase 1 |
| 3. 报告 + smoke | `build/reports/audit66_ocr_<timestamp>.md` + `docs/smoke/2026-08-28-ocr-audit66.md` | Phase 1 + 2 |

> 备选 Approach(已否决):
> - **A 真机 OCR**:华为 nova 6 已连,但要走 `assembleDebug -PmodelProfile=ice_ocr_rules` + `am instrument` + 拉 OCR 输出,setup 重、CI 不友好、与「全量报告」交付不匹配
> - **C audit 文本 fixture**:等价于「OCR 完美假设」,不能回答 OCR 还原度问题(就是 gap 本身)

---

## 3. Phase 1 — Python Fixture 录制

### 3.1 输入

- `违规案例/` 下 66 张图(过滤:文件名匹配 `\d+_.*\.(jpg|png|jpeg)`,排除 `_*` 文档类)
- 当前 `app/src/main/assets/rules/ad_signage_rules.json` 引用模型:**PP-OCRv6_small**(`rec/inference.yml: Global.model_name = PP-OCRv6_small_rec`,det 同)

### 3.2 脚本: `tools/ocr-audit66-fixtures.py`

| 项 | 规格 |
|---|---|
| 入口 | `python tools/ocr-audit66-fixtures.py` |
| 工作目录 | 项目根(`违规案例/` 必须存在) |
| 依赖 | `paddleocr==3.7.0`,首次跑自动下载 PP-OCRv6_small 模型到 `~/.paddleocr/` |
| OCR 配置 | `PaddleOCR(use_angle_cls=True, lang='ch', ocr_version='PP-OCRv6_small')`(3.7.0 API;若 API 字段名差异在脚本里 fallback) |
| 输出目录 | `app/src/test/resources/fixtures/audit66_ocr/` |
| 文件名 | `<NN>_<slug>.txt`,NN 取原文件编号前缀(01..66),slug = 去 `NN_` 前缀后中段(`_` → `-`)。例:原文件 `01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg` → fixture 文件 `01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt`。**Python 脚本与 Kotlin 测试必须按同一规则构造文件名**(否则测试找不到 fixture)。 |
| 内容 | 单 string,所有识别行按 y 坐标升序排,每行末尾去置信度;空识别结果保留空文件 + stderr warning |
| manifest.json | 含 `paddleocr_version`、`model_name`、`generated_at`、66 张图的「file → 行数 → 字符数」映射 |
| 幂等 | 重新跑覆盖同名文件 |
| 日志 | stdout 实时打每张图状态,sumo 落 `build/reports/audit66_ocr_fixtures.log`(可读 ASCII,GBK 错字用 `\uXXXX` 表示) |

### 3.3 .gitignore 增项

```
app/src/test/resources/fixtures/audit66_ocr/
```

加理由:fixture 是录制产物,几十 KB 量级,占空间 + 噪声;clone 仓库的人手动跑一次脚本即可生成。

### 3.4 边界 / 失败模式

| 情况 | 处理 |
|---|---|
| paddleocr 未装 / 版本 < 3.7 | 脚本顶部 `assert` 检查 + 明确报错 + `pip install paddleocr==3.7.0` 指引 |
| PP-OCRv6_small 模型未下载 | 脚本自动下载;若镜像慢 / 失败,stderr 给 `paddleocr --lang ch` 命令手动触发 |
| 单张图 OCR 抛异常 | 跳过该图,stderr warning,manifest 标 `error`,继续跑其余 |
| 中文字符 GBK 编码错(stdout cp936) | 脚本开头 `sys.stdout.reconfigure(encoding='utf-8')` 强制 UTF-8 |
| macOS / Linux 路径分隔 | 用 `pathlib.Path`,不 hardcode `\`,跨平台 |

---

## 4. Phase 2 — JVM 实测类

### 4.1 文件

`app/src/test/java/com/icespiritai/offline/rules/AdSignageOcrImageAudit66Test.kt`

### 4.2 结构

```kotlin
class AdSignageOcrImageAudit66Test {

    // 复用现有 AdSignageImageAuditSixtySixRegressionTest 的
    //   - projectRoot() / rulesFile() / loadRules()
    //   - parseCoverageMatrix(file): Map<文件名, Pair<gt rules, status>>
    //   - ImageResult data class + status 分类逻辑
    // 提取到一个公共 helper(本 spec 不要求重构,先 copy-paste 复用;
    //   若重复 > 50 行则抽 helper)

    @Test fun ocrAudit66ImageRuleEngine() {
        val root = projectRoot()
        val coverageMap = parseCoverageMatrix(...)
        val rules = loadRules(root)
        val matcher = AdSignageRuleMatcher(rules)

        // fixture 目录存在性 + 数量 pin(>= 60;少量图 OCR 失败容忍)
        val fixturesDir = File(root, "app/src/test/resources/fixtures/audit66_ocr")
        if (!fixturesDir.exists() || fixturesDir.listFiles().isEmpty()) {
            println("⚠️ fixtures not found at ${fixturesDir.absolutePath}")
            println("   请先跑: python tools/ocr-audit66-fixtures.py")
            return  // skip,不 fail
        }

        val results = mutableListOf<ImageResult>()
        for ((filename, gtAndStatus) in coverageMap.toSortedMap()) {
            val (gt, status) = gtAndStatus
            val slug = filename.substringBeforeLast(".")
            val fixtureFile = File(fixturesDir, "${filename.numberPrefix}_$slug.txt")
            val ocrText = if (fixtureFile.exists()) fixtureFile.readText() else ""
            val normalized = TextNormalizer.forMatching(ocrText)
            val hits = matcher.scan(normalized).map { it.ruleId }.distinct()
            results.add(ImageResult(filename, status, gt, hits))
        }

        // 落盘报告
        File(root, "build/reports/audit66_ocr_${ts}.md").writeText(buildReport(...))

        // 不设硬断言 — 全量报告交付
    }
}
```

### 4.3 报告 schema `build/reports/audit66_ocr_<timestamp>.md`

```markdown
# 66 张违规案例 · 真 OCR 规则识别实测报告

- 生成时间: ...
- 规则 JSON: app/src/main/assets/rules/ad_signage_rules.json (v9 / 129 条)
- OCR 引擎: paddleocr 3.7.0 + PP-OCRv6_small
- fixture 来源: app/src/test/resources/fixtures/audit66_ocr/
- ground truth 来源: 违规案例/_coverage_matrix.md §2

## §1 覆盖统计
| 类别 | 张数 | 占比 |
|---|---:|---:|
| 完全覆盖 (actual ⊇ ground truth) | X | xx% |
| 部分覆盖 (∩ ≠ ∅ 但不全) | X | xx% |
| 未覆盖 (∩ = ∅) | X | xx% |
| 无 ground truth | X | xx% |

## §2 全量命中清单 (按文件名升序)
| # | 文件名 | audit 状态 | ground truth | 实际命中 | 状态 | OCR 前 60 字 |
|---:|---|---|---|---|---|---|

## §3 与 audit 文本 fixture 对比
| 文件名 | audit 文本 hit | 真 OCR hit | hit diff | OCR 是否更严苛 |
|---|---|---:|---:|---|

## §4 关键 gap 列表 (audit 标"已覆盖"但真 OCR 未覆盖)
| 文件名 | ground truth | 实际命中 | OCR 前 60 字 |
|---|---|---|---|
```

`§3` 的「audit 文本 hit 数」取自最近一次 `AdSignageImageAuditSixtySixRegressionTest` 落盘的 `build/reports/66image_audit_<ts>.md`(本 spec 不强制重跑;若该报告不存在则 §3 标「缺,需先跑现有测试」)。

### 4.4 边界 / 失败模式

| 情况 | 处理 |
|---|---|
| fixture 目录不存在 | `print` 提示 + `return`(不 fail),让跑的人知道要先跑 Python 脚本 |
| fixture 文件数 < 60(多张图 OCR 失败) | 不 fail,报告里标「OCR 失败 X 张,fixture 文件数 Y」 |
| `_coverage_matrix.md` §2 解析为空 | `assertTrue` 强失败(这是 ground truth,缺失就是真问题) |
| 规则 JSON 引用未知 ruleId | `assertTrue` 强失败(防止 fixture 引用已废弃规则) |

---

## 5. Phase 3 — Smoke 文档

### 5.1 路径

`docs/smoke/2026-08-28-ocr-audit66.md`

### 5.2 必含段

| 段 | 内容 |
|---|---|
| §目的 | 本次实测要回答的问题 + 与 `AdSignageImageAuditSixtySixRegressionTest` 的差异 |
| §环境 | OS / Python 版本 / paddleocr 版本 / 模型路径 / JDK 17 |
| §runtime 差异说明 | paddleocr Python(paddlepaddle 原生推理)vs Android(ONNX Runtime + OpenCV),理论输出类似但非字节级一致 |
| §录制步骤 | 完整命令行:激活 venv → 装 paddleocr → 跑 `tools/ocr-audit66-fixtures.py` → 抽样验证 fixture 内容 |
| §实测步骤 | `./gradlew testDebugUnitTest --tests AdSignageOcrImageAudit66Test` → 报告路径 |
| §结果统计 | FULL / PARTIAL / MISS / 无 gt 张数 + 关键 gap(>5 张未覆盖的 ruleId) |
| §与 audit 文本对比 | 真 OCR 比 audit 文本严苛多少张?差在哪里(ruleId 层 / OCR 漏字层)? |
| §结论 | 主要违规点能不能识别?剩下 gap 是 OCR 问题还是规则问题?下一步建议(不扩规则,但下次 spec 可考虑) |
| §下一步 followup(可选) | 若 gap 集中于 OCR 漏字,followup:真机 baseline 录制 / 若 gap 集中于规则,followup:扩 keywords |

---

## 6. 验收

按顺序,以下条件**全部满足**才能标 spec 完成:

| # | 条件 | 验证方式 |
|---|---|---|
| 1 | `tools/ocr-audit66-fixtures.py` 跑成功,产出 60+ 个 .txt + manifest.json | `ls app/src/test/resources/fixtures/audit66_ocr/*.txt \| wc -l` |
| 2 | `app/src/test/resources/fixtures/audit66_ocr/` 加进 `.gitignore` | `grep audit66_ocr .gitignore` |
| 3 | `AdSignageOcrImageAudit66Test.kt` 编过 | `./gradlew compileDebugUnitTestKotlin` |
| 4 | 测试跑过(无论 OCR fixture 缺不缺,至少不 fail) | `./gradlew testDebugUnitTest --tests AdSignageOcrImageAudit66Test` exit 0 |
| 5 | 报告生成 | `ls build/reports/audit66_ocr_*.md` |
| 6 | smoke 文档落盘 | `ls docs/smoke/2026-08-28-ocr-audit66.md` |
| 7 | smoke §结果统计 + §结论 + §runtime 差异说明 三段写完 | grep 章节标题 |

---

## 7. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| paddleocr Python 模型下载慢 / 镜像不通 | 中 | Phase 1 卡住 | 文档给 `HF_ENDPOINT` / `PADDLE_PDX_MODEL_SOURCE` 镜像变量;fallback 让用户从 app/src/main/assets/models 复用(经 paddle → onnx → paddle 转换,本期不投入) |
| paddleocr Python 与 Android ONNX 输出差异大 | 中 | 报告结论失真 | smoke 文档 §runtime 差异说明 + 报告 §3 与 audit 文本对比,让用户能区分 OCR 漏字 vs 规则不全 |
| 66 张图 OCR 一次性跑 5+ 分钟 | 高 | 跑测体验差 | 脚本可单文件跑(`python ... --only 49`);fixture 录制跑一次,后续测试秒级 |
| 录制 fixture 与 app 实跑结果差距大导致报告失真 | 中 | 结论不可信 | smoke 文档明确「本报告反映 paddleocr Python 在 Windows 上的识别能力,不等于 Android 端能力」;后续 spec 可补真机 baseline |
| _coverage_matrix.md 后续被改,ground truth drift | 低 | 测试报告维度变化 | 测试启动时打印 _coverage_matrix.md 的 mtime,与最近 commit hash 对比,让跑的人知道 ground truth 是否新鲜 |
| 复用了现有 66-image 测试的解析 helper,导致两份代码分叉 | 中 | 维护成本 | 接受本期 copy-paste;若后续发现 helper 改了一处忘了另一处,followup 抽公共 helper(本 spec 不强制) |

---

## 8. 不在本期范围(明确边界)

- ❌ Android 真机 OCR baseline 录制(可在 followup 单独做)
- ❌ ONNX Runtime Java 集成 JVM 跑 ONNX(模型路径 / 配置 / 安装复杂,与「快速出报告」目标不符)
- ❌ 把 audit 文本 fixture 测试替换掉(用户明确选保留)
- ❌ 自动把 OCR fixture commit 到 git(`.gitignore` 隔离,手动跑生成)
- ❌ 改 `ad_signage_rules.json` / `food_label_rules.json`(单独 spec 范畴)
- ❌ 改总册 / `_coverage_matrix.md` / `_audit_gaps.md`
- ❌ 跑 `AdSignageImageAuditSixtySixRegressionTest`(本 spec 报告 §3 复用其最近一次结果,不强求重新跑)
- ❌ 任何 release / 发版动作(本 spec 不出 commit 触发发版)
