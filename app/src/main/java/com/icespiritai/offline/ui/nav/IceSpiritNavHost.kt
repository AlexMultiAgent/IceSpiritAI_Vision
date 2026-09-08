package com.icespiritai.offline.ui.nav

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icespiritai.offline.IceSpiritVisionViewModel
import com.icespiritai.offline.domain.AnalysisState
import com.icespiritai.offline.tts.EngineInfo
import com.icespiritai.offline.tts.TtsController
import com.icespiritai.offline.tts.TtsState
import com.icespiritai.offline.ui.home.HomeScreen
import com.icespiritai.offline.ui.settings.ChangelogScreen
import com.icespiritai.offline.ui.settings.SettingsScreen
import com.icespiritai.offline.ui.settings.TtsEnginePickerScreen
import com.icespiritai.offline.ui.settings.UpdateDetailScreen
import com.icespiritai.offline.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CHANGELOG = "changelog"
    const val UPDATE_DETAIL = "update_detail"
    const val VIEWER = "viewer"
    const val TTS_ENGINE_PICKER = "tts_engine_picker"
}

/**
 * Root NavHost, wrapped in a [Surface] that fills the viewport with
 * `colorScheme.background`. This is required because [enableEdgeToEdge]
 * makes the host Activity's window background transparent — without an
 * explicit Compose background, every Composable that doesn't paint its own
 * background (e.g. plain `Column { }` roots) would show the underlying
 * Activity window background, which follows the system night mode and
 * diverges from the Compose theme when `ThemeMode` is overridden.
 *
 * **ViewModel sharing**: a single [IceSpiritVisionViewModel] is hoisted
 * to the NavHost's enclosing `LocalViewModelStoreOwner` (the Activity)
 * and passed down to both `composable(Routes.HOME)` and
 * `composable(Routes.VIEWER)`. `navigation-compose` gives each
 * `NavBackStackEntry` its own `ViewModelStore`, so calling
 * `viewModel()` *inside* a `composable` block would create a fresh VM
 * per route — the Viewer would never see the URI the user just
 * double-tapped in HomeScreen. Hoisting the VM at this level makes
 * `state` + `pendingUri` live in one instance shared across both
 * destinations.
 */
@Composable
fun IceSpiritNavHost(
    ttsState: TtsState = TtsState.Disabled,
    onSpeakToggle: () -> Unit = {},
    ttsController: TtsController? = null,
    /**
     * Current TTS user preference (DataStore-backed). Threaded from
     * `IceSpiritVisionActivity` (which owns the repository) so the
     * Settings "语音播报" Switch reflects the real persisted value
     * instead of a hard-coded default. Bug 1 fix (v0.1.60) — previously
     * `SettingsScreen` always rendered with `ttsEnabled = true` and
     * `onSetTtsEnabled = {}` (the param defaults), making the Switch
     * a decorative toggle.
     */
    ttsEnabled: Boolean = true,
    onSetTtsEnabled: (Boolean) -> Unit = {},
    /**
     * Display label for the currently-active TTS engine — "跟随系统默认"
     * when no engine is pinned, otherwise the picker row's [EngineInfo.label].
     * Threaded from the Activity so the Settings "引擎" row shows real
     * data; before the fix it was a hard-coded fallback string.
     */
    currentEngineLabel: String = "跟随系统默认",
    /**
     * Package name of the engine the user has pinned (null = "follow
     * system default"). Threaded from [TtsSetting.enginePackage] so the
     * picker can highlight the currently-selected row.
     *
     * Bug 2 fix (v0.1.61): without this, the NavHost call site passed a
     * hard-coded `null` and every row rendered unselected.
     */
    currentEnginePackage: String? = null,
    /**
     * Snapshot of chinese-capable engines currently installed. Threaded
     * from [TtsController.engines] so the picker list reflects reality
     * (was previously a hard-coded empty list — picker always rendered
     * EmptyTtsState).
     */
    engines: List<EngineInfo> = emptyList(),
    /**
     * Invoked when the user taps a row in the engine picker. Receives
     * the package name to pin (null = "follow system default"). The
     * Activity wraps this with `ttsController.setEnginePackage(pkg)`
     * so the choice persists to DataStore.
     */
    onSelectEngine: (String?) -> Unit = {},
    /**
     * Invoked when the user taps the empty-state "下载" button in the
     * picker. Threaded from the Activity → [TtsController.downloadEngine]
     * which kicks off the sherpa-onnx model download. Bug 3 pivot
     * (v0.1.60): the OLD APK-install flow is reverted at e319d39 — the
     * download target is now the ONNX bundle, not a separate TTS APK.
     */
    onDownloadEngine: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // Activity-scoped (LocalViewModelStoreOwner above the NavHost
        // is the Activity, not a per-route NavBackStackEntry). Shared
        // with both HomeScreen and the Viewer composable.
        val sharedVm: IceSpiritVisionViewModel = viewModel()
        // Bug 1 fix (v0.1.60): hoist `state` and `pendingUri` collection
        // to NavHost level so HomeScreen can read `isAnalysisComplete`
        // (state is AnalysisState.Complete) for the top-bar 朗读 button.
        // Previously HomeScreen received `isAnalysisComplete = false`
        // (param default) because the NavHost call site didn't thread
        // it, so the IconButton always rendered the greyed-out
        // decorative fallback. The Viewer composable now reads from
        // the same hoisted flows — no duplicate `collectAsState` call.
        val state by sharedVm.state.collectAsState()
        val pendingUri by sharedVm.pendingUri.collectAsState()
        val isAnalysisComplete = state is AnalysisState.Complete
        // Bridge the shared VM's AnalysisState → TtsController.latestReport.
        // TtsController.toggle() is parameterless and reads its report from
        // an internal slot; without this collection the top-bar 朗读 button
        // would no-op on Idle (latestReport null) even after a Complete
        // analysis lands. Defaults to null in unit tests so this LaunchedEffect
        // is a no-op when no controller is provided.
        LaunchedEffect(sharedVm, ttsController) {
            if (ttsController != null) {
                sharedVm.state.collect { state ->
                    ttsController.setLatestReport(
                        (state as? AnalysisState.Complete)?.report
                    )
                }
            }
        }
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = sharedVm,
                    ttsState = ttsState,
                    isAnalysisComplete = isAnalysisComplete,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenViewer = { nav.navigate(Routes.VIEWER) },
                    onSpeakToggle = onSpeakToggle,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenChangelog = { nav.navigate(Routes.CHANGELOG) },
                    onOpenUpdateDetail = { nav.navigate(Routes.UPDATE_DETAIL) },
                    onOpenEnginePicker = { nav.navigate(Routes.TTS_ENGINE_PICKER) },
                    ttsState = ttsState,
                    ttsEnabled = ttsEnabled,
                    onSetTtsEnabled = onSetTtsEnabled,
                    currentEngineLabel = currentEngineLabel,
                )
            }
            composable(Routes.TTS_ENGINE_PICKER) {
                TtsEnginePickerScreen(
                    onBack = { nav.popBackStack() },
                    currentEnginePackage = currentEnginePackage,
                    engines = engines,
                    onSelectEngine = onSelectEngine,
                    onDownloadEngine = onDownloadEngine,
                )
            }
            composable(Routes.CHANGELOG) {
                ChangelogScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.UPDATE_DETAIL) {
                UpdateDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.VIEWER) {
                // Prefer the report's `lineBoxes` (populated by
                // ImageAnalyzerRepository from the OCR pass) — but
                // fall back to the transient OcrDone snapshot if the
                // user pops in before RuleScanned completes. Both
                // sources trace back to the same `ocrResult.lineBoxes`.
                val completeReport = (state as? AnalysisState.Complete)?.report
                val lineBoxes = completeReport?.lineBoxes
                    ?: (state as? AnalysisState.OcrDone)?.lineBoxes
                    ?: emptyList()
                val hits = completeReport?.hits ?: emptyList()
                val hitsCount = hits.size
                // Use the OCR engine's reference dims (full bitmap) for the
                // ViewerImage HighlightOverlay transform. Falls back to
                // nothing when the dims weren't populated (idle / shell
                // profile), in which case the overlay still falls back to
                // painter.intrinsicSize per computeFitTransform's contract.
                val imageSize = completeReport
                    ?.takeIf { it.imageWidth > 0 && it.imageHeight > 0 }
                    ?.let { androidx.compose.ui.unit.IntSize(it.imageWidth, it.imageHeight) }
                ViewerScreen(
                    imageUri = pendingUri,
                    lineBoxes = lineBoxes,
                    hits = hits,
                    hitsCount = hitsCount,
                    imageSize = imageSize,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}