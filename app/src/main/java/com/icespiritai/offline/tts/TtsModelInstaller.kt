package com.icespiritai.offline.tts

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Install the sherpa-onnx Matcha + Vocos Chinese TTS bundle into the
 * app's private `filesDir/offline-models/zh/`. Replaces the abandoned
 * APK-download path (reverted at e319d39) — Bug 3 pivot (v0.1.60).
 *
 * **Bug 7b fix (v0.1.63) — hybrid (translate pattern):**
 * The Matcha config sherpa-onnx Validate requires
 * (`model-steps-3.onnx` + `vocos-22khz-univ.onnx` + `lexicon.txt` +
 * `tokens.txt` + `date.fst` + `number.fst` + `phone.fst`) ships
 * across two sources:
 * - **ONNX** (acoustic + vocoder, ~130 MB total): downloaded from
 *   `giteaadmin/Model` release `sherpa-onnx-matcha-zh-baker` via
 *   `sherpa-onnx-matcha-zh-baker-latest.json` descriptor (see
 *   [BuildConfig.TTS_MODEL_JSON_URL]). Falls back to
 *   [FallbackDescriptors] if the JSON fetch 404s.
 * - **Text / rule resources** (lexicon.txt / tokens.txt / 3× .fst):
 *   bundled in the APK at `assets/models/tts/zh/` (1.6 MB) and
 *   copied to `modelDir` by [copyBundledAssets] at first install.
 *   Same pattern as translate's `ModelInstaller.copyBundledAsset`.
 *   Bundling avoids putting a third hardcoded URL in the fallback
 *   table for files that rarely change.
 *
 * Resume: a sidecar `.meta` file tracks per-file `{downloadedBytes,
 * totalBytes, sha256}` and a `Range: bytes=N-` header is sent on
 * restart. SHA-256 mismatch deletes both `.partial` and `.meta`
 * (mirrors TtsEngineInstaller.revert semantics).
 *
 * Progress: a [StateFlow]<[InstallState]> mirrors the existing
 * `InstallState` enum so the picker UI can show download bytes /
 * percentage with no other wiring change.
 *
 * @param assets APK AssetManager used to read bundled text/rule files.
 *               Pass `applicationContext.assets` in production. Tests
 *               may pass a Robolectric `ApplicationProvider` assets or
 *               a stubbed AssetManager.
 */
open class TtsModelInstaller(
    private val filesDir: File,
    private val scope: CoroutineScope,
    private val jsonUrl: String,
    private val assets: AssetManager,
    private val rootDirName: String = DEFAULT_ROOT_DIR,
) : TtsInstallerLike {
    private val rootDir: File get() = File(filesDir, rootDirName)
    val modelDir: File get() = File(rootDir, "zh")

    private val state_ = MutableStateFlow<InstallState>(InstallState.Idle)
    override val state: StateFlow<InstallState> = state_.asStateFlow()

    /** Test seam: lets subclasses push install states without going through download. */
    protected open fun emitState(s: InstallState) {
        state_.value = s
    }

    /**
     * True iff ALL required files exist on disk: 2 ONNX + 5 text/rule
     * resources. The text/rule files are tiny (1.6 MB total) and ship
     * bundled in the APK (see KDoc on the class); [copyBundledAssets]
     * plants them at [modelDir] at first install. Without ALL 7 files
     * present sherpa-onnx OfflineTts config Validate fails with
     * `Rule fst '<path>' does not exist` and `generate()` segfaults
     * (Bug 7b root cause).
     */
    fun isModelInstalled(): Boolean {
        if (!File(modelDir, ACOUSTIC_MODEL_FILE).isFile) return false
        if (!File(modelDir, VOCODER_FILE).isFile) return false
        for (name in BUNDLED_ASSET_FILES) {
            if (!File(modelDir, name).isFile) return false
        }
        return true
    }

    /**
     * Trigger a full install. Idempotent: if [isModelInstalled] is
     * already true, sets state to [InstallState.Done] and returns
     * without re-downloading. Safe to call repeatedly.
     */
    override fun downloadModel() {
        scope.launch {
            if (isModelInstalled()) {
                state_.value = InstallState.Done
                return@launch
            }
            try {
                modelDir.mkdirs()
                // Text/rule assets are bundled in the APK — copy them
                // before the network step so the OfflineTts config Validate
                // finds them once the ONNX downloads finish.
                state_.value = InstallState.QueryingRelease
                copyBundledAssets()

                val descriptors = fetchDescriptors()
                for (desc in descriptors) {
                    if (File(modelDir, desc.fileName).isFile &&
                        verifySha256(File(modelDir, desc.fileName), desc.sha256)
                    ) {
                        continue
                    }
                    downloadWithResume(desc)
                    state_.value = InstallState.VerifyingSha256
                    val actual = computeSha256(File(modelDir, "${desc.fileName}.partial"))
                    if (actual != desc.sha256) {
                        File(modelDir, "${desc.fileName}.partial").delete()
                        File(modelDir, "${desc.fileName}.meta").delete()
                        state_.value = InstallState.Failed(
                            "sha256 不匹配: ${desc.fileName} 期望 ${desc.sha256.take(8)}… 实际 ${actual.take(8)}…",
                        )
                        return@launch
                    }
                    File(modelDir, "${desc.fileName}.partial").renameTo(File(modelDir, desc.fileName))
                    File(modelDir, "${desc.fileName}.meta").delete()
                }
                state_.value = InstallState.Done
            } catch (e: IOException) {
                state_.value = InstallState.Failed("下载失败:${e.message}")
            }
        }
    }

    /**
     * Copy bundled assets from `assets/models/tts/zh/<name>` to [modelDir]:
     *  - 5 top-level text/rule files (lexicon / tokens / 3× .fst) — Bug 7b
     *    hybrid path (v0.1.63).
     *  - the `espeak-ng-data/` directory tree (4 core files + cmn_dict +
     *    en_dict + lang/sit/[cmn,cmn-Latn-pinyin]) — Bug 7c root cause: sherpa-onnx Matcha
     *    Validate **requires** `phontab` + `phonindex` + `phondata` +
     *    `intonations` at `data_dir`; without them generate() segfaults
     *    on the null espeak lookup. The full 2.2 MB archive ships in
     *    the APK at `assets/models/tts/zh/espeak-ng-data/` and is
     *    recursively walked here (skip-existing, idempotent).
     *
     * Marked `protected open` so tests can override the IO dispatcher
     * (the production [Dispatchers.IO] is a real thread pool not driven
     * by `runTest` — without an override the test would hang at the
     * first suspension point and `state_` would stay at
     * `QueryingRelease`). The seam pattern matches
     * [fetchDescriptors] / [downloadWithResume].
     */
    protected open suspend fun copyBundledAssets() = withContext(Dispatchers.IO) {
        val assetBase = "models/tts/zh"
        // Top-level files (BUNDLED_ASSET_FILES).
        for (name in BUNDLED_ASSET_FILES) {
            val dest = File(modelDir, name)
            if (dest.isFile) continue
            val assetPath = "$assetBase/$name"
            val tmp = File(modelDir, "$name.part")
            try {
                assets.open(assetPath).use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            } catch (e: IOException) {
                tmp.delete()
                throw IOException("failed to copy bundled asset $assetPath: ${e.message}", e)
            }
        }
        // espeak-ng-data subdirectory (Bug 7c) — recursive walk.
        copyAssetDirectory("$assetBase/espeak-ng-data", File(modelDir, "espeak-ng-data"))
    }

    /**
     * Recursively copy an APK asset directory tree to [destDir].
     * Skips files that already exist (idempotent re-install). Uses
     * [AssetManager.list] to enumerate entries; binary blobs go through
     * the standard open/copyTo path.
     */
    private fun copyAssetDirectory(assetPath: String, destDir: File) {
        val entries = assets.list(assetPath) ?: return
        if (entries.isEmpty()) return
        destDir.mkdirs()
        for (entry in entries) {
            val childAsset = "$assetPath/$entry"
            val childDest = File(destDir, entry)
            // If it has sub-entries it's a directory; otherwise it's a file.
            val sub = assets.list(childAsset)
            if (sub != null && sub.isNotEmpty()) {
                copyAssetDirectory(childAsset, childDest)
            } else {
                if (childDest.isFile) continue
                val tmp = File(destDir, "$entry.part")
                try {
                    assets.open(childAsset).use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    if (!tmp.renameTo(childDest)) {
                        tmp.copyTo(childDest, overwrite = true)
                        tmp.delete()
                    }
                } catch (e: IOException) {
                    tmp.delete()
                    throw IOException("failed to copy bundled asset $childAsset: ${e.message}", e)
                }
            }
        }
    }

    fun cancel() {
        // Best-effort: delete partials + meta sidecars so the next run
        // starts fresh. Does NOT interrupt an in-flight download.
        modelDir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".partial") || f.name.endsWith(".meta")) f.delete()
        }
        state_.value = InstallState.Idle
    }

    protected open suspend fun fetchDescriptors(): List<FileDescriptor> = withContext(Dispatchers.IO) {
        try {
            val raw = httpGet(jsonUrl)
            val obj = JSONObject(raw)
            val modelUrl = obj.getString("modelUrl")
            val vocoderUrl = obj.getString("vocoderUrl")
            val modelSize = obj.getLong("modelSize")
            val vocoderSize = obj.getLong("vocoderSize")
            val modelSha = obj.getString("modelSha256")
            val vocoderSha = obj.getString("vocoderSha256")
            listOf(
                FileDescriptor(ACOUSTIC_MODEL_FILE, modelUrl, modelSize, modelSha),
                FileDescriptor(VOCODER_FILE, vocoderUrl, vocoderSize, vocoderSha),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "fetchDescriptors: JSON fetch failed (${t.message}); using fallback constants")
            FallbackDescriptors
        }
    }

    protected open suspend fun downloadWithResume(desc: FileDescriptor) = withContext(Dispatchers.IO) {
        val partial = File(modelDir, "${desc.fileName}.partial")
        val meta = File(modelDir, "${desc.fileName}.meta")
        val existing = readMeta(meta)
        val fromBytes = existing?.downloadedBytes ?: 0L
        val conn = (URL(desc.url).openConnection() as HttpURLConnection).apply {
            if (fromBytes > 0) setRequestProperty("Range", "bytes=$fromBytes-")
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        try {
            conn.inputStream.use { input ->
                FileOutputStream(partial, fromBytes > 0).use { output ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var total = fromBytes
                    val target = if (conn.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                        val contentRange = conn.getHeaderField("Content-Range")
                        contentRange?.substringAfter("/")?.toLongOrNull() ?: desc.sizeBytes
                    } else desc.sizeBytes
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        total += n
                        state_.value = InstallState.Downloading(total, target)
                        if (total % FSYNC_INTERVAL < BUFFER_SIZE) {
                            writeMeta(meta, Meta(total, target, desc.sha256))
                        }
                    }
                    writeMeta(meta, Meta(total, target, desc.sha256))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Test seam: production uses [java.net.URL.openConnection] which is
     * final; tests override this method to inject a fake HTTP stack.
     * Returns the raw response body as a String.
     */
    protected open fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        return try {
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    data class FileDescriptor(
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    data class Meta(val downloadedBytes: Long, val totalBytes: Long, val sha256: String)

    companion object {
        private const val TAG = "TtsModelInstaller"
        const val DEFAULT_ROOT_DIR = "offline-models"
        private const val ACOUSTIC_MODEL_FILE = "model-steps-3.onnx"
        private const val VOCODER_FILE = "vocos-22khz-univ.onnx"

        /**
         * Text/rule resource files bundled in the APK at
         * `assets/models/tts/zh/<name>` and copied to [modelDir] at
         * first install (see [copyBundledAssets]). sherpa-onnx
         * Matcha-zh-baker OfflineTts config Validate requires ALL of
         * these to be present at the same modelDir as the ONNX files;
         * missing any one of them produces `Rule fst '<path>' does
         * not exist` and `generate()` segfaults (Bug 7b).
         */
        val BUNDLED_ASSET_FILES: List<String> = listOf(
            "lexicon.txt",
            "tokens.txt",
            "phone.fst",
            "date.fst",
            "number.fst",
        )

        private const val BUFFER_SIZE = 1024 * 1024
        private const val FSYNC_INTERVAL = 5L * BUFFER_SIZE

        /**
         * Hardcoded fallback descriptors — used when the JSON descriptor
         * fetch fails (Gitea 404, network blip). URLs are the
         * `giteaadmin/Model` release download endpoints; SHA-256 verified
         * 2026-09-08 via `sha256sum` against the actual Gitea attachment
         * bytes (75,624,611 / 53,884,024 bytes). Mirror of the
         * `sherpa-onnx-matcha-zh-baker-latest.json` release asset
         * uploaded at the same time.
         */
        val FallbackDescriptors: List<FileDescriptor> = listOf(
            FileDescriptor(
                fileName = ACOUSTIC_MODEL_FILE,
                url = "http://125.211.45.14:3000/giteaadmin/Model/releases/download/sherpa-onnx-matcha-zh-baker/model-steps-3.onnx",
                sizeBytes = 75_624_611L,
                sha256 = "0e1a49219d253f7f8c2d3b3b0594505ddac9cb3c5042fb573ecfaddd341c4395",
            ),
            FileDescriptor(
                fileName = VOCODER_FILE,
                url = "http://125.211.45.14:3000/giteaadmin/Model/releases/download/sherpa-onnx-matcha-zh-baker/vocos-22khz-univ.onnx",
                sizeBytes = 53_884_024L,
                sha256 = "0574a135aa1db2de6e181050db2ec528496cacd4a4701fc5d7faf9f9804c0081",
            ),
        )

        fun writeMeta(file: File, meta: Meta) {
            file.writeText(
                """{"downloadedBytes":${meta.downloadedBytes},"totalBytes":${meta.totalBytes},"sha256":"${meta.sha256}"}""",
            )
        }

        fun readMeta(file: File): Meta? = if (!file.exists()) null else try {
            val obj = JSONObject(file.readText())
            Meta(obj.getLong("downloadedBytes"), obj.getLong("totalBytes"), obj.getString("sha256"))
        } catch (e: Exception) {
            null
        }

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun verifySha256(file: File, expected: String): Boolean {
            if (!file.isFile) return false
            return computeSha256(file) == expected
        }
    }
}

/** Minimal shim so [TtsModelInstaller] can log without importing android.util.Log at the top. */
private object Log {
    fun w(tag: String, msg: String) = android.util.Log.w(tag, msg)
}
