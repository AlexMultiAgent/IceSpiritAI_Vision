package com.icespiritai.offline.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icespiritai.offline.AppGraph
import com.icespiritai.offline.BuildConfig
import com.icespiritai.offline.R
import com.icespiritai.offline.glasses.GlassesDevice
import com.icespiritai.offline.glasses.GlassesPhotoCaptureRepository
import com.icespiritai.offline.glasses.GlassesFirmwareUpdater
import com.icespiritai.offline.glasses.GlassesSystemIntents
import com.icespiritai.offline.glasses.GlassesTarget
import com.icespiritai.offline.glasses.resolveGlassesTarget
import com.icespiritai.offline.settings.SettingsRepository
import com.icespiritai.offline.settings.SettingsSnackbar
import com.icespiritai.offline.settings.SettingsViewModel
import com.icespiritai.offline.tts.TtsState
import com.icespiritai.offline.ui.home.RuleTab
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modernized Settings screen (Phase 3.5 Task 21).
 *
 * Layout: each section is wrapped in a [Card]; the changelog row is a
 * `Card(clickable) { Row { Column(weight=1f) { title; subtitle }; Icon
 * chevron } }` so its outer frame matches the 外观 / 更新 cards in both
 * themes. Top-bar title uses `headlineSmall` to match HomeTopBar.
 *
 * `SettingsRepository(context.applicationContext)` keeps Robolectric-friendly
 * SharedPreferences; tests do not need a fake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenUpdateDetail: () -> Unit,
    ttsState: TtsState = TtsState.Disabled,
    ttsEnabled: Boolean = true,
    onSetTtsEnabled: (Boolean) -> Unit = {},
    currentEngineLabel: String = "跟随系统默认",
    onOpenEnginePicker: () -> Unit = {},
    /**
     * v0.3.0 Phase C: 长报告摘要 switch state. When true, TTS only reads top 3
     * hits per report (default OFF per user decision 2026-09-11).
     */
    longReportSummaryEnabled: Boolean = false,
    onSetLongReportSummaryEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(SettingsRepository(context.applicationContext)),
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val visibleFeatures by viewModel.visibleFeatures.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect VM-emitted snackbar signals (LastFeatureCannotHide / PersistFailed)
    // for hosting in the Material3 snackbar host. String lookup uses
    // LocalContext (captured outside the LaunchedEffect) — SnackbarHostState
    // does NOT expose a `.context` accessor, so the plan's
    // `snackbarHostState.context.getString(...)` snippet is not a valid API.
    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { msg ->
            val message = when (msg) {
                SettingsSnackbar.LastFeatureCannotHide ->
                    context.getString(R.string.settings_feature_last_cannot_hide)
                is SettingsSnackbar.PersistFailed ->
                    context.getString(R.string.settings_feature_persist_failed)
            }
            snackbarHostState.showSnackbar(message = message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AppearanceSection(current = themeMode, onSelect = viewModel::setThemeMode)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                UpdateSection(viewModel = viewModel, onOpenUpdateDetail = onOpenUpdateDetail)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenChangelog),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_view_changelog))
                        Text(
                            text = stringResource(R.string.settings_view_changelog_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                TtsSection(
                    ttsEnabled = ttsEnabled,
                    onSetTtsEnabled = onSetTtsEnabled,
                    currentEngineLabel = currentEngineLabel,
                    onOpenEnginePicker = onOpenEnginePicker,
                    longReportSummaryEnabled = longReportSummaryEnabled,
                    onSetLongReportSummaryEnabled = onSetLongReportSummaryEnabled,
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                FeatureVisibilitySection(
                    visible = visibleFeatures,
                    onToggle = viewModel::setFeatureVisible,
                )
            }
            // v0.4.0: smart-glasses opt-in card. Default OFF per user
            // requirement "默认不连接"; the Switch flips to ON and reveals
            // the CaptureBar's "眼镜拍照" FAB on the home screen.
            // Pairing is a separate concern: the user toggles the Switch,
            // then taps "去蓝牙设置配对" to complete the OS-level flow.
            Card(modifier = Modifier.fillMaxWidth()) {
                val glassesEnabled by viewModel.enableGlassesCapture.collectAsStateWithLifecycle()
                // 「拍糊自动重拍」开关（默认开）——同卡片里的第二个开关。
                val autoRetake by viewModel.autoRetakeLowQualityGlassesShot.collectAsStateWithLifecycle()
                val glassesCtx = LocalContext.current
                // v0.4.3: status comes from the SAME resolver the capture
                // path uses, so this card can no longer contradict what
                // tapping 「眼镜」 does. Before the fix it read only the
                // app's own SharedPreferences, which meant a glasses already
                // paired in system Bluetooth still showed 「未配对智能眼镜」
                // until the app had connected once — the state the crash
                // report's screenshot was taken in.
                var glassesStatus by remember(glassesEnabled) {
                    mutableStateOf(glassesSettingsStatus(glassesCtx))
                }
                // Re-derive on every resume. The two actions this card
                // offers both leave the app (system Bluetooth settings, app
                // permission page), and a status line that is still red
                // after the user did what it asked is worse than no status
                // at all.
                LifecycleResumeEffect(glassesEnabled) {
                    glassesStatus = glassesSettingsStatus(glassesCtx)
                    onPauseOrDispose { }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_glasses_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = glassesEnabled,
                            onCheckedChange = { viewModel.setGlassesCaptureEnabled(it) },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_glasses_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 「拍糊自动重拍」：默认开。眼镜只有 640×480，拍糊/拍暗是
                    // 常态，一次浪费的往返（出图 1.3 s + 传输 + OCR）比多拍一张
                    // 贵得多 —— 但用户可以按自己的电量/时长偏好关掉。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_glasses_auto_retake),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = autoRetake,
                            onCheckedChange = { viewModel.setAutoRetakeLowQualityGlassesShot(it) },
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_glasses_auto_retake_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val status = glassesStatus
                    Text(
                        text = when (status) {
                            is GlassesSettingsStatus.Ready ->
                                stringResource(R.string.settings_glasses_paired, status.address)
                            GlassesSettingsStatus.PermissionMissing ->
                                stringResource(R.string.settings_glasses_permission_missing)
                            GlassesSettingsStatus.BluetoothOff ->
                                stringResource(R.string.glasses_bluetooth_off)
                            GlassesSettingsStatus.BluetoothUnavailable ->
                                stringResource(R.string.glasses_no_adapter)
                            GlassesSettingsStatus.NotPaired ->
                                stringResource(R.string.settings_glasses_not_paired)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (glassesEnabled && status.needsAction) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (status) {
                                GlassesSettingsStatus.PermissionMissing ->
                                    stringResource(R.string.settings_glasses_permission_hint)
                                else -> stringResource(R.string.settings_glasses_pairing_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                GlassesSystemIntents.openBluetoothSettings(glassesCtx)
                            },
                        ) {
                            Text(stringResource(R.string.settings_glasses_action_pair))
                        }
                        // Pairing does not help when the *permission* is the
                        // blocker, so offer the page that does.
                        if (status is GlassesSettingsStatus.PermissionMissing) {
                            TextButton(
                                onClick = {
                                    GlassesSystemIntents.openAppSettings(glassesCtx)
                                },
                            ) {
                                Text(stringResource(R.string.glasses_action_open_app_settings))
                            }
                        }
                    }
                    GlassesFirmwareRow(
                        context = glassesCtx,
                        enabled = glassesEnabled,
                    )
                    GlassesFirmwareUpgradeRow(
                        context = glassesCtx,
                        enabled = glassesEnabled,
                        snackbarHostState = snackbarHostState,
                    )
                    GlassesWifiTransferRow(
                        context = glassesCtx,
                        enabled = glassesEnabled,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_about_org),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * "语音播报" settings card(spec §7.1)。
 *
 * 总开关 Switch + 引擎 row(gated on enabled)+ footer disclaimer 双行。
 * 引擎 row clickable → onOpenEnginePicker,Activity 侧路由到 picker 子页。
 */
@Composable
private fun TtsSection(
    ttsEnabled: Boolean,
    onSetTtsEnabled: (Boolean) -> Unit,
    currentEngineLabel: String,
    onOpenEnginePicker: () -> Unit,
    longReportSummaryEnabled: Boolean = false,
    onSetLongReportSummaryEnabled: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.tts_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetTtsEnabled(!ttsEnabled) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tts_total_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = ttsEnabled,
                onCheckedChange = onSetTtsEnabled,
            )
        }
        if (ttsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenEnginePicker)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tts_engine_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = currentEngineLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
            // v0.3.0 Phase C — long report Top-N summary. Hidden when TTS is disabled
            // (consistent with engine picker gating: both are sub-options of the
            // ttsEnabled parent switch).
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetLongReportSummaryEnabled(!longReportSummaryEnabled) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tts_settings_long_report_summary),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.tts_settings_long_report_summary_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Switch(
                    checked = longReportSummaryEnabled,
                    onCheckedChange = onSetLongReportSummaryEnabled,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tts_section_footer_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = stringResource(R.string.tts_section_footer_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * "功能可见性" settings card(food-labeling feature plan §3.5 / Task 6)。
 *
 * One Switch per [RuleTab] (广告招牌 / 食品标签). The "至少保留一个" invariant
 * is enforced in [SettingsViewModel.setFeatureVisible] — the VM rejects the
 * write and emits [SettingsSnackbar.LastFeatureCannotHide], which the caller
 * surfaces as a snackbar. This composable therefore does NOT need to know
 * about the size==1 guard; it simply forwards user intent to [onToggle].
 *
 * The two row labels use dedicated settings strings (`settings_feature_ad_signage_label`
 * / `settings_feature_food_label_label`) rather than the tab bar's `tab_ad_law`
 * / `tab_food_label`, because the settings context may want different copy in
 * the future — keeping them separate avoids future tab-bar-style churn bleeding
 * into this card.
 *
 * Pure Composable: takes primitive props ([visible] + [onToggle]) instead of
 * the ViewModel directly, which keeps it Robolectric-test-friendly without a
 * fake VM factory. Matches [TtsSection]'s signature shape.
 */
@Composable
private fun FeatureVisibilitySection(
    visible: Set<RuleTab>,
    onToggle: (RuleTab, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_feature_visibility_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_feature_visibility_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        FeatureVisibilityRow(
            label = stringResource(R.string.settings_feature_ad_signage_label),
            checked = RuleTab.AdSignage in visible,
            onCheckedChange = { onToggle(RuleTab.AdSignage, it) },
        )
        FeatureVisibilityRow(
            label = stringResource(R.string.settings_feature_food_label_label),
            checked = RuleTab.FoodLabeling in visible,
            onCheckedChange = { onToggle(RuleTab.FoodLabeling, it) },
        )
    }
}

/**
 * Single label + Switch row used inside [FeatureVisibilitySection]. Extracted
 * so the two rows stay visually identical (label `bodyLarge` weight=1f +
 * trailing Switch) — duplication here would silently drift if one row is
 * later tweaked.
 */
@Composable
private fun FeatureVisibilityRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * What the 智能眼镜 card can say about the current setup.
 *
 * Four states rather than the pre-v0.4.3 boolean, because the boolean lied
 * twice: it reported 「未配对」 for a device that *was* paired in system
 * Bluetooth (the app only looked at its own remember-last-address store),
 * and it had no way to say 「未授权蓝牙权限」 at all — the state that
 * actually blocked the user in the 2026-09-16 crash report.
 */
private sealed interface GlassesSettingsStatus {

    data class Ready(val address: String) : GlassesSettingsStatus

    data object NotPaired : GlassesSettingsStatus

    data object PermissionMissing : GlassesSettingsStatus

    data object BluetoothOff : GlassesSettingsStatus

    data object BluetoothUnavailable : GlassesSettingsStatus

    /**
     * Should the card print the red "do something" hint?
     *
     * False for [BluetoothUnavailable] — a device with no Bluetooth radio
     * cannot be fixed by pairing, so pointing at Bluetooth settings would
     * be noise.
     */
    val needsAction: Boolean
        get() = when (this) {
            is Ready, BluetoothUnavailable -> false
            NotPaired, PermissionMissing, BluetoothOff -> true
        }
}

/**
 * Derive the card's status from the same pure resolver the capture path
 * uses — one source of truth, so the card and the 「眼镜」 button can never
 * disagree about whether capture is possible.
 */
/**
 * Firmware version of the paired glasses, with a manual read.
 *
 * `0x10`-with-sub-`0x20` is the vendor-documented read (see
 * [com.icespiritai.offline.glasses.GlassesPhotoProtocol.buildFirmwareVersionRequestFrame]),
 * so this is the one part of the firmware story that needs no new protocol
 * risk. The version line is what makes an OTA actionable: without it the
 * user cannot tell whether a flash did anything, and neither can we when
 * the vendor ships the FA12 pacing fix.
 *
 * The read connects on demand (the same `ensureConnected` the capture path
 * uses) instead of demanding that the user capture a photo first.
 */
@Composable
private fun GlassesFirmwareRow(
    context: Context,
    enabled: Boolean,
) {
    val scope = rememberCoroutineScope()
    val store = remember(context) { AppGraph.glassesDeviceStore(context) }
    var version by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Seed from the last version we read (persisted by the repository): it
    // survives cold starts, so a firmware upgrade that finished while the App
    // was closed still shows the new version here instead of 「未读取」.
    LaunchedEffect(store) {
        version = withContext(Dispatchers.IO) { runCatching { store.loadFirmwareVersion() }.getOrNull() }
    }

    fun read() {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            val result = runCatching {
                val device = resolvePairedGlasses(context) ?: return@runCatching null
                val repository = AppGraph.glassesPhotoCaptureRepository(context)
                val version = repository.readFirmwareVersion(device)
                // Same button, one extra read: memory / file count / FTP IP /
                // P2P MAC say whether this hardware could use the Wi-Fi
                // media-sync path at all, which decides whether chasing the
                // vendor's SPP route is even the right investment. Log-only
                // (tag GlassesCapture) — it changes nothing the user sees.
                runCatching { repository.readDeviceInfo(device) }
                version
            }.getOrNull()
            version = result
            failed = result == null
            busy = false
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                busy -> stringResource(R.string.settings_glasses_firmware_reading)
                version != null ->
                    stringResource(R.string.settings_glasses_firmware_version, version!!)
                failed -> stringResource(R.string.settings_glasses_firmware_failed)
                else -> stringResource(R.string.settings_glasses_firmware_unknown)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { read() }, enabled = enabled && !busy) {
            Text(stringResource(R.string.settings_glasses_firmware_read))
        }
    }
}

/**
 * Resolve the paired glasses the same way the capture path does, so every
 * diagnostic row on this card talks to the same device.
 */
private fun resolvePairedGlasses(context: Context): GlassesDevice? {
    val target = runCatching {
        resolveGlassesTarget(
            lastPairedAddress = AppGraph.glassesDeviceStore(context).loadLastPaired(),
            snapshot = GlassesDevice.bondedSnapshot(context),
            nowMs = System.currentTimeMillis(),
        )
    }.getOrElse { GlassesTarget.NotPaired }
    return (target as? GlassesTarget.Ready)?.device
}

/**
 * 「Wi-Fi 取图诊断」 row.
 *
 * The OEM app pulls an ordinary photo off a *storage* device over Wi-Fi
 * (FTP on the AP/P2P session it starts with `0x36` + `0x39`) and only falls
 * back to SPP/RFCOMM for memoryless hardware — see
 * `docs/knowledge/official-glasses-apk-ble-internals.md` §7. Our glasses
 * report storage, so the only question that decides whether chasing that
 * path is worth anything is whether those two commands actually make them
 * hand out an FTP address / AP account. This row asks, reports, and puts
 * the glasses back when the answer is no.
 *
 * Diagnostic only: the BLE capture pipeline is untouched.
 */
@Composable
private fun GlassesWifiTransferRow(
    context: Context,
    enabled: Boolean,
) {
    // State and work live in the repository, so an Activity recreation in the
    // middle of the probe (which really happened: a system configuration
    // change landed exactly when the glasses answered) cannot cancel it.
    val repository = remember(context) {
        AppGraph.glassesPhotoCaptureRepository(context)
    }
    val probe by repository.wifiTransferProbe.collectAsStateWithLifecycle()
    val busy = probe.running
    val hasConfig = probe.hasApConfig
    val ftp = probe.info?.ftpIp
    val ssid = probe.info?.apSsid
    // Local copy: a delegated `by` property cannot be smart-cast.
    val direct = probe.direct

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_glasses_wifi_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                busy && probe.phase == GlassesPhotoCaptureRepository.WifiProbePhase.DIRECT ->
                    stringResource(R.string.settings_glasses_wifi_direct_running)
                busy -> stringResource(R.string.settings_glasses_wifi_running)
                direct?.fetchedBytes != null -> stringResource(
                    R.string.settings_glasses_wifi_direct_ok,
                    // KB, rounded up: a 34 KB photo should not read as 33.
                    ((direct.fetchedBytes + 1023) / 1024).toInt(),
                    (direct.fetchMs ?: 0L) + (direct.listingMs ?: 0L),
                    direct.connectMs ?: 0L,
                )
                direct != null -> stringResource(
                    R.string.settings_glasses_wifi_direct_fail,
                    direct.failedPhase ?: "?",
                    direct.error ?: "?",
                )
                hasConfig && ftp != null && ssid != null ->
                    stringResource(R.string.settings_glasses_wifi_config, ftp!!, ssid!!)
                hasConfig && ssid != null ->
                    stringResource(R.string.settings_glasses_wifi_config, "—", ssid!!)
                hasConfig ->
                    stringResource(R.string.settings_glasses_wifi_config, "—", "—")
                probe.failed -> stringResource(R.string.settings_glasses_wifi_failed)
                probe.finished -> stringResource(R.string.settings_glasses_wifi_not_up)
                else -> stringResource(R.string.settings_glasses_wifi_idle)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (probe.failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        // Only worth offering when the probe left the glasses' Wi-Fi on, i.e.
        // when the transfer actually worked; every failure path stops it.
        if (direct?.fetchedBytes != null) {
            TextButton(onClick = { repository.endWifiTransfer() }, enabled = enabled && !busy) {
                Text(stringResource(R.string.settings_glasses_wifi_action_stop))
            }
        }
        TextButton(onClick = { repository.probeWifiTransfer() }, enabled = enabled && !busy) {
            Text(stringResource(R.string.settings_glasses_wifi_action_test))
        }
    }
}

/**
 * 「检查固件更新」 row + the upgrade dialog.
 *
 * Check = vendor OTA API through a guest token (no account); upgrade = hand
 * the download URL to the glasses over BLE, let *them* fetch and flash the
 * `.rbl`, then watch the firmware version change. The dialog therefore says
 * what the user must do (蓝牙共享网络 / 别断电) instead of pretending the App
 * is downloading anything.
 */
@Composable
private fun GlassesFirmwareUpgradeRow(
    context: Context,
    enabled: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val updater = remember(context) { AppGraph.glassesFirmwareUpdater(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    var dialogOpen by remember { mutableStateOf(false) }

    // The dialog can be closed while the glasses download/flash (the row keeps
    // showing the elapsed time), and the upgrade keeps running in the
    // process-scoped updater. Before this the *result* was only visible inside
    // that dialog, so a successful upgrade finished silently for anyone who had
    // closed it (user report 2026-09-17 22:08). Now every terminal state also
    // shows up as a snackbar and stays in the row text below.
    LaunchedEffect(updater) {
        updater.state.collect { s ->
            val message = when (s) {
                is GlassesFirmwareUpdater.UpgradeState.Success -> context.getString(
                    R.string.settings_glasses_firmware_success,
                    s.previousVersion ?: "?",
                    s.newVersion,
                )
                is GlassesFirmwareUpdater.UpgradeState.Failed -> context.getString(
                    R.string.settings_glasses_firmware_upgrade_failed,
                    s.reason,
                )
                else -> null
            }
            if (message != null) snackbarHostState.showSnackbar(message)
        }
    }

    fun currentDevice(): GlassesDevice? = runCatching {
        resolveGlassesTarget(
            lastPairedAddress = AppGraph.glassesDeviceStore(context).loadLastPaired(),
            snapshot = GlassesDevice.bondedSnapshot(context),
            nowMs = System.currentTimeMillis(),
        )
    }.getOrNull().let { (it as? GlassesTarget.Ready)?.device }

    val busy = state is GlassesFirmwareUpdater.UpgradeState.Checking ||
        state is GlassesFirmwareUpdater.UpgradeState.Sending ||
        state is GlassesFirmwareUpdater.UpgradeState.Upgrading

    // A finished upgrade (or a failure) keeps showing in this row: the dialog is
    // dismissable while the glasses work, so the row is where the result has to
    // survive (user report 2026-09-17 22:08 — "升级成功后没有提示").
    val finishedState = state as? GlassesFirmwareUpdater.UpgradeState.Success
        ?: state as? GlassesFirmwareUpdater.UpgradeState.Failed
    val showResult = busy || finishedState != null

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (val s = state) {
                // While the glasses download/flash the row keeps showing what is
                // happening, so the dialog can be closed without losing sight of
                // the upgrade (user report 2026-09-17: the dialog could not be
                // closed at all, and after opening 蓝牙共享网络 mid-upgrade the
                // only way forward was to kill and restart the App).
                is GlassesFirmwareUpdater.UpgradeState.Upgrading -> stringResource(
                    R.string.settings_glasses_firmware_upgrading,
                    (s.elapsedMs / 1000).toInt(),
                )
                is GlassesFirmwareUpdater.UpgradeState.Sending,
                is GlassesFirmwareUpdater.UpgradeState.Checking ->
                    stringResource(R.string.settings_glasses_firmware_checking)
                is GlassesFirmwareUpdater.UpgradeState.Success -> stringResource(
                    R.string.settings_glasses_firmware_success,
                    s.previousVersion ?: "?",
                    s.newVersion,
                )
                is GlassesFirmwareUpdater.UpgradeState.Failed ->
                    stringResource(R.string.settings_glasses_firmware_upgrade_failed, s.reason)
                else -> stringResource(R.string.settings_glasses_firmware_check)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (finishedState is GlassesFirmwareUpdater.UpgradeState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = if (showResult) true else enabled,
            onClick = {
                dialogOpen = true
                // With a result on screen (or work in flight) the button only
                // re-opens the dialog — it must not silently start another
                // check that overwrites the outcome the user just got.
                if (showResult) return@TextButton
                scope.launch {
                    val device = currentDevice()
                    if (device == null) {
                        updater.reportPreflightFailure("未找到已配对的眼镜,请先在系统蓝牙里配对")
                    } else {
                        updater.checkForUpdate(device)
                    }
                }
            },
        ) {
            Text(
                stringResource(
                    if (showResult) {
                        R.string.settings_glasses_firmware_action_view
                    } else {
                        R.string.settings_glasses_firmware_read
                    },
                ),
            )
        }
    }

    if (!dialogOpen) return
    val upgradeInfo = (state as? GlassesFirmwareUpdater.UpgradeState.Available)?.info
    AlertDialog(
        // Closing is always allowed: the watch runs on the process-scoped
        // updater, so the row keeps showing the elapsed time and the button
        // reopens this dialog (`查看`).
        onDismissRequest = { dialogOpen = false },
        title = { Text(stringResource(R.string.settings_glasses_firmware_dialog_title)) },
        text = {
            val current = state
            // While the glasses are downloading, the phone's side of the route
            // is the part that can silently be missing — say so *here*, next to
            // the elapsed-seconds counter, instead of letting the user watch a
            // 20-minute timer that cannot possibly end in success.
            val hint = (current as? GlassesFirmwareUpdater.UpgradeState.Upgrading)?.hint
            if (hint != null && hint != GlassesFirmwareUpdater.TetheringHint.NONE) {
                Column {
                    Text(
                        stringResource(
                            R.string.settings_glasses_firmware_upgrading,
                            ((current as GlassesFirmwareUpdater.UpgradeState.Upgrading).elapsedMs / 1000).toInt(),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (hint) {
                            GlassesFirmwareUpdater.TetheringHint.TETHERING_OFF ->
                                stringResource(R.string.settings_glasses_firmware_tethering_off)
                            else -> stringResource(
                                R.string.settings_glasses_firmware_tethering_not_connected,
                                (current.elapsedMs / 1000).toInt(),
                            )
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { GlassesSystemIntents.openTetheringSettings(context) }) {
                        Text(stringResource(R.string.settings_glasses_firmware_action_tethering))
                    }
                }
            } else {
                Text(
                    text = when (val s = current) {
                        is GlassesFirmwareUpdater.UpgradeState.Idle,
                        is GlassesFirmwareUpdater.UpgradeState.Checking ->
                            stringResource(R.string.settings_glasses_firmware_checking)
                        is GlassesFirmwareUpdater.UpgradeState.UpToDate ->
                            stringResource(
                                R.string.settings_glasses_firmware_uptodate,
                                s.currentVersion ?: "?",
                            )
                        is GlassesFirmwareUpdater.UpgradeState.Available -> stringResource(
                            R.string.settings_glasses_firmware_dialog_body,
                            s.info.currentVersion ?: "?",
                            s.info.latestVersion,
                            s.info.firmwareName ?: "?",
                            formatFirmwareSize(s.info.sizeBytes),
                        )
                        is GlassesFirmwareUpdater.UpgradeState.Sending -> stringResource(
                            R.string.settings_glasses_firmware_sending,
                            s.framesSent,
                            s.framesTotal,
                        )
                        is GlassesFirmwareUpdater.UpgradeState.Upgrading -> stringResource(
                            R.string.settings_glasses_firmware_upgrading,
                            (s.elapsedMs / 1000).toInt(),
                        )
                        is GlassesFirmwareUpdater.UpgradeState.Success -> stringResource(
                            R.string.settings_glasses_firmware_success,
                            s.previousVersion ?: "?",
                            s.newVersion,
                        )
                        is GlassesFirmwareUpdater.UpgradeState.Failed ->
                            stringResource(R.string.settings_glasses_firmware_upgrade_failed, s.reason)
                    },
                )
            }
        },
        confirmButton = {
            if (upgradeInfo != null) {
                val device = currentDevice()
                TextButton(
                    onClick = {
                        if (device != null) updater.startUpgrade(device, upgradeInfo)
                    },
                    enabled = device != null,
                ) {
                    Text(stringResource(R.string.settings_glasses_firmware_action_upgrade))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { dialogOpen = false },
            ) {
                Text(stringResource(R.string.settings_glasses_firmware_action_close))
            }
        },
    )
}

/** `2 655 536 B` → `2.5 MB`, for the upgrade dialog. */
private fun formatFirmwareSize(bytes: Long?): String = when {
    bytes == null || bytes <= 0 -> "未知"
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun glassesSettingsStatus(context: Context): GlassesSettingsStatus {
    val target = runCatching {
        resolveGlassesTarget(
            lastPairedAddress = AppGraph.glassesDeviceStore(context).loadLastPaired(),
            snapshot = GlassesDevice.bondedSnapshot(context),
            nowMs = System.currentTimeMillis(),
        )
    }.getOrElse { GlassesTarget.NotPaired }

    return when (target) {
        is GlassesTarget.Ready -> GlassesSettingsStatus.Ready(target.device.address)
        GlassesTarget.ConnectPermissionMissing -> GlassesSettingsStatus.PermissionMissing
        GlassesTarget.BluetoothOff -> GlassesSettingsStatus.BluetoothOff
        GlassesTarget.BluetoothUnavailable -> GlassesSettingsStatus.BluetoothUnavailable
        GlassesTarget.NotPaired -> GlassesSettingsStatus.NotPaired
    }
}
