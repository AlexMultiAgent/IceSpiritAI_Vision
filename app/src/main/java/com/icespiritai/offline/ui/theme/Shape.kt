package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * IceChat family corner-radius tokens — aligned 1:1 with
 * IceSpiritAI_Chat's `ice_radius_*` dimens (per spec §3.3). Single source
 * of truth for shape sizes; pin test in `ShapeTokensTest`.
 *
 *   ice_radius_card   12dp — surface / card
 *   ice_radius_chip   16dp — Chip widget
 *   ice_radius_dialog 20dp — AlertDialog / BottomSheet
 *   ice_radius_pill   24dp — pill / capsule surfaces
 */
val IceRadiusCard: Dp = 12.dp
val IceRadiusChip: Dp = 16.dp
val IceRadiusDialog: Dp = 20.dp
val IceRadiusPill: Dp = 24.dp

internal val IceSpiritShapes = Shapes(
    extraSmall = RoundedCornerShape(IceRadiusChip),
    small      = RoundedCornerShape(IceRadiusCard),
    medium     = RoundedCornerShape(IceRadiusCard),
    large      = RoundedCornerShape(IceRadiusDialog),
    extraLarge = RoundedCornerShape(IceRadiusPill),
)
