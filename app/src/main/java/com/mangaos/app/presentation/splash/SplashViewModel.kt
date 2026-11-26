package com.mangaos.app.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SplashDestination { WELCOME, MAIN }

class SplashViewModel : ViewModel() {

    fun resolveDestination(onResolved: (SplashDestination) -> Unit) {
        viewModelScope.launch {
            val completed = ServiceLocator.settings.onboardingCompleted.first()
            if (!completed) {
                onResolved(SplashDestination.WELCOME)
                return@launch
            }
            if (ServiceLocator.user.profile.value == null) {
                ServiceLocator.user.startGuestSession()
            }
            onResolved(SplashDestination.MAIN)
        }
    }
}
