# 官方眼镜 App BLE 传图实现研判

> 研究对象：`docs/glasses/app.apk.1.apk` —— `com.deepvision_tek.glass_front` **3.1.00 / versionCode 3100**
> 研究日期：2026-09-15 · 触发原因：用户确认「这个 App 能正常同步眼镜图片」，需要拿它当 ground truth 校准本仓实现
> 结论落地：commit `ef09f9e`（收块零丢失 + op2/op4/BALANCED 对齐）
> 设备侧对照：`docs/smoke/2026-09-15-ble-fix-verify/README.md`

---

## 0. 为什么值得写这份文档

同一份真机日志（nova 6 + Glasses-A88 **V2.4.5**）在三次会话里被读成三种结论：

| 时间 | 当时的结论 | 现在的判定 |
|---|---|---|
| `8ca05b7` | 「HIGH 会导致 35 % 丢块，改回 BALANCED」 | ❌ 丢块与优先级无关 |
| `329feec` #4 | 「固件 op2 重传通道坏：192 次 op2、0 次重传 → 删掉补发逻辑」 | ❌ 前提不成立（见 §5） |
| `329feec` 尾注 | 「OCR 看到 ~30 % 空洞是固件侧缺口，需固件+App 配套发版」 | ❌ 是 App 侧 `SharedFlow.first()` 反复重订阅 |

只读 `docs/glasses/*.md`（固件侧写的**需求文档**，不是官方 App 的实现说明）+ 读自己代码，会一直在错误前提是
否自洽。反编译一个**被确认可用的实现**才把这件事钉死。

---

## 1. 复现方法（含 androguard 4.1.4 的四个坑）

APK 未混淆：`BluetoothController$maybeRetryAiPhotoHighOnFirstChunk` 这类源码派生名原样保留，
协程 lambda 以 `Xxx$1` 内部类形式存在，`const val` 被编译期内联到**使用点**（所以 `<clinit>` 里查不到常量）。

```python
import zipfile; from loguru import logger; logger.remove()   # 否则 androguard DEBUG 会淹掉输出
from androguard.core.dex import DEX
z = zipfile.ZipFile(APK)                                     # classes.dex 21 MB + classes2.dex 11 MB
for n in ("classes.dex", "classes2.dex"):
    d = DEX(z.read(n))
    for c in d.get_classes():
        if c.get_name() != "Lcom/deepvision_tek/glass_front/bluetooth/BluetoothController;": continue
        for m in c.get_methods():
            for ins in m.get_instructions():
                print(ins.get_name(), ins.get_output())      # ← 必须两个都要
```

| 坑 | 症状 | 正确写法 |
|---|---|---|
| `get_output()` 不含操作码 | 打出来全是 `v0, v1, L…` 无法读 | `f"{ins.get_name()} {ins.get_output()}"` |
| `EncodedMethod` 无 `get_bc()` | `AttributeError` | 直接 `m.get_instructions()` |
| `EncodedMethod` 无 `get_proto/get_method`；`EncodedField` 无 `get_type`；`ClassDefItem` 无 `get_source_file` | 脚本反复中断 | 只用 `get_name()/get_access_flags_string()`，类型看描述符字符串 |
| 常量被内联 | `<clinit>` 只有 `$stable` | 到方法体（常在 `$1` lambda 内部类）里找 `const*` |

关键类分布：BLE 全在 **classes2.dex**；`bluetooth/` 下 376 个类，核心是
`bluetooth/BluetoothController`、`bluetooth/ConnectionAwareGattCallback`、`service/PhotoCaptureService`。
`docs/glasses/` 两份文档点名的 16 个方法（`handleAiPhotoFa12Packet` / `beginAiPhotoReceive` /
`maybeFinishOrRetransmitAiPhoto` / `startAiPhotoStallWatchdog` / `awaitAiPhotoIntervalFast` /
`boostAiPhotoConnectionPriority` / `emitAiPhotoComplete` / `writePhotoCtrl` / `cancelAiPhotoBleTransfer` …）
**全部存在** —— 说明那两份文档描述的对象就是这个 App。

---

## 2. 传图状态机（官方，方法级）

```
requestAiPhotoBleCapture(quality=80)
  ├─ ensurePhotoBleMtuReady()            → AI_PHOTO_MTU / AI_PHOTO_MTU_FORCE
  ├─ awaitPhotoBleChannelReady(8000)     → AI_PHOTO_CHANNEL ready= mtu= fa12=
  ├─ ensurePhotoDataNotifySubscribed()   → 串行 CCCD：FFF2 完成后延迟 80 ms 再订 FA12，失败重试 ≤2（200 ms）
  ├─ awaitAiPhotoIntervalFast(16)        → 每 40 ms 轮询 lastBleConnInterval，直到 <17
  ├─ prewarmAiPhotoBlePriority()         → requestGattConnectionPriority(HIGH, "ai_photo_prewarm_*")
  └─ writeCommand(0x33)                  → 之后 boostAiPhotoConnectionPriority(force=false)
0x51 START  ── handlePhotoStatus → beginAiPhotoReceive(fileSize)
              · 2 MiB 上限；分配 buffer + BitSet；startAiPhotoStallWatchdog()
              · aiPhotoEarlyChunks 回填 → AI_PHOTO_START_RX bytes= early= name=
FA12        ── handleAiPhotoFa12Packet（同步，synchronized(aiPhotoStateLock)）
              · if(!aiPhotoCmdSent) return; if(size<5) return
              · 首块 → maybeRetryAiPhotoHighOnFirstChunk()
              · 无 totalSize/buffer → 进 aiPhotoEarlyChunks；否则 applyAiPhotoChunkLocked → AI_PHOTO_FA12
收齐        ── maybeFinishOrRetransmitAiPhoto: nextClearBit(0)>=total
              · CRC32 → emitAiPhotoComplete（本地先出图，AI_PHOTO_COMPLETE）→ writePhotoCtrl(03|crc,"ai_photo_ble_crc")
              · aiPhotoAwaitingCrcConfirm 防重复
停包        ── startAiPhotoStallWatchdog: delay 3500 → AI_PHOTO_STALL_RETRY → writePhotoCtrl(02|firstHole,"stall_serial_N")
              → withTimeoutOrNull(2500){任何新包} → 无则 AI_PHOTO_RETRANS_TIMEOUT streak=N
              → streak/轮次到限且一块没到 → AI_PHOTO_RETRANS_ABORT + emitAiPhotoFailed("未收到图片分片数据(FA12)…") + 0x04
取消        ── cancelAiPhotoBleTransfer(reason) → AI_PHOTO_CANCEL → writePhotoCtrl(04, "cancel:"+reason)
收尾        ── restoreBlePriorityAfterAiPhoto() → requestGattConnectionPriority(BALANCED, …)
```

---

## 3. 实测数值（每条给指令索引，非文档转述）

| 参数 | 值 | 证据 |
|---|---|---|
| 快档连接间隔上限 | `interval ≤ 16`（20 ms） | `boostAiPhotoConnectionPriority` #6 `const/16 16`；`awaitAiPhotoIntervalFast` #16 `const/16 17` |
| 首块仍慢的判定 | `interval ≥ 17` 才补调 HIGH | `maybeRetryAiPhotoHighOnFirstChunk` #8 `const/16 17` + #12 reason `"ai_photo_fa12_still_40ms"`；`interval < 1`（未观测）直接返回 |
| interval 轮询步长 | 40 ms | `awaitAiPhotoIntervalFast` #60 `const-wide/16 40` |
| 通道就绪超时 | 8000 ms | `awaitPhotoBleChannelReady` #54 `const-wide/16 8000` |
| MTU 下限 / 目标 | **247 / 517** | `ensurePhotoBleMtuReady` #23 `const/16 247`、#152 `const/16 517` |
| MTU 重试节奏 | 4 次，退避 500/800/2000/5000 ms | 同方法 #207 `const/4 4`，#209/#138/#123/#162 `500/800/2000/5000` |
| 停包判定 | 3500 ms | `startAiPhotoStallWatchdog$1` #4 `const-wide/16 3500`、#371 同 |
| 补发单轮等待 | 2500 ms | 同 lambda #266 `const-wide/16 2500` |
| 补发最大轮次 | 24 | 同 lambda #164 `const/16 24` |
| 补发偏移 | `BitSet.nextClearBit(0)` | 同 lambda #117/#198 |
| FA11 写超时 | 2500 / 1500 ms；忙则 `recoverGattAfterWriteBusy` | `writePhotoCtrl` #50/#61 |
| 文件大小上限 | 2 MiB | `beginAiPhotoReceive` #2 `const/high16 2097152` |
| 落盘名 | `ai_ble_<n>.jpg` | 同方法 #0 `"ai_ble_"` #28 `".jpg"` |
| FA12 最小合法包长 | 5（4 B offset + ≥1 B 数据） | `handleAiPhotoFa12Packet` #4 `const/4 5` |
| FA11 载荷 | op2/op3 = 5 B；op4 = 1 B | `buildAiPhotoRetransmitPayload` #11 `const/4 5`、#13 `const/4 2` |
| 优先级常量 | HIGH=1 / BALANCED=0 / LOW_POWER=2 | `requestGattConnectionPriority` #31/#33/#38-42 |

### 官方日志 tag 词表（联调时按这个对，比读代码快）

```
AI_PHOTO_CHANNEL  AI_PHOTO_MTU  AI_PHOTO_MTU_FORCE  AI_PHOTO_START_RX  AI_PHOTO_FA12
AI_PHOTO_FULL     AI_PHOTO_COMPLETE                 AI_PHOTO_FA11      AI_PHOTO_FA11_REQ
AI_PHOTO_STALL_RETRY  AI_PHOTO_RETRANS_TIMEOUT  AI_PHOTO_RETRANS_ABORT
AI_PHOTO_CANCEL   AI_PHOTO_FAILED               BLE_CONN_PRIORITY
BLE_CONN_PRIORITY priority=HIGH ok=true reason=ai_photo_prewarm_overlay_open interval=32
```

---

## 4. 与本仓的对齐结论

### 已采纳（commit `ef09f9e`）

| # | 官方行为 | 本仓落地 |
|---|---|---|
| 1 | START 之前的块进 `aiPhotoEarlyChunks` 并回填 | `ReceiveTap`：订阅早于 `0x33`，块进 `Channel(UNLIMITED)`，START 后一次性回填（同一效果，且顺带修掉「消费途中丢块」） |
| 2 | stall 3500 / 单轮 2500 / 24 轮 / `nextClearBit(0)` | 四个参数逐一对齐（此前被 `329feec` 删成「被动收，不补发」） |
| 3 | 放弃会话写 FA11 `0x04` | `collectFa12Chunks` 内 `abandon()` + `repository.cancel()`；此前 `buildFa11Cancel()` **全仓零调用** |
| 4 | 零块时早停并给专门文案 | `FA12_NO_SIGNAL_ABORT_ROUNDS = 3` → 「未收到图片分片数据(FA12)」 |
| 5 | 收齐后**先**本地出图**再**写 `0x03` | 已是此顺序（stage 4 落盘 → 写 CRC） |
| 6 | `file_size` 上限 2 MiB 再分配 | `MAX_AI_PHOTO_BYTES` |
| 7 | 会话结束 `restoreBlePriorityAfterAiPhoto` → BALANCED | `capture()` 的 `finally` |
| 8 | 首块仍慢补调一次 HIGH（`interval ≥ 17`，未观测不猜） | `shouldReboostHighOnFirstFa12` + `boostPriorityIfFirstFa12StillSlow`，由 collector 保证一次/会话 |
| 9 | FFF2 → FA12 CCCD 串行 + 80 ms；FA12 CCCD 失败重试 2×200 ms | 上一轮 fixes #2/#3 已对齐（本次反汇编二次确认） |

### 有意不采纳

1. **在 `onCharacteristicChanged` 里同步重组**（官方写法）。本仓 tap 用 `Dispatchers.Unconfined`，块在 `tryEmit`
   返回前就已入队，丢失面等价；把重组搬进 `BluetoothController` 是一次无验证手段支撑的结构重写。
2. **会话中锁死外部优先级**（`requestBleConnectionPriority` 在 `aiPhotoBleActive && !balancedRestored` 时直接拒绝）。
   本仓唯一调用 `requestPriority` 的就是拍照入口，没有第二个调用方需要拦。
3. **App 端总超时 25 s + 宽限 15 s**（`PHOTO_BLE_APP_TIMEOUT_MS`）。本仓仍是 90 s 业务超时；零块早停已把最常见卡死
   压到约 11 s。改总超时会改变用户可见的重试时机，等真机数据再定。
4. **`aiPhotoRetransmitRequested` 集合**：官方每轮同样只按 `nextClearBit(0)` 要第一个洞，该集合主要服务日志与连击判定。

---

## 5. 被这份研究推翻的三个旧判断

1. **「固件丢 ~30 % 块」** —— 官方日志词表里根本没有「丢块」这件事的位置：它在回调里同步落 `BitSet`，
   不经任何会丢消息的中间层。本仓的丢失来自 `SharedFlow(replay=0) + 每次 first() 重订阅`。
   证据：同一份日志里固件在 `55.072→55.855` 的 **0.78 s** 内把 83 块全推完（`size=231` 正是 `19907−82×240` 的末块），
   而 App 只数到 20 块。
2. **「op2 重传通道坏」** —— 那 192 次 op2 发生在 GATT 写锁（`32ded3d`）之前，8 路 fanout 互相踩 `pendingCharWrite`，
   根本没保证上线；而且当时的收块方本身在丢块，无法区分「没重传」和「没收到」。
3. **「zhang 不恢复 BALANCED，所以我们也不恢复」** —— 这句对 zhang（`com.aiglass.zhangwen`，全文件只在 `:800` 调过一次
   HIGH）成立，但**可用的官方 App 是恢复的**，spec §3.2/§2.4 也要求恢复。两处权威一致，按官方改。

---

## 6. 仍未确认（需要真机或更深反汇编）

1. **首块重试的分支方向**是按 `if-gt/if-ge` 跳转 + reason 字符串（`ai_photo_fa12_still_40ms`）+ `boost` 里的 `16`
   阈值共同推定的「interval≥17 才补」，smali 的跳转偏移是按字节数而非指令数，未逐条人工走查。
2. **`onConnectionUpdated` 在本机是否真被派发** —— 它不在 compileSdk 桩里，本仓靠「子类声明同签名」这一隐藏 API
   惯用法接。官方有 `ConnectionAwareGattCallback` 同样接法。判据：跑一次拍照看
   `FA12 first block, interval=` 是否离开哨兵值 0。若一直是 0，首块补 HIGH 会**按设计保持惰性**。
3. **0x50 `aiPhotoRequestCmd` 与 0x33 `aiPhotoBleCmd` 的固件门控条件**：本仓 `GlassesPhotoProtocol` KDoc 记录 V2.4.5 对
   0x50 回 `err=0x01` 拒绝，官方主入口走 0x50 还是 0x33 未逐条走查（本仓继续 0x33，日志证明可用）。
4. **官方 `requestAiPhotoBleCapture` 里的 `prepareMediaCaptureCoexistence` / `ensureClassicBondForAcl`**：
   文档猜测 FA12 收不到「疑似需要经典蓝牙 ACL bond」，但 2026-09-15 日志已证否（本仓未做 bond，块照收）。
   这些调用更可能服务录像/录音共存与 FTP 路径。

---

*本文只记录「官方 App 实际怎么做」；本仓怎么改的完整过程与真机判据见 `docs/smoke/2026-09-15-ble-fix-verify/README.md`。*
