# 冰灵锐目 更新后台下载 + 断点续传 — 设计规范 v1

| 项 | 值 |
|---|---|
| 文档版本 | v1.0.0 |
| 日期 | 2026-08-22 |
| Phase | Phase 2 hardening(承接 2026-08-17 v1 spec §1.4 / §12 延期的 FGS 项) |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline | `docs/knowledge/build-stack-2026-08.md` |
| 关联前置 spec | `docs/superpowers/specs/2026-08-17-icevision-update-mechanism-design.md`(v1 最小版,本 spec 在其上叠加) |
| 关联 sibling spec | `D:\GitHub\IceSpiritAI_Translate\docs\superpowers\specs\2026-07-31-v1.38.0-update-mechanism-design.md`(Phase 2+ 安全护栏全套,可借鉴 UI/分流思路) |

---

## 1. 背景与目标

### 1.1 现状

2026-08-17 v1 spec 落地了"检查更新 + 下载 + 安装"最小版([UpdateRepository.kt](app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt))。下载走 `viewModelScope` + `Dispatchers.IO`,`HttpURLConnection` 流式写盘。**该版本明确将 FGS 后台下载划入 §1.4 / §12 非目标**。

### 1.2 触发问题

用户在 Settings 点 [下载更新] 后,如果出现以下任一情况,下载会中断并翻 `Failed(DownloadInterrupted)`(UI 提示 "下载失败,请重试"):

| 场景 | 根因 |
|---|---|
| 屏幕自动熄屏锁住 | Doze 进入 → 网络访问被节流 → `read()` 长时间阻塞 / `SocketTimeoutException` |
| 锁屏期间内存紧张被 LMK 杀进程 | Activity 销毁 → `viewModelScope` 取消 → 写盘协程死 → partial 文件残留但 state 翻 Failed |
| 用户从最近任务上划 App | 同上,进程死,state 翻 Failed |
| 用户切到其他 App 长时间不回来 | Activity 走 `onStop` 但不立即死,Doze 触发后同上 |

任意一种都让用户回到 App 看到 [重试] 按钮,但 [重试] 又是从头开始(partial 文件被丢弃),**下载进度归零**。

### 1.3 目标

下载做到:

1. **扛得过锁屏 / Doze / 后台切走** —— 用 Foreground Service 持下载协程,Activity 销毁不影响。
2. **扛得过进程被杀 / 用户上划** —— partial 文件 + DataStore 记录留底,下次打开 App 自动续传(`Range:` 头)。
3. **断点续传** —— `HttpURLConnection` 发 `Range: bytes=N-` 接续;若服务端不返回 206 则从头开始。
4. **进度可视化** —— 前台通知(进度条 + 取消按钮)+ App 内 `UpdateSection` 双轨。
5. **不退化现有安全校验** —— `signerCertSha256` cert-pin 仍在整文件下载完成后跑(签名是按 APK 全量计算的,不能分片)。

### 1.4 显式不目标(本期不做)

| 项 | 理由 |
|---|---|
| URL allowlist 校验 | v1 spec §12 已划入 Phase 2+ 安全护栏,本期不动 |
| `apkSha256` 校验 | v1 spec §12 同上 |
| Anti-rollback floor | v1 spec §12 同上 |
| 重定向拒绝 | v1 spec §12 同上 |
| Manifest body 大小上限 | v1 spec §12 同上 |
| 自动静默下载 | v1 spec §12 Phase 3 项 |
| 多连接分片下载 | 单连接已足够(APK 30–50 MB,Range 续传覆盖断线恢复) |
| 差分 / patch 更新 | 维持每版发完整 APK |
| 自动跳过用户确认直接安装 | Play 政策禁止,且 v1 spec 历来手动点 [立即安装] |
| Gitea 服务端 Range 支持改造 | Go `net/http` 默认开,不动服务端 |
| 引入 OkHttp | v1 spec 刻意不用,延续 `HttpURLConnection` |

## 2. 技术 baseline

| 项 | 值 | 备注 |
|---|---|---|
| AGP | 9.3.x | baseline 不变 |
| Gradle | 9.7.x | baseline 不变 |
| Kotlin | 2.4.10 | baseline 不变 |
| JDK | 17 | baseline 不变 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | minSdk=26 保证 Service.startForeground 用法稳定;targetSdk=37 触发 Android 14+ FGS type 必填规则 |
| 持久化 | `androidx.datastore:datastore-preferences` | 新增依赖,见 §11 |
| HTTP | `java.net.HttpURLConnection`(无 OkHttp) | 续传 |
| 状态管理 | `MutableStateFlow` + `Service` 作用域 | `Service` 不持 `viewModelScope`;Service 内的 Job 由 `coroutineScope { }` + `Job.cancel()` 管理 |
| 后台保活 | `Service.startForeground(type=dataSync)` | Android 14+ 必填 type |

## 3. 模块拆分

新增模块全部落在 `app/src/main/java/com/icespiritai/offline/updater/`(沿用 v1 spec §3 的目录),新增子目录 `service/`:

```
app/src/main/java/com/icespiritai/offline/updater/
├── ApkDownloader.kt                       # 新增:byte-stream 原语,无 FGS / 无 state
├── service/
│   ├── UpdateDownloadService.kt           # 新增:FGS 持有下载协程
│   ├── UpdateDownloadNotifier.kt          # 新增:通知封装
│   └── UpdateResumeCoordinator.kt         # 新增:Application.onCreate 触发冷启动续传
├── DownloadStateStore.kt                  # 新增:DataStore Preferences 包装
├── AppVersionInfo.kt                      # 修改:加 etag/contentSha256 字段(可选填)
├── UpdateState.kt                         # 不变
└── UpdateRepository.kt                    # 修改:downloadApk 委托给 Service
```

```
app/src/main/java/com/icespiritai/offline/
├── IceSpiritApplication.kt                # 新增/修改:接入 UpdateResumeCoordinator
└── ui/settings/
    ├── UpdateSection.kt                   # 修改:加 [取消] 按钮 + 失败文案细分
    └── SettingsViewModel.kt               # 修改:cancel() / 检查 ReadyToInstall 恢复路径
```

```
app/src/main/
├── AndroidManifest.xml                    # 修改:加权限 + <service> 声明
├── res/drawable/ic_stat_download.xml      # 新增:notification 小图标
├── res/values/strings.xml                 # 修改:加 notification / 取消文案
└── res/values-zh/strings.xml              # 同步(如有)
```

```
app/src/test/java/com/icespiritai/offline/updater/
├── ApkDownloaderTest.kt                   # 新增:JVM 单测
├── DownloadStateStoreTest.kt              # 新增:JVM 单测(DataStore 内存后端)
└── UpdateResumeCoordinatorTest.kt         # 新增:JVM 单测

app/src/androidTest/java/com/icespiritai/offline/updater/
├── UpdateDownloadServiceColdTest.kt       # 新增:cold / warm 计时(踩 CLAUDE.md harness 模式)
├── UpdateResumeCoordinatorAndroidTest.kt  # 新增:预置 partial 续传
├── CancelFromNotificationTest.kt          # 新增:模拟点 [取消]
└── ProcessKillResumeTest.kt               # 新增:am force-stop → 重开 → 自动续传真值
```

## 4. 数据形状

### 4.1 `DownloadRecord`(DataStore 持久化)

```kotlin
@Serializable
data class DownloadRecord(
    val downloadId: String,           // UUID,防同一 url 并发下载冲突
    val url: String,                  // APK URL,作为 de-dup key
    val destPath: String,             // /.../cacheDir/update/<uuid>.apk
    val bytesWritten: Long,           // 已落盘字节
    val totalBytes: Long,             // Content-Length,首次响应获取
    val etag: String?,                // 服务端 ETag,Range 续传时作 If-Range
    val signerCertSha256: String,     // 来自 vision-latest.json,完整性兜底
    val stage: DownloadStage,          // 见下
    val versionName: String,          // 通知文案用,例 "v0.2.0"
    val startedAtEpochMs: Long,       // 调试用
) {
    enum class DownloadStage { Downloading, VerifyingSignature, ReadyToInstall }
}
```

存储后端:DataStore Preferences 一条 key-per-record,key = `"dl_${downloadId}"`。`downloadId` 用 `info.apkUrl + versionCode` 哈希派生(同一版本同一 url 唯一),这样 URL / versionCode 决定下载身份,rerun 同版本自动接续。

**完整性不变量:**
- `bytesWritten == File(destPath).length()` 必须成立。否则记录为"被外部篡改",`UpdateResumeCoordinator` 触发清理后从头开始。
- `destPath` 文件不存在但记录 stage=Downloading → 视为"App 卸载后残留",清理记录即可。

### 4.2 `FetchResult` 与 `FetchOutcome`(`ApkDownloader` 返回值)

```kotlin
data class FetchResult(
    val bytesWritten: Long,      // 累计写入(含 resume 的 offset)
    val totalBytes: Long,        // 服务端 Content-Length
    val etag: String?,           // 末次响应的 ETag
    val sha256Hex: String,       // 整文件 SHA-256,与 cert-pin 配合(可选校验位)
    val responseCode: Int,       // 200 或 206
)

sealed class FetchOutcome {
    data class Success(val result: FetchResult) : FetchOutcome()
    data class Retryable(val cause: Throwable) : FetchOutcome()     // 超时 / 5xx / 网络断开,走退避
    data class Fatal(val cause: Throwable) : FetchOutcome()         // 416 / 4xx / 磁盘满 / 不支持 Range
}
```

### 4.3 `VerifierResult`(`ApkSignatureVerifier` 返回值,扩展现有)

v1 spec 中 `ApkSignatureVerifier.verify(...)` 返回 `String?`(匹配返回 SHA-256 hex,不匹配返回 null)。扩展为 sealed:

```kotlin
sealed class VerifierResult {
    data class Match(val actualCertSha256: String) : VerifierResult()
    data class Mismatch(val expected: String, val actual: String) : VerifierResult()
}
```

调用方更易区分 Match / Mismatch,Fail-fast 分支明确。

### 4.4 `UpdateState`(沿用 v1 spec §4.3,不变)

不变。仅 `Failed(result)` 内 `result: UpdateCheckResult.Failed.DownloadInterrupted` 子类型拆分(见 §4.5)。

### 4.5 `UpdateCheckResult.Failed.DownloadInterrupted`(重构)

v1 spec 当前 `data class DownloadInterrupted(val cause: Throwable)`,**替换为 sealed class** 以支持 UI 文案细分:

```kotlin
sealed class DownloadInterrupted : Failed("interrupted") {
    object Cancelled : DownloadInterrupted()              // 用户点 [取消] 触发
    data class NetworkUnreachable(val cause: Throwable) : DownloadInterrupted()  // 重试 3 次仍 timeout
    data class Other(val cause: Throwable) : DownloadInterrupted()                // 兜底,沿用 "下载失败,请重试" 文案
}
```

文案映射:

| 子类 | 文案 |
|---|---|
| `Cancelled` | `update_failed_cancelled` = "已取消" |
| `NetworkUnreachable` | `update_failed_network_unreachable` = "网络不可达,请重试" |
| `Other` | `update_failed_download` = "下载失败,请重试"(沿用 v1 spec 已有) |

`UpdateSection.failureLabel()` 据此三分支渲染。

**cert-pin 失败**走 v1 spec 已有的 `Failed.SignatureMismatch(actual, expected)`(现成于 [UpdateRepository.kt:231-243](app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt#L231-L243)),**不**放入 `DownloadInterrupted` 层级;UI 单独渲染 `update_failed_cert_mismatch` = "签名校验失败,请联系开发者"。

### 4.6 通知 channel / ID

| Channel ID | Importance | 用途 | 是否 ongoing |
|---|---|---|---|
| `update_download_ongoing` | LOW | 下载中进度 | 是 |
| `update_download_ready` | DEFAULT | 验证完成可安装 | 否 |
| `update_download_failed` | DEFAULT | 下载失败(仅失败时建一次) | 否 |

Notification ID:用 `NOTIF_ID_BASE = 0xF001 + downloadId.hashCode()` 派生(避免同一 App 多个并发下载覆盖)。

## 5. 行为规约

### 5.1 用户点 [下载更新]

1. `SettingsViewModel.download(info)` → `UpdateRepository.startDownload(info, resume=false)`。
2. `UpdateRepository`:
   - 派 `downloadId = sha256(info.apkUrl + ":" + info.versionCode)`(取前 16 hex 即可)
   - 创建 `cacheDir/update/`(存在则跳过)
   - 拼 `Intent(ACTION_DOWNLOAD)` extras `{downloadId, url, destPath, signerCertSha256, versionName, resume=false}`
   - `Context.startForegroundService(intent)`(API ≥ 26 必须用 `startForegroundService`)
3. `UpdateDownloadService.onStartCommand`:
   - 若已有同一 `downloadId` 的 in-flight job → 直接 `return START_NOT_STICKY`(防重复)。
   - 否则 `startForeground(NOTIF_ID, initialOngoingNotif)`,**5 秒内必须**(Android 12+ ANR 红线)。
   - 启 `serviceScope.launch { runDownload(intent) }`。

### 5.2 `runDownload(intent)`

```
fun runDownload(intent: Intent) = serviceScope.launch {
    val record = store.getOrCreate(downloadId, url, destPath, signerCertSha256, versionName)
    val resumeFrom = if (intent.getBooleanExtra("resume", false) && record.bytesWritten > 0) {
        record.bytesWritten
    } else {
        // 不续传 → 删 partial + 重置
        File(record.destPath).delete()
        store.update(record.copy(bytesWritten = 0))
        null
    }
    // 启动时 sanity check:文件大小必须 == bytesWritten
    if (resumeFrom != null && File(record.destPath).length() != resumeFrom) {
        File(record.destPath).delete()
        resumeFrom = null
        store.update(record.copy(bytesWritten = 0))
    }

    notifier.updateProgress(record, written = resumeFrom ?: 0)
    val result = ApkDownloader.fetch(
        url = url,
        destFile = File(record.destPath),
        resumeFrom = resumeFrom,
        etag = record.etag,
        onProgress = { written ->
            // 节流:throttleFirst(500ms)
            notifier.updateProgress(record, written = written)
            store.update(record.copy(bytesWritten = written))   // 高频写 DataStore 有性能风险?见 §7
        },
    )
    when (result) {
        is FetchOutcome.Success -> onDownloadComplete(record, result)
        is FetchOutcome.Retryable -> retryWithBackoff(record, intent, result.cause)
        is FetchOutcome.Fatal -> onFailed(record, DownloadInterrupted.Other(result.cause))
    }
}
```

`ApkDownloader.fetch` 内部 retry 决策:

| HTTP / IO 现象 | 决策 |
|---|---|
| 200 OK | 服务端不支持 Range 或 If-Range 不匹配 → 返回 `Success(result, code=200)`,但调用方已把 partial 文件删了从头写,所以实际是 Success(从头)。调用方无需特殊处理。 |
| 206 Partial Content | append 到 partial → 返回 `Success(result, code=206)` |
| 304 Not Modified | partial 文件与服务器一致 → 直接进入 verify 流程(返回 `Success` 但 bytesWritten=resumeFrom) |
| 416 Range Not Satisfiable | 服务端说范围越界 → 返回 `Fatal(RangeNotSatisfiable)`,调用方删 partial + 改 resumeFrom=null 重试 |
| 5xx / SocketTimeoutException / UnknownHostException | 返回 `Retryable(cause)`,外层走 2/4/8 s 退避,3 次后翻 `Failed(NetworkUnreachable)` |
| 4xx 其他 | 返回 `Fatal(cause)` |
| DiskFull / IOException(non-network) | 返回 `Fatal(cause)` |

**DataStore 写盘节流:** `onProgress` 回调频率 = 每 8 KiB 一次,但 DataStore 写盘仅每 5 s 一次(节流),崩溃最多丢 5 s 进度,Range 续传对齐到该断点。

### 5.3 下载完成 → cert-pin → ReadyToInstall

```
fun onDownloadComplete(record: DownloadRecord, result: FetchResult) {
    notifier.updateProgress(record, written = result.bytesWritten, indeterminate = true, label = "验证签名...")
    store.update(record.copy(stage = VerifyingSignature, bytesWritten = result.bytesWritten, etag = result.etag))

    val verifierResult = ApkSignatureVerifier.verify(
        apkFile = File(record.destPath),
        expectedCertSha256 = record.signerCertSha256,
    )
    when (verifierResult) {
        is VerifierResult.Match -> {
            store.update(record.copy(stage = ReadyToInstall, bytesWritten = result.bytesWritten))
            notifier.postReady(record, versionName)
            // Service 任务结束
            stopForeground(STOP_FOREGROUND_REMOVE)  // 移除 ongoing 通知
            stopSelf()
        }
        is VerifierResult.Mismatch -> {
            // 签名不匹配 = 严重事件,删文件 + 清记录
            File(record.destPath).delete()
            store.delete(record.downloadId)
            notifier.postFailed(record, reason = CertMismatch)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    // 把 state 推回 Repository(走 UpdateState.ReadyToInstall 或 Failed)
    UpdateRepository.onDownloadVerified(record, verifierResult, File(record.destPath))
}
```

`ApkSignatureVerifier.verify` 已有(参考 v1 spec §1.3 + UpdateRepository.verifySignatureForDownload)。扩展为返回 sealed `VerifierResult` 以便区分 Match / Mismatch(目前是 nullable Boolean)。

### 5.4 取消语义

| 触发 | 行为 |
|---|---|
| 通知的 [取消] 按钮 | `PendingIntent.getService(ACTION_CANCEL)` → Service `stopForeground(STOP_FOREGROUND_REMOVE)` + 删 partial + `store.delete(downloadId)` + 推 `Failed(Cancelled)` + `stopSelf()` |
| 应用内 [取消] 按钮(`UpdateSection`) | 同上,只是从 ViewModel 触发 |
| 用户最近任务上划杀进程 | Service 死,但 partial 文件 + DataStore 记录留着。下次打开 App,`UpdateResumeCoordinator` 自动续传(见 §5.5)。**不**翻 `Failed(Cancelled)` —— 用户的本意是杀 App 不是取消下载。 |
| `adb shell am force-stop` | 同上 |

### 5.5 冷启动自动续传(`UpdateResumeCoordinator`)

`IceSpiritApplication.onCreate`:
```
val records = store.all()
records.forEach { record ->
    val file = File(record.destPath)
    when {
        // 已落盘 + 已校验 → 恢复 ReadyToInstall 状态(用户回 App 直接看 [立即安装])
        record.stage == ReadyToInstall && file.length() == record.totalBytes -> {
            UpdateRepository.setReadyToInstall(file, record.versionName)
            notifier.postReady(record, record.versionName)   // 重新挂通知(可能被系统清了)
        }
        // 校验中断(verify 步骤被杀) → Coordinator 自己跑 verify,不需 FGS
        record.stage == VerifyingSignature && file.length() == record.totalBytes -> {
            runVerifyAndTransition(record)
        }
        // 下载中断 → 文件大小对齐 → 启动 worker 接力起 Service 续传
        record.stage == Downloading && record.bytesWritten > 0
            && file.length() == record.bytesWritten -> {
            OneTimeWorkRequestBuilder<UpdateResumeWorker>()
                .setInputData(workDataOf("downloadId" to record.downloadId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(CONNECTED).build())
                .build()
                .also { WorkManager.getInstance(this).enqueueUniqueWork(
                    "resume-${record.downloadId}", ExistingWorkPolicy.KEEP, it) }
        }
        // 其他(文件大小对不上 / 文件丢了 / 0 字节):清记录 + 删文件
        else -> {
            file.delete()
            store.delete(record.downloadId)
        }
    }
}
```

`UpdateResumeWorker`(一次性,ApplicationContext 驱动):
- 在 `doWork()` 里 `Context.startForegroundService(Intent(ACTION_DOWNLOAD, resume=true))`。
- 返回 `Result.success()`。**Worker 本身不下载**,接力完即返回,下载由 Service 接管。
- 这样 Worker 死掉不影响下载。

`runVerifyAndTransition(record)`:不启 Service(verify 只需要 ~50 ms,纯 CPU/IO);直接调 `ApkSignatureVerifier.verify(...)`,on Match → `UpdateRepository.setReadyToInstall(...)` + `notifier.postReady(...)`,on Mismatch → `file.delete()` + `store.delete(...)` + `UpdateRepository.setFailed(CertMismatch)`。**不发下载失败通知**(用户没在下载),仅在 App 内 UI 提示。

**幂等:** Service `onStartCommand` 检测同 `downloadId` 已有 in-flight job → 直接忽略新 intent(§5.1 第 3 步)。

### 5.6 [重试] 按钮的语义(从 v1 spec §5.5 扩展)

v1 spec `Failed.DownloadInterrupted` 走"回到 UpdateAvailable 重新点 [下载]"。本期改为:

| Failed 子类 | [重试] 行为 |
|---|---|
| `Cancelled` | 不显示 [重试],引导用户回 [下载更新] 入口 |
| `NetworkUnreachable` | 保留 partial + DataStore,`startService(ACTION_DOWNLOAD, resume=true)` 走 Range |
| `Other` | 同 `NetworkUnreachable`(partial + DataStore 都在) |
| `CertMismatch` | partial 已删,从头下载(`resume=false`) |
| `NoNetwork` / `ServerError` / `ParseError`(v1 spec 检查阶段失败) | 重跑 `checkForUpdates`,不变 |

**关键不变量:** [重试] 不删 partial(除非 cert mismatch),总是尝试从已有字节继续。`Cancelled` 明确删除是因为用户主动取消的意图优先。

### 5.7 通知 — 何时 / 如何更新

`UpdateDownloadNotifier`:

```kotlin
fun updateProgress(record: DownloadRecord, written: Long, indeterminate: Boolean = false, label: String? = null) {
    val notif = NotificationCompat.Builder(context, CHANNEL_ONGOING)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setContentTitle(context.getString(R.string.update_notif_title))
        .setContentText(label ?: context.getString(R.string.update_notif_progress,
            written / 1e6, record.totalBytes / 1e6, (written * 100 / record.totalBytes).toInt()))
        .setProgress(record.totalBytes.toInt(), written.toInt(), indeterminate)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(R.drawable.ic_cancel, context.getString(R.string.update_cancel),
            pendingServiceIntent(ACTION_CANCEL, record.downloadId))
        .build()
    NotificationManagerCompat.from(context).notify(notifId(record), notif)
}

fun postReady(record: DownloadRecord, versionName: String) {
    val installPi = pendingActivityIntent(ACTION_INSTALL, record)
    val laterPi = pendingActivityIntent(ACTION_LATER, record)
    val notif = NotificationCompat.Builder(context, CHANNEL_READY)
        .setSmallIcon(R.drawable.ic_stat_download_ready)
        .setContentTitle(context.getString(R.string.update_notif_ready_title, versionName))
        .setContentText(context.getString(R.string.update_notif_ready_body, versionName))
        .setAutoCancel(true)
        .addAction(R.drawable.ic_install, context.getString(R.string.update_install), installPi)
        .addAction(R.drawable.ic_later, context.getString(R.string.update_later), laterPi)
        .build()
    NotificationManagerCompat.from(context).notify(notifId(record), notif)
}
```

`pendingActivityIntent(ACTION_INSTALL, record)` 内构 `Intent(ACTION_VIEW)` 走 `FileProvider`(调用方 `UpdateDownloadNotifier` 接受 `record.destPath`,由 `ACTION_INSTALL` receiver 在 `IceSpiritVisionActivity` 收到后复用 `UpdateRepository.requestInstall(activity, file)` 已有的逻辑)。

进度通知节流:`onProgress` 回调里 `throttleFirst(500.milliseconds)` 后再调 `notifier.updateProgress`,避免通知系统过载。

### 5.8 POST_NOTIFICATIONS 运行时权限

- 检查点:`SettingsScreen` `onResume` 时;若 `Build.VERSION.SDK_INT >= 33 && !hasPermission(POST_NOTIFICATIONS)` 且 Settings 第一次进入 → 弹 `ActivityResultContracts.RequestPermission`。
- **关键事实:** 即便用户拒绝 POST_NOTIFICATIONS,`Service.startForeground()` 仍能调通(FGS 只需要合法 Notification 对象,不要求通知真的可见)。只是通知栏不显示进度,但 App 内 `UpdateSection` 进度条照常更新,功能不丢。
- 文案:`update_notification_rationale` = "允许通知可在锁屏时查看下载进度"。

## 6. UI 表面

### 6.1 UpdateSection 新增

| State | 渲染变化(沿用 v1 spec §6.2 + 以下新增) |
|---|---|
| `Downloading(d, t)` | 已有 `LinearProgressIndicator` + 文案;**新增** [取消] `TextButton` 触发 `viewModel.cancel()` |
| `Failed(DownloadInterrupted.Cancelled)` | 浅灰 banner: "已取消"(无 [重试] 按钮,用户回到 [下载更新] 入口) |
| `Failed(DownloadInterrupted.NetworkUnreachable)` | 红 banner: "网络不可达,请重试" + [重试] 按钮 |
| `Failed(DownloadInterrupted.Other)` | 红 banner: "下载失败,请重试" + [重试] 按钮(沿用 v1 spec) |
| `Failed(SignatureMismatch)`(沿用 v1 spec) | 红 banner: "签名校验失败,请联系开发者" + [重试] 按钮(从头下载,服务端可能换了签名) |

`SettingsViewModel` 新增:
```kotlin
fun cancel()                    // → UpdateRepository.cancel(downloadId)
```

`ReadyToInstall` 状态恢复由 `UpdateResumeCoordinator` 在 `Application.onCreate` 时直接调 `UpdateRepository.setReadyToInstall(file, versionName)`,Repository 的 `MutableStateFlow<UpdateState>` 自动推给 `SettingsViewModel.updateState`(已沿用 v1 spec §5)。ViewModel 无需新增监听方法。

## 7. 资源 / 配置

### 7.1 AndroidManifest.xml 新增

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<!-- 在 <application> 内,和 IceSpiritVisionActivity 同级 -->
<service
    android:name=".updater.service.UpdateDownloadService"
    android:exported="false"
    android:foregroundServiceType="dataSync"
    android:stopWithTask="false"/>
```

- `FOREGROUND_SERVICE` —— API 28+ 所有 FGS 必需。
- `FOREGROUND_SERVICE_DATA_SYNC` —— Android 14 (API 34) 起,targetSdk ≥ 34 时每个 FGS 必须声明具体 type([参考](https://developer.android.com/about/versions/14/changes/fgs-types-required))。`dataSync` 用于下载网络数据,正合此场景。
- `POST_NOTIFICATIONS` —— Android 13 (API 33) 起前台通知运行时权限。
- `android:stopWithTask="false"` —— 用户最近任务上划时**不**连带杀 Service;`UpdateResumeCoordinator` 在下次启动时捡起来。
- `android:exported="false"` —— 内部 Service,只能由自己 start。

### 7.2 `network_security_config.xml`(已存在)

不变(沿用 v1 spec §7.2)。`125.211.45.14:3000` 已允许 cleartext,`HttpURLConnection.setInstanceFollowRedirects(true)` 沿用 v1 spec 默认(本期不做 redirect refusal)。

### 7.3 strings.xml 新增

| Key | 中文 |
|---|---|
| `update_notif_title` | "下载更新中" |
| `update_notif_progress` | "%1$.1f / %2$.1f MB (%3$d%%)" |
| `update_notif_verifying` | "验证签名..." |
| `update_notif_ready_title` | "可安装新版本 v%1$s" |
| `update_notif_ready_body` | "v%1$s 已下载完成" |
| `update_notif_failed_title` | "下载失败" |
| `update_install` | "立即安装" |
| `update_later` | "稍后" |
| `update_cancel` | "取消" |
| `update_failed_cancelled` | "已取消" |
| `update_failed_network_unreachable` | "网络不可达,请重试" |
| `update_failed_cert_mismatch` | "签名校验失败,请联系开发者" |
| `update_notification_rationale` | "允许通知可在锁屏时查看下载进度" |

`update_failed_download`(沿用 v1 spec)与 `update_retry_button` 不变。

### 7.4 资源图标

- `res/drawable/ic_stat_download.xml`(24dp,白色 vector)—— 通知小图标,NOTIF 必须有。
- `res/drawable/ic_stat_download_ready.xml`(同上,绿色调)—— ReadyToInstall 通知。
- `res/drawable/ic_cancel.xml` / `ic_install.xml` / `ic_later.xml` —— 通知 action 图标。

## 8. Gradle / 依赖

新增依赖(`app/build.gradle.kts` `dependencies { }`):
```kotlin
implementation(libs.androidx.datastore.preferences)  // 已在 libs.versions.toml
implementation(libs.androidx.work.runtime.ktx)        // 已在 libs.versions.toml
```

不引入新三方依赖。`HttpURLConnection` 续用。

`buildConfigField` 不新增。

## 9. 错误处理

| 失败类型 | 用户可见文案 | 来源 |
|---|---|---|
| `Failed.DownloadInterrupted.Cancelled` | "已取消" | 用户主动 |
| `Failed.DownloadInterrupted.NetworkUnreachable` | "网络不可达,请重试" | 重试 3 次后 |
| `Failed.DownloadInterrupted.Other` | "下载失败,请重试" | 其他 IOException 兜底 |
| `Failed.SignatureMismatch`(沿用 v1 spec) | "签名校验失败,请联系开发者" | cert-pin 不通过 |
| `Failed.NoNetwork`(沿用 v1 spec) | "无法连接服务器,请检查网络" | checkForUpdates 阶段 |
| `Failed.ServerError(code)` | "服务器返回 HTTP {code}" | 同上 |
| `Failed.ParseError(cause)` | "更新信息格式错误" | 同上 |

**永远不**把 `Throwable.message` / URL / host 暴露给用户(沿用 v1 spec §9 约定)。`cause` 字段仅用于 `Log.w("UpdateDownloadService", "...", cause)`。

## 10. 测试

### 10.1 JVM 单测

| 测试类 | 覆盖 |
|---|---|
| `ApkDownloaderTest` | mock `HttpURLConnection` 断言:① 首次请求不带 `Range`;② `resumeFrom=N` 带 `Range: bytes=N-` + `If-Range: <etag>`;③ 收到 200 → 删旧文件从头;④ 收到 206 → append;⑤ 416 → 抛 `RangeNotSatisfiable`;⑥ SHA-256 流式计算与整文件 cert-pin 一致;⑦ 500 → `Retryable`。 |
| `DownloadStateStoreTest` | DataStore round-trip(用 `MultiProcessDataStoreFactory` 或临时目录);partial 文件大小一致性断言;删除流程不残留。 |
| `UpdateResumeCoordinatorTest` | 模拟 `Application.onCreate` 触发 + DataStore 有 Downloading 记录 + 文件大小匹配 → 期望 enqueue `UpdateResumeWorker`;不匹配 → 清记录 + 不 enqueue。 |
| `UpdateRepositoryDownloadDelegateTest`(扩展 v1 spec 的 `UpdateRepositoryDownloadTest`) | `download()` 委托给 `startService`;service 状态回推 → StateFlow 翻转正确;`Failed(DownloadInterrupted.Cancelled)` 触发条件。 |

### 10.2 androidTest(踩 CLAUDE.md 记录的所有坑)

| 测试类 | 覆盖 |
|---|---|
| `UpdateDownloadServiceColdTest` | cold 启动时间(`cold_ms`)、warm 续传时间(`warm_total_ms` / `warm_avg_ms`)。沿用 harness 模式(CLAUDE.md §"Instrumented test / 真机 A/B" 段)。 |
| `UpdateResumeCoordinatorAndroidTest` | 预置 8 MB partial + DataStore 记录 → 启动 Application → 断言 Service 被拉起 + Range 请求字节正确。Fixture 图走 `app/src/androidTest/assets/`(CLAUDE.md §"`app/src/androidTest/assets/`" 段)。 |
| `CancelFromNotificationTest` | 用 `NotificationManagerCompat.getActiveNotifications` 找到 progress notif → 通过 `PendingIntent.getBroadcast` 模拟点 [取消] → 断言 partial 删除 + DataStore 清空 + state 翻 `Failed(Cancelled)`。 |
| `ProcessKillResumeTest`(关键回归) | 1) 启动下载到 30%;2) `adb shell am force-stop com.icespiritai.vision`(或 UI 模拟上划);3) 重启 App;4) 断言自动续传 + 最终 SHA-256 + cert-pin 通过。这是"扛用户上划杀进程"场景的真值。 |

### 10.3 手工 smoke(写在 `docs/smoke/2026-08-22-update-fgs-resume.md`)

1. **锁屏下载**:开始下载 → 立刻锁屏 → 1 分钟后回到 App,看通知 + App 内进度(应已 ~100% / 进入 ReadyToInstall)。
2. **Wi-Fi 切换**:下载到 50% → 切到 4G → 看 2/4/8 s 退避 → 切回 Wi-Fi → 看续传。
3. **飞行模式**:飞行模式下载 → 3 次失败 → 看 "网络不可达,请重试" → 关闭飞行模式 → [重试] 走 Range(不丢之前的 50%)。
4. **上划杀进程**:下载到 30% → 上划 → 重开 App → 看自动续传且不弹 [重试] 卡片。
5. **POST_NOTIFICATIONS 拒绝**:权限弹窗选拒绝 → 开始下载 → FGS 仍工作,App 内进度条正常,通知栏不显示。
6. **签名校验失败**:手动改 partial 文件 1 字节(用 `adb shell dd`)→ 重启 App → 触发校验 → 看 "签名校验失败" + 文件被清。

## 11. 依赖与兼容性

- 新增依赖 2 个:`androidx.datastore:datastore-preferences`、`androidx.work:work-runtime-ktx`(均已在 libs.versions.toml 中)。先确认 `libs.versions.toml` 已有版本号;若没有则补 `1.1.x` / `2.9.x`(与项目 baseline 同步,见 `docs/knowledge/build-stack-2026-08.md`)。
- 兼容 `shell` / `ice_ocr_rules` 两个 modelProfile:整套改动放 `src/main/`,与 profile 无关(沿用 v1 spec §11)。
- 与 2026-08-17 v1 spec 兼容:`UpdateState` 不变;`UpdateCheckResult.Failed.DownloadInterrupted` 是 sealed 重构,需更新 v1 spec 引入时的两个 test(`UpdateRepositoryDownloadTest`、`UpdateSectionTest` if any)以及 `UpdateSection.failureLabel()` 分支。
- targetSdk=37 触发 Android 14+ FGS type 必填;已在 manifest 加 `FOREGROUND_SERVICE_DATA_SYNC`(§7.1)。
- minSdk=26:`Service.startForeground()` API 26+ 提供,无需兼容 API 25。

## 12. 后续 Phase(本次不做)

| Phase | 范围 |
|---|---|
| Phase 2+ (release 安全护栏) | 沿用 v1 spec §12:URL allowlist + SHA-256 校验 + cert-pin 启动期校验 + anti-rollback floor + redirect refusal + manifest body cap |
| Phase 2+ (CI) | `uploadVisionReleaseToGitea` 任务接 `gradle.token.properties` 自动 push |
| Phase 3 | 自动静默下载(用户进入 App 就开始,完成后仅显示通知) |
| Phase 3 | 累计下载计数展示(`apkCumulativeDownloads` 字段已在 schema) |
| Phase 3 | 增量 / patch 更新(bsdiff / courgette) |

---

## 附:文件清单

**新增(13 个)**

```
app/src/main/java/com/icespiritai/offline/updater/ApkDownloader.kt
app/src/main/java/com/icespiritai/offline/updater/DownloadStateStore.kt
app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt
app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadNotifier.kt
app/src/main/java/com/icespiritai/offline/updater/service/UpdateResumeCoordinator.kt
app/src/main/java/com/icespiritai/offline/updater/service/UpdateResumeWorker.kt
app/src/main/java/com/icespiritai/offline/IceSpiritApplication.kt
app/src/main/res/drawable/ic_stat_download.xml
app/src/main/res/drawable/ic_stat_download_ready.xml
app/src/main/res/drawable/ic_cancel.xml
app/src/main/res/drawable/ic_install.xml
app/src/main/res/drawable/ic_later.xml
app/src/test/java/com/icespiritai/offline/updater/ApkDownloaderTest.kt
app/src/test/java/com/icespiritai/offline/updater/DownloadStateStoreTest.kt
app/src/test/java/com/icespiritai/offline/updater/UpdateResumeCoordinatorTest.kt
app/src/androidTest/java/com/icespiritai/offline/updater/UpdateDownloadServiceColdTest.kt
app/src/androidTest/java/com/icespiritai/offline/updater/UpdateResumeCoordinatorAndroidTest.kt
app/src/androidTest/java/com/icespiritai/offline/updater/CancelFromNotificationTest.kt
app/src/androidTest/java/com/icespiritai/offline/updater/ProcessKillResumeTest.kt
```

**修改(8 个)**

```
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt
app/src/main/java/com/icespiritai/offline/updater/AppVersionInfo.kt
app/src/main/java/com/icespiritai/offline/ui/settings/UpdateSection.kt
app/src/main/java/com/icespiritai/offline/ui/settings/SettingsViewModel.kt
app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt   # 安装 intent receiver
app/build.gradle.kts                                                  # 加 2 个依赖 implementation
```

预计新增代码量:~1200 行 Kotlin / ~80 行 XML / 0 行 Gradle 任务逻辑(只加 2 行 dependencies)。

---

## 附:与 sibling 项目的对比

冰灵智译 v1.38.0 的更新机制已落地 FGS + 进度通知 + Range 续传全套。本 spec 与其对齐:

| 维度 | 智译 v1.38.0 | 锐目 v1.0(本 spec) |
|---|---|---|
| FGS 类型 | `dataSync` | `dataSync`(同) |
| Range 续传 | 有 | 有(同) |
| cert-pin | release keystore 钉死 | release keystore 钉死(`4a21f4…3043`,沿用 v1 spec) |
| 后台持久化 | DataStore Preferences | DataStore Preferences(同) |
| 取消语义 | Service stopForeground + 清文件 | Service stopForeground + 清文件(同) |
| 冷启动自动续传 | `Application.onCreate` 扫记录 | 同 |
| 通知 channel 数量 | 3(ongoing/ready/failed) | 3(同) |
| 进度节流 | 500 ms throttleFirst | 500 ms throttleFirst(同) |

锐目本次 FGS + 续传方案可作为智译下个 Phase 借鉴;反过来智译的 7 条安全护栏(URL allowlist + SHA-256 + cert-pin + anti-rollback + redirect refusal + manifest body cap + 自动 push)是锐目 Phase 2+ 的对照清单。