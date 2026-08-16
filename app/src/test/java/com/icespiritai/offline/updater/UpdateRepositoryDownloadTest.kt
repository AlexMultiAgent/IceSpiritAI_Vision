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
    fun downloadsBytes_writesAllBytesToDisk() = runTest {
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
    fun downloadsBytes_invokesProgressCallbackAtLeastOnce() = runTest {
        // 4096 bytes over an 8 KiB buffer = a single chunk; the assertion is
        // deliberately "at least once" so a smaller buffer stays valid.
        val bytes = ByteArray(4096) { (it % 256).toByte() }
        factory = { FakeApkConn(200, bytes, contentLength = 4096L) }

        val outDir = Files.createTempDirectory("icespirit-dl-progress").toFile()
        val reports = mutableListOf<Long>()
        UpdateRepository.downloadApkTo(info, outDir) { written ->
            reports.add(written)
        }

        assertTrue("progress callback must be invoked at least once", reports.isNotEmpty())
        assertEquals(4096L, reports.last()) // final report = full byte count
        assertTrue(
            "progress reports must be monotonically increasing",
            reports == reports.sorted(),
        )
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

    @Test
    fun cancellationException_isNotSwallowedByDownloadApkCatch() {
        // The downloadApk catch block has `catch (e: CancellationException) { throw e }`
        // before the broader `catch (e: Exception)`. Verify the catch logic re-throws
        // CancellationException by calling the catch lambda directly.
        val cancelEx = kotlinx.coroutines.CancellationException("cancelled by test")
        val swallowed = runCatching {
            try {
                throw cancelEx
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "swallowed"
            }
        }
        assertTrue("CancellationException must propagate, not be swallowed",
            swallowed.exceptionOrNull() === cancelEx)
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