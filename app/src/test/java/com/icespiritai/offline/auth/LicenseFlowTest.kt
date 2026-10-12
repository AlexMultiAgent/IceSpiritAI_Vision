package com.icespiritai.offline.auth

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.IceSpiritVisionActivity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * LicenseFlowTest — end-to-end smoke for the activity-level license gate
 * (Task 10). Boots [IceSpiritVisionActivity] under Robolectric and asserts
 * the activity reaches a stable composition without crashing.
 *
 * Why this test exists: the entry-point change is the highest-risk edit
 * in the auth pipeline. If the gate wiring is wrong, the app crashes on
 * every cold launch — and the unit tests for [LicenseViewModel] alone
 * wouldn't catch it, because they never start a real Activity.
 *
 * What we intentionally do NOT assert here:
 * - That LicenseGateScreen is visible (the gate shows the username/code
 *   fields when state != Active, but UI assertion under Robolectric on a
 *   real Activity is flaky vs a `createComposeRule` test; the
 *   [com.icespiritai.offline.ui.auth.LicenseGateScreenTest] covers that
 *   path on an isolated composition).
 * - That NavHost is NOT composed — same reasoning. We trust the
 *   `if (licenseState !is LicenseState.Active) LicenseGateScreen(vm)`
 *   branch on the entry-point edit; this test pins "the activity
 *   launches" as the regression gate.
 *
 * Robolectric `@Config(sdk = [33])` matches the rest of the auth suite.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LicenseFlowTest {

    @Test
    fun first_launch_with_no_license_does_not_crash() {
        // Clean DataStore so a stale code from a prior test (or a
        // developer-side install) can't flip us to LicenseState.Active
        // and skip past the gate — we want the unlicensed path.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { LicenseStore(ctx).clear() }
        ActivityScenario.launch(IceSpiritVisionActivity::class.java).use { /* 启动即通过 */ }
    }

    @Test
    fun activity_launches_smoke() {
        // Same intent, no pre-clean. Useful as a second sample if the
        // first test's DataStore clear path masks an init-time crash.
        ActivityScenario.launch(IceSpiritVisionActivity::class.java).use { /* 启动即通过 */ }
    }
}
