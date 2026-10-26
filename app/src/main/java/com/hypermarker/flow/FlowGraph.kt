package com.hypermarker.flow

import kotlin.math.max
import kotlin.math.min

/** Reglas puras del grafo de Flow Dots: dirigido, sin ciclos, con plegado por alcanzabilidad. */
object FlowGraph {
    private fun children(edges: Collection<Pair<String, String>>) = edges.groupBy({ it.first }, { it.second })

    /** `true` si añadir `from → to` cerraría un ciclo (incluida la autoconexión). */
    fun wouldCreateCycle(edges: Collection<Pair<String, String>>, from: String, to: String): Boolean {
        if (from == to) return true
        val next = children(edges)
        val seen = mutableSetOf(to); val stack = ArrayDeque(listOf(to))
        while (stack.isNotEmpty()) {
            for (child in next[stack.removeLast()].orEmpty()) {
                if (child == from) return true
                if (seen.add(child)) stack.addLast(child)
            }
        }
        return false
    }

    /**
     * Nodos ocultos por plegado: descendientes de algún nodo plegado que no se alcanzan desde una raíz
     * sin atravesar un nodo plegado. Un hijo con otro padre visible sigue visible. Si datos externos
     * trajeran un ciclo sin raíz, sus nodos se muestran en lugar de desaparecer.
     */
    fun hidden(nodes: Collection<String>, edges: Collection<Pair<String, String>>, collapsed: Set<String>): Set<String> {
        if (collapsed.isEmpty()) return emptySet()
        val ids = nodes.toSet()
        val valid = edges.filter { it.first in ids && it.second in ids }
        val next = children(valid)
        val below = mutableSetOf<String>(); val pending = ArrayDeque(collapsed.filter { it in ids }.flatMap { next[it].orEmpty() })
        while (pending.isNotEmpty()) { val node = pending.removeLast(); if (below.add(node)) pending.addAll(next[node].orEmpty()) }
        val roots = ids.filter { node -> valid.none { it.second == node } }
        fun walk(stopAtCollapsed: Boolean): Set<String> {
            val seen = mutableSetOf<String>(); val stack = ArrayDeque(roots)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                if (seen.add(node) && !(stopAtCollapsed && node in collapsed)) stack.addAll(next[node].orEmpty())
            }
            return seen
        }
        val visible = walk(stopAtCollapsed = true)
        val rootless = ids - walk(stopAtCollapsed = false)
        // Sólo se ocultan nodos que el plegado alcanza, que ninguna ruta abierta muestra y que tienen raíz.
        return below - visible - rootless
    }

    /** Cuántos nodos ocultos cuelgan de cada nodo plegado visible, para la etiqueta «+N». */
    fun hiddenCounts(edges: Collection<Pair<String, String>>, collapsed: Set<String>, hidden: Set<String>): Map<String, Int> {
        val next = children(edges)
        return collapsed.filter { it !in hidden }.associateWith { root ->
            val seen = mutableSetOf<String>(); val stack = ArrayDeque(next[root].orEmpty())
            while (stack.isNotEmpty()) { val node = stack.removeLast(); if (node in hidden && seen.add(node)) stack.addAll(next[node].orEmpty()) }
            seen.size
        }.filterValues { it > 0 }
    }

    /** Punto más cercano a `(x, y)` dentro de `radius`, en coordenadas de mundo. */
    fun <T> nearest(items: Iterable<T>, x: Float, y: Float, radius: Float, position: (T) -> Pair<Float, Float>): T? {
        var best: T? = null; var distance = radius * radius
        for (item in items) {
            val (px, py) = position(item)
            val d = (px - x) * (px - x) + (py - y) * (py - y)
            if (d <= distance) { best = item; distance = d }
        }
        return best
    }
}

/**
 * Cámara del canvas. El mundo está en dp; `scale` es el zoom del usuario y `density` convierte dp a px.
 * `offsetX/offsetY` son la posición en px de pantalla del origen del mundo.
 */
data class FlowCamera(val offsetX: Float = 0f, val offsetY: Float = 0f, val scale: Float = 1f) {
    fun unit(density: Float) = density * scale
    fun toScreenX(x: Float, density: Float) = x * unit(density) + offsetX
    fun toScreenY(y: Float, density: Float) = y * unit(density) + offsetY
    fun toWorldX(x: Float, density: Float) = (x - offsetX) / unit(density)
    fun toWorldY(y: Float, density: Float) = (y - offsetY) / unit(density)
    fun pan(dx: Float, dy: Float) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /** Aplica `factor` manteniendo fijo el punto de pantalla `(fx, fy)`. */
    fun zoom(factor: Float, fx: Float, fy: Float): FlowCamera {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = next / scale
        return FlowCamera(fx - (fx - offsetX) * applied, fy - (fy - offsetY) * applied, next)
    }

    companion object {
        const val MIN_SCALE = .2f
        const val MAX_SCALE = 4f

        /** Encuadra los puntos dados en una vista `width × height` px con `margin` px. */
        fun fit(points: List<Pair<Float, Float>>, width: Float, height: Float, density: Float, margin: Float): FlowCamera {
            if (points.isEmpty() || width <= 0f || height <= 0f) return FlowCamera(width / 2, height / 2)
            val minX = points.minOf { it.first }; val maxX = points.maxOf { it.first }
            val minY = points.minOf { it.second }; val maxY = points.maxOf { it.second }
            val spanX = max(maxX - minX, 1f) * density; val spanY = max(maxY - minY, 1f) * density
            val scale = min(max(width - 2 * margin, 1f) / spanX, max(height - 2 * margin, 1f) / spanY).coerceIn(MIN_SCALE, 1.5f)
            val unit = density * scale
            return FlowCamera(width / 2 - (minX + maxX) / 2 * unit, height / 2 - (minY + maxY) / 2 * unit, scale)
        }
    }
}
