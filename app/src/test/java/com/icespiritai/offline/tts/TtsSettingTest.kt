package com.icespiritai.offline.tts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TtsSetting + TtsSettingRepository 端到端契约 — DataStore 持久化层验证。
 *
 * - 默认值:enabled=true / enginePackage=null(跟随系统首选)
 * - 跨实例持久化:写一次,新实例读出同一值
 * - null ↔ 非 null enginePackage 切换:写包名,清空写回 null
 *
 * 走独立 prefs 文件 `tts_settings.preferences_pb`(见 TtsSettingRepository 注释),
 * 与 `settings.preferences_pb`(主题模式)互不污染,便于用户卸载冰灵 TTS 后一键重置。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsSettingTest {

    @Before
    fun resetTtsDataStore() {
        runBlocking {
            ApplicationProvider.getApplicationContext<Context>()
                .ttsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun `default setting is enabled true and enginePackage null`() = runTest {
        val repo = TtsSettingRepository(ApplicationProvider.getApplicationContext())
        val setting = repo.setting.first()
        assertEquals(true, setting.enabled)
        assertNull(setting.enginePackage)
    }

    @Test
    fun `setEnabled false persists across repository instances`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TtsSettingRepository(context).setEnabled(false)
        val setting = TtsSettingRepository(context).setting.first()
        assertEquals(false, setting.enabled)
        assertNull(setting.enginePackage)
    }

    @Test
    fun `setEnginePackage round-trips null and non-null values`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = TtsSettingRepository(context)

        // null → null (初始)
        assertNull(repo.setting.first().enginePackage)

        // 非 null:写包名,读回
        repo.setEnginePackage("com.icespiritai.tts.engine")
        assertEquals(
            "com.icespiritai.tts.engine",
            repo.setting.first().enginePackage,
        )

        // 跨实例:重新构造,仍读出包名
        assertEquals(
            "com.icespiritai.tts.engine",
            TtsSettingRepository(context).setting.first().enginePackage,
        )

        // null 写回:清空,读回 null
        repo.setEnginePackage(null)
        assertNull(repo.setting.first().enginePackage)
    }
}
