package com.icespiritai.buildhelpers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArchiveVisionTest {

    @Test
    fun archiveForUpload_copiesApkRenamed_andCopiesJsonAlongside() {
        // The upload staging dir is created on demand (build/generated/
        // release-staging/ does not exist at build time), so this test
        // also exercises the mkdirs() path.
        val tmp = Files.createTempDirectory("icespirit-upload").toFile()
        try {
            val src = File(tmp, "app-release.apk").apply {
                writeBytes(ByteArray(2048) { 0x07 })
            }
            val json = File(tmp, "vision-latest.json").apply {
                writeText("""{"versionCode":2}""")
            }
            val uploadDir = File(tmp, "upload")
            val (apkDest, jsonDest) = ArchiveVision.archiveForUpload(src, json, uploadDir)

            assertEquals("icespiritai-vision.apk", apkDest.name)
            assertEquals("vision-latest.json", jsonDest.name)
            assertEquals(2048L, apkDest.length())
            assertEquals("""{"versionCode":2}""", jsonDest.readText())
        } finally {
            tmp.deleteRecursively()
        }
    }
}