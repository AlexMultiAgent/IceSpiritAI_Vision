package com.icespiritai.offline.updater

import kotlinx.serialization.Serializable

@Serializable
data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String,
    val changelog: String = "",
    val apkCumulativeDownloads: Long = 0,
)

sealed class UpdateCheckResult {
    data class UpToDate(val current: Int) : UpdateCheckResult()
    data class UpdateAvailable(val info: AppVersionInfo) : UpdateCheckResult()
    sealed class Failed(val reasonTag: String) : UpdateCheckResult() {
        object NoNetwork : Failed("no_network")
        data class ServerError(val httpCode: Int) : Failed("server_$httpCode")
        data class ParseError(val cause: Throwable) : Failed("parse")
        data class DownloadInterrupted(val cause: Throwable) : Failed("interrupted")
    }
}