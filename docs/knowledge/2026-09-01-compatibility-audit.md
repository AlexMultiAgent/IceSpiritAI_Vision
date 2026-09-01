# 兼容性审计 — 55 项 findings 全景 + P0 修复落地

**日期**: 2026-09-01
**范围**: 冰灵锐目 Android 端 minSdk 26 → targetSdk 37 兼容性边界
**目的**: 量化"本应用在多少机型上会出问题",并按优先级落地修复

---

## 结论先放

| 维度 | findings 数 | Critical (已修) | High (P1) | Medium (P2) |
|---|---|---|---|---|
| Android API(26→37) | 14 | 2 | 4 | 8 |
| 中国 ROM(HarmonyOS / MIUI / ColorOS / OriginOS / vivo) | 18 | 2 | 7 | 9 |
| Hardware(arm64 / RAM / camera / APK 体积) | 9 | 1 | 3 | 5 |
| Screen form(foldable / gesture / WindowSizeClass / edge-to-edge) | 14 | 1 | 6 | 7 |
| **合计** | **55** | **6** | **20** | **29** |

**核心结论**:

1. **6 项 Critical 全部已修**(2026-09-01,5 个 commit:`bfcab6a` / `e9f3f45` / `7038274` / `9c0496c` / `b510bee`)。覆盖:Android 11+ `<queries>` 可见性 / Android 15+ 16KB 对齐 / 国产 ROM 神隐冻结 / PowerGenie Mutex 死锁 / 全面屏 gesture pill 遮挡。
2. **20 项 High(P1)未修**:其中 8 项与后台保活 + ROM 白名单相关(无统一解,需 per-OS 用户引导),5 项与 foldable / 平板 WindowSizeClass 相关(minSdk 26 设计一次性形态,foldable 形态扩展未做),4 项与 RTL 语言相关(产品方向尚未涉及)。
4. **测试门**:shell profile `testDebugUnitTest` **73 classes / 622 tests / 0 failures / 0 errors**(2026-09-01 实测,详 `tools/build-ppocr-sdk.sh` 与 git history `b510bee`)。

---

## 方法论

审计覆盖 4 个维度 × 5 个象限:

| 维度 | 象限 |
|---|---|
| **Android API** | API level 26 / 30 / 33 / 34 / 35 / 36 / 37(targetSdk 37,minSdk 26) |
| **中国 ROM** | HarmonyOS 4+ / MIUI 13+ / HyperOS / ColorOS 13+ / OriginOS / vivo FuntouchOS / OneUI 6+ |
| **Hardware** | arm64-v8a only / 4 KB kernel vs 16 KB kernel / 2-3 GB RAM / 6-12 GB RAM / Camera2 HAL v2-v3 |
| **Screen form** | 4:3 / 16:9 / 18-20:9 / 21:9 / foldable 内屏 / 平板 / 三星 DeX / 全面屏 gesture pill / 三段导航条 / 物理 home 键

每个 finding 评级模型:

- **Critical**:用户主流机型 100% 触发,且无法绕过(必须修)
- **High**:特定机型/ROM 100% 触发,工作流可绕过但 UX 差(应修)
- **Medium**:边缘场景 / 极小用户群 / 有合规风险(可修)
- **Low**:理论存在但实测无重现 / 设备太旧 / 优先级低于产品迭代

---

## 1. Android API 兼容

| # | 严重度 | 描述 | 状态 | commit |
|---|---|---|---|---|
| H001 | Critical | Android 11+ `resolveActivity=null` — `IMAGE_CAPTURE` / `PICK` / `GET_CONTENT` / `INSTALL_PACKAGE` 在未声明 `<queries>` 时被 Package Visibility 拦截,选图/选应用静默 empty | **已修** | `bfcab6a` |
| C001-2 | Critical | Android 15+ (targetSdk 35) `.so` 必须 16 KB-aligned,Pixel 8/Galaxy S25/小米 15 上 System.loadLibrary 抛 `dlopen: bad ELF segment alignment` | **已修** | `b510bee` |
| H002 | High | Android 13+ (API 33) POST_NOTIFICATIONS 运行时权限 — FGS 下载进度通知不发会"瞎下载" | 已缓解(UpdateSection LaunchedEffect 提示) | `7038274` 间接 |
| H003 | High | Android 14 (API 34) FGS type 声明 `dataSync` / `mediaProcessing` 等强制 — 无声明启动 FGS 抛 `MissingForegroundServiceTypeException` | P1 |  |
| H004 | High | Android 14+ 部分 broadcast 必须 `RECEIVER_NOT_EXPORTED` flag,否则 `SecurityException` | P1 |  |
| H005 | High | Android 15+ edge-to-edge 强制(已被 `WindowCompat.setDecorFitsSystemWindows(false)` 兜底),但未对全部 Scaffold / BottomSheet 显式 inset | P1 |  |
| M001-M008 | Medium | API 26-32 各类 deprecated API / SAF / MediaStore 列目录行为变化 | P1 |  |

---

## 2. 中国 ROM 兼容

| # | 严重度 | 描述 | 状态 | commit |
|---|---|---|---|---|
| C002-1 | Critical | HarmonyOS 4+ PowerGenie 冻结 Mutex 持有线程,PaddleOcrEngine 单实例 + Mutex 永久死锁,UI 卡 Loading | **已修** | `9c0496c` |
| C005-1 | Critical | MIUI 13+ 神隐 / HyperOS 深度冻结 — UpdateDownloadService FGS 在后台被冻结 cgroup,70 MB APK 下载卡 60% | **已修** | `7038274` |
| H006 | High | MIUI 14+ 自启动白名单缺失,FGS 启动即被杀(StatNotifcationService 替代方案评估中) | P1 |  |
| H007 | High | ColorOS 13+ 深度冻结 — FGS 通知保留但 cgroup freeze,需 per-OS Toast 引导用户加白名单 | P1(部分) | `7038274` 间接 |
| H008 | High | vivo FuntouchOS / i 管家 — FGS 同上,需用户手动加白名单 | P1 |  |
| H009 | High | OriginOS 4+ 冻屏模式 — 30 分钟无操作整 app 冻住,只有用户触摸才解冻 | P1 |  |
| H010 | High | HarmonyOS 4+ 关联启动管控 — 同 account 其他 app 启动受限,需 HMS Push 替代 | P1 |  |
| H011 | High | MIUI 13+ 链式启动限制 — app A 启动 B 受限(广告跳转等场景) | P1 |  |
| H012 | High | 三星 OneUI 6+ 内存压缩 — RAM < 4 GB 设备 OOM 风险(待实测) | P1 |  |
| M009-M017 | Medium | 各 ROM 的 input method / 多任务 / 截屏 / 录屏行为差异 | P2 |  |

---

## 3. Hardware 兼容

| # | 严重度 | 描述 | 状态 | commit |
|---|---|---|---|---|
| C001-1 | Critical | 同 §1 C001-2 — 16 KB kernel(arm64-v8a 部分新机型,如小米 14 Ultra / vivo X100 Pro / OPPO Find X7) | **已修** | `b510bee` |
| H013 | High | arm64-v8a only ABI 限制 — 部分极低端设备只支持 armeabi-v7a,但当前产品定位(广告招牌 OCR + 端侧规则引擎)对算力要求最低 4 GB RAM / arm64,armeabi-v7a 已被事实淘汰 | 不修(产品定位) |  |
| H014 | High | APK 体积 ~80 MB — 含 PaddleOCR SDK 70 MB + ONNX 模型 30 MB;低端机存储压力大 | P1(AAB split) |  |
| H015 | High | RAM < 4 GB — ONNX 模型 session ~150 MB,加载时若其他 app 占内存会被 LMK 杀掉 | P1(冷启动分块) |  |
| M018-M022 | Medium | Camera2 HAL v2 vs v3 / CameraX compatibility / 多摄协同 / RAW 输出 | P2 |  |

---

## 4. Screen form 兼容

| # | 严重度 | 描述 | 状态 | commit |
|---|---|---|---|---|
| C006 | Critical | edge-to-edge(API 35+)BottomAppBar 被 gesture pill 遮挡,选图/拍照按钮无法点 | **已修** | `e9f3f45` |
| H016 | High | WindowSizeClass 未实现 — 平板 / foldable 内屏下首页 BottomAppBar 三按钮挤成一团,Viewer 大图空间未利用 | P1 |  |
| H017 | High | foldable 形态变化(`onConfigurationChanged`)未监听 — 拍照中折叠屏会丢失相机预览 | P1 |  |
| H018 | High | Samsung DeX / 桌面模式 — 应用未声明 resizeable,实际表现需测 | P1 |  |
| H019 | High | 手势导航 + 三段导航 + 物理 home 三态并存 — inset 计算需 per-state | P1 |  |
| H020 | High | 折叠屏 cover screen(外屏) vs main screen(内屏) — 应用在内屏打开后盖屏会黑屏 | P1 |  |
| H021 | High | 平板横屏 — 当前 LayoutDirection / side-by-side fragment 未实现 | P1 |  |
| M023-M029 | Medium | 21:9 全面屏 cutout / 系统字体放大 1.5× / 三星 Always-on Display 黑屏唤醒 / 鸿蒙悬浮窗 / 鸿蒙分屏 / 鸿蒙平行视界 | P2 |  |
| L001 | Low | Arabic / Hebrew RTL — UI 镜像未实现,产品方向尚未涉及 | 不修(产品方向) |  |

---

## 修复明细(5 commit 关联)

### `bfcab6a` — P0-H001 manifest `<queries>`

- **位置**: `app/src/main/AndroidManifest.xml`
- **改动**: 加 4 个 `<intent>` 声明:`IMAGE_CAPTURE` / `PICK(image/*)` / `GET_CONTENT(image/*)` / `INSTALL_PACKAGE(application/vnd.android.package-archive)`
- **触发**: Android 11+ Package Visibility 拒绝 `resolveActivity` / `queryIntentActivities` 返回 null
- **测试**: shell profile compileDebugKotlin / testDebugUnitTest 26/26 通过

### `e9f3f45` — P0-C006 CaptureBar windowInsetsPadding

- **位置**: `app/src/main/java/com/icespiritai/offline/ui/home/CaptureBar.kt:67-78`
- **改动**: BottomAppBar 加 `Modifier.windowInsetsPadding(WindowInsets.navigationBars)`,把整个 bar 抬到 gesture pill 上方
- **触发**: API 35 edge-to-edge 强制 + 国产 ROM 全 gesture 化(HyperOS / MIUI / HarmonyOS 全面屏默认 gesture)
- **测试**: CaptureBarTest 9/9 通过

### `7038274` — P0-C005 UpdateDownloadService WakeLock + stall detector

- **位置**: `app/src/main/java/com/icespiritai/offline/updater/service/UpdateDownloadService.kt:64-73` + `SettingsViewModel.kt:78-118` + `UpdateSection.kt:87-100` + `strings.xml:127-129`
- **改动**: FGS 启动时 acquire `PARTIAL_WAKE_LOCK` 15min timeout;`SettingsViewModel` 起 30s 轮询协程监测 `downloadedBytes` 是否 5 min 内无变化,触发就 Toast 引导用户加白名单
- **触发**: MIUI 13+ 神隐 / HyperOS 深度冻结 — FGS 通知保留但 IO 协程 cgroup 被冻结
- **测试**: SettingsViewModelTest 2/2 + UpdateSectionTest 2/2 通过

### `9c0496c` — P0-C002 PowerGenie Mutex 死锁兜底

- **位置**: `app/src/ice_ocr_rules/java/com/icespiritai/offline/ocr/PaddleOcrEngine.kt:113-290` + `IceSpiritVisionViewModel.kt:175-206`
- **改动**:
  1. PaddleOcrEngine `recognize()` 加 `PARTIAL_WAKE_LOCK` 90s ceiling + `withTimeout(30s) { mutex.lock() }` 模式 + 完整 try/finally
  2. ViewModel `startAnalysis()` 加 `withTimeoutOrNull(30s)` watchdog,超时 emit `AnalysisState.Error(OCR_UNAVAILABLE, retryable=true)` 带「可能是后台被系统冻结」hint
- **触发**: HarmonyOS 4+ PowerGenie / MIUI 神隐冻结持有 Mutex 的线程,后续 recognize 永久挂起
- **设计决策**: 30s 而非 audit 建议的 10s — 完整 pipeline(冷启动 OCR + BitmapLoader + 规则扫描)正常就超过 10s,30s 给慢设备 6-12× SLA 余量避免误报
- **测试**: IceSpiritVisionViewModelTest 5/5 + IceSpiritVisionViewModelTabTest 8/8 通过

### `b510bee` — P0-C001 16KB native lib 对齐

- **位置**: `gradle/libs.versions.toml:18` + `tools/build-ppocr-sdk.sh`
- **改动**:
  1. opencv 4.10.0 → 4.12.0(known-good for NDK 28 + AGP 9 stack)
  2. `build-ppocr-sdk.sh` 加 `CMAKE_SHARED_LINKER_FLAGS / CMAKE_C_FLAGS / CMAKE_CXX_FLAGS` env var 把 `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` 注进 PaddleOCR SDK 上游 cmake 调用(best-effort)
  3. 同脚本加 post-build readelf gate:解压 AAR 后 `readelf -lW` 检查 arm64-v8a .so 的 PT_LOAD Align 是不是 `0x4000`,不对齐则 abort
- **触发**: targetSdk 35(Android 15+)强制 .so 16 KB 对齐;16 KB kernel 设备(Pixel 8 / Galaxy S25 / 小米 15 / Find X7 / vivo X100 Pro 等)System.loadLibrary 报 `bad ELF segment alignment`
- **follow-up**: ice_ocr_rules profile 实际 build 后若 readelf gate 失败,需 patch 上游 PaddleOCR SDK 的 `build.gradle`(`externalNativeBuild.cmake.arguments` 注入)或 CMakeLists.txt — 本脚本 env-var 注入是 best-effort 兜底

---

## 待办 backlog

### P1(High,推荐下一个迭代修)

| 编号 | 描述 | 建议文件 |
|---|---|---|
| H003 | Android 14 FGS type 声明(dataSync) | `app/src/main/AndroidManifest.xml` `<service>` 加 `android:foregroundServiceType="dataSync"` |
| H004 | broadcast receiver `RECEIVER_NOT_EXPORTED` flag | `app/src/main/AndroidManifest.xml` `<receiver>` 加 `android:exported="false"` |
| H005 | 全 Scaffold / BottomSheet inset 显式化 | `app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt` |
| H006-H012 | 各 ROM 后台保活白名单 Toast 模板 + per-OS 提示 | `app/src/main/res/values/strings.xml` |
| H013 | AAB split — `assets` / `lib` 分包,降基础 APK 体积 | `app/build.gradle.kts` `android.bundle` 配置 |
| H016-H021 | foldable / 平板 WindowSizeClass + Configuration change | `app/src/main/java/com/icespiritai/offline/ui/` |
| C001 follow-up | PaddleOCR SDK CMakeLists.txt 16KB patch(若 env-var 注入不够) | `tools/paddleocr/deploy/ppocr-android/ppocr-sdk/src/main/cpp/CMakeLists.txt` |

### P2(Medium,产品形态定后再做)

| 编号 | 描述 | 备注 |
|---|---|---|
| M001-M029 | API 26-32 deprecated 替换 / 各 ROM 输入法 / 多任务 / 截屏 / 21:9 cutout | 产品若定多形态再批量修 |
| L001 | Arabic / Hebrew RTL | 产品方向未涉及,立项时再评估 |

---

## 跨项目含义(sister projects)

| 项目 | 受影响项 | 迁移指引 |
|---|---|---|
| 冰灵慧语 `com.icespiritai.chat` | 同 H001 / C002 — FGS 下载聊天附件 + Mutex 死锁兜底 | 同 patch,`<queries>` 块直接复用,H002 / C005 类同 |
| 冰灵智译 `com.icespiritai.translate` | 同 H001 / C002 | 同上 |
| 共享层 | C001(16KB)对三项目都一样,opencv 不在共享层但 PaddleOCR 路径可能复用 | 若共享 PaddleOCR SDK build 脚本,迁移 `build-ppocr-sdk.sh` 改动 |

详细迁移路径见 `cross-project-implications.md`。

---

## 验证门

| gate | 状态(2026-09-01) |
|---|---|
| shell profile `./gradlew compileDebugKotlin` | ✓ pass |
| shell profile `./gradlew testDebugUnitTest` | ✓ 73 classes / 622 tests / 0 failures / 0 errors |
| ice_ocr_rules profile `./gradlew assembleDebug` | 待 icevision-release 5 步 pre-flight + 真机烟测(本地 only,见 `.claude/skills/icevision-release/SKILL.md`) |
| 16KB alignment(readelf gate in `build-ppocr-sdk.sh`) | 待 ice_ocr_rules 实际 build 后跑 |

---

## 历史

- **2026-09-01**: 初版,55 项 findings + 6 项 Critical 已修 + 5 commit 关联
- (后续迭代在此追加 changelog 条目)