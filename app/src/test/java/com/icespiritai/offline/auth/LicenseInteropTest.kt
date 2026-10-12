package com.icespiritai.offline.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * 跨语言对齐测试:Python 端 license_tool.py 签出的 ICS1 激活码必须能被
 * Android 端的 LicenseVerifier + LicenseCodec + java.security.Signature
 * 全部接受。等价于"两个实现对同一 payload bytes 的 ECDSA 验签产出一致结果"。
 *
 * Skip 语义:整套测试需 Python + cryptography + openssl 在 PATH 上。
 * 缺其一时通过 assumeTrue 跳过整个 class(JUnit 标记为 skipped,非 failure),
 * 这样 CI / 干净 contributor 机器上不会无故 fail。真正的跨语言对齐信号是
 * 3 个 @Test 都 PASS — 那才能断言 Python ↔ JVM/Conscrypt 的 ECDSA 字节级互操作。
 *
 * 路径假设:Gradle test JVM 的 cwd 是 `app/`(Gradle 默认 test.workingDir = projectDir),
 * 故 `tools/license_tool/license_tool.py` 位于 cwd.parent / tools/license_tool/。
 * setup 中用 walkUp 防御性确认,避免以后 Gradle 升级/手动改动 workingDir 时 silent fail。
 */
class LicenseInteropTest {

    companion object {
        private lateinit var pythonSignedCode: String
        private lateinit var androidPublicKey: java.security.PublicKey
        private val KEY_DIR_NAME = "build/license-tool-test"
        private val PEM_NAME = "interop-test.pem"
        private val PUB_DER_NAME = "interop-test-pub.der"
        private val LEDGER_NAME = "许可证台账.csv"

        /**
         * Gradle test JVM cwd = app/<project>,向上 walk 直到找到 tools/license_tool/license_tool.py。
         * 找到后返回其父目录(项目根);否则返回 null。
         */
        private fun findRepoRoot(): File? {
            var dir: File? = File(".").absoluteFile
            repeat(5) {
                val candidate = dir?.resolve("tools/license_tool/license_tool.py")
                if (candidate != null && candidate.exists()) return dir
                dir = dir?.parentFile
            }
            return null
        }

        @BeforeClass
        @JvmStatic
        fun setup() {
            // 1. 检查 Python + cryptography 可用;不可用时跳过整个测试类
            val pythonOk = try {
                val p = ProcessBuilder(
                    "python", "-c", "import cryptography; print('ok')"
                ).redirectErrorStream(true).start()
                val out = p.inputStream.readBytes()
                p.waitFor() == 0 && out.toString(Charsets.UTF_8).trim() == "ok"
            } catch (t: Throwable) {
                false
            }
            assumeTrue("Python + cryptography 不可用,跳过跨语言测试", pythonOk)

            // 2. 检查 openssl 可用(用于生成测试 keypair)
            val opensslOk = try {
                val p = ProcessBuilder("openssl", "version")
                    .redirectErrorStream(true).start()
                p.waitFor() == 0
            } catch (t: Throwable) {
                false
            }
            assumeTrue("openssl 不可用,跳过跨语言测试", opensslOk)

            // 3. 找到仓库根(防御 cwd 不一定是 app/ 的 corner case)
            val repoRoot = findRepoRoot()
                ?: error("找不到仓库根(tried 5 levels up looking for tools/license_tool/license_tool.py)")

            val keyDir = File(KEY_DIR_NAME).apply { mkdirs() }
            val pemFile = File(keyDir, PEM_NAME)

            // 4. 生成测试 keypair(PEM,只生成一次;后续 test run 复用)
            if (!pemFile.exists()) {
                val openssl = ProcessBuilder(
                    "openssl", "ecparam",
                    "-name", "prime256v1",
                    "-genkey", "-noout",
                    "-out", pemFile.absolutePath
                ).redirectErrorStream(true).start()
                val out = openssl.inputStream.readBytes()
                assumeTrue(
                    "openssl 生成 keypair 失败: ${out.toString(Charsets.UTF_8)}",
                    openssl.waitFor() == 0
                )
            }

            // 5. 清掉旧 ledger — 否则连续跑会产生 "name 已存在" ParseException
            //    (keyDir 持久,ledger 视为 ephemeral test state)
            File(keyDir, LEDGER_NAME).delete()

            // 6. 用 license_tool.py new 签一张码
            //    V2 移除 u 字段后,签发器只接受 name。原先 V1 的 6 字符 uid "intop1" 变体不再需要。
            val licenseTool = File(repoRoot, "tools/license_tool/license_tool.py")
            val code = runPython(
                listOf(
                    "python",
                    licenseTool.absolutePath,
                    "new",
                    "--name", "interoptest",
                    "--d", "ad",
                    "--expire", "20270630",
                    "--pem", pemFile.absolutePath,
                    "--ledger-dir", keyDir.absolutePath
                )
            )
            pythonSignedCode = code.trim()

            // 7. 提取公钥 DER(SubjectPublicKeyInfo)喂给 Android KeyFactory。
            //    写文件而非 stdout:openssl 的诊断信息会污染二进制流。
            val pubDerFile = File(keyDir, PUB_DER_NAME)
            val opensslPub = ProcessBuilder(
                "openssl", "ec",
                "-in", pemFile.absolutePath,
                "-pubout",
                "-outform", "DER",
                "-out", pubDerFile.absolutePath
            ).redirectErrorStream(false).start()
            assumeTrue(
                "openssl ec -pubout 失败(rc=${opensslPub.waitFor()})",
                opensslPub.waitFor() == 0 && pubDerFile.exists()
            )
            val pubDer = pubDerFile.readBytes()

            // 8. 喂给 JKS/JVM EC KeyFactory — Android Conscrypt/BouncyCastle 都能解析 X.509 SPKI
            val kf = KeyFactory.getInstance("EC")
            androidPublicKey = kf.generatePublic(X509EncodedKeySpec(pubDer))
        }

        private fun runPython(args: List<String>): String {
            val p = ProcessBuilder(args).redirectErrorStream(true).start()
            val out = p.inputStream.readBytes()
            val code = p.waitFor()
            if (code != 0) error("Process failed (exit=$code): ${out.toString(Charsets.UTF_8)}")
            return out.toString(Charsets.UTF_8)
        }
    }

    @Test
    fun android_verifier_accepts_python_signed_code() {
        val verifier = LicenseVerifier.fromPublicKey(androidPublicKey)
        val r = verifier.verify(pythonSignedCode, today = 20260101)
        assertTrue("expected Valid but was $r", r is VerifyResult.Valid)
        val valid = r as VerifyResult.Valid
        assertEquals("interoptest", valid.payload.n)
        assertEquals(20270630L, valid.payload.e)
    }

    @Test
    fun android_codec_can_parse_python_signed_code() {
        // round-trip via LicenseCodec.parse
        val parsed = LicenseCodec.parse(pythonSignedCode)
        assertEquals("interoptest", parsed?.payload?.n)
        assertEquals(20270630L, parsed?.payload?.e)
    }

    @Test
    fun android_codec_round_trip_matches_python_payload_bytes() {
        // 字节级 ECDSA 路径:Python 端的 payload bytes 应能被 Android 直接验签。
        // 这是 LicenseCodec.parse 内部的隐式契约 —— 任何把 base64url 段解析为
        // payload bytes 时改写(比如 trim / whitespace)都会让 Signature.verify 失败。
        val parsed = LicenseCodec.parse(pythonSignedCode)!!
        val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initVerify(androidPublicKey)
            update(parsed.payloadBytes)
        }
        assertTrue(
            "Android Signature.verify must accept python-signed bytes",
            sig.verify(parsed.signature)
        )
    }
}
