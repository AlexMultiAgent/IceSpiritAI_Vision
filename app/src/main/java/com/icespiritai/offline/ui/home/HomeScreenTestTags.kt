package com.icespiritai.offline.ui.home

/**
 * Stable test tags for the Home-screen composables ([StatusBanner],
 * [CaptureBar], [ErrorPanel]). Declared in **main** source (not test
 * source) because production composables in this package need to
 * attach `Modifier.testTag(...)` directly — test sources are not
 * visible from main in Gradle's source-set split. See
 * [RuleTabBarTestTags] for the same pattern in a sibling file.
 *
 * Tag naming: snake_case with a feature-area prefix (`statusBanner_` /
 * `captureBar_` / `errorPanel_`) so test-tag-only logs (Robolectric
 * `composeRule.printToLog(...)`) are easy to grep. The exception is
 * the bare `errorPanel_retry` / `errorPanel_back` — those appear in
 * a single parent so the prefix matches the column.
 *
 * Compose test-tag rules: a `testTag` lives on the merged semantics
 * tree of the node, NOT on its children. Tests should query the
 * outer container for `assertExists()` and then `onChild*` /
 * `onChildren` for inner assertions — never assume a tag propagates
 * to descendants. The empty-content `Icon(contentDescription = null)`
 * trick used inside [CaptureBar]'s FABs intentionally relies on
 * `null` so the `Save` / `PhotoLibrary` icon is reachable via
 * `contentDescription` queries, NOT testTag.
 */
object HomeScreenTestTags {
    const val STATUS_BANNER = "statusBanner"
    const val KPI_VIOLATION = "statusBanner_kpi_violation"
    const val KPI_WARNING = "statusBanner_kpi_warning"
    const val KPI_INFO = "statusBanner_kpi_info"

    const val CAPTURE_BAR_PICK = "captureBar_pick"
    const val CAPTURE_BAR_EXPORT = "captureBar_export"
    const val CAPTURE_BAR_CAPTURE = "captureBar_capture"

    const val ERROR_PANEL = "errorPanel"
    const val ERROR_PANEL_RETRY = "errorPanel_retry"
    const val ERROR_PANEL_BACK = "errorPanel_back"
}