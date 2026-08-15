package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {

    @Before
    fun resetDataStore() {
        runBlocking {
            ApplicationProvider.getApplicationContext<Context>().dataStore.edit { it.clear() }
        }
    }

    @Test
    fun `default themeMode is SYSTEM`() = runTest {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun `setThemeMode persists then reads back across instances`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SettingsRepository(context).setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, SettingsRepository(context).themeMode.first())
    }
}
