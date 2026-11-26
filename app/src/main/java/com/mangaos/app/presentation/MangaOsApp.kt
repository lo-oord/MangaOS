package com.mangaos.app.presentation

import android.app.Activity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.MangaOsTheme
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.presentation.navigation.RootNavHost

@Composable
fun MangaOsApp() {
    val context = LocalContext.current
    val settings = ServiceLocator.settings

    val language by settings.language.collectAsStateWithLifecycle(
        initialValue = LocaleManager.readLanguage(context),
    )
    val theme by settings.theme.collectAsStateWithLifecycle(initialValue = AppTheme.DEFAULT)
    val darkModePreference by settings.darkMode.collectAsStateWithLifecycle(initialValue = null)

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
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
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
