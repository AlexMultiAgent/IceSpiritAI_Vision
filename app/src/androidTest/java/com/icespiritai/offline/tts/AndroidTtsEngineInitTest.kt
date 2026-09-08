package com.icespiritai.offline.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTtsEngineInitTest {

    @Test fun engineInitSucceedsAndSupportsChinese() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = AndroidTtsEngine(ctx)
        try {
            val success = withTimeout(5_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    engine.init { ok -> cont.resumeWith(Result.success(ok)) }
                }
            }
            assertTrue("engine init failed", success)
            val engines = engine.supportedChineseEngines()
            Log.i("IceSpiritTtsE2E", "[INIT_OK] engines=${engines.map { it.packageName }}")
            // nova 6 (Honor AI Voice) 是默认引擎;至少 1 个 Chinese-capable
            assertTrue("no chinese-capable engine", engines.any { it.supportsChinese })
        } finally {
            engine.release()
        }
        Unit
    }
}