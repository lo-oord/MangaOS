package com.mangaos.app.sources.network

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Small HTML helpers shared by the sources that scrape a page. They only remove boilerplate:
 * which selector belongs to which site stays inside that source's implementation.
 */

fun Document.firstText(vararg selectors: String): String? = selectors.firstNotNullOfOrNull { selector ->
    selectFirst(selector)?.text()?.trim()?.takeIf { text -> text.isNotEmpty() }
}

fun Element.firstText(vararg selectors: String): String? = selectors.firstNotNullOfOrNull { selector ->
    selectFirst(selector)?.text()?.trim()?.takeIf { text -> text.isNotEmpty() }
}

fun Element.firstOwnText(vararg selectors: String): String? = selectors.firstNotNullOfOrNull { selector ->
    selectFirst(selector)?.ownText()?.trim()?.takeIf { text -> text.isNotEmpty() }
}

fun Document.metaContent(vararg properties: String): String? =
    properties.firstNotNullOfOrNull { property ->
        selectFirst("meta[property=$property], meta[name=$property]")
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

/** Lazily loaded images keep their real address in a data attribute, never in `src`. */
fun Element.imageUrl(vararg attributes: String): String? =
    attributes.firstNotNullOfOrNull { attribute ->
        absUrl(attribute).trim().takeIf { it.isNotEmpty() }
    }

fun Element.coverImage(): String? = imageUrl(
    "data-src",
    "data-lazy-src",
    "data-cfsrc",
    "data-original",
    "data-zoom-src",
    "src",
)

fun Document.coverImage(): String? =
    selectFirst("meta[property=og:image]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        ?: selectFirst("img").coverImage()

/** The `chapter` numbers a source uses, in its own spelling ("12.5", "12", "12.5.1"). */
fun String.chapterNumberOrNull(): String? =
    Regex("""\d+(?:\.\d+)*""").find(this)?.value

fun String.toMangaStatusOrNull(): String? =
    lowercase().trim().takeIf { it.isNotEmpty() }

/** Some sources inline a readable date, e.g. "Updated On: March 3, 2024". */
fun String.dateTail(): String? = substringAfter(':', "").trim().takeIf { it.isNotEmpty() }
