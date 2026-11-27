package com.mangaos.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.mangaos.app.di.ServiceLocator

class MangaOSApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    /**
     * One bounded disk cache for reader pages. Downloaded chapters live in filesDir and are
     * never evicted by Coil, so cache pressure cannot remove an offline chapter.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(192L * 1024L * 1024L)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
