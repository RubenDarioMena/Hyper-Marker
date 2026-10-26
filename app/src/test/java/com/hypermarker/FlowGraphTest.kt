package com.hypermarker

import com.hypermarker.flow.FlowCamera
import com.hypermarker.flow.FlowGraph
import org.junit.Assert.*
import org.junit.Test

class FlowGraphTest {
    private val edges = listOf("a" to "b", "b" to "c", "a" to "d", "e" to "c")

    @Test fun rejectsSelfLoopsAndCyclesButAllowsSharedChildren() {
        assertTrue(FlowGraph.wouldCreateCycle(edges, "a", "a"))
        assertTrue(FlowGraph.wouldCreateCycle(edges, "c", "a"))
        assertTrue(FlowGraph.wouldCreateCycle(edges, "b", "a"))
        assertFalse(FlowGraph.wouldCreateCycle(edges, "d", "c"))
        assertFalse(FlowGraph.wouldCreateCycle(edges, "c", "d"))
    }

    @Test fun collapsingHidesOnlyNodesWithoutAnotherOpenPath() {
        val nodes = listOf("a", "b", "c", "d", "e")
        assertEquals(emptySet<String>(), FlowGraph.hidden(nodes, edges, emptySet()))
        // c sigue visible porque e también es su padre.
        assertEquals(setOf("b", "d"), FlowGraph.hidden(nodes, edges, setOf("a")))
        assertEquals(setOf("b", "c", "d"), FlowGraph.hidden(nodes, edges, setOf("a", "e")))
        assertEquals(emptySet<String>(), FlowGraph.hidden(nodes, edges, setOf("c")))
        val hidden = FlowGraph.hidden(nodes, edges, setOf("a", "e"))
        assertEquals(mapOf("a" to 3, "e" to 1), FlowGraph.hiddenCounts(edges, setOf("a", "e"), hidden))
    }

    @Test fun nestedCollapsedNodeIsHiddenAndRootlessCyclesStayVisible() {
        val chain = listOf("a" to "b", "b" to "c")
        val hidden = FlowGraph.hidden(listOf("a", "b", "c"), chain, setOf("a", "b"))
        assertEquals(setOf("b", "c"), hidden)
        assertEquals(mapOf("a" to 2), FlowGraph.hiddenCounts(chain, setOf("a", "b"), hidden))
        val cycle = listOf("x" to "y", "y" to "x")
        assertEquals(emptySet<String>(), FlowGraph.hidden(listOf("x", "y"), cycle, setOf("x")))
    }

    @Test fun nearestPicksTheClosestPointInsideTheRadius() {
        val points = listOf("a" to (0f to 0f), "b" to (10f to 0f))
        assertEquals("b", FlowGraph.nearest(points, 7f, 0f, 20f) { it.second }?.first)
        assertNull(FlowGraph.nearest(points, 100f, 100f, 20f) { it.second })
    }

    @Test fun cameraRoundTripsAndZoomKeepsTheFocalPointFixed() {
        val density = 2.5f
        val camera = FlowCamera(40f, -30f, 1.3f)
        assertEquals(12f, camera.toWorldX(camera.toScreenX(12f, density), density), 1e-4f)
        assertEquals(-7f, camera.toWorldY(camera.toScreenY(-7f, density), density), 1e-4f)
        val worldX = camera.toWorldX(300f, density); val worldY = camera.toWorldY(500f, density)
        val zoomed = camera.zoom(1.8f, 300f, 500f)
        assertEquals(300f, zoomed.toScreenX(worldX, density), 1e-3f)
        assertEquals(500f, zoomed.toScreenY(worldY, density), 1e-3f)
        assertEquals(FlowCamera.MAX_SCALE, camera.zoom(100f, 0f, 0f).scale, 0f)
        assertEquals(FlowCamera.MIN_SCALE, camera.zoom(.001f, 0f, 0f).scale, 0f)
    }

    @Test fun fitCentersAllPoints() {
        val density = 2f
        val camera = FlowCamera.fit(listOf(-100f to -50f, 100f to 50f), 1000f, 800f, density, 50f)
        assertEquals(500f, camera.toScreenX(0f, density), 1e-3f)
        assertEquals(400f, camera.toScreenY(0f, density), 1e-3f)
        assertTrue(camera.toScreenX(-100f, density) >= 50f && camera.toScreenX(100f, density) <= 950f)
    }
}
