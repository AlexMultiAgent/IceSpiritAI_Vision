# 2026-09-15 BLE 拍照传图修复真机验证

## 设备

- 华为 nova 6 (AGQV023313008161, SDK 35)
- 眼镜: Glasses-A88 V2.4.5 (MAC `xx:xx:xx:xx:60:09`, 已配对)
- APK: `ice_ocr_rules` profile debug build (versionCode=74, versionName=0.3.4, 含 4 个修复)
- JAVA_HOME: `/c/Users/37311/.gradle/jdks/jdk-17.0.18+8`

## 修复内容(本仓 commit 准备)

| # | 严重度 | 文件 | 改动 |
|---|---|---|---|
| 1 | 致命 | [GlassesPhotoCaptureRepository.kt](../../app/src/main/java/com/icespiritai/offline/glasses/GlassesPhotoCaptureRepository.kt) | `collectChunks` 不再把 **0x33 拍照命令 Response 当传图完成信号** |
| 2 | 高 | [BluetoothController.kt](../../app/src/main/java/com/icespiritai/offline/glasses/BluetoothController.kt) | FA12 CCCD 写入失败重试 2 次 (200 ms backoff) |
| 3 | 中 | [BluetoothController.kt](../../app/src/main/java/com/icespiritai/offline/glasses/BluetoothController.kt) | FFF2 → FA12 CCCD 写入间隔 80 ms |
| 4 | 低 | [BluetoothController.kt](../../app/src/main/java/com/icespiritai/offline/glasses/BluetoothController.kt) | MTU 协商前置 200 ms 延时 |

## 验证 (Fix #1 关键证据)

### 修复前症状

仓库侧把 `0x33 Response` 当 SUCCESS,在 `chunkStallMs=3.5s` 后触发 `fillGapsWith(0xFF)` + `forceCompleted=true`,
产出一张全 0xFF 字节的伪 JPEG,OCR 无法解析。用户看到"拍照成功"但识图永远失败。

### 修复后行为 (两次连续拍照,华为 nova 6 + Glasses-A88 V2.4.5)

| 时间 | 事件 |
|---|---|
| `15:59:51.417` | onServicesDiscovered status=0 services=5 (含 FFF0 + FA10) |
| `15:59:52.616` | Stage 1: SendingCommand — writing 0x33 to FFF1 |
| `15:59:53.206` | 0x33 writeFff0 returned: true |
| `15:59:53.253` | 0x51 START (warm-up, 8 字节, 无 fileSize) → **正确循环等待下一条** |
| `15:59:55.072` | 0x51 START (real, 12 字节, fileSize=19907) → accept |
| `15:59:55.073` | collectChunks entered (totalSize=19907) |
| `15:59:55.110` | chunk #1 offset=960 size=240 |
| `15:59:55.114` | chunk #2 offset=1200 size=240 |
| `15:59:55.121` | chunk #3 offset=1440 size=240 |
| **`15:59:55.356`** | **`FFF2 notify size=8 raw=55aa003302010000` — 0x33 Response 到达** |
| | → **✅ 修复生效:此帧不再触发 statusFlag=SUCCESS / force-complete** |
| `15:59:55.508` | chunk #20 offset=11280 size=240 |
| `15:59:55.855` | FA12 notify size=231 (unusual size — 末块或分裂块) |
| `16:00:03.655` | 0x51 FAILED (55aa035103010003) — 固件主动放弃传图 |
| `16:00:06.366` | 0x51 FAILED during transfer → 仓库 fail("眼镜报告传图失败") |

第二次尝试 (`16:02`) 同样模式 — 0x33 Response 在 `16:02:29.249` 到达,**没有**触发 force-complete,后续块正常流。

### 修复前后对比

| 阶段 | 修复前 | 修复后 |
|---|---|---|
| 0x33 Response 到达 | `statusFlag = SUCCESS`(错误) | 静默忽略 |
| 3.5 s 后 | `fillGapsWith(0xFF)` + `forceCompleted=true` | 继续等 FA12 块 |
| JPEG 数据 | 全 0xFF (~19907 字节伪图) | 仅收齐的 chunk,缺块保留 |
| UI 反馈 | "拍照成功"(误导) → OCR 失败 | "拍照失败:眼镜报告传图失败"(诚实诊断) |

## 修复 #2-#4 验证

- **Fix #2 (FA12 CCCD retry):** 两次尝试 FA12 CCCD 写入均首次成功,未触发重试。
  重试逻辑在 `BluetoothController.handleFa12CccdWrite` 内,仅 status≠GATT_SUCCESS 且 retry < 2 时激活,
  与 zhang OEM 行为 1:1 对齐。
- **Fix #3 (FFF2 → FA12 80 ms gap):** `enableFa12Notify()` 入口处 `delay(80L)`,在仓库 `ensureConnected`
  流水线里始终紧跟 `enableFff0Notify()`。logcat 未单独标 log(原 `enableNotify` 函数本身无 log),但
  代码路径与 OEM `postDelayed(..., 80)` 一致。
- **Fix #4 (MTU 200 ms 前置延时):** `requestMtu()` 入口处 `delay(200L)`,STATE_CONNECTED 后 200 ms
  再发起 MTU 协商。`onMtuChanged` 回调无独立 log,但 MTU 协商在 `15:59:51.417` 服务发现前完成
  (服务发现回调能跑说明 MTU 已就绪)。

## 已知遗留 (V2.4.5 固件限制,非本次修复范围)

1. ~~**固件丢块 + op2 重传通道坏**~~ — **已被下文「第二轮修复」推翻**:丢块是 App 侧订阅模型造成的,不是固件。
2. **HIGH / BALANCED 优先级** — 当前 capture 入口 + finally 都是 HIGH(CLAUDE.md 注释说明此为对齐
   zhang 设计选择)。spec §3.2 推荐传图后恢复 BALANCED,本次未改。
3. **`GlassesDeviceTest` 2 个失败** — pre-existing:`NAME_PREFIX = "Glass-D15"` 与生产代码 `"Glasses-A"`
   (commit `552a8a7` 放宽)的 drift,与本次修复无关。

---

## 第二轮修复 — FA12 丢块的真正根因 (同日,同一个 logcat)

上一节把「~8 s 后 `0x51 FAILED`」归给固件,是错的。重新读同一份 logcat 后根因在 **App 侧的订阅模型**。

### 证据

| 现象 | 含义 |
|---|---|
| `chunk #1 offset=960` / 第二次 `offset=720` | 文件**头部块永久缺失** → `filled=0/19907` 从头到尾不变 |
| `chunk #20 offset=11280` | App 数到第 20 块时固件已发到第 47 块 → **约 58 % 没收到** |
| `FA12 notify size=231` | `19907 − 82×240 = 227` + 4 B offset 头 = 231 → 这是**末块**。固件在 `55.072→55.855` 的 **0.78 s 内发完了全部 83 块** |
| `tryEmit=true` 且**从未**出现 `FA12 notify DROPPED` | 发射端自认为全部成功 |
| 全程**没有**任何补发/停包日志 | 恢复通道根本不存在(`maxResendRounds` 是死变量) |

固件 0.78 s 发完 → App 只收到约 4 成 → 缺口永远补不上 → App 不写 FA11 `0x03` → 固件等到超时回 `0x51 FAILED`。
**块是 App 丢的,不是射频丢的,也不是固件丢的。**

### 根因

`GlassesPhotoCaptureRepository.collectChunks` 每次迭代用

```kotlin
withTimeoutOrNull(chunkStallMs) { bluetoothController.fa12Notifications.first() }
```

读 FA12。`SharedFlow.first()` 的语义是**订阅 → 取一个值 → 退订**,而 `_fa12Notify` 是 `replay = 0` 的
`MutableSharedFlow`:**没有订阅者时 `tryEmit` 直接把值丢掉,但仍然返回 `true`**(所以日志看不出任何异常,
`DROPPED` 分支永远不会触发)。于是只有「恰好落在 `first()` 挂起期间」的块能被收到:

1. `0x51 START` → 真正进入收集循环之间(V2.4.5 两次 START 相隔约 1.8 s)的块全丢 → **头部空洞**;
2. 处理单个块的窗口内(解析 → `addChunk` → 发 `_state` → 打日志 → 重新订阅)到达的块全丢 → **中部空洞**。

头部空洞是致命的:`GlassesPhotoStream.isComplete` 判的是连续前缀,前缀卡在 0 就永远不完,
`assemble()` / `crc32()` 抛异常被 `catch (_: Throwable)` 吞掉 → FA11 `0x03` 永远写不出去。

对照 OEM 参考实现(`docs/glasses/zhang/.../bluetooth/BluetoothController.kt:1124-1160`):它在
`onCharacteristicChanged` 里**同步**重组,并把 START 之前的块存进 `aiPhotoEarlyChunks`、START 到达后回填
(即 spec §2.3 Step 4)。本仓把原始块经 SharedFlow 跳给协程消费者,又没有早块缓冲,所以两头都漏。

### 本轮改动

| # | 严重度 | 文件 | 改动 |
|---|---|---|---|
| 5 | **致命** | 新增 `GlassesReceiveTap.kt` + `GlassesPhotoCaptureRepository.kt` | `ReceiveTap`:整个会话**只订阅一次**,重投进 `Channel(UNLIMITED)`;在写 `0x33` **之前**开启,`finally` 关闭。取代 `first()` 逐块重订阅,并天然充当 `aiPhotoEarlyChunks` |
| 6 | 高 | `GlassesPhotoCaptureRepository.kt` | 停包 → FA11 `0x02` 补洞恢复(spec §2.3 Step 6:3.5 s 停包判定 / 每轮 2.5 s / ≤24 轮),超轮次直接失败并给出准确文案;`maxResendRounds` 不再是死变量 |
| 7 | 中 | `GlassesPhotoStream.kt` | `grownTo()` — 超过声明 `file_size` 时**保留**已收字节。旧的 `GlassesPhotoStream(newSize)` 会把整段前缀清空,而修复 #5 之后前缀必然已在手,该路径由「罕见」变成「必踩」 |
| 8 | 中 | `GlassesPhotoCaptureRepository.kt` | 状态观察者泄漏:旧代码在 `return true/false` 提前退出时跳过了 `statusObserverJob.cancel()`,该协程带着 `collect { }` 永久挂在外层 `SupervisorJob` 上。改由 tap 统一投递后不再存在 |
| 9 | 低 | `BluetoothController.kt` | FA12 发射缓冲 256 → 512(按整图块数留量);日志改打 `subscribers=N`,`tryEmit=true` + `subscribers=0` 现在会明确记为 `FA12 notify LOST`,这类丢失不再隐形 |
| 10 | 低 | `GlassesPhotoProtocol.kt` | `isCaptureAckSuccess` 的 KDoc 原本还在论证「0x33 ack 是合适的存活哨兵」——正是修复 #1 刚删掉的误用。改为明确禁止把它当传图完成信号 |

顺带:`collectChunks` 每轮先把 inbox 里**已到**的块花掉,再考虑 `0x51 SUCCESS` 的 0xFF 补洞 ——
避免拿手里已有的真数据去填假数据。

### 单测

`./gradlew :app:testDebugUnitTest --tests "com.icespiritai.offline.glasses.*"`(注意:单元测试必须用**默认
`shell` profile**,带 `-PmodelProfile=ice_ocr_rules` 会因 `FakeOcrEngine` 在 `src/shell/java` 而编译失败)

- 新增 `GlassesReceiveTapTest` 7 项:含反证 `replayZeroFlowDropsEmissionsThatHaveNoSubscriberYet`
  (`tryEmit` 成功但值已消失),以及「消费者忙时到达的块不丢」「订阅在调用返回时已生效」。
- `GlassesPhotoStreamTest` 16 → 21 项(`grownTo` 保留字节 / 保留覆盖位图 / 跨旧上界的空洞仍被正确上报 / 非增大抛异常)。
- 72 项中仅 `GlassesDeviceTest` 2 项失败,即上表 #3 的 pre-existing drift。
- 全量 `:app:testDebugUnitTest`:1020 项 / 5 失败 / 2 skipped。除上述 2 项外,另 3 项
  (`AdSignageTextFixtureRegressionTest`、`ChangelogScreenTest`、`UpdateRepositoryStallTest.markCancelled`)
  与 BLE 无关且为 pre-existing —— 前两项单独运行仍失败,第三项只在 full-suite 下失败,
  `app/src/main/assets/user-changelog.md` 已记录其在 baseline `f7a8d47` 复现。

### 待真机确认

本轮**未做**真机验证(本次会话无设备接入,`adb devices` 为空)。下次接机复测时看这几点即可判断成败:

1. `chunk #1 offset=0` 且 `filled` 随块数**递增**(不再是 `0/19907`);
2. 出现 `collectChunks complete: N/N bytes covered by M chunks in Xms (resends=0)`,且 M ≈ `fileSize/240`;
3. 日志里**不再有** `FA12 notify LOST`;若仍有,说明是真射频丢块,此时应看到 `FA11 op2 #k from <offset>` 并在 1-2 轮内补齐;
4. 传图耗时预期 **< 1 s**(固件 0.78 s 就发完了,瓶颈一直在 App),spec §1.2 的 1.4 s 目标大概率一并达成。


## 对齐官方 App — `docs/glasses/app.apk.1.apk`

用户指出这个 APK 是能正常同步眼镜图片的，于是反编译对照。方法：`androguard` 4.1.4 解 DEX（无调试噪声：`loguru.remove()`），
包名 `com.deepvision_tek.glass_front` versionName **3.1.00 / versionCode 3100**，**未混淆**（`BluetoothController$maybeRetryAiPhotoHighOnFirstChunk`
这类源码方法名原样保留），所以读的是真实实现而不是猜。关键类的 `bluetooth/BluetoothController` 里，文档提到的 16 个方法
（`handleAiPhotoFa12Packet` / `beginAiPhotoReceive` / `maybeFinishOrRetransmitAiPhoto` / `startAiPhotoStallWatchdog` /
`awaitAiPhotoIntervalFast` / `boostAiPhotoConnectionPriority` / `emitAiPhotoComplete` / `writePhotoCtrl` …）**全部存在** ——
说明 `docs/glasses/` 两份文档描述的就是这个官方 App。脚本与反汇编产物在 `.qwen/tmp/`（临时，不入库）。

### 官方数值 vs 本轮实现（逐条按指令号核对，非推测）

| 项 | 官方（指令索引为证） | 本轮 | 结论 |
|---|---|---|---|
| 停包判定 | `startAiPhotoStallWatchdog$1` #4 `const-wide/16 3500` → `delay(3500)` | `chunkStallMs=3500` | ✅ 一致 |
| 补发单轮等待 | 同 lambda #266 `const-wide/16 2500` → `withTimeoutOrNull(2500)` | `resendWaitMs=2500` | ✅ 一致 |
| 最大补发轮次 | #164 `const/16 24` | `maxResendRounds=24` | ✅ 一致 |
| 补发偏移 | #14/#198 `BitSet.nextClearBit(0)` | `firstMissingRange().first` | ✅ 等价 |
| 完成判定 | `nextClearBit(0) >= totalSize`；收齐后 #70-73 `CRC32`，#104 **先** `emitAiPhotoComplete`，#134 再 `schedulePhotoCtrlWrite(op3,"complete_crc")` | 先 `assemble()` 落盘 → 后写 `0x03` | ✅ 本地先出图，一致 |
| 首块 | #7-11 `aiPhotoFa12FirstSeen` → `maybeRetryAiPhotoHighOnFirstChunk()`（日志 `ai_photo_fa12_still_40ms`，一次性 flag） | 未实现 | ⚠️ 见下 |
| 早块缓冲 | `aiPhotoEarlyChunks`，`beginAiPhotoReceive` 里回填（#50-73） | `ReceiveTap` inbox（订阅早于 `0x33`） | ✅ 等价 |
| `file_size` 上限 | `beginAiPhotoReceive` #2 `const/high16 2097152` | 新增 `MAX_AI_PHOTO_BYTES = 2 MiB` | ➕ **本轮采纳** |
| 放弃会话 | 超时/无信号路径 #347/#390 `const/4 4` → `writePhotoCtrl(0x04)`；`cancelAiPhotoBleTransfer` 同理 | **原本全仓没人调 `buildFa11Cancel()`** | ➕ **本轮采纳**（fix #11） |
| 零块早停 | #324 `AI_PHOTO_RETRANS_ABORT offset= pkts0=` + #342「未收到图片分片数据(FA12)」+ #345 `emitAiPhotoFailed` | 新增 `FA12_NO_SIGNAL_ABORT_ROUNDS = 3` | ➕ **本轮采纳**（fix #12） |
| 优先级 | 进入 `prewarmAiPhotoBlePriority`/`boostAiPhotoConnectionPriority` → `requestGattConnectionPriority(1=HIGH)`（失败 200 ms 重试 `_retry200`）；**结束时 `restoreBlePriorityAfterAiPhoto` → `requestGattConnectionPriority(0=BALANCED)`**，`aiPhotoPriorityBalancedRestored` 幂等守卫；会话中 `requestBleConnectionPriority` 拒绝外部改优先级 | 进入 HIGH；**结束改回 BALANCED**（fix #13）；外部锁未做 | ➕ 采纳一半，见下 |

> 之前代码注释里「zhang 全程 HIGH 不恢复 BALANCED」这句话本身没写错（zhang 确实只在 `:800` 调过一次 HIGH），
> 但**官方能用的 App 是恢复的**，spec §3.2/§2.4 也要求恢复。两处权威一致，故按官方改。

### 本轮据官方对齐新增

| # | 改动 | 位置 |
|---|---|---|
| 11 | App 侧每次放弃会话（停包超轮次 / 硬超时 / 零块早停 / 用户取消）都补发 FA11 `0x04`。缺它时眼镜会继续往一个没人收的会话里推 FA12，并占着自身拍照状态 —— 下一次拍照更容易卡住 | `GlassesFa12Collector.abandon()`、`GlassesPhotoCaptureRepository.cancel()` |
| 12 | 一块都没到时 3 轮即止并给准确文案（对齐 `AI_PHOTO_RETRANS_ABORT pkts0`），不再空耗 24×2.5 s≈60 s | `FA12_NO_SIGNAL_ABORT_ROUNDS` |
| 13 | 会话结束恢复 `CONNECTION_PRIORITY_BALANCED`（原来 `finally` 里再调一次 HIGH，与入口自相矛盾） | `capture()` 的 `finally` |
| 14 | `file_size` 只接受 `1..2 MiB` 再分配（`GlassesPhotoStream` 按 `file_size` 直接分配，伪造的 u32 会撑到 4 GB） | `runStages` Stage 2 |
| 15 | 抽出 `GlassesFa12Collector.kt`（`collectFa12Chunks` + `Fa12Collection` + `StatusFrames`），把「图到底有没有收全」这段决策与 Context/GATT 解耦，用模拟固件跑通 | 新文件 + `GlassesFa12CollectorTest` 13 项 |

### 明确未采纳（有意为之，非疏漏）

1. **不在 `onCharacteristicChanged` 里同步重组**（官方做法）。本仓把块经单次长订阅 tap 投进 `Channel(UNLIMITED)`，
   tap 用 `Dispatchers.Unconfined` 在 binder 线程内联投递，块在 `tryEmit` 返回前就已入队 —— 丢失面与官方等价，
   且不改官方 App 的类结构。真要再省一跳可以把重组搬进 `BluetoothController`，但那是一次没有验证手段可支撑的重构。
2. **首块仍慢时补调一次 HIGH**（官方 `maybeRetryAiPhotoHighOnFirstChunk`）与 **会话中锁死外部优先级**：
   纯提速/防干扰，本仓当前唯一调用 `requestPriority` 的就是拍照入口，没有第二个调用方需要锁；且这两条在无设备的条件下无法验证，留待接机时随真机日志一起做。
3. **25 s + 宽限 15 s 的 App 端总超时**（官方 `PHOTO_BLE_APP_TIMEOUT_MS`）：本仓仍是 90 s 业务超时。零块早停（#12）已把最常见卡死的等待压到 ~11 s，
   总超时对齐会改变用户可见的重试时机，等真机数据再定。
4. `aiPhotoRetransmitRequested`（官方用来记「这个 offset 已经要过了」）：官方每轮同样只按 `nextClearBit(0)` 要第一个洞，该 Set 主要服务日志/连击判定，行为等价，未搬。

### 真机日志锚点（对齐后可直接对照官方语义）

```
GlassesCapture: FA12 notify LOST …              # 期望：不再出现
GlassesCapture: chunk #1 offset=0 …
GlassesCapture: collectChunks complete: N/N bytes covered by M chunks in Xms (resends=0)
GlassesCapture: FA12 silent — FA11 op2 #1 from <hole>
GlassesCapture: abandoning transfer: …          # 之后应看到下一条
BluetoothController: FA11 write size=5 raw=02…  /  raw=03…  /  取消时 raw=04
GlassesCapture: 0x51 FAILED during transfer …   # 此时不应出现 raw=04（眼镜已自行结束）
```

## Build / Install / Run 命令

```bash
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
cd /d/GitHub/IceSpiritAI_Vision

# Build
./gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules

# Install
adb -s AGQV023313008161 install -r \
    app/build/outputs/apk/debug/app-debug.apk

# Launch
adb -s AGQV023313008161 shell am start -n \
    com.icespiritai.vision/com.icespiritai.offline.IceSpiritVisionActivity

# Tap glasses capture button (CaptureBar 中心按钮)
adb -s AGQV023313008161 shell input tap 540 2286

# Capture logcat (must be running BEFORE the tap per CLAUDE.md §Instrumented test)
adb -s AGQV023313008161 logcat -c
adb -s AGQV023313008161 logcat -v time \
    -s "GlassesCapture:V" "BluetoothController:V" \
    "AndroidRuntime:E" "System.err:W" \
    > docs/smoke/2026-09-15-ble-fix-verify/logcat.txt 2>&1
```

完整 logcat 见本地 `logcat.txt`（同目录，未入库 —— 本仓 smoke 记录按惯例只存 `.md`）。
