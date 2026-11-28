package com.mangaos.app.presentation.auth

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.mangaos.app.data.firebase.AuthFailure
import com.mangaos.app.data.firebase.AuthReason

/**
 * The web client id from the project's google-services.json. It is the audience Google signs the
 * ID token for; without it the token Firebase receives cannot be verified.
 */
const val GOOGLE_WEB_CLIENT_ID: String =
    "508550400647-6ijgh9sg6190jukd4qrue38k81s5pnsk.apps.googleusercontent.com"

/**
 * One launcher for every Google action: signing in, creating an account and linking Google to an
 * account that is already signed in all start from the same picker.
 */
@Composable
fun rememberGoogleSignIn(onResult: (Result<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        // Cancelling the picker arrives as a null intent or as a cancelled status, never as a token.
        val data: Intent? = activityResult.data
        if (data == null) {
            onResult(Result.failure(AuthFailure(AuthReason.CANCELLED)))
            return@rememberLauncherForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val token = account?.idToken
            if (token.isNullOrBlank()) {
                onResult(Result.failure(AuthFailure(AuthReason.GENERIC)))
            } else {
                onResult(Result.success(token))
            }
        } catch (error: ApiException) {
            val reason = if (error.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                AuthReason.CANCELLED
            } else {
                AuthReason.GENERIC
            }
            onResult(Result.failure(AuthFailure(reason)))
        }
    }
    val client = remember(context) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build(),
        )
    }
    return { runCatching { launcher.launch(client.signInIntent) } }
}

/** Mirrors the few status codes the picker reports so the UI can stay quiet on a cancel. */
private object GoogleSignInStatusCodes {
    const val SIGN_IN_CANCELLED = 12501
}
