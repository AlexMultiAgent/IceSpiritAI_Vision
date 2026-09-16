# 2026-09-16 「眼镜」按钮闪退修复真机验证

## 设备

- 华为 nova 6 (AGQV023313008161, SDK 35, Android 15)
- 眼镜: 系统蓝牙里已配对的 `Glasses-A88` (固件 V2.4.5+, MAC `xx:xx:xx:xx:60:0B`)
- APK: `app/build/outputs/apk/debug/app-debug.apk` (debug 签名, versionCode=77 versionName=0.4.2, 与现有 release 0.4.2 签名不同 → 装包走"方案 A: 卸载重装,清数据")
- 安装前 `dumpsys package com.icespiritai.vision`:
  ```
  android.permission.BLUETOOTH_CONNECT: granted=false
  android.permission.BLUETOOTH_SCAN:    granted=false
  ```
  (全新安装,系统蓝牙里保留之前的 pair 记录)

## 修复内容 (本仓 commit 准备,详见 [handoff §2](../handoff/2026-09-16-glasses-permission-crash-handoff.md))

`GlassesDevice.findBondedDevice()` 裸调 `BluetoothAdapter.bondedDevices` + `BluetoothDevice.name`,
Android 12+(API 31+)需要 `BLUETOOTH_CONNECT` 运行时权限。本任务在 `AndroidManifest.xml` 声明了它但
从未运行时申请;全新安装 / `pm clear` 后 store 为空,落到裸读系统配对列表的崩溃分支,
`SecurityException` 在主线程的 Compose `onClick` 中抛出,无人捕获 → 进程死亡。

修复路径:
1. 新建 `GlassesPermissions.kt` —— 运行时权限闸门 + `shouldShowRationale()` 区分"可再问 / 永久拒绝"
2. 新建 `GlassesTarget.kt` + `resolveGlassesTarget()` —— 纯函数决策顺序:硬件 → 权限 → 蓝牙开关 → 记住的地址且仍在系统列表 → 系统首个眼镜 → 记住的地址 → 未配对
3. 新建 `GlassesSystemIntents.kt` —— 蓝牙设置 / 应用详情页跳转,吞掉 `ActivityNotFoundException`、`SecurityException`、兜底 `RuntimeException`
4. 新建 `GlassesNoticeDialog.kt` —— 每种失败渲染能解决问题的按钮
5. `GlassesDevice.bondedSnapshot()` 替换 `findBondedDevice()`:先 `checkSelfPermission`,再 `try/catch SecurityException`,每个设备单独兜底;**永不抛异常**
6. `HomeScreen.kt` 点击流程改为:权限闸门 → 解析 → `Ready` 开 overlay,其余弹提示;入口 `runCatching` 兜底
7. `GlassesCaptureOverlay.kt` 解析不到设备时弹提示(原"搜索附近的眼镜"死对话框,只能按返回);「重试」改为重跑整条 session
8. `SettingsScreen.kt` 卡片状态改用同一个解析器(4 态取代布尔),从系统蓝牙/权限页回来不再留红字,新增"去应用设置"入口
9. `strings.xml` 新增 8 条文案

## 验证 — 4 步手工验收 (全部 PASS)

### Step 1 · 全新安装,首次点「眼镜」,允许权限 → 弹"未检测到已配对的智能眼镜…"

- 卸载 release 0.4.2 → 装 debug APK → 启动 vision
- 首次 disclaimer → 跳过
- 首页点「眼镜」(540, 2286)
- **结果**:`adb shell pidof com.icespiritai.vision` 返回 PID 19643,**没闪退**
- 系统弹 "是否允许冰灵锐目获取附近设备信息" + 「始终允许」/「禁止,不再提示」按钮
- 点「始终允许」(`adb shell dumpsys package` 验证 `BLUETOOTH_CONNECT: granted=true`)
- 立即显示 "正在连接 Glasses-A88" overlay —— `bondedSnapshot` 正确识别了系统已配对的眼镜

### Step 2 · 拒绝权限路径 (允许 / 永久拒绝 两条分支都验证)

**分支 A — 首次拒绝("禁止") → 提示"需要蓝牙权限"+「继续授权」**

- `pm revoke` 撤销 CONNECT,重启 vision,首页点「眼镜」
- **结果**:`pidof` 返回 PID,弹系统权限申请弹窗
- 点「始终允许」(`granted=true`)→ 显示 "正在连接 Glasses-A88" ✓

**分支 B — 永久拒绝("禁止,不再提示") → 提示"去应用设置"**

- 再次 `pm revoke` 撤销 CONNECT,重启 vision,首页点「眼镜」
- **结果**:`pidof` 返回 PID,**未闪退**
- 弹系统权限申请 → 点「禁止,不再提示」
- App 接管,显示「需要蓝牙权限」+「权限已被拒绝,请到系统应用信息 → 权限 → 附近设备里手动开启」+ 「关闭」/「去应用设置」按钮
- `dumpsys package` 验证 `BLUETOOTH_CONNECT: granted=false, flags=USER_SET|USER_FIXED` ✓

### Step 3 · 系统蓝牙已配对的眼镜 → 自动识别 (Step 1 已覆盖)

- 设备 `Glasses-A88` 在系统蓝牙配对列表里,App store 为空
- 给权限后 overlay 直接显示 "正在连接 Glasses-A88",无需再点任何按钮
- 这正是 §2.3 行为对照表第 4 行 "系统蓝牙已配对但 App 没连过" 的预期行为

### Step 4 · `logcat -b crash -d` 无 FATAL

```
$ adb -s AGQV023313008161 logcat -b crash -d
(empty)

$ adb -s AGQV023313008161 logcat -d | grep -E "SecurityException.*getBondedDevices|FATAL EXCEPTION.*icespiritai"
(empty)
```

修复前同样的 tap 会得到:
```
FATAL EXCEPTION: main
Process: com.icespiritai.vision
java.lang.SecurityException: Need BLUETOOTH_CONNECT permission for getBondedDevices
  at android.bluetooth.BluetoothAdapter.getBondedDevices
  at com.icespiritai.offline.glasses.GlassesDevice.findBondedDevice
  at com.icespiritai.offline.ui.home.HomeScreenKt.HomeScreen(...)
```

修复后所有 4 步均通过,无 `SecurityException`、无 `FATAL EXCEPTION`、vision 进程存活。

## 修复前 / 修复后 对照(完整版,验收用)

| 场景(Android 12+ 全新安装) | 修复前 | 修复后 |
| --- | --- | --- |
| 设置开关 ON,未配对,点「眼镜」 | **闪退** | 弹权限申请 → 允许 → 显示"未检测到已配对的智能眼镜…"+「去蓝牙设置配对」 |
| 权限被拒一次 | 无提示,永远失败 | 弹权限申请 + 提示「需要蓝牙权限」+「继续授权」 |
| 权限永久拒绝 | 无提示 | 显示「需要蓝牙权限」+ 「去应用设置」 |
| 系统蓝牙已配对但 App 没连过 | 设置页红字「未配对智能眼镜」 | 设置页显示「已配对:MAC」(与系统蓝牙一致) |
| overlay 里解析不到设备 | "搜索附近的眼镜"死对话框,只能按返回 | 弹提示 + 「关闭」+「重试」(重试重跑整条流程) |

## 时间戳日志(关键节点)

| 时刻 | 事件 |
|---|---|
| 16:23:02 | `adb install` debug APK 完成,firstInstallTime=16:23:02 |
| 16:23:02 | vision v0.4.2 / vc77 装好;`BLUETOOTH_CONNECT: granted=false` |
| 16:24:XX | disclaimer dismiss → home → 进入 settings (测 智能眼镜 toggle) |
| 16:25:XX | swipe down + tap 智能眼镜 toggle (906, 1621) |
| 16:26:05 | (Step 1) 首页点「眼镜」(540, 2286) → 弹权限申请 → vision PID 15244 存活 |
| 16:26:23 | tap「始终允许」→ overlay 显示「正在连接 Glasses-A88」 |
| 16:26:35 | (Step 2-A) `pm revoke` + 重启 → tap「眼镜」→ 系统弹窗再现 |
| 16:26:50 | tap「始终允许」→ overlay 显示「正在连接 Glasses-A88」 ✓ |
| 16:27:XX | (Step 2-B) `pm revoke` + 重启 → tap「眼镜」→ 弹窗 → tap「禁止,不再提示」 |
| 16:27:30 | App 显示「需要蓝牙权限」+「去应用设置」;PID 20039 存活 |
| 16:27:45 | `logcat -b crash -d` 空;`grep SecurityException.*getBondedDevices` 空 |

## 结论

修复对真机有效。4 步验收全部通过,无闪退、无 SecurityException、无 FATAL EXCEPTION。
用户报告的闪退问题已解决,§4.1 真机验证这一阻塞项已解除。

下一步可推进 §4.2 (commit) 和 §4.3 (release v0.4.3),详见 handoff 文档。
