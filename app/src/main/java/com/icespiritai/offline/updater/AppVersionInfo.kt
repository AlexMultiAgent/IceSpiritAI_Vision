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
    val signerCertSha256: String = "",
)

sealed class UpdateCheckResult {
    data class UpToDate(val current: Int) : UpdateCheckResult()
    data class UpdateAvailable(val info: AppVersionInfo) : UpdateCheckResult()
    sealed class Failed(val kind: String) : UpdateCheckResult() {
        object NoNetwork : Failed("no_network")
        data class ServerError(val httpCode: Int) : Failed("server_error")
        data class ParseError(val cause: Throwable) : Failed("parse_error")
        sealed class DownloadInterrupted : Failed("interrupted") {
            object Cancelled : DownloadInterrupted()
            data class NetworkUnreachable(val cause: Throwable) : DownloadInterrupted()
            data class Other(val cause: Throwable) : DownloadInterrupted()
        }
        data class SignatureMismatch(val actual: String?, val expected: String) : Failed("signature_mismatch")
    }
}