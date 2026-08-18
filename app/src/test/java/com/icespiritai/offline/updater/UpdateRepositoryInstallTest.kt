package com.icespiritai.offline.updater

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateRepositoryInstallTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun assumeNotWindows() {
            Assume.assumeTrue(
                "Skipped on Windows: AndroidX FileProvider path-separator bug — see issuetracker.google.com/issues/79845",
                !System.getProperty("os.name").lowercase().startsWith("windows")
            )
        }
    }

    @Test
    fun requestInstall_buildsActionViewIntent_withFileProviderUri() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outDir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(outDir, "icespiritai-vision.apk").apply { writeBytes(byteArrayOf(1)) }

        val intent = UpdateRepository.buildInstallIntent(context, file)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        val data: Uri = intent.data!!
        assertEquals(context.packageName + ".fileprovider", data.authority)
        assertTrue("FLAG_GRANT_READ_URI_PERMISSION must be set",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}