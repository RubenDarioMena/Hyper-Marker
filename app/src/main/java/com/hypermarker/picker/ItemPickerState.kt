package com.hypermarker.picker

/** Lo que la hoja de selección está haciendo. */
enum class PickerMode { DESTINATION, RELATE, LIST }

/** Filtro por tipo de los chips (Todo/HTML/Notas/Flow/Web). */
enum class KindFilter(val label: String, val types: Set<String>) {
    ALL("Todo", emptySet()), HTML("HTML", setOf("HTML")), NOTES("Notas", setOf("MARKDOWN", "REMOTE_MARKDOWN")),
    FLOW("Flow", setOf("FLOW")), DIAGRAMS("Diagramas", setOf("MERMAID")), WEB("Web", setOf("WEB_SNAPSHOT"));
    fun accepts(type: String) = this == ALL || type in types
}

data class PickFolder(val id: String, val parentId: String?, val name: String)
data class PickDocument(val id: String, val folderId: String?, val title: String, val type: String)

/** Una fila visible: carpeta o documento, con el motivo por el que no se puede elegir (si lo hay). */
sealed interface PickEntry {
    val id: String
    val title: String
    val disabledReason: String?
    data class FolderEntry(val folder: PickFolder, val folders: Int, val documents: Int, override val disabledReason: String?, val enterable: Boolean) : PickEntry {
        override val id get() = folder.id
        override val title get() = folder.name
    }
    data class DocumentEntry(val document: PickDocument, override val disabledReason: String?) : PickEntry {
        override val id get() = document.id
        override val title get() = document.title
    }
}

/**
 * Estado puro del selector de elementos, compartido por los tres modos. Sin Android: se prueba en JVM.
 *
 * - DESTINATION: elige una carpeta (o la raíz). Excluye la carpeta que se mueve y sus descendientes,
 *   las carpetas de GitHub (solo lectura) y marca la carpeta actual del elemento.
 * - RELATE: elige varios documentos y carpetas para relacionar con un punto; marca los ya relacionados.
 * - LIST: muestra los elementos dados; no selecciona, sólo abre.
 */
class ItemPickerState(
    val mode: PickerMode,
    folders: List<PickFolder>,
    documents: List<PickDocument>,
    /** Carpetas de la biblioteca que son raíz de una fuente GitHub (todo su subárbol es de solo lectura). */
    readOnlyRoots: Set<String> = emptySet(),
    /** Elemento que se mueve (DESTINATION): id y si es carpeta. */
    private val moving: Pair<String, Boolean>? = null,
    /** Carpeta en la que está ahora el elemento que se mueve. */
    private val currentParent: String? = null,
    /** Ids ya relacionados (RELATE). */
    private val alreadyLinked: Set<String> = emptySet(),
    /** Ids que nunca se pueden relacionar (p. ej. el propio Flow). */
    private val excluded: Set<String> = emptySet(),
    /** Subconjunto mostrado en LIST, en orden. */
    private val listed: List<String>? = null,
) {
    val folders = folders.associateBy { it.id }
    val documents = documents.associateBy { it.id }
    private val children = folders.groupBy { it.parentId }
    private val docsIn = documents.groupBy { it.folderId }
    private val readOnly: Set<String> = buildSet {
        val stack = ArrayDeque(readOnlyRoots.filter { it in this@ItemPickerState.folders })
        while (stack.isNotEmpty()) { val id = stack.removeLast(); if (add(id)) children[id].orEmpty().forEach { stack.addLast(it.id) } }
    }
    private val movingSubtree: Set<String> = buildSet {
        val (id, folder) = moving ?: return@buildSet
        if (!folder) return@buildSet
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) { val next = stack.removeLast(); if (add(next)) children[next].orEmpty().forEach { stack.addLast(it.id) } }
    }

    var location: String? = null
        private set
    var query: String = ""
    var filter: KindFilter = KindFilter.ALL
    val selection = linkedSetOf<String>()

    fun enter(folder: String?) {
        require(folder == null || folder in folders) { "La carpeta ya no existe" }
        require(folder == null || isEnterable(folder)) { "No se puede abrir esa carpeta aquí" }
        location = folder; query = ""
    }

    fun up() { location = location?.let { folders[it]?.parentId } }

    /** Migas desde la raíz hasta la ubicación actual (sin la raíz). */
    fun crumbs(): List<PickFolder> = buildList {
        var node = location?.let(folders::get); val seen = mutableSetOf<String>()
        while (node != null && seen.add(node.id)) { add(0, node); node = node.parentId?.let(folders::get) }
    }

    fun isReadOnly(folder: String) = folder in readOnly

    private fun isEnterable(folder: String) = when (mode) {
        PickerMode.DESTINATION -> folder !in movingSubtree && folder !in readOnly
        else -> true
    }

    /** Por qué no se puede elegir la carpeta como destino, o `null` si se puede. */
    fun destinationProblem(folder: String?): String? = when {
        folder != null && folder !in folders -> "La carpeta ya no existe"
        folder != null && folder in movingSubtree -> if (folder == moving?.first) "Es la carpeta que estás moviendo" else "Está dentro de la carpeta que mueves"
        folder != null && folder in readOnly -> "GitHub · solo lectura, no admite elementos"
        moving != null && folder == currentParent -> "Ya está aquí"
        else -> null
    }

    private fun relateProblem(id: String) = when {
        id in excluded -> "Es este mismo Flow"
        id in alreadyLinked -> "Ya relacionada"
        else -> null
    }

    /** Filas visibles según modo, ubicación, búsqueda y filtro. Carpetas primero, por nombre. */
    fun entries(): List<PickEntry> {
        val searching = query.isNotBlank()
        if (mode == PickerMode.LIST) {
            return listed.orEmpty().mapNotNull { id ->
                folders[id]?.let { folderEntry(it) } ?: documents[id]?.let { PickEntry.DocumentEntry(it, null) }
            }.filter { !searching || it.title.contains(query.trim(), ignoreCase = true) }
                .filter { filter == KindFilter.ALL || (it is PickEntry.DocumentEntry && filter.accepts(it.document.type)) }
        }
        val folderRows = (if (searching) folders.values.filter { it.name.contains(query.trim(), true) } else children[location].orEmpty())
            .sortedBy { it.name.lowercase() }
            .let { list -> if (mode == PickerMode.RELATE && filter != KindFilter.ALL) emptyList() else list }
            .map(::folderEntry)
        if (mode == PickerMode.DESTINATION) return folderRows
        val documentRows = (if (searching) documents.values.filter { it.title.contains(query.trim(), true) } else docsIn[location].orEmpty())
            .filter { filter.accepts(it.type) }.sortedBy { it.title.lowercase() }
            .map { PickEntry.DocumentEntry(it, relateProblem(it.id)) }
        return folderRows + documentRows
    }

    private fun folderEntry(folder: PickFolder): PickEntry.FolderEntry {
        val reason = when (mode) {
            PickerMode.DESTINATION -> destinationProblem(folder.id).takeIf { folder.id != currentParent }
            PickerMode.RELATE -> relateProblem(folder.id)
            PickerMode.LIST -> null
        }
        return PickEntry.FolderEntry(folder, children[folder.id].orEmpty().size, docsIn[folder.id].orEmpty().size, reason, isEnterable(folder.id))
    }

    /** Documentos que hay en la ubicación actual (DESTINATION los resume en lugar de listarlos). */
    fun documentsHere(): Int = docsIn[location].orEmpty().size

    fun toggle(id: String): Boolean {
        require(mode == PickerMode.RELATE)
        require(id in folders || id in documents) { "El elemento ya no existe" }
        if (relateProblem(id) != null) return false
        if (!selection.remove(id)) selection.add(id)
        return true
    }

    fun selectedDocuments() = selection.filter { it in documents }
    fun selectedFolders() = selection.filter { it in folders }

    /** Ruta legible de una carpeta: «Biblioteca / A / B». */
    fun path(folder: String?): String {
        if (folder == null) return "Biblioteca"
        val names = mutableListOf<String>(); val seen = mutableSetOf<String>(); var node = folders[folder]
        while (node != null && seen.add(node.id)) { names.add(0, node.name); node = node.parentId?.let(folders::get) }
        return (listOf("Biblioteca") + names).joinToString(" / ")
    }

    companion object {
        /** Mantiene las últimas `limit` carpetas destino, la más reciente primero. */
        fun remember(recent: List<String>, used: String, limit: Int = 4) = (listOf(used) + recent.filter { it != used }).take(limit)
    }
}
