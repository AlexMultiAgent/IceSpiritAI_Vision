package com.icespiritai.offline.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.icespiritai.offline.R
import com.icespiritai.offline.updater.AppVersionInfo
import com.icespiritai.offline.updater.UpdateRepository
import com.icespiritai.offline.updater.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [UpdateDetailScreen] — the long-changelog view split
 * out of [SettingsScreen] in v0.1.13 (the bug it shipped with was that a
 * full changelog pushed the download button off-screen).
 *
 * Pins:
 *  - top-bar title "更新详情" renders
 *  - back arrow invokes onBack
 *  - when [UpdateRepository.state] is anything other than
 *    [UpdateState.UpdateAvailable] (Idle / Checking / UpToDate /
 *    Downloading / ReadyToInstall / Failed all collapse here), the
 *    fallback "当前没有可用更新" message renders — the screen deliberately
 *    does not fabricate a changelog
 *  - when [UpdateRepository.state] is [UpdateState.UpdateAvailable] the
 *    banner "新版本 vX.Y.Z 可用" renders plus one rendered row per
 *    non-blank changelog line
 *
 * Test isolation note: [UpdateRepository] is a process-wide singleton.
 * We restore its state to [UpdateState.Idle] in `@After` so other tests
 * don't observe a leaked [UpdateState.UpdateAvailable]. The private
 * `_state` field is reached via reflection — same pattern used by
 * `SettingsViewModelTest` when it needs to seed the repository.
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ctx: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @After fun resetUpdateRepository() {
        // Restore Idle so a leaked UpdateAvailable doesn't taint other
        // tests in the suite (UpdateSectionTest asserts on Idle state).
        UpdateRepository::class.java.getDeclaredField("_state").apply {
            isAccessible = true
        }.let { field ->
            @Suppress("UNCHECKED_CAST")
            (field.get(UpdateRepository) as MutableStateFlow<UpdateState>).value = UpdateState.Idle
        }
    }

    private fun setUpdateState(state: UpdateState) {
        UpdateRepository::class.java.getDeclaredField("_state").apply {
            isAccessible = true
        }.let { field ->
            @Suppress("UNCHECKED_CAST")
            (field.get(UpdateRepository) as MutableStateFlow<UpdateState>).value = state
        }
    }

    @Test
    fun `renders the top-bar title`() {
        composeRule.setContent {
            UpdateDetailScreen(onBack = {})
        }
        composeRule.onNodeWithText(ctx.getString(R.string.update_detail_title)).assertExists()
    }

    @Test
    fun `clicking the back arrow invokes onBack`() {
        var backs = 0
        composeRule.setContent {
            UpdateDetailScreen(onBack = { backs++ })
        }
        composeRule.onNodeWithContentDescription(ctx.getString(R.string.action_back))
            .performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `renders no-pending message when repository state is Idle`() {
        // Default UpdateRepository.state is Idle; explicit assignment is
        // defensive in case another test in the same JVM mutated it.
        setUpdateState(UpdateState.Idle)
        composeRule.setContent {
            UpdateDetailScreen(onBack = {})
        }
        composeRule.onNodeWithText(ctx.getString(R.string.update_detail_no_pending))
            .assertExists()
    }

    @Test
    fun `renders version banner and one row per changelog line when update available`() {
        val info = AppVersionInfo(
            versionCode = 67,
            versionName = "0.1.67",
            apkUrl = "http://stub/apk",
            apkSize = 1L,
            apkSha256 = "deadbeef".repeat(8),
            changelog = "## v0.1.67\n\n- fix P0 audit blockers\n- wire LoadingOverlay",
        )
        setUpdateState(UpdateState.UpdateAvailable(info))

        composeRule.setContent {
            UpdateDetailScreen(onBack = {})
        }

        // Banner: "新版本 v0.1.67 可用" — %1$s substitutes versionName.
        composeRule.onNodeWithText(ctx.getString(R.string.update_available_banner, "0.1.67"))
            .assertExists()

        // The changelog rendered into the LazyColumn — each non-blank line
        // gets its own Text node. The first line is "## v0.1.67", the
        // third is the bullet "- fix P0 audit blockers", the fourth is
        // "- wire LoadingOverlay" (blank line at idx 2 is filtered).
        composeRule.onNodeWithText("## v0.1.67").assertExists()
        composeRule.onNodeWithText("- fix P0 audit blockers").assertExists()
        composeRule.onNodeWithText("- wire LoadingOverlay").assertExists()
    }

    @Test
    fun `renders no-pending message for non-UpdateAvailable states`() {
        // Spans the rest of the sealed UpdateState subclasses: any
        // non-UpdateAvailable branch must collapse into the fallback,
        // not fabricate a changelog. Pin each one explicitly so future
        // sealed-class additions get noticed.
        setUpdateState(UpdateState.Checking)
        composeRule.setContent { UpdateDetailScreen(onBack = {}) }
        composeRule.onNodeWithText(ctx.getString(R.string.update_detail_no_pending))
            .assertExists()
    }
}