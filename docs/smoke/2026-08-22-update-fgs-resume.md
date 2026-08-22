# 冰灵锐目 后台下载 + 断点续传 烟测

| 字段 | 值 |
|---|---|
| 日期 | 2026-08-22 |
| 功能 | Foreground Service 下载 + Range 断点续传 + 冷启动自动续传 |
| 分支 | `update/fgs-resume` |
| 目标版本 | v0.1.17 |
| profile | `ice_ocr_rules`(真机) |
| 测试设备 | 华为 nova 6 (ANN-AN00) / SDK 35 / arm64-v8a |
| 关联 spec | `docs/superpowers/specs/2026-08-22-icevision-update-fgs-resume-design.md` §10.3 |

## 前置条件

- [ ] 设备已连 `adb devices` 可见,无 ghost state(`pm clear` 兜底,见 CLAUDE.md)
- [ ] 设备 Android 13+(POST_NOTIFICATIONS 运行时权限)、Android 14+(`foregroundServiceType=dataSync` 红线)
- [ ] APK 安装:`./gradlew.bat :app:installDebug -PmodelProfile=ice_ocr_rules`
- [ ] **logcat 捕获**(踩 CLAUDE.md 教训):在测试启动前开
  ```bash
  adb logcat -c
  (adb logcat -v time UpdateDownloadService:V UpdateResumeWorker:V UpdateResumeCoordinator:V UpdateDownloadNotifier:V UpdateDownloader:V UpdateDownloadRepository:V UpdateSection:V UpdateScreen:V '*:S' > /tmp/updater-smoke.log) &
  ```
- [ ] Gitea 上 `vision-latest.json` 可见且 `signerCertSha256` 与本 APK 签名匹配(可手动 `unzip -p <apk> META-INF/CERT.RSA | openssl ... -sha256 -binary | xxd -p -c 64` 校验)

## 场景 1:锁屏下载

| 项 | 内容 |
|---|---|
| 操作 | 进入 Settings → [下载更新] → 立刻按下电源键锁屏 |
| 预期 | FGS 在锁屏 + Doze 期间持续下载,1 分钟后回 App,通知 + App 内 `UpdateSection` 进度都到 ~100%。`adb shell dumpsys activity services com.icespiritai.vision` 应见 `UpdateDownloadService` 处于 `STARTED` / `FOREGROUND` |
| 关键观察 | logcat: `UpdateDownloadService: ... Downloading ... bytesWritten=N` 在锁屏后仍持续;DataStore 的 `bytesWritten` 单调递增 |
| 实际 | ___ |

## 场景 2:Wi-Fi 切换

| 项 | 内容 |
|---|---|
| 操作 | 下载到 ~50% → `adb shell svc wifi disable`(系统切 4G 或完全断网)→ 等 ~30s → `adb shell svc wifi enable` |
| 预期 | 关 Wi-Fi 后 `FetchOutcome.Retryable` → 退避 2 s → 仍失败 → 4 s → 8 s → 3 次后翻 `Failed(NetworkUnreachable)`(若彻底断网)。Wi-Fi 恢复后下一次重试即命中,Range 头带 `bytes=<N>-`(logcat 观察 `UpdateDownloader: ApkDownloader request: Range=bytes=N-`) |
| 关键观察 | logcat 中 `retry attempt=1/3 nextDelayMs=2000`、`attempt=2/3 nextDelayMs=4000`、`attempt=3/3 nextDelayMs=8000`;再开 Wi-Fi 后下一行出现 `Range: bytes=` |
| 实际 | ___ |

## 场景 3:飞行模式

| 项 | 内容 |
|---|---|
| 操作 | 开始下载 → `adb shell settings put global airplane_mode_on 1 && adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true`(或系统快捷开关) |
| 预期 | 3 次退避(2/4/8 s)后翻 `Failed(NetworkUnreachable)` + App 内 `UpdateSection` 文案 "网络不可达,请重试"(对应 `update_failed_network_unreachable`);通知 channel `update_download_failed` 显示 |
| 关键观察 | logcat 中 `Failed cause=NetworkUnreachable`;DataStore 中该 record `stage = NetworkUnreachable`(打开 Settings 不退 App 仍能看到记录) |
| 实际 | ___ |

## 场景 4:上划杀进程

| 项 | 内容 |
|---|---|
| 操作 | 下载到 ~30% → 最近任务卡片上划 `UpdateDownloadService` / App → 等待 ~5s → 重新打开 App |
| 预期 | App 进程被杀后,WorkManager `UpdateResumeWorker`(spec §5.5)在 ~30s 内触发 → `Context.startForegroundService(ACTION_DOWNLOAD, resume=true)` → FGS 接续下载,`bytesWritten` 从原断点继续(不归零)。**不弹 [重试] 卡片**,App 内 `UpdateSection` 进度条直接接续 |
| 关键观察 | logcat: `UpdateResumeWorker: ... resuming downloadId=... bytesWritten=N` + `UpdateDownloadService: ... Resume from N`;`adb shell dumpsys jobscheduler | grep icespirit` 能看到 Worker job 一次 |
| 实际 | ___ |

## 场景 5:POST_NOTIFICATIONS 拒绝

| 项 | 内容 |
|---|---|
| 操作 | `adb shell pm revoke com.icespiritai.vision android.permission.POST_NOTIFICATIONS`(或在 App 内首次进入 Settings 时权限弹窗选拒绝)→ 回 Settings → [下载更新] |
| 预期 | FGS 仍能 `startForeground`(spec §5.8:只需合法 Notification 对象,不要求可见);App 内 `UpdateSection` 进度条照常更新,通知栏不显示;通知 channel 仍创建但不弹出 |
| 关键观察 | logcat 无 `MissingForegroundServiceTypeException` / `SecurityException`;`adb shell dumpsys notification --noredact | grep icespirit` 该 process 无可见通知 |
| 实际 | ___ |

## 场景 6:签名校验失败

| 项 | 内容 |
|---|---|
| 操作 | 先正常下载完成(或模拟)→ `adb shell run-as com.icespiritai.vision dd if=/dev/urandom of=cache/update/<id>.apk bs=1 count=1 seek=100 conv=notrunc` → 关闭再开 App |
| 预期 | `Failed(SignatureMismatch)` + 文案 "签名校验失败,请联系开发者"(对应 `update_failed_signature_mismatch`);partial 文件被 `ApkSignatureVerifier` 后的 cleanup 路径删除 |
| 关键观察 | logcat: `VerifierResult.Mismatch expected=... actual=...`;`adb shell run-as com.icespiritai.vision ls cache/update/` 该 id 文件不存在 |
| 实际 | ___ |

## Android 14+ 平台特定注意事项

- `foregroundServiceType="dataSync"` 是**强约束**:Android 14 (API 34)+ 在 `startForeground()` 必须在创建 FGS 5s 内调(ANR 红线),且 `Service.onStartCommand` 中若不调 `startForeground` 系统抛 `MissingForegroundServiceTypeException`。spec §7.1 manifest 已声明 `FOREGROUND_SERVICE_DATA_SYNC` 权限 + `dataSync` 类型,本测试目的即端到端确认两条都成立
- Android 13 (API 33)+ `POST_NOTIFICATIONS` 是**运行时权限**,且与 `startForeground()` 解耦(场景 5 验证)。拒绝后不影响 FGS,只影响通知栏可见性
- Android 15 (nova 6 SDK 35 接近,本机未到 15) 在 `DownloadManager` 之外用裸 `HttpURLConnection` 触发 background restriction 的风险已规避(本方案走 FGS)

## 锁屏 / Doze 覆盖范围

| 状态 | 触发条件 | FGS 期望行为 |
|---|---|---|
| 锁屏 + Doze 浅 | 屏幕关 ~30s | FGS 不受限,继续下载 |
| 锁屏 + Doze 深 | 不动 ~30min | 系统拒 ~10min 网络窗口,`read()` 阻塞到窗口开放;FGS 不被 kill |
| App 切后台但屏幕亮 | Home 键出去 | FGS 不受限,继续下载 |
| 上划杀进程(场景 4) | swipe-to-kill | FGS 进程被清,WorkManager `UpdateResumeWorker` 接管 → 拉起新 FGS 续传 |

## 通知 action 覆盖

- **下载中**(channel `update_download_ongoing`,ongoing=true):进度文本 + 小图标,用户**不可滑动清除**;无 action(只有取消在 App 内按钮走得到)
- **可安装**(channel `update_download_ready`,DEFAULT):
  - `ACTION_INSTALL` PendingIntent → `IceSpiritVisionActivity.onNewIntent` → 走 `UpdateRepository.requestInstall(activity, file)` 拉起系统安装器
  - `ACTION_LATER` PendingIntent → `IceSpiritVisionActivity.onNewIntent` → 标 `Later`(暂存记录,后续可重新触发安装)
- **失败**(channel `update_download_failed`,DEFAULT):
  - [重试] 按 spec §5.6 分 subtype:
    - `NetworkUnreachable` → `retry(NetworkUnreachable)` → `runDownload(resume=true)`
    - `SignatureMismatch` / `Other` → `retry(...)` → 走 spec §5.6 第二分支(本测试场景 6 即入口)

## 验证产物

完成后请把:

1. 本文档 `实际:___` 填好
2. logcat 截取片段(`UpdateDownloadService`、`UpdateResumeWorker`、`UpdateDownloader` 三类各 5~10 行)
3. APK 路径 `app/build/outputs/apk/ice_ocr_rules/debug/app-ice_ocr_rules-debug.apk` 大小

一起贴回 release tracking issue。
