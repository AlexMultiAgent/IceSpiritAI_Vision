# 冰灵锐目 端内自动更新机制 — 设计规范 v1

| 项 | 值 |
|---|---|
| 文档版本 | v1.0.0 |
| 日期 | 2026-08-17 |
| Phase | 后续(运营 / 装机渠道) |
| Spec 状态 | 待评审 |
| 关联项目根指令 | `CLAUDE.md` |
| 关联 baseline | `docs/knowledge/build-stack-2026-08.md` |
| 关联 init spec | `docs/superpowers/specs/2026-08-13-icespirit-vision-init-design.md`(仅骨架 / 命名空间仍生效) |
| 关联 sibling spec | `D:\GitHub\IceSpiritAI_Translate\docs\superpowers\specs\2026-07-31-v1.38.0-update-mechanism-design.md` |
| 关联 sibling 源码 | `D:\GitHub\IceSpiritAI_Translate\app\src\main\java\com\icespiritai\offline\updater/*`(9 个类 + 完整护栏) |

---

## 1. 背景与目标

### 1.1 现状

冰灵锐目当前仅 `0.1.0` 一版,无任何更新通道。每次迭代需要用户卸载 + 重装,后续装机量起来后是不可接受的运营摩擦。

### 1.2 目标

在 Settings 里提供"检查更新"入口,客户端按 Gitea 上的 `vision-latest.json` 检查版本,下载新 APK,交给系统 package installer 走标准 APK 安装流程。

### 1.3 与 sibling 的关系

冰灵智译(translate)在 v1.38.0 起落地了完整的端内更新机制,代码在 `D:\GitHub\IceSpiritAI_Translate\app\src\main\java\com\icespiritai\offline\updater/`(9 个类)+ Gradle 任务链(`generateLatestJson` → `archiveLatestRelease` → `uploadToGitea`)+ 一整套安全护栏(URL allowlist / SHA-256 / cert pinning / anti-rollback floor / FGS / redirect refusal / manifest body cap)。

冰灵锐目本次**复用 translate 的模式骨架**(模块拆分、URL 布局、JSON schema、Gradle 任务分层),但**首版只实现最小版**:不做上述 7 条安全护栏,理由是:

- 当前只有 `0.1.0` 一个 release,release signing keystore 都还没稳定,cert pinning 没有可钉的固定指纹;
- 装机量 < 内部测试,任何 dev / QA 通过 vision-app 推 debug APK 即可,不涉及用户面;
- 后续 Phase 2+ 上 release 后,按需补回 translate 的护栏(URL allowlist + cert pinning 一次性接入,其余是已存在的代码)。

### 1.4 非目标(本期不做)

- URL allowlist 校验
- APK SHA-256 校验
- Install cert pinning
- Anti-rollback floor(`MINIMUM_UPDATE_VERSION_CODE = 0`)
- 自动重定向拒绝(`instanceFollowRedirects = true` 用 HttpURLConnection 默认)
- Manifest body 大小上限(64 KiB 那个 `MAX_MANIFEST_BYTES`)
- ForegroundService 包裹下载(下载随 Activity 销毁而取消,debug 用户重新触发即可)
- Gitea 自动 push(本地文件就绪后开发者手动 `git push` 到 `vision-app`,避免 token 入仓)
- 累计下载计数展示(`apkCumulativeDownloads` 字段保留在 JSON schema 但 client 始终按 0 处理,后续 Phase 2+ 复用 translate 的 `download-stats.json` 方案)

## 2. 技术 baseline

| 项 | 值 |
|---|---|
| AGP | 9.3.x |
| Gradle | 9.7.x |
| Kotlin | 2.4.10 |
| JDK | 17 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| ABI | arm64-v8a only |
| 序列化 | `kotlinx.serialization-json`(`libs.kotlinx.serialization.json`,已在 dependencies) |
| HTTP | `java.net.HttpURLConnection`(无 OkHttp 依赖) |
| UI | Compose Material3(已在 dependencies) |
| 状态管理 | `MutableStateFlow` + `viewModelScope` |
| 安装触发 | `FileProvider` + `Intent.ACTION_VIEW`(`applicationId.fileprovider`) |

不引入新三方依赖。

## 3. 模块拆分

新增模块全部落在 `app/src/main/java/com/icespiritai/offline/updater/`,与 translate 同目录布局,便于后续跨项目比对 / 复用:

```
app/src/main/java/com/icespiritai/offline/updater/
├── AppVersionInfo.kt    // @Serializable data class + UpdateCheckResult sealed
├── UpdateState.kt       // UI 用 sealed(顶层,与 translate MainUiState 风格一致)
└── UpdateRepository.kt  // object 单例,持 MutableStateFlow,封装 fetch/download/install
```

UI 层落在 `app/src/main/java/com/icespiritai/offline/ui/settings/`:

```
app/src/main/java/com/icespiritai/offline/ui/settings/
├── UpdateSection.kt          // 新增:Compose UI 块
├── SettingsScreen.kt         // 修改:插入 UpdateSection
└── (settings/SettingsViewModel.kt)  // 修改:暴露 updateState + 4 个 action
```

启动钩子在 `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`(`onCreate` 末尾触发静默 `UpdateRepository.checkForUpdates()`)。

## 4. 数据形状

### 4.1 JSON manifest(`vision-latest.json`)

完整镜像 translate 的 `AppVersionInfo` schema,7 字段,字段顺序与 `LatestJsonGenerator.buildLatestJson` 的手写 JSON 一致(`kotlinx.serialization` 按声明顺序序列化,保证 diff 稳定):

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision-update.apk",
  "apkSize": 18392192,
  "apkSha256": "<64 hex>",
  "changelog": "## v0.2.0\n- 修复...\n- 新增...",
  "apkCumulativeDownloads": 0
}
```

### 4.2 URL 约定

| 项 | 值 |
|---|---|
| 主机 / 端口 / 协议 | `125.211.45.14:3000` / `http`(Gitea 当前未启用 TLS,cleartext 已在 `network_security_config.xml` 允许) |
| Gitea base path | `/giteaadmin/vision-app/releases/download/latest` |
| Manifest 文件名 | `vision-latest.json` |
| APK 文件名(客户端下载的) | `icespiritai-vision-update.apk` |
| 本地缓存路径 | `cacheDir/update/icespiritai-vision-update.apk` |
| 版本化归档名(服务端多版本留存) | `icespiritai-vision-v0.X.Y.apk` |
| 上传 staging 目录 | `D:\GitHub\IceSpiritAI_Vision\发布版历史存档\最新版改名上传\` |

`BuildConfig.UPDATE_JSON_URL` 直接 hardcode `http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json`(不走 git tag 推导)。

### 4.3 UpdateState(UI 用 sealed)

顶层 sealed,Compose 用 `collectAsStateWithLifecycle` 直接消费:

```kotlin
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val currentVersionCode: Int) : UpdateState()
    data class UpdateAvailable(val info: AppVersionInfo) : UpdateState()
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Failed(val result: UpdateCheckResult.Failed) : UpdateState()
}
```

### 4.4 UpdateCheckResult(repository 内部 sealed)

镜像 translate,但**精简到 4 个 Failed subtype**(去掉 `HashMismatch` + `VersionCodeTooOld` + `CertMismatch`):

```kotlin
sealed class UpdateCheckResult {
    data class UpToDate(val current: Int) : UpdateCheckResult()
    data class UpdateAvailable(val info: AppVersionInfo) : UpdateCheckResult()
    sealed class Failed(val reasonTag: String) : UpdateCheckResult() {
        object NoNetwork : Failed("no_network")
        data class ServerError(val httpCode: Int) : Failed("server_$httpCode")
        data class ParseError(val cause: Throwable) : Failed("parse")
        data class DownloadInterrupted(val cause: Throwable) : Failed("interrupted")
    }
}
```

## 5. 行为规约

### 5.1 启动静默检查

`IceSpiritVisionActivity.onCreate` 在 setContent 之后,launch 一个 `lifecycleScope.launch { UpdateRepository.checkForUpdates(BuildConfig.VERSION_CODE) }`(lifecycleScope 而非 applicationScope — Activity 销毁时取消静默检查,避免冷启动路径上的网络请求 leak)。

静默检查只翻 `_state` 不显示 UI;state 进入 `UpdateAvailable` 后,用户进入 Settings 才看到 banner。

### 5.2 手动检查

`SettingsViewModel.refresh()` 触发 `UpdateRepository.checkForUpdates(BuildConfig.VERSION_CODE)`。如果已经在 `Checking` state,直接 return(debounce 防双击)。

### 5.3 下载

`SettingsViewModel.download(info: AppVersionInfo)` → `UpdateRepository.downloadApk(info, onProgress)`。Repository:

1. 创建 `cacheDir/update/`,存在则跳过
2. `URL(info.apkUrl).openConnection()` → `HttpURLConnection`
3. 同步阻塞写到 `cacheDir/update/icespiritai-vision-update.apk`,每 ~256 KiB / 500 ms 调一次 `onProgress(downloaded, total)`
4. 写完后 flip state 到 `ReadyToInstall(file)`
5. 任何 IOException / SecurityException → flip 到 `Failed.DownloadInterrupted(cause)`

**不**做 SHA-256 校验(本期最小版);**不**做 Range resume(失败重下即可)。

### 5.4 安装

`SettingsViewModel.install(file: File)` → `UpdateRepository.requestInstall(activity, file)`。Repository:

1. 用 `FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.fileprovider", file)` 拿 URI
2. `Intent(ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")` + `FLAG_GRANT_READ_URI_PERMISSION`
3. `activity.startActivity(intent)`(若 `startActivity` 抛 `ActivityNotFoundException`,fallback 到 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` 让用户去开权限)

### 5.5 重试

`SettingsViewModel.retry()` → 根据当前 `Failed.result` 决定重试模式:
- `NoNetwork` / `ServerError` / `ParseError` → 重跑 `checkForUpdates`
- `DownloadInterrupted` → 不自动重试(避免用户误以为在后台重下);UI 重新进入 `UpdateAvailable` 让用户重新点"下载"

## 6. UI 表面

### 6.1 SettingsScreen 布局

现有布局(SettingsScreen.kt):
```
[Scaffold TopAppBar]
[AppearanceSection(主题切换)]    ← 已存在
[HorizontalDivider]
[Spacer 16dp]
[版本号 row]                      ← 已存在
```

修改后:
```
[Scaffold TopAppBar]
[UpdateSection]                   ← 新增:基于 UpdateState 分支渲染
[HorizontalDivider]
[AppearanceSection]
[HorizontalDivider]
[Spacer 16dp]
[版本号 row]
```

### 6.2 UpdateSection 内部(根据 UpdateState 分支)

| State | 渲染 |
|---|---|
| `Idle` | 一行 "检查更新" 按钮 |
| `Checking` | 按钮 disabled + 14dp spinner + "正在检查..." |
| `UpToDate(code)` | 浅蓝 banner: "已是最新 v0.X.Y" |
| `UpdateAvailable(info)` | 黄色 banner: "新版本 v0.X.Y 可用",展开后显示 `info.changelog` 文本(Markdown 不解析,纯文本 pre-wrap)+ "下载并安装" 按钮 |
| `Downloading(d, t)` | `LinearProgressIndicator(progress = d/t)` + "下载中 X.X / Y.Y MB" |
| `ReadyToInstall(file)` | 绿色 banner: "下载完成,点击安装" → 触发 install intent |
| `Failed(result)` | 红色 banner + 重试按钮(根据 subtype 展示具体提示) |

文案放在 `strings.xml`,详细列表见 §7.4。

### 6.3 SettingsViewModel 接口

```kotlin
class SettingsViewModel(...) {
    val updateState: StateFlow<UpdateState>      // 取自 UpdateRepository.state
    fun refresh()                                 // 手动检查(debounce vs Checking)
    fun download(info: AppVersionInfo)            // 启动下载
    fun install(file: File)                       // 触发 install intent
    fun retry()                                   // 失败后重试
}
```

Compose 调用方式:`val state by viewModel.updateState.collectAsStateWithLifecycle()`,然后 `when (state) { ... }`。

## 7. 资源 / 配置

### 7.1 AndroidManifest.xml 新增

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

> `POST_NOTIFICATIONS` 暂不发通知(本期无 FGS),但保留权限声明以备 Phase 2+ 复用。

### 7.2 `network_security_config.xml`(新建)

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">125.211.45.14</domain>
    </domain-config>
</network-security-config>
```

### 7.3 `file_provider_paths.xml`(修改)

在现有 `<cache-path name="evidence" path="evidence/" />` 后追加:

```xml
<cache-path name="update" path="update/" />
```

Authority `${applicationId}.fileprovider` 已经在 manifest 里声明过,无需修改。

### 7.4 strings.xml 新增

| Key | 中文(其他语言后续补) |
|---|---|
| `update_section_title` | "更新" |
| `update_check_button` | "检查更新" |
| `update_checking` | "正在检查..." |
| `update_up_to_date` | "已是最新 v%1$s" |
| `update_available_banner` | "新版本 v%1$s 可用" |
| `update_download_button` | "下载并安装" |
| `update_downloading` | "下载中 %1$.1f / %2$.1f MB" |
| `update_ready_to_install` | "下载完成,点击安装" |
| `update_failed_no_network` | "无法连接服务器,请检查网络" |
| `update_failed_server` | "服务器返回 HTTP %1$d" |
| `update_failed_parse` | "更新信息格式错误" |
| `update_failed_download` | "下载失败,请重试" |
| `update_retry_button` | "重试" |

## 8. Gradle 任务链

`assembleDebug` finalizedBy `archiveVisionDebug`:

| 任务 | 职责 |
|---|---|
| `archiveVisionDebug` | 在 `发布版历史存档/` 下创建 `icespiritai-vision-v0.X.Y.apk`(读 `BuildConfig.VERSION_NAME`) |
| `generateVisionLatestJson` | 复制同一 APK 成 `icespiritai-vision-update.apk` 到 `发布版历史存档/最新版改名上传/`,计算 SHA-256(写入 JSON 但客户端**不校验**,留作 Phase 2+),写 `vision-latest.json` |
| `uploadToGitea` | **本期不实现**,Phase 2+ 接入 translate 的 token-based REST API 方案 |

`BuildConfig.UPDATE_JSON_URL` buildConfigField 在 `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "UPDATE_JSON_URL",
    "\"http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json\"")
```

## 9. 错误处理

**所有 UI 可见的错误文案都来自 `strings.xml`,绝不把 `Throwable.message` / URL / host 暴露给用户**(对齐 translate 的 "Never put user-content in Throwable.message" 约定)。

| 失败类型 | 用户可见文案 |
|---|---|
| `NoNetwork`(UnknownHostException / SocketTimeoutException) | "无法连接服务器,请检查网络" |
| `ServerError(code)`(HTTP 非 2xx) | "服务器返回 HTTP {code}" |
| `ParseError(cause)`(JSON 反序列化失败 / 字段缺失) | "更新信息格式错误" |
| `DownloadInterrupted(cause)`(IOException / SecurityException) | "下载失败,请重试" |

`DownloadInterrupted` 的 `cause` 字段仅用于日志(`Log.w("UpdateRepository", "download failed: ${cause.javaClass.simpleName}")`),不进 UI。

## 10. 测试

| 测试类 | 覆盖 |
|---|---|
| `AppVersionInfoSerializationTest` | JSON round-trip;`ignoreUnknownKeys = true` 验证;缺字段 / 多字段两方向 |
| `UpdateRepositoryCheckTest` | 用 fake `HttpURLConnection`(同 translate `AppUpdaterTest` 模式)覆盖 UpToDate / UpdateAvailable / NoNetwork / ServerError / ParseError |
| `UpdateRepositoryDownloadTest` | 用 `Files.createTempDirectory` 覆盖下载完成、下载中断(IOException) |
| `VisionLatestJsonBuilderTest` | gradle 任务镜像的 `LatestJsonGenerator.buildLatestJson` 与 translate 同名类共享 fixture,断言生成的 JSON 能被 `AppVersionInfo` 反序列化 |
| `ArchiveVisionDebugTest` | 验证归档目录结构 + APK rename 正确性 |

UI 层(Compose)暂不写测试,留作 Phase 2+(`UpdateSection` 暂时靠手动 `adb logcat` + 截图验证)。

## 11. 依赖与兼容性

- 全部使用已在 `app/build.gradle.kts` dependencies 里的库(`kotlinx-serialization-json` / Compose Material3 / `androidx.core.content.FileProvider` / `androidx.activity.compose`)。
- 不引入新三方依赖。
- 兼容 `shell` / `ice_ocr_rules` 两个 modelProfile —— updater 模块在 `main` sourceSet,与 profile 无关。

## 12. 后续 Phase(本次不做)

| Phase | 范围 |
|---|---|
| Phase 2+ (release) | 接入 translate 的 7 条安全护栏:URL allowlist + SHA-256 校验 + cert pinning(release keystore fingerprint)+ anti-rollback floor + FGS 后台下载 + redirect refusal + manifest body cap |
| Phase 2+ (CI) | `uploadToGitea` 任务接 `gradle.token.properties`(`.gitignore`),REST API 自动 push 到 vision-app,接 `download-stats.json` 累计下载计数 |
| Phase 3 | 累计下载计数展示 + Settings → 关于页显示 |
| Phase 3 | 自动静默下载(用户进入 App 就开始下,完成后仅显示通知) |

---

## 附:文件清单

**新增(8 个)**

```
app/src/main/java/com/icespiritai/offline/updater/AppVersionInfo.kt
app/src/main/java/com/icespiritai/offline/updater/UpdateState.kt
app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt
app/src/main/java/com/icespiritai/offline/ui/settings/UpdateSection.kt
app/src/main/res/xml/network_security_config.xml
app/src/test/java/com/icespiritai/offline/updater/AppVersionInfoSerializationTest.kt
app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryCheckTest.kt
app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryDownloadTest.kt
```

**修改(7 个)**

```
app/src/main/AndroidManifest.xml                                # 加权限 + networkSecurityConfig
app/src/main/res/xml/file_provider_paths.xml                    # 加 <cache-path name="update" />
app/src/main/res/values/strings.xml                             # 加 13 条文案
app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt  # onCreate 触发静默检查
app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt  # 插入 UpdateSection
app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt # 暴露 updateState + 4 个 action
app/build.gradle.kts                                            # 加 buildConfigField + archiveVisionDebug 任务链
```

**新增 Gradle 镜像文件(2 个,在 `app/` 下)**

```
app/buildVisionLatestJson.gradle.kts    # 镜像 translate 的 buildLatestJson,生成 JSON
app/archiveVisionDebug.gradle.kts       # 归档 + 复制 staging
```

预计新增代码量:~800 行 Kotlin / ~150 行 XML / ~250 行 Gradle / ~30 行 strings。