package com.icespiritai.offline.updater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object UpdateRepository {

    private const val TAG = "UpdateRepository"

    private val JSON_PARSER = Json { ignoreUnknownKeys = true }

    /**
     * Process-global state. Observed by `SettingsViewModel` via
     * `viewModel.state`; mutated by `checkForUpdates` / `downloadApk` /
     * `requestInstall`. Singleton survives Activity / ViewModel lifetime.
     */
    val state: StateFlow<UpdateState> get() = _state
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /**
     * Test hook: tests set this to inject a fake HttpURLConnection factory.
     * Production callers do NOT set this; the default opens real connections.
     */
    @Volatile
    var connectionFactory: ((String) -> HttpURLConnection)? = null

    private fun openConnection(url: String): HttpURLConnection {
        val f = connectionFactory
        return if (f != null) f(url) else (URL(url).openConnection() as HttpURLConnection)
    }

    /**
     * Read vision-latest.json and compare versionCode. Returns the
     * UpdateCheckResult sealed-class branch; the caller (state machine)
     * translates to UpdateState.
     */
    suspend fun checkForUpdates(
        jsonUrl: String,
        currentVersionCode: Int,
        connectionFactory: ((String) -> HttpURLConnection)? = this.connectionFactory,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = (connectionFactory?.let { it(jsonUrl) }
                ?: (URL(jsonUrl).openConnection() as HttpURLConnection))
                .apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return@withContext UpdateCheckResult.Failed.ServerError(code)
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val info = JSON_PARSER.decodeFromString(AppVersionInfo.serializer(), text)
                if (info.versionCode <= currentVersionCode) {
                    UpdateCheckResult.UpToDate(currentVersionCode)
                } else {
                    UpdateCheckResult.UpdateAvailable(info)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: UnknownHostException) {
            UpdateCheckResult.Failed.NoNetwork
        } catch (e: SocketTimeoutException) {
            UpdateCheckResult.Failed.DownloadInterrupted(e)
        } catch (e: SerializationException) {
            UpdateCheckResult.Failed.ParseError(e)
        } catch (e: java.io.IOException) {
            UpdateCheckResult.Failed.DownloadInterrupted(e)
        }
    }

    /**
     * Coroutine entry point for both manual button taps and the silent
     * startup check. Translates [UpdateCheckResult] to [UpdateState] and
     * writes to [state]. Debounces against double-taps by returning
     * early if state is already [UpdateState.Checking].
     */
    fun checkForUpdatesAsync(jsonUrl: String, currentVersionCode: Int) {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        GlobalScope.launch(Dispatchers.IO) {
            val r = checkForUpdates(jsonUrl, currentVersionCode)
            _state.value = when (r) {
                is UpdateCheckResult.UpToDate -> UpdateState.UpToDate(r.current)
                is UpdateCheckResult.UpdateAvailable -> UpdateState.UpdateAvailable(r.info)
                is UpdateCheckResult.Failed -> UpdateState.Failed(r)
            }
        }
    }

    // requestInstall lands in Task 6.

    /**
     * Pure file-IO download path (no Context). Production callers should use
     * [downloadApk]; tests can drive this variant directly with a
     * `Files.createTempDirectory`.
     *
     * The caller owns the dispatcher — this is a plain suspend block, so it
     * must be invoked from an IO-capable context ([downloadApk] uses
     * `Dispatchers.IO`). Network read timeout is 5 minutes, tuned for a
     * ~20 MB APK over a slow LAN link to the Gitea release host.
     *
     * [onProgress] receives the cumulative bytes written to disk after each
     * chunk; the caller publishes it to [state].
     */
    suspend fun downloadApkTo(
        info: AppVersionInfo,
        updateDir: File,
        onProgress: (Long) -> Unit = {},
    ): File {
        updateDir.mkdirs()
        val outFile = File(updateDir, "icespiritai-vision-update.apk")
        val conn = openConnection(info.apkUrl).apply {
            connectTimeout = 15_000
            readTimeout = 300_000
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("http_${conn.responseCode}")
            }
            var written = 0L
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        written += n
                        onProgress(written)
                    }
                }
            }
            return outFile
        } finally {
            conn.disconnect()
        }
    }

    /** Coroutine entry: writes to `cacheDir/update/`, publishes progress to [state]. */
    fun downloadApk(info: AppVersionInfo, appContext: android.content.Context) {
        val updateDir = File(appContext.cacheDir, "update")
        _state.value = UpdateState.Downloading(0L, info.apkSize)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val file = downloadApkTo(info, updateDir) { written ->
                    _state.value = UpdateState.Downloading(written, info.apkSize)
                }
                _state.value = UpdateState.ReadyToInstall(file)
            } catch (e: Exception) {
                Log.w(TAG, "downloadApk failed: ${e.javaClass.simpleName}")
                _state.value = UpdateState.Failed(UpdateCheckResult.Failed.DownloadInterrupted(e))
            }
        }
    }

    fun requestInstall(@Suppress("UNUSED_PARAMETER") context: android.content.Context, @Suppress("UNUSED_PARAMETER") file: File) {
        error("requestInstall implemented in Task 6")
    }
}