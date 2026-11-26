package com.mangaos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderBackground
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings
import com.mangaos.app.domain.repository.ReaderSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : ReaderSettingsRepository {

    private object Keys {
        val mode = stringPreferencesKey("reader_mode")
        val autoWebtoon = booleanPreferencesKey("reader_auto_webtoon")
        val continuous = booleanPreferencesKey("reader_continuous")
        val keepScreen = booleanPreferencesKey("reader_keep_screen")
        val fullscreen = booleanPreferencesKey("reader_fullscreen")
        val hideStatus = booleanPreferencesKey("reader_hide_status")
        val imageFit = stringPreferencesKey("reader_image_fit")
        val zoom = booleanPreferencesKey("reader_zoom")
        val margins = intPreferencesKey("reader_margins")
        val spacing = intPreferencesKey("reader_spacing")
        val bg = stringPreferencesKey("reader_bg")
        val brightness = floatPreferencesKey("reader_brightness")
        val contrast = floatPreferencesKey("reader_contrast")
        val saturation = floatPreferencesKey("reader_saturation")
        val hue = floatPreferencesKey("reader_hue")
        val grayscale = booleanPreferencesKey("reader_grayscale")
        val invert = booleanPreferencesKey("reader_invert")
        val autoScroll = booleanPreferencesKey("reader_autoscroll")
        val autoScrollSpeed = floatPreferencesKey("reader_autoscroll_speed")
        val preloadImages = intPreferencesKey("reader_preload_images")
        val preloadChapters = intPreferencesKey("reader_preload_chapters")
        val cacheSize = intPreferencesKey("reader_cache_size")
        val showTop = booleanPreferencesKey("reader_show_top")
        val showBottom = booleanPreferencesKey("reader_show_bottom")
        val autoHide = booleanPreferencesKey("reader_auto_hide")
        val autoHideDelay = longPreferencesKey("reader_auto_hide_delay")
        val showProgress = booleanPreferencesKey("reader_show_progress")
        val orientation = intPreferencesKey("reader_orientation")
    }

    override val settings: Flow<ReaderSettings> = dataStore.data.map { p ->
        ReaderSettings(
            readingMode = ReaderMode.valueOf(p[Keys.mode] ?: ReaderMode.VERTICAL.name),
            autoWebtoonDetection = p[Keys.autoWebtoon] ?: true,
            continuousReading = p[Keys.continuous] ?: true,
            keepScreenOn = p[Keys.keepScreen] ?: true,
            fullscreen = p[Keys.fullscreen] ?: false,
            hideStatusBar = p[Keys.hideStatus] ?: false,
            imageFit = ImageFit.valueOf(p[Keys.imageFit] ?: ImageFit.FIT_WIDTH.name),
            zoomEnabled = p[Keys.zoom] ?: true,
            marginsDp = p[Keys.margins] ?: 0,
            imageSpacingDp = p[Keys.spacing] ?: 4,
            backgroundColor = ReaderBackground.valueOf(p[Keys.bg] ?: ReaderBackground.BLACK.name),
            brightness = p[Keys.brightness] ?: 1f,
            contrast = p[Keys.contrast] ?: 1f,
            saturation = p[Keys.saturation] ?: 1f,
            hue = p[Keys.hue] ?: 0f,
            grayscale = p[Keys.grayscale] ?: false,
            invertColors = p[Keys.invert] ?: false,
            autoScroll = p[Keys.autoScroll] ?: false,
            autoScrollSpeed = p[Keys.autoScrollSpeed] ?: 1f,
            preloadImages = p[Keys.preloadImages] ?: 3,
            preloadChapters = p[Keys.preloadChapters] ?: 1,
            cacheSizeMb = p[Keys.cacheSize] ?: 64,
            showTopBar = p[Keys.showTop] ?: true,
            showBottomBar = p[Keys.showBottom] ?: true,
            autoHideControls = p[Keys.autoHide] ?: true,
            autoHideDelayMs = p[Keys.autoHideDelay] ?: 3000L,
            showProgressPercent = p[Keys.showProgress] ?: true,
            screenOrientation = p[Keys.orientation] ?: 0,
        )
    }

    override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        dataStore.edit { prefs ->
            // read current from prefs
            val cur = ReaderSettings(
                readingMode = ReaderMode.valueOf(prefs[Keys.mode] ?: ReaderMode.VERTICAL.name),
                autoWebtoonDetection = prefs[Keys.autoWebtoon] ?: true,
                continuousReading = prefs[Keys.continuous] ?: true,
                keepScreenOn = prefs[Keys.keepScreen] ?: true,
                fullscreen = prefs[Keys.fullscreen] ?: false,
                hideStatusBar = prefs[Keys.hideStatus] ?: false,
                imageFit = ImageFit.valueOf(prefs[Keys.imageFit] ?: ImageFit.FIT_WIDTH.name),
                zoomEnabled = prefs[Keys.zoom] ?: true,
                marginsDp = prefs[Keys.margins] ?: 0,
                imageSpacingDp = prefs[Keys.spacing] ?: 4,
                backgroundColor = ReaderBackground.valueOf(prefs[Keys.bg] ?: ReaderBackground.BLACK.name),
                brightness = prefs[Keys.brightness] ?: 1f,
                contrast = prefs[Keys.contrast] ?: 1f,
                saturation = prefs[Keys.saturation] ?: 1f,
                hue = prefs[Keys.hue] ?: 0f,
                grayscale = prefs[Keys.grayscale] ?: false,
                invertColors = prefs[Keys.invert] ?: false,
                autoScroll = prefs[Keys.autoScroll] ?: false,
                autoScrollSpeed = prefs[Keys.autoScrollSpeed] ?: 1f,
                preloadImages = prefs[Keys.preloadImages] ?: 3,
                preloadChapters = prefs[Keys.preloadChapters] ?: 1,
                cacheSizeMb = prefs[Keys.cacheSize] ?: 64,
                showTopBar = prefs[Keys.showTop] ?: true,
                showBottomBar = prefs[Keys.showBottom] ?: true,
                autoHideControls = prefs[Keys.autoHide] ?: true,
                autoHideDelayMs = prefs[Keys.autoHideDelay] ?: 3000L,
                showProgressPercent = prefs[Keys.showProgress] ?: true,
                screenOrientation = prefs[Keys.orientation] ?: 0,
            )
            val next = transform(cur)
            prefs[Keys.mode] = next.readingMode.name
            prefs[Keys.autoWebtoon] = next.autoWebtoonDetection
            prefs[Keys.continuous] = next.continuousReading
            prefs[Keys.keepScreen] = next.keepScreenOn
            prefs[Keys.fullscreen] = next.fullscreen
            prefs[Keys.hideStatus] = next.hideStatusBar
            prefs[Keys.imageFit] = next.imageFit.name
            prefs[Keys.zoom] = next.zoomEnabled
            prefs[Keys.margins] = next.marginsDp
            prefs[Keys.spacing] = next.imageSpacingDp
            prefs[Keys.bg] = next.backgroundColor.name
            prefs[Keys.brightness] = next.brightness
            prefs[Keys.contrast] = next.contrast
            prefs[Keys.saturation] = next.saturation
            prefs[Keys.hue] = next.hue
            prefs[Keys.grayscale] = next.grayscale
            prefs[Keys.invert] = next.invertColors
            prefs[Keys.autoScroll] = next.autoScroll
            prefs[Keys.autoScrollSpeed] = next.autoScrollSpeed
            prefs[Keys.preloadImages] = next.preloadImages
            prefs[Keys.preloadChapters] = next.preloadChapters
            prefs[Keys.cacheSize] = next.cacheSizeMb
            prefs[Keys.showTop] = next.showTopBar
            prefs[Keys.showBottom] = next.showBottomBar
            prefs[Keys.autoHide] = next.autoHideControls
            prefs[Keys.autoHideDelay] = next.autoHideDelayMs
            prefs[Keys.showProgress] = next.showProgressPercent
            prefs[Keys.orientation] = next.screenOrientation
        }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}