package com.icespiritai.offline.ui.auth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.R
import com.icespiritai.offline.auth.LicenseState
import com.icespiritai.offline.auth.LicenseStore
import com.icespiritai.offline.auth.LicenseViewModel
import com.icespiritai.offline.auth.VerifyResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LicenseGateScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun makeVm(): LicenseViewModel {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return LicenseViewModel(LicenseStore(ctx)) { 20260101L }
    }

    @Test
    fun shows_username_and_code_fields() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val vm = makeVm()
        composeTestRule.setContent { LicenseGateScreen(vm = vm) }
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_gate_username_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_gate_code_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_gate_login)).assertIsDisplayed()
    }

    @Test
    fun shows_signature_mismatch_error_when_state_is_error() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val vm = makeVm()
        composeTestRule.setContent { LicenseGateScreen(vm = vm) }
        vm.forceStateForTest(LicenseState.Error(VerifyResult.SignatureMismatch))
        composeTestRule.onNodeWithText(ctx.getString(R.string.license_error_signature_mismatch)).assertIsDisplayed()
    }
}
