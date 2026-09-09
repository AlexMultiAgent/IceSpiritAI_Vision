package com.icespiritai.offline.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Editorial typography stack.
 *
 * v0.1.67 audit fix: previously every [TextStyle] in [IceSpiritTypography]
 * omitted `fontFamily`, so all text resolved to the platform default
 * (Roboto on most Android devices). The DisclaimerDialog KDoc claimed
 * "Source Han Serif SC Bold 经 MaterialTheme 透传" — that claim was a
 * lie. The Editorial design language called for Source Han Serif SC
 * (a serif CJK face), but the project never bundled the OTF.
 *
 * **Trade-off**: shipping a single static Source Han Serif SC OTF costs
 * 10–24 MB APK; the variable-font subset TTF (Noto Serif SC VF) costs
 * ~14 MB; Adobe's full 7-weight static SC pack is ~80 MB. None of
 * those fit a hardening-pass APK budget (current ~58 MB), and the
 * project's CN network frequently resets large-OTF downloads (curl
 * fails at ~21 s on github.com raw downloads from this host).
 *
 * **v0.1.67 compromise uses [FontFamily.Serif]** — the platform serif
 * (Noto Serif on AOSP / Huawei EMUI), which gives Latin Editorial
 * serif styling and CJK falls back to system CJK fonts. The
 * infrastructure is now in place so dropping in a real
 * `app/src/main/res/font/source_han_serif_sc.ttf` later is a 4-line
 * change (replace the [EditorialFontFamily] body with
 * `FontFamily(Font(R.font.source_han_serif_sc, FontVariation.weight(400)), ...)`
 * — no call-site changes needed, every [TextStyle] already reads
 * [EditorialFontFamily]).
 */
val EditorialFontFamily: FontFamily = FontFamily.Serif

val IceSpiritTypography = Typography(
    displaySmall = TextStyle(fontFamily = EditorialFontFamily, fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = EditorialFontFamily, fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = EditorialFontFamily, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = EditorialFontFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = EditorialFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = EditorialFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = EditorialFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = EditorialFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = EditorialFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = EditorialFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = EditorialFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
