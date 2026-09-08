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
class AndroidTtsEngineSpeakTest {

    @Test fun speakCompletesWithoutException() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = AndroidTtsEngine(ctx)
        try {
            // 先 init
            withTimeout(5_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    engine.init { ok -> if (ok) cont.resumeWith(Result.success(Unit)) else cont.resumeWith(Result.failure(IllegalStateException("init fail"))) }
                }
            }
            // speak 异步,等 onDone 触发
            val coldMs = System.currentTimeMillis()
            withTimeout(15_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    engine.speak("测试中文朗读", "test_1") { _ ->
                        Log.i("IceSpiritTtsE2E", "[SPEAK_DONE] cold_to_done_ms=${System.currentTimeMillis() - coldMs}")
                        cont.resumeWith(Result.success(Unit))
                    }
                }
            }
            assertTrue(true)
        } finally {
            engine.release()
        }
        Unit
    }
}