package com.icespiritai.offline.tts

/** Gitea release metadata 解析后的产物。 */
data class TtsEngineReleaseInfo(
    val tag: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)