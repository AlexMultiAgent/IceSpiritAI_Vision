package com.icespiritai.offline.updater

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class UpdateRepositoryCheckTest {

    private val jsonUrl = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json"
    private val sampleJson = """
        {"versionCode":2,"versionName":"0.2.0",
         "apkUrl":"http://x/y.apk","apkSize":1,"apkSha256":"${"a".repeat(64)}",
         "changelog":"","apkCumulativeDownloads":0}
    """.trimIndent()

    /** Mutable singleton holder so we can swap the factory between tests. */
    private var factory: (String) -> HttpURLConnection = { error("not configured") }

    @Before
    fun reset() {
        UpdateRepository.connectionFactory = { factory(it) }
    }

    @After
    fun cleanup() {
        UpdateRepository.connectionFactory = null
    }

    @Test
    fun newerVersionCode_returnsUpdateAvailable() = runTest {
        factory = { FakeConn(200, sampleJson) }
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 1,
            connectionFactory = { factory(it) },
        )
        assertTrue(r is UpdateCheckResult.UpdateAvailable)
        assertEquals(2, (r as UpdateCheckResult.UpdateAvailable).info.versionCode)
    }

    @Test
    fun sameVersionCode_returnsUpToDate() = runTest {
        factory = { FakeConn(200, sampleJson) }
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 2,
            connectionFactory = { factory(it) },
        )
        assertEquals(UpdateCheckResult.UpToDate(2), r)
    }

    @Test
    fun olderVersionCode_returnsUpToDate() = runTest {
        factory = { FakeConn(200, sampleJson) }
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 5,
            connectionFactory = { factory(it) },
        )
        assertEquals(UpdateCheckResult.UpToDate(5), r)
    }

    @Test
    fun httpError_returnsServerError() = runTest {
        factory = { FakeConn(500, "") }
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 1,
            connectionFactory = { factory(it) },
        )
        assertTrue(r is UpdateCheckResult.Failed.ServerError)
        assertEquals(500, (r as UpdateCheckResult.Failed.ServerError).httpCode)
    }

    @Test
    fun unknownHost_returnsNoNetwork() = runTest {
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 1,
            connectionFactory = { throw UnknownHostException("test") },
        )
        assertEquals(UpdateCheckResult.Failed.NoNetwork, r)
    }

    @Test
    fun socketTimeout_returnsDownloadInterrupted() = runTest {
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 1,
            connectionFactory = { throw SocketTimeoutException("test") },
        )
        assertTrue(r is UpdateCheckResult.Failed.DownloadInterrupted)
    }

    @Test
    fun garbageJson_returnsParseError() = runTest {
        factory = { FakeConn(200, "not json at all") }
        val r = UpdateRepository.checkForUpdates(
            jsonUrl = jsonUrl,
            currentVersionCode = 1,
            connectionFactory = { factory(it) },
        )
        assertTrue(r is UpdateCheckResult.Failed.ParseError)
    }

    /** Minimal fake HttpURLConnection — only the methods UpdateRepository uses. */
    private class FakeConn(
        private val code: Int,
        private val body: String,
    ) : HttpURLConnection(URL("http://fake/")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream(): InputStream? = if (code >= 400) ByteArrayInputStream(byteArrayOf()) else null
    }
}