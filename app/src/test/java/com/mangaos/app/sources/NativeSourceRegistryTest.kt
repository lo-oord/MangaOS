package com.mangaos.app.sources

import com.mangaos.app.sources.registry.NativeSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Architecture invariants: every bundled source is complete, unique and instantiable. */
class NativeSourceRegistryTest {

    @Test
    fun `ids are unique and stable`() {
        val ids = NativeSourceRegistry.definitions.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `definitions declare real metadata`() {
        NativeSourceRegistry.definitions.forEach { definition ->
            assertTrue(definition.name.isNotBlank())
            assertEquals(definition.id, definition.factory().id)
            assertEquals(definition.name, definition.factory().name)
            assertEquals(definition.lang, definition.factory().lang)
            assertEquals(definition.baseUrl, definition.factory().baseUrl)
            assertTrue(definition.baseUrl.startsWith("https://"))
            assertTrue(
                "${definition.id} has no capability",
                definition.capabilities.isNotEmpty(),
            )
        }
    }

    @Test
    fun `every implementation is a distinct instance`() {
        val first = NativeSourceRegistry.definitions.map { it.factory() }
        val second = NativeSourceRegistry.definitions.map { it.factory() }
        first.indices.forEach { index ->
            assertTrue(first[index] !== second[index])
        }
    }

    @Test
    fun `the primary server is the default and the backup follows it`() {
        assertEquals("mangatek", NativeSourceRegistry.DEFAULT_ID)
        assertEquals(NativeSourceRegistry.DEFAULT_ID, NativeSourceRegistry.definitions.first().id)
        assertEquals("teamx", NativeSourceRegistry.definitions.last().id)
    }

    @Test
    fun `registry can resolve an id and rejects unknown ones`() {
        NativeSourceRegistry.definitions.forEach { definition ->
            assertEquals(definition, NativeSourceRegistry.definition(definition.id))
        }
        assertEquals(null, NativeSourceRegistry.definition("does-not-exist"))
    }
}
