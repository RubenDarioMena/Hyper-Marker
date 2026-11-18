package com.hypermarker.mermaid

import org.json.JSONArray
import org.json.JSONObject

/** Estructura de un flowchart tal como la entrega el visor (parser y layout de Mermaid). Datos no confiables. */
data class MermaidGraph(val type: String, val supported: Boolean, val direction: String,
                        val nodes: List<Node>, val edges: List<Edge>, val groups: List<Group>) {
    data class Node(val id: String, val label: String, val x: Double?, val y: Double?)
    data class Edge(val from: String, val to: String, val label: String, val stroke: String)
    data class Group(val id: String, val title: String, val nodes: List<String>, val x: Double?, val y: Double?, val height: Double?, val color: String?)
}

/** Copia planificada: puntos con posición de mundo (dp) y conexiones por índice, más lo que no pudo copiarse literalmente. */
data class FlowPlan(val nodes: List<Planned>, val edges: List<Pair<Int, Int>>, val cycles: Int, val duplicates: Int,
                    val loops: Int, val invisible: Int, val labels: Int) {
    data class Planned(val title: String, val body: String, val x: Float, val y: Float, val ring: Long?)
    /** Resumen legible para el aviso final; nunca oculta una conexión omitida. */
    fun summary(): String = buildString {
        append("${nodes.size} puntos y ${edges.size} conexiones copiados.")
        if (cycles > 0) append(" $cycles conexión(es) cerraban un ciclo: Flow no admite ciclos, así que se anotaron en el punto de origen.")
        if (labels > 0) append(" $labels etiqueta(s) de flecha se copiaron al texto del punto destino.")
        if (duplicates > 0) append(" $duplicates conexión(es) repetidas se unificaron.")
        if (loops > 0) append(" $loops autoconexión(es) se anotaron en su punto.")
        if (invisible > 0) append(" $invisible enlace(s) invisibles de maquetación se ignoraron.")
    }
}

object MermaidFlow {
    const val MAX_NODES = 5_000
    const val MAX_EDGES = 20_000
    private const val MAX_TEXT = 4_000
    /** Píxeles del layout de Mermaid → dp de mundo del Flow; 1:1 conserva la proporción que se ve en el diagrama. */
    private const val SCALE = 1f

    fun parse(json: String): MermaidGraph {
        require(json.length <= 16_000_000) { "El diagrama es demasiado grande para convertirlo" }
        val root = JSONObject(json)
        fun text(value: JSONObject, key: String) = value.optString(key, "").take(MAX_TEXT)
        fun number(value: JSONObject, key: String) = if (value.isNull(key)) null else value.optDouble(key).takeIf { it.isFinite() && kotlin.math.abs(it) < 1e7 }
        fun objects(key: String, limit: Int): List<JSONObject> {
            val array = root.optJSONArray(key) ?: JSONArray()
            require(array.length() <= limit) { "El diagrama supera el máximo de $limit elementos ($key)" }
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
        val nodes = objects("nodes", MAX_NODES).map { MermaidGraph.Node(text(it, "id"), text(it, "label"), number(it, "x"), number(it, "y")) }
            .filter { it.id.isNotBlank() }.distinctBy { it.id }
        val edges = objects("edges", MAX_EDGES).map { MermaidGraph.Edge(text(it, "from"), text(it, "to"), text(it, "label"), text(it, "stroke")) }
        val groups = objects("groups", 1_000).map { group ->
            val members = group.optJSONArray("nodes") ?: JSONArray()
            MermaidGraph.Group(text(group, "id"), text(group, "title"), (0 until minOf(members.length(), MAX_NODES)).map { members.optString(it).take(MAX_TEXT) },
                number(group, "x"), number(group, "y"), number(group, "height"), group.optString("color").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) })
        }
        return MermaidGraph(root.optString("type").take(80), root.optBoolean("supported"), root.optString("direction", "TB").take(4), nodes, edges, groups)
    }

    /** Etiqueta Mermaid → líneas de texto plano: saltos `<br>`, sin HTML ni marcas de cadena Markdown, entidades decodificadas. */
    fun lines(label: String): List<String> {
        var value = label.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]{0,500}>"), "")
        value = value.replace(Regex("#(\\d{1,7});")) { m -> m.groupValues[1].toInt().takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: "" }
        val named = mapOf("quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">", "nbsp" to " ", "apos" to "'")
        value = value.replace(Regex("[#&](quot|amp|lt|gt|nbsp|apos);")) { named.getValue(it.groupValues[1]) }
            .replace(Regex("&#(\\d{1,7});")) { m -> m.groupValues[1].toInt().takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: "" }
        value = value.trim().removeSurrounding("`").replace("**", "").replace("__", "")
        return value.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun ring(color: String?): Long? = color?.removePrefix("#")?.toLongOrNull(16)?.let { 0xFF000000L or it }

    /**
     * Planifica la copia de un flowchart dentro de un Flow, a partir de `(originX, originY)` en dp de mundo.
     * Flow es un grafo sin ciclos: una arista que cerraría un ciclo no se crea, pero queda escrita en el punto de origen.
     * `header` añade un punto suelto con el nombre del diagrama encima de la copia (útil para comparar varios en un Flow).
     */
    fun plan(graph: MermaidGraph, originX: Float = 0f, originY: Float = 0f, header: Pair<String, String>? = null): FlowPlan {
        require(graph.supported) { "Por ahora sólo los diagramas flowchart (o graph) se convierten en Flow" }
        val byId = graph.nodes.associateBy { it.id }
        val groupsById = graph.groups.associateBy { it.id }
        // Subgrafo más interno de cada nodo: Mermaid lista los internos antes que los que los contienen.
        val groupOf = mutableMapOf<String, MermaidGraph.Group>()
        graph.groups.forEach { group -> group.nodes.forEach { id -> groupOf.putIfAbsent(id, group) } }
        // Un subgrafo usado como extremo de una flecha se copia como un punto propio.
        val endpointGroups = graph.edges.flatMap { listOf(it.from, it.to) }.filter { it !in byId && it in groupsById }.distinct()
        data class Draft(val key: String, val title: String, val body: StringBuilder, val x: Double?, val y: Double?, val ring: Long?)
        val drafts = mutableListOf<Draft>()
        graph.nodes.forEach { node ->
            val text = lines(node.label).ifEmpty { listOf(node.id) }
            val body = StringBuilder(text.drop(1).joinToString("\n"))
            val group = groupOf[node.id]
            if (group != null) { if (body.isNotEmpty()) body.append("\n\n"); body.append("Grupo: ").append(lines(group.title).joinToString(" ").ifEmpty { group.id }) }
            drafts += Draft(node.id, text.first().take(200), body, node.x, node.y, ring(group?.color))
        }
        endpointGroups.forEach { id ->
            val group = groupsById.getValue(id)
            val top = if (group.y != null && group.height != null) group.y - group.height / 2 else group.y
            drafts += Draft(id, lines(group.title).joinToString(" ").ifEmpty { id }.take(200), StringBuilder("Subgrafo de Mermaid"), group.x, top, ring(group.color))
        }
        require(drafts.isNotEmpty()) { "El diagrama no tiene puntos que copiar" }
        require(drafts.size + (if (header != null) 1 else 0) <= MAX_NODES) { "El Flow admite hasta $MAX_NODES puntos" }
        val index = drafts.withIndex().associate { it.value.key to it.index }
        val pairs = linkedSetOf<Pair<Int, Int>>()
        var cycles = 0; var duplicates = 0; var loops = 0; var invisible = 0; var labels = 0
        val children = HashMap<Int, MutableList<Int>>()
        fun reaches(start: Int, target: Int): Boolean {
            val seen = HashSet<Int>(); val stack = ArrayDeque(listOf(start))
            while (stack.isNotEmpty()) { val next = stack.removeLast(); if (next == target) return true; if (seen.add(next)) children[next]?.let(stack::addAll) }
            return false
        }
        fun note(at: Int, line: String) { val body = drafts[at].body; if (body.isNotEmpty()) body.append("\n"); body.append(line) }
        for (edge in graph.edges) {
            val from = index[edge.from] ?: continue
            val to = index[edge.to] ?: continue
            if (edge.stroke == "invisible") { invisible++; continue }
            val label = lines(edge.label).joinToString(" ")
            if (from == to) { loops++; note(from, "↻ Se conecta consigo mismo" + if (label.isNotEmpty()) ": $label" else ""); continue }
            if (from to to in pairs) { duplicates++; if (label.isNotEmpty()) { labels++; note(to, "← ${drafts[from].title}: $label") }; continue }
            // Misma regla que FlowGraph.wouldCreateCycle, con adyacencia incremental para no recorrer todas las aristas cada vez.
            if (reaches(to, from)) {
                cycles++
                note(from, "→ ${drafts[to].title} (conexión omitida: crearía un ciclo)" + if (label.isNotEmpty()) " · $label" else "")
                continue
            }
            pairs += from to to; children.getOrPut(from) { mutableListOf() } += to
            if (label.isNotEmpty()) { labels++; note(to, "← ${drafts[from].title}: $label") }
        }
        require(pairs.size <= MAX_EDGES) { "El Flow admite hasta $MAX_EDGES conexiones" }
        // Posiciones: centros del layout de Mermaid trasladados al origen; lo que no tenga posición va en una fila debajo.
        val placed = drafts.filter { it.x != null && it.y != null }
        val minX = placed.minOfOrNull { it.x!! } ?: 0.0
        val minY = placed.minOfOrNull { it.y!! } ?: 0.0
        val maxY = placed.maxOfOrNull { it.y!! } ?: 0.0
        val maxX = placed.maxOfOrNull { it.x!! } ?: 0.0
        val shift = if (header != null) 140f else 0f
        var spare = 0
        val nodes = drafts.map { draft ->
            val x = if (draft.x != null && draft.y != null) originX + ((draft.x - minX) * SCALE).toFloat() else originX + 200f * spare
            val y = if (draft.x != null && draft.y != null) originY + shift + ((draft.y - minY) * SCALE).toFloat()
                else originY + shift + ((maxY - minY) * SCALE).toFloat() + 200f.also { spare++ }
            FlowPlan.Planned(draft.title.ifBlank { draft.key.take(200) }, draft.body.toString().take(256_000), x, y, draft.ring)
        }
        val all = if (header == null) nodes else listOf(FlowPlan.Planned(header.first.take(200).ifBlank { "Diagrama" }, header.second.take(256_000),
            originX + ((maxX - minX) * SCALE / 2).toFloat(), originY, 0xFFFFC700L)) + nodes
        val offset = if (header == null) 0 else 1
        return FlowPlan(all, pairs.map { (a, b) -> a + offset to b + offset }, cycles, duplicates, loops, invisible, labels)
    }
}
