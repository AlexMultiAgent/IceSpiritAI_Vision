package com.icespiritai.offline.tts

import androidx.test.core.app.ApplicationProvider
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [TtsModelInstaller] (Bug 3 pivot v0.1.60, Bug 7b
 * hybrid path v0.1.63, Bug 7c espeak-ng-data copy v0.1.63).
 *
 * Covers:
 *  - isModelInstalled is false when any required file is missing (2 ONNX
 *    + 5 text/rule + espeak-ng-data core files — Bug 7c)
 *  - isModelInstalled is true when all required files exist
 *  - downloadModel copies bundled assets from APK assets (including the
 *    espeak-ng-data subdirectory — Bug 7c recursive walk) and downloads
 *    ONNX; reports Done when sha256 verifies
 *  - downloadModel verifies sha256 and reports Failed on mismatch
 *  - meta sidecar round-trip (writeMeta / readMeta)
 *
 * Robolectric (sdk=33) so `context.assets.open(...)` works for the
 * bundled copy. Pure JVM tests can't drive AssetManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsModelInstallerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(SupervisorJob() + testDispatcher)

    private lateinit var tempDir: File
    private lateinit var installer: TestableTtsModelInstaller

    @Before fun setUp() {
        tempDir = createTempDirectory(prefix = "tts-model-installer-test").toFile().apply { deleteOnExit() }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        installer = TestableTtsModelInstaller(
            filesDir = tempDir,
            scope = testScope,
            assets = ctx.assets,
            testDispatcher = testDispatcher,
        )
    }

    @After fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test fun `isModelInstalled is false when any required file is missing`() {
        // Empty modelDir
        assertFalse(installer.isModelInstalled())
        // ONNX only, no text/rule files
        installer.modelDir.mkdirs()
        File(installer.modelDir, "model-steps-3.onnx").writeBytes(ByteArray(10))
        File(installer.modelDir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(10))
        assertFalse("text/rule files missing", installer.isModelInstalled())
    }

    @Test fun `isModelInstalled is true when all required files exist`() {
        installer.modelDir.mkdirs()
        File(installer.modelDir, "model-steps-3.onnx").writeBytes(ByteArray(10))
        File(installer.modelDir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(10))
        for (name in TtsModelInstaller.BUNDLED_ASSET_FILES) {
            File(installer.modelDir, name).writeBytes(ByteArray(10))
        }
        // Bug 7c + v0.1.65 hardening: ALL 9 espeak-ng-data files must
        // be present (partial install → null deref at
        // OfflineTts_generateImpl+268).
        plantAllEspeakFiles()
        assertTrue(installer.isModelInstalled())
    }

    @Test fun `isModelInstalled is false when espeak-ng-data files are missing`() {
        installer.modelDir.mkdirs()
        File(installer.modelDir, "model-steps-3.onnx").writeBytes(ByteArray(10))
        File(installer.modelDir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(10))
        for (name in TtsModelInstaller.BUNDLED_ASSET_FILES) {
            File(installer.modelDir, name).writeBytes(ByteArray(10))
        }
        // Plant 8 of 9 — drop the last one and verify integrity gate.
        val espeakDir = File(installer.modelDir, TtsModelInstaller.ESPEAK_DATA_DIR).apply { mkdirs() }
        for (name in TtsModelInstaller.BUNDLED_ESPEAK_FILES.dropLast(1)) {
            val f = File(espeakDir, name)
            f.parentFile?.mkdirs()
            f.writeBytes(ByteArray(10))
        }
        assertFalse("missing ${TtsModelInstaller.BUNDLED_ESPEAK_FILES.last()} must fail integrity check",
            installer.isModelInstalled())
    }

    @Test fun `downloadModel copies bundled assets downloads ONNX and reports Done`() = runTest(testDispatcher) {
        val modelBytes = ByteArray(256) { 0x42 }
        val vocoderBytes = ByteArray(128) { 0x77 }
        installer.fakeServer[ACOUSTIC_URL] = modelBytes
        installer.fakeServer[VOCODER_URL] = vocoderBytes
        installer.expectedSha["model-steps-3.onnx"] = sha256Of(modelBytes)
        installer.expectedSha["vocos-22khz-univ.onnx"] = sha256Of(vocoderBytes)

        installer.downloadModel()
        advanceUntilIdle()

        // Bundled assets written (TestAssets provides canned bytes for each)
        for (name in TtsModelInstaller.BUNDLED_ASSET_FILES) {
            val f = File(installer.modelDir, name)
            assertTrue("bundled asset $name missing", f.isFile)
        }
        // Bug 7c + v0.1.65 hardening: full espeak-ng-data subtree (9
        // files) planted by copyBundledAssets() — partial copy would
        // silently leave the engine routing into the native null deref.
        val espeakDir = File(installer.modelDir, TtsModelInstaller.ESPEAK_DATA_DIR)
        assertTrue("espeak-ng-data dir missing", espeakDir.isDirectory)
        for (name in TtsModelInstaller.BUNDLED_ESPEAK_FILES) {
            val f = File(espeakDir, name)
            assertTrue("espeak-ng-data/$name missing", f.isFile)
        }
        // ONNX written from fake server
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

    @Test fun `downloadModel is idempotent when already installed`() = runTest(testDispatcher) {
        // Pre-populate modelDir with all required files (incl. v0.1.65
        // hardening — ALL 9 espeak-ng-data files must be present for
        // isModelInstalled() to short-circuit)
        installer.modelDir.mkdirs()
        File(installer.modelDir, "model-steps-3.onnx").writeBytes(ByteArray(10))
        File(installer.modelDir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(10))
        for (name in TtsModelInstaller.BUNDLED_ASSET_FILES) {
            File(installer.modelDir, name).writeBytes(ByteArray(10))
        }
        plantAllEspeakFiles()
        // Wipe fake server — downloadModel must NOT touch network when
        // isModelInstalled() returns true
        installer.fakeServer.clear()
        installer.expectedSha.clear()

        installer.downloadModel()
        advanceUntilIdle()

        assertEquals(InstallState.Done, installer.state.value)
        // ONNX partials must NOT exist (no download attempted)
        assertFalse(File(installer.modelDir, "model-steps-3.onnx.partial").exists())
        assertFalse(File(installer.modelDir, "vocos-22khz-univ.onnx.partial").exists())
    }

    /**
     * Plant all 9 espeak-ng-data files under modelDir/espeak-ng-data/ as
     * canned bytes. Used by tests that need isModelInstalled() to pass.
     * `parentFile?.mkdirs()` is required because [TtsModelInstaller.BUNDLED_ESPEAK_FILES]
     * includes nested paths (`lang/sit/cmn`, `lang/sit/cmn-Latn-pinyin`)
     * whose parent directories don't exist yet on a fresh modelDir.
     */
    private fun plantAllEspeakFiles() {
        val espeakDir = File(installer.modelDir, TtsModelInstaller.ESPEAK_DATA_DIR).apply { mkdirs() }
        for (name in TtsModelInstaller.BUNDLED_ESPEAK_FILES) {
            val f = File(espeakDir, name)
            f.parentFile?.mkdirs()
            f.writeBytes(ByteArray(10))
        }
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
        assets: android.content.res.AssetManager,
        private val testDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : TtsModelInstaller(
        filesDir = filesDir,
        scope = scope,
        jsonUrl = "http://stub/releases/latest.json",
        assets = assets,
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

        // Plant bundled assets synchronously (no IO dispatcher hop) so
        // runTest(testDispatcher) actually drives the launch coroutine
        // past copyBundledAssets. The production path uses
        // withContext(Dispatchers.IO) for real AssetManager reads, but
        // Dispatchers.IO is a real thread pool not drained by the test
        // scheduler, which would hang the test at the first suspension.
        override suspend fun copyBundledAssets() {
            kotlinx.coroutines.withContext(testDispatcher) {
                for (name in TtsModelInstaller.BUNDLED_ASSET_FILES) {
                    File(modelDir, name).writeBytes(ByteArray(32) { 0x33 })
                }
                // Bug 7c + v0.1.65 hardening: plant ALL 9 espeak-ng-data
                // files so the test exercises the full integrity gate
                // (not just the 4 core ones sherpa-onnx Validate checks).
                // parentFile?.mkdirs() is required for nested paths like
                // lang/sit/cmn whose parent dirs don't exist yet.
                val espeakDir = File(modelDir, TtsModelInstaller.ESPEAK_DATA_DIR)
                espeakDir.mkdirs()
                for (name in TtsModelInstaller.BUNDLED_ESPEAK_FILES) {
                    val f = File(espeakDir, name)
                    f.parentFile?.mkdirs()
                    f.writeBytes(ByteArray(16) { 0x55 })
                }
            }
        }
    }

    /**
     * Minimal in-memory AssetManager that returns canned bytes for the
     * 5 bundled asset filenames. Robolectric's stock AssetManager
     * exposes the real APK assets at `models/tts/zh/...` (from the
     * merged main+test asset paths) so we can use it directly via
     * [androidx.test.core.app.ApplicationProvider.getApplicationContext].
     */
    private fun sha256Of(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val ACOUSTIC_URL = "http://stub/model-steps-3.onnx"
        private const val VOCODER_URL = "http://stub/vocos-22khz-univ.onnx"
    }
}
