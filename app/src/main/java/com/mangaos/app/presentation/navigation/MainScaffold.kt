package com.mangaos.app.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mangaos.app.R
import com.mangaos.app.core.ui.components.AppDrawer
import com.mangaos.app.core.ui.components.AppTopBar
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                profile = profile,
                unreadNotifications = unreadNotifications,
                currentRoute = currentRoute,
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
                )
            }
            MainNavHost(
                navController = navController,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
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
private fun MainNavHost(navController: NavHostController, modifier: Modifier) {
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
            LatestScreenRoute(navController)
        }
        composable(DrawerDestination.MANGA.route) {
            MangaListScreenRoute(navController)
        }
        composable(DrawerDestination.LIBRARY.route) {
            LibraryScreenRoute(navController)
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
        composable(Routes.MANGA_DETAIL) { entry ->
            com.mangaos.app.presentation.detail.MangaDetailScreen(
                mangaId = entry.arguments?.getString("mangaId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun LatestScreenRoute(navController: NavHostController) {
    com.mangaos.app.presentation.latest.LatestScreen(
        onMangaClick = { navController.navigate(Routes.mangaDetail(it.id)) },
    )
}

@Composable
private fun MangaListScreenRoute(navController: NavHostController) {
    com.mangaos.app.presentation.manga.MangaListScreen(
        onMangaClick = { navController.navigate(Routes.mangaDetail(it.id)) },
    )
}

@Composable
private fun LibraryScreenRoute(navController: NavHostController) {
    com.mangaos.app.presentation.library.LibraryScreen(
        onMangaClick = { navController.navigate(Routes.mangaDetail(it.id)) },
        onBrowse = {
            navController.navigate(DrawerDestination.MANGA.route) {
                popUpTo(DrawerDestination.startRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}
