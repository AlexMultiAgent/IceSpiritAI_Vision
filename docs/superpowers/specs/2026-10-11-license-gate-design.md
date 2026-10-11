# License Gate(激活码登录)— 设计规格

日期:2026-10-11
项目:冰灵锐目 (IceSpiritAI_Vision)
目标:在用户首次启动 App 后,通过激活码 + 用户名登录才能进入主功能

---

## 1. 范围与非范围

### 1.1 在范围内(v1 必做)

- 首次启动 / 卸载重装后强制登录,登录前 App 主功能不渲染
- 用户名(文本)+ 激活码(字符串)双输入校验
- 激活码 = 数字签名(ECDSA P-256),私钥离线签发,**零网络依赖**
- 可续期:同一 uid 签发新码覆盖旧码,App 自动识别续期
- 到期日可选(绝对日期 `yyyyMMdd` 或 `0`=永久)
- 签码工具:Windows 桌面程序(独立 `.exe`),市局广告处独立运行
- 加客户/续期**不发版**,工具单点即可

### 1.2 不在范围内(v1 显式不做)

| 项 | 理由 |
|---|---|
| 下载环节限制 | 用户已确认 Gitea 公开 APK,只靠 App 内激活码 |
| 设备绑定 / 一码一机 | 离线方案做不到,留 v2 |
| 扫码登录 | 锦上添花,v2 |
| native 验签加固 | 反编译改 APK 绕过 LicenseVerifier 是已知边界,**接受**;v2 可选 C/C++ 实现 |
| 服务端在线续期 | 全程离线 |
| 密码找回 / 自主改密 | 客户 = 市局广告处的服务对象,工单走市局广告处 |

### 1.3 目标层级(防止误判)

| 攻击 | 防御状态 |
|---|---|
| 拿到 APK 后随手安装直接使用 | **挡住**(本设计目标) |
| 客户把激活码发给朋友 | **不挡**(用户主动分享不在目标范围) |
| 拿到 APK 后 apktool 反编译改掉 LicenseVerifier | **不挡**(逆向不在目标范围,留 v2 加固) |

---

## 2. 激活码格式

```
ICS1.<payload-base64url>.<signature-base64url>
```

### 2.1 payload JSON

| 字段 | 含义 | 例 |
|---|---|---|
| `v` | 码格式版本 | `1` |
| `u` | 客户稳定 ID,6 位字符串,续期不变 | `"a1b2c3"` |
| `n` | 用户名(文本,用于客户自检) | `"acme"` |
| `e` | 到期日 `yyyyMMdd`,或 `0` 表示永久 | `20270630` 或 `0` |

### 2.2 签名

- 算法:ECDSA over NIST P-256(SHA-256)
- 签名输入:payload base64url 字符串的原始字节(签 JSON 字节,不是签解码后的结构)
- 编码:signature DER → base64url(固定 len=76 字符左右; 比 raw r||s 长但跨语言兼容)

### 2.3 续期语义

- 同 uid(`u` 字段相同)→ 视为续期,DataStore 直接覆盖原码
- 不同 uid → 视为新客户激活,需 DataStore 清空再写

### 2.4 到期日

- 绝对日期字段,**不用「激活后 N 天」**(换手机 / 重装不会重算)
- `0` = 永久

### 2.5 长度估算

- payload 约 80-100 字符
- base64url 后 ~110-130 字符
- signature base64url 后 ~76 字符
- 总长约 **200 字符以内**,单行可读,支持长按复制 / 一键粘贴

---

## 3. 密钥与凭据管理

### 3.1 私钥(ECDSA P-256)

- 路径:`~/.gradle/icespiritai-license-private.pem`(你的开发机,chmod 600)
- 备份:
  - `/d/keystores/icespiritai-license-private.pem`(本机外,与 release keystore 同盘)
  - 一份离线网盘备份
- **永不进 git**,**永不进 APK**
- **首次交付**:`明文 PEM` U 盘当面交市局广告处;后续他们自行用 `签码工具.exe` 导入

### 3.2 公钥

- 在 App 内通过 `buildConfigField LICENSE_PUBLIC_KEY`(Base64 字符串)注入 `BuildConfig`
- 与现有 `BuildConfig.UPDATE_EXPECTED_CERT_SHA256` 完全同一范式(读 env var / fallback)
- 公钥指纹(`SHA-256(pubkey)` 截前 16 hex 字符)= `KEY_FINGERPRINT`,写进 `BuildConfig`
- **客户端仅用公钥**,永远不能导出私钥

### 3.3 私钥指纹校验

签发后任何场景下出现「签出来的码 App 拒绝」时:

1. 客户端读取当前 `BuildConfig.KEY_FINGERPRINT`
2. 用 `license_tool.py info --pem path/to/private.pem` 显示私钥侧指纹
3. 不一致 = 公钥/私钥不匹配,**一定是私钥换过未更新 App 内公钥**

---

## 4. App 端架构

### 4.1 接入点

接入位置:`IceSpiritVisionActivity.kt:97-101` 的根 `setContent {}` 内。**替换 NavHost 而非叠加** —— 未激活用户必须**拿不到也用不了** OCR。

```
setContent {
    val licenseState by licenseViewModel.state.collectAsState()
    when (licenseState) {
        Active -> IceSpiritNavHost(...)        // 现状不变
        else    -> LicenseGateScreen(...)      // NavHost 完全不 compose
    }
}
```

参照现有 `disclaimer_accepted_at` 范式([IceSpiritVisionActivity.kt:97-101](app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt#L97-L101)),但 `disclaimerDialog` 是 overlay,本设计是 **gate 替换**。

### 4.2 组件拆分(每个单测可独立)

| 文件 | 职责 | Android 依赖 |
|---|---|---|
| `auth/LicensePayload.kt` | payload data class + kotlinx.serialization | 无 |
| `auth/LicenseCodec.kt` | 字符串 ↔ (payload, signature),Base64URL 编解码 + 结构校验 | **无** |
| `auth/LicenseVerifier.kt` | 验签(Conscrypt ECDSA)+ 到期判断 | `java.security` + 公钥 |
| `auth/LicenseStore.kt` | DataStore 存取,照抄 `SettingsRepository` 范式 | DataStore |
| `auth/LicenseViewModel.kt` | 状态流(Idle / Verifying / Active / Error)+ 提交校验 | 上面三个 |
| `ui/auth/LicenseGateScreen.kt` | 登录页 | Compose |

`LicenseCodec` 刻意不碰验签 → 它是纯 Kotlin,可与 Python 工具做格式交叉验证测试。

### 4.3 启动流程

每次 `Application.onCreate` / Activity `onCreate` 都跑一遍 `LicenseStore.observeActive` + `LicenseVerifier.verify`,不存布尔标志 `isActivated=true` **而不是** DataStore 里写布尔标志 —— 存**原始激活码字符串**,启动时重跑验签 + 到期判断。单次 ECDSA 公钥运算 < 1ms。

### 4.4 状态机

```
Idle --[submit]--> Verifying --[ok]--> Active(整个 Activity 重 compose)
                    |
                    +----[bad]----> Error(Msg = signature_mismatch | username_mismatch | parse_failed)
                    +----[expired]-> Error(Msg = expired)
```

| Error | 触发条件 | 文案 |
|---|---|---|
| `parse_failed` | 字符串结构错(Base64URL 解码失败 / JSON 字段缺失) | 激活码格式错误,请检查是否复制完整 |
| `signature_mismatch` | payload 任何字节被改 / signature 被改 / 公钥错配 | 激活码无效,请联系市局广告处 |
| `username_mismatch` | payload `n` ≠ 用户输入的用户名 | 用户名与激活码不匹配,请联系市局广告处 |
| `expired` | 当前日期 > payload `e`(且 `e≠0`) | 激活码已于 YYYY-MM-DD 到期,请联系市局广告处续期 |

错误文案放 `strings.xml`,**所有「请联系市局广告处」字眼一致**,每条独立字符串以备本地化。错误时**保留输入**,按钮允许重试,符合现有 `SettingsViewModel` snackbar 范式。

### 4.5 UX 要点

- 登录页不做花哨拖动 / 手势,**输入框 + 登录按钮**即可,与现有「免责声明」一致
- **续期零摩擦**:用户拿到新码重新登录,App 自动判断 `u` 相同覆盖,**无「续期」按钮**
- **到期前 14 天首屏顶部 banner**:实现方式参照现有 `stallDetectorJob` 时机把控(别太早让人焦虑)
- **登录失败不消耗次数**:签名验签 O(1),无限重试
- **没有「忘记密码」/「注册」入口**:工单走市局广告处
- **设置页保留「切换账号 /重新登录」入口**:加 Card,客户拿到续期码时不用先退登

### 4.6 持久化

- DataStore Preferences(`preferencesDataStore(name = "license")`,与 `settings` 同一家族)
- 明文存激活码字符串
- Android 12+ 已禁 adb backup,文件拿不到
- **v1 不上 Keystore AES-GCM**(会引入换手机/卸载重装/指纹重置的失败模式)

---

## 5. 签码工具

### 5.1 形态

- Python 3 + PySide6(Qt) GUI + `cryptography` 库
- 核心纯函数 `tools/license_tool/license_tool.py`(CLI 形态,UI 仅是其 wrapper)
- PyInstaller 一次打包产出 `签码工具.exe`,**市局电脑免装 Python**

### 5.2 子命令

```
python license_tool.py new     --uid<6 位> --name <用户名> --expire <yyyyMMdd | 0=永久>
python license_tool.py renew   --uid <uid> --expire <yyyyMMdd | 0=永久>
python license_tool.py info    --code '<ICS1....>'  # 仅解 payload,不验签,销售助理可跑
python license_tool.py verify  --code '<ICS1....>'  # 验签,需要私钥,内部用
```

### 5.3 安全策略

- **`renew` 自动拒绝缩短期限**:若新 expire 早于或等于台账当前值,**拒绝并提示**「客户已是永久 / 已续到更晚」。防误操作
- **`new` uid 不可复用**:已签发的 uid 不能重新 `new`(必须 `renew`),防止 uid 撞车把别人覆盖
- `info` 命令不需要私钥,可单独交给销售助理
- `verify` 命令读私钥,UI 上标注「内部使用」

### 5.4 私钥导入

- 首次启动 .exe:弹「导入私钥」对话框,粘贴 PEM → `cryptography` 库 DPAPI 加密存到 `%APPDATA%\IceSpiritAI\license_key.dpapi`
- DPAPI 绑本机 + 当前用户 → 拷到其他电脑**打不开**
- 私钥丢失 = 用 `.pem` 备份重新导入

### 5.5 UI 形态

```
 ┌────────────────────────────────────────┐
  │  冰灵锐目 激活码签发工具                │
  │ ────────────────────────────────────── │
  │  UID:    [______]  ← 6 位,自动建议 │
  │  名称:   [______]  ← 用户/机构代号     │
  │  到期日: [______]  ← yyyyMMdd,留空=永久│
  │                                        │
  │  [ 签发新码 ]  [ 续期 ]  [ 查询码信息 ] │
  │                                        │
  │  结果:                                  │
  │  ┌──────────────────────────────────┐  │
  │  │ ICS1.eyJ2Ijox...  │
  │  │              [复制] [导出.csv]   │  │
  │  └──────────────────────────────────┘  │
  │                                        │
  │  台账: 2026 年共签发 X 条 / 续期 Y 条   │
  └────────────────────────────────────────┘
```

### 5.6 台账

- `许可证台账.csv`(gitignored,与私钥同目录)
- 每次 `new` / `renew` 追加一行:`ts,op,uid,name,issued_expire,note`
- `info` 命令(可选增强)读 CSV 反查某 uid 历史

---

## 6. 测试与跨语言校验

| 层 | 文件 | 覆盖 |
|---|---|---|
| 纯函数(无 Android) | `LicenseCodecTest` | Base64URL 编解码、payload 解析、`ICS1.x.y` 缺段/段过短/非 base64url |
| 验签(JVM) | `LicenseVerifierTest` | 正确签通过;payload 任何字节篡改拒;signature 篡改拒;公钥错配拒;过期拒;`e:0` 永久通过 |
| 续期语义 | `LicenseStoreTest` | 同 uid 第二次写入覆盖前次;不同 uid 共存;空 DataStore 启动返回 null |
| ViewModel | `LicenseViewModelTest` | 4 种 Error 状态触发路径;成功路径不残留 Error |
| **跨语言** | `LicenseInteropTest`(集成) | **用 Python `license_tool.py new` 现场签一张码,Android `LicenseVerifier.verify` 必须通过** —— 唯一一处真正验证两端 ECDSA 实现对齐 |
| UI | `LicenseGateScreenTest`(Robolectric) | 输入合法码显示 Active;输入 `signature_mismatch` 显示「请联系市局广告处」+ 按钮可点 |
| 用户流程 | `LicenseFlowTest` | 首次启动 = gate;提交合法码 = gate 消失 + NavHost compose;卸载重装 = 重新 gate

工具侧 `license_tool.py` 用 pytest 单独跑,不进 Android 单测套件(沿用 `testDebugUnitTest` 范式)。

---

## 7. 发版接入

### 7.1 首次发版(一次性 setup)

1. 本机 `cryptography` 跑一次:`openssl ecparam -name prime256v1 -genkey -noout -out icespiritai-license-private.pem` + 提取公钥
2. 公钥 base64 写到 `app/build.gradle.kts` 新增 `buildConfigField LICENSE_PUBLIC_KEY`,沿用现有 `if (System.getenv(...) != null) ... else hardcoded fallback` 范式
3. 公钥 SHA-256 截前 16 hex 写到 `KEY_FINGERPRINT`
4. 私钥备份:`/d/keystores/icespiritai-license-private.pem` + 离线网盘
5. PyInstaller 打包 `签码工具.exe`,输出到 `/d/icevision-license-tool/`
6. `user-changelog.md` 顶部加 prose 条目(参照主君 2026-10-06 反馈,用户能读懂):
   > 现在使用本 App 需要激活码,首次启动请输入市局广告处给您的用户名和激活码。

### 7.2 后续发版

- 加客户/续期 **不发版**,签码工具独立使用
- 发版 checklist 注明「加客户不需要发版,签码工具用即可」

### 7.3 CLAUDE.md 更新

新增「license tooling」一节(进 git,所有协作者可见):

- 私钥路径
- 台账路径
- 工具命令
- 续期策略
- 「请联系市局广告处」字眼统一

---

## 8. 客户交付链

```
开发者(你)
  │  生成 ECDSA keypair
  │  备份 ./ 签码工具.exe / .pem 明文
  │
  ▼
市局广告处(他们电脑)
  │  启动 .exe 导入 .pem
  │  DPAPI 加密私钥存到 %APPDATA%
  │
  ▼
市局广告处日常签发 / 续期(零网络)
  │  把「用户名 + 激活码」发给客户
  │
  ▼
客户设备(App)
  │  首次启动输入 → 验签 → 进入主功能
  │  续期:新码重新登录自动覆盖
```

---

## 9. 边界与风险

| 风险 | 缓解 |
|---|---|
| 私钥泄漏 | PEM 当面交 + DPAPI 本机加密 + 多份离线备份;**失钥 = 所有已激活客户需重发版**(极高代价) |
| 客户主动分享码 | 不挡(不在目标范围) |
| 客户换手机 / 卸载重装 | 必须重新登录(Android 硬约束);体验与微信/支付宝一致 |
| 客户改系统时间骗过到期 | 用 DataStore `activatedAt` 戳 + 绝对到期日二选一判断;用户改时间只会让 `now` 变小不会变大 |
| APK 反编译改 LicenseVerifier | 接受(v2 加固:native C/C++) |
| 私钥轮换 | v2:同时签两个公钥进 APK,新公钥签发 + 老公钥留 6 个月过渡期 |

---

## 10. 关键文件位置(实施时参照)

| 文件 | 状态 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/auth/LicensePayload.kt` | 新建 |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseCodec.kt` | 新建 |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseVerifier.kt` | 新建 |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseStore.kt` | 新建 |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseViewModel.kt` | 新建 |
| `app/src/main/java/com/icespiritai/offline/ui/auth/LicenseGateScreen.kt` | 新建 |
| `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt` | 改:根 setContent 走 license state 分支 |
| `app/build.gradle.kts` | 改:新增 `buildConfigField LICENSE_PUBLIC_KEY` + `KEY_FINGERPRINT` |
| `app/src/main/res/values/strings.xml` | 改:新增 4 种错误文案 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseCodecTest.kt` | 新建 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseVerifierTest.kt` | 新建 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseStoreTest.kt` | 新建 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseViewModelTest.kt` | 新建 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseInteropTest.kt` | 新建(集成) |
| `app/src/test/java/com/icespiritai/offline/ui/auth/LicenseGateScreenTest.kt` | 新建 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseFlowTest.kt` | 新建 |
| `tools/license_tool/license_tool.py` | 新建(纯函数 CLI) |
| `tools/license_tool/license_tool_gui.py` | 新建(PySide6) |
| `tools/license_tool/release.py` | 新建(PyInstaller 打包脚本) |
| `tools/license_tool/README.md` | 新建(1 页交付说明) |
| `~/.gradle/icespiritai-license-private.pem` | 本地,gitignored |
| `/d/keystores/icespiritai-license-private.pem` | 本地,gitignored |
| `许可证台账.csv` | 工具同目录,gitignored |
| `app/src/main/assets/user-changelog.md` | 改:加 prose 条目 |
| `CLAUDE.md` | 改:加 license tooling 节 |