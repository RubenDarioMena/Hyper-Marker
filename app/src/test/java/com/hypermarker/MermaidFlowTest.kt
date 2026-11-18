package com.hypermarker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.mermaid.ChunkAssembler
import com.hypermarker.mermaid.MermaidFlow
import com.hypermarker.mermaid.MermaidGraph
import com.hypermarker.mermaid.pngSize
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.storage.MERMAID_MAX_CHARS
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MermaidFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LibraryDatabase
    private lateinit var repo: LibraryRepository
    /** Grafo que el visor extrae del diagrama de referencia (`fixtures/mermaid/generador-gravitacional.mmd`). */
    private val reference by lazy { MermaidFlow.parse(javaClass.getResource("/mermaid/generador-gravitacional.graph.json")!!.readText()) }
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(context, database)
    }
    @After fun tearDown() { database.close() }

    private fun node(id: String, label: String = id, x: Double? = 0.0, y: Double? = 0.0) = MermaidGraph.Node(id, label, x, y)
    private fun edge(from: String, to: String, label: String = "", stroke: String = "normal") = MermaidGraph.Edge(from, to, label, stroke)
    private fun graph(nodes: List<MermaidGraph.Node>, edges: List<MermaidGraph.Edge>, groups: List<MermaidGraph.Group> = emptyList()) =
        MermaidGraph("flowchart-v2", true, "TB", nodes, edges, groups)

    @Test fun referenceDiagramBecomesAFlowWithTheSameStructureAndLayout() {
        assertEquals(21, reference.nodes.size); assertEquals(25, reference.edges.size); assertEquals(2, reference.groups.size)
        val plan = MermaidFlow.plan(reference)
        assertEquals(21, plan.nodes.size); assertEquals(25, plan.edges.size)
        assertEquals(0, plan.cycles + plan.duplicates + plan.loops + plan.invisible + plan.labels)
        val byTitle = plan.nodes.associateBy { it.title }
        val a1 = byTitle.getValue("Transmutación nuclear")
        assertTrue(a1.body.startsWith("Producir elementos escasos"))
        assertTrue(a1.body.endsWith("Grupo: RAMA A · MATERIA EXTREMA"))
        val b1 = byTitle.getValue("Acelerador de partículas")
        assertNotNull(a1.ring); assertNotNull(b1.ring); assertNotEquals(a1.ring, b1.ring)
        assertNull(byTitle.getValue("Superarma de etapa final").ring)
        assertEquals("", byTitle.getValue("Núcleo gravitacional cargado").body)
        // El layout TB se conserva: la raíz arriba, la superarma abajo, rama B a la izquierda de rama A.
        val root = byTitle.getValue("INFRAESTRUCTURA INICIAL"); val last = byTitle.getValue("Superarma de etapa final")
        assertTrue(root.y < a1.y && a1.y < last.y); assertTrue(b1.x < a1.x)
        assertEquals(0f, plan.nodes.minOf { it.x }, .01f); assertEquals(0f, plan.nodes.minOf { it.y }, .01f)
        // La captura del agujero negro tiene dos padres (ambas rutas).
        val capture = plan.nodes.indexOfFirst { it.title.startsWith("CAPTURA DE AGUJERO NEGRO") }
        assertEquals(2, plan.edges.count { it.second == capture })
        assertTrue(plan.summary().startsWith("21 puntos y 25 conexiones copiados."))
    }

    @Test fun labelsBecomePlainLinesWithoutHtmlOrEntities() {
        assertEquals(listOf("Uno", "Dos"), MermaidFlow.lines("Uno<br/>Dos"))
        assertEquals(listOf("A & B \"x\" <c>"), MermaidFlow.lines("A #amp; B #quot;x#quot; #lt;c#gt;"))
        assertEquals(listOf("negrita y código"), MermaidFlow.lines("`**negrita** y código`"))
        assertEquals(listOf("sin etiquetas"), MermaidFlow.lines("<b>sin</b> <img src=x onerror=alert(1)>etiquetas"))
        assertEquals(listOf("#"), MermaidFlow.lines("#35;"))
        // Así llegan desde el visor (entidades HTML tras decodificar los marcadores internos de Mermaid).
        assertEquals(listOf("Medio \"quoted\" <x>", "Detalle"), MermaidFlow.lines("Medio &quot;quoted&quot; &lt;x&gt;<br>Detalle"))
        assertEquals(listOf("Fase # 2"), MermaidFlow.lines("Fase &#35; 2"))
    }

    @Test fun cyclesAreNeverCreatedButAreWrittenDownInTheSourcePoint() {
        val plan = MermaidFlow.plan(graph(listOf(node("a", "Inicio"), node("b", "Medio"), node("c", "Fin")),
            listOf(edge("a", "b"), edge("b", "c", "sí"), edge("c", "a", "reintentar"), edge("a", "b"), edge("b", "b"), edge("a", "c", stroke = "invisible"))))
        assertEquals(listOf(0 to 1, 1 to 2), plan.edges)
        assertEquals(1, plan.cycles); assertEquals(1, plan.duplicates); assertEquals(1, plan.loops); assertEquals(1, plan.invisible); assertEquals(1, plan.labels)
        assertTrue(plan.nodes[2].body.contains("→ Inicio (conexión omitida: crearía un ciclo) · reintentar"))
        assertTrue(plan.nodes[2].body.contains("← Medio: sí"))
        assertTrue(plan.nodes[1].body.contains("↻ Se conecta consigo mismo"))
        assertTrue(plan.summary().contains("1 conexión(es) cerraban un ciclo"))
    }

    @Test fun subgraphUsedAsEndpointIsCopiedAsItsOwnPointAndUnplacedNodesGoBelow() {
        val group = MermaidGraph.Group("G", "Equipo", listOf("x"), 100.0, 200.0, 80.0, "#2dd4bf")
        val plan = MermaidFlow.plan(graph(listOf(node("x", "Dentro", 100.0, 210.0), node("y", "Fuera", 300.0, 400.0), node("z", "Sin posición", null, null)),
            listOf(edge("y", "G")), listOf(group)))
        assertEquals(4, plan.nodes.size)
        val point = plan.nodes.last()
        assertEquals("Equipo", point.title); assertEquals(0xFF2DD4BFL, point.ring)
        assertEquals(listOf(1 to 3), plan.edges)
        val loose = plan.nodes[2]
        assertTrue(loose.y > plan.nodes[1].y)
    }

    @Test fun appendingAddsAHeaderAndShiftsEdgeIndexes() {
        val plan = MermaidFlow.plan(graph(listOf(node("a", x = 0.0, y = 0.0), node("b", x = 200.0, y = 100.0)), listOf(edge("a", "b"))), 1000f, 50f, "Plan B" to "Copia")
        assertEquals("Plan B", plan.nodes[0].title); assertEquals(1100f, plan.nodes[0].x, .01f); assertEquals(50f, plan.nodes[0].y, .01f)
        assertEquals(listOf(1 to 2), plan.edges)
        assertTrue(plan.nodes.drop(1).all { it.x >= 1000f && it.y > 50f })
    }

    @Test fun unsupportedDiagramsAndOversizedInputAreRejected() {
        val sequence = MermaidFlow.parse("""{"type":"sequence","supported":false}""")
        try { MermaidFlow.plan(sequence); fail("Se convirtió un diagrama no compatible") } catch (_: IllegalArgumentException) {}
        val nodes = (0..MermaidFlow.MAX_NODES).joinToString(",") { """{"id":"n$it","label":"x"}""" }
        try { MermaidFlow.parse("""{"type":"flowchart-v2","supported":true,"nodes":[$nodes]}"""); fail("Se aceptaron demasiados nodos") } catch (_: IllegalArgumentException) {}
        val hostile = MermaidFlow.parse("""{"type":"flowchart-v2","supported":true,"nodes":[{"id":"a","label":"A","x":"NaN","y":1e300}]}""")
        assertNull(hostile.nodes[0].x); assertNull(hostile.nodes[0].y)
    }

    @Test fun chunksMustArriveInOrderAndWithinTheLimit() {
        val chunks = ChunkAssembler(10)
        assertNull(chunks.accept(0, 2, "abcd")); assertEquals("abcdef", chunks.accept(1, 2, "ef"))
        try { ChunkAssembler(10).accept(1, 2, "a"); fail("Fragmento fuera de orden aceptado") } catch (_: IllegalArgumentException) {}
        try { ChunkAssembler(3).accept(0, 1, "abcd"); fail("Límite superado") } catch (_: IllegalArgumentException) {}
        val changing = ChunkAssembler(10); changing.accept(0, 3, "a")
        try { changing.accept(1, 2, "b"); fail("Total cambiante aceptado") } catch (_: IllegalArgumentException) {}
    }

    @Test fun pngSizeMatchesTheViewerRule() {
        assertEquals(2776 to 4752, pngSize(925.25, 1584.0, 3))
        val (w, h) = pngSize(4000.0, 3000.0, 8)
        assertTrue(w <= 8192 && h <= 8192 && w.toLong() * h <= 40_000_000)
        assertEquals(4.0 / 3, w.toDouble() / h, .01)
    }

    @Test fun repositoryStoresSourceAndCopiesIntoNewAndExistingFlows() = runBlocking {
        val diagram = repo.createMermaid(null, "Superarma", File("../fixtures/mermaid/generador-gravitacional.mmd").readText())
        assertEquals("MERMAID", diagram.type)
        assertTrue(repo.readMermaid(diagram.id).startsWith("flowchart TB"))
        repo.saveMermaid(diagram.id, "flowchart LR\n  a --> b\n")
        assertEquals("flowchart LR\n  a --> b\n", repo.readMermaid(diagram.id))
        assertEquals(2, repo.dao.document(diagram.id)!!.revision)
        try { repo.saveMermaid(diagram.id, "x".repeat(MERMAID_MAX_CHARS + 1)); fail("Diagrama demasiado grande aceptado") } catch (_: IllegalArgumentException) {}

        val (flow, plan) = repo.copyMermaidToFlow(reference, null, null, "Superarma", "Superarma" to "Copia")
        assertEquals("FLOW", flow.type); assertEquals(21, plan.nodes.size)
        assertEquals(21, repo.dao.flowNodes(flow.id).size); assertEquals(25, repo.dao.flowEdges(flow.id).size)
        assertTrue(repo.dao.flowNodes(flow.id).none { it.title == "Superarma" })
        // Copia, no enlace: ninguna relación apunta al diagrama y editarlo no cambia el Flow.
        assertTrue(repo.dao.flowLinks(flow.id).isEmpty())
        repo.saveMermaid(diagram.id, "flowchart LR\n  z --> w\n")
        assertEquals(21, repo.dao.flowNodes(flow.id).size)

        val first = repo.dao.flowNodes(flow.id)
        val second = repo.copyMermaidToFlow(reference, flow.id, null, "Variante", "Variante" to "Copia para comparar").second
        assertEquals(22, second.nodes.size)
        val nodes = repo.dao.flowNodes(flow.id)
        assertEquals(43, nodes.size); assertEquals(50, repo.dao.flowEdges(flow.id).size)
        val added = nodes.filter { node -> first.none { it.id == node.id } }
        assertTrue("la segunda copia queda a la derecha", added.all { it.x >= first.maxOf { f -> f.x } + 359f })
        assertEquals(first.minOf { it.y }, added.single { it.title == "Variante" }.y, .01f)
        assertEquals(2, nodes.count { it.title == "Transmutación nuclear" })

        // Duplicar el documento conserva el código.
        repo.duplicate(diagram.id, false)
        val copy = repo.dao.documents().single { it.type == "MERMAID" && it.id != diagram.id }
        assertEquals("flowchart LR\n  z --> w\n", repo.readMermaid(copy.id))
        try { repo.copyMermaidToFlow(reference, diagram.id, null, "x", null); fail("Se copió a algo que no es un Flow") } catch (_: IllegalArgumentException) {}
    }
}
