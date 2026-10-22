package com.hypermarker.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.*
import androidx.webkit.*
import com.hypermarker.bridge.BridgeValidator
import com.hypermarker.security.PathGuard
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
class DocumentWebView(context: Context, val document: Document, private val repository: LibraryRepository,
    private val event: (JSONObject) -> Unit, private val console: (String) -> Unit,
    private val finished: (DocumentWebView) -> Unit, private val crashed: () -> Unit) : WebView(context) {
    val origin = "https://d${document.id}.hypermarker.invalid"
    var sessionId = UUID.randomUUID().toString()
    private var validator = BridgeValidator(sessionId, document.id, document.revision)
    var mode: String = "VIEW"
        private set
    var touched: () -> Unit = {}
    /** Toque simple sin bridge disponible; con bridge, el controlador distingue contenido interactivo. */
    var tapped: () -> Unit = {}
    /** Última posición táctil en px de la vista; la cruceta se dibuja allí. */
    var lastTouchX = 0f
        private set
    var lastTouchY = 0f
        private set
    private var pendingScale: ((Float) -> Unit)? = null
    private val settleScale = Runnable { pendingScale?.let { action -> pendingScale = null; @Suppress("DEPRECATION") action(scale) } }
    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { if (!bridgeAvailable) tapped(); return false }
    })
    val earlyScriptAvailable = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    val bridgeAvailable = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && earlyScriptAvailable
    private val controller by lazy { context.assets.open("annotation/controller.js").bufferedReader().use { it.readText() } }
    private val localAssets = WebViewAssetLoader.Builder().setDomain(Uri.parse(origin).host!!)
        .addPathHandler("/", WebViewAssetLoader.PathHandler { path -> localResponse(path) }).build()

    init {
        settings.apply {
            javaScriptEnabled = earlyScriptAvailable
            blockNetworkLoads = !document.networkAllowed
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val storage = context.assets.open("security/storage-bootstrap.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(this, storage, setOf("*"))
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val bootstrap = context.assets.open("security/offline-bootstrap.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(this, bootstrap, setOf("*"))
        }
        CookieManager.getInstance().setAcceptCookie(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            val workers = ServiceWorkerControllerCompat.getInstance()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS)) workers.serviceWorkerWebSettings.blockNetworkLoads = true
            workers.setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = denied()
            })
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.addWebMessageListener(this, "HyperMarkerBridge", setOf("*")) { _, message, source, mainFrame, _ ->
            if (mainFrame && Uri.parse(url ?: "").host == Uri.parse(origin).host) {
                val accepted = validator.validate(message.data, mode)
                if (accepted != null) {
                    val current = Uri.parse(url ?: "")
                    val fragment = current.encodedFragment.orEmpty()
                    val page = current.encodedPath.orEmpty() + (current.encodedQuery?.let { "?$it" } ?: "") + if (fragment.startsWith('/') || fragment.startsWith('!')) "#$fragment" else ""
                    if (accepted.getJSONObject("payload").optString("pageKey") == page) event(accepted)
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                console("${message.messageLevel()}: ${message.message().take(2000)} · ${message.sourceId().substringAfterLast('/').take(100)}:${message.lineNumber()}")
                return true
            }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) { callback?.invoke(origin, false, false) }
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean { console("alert: ${message?.take(1000)}"); result?.confirm(); return true }
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean { result?.cancel(); return true }
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean { result?.cancel(); return true }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == Uri.parse(origin).host) return localAssets.shouldInterceptRequest(uri) ?: denied()
                if (uri.host?.endsWith(".hypermarker.invalid") == true) return denied()
                return if (document.networkAllowed && uri.scheme == "https") null else denied()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val uri = request.url
                val permitted = uri.scheme == "https" && uri.host == Uri.parse(origin).host
                if (!permitted && request.isForMainFrame) console("Navegación externa bloqueada. Mantén el proyecto abierto; los recursos HTTPS requieren permiso por documento.")
                return !permitted
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { mode = "VIEW"; sessionId = UUID.randomUUID().toString(); validator = BridgeValidator(sessionId, document.id, document.revision) }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (Uri.parse(url).host != Uri.parse(origin).host) return
                if (bridgeAvailable) {
                    val config = JSONObject().put("sessionId", sessionId).put("documentId", document.id).put("revision", document.revision).put("longPressMs", maxOf(500, android.view.ViewConfiguration.getLongPressTimeout()))
                    evaluateJavascript("$controller\n;window.HyperMarker?.init($config);", null)
                }
                finished(this@DocumentWebView)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) { console("Recurso no disponible: ${request?.url?.lastPathSegment} · ${error?.description}") }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) { console("HTTP ${response?.statusCode}: ${request?.url?.lastPathSegment}") }
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean { crashed(); return true }
            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                if (pendingScale != null) { removeCallbacks(settleScale); postDelayed(settleScale, 120) }
            }
        }
        setDownloadListener { _, _, _, _, _ -> console("Las descargas del documento están deshabilitadas. Importa archivos desde la biblioteca.") }
        // Con bridge, la pulsación prolongada abre la cruceta en todos los modos en lugar de la selección nativa.
        setOnLongClickListener { bridgeAvailable || mode != "VIEW" }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastTouchX = event.x; lastTouchY = event.y
        taps.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) touched()
        if (event.actionMasked == MotionEvent.ACTION_UP && mode == "VIEW") performClick()
        return super.onTouchEvent(event)
    }
    /** Encaja el rectángulo (CSS px de documento) completo en la vista y lo centra. */
    fun zoomToFit(x: Double, y: Double, w: Double, h: Double) {
        if (w <= 0 || h <= 0 || width == 0 || height == 0) return
        val target = (minOf(width / w, height / h) * 0.98).toFloat()
        scaleThen(target) { actual ->
            scrollTo((x * actual - (width - w * actual) / 2).roundToInt().coerceAtLeast(0), (y * actual - (height - h * actual) / 2).roundToInt().coerceAtLeast(0))
        }
    }
    /** Vuelve a la escala mínima permitida por la página, que equivale a la vista original sin zoom. */
    fun resetZoom() { scaleThen(0f) { scrollTo(0, scrollY) } }
    private fun scaleThen(target: Float, then: (Float) -> Unit) {
        @Suppress("DEPRECATION") val current = scale
        // WebView rechaza 0.01 exacto; 0.02 sigue llegando a la escala mínima desde cualquier zoom práctico.
        val factor = if (target <= 0f) 0.02f else (target / current).coerceIn(0.02f, 99f)
        removeCallbacks(settleScale)
        pendingScale = then
        if (target > 0f && kotlin.math.abs(factor - 1f) < 0.01f) { post(settleScale); return }
        zoomBy(factor)
        postDelayed(settleScale, 450)
    }
    /** Luminancia media (0..1) del contenido bajo cada región, o null si no se pudo muestrear. */
    fun luminance(regions: List<RectF>): List<Float?> = runCatching {
        val factor = 0.08f
        val bitmap = Bitmap.createBitmap((width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.scale(factor, factor); canvas.translate(-scrollX.toFloat(), -scrollY.toFloat()); draw(canvas)
            regions.map { region ->
                var total = 0f; var samples = 0
                val left = (region.left * factor).toInt().coerceIn(0, bitmap.width - 1); val right = (region.right * factor).toInt().coerceIn(left + 1, bitmap.width)
                val top = (region.top * factor).toInt().coerceIn(0, bitmap.height - 1); val bottom = (region.bottom * factor).toInt().coerceIn(top + 1, bitmap.height)
                for (px in left until right) for (py in top until bottom) {
                    val color = bitmap.getPixel(px, py)
                    if (Color.alpha(color) < 128) continue
                    total += (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f; samples++
                }
                if (samples == 0) null else total / samples
            }
        } finally { bitmap.recycle() }
    }.getOrElse { regions.map { null } }
    fun changeMode(value: String) { mode = value; command("mode", JSONObject().put("mode", value)) }
    fun command(type: String, payload: Any = JSONObject()) {
        evaluateJavascript("window.HyperMarker?.command(${JSONObject.quote(type)},${payload});", null)
    }
    fun open(path: String = requireNotNull(document.entryPath)) {
        val normalized = PathGuard.normalize(path)
        val encoded = normalized.split('/').joinToString("/") { Uri.encode(it) }
        loadUrl("$origin/$encoded")
    }
    fun openRelativePage(page: String) {
        val uri = Uri.parse(page)
        require(uri.scheme == null && uri.authority == null && page.startsWith("/") && !page.startsWith("//")) { "Página no permitida" }
        val path = PathGuard.normalize(uri.path.orEmpty().trimStart('/'))
        require(PathGuard.resolve(repository.original(document.id), path).isFile) { "La página guardada no existe" }
        val encoded = path.split('/').joinToString("/") { Uri.encode(it) }
        loadUrl("$origin/$encoded" + (uri.encodedQuery?.let { "?$it" } ?: "") + (uri.encodedFragment?.let { "#$it" } ?: ""))
    }
    private fun localResponse(path: String): WebResourceResponse = runCatching {
        val file = PathGuard.resolve(repository.original(document.id), Uri.decode(path))
        require(file.isFile)
        val mime = when (file.extension.lowercase()) {
            "html", "htm" -> "text/html"; "js", "mjs" -> "application/javascript"; "css" -> "text/css"
            "svg" -> "image/svg+xml"; "json", "map" -> "application/json"; "wasm" -> "application/wasm"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        }
        val resources = if (document.networkAllowed) "$origin https: data: blob:" else "$origin data: blob:"
        val connect = if (document.networkAllowed) "https: wss:" else origin
        val csp = "sandbox allow-scripts; default-src $resources; script-src $resources 'unsafe-inline' 'unsafe-eval'; style-src $resources 'unsafe-inline'; connect-src $connect; worker-src 'none'; frame-src $origin; object-src 'none'; base-uri $origin; form-action 'none'"
        WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*", "Content-Security-Policy" to csp, "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store"), file.inputStream())
    }.getOrElse { denied() }
    companion object {
        fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream("Recurso bloqueado o inexistente".toByteArray()))
    }
}
