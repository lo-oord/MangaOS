package com.mangaos.app.sources.registry

import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability

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
 * The single list of sources compiled into MangaOS.
 *
 * It is currently empty: the previously bundled sources and their implementations were removed
 * from the project. Adding a source means appending one [SourceDefinition] here together with
 * its [MangaSource] implementation — the Sources screen, the enable/disable state, the picker
 * and the repository all read this list, so nothing else has to change.
 *
 * Identity is the stable [SourceDefinition.id], never the display name.
 */
object NativeSourceRegistry {

    val definitions: List<SourceDefinition> = emptyList()

    init {
        val duplicates = definitions.groupBy { it.id }.filterValues { it.size > 1 }
        require(duplicates.isEmpty()) { "Duplicate source ids: ${duplicates.keys}" }
        require(definitions.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Every source needs a stable id and a name"
        }
        require(definitions.all { it.baseUrl.startsWith("https://") }) {
            "Every source needs an https base url"
        }
    }

    fun definition(id: String): SourceDefinition? = definitions.firstOrNull { it.id == id }
}
