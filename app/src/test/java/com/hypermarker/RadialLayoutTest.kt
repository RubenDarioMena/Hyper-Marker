package com.hypermarker

import com.hypermarker.flow.FlowGraph
import com.hypermarker.flow.RadialLayout
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class RadialLayoutTest {
    /** FLOW-11: 2, 6 y 7+ elementos. */
    @Test fun petalsPerPage() {
        assertEquals(listOf(0, 1), RadialLayout.petals(2, 0).map { it.item })
        val six = RadialLayout.petals(6, 0)
        assertEquals((0..5).toList(), six.map { it.item }); assertEquals(1, RadialLayout.pages(6))
        val eleven = RadialLayout.petals(11, 0)
        assertEquals(7, eleven.size); assertNull(eleven.last().item); assertEquals(5, eleven.last().more)
        assertEquals(listOf(6, 7, 8, 9, 10, null), RadialLayout.petals(11, 1).map { it.item })
        assertEquals("El anillo vuelve a la primera página", eleven, RadialLayout.petals(11, 2))
        assertEquals(eleven, RadialLayout.petals(11, -2))
        assertTrue(RadialLayout.petals(0, 0).isEmpty())
    }

    @Test fun firstPetalIsOnTopAndSpacingIsEven() {
        val petals = RadialLayout.petals(4, 0)
        assertEquals(-PI / 2, petals[0].angle, 1e-9)
        assertEquals(0f, petals[0].x(100f), 1e-3f); assertEquals(-100f, petals[0].y(100f), 1e-3f)
        assertEquals(PI / 2, petals[1].angle - petals[0].angle, 1e-9)
    }

    @Test fun petalUnderFingerOnlyInsideTheBand() {
        val petals = RadialLayout.petals(6, 0)
        assertEquals(0, RadialLayout.petalAt(petals, 0f, -120f, 120f, 40f)?.item)
        assertEquals(1, RadialLayout.petalAt(petals, petals[1].x(120f), petals[1].y(120f), 120f, 40f)?.item)
        assertNull("El centro no es un pétalo", RadialLayout.petalAt(petals, 5f, 5f, 120f, 40f))
        assertNull(RadialLayout.petalAt(petals, 0f, -300f, 120f, 40f))
    }

    @Test fun rotationPagesAndAngles() {
        assertEquals(0, RadialLayout.pageShift(30.0)); assertEquals(1, RadialLayout.pageShift(60.0)); assertEquals(-2, RadialLayout.pageShift(-101.0))
        assertTrue(abs(RadialLayout.angularDistance(-PI + .01, PI - .01) - .02) < 1e-6)
    }

    @Test fun hitUsesPerNodeRadius() {
        data class N(val id: String, val x: Float, val y: Float, val r: Float)
        val nodes = listOf(N("small", 0f, 0f, 10f), N("photo", 30f, 0f, 40f))
        assertEquals("photo", FlowGraph.nearestWithin(nodes, 60f, 0f, { it.x to it.y }, { it.r })?.id)
        assertEquals("small", FlowGraph.nearestWithin(nodes, 2f, 0f, { it.x to it.y }, { it.r })?.id)
        assertNull(FlowGraph.nearestWithin(nodes, 0f, 80f, { it.x to it.y }, { it.r }))
    }
}
