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
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.presentation.auth.AccountChoiceScreen
import com.mangaos.app.presentation.auth.ProfileSetupScreen
import com.mangaos.app.presentation.auth.SignInScreen
import com.mangaos.app.presentation.auth.SignUpScreen
import com.mangaos.app.presentation.auth.VerifyEmailScreen
import com.mangaos.app.presentation.auth.rememberAuthViewModel
import com.mangaos.app.presentation.splash.SplashDestination
import com.mangaos.app.presentation.splash.SplashScreen

@Composable
fun RootNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val profile by ServiceLocator.user.profile.collectAsStateWithLifecycle()
    val unreadNotifications by ServiceLocator.notifications.unreadCount.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = { destination ->
                    val target = if (destination == SplashDestination.MAIN) Routes.MAIN else Routes.AUTH_CHOICE
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.AUTH_CHOICE) {
            val authViewModel = rememberAuthViewModel()
            AccountChoiceScreen(
                onCreateAccount = { navController.navigate(Routes.AUTH_SIGN_UP) },
                onSignIn = { navController.navigate(Routes.AUTH_SIGN_IN) },
                onGuest = {
                    authViewModel.continueAsGuest {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.AUTH_CHOICE) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.AUTH_SIGN_UP) {
            val authViewModel = rememberAuthViewModel()
            SignUpScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.AUTH_PROFILE) },
            )
        }

        composable(Routes.AUTH_PROFILE) {
            val authViewModel = rememberAuthViewModel()
            ProfileSetupScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.AUTH_VERIFY) },
            )
        }

        composable(Routes.AUTH_VERIFY) {
            val authViewModel = rememberAuthViewModel()
            VerifyEmailScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH_CHOICE) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.AUTH_SIGN_IN) {
            val authViewModel = rememberAuthViewModel()
            SignInScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onSignedIn = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH_CHOICE) { inclusive = true }
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
