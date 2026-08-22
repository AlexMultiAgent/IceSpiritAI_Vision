package com.icespiritai.offline.updater

import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val currentVersionCode: Int) : UpdateState()
    data class UpdateAvailable(val info: AppVersionInfo) : UpdateState()
    data class Downloading(
        val downloadId: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Failed(val result: UpdateCheckResult.Failed) : UpdateState()
}