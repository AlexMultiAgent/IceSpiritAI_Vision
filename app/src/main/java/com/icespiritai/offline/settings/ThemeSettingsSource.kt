package com.icespiritai.offline.settings

import com.icespiritai.offline.ui.home.RuleTab
import com.icespiritai.offline.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the persistent theme-mode setting. Implemented by
 * [SettingsRepository] for production (DataStore-backed) and by test
 * doubles under `src/test/`.
 *
 * `themeMode` is exposed as a [Flow] rather than a [kotlinx.coroutines.flow.StateFlow]
 * so the contract stays minimal: callers that need a StateFlow (e.g.
 * `SettingsViewModel` for Compose `collectAsStateWithLifecycle`) project it
 * via `stateIn(scope, started, initialValue)`. The interface intentionally
 * doesn't pin to StateFlow to avoid forcing every implementation to
 * eagerly materialize one.
 *
 * `visibleFeatures` follows the same Flow-vs-StateFlow rationale — the
 * set of currently-enabled RuleTabs is a small set so callers can
 * project it via `stateIn` cheaply when they need Compose-friendly state.
 */
interface ThemeSettingsSource {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Set of [RuleTab] values currently visible to the user. Persisted in
     * DataStore by the production implementation; defaults to all tabs
     * when the key is missing or the persisted set is empty after
     * deserialization (e.g. stale enum names left from a previous version
     * that no longer exist in the current [RuleTab] enum).
     */
    val visibleFeatures: Flow<Set<RuleTab>>
    suspend fun setVisibleFeatures(value: Set<RuleTab>)

    /**
     * Whether the user has opted in to smart-glasses BLE capture.
     *
     * **Default `false`** — the app's core flow (gallery pick + phone
     * camera capture → OCR + rule scan → TTS) works without any glasses
     * hardware, so the BLE capture path is opt-in. The CaptureBar hides
     * its "眼镜拍照" button when this is `false`; [SettingsScreen]
     * exposes the toggle.
     *
     * Pairing status is **separate** from this flag: a user can have
     * `enableGlassesCapture = true` but no paired device in system
     * Bluetooth settings, in which case the glasses button shows but the
     * capture flow surfaces a "未配对" hint pointing at the system
     * Bluetooth settings deep-link.
     */
    val enableGlassesCapture: Flow<Boolean>
    suspend fun setGlassesCaptureEnabled(enabled: Boolean)
}
