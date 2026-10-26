package com.hypermarker

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.hypermarker.storage.*
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppWorkflowTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrument)
    private val context get() = instrument.targetContext
    private val repo get() = (context.applicationContext as HyperMarkerApplication).repository
    private fun find(selector: BySelector): UiObject2 {
        val result = device.wait(Until.findObject(selector), 10_000)
        if(result == null){saveScreenshot("workflow-failure");error("No se encontró: $selector")}
        return result
    }
    private fun scrollTo(text: String): UiObject2 {
        repeat(8) {
            device.findObject(By.text(text))?.let { if(it.visibleBounds.height() > 45) return it }
            val scroll = device.findObjects(By.scrollable(true)).lastOrNull()
            if (scroll != null) scroll.scroll(Direction.DOWN, .65f) else device.swipe(device.displayWidth/2, device.displayHeight*8/10, device.displayWidth/2, device.displayHeight*4/10, 30)
            SystemClock.sleep(200)
        }
        return find(By.text(text))
    }
    /** El cromo de cristal se alterna tocando la página; sólo se toca si está oculto. */
    private fun showChrome() {
        if (device.wait(Until.hasObject(By.desc("Opciones del documento")), 600)) return
        device.click(device.displayWidth / 2, device.displayHeight * 3 / 4)
        find(By.desc("Opciones del documento"))
    }
    /** En Ver el cromo se oculta solo; reintenta si desaparece durante el clic. */
    private fun openMenu() {
        repeat(4) {
            showChrome()
            runCatching { device.findObject(By.desc("Opciones del documento"))?.click() }
            if (device.wait(Until.hasObject(By.text("Personalizar cruceta")), 1500)) return
        }
        find(By.text("Personalizar cruceta"))
    }
    private fun web(root: View): WebView? {
        if (root is WebView) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) web(root.getChildAt(i))?.let { return it }
        return null
    }
    private fun evaluate(scenario: ActivityScenario<MainActivity>, script: String): Any? {
        val latch = CountDownLatch(1); var result: Any? = null
        scenario.onActivity { activity ->
            val view = requireNotNull(web(activity.window.decorView))
            view.evaluateJavascript(script) { result = JSONTokener(it).nextValue(); latch.countDown() }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS)); return result
    }
    private fun waitFor(scenario: ActivityScenario<MainActivity>, expression: String, expected: Any) {
        repeat(80) { if (evaluate(scenario, expression) == expected) return; SystemClock.sleep(100) }
        assertEquals(expected, evaluate(scenario, expression))
    }
    private fun saveScreenshot(name: String) {
        if(Build.VERSION.SDK_INT < 29)return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png");put(MediaStore.Downloads.MIME_TYPE,"image/png")
            put(MediaStore.Downloads.RELATIVE_PATH,"Download/HyperMarkerVerification")
        }
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use { output -> instrument.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output) }
    }
    @Test fun markdownKeepsReadingPositionAndRestoresToolsAfterTimeout() {
        val title = "Prueba de posición ${SystemClock.uptimeMillis()}"
        val source = (0 until 80).joinToString("\n\n") { "## Sección $it\n\nTexto de la sección $it." } + "\n"
        val document = runBlocking { repo.createMarkdown(null, title, source) }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            find(By.clazz("android.widget.EditText")).text = title
            find(By.text(title)).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Mostrar herramientas")), 7_000))
            assertFalse(device.hasObject(By.text("Fuente")))
            find(By.desc("Mostrar herramientas")).click()
            find(By.text("Visual")).click()
            waitFor(scenario, "typeof window.editorSnapshot", "function")
            evaluate(scenario, "window.editorSetScrollFraction(.7)")
            SystemClock.sleep(300)
            val before = org.json.JSONObject(evaluate(scenario, "window.editorSnapshot()") as String).getDouble("fraction")
            assertTrue("Posición visual inicial: $before", before in 0.5..0.9)
            device.findObject(By.desc("Mostrar herramientas"))?.click()
            find(By.text("Fuente")).click()
            device.findObject(By.desc("Mostrar herramientas"))?.click()
            find(By.text("Visual")).click()
            waitFor(scenario, "typeof window.editorSnapshot", "function")
            SystemClock.sleep(300)
            val fraction = org.json.JSONObject(evaluate(scenario, "window.editorSnapshot()") as String).getDouble("fraction")
            assertTrue("Posición restaurada: $fraction", fraction in 0.5..0.9)
        } finally {
            scenario.close()
            runBlocking { repo.delete(document.id, false) }
        }
    }
    @Test fun sharePinSaveCancelUndoAndVisualMarkdownSurviveRecreation() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        runBlocking { repo.recover() }
        val before = runBlocking { repo.dao.documents().map { it.id }.toSet() }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "workflow.html"); put(MediaStore.Downloads.MIME_TYPE, "text/html")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/HyperMarkerTests")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write("""<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0;padding:20px;font:20px system-ui}p{padding:30px;background:#dfd;margin-top:120px}</style><p id="target">Una idea para anclar</p><p id="second">Segundo bloque estable</p><button id="counter" onclick="this.textContent='Funciona'">Interactuar</button><div style="height:1500px"></div>""".toByteArray()) }
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            showChrome()
            device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)(Got it|Entendido|Aceptar)"))), 2000)?.click()
            assertFalse(device.hasObject(By.desc("Herramientas del documento")))
            showChrome()
            find(By.desc("Modo Editar")).click(); SystemClock.sleep(400)
            waitFor(scenario, "document.documentElement.dataset.hypermarkerMode", "EDIT")
            var coords = Pair(0f, 0f)
            val raw = evaluate(scenario, "JSON.stringify((()=>{let r=document.getElementById('target').getBoundingClientRect();return [r.left+40,r.top+40]})())") as String
            val css = JSONArray(raw)
            scenario.onActivity { activity -> val view = web(activity.window.decorView)!!; val location = IntArray(2); view.getLocationOnScreen(location)
                @Suppress("DEPRECATION") val scale = view.scale
                coords = (css.getDouble(0).toFloat()*scale+location[0]) to (css.getDouble(1).toFloat()*scale+location[1])
            }
            fun gesture() {
                val down = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                    if (index == 1) SystemClock.sleep(650)
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, coords.first, coords.second, 0); event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrument.uiAutomation.injectInputEvent(event, true)); event.recycle()
                }
            }
            android.util.Log.i("Workflow", "coords=$coords css=$css scale="+evaluate(scenario,"devicePixelRatio")+" viewport="+evaluate(scenario,"innerWidth+'x'+innerHeight"))
            saveScreenshot("workflow-pin-ready")
            gesture(); find(By.desc("Cruceta de acciones"))
            saveScreenshot("workflow-edit-cross")
            find(By.desc("Nota pin")).click(); find(By.text("Nueva nota anclada"))
            val inputs = device.findObjects(By.clazz("android.widget.EditText")).sortedBy { it.visibleBounds.top }
            assertEquals(2, inputs.size); inputs[0].text = "Idea persistente"; inputs[1].text = "**Contexto** de la idea"
            find(By.text("Guardar")).click(); SystemClock.sleep(800)
            val doc = runBlocking { repo.dao.documents().first { it.id !in before && it.type == "HTML" } }
            assertEquals(1, runBlocking { repo.annotations(doc.id).size })
            saveScreenshot("workflow-pin-saved")
            coords = coords.first + 170f to coords.second
            gesture(); find(By.desc("Nota pin")).click(); find(By.text("Nueva nota anclada")); find(By.text("Cancelar")).click(); SystemClock.sleep(300)
            assertEquals(1, runBlocking { repo.annotations(doc.id).size })
            find(By.desc("Deshacer")).click(); SystemClock.sleep(300)
            assertEquals(0, runBlocking { repo.annotations(doc.id).size })
            find(By.desc("Rehacer")).click(); SystemClock.sleep(300)
            assertEquals(1, runBlocking { repo.annotations(doc.id).size })
            device.setOrientationLeft(); SystemClock.sleep(700)
            device.setOrientationNatural(); SystemClock.sleep(700)
            scenario.recreate()
            waitFor(scenario, "document.querySelector('#__hypermarker_overlay')?.shadowRoot.querySelectorAll('.pin').length", 1)
            openMenu()
            scrollTo("Crear nota Markdown"); device.waitForIdle()
            repeat(3) {
                if (!device.hasObject(By.text("Fuente"))) {
                    device.findObject(By.text("Crear nota Markdown"))?.click()
                    device.wait(Until.hasObject(By.text("Fuente")), 2000)
                }
            }
            SystemClock.sleep(500)
            saveScreenshot("workflow-note-created")
            android.util.Log.i("Workflow", "Documentos="+runBlocking { repo.dao.documents().map { it.type+":"+it.title } })
            val editor = find(By.clazz("android.widget.EditText"))
            editor.text = "# Título\n\n- Uno\n- Dos\n\n**Fuerte** y *énfasis*."
            SystemClock.sleep(800)
            find(By.text("Visual")).click()
            waitFor(scenario, "document.querySelector('.ProseMirror h1')?.textContent", "Título")
            saveScreenshot("workflow-markdown-visual")
            evaluate(scenario, "(()=>{const e=document.querySelector('.ProseMirror');e.focus();const selection=getSelection();selection.selectAllChildren(e);selection.collapseToEnd();document.execCommand('insertText',false,' Texto visual');return true})()")
            SystemClock.sleep(900)
            find(By.text("Fuente")).click(); SystemClock.sleep(300)
            assertTrue(find(By.clazz("android.widget.EditText")).text.contains("Texto visual"))
            scenario.recreate(); SystemClock.sleep(300)
            assertTrue(find(By.clazz("android.widget.EditText")).text.contains("Texto visual"))
            find(By.desc("Guardar y volver")).click()
            val note = runBlocking { repo.dao.documents().first { it.id !in before && it.type == "MARKDOWN" } }
            assertTrue(runBlocking { repo.readMarkdown(note.id) }.contains("Texto visual"))
        } finally {
            device.unfreezeRotation()
            scenario.close(); context.contentResolver.delete(uri, null, null)
            runBlocking { repo.dao.documents().filter { it.id !in before }.forEach { repo.delete(it.id, false) } }
        }
    }
}
