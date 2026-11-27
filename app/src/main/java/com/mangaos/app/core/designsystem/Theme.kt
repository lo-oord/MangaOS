package com.mangaos.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * The whole palette lives here. Every screen reads MaterialTheme.colorScheme (plus
 * MangaTheme.tokens for the handful of semantic colours Material does not model), so adding a
 * theme means adding two schemes below and one entry in AppTheme — no screen changes.
 *
 * Design rule for every theme: calm, low-chroma backgrounds; the identity lives in primary,
 * containers and accents. Nothing is a full-screen wash of a character colour.
 */

private val DefaultLight: ColorScheme = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF001A3D),
    inversePrimary = Color(0xFF9CC7FF),
    secondary = Color(0xFF4E6076),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAF2),
    onSecondaryContainer = Color(0xFF0F1A24),
    tertiary = Color(0xFF0E8FB8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6F1FB),
    onTertiaryContainer = Color(0xFF002733),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111820),
    surfaceVariant = Color(0xFFE8EDF4),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F9),
    surfaceContainer = Color(0xFFEFF2F7),
    surfaceContainerHigh = Color(0xFFE9EDF3),
    surfaceContainerHighest = Color(0xFFE3E8F0),
    outline = Color(0xFFCBD3DE),
    outlineVariant = Color(0xFFE2E7EF),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF2A323B),
    inverseOnSurface = Color(0xFFEFF2F7),
)

private val DefaultDark: ColorScheme = darkColorScheme(
    primary = BrandColors.PrimaryDark,
    onPrimary = Color(0xFF00203F),
    primaryContainer = Color(0xFF123B68),
    onPrimaryContainer = Color(0xFFD6E7FF),
    inversePrimary = BrandColors.Primary,
    secondary = Color(0xFFA9BACD),
    onSecondary = Color(0xFF16232E),
    secondaryContainer = Color(0xFF26323D),
    onSecondaryContainer = Color(0xFFDCE6F0),
    tertiary = Color(0xFF5FD3F2),
    onTertiary = Color(0xFF002F3B),
    tertiaryContainer = Color(0xFF11414F),
    onTertiaryContainer = Color(0xFFD0F0FA),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE7EBF0),
    surface = Color(0xFF11151C),
    onSurface = Color(0xFFE7EBF0),
    surfaceVariant = Color(0xFF20262F),
    onSurfaceVariant = Color(0xFFA3AEBC),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF12161D),
    surfaceContainer = Color(0xFF161B23),
    surfaceContainerHigh = Color(0xFF1B212A),
    surfaceContainerHighest = Color(0xFF212833),
    outline = Color(0xFF2C3542),
    outlineVariant = Color(0xFF232B36),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7EBF0),
    inverseOnSurface = Color(0xFF20262F),
)

private val SakuraLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFB83F66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE1E9),
    onPrimaryContainer = Color(0xFF3E0A1C),
    inversePrimary = Color(0xFFF4A0BC),
    secondary = Color(0xFF6B5862),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3E8),
    onSecondaryContainer = Color(0xFF25151C),
    tertiary = Color(0xFF2F8C86),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6F0ED),
    onTertiaryContainer = Color(0xFF002523),
    background = Color(0xFFFDF7F9),
    onBackground = Color(0xFF1D1418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1418),
    surfaceVariant = Color(0xFFF5E7EC),
    onSurfaceVariant = Color(0xFF6B5560),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF7F9),
    surfaceContainer = Color(0xFFFAF0F3),
    surfaceContainerHigh = Color(0xFFF7E9EE),
    surfaceContainerHighest = Color(0xFFF3E3E9),
    outline = Color(0xFFDCC4CE),
    outlineVariant = Color(0xFFEFDDE4),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF33262B),
    inverseOnSurface = Color(0xFFFDF7F9),
)

private val SakuraDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFF4A0BC),
    onPrimary = Color(0xFF4A0E24),
    primaryContainer = Color(0xFF6B2340),
    onPrimaryContainer = Color(0xFFFCE1E9),
    inversePrimary = Color(0xFFB83F66),
    secondary = Color(0xFFD6BCC6),
    onSecondary = Color(0xFF2A1A21),
    secondaryContainer = Color(0xFF3A2830),
    onSecondaryContainer = Color(0xFFF3E3E8),
    tertiary = Color(0xFF7FD6CD),
    onTertiary = Color(0xFF00332F),
    tertiaryContainer = Color(0xFF12433F),
    onTertiaryContainer = Color(0xFFD6F0ED),
    background = Color(0xFF130E11),
    onBackground = Color(0xFFF0E4E9),
    surface = Color(0xFF1A1317),
    onSurface = Color(0xFFF0E4E9),
    surfaceVariant = Color(0xFF2A1F25),
    onSurfaceVariant = Color(0xFFBCA7B1),
    surfaceContainerLowest = Color(0xFF0C080A),
    surfaceContainerLow = Color(0xFF191216),
    surfaceContainer = Color(0xFF1E161A),
    surfaceContainerHigh = Color(0xFF251B20),
    surfaceContainerHighest = Color(0xFF2C2027),
    outline = Color(0xFF45333C),
    outlineVariant = Color(0xFF34262D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF0E4E9),
    inverseOnSurface = Color(0xFF2A1F25),
)

private val ShanaLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFBE4324),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D5),
    onPrimaryContainer = Color(0xFF3D0D00),
    inversePrimary = Color(0xFFFFB59B),
    secondary = Color(0xFF6C574E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E4DA),
    onSecondaryContainer = Color(0xFF241510),
    tertiary = Color(0xFFA5731C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEBC7),
    onTertiaryContainer = Color(0xFF2C1E00),
    background = Color(0xFFFDF8F6),
    onBackground = Color(0xFF1F1512),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1512),
    surfaceVariant = Color(0xFFF4E6DF),
    onSurfaceVariant = Color(0xFF6A574E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF7F4),
    surfaceContainer = Color(0xFFFAF1EC),
    surfaceContainerHigh = Color(0xFFF7EAE3),
    surfaceContainerHighest = Color(0xFFF2E2D9),
    outline = Color(0xFFDCC7BC),
    outlineVariant = Color(0xFFEEDED6),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF352A25),
    inverseOnSurface = Color(0xFFFDF7F4),
)

private val ShanaDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFA97F),
    onPrimary = Color(0xFF4C1200),
    primaryContainer = Color(0xFF6B2410),
    onPrimaryContainer = Color(0xFFFFE0D5),
    inversePrimary = Color(0xFFBE4324),
    secondary = Color(0xFFDCC2B6),
    onSecondary = Color(0xFF2A1A14),
    secondaryContainer = Color(0xFF3B2822),
    onSecondaryContainer = Color(0xFFF3E4DA),
    tertiary = Color(0xFFE8C37A),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF55401A),
    onTertiaryContainer = Color(0xFFFBEBC7),
    background = Color(0xFF130E0C),
    onBackground = Color(0xFFF2E5DF),
    surface = Color(0xFF1A1310),
    onSurface = Color(0xFFF2E5DF),
    surfaceVariant = Color(0xFF2A1F1A),
    onSurfaceVariant = Color(0xFFC0A99E),
    surfaceContainerLowest = Color(0xFF0C0806),
    surfaceContainerLow = Color(0xFF191210),
    surfaceContainer = Color(0xFF1E1613),
    surfaceContainerHigh = Color(0xFF251B17),
    surfaceContainerHighest = Color(0xFF2C211C),
    outline = Color(0xFF46332B),
    outlineVariant = Color(0xFF352620),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF2E5DF),
    inverseOnSurface = Color(0xFF2A1F1A),
)

private val MikuLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF0E8C86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2EE),
    onPrimaryContainer = Color(0xFF00201D),
    inversePrimary = Color(0xFF7FE3DA),
    secondary = Color(0xFF4B6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEFEC),
    onSecondaryContainer = Color(0xFF0C1F1D),
    tertiary = Color(0xFFC94D95),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBD9EA),
    onTertiaryContainer = Color(0xFF3A0A25),
    background = Color(0xFFF5FAFA),
    onBackground = Color(0xFF0E1716),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1716),
    surfaceVariant = Color(0xFFE1EFEE),
    onSurfaceVariant = Color(0xFF526462),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F9F8),
    surfaceContainer = Color(0xFFEDF6F5),
    surfaceContainerHigh = Color(0xFFE6F1F0),
    surfaceContainerHighest = Color(0xFFDFECEB),
    outline = Color(0xFFC4D6D4),
    outlineVariant = Color(0xFFDCE9E8),
    error = Color(0xFFB9362E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF27312F),
    inverseOnSurface = Color(0xFFEFF7F6),
)

private val MikuDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF4FD8CE),
    onPrimary = Color(0xFF00302C),
    primaryContainer = Color(0xFF0E463F),
    onPrimaryContainer = Color(0xFFC9F2EE),
    inversePrimary = Color(0xFF0E8C86),
    secondary = Color(0xFFA6C7C3),
    onSecondary = Color(0xFF14211F),
    secondaryContainer = Color(0xFF253C39),
    onSecondaryContainer = Color(0xFFDCEFEC),
    tertiary = Color(0xFFF09BCB),
    onTertiary = Color(0xFF3A0A25),
    tertiaryContainer = Color(0xFF52203C),
    onTertiaryContainer = Color(0xFFFBD9EA),
    background = Color(0xFF0A1211),
    onBackground = Color(0xFFE3EEED),
    surface = Color(0xFF101B1A),
    onSurface = Color(0xFFE3EEED),
    surfaceVariant = Color(0xFF1E2C2A),
    onSurfaceVariant = Color(0xFF9FB5B2),
    surfaceContainerLowest = Color(0xFF070D0D),
    surfaceContainerLow = Color(0xFF111C1B),
    surfaceContainer = Color(0xFF152221),
    surfaceContainerHigh = Color(0xFF1A2827),
    surfaceContainerHighest = Color(0xFF20302E),
    outline = Color(0xFF2C3E3C),
    outlineVariant = Color(0xFF22322F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE3EEED),
    inverseOnSurface = Color(0xFF1E2C2A),
)

private val NeonLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF6E5AC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DEFF),
    onPrimaryContainer = Color(0xFF24005A),
    inversePrimary = Color(0xFFC3AAFF),
    secondary = Color(0xFF5F5A72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBE6F4),
    onSecondaryContainer = Color(0xFF1C1729),
    tertiary = Color(0xFFB85C8A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCDCEA),
    onTertiaryContainer = Color(0xFF3D0522),
    background = Color(0xFFF8F7FC),
    onBackground = Color(0xFF16121F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16121F),
    surfaceVariant = Color(0xFFEDE8F6),
    onSurfaceVariant = Color(0xFF605A70),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F3FC),
    surfaceContainer = Color(0xFFF2EEFA),
    surfaceContainerHigh = Color(0xFFECE6F6),
    surfaceContainerHighest = Color(0xFFE6DFF2),
    outline = Color(0xFFD3CBE4),
    outlineVariant = Color(0xFFE7E0F2),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF2C2638),
    inverseOnSurface = Color(0xFFF4F0FB),
)

private val NeonDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFA79AE2),
    onPrimary = Color(0xFF2A0060),
    primaryContainer = Color(0xFF3D1A7A),
    onPrimaryContainer = Color(0xFFE9DEFF),
    inversePrimary = Color(0xFF7A3FF2),
    secondary = Color(0xFFBFB8CE),
    onSecondary = Color(0xFF1F1A2A),
    secondaryContainer = Color(0xFF2C2539),
    onSecondaryContainer = Color(0xFFEBE6F4),
    tertiary = Color(0xFFE39CBB),
    onTertiary = Color(0xFF4A0027),
    tertiaryContainer = Color(0xFF5E1440),
    onTertiaryContainer = Color(0xFFFCDCEA),
    background = Color(0xFF08060F),
    onBackground = Color(0xFFEDE9F6),
    surface = Color(0xFF110D1C),
    onSurface = Color(0xFFEDE9F6),
    surfaceVariant = Color(0xFF221B33),
    onSurfaceVariant = Color(0xFFB0A7C4),
    surfaceContainerLowest = Color(0xFF05040A),
    surfaceContainerLow = Color(0xFF120F1D),
    surfaceContainer = Color(0xFF171223),
    surfaceContainerHigh = Color(0xFF1E182C),
    surfaceContainerHighest = Color(0xFF261E36),
    outline = Color(0xFF352C4A),
    outlineVariant = Color(0xFF2A2339),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFEDE9F6),
    inverseOnSurface = Color(0xFF221B33),
)

private val SkyLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E7FC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECFB),
    onPrimaryContainer = Color(0xFF001D33),
    inversePrimary = Color(0xFF8CCAF2),
    secondary = Color(0xFF4C6273),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EDF5),
    onSecondaryContainer = Color(0xFF0C1B26),
    tertiary = Color(0xFF0F919E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2F0F3),
    onTertiaryContainer = Color(0xFF00262A),
    background = Color(0xFFF4F9FD),
    onBackground = Color(0xFF0E1720),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1720),
    surfaceVariant = Color(0xFFE5EEF6),
    onSurfaceVariant = Color(0xFF536472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F8FD),
    surfaceContainer = Color(0xFFEBF4FB),
    surfaceContainerHigh = Color(0xFFE4EFF8),
    surfaceContainerHighest = Color(0xFFDDEAF5),
    outline = Color(0xFFC3D5E3),
    outlineVariant = Color(0xFFDCE8F2),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF26313A),
    inverseOnSurface = Color(0xFFEFF7FD),
)

private val SkyDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF79C0F2),
    onPrimary = Color(0xFF00304F),
    primaryContainer = Color(0xFF14466B),
    onPrimaryContainer = Color(0xFFD6ECFB),
    inversePrimary = Color(0xFF1E7FC4),
    secondary = Color(0xFFA7BDCE),
    onSecondary = Color(0xFF13202A),
    secondaryContainer = Color(0xFF24333F),
    onSecondaryContainer = Color(0xFFE2EDF5),
    tertiary = Color(0xFF63D3DC),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF14515A),
    onTertiaryContainer = Color(0xFFD2F0F3),
    background = Color(0xFF08111A),
    onBackground = Color(0xFFE4EDF5),
    surface = Color(0xFF0F1922),
    onSurface = Color(0xFFE4EDF5),
    surfaceVariant = Color(0xFF1D2A35),
    onSurfaceVariant = Color(0xFF9FB2C2),
    surfaceContainerLowest = Color(0xFF060C12),
    surfaceContainerLow = Color(0xFF101A24),
    surfaceContainer = Color(0xFF14202B),
    surfaceContainerHigh = Color(0xFF19262F),
    surfaceContainerHighest = Color(0xFF1F2E39),
    outline = Color(0xFF2B3B48),
    outlineVariant = Color(0xFF22303B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE4EDF5),
    inverseOnSurface = Color(0xFF1D2A35),
)

private val ForestLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBDD),
    onPrimaryContainer = Color(0xFF082116),
    inversePrimary = Color(0xFF8FCBA8),
    secondary = Color(0xFF54635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EAE4),
    onSecondaryContainer = Color(0xFF111A15),
    tertiary = Color(0xFF8A6A2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E7CC),
    onTertiaryContainer = Color(0xFF2A1F05),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF111814),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111814),
    surfaceVariant = Color(0xFFE7EEE9),
    onSurfaceVariant = Color(0xFF53605A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F7F4),
    surfaceContainer = Color(0xFFEEF3F0),
    surfaceContainerHigh = Color(0xFFE8EFEA),
    surfaceContainerHighest = Color(0xFFE2EBE5),
    outline = Color(0xFFC4D2C9),
    outlineVariant = Color(0xFFDCE7DF),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF27322C),
    inverseOnSurface = Color(0xFFEFF5F1),
)

private val ForestDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FCBA8),
    onPrimary = Color(0xFF00341F),
    primaryContainer = Color(0xFF1B4B36),
    onPrimaryContainer = Color(0xFFD7EBDD),
    inversePrimary = Color(0xFF2F6B4F),
    secondary = Color(0xFFB5C5BA),
    onSecondary = Color(0xFF16211B),
    secondaryContainer = Color(0xFF2A3630),
    onSecondaryContainer = Color(0xFFE2EAE4),
    tertiary = Color(0xFFDCC08A),
    onTertiary = Color(0xFF3B2B08),
    tertiaryContainer = Color(0xFF4E3B15),
    onTertiaryContainer = Color(0xFFF3E7CC),
    background = Color(0xFF0B120E),
    onBackground = Color(0xFFE2EBE6),
    surface = Color(0xFF101914),
    onSurface = Color(0xFFE2EBE6),
    surfaceVariant = Color(0xFF1D2822),
    onSurfaceVariant = Color(0xFFA2B3A9),
    surfaceContainerLowest = Color(0xFF070D0A),
    surfaceContainerLow = Color(0xFF111A15),
    surfaceContainer = Color(0xFF151F19),
    surfaceContainerHigh = Color(0xFF1A251F),
    surfaceContainerHighest = Color(0xFF202C25),
    outline = Color(0xFF2C3A32),
    outlineVariant = Color(0xFF232F28),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE2EBE6),
    inverseOnSurface = Color(0xFF1D2822),
)

private val CoffeeLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF7A5A3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E2D2),
    onPrimaryContainer = Color(0xFF2A1A0A),
    inversePrimary = Color(0xFFD8B591),
    secondary = Color(0xFF6A5A4C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE6DC),
    onSecondaryContainer = Color(0xFF1F1710),
    tertiary = Color(0xFF4E6B57),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEADF),
    onTertiaryContainer = Color(0xFF0B1F13),
    background = Color(0xFFFAF6F1),
    onBackground = Color(0xFF1C1713),
    surface = Color(0xFFFFFDFB),
    onSurface = Color(0xFF1C1713),
    surfaceVariant = Color(0xFFF2E9E0),
    onSurfaceVariant = Color(0xFF615449),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF5EF),
    surfaceContainer = Color(0xFFF6EFE7),
    surfaceContainerHigh = Color(0xFFF1E8DE),
    surfaceContainerHighest = Color(0xFFECE2D7),
    outline = Color(0xFFD6C7B8),
    outlineVariant = Color(0xFFE9DED2),
    error = Color(0xFFC1352C),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF322A24),
    inverseOnSurface = Color(0xFFFAF5EF),
)

private val CoffeeDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFD8B591),
    onPrimary = Color(0xFF3D2A15),
    primaryContainer = Color(0xFF553D25),
    onPrimaryContainer = Color(0xFFF0E2D2),
    inversePrimary = Color(0xFF7A5A3A),
    secondary = Color(0xFFCFBBAB),
    onSecondary = Color(0xFF261D16),
    secondaryContainer = Color(0xFF3A2F26),
    onSecondaryContainer = Color(0xFFEFE6DC),
    tertiary = Color(0xFFA9CDB3),
    onTertiary = Color(0xFF11351F),
    tertiaryContainer = Color(0xFF2C4A37),
    onTertiaryContainer = Color(0xFFDDEADF),
    background = Color(0xFF12100D),
    onBackground = Color(0xFFEDE4DB),
    surface = Color(0xFF191512),
    onSurface = Color(0xFFEDE4DB),
    surfaceVariant = Color(0xFF271F19),
    onSurfaceVariant = Color(0xFFC0B1A3),
    surfaceContainerLowest = Color(0xFF0C0A08),
    surfaceContainerLow = Color(0xFF181411),
    surfaceContainer = Color(0xFF1D1815),
    surfaceContainerHigh = Color(0xFF241E19),
    surfaceContainerHighest = Color(0xFF2B241E),
    outline = Color(0xFF3C322A),
    outlineVariant = Color(0xFF2F2721),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFEDE4DB),
    inverseOnSurface = Color(0xFF271F19),
)

/**
 * AMOLED variant of any dark scheme: a true #000000 canvas with barely-there surface steps so
 * cards still read as separate layers. The theme's accent, text colours and errors are kept,
 * so every theme works on an AMOLED screen with its own identity.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF141414),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1B1B1B),
    outline = Color(0xFF2E2E2E),
    outlineVariant = Color(0xFF1E1E1E),
    inverseSurface = onSurface,
    inverseOnSurface = Color(0xFF141414),
)

/** Resolves the scheme for a theme and mode pair, including the AMOLED override. */
fun colorSchemeFor(theme: AppTheme, dark: Boolean, amoled: Boolean = false): ColorScheme {
    val scheme = when (theme) {
        AppTheme.DEFAULT -> if (dark) DefaultDark else DefaultLight
        AppTheme.SAKURA -> if (dark) SakuraDark else SakuraLight
        AppTheme.SHANA -> if (dark) ShanaDark else ShanaLight
        AppTheme.MIKU -> if (dark) MikuDark else MikuLight
        AppTheme.NEON -> if (dark) NeonDark else NeonLight
        AppTheme.SKY -> if (dark) SkyDark else SkyLight
        AppTheme.FOREST -> if (dark) ForestDark else ForestLight
        AppTheme.COFFEE -> if (dark) CoffeeDark else CoffeeLight
    }
    return if (amoled) scheme.toAmoled() else scheme
}

/** The three swatches shown on a theme preview: canvas, elevated surface and accent. */
data class ThemeSwatches(val background: Color, val surface: Color, val accent: Color)

fun themeSwatches(theme: AppTheme, dark: Boolean): ThemeSwatches =
    colorSchemeFor(theme, dark).let { ThemeSwatches(it.background, it.surfaceContainerHigh, it.primary) }

/**
 * Semantic colours Material's ColorScheme does not model. Everything is derived from the
 * active scheme, so a new theme cannot forget one of them.
 */
data class ThemeTokens(
    val success: Color,
    val warning: Color,
    val rating: Color,
    val elevatedSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val disabled: Color,
    val readerBackground: Color,
    val readerControls: Color,
    val selection: Color,
    val progress: Color,
)

fun themeTokens(scheme: ColorScheme, dark: Boolean, amoled: Boolean): ThemeTokens = ThemeTokens(
    success = if (dark) Color(0xFF4DCB8C) else BrandColors.Ongoing,
    warning = if (dark) Color(0xFFE6BE6A) else BrandColors.Hiatus,
    rating = BrandColors.Rating,
    elevatedSurface = scheme.surfaceContainerHigh,
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    divider = scheme.outlineVariant,
    disabled = scheme.onSurface.copy(alpha = 0.38f),
    readerBackground = if (amoled) Color.Black else scheme.background,
    readerControls = if (amoled) Color(0xFF111111) else scheme.surfaceContainerHigh,
    selection = scheme.primary.copy(alpha = 0.16f),
    progress = scheme.primary,
)

val LocalThemeTokens = staticCompositionLocalOf {
    themeTokens(DefaultLight, dark = false, amoled = false)
}

/** Short accessor so screens read `MangaTheme.tokens.success` next to MaterialTheme colours. */
object MangaTheme {
    val tokens: ThemeTokens
        @Composable
        get() = LocalThemeTokens.current
}

@Composable
fun MangaOsTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    dark: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(theme, dark, amoled) { colorSchemeFor(theme, dark, amoled) }
    val tokens = remember(colorScheme, dark, amoled) { themeTokens(colorScheme, dark, amoled) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MangaTypography,
            shapes = MangaShapes,
            content = content,
        )
    }
}
