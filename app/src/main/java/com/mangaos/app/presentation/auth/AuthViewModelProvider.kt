package com.mangaos.app.presentation.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The account draft lives with the Activity, so a configuration change or a theme switch never
 * clears what the reader typed in the authentication shell.
 */
@Composable
fun rememberAuthViewModel(): AuthViewModel {
    val activity = LocalContext.current.findComponentActivity()
    return if (activity != null) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel()
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current != null) {
        if (current is ComponentActivity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
