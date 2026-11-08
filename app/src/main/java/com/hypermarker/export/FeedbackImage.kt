package com.hypermarker.export

import android.graphics.*
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.math.*

/** Limita la resolución, nunca el rectángulo del dibujo: incluye puntos negativos y trazos fuera del viewport. */
internal object FeedbackImage {
    suspend fun write(paths: JSONArray, view: WebView?, output: File): Double {
        var left = Double.POSITIVE_INFINITY; var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY; var bottom = Double.NEGATIVE_INFINITY
        for (i in 0 until paths.length()) {
            val path = paths.getJSONObject(i); val points = path.getJSONArray("points")
            val radius = path.getDouble("width") / 2
            for (j in 0 until points.length()) {
                val p = points.getJSONArray(j); val x = p.getDouble(0); val y = p.getDouble(1)
                require(x.isFinite() && y.isFinite())
                left = min(left, x - radius); top = min(top, y - radius)
                right = max(right, x + radius); bottom = max(bottom, y + radius)
            }
        }
        require(left.isFinite()) { "El dibujo no contiene puntos" }
        left -= 24; top -= 24; right += 24; bottom += 24
        val width = right - left; val height = bottom - top
        val factor = minOf(1.0, 4096.0 / max(width, height), sqrt(4_000_000.0 / (width * height)))
        val bitmap = Bitmap.createBitmap(ceil(width * factor).toInt().coerceAtLeast(1), ceil(height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            if (view != null) {
                @Suppress("DEPRECATION") val scale = view.scale.toDouble()
                canvas.save()
                canvas.scale((factor / scale).toFloat(), (factor / scale).toFloat())
                canvas.translate((-left * scale).toFloat(), (-top * scale).toFloat())
                view.draw(canvas); canvas.restore()
            }
            // Dibujar los puntos nativamente garantiza su totalidad, incluso fuera del viewport del renderer.
            canvas.save(); canvas.scale(factor.toFloat(), factor.toFloat()); canvas.translate(-left.toFloat(), -top.toFloat())
            for (i in 0 until paths.length()) {
                val data = paths.getJSONObject(i); val points = data.getJSONArray("points")
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(data.getString("color")); style = Paint.Style.STROKE
                    strokeWidth = max(data.getDouble("width"), 1 / factor).toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                    alpha = if (data.optString("tool") == "highlighter") 77 else 255
                }
                val path = Path()
                for (j in 0 until points.length()) {
                    val p = points.getJSONArray(j)
                    if (j == 0) path.moveTo(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) else path.lineTo(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                }
                if (points.length() == 1) { val p = points.getJSONArray(0); canvas.drawPoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), paint) }
                else canvas.drawPath(path, paint)
            }
            canvas.restore()
            withContext(Dispatchers.IO) { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            return factor
        } finally { bitmap.recycle() }
    }
}
