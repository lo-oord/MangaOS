package com.mangaos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.mangaOsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mangaos_settings")
