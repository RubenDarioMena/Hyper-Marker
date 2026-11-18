package com.hypermarker.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import com.hypermarker.R
import com.hypermarker.flow.FlowCamera
import com.hypermarker.storage.Document
import com.hypermarker.storage.FlowEdge
import com.hypermarker.storage.FlowNode
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.storage.PrivateImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Miniaturas para el Flow y el selector. Nunca ejecuta contenido importado: Markdown y Flow se
 * dibujan como texto y geometría; HTML y páginas guardadas sólo tienen la captura que el lector
 * tomó de lo que el usuario ya vio al cerrarlo. Sin captura, la UI muestra el icono del tipo.
 * Los archivos viven en `cacheDir/previews` y se regeneran cuando cambia la firma del documento.
 */
class PreviewStore(private val repository: LibraryRepository) {
    private val context = repository.context
    private val root = File(context.cacheDir, "previews")
    private val memory = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val generating = Semaphore(2)

    /** Miniatura de un documento, o `null` si su tipo no tiene una disponible todavía. */
    suspend fun document(document: Document): ImageBitmap? = withContext(Dispatchers.IO) {
        val signature = signature(document) ?: return@withContext null
        val name = fileName(document.id, signature)
        memory.get(name)?.let { return@withContext it }
        generating.withPermit {
            val file = File(root, name)
            if (!file.isFile) {
                val bitmap = when (document.type) {
                    "MARKDOWN", "REMOTE_MARKDOWN" -> renderMarkdown(document.title, runCatching { repository.readMarkdown(document.id) }.getOrDefault(""))
                    "FLOW" -> renderFlow(repository.dao.flowNodes(document.id), repository.dao.flowEdges(document.id))
                    else -> null
                } ?: return@withPermit null
                forget(document.id)
                PrivateImages.writeWebp(bitmap, file, 80)
            }
            PrivateImages.decodeFile(file, WIDTH)?.asImageBitmap()?.also { memory.put(name, it) }
        }
    }

    /** Miniatura de una imagen privada de un Flow (relación de tipo imagen o foto de punto). */
    suspend fun flowImage(flowId: String, relative: String, side: Int = WIDTH): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "img:$flowId/$relative@$side"
        memory.get(key) ?: runCatching { repository.flowFile(flowId, relative) }.getOrNull()?.takeIf { it.isFile }
            ?.let { PrivateImages.decodeFile(it, side) }?.asImageBitmap()?.also { memory.put(key, it) }
    }

    /** Archivo de la miniatura ya generada (para usarla como foto de un punto). */
    suspend fun file(document: Document): File? {
        document(document) ?: return null
        val signature = withContext(Dispatchers.IO) { signature(document) } ?: return null
        return File(root, fileName(document.id, signature)).takeIf { it.isFile }
    }

    /** Captura de lo visible en el lector (o en el visor de un diagrama) al cerrarlo. Se llama en el hilo principal con la vista aún adjunta. */
    fun captureView(document: Document, view: View) {
        if (view.width <= 0 || view.height <= 0 || document.type !in listOf("HTML", "WEB_SNAPSHOT", "MERMAID")) return
        val scale = WIDTH.toFloat() / view.width
        val height = min((view.height * scale).toInt(), (WIDTH * 1.5f).toInt()).coerceAtLeast(1)
        val bitmap = runCatching {
            Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888).also { target ->
                val canvas = Canvas(target)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.scale(scale, scale)
                canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
                view.draw(canvas)
            }
        }.getOrNull() ?: return
        Thread {
            runCatching {
                forget(document.id)
                PrivateImages.writeWebp(bitmap, File(root, fileName(document.id, "capture")), 80)
            }
        }.apply { name = "preview-capture"; start() }
    }

    /** Elimina miniaturas de un documento (se llama al regenerar o borrar). */
    fun forget(id: String) {
        val prefix = "$id-"
        root.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
        memory.snapshot().keys.filter { it.startsWith(prefix) }.forEach { memory.remove(it) }
    }

    /** Borra miniaturas de documentos que ya no existen. */
    fun prune(existing: Set<String>) {
        root.listFiles()?.filter { it.name.take(36) !in existing }?.forEach { it.delete() }
    }

    private suspend fun signature(document: Document): String? = when (document.type) {
        "MARKDOWN" -> "v${document.revision}-${document.updatedAt}"
        "REMOTE_MARKDOWN" -> "v${document.revision}-${(document.entryPath ?: "").hashCode().toUInt()}"
        "FLOW" -> "f" + flowSignature(repository.dao.flowNodes(document.id), repository.dao.flowEdges(document.id))
        "HTML", "WEB_SNAPSHOT", "MERMAID" -> "capture".takeIf { File(root, fileName(document.id, "capture")).isFile }
        else -> null
    }

    private fun renderMarkdown(title: String, source: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val heading = runCatching { ResourcesCompat.getFont(context, R.font.space_grotesk_variable) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val body = runCatching { ResourcesCompat.getFont(context, R.font.inter_variable) }.getOrNull() ?: Typeface.DEFAULT
        val pad = 32f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14201C.toInt(); textSize = 30f; typeface = Typeface.create(heading, Typeface.BOLD) }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF56615C.toInt(); textSize = 20f; typeface = body }
        val lines = MarkdownPreviewText.lines(source, 14)
        val firstHeading = lines.firstOrNull()?.takeIf { it.heading }?.text
        val shownTitle = firstHeading ?: title
        var y = pad
        val titleLayout = StaticLayout.Builder.obtain(shownTitle, 0, shownTitle.length, titlePaint, WIDTH - 2 * pad.toInt()).setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.save(); canvas.translate(pad, y); titleLayout.draw(canvas); canvas.restore()
        y += titleLayout.height + 14f
        for (line in lines.drop(if (firstHeading != null) 1 else 0)) {
            if (y > HEIGHT - pad) break
            val paint = if (line.heading) TextPaint(titlePaint).apply { textSize = 22f } else textPaint
            val text = (if (line.bullet) "•  " else "") + line.text
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH - 2 * pad.toInt()).setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.translate(pad, y); layout.draw(canvas); canvas.restore()
            y += layout.height + 8f
        }
        if (lines.isEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECE9DE.toInt() }
            repeat(4) { i -> canvas.drawRoundRect(pad, y + i * 30f, WIDTH - pad - i * 50f, y + i * 30f + 12f, 6f, 6f, paint) }
        }
        return bitmap
    }

    private fun renderFlow(nodes: List<FlowNode>, edges: List<FlowEdge>): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF14201C.toInt())
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF33403B.toInt() }
        var gx = 14f
        while (gx < WIDTH) { var gy = 14f; while (gy < HEIGHT) { canvas.drawCircle(gx, gy, 1.6f, grid); gy += 28f }; gx += 28f }
        if (nodes.isEmpty()) return bitmap
        val camera = FlowCamera.fit(nodes.map { it.x to it.y }, WIDTH.toFloat(), HEIGHT.toFloat(), 1f, 56f)
        val byId = nodes.associateBy { it.id }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF89D5BC.toInt(); strokeWidth = 4f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        for (edge in edges) {
            val a = byId[edge.fromId] ?: continue; val b = byId[edge.toId] ?: continue
            val ax = camera.toScreenX(a.x, 1f); val ay = camera.toScreenY(a.y, 1f); val bx = camera.toScreenX(b.x, 1f); val by = camera.toScreenY(b.y, 1f)
            val path = android.graphics.Path().apply { moveTo(ax, ay); cubicTo(ax, (ay + by) / 2, bx, (ay + by) / 2, bx, by) }
            canvas.drawPath(path, line)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF89D5BC.toInt() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5B62F.toInt() }
        val radius = (14f * camera.scale).coerceIn(7f, 18f)
        for (node in nodes) {
            val x = camera.toScreenX(node.x, 1f); val y = camera.toScreenY(node.y, 1f)
            if (node.avatarPath != null) canvas.drawCircle(x, y, radius + 4f, ring)
            canvas.drawCircle(x, y, radius, dot)
        }
        return bitmap
    }

    companion object {
        const val WIDTH = 480
        const val HEIGHT = 360
        fun fileName(id: String, signature: String) = "$id-$signature.webp"

        /** Firma estable del grafo: cambia si cambian puntos, posiciones, fotos o conexiones. */
        fun flowSignature(nodes: List<FlowNode>, edges: List<FlowEdge>): String {
            var hash = 17L
            nodes.sortedBy { it.id }.forEach { hash = hash * 31 + (it.id + it.x + "," + it.y + it.avatarPath).hashCode() }
            edges.sortedBy { it.id }.forEach { hash = hash * 31 + (it.fromId + ">" + it.toId).hashCode() }
            return java.lang.Long.toHexString(hash) + "-" + nodes.size
        }
    }
}

/** Texto plano de las primeras líneas de un Markdown para la miniatura. Puro, sin WebView. */
object MarkdownPreviewText {
    data class Line(val text: String, val heading: Boolean = false, val bullet: Boolean = false)

    fun lines(source: String, max: Int): List<Line> {
        val result = mutableListOf<Line>()
        var fence = false
        for (raw in source.lineSequence()) {
            if (result.size >= max) break
            val line = raw.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) { fence = !fence; continue }
            if (fence || line.isEmpty() || line.matches(Regex("[-*_=]{3,}")) || line.startsWith("<!--") || line.startsWith("|---")) continue
            val heading = line.startsWith("#")
            val bullet = !heading && line.matches(Regex("([-*+]|\\d+[.)])\\s+.*"))
            val text = inline(line.replace(Regex("^#{1,6}\\s*"), "").replace(Regex("^([-*+]|\\d+[.)])\\s+(\\[[ xX]]\\s+)?"), "").removePrefix("> "))
            if (text.isNotBlank()) result += Line(text, heading, bullet)
        }
        return result
    }

    private fun inline(value: String) = value
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("(\\*\\*|__|\\*|_|~~|`)"), "")
        .trim()
}
