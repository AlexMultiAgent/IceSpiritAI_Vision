package com.icespiritai.offline.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.ViolationReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ExportAction {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val EVIDENCE_DIR = "evidence"
    private const val TAG = "ExportAction"

    /**
     * Build the evidence ZIP, write it to cacheDir, and dispatch the share
     * chooser. Returns the launched [Job] so callers can await completion
     * or attach error handlers.
     *
     * The ContentResolver read (image bytes) + [EvidencePackageBuilder.toFile]
     * (zip build) + [File.writeBytes] (disk write) are dispatched to
     * [ioDispatcher] (default [Dispatchers.IO]) — for an 8 MB image this is
     * 50-200 ms of work that would otherwise block the main thread when
     * [share] is invoked from `CaptureBar`'s `onClick`. The Toast +
     * `startActivity` are bounced to [Dispatchers.Main] (Toast queueing and
     * Activity dispatch must be on the main thread per Android framework
     * contract).
     *
     * Caller-supplied [ioScope] is used so the lifecycle is tied to the
     * caller's scope (e.g. `rememberCoroutineScope()` in Compose). We do
     * NOT create an internal scope here — a top-level supervisor would
     * leak beyond the screen that triggered the export.
     *
     * The [ioDispatcher] parameter is exposed for tests; production callers
     * should rely on the default. Injecting a `TestDispatcher` lets unit
     * tests drive the entire share flow deterministically without
     * contending with the JVM's real IO pool.
     */
    fun share(
        context: Context,
        report: ViolationReport,
        appVersion: String,
        ioScope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Job = ioScope.launch(ioDispatcher) {
        val bytes: ByteArray
        try {
            bytes = EvidencePackageBuilder.toFile(
                report = report,
                imageProvider = ImageBytesProvider.from(context),
                appVersion = appVersion,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build evidence package", t)
            showFailureToast(context)
            return@launch
        }

        val dir = File(context.cacheDir, EVIDENCE_DIR).apply { mkdirs() }
        val file = File(dir, "evidence_${report.timestampMs}.zip")
        try {
            file.writeBytes(bytes)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write evidence zip", t)
            showFailureToast(context)
            return@launch
        }

        withContext(Dispatchers.Main) {
            val authority = context.packageName + FILE_PROVIDER_SUFFIX
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.export_share_chooser)),
            )
        }
    }

    private suspend fun showFailureToast(context: Context) =
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
        }
}