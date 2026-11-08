package com.hypermarker.export

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.hypermarker.storage.Annotation
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import kotlin.coroutines.resume

/** Renderer privado y offline: no cambia scroll/zoom/página del lector ni publica anotaciones. */
class FeedbackCapture(private val source: DocumentWebView, private val document: Document,
    private val repository: LibraryRepository, private val root: File, private val annotations: List<Annotation>) {
    suspend fun capture(drawing: FeedbackDrawing, output: File): String = withContext(Dispatchers.Main) {
        val parent = source.parent as? ViewGroup ?: error("El lector ya no está disponible")
        val ready = CompletableDeferred<Unit>()
        val view = DocumentWebView(source.context, document.copy(networkAllowed = false), repository,
            event = { if (it.optString("type") == "controller.ready") ready.complete(Unit) }, console = {},
            finished = { if (!it.bridgeAvailable) ready.completeExceptionally(IllegalStateException("Actualiza Android System WebView para exportar capturas")) },
            crashed = { ready.completeExceptionally(IllegalStateException("El visor de captura se cerró")) }, exportRoot = root)
        view.alpha = 0f
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val w = source.width.coerceAtLeast(1); val h = source.height.coerceAtLeast(1)
        parent.addView(view, ViewGroup.LayoutParams(w, h))
        view.layout(0, 0, w, h)
        try {
            val geometry = try {
                withTimeout(20_000) {
                    view.openRelativePage(drawing.page)
                    ready.await()
                    delay(350) // Permitir la composición inicial de recursos locales y scripts compilados.
                }
                val list = JSONArray().apply { annotations.forEach { put(JSONObject().put("id", it.id).put("kind", it.kind)
                    .put("pageKey", it.pageKey).put("payload", JSONObject(it.payload))) } }
                val ids = JSONArray(drawing.strokes.map { it.id })
                evaluate(view, "window.HyperMarker.command('annotations',$list);JSON.stringify(window.HyperMarker.exportDrawing($ids))")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                // El PNG sigue conservando el dibujo cuando la página dejó de existir o no puede renderizarse.
                JSONObject().put("context", false).put("paths", JSONArray().apply {
                    drawing.strokes.forEach { stroke ->
                        val p = JSONObject(stroke.payload)
                        put(JSONObject().put("points", p.getJSONArray("points")).put("color", p.getString("color"))
                            .put("width", p.getDouble("width")).put("tool", p.getString("tool")))
                    }
                })
            }
            val context = geometry.getBoolean("context")
            val paths = geometry.getJSONArray("paths")
            require(paths.length() == drawing.strokes.size) { "No se encontraron todos los trazos del dibujo" }
            if (context) {
                val painted = CompletableDeferred<Unit>()
                view.postVisualStateCallback(1, object : WebView.VisualStateCallback() { override fun onComplete(requestId: Long) { painted.complete(Unit) } })
                withTimeout(10_000) { painted.await() }
            }
            val factor = FeedbackImage.write(paths, if (context) view else null, output)
            (if (context) "Dibujo completo con contexto local del elemento." else "Dibujo completo sobre fondo blanco: el ancla o la página no se pudo resolver; no se atribuye a un elemento distinto.") +
                if (factor < 1) " Imagen reducida proporcionalmente para incluir todos los trazos; no se recorta." else " Sin recorte."
        } finally { parent.removeView(view); view.stopLoading(); view.destroy() }
    }
    private suspend fun evaluate(view: WebView, script: String): JSONObject = withTimeout(10_000) {
        suspendCancellableCoroutine { continuation -> view.evaluateJavascript(script) { raw ->
            if (continuation.isActive) runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }
                .fold({ continuation.resume(it) }, { continuation.resumeWith(Result.failure(it)) })
        } }
    }
}
