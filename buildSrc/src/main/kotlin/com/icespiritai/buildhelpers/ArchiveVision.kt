package com.icespiritai.buildhelpers

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Pure helpers for staging the release APK + vision-latest.json into a
 * build-local upload staging dir. Mirrors the shape of translate's
 * `ArchiveLatest` (D:/GitHub/IceSpiritAI_Translate/app/src/main/java/com/
 * icespiritai/offline/updater/ArchiveLatest.kt). Lives in buildSrc/ so the
 * Gradle task in app/build.gradle.kts can call it without duplicating
 * logic.
 *
 * 2026-08-21: the prior `archive()` function (versioned APK copy into a
 * repo-root `发布版历史存档/` dir) was removed — release artifacts no
 * longer leak outside the build tree.
 */
object ArchiveVision {

    /**
     * Stage [apkSource] (already named `app-release.apk` by AGP) +
     * [jsonSource] into [uploadStagingDir]. The APK is RENAMED to
     * `icespiritai-vision.apk` (the Gitea release attachment filename —
     * the asset's filename on Gitea equals the multipart upload
     * basename, so clients can fetch it by direct URL). The JSON
     * filename is preserved.
     */
    fun archiveForUpload(
        apkSource: File,
        jsonSource: File,
        uploadStagingDir: File,
    ): Pair<File, File> {
        if (!uploadStagingDir.exists()) uploadStagingDir.mkdirs()
        require(uploadStagingDir.isDirectory) {
            "archiveForUpload: ${uploadStagingDir.absolutePath} exists but is not a directory"
        }
        val apkDest = uploadStagingDir.resolve("icespiritai-vision.apk")
        FileInputStream(apkSource).use { ins ->
            FileOutputStream(apkDest).use { out ->
                ins.copyTo(out, bufferSize = 64 * 1024)
            }
        }
        val jsonDest = uploadStagingDir.resolve(jsonSource.name)
        jsonSource.copyTo(jsonDest, overwrite = true)
        return apkDest to jsonDest
    }
}