package com.aiglass.zhangwen.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsStore by preferencesDataStore("gov_app_settings")

object AppSettingsStore {
    private val KEY_PROMPT = stringPreferencesKey("identify_system_prompt")
    private val KEY_SPLASH_URI = stringPreferencesKey("splash_image_uri")

    val DEFAULT_IDENTIFY_PROMPT =
        "请用简体中文，用口语描述图中主要物体与场景，一两句话，最多80字。" +
            "勿用Markdown、列表或标题。"

    fun identifyPromptFlow(context: Context): Flow<String> =
        context.appSettingsStore.data.map { prefs ->
            prefs[KEY_PROMPT]?.takeIf { it.isNotBlank() } ?: DEFAULT_IDENTIFY_PROMPT
        }

    suspend fun setIdentifyPrompt(context: Context, prompt: String) {
        context.appSettingsStore.edit { it[KEY_PROMPT] = prompt }
    }

    suspend fun resetIdentifyPrompt(context: Context) {
        context.appSettingsStore.edit { it[KEY_PROMPT] = DEFAULT_IDENTIFY_PROMPT }
    }

    fun splashImageUriFlow(context: Context): Flow<String?> =
        context.appSettingsStore.data.map { prefs ->
            prefs[KEY_SPLASH_URI]?.takeIf { it.isNotBlank() }
        }

    suspend fun setSplashImageUri(context: Context, uri: String?) {
        context.appSettingsStore.edit {
            if (uri.isNullOrBlank()) it.remove(KEY_SPLASH_URI) else it[KEY_SPLASH_URI] = uri
        }
    }
}
