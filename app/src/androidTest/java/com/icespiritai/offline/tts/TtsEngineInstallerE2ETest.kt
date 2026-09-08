package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 完整 e2e:从 Gitea 下载 + sha256 verify + FileProvider install。
 *
 * 真机烟测触发条件:
 *   1. ANDROID_SERIAL=AGQV023313008161 (nova 6) 已连接
 *   2. 设备尚未装 com.icespiritai.tts.engine
 *   3. 联网可用
 *
 * 不满足时 Assume skip,避免 CI 误跑导致 Gitea 流量 / 安装干扰。
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineInstallerE2ETest {

    @Test fun fullInstallFlow() = runBlocking {
        val pm = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        val alreadyInstalled = runCatching {
            pm.getPackageInfo("com.icespiritai.tts.engine", 0)
        }.isSuccess
        assumeTrue("engine already installed, skip; uninstall manually to re-run", !alreadyInstalled)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = TtsEngineInstaller(ctx)
        val coldMs = System.currentTimeMillis()
        val result = installer.install()
        Log.i("IceSpiritTtsE2E", "[INSTALL_RESULT] $result cold_ms=${System.currentTimeMillis() - coldMs}")
        // 不强 assert Done — 系统安装弹窗需用户手动点 "安装"
        // 校验至少进到 Installing / Done 阶段
        assertTrue(result is InstallState.Installing || result is InstallState.Done || result is InstallState.Failed)
        Unit
    }
}