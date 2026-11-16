package com.hypermarker.flow

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geometría pura del menú radial (pantalla 11). Hasta [PER_PAGE] pétalos por página; si hay más
 * elementos, se añade un pétalo «+N» que pasa a la página siguiente. Girar el anillo cambia de página.
 */
object RadialLayout {
    const val PER_PAGE = 6

    /** Un pétalo: `item` es el índice del elemento, o `null` para el pétalo «+N». */
    data class Petal(val item: Int?, val angle: Double, val more: Int = 0) {
        fun x(radius: Float) = (cos(angle) * radius).toFloat()
        fun y(radius: Float) = (sin(angle) * radius).toFloat()
    }

    fun pages(count: Int) = if (count <= 0) 1 else ceil(count / PER_PAGE.toDouble()).toInt()

    /** Pétalos de `page` (se normaliza en anillo). El primero queda arriba y siguen en sentido horario. */
    fun petals(count: Int, page: Int): List<Petal> {
        if (count <= 0) return emptyList()
        val total = pages(count)
        val current = Math.floorMod(page, total)
        val start = current * PER_PAGE
        val items = (start until minOf(count, start + PER_PAGE)).toList()
        val remaining = count - items.size
        val slots = items.size + if (total > 1) 1 else 0
        val step = 2 * PI / slots.coerceAtLeast(1)
        return items.mapIndexed { index, item -> Petal(item, -PI / 2 + index * step) } +
            if (total > 1) listOf(Petal(null, -PI / 2 + items.size * step, remaining)) else emptyList()
    }

    /** Pétalo bajo `(dx, dy)` relativo al centro, si cae en la banda del anillo. */
    fun petalAt(petals: List<Petal>, dx: Float, dy: Float, radius: Float, band: Float): Petal? {
        val distance = hypot(dx, dy)
        if (distance < radius - band || distance > radius + band || petals.isEmpty()) return null
        val angle = atan2(dy.toDouble(), dx.toDouble())
        return petals.minByOrNull { angularDistance(it.angle, angle) }
    }

    /** Páginas que avanza un giro de `degrees` (positivo = horario). Un paso cada 50°. */
    fun pageShift(degrees: Double) = (degrees / 50.0).toInt()

    fun angularDistance(a: Double, b: Double): Double {
        val d = Math.floorMod(((a - b) * 1_000_000).toLong(), (2 * PI * 1_000_000).toLong()) / 1_000_000.0
        return minOf(d, 2 * PI - d)
    }
}
