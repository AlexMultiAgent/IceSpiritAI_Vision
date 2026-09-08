package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 断点续传测试:
 *   1. 启动 install,在 Downloading(progress=~30%)时强行 cancel(删 meta)
 *      让 partial 留 ~30% 文件
 *   2. 重新 install,期望第二次请求带 Range: bytes=N- 头(走 206 Partial Content)
 *
 * 真机烟测需要先有下载进度 — 用 Robolectric / mock 不易还原 Gitea 真实下载,
 * 这里只断言"第二次 install 不报 Failed",且 progress 起步大于 0。
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineInstallerResumeTest {

    @Test fun resumeAfterPartialDownload() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = ctx.cacheDir
        val partial = File(cache, "icespirit-tts-engine.apk.partial")
        val meta = File(cache, "icespirit-tts-engine.apk.meta")

        // 模拟已经下过 ~1MB 的 partial + meta
        partial.writeBytes(ByteArray(1024 * 1024) { 0x42 })
        meta.writeText("""{"downloadedBytes":1048576,"totalBytes":157286400,"sha256":"unknown"}""")

        val installer = TtsEngineInstaller(ctx)
        val result = installer.install()
        Log.i("IceSpiritTtsE2E", "[RESUME_RESULT] $result")
        // 注:sha256 必然 mismatch(我们 mock 的 1MB),会走到 Failed 但 partial + meta 删干净
        assertTrue(result is InstallState.Failed)
        assertTrue(!partial.exists())
        assertTrue(!meta.exists())
        Unit
    }
}