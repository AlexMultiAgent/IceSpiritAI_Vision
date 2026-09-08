package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryDisclaimerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun tearDown() {
        // DataStore Preferences 用独立 preferences_pb 文件,不是 SharedPreferences —
        // 必须走 dataStore.edit { it.clear() } 才能真正重置,SharedPreferences.clear() 无效。
        runBlocking {
            context.dataStore.edit { it.clear() }
        }
    }

    @Test fun `disclaimerAcceptedAt is null on fresh install`() = runTest {
        val repo = SettingsRepository(context)
        assertNull(repo.disclaimerAcceptedAt.first())
    }

    @Test fun `acceptDisclaimer persists timestamp`() = runTest {
        val repo = SettingsRepository(context)
        repo.acceptDisclaimer()
        val ts = repo.disclaimerAcceptedAt.first()
        assertNotNull(ts)
        assert(ts!! > 0L)
    }
}