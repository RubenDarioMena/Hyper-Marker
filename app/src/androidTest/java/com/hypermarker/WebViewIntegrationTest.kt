package com.hypermarker

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermarker.storage.*
import com.hypermarker.storage.Annotation
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewIntegrationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var repository: LibraryRepository
    private lateinit var document: Document
    private lateinit var web: DocumentWebView
    private val extraDocuments = mutableListOf<String>()
    private val events = CopyOnWriteArrayList<JSONObject>()
    private val logs = CopyOnWriteArrayList<String>()
    @Before fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        repository = (instrument.targetContext.applicationContext as HyperMarkerApplication).repository
        runBlocking { repository.recover() }
        val id = UUID.randomUUID().toString()
        val staging = File(instrument.targetContext.filesDir, "imports/$id").apply { mkdirs() }
        val root = File(staging, "original").apply { mkdirs() }
        File(root, "index.html").writeText("""
            <!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;padding:20px;font:20px system-ui}p{height:80px;margin:18px 0;padding:12px;background:#dfd}button{padding:20px}canvas{background:orange}</style>
            <p id="a">Primer elemento estable</p><p id="b">Segundo elemento estable</p><button id="button" onclick="this.textContent='Funciona'">Interactuar</button>
            <canvas id="canvas" width="300" height="100"></canvas><a id="next" href="second.html">Segunda página</a><div style="height:2000px"></div>
        """.trimIndent())
        File(root, "data.json").writeText("{\"value\":42}")
        File(root, "second.html").writeText("<h1>Segunda página</h1><a href='index.html'>Volver</a>")
        document = runBlocking { repository.finishImport(PendingImport(id, staging, "Integration", listOf("index.html"), "index.html"), "index.html", null) }
        attach(document)
    }
    private fun attach(doc: Document) {
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            if (::web.isInitialized) { web.stopLoading(); web.destroy() }
            web = DocumentWebView(activity, doc, repository, event = { event -> events.add(event); if (event.optString("type") == "controller.ready") latch.countDown() },
                console = { logs.add(it) }, finished = {}, crashed = { fail("Renderer cerrado") })
            activity.setContentView(web)
            web.open()
        }
        assertTrue("Controlador no inicializado: $logs", latch.await(15, TimeUnit.SECONDS))
        SystemClock.sleep(200)
    }
    @After fun tearDown() { scenario.close(); runBlocking { repository.delete(document.id, false); extraDocuments.forEach { repository.delete(it, false) } } }
    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1); var result = ""
        instrument.runOnMainSync { web.evaluateJavascript(script) { result = it; latch.countDown() } }
        assertTrue(latch.await(5, TimeUnit.SECONDS)); return result
    }
    private fun decoded(script: String) = JSONTokener(evaluate(script)).nextValue()
    private fun rect(id: String): Pair<Float, Float> {
        val raw = decoded("JSON.stringify((()=>{let r=document.getElementById('$id').getBoundingClientRect();return [r.left+30,r.top+30]})())") as String
        val coords = JSONArray(raw)
        var scale = 1f; val location = IntArray(2)
        instrument.runOnMainSync { @Suppress("DEPRECATION") val current = web.scale; scale = current; web.getLocationOnScreen(location) }
        return (coords.getDouble(0).toFloat() * scale + location[0]) to (coords.getDouble(1).toFloat() * scale + location[1])
    }
    private fun touch(action: Int, point: Pair<Float, Float>, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue(instrument.uiAutomation.injectInputEvent(event, true)); event.recycle()
    }
    private fun mode(value: String) { instrument.runOnMainSync { web.changeMode(value) }; SystemClock.sleep(100) }
    private fun screenshot(name: String) {
        if(android.os.Build.VERSION.SDK_INT < 29)return
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME,"$name.png")
            put(android.provider.MediaStore.Downloads.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH,"Download/HyperMarkerVerification")
        }
        val uri=instrument.targetContext.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        instrument.uiAutomation.takeScreenshot().useBitmap { bitmap -> instrument.targetContext.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }

    @Test fun realLongPressHighlightsSlidesAndCommitsOnRelease() {
        mode("EDIT")
        val a = rect("a"); val b = rect("b"); val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, a, down); SystemClock.sleep(650)
        assertEquals("block", decoded("document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.display"))
        assertFalse(events.any { it.optString("type") == "target.selected" })
        screenshot("pin-held")
        touch(MotionEvent.ACTION_MOVE, b, down); SystemClock.sleep(100)
        assertEquals(decoded("document.getElementById('b').getBoundingClientRect().top"), decoded("parseFloat(document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.top)"))
        screenshot("pin-slide")
        touch(MotionEvent.ACTION_UP, b, down); SystemClock.sleep(250)
        val selected = events.last { it.optString("type") == "target.selected" }
        assertEquals("#b", selected.getJSONObject("payload").getJSONObject("anchor").getString("selector"))
        assertEquals("block", decoded("document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.display"))
        instrument.runOnMainSync { web.command("cancel") }; SystemClock.sleep(100)
        assertEquals("none", decoded("document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.display"))
        mode("VIEW"); evaluate("document.getElementById('button').click()")
        assertEquals("Funciona", decoded("document.getElementById('button').textContent"))
    }
    @Test fun viewLongPressSelectsBlockAndZoomFitsThenResets() {
        val point = rect("button"); val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, point, down); SystemClock.sleep(650); touch(MotionEvent.ACTION_UP, point, down); SystemClock.sleep(250)
        assertEquals("Interactuar", decoded("document.getElementById('button').textContent"))
        val press = events.last { it.optString("type") == "longpress" }.getJSONObject("payload")
        assertEquals("button", press.getJSONObject("target").getString("tag"))
        val box = press.getJSONObject("target").getJSONObject("rect")
        instrument.runOnMainSync { web.command("cancel"); web.zoomToFit(box.getDouble("x"), box.getDouble("y"), box.getDouble("width"), box.getDouble("height")) }
        SystemClock.sleep(900)
        assertTrue((decoded("visualViewport.scale") as Number).toDouble() > 1.5)
        val fits = decoded("(()=>{const r=document.getElementById('button').getBoundingClientRect(),v=visualViewport;return r.left-v.offsetLeft>=-2&&r.right-v.offsetLeft<=v.width+2&&r.top-v.offsetTop>=-2&&r.bottom-v.offsetTop<=v.height+2})()")
        screenshot("view-zoom-fit")
        assertEquals(true, fits)
        instrument.runOnMainSync { web.resetZoom() }
        SystemClock.sleep(900)
        assertEquals(1.0, (decoded("visualViewport.scale") as Number).toDouble(), 0.05)
    }
    @Test fun persistedPinReopensAndMissingTargetIsUnanchored() {
        mode("EDIT"); val down = SystemClock.uptimeMillis(); val a = rect("a")
        touch(MotionEvent.ACTION_DOWN, a, down); SystemClock.sleep(650); touch(MotionEvent.ACTION_UP, a, down); SystemClock.sleep(200)
        val data = events.last { it.optString("type") == "target.selected" }.getJSONObject("payload")
        val pin = Annotation(UUID.randomUUID().toString(), document.id, "PIN", data.getString("pageKey"), JSONObject().put("anchor", data.getJSONObject("anchor")).put("title", "Persistente").put("body", "**Nota**").toString())
        runBlocking { repository.replaceAnnotations(document.id, listOf(pin)) }
        attach(document)
        val stored = runBlocking { repository.annotations(document.id).single() }
        val display = JSONArray().put(JSONObject().put("id", stored.id).put("kind", stored.kind).put("pageKey", stored.pageKey).put("payload", JSONObject(stored.payload)))
        instrument.runOnMainSync { web.command("annotations", display) }; SystemClock.sleep(150)
        assertEquals(1, (decoded("document.querySelector('#__hypermarker_overlay').shadowRoot.querySelectorAll('.pin').length") as Number).toInt())
        evaluate("document.getElementById('a').textContent='Otro contenido con el mismo ID'"); SystemClock.sleep(200)
        val missing = events.last { it.optString("type") == "anchor.unresolved" }.getJSONObject("payload").getJSONArray("ids")
        assertEquals(stored.id, missing.getString(0))
    }
    @Test fun drawingCompletesTransactionAndCanvasUsesSurface() {
        mode("DRAW"); val start = rect("a"); val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, start, down)
        repeat(5) { touch(MotionEvent.ACTION_MOVE, start.first + it * 10f to start.second + it * 5f, down) }
        touch(MotionEvent.ACTION_UP, start.first + 60f to start.second + 30f, down); SystemClock.sleep(200)
        assertTrue(events.any { it.optString("type") == "stroke.completed" })
        mode("EDIT"); val surface = rect("canvas"); val next = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, surface, next); SystemClock.sleep(650); touch(MotionEvent.ACTION_UP, surface, next); SystemClock.sleep(200)
        assertEquals("SURFACE", events.last { it.optString("type") == "target.selected" }.getJSONObject("payload").getJSONObject("anchor").getString("strategy"))
    }
    @Test fun offlineBlocksFetchWebsocketWorkersAndOtherDocumentReads() {
        ServerSocket(0).use { server ->
            server.soTimeout = 800
            val port = server.localPort
            evaluate("fetch('https://127.0.0.1:$port/probe').catch(()=>{});try{new WebSocket('wss://127.0.0.1:$port/socket')}catch(e){};try{new Worker('https://127.0.0.1:$port/worker.js')}catch(e){};true")
            try { server.accept().close(); fail("El documento offline intentó abrir red") } catch (_: java.net.SocketTimeoutException) {}
        }
        evaluate("window.result='waiting';fetch('https://d00000000-0000-0000-0000-000000000000.hypermarker.invalid/index.html').then(r=>window.result=r.status).catch(()=>window.result='blocked')")
        SystemClock.sleep(300)
        assertTrue(decoded("String(window.result)") in listOf("blocked", "403"))
        instrument.runOnMainSync { assertFalse(web.settings.allowFileAccess); assertFalse(web.settings.allowContentAccess) }
    }
    @Test fun localRelativeNavigationAndConsoleWork() {
        evaluate("console.warn('fixture-warning')")
        val point = rect("next"); val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, point, down); touch(MotionEvent.ACTION_UP, point, down)
        repeat(50) { if (!decoded("document.body.textContent").toString().contains("Segunda página")) SystemClock.sleep(100) }
        assertTrue(decoded("document.body.textContent")!!.toString().contains("Segunda página"))
        instrument.runOnMainSync { web.goBack() }
        repeat(50) { if (decoded("document.getElementById('a')?.textContent||''") != "Primer elemento estable") SystemClock.sleep(100) }
        assertEquals("URL: ${decoded("location.href")} / $logs", "Primer elemento estable", decoded("document.getElementById('a')?.textContent"))
        assertTrue(logs.any { it.contains("fixture-warning") })
    }
    @Test fun autoscrollPausesOnTouchAndSupportsReverseDirection() {
        instrument.runOnMainSync { web.command("autoscroll", JSONObject().put("speed", 200)) }
        SystemClock.sleep(500)
        val first = (decoded("scrollY") as Number).toInt()
        assertTrue(first > 20)
        val down=SystemClock.uptimeMillis(); val at=Pair(300f, 600f)
        touch(MotionEvent.ACTION_DOWN, at, down); touch(MotionEvent.ACTION_UP, at, down)
        SystemClock.sleep(100)
        val paused=(decoded("scrollY") as Number).toInt()
        SystemClock.sleep(300)
        assertEquals(paused,(decoded("scrollY") as Number).toInt())
        instrument.runOnMainSync { web.command("autoscroll", JSONObject().put("speed", -100)) }
        SystemClock.sleep(300)
        assertTrue((decoded("scrollY") as Number).toInt() < paused)
    }
    @Test fun networkOptInAllowsSocketAttemptAndRevocationBlocksItAgain() {
        runBlocking { repository.setNetwork(document.id, true) }
        attach(runBlocking { repository.dao.document(document.id)!! })
        ServerSocket(0).use { server ->
            server.soTimeout=3000
            evaluate("try{new WebSocket('wss://127.0.0.1:${server.localPort}/socket')}catch(e){};true")
            server.accept().close()
        }
        runBlocking { repository.setNetwork(document.id, false) }
        attach(runBlocking { repository.dao.document(document.id)!! })
        ServerSocket(0).use { server ->
            server.soTimeout=700
            evaluate("try{new WebSocket('wss://127.0.0.1:${server.localPort}/socket')}catch(e){};true")
            try{server.accept().close();fail("Sigue abierta la red después de revocar permiso")}catch(_:java.net.SocketTimeoutException){}
        }
        assertFalse(runBlocking { repository.dao.document(document.id)!!.networkAllowed })
    }
    @Test fun pendingLongPressCanBeCancelledAndZoomKeepsOverlayAvailable() {
        mode("EDIT");val a=rect("a");val down=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,a,down)
        touch(MotionEvent.ACTION_MOVE,a.first+120f to a.second,down)
        SystemClock.sleep(600);touch(MotionEvent.ACTION_UP,a.first+120f to a.second,down)
        assertFalse(events.any { it.optString("type")=="target.selected" })
        mode("VIEW")
        instrument.runOnMainSync { web.zoomBy(1.25f) }
        SystemClock.sleep(250)
        assertEquals("object",decoded("typeof window.HyperMarker"))
        assertTrue((decoded("visualViewport.scale") as Number).toDouble() > 1.0)
    }

    @Test fun builtReactAssetsWorkInAndroidWebViewAndLocalStorageIsIsolated() {
        evaluate("localStorage.setItem('private','from-A')")
        val next = UUID.randomUUID().toString()
        val staging=File(instrument.targetContext.filesDir,"imports/$next").apply{mkdirs()}
        val root=File(staging,"original").apply{mkdirs()}
        fun copy(path: String, destination: File) {
            val assets=instrument.context.assets
            val children=assets.list(path).orEmpty()
            if(children.isNotEmpty()){destination.mkdirs();children.forEach{copy("$path/$it",File(destination,it))}}
            else assets.open(path).use{input->destination.parentFile!!.mkdirs();destination.outputStream().use{input.copyTo(it)}}
        }
        copy("react-built",root)
        val other=runBlocking { repository.finishImport(PendingImport(next,staging,"React",listOf("index.html"),"index.html"),"index.html",null) }
        extraDocuments+=other.id
        attach(other)
        repeat(50){if(decoded("document.getElementById('react-title')?.textContent")!="Un documento vivo")SystemClock.sleep(100)}
        assertEquals("Un documento vivo",decoded("document.getElementById('react-title').textContent"))
        assertEquals(JSONObject.NULL,decoded("localStorage.getItem('private')"))
        val at=rect("react-counter");val down=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,at,down);touch(MotionEvent.ACTION_UP,at,down);SystemClock.sleep(150)
        assertEquals("Explorar · 1",decoded("document.getElementById('react-counter').textContent"))
    }

    @Test fun offlineDisablesPeerConnectionsAndBlocksStunFromBlankFrames() {
        assertEquals("undefined",decoded("typeof RTCPeerConnection"))
        java.net.DatagramSocket(0,java.net.InetAddress.getByName("127.0.0.1")).use { socket ->
            socket.soTimeout=2000
            evaluate("(()=>{const frame=document.createElement('iframe');document.body.append(frame);try{const rtc=new frame.contentWindow.RTCPeerConnection({iceServers:[{urls:'stun:127.0.0.1:${socket.localPort}'}]});window.testPeer=rtc;rtc.createDataChannel('probe');rtc.createOffer().then(offer=>rtc.setLocalDescription(offer)).catch(()=>{});}catch(error){};return true})()")
            try{socket.receive(java.net.DatagramPacket(ByteArray(1024),1024));fail("El frame offline envió un paquete STUN")}catch(_:java.net.SocketTimeoutException){}
        }
    }

    @Test fun offlineFetchCanReadRelativeJsonButNotOtherProjects() {
        evaluate("window.localData=null;fetch('data.json').then(r=>r.json()).then(data=>window.localData=data.value).catch(error=>window.localData=String(error))")
        repeat(40){if(decoded("window.localData")!=42)SystemClock.sleep(100)}
        assertEquals(42,decoded("window.localData"))
    }

}
