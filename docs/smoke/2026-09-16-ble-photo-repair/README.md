# 2026-09-16 拍照传图「慢 + 眼镜未响应补发请求」真机定位与修复

## 结论（先说结果）

用户报：「眼镜拍照完后与 App 传输很慢，一段时间后报错（截图：已接收 46080/56971 字节 →
`拍照失败:眼镜未响应补发请求`）」。

**根因不在收图，而在补洞。** 眼镜 V2.4.5 的 FA12 突发绝大多数会丢（本机实测一轮只送到
13 %~25 %），App 收图路径本身零丢失；但补洞循环是「一次 op2 → 等 2.5 s」，而眼镜对每个 op2
只回 **1~3 个块**（官方 App 假设的是「回剩下整段」，所以它的 2.5 s/轮 在本固件上等于
**0.3 块/秒**）。于是一张 48–57 KB 的照片：突发 1~6 s 送完一小截，剩下 4 万个字节按 0.3 块/秒
永远补不完 → 24 轮 × 2.5 s ≈ 60 s 后放弃，并给出**与实际不符**的文案「眼镜未响应补发请求」
（眼镜其实每一轮都答了）。

**修复**：把补洞从「一轮一个请求」改成「一轮一批请求 + 短等待 + 按进展判死」。

| | 修复前 | 修复后 |
|---|---|---|
| 补洞节奏 | 1 × op2 / 2.5 s | ≤20 × op2 / 轮，轮等待 250 ms |
| 实测补洞吞吐 | ~0.3 块/s（240 B / 2.5 s） | **~16–20 块/s**（3.8–4.8 KB/s） |
| 48–57 KB 照片结果 | 60 s 后失败（`眼镜未响应补发请求`） | **19.4–22.3 s 成功收齐** |
| 失败判定 | 24 轮固定预算（与是否在进展无关） | 连续 10 s 无新字节才放弃，文案改为「传图未完成（缺 N/M 字节）」 |
| 进度文案 | 全程「拍照中…」（看着像卡死） | 进入补洞后显示「补传缺块中…(眼镜漏发,正在逐块补回)」 |

真机验证（nova 6 / AGQV023313008161 + Glasses-A88 V2.4.5，APK = 本仓 `packageRelease`，
release key 覆盖安装，未丢数据）：

| 轮次 | 突发收到 | 缺字节 | 补洞批次 | 结果 |
|---|---|---|---|---|
| 20:00:44 | 8 880 / 49 299 B | 40 419 | 23 | **complete 22 265 ms** |
| 20:02:40（实验：BALANCED） | 12 480 / 48 587 B | 36 107 | 17 | **complete 19 830 ms** |
| 20:06:39（批 8→20、等待 400→250 ms） | 6 240 / 48 335 B | 42 095 | 11 | **complete 19 357 ms** |
| 20:07:28（实验：关 WiFi） | 9 840 / 48 255 B | 38 415 | 13 | **complete 21 915 ms** |
| 20:09:35（实验：DCK 优先级） | 9 600 / 48 123 B | 38 523 | 12 | **complete 19 638 ms** |

## 证据链

### 1. App 收图不丢块（排除 App 侧订阅/消费问题）

同一次会话里，系统日志 `BluetoothGatt: onNotify() ... handle=36`（应用进程收到 FA12 通知的次数）
= 应用侧 `blocks` 计数 = **206**，而该图正好需要 206 块（49 299 B ÷ 240 B）。也就是说
**每一条到达手机的通知都被应用收下并落进图里**，没有「到了但被丢掉」的块。

对照：`com.android.bluetooth` 侧 `gatt_process_notification` 的计数与之相同，而 `_fa12Notify`
全程没有打过一次 `FA12 notify LOST`（该日志在「缓冲区满 / 无订阅者」时会打）。

### 2. 丢在突发段（手机栈以下 / 固件侧），且随会话剧烈波动

| 会话 | 文件大小 | 突发实际到达 | 到达率 |
|---|---|---|---|
| 09-16 19:36 | 56 971 B | 192 / 238 块（81 %） | 前 2 s 110–133 块/s，随后骤降 |
| 09-16 19:39 | 56 715 B | 68 / 238 块（29 %） | 6 s 内 68 块，间隔 15–300 ms 不规则 |
| 09-16 20:00 | 49 299 B | 36 / 206 块（17 %） | ~45 块/s |
| 09-16 20:07（WiFi 关） | 48 255 B | 41 / 202 块（20 %） | 同上 |

同期实测排除了几个「便宜的假设」：

- **不是 WiFi/2.4 GHz 共存**：关掉手机 WiFi 后丢块率、总耗时都不变（21.9 s vs 21.9 s）。
- **不是连接间隔选错**：应用请求 `CONNECTION_PRIORITY_HIGH` 得到 interval=12（15 ms）；
  请求 `BALANCED` 会被固件自己的 LL 参数更新拉回 12；请求隐藏常量 3（DCK，6..9）也没有改变
  突发丢失与总耗时。三者都是 19.4–22.3 s 收齐。
- **不是 PHY/MTU**：链路 `txPhy=2 / rxPhy=1~2` 抖动、MTU 已协商 517，均与丢失无相关性。

固件侧自己的需求文档（`docs/glasses/AI识图传图提速_App连接参数配合.md` §2.3）已经写明这一点：
块间节奏宏（当前实测约 8 ms/块 ≈ 125 块/s）远快于链路排水能力时，**发送端队列打满后 ATT 静默丢包**，
需要固件把节奏宏调到 ~10 ms 并配合 App 的 HIGH 才达到「零丢块 1.4 s」。**该固件改动至今未发版**，
所以本仓 App 能做的只有：把补洞做快、做诚实。

### 3. 补洞通道本身是好的，只是被 App 的等待策略拖死

日志逐轮可见：每个 op2 都在 ~20–80 ms 内被应答（例：`20:00:40.186` 写 op2 →
`20:00:40.263` 对应块落地），**每个 op2 回 1 个块**（偶发 2–3 个），所以
「一轮一个 op2 + 2.5 s 等待」= 240 B / 2.5 s。修复前 19:36 那轮就是这样从
46 080 B 一路爬到 12 720 B 用了 66 s，最后报「眼镜未响应补发请求」。

## 代码改动

| 文件 | 改动 |
|---|---|
| `glasses/GlassesFa12Collector.kt` | 新增 `FA12_REPAIR_BATCH=20` / `FA12_REPAIR_STRIDE_BYTES=480` / `FA12_REPAIR_NO_PROGRESS_MS=10_000`；补洞改为**每轮批量请求**（按缺口步进 480 B，最多 20 个），轮等待 `resendWaitMs` 由 2500 → 400（再由仓库调成 250）；新增「连续 10 s 无新字节 → 放弃并报缺字节数」；`resendRounds` 语义变为「轮」；`onProgress` 增加 `repairing` 标志；`maxResendRounds` → `maxRepairCycles=64`（仅作请求量护栏，真正判死的是无进展计时） |
| `glasses/GlassesPhotoCaptureRepository.kt` | 补洞参数改为批量版；`CaptureProgress.Stage` 新增 `Repairing` |
| `glasses/ui/GlassesCaptureOverlay.kt` | 补洞阶段显示「补传缺块中…」（`R.string.glasses_capture_repairing`） |
| `res/values/strings.xml` | 新增 `glasses_capture_repairing` |
| `test/.../GlassesFa12CollectorTest.kt` | 新增 4 项：一批修多个块 / 批量上限 / 边补边进的会话不会被轮数掐死 / 补洞阶段上报 UI / 无进展时的文案与耗时；两项旧断言按批量语义更新 |

单测：`./gradlew :app:testDebugUnitTest --tests "com.icespiritai.offline.glasses.*"` → 119 项全绿。

## 仍未解决（需要固件配套）

1. **突发丢块 ~75–87 %**：只要固件仍以 ~8 ms/块往下推，链路排水（15 ms 间隔、1 M PHY）跟不上，
   队列就打满丢包。这是厂商文档 §2.3 描述的已知固件缺陷，需发版改块间节奏宏。
2. **补洞速度上限**：每个 op2 一次 ATT write-with-response 往返（实测 ~30 ms）+ 固件只回 1 块，
   补洞上限约 20–30 块/s。若把 op2 改成 **write without response**（`WRITE_TYPE_NO_RESPONSE`）
   有可能再快数倍，但需先确认固件是否处理 ATT write command —— 未验证，故未采纳。
3. 突发段本身的耗时（0.8–6 s）与固件两次 START 之间 ~1.8 s 的预热，App 无法压缩。

## 复现与验证命令

```powershell
$env:JAVA_HOME="C:\Users\37311\.gradle\jdks\jdk-17.0.18+8"
# 只打包 release（不要用 assembleRelease：它 finalizedBy uploadVisionReleaseToGitea，会推线上更新通道）
.\gradlew.bat :app:packageRelease -PmodelProfile=ice_ocr_rules
adb -s AGQV023313008161 install -r -d app\build\outputs\apk\release\app-release.apk
adb -s AGQV023313008161 logcat -c
adb -s AGQV023313008161 shell input tap 540 2286   # 底部「眼镜」按钮
adb -s AGQV023313008161 logcat -d -v time -s "GlassesCapture:V" "BluetoothController:V"
```

判据：

```
GlassesCapture: FA11 op2 batch #N: 20 request(s) for <缺字节> missing byte(s)   # 批量补洞生效
GlassesCapture: collectChunks complete: N/N bytes covered by M blocks in Xms (resends=K)
GlassesCapture: no FA12 progress for 10000ms over K repair cycles — giving up … # 真·无响应时才出现
```

> ⚠️ **发布流程坑（本次踩到）**：`assembleRelease` 会 `finalizedBy` → `generateVisionLatestJson`
> → `archiveVisionRelease` → `uploadVisionReleaseToGitea`，后者对 Gitea `latest` release 执行
> 「先删除同名资产、再上传」。本次一次 `assembleRelease` 在 2026-09-16 19:53 删掉了线上
> `icespiritai-vision.apk` + `vision-latest.json`，而上传因 HTTP 100 失败，线上通道一度为空；
> 随后用发布时的原始 APK（sha256 `efb72810…`，本地留档 `%TEMP%\vision-remote.apk`）经
> `generateVisionLatestJson` + `archiveVisionRelease` + `uploadVisionReleaseToGitea` 复原，
> 校验：`latest` 资产齐全、JSON `versionCode=78/0.4.3/apkSha256=efb72810…` 与发布一致、
> APK 区间请求 206。**验证性构建一律用 `:app:packageRelease`。**
