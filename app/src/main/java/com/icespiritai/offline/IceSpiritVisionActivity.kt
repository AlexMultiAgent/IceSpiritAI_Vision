package com.icespiritai.offline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.tts.AndroidTtsEngine
import com.icespiritai.offline.tts.TtsController
import com.icespiritai.offline.tts.TtsSetting
import com.icespiritai.offline.tts.TtsSettingRepository
import com.icespiritai.offline.tts.TtsSettingRepositoryAdapter
import com.icespiritai.offline.ui.common.DisclaimerDialog
import com.icespiritai.offline.ui.nav.IceSpiritNavHost
import com.icespiritai.offline.ui.theme.IceSpiritVisionTheme
import com.icespiritai.offline.ui.theme.ThemeMode
import com.icespiritai.offline.updater.UpdateDownloadActions
import com.icespiritai.offline.updater.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class IceSpiritVisionActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob())
    private lateinit var ttsController: TtsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SettingsRepository(applicationContext)
        // Apply the persisted night mode asynchronously instead of blocking the
        // main thread on the first DataStore read.
        lifecycleScope.launch {
            AppCompatDelegate.setDefaultNightMode(settings.themeMode.first().toNightMode())
        }

        // TTS controller: process-singleton (Activity field), AppGraph untouched.
        // CLAUDE.md §4 backend stays intact — TTS is a new component, surfaced
        // to the UI through the LocalTtsController CompositionLocal.
        val ttsRepo = TtsSettingRepository(applicationContext)
        ttsController = TtsController(
            engine = AndroidTtsEngine(applicationContext),
            settings = TtsSettingRepositoryAdapter(ttsRepo),
            scope = appScope,
        )

        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                // First-frame placeholder before the first DataStore read
                // returns. Matches the factory default (SYSTEM) so a
                // freshly installed user does not see a one-frame flash
                // before the first read lands.
                initialValue = ThemeMode.SYSTEM,
            )
            val disclaimerAccepted by settings.disclaimerAcceptedAt
                .map { it != null }
                .collectAsStateWithLifecycle(
                    initialValue = false,
                )
            // Thread TTS controller state down to the NavHost so the home
            // top-bar 朗读 button can flip between Idle / Speaking /
            // InitFailed (Bug 2 fix — without this wiring the NavHost
            // defaults to Disabled and the icon never renders).
            val ttsState by ttsController.state.collectAsStateWithLifecycle()
            // Bug 1 fix (v0.1.60): collect the persisted TtsSetting so the
            // Settings "语音播报" Switch + engine label reflect real
            // DataStore values, not the NavHost param defaults. The
            // `ttsSetting` flow is a Flow<TtsSetting> backed by DataStore;
            // initialValue = TtsSetting() matches the DataStore default
            // (enabled=true, enginePackage=null) so the first frame is
            // visually identical to a freshly installed user.
            val ttsSetting by ttsController.setting.collectAsStateWithLifecycle(
                initialValue = TtsSetting(),
            )
            val currentEngineLabel = remember(ttsSetting) {
                ttsController.currentEngineLabel(ttsSetting.enginePackage)
            }
            // Bug 2 fix (v0.1.61): collect the engine list so the picker
            // actually shows devices' installed chinese-capable engines
            // instead of always rendering the EmptyTtsState branch.
            val engines by ttsController.engines.collectAsStateWithLifecycle()

            IceSpiritVisionTheme(themeMode = themeMode, ttsController = ttsController) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IceSpiritNavHost(
                        ttsState = ttsState,
                        onSpeakToggle = { ttsController.toggle() },
                        ttsController = ttsController,
                        ttsEnabled = ttsSetting.enabled,
                        onSetTtsEnabled = { enabled ->
                            // ttsController.setEnabled delegates to
                            // TtsSettingRepository.setEnabled via the
                            // TtsSettingRepositoryAdapter; we just need
                            // a CoroutineScope. lifecycleScope is the
                            // Activity-scoped scope, fine for a single
                            // DataStore edit (low-frequency UI event).
                            lifecycleScope.launch {
                                ttsController.setEnabled(enabled)
                            }
                        },
                        currentEngineLabel = currentEngineLabel,
                        // Bug 2 fix (v0.1.61): thread the persisted package
                        // name and the live engine list to the picker so
                        // tapping a row actually flips the highlighted
                        // selection and persists via setEnginePackage.
                        currentEnginePackage = ttsSetting.enginePackage,
                        engines = engines,
                        onSelectEngine = { pkg ->
                            lifecycleScope.launch { ttsController.setEnginePackage(pkg) }
                        },
                    )
                    if (!disclaimerAccepted) {
                        DisclaimerDialog(onAcknowledge = {
                            lifecycleScope.launch { settings.acceptDisclaimer() }
                        })
                    }
                }
            }
        }

        // In-app update: silent startup check. checkForUpdatesAsync owns a
        // process-wide default scope, so the check survives Activity
        // recreation (a fresh Activity would otherwise re-fire it). Its
        // state mutations land in `UpdateRepository.state`, which is observed
        // by `SettingsViewModel.updateState`. Fire-and-forget.
        UpdateRepository.checkForUpdatesAsync(
            jsonUrl = BuildConfig.UPDATE_JSON_URL,
            currentVersionCode = BuildConfig.VERSION_CODE,
        )

        // Notification PendingIntents for [立即安装] / [稍后] launch the
        // Activity with ACTION_INSTALL / ACTION_LATER. Handle the action
        // here so the tap is consumed before the user sees the home
        // screen. Clearing `intent.action` prevents onResume / rotation
        // from re-triggering the install.
        handleUpdateActionIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the Activity's current intent so subsequent reads via
        // getIntent() see the new payload (matches the spec'd single-
        // launch semantics for ACTION_INSTALL).
        setIntent(intent)
        handleUpdateActionIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsController.release()
    }

    private fun handleUpdateActionIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            UpdateDownloadActions.ACTION_INSTALL -> {
                val downloadId = intent.getStringExtra(UpdateDownloadActions.EXTRA_DOWNLOAD_ID)
                    ?: return
                val file = File(cacheDir, "update/$downloadId.apk")
                if (file.exists()) UpdateRepository.requestInstall(this, file)
                // Prevent onResume / rotation from re-firing install.
                intent.action = null
            }
            UpdateDownloadActions.ACTION_LATER -> {
                // [稍后] is a no-op beyond consuming the action — the
                // notification already represents the user-deferred
                // choice. Clearing the action stops rotation re-triggers.
                intent.action = null
            }
        }
    }
}
