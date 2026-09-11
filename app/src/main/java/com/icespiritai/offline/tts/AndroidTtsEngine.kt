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
 *   (init() 阶段对当前 defaultEngine 已 probe 一次,缓存 primaryEnginePackage +
 *   primarySupportsChinese;supportedChineseEngines() 对匹配的 engine 返回真实
 *   probe 结果,对其他 engines 走 substring 兜底(filter 仅供 picker UI 显示,
 *   init 时不会落到它们上))
 * - 永不降级到英文:setLanguage < LANG_AVAILABLE 直接 InitFailed
 */
class AndroidTtsEngine(private val context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null

    // Cache the engine we just init'd + whether its locale probe succeeded.
    // supportedChineseEngines() uses these for the matching engine so the
    // picker list reflects what we actually verified, not a substring guess.
    // (nova 6 Honor voiceengine `com.hihonor.voiceengine` was missed by the
    // old substring table, causing 真机 InitTest "no chinese-capable engine"
    // to fail. Real-device verification in AndroidTtsEngineInitTest.)
    private var primaryEnginePackage: String? = null
    private var primarySupportsChinese: Boolean = false

    // v0.3.0: per-utterance onStart forwarder. TtsController multi-segment
    // speak hooks this so the UI can scroll to the matching hit card when
    // the engine actually starts playing that segment.
    override var onUtteranceStart: ((String) -> Unit)? = null

    // v0.3.0: single field → Map keyed by utteranceId. Multi-segment
    // speak() calls no longer overwrite each other's callbacks; each
    // segment's onDone fires on its own onStart/onDone cycle.
    private val pendingOnDone = mutableMapOf<String, (String) -> Unit>()

    override fun init(onDone: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeOk = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)?.let {
                    it >= TextToSpeech.LANG_AVAILABLE
                } ?: false
                // Cache for supportedChineseEngines() — see class KDoc.
                primaryEnginePackage = tts?.defaultEngine
                primarySupportsChinese = localeOk
                if (localeOk) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            utteranceId?.let { uid -> onUtteranceStart?.invoke(uid) }
                        }
                        override fun onDone(utteranceId: String?) {
                            val uid = utteranceId ?: ""
                            pendingOnDone.remove(uid)?.invoke(uid)
                        }
                        @Deprecated("required override")
                        override fun onError(utteranceId: String?) {
                            val uid = utteranceId ?: ""
                            pendingOnDone.remove(uid)?.invoke(uid)
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

    override fun speak(text: String, utteranceId: String, onDone: (String) -> Unit) {
        val engine = tts ?: return
        // v0.3.0: register under utteranceId so concurrent/queued segments
        // don't clobber each other's onDone. QUEUE_ADD lets segments pile
        // up; the controller's first speak() pre-calls stop() if a flush
        // is desired, so we don't need QUEUE_FLUSH here.
        pendingOnDone[utteranceId] = onDone
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
        // v0.3.0: clearing the Map also fires no callbacks — Android's
        // tts.stop() invokes onError for in-flight utterances, but the
        // controller's state machine treats stop() as a synchronous
        // Idle transition and doesn't expect onDone callbacks after
        // stop() (matches AndroidTtsEngine v0.1.x semantics + Bug 3
        // pivot sherpa interrupt semantics).
        pendingOnDone.clear()
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking == true

    override fun supportedChineseEngines(): List<EngineInfo> {
        val engine = tts ?: return emptyList()
        return engine.engines?.map { info ->
            // For the engine we just init'd, return the real probe result
            // (setLanguage(zh-CN) >= LANG_AVAILABLE) so the picker reflects
            // what's actually wired. For other engines, fall back to a
            // substring table — they only appear in the picker UI, init()
            // does not bind to them until the user picks + re-inits.
            EngineInfo(
                packageName = info.name,
                label = info.label?.toString() ?: info.name,
                supportsChinese = if (info.name == primaryEnginePackage) {
                    primarySupportsChinese
                } else {
                    info.name.contains("hivoice", ignoreCase = true)
                        || info.name.contains("hihonor", ignoreCase = true)
                        || info.name.contains("google", ignoreCase = true)
                        || info.name.contains("samsung", ignoreCase = true)
                        || info.name.contains("xiaomi", ignoreCase = true)
                        || info.name.contains("icespiritai", ignoreCase = true)
                },
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