package com.icespiritai.offline.updater.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.icespiritai.offline.updater.ApkSignatureVerifier
import com.icespiritai.offline.updater.DownloadRecord
import com.icespiritai.offline.updater.DownloadStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateResumeCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { tmp.root.resolve("test.preferences_pb") },
    )

    /**
     * Partial-download record whose on-disk size does NOT match its claimed
     * [DownloadRecord.bytesWritten]. Coordinator must treat this as stale,
     * delete the partial file, and remove the record from [DownloadStateStore].
     */
    @Test fun stale_record_size_mismatch_cleans_up() = runBlocking {
        val partial = tmp.newFile("partial.apk")
        partial.writeBytes(ByteArray(50) { 1 })  // file: 50 bytes on disk
        val stateStore = DownloadStateStore(newStore())
        stateStore.upsert(
            DownloadRecord(
                downloadId = "d2", url = "http://x", destPath = partial.absolutePath,
                bytesWritten = 100, totalBytes = 1000, etag = null,  // record: claims 100
                signerCertSha256 = "c",
                stage = DownloadRecord.DownloadStage.Downloading,
                versionName = "v0.2.0", startedAtEpochMs = 0L,
            )
        )
        val coord = UpdateResumeCoordinator(
            stateStore = stateStore,
            // SAM conversion of ApkSignatureVerifier's `verify(File, String): VerifierResult`
            // method into the fun-interface. Not actually invoked by this test.
            verifier = ApkSignatureVerifier::verify,
            verifierResultSink = { /* no-op */ },
            resumeWorkerLauncher = { /* no-op */ },
            readyToInstallSink = { _, _ -> /* no-op */ },
            failedSink = { /* no-op */ },
        )
        coord.scanAndDispatch()
        assertNull(stateStore.get("d2"))
        assertFalse(partial.exists())
    }

    /**
     * A record at the [DownloadRecord.DownloadStage.ReadyToInstall] stage whose
     * on-disk file size exactly matches [DownloadRecord.totalBytes] must be
     * surfaced to the install-prompt sink with the correct File + versionName.
     */
    @Test fun ready_to_install_with_full_file_sinks_state() = runBlocking {
        val apk = tmp.newFile("done.apk")
        apk.writeBytes(ByteArray(1000) { 1 })
        val stateStore = DownloadStateStore(newStore())
        stateStore.upsert(
            DownloadRecord(
                downloadId = "d3", url = "http://x", destPath = apk.absolutePath,
                bytesWritten = 1000, totalBytes = 1000, etag = null,
                signerCertSha256 = "c",
                stage = DownloadRecord.DownloadStage.ReadyToInstall,
                versionName = "v0.2.0", startedAtEpochMs = 0L,
            )
        )
        var sinkFile: File? = null
        var sinkVersion: String? = null
        val coord = UpdateResumeCoordinator(
            stateStore = stateStore,
            verifier = ApkSignatureVerifier::verify,
            verifierResultSink = { /* no-op */ },
            resumeWorkerLauncher = { /* no-op */ },
            readyToInstallSink = { f, v -> sinkFile = f; sinkVersion = v },
            failedSink = { /* no-op */ },
        )
        coord.scanAndDispatch()
        assertEquals(apk, sinkFile)
        assertEquals("v0.2.0", sinkVersion)
    }
}