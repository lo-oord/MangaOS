package com.mangaos.app.presentation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.MangaOsTheme
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.presentation.navigation.RootNavHost

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
    val darkModePreference by settings.darkMode.collectAsStateWithLifecycle(initialValue = null)
    val appSettings by settings.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val darkTheme = darkModePreference ?: isSystemInDarkTheme()

    // Language changes rebuild the Activity so every resource and the layout direction
    // switch together; the preference itself is already persisted.
    var lastLanguage by remember { mutableStateOf(language) }
    LaunchedEffect(language) {
        if (language != lastLanguage) {
            lastLanguage = language
            (context as? Activity)?.recreate()
        }
    }

    MangaOsTheme(theme = theme, darkTheme = darkTheme) {
        val snackbarHostState = remember { SnackbarHostState() }
        CompositionLocalProvider(
            LocalSnackbarHostState provides snackbarHostState,
            LocalReduceMotion provides appSettings.reduceMotion,
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
        }
    }
}
