package com.hypermarker

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermarker.browser.BrowserWebView
import com.hypermarker.storage.*
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.runBlocking
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserIntegrationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val repo get() = (context.applicationContext as HyperMarkerApplication).repository
    private fun evaluate(web: android.webkit.WebView, js: String): String {
        val latch = CountDownLatch(1); var result = ""
        instrument.runOnMainSync { web.evaluateJavascript(js) { result = it; latch.countDown() } }
        assertTrue(latch.await(8, TimeUnit.SECONDS)); return result
    }
    @Test fun realWebArchiveReopensOfflineWithControllerAndResources() {
        runBlocking { repo.recover() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val archive = File(context.cacheDir, "browser-integration.mhtml")
        var browser: BrowserWebView? = null
        var reader: DocumentWebView? = null
        var doc: Document? = null
        try {
            val loaded = CountDownLatch(1)
            scenario.onActivity { activity ->
                browser = BrowserWebView(activity, changed = { view, _ -> if (view.progress == 100) loaded.countDown() }, problem = { fail(it) }, crashed = { fail("Renderer") })
                activity.setContentView(browser)
                val html = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"></head><body>
                    <h1 id="study">Física sin conexión</h1><p id="note">Conservar este contenido</p>
                    <img id="picture" src="picture.svg">
                    <script>document.getElementById('note').textContent='Contenido dinámico ya cargado';</script>
                    </body></html>
                """.trimIndent()
                // Transporte sintético sólo de este fixture; la captura sigue siendo el MHTML real de Chromium.
                browser!!.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                        val (mime, content) = when (request.url.path) {
                            "/lesson.html" -> "text/html" to html
                            "/style.css" -> "text/css" to "#study{color:rgb(12,34,56)}"
                            "/picture.svg" -> "image/svg+xml" to "<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'><rect width='20' height='20' fill='red'/></svg>"
                            else -> return DocumentWebView.denied()
                        }
                        return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(content.toByteArray()))
                    }
                    override fun onPageFinished(view: WebView?, url: String?) { loaded.countDown() }
                }
                browser!!.open("https://study.example.test/lesson.html")
            }
            assertTrue("Página inicial", loaded.await(15, TimeUnit.SECONDS))
            assertEquals("undefined", JSONTokener(evaluate(browser!!, "typeof window.HyperMarkerBridge")).nextValue())
            val captured = CountDownLatch(1)
            instrument.runOnMainSync { browser!!.saveWebArchive(archive.absolutePath, false) { assertNotNull(it); captured.countDown() } }
            assertTrue("Captura MHTML", captured.await(20, TimeUnit.SECONDS))
            doc = runBlocking { repo.saveWebSnapshot(archive, "https://study.example.test/lesson.html", "Lección", null) }
            assertFalse(doc!!.networkAllowed)
            assertTrue(File(repo.directory(doc!!.id), "capture/page.mhtml").isFile)
            val ready = CountDownLatch(1)
            scenario.onActivity { activity ->
                browser!!.destroy(); browser = null
                reader = DocumentWebView(activity, doc!!, repo, event = { if (it.optString("type") == "controller.ready") ready.countDown() }, console = {}, finished = {}, crashed = { fail("Renderer") })
                activity.setContentView(reader); reader!!.open()
            }
            assertTrue("Controlador en copia offline", ready.await(15, TimeUnit.SECONDS))
            assertEquals("Física sin conexión", JSONTokener(evaluate(reader!!, "document.getElementById('study').textContent")).nextValue())
            assertEquals("Contenido dinámico ya cargado", JSONTokener(evaluate(reader!!, "document.getElementById('note').textContent")).nextValue())
            assertEquals("rgb(12, 34, 56)", JSONTokener(evaluate(reader!!, "getComputedStyle(document.getElementById('study')).color")).nextValue())
            assertEquals("true", evaluate(reader!!, "document.getElementById('picture').naturalWidth>0"))
            assertTrue(runBlocking { repo.webSource(doc!!.id) } == "https://study.example.test/lesson.html")
            // Una copia sigue siendo offline aunque alguien intente cambiar su preferencia por repositorio.
            try { runBlocking { repo.setNetwork(doc!!.id, true) }; fail("Snapshot con red") } catch (_: IllegalArgumentException) {}
        } finally {
            scenario.onActivity { reader?.destroy(); browser?.destroy() }; scenario.close()
            doc?.let { runBlocking { repo.delete(it.id, false) } }; archive.delete()
        }
    }
    @Test fun browserRejectsPrivilegedOriginsAndHasNoNativeBridge() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val browser = BrowserWebView(activity, changed = { _, _ -> }, problem = {}, crashed = {})
                try {
                    assertFalse(browser.settings.allowFileAccess)
                    assertFalse(browser.settings.allowContentAccess)
                    for (url in listOf("file:///data/data/com.hypermarker/secret.html", "content://provider/private", "http://example.com", "https://d000.hypermarker.invalid/index.html", "https://user:pass@example.com/")) {
                        val request = object : WebResourceRequest {
                            override fun getUrl() = Uri.parse(url)
                            override fun isForMainFrame() = true
                            override fun isRedirect() = false
                            override fun hasGesture() = true
                            override fun getMethod() = "GET"
                            override fun getRequestHeaders() = emptyMap<String, String>()
                        }
                        assertTrue(url, browser.webViewClient.shouldOverrideUrlLoading(browser, request))
                        assertEquals(403, browser.webViewClient.shouldInterceptRequest(browser, request)!!.statusCode)
                    }
                } finally { browser.destroy() }
            }
            try { repo.importer.displayName(Uri.parse("file://${context.filesDir}/documents/private.html")); fail("Ruta privada aceptada") }
            catch (_: IllegalArgumentException) {}
        } finally { scenario.close() }
    }
    @Test fun offlineSnapshotBlocksScriptsAndUncapturedNetworkEvenWithInconsistentPreference() {
        runBlocking { repo.recover() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val archive = File(context.cacheDir, "hostile-web-snapshot.mhtml")
        var doc: Document? = null
        var reader: DocumentWebView? = null
        try {
            java.net.ServerSocket(0).use { server ->
                server.soTimeout = 1500
                val html = "<h1 id='safe'>Guardada</h1><img src='https://127.0.0.1:${server.localPort}/image'><script>window.snapshotScriptRan=true;fetch('https://127.0.0.1:${server.localPort}/fetch')</script>"
                archive.writeText("MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=probe\r\n\r\n--probe\r\nContent-Type: text/html; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\nContent-Location: https://example.test/\r\n\r\n" + java.util.Base64.getEncoder().encodeToString(html.toByteArray()) + "\r\n--probe--\r\n")
                doc = runBlocking { repo.saveWebSnapshot(archive, "https://example.test/", "Copia hostil", null) }
                val ready = CountDownLatch(1)
                scenario.onActivity { activity ->
                    reader = DocumentWebView(activity, doc!!.copy(networkAllowed = true), repo,
                        event = { if (it.optString("type") == "controller.ready") ready.countDown() }, console = {}, finished = {}, crashed = { fail("Renderer") })
                    activity.setContentView(reader); reader!!.open()
                }
                assertTrue(ready.await(15, TimeUnit.SECONDS))
                assertEquals("undefined", JSONTokener(evaluate(reader!!, "typeof window.snapshotScriptRan")).nextValue())
                try { server.accept().close(); fail("La copia offline abrió red") } catch (_: java.net.SocketTimeoutException) {}
            }
        } finally {
            scenario.onActivity { reader?.destroy() }; scenario.close()
            doc?.let { runBlocking { repo.delete(it.id, false) } }; archive.delete()
        }
    }
    @Test fun androidOpenWithHtmlImportsPrivateCopyAndKeepsOfflineChoice() {
        runBlocking { repo.recover() }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "abrir-con-hypermarker.htm")
            put(MediaStore.Downloads.MIME_TYPE, "text/html")
        }))
        context.contentResolver.openOutputStream(uri)!!.use { it.write("<h1>Abrir con HTML</h1>".toByteArray()) }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("DEPRECATION")
        val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue("Hyper-Marker no aparece en Abrir con", matches.any { it.activityInfo.packageName == context.packageName })
        val old = runBlocking { repo.dao.documents().map { it.id }.toSet() }
        val scenario = ActivityScenario.launch<MainActivity>(intent.setPackage(context.packageName))
        var imported: Document? = null
        try {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (imported == null && SystemClock.uptimeMillis() < deadline) {
                imported = runBlocking { repo.dao.documents().firstOrNull { it.id !in old } }
                if (imported == null) SystemClock.sleep(100)
            }
            assertNotNull("No se importó ACTION_VIEW", imported)
            assertTrue(imported!!.networkAllowed)
            context.contentResolver.delete(uri, null, null)
            assertTrue(repo.original(imported!!.id).resolve(imported!!.entryPath!!).readText().contains("Abrir con HTML"))
            runBlocking { repo.setNetwork(imported!!.id, false) }
            assertFalse(runBlocking { repo.dao.document(imported!!.id)!!.networkAllowed })
            val xhtml = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example/item/xhtml"), "application/xhtml+xml")
            @Suppress("DEPRECATION")
            assertTrue(context.packageManager.queryIntentActivities(xhtml, PackageManager.MATCH_DEFAULT_ONLY).any { it.activityInfo.packageName == context.packageName })
        } finally {
            scenario.close(); context.contentResolver.delete(uri, null, null)
            imported?.let { runBlocking { repo.delete(it.id, false) } }
        }
    }
}
