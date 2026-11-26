package com.mangaos.app.data.extension

import com.mangaos.app.domain.model.ManagedExtension

/**
 * The only extensions MangaOS may list, install or execute.
 *
 * Identity is the extension package, not a fuzzy display name: similar names in the upstream
 * repository must never be substituted for an approved source. The display name and language
 * are the ones requested for MangaOS, while [ManagedExtension.pkgName] keeps the real package
 * identity used by Android and the runtime.
 *
 * Content classification is enforced at the same gate. Only SAFE (1) and MIXED (2) entries are
 * accepted; NSFW (3) and unknown (0) entries are rejected. A MIXED source is not an +18-only
 * source, but its works are still subject to the per-work filters in the manga repository.
 */
internal object ExtensionAllowlist {

    internal data class AllowedSource(
        val displayName: String,
        val pkgName: String,
        val language: String,
    )

    /**
     * Names that were requested but do not have a verified, matching extension in the live
     * catalogue. They are deliberately absent from [entries]; they are not replaced with a
     * similarly named source.
     */
    val unavailable: List<String> = listOf(
        "Manga online",
        "Mangatok",
    )

    private val entries: List<AllowedSource> = listOf(
        // Arabic
        AllowedSource("TeamX manga", "eu.kanade.tachiyomi.extension.ar.teamx", "ar"),
        AllowedSource("Azora", "eu.kanade.tachiyomi.extension.ar.azora", "ar"),
        AllowedSource("Manga swat", "eu.kanade.tachiyomi.extension.ar.mangaswat", "ar"),
        AllowedSource("Mangatek", "eu.kanade.tachiyomi.extension.ar.mangatek", "ar"),
        AllowedSource("Kawaii Manga", "eu.kanade.tachiyomi.extension.ar.kawiimanga", "ar"),
        AllowedSource("Despair manga", "eu.kanade.tachiyomi.extension.ar.despairmanga", "ar"),
        AllowedSource("Dilar", "eu.kanade.tachiyomi.extension.ar.dilar", "ar"),
        AllowedSource("Hijala", "eu.kanade.tachiyomi.extension.ar.hijala", "ar"),
        AllowedSource("Hizo manga", "eu.kanade.tachiyomi.extension.ar.hizomanga", "ar"),
        AllowedSource("Lava scans", "eu.kanade.tachiyomi.extension.ar.lavascans", "ar"),
        AllowedSource("Manga origin", "eu.kanade.tachiyomi.extension.ar.mangaorigin", "ar"),
        AllowedSource("Manga tales", "eu.kanade.tachiyomi.extension.ar.mangatales", "ar"),
        AllowedSource("Manga cloud", "eu.kanade.tachiyomi.extension.ar.mangacloud", "ar"),
        AllowedSource("Manga time", "eu.kanade.tachiyomi.extension.ar.mangatime", "ar"),
        AllowedSource("Manga tik", "eu.kanade.tachiyomi.extension.ar.mangatik", "ar"),
        AllowedSource("Pro comic", "eu.kanade.tachiyomi.extension.ar.procomic", "ar"),
        AllowedSource(
            "Manga Arabic (مانجا عربي)",
            "eu.kanade.tachiyomi.extension.ar.mangaar",
            "ar",
        ),
        AllowedSource("Manga spark", "eu.kanade.tachiyomi.extension.ar.mangaspark", "ar"),

        // English
        AllowedSource("Asura scans", "eu.kanade.tachiyomi.extension.en.asurascans", "en"),
        AllowedSource("Comic asura", "eu.kanade.tachiyomi.extension.en.comicasura", "en"),
        AllowedSource("Thunder scans", "eu.kanade.tachiyomi.extension.en.thunderscans", "en"),
        AllowedSource("Manga demon", "eu.kanade.tachiyomi.extension.en.mangademon", "en"),
        AllowedSource("Manga club", "eu.kanade.tachiyomi.extension.all.mangaclub", "en"),
        AllowedSource("Comix", "eu.kanade.tachiyomi.extension.en.comix", "en"),
        AllowedSource("Manga dex", "eu.kanade.tachiyomi.extension.all.mangadex", "en"),

        // Japanese
        AllowedSource("Zenon", "eu.kanade.tachiyomi.extension.ja.zenon", "ja"),
        AllowedSource("Zebrack", "eu.kanade.tachiyomi.extension.ja.zebrack", "ja"),
        AllowedSource("Yomanga", "eu.kanade.tachiyomi.extension.ja.yomonga", "ja"),
        AllowedSource("We love manga", "eu.kanade.tachiyomi.extension.ja.rawlh", "ja"),
    )

    private val byPackage: Map<String, AllowedSource> = entries.associateBy { it.pkgName }

    fun filter(catalogue: List<ManagedExtension>): List<ManagedExtension> = catalogue
        .mapNotNull(::canonical)
        .distinctBy { it.pkgName }
        .sortedBy { it.name.lowercase() }

    /**
     * Returns the catalogue entry with the MangaOS display name and language, or null when it
     * is not approved, has no usable APK URL, or is NSFW/unknown.
     */
    fun canonical(entry: ManagedExtension): ManagedExtension? {
        val allowed = byPackage[entry.pkgName] ?: return null
        if (entry.contentWarning !in SAFE_CLASSIFICATIONS) return null
        if (entry.apkUrl.isBlank()) return null
        return entry.copy(
            name = allowed.displayName,
            languages = listOf(allowed.language),
            baseUrls = entry.baseUrls,
        )
    }

    fun isAllowedPackage(pkgName: String): Boolean = pkgName in byPackage

    /** Enforced again in the runtime so an installed package cannot smuggle in extra sources. */
    fun isAllowedRuntimeSource(pkgName: String, isNsfw: Boolean): Boolean =
        !isNsfw && pkgName in byPackage

    fun displayName(pkgName: String): String? = byPackage[pkgName]?.displayName

    private val SAFE_CLASSIFICATIONS = setOf(1, 2)
}
