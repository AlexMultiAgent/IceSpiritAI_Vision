// TODO(icevision/task-16): Replace with real EvidencePackageBuilder + ACTION_SEND.
package com.icespiritai.offline.export

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.ViolationReport

object ExportAction {
    fun share(context: Context, report: ViolationReport) {
        Log.d("ExportAction", "share() stub invoked (real impl in Task 16)")
        Toast.makeText(context, R.string.action_export, Toast.LENGTH_SHORT).show()
    }
}