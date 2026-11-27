package com.mangaos.app.sources.registry

import com.mangaos.app.R
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.implementations.AsuraScansSource
import com.mangaos.app.sources.implementations.AzoraSource
import com.mangaos.app.sources.implementations.DespairMangaSource
import com.mangaos.app.sources.implementations.DilarSource
import com.mangaos.app.sources.implementations.KawaiiMangaSource
import com.mangaos.app.sources.implementations.MangaArSource
import com.mangaos.app.sources.implementations.MangaDarSource
import com.mangaos.app.sources.implementations.MangaOriginSource
import com.mangaos.app.sources.implementations.MangaSwatSource
import com.mangaos.app.sources.implementations.MangaTekSource
import com.mangaos.app.sources.implementations.MangaTikSource
import com.mangaos.app.sources.implementations.MangaTimeSource
import com.mangaos.app.sources.implementations.ProcomicSource
import com.mangaos.app.sources.implementations.TeamXSource
import com.mangaos.app.sources.implementations.ThunderScansSource

/**
 * A bundled source: identity, display metadata, its icon and a factory. The factory is what
 * makes a source lazy: no instance and no request exist until it is actually used.
 */
data class SourceDefinition(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val description: String,
    val version: String,
    val capabilities: Set<SourceCapability>,
    val iconRes: Int,
    val factory: () -> MangaSource,
)

/**
 * The single list of sources compiled into MangaOS.
 *
 * Every source here is a native implementation inside the APK: there is nothing to install,
 * download or update separately, and no extension APK or repository is loaded at runtime.
 * Adding a source means appending one [SourceDefinition] together with its [MangaSource]
 * implementation — the Sources screen, the enable/disable state, the picker and the repository
 * all read this list.
 *
 * Identity is the stable [SourceDefinition.id], never the display name, so a renamed source
 * keeps its library, reading history and downloads.
 */
object NativeSourceRegistry {

    val definitions: List<SourceDefinition> = listOf(
        SourceDefinition(
            id = "mangatek",
            name = "MangaTek",
            lang = "ar",
            baseUrl = "https://mangatek.com",
            description = "مانجا عربية مع طبقات ترجمة مرسومة على الصفحات",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangatek,
            factory = ::MangaTekSource,
        ),
        SourceDefinition(
            id = "mangakawaii",
            name = "Kawaii Manga",
            lang = "fr",
            baseUrl = "https://www.mangakawaii.fr",
            description = "Catalogue français de mangas et webtoons",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangakawaii,
            factory = ::KawaiiMangaSource,
        ),
        SourceDefinition(
            id = "despairmanga",
            name = "Despair Manga",
            lang = "ar",
            baseUrl = "https://despair-world.com",
            description = "مانجا عربية عبر واجهة الموقع العامة",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_despairmanga,
            factory = ::DespairMangaSource,
        ),
        SourceDefinition(
            id = "procomic",
            name = "ProComic",
            lang = "ar",
            baseUrl = "https://procomic.pro",
            description = "مانجا ومانهوا عربية",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_procomic,
            factory = ::ProcomicSource,
        ),
        SourceDefinition(
            id = "mangaar",
            name = "MangaAr",
            lang = "ar",
            baseUrl = "https://mangaar.com",
            description = "مانغا عربي بترجمات محلية وفصول من مانجا ديكس",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangaar,
            factory = ::MangaArSource,
        ),
        SourceDefinition(
            id = "thunderscans",
            name = "Thunder Scans",
            lang = "en",
            baseUrl = "https://en-thunderscans.com",
            description = "English manhwa and manga scans",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_thunderscans,
            factory = ::ThunderScansSource,
        ),
        SourceDefinition(
            id = "asurascans",
            name = "Asura Scans",
            lang = "en",
            baseUrl = "https://asurascans.com",
            description = "English manhwa and manga scans",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_asurascans,
            factory = ::AsuraScansSource,
        ),
        SourceDefinition(
            id = "teamx",
            name = "Team X Manga",
            lang = "ar",
            baseUrl = "https://olympustaff.com",
            description = "مانجا عربية من فريق Team X",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_teamx,
            factory = ::TeamXSource,
        ),
        SourceDefinition(
            id = "azora",
            name = "Azora",
            lang = "ar",
            baseUrl = "https://azorafly.com",
            description = "مانجا ومانهوا عربية",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_azora,
            factory = ::AzoraSource,
        ),
        SourceDefinition(
            id = "mangadar",
            name = "Manga Dar",
            lang = "ar",
            baseUrl = "https://mangadar.com",
            description = "مانجا عربية",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangadar,
            factory = ::MangaDarSource,
        ),
        SourceDefinition(
            id = "mangaorigin",
            name = "Manga Origin",
            lang = "ar",
            baseUrl = "https://teammangaorigin.blogspot.com",
            description = "مانجا عربية على مدونة Blogger",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangaorigin,
            factory = ::MangaOriginSource,
        ),
        SourceDefinition(
            id = "mangaswat",
            name = "Manga Swat",
            lang = "ar",
            baseUrl = "https://meshmanga.com",
            description = "مانجا عربية عبر واجهة التطبيق",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangaswat,
            factory = ::MangaSwatSource,
        ),
        SourceDefinition(
            id = "mangatik",
            name = "Manga Tik",
            lang = "ar",
            baseUrl = "https://mangatik.com",
            description = "مانجا عربية",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangatik,
            factory = ::MangaTikSource,
        ),
        SourceDefinition(
            id = "mangatime",
            name = "Manga Time",
            lang = "ar",
            baseUrl = "https://mangatime.org",
            description = "مانجا عربية",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_mangatime,
            factory = ::MangaTimeSource,
        ),
        SourceDefinition(
            id = "dilar",
            name = "Dilar",
            lang = "ar",
            baseUrl = "https://dilar.tube",
            description = "مانجا ومانهوا عربية",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            iconRes = R.drawable.source_dilar,
            factory = ::DilarSource,
        ),
    )

    init {
        val duplicates = definitions.groupBy { it.id }.filterValues { it.size > 1 }
        require(duplicates.isEmpty()) { "Duplicate source ids: ${duplicates.keys}" }
        val names = definitions.groupBy { it.name }.filterValues { it.size > 1 }
        require(names.isEmpty()) { "Duplicate source names: ${names.keys}" }
        require(definitions.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Every source needs a stable id and a name"
        }
        require(definitions.all { it.baseUrl.startsWith("https://") }) {
            "Every source needs an https base url"
        }
    }

    fun definition(id: String): SourceDefinition? = definitions.firstOrNull { it.id == id }
}
