# 交接文档:「眼镜」按钮闪退修复(v0.4.3)

> 交接时间:2026-09-16 · 交接基点:branch `main`,HEAD `4fdfb68`,**改动未提交**
> 接手对象:下一个 AI / 开发者
> 读法:先读 §1 根因 + §2 已完成,再按 §4 顺序执行;§5 是命令速查,§6 是红线

---

## 1. 根因(已在真机上证实,不用再找)

现象:首页点「眼镜」按钮 → App 闪退(用户报告"设置上没有配对眼镜")。期望是给出提示。

根因:`GlassesDevice.findBondedDevice()` 裸调 `BluetoothAdapter.bondedDevices` 与
`BluetoothDevice.name`。这两个 API 在 **Android 12+(API 31+)需要运行时权限
`BLUETOOTH_CONNECT`**,而本仓只在 `AndroidManifest.xml` 里"声明"了它,**从未在运行时申请**
(`git log -S "RequestMultiplePermissions"` 全历史为空)。未申请的运行时权限 = 拒绝 →
第一次调用抛 `SecurityException`,发生在主线程的 Compose `onClick` 里,无人捕获 → 进程死亡。

为什么只在"未配对"时炸:`loadLastPaired()` 一旦有值(以前连过一次眼镜),代码在 store 分支
就返回了,永远走不到读系统配对列表那一步。store 为空(全新安装 / `pm clear`)才落到崩溃分支。

真机证据(设备 AGQV023313008161,只读 `dumpsys`,未改动设备状态):

```
Android 15 / SDK 35,ANN-AN00
com.icespiritai.vision 0.4.2 versionCode=77 targetSdk=37
  android.permission.BLUETOOTH_CONNECT: granted=false
  android.permission.BLUETOOTH_SCAN:    granted=false
```

完整证据链 + 范围决策写在 [docs/knowledge/2026-09-16-glasses-permission-crash.md](../knowledge/2026-09-16-glasses-permission-crash.md)。

**遗留的一个未解之谜(不影响结论)**:09-15 的冒烟测试(APK 0.3.4 / vc74)在这台 API 35
机器上跑通了含 `findBondedDevice` 的 overlay。可能解释:当时 `BLUETOOTH_CONNECT` 被人手动
开过(该机 `firstInstallTime` 是 2026-09-16 11:06,重装会把运行时权限重置为未授予),或
当时走的是 store 非空分支。**不要在这上面花时间**,修复后该链路不再依赖这个状态。

---

## 2. 已完成(代码改完、测试全绿、APK 已出,均未提交)

### 2.1 新增文件(8 个:4 个 production + 4 个测试)

| 文件 | 作用 |
| --- | --- |
| `app/src/main/java/com/icespiritai/offline/glasses/GlassesPermissions.kt` | 运行时权限闸门。v1 只申请 `BLUETOOTH_CONNECT`;`shouldShowRationale()` 区分"可再问"与"永久拒绝";不缓存结果(撤销权限会杀进程) |
| `app/src/main/java/com/icespiritai/offline/glasses/GlassesTarget.kt` | `BondedSnapshot` + `GlassesTarget` + 纯函数 `resolveGlassesTarget()`,决策顺序:硬件 → 权限 → 蓝牙开关 → 记住的地址且仍在系统列表 → 系统首个眼镜 → 记住的地址 → 未配对 |
| `app/src/main/java/com/icespiritai/offline/glasses/GlassesSystemIntents.kt` | 蓝牙设置 / 应用详情页跳转,吞掉 `ActivityNotFoundException`、`SecurityException`、兜底 `RuntimeException` |
| `app/src/main/java/com/icespiritai/offline/glasses/ui/GlassesNoticeDialog.kt` | `GlassesNotice` + 提示对话框,每种失败渲染能解决问题的按钮 |
| `app/src/test/java/com/icespiritai/offline/glasses/GlassesTargetResolverTest.kt` | 11 项,覆盖解析器全分支(含崩溃场景) |
| `app/src/test/java/com/icespiritai/offline/glasses/GlassesDeviceBondedSnapshotTest.kt` | 4 项 Robolectric(sdk=33),钉死"无权限时返回 PERMISSION_MISSING 而不是抛异常" |
| `app/src/test/java/com/icespiritai/offline/glasses/ui/GlassesNoticeDialogTest.kt` | 7 项,钉死每种提示的文案与按钮行为 |
| `app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenGlassesNoticeTest.kt` | 2 项集成回归:未配对点击 → 弹提示不崩;无权限点击 → 发起权限申请 |

合计 24 项新测试。另外两篇文档([docs/knowledge/2026-09-16-glasses-permission-crash.md](../knowledge/2026-09-16-glasses-permission-crash.md) 与本文)不计入代码统计。

### 2.2 修改文件(5 个)

| 文件 | 改动要点 |
| --- | --- |
| `glasses/GlassesDevice.kt` | `bondedSnapshot()` 取代裸读:先 `checkSelfPermission`,再整体 `try/catch SecurityException`,每个设备单独兜底;**永不抛异常**。删掉了已无调用方的 `findBondedDevice()` |
| `ui/home/HomeScreen.kt` | 点击流程改为:权限闸门 → 解析 → `Ready` 开 overlay,其余弹提示;入口 `runCatching` 兜底;替换掉原来那个"未配对智能眼镜"名词 Toast |
| `glasses/ui/GlassesCaptureOverlay.kt` | 解析不到设备时弹提示(原来是"搜索附近的眼镜"死胡同,没有关闭按钮);「重试」改为重跑整条 session,不再是 `loadLastPaired() ?: return@launch` 静默失败 |
| `ui/settings/SettingsScreen.kt` | 卡片状态改用同一个解析器(4 态取代布尔),`LifecycleResumeEffect` 里重算;从系统蓝牙/权限页回来不再留红字;新增"去应用设置"入口 |
| `res/values/strings.xml` | 新增 8 条文案(未配对引导语 / 权限说明 / 永久拒绝说明 / 继续授权 / 去应用设置 / 设置页两态文案等) |

### 2.3 行为变化(验收时对照)

| 场景(Android 12+ 全新安装) | 修复前 | 修复后 |
| --- | --- | --- |
| 设置开关 ON,未配对,点「眼镜」 | **闪退** | 先弹权限申请;允许后弹「未检测到已配对的智能眼镜…」+「去蓝牙设置配对」 |
| 权限被拒一次 | 无提示,永远失败 | 提示「需要蓝牙权限」+「继续授权」 |
| 权限永久拒绝 | 无提示 | 提示「去应用设置」 |
| 系统蓝牙已配对但 App 没连过 | 设置页红字「未配对智能眼镜」 | 设置页显示「已配对:MAC」(与系统蓝牙一致) |
| overlay 里解析不到设备 | "搜索附近的眼镜"死对话框,只能按返回 | 弹提示 + 关闭;重试可重跑整条流程 |

---

## 3. 已通过的验证(可直接复现)

```powershell
# 单测(注意:不要加 -PmodelProfile,见 §5 踩坑)
$env:JAVA_HOME="C:\Users\37311\.gradle\jdks\jdk-17.0.18+8"
.\gradlew.bat :app:testDebugUnitTest --console=plain
# 期望:BUILD SUCCESSFUL;108 个测试类 / 1068 项 / 0 失败

# 出货 profile 编译 + 出包
.\gradlew.bat :app:compileReleaseKotlin -PmodelProfile=ice_ocr_rules --console=plain
.\gradlew.bat :app:assembleDebug      -PmodelProfile=ice_ocr_rules --console=plain
```

已产出的可装包:`app/build/outputs/apk/debug/app-debug.apk`(81.3 MB,sha256 前缀
`7D832969BE82AC9E`)。**这是 debug 签名包,不能覆盖安装到设备上现有的 release 签名 0.4.2。**

---

## 4. 下一步(按顺序执行)

### 4.1 P0 · 真机验证(**当前唯一的阻塞项**)

设备已插着:`AGQV023313008161`(Android 15 / API 35)。签名冲突有两种解法,**必须先问用户选哪种**:

- 方案 A:用户同意卸载现有 0.4.2(会清空 App 设置 + 缓存)→ 直接装 debug 包。
- 方案 B:不动现有 App → 用 release 签名出包(`source ~/.gradle/release-env.sh` 提供
  `ICESPIRITAI_RELEASE_*`),`adb install -r` 覆盖安装,数据保留。**注意先确认
  `app/build.gradle.kts` 里 `enableV1Signing = true`**(AGP 默认 v2-only,in-app update 验签会拒签)。

装好后**清掉 logcat 再手工走一遍**,并用一条命令确认没有崩溃:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s AGQV023313008161 logcat -c
# …手工操作 1-4 步…
& $adb -s AGQV023313008161 logcat -b crash -d        # 期望:空
& $adb -s AGQV023313008161 shell "dumpsys package com.icespiritai.vision" |
    Select-String "BLUETOOTH_CONNECT"                # 期望:granted=true(授权后)
```

手工验收 4 步(缺一不可):

1. 设置 → 智能眼镜 打开开关 → 首页点「眼镜」→ 弹权限申请 → 允许 → 弹「未检测到已配对的智能眼镜…」+ 可跳系统蓝牙。**全程不闪退。**
2. 同一流程选「不允许」→ 弹「继续授权」;再拒一次 → 弹「去应用设置」。
3. 系统蓝牙里配对眼镜 → 回 App:设置页红字应自动变成「已配对:…」→ 点「眼镜」进入连接/拍照流程。
4. `logcat -b crash -d` 无 `SecurityException ... getBondedDevices`、无 `FATAL EXCEPTION`。

结果写进 `docs/smoke/2026-09-16-glasses-permission-fix/README.md`(照抄
`docs/smoke/2026-09-15-ble-fix-verify/README.md` 的格式:设备 / APK / 命令 / 时间戳日志)。

### 4.2 P0 · 提交(用户确认后再做)

遵循 `CLAUDE.md` 的 Trip Sequence:

1. `$env:JAVA_HOME="C:\Users\37311\.gradle\jdks\jdk-17.0.18+8"`
2. 作者 = `AlexMultiAgent`,**绝不**加 `Co-Authored-By:` trailer(仓库 hook 会拦)
3. **用具体路径 `git add`,绝不 `git add -A` / `git add .`**(PreToolUse hook 直接拒)

本任务应加入的文件(逐条列出,不要多):

```
app/src/main/java/com/icespiritai/offline/glasses/GlassesPermissions.kt
app/src/main/java/com/icespiritai/offline/glasses/GlassesTarget.kt
app/src/main/java/com/icespiritai/offline/glasses/GlassesSystemIntents.kt
app/src/main/java/com/icespiritai/offline/glasses/GlassesDevice.kt
app/src/main/java/com/icespiritai/offline/glasses/ui/GlassesNoticeDialog.kt
app/src/main/java/com/icespiritai/offline/glasses/ui/GlassesCaptureOverlay.kt
app/src/main/java/com/icespiritai/offline/ui/home/HomeScreen.kt
app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt
app/src/main/res/values/strings.xml
app/src/test/java/com/icespiritai/offline/glasses/GlassesTargetResolverTest.kt
app/src/test/java/com/icespiritai/offline/glasses/GlassesDeviceBondedSnapshotTest.kt
app/src/test/java/com/icespiritai/offline/glasses/ui/GlassesNoticeDialogTest.kt
app/src/test/java/com/icespiritai/offline/ui/home/HomeScreenGlassesNoticeTest.kt
docs/knowledge/2026-09-16-glasses-permission-crash.md
docs/handoff/2026-09-16-glasses-permission-crash-handoff.md
```

### 4.3 P0 · 发版(需用户明确同意"现在发")

1. `app/build.gradle.kts:76-77`: `versionCode = 78`,`versionName = "0.4.3"`。
2. `app/src/main/assets/user-changelog.md` **最上方**加 `## v0.4.3 — 2026-09-16` 条目,
   风格照抄现有条目:先一句大白话总结,再 `### 修复` 列用户能感知的点(例:"修复了没配对智能眼镜时点『眼镜』按钮 App 闪退的问题,现在会提示先去系统蓝牙里配对";不要出现 `SecurityException`、类名等术语)。
3. 走 `CLAUDE.md` 的 release 流程:`source ~/.gradle/release-env.sh` → `assembleRelease`
   → `generateVisionLatestJson` → `archiveVisionRelease` → `uploadVisionReleaseToGitea`。
4. 发版后对齐 Triple-SHA:`git rev-parse v0.4.3^{}` = HEAD =
   `sha256sum app/build/generated/release-staging/icespiritai-vision.apk` = JSON 里的 `apkSha256`。

### 4.4 P1 · `GlassesScan` 的老设备分支(需要用户拍板,不要擅自改)

`glasses/GlassesScan.kt:100` 的 `hasScanPermission()` 在 API ≤ 30 分支要求
`ACCESS_FINE_LOCATION`,但 manifest 没有声明该权限 → 老设备上恒为 `MissingPermission`。
而 `GlassesScan` **目前没有接进任何 UI**。三个选项:

- **(a) 什么都不做** —— v1 不扫描,但要在 KDoc 里写明"未接线",避免下一个人以为它在用。
- **(b) 加 `ACCESS_FINE_LOCATION`(`maxSdkVersion=30`)** —— 最省事,但**会引入定位权限面**,
  政务 App 的隐私清单要同步改,**必须用户同意**。
- **(c) 删掉 `GlassesScan` + `ScanException`** —— v2 需要时再写;仓库目前零引用,删除成本最低。

建议 (a) 或 (c);若确认 v2 不在近期计划内,倾向 (c)。

### 4.5 P1 · 同类隐患审计(本次只修了眼镜链路)

本仓其他地方还有 `@RequiresPermission` 级别的平台调用吗?建议全量搜一遍并把结论写进
`docs/knowledge/`:

```powershell
rg -n "getBondedDevices|getDefaultAdapter|getRemoteDevice|BluetoothLeScanner|checkSelfPermission|shouldShowRequestPermissionRationale" app/src/main/java
rg -n "isEnabled|getSystemService" app/src/main/java/com/icespiritai/offline/updater   # FGS / 通知链路
```

已确认修好的:`BluetoothController.connect()` 早有 `hasConnectPermission()` 守卫,但它只是
**降级报错**(返回 `ConnectionState.Failed("BLUETOOTH_CONNECT permission not granted")`),
把英文原文直接塞进「拍照失败:%1$s」给用户看。**建议 P2 把它换成中文文案**
(例如复用 `R.string.glasses_permission_denied`),否则用户仍会看到英文报错。

### 4.6 P2 · 可选清理(都不影响本次验收)

- `strings.xml` 里 `settings_glasses_action_forget` / `settings_glasses_action_forget_confirm`
  **从未接线**(只有定义);`GlassesDeviceStore.clear()` 也只有测试在用。要么补一个"清除记忆"
  入口(用户遇到记住的地址失效时,目前除了卸载 App 没法清),要么删掉这两条文案。
- `glasses_scan_empty` / `glasses_action_cancel` 同样是死文案,随 `GlassesScan` 决策一起处理。
- overlay 里 `GlassesPhotoCaptureRepository.Failed` 的 `reason` 目前混合了中文原因与英文异常串,
  建议统一成"错误码 + 本地化文案"两层。

---

## 5. 命令速查与踩坑

```powershell
# 统一先设 JAVA_HOME(否则 AGP 9.3 toolchain 解析崩)
$env:JAVA_HOME="C:\Users\37311\.gradle\jdks\jdk-17.0.18+8"

# 单测:必须用默认(shell)profile
.\gradlew.bat :app:testDebugUnitTest --console=plain
# 单个类
.\gradlew.bat :app:testDebugUnitTest --console=plain `
  --tests "com.icespiritai.offline.glasses.*" `
  --tests "com.icespiritai.offline.ui.home.HomeScreenGlassesNoticeTest"

# 出货 profile(真机包 / release)
.\gradlew.bat :app:assembleDebug   -PmodelProfile=ice_ocr_rules --console=plain
.\gradlew.bat :app:compileReleaseKotlin -PmodelProfile=ice_ocr_rules --console=plain

# 真机(只读排障)
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb -s AGQV023313008161 shell getprop ro.build.version.sdk
& $adb -s AGQV023313008161 shell "dumpsys package com.icespiritai.vision" | Select-String "BLUETOOTH|versionName"
& $adb -s AGQV023313008161 logcat -b crash -d
```

| 坑 | 说明 |
| --- | --- |
| `-PmodelProfile=ice_ocr_rules` 会让**单测编译失败** | `FakeOcrEngine` / `FakeOcrEngineFactory` 在 `app/src/shell/java`,只有默认(shell)profile 才挂上去;报错是 `Unresolved reference 'FakeOcrEngine'`。单测一律不加该参数。 |
| Robolectric `maxSdk` 只有 34,targetSdk 是 37 | 新测试统一写 `@Config(sdk = [33])`。33 也是"权限被强制校验"的代表 API,别再往上调。 |
| Robolectric 不投递权限结果 | `ShadowActivity` 只记录 `lastRequestedPermission`,不会回调 grant/deny。所以"点击 → 弹提示"的用例要先 `shadowOf(app).grantPermissions(...)`,否则会走权限申请分支。 |
| Robolectric 的 `BluetoothAdapter.getBondedDevices()` 不校验权限 | 不要指望用 shadow 复现 SecurityException;权限语义靠我们自己的 `checkSelfPermission` 守卫来测(见 `GlassesDeviceBondedSnapshotTest`)。 |
| 真机 dropbox 里没有 vision 的崩溃记录 | `dumpsys dropbox --print` 只找到 `com.icespiritai.translate` 的崩溃;用户报的闪退大概率发生在**另一台手机**上。不影响根因结论(权限证据在本机 `dumpsys` 里)。 |
| 权限组文案 | `BLUETOOTH_SCAN` 与 `BLUETOOTH_CONNECT` 同属 `NEARBY_DEVICES`,系统弹窗文案是组级的「发现并连接到附近的设备」(可在 `platforms/android-35/data/res/values/strings.xml` 核对),所以只申请 CONNECT 不会让弹窗变好看或变难看——不要以此为由加 SCAN。 |

---

## 6. 边界 / 不要做

1. **不要碰别人正在改的文件。** 交接时工作区里这些改动**不属于本任务**,是你旁边另一个会话在做的
   (可能随时变化):
   `app/src/main/java/com/icespiritai/offline/settings/SettingsViewModel.kt`、
   `app/src/main/java/com/icespiritai/offline/ui/settings/UpdateSection.kt`、
   `app/src/main/java/com/icespiritai/offline/updater/UpdateRepository.kt`、
   `app/src/test/java/com/icespiritai/offline/ui/settings/UpdateSectionTest.kt`、
   `app/src/test/java/com/icespiritai/offline/updater/UpdateRepositoryRetryTest.kt`。
   提交时只 `git add` §4.2 列出的路径。
2. **不要为了消灭崩溃就把眼镜按钮藏起来/置灰。** 用户明确要求"入口在、点了给解释"。
3. **不要新增定位权限、不要改 manifest 的权限面**——除了用户点头的 §4.4 选项 (b)。
4. **不要为了让测试变绿而放宽断言**(例如把"必须弹提示"改成"不崩就行")。这次的回归价值全在那两条断言上。
5. **不要在没跑完 §4.1 的情况下发版。** 本次没有真机验证过修复后的包,这是唯一的硬阻塞。
6. 本次会话**没有**做:`git commit`、发版、改 versionCode/versionName、动 `user-changelog.md`。
   这些都留给接手方,且都需要用户先确认。

---

## 7. 需要用户拍板的问题(接手后第一件事就是问)

1. 真机验证走哪个方案:A 卸载重装 debug 包(清数据)还是 B 用 release 签名打可覆盖安装的包?
2. 现在就发 v0.4.3,还是先只提交、攒到下一个版本一起发?
3. `GlassesScan` 走 §4.4 的哪个选项?(涉及是否新增定位权限)
4. 用户报闪退的那台手机是什么型号 / Android 版本?(本机是 Android 15,报障机可能是另一台,
   如果也是 12+ 则与根因一致;若是 Android 10/11,需要另行解释——但修复本身对两者都成立)

---

## 8. 参考文档

- 根因与范围决策(含证据表):[docs/knowledge/2026-09-16-glasses-permission-crash.md](../knowledge/2026-09-16-glasses-permission-crash.md)
- 眼镜 BLE 协议与提速参数:`docs/glasses/AI识图传图提速_App连接参数配合.md`
- 官方示例 App(权限申请写法参照):`docs/glasses/官方技术给的示例（仅参考）/app/src/main/java/com/aiglass/zhangwen/ui/bind/BindDeviceScreen.kt`
- 上一次眼镜真机冒烟(格式模板):`docs/smoke/2026-09-15-ble-fix-verify/README.md`
- 仓库协作约定(提交 / 发版 / 命名):`CLAUDE.md` §"每次发版 / 每次 commit 必跑 (Trip Sequence)"
