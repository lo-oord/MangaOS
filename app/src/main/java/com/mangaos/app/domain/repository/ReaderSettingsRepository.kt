package com.mangaos.app.domain.repository

import com.mangaos.app.domain.model.ReaderSettings
import kotlinx.coroutines.flow.Flow

interface ReaderSettingsRepository {
    val settings: Flow<ReaderSettings>
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
    suspend fun reset()
}