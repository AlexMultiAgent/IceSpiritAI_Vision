package com.icespiritai.offline.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.updater.AppVersionInfo
import com.icespiritai.offline.updater.UpdateCheckResult
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * One-shot user-facing messages emitted by [SettingsViewModel]. Surfaced
 * to the UI via [SettingsViewModel.snackbar] for hosting in a
 * `SnackbarHostState` (no `Toast` / context-coupled plumbing at this
 * layer — keeps the VM pure Kotlin and JVM-test-friendly).
 */
sealed class SettingsSnackbar {
    /**
     * The user tried to hide the last visible [RuleTab], which would
     * leave the tab bar empty. UI maps this to a "至少保留一个"
     * snackbar and leaves the persisted state unchanged.
     */
    object LastFeatureCannotHide : SettingsSnackbar()

    /**
     * The upstream write to [ThemeSettingsSource] failed. [cause]
     * carries the original throwable so the UI can decide whether to
     * offer retry, log, or surface a generic "保存失败" message.
     */
    data class PersistFailed(val cause: Throwable) : SettingsSnackbar()
}

class SettingsViewModel(
    private val source: ThemeSettingsSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

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
     * Currently visible [RuleTab] set, projected from [ThemeSettingsSource.visibleFeatures]
     * via `stateIn(Eagerly)`. Initial value is [RuleTab.entries] (all tabs
     * visible) so a brand-new install's first composition matches the
     * production "show everything" baseline even before DataStore's first
     * read lands — the source's own fallback (`SettingsRepository` falls
     * back to all-tabs when the key is missing) will agree once it emits.
     *
     * Consumers: [com.icespiritai.offline.IceSpiritVisionViewModel] filters
     * `matcherFor(tab)` / `setTab(tab)` against this set so a disabled
     * tab is never rendered or selected; [com.icespiritai.offline.ui.home.RuleTabBar]
     * reads it to decide how many pills to render.
     */
    val visibleFeatures: StateFlow<Set<RuleTab>> = source.visibleFeatures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RuleTab.entries.toSet(),
    )

    /**
     * v0.4.0: smart-glasses capture opt-in flag. Defaults to `false` —
     * the BLE pipeline is disabled until the user explicitly turns it
     * on from Settings (matches the user's "默认不连接" requirement).
     *
     * Read by:
     *  - [com.icespiritai.offline.ui.home.CaptureBar] to hide its
     *    "眼镜拍照" button when `false`
     *  - [com.icespiritai.offline.ui.home.HomeScreen.launchGlassesCapture]
     *    as a defensive guard against the (defensive) button visibility
     *    check being bypassed (e.g. via deep-link or deeplink jump)
     *  - [com.icespiritai.offline.glasses.ui.GlassesCaptureOverlay] to
     *    surface a "未启用" state if the overlay is somehow shown with
     *    the flag off
     */
    val enableGlassesCapture: StateFlow<Boolean> = source.enableGlassesCapture.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    /**
     * Persist the user's opt-in / opt-out. Mirrors [setFeatureVisible]
     * in spirit: a no-op coroutine on [viewModelScope], DataStore IO
     * off the main thread, surface IO failures via [SettingsSnackbar].
     *
     * Unlike [setFeatureVisible], there's no "at least one must stay
     * visible" invariant — the flag is a single Boolean.
     */
    fun setGlassesCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { source.setGlassesCaptureEnabled(enabled) }
                .onFailure { cause ->
                    if (cause !is IOException) {
                        Log.w(TAG, "setGlassesCaptureEnabled failed with non-IO throwable", cause)
                    }
                    _snackbar.tryEmit(SettingsSnackbar.PersistFailed(cause))
                }
        }
    }

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
     * One-shot [SettingsSnackbar] signals surfaced to the UI for hosting
     * in a `SnackbarHostState`. Mirrors [downloadStallEvents] in spirit
     * (replay = 0 — past emissions are not interesting to a freshly
     * composed screen) but uses `extraBufferCapacity = 4` to absorb a
     * rapid back-to-back rejection (`setFeatureVisible` rejecting the
     * last tab while a DataStore flush is still settling).
     *
     * On overflow the oldest pending event is dropped to make room for
     * the new one (`BufferOverflow.DROP_OLDEST`): for a non-fatal UX
     * hint, fresh events preempt stale ones rather than getting silently
     * dropped on arrival. `extraBufferCapacity = 4` means 4 events fit;
     * the 5th `tryEmit` returns `false` only when the buffer is already
     * full AND no collector has drained it yet.
     */
    private val _snackbar = MutableSharedFlow<SettingsSnackbar>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val snackbar: SharedFlow<SettingsSnackbar> = _snackbar.asSharedFlow()

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
     * threading concerns. While no download is in flight the coroutine
     * parks on [kotlinx.coroutines.flow.first] instead of re-arming that
     * timer, which both avoids a wakeup every 30 s for the life of the
     * ViewModel and is what keeps `runTest`'s `advanceUntilIdle` from
     * spinning forever here (see the KDoc on [stallDetectorJob]).
     */
    private val stallDetectorJob = viewModelScope.launch {
        var lastWritten = -1L
        var stallStartedAt = 0L
        var stallSignaled = false
        while (true) {
            val current = updateState.value as? UpdateState.Downloading
            if (current == null) {
                // Nothing to watch. Suspend until a download actually starts
                // rather than keeping a repeating delay armed: an unbounded
                // `delay` loop is unsatisfiable for a coroutine-test scheduler
                // (`advanceUntilIdle` re-runs it forever, which is how every
                // SettingsViewModelTest hung from v0.1.45 on), and a StateFlow
                // await costs nothing while idle.
                updateState.first { it is UpdateState.Downloading }
                lastWritten = -1L
                stallStartedAt = 0L
                stallSignaled = false
                continue
            }
            if (current.downloadedBytes != lastWritten) {
                lastWritten = current.downloadedBytes
                stallStartedAt = clock()
                stallSignaled = false
            } else if (!stallSignaled &&
                clock() - stallStartedAt >= STALL_THRESHOLD_MS
            ) {
                stallSignaled = true
                _downloadStallEvents.tryEmit(Unit)
                // Layer 1: write Failed.NetworkUnreachable directly so the
                // UI shows the 「重试」 button. Guarded inside the Repository
                // method — if state has moved on (FGS finished, or another
                // transition fired) the call is a no-op.
                (updateState.value as? UpdateState.Downloading)?.let {
                    UpdateRepository.tryMarkStalledAsFailed(it.downloadId)
                }
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

    /**
     * Toggle [tab]'s membership in the persisted visible-feature set.
     *
     * Enforces the "at least one tab must stay visible" invariant: if
     * the user requests to hide the last remaining [RuleTab], the write
     * is refused and [SettingsSnackbar.LastFeatureCannotHide] is emitted
     * on [snackbar] so the UI can show a "至少保留一个" hint and leave the
     * persisted state at its last-allowed value.
     *
     * Persistence failures (DataStore IOException, etc.) are surfaced
     * as [SettingsSnackbar.PersistFailed] carrying the original throwable;
     * the in-memory [visibleFeatures] StateFlow is NOT silently advanced
     * to the would-be new value, because the write never landed —
     * [ThemeSettingsSource.visibleFeatures] only emits when its underlying
     * store does, and the source's [stateIn] projection reflects that.
     *
     * Runs on [viewModelScope] (Main dispatcher) — `source.setVisibleFeatures`
     * is `suspend` and DataStore writes must not block the UI thread, so
     * the launch keeps the call site non-blocking. `tryEmit` (rather than
     * `emit`) means a buffer-full rejection is silently dropped; the
     * snackbar is best-effort UX hint, not a correctness signal.
     */
    fun setFeatureVisible(tab: RuleTab, visible: Boolean) {
        viewModelScope.launch {
            val current = visibleFeatures.value
            if (!visible && current.size <= 1) {
                _snackbar.tryEmit(SettingsSnackbar.LastFeatureCannotHide)
                return@launch
            }
            val next = if (visible) current + tab else current - tab
            runCatching { source.setVisibleFeatures(next) }
                .onFailure { cause ->
                    // IO failures are the documented "保存失败" path; anything
                    // else (ClassCastException, IllegalStateException from a
                    // bug in our own code, a CancellationException we forgot
                    // to re-throw, ...) should still surface in logcat so a
                    // real programming bug isn't silently masked as a UX hint.
                    if (cause !is IOException) {
                        Log.w(TAG, "setVisibleFeatures failed with non-IO throwable", cause)
                    }
                    _snackbar.tryEmit(SettingsSnackbar.PersistFailed(cause))
                }
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
     * Pre-fix this method also wrote [info] to `lastDownloadInfo` (a VM-
     * private cache) so [cancel] / [retry] could recover the same
     * downloadId after VM recreation. That cache has been removed —
     * `UpdateRepository` now owns the process-global `_lastDownloadInfo`
     * (set internally by `downloadApk` / `resumeService`), and
     * `UpdateRepository.retry` reads it directly. [cancel] uses the live
     * StateFlow's `UpdateState.Downloading.downloadId` instead. Net
     * surface-area reduction at the VM layer.
     */
    fun download(info: AppVersionInfo, context: Context) {
        UpdateRepository.downloadApk(context.applicationContext, info)
    }

    /**
     * User-initiated cancellation of the in-flight download. Resolves the
     * `downloadId` from [UpdateState.Downloading.downloadId] — extracted
     * from the live [updateState] StateFlow. Covers both the same-VM
     * happy path and the cold-resume path (where `UpdateResumeWorker`
     * woke the app mid-download with a freshly-created SettingsViewModel).
     *
     * Pre-fix this method also consulted `SettingsViewModel.lastDownloadInfo`
     * (a VM-private cache); that field was removed because it was only
     * authoritative within the same VM and silently fell through to the
     * StateFlow path on VM recreation anyway. Now StateFlow is the single
     * source of truth — it is always populated by `onDownloadProgress`
     * before the cancel button can render (which only happens when state
     * is `Downloading`).
     *
     * Falls back to a no-op if state is not `Downloading` — guards
     * against a stray cancel tap before any download has been kicked off.
     */
    fun cancel(context: Context) {
        val downloadId = (updateState.value as? UpdateState.Downloading)?.downloadId ?: return
        UpdateRepository.cancel(context.applicationContext, downloadId)
        // cgroup-frozen devices: FGS handleCancel IO coroutine may never
        // schedule. Write state directly so the UI updates immediately
        // instead of waiting for the (potentially dead) FGS to acknowledge.
        UpdateRepository.markCancelled(downloadId)
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
     *
     * Note: pre-fix this method passed `info = lastDownloadInfo` (a
     * VM-private cache) to [UpdateRepository.retry]. That cache was
     * lost on VM recreation (rotation, navigation away/back,
     * `UpdateResumeWorker` cold-start) and the Repository's download
     * branches silently no-op'd when `info` came back null. The cache
     * is now process-global inside `UpdateRepository` — see
     * `UpdateRepository._lastDownloadInfo` and `UpdateRepository.retry`
     * KDoc — so the VM no longer needs to track it.
     */
    fun retry(context: Context, jsonUrl: String) {
        Log.i(TAG, "retry tapped, state=${updateState.value::class.simpleName}")
        when (val current = updateState.value) {
            is UpdateState.Failed -> {
                when (current.result) {
                    is UpdateCheckResult.Failed.DownloadInterrupted.NetworkUnreachable,
                    is UpdateCheckResult.Failed.DownloadInterrupted.Other,
                    is UpdateCheckResult.Failed.SignatureMismatch,
                    is UpdateCheckResult.Failed.DownloadInterrupted.Cancelled -> {
                        UpdateRepository.retry(
                            context = context.applicationContext,
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

        private const val TAG = "SettingsViewModel"

        fun factory(repository: SettingsRepository) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository) as T
            }
        }
    }
}
