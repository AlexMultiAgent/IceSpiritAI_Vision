package com.icespiritai.offline.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class TtsEngineInstallerTest {

    private val cacheDir: File = createTempDirectory(prefix = "tts-test").toFile().apply { deleteOnExit() }
    private val apkFile = File(cacheDir, "icespirit-tts-engine.apk")
    private val partialFile = File(cacheDir, "icespirit-tts-engine.apk.partial")
    private val metaFile = File(cacheDir, "icespirit-tts-engine.apk.meta")

    @Test fun `sidecar meta tracks downloadedBytes and totalBytes`() = runTest {
        val meta = TtsEngineInstaller.Meta(downloadedBytes = 1024, totalBytes = 4096, sha256 = "abc")
        TtsEngineInstaller.writeMeta(metaFile, meta)
        val read = TtsEngineInstaller.readMeta(metaFile)
        assertNotNull(read)
        assertEquals(1024, read!!.downloadedBytes)
        assertEquals(4096, read.totalBytes)
        assertEquals("abc", read.sha256)
    }

    @Test fun `readMeta returns null if file does not exist`() {
        val read = TtsEngineInstaller.readMeta(File(cacheDir, "nope.meta"))
        assertEquals(null, read)
    }

    @Test fun `parseReleaseJson extracts url size sha256`() {
        val json = """
            {"tag_name":"icespirit-tts-engine-v1.0.0",
             "assets":[{"name":"icespirit-tts-engine.apk",
                        "browser_download_url":"http://x/y.apk",
                        "size":12345,
                        "sha256":"abc123"}]}
        """.trimIndent()
        val info = TtsEngineInstaller.parseReleaseJson(json)
        assertEquals("icespirit-tts-engine-v1.0.0", info.tag)
        assertEquals("http://x/y.apk", info.apkUrl)
        assertEquals(12345L, info.sizeBytes)
        assertEquals("abc123", info.sha256)
    }

    @Test fun `parseLatestJson extracts apkUrl size sha256 from vision-latest schema`() {
        // Bug 3 fix (v0.1.60): the engine release JSON mirrors
        // vision-latest.json (apkUrl / apkSize / apkSha256 / versionCode),
        // NOT the Gitea `/releases` API shape parsed by parseReleaseJson.
        // fetchReleaseInfo() does a real HTTP GET on this document; the
        // parse step is factored out here so it is unit-testable.
        val json = """
            {
              "versionCode": 3,
              "versionName": "1.0.0",
              "apkUrl": "http://125.211.45.14:3000/attachments/abc-uuid",
              "apkSize": 157286400,
              "apkSha256": "deadbeef",
              "signerCertSha256": "4a21f4"
            }
        """.trimIndent()
        val info = TtsEngineInstaller.parseLatestJson(json)
        assertEquals("http://125.211.45.14:3000/attachments/abc-uuid", info.apkUrl)
        assertEquals(157286400L, info.sizeBytes)
        assertEquals("deadbeef", info.sha256)
        assertEquals("3", info.tag)
    }

    @Test fun `parseLatestJson tolerates missing apkSize`() {
        // Gitea attachment URLs sometimes omit apkSize; downloadWithResume
        // falls back to the Content-Range header, so -1 must not throw.
        val info = TtsEngineInstaller.parseLatestJson(
            """{"versionCode":1,"apkUrl":"http://x/y.apk","apkSha256":"h"}"""
        )
        assertEquals(-1L, info.sizeBytes)
        assertEquals("h", info.sha256)
    }

    @Test fun `sha256 mismatch transition to Failed and deletes partial`() = runTest {
        partialFile.writeBytes(ByteArray(100) { 0x42 })
        metaFile.writeText("""{"downloadedBytes":100,"totalBytes":100,"sha256":"expected"}""")
        val actual = "actual_hash"
        val result = TtsEngineInstaller.verifyOrDelete(partialFile, metaFile, expectedSha = "expected", actualSha = actual)
        assertTrue(result is InstallState.Failed)
        assertTrue(!partialFile.exists())
        assertTrue(!metaFile.exists())
    }

    @Test fun `sha256 match returns Done and renames partial to apk`() = runTest {
        partialFile.writeBytes(ByteArray(4))
        metaFile.writeText("""{"downloadedBytes":4,"totalBytes":4,"sha256":"h"}""")
        val result = TtsEngineInstaller.verifyOrDelete(partialFile, metaFile, expectedSha = "h", actualSha = "h")
        assertTrue(result is InstallState.Done)
        assertTrue(apkFile.exists())
        assertTrue(!partialFile.exists())
        assertTrue(!metaFile.exists())
    }
}