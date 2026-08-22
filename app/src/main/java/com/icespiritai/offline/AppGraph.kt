package com.icespiritai.offline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.icespiritai.offline.updater.DownloadStateStore

/**
 * Process-wide singleton for shared DataStore-backed stores. Keeps the DataStore
 * instance alive across Activity recreation and Service / Worker processes.
 *
 * File `update_state.preferences_pb` lives in the app's `datastore/` dir.
 */
object AppGraph {
    @Volatile private var storeInstance: DataStore<Preferences>? = null

    @Synchronized
    fun dataStore(context: Context): DataStore<Preferences> {
        return storeInstance ?: PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("update_state") }
        ).also { storeInstance = it }
    }

    fun downloadStateStore(context: Context): DownloadStateStore =
        DownloadStateStore(dataStore(context))
}
