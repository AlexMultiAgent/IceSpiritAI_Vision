package com.icespiritai.offline.updater

import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
     * Default scope for callers that haven't migrated to passing a
     * caller-owned scope. Lives for the process lifetime; survives
     * Activity recreation. SupervisorJob so a failure in one launched
     * coroutine doesn't tear down the rest.
     */
    private val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Process-global state. Observed by `SettingsViewModel` via
     * `viewModel.state`; mutated by `checkForUpdates` / `downloadApk` /
     * `requestInstall`. Singleton survives Activity / ViewModel lifetime.
     *
     * **Multi-window / multi-Activity risk**: this object is a true process
     * singleton. If we ever introduce multi-window or split-screen UI, two
     * Settings screens would share the same [UpdateState] — a download
     * kicked off by one Activity would visibly progress on the other, and
     * a cancellation on one would race the other's state machine. For now
     * the app is single-Activity ([IceSpiritVisionActivity]) so this is
     * acceptable; a future refactor should scope state per ViewModelStore.
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
     *
     * Caller-owned [scope]: pass `viewModelScope` (or `lifecycleScope`) so
     * the in-flight network call cancels with the host. Defaults to a
     * private IO scope for backward-compat callers that haven't migrated
     * yet (e.g. background workers).
     */
    fun checkForUpdatesAsync(
        jsonUrl: String,
        currentVersionCode: Int,
        scope: CoroutineScope = defaultScope,
    ) {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            val r = checkForUpdates(jsonUrl, currentVersionCode)
            _state.value = when (r) {
                is UpdateCheckResult.UpToDate -> UpdateState.UpToDate(r.current)
                is UpdateCheckResult.UpdateAvailable -> UpdateState.UpdateAvailable(r.info)
                is UpdateCheckResult.Failed -> UpdateState.Failed(r)
            }
        }
    }

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
        val outFile = File(updateDir, "icespiritai-vision.apk")
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
    fun downloadApk(
        info: AppVersionInfo,
        appContext: android.content.Context,
        scope: CoroutineScope = defaultScope,
    ) {
        val updateDir = File(appContext.cacheDir, "update")
        _state.value = UpdateState.Downloading(0L, info.apkSize)
        scope.launch(Dispatchers.IO) {
            try {
                val file = downloadApkTo(info, updateDir) { written ->
                    _state.value = UpdateState.Downloading(written, info.apkSize)
                }
                // Double-gate cert check (after APK bytes on disk; before announcing install-ready).
                // Backward compat: empty `signerCertSha256` (older vision-latest.json) skips gate.
                verifySignatureForDownload(info, file)?.let { mismatch ->
                    Log.w(TAG, "signature mismatch: expected=${mismatch.expected.take(16)}… actual=${mismatch.actual?.take(16) ?: "null"}")
                    file.delete()
                    _state.value = UpdateState.Failed(mismatch)
                    return@launch
                }
                _state.value = UpdateState.ReadyToInstall(file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "downloadApk failed: ${e.javaClass.simpleName}")
                _state.value = UpdateState.Failed(UpdateCheckResult.Failed.DownloadInterrupted(e))
            }
        }
    }

    /**
     * Cert-pin gate: verifies the v1 signer certificate SHA-256 of [file] against
     * `info.signerCertSha256`.
     *
     * Returns `null` when the gate is skipped (`info.signerCertSha256` is empty,
     * for backward compat with `vision-latest.json` published before Wave 1
     * Task 1.2), or when the actual fingerprint matches `expected`
     * (case-insensitive) and the download may proceed to [UpdateState.ReadyToInstall].
     *
     * Returns [UpdateCheckResult.Failed.SignatureMismatch] when `expected` is
     * non-empty and the actual fingerprint is `null` (file unparsable as a JAR)
     * or differs from `expected`.
     *
     * Extracted as `@VisibleForTesting internal` so unit tests can drive the
     * gate directly with a synthesized File without spinning the coroutine
     * wrapper, Context, or cacheDir plumbing.
     */
    @VisibleForTesting
    internal fun verifySignatureForDownload(
        info: AppVersionInfo,
        file: File,
    ): UpdateCheckResult.Failed.SignatureMismatch? {
        val expected = info.signerCertSha256
        if (expected.isEmpty()) return null
        val actual = ApkSignatureVerifier.readFirstSignerCert(file)
        return if (actual == null || !actual.equals(expected, ignoreCase = true)) {
            UpdateCheckResult.Failed.SignatureMismatch(actual = actual, expected = expected)
        } else {
            null
        }
    }

    /**
     * Build an `ACTION_VIEW` intent for the given APK file, mediated by
     * FileProvider. The caller is responsible for `startActivity(intent)` —
     * keeping that call out of the Repository makes it Robolectric-testable.
     */
    @VisibleForTesting
    internal fun buildInstallIntent(context: android.content.Context, file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Convenience: build + startActivity. */
    fun requestInstall(context: android.content.Context, file: File) {
        context.startActivity(buildInstallIntent(context, file))
    }
}