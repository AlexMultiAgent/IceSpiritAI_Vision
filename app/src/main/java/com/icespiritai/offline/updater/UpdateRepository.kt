package com.icespiritai.offline.updater

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.icespiritai.offline.updater.service.UpdateDownloadService
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
     * `requestInstall` / the Service's terminal callbacks. Singleton
     * survives Activity / ViewModel lifetime.
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
            UpdateCheckResult.Failed.DownloadInterrupted.Other(e)
        } catch (e: SerializationException) {
            UpdateCheckResult.Failed.ParseError(e)
        } catch (e: java.io.IOException) {
            UpdateCheckResult.Failed.DownloadInterrupted.Other(e)
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
     * Coroutine entry: starts the FGS via Intent. Download progress lives in
     * [com.icespiritai.offline.updater.service.UpdateDownloadService]. The state
     * transitions publish through [state] from the Service's callbacks
     * (onDownloadVerified / onDownloadFailed / onDownloadCancelled).
     *
     * The Service owns the actual byte-stream read, partial-file resume, and
     * cert-pin gate; this Repository entry is fire-and-forget.
     */
    fun downloadApk(
        context: Context,
        info: AppVersionInfo,
    ) {
        val downloadId = sha256Short(info.apkUrl + ":" + info.versionCode)
        val updateDir = File(context.cacheDir, "update").apply { mkdirs() }
        val destPath = File(updateDir, "$downloadId.apk").absolutePath
        val intent = Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
            setClass(context, UpdateDownloadService::class.java)
            putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(UpdateDownloadActions.EXTRA_URL, info.apkUrl)
            putExtra(UpdateDownloadActions.EXTRA_DEST_PATH, destPath)
            putExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256, info.signerCertSha256)
            putExtra(UpdateDownloadActions.EXTRA_VERSION_NAME, info.versionName)
            putExtra(UpdateDownloadActions.EXTRA_RESUME, false)
        }
        context.startForegroundService(intent)
    }

    private fun sha256Short(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Service callbacks — invoked by [UpdateDownloadService] at terminal points
     * of the download lifecycle. Each callback translates the Service-side event
     * into an [UpdateState] transition for the UI to observe.
     *
     * Kept as `fun` (not `suspend`) because they only mutate [_state] (a thread-safe
     * StateFlow value). The Service runs on Dispatchers.IO; these are safe to call
     * from any thread.
     */
    fun onDownloadVerified(record: DownloadRecord, result: VerifierResult, file: File) {
        _state.value = when (result) {
            is VerifierResult.Match -> UpdateState.ReadyToInstall(file)
            is VerifierResult.Mismatch -> UpdateState.Failed(
                UpdateCheckResult.Failed.SignatureMismatch(
                    expected = result.expected, actual = result.actual,
                )
            )
        }
    }

    fun onDownloadFailed(record: DownloadRecord, failed: UpdateCheckResult.Failed.DownloadInterrupted) {
        _state.value = UpdateState.Failed(failed)
    }

    fun onDownloadCancelled(record: DownloadRecord) {
        _state.value = UpdateState.Failed(UpdateCheckResult.Failed.DownloadInterrupted.Cancelled)
    }

    fun setReadyToInstall(file: File, versionName: String) {
        _state.value = UpdateState.ReadyToInstall(file)
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
     * gate directly with a synthesized File without spinning the Service,
     * Context, or cacheDir plumbing.
     */
    @VisibleForTesting
    internal fun verifySignatureForDownload(
        info: AppVersionInfo,
        file: File,
    ): UpdateCheckResult.Failed.SignatureMismatch? {
        val r = ApkSignatureVerifier.verify(file, info.signerCertSha256)
        return when (r) {
            is VerifierResult.Match -> null
            is VerifierResult.Mismatch -> UpdateCheckResult.Failed.SignatureMismatch(
                expected = r.expected, actual = r.actual,
            )
        }
    }

    /**
     * Retry by Failed subtype. Called from SettingsViewModel when user taps "retry".
     * - Cancelled → restore UpdateAvailable so user can re-tap "Download"
     * - NetworkUnreachable / Other → resumeService (Service will pick up partial)
     * - SignatureMismatch → fresh download (Service will re-download, file deleted on mismatch)
     * - else (NoNetwork / ServerError / ParseError) → re-run checkForUpdates
     */
    fun retry(context: Context, info: AppVersionInfo?, currentVersionCode: Int) {
        when (val cur = _state.value) {
            is UpdateState.Failed -> when (val r = cur.result) {
                is UpdateCheckResult.Failed.DownloadInterrupted.Cancelled -> {
                    if (info != null) _state.value = UpdateState.UpdateAvailable(info)
                }
                is UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable,
                is UpdateCheckResult.Failed.DownloadInterrupted.Other -> {
                    if (info != null) resumeService(context, info)
                }
                is UpdateCheckResult.Failed.SignatureMismatch -> {
                    if (info != null) downloadApk(context, info)
                }
                else -> checkForUpdatesAsync(currentVersionInfoUrl(), currentVersionCode)
            }
            else -> { /* no-op: only Failed is retryable */ }
        }
    }

    private fun currentVersionInfoUrl(): String {
        // The JSON URL is normally injected via SettingsViewModel; for retry we re-use
        // the same lookup pattern. Default to the prod host.
        return "https://icespiritai-vision.example/vision-latest.json"
    }

    private fun resumeService(context: Context, info: AppVersionInfo) {
        val downloadId = sha256Short(info.apkUrl + ":" + info.versionCode)
        val intent = Intent(UpdateDownloadActions.ACTION_DOWNLOAD).apply {
            setClass(context, UpdateDownloadService::class.java)
            putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(UpdateDownloadActions.EXTRA_URL, info.apkUrl)
            putExtra(UpdateDownloadActions.EXTRA_DEST_PATH,
                File(context.cacheDir, "update/$downloadId.apk").absolutePath)
            putExtra(UpdateDownloadActions.EXTRA_SIGNER_CERT_SHA256, info.signerCertSha256)
            putExtra(UpdateDownloadActions.EXTRA_VERSION_NAME, info.versionName)
            putExtra(UpdateDownloadActions.EXTRA_RESUME, true)
        }
        context.startForegroundService(intent)
    }

    /**
     * User-initiated cancellation. Sends ACTION_CANCEL to the running FGS.
     */
    fun cancel(context: Context, downloadId: String) {
        val intent = Intent(UpdateDownloadActions.ACTION_CANCEL).apply {
            setClass(context, UpdateDownloadService::class.java)
            putExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.startService(intent)
    }

    /**
     * Build an `ACTION_VIEW` intent for the given APK file, mediated by
     * FileProvider. The caller is responsible for `startActivity(intent)` —
     * keeping that call out of the Repository makes it Robolectric-testable.
     */
    @VisibleForTesting
    internal fun buildInstallIntent(context: Context, file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Convenience: build + startActivity. */
    fun requestInstall(context: Context, file: File) {
        context.startActivity(buildInstallIntent(context, file))
    }
}
