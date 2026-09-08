package com.icespiritai.offline.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [TtsModelInstaller] (Bug 3 pivot v0.1.60).
 *
 * Covers:
 *  - isModelInstalled flips when both ONNX files exist
 *  - downloadModel writes both ONNX to filesDir/<rootDir>/zh/
 *  - downloadModel verifies sha256 and reports Failed on mismatch
 *  - meta sidecar round-trip (writeMeta / readMeta)
 *
 * Pure JVM — uses a temporary directory, a real coroutine scope on the
 * test dispatcher, and a [TtsModelInstaller] subclass that injects
 * fake descriptors + writes (no real network).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TtsModelInstallerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(SupervisorJob() + testDispatcher)

    private lateinit var tempDir: File
    private lateinit var installer: TestableTtsModelInstaller

    @Before fun setUp() {
        tempDir = createTempDirectory(prefix = "tts-model-installer-test").toFile().apply { deleteOnExit() }
        installer = TestableTtsModelInstaller(
            filesDir = tempDir,
            scope = testScope,
        )
    }

    @After fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test fun `isModelInstalled is false when both ONNX files missing`() {
        assertFalse(installer.isModelInstalled())
    }

    @Test fun `isModelInstalled is true when both ONNX files exist`() {
        installer.modelDir.mkdirs()
        File(installer.modelDir, "model-steps-3.onnx").writeBytes(ByteArray(10))
        File(installer.modelDir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(10))
        assertTrue(installer.isModelInstalled())
    }

    @Test fun `downloadModel writes both ONNX to modelDir and reports Done`() = runTest(testDispatcher) {
        val modelBytes = ByteArray(256) { 0x42 }
        val vocoderBytes = ByteArray(128) { 0x77 }
        installer.fakeServer[ACOUSTIC_URL] = modelBytes
        installer.fakeServer[VOCODER_URL] = vocoderBytes
        installer.expectedSha["model-steps-3.onnx"] = sha256Of(modelBytes)
        installer.expectedSha["vocos-22khz-univ.onnx"] = sha256Of(vocoderBytes)

        installer.downloadModel()
        advanceUntilIdle()

        val acoustic = File(installer.modelDir, "model-steps-3.onnx")
        val vocoder = File(installer.modelDir, "vocos-22khz-univ.onnx")
        assertTrue("acoustic missing", acoustic.isFile)
        assertTrue("vocoder missing", vocoder.isFile)
        assertEquals(modelBytes.size.toLong(), acoustic.length())
        assertEquals(vocoderBytes.size.toLong(), vocoder.length())
        assertEquals(InstallState.Done, installer.state.value)
    }

    @Test fun `downloadModel reports Failed and deletes partial on sha256 mismatch`() = runTest(testDispatcher) {
        installer.fakeServer[ACOUSTIC_URL] = ByteArray(64) { 0x55 }
        installer.fakeServer[VOCODER_URL] = ByteArray(32) { 0x66 }
        installer.expectedSha["model-steps-3.onnx"] = "deadbeef".repeat(8)  // wrong
        installer.expectedSha["vocos-22khz-univ.onnx"] = "deadbeef".repeat(8)

        installer.downloadModel()
        advanceUntilIdle()

        val state = installer.state.value
        assertTrue("expected Failed, got $state", state is InstallState.Failed)
        assertFalse(File(installer.modelDir, "model-steps-3.onnx").exists())
        assertFalse(File(installer.modelDir, "model-steps-3.onnx.partial").exists())
    }

    @Test fun `sidecar meta round trip`() {
        val meta = TtsModelInstaller.Meta(downloadedBytes = 1024, totalBytes = 4096, sha256 = "abc")
        val metaFile = File(tempDir, "test.meta")
        TtsModelInstaller.writeMeta(metaFile, meta)
        val read = TtsModelInstaller.readMeta(metaFile)
        assertNotNull(read)
        assertEquals(1024L, read!!.downloadedBytes)
        assertEquals(4096L, read.totalBytes)
        assertEquals("abc", read.sha256)
    }

    @Test fun `readMeta returns null when file missing`() {
        assertEquals(null, TtsModelInstaller.readMeta(File(tempDir, "nope.meta")))
    }

    @Test fun `verifySha256 returns true on match`() {
        val bytes = ByteArray(100) { 0x42 }
        val file = File(tempDir, "test.bin").apply { writeBytes(bytes) }
        val expected = sha256Of(bytes)
        assertTrue(TtsModelInstaller.verifySha256(file, expected))
    }

    @Test fun `verifySha256 returns false on mismatch`() {
        val file = File(tempDir, "test.bin").apply { writeBytes(ByteArray(10)) }
        assertFalse(TtsModelInstaller.verifySha256(file, "deadbeef".repeat(8)))
    }

    /**
     * Subclass that injects:
     *  - a fake HTTP server keyed by URL → ByteArray
     *  - per-file expected sha256 overrides (otherwise defaults to all-zeros
     *    which would mismatch any non-empty download)
     */
    private class TestableTtsModelInstaller(
        filesDir: File,
        scope: CoroutineScope,
    ) : TtsModelInstaller(
        filesDir = filesDir,
        scope = scope,
        jsonUrl = "http://stub/releases/latest.json",
    ) {
        val fakeServer = mutableMapOf<String, ByteArray>()
        val expectedSha = mutableMapOf<String, String>()

        override suspend fun fetchDescriptors(): List<FileDescriptor> = listOf(
            FileDescriptor(
                fileName = "model-steps-3.onnx",
                url = ACOUSTIC_URL,
                sizeBytes = 0L,
                sha256 = expectedSha["model-steps-3.onnx"] ?: "0".repeat(64),
            ),
            FileDescriptor(
                fileName = "vocos-22khz-univ.onnx",
                url = VOCODER_URL,
                sizeBytes = 0L,
                sha256 = expectedSha["vocos-22khz-univ.onnx"] ?: "0".repeat(64),
            ),
        )

        override suspend fun downloadWithResume(desc: FileDescriptor) {
            val bytes = fakeServer[desc.url] ?: ByteArray(0)
            File(modelDir, "${desc.fileName}.partial").writeBytes(bytes)
        }
    }

    private fun sha256Of(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val ACOUSTIC_URL = "http://stub/model-steps-3.onnx"
        private const val VOCODER_URL = "http://stub/vocos-22khz-univ.onnx"
    }
}
