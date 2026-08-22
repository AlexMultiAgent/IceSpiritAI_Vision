package com.icespiritai.offline.updater

object UpdateDownloadActions {
    const val ACTION_DOWNLOAD = "com.icespiritai.offline.updater.action.DOWNLOAD"
    const val ACTION_CANCEL = "com.icespiritai.offline.updater.action.CANCEL"
    const val ACTION_INSTALL = "com.icespiritai.offline.updater.action.INSTALL"
    const val ACTION_LATER = "com.icespiritai.offline.updater.action.LATER"
    const val ACTION_RETRY = "com.icespiritai.offline.updater.action.RETRY"

    const val EXTRA_DOWNLOAD_ID = "downloadId"
    const val EXTRA_URL = "url"
    const val EXTRA_DEST_PATH = "destPath"
    const val EXTRA_SIGNER_CERT_SHA256 = "signerCertSha256"
    const val EXTRA_VERSION_NAME = "versionName"
    const val EXTRA_RESUME = "resume"

    const val CHANNEL_ONGOING = "update_download_ongoing"
    const val CHANNEL_READY = "update_download_ready"
    const val CHANNEL_FAILED = "update_download_failed"
}
