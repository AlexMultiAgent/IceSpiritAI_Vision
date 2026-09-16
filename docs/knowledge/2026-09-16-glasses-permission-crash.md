# 「眼镜」按钮闪退:`BLUETOOTH_CONNECT` 声明了但从未申请(v0.4.3 修复)

> Captured 2026-09-16 from a field report: tapping the home-screen 「眼镜」
> button crashed the app when no glasses were paired. Fix landed in v0.4.3.

## TL;DR

`GlassesDevice.findBondedDevice()` called `BluetoothAdapter.bondedDevices`
and `BluetoothDevice.name` directly. Both require **`BLUETOOTH_CONNECT`** on
Android 12+ (API 31+), and this app targets 37 — but the permission was only
*declared* in `AndroidManifest.xml`, never *requested* at runtime. An un-asked
runtime permission is denied, so the first call threw

```
java.lang.SecurityException: Need android.permission.BLUETOOTH_CONNECT
permission ... for AdapterService getBondedDevices
```

on the main thread, inside the Compose `onClick` of the 「眼镜」 FAB. Nothing
caught it → process death.

**Why "no glasses paired" was the trigger:** `GlassesDeviceStore.loadLastPaired()`
short-circuits the bonded-device read whenever the app has connected to the
glasses at least once. Only an empty store (never connected / `pm clear`)
fell through to the crashing call — which is exactly the fresh-install state,
and exactly why every pre-v0.4.3 smoke run on a device whose store was already
populated passed.

**Fix:** a runtime-permission gate (CONNECT only) + guard-safe snapshot +
a pure resolver + real prompts instead of a bare-noun Toast.

## Evidence trail

| Question | Answer |
| --- | --- |
| Was the permission declared? | Yes — `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` in `AndroidManifest.xml`. |
| Was it ever requested? | No. `git log -S "RequestMultiplePermissions"` is empty for the whole history; the only runtime requests in the tree were `CAMERA` (HomeScreen) and `POST_NOTIFICATIONS` (UpdateSection). |
| Why did the crash need "unpaired"? | `loadLastPaired()` ≠ null → the code returns at the store branch and never touches `bondedDevices`. |
| Why did smoke tests pass? | The glasses were already paired *and* connected on the test device, so the store was non-empty and the crashing branch was unreachable. |
| What did the official app do? | `docs/glasses/官方技术给的示例（仅参考）/.../BindDeviceScreen.kt` requests `BLUETOOTH_SCAN + BLUETOOTH_CONNECT` via `RequestMultiplePermissions` before any Bluetooth call. That is the pattern this repo was missing. |

## Scope decision: CONNECT only (not SCAN)

Both permissions belong to the `NEARBY_DEVICES` group, whose system dialog is
*group-level* — verified from the platform resources
(`platforms/android-35/data/res/values/strings.xml`):

```
permgrouplab_nearby_devices = 附近的设备 / Nearby devices
permgroupdesc_nearby_devices = 发现并连接到附近的设备 / discover and connect to nearby devices
```

Asking for `BLUETOOTH_SCAN` therefore buys **no** UX difference — the dialog
says "发现并连接到附近的设备" either way — while widening the permission
surface that a government-app privacy review has to justify. v1 pairs in
system Bluetooth settings and connects by MAC, so `bondedDevices` +
`getRemoteDevice` + `connectGatt` need CONNECT alone. The `BLUETOOTH_SCAN`
declaration stays in the manifest, so wiring in-app pairing in v2 needs no
manifest change; the request belongs next to the scan button where the user's
intent is self-evident.

## Fix shape (v0.4.3)

| File | Role |
| --- | --- |
| `glasses/GlassesPermissions.kt` | `required()` / `missing()` / `shouldShowRationale()` — CONNECT on API 31+, empty below. Never cached (revoking the permission kills the process). |
| `glasses/GlassesTarget.kt` | `BondedSnapshot` + `GlassesTarget` + `resolveGlassesTarget()` — a pure decision table: hardware → permission → radio → remembered-and-still-bonded → first bonded → remembered → 未配对. |
| `glasses/GlassesDevice.kt` | `bondedSnapshot()` replaces the unguarded read: explicit `checkSelfPermission`, `try/catch (SecurityException)` around the whole read, per-device `try/catch` for name/address. Never throws. |
| `glasses/GlassesSystemIntents.kt` | Bluetooth-settings / app-settings deep links, swallowing `ActivityNotFoundException` **and** `SecurityException`. |
| `glasses/ui/GlassesNoticeDialog.kt` | `GlassesNotice` + dialog: each failure renders the action that fixes it (去蓝牙设置配对 / 继续授权 / 去应用设置 / 关闭). |
| `ui/home/HomeScreen.kt` | Permission launcher on the tap; resolver behind `runCatching`; notice dialog instead of `Toast(R.string.settings_glasses_not_paired)`; toast-era crash invariant: **a Compose `onClick` must not be able to throw**. |
| `glasses/ui/GlassesCaptureOverlay.kt` | Unresolved target → notice (was: a dead-end dialog reading 「搜索附近的眼镜」 with no close button); 重试 re-resolves instead of `loadLastPaired() ?: return@launch`. |
| `ui/settings/SettingsScreen.kt` | Card status derives from the same resolver (4 states, not a boolean), re-derived on `LifecycleResumeEffect` so returning from system settings updates it. |

## Verification

Unit tests (Robolectric `sdk = 33`, the first API level where the permission is
enforced; the repo's Robolectric caps at 34 and targetSdk is 37):

| Test | Pins |
| --- | --- |
| `GlassesTargetResolverTest` (11) | every resolver branch, including the crash-report configuration |
| `GlassesDeviceBondedSnapshotTest` (4) | denied permission returns `PERMISSION_MISSING` instead of throwing; granted reads only prefix-matching devices |
| `GlassesNoticeDialogTest` (7) | each notice's wording + which action each button fires |
| `HomeScreenGlassesNoticeTest` (2) | tap with nothing paired → dialog, no crash; tap without permission → runtime request issued |

Real device (Android 12+), after installing the build:

```bash
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> logcat -c
# 1. fresh state, no pairing: clear the app's memory of a device
adb -s <serial> shell pm clear com.icespiritai.vision
```

1. 设置 → 智能眼镜 打开开关 → 回首页点「眼镜」→ 应弹权限申请 → 允许 → 应弹
   「未检测到已配对的智能眼镜…」并可跳系统蓝牙设置。**全程不闪退。**
2. 同一流程里选「不允许」→ 提示「继续授权」;再拒一次 → 提示「去应用设置」。
3. `adb -s <serial> logcat -b crash -d` → 不应再出现
   `SecurityException ... getBondedDevices`,也不应有 `FATAL EXCEPTION`。
4. 系统蓝牙里配对眼镜 → 回到 App:设置页红字应变成「已配对:…」(前台重查) →
   点「眼镜」→ 正常进入连接/拍照流程。

## Prevention

1. **A declared runtime permission is not a granted one.** Any `@RequiresPermission`
   platform call needs a request path *and* a `try/catch` — ROMs disagree about
   which call in a family they enforce (`isEnabled` vs `bondedDevices` vs
   `getName`).
2. **Never let a platform call throw out of a Compose `onClick`.** Guard the
   individual calls *and* wrap the entry point in `runCatching`.
3. **`null` is not an error model.** The three failure arms here need three
   different UI actions; flattening them to `null` is what left "未配对" as a
   bare Toast for every cause.
4. **Read OS state at the point of use.** The Settings card derived its status
   from the app's own SharedPreferences, so it reported 「未配对」 for a device
   that *was* paired in system Bluetooth — the state the field screenshot was
   taken in.
