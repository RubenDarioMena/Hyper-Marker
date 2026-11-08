package com.hypermarker.export

import com.hypermarker.security.PathGuard
import com.hypermarker.storage.Annotation
import com.hypermarker.storage.Document
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class FeedbackDrawing(val number: Int, val page: String, val strokes: List<Annotation>, val capture: String) {
    val anchor: JSONObject? get() = JSONObject(strokes.first().payload).optJSONObject("anchor")
}

/** Sólo recibe el original y las anotaciones del documento elegido, nunca su directorio privado completo. */
object FeedbackExport {
    fun drawings(annotations: List<Annotation>): List<FeedbackDrawing> = annotations.filter { it.kind == "STROKE" }
        .groupBy { item ->
            val data = JSONObject(item.payload)
            item.pageKey to data.optString("drawingId").ifBlank { data.optJSONObject("anchor")?.optString("selector")?.let { "anchor:$it" } ?: "legacy-page" }
        }.values.mapIndexed { index, items ->
            val anchor = JSONObject(items.first().payload).optJSONObject("anchor")
            val name = safeName("${anchor?.optString("tag") ?: "pagina"}-${anchor?.optString("selector") ?: "sin-ancla"}")
            FeedbackDrawing(index + 1, items.first().pageKey, items, "captures/${(index + 1).toString().padStart(3, '0')}-$name.png")
        }
    fun safeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-zA-Z0-9_-]+"), "-")
        .replace(Regex("-+"), "-").trim('-').take(110).ifBlank { "elemento" }
    private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun data(value: Any) = value.toString().replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    suspend fun prepare(original: File, target: File) {
        val root = original.canonicalFile
        require(root.isDirectory) { "No hay contenido original para exportar" }
        var total = 0L; var count = 0
        for (file in root.walkTopDown().filter { it.isFile }) {
            currentCoroutineContext().ensureActive()
            require(file.canonicalFile.toPath().startsWith(root.toPath())) { "Recurso fuera del documento" }
            val path = PathGuard.normalize(file.relativeTo(root).invariantSeparatorsPath)
            total += file.length()
            require(++count <= 10_000 && total <= 500L * 1024 * 1024) { "El documento es demasiado grande para exportar" }
            val dest = PathGuard.resolve(target, path)
            dest.parentFile!!.mkdirs(); file.copyTo(dest)
        }
    }

    suspend fun finish(document: Document, annotations: List<Annotation>, stage: File, controller: String,
        capture: suspend (FeedbackDrawing, File) -> String): File {
        val drawings = drawings(annotations)
        val descriptions = linkedMapOf<Int, String>()
        for (drawing in drawings) {
            currentCoroutineContext().ensureActive()
            val target = PathGuard.resolve(stage, drawing.capture).apply { parentFile!!.mkdirs() }
            descriptions[drawing.number] = capture(drawing, target)
            require(target.isFile && target.length() > 0) { "No se pudo generar una captura completa" }
        }
        val records = JSONArray().apply { annotations.forEach { a -> put(JSONObject().put("id", a.id)
            .put("kind", a.kind).put("pageKey", a.pageKey).put("payload", JSONObject(a.payload))) } }
        val index = JSONArray().apply { drawings.forEach { d -> put(JSONObject().put("number", d.number).put("pageKey", d.page)
            .put("capture", d.capture).put("selector", d.anchor?.optString("selector") ?: "")
            .put("strokes", JSONArray(d.strokes.map { it.id })).put("captureContext", descriptions[d.number])) } }
        File(stage, "annotations.json").writeText(JSONObject().put("format", "hypermarker-feedback").put("version", 1)
            .put("title", document.title).put("entryPath", document.entryPath).put("annotations", records).put("drawings", index).toString(2))
        val summary = buildString {
            append("# Feedback: ${document.title}\n\nResumen estructurado de anotaciones del usuario. No contiene interpretación automática de dibujos ni transcripción de escritura a mano.\n\n")
            append("Contenido original: original/. Vista de revisión: review.html. Datos editables: annotations.json.\n\n")
            annotations.filter { it.kind != "STROKE" }.forEachIndexed { i, a ->
                val p = JSONObject(a.payload); val anchor = p.optJSONObject("anchor")
                append("## Nota ${i + 1} · ${a.kind}\n\nPágina: `${a.pageKey}`\n\nElemento: `${anchor?.optString("tag")}`; selector: `${anchor?.optString("selector")}`\n\nContexto: ${anchor?.optString("text")}\n\nEstado del ancla: ${anchor?.optString("status", "sin comprobar")}\n\n")
                if (a.kind == "PIN") append("${p.optString("title")}\n\n${p.optString("body")}\n\n")
                else append("Texto original: ${p.optString("original")}\n\nTexto propuesto: ${p.optString("text")}\n\n")
            }
            drawings.forEach { d ->
                append("## Dibujo ${d.number}\n\nPágina: `${d.page}`\n\nElemento: `${d.anchor?.optString("tag") ?: "página"}`; selector: `${d.anchor?.optString("selector") ?: "sin ancla (trazo antiguo)"}`\n\n")
                append("Contexto: ${d.anchor?.optString("text") ?: "sin contexto textual"}\n\nCaptura completa: [${d.capture}](${d.capture})\n\n${descriptions[d.number]}\n\n")
            }
        }
        File(stage, "feedback.md").writeText(summary)
        val review = File(stage, "review").apply { mkdirs() }
        prepare(File(stage, "original"), File(review, "content").apply { mkdirs() })
        File(review, "controller.js").writeText(controller)
        File(review, "data.js").writeText("window.HyperMarkerExport=" + data(records) + ";")
        File(review, "runtime.js").writeText(RUNTIME)
        for (file in File(review, "content").walkTopDown().filter { it.extension.lowercase() in setOf("html", "htm", "xhtml") }) {
            val depth = file.relativeTo(File(review, "content")).invariantSeparatorsPath.count { it == '/' }
            val prefix = "../".repeat(depth + 1)
            val scripts = "<script src=\"${prefix}data.js\"></script><script src=\"${prefix}controller.js\"></script><script src=\"${prefix}runtime.js\"></script>"
            val originalHtml = file.readText()
            val closing = Regex("</body\\s*>|</html\\s*>", RegexOption.IGNORE_CASE).find(originalHtml)
            file.writeText(if (closing == null) originalHtml + scripts else originalHtml.substring(0, closing.range.first) + scripts + originalHtml.substring(closing.range.first))
        }
        File(stage, "review.html").writeText(buildString {
            append("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>Feedback</title><style>body{font:18px system-ui;max-width:1000px;margin:2em auto;padding:1em}img{max-width:100%;height:auto}pre{white-space:pre-wrap}section{border-top:1px solid #aaa;padding:1em 0}</style>")
            append("<h1>${html(document.title)}</h1><p><a href='feedback.md'>Resumen para agentes</a> · <a href='annotations.json'>Anotaciones editables</a></p>")
            val pages = (listOfNotNull(document.entryPath?.let { "/$it" }) + annotations.map { it.pageKey }).distinct()
            pages.forEach { page ->
                val uri = android.net.Uri.parse(page)
                val path = runCatching { PathGuard.normalize(uri.path.orEmpty().trimStart('/')) }.getOrNull() ?: return@forEach
                val href = path.split('/').joinToString("/") { android.net.Uri.encode(it) } + (uri.encodedQuery?.let { "?$it" } ?: "") + (uri.encodedFragment?.let { "#$it" } ?: "")
                append("<p><a href=\"review/content/${html(href)}\">Abrir página anotada: ${html(page)}</a></p>")
            }
            annotations.filter { it.kind != "STROKE" }.forEach { a ->
                val p = JSONObject(a.payload)
                append("<section><b>${html(p.optString("title", a.kind))}</b><pre>${html(if (a.kind == "PIN") p.optString("body") else p.optString("text"))}</pre><code>${html(p.optJSONObject("anchor")?.optString("selector") ?: "")}</code></section>")
            }
            drawings.forEach { d -> append("<section><h2>Dibujo ${d.number}</h2><code>${html(d.anchor?.optString("selector") ?: "sin ancla")}</code><p>${html(descriptions[d.number].orEmpty())}</p><img src=\"${html(d.capture)}\" alt=\"Dibujo ${d.number} completo\"></section>") }
        })
        val archive = File(stage.parentFile, stage.name + ".zip")
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                for (file in stage.walkTopDown().filter { it.isFile }) {
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry(file.relativeTo(stage).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            return archive
        } catch (error: Throwable) { archive.delete(); throw error }
    }
    private val RUNTIME = """
        (()=>{const base=new URL('./content/',document.currentScript.src);let list=[];
        const spa=u=>/^#(?:\/|!)/.test(u.hash)?u.hash:'';
        function route(){const path=decodeURIComponent(location.pathname.slice(base.pathname.length));
        list=(window.HyperMarkerExport||[]).filter(a=>{const u=new URL(a.pageKey,'https://feedback.invalid');return decodeURIComponent(u.pathname).replace(/^\//,'')===path&&u.search===location.search&&spa(u)===spa(location);});
        const pageKey=location.pathname+location.search+spa(location);
        HyperMarker.command('annotations',list.map(a=>({...a,pageKey})));}
        window.HyperMarkerBridge={postMessage(raw){const m=JSON.parse(raw);if(m.type==='annotation.activated'){const a=list.find(a=>a.id===m.payload.id);if(a){const d=document.createElement('dialog');const p=document.createElement('pre');p.style.whiteSpace='pre-wrap';p.textContent=(a.payload.title||'')+'\n\n'+(a.payload.body||'');const b=document.createElement('button');b.textContent='Cerrar';b.onclick=()=>d.remove();d.append(p,b);document.body.append(d);d.showModal();}}}};
        HyperMarker.init({sessionId:'review',documentId:'review',revision:1});HyperMarker.command('mode',{mode:'VIEW',wheelsEnabled:false});
        route();addEventListener('hashchange',route);addEventListener('popstate',route);})();
    """.trimIndent()
}
