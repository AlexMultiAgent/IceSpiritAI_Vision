package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin IceSpiritAI_Vision's `IceRadius*` corner-radius tokens to the
 * IceSpiritAI_Chat `ice_radius_*` family (per spec §3.3). Aligned to:
 *   ice_radius_card   12dp
 *   ice_radius_chip   16dp
 *   ice_radius_dialog 20dp
 *   ice_radius_pill   24dp
 */
class ShapeTokensTest {

    @Test fun iceRadiusCard() = assertEquals(12.dp, IceRadiusCard)
    @Test fun iceRadiusChip() = assertEquals(16.dp, IceRadiusChip)
    @Test fun iceRadiusDialog() = assertEquals(20.dp, IceRadiusDialog)
    @Test fun iceRadiusPill() = assertEquals(24.dp, IceRadiusPill)

    @Test fun iceSpiritShapes_extraSmall_isChipRadius() {
        assertEquals(RoundedCornerShape(16.dp), IceSpiritShapes.extraSmall)
    }

    @Test fun iceSpiritShapes_small_isCardRadius() {
        assertEquals(RoundedCornerShape(12.dp), IceSpiritShapes.small)
    }

    @Test fun iceSpiritShapes_medium_isCardRadius() {
        assertEquals(RoundedCornerShape(12.dp), IceSpiritShapes.medium)
    }

    @Test fun iceSpiritShapes_large_isDialogRadius() {
        assertEquals(RoundedCornerShape(20.dp), IceSpiritShapes.large)
    }

    @Test fun iceSpiritShapes_extraLarge_isPillRadius() {
        assertEquals(RoundedCornerShape(24.dp), IceSpiritShapes.extraLarge)
    }
}