package com.icespiritai.offline.updater

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStateStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeStore(): Pair<DownloadStateStore, DataStore<Preferences>> {
        val store = PreferenceDataStoreFactory.create(
            produceFile = { tmp.root.resolve("test.preferences_pb") },
        )
        return DownloadStateStore(store) to store
    }

    @Test
    fun upsert_then_get_round_trips() = runBlocking {
        val (repo, _) = makeStore()
        val r = DownloadRecord(
            downloadId = "abc", url = "http://x", destPath = "/tmp/x.apk",
            bytesWritten = 100, totalBytes = 1000, etag = "v1",
            signerCertSha256 = "deadbeef", stage = DownloadRecord.DownloadStage.Downloading,
            versionName = "v0.2.0", startedAtEpochMs = 1L,
        )
        repo.upsert(r)
        val got = repo.get("abc")
        assertEquals(r, got)
    }

    @Test
    fun delete_removes_record() = runBlocking {
        val (repo, _) = makeStore()
        repo.upsert(
            DownloadRecord(
                "a", "u", "/p", 0, 0, null, "c",
                DownloadRecord.DownloadStage.Downloading, "v", 0L,
            ),
        )
        repo.delete("a")
        assertNull(repo.get("a"))
    }

    @Test
    fun all_returns_all_records() = runBlocking {
        val (repo, _) = makeStore()
        repo.upsert(
            DownloadRecord(
                "a", "u1", "/p", 0, 0, null, "c",
                DownloadRecord.DownloadStage.Downloading, "v", 0L,
            ),
        )
        repo.upsert(
            DownloadRecord(
                "b", "u2", "/p", 0, 0, null, "c",
                DownloadRecord.DownloadStage.ReadyToInstall, "v", 0L,
            ),
        )
        assertEquals(2, repo.all().size)
    }
}
