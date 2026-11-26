package com.mangaos.app.core.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Set once from the user's interaction settings. Components read it instead of
 * animating against the system preference only, so the in-app switch is honoured.
 */
val LocalReduceMotion = compositionLocalOf { false }
