package com.icespiritai.offline.tts

/** 下载/安装状态机(spec §8.3)。 */
sealed interface InstallState {
    object Idle : InstallState
    object QueryingRelease : InstallState
    object CheckingCache : InstallState
    data class Downloading(val bytesDownloaded: Long, val bytesTotal: Long) : InstallState
    object VerifyingSha256 : InstallState
    object Installing : InstallState
    data class Failed(val reason: String) : InstallState
    object Done : InstallState
}