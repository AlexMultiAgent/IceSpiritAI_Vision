package com.icespiritai.offline.glasses

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Firmware upgrade for the glasses: check the vendor's OTA API, hand the
 * download URL to the glasses over BLE, then watch until they come back on
 * the new version.
 *
 * **What the App does and does not do.** The App never carries firmware
 * bytes: `GlassesFirmwareProtocol` sends `{"u":"<downloadUrl>"}` in `0x43`
 * frames and the glasses fetch the `.rbl` themselves (that is why the
 * official app requires 蓝牙共享网络 / BT PAN first — see
 * `docs/knowledge/official-glasses-ota-protocol.md`). Everything the App can
 * verify afterwards is the firmware version, read back over `0x10|0x20`.
 *
 * The state machine is deliberately coarse — the glasses own the download
 * and flash progress, and the only trustworthy signal we have is
 * "the version changed".
 */
class GlassesFirmwareUpdater(
    private val photoRepository: GlassesPhotoCaptureRepository,
    private val controller: BluetoothController,
    private val service: GlassesFirmwareService,
    private val scope: CoroutineScope,
) {

    /** Live precondition problem shown next to the upgrade progress. */
    enum class TetheringHint { NONE, TETHERING_OFF, NO_PROGRESS }

    sealed class UpgradeState {
        data object Idle : UpgradeState()
        data object Checking : UpgradeState()
        data class UpToDate(val currentVersion: String?) : UpgradeState()
        data class Available(val info: FirmwareUpdateInfo) : UpgradeState()
        data class Sending(
            val info: FirmwareUpdateInfo,
            val framesSent: Int,
            val framesTotal: Int,
        ) : UpgradeState()
        data class Upgrading(
            val info: FirmwareUpdateInfo,
            val elapsedMs: Long,
            val lastSeenVersion: String?,
            /** Non-NONE means the download cannot be progressing. */
            val hint: TetheringHint = TetheringHint.NONE,
        ) : UpgradeState()
        data class Success(val previousVersion: String?, val newVersion: String) : UpgradeState()
        data class Failed(val reason: String) : UpgradeState()
    }

    private val _state = MutableStateFlow<UpgradeState>(UpgradeState.Idle)
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    private var watchJob: Job? = null
    private var seq: Byte = 0x21

    /** Forget the last result (new check / dialog reopened). */
    fun reset() {
        watchJob?.cancel()
        watchJob = null
        _state.value = UpgradeState.Idle
    }

    /**
     * Publish a failure without running a check — for pre-flight problems
     * (no paired glasses, missing permission) that never reach the network.
     */
    fun reportPreflightFailure(reason: String) {
        _state.value = UpgradeState.Failed(reason)
    }

    /**
     * Ask the vendor whether [device] is behind, and publish the answer as
     * [UpgradeState]. Never throws: failures land in [UpgradeState.Failed]
     * with a message the settings dialog can show verbatim.
     */
    suspend fun checkForUpdate(device: GlassesDevice): UpgradeState {
        _state.value = UpgradeState.Checking
        val current = runCatching { photoRepository.readFirmwareVersion(device) }.getOrNull()
        val result = try {
            service.checkUpdate(macAddress = device.address, currentVersion = current)
        } catch (e: Throwable) {
            FirmwareCheckResult.Failed(e.message ?: "未知错误")
        }
        val state = when (result) {
            is FirmwareCheckResult.Available -> UpgradeState.Available(result.info)
            is FirmwareCheckResult.UpToDate -> UpgradeState.UpToDate(result.currentVersion ?: current)
            is FirmwareCheckResult.Failed -> UpgradeState.Failed(result.reason)
        }
        _state.value = state
        return state
    }

    /**
     * Hand [info]'s download URL to the glasses and then watch their version
     * until it changes. Runs on [scope]; progress is published on [state].
     */
    fun startUpgrade(device: GlassesDevice, info: FirmwareUpdateInfo) {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            val previous = runCatching { photoRepository.readFirmwareVersion(device) }.getOrNull()
            photoRepository.ensureConnected(device)
            if (photoRepository.state.value !is
                GlassesPhotoCaptureRepository.GlassesCaptureState.Ready
            ) {
                _state.value = UpgradeState.Failed("眼镜未连接,无法下发升级指令")
                return@launch
            }


            val payload = GlassesFirmwareProtocol.buildUpgradePayload(info.downloadUrl)
            val frames = runCatching {
                GlassesFirmwareProtocol.buildUpgradeFrames(
                    seq = seq++,
                    payload = payload,
                    chunkSize = GlassesFirmwareProtocol.chunkSizeForMtu(controller.mtu.value),
                )
            }.getOrElse {
                _state.value = UpgradeState.Failed("升级指令构造失败:${it.message}")
                return@launch
            }
            _state.value = UpgradeState.Sending(info, framesSent = 0, framesTotal = frames.size)

            val sent = try {
                controller.writeFirmwareOtaFrames(frames).also {
                    if (it) _state.value =
                        UpgradeState.Sending(info, frames.size, frames.size)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "OTA frame write failed", e)
                false
            }
            if (!sent) {
                _state.value = UpgradeState.Failed("眼镜未接受升级指令,请确认连接后重试")
                return@launch
            }
            Log.i(TAG, "OTA url handed to glasses (${frames.size} frame(s), ${payload.size} B): ${info.downloadUrl}")

            awaitGlassesUpgrade(device, info, previous)
        }
    }

    /** The glasses download and flash on their own; watch the version. */
    private suspend fun awaitGlassesUpgrade(
        device: GlassesDevice,
        info: FirmwareUpdateInfo,
        previous: String?,
    ) {
        val startedAt = System.currentTimeMillis()
        var lastSeen = previous
        // The glasses are the only ones who know whether their PAN link to the
        // phone works: they report it as a `0x15` TLV in their `0x11` status
        // notify, and the firmware pushes one right after the OTA hand-over
        // (real frame 2026-09-17: `… 11 03 0300 15 01 00` while the phone's
        // 蓝牙共享网络 was off). There is no public API for a third-party app
        // to read the phone's own tethering switch — `BluetoothPan` is a
        // hidden class — so this is also the only honest signal available.
        val statusTap = scope.tapSharedFlow(controller.fff0Notifications)
        statusTap.drainBuffered()
        var sharingReported: Boolean? = null
        var lastHint = TetheringHint.NONE
        _state.value = UpgradeState.Upgrading(info, 0L, previous, lastHint)
        try {
            while (System.currentTimeMillis() - startedAt < UPGRADE_WATCH_TIMEOUT_MS) {
                delay(POLL_INTERVAL_MS)
                val seen = runCatching { photoRepository.readFirmwareVersion(device) }.getOrNull()
                if (seen != null) {
                    lastSeen = seen
                    if (!sameVersion(seen, previous)) {
                        Log.i(TAG, "firmware upgraded: $previous -> $seen")
                        _state.value = UpgradeState.Success(previous, seen)
                        return
                    }
                }
                statusTap.drainBuffered().forEach { frame ->
                    GlassesPhotoProtocol.reportsNetworkSharingOn(frame)?.let { sharing ->
                        if (sharing != sharingReported) {
                            Log.i(TAG, "glasses report BT network sharing = $sharing")
                        }
                        sharingReported = sharing
                    }
                }
                val elapsed = System.currentTimeMillis() - startedAt
                val hint = firmwareTetheringHint(sharingReported, elapsed)
                if (hint != lastHint) {
                    Log.w(TAG, "tethering hint: $lastHint -> $hint (elapsed=${elapsed}ms, sharing=$sharingReported)")
                    lastHint = hint
                }
                _state.value = UpgradeState.Upgrading(
                    info = info,
                    elapsedMs = elapsed,
                    lastSeenVersion = lastSeen,
                    hint = hint,
                )
            }
        } finally {
            statusTap.stop()
        }
        _state.value = UpgradeState.Failed(
            "升级超时:眼镜版本仍是 ${lastSeen ?: "未知"}。" +
                "请确认系统设置里已打开「蓝牙共享网络」,眼镜电量充足,然后重试",
        )
    }

    /** Does the glasses' reported version differ from [reference]? */
    private fun sameVersion(a: String?, b: String?): Boolean {
        fun normalize(v: String?) = v?.trim()?.removePrefix("V")?.removePrefix("v")?.lowercase()
        if (a == null || b == null) return true // unknown → keep waiting, never "success" by accident
        return normalize(a) == normalize(b)
    }

    private companion object {
        const val TAG = "GlassesFirmware"

        /** Poll cadence while the glasses download + flash. */
        const val POLL_INTERVAL_MS = 10_000L

        /**
         * How long to wait for the version to change. 2.6 MB over BT-PAN
         * plus a flash cycle is minutes, and a user who leaves the phone
         * alone must not come back to a false failure — but neither should
         * the dialog spin forever.
         */
        const val UPGRADE_WATCH_TIMEOUT_MS = 20 * 60 * 1000L
    }
}

/**
 * Turn what the App knows into the upgrade dialog's precondition hint.
 *
 * Two levels, both evidence-based:
 *  - the glasses *said* there is no sharing (`0x15` TLV = 0) and a minute has
 *    passed without the version moving → they cannot download;
 *  - nothing was ever reported and three minutes passed → "something is
 *    wrong", phrased so it covers the other causes (battery, link) too.
 *
 * Deliberately asymmetric: silence and a mis-read polarity can only ever
 * produce the vaguer warning, never a false definite one.
 *
 * File-level so the rule is unit-testable without Android or a fake updater.
 */
internal fun firmwareTetheringHint(
    sharingReported: Boolean?,
    elapsedMs: Long,
): GlassesFirmwareUpdater.TetheringHint = when {
    sharingReported == false && elapsedMs >= TETHERING_OFF_GRACE_MS ->
        GlassesFirmwareUpdater.TetheringHint.TETHERING_OFF
    sharingReported == null && elapsedMs >= NO_PROGRESS_MS ->
        GlassesFirmwareUpdater.TetheringHint.NO_PROGRESS
    else -> GlassesFirmwareUpdater.TetheringHint.NONE
}

/** Grace period before the glasses' "no sharing" report becomes a warning. */
private const val TETHERING_OFF_GRACE_MS = 60_000L

/** When nothing is known about sharing, warn after this long without progress. */
private const val NO_PROGRESS_MS = 180_000L
