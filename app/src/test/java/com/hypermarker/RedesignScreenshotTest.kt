package com.hypermarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.flow.FlowScreen
import com.hypermarker.picker.*
import com.hypermarker.preview.PreviewStore
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.storage.PrivateImages
import com.hypermarker.ui.theme.HyperMarkerTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas del rediseño renderizadas por Compose con gráficos nativos de Robolectric (sin dispositivo).
 * Sirven como evidencia visual en `app/build/screenshots/`; no sustituyen la prueba táctil en un teléfono.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RedesignScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private val out = File("build/screenshots").apply { mkdirs() }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(context, database)
    }
    @After fun tearDown() { database.close() }

    /** Vistas raíz de todas las ventanas (actividad y hojas), en orden de apilado. */
    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<android.view.View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(global) as List<android.view.View>).filter { it.isAttachedToWindow && it.width > 0 }
    }
    /** Dibuja todas las ventanas en un mismo lienzo, como se verían en pantalla. */
    private fun save(name: String) {
        compose.waitForIdle()
        val views = windows()
        val base = views.first()
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        views.forEach { view ->
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat()); view.draw(canvas); canvas.restore()
        }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun saveTopWindow(name: String) = save(name)

    private fun avatar(flow: String, name: String, background: Int, accent: Int): String {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(180f, 70f, 34f, paint)
        paint.color = 0xFF176B5B.toInt(); canvas.drawRect(0f, 170f, 256f, 256f, paint)
        val path = "avatars/$name.webp"
        PrivateImages.writeWebp(bitmap, repo.flowFile(flow, path)!!)
        return path
    }

    private fun seedFlow(): Pair<String, Map<String, String>> = runBlocking {
        val flow = repo.createFlow(null, "Mapa de ideas")
        val root = repo.createFlowNode(flow.id, 0f, 0f, "Hyper-Marker", "")
        val read = repo.createFlowNode(flow.id, -130f, 190f, "Leer", "")
        val note = repo.createFlowNode(flow.id, 0f, 230f, "Anotar", "Pines, edición de texto y dibujos separados del HTML original.")
        val think = repo.createFlowNode(flow.id, 140f, 200f, "Pensar", "")
        val guide = repo.createFlowNode(flow.id, -90f, 420f, "Guía Sync", "Revisar antes de añadir la carpeta «docs».")
        val sketches = repo.createFlowNode(flow.id, 90f, 430f, "Bocetos", "")
        val hidden = repo.createFlowNode(flow.id, 90f, 560f, "Oculto", "")
        listOf(root to read, root to note, root to think, note to guide, note to sketches, sketches to hidden).forEach { (a, b) -> repo.connectFlowNodes(flow.id, a.id, b.id) }
        repo.setFlowCollapsed(flow.id, sketches.id, true)
        listOf(root to avatar(flow.id, "root", 0xFF176B5B.toInt(), 0xFFE5B62F.toInt()), read to avatar(flow.id, "read", 0xFFFBE7A1.toInt(), 0xFF14201C.toInt()),
            note to avatar(flow.id, "note", 0xFFD5EBE3.toInt(), 0xFFE5B62F.toInt()), guide to avatar(flow.id, "guide", 0xFFFFFFFF.toInt(), 0xFF176B5B.toInt())).forEach { (node, path) ->
            repo.dao.put(repo.dao.flowNodes(flow.id).first { it.id == node.id }.copy(avatarPath = path, ringColor = if (node == note) 0xFFE5B62F else null))
        }
        val docs = (1..8).map { repo.createMarkdown(null, listOf("Notas del ejemplo", "README", "Ejemplo interactivo", "Otro mapa", "Guía", "Ideas", "Lista", "Plan")[it - 1],
            "# Notas\n\nLo que quiero revisar.\n\n- Probar la cruceta\n- Guardar una web").id }
        repo.createFolder(null, "docs"); val folder = repo.dao.folders().first()
        repo.linkFlowNode(flow.id, note.id, docs, listOf(folder.id))
        repo.linkFlowNode(flow.id, guide.id, listOf(docs[4]), emptyList())
        flow.id to mapOf("note" to note.id, "guide" to guide.id, "think" to think.id)
    }

    private fun flowScreen(id: String) {
        val document = runBlocking { repo.dao.document(id)!! }
        compose.setContent { HyperMarkerTheme(dark = false) { FlowScreen(document, repo, PreviewStore(repo), onClose = {}) } }
    }

    @Test fun flowCanvasWithPhotos() {
        val (flow, _) = seedFlow()
        flowScreen(flow)
        compose.mainClock.advanceTimeBy(1000)
        save("09-flow-lienzo")
    }

    /** Texto en las flechas y una conexión de retorno (bucle visual) hacia un punto anterior. */
    @Test fun flowArrowTextAndReturns() {
        val flow = runBlocking {
            val flow = repo.createFlow(null, "Ciclo de una idea")
            val idea = repo.createFlowNode(flow.id, 0f, 0f, "Idea", "")
            val proto = repo.createFlowNode(flow.id, 0f, 190f, "Prototipo", "")
            val test = repo.createFlowNode(flow.id, 0f, 380f, "Prueba", "")
            val ship = repo.createFlowNode(flow.id, 150f, 560f, "Lanzar", "")
            val notes = repo.createFlowNode(flow.id, -150f, 560f, "Aprendizajes", "")
            repo.connectFlowNodes(flow.id, idea.id, proto.id, "construir")
            repo.connectFlowNodes(flow.id, proto.id, test.id, "probar")
            repo.connectFlowNodes(flow.id, test.id, ship.id, "si funciona")
            repo.connectFlowNodes(flow.id, test.id, notes.id)
            check(repo.connectFlowNodes(flow.id, test.id, idea.id, "ajustar").loop)
            check(repo.connectFlowNodes(flow.id, notes.id, proto.id, "mejora").loop)
            flow.id
        }
        flowScreen(flow)
        compose.mainClock.advanceTimeBy(1000)
        save("15-flow-flechas-y-retornos")
    }

    /** Las muestras de estados y mapa mental del propietario copiadas a un mismo Flow (ver MermaidFlowTest). */
    @Test fun flowFromStateAndMindMap() {
        val flow = runBlocking {
            fun graph(name: String) = com.hypermarker.mermaid.MermaidFlow.parse(javaClass.getResource("/mermaid/$name.graph.json")!!.readText())
            val (flow, _) = repo.copyMermaidToFlow(graph("muestra-4-mapa-mental"), null, null, "Mermaid en Flow", null)
            repo.copyMermaidToFlow(graph("muestra-3-estados"), flow.id, null, "Estados", "Estados" to "Copia")
            flow.id
        }
        flowScreen(flow)
        compose.mainClock.advanceTimeBy(1000)
        save("16-flow-desde-mapa-mental-y-estados")
    }

    @Test fun flowOpenDotAndRadial() {
        val (flow, ids) = seedFlow()
        flowScreen(flow)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Punto: Guía Sync", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1500)
        save("10-flow-punto-abierto")
        compose.onAllNodesWithContentDescription("Cerrar").onFirst().performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("Punto: Anotar", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1500)
        save("11-flow-radial")
        compose.onNodeWithText("Foto").performClick()
        compose.mainClock.advanceTimeBy(1500)
        saveTopWindow("12-flow-foto")
        check(ids.isNotEmpty())
    }

    @Test fun pickerMoveAndRelate() {
        val folders = listOf(PickFolder("a", null, "Proyectos"), PickFolder("a1", "a", "RTS auto"), PickFolder("a2", "a", "Factorio"), PickFolder("g", "a", "docs"))
        val documents = listOf(PickDocument("d1", "a", "Guía GitHub Sync", "WEB_SNAPSHOT"), PickDocument("d2", "a", "Notas del ejemplo", "MARKDOWN"),
            PickDocument("d3", "a", "Ejemplo interactivo", "HTML"), PickDocument("d4", "a", "Otro mapa", "FLOW"))
        val move = ItemPickerState(PickerMode.DESTINATION, folders, documents, readOnlyRoots = setOf("g"), moving = "d1" to false).apply { enter("a") }
        compose.setContent { HyperMarkerTheme(dark = false) { Picker(move, PickerSubject("Mover", "Guía GitHub Sync")) } }
        compose.mainClock.advanceTimeBy(1500)
        saveTopWindow("13-selector-mover")
    }

    @Test fun pickerRelate() {
        val folders = listOf(PickFolder("a", null, "Proyectos"))
        val documents = listOf(PickDocument("d1", null, "Guía GitHub Sync", "WEB_SNAPSHOT"), PickDocument("d2", null, "Notas del ejemplo", "MARKDOWN"),
            PickDocument("d3", null, "Ejemplo interactivo", "HTML"), PickDocument("d4", null, "Otro mapa", "FLOW"))
        val relate = ItemPickerState(PickerMode.RELATE, folders, documents, alreadyLinked = setOf("d1")).apply { toggle("d2"); toggle("d3") }
        compose.setContent { HyperMarkerTheme(dark = false) { Picker(relate, PickerSubject("Relacionar con", "Anotar")) } }
        compose.mainClock.advanceTimeBy(1500)
        saveTopWindow("14-selector-relacionar")
    }

    @Test fun libraryAndNewSheet() {
        val app = context as HyperMarkerApplication
        val ids = runBlocking {
            val r = app.repository
            r.createFolder(null, "Proyectos")
            val sample = r.createMarkdown(null, "Notas · Ejemplo interactivo", "# Notas")
            r.createFlow(null, "Mapa de ideas")
            r.createMarkdown(null, "Guía GitHub Sync", "Texto")
            r.markOpened(sample.id); sample.id
        }
        val model = com.hypermarker.library.LibraryViewModel(app)
        compose.setContent { HyperMarkerTheme(dark = false) { com.hypermarker.library.LibraryScreen(model) } }
        compose.mainClock.advanceTimeBy(1500)
        save("01-biblioteca")
        compose.onNodeWithText("Nuevo").performClick()
        compose.mainClock.advanceTimeBy(1500)
        save("02-nuevo")
        check(ids.isNotEmpty())
    }

    @Test fun markdownSource() {
        val app = context as HyperMarkerApplication
        val note = runBlocking { app.repository.createMarkdown(null, "Notas · Ejemplo interactivo", "# Notas del ejemplo\n\nLo que quiero revisar al volver a esta página: **los pines** guardan una nota.\n\n## Pendientes\n\n- Probar la cruceta\n- Guardar una web sin conexión\n\n<div>html</div>") }
        compose.setContent { HyperMarkerTheme(dark = false) { com.hypermarker.markdown.MarkdownScreen(note, app.repository, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1500)
        save("07-markdown")
    }

    @Test fun browserAndGitHub() {
        val app = context as HyperMarkerApplication
        compose.setContent { HyperMarkerTheme(dark = false) { com.hypermarker.browser.BrowserScreen(app.repository, null, null, onClose = {}, onSaved = {}) } }
        compose.mainClock.advanceTimeBy(1000)
        save("03-navegador")
    }

    @Test fun gitHubWizard() {
        val model = com.hypermarker.library.LibraryViewModel(context as HyperMarkerApplication)
        compose.setContent { HyperMarkerTheme(dark = false) { com.hypermarker.github.GitHubAddScreen(model) } }
        compose.mainClock.advanceTimeBy(1000)
        save("04-github-cuenta")
    }

    @Composable private fun Picker(state: ItemPickerState, subject: PickerSubject) =
        ItemPickerSheet(state, subject, recent = listOf("a1", "a2"), onDismiss = {}, onCreateFolder = { _, _ -> }, onImage = {})
}
