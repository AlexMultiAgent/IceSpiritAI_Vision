package com.icespiritai.offline.updater

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Outcome of a single APK byte-stream fetch. [Success] carries the final
 * on-disk file metadata (size / etag / sha-256 / response code). [Retryable]
 * maps to a network blip the caller's outer loop should back off and try
 * again. [Fatal] is unrecoverable for this URL (416 Range Not Satisfiable,
 * 4xx other than 416, or a non-IO Exception) — caller should NOT retry.
 *
 * Note: for the 206 branch, [FetchResult.sha256Hex] covers ONLY the bytes
 * received in this response (the tail), not the cumulative file. The caller
 * (UpdateRepository.downloadApkTo or the cert-pin gate) is responsible for
 * hashing the full destination file.
 */
data class FetchResult(
    val bytesWritten: Long,
    val totalBytes: Long,
    val etag: String?,
    val sha256Hex: String,
    val responseCode: Int,
)

sealed class FetchOutcome {
    data class Success(val result: FetchResult) : FetchOutcome()
    data class Retryable(val cause: Throwable) : FetchOutcome()
    data class Fatal(val cause: Throwable) : FetchOutcome()
}

/**
 * Pure byte-stream primitive for APK downloads. Given a connection factory
 * (so tests can inject a [java.net.HttpURLConnection] without touching the
 * real network), writes the response body to [destFile].
 *
 * Resume semantics:
 *   * `resumeFrom == null` → server returns 200 OK, we write the whole body
 *     to a fresh file (any existing partial on disk is overwritten).
 *   * `resumeFrom != null` AND `etag != null` → we send `Range: bytes=N-`
 *     plus `If-Range: <etag>`. On 206 we APPEND to the existing partial;
 *     on 200 (server says "file changed, restart") we DELETE and rewrite.
 *   * 416 → [FetchOutcome.Fatal] (Range Not Satisfiable — caller must restart
 *     from 0 with no Range header).
 *   * 5xx and SocketTimeoutException / UnknownHostException →
 *     [FetchOutcome.Retryable].
 *
 * The factory lambda is invoked once per call; it should produce a fresh,
 * unconnected [HttpURLConnection] (connect-timeout / read-timeout are
 * already set by the caller if needed). `disconnect()` is called in `finally`.
 */
object ApkDownloader {
    private const val BUF_SIZE = 8192

    fun fetch(
        openConnection: () -> HttpURLConnection,
        destFile: File,
        resumeFrom: Long?,
        etag: String?,
        onProgress: (Long) -> Unit,
        onMetadata: (Long) -> Unit = {},
    ): FetchOutcome {
        val conn = openConnection()
        try {
            // Headers must be set BEFORE responseCode is read; HttpURLConnection
            // throws IllegalStateException("Already connected") otherwise.
            if (resumeFrom != null && resumeFrom > 0) {
                conn.setRequestProperty("Range", "bytes=$resumeFrom-")
                if (!etag.isNullOrEmpty()) conn.setRequestProperty("If-Range", etag)
            }

            val code = conn.responseCode
            // HttpURLConnection returns -1 when the server omits Content-Length
            // (chunked transfer, RFC 7230 §3.3.2). Clamp to 0 so the progress
            // UI can branch on a "total unknown" sentinel without accidentally
            // computing negative remaining-byte values downstream.
            val partialBodyLen = maxOf(0L, conn.contentLengthLong)
            val totalBytes = if (code == 206 && resumeFrom != null) resumeFrom + partialBodyLen
                             else partialBodyLen
            val respEtag = conn.getHeaderField("ETag")

            // Fire onMetadata as soon as Content-Length is known so the caller's
            // progress UI (and the FGS notification) get a non-zero `total`
            // before the first byte of the body lands. Defaulted to a no-op so
            // existing call sites that only care about byte-by-byte progress
            // (or unit tests) don't have to plumb the callback through.
            // -1 means "server omitted Content-Length" — don't surface that as
            // a real total to UI consumers (would divide by it).
            if (totalBytes > 0) onMetadata(totalBytes)

            when {
                code == 200 || code == 206 -> {
                    val startOffset = if (code == 206) resumeFrom ?: 0L else 0L
                    // Server said 200 even though we asked for a Range — the file
                    // changed since our etag. Drop the partial and rewrite from 0.
                    if (code == 200 && resumeFrom != null) {
                        destFile.delete()
                    }
                    val md = MessageDigest.getInstance("SHA-256")
                    FileOutputStream(destFile, code == 206).use { fos ->
                        conn.inputStream.use { ins ->
                            val buf = ByteArray(BUF_SIZE)
                            var written = startOffset
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                fos.write(buf, 0, n)
                                md.update(buf, 0, n)
                                written += n
                                onProgress(written)
                            }
                        }
                    }
                    val sha = md.digest().joinToString("") { "%02x".format(it) }
                    return FetchOutcome.Success(
                        FetchResult(
                            bytesWritten = destFile.length(),
                            totalBytes = totalBytes,
                            etag = respEtag,
                            sha256Hex = sha,
                            responseCode = code,
                        )
                    )
                }
                code == 416 -> return FetchOutcome.Fatal(
                    IOException("HTTP 416 Range Not Satisfiable (resumeFrom=$resumeFrom)"),
                )
                code in 500..599 -> return FetchOutcome.Retryable(IOException("HTTP $code"))
                code in 400..499 -> return FetchOutcome.Fatal(IOException("HTTP $code"))
                else -> return FetchOutcome.Fatal(IOException("unexpected HTTP $code"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            return FetchOutcome.Retryable(e)
        } catch (e: java.net.UnknownHostException) {
            return FetchOutcome.Retryable(e)
        } catch (e: IOException) {
            return FetchOutcome.Retryable(e)
        } catch (e: Exception) {
            return FetchOutcome.Fatal(e)
        } finally {
            conn.disconnect()
        }
    }
}