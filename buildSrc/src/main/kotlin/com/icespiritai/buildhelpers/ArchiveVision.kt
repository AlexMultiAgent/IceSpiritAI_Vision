package com.icespiritai.buildhelpers

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Pure helpers for staging the debug APK + vision-latest.json into the
 * `发布版历史存档/` directory. Mirrors the shape of translate's
 * `ArchiveLatest` (D:/GitHub/IceSpiritAI_Translate/app/src/main/java/com/
 * icespiritai/offline/updater/ArchiveLatest.kt). Lives in buildSrc/ so the
 * Gradle task in app/build.gradle.kts (Task 15) can call it without
 * duplicating logic.
 */
object ArchiveVision {

    /**
     * Copy [apkSource] into [archiveDir] as `icespiritai-vision-v<versionName>.apk`.
     * Returns the destination [File].
     */
    fun archive(apkSource: File, archiveDir: File, versionName: String): File {
        if (!archiveDir.exists()) archiveDir.mkdirs()
        require(archiveDir.isDirectory) {
            "archive: ${archiveDir.absolutePath} exists but is not a directory"
        }
        val apkName = "icespiritai-vision-v$versionName.apk"
        val apkDest = archiveDir.resolve(apkName)
        FileInputStream(apkSource).use { ins ->
            FileOutputStream(apkDest).use { out ->
                ins.copyTo(out, bufferSize = 64 * 1024)
            }
        }
        return apkDest
    }

    /**
     * Stage [apkSource] (already named icespiritai-vision-vX.Y.Z.apk) +
     * [jsonSource] into [uploadStagingDir]. The APK is RENAMED to
     * `icespiritai-vision-update.apk` (matches the Gitea release attachment
     * filename). The JSON filename is preserved.
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
        val apkDest = uploadStagingDir.resolve("icespiritai-vision-update.apk")
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