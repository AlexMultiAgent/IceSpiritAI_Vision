package com.icespiritai.offline.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.ui.home.RuleTab
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

    @Test
    fun `visibleFeatures defaults to all tabs when key missing`() = runTest {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals(RuleTab.entries.toSet(), repo.visibleFeatures.first())
    }

    @Test
    fun `visibleFeatures roundtrips via setVisibleFeatures`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)
        repo.setVisibleFeatures(setOf(RuleTab.AdSignage))
        assertEquals(
            setOf(RuleTab.AdSignage),
            SettingsRepository(context).visibleFeatures.first(),
        )
    }

    @Test
    fun `visibleFeatures falls back to all tabs when persisted enum name is stale`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { prefs ->
            prefs[stringSetPreferencesKey("visible_features")] = setOf("OldTabName")
        }
        val repo = SettingsRepository(context)
        assertEquals(RuleTab.entries.toSet(), repo.visibleFeatures.first())
    }
}
