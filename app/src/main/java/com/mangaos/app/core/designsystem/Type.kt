package com.mangaos.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mangaos.app.R

/**
 * Noto Sans Arabic carries the Arabic and Latin glyphs; Japanese falls back to the
 * platform CJK font per glyph, which keeps a calm, consistent text colour everywhere.
 */
val MangaFontFamily = FontFamily(
    Font(R.font.noto_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.noto_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_arabic_bold, FontWeight.Bold),
)

private val base = Typography()

val MangaTypography: Typography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = base.titleMedium.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = MangaFontFamily, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = MangaFontFamily, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontFamily = MangaFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = base.labelMedium.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = base.labelSmall.copy(fontFamily = MangaFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
