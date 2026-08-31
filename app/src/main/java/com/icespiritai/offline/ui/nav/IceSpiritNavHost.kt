package com.icespiritai.offline.ui.nav

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import com.icespiritai.offline.ui.home.HomeScreen
import com.icespiritai.offline.ui.settings.ChangelogScreen
import com.icespiritai.offline.ui.settings.SettingsScreen
import com.icespiritai.offline.ui.settings.UpdateDetailScreen
import com.icespiritai.offline.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CHANGELOG = "changelog"
    const val UPDATE_DETAIL = "update_detail"
    const val VIEWER = "viewer"
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
fun IceSpiritNavHost(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // Activity-scoped (LocalViewModelStoreOwner above the NavHost
        // is the Activity, not a per-route NavBackStackEntry). Shared
        // with both HomeScreen and the Viewer composable.
        val sharedVm: IceSpiritVisionViewModel = viewModel()
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = sharedVm,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenViewer = { nav.navigate(Routes.VIEWER) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenChangelog = { nav.navigate(Routes.CHANGELOG) },
                    onOpenUpdateDetail = { nav.navigate(Routes.UPDATE_DETAIL) },
                )
            }
            composable(Routes.CHANGELOG) {
                ChangelogScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.UPDATE_DETAIL) {
                UpdateDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.VIEWER) {
                val state by sharedVm.state.collectAsState()
                val pendingUri by sharedVm.pendingUri.collectAsState()
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