package com.icespiritai.buildhelpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArchiveVisionTest {

    @Test
    fun archive_copiesApkToArchiveDir_withVersionedName() {
        val tmp = Files.createTempDirectory("icespirit-archive").toFile()
        try {
            val src = File(tmp, "src.apk").apply {
                writeBytes(ByteArray(1024) { 0x42 })
            }
            val archiveDir = File(tmp, "archive")
            val out = ArchiveVision.archive(src, archiveDir, versionName = "0.2.0")

            assertEquals("icespiritai-vision-v0.2.0.apk", out.name)
            assertEquals(1024L, out.length())
            assertEquals(src.readBytes().toList(), out.readBytes().toList())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun archiveForUpload_copiesApkRenamed_andCopiesJsonAlongside() {
        val tmp = Files.createTempDirectory("icespirit-upload").toFile()
        try {
            val src = File(tmp, "icespiritai-vision-v0.2.0.apk").apply {
                writeBytes(ByteArray(2048) { 0x07 })
            }
            val json = File(tmp, "vision-latest.json").apply {
                writeText("""{"versionCode":2}""")
            }
            val uploadDir = File(tmp, "upload")
            val (apkDest, jsonDest) = ArchiveVision.archiveForUpload(src, json, uploadDir)

            assertEquals("icespiritai-vision-update.apk", apkDest.name)
            assertEquals("vision-latest.json", jsonDest.name)
            assertEquals(2048L, apkDest.length())
            assertEquals("""{"versionCode":2}""", jsonDest.readText())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun archive_createsMissingDirectory() {
        val tmp = Files.createTempDirectory("icespirit-mkdir").toFile()
        try {
            val src = File(tmp, "x.apk").apply { writeBytes(byteArrayOf(1)) }
            val archiveDir = File(tmp, "deep/nested/path")
            ArchiveVision.archive(src, archiveDir, versionName = "0.1.0")
            assertTrue(archiveDir.isDirectory)
        } finally {
            tmp.deleteRecursively()
        }
    }
}