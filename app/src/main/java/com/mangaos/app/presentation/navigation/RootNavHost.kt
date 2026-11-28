package com.mangaos.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.presentation.auth.AuthScreen
import com.mangaos.app.presentation.onboarding.OnboardingScreen
import com.mangaos.app.presentation.splash.SplashDestination
import com.mangaos.app.presentation.splash.SplashScreen

/**
 * The launch order is fixed: splash, then the first-launch wizard exactly once, then
 * authentication when there is no session, then the home screens. Each hand-off replaces its
 * predecessor, so back never walks into a wizard that was already completed.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val profile by ServiceLocator.user.profile.collectAsStateWithLifecycle()
    val unreadNotifications by ServiceLocator.notifications.unreadCount.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val transitionMillis = if (reduceMotion) 0 else 220

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
        enterTransition = { fadeIn(tween(transitionMillis)) },
        exitTransition = { fadeOut(tween(if (reduceMotion) 0 else 180)) },
        popEnterTransition = { fadeIn(tween(transitionMillis)) },
        popExitTransition = { fadeOut(tween(if (reduceMotion) 0 else 180)) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = { destination ->
                    val target = when (destination) {
                        SplashDestination.ONBOARDING -> Routes.ONBOARDING
                        SplashDestination.AUTH -> Routes.AUTH
                        SplashDestination.MAIN -> Routes.MAIN
                    }
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                onSignedIn = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MAIN) {
            MainScaffold(
                profile = profile,
                unreadNotifications = unreadNotifications,
            )
        }
    }
}
