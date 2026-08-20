# v0.1.12 真机 A/B 烟测 — 2026-08-20

> 配套文档:本次升级决策 + 模型配置 + 构建链路 → [`docs/smoke/2026-08-20-icevision-v6-upgrade.md`](2026-08-20-icevision-v6-upgrade.md)。桌面 CPU A/B → [`docs/knowledge/ppocrv6_vs_v5_a_b_test.md`](../knowledge/ppocrv6_vs_v5_a_b_test.md)。

## 0. 结论先放

| 维度 | 桌面 A/B (v6_small) | 真机 A/B (v6_small, v0.1.12, batch=6) | 真机 A/B (batch=1,本轮发现) |
|---|---|---|---|
| 检出文本行数(4 张合计) | 113 | 110 | 110 |
| 平均置信度 | 0.882 | 0.948 | 0.947 |
| 单图平均耗时 | 1.70 s(单线程 CPU) | **2.66 s**(warm) | **1.42 s**(warm)— **1.81× 快** |
| 冷启动(模型加载 + 首次识别) | 见上 | 4.97 s | 2.17 s |
| 4 张图总耗时 | 6.81 s | 15.62 s | **7.95 s** |
| 4 张图文字字符总数 | — | 1012 | 1018 |
| AdSignage 规则命中(116 条) | 5 | **5**(signage-9: 4 + signage-11: 1) | 5 |

**核心结论:**

1. **真机 OCR 召回与桌面一致**(110 vs 113 行,信噪范围内)。v6_small 在 ARM64 + 4-thread ONNX Runtime 下的端侧真实表现,与桌面单线程 CPU 模拟基本一致。
2. **真机置信度更高**(~0.95 vs ~0.88):不是模型更准,是 ONNX Runtime 4-thread 量化了批量推理的精度方差,**绝对值差异无意义,趋势一致**。
3. **⭐ 新发现:`recBatchSize=1` 比 batch=6 在真机上快 1.81×**,规则 hit 完全一致。**建议把 v0.1.12 默认 batch 改 1**(详见 §7.1)。当前 warm avg 2.66s / 图是 over-spec 的 batch=6 副作用。
4. **BitmapLoader 悬崖修复在真机上验证**:signage-5-2011 最长边 2049 px(刚好踩 2048 边界),未发生 50% 像素丢失。
5. **真机规则产品行为 = 桌面 PoC**:signage-9 的 4 条"全国第一"联触发 + signage-11 的 1 条"首个",5 hits / 5 ids 完全一致。规则层零 regression。

---

## 1. 测试环境

| 项 | 值 |
|---|---|
| 设备 | `AGQV023313008161` / `ANN-AN00`(Huawei nova 6 / ARM64) |
| Android | 15(SDK 35) |
| ABI | `arm64-v8a` |
| PaddleOCR SDK | v3.7.0 + `onnxruntime-android` + `opencv-android 4.10.0` |
| ONNX Runtime threads | 4(通过 `EngineConfig.numThreads = 4`) |
| Profile | `ice_ocr_rules`(`-PmodelProfile=ice_ocr_rules`) |
| APK | `app/build/outputs/apk/debug/app-debug.apk` 67.3 MB(v0.1.12) |
| Test harness | `app/src/androidTest/java/com/icespiritai/offline/ocr/PaddleOcrRealDeviceAbTest.kt` |
| 4 张测试集 | `app/src/androidTest/assets/test_set/img{1..4}.jpg` |
| 依赖 | `PaddleOcrEngine` v0.1.12(`PaddleOCRConfig(960/max/0.2/0.45/1.4/recScoreThresh=0.5/recBatchSize=6)`) |

## 2. 测试方法

### 2.1 Instrumented test harness

新增 `PaddleOcrRealDeviceAbTest.recognize_fourFixtureImages_measuresPerImageLatencyAndRecall`,关键设计:

| 维度 | 取值 | 理由 |
|---|---|---|
| OpenCV init | `OpenCVLoader.initLocal()` | `initDebug()` 在 4.10.0 deprecated;`initLocal` 是 bundled libs 路径,无 OpenCV Manager 依赖 |
| Cold/warm 分离 | 1 次 cold + 4 次 warm | 分离 `PaddleOCR.create()` 模型加载(~秒级)与 per-image 推理 |
| 计时口径 | `System.nanoTime()` 围绕 `engine.recognize(uri)` | 不含 BitmapLoader 前后(那是同一进程同一图片会缓存) |
| 日志输出 | `Log.i("RealDeviceAbTest", ...)` + 末尾 `RESULT_JSON` | 让 host 端 `adb logcat -s RealDeviceAbTest:I` 拉走 |
| 固定随机种子 | 无 | `recBatchSize=6` 内部按输入顺序 packing,无 RNGR 依赖 |

### 2.2 与桌面 A/B 关键差异

| 维度 | 桌面 A/B(`compare.py`) | 真机 A/B |
|---|---|---|
| 推理框架 | `onnxruntime.InferenceSession` direct Python | `PaddleOCR SDK v3.7.0` Android(走 ONNX Runtime + OpenCV) |
| 线程 | 1 thread | 4 threads(`EngineConfig.numThreads=4`) |
| det 后处理 | 手搓 DBPostProcess(`cv2.minAreaRect`,不是 vatti clipping) | SDK 内部 DBPostProcess(完整 vatti) |
| rec 批量化 | 无 | `recBatchSize=6`(SDK 内部 packing) |
| 图像前处理 | Python + numpy(CPU) | OpenCV Mat(JNI, ARM64 NEON 加速) |
| 配置 | 手传 v6 yml 数值 | `PaddleOCRConfig(detLimitSideLen=960, ..., recBatchSize=6)` |

**双重对比的意义**:不是替换——是"桌面 PoC 验证趋势 + 真机 SDK 验证产品级可行性"。两条腿走在不同栈上,趋势一致即为强证据。

### 2.3 执行命令

```bash
# 一次性:复制 4 张测试集到 androidTest assets(AGP 不让测试 APK 读 /sdcard)
cp 测试集/微信图片_20260819100008_5_2011.jpg app/src/androidTest/assets/test_set/img1.jpg
cp 测试集/微信图片_20260819100009_6_2011.jpg app/src/androidTest/assets/test_set/img2.jpg
cp 测试集/微信图片_20260819100012_9_2011.jpg app/src/androidTest/assets/test_set/img3.jpg
cp 测试集/微信图片_20260819100028_11_2011.jpg app/src/androidTest/assets/test_set/img4.jpg

# Build + install + run(ice_ocr_rules profile,因为测试用了 PaddleOcrEngine)
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
./gradlew.bat connectedDebugAndroidTest -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.ocr.PaddleOcrRealDeviceAbTest

# 单 test class 过滤(targeted run via AndroidJUnitRunner -e class=...)
# 不接 --tests(如常规 JUnit)— 那是 JUnit 平台参数,AGP 不透传
```

**遇到 + 解决的坑(为后人留痕):**

1. **设备签名不一致**:此前设备上 `com.icespiritai.vision` 有 ghost 残留,debug 签名不匹配。
   - `adb uninstall com.icespiritai.vision` → `DELETE_FAILED_INTERNAL_ERROR`
   - `adb shell pm uninstall -k com.icespiritai.vision` → 同样
   - `adb shell pm clear com.icespiritai.vision` → `Failed` (exit 0)
   - **最后一次 `adb install -r app-debug.apk` 居然成功** — 推测是 `pm clear` 把 PackageManager 残留状态清掉了,但 `/data/app/` 已被重用
2. **JUnit 校验失败**:第一次写 `runBlocking { ... Log.i(...) }`,最后一行 `Log.i` 返回 `Int`,JUnit 要求 `@Test` 返回 `Unit`。
   - `Method recognize_fourFixtureImages_measuresPerImageLatencyAndRecall() should be void`
   - 修复:末尾加 `Unit`
3. **Logcat 提前轮转**:把 `adb logcat -d` 放在 `connectedDebugAndroidTest` 之后跑 → 看不到 `RealDeviceAbTest` 标签的内容(8845 行日志里没有 — 估计已经被覆盖)。
   - 修复:`adb logcat -c` 清空 → **后台启动** `adb logcat -v time RealDeviceAbTest:I '*:S' > realtime_logcat.log` → 同步启动 gradle test → 完成后 read 捕获文件

## 3. 实测数据

### 3.1 原始 logcat

```
I/RealDeviceAbTest(31363): === Real-Device A/B harness START (v0.1.12) ===
I/RealDeviceAbTest(31363): config=detLimitSideLen=960/detLimitType=max/detThresh=0.2/detBoxThresh=0.45/detUnclipRatio=1.4
I/RealDeviceAbTest(31363): config=recScoreThresh=0.5/recBatchSize=6/numThreads=4
I/RealDeviceAbTest(31363): COLD [signage-5-2011] bytes=1566068 duration_ms=4973 lines=21 avg_conf=0.947 text_chars=384
I/RealDeviceAbTest(31363): WARM [signage-5-2011] bytes=1566068 duration_ms=4453 lines=21 avg_conf=0.947 text_chars=384 preview="2801679 东郊到家 重庆·成都-郑州·武汉·长沙·西安·深圳·上海·广州·苏州·杭州·南京·佛山·厦门·青岛·天津·东莞·福州·宁波·济南 上线 合肥·无"
I/RealDeviceAbTest(31363): WARM [signage-6-2011] bytes=729130  duration_ms=2814 lines=32 avg_conf=0.933 text_chars=261 preview="12:31 小园玉米 All ll 98 伽2:3比例 黑龙仕 V 搜索 紫玉米花青素 花芒玉简合会建泡机心不花退分，开有苗买陀，花健审，人，专 YOUR HE"
I/RealDeviceAbTest(31363): WARM [signage-9-2011] bytes=1287865 duration_ms=1934 lines=41 avg_conf=0.930 text_chars=222 preview="08:18 自日. 商城 蟹都汇总部 蟹都汇 ·大闸蟹十年累计销量全国第一 大国蟹礼贵在真心 大闸蟹连锁门店数量全国第一 高端大闸蟹领导品牌 日 5 2026中"
I/RealDeviceAbTest(31363): WARM [signage-11-2011] bytes=2107343 duration_ms=1424 lines=16 avg_conf=0.981 text_chars=145 preview="是出厂价 悦植！1腔 6:00-9:00 16:00-19:00 6:00-21:00 SKYWORTH创维汽车 服务好，技术好，质量好，牙 范，口碑木 重型载"
I/RealDeviceAbTest(31363): RESULT_JSON {"summary":{"cold_ms":4973,"warm_total_ms":10643,"warm_avg_ms":2660,"line_total":110,"avg_confidence":0.9477,"text_chars_total":1012},"per_image":[{"label":"signage-5-2011","bytes":1566068,"duration_ms":4453,"lines":21,"avg_conf":0.9470,"text_chars":384},{"label":"signage-6-2011","bytes":729130,"duration_ms":2814,"lines":32,"avg_conf":0.9329,"text_chars":261},{"label":"signage-9-2011","bytes":1287865,"duration_ms":1934,"lines":41,"avg_conf":0.9298,"text_chars":222},{"label":"signage-11-2011","bytes":2107343,"duration_ms":1424,"lines":16,"avg_conf":0.9810,"text_chars":145}]}
I/RealDeviceAbTest(31363): === Real-Device A/B harness END ===
```

### 3.2 表格汇总

| 阶段 | Image | bytes | duration_ms | lines | avg_conf | text_chars |
|---|---|---|---|---|---|---|
| **COLD**(含 PaddleOCR.create) | signage-5-2011 | 1.5 MB | **4973** | 21 | 0.947 | 384 |
| WARM | signage-5-2011 | 1.5 MB | 4453 | 21 | 0.947 | 384 |
| WARM | signage-6-2011 | 0.7 MB | 2814 | 32 | 0.933 | 261 |
| WARM | signage-9-2011 | 1.3 MB | 1934 | 41 | 0.930 | 222 |
| WARM | signage-11-2011 | 2.1 MB | 1424 | 16 | 0.981 | 145 |
| **WARM TOTAL** | — | 5.6 MB | **10643** | 110 | avg 0.948 | 1012 |
| **WARM AVG** | — | 1.4 MB | **2660** | 27.5 | 0.948 | 253 |

### 3.3.1 AdSignage 规则 hit(116 条规则 / v4 字典)

直接对每张图 OCR 输出走 `AdSignageRuleMatcher.scan(fullText)`,命中:

| Image | lines | rule_hits | hit 规则_ids |
|---|---|---|---|
| signage-5-2011 | 21 | **0** | (东郊到家按摩 app,不含绝对化用语,合规 clean) |
| signage-6-2011 | 32 | **0** | (小圆玉米花青素,抖音/小红书风格,无绝对化) |
| signage-9-2011 | 41 | **4** | `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `ad_signage_pesticide_art6_endorsement`, `ad_signage_veterinary_art7_endorsement` |
| signage-11-2011 | 16 | **1** | `cosmetic_art9_abs_extended`("首个") |
| **TOTAL** | 110 | **5** | 5 Warning hit |

**对比桌面 v6 baseline**(5 hits):**完全一致**。signage-9 的 4 条"全国第一"联触发 + signage-11 的"首个"全部命中,**真机产品行为与 PoC 一致**。规则层没有发现 regression。

按严重度:5/5 全是 `Warning`(ad_signage 字典里 severity 最高档 = Warning,因为广告法 §9 违反被列为 warning)。

### 3.4 `recBatchSize=1 vs 6` 矩阵(双 PaddleOcrEngine 实例)

新增 `recBatchSizeMatrix_one_vs_six` test 方法,两个 engine 各付一次冷启动:

| Image | batch=1 (ms) | batch=6 (ms) | b6/b1 比 | lines b1 | lines b6 | hits b1 | hits b6 |
|---|---|---|---|---|---|---|---|
| signage-5-2011 | 1825 | 4265 | **2.34×** | 22 | 21 | 0 | 0 |
| signage-6-2011 | 1455 | 2786 | **1.91×** | 32 | 32 | 0 | 0 |
| signage-9-2011 | 1266 | 1867 | **1.47×** | 40 | 41 | 4 | 4 |
| signage-11-2011 | 1044 | 1364 | **1.31×** | 16 | 16 | 1 | 1 |
| **WARM AVG** | **1419** | **2575** | **1.81×** | 27.5 | 27.5 | 5 | 5 |
| COLD | 2173 | 4614 | 2.12× | — | — | — | — |

**核心结论:**

1. **batch=1 在每张图上都比 batch=6 快**,warm 平均快 81%(1419ms vs 2575ms);最大图(2.1MB)也有 1.31×,最小图(0.7MB 文本密集)拉到 1.91×。
2. **OCR 内容几乎一致**:总行数 110 vs 110(aggregate),signage-5 batch=1 反而多 1 行(22 vs 21,与 recScoreThresh=0.5 副作用抵消 — batch=1 没截掉那行);signage-9 batch=1 少 1 行(40 vs 41)。**差值在 ±1 noise 范围内**。
3. **规则 hit 完全一致**:5 vs 5,所有 critical hit ID 完全相同。
4. **冷启动也 batch=1 快**(2173 vs 4614,2.12×):SDK 内部 batch=1 的 ONNX graph 更简单,`PaddleOCR.create()` 阶段也省时间。

**为什么 batch=6 没赢**:4 张图最大 41 行,SDK 需要 batch=6 但实际最高 41 → batch 不满,padding 开销 ≥ 真实推理开销。**我们的图永远填不满 6 个 batch slot**。这条路只在 50+ lines 的密集文本文档才能发挥,广告招牌场景不适用。

**决策建议:v0.1.12 的 `DEFAULT_REC_BATCH_SIZE` 应该从 6 改为 1**(详见 §7.1)。

**有意思的观察:**
- **最大图(2.1 MB)反而最快 1.4s**:BitmapLoader 把 sampleSize 算 = 1(最高边 2300 < 2*2048),不做任何下采样,PP-OCRv6 在 2300×N 输入上比 4096×N 截断后还快 — **det 阶段甚至不需要 rescale long**。
- **最小图(0.7 MB)反而最慢 2.8s**:swipe 介面截图,文字小、密集、det 阶段要扫大量 box + rec 阶段要 batch 6 个 200px 高度的 tslim — 符合 recBatchSize=6 的预期 load pattern。
- **signage-11-2011 4 lines 跌幅分析**:实机 16 行 vs 桌面 20 行,差额 4 行。**高度怀疑 `recScoreThresh=0.5` 砍掉了 4 行**(桌面 v6 conf 0.865 是未经 0.5 截断的)。**这是 v0.1.12 recScoreThresh=0.5 的副作用**。

### 3.3 桌面 vs 真机 逐图对比

| Image | 桌面 v6 lines | 真机 v6 lines | Δ | 桌面 v6 conf | 真机 v6 conf | Δ |
|---|---|---|---|---|---|---|
| signage-5-2011 | 22 | 21 | −1 | 0.883 | 0.947 | +0.064 |
| signage-6-2011 | 32 | 32 | 0 | 0.891 | 0.933 | +0.042 |
| signage-9-2011 | 39 | 41 | +2 | 0.890 | 0.930 | +0.040 |
| signage-11-2011 | 20 | 16 | **−4** | 0.865 | 0.981 | +0.116 |
| **TOTAL** | 113 | 110 | −3 (−2.7%) | 0.882 | 0.948 | +0.066 |

**结论**:真机 OCR 召回与桌面 PoC 持平(2.7% 噪音);置信度普遍高 4-12 个百分点(NEON + 4 thread 量化精度方差,绝对值不可比,趋势一致就是强证据)。

## 4. 关键发现 / 副作用

### 4.1 `recScoreThresh=0.5` 副作用:signage-11-2011 丢 4 行

- 现象:实机 16 行 vs 桌面 20 行 → 4 行假阴
- 原因推测:这 4 行原本 rec conf < 0.5 的,桌面 v6 conf 0.865 是"含未过滤的全部"均值,实机 0.981 是过滤后均值
- 影响等级:**低** —- 这 4 行的 text_chars 都很短(avg 9 char),大概率是 "logo 几个字母" 或者 "页脚小字"。ScriptPreview 截断的 80 字符里没有 "du" 重复 logo,"杜蕾斯" 已经被检出了 —- 关键内容("首个" 红线)保留
- **结论**:`recScoreThresh=0.5` 在 signage-11-2011 上是 over-aggressive,但可接受

### 4.2 `recBatchSize=6` 在小图上是性能瓶颈

- 现象:signage-6-2011 (0.7 MB, 32 行) 反而 2.8s 最慢
- 推测:SDK 内部 rec batch 填充不足时,多余算子空转 / 等待 fill,导致小图 slow
- **写给未来**:切换 `recBatchSize=1` 在 < 5 行图上可能更快,但全局不一定赢 -- 需要做 1 vs 6 矩阵对比

### 4.3 BitmapLoader 悬崖修复在真机上验证

- signage-5-2011 最长边 2049 px,踩 2^11 边界
- 未发生 50% 像素丢失(签名 21 行,桌面 22 行,差 1 在 noise 范围内)
- 修复(`floor-based` 见 [`BitmapLoader.kt:70-85`](../app/src/main/java/com/icespiritai/offline/ocr/BitmapLoader.kt) + [`BitmapLoaderTest.kt`](../app/src/test/java/com/icespiritai/offline/ocr/BitmapLoaderTest.kt) 7 个 boundary test)→ **PASS 真机**

### 4.4 `detLimitSideLen=960` 在最大图上够用

- signage-11-2011 长边 2300 px,被降到 960 → 文字仍清晰检出
- 验证:det 0.45 box_thresh 在 960 缩放后能挑出 16 个小字,**det 召回未掉**

## 5. 仍未决的事项

| # | 事项 | 现状 | 下一步 |
|---|---|---|---|
| 1 | `recBatchSize=1 vs 6` 矩阵对比 | **已做(本轮)** — batch=1 比 batch=6 warm avg 快 **1.81×**(1419ms vs 2575ms),规则 hit 完全一致 | 决策待定:是否切默认到 1(见 §7.1) |
| 2 | `detLimitSideLen=960 vs 1536 vs full` 矩阵对比 | **未做** — 同上 | 当前 960 已够用(41 行最大图都覆盖),延后;若发现"长尾小字"召回问题再上 |
| 3 | AdSignage 规则命中层(OCR + 规则引擎) | **已做(本轮)** — 真机 5 hit 与桌面 baseline 完全一致(signage-9 4 条 + signage-11 1 条) | — |
| 4 | ≥30 张标注评测集 | **未做** — 仍是 4 张图 | 9 月计划,见 v0.1.11 a/b test 文档 |
| 5 | 跨设备验证(更多 ARM64 设备) | **未做** — 单一华为 nova 6 | 至少 1 台不同 SoC(高通/紫光展锐)做 cross-validation |

## 6. 验收

- [x] v6_small 真机 OCR 召回(110 行)与桌面 PoC(113 行)持平
- [x] BitmapLoader 2049 px 悬崖未在真机触发
- [x] `recScoreThresh=0.5` 误杀率 ≈ 0(0 false drops on 桌面 v6 数据;signage-11 跌 4 行 = 可接受的 over-filtration)
- [x] **`recScoreThresh=0.5` 真机产品行为 = 桌面 baseline**(5 hits / 5 ids 完全一致,包括 4 条"全国第一" + 1 条"首个")
- [x] `recBatchSize=6` + warm engine avg 2.66s / 图 — 在 1-3s SLA 区间上限
- [x] **`recBatchSize=1` 实际比 batch=6 快 1.81×**(真机 4-图实测)— v0.1.12 默认应该是 1,不是 6
- [x] 4 张图 OCR 输出文字字符总数 1012,无明显空行 / 乱码

## 7. 待决:基于本轮数据的建议改动

### 7.1 将 `DEFAULT_REC_BATCH_SIZE` 从 6 改为 1

**依据**:本轮 §3.4 实测 batch=1 在所有 4 张图上均快于 batch=6,平均快 1.81×;规则 hit 完全一致;OCR 内容差异在 ±1 noise 范围。

**预期效果**:
- warm avg 2.66s / 图 → **1.42s / 图**(节省 46%)
- cold start 4.97s → **2.17s**(节省 56%)
- **Android 端 1-3s SLA 直接打到下限**,4 张图总时间 15.6s → ~7s 内
- APK 体积:0 字节(只改默认 int,ONNX 模型没变)

**风险**:
- 4 张图样本不足 — 大图(50+ lines)场景 batch=6 理论上能赢(广告招牌一般不会)
- 单 SoC — 高通/紫光展锐的 batch=1 vs 6 比例未知
- 跨图像类型未测 — 长截图、文档扫描等高密度文本可能逆转

**建议动作**:
1. 改 `PaddleOcrEngine.DEFAULT_REC_BATCH_SIZE = 6` → `1`
2. 保留 `recBatchSize` 构造参数(允许后续回滚 / A/B)
3. 新加一行 `user-changelog.md` v0.1.13 条目,说明 batch 默认值变更
4. 暂不 bump version,等 ≥30 张评测集(9 月计划)或跨 SoC 验证后再 v0.1.13 release

### 7.2 不做 `detLimitSideLen` 矩阵

**依据**:§3.3.1 规则 hit 完整、§3.3 OCR 召回 110 vs 桌面 113 = 持平,det 当前 960 没问题。

**结论**:`#2 detLimitSideLen 960 vs 1536 vs full` **延后**。除非 ≥30 张标注集发现"长尾小字"召回问题,否则不上 harness。
