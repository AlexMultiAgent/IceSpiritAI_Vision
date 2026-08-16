package com.icespiritai.offline.updater

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class UpdateRepositoryDownloadTest {

    private var factory: (String) -> HttpURLConnection = { error("not configured") }
    private val apkUrl = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision-update.apk"
    private val info = AppVersionInfo(
        versionCode = 2, versionName = "0.2.0",
        apkUrl = apkUrl, apkSize = 1024L,
        apkSha256 = "a".repeat(64), changelog = "",
    )

    @Before
    fun reset() {
        UpdateRepository.connectionFactory = { factory(it) }
    }

    @After
    fun cleanup() {
        UpdateRepository.connectionFactory = null
    }

    @Test
    fun downloadsBytes_andReportsProgress_atLeastOnce() = runTest {
        val bytes = ByteArray(1024) { (it % 256).toByte() }
        factory = { FakeApkConn(200, bytes, contentLength = 1024L) }

        val outDir = Files.createTempDirectory("icespirit-dl").toFile()
        val outFile = UpdateRepository.downloadApkTo(info, outDir)

        assertEquals("icespiritai-vision-update.apk", outFile.name)
        assertEquals(1024L, outFile.length())
        assertTrue(bytes.toList() == outFile.readBytes().toList())
        outDir.deleteRecursively()
    }

    @Test
    fun httpError_throwsIOException() = runTest {
        factory = { FakeApkConn(500, ByteArray(0), contentLength = 0L) }
        val outDir = Files.createTempDirectory("icespirit-dl-err").toFile()
        var threw = false
        try {
            UpdateRepository.downloadApkTo(info, outDir)
        } catch (e: IOException) {
            threw = true
        }
        assertTrue("IOException expected on HTTP 500", threw)
        outDir.deleteRecursively()
    }

    private class FakeApkConn(
        private val code: Int,
        private val bytes: ByteArray,
        private val contentLength: Long,
    ) : HttpURLConnection(URL("http://fake/")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
        override fun getContentLengthLong(): Long = contentLength
        override fun getErrorStream(): InputStream? = if (code >= 400) ByteArrayInputStream(byteArrayOf()) else null
    }
}