# OCR 长图 / 高密度小字 评估 — 2026-08-29

> **Date**: 2026-08-29
> **触发**: [2026-08-29-keyword-expansion-2nd-verify.md](../smoke/2026-08-29-keyword-expansion-2nd-verify.md) E2E 后剩 4 MISS (#08 #19 #59 #60),其中 #08 #59 #60 是 **OCR 端漏检**(`text_chars < 100`),#19 是 OCR + keyword 双重问题
> **结论**: 暂不落地 — 现有方案已是最优帕累托,详见 §3
> **讨论**: long-image slicing 是 OCR 端的最大潜在改进方向,但 ROI 在当前 fixture 集 / 模型选择下不划算

## §1 现状

### §1.1 3 张 OCR-end MISS 的特征

| Fixture | 文件大小 | text_chars | 推测原图分辨率 | 推测字号 | det 行为 |
|---|---:|---:|---|---|---|
| **#08** 蜜蜜游俄罗斯椴树蜜 | 12.9 MB | 3 chars | ~8000×6000 | 招牌大字 ~80pt | downsampled 到 2048×N 后,招牌字 ~20px,det 阈值 0.2 下未识别 |
| **#59** 凯利集团地产 | 5.9 MB | 24 chars | ~5000×3500 | 招牌字 ~60pt | downsampled 后 ~15px,det 边缘 case |
| **#60** 哈佛特区地产 | 5.8 MB | 25 chars | ~5000×3500 | 招牌字 ~60pt | 同 #59 |

所有 3 张都是 **"大图 + 中等字号"**:downsampled 后文字占像素数 < 20px,刚好在 PP-OCRv6_det 的 det_thresh=0.2 检出边缘。

### §1.2 现有拦截尝试(2026-08-29 已实证)

[PaddleOcrEngine.kt:106-114](../../app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt#L106-L114) 注释明示:**已经试过** 把 det 参数放宽到 `1280 / min / 0.3 / 0.5 / 1.6`,66 张 E2E 覆盖率 **93.9% → 90.8%**(FULL 44→26 / PARTIAL 18→33)。原因不是 "放宽不够",而是放宽 → 引入 noise → matcher 把不同 rule id 命中 → overlap 减少 → FULL → PARTIAL。**净负回滚**。

即:**det 阈值放宽的方向是死的**。剩下的方向就是提升输入图像的"文字像素密度"——这指向 image-side 的预处理。

### §1.3 现有 downsample 策略

[BitmapLoader.kt:13](../../app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt#L13) `DEFAULT_MAX_EDGE_PX = 2048`。

[BitmapLoader.kt:65-80](../../app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt#L65-L80) `sampleSize()` 算法 floor 到 2 的幂,使 longest edge **接近但不超** maxEdge。

`#08` 12.9 MB 推断 8000×6000 → longest 8000 → sampleSize=4 → 2000×1500 → 文字 80pt → ~20px → det 漏。

## §2 候选方案对比

### §2.1 方案 A — Block-based slicing(分块全 OCR)

**思路**:把 downsampled bitmap 分成 N×M 块(如 2×2),各块单独 OCR,合并结果。

| 维度 | 评估 |
|---|---|
| 必要覆盖 | ✅ 直接对症(#08 #59 #60 都是"大图 downsampled 后小字") |
| 实现成本 | 中 — `BitmapLoader` 加 block-tile 函数 + `PaddleOcrEngine.recognize` 多遍 OCR + 拼接 |
| 边界文字被切 | ⚠️ 边界行字符跨块被切 → det 漏该行 → 可能引入新 MISS |
| 减少 overlap | ⚠️ 块间 overlap 留 50-100 px 缓解,但增加总 OCR 次数 |
| latency | ❌ 4 块 → 4 次 `ocr.recognize(bitmap)` → warm_avg_ms 2043 → ~8000 ms(4×) |
| memory | 4 块 bitmap 共占用 ~4 × (2048×1500×4B) ≈ 48 MB 一次性 peak |
| recBatchSize 浪费 | ⚠️ 现有 `recBatchSize=6`,切 4 块每块 ~20 lines → batch 未填 → preprocess overhead 摊薄 |

**结论**:**❌ 不推荐**。latency 4× 退化无法接受,且每块 batch 未填,warm 优势丢失。

### §2.2 方案 B — Two-pass zoom-in(局部 zoom OCR)

**思路**:
1. 第一遍:downsampled bitmap → det → 拿到候选文字框
2. 对 **first-pass 漏字 + 大图 + 高密度字号 估计** 的局部区域:用原图(无 downsample 或更小 downsample)在该区域重 OCR
3. 合并两遍结果

| 维度 | 评估 |
|---|---|
| 必要覆盖 | ⚠️ 需要定义 "需要 zoom 的区域" — 比如 "第一遍 det 框 < 5 个 + 原图 > 5MB" |
| 实现成本 | 大 — 第一遍 det + 第二遍区域 crop + 坐标映射 + 合并逻辑 |
| 边界文字 | ✅ 不切图,zoom 区域是 det 框附近,不需要再切 |
| latency | ⚠️ 通常 1.5× (1 first + ~0.5 second) → ~3000 ms;最坏 2× |
| 风险 | ⚠️ "需要 zoom 的区域" 启发式错估 → 该 zoom 的没 zoom(漏检) 或 不该 zoom 的 zoom 了(浪费) |
| 可观测性 | ✅ 两遍 OCR 各自 log metric,容易定位问题 |

**结论**:**⚠️ 可选**,但需要 A/B 验证 + 启发式 tuning,工作量 1-2 天。

### §2.3 方案 C — 单图 maxEdge 提升到 4096

**思路**:直接把 `DEFAULT_MAX_EDGE_PX` 从 2048 提到 4096。

| 维度 | 评估 |
|---|---|
| 必要覆盖 | ✅ 直接解决 #08 #59 #60 — 文字 ~40px(2x),det 阈值 0.2 下完全识别 |
| 实现成本 | 极小 — 单行常量改动 |
| latency | ⚠️ bitmap 内存 4×(2048×1500×4B × 4 = 24 MB),det 单图推理 1.5-2×(更长边,model 内部 pipeline 长) |
| model batch 摊薄 | ⚠️ 同 batch_size=6,det 单图时间翻倍但仍是单图 |
| 兼容性 | ⚠️ 部分老旧机型(2GB RAM)可能 OOM |

**结论**:**❌ 单行改动看似诱人,但 §2.1 §2.2 的 det 阈值放宽历史告诉我们,任何单边放大都会让另外 60+ 张 FULL fixture 受影响** — 12.9MB 图相对罕见,4096 maxEdge 会让 4-5 MB 正常广告图也按 4096 处理(不再下采样),det 慢一倍,但对 FULL fixture 无帮助(原来 2048 已能识别)。

且单图推理时间从 ~800ms 涨到 ~1600ms,total warm_avg_ms 2043→ 2843 — SLA 退化但不严重。

### §2.4 方案 D — 不动,接受现状

**思路**:保持现有 pipeline,继续优化 keywords / matcher / GT。

| 维度 | 评估 |
|---|---|
| 必要覆盖 | ❌ 不解决 #08 #59 #60 |
| ROI | ✅ 现有 P0-P3 工作已经把 FULL 从 27 → 46,边际收益递减 |
| 用户体验 | ⚠️ 用户拍摄大图招牌会看到 "未识别文字" 的低完整度报告 |
| 长期 | 待 PP-OCRv7 / 多尺度训练 / VLM 模型路线(ice_vision profile) |

**结论**:**✅ 当前推荐**。理由见 §3。

## §3 推荐 — 不动,接受现状

### §3.1 ROI 不划算

- 3 张 fixture MISS(全 65 中)→ 完整度 +4.6pp(从 70.8% → ~75%)。用户场景:拍大图广告招牌 → 部分文字被识别 → hit 卡显示部分违规。
- 现有 ROI 更高的方向:
  - PP-OCRv7 升级(2026-Q4 发布,自带 multi-scale training)→ 等官方
  - ice_vision profile 走 VLM 路线 → 自然支持长图 / 任意分辨率
  - GT 修正 + audit 沟通 — 见 [2026-08-29-keyword-expansion-2nd-verify.md §3](../smoke/2026-08-29-keyword-expansion-2nd-verify.md#3) #19

### §3.2 det 阈值放宽历史教训

[PaddleOcrEngine.kt:106-114](../../app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt#L106-L114) 已实证:**放宽 det 阈值会让 60+ 张 FULL fixture 退步**(noise 引入 → 不同 rule id 命中 → overlap 减少)。任何 image-side 单边放大(maxEdge / slicing)都会有类似副作用 — **新引入的命中 / 边界切字同样会破坏现有 FULL fixture**。

要 land 这种改动,必须:
1. 在 nova 6 上跑 66 张 fixture A/B(改动前 vs 后 FULL/PARTIAL/MISS 分布)
2. 确认新触达的 fixture 不是 "image-side 引入的 FP" 而是 "image-side 真实漏掉的"
3. 配套 #19 GT 修正、#08/59/60 fixture 重测

工作量 1-2 天,且结果有 50% 概率仍要回滚(类似 det 阈值放宽那次)。

### §3.3 VLM 路线自然覆盖

[CLAUDE.md §视觉/OCR 模型路线(2026-08 锁定)](../../CLAUDE.md) 已明确:Phase 1 走 OCR + 规则库,Phase 2+ 走 VLM(`ice_vision` profile)。VLM 模型对任意分辨率 + 长图 + 小字的支持是 intrinsic 的(long-context transformer + multi-scale vision encoder)。`#08 #59 #60` 在 VLM 路线下会自然消失,无需 image-side 切片工程。

短期在 OCR 路线下为 3 张 fixture 投入 1-2 天切片工程,长期看会被 VLM 路线覆盖 — ROI 极差。

## §4 后续路径(给后人)

| 时间窗 | 工作 |
|---|---|
| **2026-08 → 2026-Q4** | 不动 OCR 长图问题,继续优化 keywords / matcher / GT |
| **2026-Q4 PP-OCRv7 发布** | 评估 v7 在 nova 6 上 4 张大图 A/B,如官方 multi-scale 提升自然解决 #08 #59 #60 → 升级 |
| **2026-Q4 ice_vision profile** | 走 VLM 路线,OCR 长图问题自然消失 |
| **GT 沟通** | #19 GT 改 med_art6 + med_art7(留 audit 用户决定),与本评估正交 |

## §5 Hygiene

- 本评估基于 2026-08-29 E2E 数据(`dd62609` commit)+ PaddleOcrEngine.kt 实测源码阅读
- 不修改 PaddleOcrEngine / BitmapLoader — 现有代码是经过 2026-08-29 净负回滚后验证的最优帕累托
- 本 doc 不带 commit 引用(无代码改动)