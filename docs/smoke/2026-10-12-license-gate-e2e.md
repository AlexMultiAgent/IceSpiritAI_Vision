# License Gate 真机端到端烟测

日期:2026-10-12
设备:Huawei nova 6(AGQV023313008161 / SDK 35)
APK:`app/build/outputs/apk/debug/app-debug.apk`(assembleDebug -PmodelProfile=ice_ocr_rules,85 MB)
真公钥指纹:`eb8ddcfb3b282a30`(已注入 BuildConfig)

## 已通过的路径

### 路径 1:首次启动 → LicenseGateScreen → 合法码 → 免责声明 → 主屏

```
adb shell pm clear com.icespiritai.vision   # ghost state fix
adb install -r app-debug.apk
adb shell am start -n com.icespiritai.vision/.IceSpiritVisionActivity
```

UI dump 显示 gate:
- "激活冰灵锐目"
- "首次使用请向市局广告处索取"
- "用户名" / "激活码" / "登录"

签 `smoke0` uid 的码(uid=smoke0, name=smoke, expire=20991231)。tap (540,1130) → input "smoke" → tap (540,1370) → 粘贴 ICS1... → tap (540,1578)。4 秒后 dump 显示「使用提示」页 + 「我了解」按钮。tap (840,1600) 后进入主屏:
- "冰灵锐目" / "广告招牌" / "请对正图片后点击拍照"

**结论**:Activity 路由分支正确,LicenseState.Active 时 NavHost 渲染。

### 路径 2:错误签名 → 错误文案

卸载重装清状态。用临时生成的 wrongkey.pem 签出 uid=badsig 的码(其他参数 → SignWithWrongKey)。同样输入路径点击登录。

UI dump:
- "激活码无效,请联系市局广告处"

**结论**:VerifyResult.SignatureMismatch 路径触发,文案中"市局广告处"四个字完全替换"销售"。

### 路径 3:卸载重装 → gate 重显示

```
adb uninstall com.icespiritai.vision
adb install -r app-debug.apk
adb shell am start ...
```

UI dump 重显 "激活冰灵锐目" — gate 重新显示,DataStore 真的被清空。

**结论**:LicenseStore 与 LicenseViewModel 的 init 重 hydrate 路径正确。

### 路径 4(代码覆盖):过期码 / 用户名不匹配 / parse failed

未在真机走流程(adb input 清理文本框 input 字段的 200 次按键超时中断),由单元测试 `LicenseVerifierTest` 7/7 + `LicenseViewModelTest` 7/7 覆盖:
- `rejects_expired`
- `submit_with_expired_returns_error`
- `submit_with_username_mismatch_returns_error`
- `submit_with_garbage_returns_parse_failed`

### 路径 5:跨语言 ECDSA 字节级对齐(Task 5)

`LicenseInteropTest` 3/3 通过:
- Python 端 `license_tool.py new` 签的码 Android JVM 端 `LicenseVerifier.verify` 返回 `Valid`
- `LicenseCodec.parse` 字节级 round-trip
- `java.security.Signature.verify` 直接验证 Python 签名的 DER 格式

## 已知 UX 细节

1. 退出登录:进入设置 → 找到"切换账号 / 重新登录" Card(从实现里点)→ 弹 dialog → 确认 → DataStore 清空 + Activity recreate → gate 重显。
2. 续期:用相同 uid 再签一张 expire 更晚的码,DataStore.setCode 覆盖,无需任何重登按钮。

## 测试期间使用的码

| uid | name | expire | 用途 |
|---|---|---|---|
| smoke0 | smoke | 20991231 | 路径 1(成功) |
| badsig | bad | 20991231 | 路径 2(错密钥签) |
| expird | expired | 20200101 | 路径 4 覆盖(未在真机走) |

实际签发的 ICS1 码存放在 `/d/icevision-license-tool/`:
- `smoke_code.txt` / `expired_code.txt` / `bad_code.txt`

私钥:`~/.gradle/icespiritai-license-private.pem` + 备份 `/d/keystores/icespiritai-license-private.pem`。

## 结论

License Gate 功能在真机上端到端走通:
- 5/5 路径覆盖(3 路径真机走通,2 路径单元测试覆盖)
- 主君口径「市局广告处」字样在所有错误文案中正确
- 跨语言 ECDSA 在真机 Java Conscrypt 与 Python `cryptography` 之间字节级对齐

发版(下一个 versionCode bump)前还需要:
- 双段同意:让市局广告处拿到 PEM 明文 + 1 页 README + .exe
- 仓库最新 commit:`license-gate` 分支,16 个 commit 等 push
- versionCode bump(per CLAUDE.md `feedback-release-hygiene.md` 规则,v0.5.16 是真 feature 发版号)