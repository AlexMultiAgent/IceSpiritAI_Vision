package com.icespiritai.offline.updater.service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.updater.DownloadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateDownloadNotifierTest {

    private fun makeNotifier() = UpdateDownloadNotifier(ApplicationProvider.getApplicationContext())

    @Test fun progress_notification_has_running_flag_and_cancel_action() {
        val n = makeNotifier()
        val record = DownloadRecord(
            "d1", "http://x", "/p", 500L, 1000L, null, "c",
            DownloadRecord.DownloadStage.Downloading, "v0.2.0", 0L,
        )
        val notification = n.buildProgressNotification(record, written = 500L)
        assertNotNull(notification)
        val ongoingFlag = NotificationCompat.FLAG_ONGOING_EVENT
        assertEquals(ongoingFlag, notification.flags and ongoingFlag)
        val cancelTitle = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.update_cancel)
        assertTrue(notification.actions.any { it.title.toString() == cancelTitle })
    }

    @Test fun ready_notification_has_install_and_later_actions() {
        val n = makeNotifier()
        val record = DownloadRecord(
            "d2", "http://x", "/p", 1000L, 1000L, null, "c",
            DownloadRecord.DownloadStage.ReadyToInstall, "v0.2.0", 0L,
        )
        val notification = n.buildReadyNotification(record, versionName = "v0.2.0")
        assertNotNull(notification)
        assertEquals(2, notification.actions.size)
        assertEquals(0, notification.flags and NotificationCompat.FLAG_ONGOING_EVENT)
    }
}
