package com.icespiritai.offline.tts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 独立 prefs 文件 `tts_settings.preferences_pb`,与 `SettingsRepository` 的
 * `settings.preferences_pb`(主题模式)互不污染。
 *
 * 为什么分开存:
 * 1. 主题模式 vs TTS 引擎是两个不相关的设置域,合在一起一旦 DataStore 损坏
 *    会同时丢失;
 * 2. TTS 设置的生命周期与外部 `icespirit-tts-engine` APK 的安装 / 卸载强相关
 *    — 用户卸载冰灵 TTS 后,我们允许通过「清空 tts 域 DataStore」一键回到默认
 *    状态(保留主题模式),分开存是前提;
 * 3. 测试隔离:[TtsSettingTest] 的 `@Before resetTtsDataStore()` 只清 tts 域,
 *    不影响其他测试套件的 settings 域。
 *
 * 命名规范:沿用 `SettingsRepository.kt` `internal val Context.dataStore` 形式
 * 但加 `tts` 前缀避免重名冲突。
 */
internal val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tts_settings",
)

/**
 * DataStore-backed TTS 设置仓库。
 *
 * - [setting]: cold Flow,每次订阅都会重新 emit 当前快照(从 Preferences 读出)
 * - [setEnabled] / [setEnginePackage]:写入后 [setting] 自动重新发射,UI 通过
 *   collect 拿到最新值(spec §5.3 写时 `dataStore.edit { it[KEY_ENABLED] = b }`,
 *   读时 `dataStore.data.map { TtsSetting(...) }`)。
 *
 * 损坏 fallback 到 `TtsSetting(enabled=true, enginePackage=null)` + 由
 * DataStore 自身 Preferences 层 IOException 抛出 + 调用方 Logcat warn
 * (见 spec §10 「DataStore 损坏」行)。
 */
class TtsSettingRepository(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("tts_enabled")
    private val enginePackageKey = stringPreferencesKey("tts_engine_package")

    val setting: Flow<TtsSetting> =
        context.ttsDataStore.data.map { prefs ->
            TtsSetting(
                enabled = prefs[enabledKey] ?: true,
                enginePackage = prefs[enginePackageKey],
            )
        }

    suspend fun setEnabled(b: Boolean) {
        context.ttsDataStore.edit { prefs ->
            prefs[enabledKey] = b
        }
    }

    suspend fun setEnginePackage(pkg: String?) {
        context.ttsDataStore.edit { prefs ->
            if (pkg == null) {
                prefs.remove(enginePackageKey)
            } else {
                prefs[enginePackageKey] = pkg
            }
        }
    }
}
