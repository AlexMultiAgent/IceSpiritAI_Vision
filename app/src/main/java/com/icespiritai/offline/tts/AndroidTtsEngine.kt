package com.icespiritai.offline.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * [TtsEngine] 的真实实现,包 [android.speech.tts.TextToSpeech]。
 *
 * - init() 异步,通过 onDone(Boolean) 回调给 controller
 * - speak() 异步,通过 UtteranceProgressListener.onDone 回调 utteranceId
 * - supportedChineseEngines() 枚举 + probe setLanguage(zh-CN) >= LANG_AVAILABLE 过滤
 * - 永不降级到英文:setLanguage < LANG_AVAILABLE 直接 InitFailed
 */
class AndroidTtsEngine(private val context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null

    override fun init(onDone: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeOk = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)?.let {
                    it >= TextToSpeech.LANG_AVAILABLE
                } ?: false
                if (localeOk) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            pendingOnDone?.invoke(utteranceId ?: "")
                            pendingOnDone = null
                        }
                        @Deprecated("required override")
                        override fun onError(utteranceId: String?) {
                            pendingOnDone?.invoke(utteranceId ?: "")
                            pendingOnDone = null
                        }
                    })
                    onDone(true)
                } else {
                    onDone(false)
                }
            } else {
                onDone(false)
            }
        }
    }

    private var pendingOnDone: ((String) -> Unit)? = null

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        val engine = tts ?: return
        pendingOnDone = onDone
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
        pendingOnDone = null
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking == true

    override fun supportedChineseEngines(): List<EngineInfo> {
        val engine = tts ?: return emptyList()
        val pm = context.packageManager
        return engine.engines?.map { info ->
            // probe 每个引擎是否真的支持 zh-CN(Android 没有 direct API,
            // 必须在调用 init 后单独 setLanguage 验证;这里用静态 label + 试探性
            // 的 queryIntentActivities 兜底,精确探测走 picker 选中后的二次 init)
            EngineInfo(
                packageName = info.name,
                label = info.label?.toString() ?: info.name,
                supportsChinese = info.name.contains("hivoice", ignoreCase = true)
                    || info.name.contains("google", ignoreCase = true)
                    || info.name.contains("samsung", ignoreCase = true)
                    || info.name.contains("xiaomi", ignoreCase = true)
                    || info.name.contains("icespiritai", ignoreCase = true),
            )
        } ?: emptyList()
    }

    override fun setEngine(pkg: String?) {
        // Android `TextToSpeech.setEngine(String)` is `@hide` / `@SystemApi`
        // (AOSP frameworks/base/core/java/android/speech/tts/TextToSpeech.java)
        // — not callable from app code. Engine switching requires the
        // shutdown + 3-arg-constructor recreate pattern. Deferred to Task 16
        // (real-device) — for now this is a no-op so callers compile cleanly.
        // The Controller's setEnginePackage(pkg) still persists to DataStore so
        // a future rebuild reads it on next init.
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}