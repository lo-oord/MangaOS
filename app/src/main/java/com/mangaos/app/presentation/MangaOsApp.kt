package com.mangaos.app.presentation

import android.Manifest
import android.content.res.Configuration
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.MangaOsTheme
import com.mangaos.app.core.designsystem.ThemeMode
import com.mangaos.app.core.designsystem.ThemeReveal
import com.mangaos.app.core.designsystem.colorSchemeFor
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.LanguageTransition
import com.mangaos.app.core.ui.components.LanguageTransitionOverlay
import com.mangaos.app.core.util.LocalizedContext
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.core.util.findActivity
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.presentation.navigation.RootNavHost
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.hypot

/** The palette actually on screen; it catches up with the preference at the end of a reveal. */
private data class AppliedTheme(
    val theme: AppTheme,
    val dark: Boolean,
    val amoled: Boolean,
)

private data class RevealOverlay(
    val center: Offset,
    val color: Color,
    val accent: Color,
)

@Composable
fun MangaOsApp() {
    val context = LocalContext.current
    val settings = ServiceLocator.settings

    // Download progress and new chapters are Android notifications, so ask once on Android 13+
    // instead of silently posting notifications the system will drop.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val language by settings.language.collectAsStateWithLifecycle(
        initialValue = LocaleManager.readLanguage(context),
    )
    val theme by settings.theme.collectAsStateWithLifecycle(initialValue = AppTheme.DEFAULT)
    val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val appSettings by settings.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val systemDark = isSystemInDarkTheme()
    val target = AppliedTheme(
        theme = theme,
        dark = themeMode.resolveDark(systemDark),
        amoled = themeMode.isAmoled,
    )

    // The theme is switched in composition, never by recreating the Activity, so every screen,
    // scroll position, field and open sheet survives the change.
    var applied by remember { mutableStateOf(target) }
    var overlay by remember { mutableStateOf<RevealOverlay?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val expansion = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(target) {
        // The origin is only set by a theme/mode row tap. Anything else (the value arriving from
        // storage at launch, the system switching to dark) applies instantly, without a reveal.
        val origin = ThemeReveal.consume()
        if (target == applied) return@LaunchedEffect
        if (origin == null || appSettings.reduceMotion || overlay != null) {
            // No tap origin, reduced motion, or a second tap while a reveal is running: settle
            // immediately rather than play two transitions on top of each other.
            applied = target
            overlay = null
            return@LaunchedEffect
        }
        val scheme = colorSchemeFor(target.theme, target.dark, target.amoled)
        val center = origin
        overlay = RevealOverlay(center = center, color = scheme.background, accent = scheme.primary)
        expansion.snapTo(0f)
        overlayAlpha.snapTo(1f)
        expansion.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
        // Swap the palette while the new canvas fully covers the old one, then fade the cover.
        applied = target
        overlayAlpha.animateTo(0f, tween(durationMillis = 140))
        overlay = null
    }

    // Language changes are applied in place: a localized context re-renders every label and the
    // layout direction flips with it, so the open screen, its scroll position and the reader's
    // page all survive the switch and the app is never restarted.
    val languageSwitch by LanguageTransition.pending.collectAsStateWithLifecycle()
    val locale = remember(language) { LocaleManager.displayLocale(language) }
    val localizedContext = remember(language, context) { LocalizedContext.of(context, language) }
    val layoutDirection = if (LocaleManager.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr

    SideEffect {
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
        val activity = context.findActivity() ?: return@SideEffect
        val resources = activity.resources
        if (resources.configuration.locales.get(0) == locale) return@SideEffect
        val configuration = Configuration(resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    // The overlay stays up until the requested language is actually the active one, then leaves.
    LaunchedEffect(languageSwitch, language) {
        val pending = languageSwitch ?: return@LaunchedEffect
        if (pending.code != language) return@LaunchedEffect
        delay(620)
        LanguageTransition.finish(pending.code)
    }

    MangaOsTheme(theme = applied.theme, dark = applied.dark, amoled = applied.amoled) {
        val snackbarHostState = remember { SnackbarHostState() }
        CompositionLocalProvider(
            LocalSnackbarHostState provides snackbarHostState,
            LocalReduceMotion provides appSettings.reduceMotion,
            LocalContext provides localizedContext,
            LocalLayoutDirection provides layoutDirection,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootSize = it },
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    RootNavHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }

                languageSwitch?.let { pending ->
                    LanguageTransitionOverlay(switch = pending)
                }

                val reveal = overlay
                if (reveal != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val farthestX = maxOf(reveal.center.x, size.width - reveal.center.x)
                        val farthestY = maxOf(reveal.center.y, size.height - reveal.center.y)
                        val maxRadius = hypot(farthestX.toDouble(), farthestY.toDouble()).toFloat()
                        val radius = expansion.value * maxRadius
                        val alpha = overlayAlpha.value
                        drawCircle(
                            color = reveal.color.copy(alpha = alpha),
                            radius = radius,
                            center = reveal.center,
                        )
                        if (expansion.value < 1f) {
                            // A thin accent edge makes the spreading palette legible.
                            drawCircle(
                                color = reveal.accent.copy(alpha = 0.45f * (1f - expansion.value) * alpha),
                                radius = radius,
                                center = reveal.center,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}
