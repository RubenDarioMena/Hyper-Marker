package com.hypermarker

import com.hypermarker.export.FeedbackExport
import com.hypermarker.storage.Annotation
import com.hypermarker.storage.Document
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class FeedbackExportTest {
    private fun stroke(id: String, drawing: String = "session", page: String = "/index.html") = Annotation(id, "doc", "STROKE", page,
        JSONObject().put("drawingId", drawing).put("points", JSONArray("[[-40,-50],[300,1800]]"))
            .put("color", "#00aa00").put("width", 3).put("tool", "pen").put("coordinates", "ELEMENT_CENTER_CSS")
            .put("anchor", JSONObject().put("tag", "section").put("selector", "#panel > .card:first-child").put("text", "Contexto")).toString())
    @Test fun groupsSessionsAndUsesSafeElementSelectorNames() {
        val groups = FeedbackExport.drawings(listOf(stroke("a"), stroke("b"), stroke("c", "next"), stroke("d", page = "/other.html")))
        assertEquals(3, groups.size); assertEquals(2, groups.first().strokes.size)
        assertTrue(groups.first().capture.contains("section-panel-card-first-child"))
        assertTrue(groups.all { it.capture.matches(Regex("captures/[a-zA-Z0-9_-]+\\.png")) })
        assertFalse(FeedbackExport.safeName("../../etc/secret").contains("/"))
    }
    @Test fun zipPreservesOriginalIncludesEachCaptureAndEscapesUntrustedNotesInReview() = runBlocking {
        val root = Files.createTempDirectory("feedback").toFile()
        try {
            val original = File(root, "source").apply { mkdirs() }
            val source = "<h1 id=title>Original</h1><section id=panel><section class=card>Contexto</section></section><script>window.original=true</script>"
            File(original, "index.html").writeText(source)
            File(original, "assets").mkdirs(); File(original, "assets/style.css").writeText("h1{color:red}")
            File(root, "secret-token").writeText("DO_NOT_EXPORT")
            val stage = File(root, "stage").apply { mkdirs() }
            FeedbackExport.prepare(original, File(stage, "original").apply { mkdirs() })
            val pin = Annotation("pin", "doc", "PIN", "/index.html", JSONObject().put("title", "<img src=x onerror=alert(1)>")
                .put("body", "Revisar este elemento </script>").put("anchor", JSONObject().put("selector", "#title").put("tag", "h1").put("text", "Original").put("strategy", "DOM").put("relativeX", .5).put("relativeY", .5)).toString())
            val annotations = listOf(stroke("a"), stroke("b"), stroke("c", "next"), pin)
            val document = Document("doc", null, "Ejemplo", "HTML", "index.html")
            val archive = FeedbackExport.finish(document, annotations, stage, androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets.open("annotation/controller.js").bufferedReader().use { it.readText() }) { drawing, target ->
                com.hypermarker.export.FeedbackImage.write(JSONArray().put(JSONObject(drawing.strokes.first().payload)), null, target); "Captura ${drawing.number} completa"
            }
            archive.copyTo(File("build/feedback-export-fixture.zip"), overwrite = true)
            val fixture = File("build/feedback-export-fixture"); fixture.deleteRecursively(); stage.copyRecursively(fixture)
            ZipFile(archive).use { zip ->
                fun read(path: String) = zip.getInputStream(zip.getEntry(path)).bufferedReader().use { it.readText() }
                assertEquals(source, read("original/index.html"))
                assertNotNull(zip.getEntry("original/assets/style.css")); assertNull(zip.getEntry("secret-token"))
                assertEquals(2, zip.entries().asSequence().count { it.name.startsWith("captures/") })
                val json = JSONObject(read("annotations.json"))
                assertEquals(4, json.getJSONArray("annotations").length()); assertEquals(2, json.getJSONArray("drawings").length())
                assertTrue(read("feedback.md").contains("#panel > .card:first-child"))
                assertTrue(read("review.html").contains("&lt;img")); assertFalse(read("review.html").contains("<img src=x"))
                assertFalse(read("review/data.js").contains("</script>"))
                assertTrue(read("review/content/index.html").contains("runtime.js"))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun pngIncludesNegativeCoordinatesAndFarEndpointsWithoutViewportClipping() = runBlocking {
        val file = File.createTempFile("feedback-image", ".png")
        try {
            val paths = JSONArray().put(JSONObject().put("points", JSONArray("[[-400,-300],[500,1900]]")).put("width", 8).put("color", "#00aa00").put("tool", "pen"))
            val factor = com.hypermarker.export.FeedbackImage.write(paths, null, file)
            assertEquals(1.0, factor, 0.0)
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
            assertEquals(956, bitmap.width); assertEquals(2256, bitmap.height)
            assertEquals(0x00aa00, bitmap.getPixel(28,28) and 0xffffff)
            assertEquals(0x00aa00, bitmap.getPixel(928,2228) and 0xffffff)
            bitmap.recycle()
            paths.getJSONObject(0).put("points", JSONArray("[[-10000,-5000],[10000,5000]]"))
            assertTrue(com.hypermarker.export.FeedbackImage.write(paths, null, file) < 1)
            val large = android.graphics.BitmapFactory.decodeFile(file.path)
            assertTrue(large.width <= 4096 && large.height <= 4096)
            assertTrue(large.width.toLong() * large.height < 4_010_000)
            large.recycle()
        } finally { file.delete() }
    }
    @Test fun prepareRejectsSymlinksOutsideTheOriginal() = runBlocking {
        val root = Files.createTempDirectory("feedback-paths").toFile()
        try {
            val original = File(root, "source").apply { mkdirs() }; val outside = File(root, "secret").apply { writeText("secret") }
            Files.createSymbolicLink(File(original, "leak.txt").toPath(), outside.toPath())
            try { FeedbackExport.prepare(original, File(root,"target")); fail("Exportó contenido externo") } catch (_: IllegalArgumentException) {}
        } finally { root.deleteRecursively() }
    }
}
