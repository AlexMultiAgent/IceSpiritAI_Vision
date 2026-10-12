package com.icespiritai.offline.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.licenseDataStore: DataStore<Preferences> by preferencesDataStore(name = "license")

class LicenseStore(private val context: Context) {

    private val store: DataStore<Preferences> get() = context.licenseDataStore

    fun observeCode(): Flow<String?> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_CODE] }

    suspend fun setCode(code: String) {
        store.edit { it[KEY_CODE] = code }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY_CODE) }
    }

    companion object {
        private val KEY_CODE = stringPreferencesKey("license_code")
    }
}
