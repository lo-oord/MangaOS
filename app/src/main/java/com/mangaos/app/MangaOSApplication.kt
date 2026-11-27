package com.mangaos.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.sources.images.OverlayImageInterceptor
import okhttp3.OkHttpClient

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
        // Reader pages go through the shared image pipeline: a page whose translation is a
        // separate text layer (MangaTek) is painted before it is shown.
        .okHttpClient(
            OkHttpClient.Builder()
                .addInterceptor(OverlayImageInterceptor)
                .build(),
        )
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
