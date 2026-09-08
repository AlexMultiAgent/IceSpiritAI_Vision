package com.icespiritai.offline.tts

/**
 * 用户对识别结果语音朗读的偏好设置。
 *
 * - [enabled] = false 时,HomeTopBar 朗读按钮不渲染(spec §6.1 — setting.enabled=false
 *   行为),用户可完全关掉此功能(对应 `feedback-release-hygiene` 精神 — 不希望某功能的用户
 *   可关闭它)。
 * - [enginePackage] = null 时,Android `TextToSpeech` 自动按用户在系统设置里选定的
 *   「首选引擎」路由(华为 nova 6 = 荣耀 AI 语音引擎 / Pixel = Google TTS / 三星 =
 *   Samsung TTS 等);非 null 时强制绑定到该包名,卸载后自动 fallback 到 null 并 Logcat
 *   warn(spec §10 「Setting saved enginePackage 但引擎被卸」)。
 *
 * 默认值 [enabled]=true(默认开启,符合 spec §3.5 "总开关对应 release-hygiene")、
 * [enginePackage]=null(跟随系统首选,绝大多数用户最自然的选择)。
 */
data class TtsSetting(
    val enabled: Boolean = true,
    val enginePackage: String? = null,
)
