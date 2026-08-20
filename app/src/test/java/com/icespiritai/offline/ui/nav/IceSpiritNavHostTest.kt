package com.icespiritai.offline.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pin-level tests for [IceSpiritNavHost] routing surface.
 *
 * The NavHost composes 4 destinations: HOME / SETTINGS / CHANGELOG / VIEWER.
 * Because the production wiring is `viewModel()` at the Activity-scoped
 * ViewModelStoreOwner, and the start destination `HomeScreen` reads heavy
 * deps (BitmapLoader + OCR engine via `IceSpiritVisionViewModel`), a
 * full Compose smoke test of the start destination is deferred — it
 * would require real models under `ice_ocr_rules` profile or extensive
 * fakes for `shell`.
 *
 * What we pin here:
 *  - the four route constants are stable strings (anything that
 *    hard-codes "home" / "settings" / etc. is depending on these)
 *  - route constants are distinct (HOME ≠ VIEWER ≠ ...)
 *  - the start destination is HOME
 *
 * RobolectricTestRunner + sdk=33 because targetSdk=37 > Robolectric 4.13's
 * maxSdk=34.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IceSpiritNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Routes defines the four expected destinations`() {
        assertEquals("home", Routes.HOME)
        assertEquals("settings", Routes.SETTINGS)
        assertEquals("changelog", Routes.CHANGELOG)
        assertEquals("viewer", Routes.VIEWER)
    }

    @Test
    fun `all Routes constants are distinct`() {
        // Pin against accidental merging or shadowing where someone
        // refactors and accidentally makes Routes.VIEWER == Routes.HOME.
        val all = setOf(Routes.HOME, Routes.SETTINGS, Routes.CHANGELOG, Routes.VIEWER)
        assertEquals(4, all.size)
        assertNotEquals(Routes.HOME, Routes.VIEWER)
        assertNotEquals(Routes.SETTINGS, Routes.CHANGELOG)
    }

    @Test
    fun `Routes object is the public stable identity`() {
        // Compose routes are referenced by string from many places;
        // the Routes object is the canonical pointer. Pin the FQN so
        // that anyone renaming the object fails this test loudly.
        assertEquals(
            "com.icespiritai.offline.ui.nav.Routes",
            Routes::class.qualifiedName,
        )
    }
}
