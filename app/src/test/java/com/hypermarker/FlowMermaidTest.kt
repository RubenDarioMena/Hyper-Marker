package com.hypermarker

import com.hypermarker.mermaid.FlowMermaid
import com.hypermarker.mermaid.MermaidFlow
import com.hypermarker.storage.FlowEdge
import com.hypermarker.storage.FlowNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FlowMermaidTest {
    private fun node(id: String, title: String, body: String, x: Float, y: Float, ring: Long? = null) = FlowNode(id, "doc", title, body, x, y, ringColor = ring)
    private fun edge(from: String, to: String) = FlowEdge("$from-$to", "doc", from, to)

    /** Salida exacta: la misma que `web/tests/mermaid.test.mjs` dibuja con Mermaid real. */
    @Test fun exportRestoresGroupsLabelsAndOmittedCyclesAndEscapesText() {
        val nodes = listOf(
            node("a", "Inicio", "Arranque del sistema", 0f, 0f),
            node("b", "Medio \"quoted\" <x>", "← Inicio: sí\nDetalle 1\nDetalle 2\nDetalle 3", 0f, 100f),
            node("c", "Fin", "→ Inicio (conexión omitida: crearía un ciclo) · reintentar\nGrupo: Fase # 2", 0f, 200f, 0xFFE879F9),
            node("d", "Suelto", "", 300f, 200f, 0xFF2DD4BF))
        val code = FlowMermaid.generate("Prueba", nodes, listOf(edge("a", "b"), edge("b", "c")))
        assertEquals(File("../fixtures/mermaid/flow-export-sample.mmd").readText(), code)
    }

    @Test fun referenceSurvivesARoundTripThroughFlow() {
        val graph = MermaidFlow.parse(javaClass.getResource("/mermaid/generador-gravitacional.graph.json")!!.readText())
        val plan = MermaidFlow.plan(graph)
        val nodes = plan.nodes.mapIndexed { i, p -> node("p$i", p.title, p.body, p.x, p.y, p.ring) }
        val code = FlowMermaid.generate("Superarma", nodes, plan.edges.map { (a, b) -> edge("p$a", "p$b") })
        assertEquals(21, Regex("""^\s+n\d+\["""", RegexOption.MULTILINE).findAll(code).count())
        assertEquals(25, Regex("""^\s+n\d+ --> n\d+$""", RegexOption.MULTILINE).findAll(code).count())
        assertTrue(code.contains("subgraph g1[\"RAMA A · MATERIA EXTREMA\"]") || code.contains("subgraph g1[\"RAMA B · FÍSICA DE PARTÍCULAS\"]"))
        assertEquals(2, Regex("""^\s+subgraph """, RegexOption.MULTILINE).findAll(code).count())
        assertFalse(code.contains("Grupo:"))
        assertTrue(code.contains("[\"Transmutación nuclear<br/>Producir elementos escasos\"]"))
        assertFalse("los nodos agrupados no llevan estilo propio", code.contains("style"))
    }

    @Test fun annotationsThatDoNotMatchStayAsTextAndLimitsApply() {
        val nodes = listOf(node("a", "", "Solo cuerpo\n← Nadie: x\n→ Fantasma (conexión omitida: crearía un ciclo)", 0f, 0f))
        val code = FlowMermaid.generate("x", nodes, emptyList())
        assertTrue(code.contains("n1[\"Solo cuerpo<br/>← Nadie: x<br/>→ Fantasma (conexión omitida: crearía un ciclo)\"]"))
        assertEquals("a #35;1 #124; #96;b#96;", FlowMermaid.escape("a #1 | `b`"))
        try { FlowMermaid.generate("x", emptyList(), emptyList()); fail("Flow vacío convertido") } catch (_: IllegalArgumentException) {}
        val many = (0..FlowMermaid.MAX_EDGES).map { node("n$it", "P$it", "", 0f, it.toFloat()) }
        try { FlowMermaid.generate("x", many, many.zipWithNext().map { (a, b) -> edge(a.id, b.id) } + edge("n0", "n2")); fail("Demasiadas conexiones") } catch (_: IllegalArgumentException) {}
    }
}
