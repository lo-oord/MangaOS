package com.mangaos.app.sources.registry

import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.implementations.MangaTekSource
import com.mangaos.app.sources.implementations.TeamXSource

/**
 * A bundled server: identity, display metadata, its icon (none by default) and a factory. The
 * factory is what makes a server lazy: no instance and no request exist until it is used.
 */
data class SourceDefinition(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val description: String,
    val version: String,
    val capabilities: Set<SourceCapability>,
    /** Brand artwork is deliberately absent: servers are presented neutrally in the app. */
    val iconRes: Int? = null,
    val factory: () -> MangaSource,
)

/**
 * The single list of servers compiled into MangaOS: one primary and one backup.
 *
 * Both implementations are native and live inside the APK, so there is nothing to install or
 * update separately and no extension repository is loaded at runtime. The Sources screen, the
 * enable/disable state, the picker and the repository all read this list.
 *
 * Identity is the stable [SourceDefinition.id], never the display name: a renamed server keeps
 * every library entry, reading position and download that already belongs to it. The MangaTek
 * implementation therefore keeps its id `mangatek` and Team X keeps `teamx`.
 */
object NativeSourceRegistry {

    val definitions: List<SourceDefinition> = listOf(
        SourceDefinition(
            id = "mangatek",
            name = "السيرفر الرئيسي",
            description = "الخادم الأساسي للمانجا العربية",
            lang = "ar",
            baseUrl = "https://mangatek.com",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            factory = ::MangaTekSource,
        ),
        SourceDefinition(
            id = "teamx",
            name = "السيرفر الاحتياطي",
            description = "خادم بديل يُستخدم عند تعذّر الخادم الرئيسي",
            lang = "ar",
            baseUrl = "https://olympustaff.com",
            version = "1.0.0",
            capabilities = setOf(
                SourceCapability.LATEST,
                SourceCapability.POPULAR,
                SourceCapability.SEARCH,
            ),
            factory = ::TeamXSource,
        ),
    )

    init {
        val duplicates = definitions.groupBy { it.id }.filterValues { it.size > 1 }
        require(duplicates.isEmpty()) { "Duplicate server ids: ${duplicates.keys}" }
        val names = definitions.groupBy { it.name }.filterValues { it.size > 1 }
        require(names.isEmpty()) { "Duplicate server names: ${names.keys}" }
        require(definitions.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Every server needs a stable id and a name"
        }
        require(definitions.all { it.baseUrl.startsWith("https://") }) {
            "Every server needs an https base url"
        }
    }

    fun definition(id: String): SourceDefinition? = definitions.firstOrNull { it.id == id }
}
