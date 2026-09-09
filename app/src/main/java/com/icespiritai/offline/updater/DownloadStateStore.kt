package com.icespiritai.offline.updater

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class DownloadRecord(
    val downloadId: String,
    val url: String,
    val destPath: String,
    val bytesWritten: Long,
    val totalBytes: Long,
    val etag: String?,
    val signerCertSha256: String,
    val stage: DownloadStage,
    val versionName: String,
    val startedAtEpochMs: Long,
) {
    enum class DownloadStage { Downloading, VerifyingSignature, ReadyToInstall }
}

/**
 * Wrapper around [DataStore] for resumable APK downloads. Each `store.data`
 * reader must wrap with `.catch { ... emit(emptyPreferences()) }` —
 * see [SettingsRepository] KDoc; same root cause (corrupt preferences file
 * would crash the next read on cold start).
 */
class DownloadStateStore(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(id: String) = stringPreferencesKey("dl_$id")

    suspend fun upsert(record: DownloadRecord) {
        val encoded = json.encodeToString(record)
        store.edit { it[key(record.downloadId)] = encoded }
    }

    suspend fun get(downloadId: String): DownloadRecord? {
        val raw = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[key(downloadId)] }
            .first() ?: return null
        return runCatching { json.decodeFromString<DownloadRecord>(raw) }.getOrNull()
    }

    suspend fun delete(downloadId: String) {
        store.edit { it.remove(key(downloadId)) }
    }

    suspend fun all(): List<DownloadRecord> {
        val prefs = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return prefs.asMap().entries.mapNotNull { (_, v) ->
            val s = v as? String ?: return@mapNotNull null
            runCatching { json.decodeFromString<DownloadRecord>(s) }.getOrNull()
        }
    }
}
