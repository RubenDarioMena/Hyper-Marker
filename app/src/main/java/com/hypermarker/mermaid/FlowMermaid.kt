package com.hypermarker.mermaid

import com.hypermarker.storage.FlowEdge
import com.hypermarker.storage.FlowNode

/**
 * Flow → código Mermaid (copia, para compartir o imprimir como SVG/PNG). Mermaid recalcula la disposición.
 *
 * Deshace las anotaciones que deja `MermaidFlow.plan`, para que ida y vuelta no pierdan estructura:
 * «Grupo: X» → subgrafo; «← Padre: texto» → etiqueta de la flecha; «→ Destino (conexión omitida…)» → flecha
 * punteada (Mermaid sí admite ciclos); «↻ Se conecta consigo mismo» → autoconexión.
 */
object FlowMermaid {
    const val MAX_EDGES = 4_000
    private const val BODY_LINES = 2
    private const val LINE_CHARS = 80
    private val group = Regex("^Grupo: (.+)$")
    private val incoming = Regex("^← (.+?): (.+)$")
    private val omitted = Regex("^→ (.+) \\(conexión omitida: crearía un ciclo\\)(?: · (.+))?$")
    private val loop = Regex("^↻ Se conecta consigo mismo(?:: (.+))?$")

    /** Texto seguro dentro de `["…"]` o `|…|`: entidades de Mermaid para lo que rompería la sintaxis. */
    fun escape(value: String): String = value.replace("#", "#35;").replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;")
        .replace("|", "#124;").replace("`", "#96;").replace(Regex("[\\r\\n\\t]+"), " ").trim()

    private fun clip(value: String) = if (value.length <= LINE_CHARS) value else value.take(LINE_CHARS - 1).trimEnd() + "…"

    fun generate(title: String, nodes: List<FlowNode>, edges: List<FlowEdge>): String {
        require(nodes.isNotEmpty()) { "El Flow no tiene puntos que convertir" }
        val ordered = nodes.sortedWith(compareBy<FlowNode>({ it.y }, { it.x }, { it.id }))
        val ids = ordered.withIndex().associate { it.value.id to "n${it.index + 1}" }
        val byId = ordered.associateBy { it.id }
        val known = edges.filter { it.fromId in ids && it.toId in ids }
        val parents = known.groupBy({ it.toId }, { it.fromId })
        val labels = mutableMapOf<Pair<String, String>, String>()
        val extra = mutableListOf<String>()
        val groups = linkedMapOf<String, MutableList<String>>()
        val text = mutableMapOf<String, List<String>>()
        for (node in ordered) {
            val lines = mutableListOf<String>()
            for (raw in node.body.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
                val inGroup = group.matchEntire(raw); val arrow = incoming.matchEntire(raw)
                val cycle = omitted.matchEntire(raw); val self = loop.matchEntire(raw)
                when {
                    inGroup != null -> groups.getOrPut(inGroup.groupValues[1]) { mutableListOf() } += node.id
                    arrow != null -> {
                        val parent = parents[node.id].orEmpty().firstOrNull { byId[it]?.title == arrow.groupValues[1] && (it to node.id) !in labels }
                        if (parent != null) labels[parent to node.id] = arrow.groupValues[2] else lines += raw
                    }
                    cycle != null -> {
                        val target = ordered.filter { it.title == cycle.groupValues[1] }.singleOrNull()
                        if (target != null) extra += "${ids[node.id]} -.->" + label(cycle.groupValues[2]) + " ${ids[target.id]}" else lines += raw
                    }
                    self != null -> extra += "${ids[node.id]} -.->" + label(self.groupValues[1]) + " ${ids[node.id]}"
                    else -> lines += raw
                }
            }
            text[node.id] = lines
        }
        require(known.size + extra.size <= MAX_EDGES) { "El Flow tiene demasiadas conexiones para un diagrama (máximo $MAX_EDGES)" }
        fun box(node: FlowNode): String {
            val head = node.title.ifBlank { text.getValue(node.id).firstOrNull().orEmpty() }
            val rest = text.getValue(node.id).let { if (node.title.isBlank()) it.drop(1) else it }.take(BODY_LINES)
            val parts = (listOf(head) + rest).filter { it.isNotBlank() }.map { escape(clip(it)) }
            return "${ids[node.id]}[\"${parts.joinToString("<br/>").ifEmpty { " " }}\"]"
        }
        val grouped = groups.values.flatten().toSet()
        return buildString {
            append("%% Copia del Flow «").append(title.replace(Regex("[\\r\\n]+"), " ").take(200)).append("» generada por Hyper-Marker\n")
            append("flowchart TB\n")
            ordered.filter { it.id !in grouped }.forEach { append("    ").append(box(it)).append('\n') }
            groups.entries.forEachIndexed { index, (name, members) ->
                append("\n    subgraph g${index + 1}[\"").append(escape(clip(name))).append("\"]\n        direction TB\n")
                members.distinct().forEach { append("        ").append(box(byId.getValue(it))).append('\n') }
                append("    end\n")
            }
            if (known.isNotEmpty() || extra.isNotEmpty()) append('\n')
            known.forEach { edge -> append("    ${ids[edge.fromId]} -->").append(label(labels[edge.fromId to edge.toId])).append(" ${ids[edge.toId]}\n") }
            extra.forEach { append("    ").append(it).append('\n') }
            val colored = ordered.filter { it.ringColor != null && it.id !in grouped }
            if (colored.isNotEmpty()) append('\n')
            colored.forEach { node -> append("    style ${ids[node.id]} stroke:#%06x,stroke-width:2px\n".format((node.ringColor!! and 0xFFFFFF))) }
        }
    }

    private fun label(value: String?) = value?.takeIf { it.isNotBlank() }?.let { "|${escape(clip(it))}|" } ?: ""
}
