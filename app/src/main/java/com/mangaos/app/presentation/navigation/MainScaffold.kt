package com.mangaos.app.presentation.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mangaos.app.R
import com.mangaos.app.core.ui.components.AppDrawer
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppTopBar
import com.mangaos.app.core.ui.components.ViewModeToggle
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.UserProfile
import com.mangaos.app.presentation.settings.SettingsSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    profile: UserProfile?,
    unreadNotifications: Int,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerDestination = DrawerDestination.fromRoute(currentRoute)
    val isDrawerRoot = drawerDestination != null

    val viewMode by ServiceLocator.settings.viewMode.collectAsStateWithLifecycle(
        initialValue = MangaViewMode.GRID,
    )
    val showsViewToggle = currentRoute in listOf(
        DrawerDestination.LATEST.route,
        DrawerDestination.MANGA.route,
        DrawerDestination.LIBRARY.route,
    )
    val appSettings by ServiceLocator.settings.appSettings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    var showExitDialog by remember { mutableStateOf(false) }
    var restoredDestination by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        val preferences = ServiceLocator.settings.appSettings.first()
        if (preferences.restoreLastScreen) {
            val saved = ServiceLocator.settings.lastDestination.first()
            if (saved != null && DrawerDestination.fromRoute(saved) != null && saved != currentRoute) {
                navController.navigate(saved) {
                    popUpTo(DrawerDestination.startRoute) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        restoredDestination = true
    }

    LaunchedEffect(currentRoute, restoredDestination) {
        if (restoredDestination && isDrawerRoot && currentRoute != null) {
            ServiceLocator.settings.setLastDestination(currentRoute)
        }
    }

    BackHandler(enabled = isDrawerRoot && appSettings.confirmExit) {
        showExitDialog = true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A drawer never covers the whole screen: 84% wide, at most 360dp.
        val drawerWidth = minOf(maxWidth * 0.84f, 360.dp)

        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.42f),
            drawerContent = {
                AppDrawer(
                    profile = profile,
                    unreadNotifications = unreadNotifications,
                    currentRoute = currentRoute,
                    modifier = Modifier.width(drawerWidth),
                    onNavigate = { target ->
                        scope.launch { drawerState.close() }
                        if (currentRoute != target.route) {
                            navController.navigate(target.route) {
                                popUpTo(DrawerDestination.startRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onNotificationsClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                    },
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (currentRoute != Routes.MANGA_DETAIL && currentRoute?.startsWith("reader") != true) {
                    AppTopBar(
                        title = topBarTitle(currentRoute),
                        navigationIcon = if (isDrawerRoot) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack,
                        navigationDescription = stringResource(
                            if (isDrawerRoot) R.string.cd_menu else R.string.cd_back,
                        ),
                        onNavigationClick = {
                            if (isDrawerRoot) {
                                scope.launch { drawerState.open() }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        actions = {
                            if (showsViewToggle) {
                                ViewModeToggle(
                                    viewMode = viewMode,
                                    onToggle = {
                                        scope.launch {
                                            ServiceLocator.settings.setViewMode(viewMode.toggled())
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
                MainNavHost(
                    navController = navController,
                    viewMode = viewMode,
                    reduceMotion = appSettings.reduceMotion,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }

    if (showExitDialog) {
        AppDialog(
            title = stringResource(R.string.exit_confirm_title),
            message = stringResource(R.string.exit_confirm_message),
            confirmLabel = stringResource(R.string.action_exit),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showExitDialog = false
                activity?.finish()
            },
            onDismiss = { showExitDialog = false },
        )
    }
}

@Composable
private fun topBarTitle(route: String?): String = when (route) {
    Routes.NOTIFICATIONS -> stringResource(R.string.notifications_title)
    Routes.PRIVACY -> stringResource(R.string.about_privacy)
    Routes.TERMS -> stringResource(R.string.about_terms)
    else -> SettingsSection.fromRoute(route)?.let { stringResource(it.titleRes) }
        ?: DrawerDestination.fromRoute(route)?.let { stringResource(it.labelRes) }.orEmpty()
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    viewMode: MangaViewMode,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val navScope = rememberCoroutineScope()
    val transitionMillis = if (reduceMotion) 0 else 200
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = DrawerDestination.startRoute,
        modifier = modifier,
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(transitionMillis)) },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduceMotion) 0 else 160)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(transitionMillis)) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduceMotion) 0 else 160)) },
    ) {
        composable(DrawerDestination.LATEST.route) {
            com.mangaos.app.presentation.latest.LatestScreen(
                viewMode = viewMode,
                onMangaClick = { navController.navigate(Routes.mangaDetail(it.sourceId, it.id)) },
                onOpenSources = { navController.navigate(DrawerDestination.SOURCES.route) },
            )
        }
        composable(DrawerDestination.MANGA.route) {
            com.mangaos.app.presentation.manga.MangaListScreen(
                viewMode = viewMode,
                onMangaClick = { navController.navigate(Routes.mangaDetail(it.sourceId, it.id)) },
                onOpenSources = { navController.navigate(DrawerDestination.SOURCES.route) },
            )
        }
        composable(DrawerDestination.LIBRARY.route) {
            com.mangaos.app.presentation.library.LibraryScreen(
                viewMode = viewMode,
                onMangaClick = { navController.navigate(Routes.mangaDetail(it.sourceId, it.id)) },
                onBrowse = {
                    navController.navigate(DrawerDestination.MANGA.route) {
                        popUpTo(DrawerDestination.startRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(DrawerDestination.SOURCES.route) {
            com.mangaos.app.presentation.sources.SourcesScreen(
                onOpenSource = { source ->
                    navScope.launch { ServiceLocator.settings.setLatestSourceId(source.id) }
                    navController.navigate(DrawerDestination.LATEST.route) {
                        popUpTo(DrawerDestination.startRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(DrawerDestination.DOWNLOADS.route) {
            com.mangaos.app.presentation.downloads.DownloadsScreen()
        }
        composable(DrawerDestination.STORAGE.route) {
            com.mangaos.app.presentation.storage.LocalStorageScreen()
        }
        composable(DrawerDestination.SETTINGS.route) {
            com.mangaos.app.presentation.settings.SettingsScreen(
                onOpenSection = { section -> navController.navigate(section.route) },
            )
        }
        SettingsSection.entries.forEach { section ->
            composable(section.route) {
                com.mangaos.app.presentation.settings.SettingsDetailScreen(
                    section = section,
                    onOpenDownloads = {
                        navController.navigate(DrawerDestination.DOWNLOADS.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        composable(DrawerDestination.ABOUT.route) {
            com.mangaos.app.presentation.about.AboutScreen(
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onTerms = { navController.navigate(Routes.TERMS) },
            )
        }
        composable(Routes.NOTIFICATIONS) {
            com.mangaos.app.presentation.notifications.NotificationsScreen()
        }
        composable(Routes.PRIVACY) {
            com.mangaos.app.presentation.about.LegalPlaceholderScreen()
        }
        composable(Routes.TERMS) {
            com.mangaos.app.presentation.about.LegalPlaceholderScreen()
        }
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument(Routes.ARG_SOURCE_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_MANGA_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_CHAPTER_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val sourceId = Uri.decode(entry.arguments?.getString(Routes.ARG_SOURCE_ID).orEmpty())
            val mangaId = Uri.decode(entry.arguments?.getString(Routes.ARG_MANGA_ID).orEmpty())
            val chapterId = Uri.decode(entry.arguments?.getString(Routes.ARG_CHAPTER_ID).orEmpty())
            com.mangaos.app.presentation.reader.ReaderScreen(
                sourceId = sourceId,
                mangaId = mangaId,
                chapterId = chapterId,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.READER_SETTINGS) },
            )
        }
        composable(Routes.READER_SETTINGS) {
            com.mangaos.app.presentation.reader.ReaderSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.MANGA_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_SOURCE_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_MANGA_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val sourceId = Uri.decode(entry.arguments?.getString(Routes.ARG_SOURCE_ID).orEmpty())
            val mangaId = Uri.decode(entry.arguments?.getString(Routes.ARG_MANGA_ID).orEmpty())
            com.mangaos.app.presentation.detail.MangaDetailScreen(
                sourceId = sourceId,
                mangaId = mangaId,
                onBack = { navController.popBackStack() },
                onOpenSources = { navController.navigate(DrawerDestination.SOURCES.route) },
                onOpenChapter = { chapter ->
                    navController.navigate(Routes.reader(sourceId, mangaId, chapter.id))
                },
            )
        }
    }
}
