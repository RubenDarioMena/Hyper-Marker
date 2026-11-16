package com.hypermarker

import com.hypermarker.picker.*
import org.junit.Assert.*
import org.junit.Test

class ItemPickerStateTest {
    private val folders = listOf(
        PickFolder("a", null, "Proyectos"), PickFolder("a1", "a", "RTS auto"), PickFolder("a2", "a", "Factorio"),
        PickFolder("a11", "a1", "Mapas"), PickFolder("g", null, "docs"), PickFolder("g1", "g", "guias"), PickFolder("b", null, "Archivo"))
    private val documents = listOf(
        PickDocument("d1", "a", "Guía GitHub Sync", "WEB_SNAPSHOT"), PickDocument("d2", "a", "Notas del ejemplo", "MARKDOWN"),
        PickDocument("d3", null, "Ejemplo interactivo", "HTML"), PickDocument("f", null, "Mapa de ideas", "FLOW"), PickDocument("f2", "a1", "Otro mapa", "FLOW"))

    /** PICK-01: destinos inválidos se ven y explican por qué. */
    @Test fun destinationExcludesSelfDescendantsAndReadOnly() {
        val state = ItemPickerState(PickerMode.DESTINATION, folders, documents, readOnlyRoots = setOf("g"), moving = "a1" to true, currentParent = "a")
        assertEquals("Ya está aquí", state.destinationProblem("a"))
        assertEquals("Es la carpeta que estás moviendo", state.destinationProblem("a1"))
        assertEquals("Está dentro de la carpeta que mueves", state.destinationProblem("a11"))
        assertNotNull(state.destinationProblem("g")); assertNotNull(state.destinationProblem("g1"))
        assertNull(state.destinationProblem(null)); assertNull(state.destinationProblem("b"))
        val root = state.entries().filterIsInstance<PickEntry.FolderEntry>().associateBy { it.id }
        assertEquals(setOf("a", "g", "b"), root.keys)
        assertFalse(root.getValue("g").enterable); assertTrue(root.getValue("g").disabledReason!!.contains("solo lectura"))
        assertNull("La carpeta actual se puede abrir para elegir dentro", root.getValue("a").disabledReason)
        state.enter("a")
        val inside = state.entries().filterIsInstance<PickEntry.FolderEntry>().associateBy { it.id }
        assertFalse(inside.getValue("a1").enterable)
        assertEquals(2, state.documentsHere())
        assertThrows(IllegalArgumentException::class.java) { state.enter("a1") }
        assertEquals(listOf("Proyectos"), state.crumbs().map { it.name })
        assertTrue("DESTINATION sólo lista carpetas", state.entries().all { it is PickEntry.FolderEntry })
    }

    /** PICK-02: la búsqueda recorre toda la biblioteca y las migas siguen la navegación. */
    @Test fun searchCoversWholeLibrary() {
        val state = ItemPickerState(PickerMode.RELATE, folders, documents)
        state.query = "mapa"
        assertEquals(setOf("a11", "f", "f2"), state.entries().map { it.id }.toSet())
        state.query = ""; state.enter("a"); state.enter("a1")
        assertEquals(listOf("Proyectos", "RTS auto"), state.crumbs().map { it.name })
        assertEquals("Biblioteca / Proyectos / RTS auto", state.path("a1"))
        state.up(); assertEquals("a", state.location)
        state.filter = KindFilter.FLOW; state.enter(null)
        assertEquals(listOf("f"), state.entries().map { it.id })
    }

    /** PICK-03: las recientes guardan las 4 últimas sin repetir. */
    @Test fun recentDestinations() {
        var recent = emptyList<String>()
        listOf("a", "b", "a1", "a", "g1", "a2").forEach { recent = ItemPickerState.remember(recent, it) }
        assertEquals(listOf("a2", "g1", "a", "a1"), recent)
    }

    /** PICK-05 y PICK-06: multiselección; lo ya relacionado y el propio Flow no se eligen. */
    @Test fun relateSelectionSkipsLinkedAndSelf() {
        val state = ItemPickerState(PickerMode.RELATE, folders, documents, alreadyLinked = setOf("d1"), excluded = setOf("f"))
        assertTrue(state.toggle("d3")); assertTrue(state.toggle("a"))
        assertFalse(state.toggle("d1")); assertFalse(state.toggle("f"))
        assertEquals(listOf("d3"), state.selectedDocuments()); assertEquals(listOf("a"), state.selectedFolders())
        assertTrue(state.toggle("d3")); assertEquals(emptyList<String>(), state.selectedDocuments())
        val rows = state.entries().associateBy { it.id }
        assertEquals("Es este mismo Flow", rows.getValue("f").disabledReason)
        state.enter("a")
        assertEquals("Ya relacionada", state.entries().first { it.id == "d1" }.disabledReason)
        assertThrows(IllegalArgumentException::class.java) { state.toggle("zzz") }
    }

    @Test fun listShowsOnlyGivenItemsInOrder() {
        val state = ItemPickerState(PickerMode.LIST, folders, documents, listed = listOf("d2", "a", "missing", "d3"))
        assertEquals(listOf("d2", "a", "d3"), state.entries().map { it.id })
        state.filter = KindFilter.HTML
        assertEquals(listOf("d3"), state.entries().map { it.id })
    }
}
