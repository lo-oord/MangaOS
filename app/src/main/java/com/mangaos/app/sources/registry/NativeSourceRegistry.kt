package com.mangaos.app.sources.registry

import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.implementations.AsuraScansSource
import com.mangaos.app.sources.implementations.MangaClubSource
import com.mangaos.app.sources.implementations.MangaDexSource

/**
 * A bundled source: identity and display metadata plus a factory. The factory is what makes the
 * source lazy: no instance and no request exist until the source is actually used.
 */
data class SourceDefinition(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val description: String,
    val version: String,
    val capabilities: Set<SourceCapability>,
    val factory: () -> MangaSource,
)

/**
 * The single list of sources compiled into MangaOS. Adding a source means adding one entry
 * here and its implementation class; nothing else in the app needs to know about it.
 *
 * Identity is the stable [SourceDefinition.id], never the display name.
 */
object NativeSourceRegistry {

    val definitions: List<SourceDefinition> = listOf(
        SourceDefinition(
            id = "mangadex-ar",
            name = "MangaDex (العربية)",
            lang = "ar",
            baseUrl = "https://mangadex.org",
            description = "أكبر فهرس مانجا مفتوح، فصول عربية.",
            version = "1.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            factory = { MangaDexSource("ar") },
        ),
        SourceDefinition(
            id = "mangadex-en",
            name = "MangaDex (English)",
            lang = "en",
            baseUrl = "https://mangadex.org",
            description = "Large open manga catalogue, English chapters.",
            version = "1.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            factory = { MangaDexSource("en") },
        ),
        SourceDefinition(
            id = "asura",
            name = "Asura Scans",
            lang = "en",
            baseUrl = "https://asurascans.com",
            description = "English manhwa releases with a public JSON catalogue.",
            version = "1.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            factory = { AsuraScansSource() },
        ),
        SourceDefinition(
            id = "mangaclub",
            name = "Manga Club",
            lang = "en",
            baseUrl = "https://www.mangaclub.net",
            description = "English manga, manhwa and manhua library.",
            version = "1.0",
            capabilities = setOf(
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            factory = { MangaClubSource() },
        ),
    )

    init {
        val duplicates = definitions.groupBy { it.id }.filterValues { it.size > 1 }
        require(duplicates.isEmpty()) { "Duplicate source ids: ${duplicates.keys}" }
        require(definitions.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Every source needs a stable id and a name"
        }
    }

    fun definition(id: String): SourceDefinition? = definitions.firstOrNull { it.id == id }
}
