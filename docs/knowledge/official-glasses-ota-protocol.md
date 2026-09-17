# 官方眼镜 App 的 OTA 固件升级协议（反编译 + 厂商示例交叉核对）

> 对象：`docs/glasses/AI眼镜官方app.apk`（`com.deepvision_tek.glass_front` 3.1.00，classes2.dex）
> 交叉来源：`docs/glasses/官方技术给的示例（仅参考）/`（厂商参考工程，源码可读）
> 日期：2026-09-16 · 触发原因：用户要求「App 设置里增加眼镜固件升级功能」
> 目的：把固件升级需要的东西钉死，并说明本仓现在能做什么、缺什么

---

## 0. 一句话结论

官方 OTA = **云端查版本 → BLE `0x43` 把「下载地址」交给眼镜 → 眼镜自己联网下载 .rbl 并刷写/重启
→ App 重连后读一次固件版本确认**。

**关键点：BLE 上推的不是固件本体，而是一段 JSON `{"u":"<downloadUrl>"}`**（`startOtaUpdate`
里 `mapOf("u" to downloadUrl)` → Gson → `getBytes()`，整个数组才 ~100 B，按 64 B 分 2 包发出去）。
2.6 MB 的 `.rbl` 由**眼镜侧**从 CDN 拉取 —— 这就是官方为什么要先开「蓝牙共享网络(PAN)」。

**账号也不需要**：`/user/me/ota/check` 用 `POST /user/guest-login`（空 body）拿到的游客 JWT 即可查询
（2026-09-17 实测，见 §5）。

## 1. 命令字（`BleCommandConfig$Companion.default()` 构造参数，逐个对齐）

39+1 个字节按字段声明顺序传入，逐一从 iput 顺序核对得到的真值表（节选）：

| 字段 | 值 | 用途 |
|---|---|---|
| `request` / `response` / `notify` | 0x01 / 0x02 / 0x03 | 帧 type |
| `getDeviceInfoCmd` | **0x10** | 设备信息读取（TLV 信封） |
| `deviceStatusNotifyCmd` | **0x11** | 设备状态上报（TLV 列表，可能带固件版本） |
| `otaCmd` | **0x43** | **OTA 固件传输** |
| `aiPhotoBleCmd` | **0x33** | AI 拍照（本仓在用；与文档一致） |
| `aiPhotoStatusCmd` | 0x51 | 拍照状态 |
| `aiPhotoRequestCmd` | 0x50 | 新版拍照入口（V2.4.6 之前固件拒绝，本仓未用） |
| `takePhotoCmd` / `videoRecordCmd` / `audioRecordCmd` | 0x30 / 0x31 / 0x32 | 普通拍照/录像/录音 |
| `syncCompleteCmd` | 0x3A | 媒体同步完成 |
| `factoryResetCmd` / `setPhoneLanguageCmd` | 0x24 / 0x44 | 恢复出厂 / 手机语言 |
| `subFirmwareInfo` | **0x20** | 0x10/0x11 里的固件版本 TLV |
| `subBattery` / `subFileCount` / `subMemory` / `subFtpIp` / `subP2pMac` / `subApAccount` | 0x01 / 0x17 / 0xF4 / 0xF3 / 0xF2 / 0x14 | 其它子命令 TLV |

## 2. OTA 帧格式（已完整提取，可实现）

### 2.1 帧壳

仍然是 `55 AA | seq | cmd | type | u16 LE | payload`（与拍照命令同族），只是 OTA 的
**`u16` 字段含义不同**：`BlePacketBuilder.buildOtaFragmentPacket(seq, total, fragmentPayload)`
把 **固件总大小** 写进长度位，payload 才是本包固件片段；眼镜靠「累计字节数 == 总大小」判断收完。

### 2.2 分包规则（`sendOtaInFragments` + `resolveOtaFragmentPayloadSize`）

> payload 是 **`{"u":"<downloadUrl>"}` 这段 JSON**（约 100 B），不是固件本体；
> 因此实际只有 2 个分包。

| 项 | 值 | 依据 |
|---|---|---|
| 每包字节数 | `max(20, min(64, mtu - 3))`，MTU≥67 时即 **64 B** | `resolveOtaFragmentPayloadSize` 返回 `min(64, x)`；`sendOtaInFragments` 再 `max(20, x)` |
| 包数 | `ceil(payload.size / 每包字节)`，本场景 = 2 | 同上，循环 `i in 0 until 包数` |
| 每包帧长 | `7 + 本包字节`（≤71 B） | `ByteBuffer.allocate(fragmentPayload.size + 7)` |
| 包标签 | 非末包 `"OTA分包#<i>/<n>"`，末包 `"OTA升级"` | `isLastOtaFragmentCommand` / `applyOtaFragmentWriteProgress` |
| 包间隔 | **80 ms**（另有 120 ms / 500 ms 分支，条件在 `otaFragmentSendGapMs` 的 smali 跳转里，未逐条走查） | 同方法 `const-wide/16 80 / 120 / 500` |
| 写入特征 | `writeCharacteristic`（FFF1，命令通道）；写类型按 `PROPERTY_WRITE(0x08)` 判 `WRITE_TYPE_DEFAULT` | `sendNext()` 调 `resolveWriteType` |
| 开始/结束包 | **没有**：`buildOtaStartPacket` 在 APK 里没有任何调用点，整套 OTA 就是 `0x43` 分包流 | 全 dex 调用点扫描 |

### 2.3 前置条件与收尾

1. BLE 已连接 + **经典蓝牙已连接**，官方还会开「蓝牙共享网络(PAN)」并等它就绪
   （`enqueueOtaPayloadAfterPanReady`：`setBluetoothNetworkSharing(true)` +
   `waitForBluetoothNetworkSharing(6000)`；未就绪直接报
   「经典蓝牙未连接，无法开启共享网络拉包」/「蓝牙共享网络未开启，请到系统设置打开后再升级」）。
   PAN 在官方流程里用来让眼镜侧拉包，**是否 BLE 分包推送的硬前置未验证**。
2. 推完分包后：眼镜侧校验 → 重启（`startOtaRebootingTimeout`、`markOtaDisconnectAsLikelyReboot`）。
3. App 收尾：重连后**再读一次固件版本**做本地确认（`finalizeOtaByLocalVersionCheck`），
   失败分类见 `OtaErrorCodeMapper` / `OtaErrorCategory`；进度上报
   `MeRepository.updateOtaUpgradeProgress` → `/user/me/ota/progress`。
4. 断线可续：`OtaSessionStore`（`firmwareId`、包序号）持久化在本地。

## 3. 云侧接口（官方，需账号）

**服务地址（来自 `assets/app_config.json`，官方 APK 内）**：

| 项 | 值 |
|---|---|
| `network.baseUrl`（prod） | `https://s1.deepvision-tek.com:8089/` |
| `network.baseUrls[].dev` | `http://192.168.1.2:9898/` |
| 官网/联系页 | `https://s1.deepvision-tek.com:5174/index.html`、`/contact.html`、`/terms.html`、`/privacy.html` |
| 文件分发主机 | `http://218.17.188.146:9100/`（sherpa-onnx 包、官方 APK） |

**厂商服务端把 OpenAPI 文档公开了（免登录）**：
`GET https://s1.deepvision-tek.com:8089/v3/api-docs`（OpenAPI v1，91 个接口，HTTP 200）、
`/swagger-ui.html`。OTA 相关接口（均为 `bearerAuth` JWT）：

| 接口 | 用途 |
|---|---|
| `GET /user/me/ota/check?deviceId&macAddress&currentVersion` | 查升级信息，返回 `latestVersion.downloadUrl` / `sha256` / 版本号 / size |
| `POST /user/me/devices/firmware/report` | 上报当前固件版本 |
| `POST/GET /user/me/ota/progress` | 上报/查询进度 |
| `POST /user/me/ota/records` | 升级记录 |
| `POST /admin/ota/versions` | 新增固件版本（管理端） |

**官方 App 自身的更新接口是免登录的，直链可用**：

```
GET  https://s1.deepvision-tek.com:8089/version/latest
POST https://s1.deepvision-tek.com:8089/version/check-update   {currentVersionCode, currentVersionName, platform}
→ {"versionName":"3.1.20","versionCode":3120,"publishTime":"2026-09-16T13:40:15",
   "downloadUrl":"http://218.17.188.146:9100/app-d15.apk","fileSize":131594025,
   "md5":"a712a0cc405c599624464ab7df88f950"}
```

> 注意：仓库里反编译的那份是 **3.1.00 / vc3100**，厂商线上已是 **3.1.20 / vc3120**（2026-09-16 发布），
> 本文的 OTA 协议结论基于 3.1.00，未变则同样适用，需以 3.1.20 复核。
> 另：`downloadUrl` 需要带浏览器/okhttp 风格的 User-Agent 访问，curl 默认 UA 会被 nginx 403。

| 接口 | 用途 |
|---|---|
| `GET/POST /user/me/ota/check` | 返回 `upgradeAvailable` + `latestVersion{OtaFirmwareVersionResponse}` |
| `POST /user/me/devices/firmware/report` | 上报当前眼镜固件版本 |
| `POST /user/me/ota/progress` | 上报升级进度 |
| `GET /user/me/ota/records` | 升级记录 |

`OtaFirmwareVersionResponse` 关键字段：`downloadUrl`、`sha256`、`versionCode`、`versionName`、
`versionSizeBytes`、`releaseNotesHtml`、`forced`、`modelId`。**固件包从 `downloadUrl` 取，
并由 App 校验 `sha256`。**

## 4. 本仓现状（2026-09-16）

**2026-09-17 已实现（设置页 → 智能眼镜卡片）**：

| 组件 | 作用 |
|---|---|
| `glasses/GlassesFirmwareProtocol.kt` | `0x43` 帧：`55 AA │ seq │ 43 │ 01 │ u16(=**整个 payload 长度**) │ 分片`，分片 `max(20,min(64,mtu-3))`，帧间隔 80 ms；payload = `{"u":"<downloadUrl>"}`；`parseOtaFrame` 读眼镜侧的 `0x43` 状态 |
| `glasses/GlassesFirmwareService.kt` | `guest-login` + `ota/check`（HttpURLConnection，可注入假连接测试）；401 自动换 token；缺 `downloadUrl` 直接报错而不是把垃圾发给眼镜 |
| `glasses/GlassesFirmwareUpdater.kt` | 状态机：`Checking → UpToDate/Available → Sending → Upgrading(轮询版本) → Success/Failed`；升级后**以版本变化为准**判成功，20 min 超时 |
| `BluetoothController.writeFirmwareOtaFrames` | 逐帧写 FFF1（`WRITE_TYPE_DEFAULT`），失败即返回 false → 文案「眼镜未接受升级指令」 |
| 设置页 UI | 「检查固件更新」按钮 + 升级对话框（当前/最新版本、包大小、PAN/电量/断电提示、开始升级） |

真机验证（2026-09-17，nova 6）：点「检查固件更新」→ 对话框显示
「当前版本:V2.4.6 / 最新版本:v2.5.8 / 固件包:DPS_G20_V1_258 / 大小:2.5 MB」。
**升级前的系统前置**：`dumpsys bluetooth_manager` 显示 `mPanDevices:` 为空、
`settings get global bluetooth_tethering_on` = null → 手机的「蓝牙共享网络」当前是关的，
必须先打开（眼镜要靠它去 OSS 下载 .rbl）。

## 5. 2026-09-17 实测：游客登录即可拿到固件信息（无需账号）

## 6. 2026-09-17 升级结果与升级后复测（V2.4.6 → V2.5.8）

1. **升级成功**：升级由 App 下发 URL、眼镜自行拉包刷写；重开后 App 读到
   `firmware version: V2.5.8`（设置页显示「固件版本:V2.5.8」）。
2. **升级后传图复测（nova 6 + Glasses-A88 V2.5.8，43 119 B 照片）**：
   突发只送到 `filled=7440`（**丢约 83 %**），补洞 9 轮 →
   `collectChunks complete: 43119/43119 bytes covered by 180 blocks in 14537ms`。
   对照 V2.4.6 的 15.4–22.3 s —— **v2.5.8 没有修 FA12 突发丢块**，
   App 侧批量补洞仍是唯一兜底；要回到 1–2 s 必须厂商改块间节奏/队列（其文档 §2.3 的 10 ms 宏）。
3. **顺带修掉一个真机 bug**（用户 2026-09-17 报：升级后重启，读版本必超时，
   只有关掉 App 重开才正常）：
   - 根因：`GlassesPhotoCaptureRepository.ensureConnected` 只看自己缓存的 `Ready`
     状态就提前返回；眼镜重启/链路掉线不会把 `Ready` 降级，于是后续所有读写都在
     一个已经死掉的 GATT 句柄上，直到进程重启。
   - 修复：新增 `isGlassesLinkUsable()`（`Ready` **且** controller 真的 `Connected`
     且地址一致才算可用，纯函数 + 5 项单测）；失效时 `BluetoothController.resetLink()`
     丢句柄重连；`readFirmwareVersion` 失败后再强制重连重试一次；`capture()` 同样先校验。
   - 真机验证（模拟链路失效后点「读取」）：
     `firmware version request rejected by the stack` →
     `resetLink: dropping GATT handle (was Connected(…60:0B…))` →
     `onServicesDiscovered status=0 services=5` → `firmware version: V2.5.8`，**无需重启 App**。

```
POST https://s1.deepvision-tek.com:8089/user/guest-login   body={}  → {"code":200,"data":"<JWT isGuest>"}
GET  https://s1.deepvision-tek.com:8089/user/me/ota/check
       ?macAddress=C4:12:22:55:60:0B&currentVersion=V2.4.6
       Authorization: Bearer <guest JWT>
```

返回（真机 MAC = 用户那副 Glasses-A88）：

| 字段 | 值 |
|---|---|
| `upgradeAvailable` / `forced` | **true** / false（非强制） |
| `currentVersionName` | `V2.4.6`（code 20406） |
| `latestVersion.firmwareName` | `DPS_G20_V1_258` |
| `latestVersion.versionName` | **`v2.5.8`**（versionCode 246，`releaseType=STABLE`） |
| `versionSizeBytes` | 2 655 536 B（2.6 MB） |
| `downloadUrl` | `https://glass-dps.oss-cn-shenzhen.aliyuncs.com/ota/1789370230610_G20_V1_258.rbl` |
| `sha256` / `md5` | `b27236adf141c0ab7f4cd735344ff552220f8a75da3245f87cc7c523f6f94a8c` / `491da4ddb50d42087d82086d04416c5c` |

要点：
- 不需要把设备绑到账号上：只传 `macAddress`（+ 可选 `currentVersion`）即可，游客 token 够用。
- `.rbl` 放在阿里云 OSS 上，**是给眼镜下载的**，App 侧只需把 URL 用 `0x43` 发下去。
- 升级前需要在系统里打开**蓝牙共享网络(PAN)**（官方报错文案：
  「蓝牙共享网络未开启，请到系统设置打开后再升级」），否则眼镜拉不到包。

已实现并真机验证：

- `GlassesPhotoProtocol.buildFirmwareVersionRequestFrame` / `parseFirmwareVersion`
  （`0x10` + sub `0x20`，同时兼容 `0x11` 状态 TLV 列表），单测 6 项；
- `GlassesPhotoCaptureRepository.readFirmwareVersion(device)`；
- 设置页「智能眼镜」卡片新增 **固件版本: … [读取]** 一行；
- 真机（nova 6 + Glasses-A88）读到 **`V2.4.6`**（日志 `firmware version: V2.4.6`，
  原始帧 `55 aa 01 10 02 08 00 20 06 56 32 2e 34 2e 36`）——
  注意本仓旧文档一直按 **V2.4.5** 记录，需以设备实测为准。

尚未实现（需要固件包 / 厂商确认）：

1. **固件包来源**：官方走账号接口；本仓没有账号体系。可行路径是「用户/厂商提供 .bin
   → App 选文件 → 走 §2 的 0x43 分包推送」（不需要账号）。
2. **刷写流程**：协议已够写，但**没有固件包就无法真机验证**，且刷写中断有变砖风险；
   PAN 前置是否必需也未验证。建议先拿到厂商的 OTA 说明或一个可刷的包再实现。
3. 升级进度上报/断点续传（官方有，本仓暂无账号侧，不需要）。
