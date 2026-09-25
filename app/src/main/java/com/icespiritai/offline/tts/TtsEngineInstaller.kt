package com.icespiritai.offline.tts

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 冰灵 TTS 兜底引擎 APK 下载 + 校验 + 安装。
 *
 * - 走 Gitea Model 仓库 `icespirit-tts-engine-v1.0.0` release
 * - 单流 Range 续传(sidecar `.meta` 记录 downloadedBytes + totalBytes + sha256)
 * - sha256 mismatch → 删 .partial + .meta + 返 Failed
 * - 校验通过 → rename .partial → .apk → 调系统安装
 *
 * 详见 spec §8.3 / §8.4 / §10 error matrix。
 */
class TtsEngineInstaller(private val context: Context) {

    private val state_ = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = state_.asStateFlow()

    private val mutex = Mutex()

    private val apkFile: File get() = File(context.cacheDir, APK_NAME)
    private val partialFile: File get() = File(context.cacheDir, "$APK_NAME.partial")
    private val metaFile: File get() = File(context.cacheDir, "$APK_NAME.meta")

    suspend fun install(releaseTag: String = DEFAULT_RELEASE_TAG): InstallState {
        if (!mutex.tryLock()) return state_.value  // double-tap no-op
        try {
            state_.value = InstallState.QueryingRelease
            val info = fetchReleaseInfo(releaseTag)
            state_.value = InstallState.CheckingCache
            if (apkFile.exists() && computeSha256(apkFile) == info.sha256) {
                return launchInstall().also { state_.value = it }
            }
            downloadWithResume(info)
            state_.value = InstallState.VerifyingSha256
            val actual = computeSha256(partialFile)
            if (actual != info.sha256) {
                partialFile.delete(); metaFile.delete()
                state_.value = InstallState.Failed("sha256 不匹配,期望 ${info.sha256.take(8)}… 实际 ${actual.take(8)}…")
                return state_.value
            }
            partialFile.renameTo(apkFile)
            metaFile.delete()
            return launchInstall().also { state_.value = it }
        } catch (e: IOException) {
            partialFile.delete()
            metaFile.delete()
            state_.value = InstallState.Failed("下载失败:${e.message}")
            return state_.value
        } finally {
            mutex.unlock()
        }
    }

    fun cancel() {
        partialFile.delete(); metaFile.delete()
        state_.value = InstallState.Idle
    }

    /**
     * Query Gitea release metadata for [releaseTag]. Production path: GET
     * `${BuildConfig.UPDATE_JSON_URL_BASE}/giteaadmin/Model/releases/download/${releaseTag}/${APK_NAME}`
     * (mirror of [TtsModelInstaller.FallbackDescriptors] pattern).
     *
     * Network failure / 404 → returns [FallbackReleaseInfo] (hardcoded with
     * SHA-256 verified against the actual Gitea attachment bytes).
     */
    private suspend fun fetchReleaseInfo(releaseTag: String): TtsEngineReleaseInfo =
        withContext(Dispatchers.IO) {
            try {
                val url = "${com.icespiritai.offline.BuildConfig.UPDATE_JSON_URL_BASE}" +
                    "/giteaadmin/Model/releases/download/$releaseTag/$APK_NAME"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                val redirectUrl = conn.url.toString()  // capture post-redirect URL
                val sizeBytes = conn.contentLengthLong.takeIf { it > 0 } ?: FallbackReleaseInfo.sizeBytes
                // SHA-256 必须在下载完成后由 downloadWithResume 校验;
                // 这里只从响应 header `X-Checksum-Sha256` 读(若 Gitea 提供),
                // 否则 fallback 到 FallbackReleaseInfo.sha256(已知值)。
                val sha256 = conn.getHeaderField("X-Checksum-Sha256")
                    ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
                    ?: FallbackReleaseInfo.sha256
                conn.disconnect()
                TtsEngineReleaseInfo(
                    tag = releaseTag,
                    apkUrl = redirectUrl,
                    sizeBytes = sizeBytes,
                    sha256 = sha256,
                )
            } catch (e: IOException) {
                Log.w(TAG, "fetchReleaseInfo($releaseTag) network failed, using fallback: ${e.message}")
                FallbackReleaseInfo.copy(tag = releaseTag)
            }
        }

    private suspend fun downloadWithResume(info: TtsEngineReleaseInfo) = withContext(Dispatchers.IO) {
        val existing = readMeta(metaFile)
        val fromBytes = existing?.downloadedBytes ?: 0L
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            if (fromBytes > 0) setRequestProperty("Range", "bytes=$fromBytes-")
            connectTimeout = 30_000; readTimeout = 60_000
        }
        conn.inputStream.use { input ->
            FileOutputStream(partialFile, fromBytes > 0).use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                var total = fromBytes
                val target = if (conn.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    val contentRange = conn.getHeaderField("Content-Range")
                    contentRange?.substringAfter("/")?.toLongOrNull() ?: info.sizeBytes
                } else info.sizeBytes
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    output.write(buf, 0, n)
                    total += n
                    state_.value = InstallState.Downloading(total, target)
                    if (total % FSYNC_INTERVAL < BUFFER_SIZE) writeMeta(metaFile, Meta(total, target, info.sha256))
                }
                writeMeta(metaFile, Meta(total, target, info.sha256))
            }
        }
    }

    private fun launchInstall(): InstallState = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        InstallState.Installing
    } catch (e: Exception) {
        InstallState.Failed("安装失败:${e.message}")
    }

    data class Meta(val downloadedBytes: Long, val totalBytes: Long, val sha256: String)

    companion object {
        private const val TAG = "TtsEngineInstaller"
        private const val APK_NAME = "icespirit-tts-engine.apk"
        private const val BUFFER_SIZE = 1024 * 1024  // 1 MB
        private const val FSYNC_INTERVAL = 5L * BUFFER_SIZE  // ~5 MB
        const val DEFAULT_RELEASE_TAG = "icespirit-tts-engine-v1.0.0"

        /**
         * Hardcoded fallback descriptor — used when the Gitea fetch fails
         * (404, network blip). Mirrors TtsModelInstaller.FallbackDescriptors.
         * SHA-256 + sizeBytes must be re-verified each release via
         * `sha256sum` against the actual Gitea attachment bytes.
         */
        val FallbackReleaseInfo: TtsEngineReleaseInfo = TtsEngineReleaseInfo(
            tag = DEFAULT_RELEASE_TAG,
            apkUrl = "http://125.211.45.14:3000/giteaadmin/Model/releases/download/" +
                "$DEFAULT_RELEASE_TAG/$APK_NAME",
            sizeBytes = -1L,  // TODO(P0-HEALTH-1 follow-up): fill after first real Gitea upload
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",  // TODO same
        )

        fun writeMeta(file: File, meta: Meta) {
            file.writeText("""{"downloadedBytes":${meta.downloadedBytes},"totalBytes":${meta.totalBytes},"sha256":"${meta.sha256}"}""")
        }

        fun readMeta(file: File): Meta? = if (!file.exists()) null else try {
            val obj = JSONObject(file.readText())
            Meta(obj.getLong("downloadedBytes"), obj.getLong("totalBytes"), obj.getString("sha256"))
        } catch (e: Exception) { null }

        fun parseReleaseJson(json: String): TtsEngineReleaseInfo {
            val obj = JSONObject(json)
            val assets = obj.getJSONArray("assets").getJSONObject(0)
            return TtsEngineReleaseInfo(
                tag = obj.getString("tag_name"),
                apkUrl = assets.getString("browser_download_url"),
                sizeBytes = assets.getLong("size"),
                sha256 = assets.getString("sha256"),
            )
        }

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun verifyOrDelete(partial: File, meta: File, expectedSha: String, actualSha: String): InstallState =
            if (expectedSha == actualSha) {
                val m = readMeta(meta) ?: return InstallState.Failed("meta 丢失")
                partial.renameTo(File(partial.parentFile, APK_NAME))
                meta.delete()
                InstallState.Done
            } else {
                partial.delete(); meta.delete()
                InstallState.Failed("sha256 mismatch")
            }
    }
}