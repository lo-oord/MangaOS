package com.mangaos.app.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Where the launch screen hands over to.
 *
 * Onboarding and the session are independent: a completed wizard with no session goes to the
 * authentication screen, and only a completed wizard with a live session goes straight home. The
 * wizard's flag is never reset by signing out or by an app update.
 */
enum class SplashDestination { ONBOARDING, AUTH, MAIN }

class SplashViewModel : ViewModel() {

    fun resolveDestination(onResolved: (SplashDestination) -> Unit) {
        viewModelScope.launch {
            // Both reads are awaited, not sampled: the session is still loading when the launch
            // screen starts, and a `.value` check would send a signed-in reader to sign in again.
            val onboardingCompleted = ServiceLocator.settings.onboardingCompleted.first()
            val profile = ServiceLocator.user.profile.first()
            onResolved(
                when {
                    !onboardingCompleted -> SplashDestination.ONBOARDING
                    profile == null -> SplashDestination.AUTH
                    else -> SplashDestination.MAIN
                },
            )
        }
    }
}
