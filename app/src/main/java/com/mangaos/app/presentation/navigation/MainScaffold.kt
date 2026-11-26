package com.mangaos.app.presentation.navigation

import android.net.Uri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.mangaos.app.core.ui.components.AppTopBar
import com.mangaos.app.core.ui.components.ViewModeToggle
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.UserProfile
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
                if (currentRoute != Routes.MANGA_DETAIL) {
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun topBarTitle(route: String?): String = when (route) {
    Routes.NOTIFICATIONS -> stringResource(R.string.notifications_title)
    Routes.PRIVACY -> stringResource(R.string.about_privacy)
    Routes.TERMS -> stringResource(R.string.about_terms)
    else -> DrawerDestination.fromRoute(route)?.let { stringResource(it.labelRes) }.orEmpty()
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    viewMode: MangaViewMode,
    modifier: Modifier,
) {
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = DrawerDestination.startRoute,
        modifier = modifier,
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) },
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
            com.mangaos.app.presentation.sources.SourcesScreen()
        }
        composable(DrawerDestination.DOWNLOADS.route) {
            com.mangaos.app.presentation.downloads.DownloadsScreen()
        }
        composable(DrawerDestination.STORAGE.route) {
            com.mangaos.app.presentation.storage.LocalStorageScreen()
        }
        composable(DrawerDestination.SETTINGS.route) {
            com.mangaos.app.presentation.settings.SettingsScreen()
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
            )
        }
    }
}
