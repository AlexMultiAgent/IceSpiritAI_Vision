# PP-OCRv6_small vs PP-OCRv5_mobile — 测试集 A/B 实测

**日期**: 2026-08-20
**测试集**: `D:\GitHub\IceSpiritAI_Vision\测试集\`(4 张实拍)
**环境**: Windows 11 + Python 3.12 + onnxruntime CPU(单线程)
**目的**: 量化 v6_small 比 v5_mobile 在端侧广告招牌场景下"提高多少"

---

## 结论先放

| 维度 | v5_mobile | v6_small | v6 vs v5 |
|---|---|---|---|
| 检出文本行数(4 张合计) | 101 | 113 | **+12%** |
| 平均置信度 | 0.837 | 0.882 | **+5.4%** |
| 单图平均耗时(3072-4096 px) | 1.88 s | 1.70 s | **−10%** |
| AdSignage 规则命中数(116 条规则) | 1 | 5 | **5×** |
| AdSignage 命中规则种类 | 1 | 5 | **5×** |

**核心结论**:
1. v6_small 在广告招牌域明显领先——检测更稳(中文行 +12%)、置信度更高(平均 +5%)、**关键违规用语识别完整**(蟹都汇"全国第一"v6 检出 v5 没检)。
2. 速度没变慢,v6_small 在多数图上更快,符合官方宣传(ONNX Runtime + OpenCV 路径)。
3. 评测集只有 4 张图、且都是双塔 single-image 路径,**置信度差异是趋势性的,不能直接换算成 N 个百分点的精度**;真正的提升要在 ≥30 张标注集上跑后才能给"X% 精度"。

---

## 测试方法

- **模型加载**: 两套都走 `onnxruntime.InferenceSession` + CPU,直接从 `inference.onnx` 推,不走 Paddle/PaddleX(paddlex 默认要 Paddle 格式,绕路且依赖重)。
- **图像前处理**: det 按各自 yml 的 `DetResizeForTest`(v5 = resize_long=960,v6 = limit_side=960)+ ImageNet mean/std 归一化;rec 按 `OCRReisizeNormImg.resize_norm_img`(BGR → /255 → −0.5 → /0.5,不是 imagenet 归一化)。
- **CTC 解码**: 走 PaddleOCR CTCLabelDecode 等价路径。字典构造 = `["blank"] + yml.character_dict + [" "]`(匹配 PaddleOCR 内部 init 顺序);blank 始终是 index 0。
- **规则匹配**: 把每张图的全部 OCR 行 join 后过 Aho-Corasick(532 个唯一归一化 keyword);规则库 `app/src/main/assets/rules/ad_signage_rules.json`(v4,116 条)。
- **完整脚本**: `D:\tmp\ocr_compare\compare.py` + `D:\tmp\ocr_compare\match_rules.py`,中间产物在同目录。

**已知小坑**(不影响 v5 vs v6 相对比较,但解读绝对数字时要心里有数):
- det 是手搓 DBPostProcess(简化版):binary → contour → box_thresh mean filter → cv2.minAreaRect + offset 做 unclip。**不是官方 PaddleOCR 完整 vatti clipping**,所以 box 形状偏矩形而不是严格四边形。影响：极少数四边形文案的 box 边界会偏离几像素,但不会漏大段。
- rec 单图 batch(没 batch 维度对齐),`max_wh_ratio` 用 `w/h`。批量化提速没做。
- ONNX 输出头只剩 CTC(NRTR head 在导出时丢弃)——这点 v5/v6 都一样。

---

## 分图明细

### 微信图片_20260819100008_5_2011.jpg(东郊到家 按摩 app 电梯广告)

| 指标 | v5 | v6 |
|---|---|---|
| det box 数 | 24 | — |
| OCR 行数 | 20 | 22 |
| avg_score | 0.799 | 0.883 |
| 总耗时 | 1.80 s | 1.15 s |
| 规则命中 | 0 | 0 |

**解读**: 此图含有 "按摩我选东郊"、"全国技师超9万人"、"153 7398 5377" 等,但**不含绝对化用语或禁用承诺**,所以 AdSignage 规则 0 命中正常——广告合规视角这张图是 clean 的。
v6 多检了 2 行(brand 副标题等),置信度比 v5 高 10 个百分点,快 36%。

### 微信图片_20260819100009_6_2011.jpg(小圆玉米 紫玉米花青素 短视频)

| 指标 | v5 | v6 |
|---|---|---|
| OCR 行数 | 30 | 32 |
| avg_score | 0.801 | 0.891 |
| 总耗时 | 1.63 s | 2.11 s |
| 规则命中 | 0 | 0 |

**解读**: 抖音/小红书风格视频截图,含 "YOUR HEALTHY DIET EXPERT"、各种 emoji hash tag,两个模型都识别出大量英文 + 数字;v6 略慢(模型稍大)。
AdSignage 0 命中——视频里没 "100%有效""根治" 这种强违规词。

### 微信图片_20260819100012_9_2011.jpg(蟹都汇商城页)⭐ 关键胜负手

| 指标 | v5 | v6 |
|---|---|---|
| OCR 行数 | 34 | 39 |
| avg_score | 0.858 | 0.889 |
| 总耗时 | 2.67 s | 2.27 s |
| 规则命中 | **0** | **4** |

**v6 独有的关键识别**(原文):
- `"大闸蟹十年累计销量全国第"` ← 完整覆盖 "全国第"
- `"大闸蟹连锁门店数量全国第一"` ← 完整覆盖 "全国第一"

**v5 的同一行被识别成**:
- `"大蟹年量全国谢"` ← "谢" 替代 "第",导致 "全国第*" 这个 keyword 没匹配上

**命中的 4 条规则**(v6 only):
- `ad_signage_art9_abs_top` — 广告法第 9 条 "顶级 / 最佳 / 第一" 禁用
- `ad_signage_art9_edu_abs` — 教育培训 "第一" 禁用(误命中,因为 "第一" 关键字同时存在)
- `ad_signage_pesticide_art6_endorsement` — 农药 "第一" 禁用(同上,误命中)
- `ad_signage_veterinary_art7_endorsement` — 兽药 "第一" 禁用(同上)

后三条误命中是 keyword "第一" 在多条规则里复用的副作用,不影响 "v6 找到了 v5 漏掉的关键违规" 这个结论。

**业务结论**: v5 在这张图上漏报了一个**实际会触发监管处罚**的绝对化用语;v6 检出。这是 4 张图里唯一明显的"v6 救场"案例。

### 微信图片_20260819100028_11_2011.jpg(durex 公交车身广告)

| 指标 | v5 | v6 |
|---|---|---|
| OCR 行数 | 17 | 20 |
| avg_score | 0.890 | 0.865 |
| 总耗时 | 1.42 s | 1.28 s |
| 规则命中 | 1 | 1 |

**共同识别**:
- `"经典红|杜蕾斯首个公益装"` ← 命中 `cosmetic_art9_abs_extended`(关键词 "首个")

**解读**: 两个模型都检出了 "首个"(绝对化用语),v6 还多检了 "杜蕾斯"、"durex" 重复出现,但 v6 这次置信度反而略低(0.865 vs 0.890)——这种"重复 logo"的图,v5 的精修反而更稳。**没有压倒性差距**。

---

## 时间成本(端侧最关心)

| 模型 | 模型文件大小 | 平均耗时(单图) | 主要瓶颈 |
|---|---|---|---|
| v5_mobile det | ~3.5 MB | det 0.23s | DBNet stride-32 padding |
| v5_mobile rec | ~12 MB(中文 NRTR) | rec 1.57s | 单图 48×T 串行 |
| v6_small det | ~4 MB | det 0.30s | 同上 |
| v6_small rec | ~16 MB | rec 1.20s | 同上 |

CPU 上 v6 整体不输 v5(rec 部分更快);**NNAPI / GPU 路径下数字会不一样**,这里没测。

---

## 综合结论 & 下一步

1. **不建议在 8 月底 release 之前换**:
   - 4 张图不构成评测集,差异在 ±10% 是 noise 范围;
   - v6_small 模型比 v5_mobile 大 ~5 MB(rec 部分),APK 体积会涨;
   - 当前 v5 已 shipped 且通过烟测,切换会重新走一轮 release pipeline。
2. **建议 9 月做"≥30 张标注评测集"**:
   - 标注格式:`{image, boxes: [...], transcripts: [...]}`;
   - 指标:detection F1 + recognition CER + 端到端 L1/L2 规则 hit 数;
   - 如果 v6 在端到端 L2 hit 上显著优于 v5(≥20% 相对),再发起升级 PR,附实测链接到本文件。
3. **当前手搓 pipeline 是 PoC,不能上线**:
   - DBPostProcess 用 `cv2.minAreaRect` 代替 vatti clipping,box 形状不够准;
   - rec 单图串行,实拍路径下要批量化;
   - 真上线还是要走 PaddleOCR 官方 SDK 或 paddlex,而不是 onnxruntime 直接拼。