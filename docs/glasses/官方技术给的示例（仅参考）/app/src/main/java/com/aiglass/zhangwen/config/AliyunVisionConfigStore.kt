package com.aiglass.zhangwen.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

data class AliyunVisionConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val timeoutSec: Int = 60,
    val maxTokens: Int = 256
) {
    fun isReady(): Boolean = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}

object AliyunVisionConfigStore {
    private const val PREFS = "aliyun_vision_secure"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_TIMEOUT = "timeout_sec"
    private const val KEY_MAX_TOKENS = "max_tokens"

    /**
     * 百炼控制台（北京区体验页，仅供查阅）：
     * https://bailian.console.aliyun.com/cn-beijing/model/experience/text
     *
     * 实际调用地址为业务空间 OpenAI 兼容 Chat Completions。
     */
    const val BAILIAN_CONSOLE_URL =
        "https://bailian.console.aliyun.com/cn-beijing/model/experience/text"

    /** 对应 Nacos vision-model.yml → providers.bailian.endpoint */
    const val PLACEHOLDER_BASE_URL =
        "https://ws-ri9dlablk1xgy0ea.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"

    /** 视觉模型名须小写 */
    const val PLACEHOLDER_MODEL = "qwen3-vl-flash"

    const val PLACEHOLDER_API_KEY =
        "sk-ws-H.EDRYXLX.apaE.MEUCIAbkz1OSnka_TW6EZMKnqwZFvcd7ykBZdiYSMQtL0mvOAiEAkVh5a00yABHvOzykhRk6OS0aV8nntdGiR9R2T_JS0II"

    fun exampleBailianConfig(): AliyunVisionConfig = AliyunVisionConfig(
        baseUrl = PLACEHOLDER_BASE_URL,
        apiKey = PLACEHOLDER_API_KEY,
        model = PLACEHOLDER_MODEL,
        timeoutSec = 60,
        maxTokens = 256
    )

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(context: Context): AliyunVisionConfig {
        val p = prefs(context)
        return AliyunVisionConfig(
            baseUrl = p.getString(KEY_BASE_URL, "") ?: "",
            apiKey = p.getString(KEY_API_KEY, "") ?: "",
            model = (p.getString(KEY_MODEL, "") ?: "").trim(),
            timeoutSec = p.getInt(KEY_TIMEOUT, 60),
            maxTokens = p.getInt(KEY_MAX_TOKENS, 256)
        )
    }

    /** 同步写入，保证点「保存」后立刻可用于识物请求 */
    fun save(context: Context, config: AliyunVisionConfig): Boolean {
        val normalized = config.copy(
            baseUrl = config.baseUrl.trim(),
            apiKey = config.apiKey.trim(),
            model = config.model.trim().lowercase()
        )
        return prefs(context).edit()
            .putString(KEY_BASE_URL, normalized.baseUrl)
            .putString(KEY_API_KEY, normalized.apiKey)
            .putString(KEY_MODEL, normalized.model)
            .putInt(KEY_TIMEOUT, normalized.timeoutSec.coerceIn(10, 180))
            .putInt(KEY_MAX_TOKENS, normalized.maxTokens.coerceIn(32, 2048))
            .commit()
    }
}
