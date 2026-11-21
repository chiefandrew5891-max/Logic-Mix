package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import io.github.chiefandrew5891max.logicmix.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Nunito (SIL OFL 1.1, see licenses/Nunito-OFL.txt): rounded game face. CJK glyphs fall back to the system font. */
@OptIn(ExperimentalTextApi::class)
private val Display = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
    ).map { w -> Font(R.font.nunito, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
)

private val Base = Typography()

internal val LogicMixTypography = Typography(
    displayMedium = Base.displayMedium.copy(fontFamily = Display),
    displaySmall = Base.displaySmall.copy(fontFamily = Display),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    bodySmall = Base.bodySmall.copy(fontFamily = Display),
    labelSmall = Base.labelSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
)
