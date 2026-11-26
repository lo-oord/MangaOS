package com.mangaos.app.domain.model

enum class ReaderMode {
    VERTICAL,
    HORIZONTAL_LTR,
    HORIZONTAL_RTL,
    SINGLE_PAGE,
    DOUBLE_PAGE,
    WEBTOON,
}

enum class ImageFit {
    FIT_WIDTH,
    FIT_HEIGHT,
    FIT_SCREEN,
    ORIGINAL,
}

enum class ReaderBackground {
    BLACK,
    WHITE,
    GRAY,
    CUSTOM,
}

data class ReaderSettings(
    val readingMode: ReaderMode = ReaderMode.VERTICAL,
    val autoWebtoonDetection: Boolean = true,
    val continuousReading: Boolean = true,
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = false,
    val hideStatusBar: Boolean = false,
    val imageFit: ImageFit = ImageFit.FIT_WIDTH,
    val zoomEnabled: Boolean = true,
    val marginsDp: Int = 0,
    val imageSpacingDp: Int = 4,
    val backgroundColor: ReaderBackground = ReaderBackground.BLACK,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val grayscale: Boolean = false,
    val invertColors: Boolean = false,
    val autoScroll: Boolean = false,
    val autoScrollSpeed: Float = 1f,
    val preloadImages: Int = 3,
    val preloadChapters: Int = 1,
    val cacheSizeMb: Int = 64,
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val autoHideControls: Boolean = true,
    val autoHideDelayMs: Long = 3000L,
    val showProgressPercent: Boolean = true,
    val screenOrientation: Int = 0, // 0 auto
)