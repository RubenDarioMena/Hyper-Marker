package com.hypermarker.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.*
import androidx.webkit.*
import com.hypermarker.viewer.DocumentWebView

/** Navegación remota sin AssetLoader, bridge nativo ni acceso a archivos privados. */
@SuppressLint("SetJavaScriptEnabled")
class BrowserWebView(context: Context, private val changed: (BrowserWebView, Boolean) -> Unit,
    private val problem: (String) -> Unit, private val crashed: () -> Unit) : WebView(context) {
    var capturing = false
    init {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false; allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true; setGeolocationEnabled(false)
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            for (asset in listOf("security/storage-bootstrap.js", "security/offline-bootstrap.js")) {
                WebViewCompat.addDocumentStartJavaScript(this, context.assets.open(asset).bufferedReader().use { it.readText() }, setOf("*"))
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) { callback?.invoke(origin, false, false) }
            override fun onReceivedTitle(view: WebView?, title: String?) { changed(this@BrowserWebView, false) }
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean { result?.cancel(); return true }
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean { result?.cancel(); return true }
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean { result?.cancel(); return true }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (capturing) return true
                val blocked = !WebAddress.permitted(request.url.toString())
                if (blocked && request.isForMainFrame) problem("Sólo se pueden abrir páginas HTTPS. Ese enlace requiere otra aplicación.")
                return blocked
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                if (WebAddress.permitted(request.url.toString())) null else DocumentWebView.denied()
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { changed(this@BrowserWebView, true) }
            override fun onPageFinished(view: WebView?, url: String?) { changed(this@BrowserWebView, false) }
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) { changed(this@BrowserWebView, false) }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) problem("No se pudo abrir la página. Comprueba tu conexión y la dirección.")
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                if (request?.isForMainFrame == true) problem("La página respondió con HTTP ${response?.statusCode}.")
            }
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean { crashed(); return true }
        }
        setDownloadListener { _, _, _, _, _ -> problem("Para conservar esta página usa Descargar página. Para otros archivos, descárgalos e impórtalos desde la biblioteca.") }
    }
    fun open(address: String) { require(WebAddress.permitted(address)); loadUrl(address) }
}
