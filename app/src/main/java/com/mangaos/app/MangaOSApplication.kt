package com.mangaos.app

import android.app.Application
import com.mangaos.app.data.extension.MihonRuntime
import com.mangaos.app.di.ServiceLocator

class MangaOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MihonRuntime.initialize(this)
        ServiceLocator.init(this)
    }
}
