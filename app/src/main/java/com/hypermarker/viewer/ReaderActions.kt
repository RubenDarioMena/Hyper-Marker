package com.hypermarker.viewer

import android.content.Context

/** Acción asignable a la cruceta o al menú. `target` indica que actúa sobre el elemento mantenido. */
enum class ReaderAction(val label: String, val modes: Set<String>, val target: Boolean = false, val available: Boolean = true) {
    AUTOSCROLL("Autoscroll", setOf("VIEW")),
    ZOOM_FIT("Acercar elemento", setOf("VIEW"), target = true),
    ZOOM_RESET("Zoom original", setOf("VIEW")),
    RELOAD("Recargar", setOf("VIEW")),
    BACK("Atrás", setOf("VIEW")),
    FORWARD("Adelante", setOf("VIEW")),
    FULLSCREEN("Pantalla completa", setOf("VIEW")),
    NOTES("Notas y cambios", setOf("VIEW", "EDIT")),
    PIN("Nota pin", setOf("EDIT"), target = true),
    EDIT_TEXT("Editar texto", setOf("EDIT"), target = true),
    DELETE_PINS("Borrar adjuntos", setOf("EDIT"), target = true),
    RESTORE_TEXT("Borrar cambios", setOf("EDIT"), target = true),
    COLOR("Color", setOf("DRAW")),
    WIDTH("Grosor", setOf("DRAW")),
    TOOL("Herramienta", setOf("DRAW")),
    STABILIZER("Estabilizar", setOf("DRAW"), available = false),
    CLEAR("Borrar página", setOf("DRAW")),
}

/** Cruceta de cuatro posiciones por modo: arriba, derecha, abajo, izquierda. */
object CrossLayout {
    val MODES = listOf("VIEW", "EDIT", "DRAW")
    val SLOTS = listOf("Arriba", "Derecha", "Abajo", "Izquierda")
    val defaults = mapOf(
        "VIEW" to listOf(ReaderAction.AUTOSCROLL, ReaderAction.ZOOM_FIT, ReaderAction.ZOOM_RESET, ReaderAction.RELOAD),
        "EDIT" to listOf(ReaderAction.PIN, ReaderAction.EDIT_TEXT, ReaderAction.DELETE_PINS, ReaderAction.RESTORE_TEXT),
        "DRAW" to listOf(ReaderAction.COLOR, ReaderAction.WIDTH, ReaderAction.TOOL, ReaderAction.STABILIZER),
    )
    fun pool(mode: String) = ReaderAction.entries.filter { mode in it.modes }
    fun rest(mode: String, cross: List<ReaderAction>) = pool(mode) - cross.toSet()
    fun parse(mode: String, raw: String?): List<ReaderAction> {
        val values = raw?.split(',')?.mapNotNull { name -> ReaderAction.entries.firstOrNull { it.name == name } }
        return if (values != null && values.size == 4 && values.toSet().size == 4 && values.all { mode in it.modes }) values else defaults.getValue(mode)
    }
    fun format(cross: List<ReaderAction>) = cross.joinToString(",") { it.name }
    /** Coloca la acción en la posición; si ya estaba en otra, intercambia ambas. */
    fun assign(cross: List<ReaderAction>, slot: Int, action: ReaderAction): List<ReaderAction> {
        require(slot in 0..3)
        val result = cross.toMutableList()
        val previous = result.indexOf(action)
        if (previous >= 0) result[previous] = result[slot]
        result[slot] = action
        return result
    }
}

class CrossLayoutStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("reader_cross", Context.MODE_PRIVATE)
    fun load(): Map<String, List<ReaderAction>> = CrossLayout.MODES.associateWith { CrossLayout.parse(it, preferences.getString(it, null)) }
    fun save(mode: String, cross: List<ReaderAction>) { preferences.edit().putString(mode, CrossLayout.format(cross)).apply() }
}
