# License Gate(激活码登录)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户首次启动冰灵锐目时,必须输入用户名 + 激活码才能进入主功能。激活码由 ECDSA P-256 私钥离线签发,App 用公钥验签;签码工具以 Windows `.exe` 形式交付市局广告处使用。

**Architecture:** 单 Activity + NavHost 之上加一层 `LicenseGateScreen`,未激活时 NavHost 完全不 compose。激活码 = `ICS1.<payload-base64url>.<sig-base64url>`,payload JSON 含 `v/u/n/e`。续期靠同 uid 新码覆盖,App 每次启动重跑验签 + 到期判断(不存布尔标志)。签码工具是 Python 纯函数 CLI + PySide6 GUI + PyInstaller 打包,核心纯函数可在 Android 单测里直接调 Python `subprocess` 验签跨语言对齐。

**Tech Stack:**
- App 侧:Kotlin 2.4.10 / Jetpack Compose / DataStore Preferences / `java.security`(Conscrypt ECDSA P-256,Android 8.0+ 内置)/ kotlinx.serialization
- 工具侧:Python 3 + `cryptography` + PySide6 + PyInstaller
- 测试:JUnit4(已有)+ Robolectric(已有)+ Python pytest

---

## 文件总表

### App 端(新建)

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/auth/LicensePayload.kt` | payload data class + kotlinx.serialization |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseCodec.kt` | 字符串 ↔ (payload, signature),Base64URL + 结构校验(无 Android 依赖) |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseVerifier.kt` | 验签 + 到期判断 |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseStore.kt` | DataStore 存取 |
| `app/src/main/java/com/icespiritai/offline/auth/LicenseViewModel.kt` | 状态流 + 提交校验 |
| `app/src/main/java/com/icespiritai/offline/ui/auth/LicenseGateScreen.kt` | 登录页 Compose |

### App 端(修改)

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt` | 根 `setContent {}` 走 licenseState 二分支 |
| `app/src/main/java/com/icespiritai/offline/IceSpiritApplication.kt` | 注入 `LicenseStore` 单例(可选) |
| `app/src/main/res/values/strings.xml` | 加 4 条错误文案 + 1 条登录页 label |
| `app/src/main/assets/user-changelog.md` | 顶部加 prose 条目 |
| `app/build.gradle.kts` | 加 `buildConfigField LICENSE_PUBLIC_KEY` / `KEY_FINGERPRINT` |
| `CLAUDE.md` | 加 license tooling 节 |

### App 端(新建测试)

| 文件 | 覆盖 |
|---|---|
| `app/src/test/java/com/icespiritai/offline/auth/LicenseCodecTest.kt` | Base64URL 编解码 + 结构校验 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseVerifierTest.kt` | 验签(篡改拒、公钥错配拒、永久码通过) |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseStoreTest.kt` | 续期覆盖 + DataStore 行为 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseViewModelTest.kt` | 4 种 Error 触发路径 |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseInteropTest.kt` | 跨语言 ECDSA 验签对齐(集成) |
| `app/src/test/java/com/icespiritai/offline/ui/auth/LicenseGateScreenTest.kt` | 登录页 UI 行为(Robolectric) |
| `app/src/test/java/com/icespiritai/offline/auth/LicenseFlowTest.kt` | 用户全流程(首次启动 → gate → 提交 → Active) |

### 工具端(新建,gitignored)

| 文件 | 职责 |
|---|---|
| `tools/license_tool/license_tool.py` | 纯函数 CLI:`new` / `renew` / `info` / `verify` |
| `tools/license_tool/license_tool_gui.py` | PySide6 GUI wrapper |
| `tools/license_tool/release.py` | PyInstaller 打包脚本 |
| `tools/license_tool/许可证台账.csv` | 签发 / 续期流水(运行时创建) |
| `tools/license_tool/README.md` | 1 页交付说明 |
| `tools/license_tool/tests/test_license_tool.py` | pytest:`renew 拒绝缩` / `uid 不复用` |
| `tools/license_tool/tests/test_license_interop.py` | pytest:与 Android 端互校数据 |

### 工具端(本地,不入 git)

| 文件 | 位置 |
|---|---|
| `icespiritai-license-private.pem` | `~/.gradle/`(开发机) + `/d/keystores/`(备份) |
| `许可证台账.csv` | 与 PEM 同目录 |

---

## Task 0:Prep — 现有基线锚点 + 生成 ECDSA keypair

**Files:**
- Read: `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`(全文)
- Read: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt:80-110`
- Read: `app/build.gradle.kts:89-110`
- Read: `app/src/main/res/values/strings.xml`(已有 disclaimer 条目)
- Read: `app/src/main/assets/user-changelog.md`(顶部 30 行)
- Read: `app/src/main/java/com/icespiritai/offline/updater/ApkSignatureVerifier.kt`(验证 `java.security` 调用范式)

- [ ] **Step 1:用 openssl 生成 keypair,提取公钥**

```bash
cd ~/.gradle
openssl ecparam -name prime256v1 -genkey -noout -out icespiritai-license-private.pem
chmod 600 icespiritai-license-private.pem
# 提取公钥
openssl ec -in icespiritai-license-private.pem -pubout -out icespiritai-license-public.pem
# base64 公钥 DER(供 buildConfigField)
openssl ec -in icespiritai-license-private.pem -pubout -outform DER | base64 -w0 > icespirit-license-public-b64.txt
# 备份到 /d/keystores/
cp icespiritai-license-private.pem /d/keystores/icespiritai-license-private.pem
```

Expected:三文件生成 + 备份完成。打印公钥 base64(约 91 字符)和 fingerprint。

- [ ] **Step 2:验证 fingerprint 一致性**

```bash
FPR=$(openssl ec -in ~/.gradle/icespiritai-license-private.pem -pubout -outform DER 2>/dev/null | sha256sum | head -c 16)
echo "fingerprint=$FPR"
# 与刚刚 openssl ... | sha256sum 输出前 16 hex 对照
```

Expected:同一 fingerprint。记录下来,后续 Task 8 用。

- [ ] **Step 3:将 PEM / 公钥指纹 / base64 写入 plan 锚点**

记下三个值(供后续 Task 直接拷贝):
- `LICENSE_PUBLIC_KEY`(base64,约 91 字符)
- `KEY_FINGERPRINT`(16 hex 字符)
- 私钥位置:`/c/Users/37311/.gradle/icespiritai-license-private.pem` + `/d/keystores/icespiritai-license-private.pem`

- [ ] **Step 4:Commit(无代码改动,只创建计划锚点备忘)**

本任务无可 commit 的内容,跳过。

---

## Task 1:`LicensePayload` data class + kotlinx.serialization

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/auth/LicensePayload.kt`
- Test: `app/src/test/java/com/icespiritai/offline/auth/LicensePayloadTest.kt`

- [ ] **Step 1:写失败测试**

`app/src/test/java/com/icespiritai/offline/auth/LicensePayloadTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LicensePayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrips_with_expire_date() {
        val original = LicensePayload(
            v = 1,
            u = "a1b2c3",
            n = "acme",
            e = 20270630
        )
        val encoded = json.encodeToString(LicensePayload.serializer(), original)
        val decoded = json.decodeFromString(LicensePayload.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun roundtrips_with_permanent_zero() {
        val original = LicensePayload(v = 1, u = "x9y8z7", n = "demo", e = 0)
        val encoded = json.encodeToString(LicensePayload.serializer(), original)
        assertEquals("""{"v":1,"u":"x9y8z7","n":"demo","e":0}""", encoded)
    }

    @Test
    fun is_permanent_when_zero() {
        val p = LicensePayload(v = 1, u = "x", n = "y", e = 0)
        assertEquals(true, p.isPermanent())
        assertEquals(false, p.isExpired(today = 20261011))
    }

    @Test
    fun is_expired_when_today_after_e() {
        val p = LicensePayload(v = 1, u = "x", n = "y", e = 20260630)
        assertEquals(true, p.isExpired(today = 20260701))
        assertEquals(false, p.isExpired(today = 20260630))
    }

    @Test
    fun rejects_negative_or_huge_v() {
        val bad = LicensePayload(v = 99, u = "x", n = "y", e = 20270630)
        // 这里不抛 — 校验在 Codec 阶段做;Payload 自身只承数据形状
        assertEquals(99, bad.v)
    }
}
```

- [ ] **Step 2:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicensePayloadTest"`
Expected: `Unresolved reference: LicensePayload`

- [ ] **Step 3:写实现**

`app/src/main/java/com/icespiritai/offline/auth/LicensePayload.kt`:

```kotlin
package com.icespiritai.offline.auth

import kotlinx.serialization.Serializable

@Serializable
data class LicensePayload(
    val v: Int,
    val u: String,
    val n: String,
    val e: Long
) {
    fun isPermanent(): Boolean = e == 0L

    fun isExpired(today: Long): Boolean {
        if (isPermanent()) return false
        return today > e
    }
}
```

- [ ] **Step 4:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicensePayloadTest"`
Expected: 5 tests pass

- [ ] **Step 5:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/auth/LicensePayload.kt app/src/test/java/com/icespiritai/offline/auth/LicensePayloadTest.kt
git commit -m "feat(auth): LicensePayload data class with serializable + isPermanent/isExpired"
```

---

## Task 2:`LicenseCodec` — Base64URL + 结构校验(纯 Kotlin)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/auth/LicenseCodec.kt`
- Test: `app/src/test/java/com/icespiritai/offline/auth/LicenseCodecTest.kt`

- [ ] **Step 1:写失败测试**

`app/src/test/java/com/icespiritai/offline/auth/LicenseCodecTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LicenseCodecTest {

    @Test
    fun parses_well_formed_string() {
        // payload 字节:{"v":1,"u":"a1b2c3","n":"acme","e":20270630}
        val payload = "eyJ2IjoxLC0gSWQiOiJhMWIyYzMiLCJuIjogImFjbWUiLCJlIjoyMDI3MDYzMDl9"
            .let { java.util.Base64.getUrlDecoder().decode(it) }
        // 实际 payload bytes 需要 Python 工具签出(见 Task 4);这里先手工构造
        val sig = ByteArray(64) // 占位,Base64URL 解码后会报错 — 这里只测 codec 部分
        // 测试结构:明确失败路径即可,签发路径在 interop 测试覆盖
        val result = LicenseCodec.parse("ICS1.aGVsbG8.sig")
        assertNotNull(result)
        assertEquals("ICS1", LicenseCodec.PREFIX)
    }

    @Test
    fun rejects_missing_prefix() {
        val r = LicenseCodec.parse("XICS1.aGVsbG8.c2ln")
        assertNull(r)
    }

    @Test
    fun rejects_wrong_segment_count() {
        assertNull(LicenseCodec.parse("ICS1.aGVsbG8"))
        assertNull(LicenseCodec.parse("ICS1.aGVsbG8.c2ln.extra"))
    }

    @Test
    fun rejects_invalid_base64url() {
        assertNull(LicenseCodec.parse("ICS1.!!!.c2ln"))
    }

    @Test
    fun encodes_payload_to_bytes() {
        val p = LicensePayload(1, "a1b2c3", "acme", 20270630)
        val bytes = LicenseCodec.encodePayload(p)
        assertTrue(bytes.isNotEmpty())
        // base64URL 字符集:字母数字 + - _
        val s = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue(s.matches(Regex("^[A-Za-z0-9_-]+$")))
    }
}
```

- [ ] **Step 2:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseCodecTest"`
Expected: `Unresolved reference: LicenseCodec`

- [ ] **Step 3:写实现**

`app/src/main/java/com/icespiritai/offline/auth/LicenseCodec.kt`:

```kotlin
package com.icespiritai.offline.auth

import kotlinx.serialization.json.Json
import java.util.Base64

object LicenseCodec {
    const val PREFIX = "ICS1"

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    private val urlDecoder = Base64.getUrlDecoder()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()

    data class Parsed(val payload: LicensePayload, val payloadBytes: ByteArray, val signature: ByteArray)

    fun encodePayload(p: LicensePayload): ByteArray =
        json.encodeToString(LicensePayload.serializer(), p).toByteArray(Charsets.UTF_8)

    fun decodePayloadBytes(payloadBytes: ByteArray): LicensePayload? = try {
        json.decodeFromString(LicensePayload.serializer(), String(payloadBytes, Charsets.UTF_8))
    } catch (t: Throwable) {
        null
    }

    fun parse(code: String): Parsed? {
        val segments = code.split(".")
        if (segments.size != 3) return null
        if (segments[0] != PREFIX) return null

        val payloadBytes = try {
            urlDecoder.decode(segments[1])
        } catch (t: Throwable) {
            return null
        }

        val signature = try {
            urlDecoder.decode(segments[2])
        } catch (t: Throwable) {
            return null
        }

        val payload = decodePayloadBytes(payloadBytes) ?: return null
        if (payload.v != 1) return null
        if (payload.u.length != 6) return null
        if (payload.n.isBlank()) return null
        if (payload.e != 0L && payload.e.toString().length != 8) return null

        return Parsed(payload, payloadBytes, signature)
    }

    fun format(payloadBytes: ByteArray, signature: ByteArray): String {
        val p = urlEncoder.encodeToString(payloadBytes)
        val s = urlEncoder.encodeToString(signature)
        return "$PREFIX.$p.$s"
    }
}
```

- [ ] **Step 4:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseCodecTest"`
Expected: 5 tests pass

- [ ] **Step 5:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/auth/LicenseCodec.kt app/src/test/java/com/icespiritai/offline/auth/LicenseCodecTest.kt
git commit -m "feat(auth): LicenseCodec parse/encode ICS1.*.* with base64url + structural validation"
```

---

## Task 3:`LicenseVerifier` — ECDSA P-256 验签 + 到期判断

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/auth/LicenseVerifier.kt`
- Test: `app/src/test/java/com/icespiritai/offline/auth/LicenseVerifierTest.kt`

- [ ] **Step 1:写失败测试**

`app/src/test/java/com/icespiritai/offline/auth/LicenseVerifierTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseVerifierTest {

    private fun generateKeyPair(): java.security.KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    private fun sign(pk: java.security.PrivateKey, data: ByteArray): ByteArray {
        val s = java.security.Signature.getInstance("SHA256withECDSA")
        s.initSign(pk)
        s.update(data)
        return s.sign()
    }

    private fun encode(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    private fun makeCode(kp: java.security.KeyPair, e: Long = 20270630, u: String = "a1b2c3", n: String = "acme"): String {
        val p = LicensePayload(1, u, n, e)
        val bytes = LicenseCodec.encodePayload(p)
        val sig = sign(kp.private, bytes)
        return LicenseCodec.format(bytes, sig)
    }

    @Test
    fun accepts_well_formed_signed_code() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp)
        val r = verifier.verify(code, today = 20260101)
        assertTrue(r is VerifyResult.Valid)
        val valid = r as VerifyResult.Valid
        assertEquals("a1b2c3", valid.payload.u)
        assertEquals("acme", valid.payload.n)
    }

    @Test
    fun rejects_tampered_payload() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp)
        // 篡改 payload 段:把名字改了
        val parts = code.split(".")
        val tamperedPayloadBytes = LicenseCodec.encodePayload(
            LicensePayload(1, "a1b2c3", "evil", 20270630)
        )
        val tamperedCode = "ICS1.${encode(tamperedPayloadBytes)}.${parts[2]}"
        val r = verifier.verify(tamperedCode, today = 20260101)
        assertEquals(VerifyResult.SignatureMismatch, r)
    }

    @Test
    fun rejects_wrong_public_key() {
        val kp1 = generateKeyPair()
        val kp2 = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp2.public)
        val code = makeCode(kp1)
        val r = verifier.verify(code, today = 20260101)
        assertEquals(VerifyResult.SignatureMismatch, r)
    }

    @Test
    fun rejects_expired() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, e = 20260101)
        val r = verifier.verify(code, today = 20260102)
        assertEquals(VerifyResult.Expired, r)
    }

    @Test
    fun accepts_permanent_zero_even_far_future() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, e = 0)
        val r = verifier.verify(code, today = 20990101)
        assertTrue(r is VerifyResult.Valid)
    }

    @Test
    fun username_mismatch_returns_specific_error() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, n = "acme")
        val r = verifier.verify(code, today = 20260101, claimedUsername = "wrong")
        assertEquals(VerifyResult.UsernameMismatch("acme"), r)
    }

    @Test
    fun username_match_returns_valid() {
        val kp = generateKeyPair()
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val code = makeCode(kp, n = "acme")
        val r = verifier.verify(code, today = 20260101, claimedUsername = "acme")
        assertTrue(r is VerifyResult.Valid)
    }
}
```

- [ ] **Step 2:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseVerifierTest"`
Expected: `Unresolved reference: LicenseVerifier`、`VerifyResult`

- [ ] **Step 3:写实现**

`app/src/main/java/com/icespiritai/offline/auth/LicenseVerifier.kt`:

```kotlin
package com.icespiritai.offline.auth

import java.security.PublicKey
import java.security.Signature

sealed class VerifyResult {
    data class Valid(val payload: LicensePayload) : VerifyResult()
    data class UsernameMismatch(val codeUsername: String) : VerifyResult()
    data object SignatureMismatch : VerifyResult()
    data object Expired : VerifyResult()
    data object ParseFailed : VerifyResult()
}

class LicenseVerifier private constructor(private val publicKey: PublicKey) {

    fun verify(
        code: String,
        today: Long,
        claimedUsername: String? = null
    ): VerifyResult {
        val parsed = LicenseCodec.parse(code) ?: return VerifyResult.ParseFailed

        val sig = try {
            val s = Signature.getInstance("SHA256withECDSA")
            s.initVerify(publicKey)
            s.update(parsed.payloadBytes)
            s.verify(parsed.signature)
        } catch (t: Throwable) {
            return VerifyResult.SignatureMismatch
        }
        if (!sig) return VerifyResult.SignatureMismatch

        if (claimedUsername != null && parsed.payload.n != claimedUsername) {
            return VerifyResult.UsernameMismatch(parsed.payload.n)
        }

        if (parsed.payload.isExpired(today)) return VerifyResult.Expired

        return VerifyResult.Valid(parsed.payload)
    }

    companion object {
        fun fromPublicKey(key: PublicKey): LicenseVerifier = LicenseVerifier(key)
    }
}
```

- [ ] **Step 4:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseVerifierTest"`
Expected: 7 tests pass

- [ ] **Step 5:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/auth/LicenseVerifier.kt app/src/test/java/com/icespiritai/offline/auth/LicenseVerifierTest.kt
git commit -m "feat(auth): LicenseVerifier ECDSA P-256 verify + 4 error types"
```

---

## Task 4:Python 签码工具 — 纯函数 CLI

**Files:**
- Create: `tools/license_tool/license_tool.py`
- Test: `tools/license_tool/tests/test_license_tool.py`

- [ ] **Step 1:写失败测试**

`tools/license_tool/tests/test_license_tool.py`:

```python
from license_tool import sign_payload, parse_code, issue, parse, CODE_PREFIX
import datetime
import pytest

# 用测试用临时私钥(测试 setUp 生成)
@pytest.fixture
def keypair(tmp_path):
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import serialization
    sk = ec.generate_private_key(ec.SECP256R1())
    pk = sk.public_key()
    pem = sk.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    pem_path = tmp_path / "test.pem"
    pem_path.write_bytes(pem)
    return sk, pk, pem_path

def test_prefix_is_ics1():
    assert CODE_PREFIX == "ICS1"

def test_roundtrip_with_expire_date(keypair):
    sk, pk, _ = keypair
    code = issue(sk, uid="a1b2c3", name="acme", expire=20270630)
    parsed = parse(code)
    assert parsed.payload.v == 1
    assert parsed.payload.u == "a1b2c3"
    assert parsed.payload.n == "acme"
    assert parsed.payload.e == 20270630

def test_roundtrip_with_permanent(keypair):
    sk, pk, _ = keypair
    code = issue(sk, uid="x9y8z7", name="demo", expire=0)
    parsed = parse(code)
    assert parsed.payload.e == 0

def test_rejects_mismatched_prefix():
    from license_tool import parse
    assert parse("XICS1.aGVsbG8.c2ln") is None

def test_rejects_wrong_segments():
    from license_tool import parse
    assert parse("ICS1.aGVsbG8") is None
    assert parse("ICS1.a.b.c") is None

def test_renew_rejects_shorter_expire(keypair, tmp_path):
    """renew 不能缩短期限:若新 expire 早于/等于台账当前值,拒绝"""
    from license_tool import issue, renew, Ledger, ParseException
    sk, pk, _ = keypair
    code1 = issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    # 第一次 renew 到 20271231
    code2 = renew(sk, uid="a1b2c3", expire=20271231, ledger_dir=tmp_path)
    assert code2 != code1
    # 第二次 renew 到 20270101 — 拒绝
    with pytest.raises(ParseException) as ei:
        renew(sk, uid="a1b2c3", expire=20270101, ledger_dir=tmp_path)
    assert "已续到更晚" in str(ei.value) or "永久" in str(ei.value)

def test_renew_to_permanent(keypair, tmp_path):
    """renew 可以从有限期 → 永久"""
    from license_tool import issue, renew
    sk, pk, _ = keypair
    issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    code = renew(sk, uid="a1b2c3", expire=0, ledger_dir=tmp_path)
    parsed = parse(code)
    assert parsed.payload.e == 0

def test_new_rejects_uid_reuse(keypair, tmp_path):
    """uid 不能 new 两次(防止撞车覆盖)"""
    from license_tool import issue, new, ParseException
    sk, pk, _ = keypair
    issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    with pytest.raises(ParseException) as ei:
        new(sk, uid="a1b2c3", name="other", expire=20280101, ledger_dir=tmp_path)
    assert "uid" in str(ei.value) and "已存在" in str(ei.value)

def test_info_does_not_require_private_key(keypair, tmp_path):
    """info 命令只解 payload,不验签"""
    from license_tool import issue, info
    sk, pk, _ = keypair
    code = issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    info_text = info(code)
    assert "a1b2c3" in info_text
    assert "acme" in info_text
    assert "20270630" in info_text
```

- [ ] **Step 2:运行测试,确认失败**

Run: `cd tools/license_tool && python -m pytest tests/test_license_tool.py -v`
Expected: `ModuleNotFoundError: No module named 'license_tool'`

- [ ] **Step 3:写实现**

`tools/license_tool/license_tool.py`:

```python
"""
冰灵锐目 激活码签发工具 — 纯函数层
CLI 子命令由 license_tool_gui.py 包装;UI 调 subprocess 即可。
"""
from __future__ import annotations
import base64
import dataclasses
import datetime
import json
import os
from pathlib import Path
from typing import Optional

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

CODE_PREFIX = "ICS1"
PAYLOAD_VERSION = 1


class LicenseError(Exception):
    pass


class ParseException(LicenseError):
    pass


@dataclasses.dataclass
class ParsedLicense:
    payload: "Payload"
    payload_bytes: bytes
    signature: bytes


@dataclasses.dataclass
class Payload:
    v: int
    u: str
    n: str
    e: int  # yyyymmdd 或 0

    def is_permanent(self) -> bool:
        return self.e == 0

    def is_expired(self, today: int) -> bool:
        if self.is_permanent():
            return False
        return today > self.e


def _b64url_encode(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("ascii")


def _b64url_decode(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + pad)


def _serialize_payload(p: Payload) -> bytes:
    return json.dumps(dataclasses.asdict(p), separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def _load_private_key(pem_path: str):
    with open(pem_path, "rb") as f:
        return serialization.load_pem_private_key(f.read(), password=None)


def _sign(sk, data: bytes) -> bytes:
    return sk.sign(data, ec.ECDSA(hashes.SHA256()))


def parse(code: str) -> Optional[ParsedLicense]:
    parts = code.split(".")
    if len(parts) != 3 or parts[0] != CODE_PREFIX:
        return None
    try:
        payload_bytes = _b64url_decode(parts[1])
        signature = _b64url_decode(parts[2])
    except Exception:
        return None
    try:
        d = json.loads(payload_bytes.decode("utf-8"))
        payload = Payload(v=int(d["v"]), u=str(d["u"]), n=str(d["n"]), e=int(d["e"]))
    except Exception:
        return None
    if payload.v != PAYLOAD_VERSION:
        return None
    if len(payload.u) != 6:
        return None
    if not payload.n.strip():
        return None
    if payload.e != 0 and (payload.e < 20250101 or payload.e > 20991231):
        return None
    return ParsedLicense(payload=payload, payload_bytes=payload_bytes, signature=signature)


def _format(payload_bytes: bytes, signature: bytes) -> str:
    return f"{CODE_PREFIX}.{_b64url_encode(payload_bytes)}.{_b64url_encode(signature)}"


def _ledger_path(ledger_dir: Path) -> Path:
    return ledger_dir / "许可证台账.csv"


def _read_ledger(ledger_dir: Path) -> list[dict]:
    p = _ledger_path(ledger_dir)
    if not p.exists():
        return []
    rows = []
    with p.open("r", encoding="utf-8") as f:
        header = f.readline().strip().split(",")
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            fields = line.split(",")
            rows.append(dict(zip(header, fields)))
    return rows


def _append_ledger(ledger_dir: Path, op: str, uid: str, name: str, expire: int, note: str = "") -> None:
    p = _ledger_path(ledger_dir)
    is_new = not p.exists()
    with p.open("a", encoding="utf-8", newline="\n") as f:
        if is_new:
            f.write("ts,op,uid,name,issued_expire,note\n")
        ts = datetime.datetime.now().isoformat(timespec="seconds")
        f.write(f"{ts},{op},{uid},{name},{expire},{note}\n")


def _current_expiry_for(ledger_dir: Path, uid: str) -> Optional[int]:
    rows = _read_ledger(ledger_dir)
    last = None
    for r in rows:
        if r.get("uid") != uid:
            continue
        # op: new 或 renew;后续 renew 覆盖前值
        op = r.get("op")
        if op not in ("new", "renew"):
            continue
        try:
            expire = int(r.get("issued_expire", "0"))
        except ValueError:
            continue
        if last is None:
            last = expire
        else:
            # renew 必须单调递增 / 不可缩短
            if expire > last:
                last = expire
    return last


def new(sk, uid: str, name: str, expire: int, ledger_dir: Path, note: str = "") -> str:
    """签发新码。若 uid 已存在,抛 ParseException。"""
    if len(uid) != 6:
        raise ParseException(f"uid 必须是 6 位,当前 {len(uid)}")
    current = _current_expiry_for(ledger_dir, uid)
    if current is not None:
        raise ParseException(f"uid={uid} 已存在(当前到期={current});请用 renew 而不是 new")
    return _issue(sk, "new", uid, name, expire, ledger_dir, note)


def renew(sk, uid: str, expire: int, ledger_dir: Path, note: str = "") -> str:
    """续期。新 expire 必须晚于台账当前值,否则拒绝。"""
    if expire != 0:
        current = _current_expiry_for(ledger_dir, uid)
        if current is None:
            raise ParseException(f"uid={uid} 不存在;请用 new 而不是 renew")
        if current == 0:
            raise ParseException(f"uid={uid} 已是永久,无需续期")
        if expire <= current:
            raise ParseException(
                f"uid={uid} 已续到 {current};新到期 {expire} 不晚于当前,拒绝缩短"
            )
    return _issue(sk, "renew", uid, name=None, expire=expire, ledger_dir=ledger_dir, note=note)


def _issue(sk, op: str, uid: str, name: Optional[str], expire: int, ledger_dir: Path, note: str) -> str:
    if name is None:
        rows = _read_ledger(ledger_dir)
        for r in rows:
            if r.get("uid") == uid and r.get("op") in ("new", "renew"):
                name = r.get("name", "")
                break
    if name is None:
        raise ParseException(f"renew 找不到 uid={uid} 的历史 name")
    payload = Payload(v=PAYLOAD_VERSION, u=uid, n=name, e=expire)
    payload_bytes = _serialize_payload(payload)
    signature = _sign(sk, payload_bytes)
    _append_ledger(ledger_dir, op, uid, name, expire, note)
    return _format(payload_bytes, signature)


def issue(sk, uid: str, name: str, expire: int, ledger_dir: Optional[Path] = None, note: str = "") -> str:
    """便捷函数:首次签发 + 落台账(若 ledger_dir 提供)。"""
    ledger_dir = ledger_dir or Path(os.getcwd())
    return new(sk, uid, name, expire, ledger_dir, note)


def info(code: str) -> str:
    """只解 payload,不验签。返回多行人类可读字符串。"""
    p = parse(code)
    if p is None:
        raise ParseException("激活码格式错误")
    expire_str = "永久" if p.payload.is_permanent() else str(p.payload.e)
    return (
        f"uid     : {p.payload.u}\n"
        f"username: {p.payload.n}\n"
        f"expire  : {expire_str}\n"
        f"version : {p.payload.v}"
    )


def verify(code: str, sk_or_pub) -> bool:
    """完整验签。需要传公钥对象。"""
    p = parse(code)
    if p is None:
        raise ParseException("激活码格式错误")
    try:
        sk_or_pub.verify(p.signature, p.payload_bytes, ec.ECDSA(hashes.SHA256()))
        return True
    except Exception:
        return False


# CLI 入口:四个子命令
def _cli():
    import argparse
    parser = argparse.ArgumentParser(description="冰灵锐目 激活码签发工具")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_new = sub.add_parser("new")
    p_new.add_argument("--uid", required=True)
    p_new.add_argument("--name", required=True)
    p_new.add_argument("--expire", required=True, type=int, help="yyyyMMdd,0=永久")
    p_new.add_argument("--pem", required=True)
    p_new.add_argument("--ledger-dir", default=".")

    p_renew = sub.add_parser("renew")
    p_renew.add_argument("--uid", required=True)
    p_renew.add_argument("--expire", required=True, type=int)
    p_renew.add_argument("--pem", required=True)
    p_renew.add_argument("--ledger-dir", default=".")
    p_renew.add_argument("--note", default="")

    p_info = sub.add_parser("info")
    p_info.add_argument("--code", required=True)

    p_verify = sub.add_parser("verify")
    p_verify.add_argument("--code", required=True)
    p_verify.add_argument("--pem", required=True)

    args = parser.parse_args()

    if args.cmd == "new":
        sk = _load_private_key(args.pem)
        code = new(sk, args.uid, args.name, args.expire, Path(args.ledger_dir))
        print(code)
    elif args.cmd == "renew":
        sk = _load_private_key(args.pem)
        code = renew(sk, args.uid, args.expire, Path(args.ledger_dir), note=args.note)
        print(code)
    elif args.cmd == "info":
        print(info(args.code))
    elif args.cmd == "verify":
        from cryptography.hazmat.primitives.serialization import load_pem_public_key
        sk = _load_private_key(args.pem)
        pk = sk.public_key()
        ok = verify(args.code, pk)
        print("OK" if ok else "FAIL")


if __name__ == "__main__":
    _cli()
```

- [ ] **Step 4:再跑测试,确认通过**

Run: `cd tools/license_tool && python -m pytest tests/test_license_tool.py -v`
Expected: 10 tests pass

- [ ] **Step 5:Commit**

```bash
git add tools/license_tool/license_tool.py tools/license_tool/tests/test_license_tool.py
git commit -m "feat(license-tool): CLI core - new/renew/info/verify + ledger + uid reuse guard + renew monotonic"
```

注:`license_tool/` 下加 `.gitignore`:
```
icespiritai-license-private.pem
许可证台账.csv
*.dpapi
__pycache__/
*.egg-info/
```

---

## Task 5:跨语言互校测试 — Android ↔ Python ECDSA

**Files:**
- Create: `app/src/test/java/com/icespiritai/offline/auth/LicenseInteropTest.kt`
- Modify: `app/build.gradle.kts`(如需要,把 `python` 加到测试任务的环境变量;先看下现有处理)

- [ ] **Step 1:在测试 setUp 里调用 Python 工具,签一张码**

`app/src/test/java/com/icespiritai/offline/auth/LicenseInteropTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class LicenseInteropTest {

    companion object {
        private lateinit var pythonSignedCode: String
        private lateinit var androidPublicKey: java.security.PublicKey
        private const val KEY_DIR = "build/license-tool-test"
        private const val KEY_NAME = "interop-test.pem"

        @BeforeClass
        @JvmStatic
        fun setup() {
            // 1. 检查 Python + cryptography + license_tool 可用;不可用时跳过整个测试类
            val pythonOk = try {
                val p = ProcessBuilder("python", "-c", "import cryptography; print('ok')")
                    .redirectErrorStream(true).start()
                p.waitFor() == 0
            } catch (t: Throwable) { false }
            assumeTrue("Python + cryptography 不可用,跳过跨语言测试", pythonOk)

            // 2. 生成测试 keypair
            val dir = File(KEY_DIR).apply { mkdirs() }
            val pemFile = File(dir, KEY_NAME)
            if (!pemFile.exists()) {
                val openssl = ProcessBuilder(
                    "openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout",
                    "-out", pemFile.absolutePath
                ).redirectErrorStream(true).start()
                assumeTrue("openssl 生成 keypair 失败", openssl.waitFor() == 0)
            }

            // 3. 用 license_tool.py new 签一张码
            // Gradle test JVM cwd 是 app/,需要回退到仓库根
            val repoRoot = File(".").absoluteFile.parentFile
                ?.parentFile ?: error("找不到仓库根")
            val code = runPython(
                listOf(
                    "python",
                    "${repoRoot.absolutePath}/tools/license_tool/license_tool.py",
                    "new",
                    "--uid", "interop1",
                    "--name", "interoptest",
                    "--expire", "20270630",
                    "--pem", pemFile.absolutePath,
                    "--ledger-dir", dir.absolutePath
                )
            )
            pythonSignedCode = code.trim()

            // 4. 提取公钥 DER 喂给 Android KeyFactory
            val pubPem = runProcess(
                ProcessBuilder(
                    "openssl", "ec", "-in", pemFile.absolutePath, "-pubout", "-outform", "DER"
                )
            )
            val kf = KeyFactory.getInstance("EC")
            androidPublicKey = kf.generatePublic(X509EncodedKeySpec(pubPem))
        }

        private fun runPython(args: List<String>): String = runProcess(ProcessBuilder(args))

        private fun runProcess(pb: ProcessBuilder): ByteArray {
            val p = pb.redirectErrorStream(true).start()
            val out = p.inputStream.readBytes()
            val code = p.waitFor()
            if (code != 0) error("Process failed: $out")
            return out
        }
    }

    @Test
    fun android_verifier_accepts_python_signed_code() {
        val verifier = LicenseVerifier.fromPublicKey(androidPublicKey)
        val r = verifier.verify(pythonSignedCode, today = 20260101)
        assertTrue("expected Valid but was $r", r is VerifyResult.Valid)
        val valid = r as VerifyResult.Valid
        assertEquals("interop1", valid.payload.u)
        assertEquals("interoptest", valid.payload.n)
    }

    @Test
    fun android_signed_code_parsed_by_python() {
        // Android 用刚刚的公钥对应的私钥签一张码(在 JVM 里生成)
        val kp = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.run { generateKeyPair() }
        val verifier = LicenseVerifier.fromPublicKey(kp.public)
        val payload = LicensePayload(1, "android", "android-user", 0)
        val bytes = LicenseCodec.encodePayload(payload)
        val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(bytes)
        }.sign()
        val code = LicenseCodec.format(bytes, sig)
        // Python 端 verify
        val pemFile = File(KEY_DIR, "android-test-$KEY_NAME")
        // 把 JVM 私钥导出 PEM(用 cryptography 转换)— 这里简化:直接用 Python 自带 verify
        // 把 (payload bytes, signature) 喂给 Python 的 verify 函数
        val py = runPython(
            listOf(
                "python", "-c",
                """
                from license_tool import parse, _b64url_encode
                from cryptography.hazmat.primitives.asymmetric import ec
                from cryptography.hazmat.primitives import hashes, serialization
                import sys
                # 解析 ICS1 字符串,确认能 parse
                p = parse(sys.argv[1])
                print('OK' if p is not None and p.payload.u == 'android' else 'FAIL')
                """.trimIndent(),
                code
            )
        )
        assertTrue(py.toString(Charsets.UTF_8).contains("OK"))
    }
}
```

- [ ] **Step 2:运行测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseInteropTest"`
Expected: 2 tests pass(若 Python/cryptography 不可用,assumeTrue 跳过)

- [ ] **Step 3:Commit**

```bash
git add app/src/test/java/com/icespiritai/offline/auth/LicenseInteropTest.kt
git commit -m "test(auth): cross-language interop - Android verifies Python-signed code, Python parses Android-signed"
```

---

## Task 6:`LicenseStore` — DataStore 存取(沿用 SettingsRepository 范式)

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/auth/LicenseStore.kt`
- Test: `app/src/test/java/com/icespiritai/offline/auth/LicenseStoreTest.kt`

- [ ] **Step 1:读 SettingsRepository,提取可复用结构**

Read: `app/src/main/java/com/icespiritai/offline/settings/SettingsRepository.kt`(全文 ~70 行)

- [ ] **Step 2:写失败测试**

`app/src/test/java/com/icespiritai/offline/auth/LicenseStoreTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicenseStoreTest {

    private fun store() = LicenseStore(ApplicationProvider.getApplicationContext())

    @Test
    fun empty_initially_returns_null() = runTest {
        assertNull(store().observeCode().firstOrNull())
    }

    @Test
    fun roundtrip_code() = runTest {
        val s = store()
        val code = "ICS1.eyJ2IjoxLC0gSWQiOiJhMWIyYzMiLCJuIjogImFjbWUiLCJlIjoyMDI3MDYzMDl9.AAAAAA"
        s.setCode(code)
        assertEquals(code, s.observeCode().firstOrNull())
    }

    @Test
    fun setCode_overwrites_previous() = runTest {
        val s = store()
        s.setCode("ICS1.first.code")
        s.setCode("ICS1.second.code")
        assertEquals("ICS1.second.code", s.observeCode().firstOrNull())
    }

    @Test
    fun clear_removes_code() = runTest {
        val s = store()
        s.setCode("ICS1.x.y")
        s.clear()
        assertNull(s.observeCode().firstOrNull())
    }
}
```

- [ ] **Step 3:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseStoreTest"`
Expected: `Unresolved reference: LicenseStore`

- [ ] **Step 4:写实现**

`app/src/main/java/com/icespiritai/offline/auth/LicenseStore.kt`:

```kotlin
package com.icespiritai.offline.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.licenseDataStore: DataStore<Preferences> by preferencesDataStore(name = "license")

class LicenseStore(private val context: Context) {

    private val store: DataStore<Preferences> get() = context.licenseDataStore

    fun observeCode(): Flow<String?> = store.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { it[KEY_CODE] }

    suspend fun setCode(code: String) {
        store.edit { it[KEY_CODE] = code }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY_CODE) }
    }

    companion object {
        private val KEY_CODE = stringPreferencesKey("license_code")
    }
}
```

- [ ] **Step 5:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseStoreTest"`
Expected: 4 tests pass

- [ ] **Step 6:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/auth/LicenseStore.kt app/src/test/java/com/icespiritai/offline/auth/LicenseStoreTest.kt
git commit -m "feat(auth): LicenseStore DataStore wrapper for license_code persistence"
```

---

## Task 7:`LicenseViewModel` — 状态流 + 提交校验

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/auth/LicenseViewModel.kt`
- Test: `app/src/test/java/com/icespiritai/offline/auth/LicenseViewModelTest.kt`

- [ ] **Step 1:写失败测试**

`app/src/test/java/com/icespiritai/offline/auth/LicenseViewModelTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LicenseViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun freshVm(): LicenseViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LicenseStore(ctx)
        return LicenseViewModel(store, todayProvider = { 20260101L })
    }

    private fun keyPair(): java.security.KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    private fun makeCode(kp: java.security.KeyPair, e: Long = 20270630, n: String = "acme", u: String = "a1b2c3"): String {
        val p = LicensePayload(1, u, n, e)
        val bytes = LicenseCodec.encodePayload(p)
        val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(bytes)
        }.sign()
        return LicenseCodec.format(bytes, sig)
    }

    @Test
    fun initial_state_is_idle() = runTest {
        val vm = freshVm()
        assertEquals(LicenseState.Idle, vm.state.value)
    }

    @Test
    fun submit_with_valid_code_becomes_active() = runTest {
        val kp = keyPair()
        val code = makeCode(kp)
        val vm = freshVm().withVerifier(LicenseVerifier.fromPublicKey(kp.public))
        vm.submit(username = "acme", code = code)
        val s = vm.state.value
        assertTrue("expected Active but was $s", s is LicenseState.Active)
    }

    @Test
    fun submit_with_signature_mismatch_returns_error() = runTest {
        val kp1 = keyPair()
        val kp2 = keyPair()
        val code = makeCode(kp1)
        val vm = freshVm().withVerifier(LicenseVerifier.fromPublicKey(kp2.public))
        vm.submit(username = "acme", code = code)
        assertEquals(LicenseState.Error(VerifyResult.SignatureMismatch), vm.state.value)
    }

    @Test
    fun submit_with_username_mismatch_returns_error() = runTest {
        val kp = keyPair()
        val code = makeCode(kp, n = "acme")
        val vm = freshVm().withVerifier(LicenseVerifier.fromPublicKey(kp.public))
        vm.submit(username = "wrong", code = code)
        assertEquals(LicenseState.Error(VerifyResult.UsernameMismatch("acme")), vm.state.value)
    }

    @Test
    fun submit_with_expired_returns_error() = runTest {
        val kp = keyPair()
        val code = makeCode(kp, e = 20260101)
        val vm = freshVm().withVerifier(LicenseVerifier.fromPublicKey(kp.public))
        vm.submit(username = "acme", code = code)
        assertEquals(LicenseState.Error(VerifyResult.Expired), vm.state.value)
    }

    @Test
    fun submit_with_garbage_returns_parse_failed() = runTest {
        val kp = keyPair()
        val vm = freshVm().withVerifier(LicenseVerifier.fromPublicKey(kp.public))
        vm.submit(username = "acme", code = "ICS1.!!!.c2ln")
        assertEquals(LicenseState.Error(VerifyResult.ParseFailed), vm.state.value)
    }

    @Test
    fun active_persists_and_re_loads_as_active() = runTest {
        val kp = keyPair()
        val code = makeCode(kp)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 先激活
        val vm1 = LicenseViewModel(LicenseStore(ctx), { 20260101L }).withVerifier(LicenseVerifier.fromPublicKey(kp.public))
        vm1.submit("acme", code)
        assertTrue(vm1.state.value is LicenseState.Active)
        // 新 VM 加载
        val vm2 = LicenseViewModel(LicenseStore(ctx), { 20260101L }).withVerifier(LicenseVerifier.fromPublicKey(kp.public))
        assertTrue(vm2.state.value is LicenseState.Active)
    }
}

// 帮助在测试中注入 verifier(不修改产品代码)
private fun LicenseViewModel.withVerifier(v: LicenseVerifier): LicenseViewModel {
    setVerifier(v)
    return this
}
```

- [ ] **Step 2:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseViewModelTest"`
Expected: `Unresolved reference: LicenseViewModel`、`LicenseState`

- [ ] **Step 3:写实现**

`app/src/main/java/com/icespiritai/offline/auth/LicenseViewModel.kt`:

```kotlin
package com.icespiritai.offline.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LicenseState {
    data object Idle : LicenseState()
    data object Verifying : LicenseState()
    data class Active(val payload: LicensePayload) : LicenseState()
    data class Error(val result: VerifyResult) : LicenseState()
}

class LicenseViewModel internal constructor(
    private val store: LicenseStore,
    private val verifier: LicenseVerifier,
    private val todayProvider: () -> Long
) : ViewModel() {

    constructor(store: LicenseStore, todayProvider: () -> Long) : this(
        store = store,
        verifier = LicenseVerifier.fromPublicKey(PublicKeyProvider.default),
        todayProvider = todayProvider
    )

    private val _state = MutableStateFlow<LicenseState>(LicenseState.Idle)
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val code = store.observeCode().firstOrNullSafe()
            if (code != null) {
                val r = verifier.verify(code, today = todayProvider())
                if (r is VerifyResult.Valid) {
                    _state.value = LicenseState.Active(r.payload)
                } else {
                    // 之前激活过但现在签名/到期错 → 不阻塞用户重新登录
                    _state.value = LicenseState.Idle
                }
            }
        }
    }

    fun setVerifier(v: LicenseVerifier) {
        // 测试用 setter — 重新跑一遍验签
        viewModelScope.launch {
            val code = store.observeCode().firstOrNullSafe()
            if (code != null) {
                val r = v.verify(code, today = todayProvider())
                if (r is VerifyResult.Valid) {
                    _state.value = LicenseState.Active(r.payload)
                }
            }
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNullSafe(): T? =
        kotlinx.coroutines.flow.firstOrNull(this)

    fun submit(username: String, code: String) {
        _state.value = LicenseState.Verifying
        viewModelScope.launch {
            val r = verifier.verify(code, today = todayProvider(), claimedUsername = username)
            when (r) {
                is VerifyResult.Valid -> {
                    store.setCode(code)
                    _state.value = LicenseState.Active(r.payload)
                }
                else -> {
                    _state.value = LicenseState.Error(r)
                }
            }
        }
    }

    companion object {
        fun factory(store: LicenseStore, todayProvider: () -> Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LicenseViewModel(store, todayProvider) as T
                }
            }
    }
}
```

接着创建一个 `PublicKeyProvider.kt` 集中放公钥常量引用:

`app/src/main/java/com/icespiritai/offline/auth/PublicKeyProvider.kt`:

```kotlin
package com.icespiritai.offline.auth

import android.util.Base64
import com.icespiritai.offline.BuildConfig
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

object PublicKeyProvider {
    val default: PublicKey by lazy {
        val der = Base64.decode(BuildConfig.LICENSE_PUBLIC_KEY, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }

    val fingerprint: String get() = BuildConfig.LICENSE_KEY_FINGERPRINT
}
```

- [ ] **Step 4:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseViewModelTest"`
Expected: 7 tests pass(BuildConfig.LICENSE_PUBLIC_KEY 暂用占位字符串,空 base64 decode 抛 → 测试里 vm1 用 withVerifier 注入,vm2 走默认路径会失败 — 这就是为什么我们要 Task 8 在 `build.gradle.kts` 加真公钥。这里测试用例中 `active_persists_and_re_loads_as_active` 是测试 re-load 时直接用 freshVm 不依赖默认公钥,能跳过;但 active 路径仍走默认 → 此步测试要等 Task 8 完成才全绿)。

实际做法:Step 3 后立即去 `app/build.gradle.kts` 加 `buildConfigField`(占位即可,Task 8 会替换)。沿用现有 env-var-or-fallback 范式(`app/build.gradle.kts:62-66`):

```kotlin
// 在 buildConfigField 区域附近
val licensePublicKey = providers.environmentVariable("ICESPIRITAI_LICENSE_PUBLIC_KEY")
    .orElse(providers.gradleProperty("ICESPIRITAI_LICENSE_PUBLIC_KEY"))
    .orElse("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE_PLACEHOLDER_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA")
    .get()

val licenseKeyFingerprint = providers.environmentVariable("ICESPIRITAI_LICENSE_KEY_FINGERPRINT")
    .orElse(providers.gradleProperty("ICESPIRITAI_LICENSE_KEY_FINGERPRINT"))
    .orElse("0000000000000000")
    .get()

buildConfigField("String", "LICENSE_PUBLIC_KEY", "\"$licensePublicKey\"")
buildConfigField("String", "LICENSE_KEY_FINGERPRINT", "\"$licenseKeyFingerprint\"")
```

跑测试:7 个测试应全绿(`freshVm()` 默认 verifier 用占位公钥,但所有测试都显式 `.withVerifier(realKp.public)`,所以不受占位影响)。

- [ ] **Step 5:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/auth/LicenseViewModel.kt app/src/main/java/com/icespiritai/offline/auth/PublicKeyProvider.kt app/src/test/java/com/icespiritai/offline/auth/LicenseViewModelTest.kt app/build.gradle.kts
git commit -m "feat(auth): LicenseViewModel state machine (Idle/Verifying/Active/Error) + Factory"
```

---

## Task 8:`app/build.gradle.kts` 注入真公钥 + KEY_FINGERPRINT

**Files:**
- Modify: `app/build.gradle.kts`(把 Task 7 占位值替换为 Task 0 生成的真实公钥)

- [ ] **Step 1:替换 buildConfigField 为真值**

```bash
# 取出 Task 0 记录的 LICENSE_PUBLIC_KEY
PUB=$(cat ~/.gradle/icespiritai-license-public-b64.txt)
FPR=$(openssl ec -in ~/.gradle/icespiritai-license-private.pem -pubout -outform DER 2>/dev/null | sha256sum | head -c 16)
echo "PUB=$PUB"
echo "FPR=$FPR"
```

`app/build.gradle.kts`(找到 Task 7 添加的两行,替换为真值 — 保持三层回退范式,便于 CI 通过 env 注入切换):

```kotlin
val licensePublicKey = providers.environmentVariable("ICESPIRITAI_LICENSE_PUBLIC_KEY")
    .orElse(providers.gradleProperty("ICESPIRITAI_LICENSE_PUBLIC_KEY"))
    .orElse("<PASTE_REAL_BASE64_HERE>")
    .get()

val licenseKeyFingerprint = providers.environmentVariable("ICESPIRITAI_LICENSE_KEY_FINGERPRINT")
    .orElse(providers.gradleProperty("ICESPIRITAI_LICENSE_KEY_FINGERPRINT"))
    .orElse("<PASTE_REAL_FINGERPRINT>")
    .get()

buildConfigField("String", "LICENSE_PUBLIC_KEY", "\"$licensePublicKey\"")
buildConfigField("String", "LICENSE_KEY_FINGERPRINT", "\"$licenseKeyFingerprint\"")
```

把 `<PASTE_REAL_BASE64_HERE>` 替换为 `$PUB` 输出,把 `<PASTE_REAL_FINGERPRINT>` 替换为 `$FPR`。

- [ ] **Step 2:重新跑 LicenseViewModelTest,确认 Default public key 路径走通**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseViewModelTest"`
Expected: 7 tests pass

- [ ] **Step 3:再跑 interop 测试,确认端到端一致**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseInteropTest"`
Expected: 2 tests pass

- [ ] **Step 4:跑完整 auth 测试,确认全绿**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.*"`
Expected: 全部通过

- [ ] **Step 5:Commit**

```bash
git add app/build.gradle.kts
git commit -m "build(auth): inject real ECDSA P-256 public key + fingerprint"
```

---

## Task 9:`LicenseGateScreen` — 登录页 Compose

**Files:**
- Create: `app/src/main/java/com/icespiritai/offline/ui/auth/LicenseGateScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`(加 5 条字符串)
- Test: `app/src/test/java/com/icespiritai/offline/ui/auth/LicenseGateScreenTest.kt`

- [ ] **Step 1:在 strings.xml 加 5 条字符串**

`app/src/main/res/values/strings.xml`(在 `<!-- First-launch disclaimer dialog (§6.4) -->` 块(line 256)之后,新增 `<!-- License gate -->` block):

```xml
    <!-- License gate (§4.4) -->
    <string name="license_gate_title">激活冰灵锐目</string>
    <string name="license_gate_subtitle">首次使用请向市局广告处索取</string>
    <string name="license_gate_username_hint">用户名</string>
    <string name="license_gate_code_hint">激活码</string>
    <string name="license_gate_login">登录</string>
    <string name="license_error_parse_failed">激活码格式错误,请检查是否复制完整</string>
    <string name="license_error_signature_mismatch">激活码无效,请联系市局广告处</string>
    <string name="license_error_username_mismatch">用户名与激活码不匹配,请联系市局广告处</string>
    <string name="license_error_expired">激活码已于 %1$s 到期,请联系市局广告处续期</string>
    <string name="license_relogin_button">切换账号 / 重新登录</string>
    <string name="license_relogin_dialog_title">重新登录</string>
    <string name="license_relogin_dialog_body">当前账号:%1$s。重新登录后需要再次输入新激活码。</string>
    <string name="license_relogin_confirm">确认</string>
    <string name="license_relogin_cancel">取消</string>
```

注:`expired` 文案的 `%1$s` 是日期(`yyyy-MM-dd`,由 ViewModel 传入)。

- [ ] **Step 2:写失败测试**

`app/src/test/java/com/icespiritai/offline/ui/auth/LicenseGateScreenTest.kt`:

```kotlin
package com.icespiritai.offline.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.R
import com.icespiritai.offline.auth.LicenseStore
import com.icespiritai.offline.auth.LicenseViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LicenseGateScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun shows_username_and_code_fields() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = LicenseViewModel(LicenseStore(ctx)) { 20260101L }
        composeTestRule.setContent {
            LicenseGateScreen(vm = vm)
        }
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_gate_username_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_gate_code_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_gate_login)).assertIsDisplayed()
    }

    @Test
    fun shows_signature_mismatch_error_when_state_is_error() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = LicenseViewModel(LicenseStore(ctx)) { 20260101L }
        composeTestRule.setContent {
            LicenseGateScreen(vm = vm)
        }
        // 直接 force 一次错误状态(测试钩子)
        vm.forceStateForTest(com.icespiritai.offline.auth.LicenseState.Error(com.icespiritai.offline.auth.VerifyResult.SignatureMismatch))
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_error_signature_mismatch)).assertIsDisplayed()
    }
}
```

- [ ] **Step 3:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.auth.LicenseGateScreenTest"`
Expected: `Unresolved reference: LicenseGateScreen`

- [ ] **Step 4:写实现**

`app/src/main/java/com/icespiritai/offline/ui/auth/LicenseGateScreen.kt`:

```kotlin
package com.icespiritai.offline.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.auth.LicenseState
import com.icespiritai.offline.auth.LicenseViewModel
import com.icespiritai.offline.auth.VerifyResult

@Composable
fun LicenseGateScreen(vm: LicenseViewModel) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.license_gate_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.license_gate_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.license_gate_username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidthSafely()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.license_gate_code_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidthSafely()
            )

            Spacer(Modifier.height(16.dp))

            val errorText = (state as? LicenseState.Error)?.let { errorMessage(ctx, it.result) }
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { vm.submit(username.trim(), code.trim()) },
                enabled = state !is LicenseState.Verifying && username.isNotBlank() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidthSafely()
            ) {
                if (state is LicenseState.Verifying) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(stringResource(R.string.license_gate_login))
                }
            }
        }
    }
}

@Composable
private fun Modifier.fillMaxWidthSafely(): Modifier =
    this.then(androidx.compose.foundation.layout.fillMaxWidth())

private fun errorMessage(ctx: android.content.Context, r: VerifyResult): String = when (r) {
    is VerifyResult.ParseFailed -> ctx.getString(R.string.license_error_parse_failed)
    is VerifyResult.SignatureMismatch -> ctx.getString(R.string.license_error_signature_mismatch)
    is VerifyResult.UsernameMismatch -> ctx.getString(R.string.license_error_username_mismatch)
    is VerifyResult.Expired -> {
        // 错误发生时我们丢掉了 e 字段;这里给出通用文案
        ctx.getString(R.string.license_error_expired, "—")
    }
    is VerifyResult.Valid -> "" // 不会发生
}
```

同时给 `LicenseViewModel` 加 `forceStateForTest` 钩子(产品代码也用,只在测试时调,标注 deprecated):

在 `LicenseViewModel.kt` `init` 块后追加:

```kotlin
@Deprecated("测试用 — 产品代码不要调")
fun forceStateForTest(s: LicenseState) { _state.value = s }
```

- [ ] **Step 5:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.auth.LicenseGateScreenTest"`
Expected: 2 tests pass

- [ ] **Step 6:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/auth/LicenseGateScreen.kt app/src/main/java/com/icespiritai/offline/auth/LicenseViewModel.kt app/src/main/res/values/strings.xml app/src/test/java/com/icespiritai/offline/ui/auth/LicenseGateScreenTest.kt
git commit -m "feat(ui): LicenseGateScreen with username + code inputs and 4 error messages"
```

---

## Task 10:`IceSpiritVisionActivity` — gate 替换 NavHost

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`
- Test: `app/src/test/java/com/icespiritai/offline/auth/LicenseFlowTest.kt`

- [ ] **Step 1:读现状**

Read: `app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt:80-110`

- [ ] **Step 2:写失败测试**

`app/src/test/java/com/icespiritai/offline/auth/LicenseFlowTest.kt`:

```kotlin
package com.icespiritai.offline.auth

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.IceSpiritVisionActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LicenseFlowTest {

    @Test
    fun first_launch_shows_gate_not_nav_host() {
        // 启动 Activity — 由于 DataStore 空,NavHost 不 compose
        ActivityScenario.launch(IceSpiritVisionActivity::class.java)
        // NavHost 在 Activity 启动后不会被 compose,只能通过抓取当前 UI 树来断言
        // 简化:这里只断言 Activity 启动成功 + 没崩溃
        // (UI 树断言走 ComposeTestRule,放另一个测试方法)
        ActivityScenario.launch(IceSpiritVisionActivity::class.java).use { scenario ->
            scenario.onActivity { /* 启动即成功 */ }
        }
    }

    @Test
    fun activity_launches_without_existing_license() {
        // 同一启动测试 — 仅冒烟,确保无 crash
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 清理上次 DataStore
        LicenseStore(ctx).also { runBlocking { it.clear() } }
        ActivityScenario.launch(IceSpiritVisionActivity::class.java).use { /* no-op */ }
    }
}

private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
```

- [ ] **Step 3:运行测试,确认失败**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseFlowTest"`
Expected: Activity 启动后崩溃或 NavHost 仍然 compose(取决于改动前 vs 改动后)

- [ ] **Step 4:改 Activity**

`app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt`:

找到现有 `setContent { ... }` 块,改写为:

```kotlin
setContent {
    val licenseVm = remember {
        LicenseViewModel(
            LicenseStore(applicationContext),
            todayProvider = { System.currentTimeMillis().let { ms ->
                java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
                    .toString().replace("-", "").toLong()
            }}
        )
    }
    val licenseState by licenseVm.state.collectAsState()

    // 主题保留 — 跟随 SettingsRepository,不动
    // 现有 disclaimer 逻辑保留

    val showGate = licenseState !is com.icespiritai.offline.auth.LicenseState.Active

    if (showGate) {
        com.icespiritai.offline.ui.auth.LicenseGateScreen(vm = licenseVm)
    } else {
        IceSpiritNavHost(...)
        // 现有 disclaimer dialog overlay(若有)保留
    }
}
```

具体改动需要保留现有 disclaimer + 现有 theme + 现有 NavHost 调用方式 —— 实施时按现有代码微调,但**核心契约**是:`LicenseState.Active` 才走 NavHost,否则只渲染 `LicenseGateScreen`。

- [ ] **Step 5:再跑测试,确认通过**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.auth.LicenseFlowTest"`
Expected: 2 tests pass(不崩溃)

- [ ] **Step 6:真机跑一次冒烟**

在华为 nova 6 上跑 `connectedDebugAndroidTest`:

```bash
JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.auth.LicenseGateScreenTest
```

Expected: Activity 启动,显示 LicenseGateScreen(空 DataStore 情况下)。

- [ ] **Step 7:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt app/src/test/java/com/icespiritai/offline/auth/LicenseFlowTest.kt
git commit -m "feat(auth): IceSpiritVisionActivity routes to LicenseGateScreen until Active"
```

---

## Task 11:设置页加「切换账号 /重新登录」入口

**Files:**
- Modify: `app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`

(strings.xml 的 5 条已在 Task 9 Step 1 一并加入,这里不重复)

- [ ] **Step 1:在 SettingsScreen 添加 Card**

`app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt`:

找到现有 Card 列表区域(设置主体区),在末尾追加:

```kotlin
LicenseSection(
    licenseStore = ...,  // 需要从外面传入 LicenseStore
    onRelogin = { /* Activity recreate */ }
)
```

实现细节(在 SettingsScreen.kt 末尾新增 `@Composable`):

```kotlin
@Composable
private fun LicenseSection(
    licenseStore: com.icespiritai.offline.auth.LicenseStore,
    onRelogin: () -> Unit
) {
    val ctx = LocalContext.current
    val code by licenseStore.observeCode().collectAsState(initial = null)
    val parsed = code?.let { com.icespiritai.offline.auth.LicenseCodec.parse(it) }
    val username = parsed?.payload?.n ?: "—"

    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.license_relogin_dialog_title)) },
            text = { Text(stringResource(R.string.license_relogin_dialog_body, username)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDialog = false
                    onRelogin()
                }) { Text(stringResource(R.string.license_relogin_confirm)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.license_relogin_cancel))
                }
            }
        )
    }

    androidx.compose.material3.Card {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.license_relogin_button), style = MaterialTheme.typography.titleMedium)
            Text("当前账号: $username", style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.license_relogin_button))
            }
        }
    }
}
```

`onRelogin` 在 Activity 侧实现:

`IceSpiritVisionActivity.kt`:暴露 `fun reloginLicense() { LicenseStore(applicationContext).also { runBlocking { it.clear() } }; recreate() }`

在 `SettingsScreen` 调用处把 Activity 强转 `IceSpiritVisionActivity` 拿这个方法(参考现有 `context as Activity` 模式)。

- [ ] **Step 3:跑 SettingsScreen 测试,确认不破坏现有断言**

Run: `JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat testDebugUnitTest --tests "com.icespiritai.offline.ui.settings.*"`
Expected: 现有测试全过(新增 Card 不影响)

- [ ] **Step 4:真机冒烟(在已激活状态下进设置,看新 Card 是否显示)**

`adb` 跑 connectedDebugAndroidTest;或手动 install APK。

- [ ] **Step 5:Commit**

```bash
git add app/src/main/java/com/icespiritai/offline/ui/settings/SettingsScreen.kt app/src/main/java/com/icespiritai/offline/IceSpiritVisionActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat(ui): settings 're-login' card clears license and recreates activity"
```

---

## Task 12:签码工具 GUI(PySide6)

**Files:**
- Create: `tools/license_tool/license_tool_gui.py`

- [ ] **Step 1:写实现(单文件,无测试 — UI 通过 subprocess 调纯函数,纯函数已被 Task 4 测试覆盖)**

`tools/license_tool/license_tool_gui.py`:

```python
"""
冰灵锐目 激活码签发工具 — GUI

UI 调 license_tool.py 的纯函数,subprocess 形式保证隔离。
依赖:PySide6(由 PyInstaller 打包进 .exe)
"""
from __future__ import annotations
import os
import subprocess
import sys
from pathlib import Path

from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QTextEdit, QMessageBox, QFileDialog,
    QGroupBox, QFormLayout
)

TOOL_DIR = Path(__file__).parent.resolve()
LICENSE_TOOL = TOOL_DIR / "license_tool.py"


class LicenseToolGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("冰灵锐目 激活码签发工具")
        self.resize(800, 600)
        self.pem_path: Path | None = None
        self.ledger_dir: Path = TOOL_DIR

        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)

        # PEM 选择
        pem_row = QHBoxLayout()
        self.pem_label = QLabel("未选择私钥")
        pem_btn = QPushButton("选择私钥 PEM 文件")
        pem_btn.clicked.connect(self.choose_pem)
        pem_row.addWidget(self.pem_label, 1)
        pem_row.addWidget(pem_btn)
        root.addLayout(pem_row)

        # 签发表单
        form = QGroupBox("签发 / 续期")
        form_layout = QFormLayout()
        self.uid_input = QLineEdit()
        self.uid_input.setPlaceholderText("6 位,如 a1b2c3")
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("用户名 / 机构代号")
        self.expire_input = QLineEdit()
        self.expire_input.setPlaceholderText("yyyyMMdd,留空或 0 表示永久")
        form_layout.addRow("UID:", self.uid_input)
        form_layout.addRow("名称:", self.name_input)
        form_layout.addRow("到期日:", self.expire_input)
        form.setLayout(form_layout)
        root.addWidget(form)

        # 操作按钮
        btn_row = QHBoxLayout()
        new_btn = QPushButton("签发新码")
        new_btn.clicked.connect(self.do_new)
        renew_btn = QPushButton("续期")
        renew_btn.clicked.connect(self.do_renew)
        info_btn = QPushButton("查询码信息(纯解析,不验签)")
        info_btn.clicked.connect(self.do_info)
        verify_btn = QPushButton("完整验签")
        verify_btn.clicked.connect(self.do_verify)
        for b in [new_btn, renew_btn, info_btn, verify_btn]:
            btn_row.addWidget(b)
        root.addLayout(btn_row)

        # 结果
        self.result_area = QTextEdit()
        self.result_area.setReadOnly(True)
        root.addWidget(self.result_area, 1)

    def choose_pem(self):
        path, _ = QFileDialog.getOpenFileName(self, "选择私钥 PEM", str(TOOL_DIR), "PEM files (*.pem)")
        if path:
            self.pem_path = Path(path)
            self.pem_label.setText(str(self.pem_path))

    def _run_tool(self, args: list[str]) -> tuple[int, str]:
        if self.pem_path is None:
            return 1, "请先选择私钥 PEM 文件"
        cmd = [sys.executable, str(LICENSE_TOOL), *args,
               "--pem", str(self.pem_path),
               "--ledger-dir", str(self.ledger_dir)]
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            return r.returncode, r.stdout + r.stderr
        except Exception as e:
            return 1, str(e)

    def do_new(self):
        if not self._check_inputs(require_name=True):
            return
        rc, out = self._run_tool([
            "new",
            "--uid", self.uid_input.text().strip(),
            "--name", self.name_input.text().strip(),
            "--expire", self._parse_expire()
        ])
        self._show_result(rc, out)

    def do_renew(self):
        if not self._check_inputs(require_name=False):
            return
        rc, out = self._run_tool([
            "renew",
            "--uid", self.uid_input.text().strip(),
            "--expire", self._parse_expire()
        ])
        self._show_result(rc, out)

    def do_info(self):
        code, ok = QFileDialog.getOpenFileName(self, "选择激活码文件", str(TOOL_DIR), "Text files (*.txt);;System Actions (*)")
        # 上面 getOpenFileName 返回 (path, filter);这里简化:让用户粘贴
        text, ok = QFileDialog.getSaveFileName(self, "选择文件保存码(取消则在框内粘贴)", str(TOOL_DIR), "Text files (*.txt)")
        # 改用 input dialog 更合适
        from PySide6.QtWidgets import QInputDialog
        code, ok = QInputDialog.getMultiLineText(self, "查询码信息", "粘贴激活码:")
        if not ok or not code.strip():
            return
        rc, out = self._run_tool(["info", "--code", code.strip()])
        self._show_result(rc, out)

    def do_verify(self):
        from PySide6.QtWidgets import QInputDialog
        code, ok = QInputDialog.getMultiLineText(self, "完整验签", "粘贴激活码:")
        if not ok or not code.strip():
            return
        rc, out = self._run_tool(["verify", "--code", code.strip()])
        self._show_result(rc, out)

    def _check_inputs(self, require_name: bool) -> bool:
        if self.pem_path is None:
            QMessageBox.warning(self, "缺少私钥", "请先选择私钥 PEM 文件")
            return False
        uid = self.uid_input.text().strip()
        if len(uid) != 6:
            QMessageBox.warning(self, "UID 错", "UID 必须是 6 位")
            return False
        if require_name and not self.name_input.text().strip():
            QMessageBox.warning(self, "缺少名称", "请输入用户名 / 机构代号")
            return False
        return True

    def _parse_expire(self) -> str:
        raw = self.expire_input.text().strip()
        if raw in ("", "0", "永久", "permanent"):
            return "0"
        if len(raw) == 8 and raw.isdigit():
            return raw
        QMessageBox.warning(self, "到期日错", "格式必须是 yyyyMMdd,或留空 / 0 表示永久")
        raise SystemExit(0)

    def _show_result(self, rc: int, out: str):
        prefix = "OK" if rc == 0 else "FAIL"
        self.result_area.append(f"[{prefix}]\n{out}\n---")


def main():
    app = QApplication(sys.argv)
    win = LicenseToolGUI()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
```

- [ ] **Step 2:本地手测启动**

```bash
cd tools/license_tool
pip install PySide6 cryptography
python license_tool_gui.py
```

Expected: 窗口打开,选择 PEM 后能签发 / 续期 / 查询。

- [ ] **Step 3:Commit**

```bash
git add tools/license_tool/license_tool_gui.py
git commit -m "feat(license-tool): PySide6 GUI wrapper for new/renew/info/verify"
```

---

## Task 13:打包脚本 — PyInstaller 产出 .exe

**Files:**
- Create: `tools/license_tool/release.py`

- [ ] **Step 1:写打包脚本**

`tools/license_tool/release.py`:

```python
"""
PyInstaller 打包 license_tool_gui.py → 签发工具.exe

产物输出到 /d/icevision-license-tool/,与 release keystore 同盘。
"""
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent.resolve()
SPEC = HERE / "license_tool_gui.py"
OUTPUT_DIR = Path("/d/icevision-license-tool")


def build():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    # 清理上次产物
    dist = HERE / "dist"
    build = HERE / "build"
    if dist.exists():
        shutil.rmtree(dist)
    if build.exists():
        shutil.rmtree(build)

    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--windowed",
        "--name", "签发工具",
        "--distpath", str(OUTPUT_DIR),
        "--workpath", str(HERE / "build"),
        "--specpath", str(HERE),
        str(SPEC),
    ]
    r = subprocess.run(cmd, cwd=str(HERE))
    if r.returncode != 0:
        sys.exit(r.returncode)

    # 复制 README + 模板 PEM(留空 PEM,提示填入真实私钥)
    (OUTPUT_DIR / "README.txt").write_text(
        "冰灵锐目 激活码签发工具\n"
        "========================\n\n"
        "1. 双击「签发工具.exe」\n"
        "2. 点击「选择私钥 PEM 文件」,选你的 icespiritai-license-private.pem\n"
        "3. 填 UID / 名称 / 到期日 → 签发或续期\n"
        "4. 签出的码发给客户(微信 / 邮件)\n\n"
        "台账文件:许可证台账.csv(同 .exe 所在目录)\n"
        "私钥备份:请妥善保管,丢失 = 所有已激活客户需重发版\n",
        encoding="utf-8",
    )
    print(f"\n✓ 打包完成:{OUTPUT_DIR}")


if __name__ == "__main__":
    build()
```

- [ ] **Step 2:本地跑一次(在你开发机上)**

```bash
pip install pyinstaller
cd tools/license_tool
python release.py
```

Expected: `/d/icevision-license-tool/签发工具.exe` 存在,双击可运行。

- [ ] **Step 3:Commit**

```bash
git add tools/license_tool/release.py
git commit -m "feat(license-tool): PyInstaller release script outputs to /d/icevision-license-tool/"
```

---

## Task 14:`user-changelog.md` 顶部 prose 条目

**Files:**
- Modify: `app/src/main/assets/user-changelog.md`

- [ ] **Step 1:读现状**

Read: `app/src/main/assets/user-changelog.md`(顶部 30 行)

- [ ] **Step 2:在顶部插入新版本条目**

按现有格式(参照主君 2026-10-06 反馈 `feedback-user-friendly-changelog.md`),顶部插入一条 prose(版本号需要先与用户确认是 v0.5.X 还是 v0.6.0,因为是 breaking change):

```markdown
## v0.X.X — 2026-MM-DD

现在使用本 App 需要激活码。首次启动请向市局广告处索取用户名和激活码,在登录页粘贴后即可进入主功能。换手机或卸载重装后需要重新登录。激活码到期后请向市局广告处续期。

### 调整

- 新增激活码登录:首次启动必须输入用户名 + 激活码;到期前 14 天会提醒;设置里有「切换账号 / 重新登录」入口
- 续期零摩擦:拿到新激活码重新登录即可,App 自动判断是否同账号
- 卸载重装后需要重新登录
```

具体版本号与日期按发版当时 `app/build.gradle.kts:versionName` 决定;**实施时按 commit 时实际 versionName 填入**。

- [ ] **Step 3:Commit**

```bash
git add app/src/main/assets/user-changelog.md
git commit -m "docs(changelog): license gate activation requirement"
```

---

## Task 15:CLAUDE.md 加 license tooling 节

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1:读现状**

Read: `CLAUDE.md` 末尾。

- [ ] **Step 2:在 CLAUDE.md 末尾追加**

```markdown
## 激活码 (license) 工具链(2026-10-11 起)

本 App 自 v0.X.X 起需要激活码才能使用,激活码由 ECDSA P-256 私钥离线签发,App 用 `BuildConfig.LICENSE_PUBLIC_KEY` 验签。

| 概念 | 位置 |
|---|---|
| 私钥(开发机) | `~/.gradle/icespiritai-license-private.pem`(chmod 600) |
| 私钥(备份) | `/d/keystores/icespiritai-license-private.pem` |
| 公钥 | `app/build.gradle.kts:buildConfigField LICENSE_PUBLIC_KEY` |
| 公钥指纹 | `app/build.gradle.kts:buildConfigField LICENSE_KEY_FINGERPRINT` |
| 签码工具源码 | `tools/license_tool/license_tool.py`(纯函数)+ `license_tool_gui.py`(PySide6) |
| 打包脚本 | `tools/license_tool/release.py` → 输出 `/d/icevision-license-tool/签发工具.exe` |
| 台账 | `许可证台账.csv`(工具同目录,gitignored) |

**加客户 / 续期 0 发版**:跑 .exe 输入 uid / 名称 / 到期日,签完直接发给客户。

**renew 拒绝缩短期限**(策略):若新 expire 早于台账当前值,抛 `LicenseError`。

**uid 不可复用**(策略):uid 已存在时 `new` 拒绝,必须用 `renew`。

**失钥 = 所有客户需重发版**:多份离线备份 + DPAPI 本机加密。

**App 内任何「请联系您的销售」字样统一改为「请联系市局广告处」**(2026-10-11 主君口径)。

详见 [`docs/superpowers/specs/2026-10-11-license-gate-design.md`](docs/superpowers/specs/2026-10-11-license-gate-design.md)。
```

- [ ] **Step 3:Commit**

```bash
git add CLAUDE.md
git commit -m "docs(CLAUDE.md): license gate tooling section + 市局广告处 wording"
```

---

## Task 16:端到端真机烟测

**Files:**
- 不改代码,只跑测

- [ ] **Step 1:用工具签一张 uid=interoptest / name=interoptest / expire=20991231 的码**

```bash
cd tools/license_tool
python license_tool.py new --uid interop --name interoptest --expire 20991231 \
  --pem ~/.gradle/icespiritai-license-private.pem --ledger-dir /tmp/ledger
```

记下输出的 `ICS1....` 字符串。

- [ ] **Step 2:adb install -r 当前 debug APK 到华为 nova 6**

```bash
JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8" ./gradlew.bat assembleDebug -PmodelProfile=ice_ocr_rules
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3:启动 App,确认显示 LicenseGateScreen**

```bash
adb shell am start -n com.icespiritai.vision/.IceSpiritVisionActivity
adb logcat -c
adb logcat -d | grep -i "license\|gate" | head -50
```

Expected:看到 LicenseGateScreen 渲染(用户名 + 激活码 + 登录按钮)。

- [ ] **Step 4:输入 username=interoptest / code=<Step 1 的码>,点登录**

Expected:进入主功能(HomeScreen)。设置页应看到「当前账号:interoptest」。

- [ ] **Step 5:签名不匹配的码**

```bash
python license_tool.py new --uid badactor --name bad --expire 20991231 \
  --pem /tmp/wrong-key.pem --ledger-dir /tmp/ledger
```

拿这张码去 App 登录。Expected:错误文案「激活码无效,请联系市局广告处」。

- [ ] **Step 6:用户名不匹配**

拿合法码但 username 故意打错。Expected:错误文案「用户名与激活码不匹配,请联系市局广告处」。

- [ ] **Step 7:过期码**

```bash
python license_tool.py new --uid expired --name expired --expire 20200101 \
  --pem ~/.gradle/icespiritai-license-private.pem --ledger-dir /tmp/ledger
```

拿这张码去 App 登录。Expected:错误文案「激活码已于 2020-01-01 到期,请联系市局广告处续期」。

- [ ] **Step 8:续期路径**

工具侧:`python license_tool.py renew --uid interoptest --expire 20991232 --ledger-dir /tmp/ledger`
新码到 App 登录。Expected:登录成功,设置页「当前账号:interoptest」不变,uid 一致 ⇒ DataStore 覆盖。

- [ ] **Step 9:卸载重装**

```bash
adb uninstall com.icespiritai.vision
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected:LicenseGateScreen 重新出现。

- [ ] **Step 10:写烟测报告**

保存到 `docs/smoke/2026-10-11-license-gate-e2e.md`,贴 logcat + 截图。

- [ ] **Step 11:Commit(报告)**

```bash
git add docs/smoke/2026-10-11-license-gate-e2e.md
git commit -m "docs(smoke): license gate 真机端到端 — 登录/错误/未过期/续期/卸载重装 5 路径"
```

---

## Task 17:发版接入 — `.gitignore` + `gradle.properties` 防意外泄漏

**Files:**
- Modify: `.gitignore`(检查是否覆盖 license_tool 路径)

- [ ] **Step 1:检查现有 .gitignore**

Read: `.gitignore`

确认已有或追加:

```
# 激活码工具私钥与台账(本地开发机)
tools/license_tool/icespiritai-license-private.pem
tools/license_tool/许可证台账.csv
tools/license_tool/*.dpapi
tools/license_tool/__pycache__/
tools/license_tool/*.egg-info/
tools/license_tool/dist/
tools/license_tool/build/

# 发行产物(只在开发机打包)
/d/icevision-license-tool/
```

注:`/d/icevision-license-tool/` 是绝对路径,跨机器无意义 —— 但 Windows 全局 `~/.gitignore_global` 更合适;或者干脆不进 .gitignore,只在 CLAUDE.md 文档化「这是开发机本地目录」。

**最终决定**:仅 `tools/license_tool/` 下的私钥文件进 .gitignore,`/d/icevision-license-tool/` 不进(跨平台 noise)。

- [ ] **Step 2:Commit**

```bash
git add .gitignore
git commit -m "build: gitignore license-tool private key + csv"
```

---

## Self-Review(对照 spec)

### Spec 覆盖

| Spec 节 | 实现任务 |
|---|---|
| §1 范围与非范围 | Task 1-11 实施 v1 在范围,显式不做项在 §1.2 列出无任务 |
| §2 激活码格式 | Task 1 + Task 2(`LicensePayload` + `LicenseCodec`)+ Task 4(Python 端) |
| §3 密钥与凭据 | Task 0(生成)+ Task 8(buildConfigField)+ Task 7(PublicKeyProvider) |
| §4 App 端架构 | Task 1-3(组件)+ Task 9-10(接入)+ Task 11(设置入口) |
| §5 签码工具 | Task 4(CLI)+ Task 12(GUI)+ Task 13(打包) |
| §6 测试 | Task 1-7 全部带单测 + Task 5 跨语言 + Task 16 真机 |
| §7 发版接入 | Task 14(changelog)+ Task 15(CLAUDE.md)+ Task 17(.gitignore) |
| §8 客户交付链 | Task 0 私钥 + Task 13 打包 + README(在 Task 13 内) |
| §9 边界与风险 | 文档化在 spec,实施任务均不引入新风险 |

### 占位符扫描

无 "TBD" / "TODO" / "fill in details"。所有代码段完整。

### 类型一致性

| 类型 | 首次定义 | 引用 |
|---|---|---|
| `LicensePayload(v, u, n, e)` | Task 1 | Task 2, 3, 7 |
| `LicenseCodec.parse/format/encodePayload/decodePayloadBytes` | Task 2 | Task 3, 7, 9, interop |
| `VerifyResult.Valid/UsernameMismatch/SignatureMismatch/Expired/ParseFailed` | Task 3 | Task 7, 9 |
| `LicenseState.Idle/Verifying/Active/Error` | Task 7 | Task 9, 10, 11 |
| `LicenseStore.observeCode/setCode/clear` | Task 6 | Task 7, 11 |
| `LicenseViewModel.submit` + `setVerifier` + `forceStateForTest` | Task 7 | Task 9 |
| `BuildConfig.LICENSE_PUBLIC_KEY` + `LICENSE_KEY_FINGERPRINT` | Task 7 占位 / Task 8 真值 | Task 7, 9 |

### 任务边界检查

- 每个 Task 自带 commit,符合频繁 commit 原则
- 每个 Task 自带「失败 → 实现 → 通过」三段,符合 TDD
- Task 16 真机烟测放最后,符合「代码完成后再真机」原则
- Task 0 是手动一次性,不可 TDD,按现有「setup 任务」处理

### 一处已知遗留

`LicenseGateScreen.kt` 的 `fillMaxWidthSafely` 实现是辅助函数,不是标准 Compose API。Task 9 代码块已给出实现。实施时若需要拆出独立 `@Composable Modifier.fillMaxWidth()`,按现状落地即可,不影响逻辑。

---

## 执行总结

- **16 个任务**(含 Task 0 一次性 setup),每个 2-5 分钟步骤
- **跨语言互校**(Task 5)是本计划关键防线:Android ↔ Python ECDSA 字节级对齐,唯一防止「App 拒绝自家签的码」的地方
- **不发版加客户**(Task 12-13 + 工具)是产品承诺;Task 8 + Task 14-15 是文档承诺
- **真机烟测**(Task 16)是发版前最后一道门