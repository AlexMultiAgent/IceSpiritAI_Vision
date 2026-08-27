package com.icespiritai.offline.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun full_download_200_writes_entire_body() {
        val body = ByteArray(8192 * 3) { (it and 0xFF).toByte() }
        val conn = FakeHttpConn(
            code = 200,
            contentLength = body.size.toLong(),
            etag = "\"v1\"",
            inputBytes = body,
        )
        val dest = tmp.newFile("out.apk")
        var lastWritten = 0L
        val outcome = ApkDownloader.fetch(
            openConnection = { conn },
            destFile = dest,
            resumeFrom = null,
            etag = null,
            onProgress = { lastWritten = it },
        )
        assertTrue("expected Success, got $outcome", outcome is FetchOutcome.Success)
        val s = outcome as FetchOutcome.Success
        assertEquals(body.size.toLong(), s.result.bytesWritten)
        assertEquals("\"v1\"", s.result.etag)
        assertEquals(body.size, dest.readBytes().size)
        assertEquals(body.size.toLong(), lastWritten)
        assertEquals(null, conn.requestedHeaders["Range"])
    }

    @Test
    fun resume_206_appends_to_existing_file() {
        val existing = byteArrayOf(1, 2, 3, 4, 5)
        val appended = byteArrayOf(6, 7, 8, 9, 10)
        val dest = tmp.newFile("partial.apk")
        dest.writeBytes(existing)
        val conn = FakeHttpConn(
            code = 206,
            contentLength = appended.size.toLong(),
            etag = "\"v2\"",
            inputBytes = appended,
        )
        val outcome = ApkDownloader.fetch(
            openConnection = { conn },
            destFile = dest,
            resumeFrom = 5L,
            etag = "\"v1\"",
            onProgress = {},
        )
        assertTrue("expected Success, got $outcome", outcome is FetchOutcome.Success)
        val bytes = dest.readBytes()
        assertEquals(10, bytes.size)
        assertEquals(1.toByte(), bytes[0])
        assertEquals(10.toByte(), bytes[9])
        assertEquals("bytes=5-", conn.requestedHeaders["Range"]?.firstOrNull())
        assertEquals("\"v1\"", conn.requestedHeaders["If-Range"]?.firstOrNull())
    }

    @Test
    fun http_416_returns_Fatal() {
        val conn = FakeHttpConn(code = 416)
        val dest = tmp.newFile("out.apk")
        val outcome = ApkDownloader.fetch(
            openConnection = { conn },
            destFile = dest,
            resumeFrom = 99999L,
            etag = "\"v1\"",
            onProgress = {},
        )
        assertTrue("expected Fatal, got $outcome", outcome is FetchOutcome.Fatal)
    }

    @Test
    fun http_503_returns_Retryable() {
        val conn = FakeHttpConn(code = 503)
        val dest = tmp.newFile("out.apk")
        val outcome = ApkDownloader.fetch(
            openConnection = { conn },
            destFile = dest,
            resumeFrom = null,
            etag = null,
            onProgress = {},
        )
        assertTrue("expected Retryable, got $outcome", outcome is FetchOutcome.Retryable)
    }

    @Test
    fun onMetadata_fires_once_with_total_bytes_before_body() {
        val body = ByteArray(1024) { (it and 0xFF).toByte() }
        val conn = FakeHttpConn(
            code = 200,
            contentLength = body.size.toLong(),
            inputBytes = body,
        )
        var metadataFires = 0
        var progressFires = 0
        var metadataValue = -1L
        var firstWrittenAfterMetadata = -1L
        ApkDownloader.fetch(
            openConnection = { conn },
            destFile = tmp.newFile("out.apk"),
            resumeFrom = null,
            etag = null,
            onProgress = {
                progressFires += 1
                if (metadataFires == 1 && firstWrittenAfterMetadata < 0L) firstWrittenAfterMetadata = it
            },
            onMetadata = {
                metadataFires += 1
                metadataValue = it
            },
        )
        assertEquals(1, metadataFires)
        assertEquals(body.size.toLong(), metadataValue)
        // onMetadata must fire BEFORE any onProgress callback — UpdateDownloadService
        // relies on the total being published to StateFlow before the first body byte
        // lands so the UI bar moves from indeterminate to a real percentage immediately.
        assertTrue(
            "expected onMetadata to fire before onProgress, got metadata=$metadataFires progress=$progressFires",
            metadataFires == 1 && progressFires >= 1,
        )
    }

    @Test
    fun onMetadata_skipped_when_content_length_unknown() {
        // -1 is HttpURLConnection's sentinel for "server didn't send
        // Content-Length" — fetch() must not surface that as a fake total
        // to UI consumers (would divide by it).
        val conn = FakeHttpConn(code = 200, contentLength = -1L)
        var metadataFires = 0
        ApkDownloader.fetch(
            openConnection = { conn },
            destFile = tmp.newFile("out.apk"),
            resumeFrom = null,
            etag = null,
            onProgress = {},
            onMetadata = { metadataFires += 1 },
        )
        assertEquals(0, metadataFires)
    }
}

private class FakeHttpConn(
    private val code: Int,
    private val contentLength: Long = 0L,
    private val etag: String? = null,
    private val inputBytes: ByteArray = ByteArray(0),
) : HttpURLConnection(URL("http://fake/")) {

    /** Captured setRequestProperty calls. Key is case-preserving; values are appended in order. */
    val requestedHeaders: MutableMap<String, MutableList<String>> = mutableMapOf()

    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = code
    override fun getInputStream(): InputStream = ByteArrayInputStream(inputBytes)
    override fun getContentLengthLong(): Long = contentLength
    override fun getErrorStream(): InputStream? = null

    override fun getHeaderField(name: String?): String? =
        if (name.equals("ETag", ignoreCase = true)) etag else null

    override fun setRequestProperty(key: String?, value: String?) {
        if (key != null && value != null) {
            requestedHeaders.getOrPut(key) { mutableListOf() }.add(value)
        }
    }
}