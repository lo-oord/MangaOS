package com.mangaos.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.data.notifications.NotificationIntents
import com.mangaos.app.presentation.MangaOsApp
import com.mangaos.app.presentation.navigation.NavigationRequests

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyTo(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        setContent { MangaOsApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * A notification carries the source and manga ids it was created for; the UI navigates by
     * those ids only, never by a title that could match a different work.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val sourceId = intent.getStringExtra(NotificationIntents.EXTRA_SOURCE_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        val mangaId = intent.getStringExtra(NotificationIntents.EXTRA_MANGA_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        NavigationRequests.open(
            NavigationRequests.MangaTarget(
                sourceId = sourceId,
                mangaId = mangaId,
                local = intent.getBooleanExtra(NotificationIntents.EXTRA_LOCAL, false),
                notificationId = intent.getStringExtra(NotificationIntents.EXTRA_NOTIFICATION_ID),
            ),
        )
        intent.removeExtra(NotificationIntents.EXTRA_SOURCE_ID)
        intent.removeExtra(NotificationIntents.EXTRA_MANGA_ID)
        intent.removeExtra(NotificationIntents.EXTRA_LOCAL)
        intent.removeExtra(NotificationIntents.EXTRA_NOTIFICATION_ID)
    }
}
