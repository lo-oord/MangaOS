package com.mangaos.app.domain.model

/**
 * One extension listed by the bundled catalogue: the product of parsing the repository
 * index (the user never sees the repository itself).
 */
data class ManagedExtension(
    val name: String,
    val pkgName: String,
    val apkUrl: String,
    val iconUrl: String?,
    val libVersion: String,
    val versionCode: Long,
    val versionName: String,
    val languages: List<String>,
    val contentWarning: Int,
    val baseUrls: List<String> = emptyList(),
) {
    /** "ar", "en", "ja" or "all" when the extension groups several languages. */
    val language: String
        get() = when {
            languages.isEmpty() -> "all"
            languages.size == 1 -> languages.first()
            else -> "all"
        }
}

/** An extension APK that is really installed on this device. */
data class InstalledExtension(
    val pkgName: String,
    val versionCode: Long,
    val versionName: String,
    val label: String,
    val sourceDir: String,
)

/**
 * A source that was actually instantiated from an installed extension. This is the unit the
 * Sources screen lists and every browse/search/read operation targets.
 */
data class SourceInfo(
    val id: String,
    val name: String,
    val lang: String,
    val pkgName: String,
    val extensionName: String,
    val versionName: String,
    val isNsfw: Boolean,
    val supportsLatest: Boolean,
) {
    val language: String get() = lang
}

enum class ExtensionFailure {
    OFFLINE,
    NETWORK,
    INVALID_INDEX,
    EMPTY,
    PERMISSION,
    INSTALL,
    UNSUPPORTED,
    INCOMPATIBLE,
    NO_SOURCES,
}

/** Progress of one install job, keyed by package name. */
sealed interface TransferState {
    data object Downloading : TransferState
    data object Installing : TransferState
    data object Installed : TransferState
    data class Failed(val failure: ExtensionFailure, val message: String? = null) : TransferState
}

data class ExtensionEntry(
    val extension: ManagedExtension,
    val installed: InstalledExtension?,
) {
    val isInstalled: Boolean get() = installed != null
    val hasUpdate: Boolean get() = installed != null && extension.versionCode > installed.versionCode
    val isUpToDate: Boolean get() = installed != null && extension.versionCode <= installed.versionCode
}

sealed interface ExtensionsState {
    data object Loading : ExtensionsState
    data class Content(val entries: List<ExtensionEntry>) : ExtensionsState
    data class Error(val failure: ExtensionFailure) : ExtensionsState
}

/** Language chips on the Sources screen. */
enum class LanguageFilter(val code: String) {
    ALL("all"),
    ARABIC("ar"),
    ENGLISH("en"),
    JAPANESE("ja");

    /**
     * "All" lists every source regardless of language. A concrete filter accepts sources in
     * that language and extension entries that cannot report a language.
     */
    fun matches(languages: List<String>): Boolean {
        if (this == ALL) return true
        val normalized = languages.map { it.lowercase() }.filter { it.isNotBlank() }
        if (normalized.isEmpty() || "all" in normalized) return true
        return normalized.any { it == code || it.startsWith("$code-") }
    }
}

/** Source browsing scope, used by the unified search and the Latest source picker. */
enum class MangaViewMode { GRID, LIST;

    fun toggled(): MangaViewMode = if (this == GRID) LIST else GRID

    companion object {
        fun fromStorage(value: String?): MangaViewMode =
            if (value == "list") LIST else GRID
    }
}
