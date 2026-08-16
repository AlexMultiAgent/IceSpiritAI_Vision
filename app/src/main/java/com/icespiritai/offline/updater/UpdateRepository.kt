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

    // downloadApk / requestInstall land in Tasks 5 and 6.
    fun downloadApk(@Suppress("UNUSED_PARAMETER") info: AppVersionInfo) {
        error("downloadApk implemented in Task 5")
    }

    fun requestInstall(@Suppress("UNUSED_PARAMETER") context: android.content.Context, @Suppress("UNUSED_PARAMETER") file: File) {
        error("requestInstall implemented in Task 6")
    }
}