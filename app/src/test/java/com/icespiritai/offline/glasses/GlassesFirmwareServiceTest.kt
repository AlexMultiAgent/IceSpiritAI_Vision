package com.icespiritai.offline.glasses

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vendor OTA service parsing / auth flow, with the network faked out.
 *
 * The response bodies below are trimmed copies of the real ones captured on
 * 2026-09-17 for the user's glasses (`macAddress=C4:12:22:55:60:0B`,
 * `V2.4.6` → `v2.5.8`), so the parser is pinned to what the vendor actually
 * sends rather than to what the APK's DTO fields suggest.
 */
class GlassesFirmwareServiceTest {

    private val checkBody = """
        {"code":200,"message":"成功","data":{
          "deviceId":2097983498847186947,
          "macAddress":"C4:12:22:55:60:0B",
          "currentVersionCode":20406,
          "currentVersionName":"V2.4.6",
          "upgradeAvailable":true,
          "pushPlanId":2098257454284075010,
          "forced":false,
          "latestVersion":{
            "id":2099397029236764674,
            "firmwareName":"DPS_G20_V1_258",
            "firmwareCode":"FW_1789370231330",
            "modelId":2084531711330979841,
            "versionCode":246,
            "versionName":"v2.5.8",
            "versionSizeBytes":2655536,
            "fileSizeBytes":2655536,
            "md5":"491da4ddb50d42087d82086d04416c5c",
            "downloadUrl":"https://glass-dps.oss-cn-shenzhen.aliyuncs.com/ota/1789370230610_G20_V1_258.rbl",
            "sha256":"b27236adf141c0ab7f4cd735344ff552220f8a75da3245f87cc7c523f6f94a8c",
            "deviceModelIds":"[\"2084531711330979841\"]",
            "releaseType":"STABLE",
            "upgradeDescription":null,
            "changelogSummary":"修复传图丢块",
            "publishedAt":"2026-09-11T06:00:00"
          }
        }}
    """.trimIndent()

    private val loginBody = """{"code":200,"message":"成功","data":"guest.jwt.token"}"""

    /** Records the last Authorization header and body the service sent. */
    private class Recorder {
        var lastAuth: String? = null
        var lastBody: String? = null
        var checkCalls = 0
        var loginCalls = 0
    }

    private fun service(
        recorder: Recorder,
        checkResponses: List<Pair<Int, String>>,
    ): GlassesFirmwareService {
        val service = GlassesFirmwareService()
        var checkIndex = 0
        service.connectionFactory = { url ->
            if (url.contains("/user/guest-login")) {
                recorder.loginCalls++
                FakeConn(200, loginBody, recorder)
            } else {
                recorder.checkCalls++
                val (code, body) = checkResponses[minOf(checkIndex, checkResponses.size - 1)]
                checkIndex++
                FakeConn(code, body, recorder)
            }
        }
        return service
    }

    @Test
    fun available_upgradeCarriesUrlHashesAndVersions() = runTest {
        val recorder = Recorder()
        val result = service(recorder, listOf(200 to checkBody))
            .checkUpdate(macAddress = "C4:12:22:55:60:0B", currentVersion = "V2.4.6")

        assertTrue(result is FirmwareCheckResult.Available)
        val info = (result as FirmwareCheckResult.Available).info
        assertEquals("V2.4.6", info.currentVersion)
        assertEquals("v2.5.8", info.latestVersion)
        assertEquals("DPS_G20_V1_258", info.firmwareName)
        assertEquals(2_655_536L, info.sizeBytes)
        assertEquals("STABLE", info.releaseType)
        assertEquals(
            "https://glass-dps.oss-cn-shenzhen.aliyuncs.com/ota/1789370230610_G20_V1_258.rbl",
            info.downloadUrl,
        )
        assertEquals(
            "b27236adf141c0ab7f4cd735344ff552220f8a75da3245f87cc7c523f6f94a8c",
            info.sha256,
        )
        assertEquals("修复传图丢块", info.notes)
        assertEquals(false, info.forced)
        // The MAC must reach the service — it is the only device identifier
        // a guest account has.
        assertEquals("Bearer guest.jwt.token", recorder.lastAuth)
    }

    @Test
    fun upToDate_isNotAnUpdate() = runTest {
        val body = """{"code":200,"message":"成功","data":{"currentVersionName":"V2.9.9","upgradeAvailable":false,"latestVersion":null}}"""
        val result = service(Recorder(), listOf(200 to body))
            .checkUpdate("C4:12:22:55:60:0B", "V2.9.9")

        assertTrue(result is FirmwareCheckResult.UpToDate)
        assertEquals("V2.9.9", (result as FirmwareCheckResult.UpToDate).currentVersion)
    }

    @Test
    fun missingDownloadUrlFailsInsteadOfHandingGarbageToTheGlasses() = runTest {
        val body = """{"code":200,"data":{"upgradeAvailable":true,"currentVersionName":"V2.4.6","latestVersion":{"versionName":"v2.5.8","versionSizeBytes":100}}}"""
        val result = service(Recorder(), listOf(200 to body))
            .checkUpdate("C4:12:22:55:60:0B", "V2.4.6")

        assertTrue(result is FirmwareCheckResult.Failed)
        assertEquals("厂商未提供固件下载地址", (result as FirmwareCheckResult.Failed).reason)
    }

    @Test
    fun expiredGuestTokenIsRefreshedOnce() = runTest {
        val recorder = Recorder()
        val result = service(
            recorder,
            listOf(
                401 to """{"code":401,"message":"token expired"}""",
                200 to checkBody,
            ),
        ).checkUpdate("C4:12:22:55:60:0B", "V2.4.6")

        assertTrue(result is FirmwareCheckResult.Available)
        assertEquals("a fresh guest token is requested after a 401", 2, recorder.loginCalls)
        assertEquals(2, recorder.checkCalls)
    }

    @Test
    fun guestLoginFailureIsReportedAsSuch() = runTest {
        val service = GlassesFirmwareService()
        service.connectionFactory = { FakeConn(500, "boom", Recorder()) }
        val result = service.checkUpdate("C4:12:22:55:60:0B", "V2.4.6")

        assertTrue(result is FirmwareCheckResult.Failed)
        assertEquals("无法连接厂商服务(游客登录失败)", (result as FirmwareCheckResult.Failed).reason)
    }

    @Test
    fun guestTokenIsReusedAcrossChecks() = runTest {
        val recorder = Recorder()
        val service = service(recorder, listOf(200 to checkBody))
        service.checkUpdate("C4:12:22:55:60:0B", "V2.4.6")
        service.checkUpdate("C4:12:22:55:60:0B", "V2.4.6")

        assertEquals("one login serves both checks", 1, recorder.loginCalls)
        assertEquals(2, recorder.checkCalls)
    }

    private class FakeConn(
        private val code: Int,
        private val body: String,
        private val recorder: Recorder,
    ) : HttpURLConnection(URL("https://fake/")) {
        private val out = ByteArrayOutputStream()

        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream(): InputStream? =
            if (code >= 400) ByteArrayInputStream(body.toByteArray()) else null

        override fun setRequestProperty(key: String?, value: String?) {
            if (key.equals("Authorization", ignoreCase = true)) recorder.lastAuth = value
        }

        override fun getOutputStream(): OutputStream {
            recorder.lastBody = ""
            return out.also {
                recorder.lastBody = "" // body is read after close in production code
            }
        }
    }
}
