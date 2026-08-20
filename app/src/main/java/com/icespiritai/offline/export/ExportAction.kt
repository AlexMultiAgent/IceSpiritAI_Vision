package com.icespiritai.offline.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.ViolationReport
import java.io.File

object ExportAction {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val EVIDENCE_DIR = "evidence"
    private const val TAG = "ExportAction"

    fun share(
        context: Context,
        report: ViolationReport,
        appVersion: String,
    ) {
        val bytes: ByteArray
        try {
            bytes = EvidencePackageBuilder.toFile(
                report = report,
                imageProvider = ImageBytesProvider.from(context),
                appVersion = appVersion,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build evidence package", t)
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            return
        }

        val dir = File(context.cacheDir, EVIDENCE_DIR).apply { mkdirs() }
        val file = File(dir, "evidence_${report.timestampMs}.zip")
        try {
            file.writeBytes(bytes)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write evidence zip", t)
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            return
        }

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