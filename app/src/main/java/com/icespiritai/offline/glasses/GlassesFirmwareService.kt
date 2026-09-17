package com.icespiritai.offline.glasses

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the update check found, flattened for the UI. */
data class FirmwareUpdateInfo(
    val currentVersion: String?,
    val latestVersion: String,
    val firmwareName: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val md5: String?,
    /** Handed to the glasses verbatim — they download it themselves. */
    val downloadUrl: String,
    val releaseType: String?,
    val forced: Boolean,
    val notes: String?,
)

/** Outcome of [GlassesFirmwareService.checkUpdate]. */
sealed class FirmwareCheckResult {
    data class Available(val info: FirmwareUpdateInfo) : FirmwareCheckResult()
    data class UpToDate(val currentVersion: String?) : FirmwareCheckResult()
    data class Failed(val reason: String) : FirmwareCheckResult()
}

/**
 * The vendor's OTA service, as used by the official app
 * (`com.deepvision_tek.glass_front`): `POST /user/guest-login` for an
 * anonymous token, then `GET /user/me/ota/check` with the glasses' MAC.
 *
 * **Guest login needs no account**, and the check needs no bound device —
 * verified on the real glasses 2026-09-17 (see
 * `docs/knowledge/official-glasses-ota-protocol.md` §5). That is what makes
 * an in-app "check for firmware updates" possible without shipping vendor
 * credentials.
 *
 * Endpoint/host live in the official APK's `assets/app_config.json`
 * (`_comment_baseUrl` / `network.baseUrl`), and the same host publishes its
 * OpenAPI document at `/v3/api-docs` (`/user/me/ota/check`,
 * `/user/me/ota/progress`, `/user/me/devices/firmware/report`).
 *
 * Testability follows [com.icespiritai.offline.updater.UpdateRepository]:
 * a blocking [HttpURLConnection] call with an injectable
 * [connectionFactory], so unit tests never touch the network.
 */
class GlassesFirmwareService(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 20_000,
) {

    /** Test hook: replaces the real `URL(...).openConnection()`. */
    var connectionFactory: ((String) -> HttpURLConnection)? = null

    @Volatile
    private var guestToken: String? = null

    /**
     * Ask the vendor whether [macAddress] has a newer firmware than
     * [currentVersion] (e.g. `V2.4.6`, as read off the glasses over BLE).
     */
    suspend fun checkUpdate(
        macAddress: String,
        currentVersion: String?,
    ): FirmwareCheckResult = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append("?macAddress=").append(urlEncode(macAddress))
                if (!currentVersion.isNullOrBlank()) {
                    append("&currentVersion=").append(urlEncode(currentVersion))
                }
            }
            val token = ensureGuestToken() ?: return@withContext FirmwareCheckResult.Failed("无法连接厂商服务(游客登录失败)")
            var response = getJson("/user/me/ota/check$query", token)
            if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // Token expired between checks — log in again, once.
                guestToken = null
                val fresh = ensureGuestToken() ?: return@withContext FirmwareCheckResult.Failed("厂商服务鉴权失败")
                response = getJson("/user/me/ota/check$query", fresh)
            }
            if (response.code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "ota/check HTTP ${response.code}: ${response.body.take(200)}")
                return@withContext FirmwareCheckResult.Failed("查询失败(HTTP ${response.code})")
            }
            parseCheckResponse(response.body)
        } catch (e: IOException) {
            Log.w(TAG, "ota/check IO error", e)
            FirmwareCheckResult.Failed("网络错误:${e.message ?: e.javaClass.simpleName}")
        } catch (e: Throwable) {
            Log.w(TAG, "ota/check failed", e)
            FirmwareCheckResult.Failed(e.message ?: "未知错误")
        }
    }

    /** Drop the cached guest token (used by tests and after an auth failure). */
    fun clearSession() {
        guestToken = null
    }

    private fun ensureGuestToken(): String? {
        guestToken?.let { return it }
        val response = postJson("/user/guest-login", body = "{}", token = null)
        if (response.code != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "guest-login HTTP ${response.code}: ${response.body.take(200)}")
            return null
        }
        val token = runCatching {
            json.parseToJsonElement(response.body).let { element ->
                (element as? kotlinx.serialization.json.JsonObject)
                    ?.get("data")
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
        }.getOrNull()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "guest-login returned no token: ${response.body.take(200)}")
            return null
        }
        guestToken = token
        return token
    }

    private fun parseCheckResponse(body: String): FirmwareCheckResult {
        val envelope = runCatching { json.decodeFromString(CheckEnvelope.serializer(), body) }
            .getOrElse { return FirmwareCheckResult.Failed("返回格式异常") }
        val data = envelope.data ?: return FirmwareCheckResult.Failed(envelope.message ?: "返回内容为空")
        val latest = data.latestVersion
        if (data.upgradeAvailable != true || latest == null) {
            return FirmwareCheckResult.UpToDate(data.currentVersionName)
        }
        val url = latest.downloadUrl
        if (url.isNullOrBlank()) {
            return FirmwareCheckResult.Failed("厂商未提供固件下载地址")
        }
        val versionName = latest.versionName
        if (versionName.isNullOrBlank()) {
            return FirmwareCheckResult.Failed("厂商未提供固件版本号")
        }
        return FirmwareCheckResult.Available(
            FirmwareUpdateInfo(
                currentVersion = data.currentVersionName,
                latestVersion = versionName,
                firmwareName = latest.firmwareName,
                sizeBytes = latest.versionSizeBytes ?: latest.fileSizeBytes,
                sha256 = latest.sha256,
                md5 = latest.md5,
                downloadUrl = url,
                releaseType = latest.releaseType,
                forced = data.forced == true,
                notes = latest.changelogSummary ?: latest.upgradeDescription,
            ),
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // HTTP plumbing
    // ────────────────────────────────────────────────────────────────────

    private data class HttpResult(val code: Int, val body: String)

    private fun getJson(path: String, token: String?): HttpResult =
        request("GET", path, body = null, token = token)

    private fun postJson(path: String, body: String, token: String?): HttpResult =
        request("POST", path, body = body, token = token)

    private fun request(method: String, path: String, body: String?, token: String?): HttpResult {
        val connection = connectionFactory?.invoke(baseUrl + path.trimStart('/'))
            ?: (URL(baseUrl + path.trimStart('/')).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            // The download host in front of the API 403s curl's default UA;
            // matching the official client's keeps both hosts happy.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResult(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "GlassesFirmware"

        /**
         * `network.baseUrl` (prod) from the official APK's
         * `assets/app_config.json`.
         */
        const val DEFAULT_BASE_URL = "https://s1.deepvision-tek.com:8089/"

        /** The official client identifies itself as OkHttp; keep parity. */
        const val USER_AGENT = "okhttp/4.12.0"

        val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    }

    // ── wire DTOs ───────────────────────────────────────────────────────

    @Serializable
    private data class CheckEnvelope(
        val code: Int? = null,
        val message: String? = null,
        val data: CheckData? = null,
    )

    @Serializable
    private data class CheckData(
        val macAddress: String? = null,
        val currentVersionCode: Int? = null,
        val currentVersionName: String? = null,
        val upgradeAvailable: Boolean? = null,
        val forced: Boolean? = null,
        val latestVersion: LatestFirmware? = null,
    )

    @Serializable
    private data class LatestFirmware(
        val firmwareName: String? = null,
        val firmwareCode: String? = null,
        val versionName: String? = null,
        val versionCode: Int? = null,
        val versionSizeBytes: Long? = null,
        @SerialName("fileSizeBytes") val fileSizeBytes: Long? = null,
        val md5: String? = null,
        val sha256: String? = null,
        val downloadUrl: String? = null,
        val releaseType: String? = null,
        val upgradeDescription: String? = null,
        val changelogSummary: String? = null,
    )
}
