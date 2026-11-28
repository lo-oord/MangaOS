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
            // Both values come from the same DataStore read, so the hand-off is one decision.
            val onboardingCompleted = ServiceLocator.settings.onboardingCompleted.first()
            when {
                !onboardingCompleted -> onResolved(SplashDestination.ONBOARDING)
                ServiceLocator.user.profile.value == null -> onResolved(SplashDestination.AUTH)
                else -> onResolved(SplashDestination.MAIN)
            }
        }
    }
}
