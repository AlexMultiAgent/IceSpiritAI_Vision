# v0.1.47 Release Smoke (2026-09-02)

## 改动内容

5 commit,纯 UI 渲染微调 + 版本三段式打标:

| Commit | 改动 |
|---|---|
| `a5ba228` | feat(strings): 新增 settings_about_org = 哈尔滨市市场监管局 |
| `9bc32f8` | feat(ui): 设置页关于区三行堆叠(冰灵⚡锐目 / 版本 / 哈尔滨市市场监管局) |
| `537d948` | feat(ui): 查看更新日志 Card 去掉 ListItem,改 Row+clickable 与外观/更新 Card 视觉一致 |
| `b09395e` | docs(settings): KDoc 删除过期 [ListItem] 引用 |
| `473c252` | feat(ui): 首页顶部标题字号 titleLarge→titleMedium (22sp→16sp) |
| `da6cc93` | chore(v0.1.47): Release 三段式打标(versionCode 46→47 + user-changelog + tag v0.1.47) |

## Pre-flight 结果

| # | 项 | 状态 |
|---|---|---|
| 1 | JDK 17 (Temurin 17.0.18+8) | ✓ |
| 2 | `enableV1Signing = true` (app/build.gradle.kts:194) | ✓ |
| 3 | `gradle.token.properties` 存在,未入仓 | ✓ |
| 4 | AAR (91K) + det (9.5M) + rec (21M) ONNX | ✓ |
| 5 | Cert SHA-256 = `4a21f417782d561dccd31ff0a10e4d643d13d00a8a2be77b4e9eeee0660b3043` 与 in-app verifier 一致 | ✓ |

## 4 步流水线

| Step | Gradle task | 输出 |
|---|---|---|
| 1 | `assembleRelease` (ice_ocr_rules) | `app/build/outputs/apk/release/app-release.apk`,60,507,211 bytes(~58 MB)|
| 2 | `generateVisionLatestJson` | `vision-latest.json`:versionCode=47, versionName=0.1.47, sha256=08db5a9b5fab64c9..., apkUrl=releases/download/latest/icespiritai-vision.apk (未改写)|
| 3 | `archiveVisionRelease` | 拷贝 + 重命名为 `icespiritai-vision.apk` → `app/build/generated/release-staging/`,JSON → 同目录 |
| 4 | `uploadVisionReleaseToGitea` | 删除旧 asset id=338 (apk) + 339 (json),POST 新 APK uuid=`7fc9e916-278e-4d86-91cb-fcb309b1db16`,改写 apkUrl → `/attachments/7fc9e916-278e-4d86-91cb-fcb309b1db16`,POST JSON 1679 bytes,tag=`latest`, release id=187 |

总耗时:BUILD SUCCESSFUL in 3m 58s,57 actionable tasks (34 executed, 23 up-to-date)。

## Post-release smoke

### Smoke 1: JSON metadata 可达 + 关键字段

```
JSON OK:
  versionCode= 47
  versionName= 0.1.47
  apkSize= 60507211
  signerCertSha256= 4a21f417782d561dccd31ff0a10e4d643d13d00a8a2be77b4e9eeee0660b3043
  apkUrl= http://125.211.45.14:3000/attachments/7fc9e916-278e-4d86-91cb-fcb309b1db16
ALL ASSERTIONS PASSED
```

- `versionCode == 47` ✓ (与 da6cc93 bump 一致)
- `signerCertSha256` 以 `4a21f4` 开头 ✓ (与 .gradle/gradle.properties 记录 + keytool 实测一致)
- `apkUrl` 已 rewrite 到 `/attachments/<uuid>` ✓ (Gitea 1.22.x APK 404 防御性绕路)

### Smoke 2: APK 可达 + 大小一致

```
Local APK size: 60507211
Remote APK size: 60507211
SIZE OK (60507211 bytes)
```

`/attachments/7fc9e916-278e-4d86-91cb-fcb309b1db16` 返回 HTTP 200,Content-Length = 60,507,211 = local APK 大小。

### Smoke 3: 真机烟测(可选,本期未跑)

跳过(纯 UI 渲染改动,无 OCR/规则/导出/更新逻辑变动;若需要由用户触发华为 nova 6 in-app update 验证)。

## Code repo 同步

- `gitea` remote main:`473c252..da6cc93` push 完成(发版前已做,见 `da6cc93` commit 信息)
- `github` remote main:同步 push 完成
- `publishing` repo `giteaadmin/vision-app`:Step 4 已写入 APK + JSON 到 tag `latest`

## 已知差异

无(与 v0.1.46 流水线唯一区别:本版本 APK 比 v0.1.46 略小,可能是 R8 dead-code 略有差异,与功能无关)。

## 下一步

- 真机(in-app update)验证留待用户触发
- 若发现 UI regression,需 fix commit + 重新打标(re-bump minor 或 patch)
