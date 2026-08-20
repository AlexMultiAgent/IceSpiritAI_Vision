package com.icespiritai.offline.ui.home

import com.icespiritai.offline.domain.AnalysisState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-level test for [loadingLabelRes] — the [AnalysisState.Loading.Stage]
 * → string-resource mapper used by `HomeScreen` to render the running-phase
 * hint beneath the capture bar.
 *
 * If anyone moves the strings or renames the stages, this fails before the
 * UI loads the wrong copy. Pure function — no Compose / Robolectric needed.
 */
class LoadingOverlayTest {

    @Test
    fun `loadingLabelRes maps OcrRunning to status_ocr_running`() {
        assertEquals(
            com.icespiritai.offline.R.string.status_ocr_running,
            loadingLabelRes(AnalysisState.Loading.Stage.OcrRunning),
        )
    }

    @Test
    fun `loadingLabelRes maps RuleScanning to status_rule_scanning`() {
        assertEquals(
            com.icespiritai.offline.R.string.status_rule_scanning,
            loadingLabelRes(AnalysisState.Loading.Stage.RuleScanning),
        )
    }
}
