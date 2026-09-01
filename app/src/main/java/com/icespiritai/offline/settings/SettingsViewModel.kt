package com.icespiritai.offline.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.updater.AppVersionInfo
import com.icespiritai.offline.updater.UpdateCheckResult
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(private val source: ThemeSettingsSource) : ViewModel() {

    /**
     * Last [AppVersionInfo] passed to [download]. Held so [cancel] can
     * recompute the same `downloadId` that [UpdateRepository.downloadApk]
     * minted for the FGS intent (see [sha256Short]). Cleared on VM destroy
     * (default ViewModel scope) — sufficient for the user-cancellation
     * flow, which always follows a same-VM [download] call.
     *
     * Not a [StateFlow]: no observer needs this externally — it is purely
     * a write-once-then-read-once hand-off between [download] and [cancel].
     */
    private var lastDownloadInfo: AppVersionInfo? = null

    val themeMode: StateFlow<ThemeMode> = source.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Matches ThemeMode.fromName(null) so a brand-new install's first
        // composition doesn't briefly flip through a different value before
        // DataStore's first read lands. Factory default is SYSTEM (follow
        // the OS); the user can pin to DARK/LIGHT from settings.
        initialValue = ThemeMode.SYSTEM,
    )

    /**
     * Update flow read-through; ViewModel does not own the StateFlow
     * (singleton lives in [UpdateRepository]). Anything observing
     * `updateState` is observing the same process-global [UpdateRepository.state].
     */
    val updateState: StateFlow<UpdateState> = UpdateRepository.state

    /**
     * P0-C005: emits a one-shot signal when a download has stalled for
     * [STALL_THRESHOLD_MS] without any byte progress being reported. The
     * UI ([com.icespiritai.offline.ui.settings.UpdateSection]) observes
     * this and surfaces a Toast asking the user to whitelist the app in
     * the system background-killer (MIUI 神隐 / ColorOS 深度冻结 /
     * HarmonyOS PowerGenie all have separate user-facing toggles).
     *
     * Replay = 0 so a VM created AFTER the stall already fired does not
     * re-emit the past event to a freshly-recomposed settings screen.
     * extraBufferCapacity = 1 means a stall event firing while the UI
     * isn't collecting (process foreground/background race) is held
     * briefly, not dropped on the floor.
     */
    private val _downloadStallEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val downloadStallEvents: SharedFlow<Unit> = _downloadStallEvents.asSharedFlow()

    /**
     * P0-C005: stall detector. Foreground-service status alone is not
     * enough on aggressive Chinese ROMs — MIUI 13+ 神隐模式 / HyperOS
     * keeps the FGS notification on-screen but silently freezes the IO
     * coroutine, leaving a 70 MB APK download stranded at ~60% in
     * practice. Watch [updateState] for [UpdateState.Downloading];
     * whenever `downloadedBytes` stops moving for at least
     * [STALL_THRESHOLD_MS], we fire [downloadStallEvents] once per
     * download (a fresh progress tick resets the window).
     *
     * Polled every [STALL_POLL_INTERVAL_MS] on the main dispatcher —
     * cheap (single StateFlow read + integer compare), no IO, no
     * threading concerns.
     */
    private val stallDetectorJob = viewModelScope.launch {
        var lastWritten = -1L
        var stallStartedAt = 0L
        var stallSignaled = false
        while (true) {
            val current = updateState.value
            if (current is UpdateState.Downloading) {
                if (current.downloadedBytes != lastWritten) {
                    lastWritten = current.downloadedBytes
                    stallStartedAt = System.currentTimeMillis()
                    stallSignaled = false
                } else if (!stallSignaled &&
                    System.currentTimeMillis() - stallStartedAt >= STALL_THRESHOLD_MS
                ) {
                    stallSignaled = true
                    _downloadStallEvents.tryEmit(Unit)
                }
            } else {
                // Outside Downloading → reset the sliding window so the
                // next download's stall detection starts fresh.
                lastWritten = -1L
                stallStartedAt = 0L
                stallSignaled = false
            }
            delay(STALL_POLL_INTERVAL_MS)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        // Persist to DataStore first, then push the new night mode to AppCompat.
        // Order matters: if we flipped night mode before the write landed, an
        // Activity recreate could read the previous value from DataStore and
        // snap the theme back. Both calls run on the main dispatcher because
        // viewModelScope defaults to Dispatchers.Main.immediate.
        viewModelScope.launch {
            source.setThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
        }
    }

    /** Manual "Check for updates" tap (also invoked from [retry]). */
    fun refresh() {
        UpdateRepository.checkForUpdatesAsync(
            BuildConfig.UPDATE_JSON_URL,
            BuildConfig.VERSION_CODE,
            scope = viewModelScope,
        )
    }

    /**
     * Coroutine entry into the download path. [context] is the Activity
     * (caller-supplied via [androidx.compose.ui.platform.LocalContext]); the
     * `applicationContext` is what reaches [UpdateRepository.downloadApk] so
     * the cacheDir outlives any rotation-driven Activity recreation.
     *
     * The actual byte-stream download + cert-pin gate live in the FGS
     * (`UpdateDownloadService`); this call only fires the Intent.
     *
     * Caches [info] in [lastDownloadInfo] so a subsequent [cancel] can recompute
     * the same downloadId that [UpdateRepository.downloadApk] minted for the
     * service Intent. The FGS pushes an initial `UpdateState.Downloading`
     * transition carrying `downloadId` via `UpdateRepository.onDownloadProgress`
     * before the first body byte lands, so the VM-side cache is now a
     * belt-and-braces second source for [cancel] (the live StateFlow is the
     * primary source — covers the cold-resume path where a freshly-created
     * `SettingsViewModel` has a null `lastDownloadInfo`).
     */
    fun download(info: AppVersionInfo, context: Context) {
        lastDownloadInfo = info
        UpdateRepository.downloadApk(context.applicationContext, info)
    }

    /**
     * User-initiated cancellation of the in-flight download. Resolves the
     * `downloadId` from two possible sources, in priority order:
     *
     *  1. [lastDownloadInfo] — set by a same-VM [download] call. Always
     *     authoritative when present (covers the in-VM happy path).
     *  2. [UpdateState.Downloading.downloadId] — extracted from the live
     *     [updateState] StateFlow. Covers the cold-resume path, where the
     *     `UpdateResumeWorker` woke the app mid-download with a fresh
     *     SettingsViewModel whose `lastDownloadInfo` is null.
     *
     * Falls back to a no-op if neither source has a downloadId — guards
     * against a stray cancel tap before any download has been kicked off.
     */
    fun cancel(context: Context) {
        val infoId = lastDownloadInfo?.let { sha256Short(it.apkUrl + ":" + it.versionCode) }
        val stateId = (updateState.value as? UpdateState.Downloading)?.downloadId
        val downloadId = infoId ?: stateId ?: return
        UpdateRepository.cancel(context.applicationContext, downloadId)
    }

    private fun sha256Short(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Hand the APK file off to the system installer. If the user hasn't yet
     * granted "Install unknown apps" to this package, [ActivityNotFoundException]
     * is the documented signal — fall back to the system settings page so they
     * can flip the toggle and re-tap "Install".
     */
    fun install(file: File, context: Context) {
        try {
            UpdateRepository.requestInstall(context, file)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
    }

    /**
     * Smart retry by Failed subtype (spec §5.6):
     *  - [UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable] /
     *    [UpdateCheckResult.Failed.DownloadInterrupted.Other]: hand off to
     *    [UpdateRepository.retry], which resumes the FGS — the Service
     *    re-fetches the partial via Range / If-Range.
     *  - [UpdateCheckResult.Failed.SignatureMismatch]: also [UpdateRepository.retry],
     *    which kicks a fresh download (Service deletes the existing file on
     *    mismatch and starts over).
     *  - [UpdateCheckResult.Failed.NoNetwork] /
     *    [UpdateCheckResult.Failed.ServerError] /
     *    [UpdateCheckResult.Failed.ParseError]: re-run the metadata check via
     *    [refresh] — there's no partial APK to resume from.
     *  - [UpdateCheckResult.Failed.DownloadInterrupted.Cancelled]: [UpdateRepository.retry]
     *    restores [UpdateState.UpdateAvailable] so the user can re-tap "Download".
     *
     * [context] is the caller Activity (used only as a delivery vehicle for
     * [UpdateRepository.downloadApk] / `resumeService` which take
     * `applicationContext` internally). [jsonUrl] is forwarded so the
     * Repository has no prod-host hard-code.
     */
    fun retry(context: Context, jsonUrl: String) {
        when (val current = updateState.value) {
            is UpdateState.Failed -> {
                when (current.result) {
                    is UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable,
                    is UpdateCheckResult.Failed.DownloadInterrupted.Other,
                    is UpdateCheckResult.Failed.SignatureMismatch,
                    is UpdateCheckResult.Failed.DownloadInterrupted.Cancelled -> {
                        UpdateRepository.retry(
                            context = context.applicationContext,
                            info = lastDownloadInfo,
                            currentVersionCode = BuildConfig.VERSION_CODE,
                            jsonUrl = jsonUrl,
                        )
                    }
                    is UpdateCheckResult.Failed.NoNetwork,
                    is UpdateCheckResult.Failed.ServerError,
                    is UpdateCheckResult.Failed.ParseError -> {
                        // Pure metadata failures — no partial APK to resume from.
                        refresh()
                    }
                }
            }
            else -> refresh()
        }
    }

    companion object {
        /**
         * P0-C005: stall threshold. UpdateDownloadService pushes a fresh
         * progress tick every 500 ms (see [runDownload] / `lastNotifUpdate`
         * gate), so 5 minutes is ~600 ticks — generous enough to ride
         * out a slow network segment on the far end of a captive portal,
         * short enough that the user is still in front of the device when
         * the Toast fires.
         */
        private const val STALL_THRESHOLD_MS = 5L * 60L * 1000L

        /**
         * P0-C005: stall detector polling interval. 30 s keeps the worst-
         * case stall latency at STALL_THRESHOLD_MS + 30 s and avoids a
         * tight main-thread loop.
         */
        private const val STALL_POLL_INTERVAL_MS = 30_000L

        fun factory(repository: SettingsRepository) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository) as T
            }
        }
    }
}
