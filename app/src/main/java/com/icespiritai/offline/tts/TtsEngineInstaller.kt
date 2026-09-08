package com.icespiritai.offline.tts

import android.content.Context
import android.content.Intent
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
     * Query Gitea release metadata for the given tag. Out-of-band here so
     * unit tests can drive [install] flow without an actual Gitea call; the
     * production wiring (Task 12) routes through `UpdateRepository.fetchApkInfo`
     * but keeps this fallback for the spec's single-tag primary path.
     */
    private fun fetchReleaseInfo(releaseTag: String): TtsEngineReleaseInfo {
        // For now this is a stub returning the canonical release tag/url/sha
        // referenced by the spec §14. The production fetch path is wired in
        // Task 12 alongside FileProvider registration; this method exists so
        // [install]'s flow is exercised end-to-end without mocking.
        return TtsEngineReleaseInfo(
            tag = releaseTag,
            apkUrl = "https://gitea.example/$releaseTag/$APK_NAME",
            sizeBytes = -1L,
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        )
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
        private const val APK_NAME = "icespirit-tts-engine.apk"
        private const val BUFFER_SIZE = 1024 * 1024  // 1 MB
        private const val FSYNC_INTERVAL = 5L * BUFFER_SIZE  // ~5 MB
        const val DEFAULT_RELEASE_TAG = "icespirit-tts-engine-v1.0.0"

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