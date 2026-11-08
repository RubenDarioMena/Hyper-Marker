package com.hypermarker

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderDrawingTest {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue(condition())
    }
    @Test fun drawingIsDraftUntilConfirmedAndCancellationRestoresErasedAnnotations() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LibraryDatabase::class.java).build()
        val repo = LibraryRepository(ApplicationProvider.getApplicationContext(), db)
        val document = runBlocking { repo.createMarkdown(null, "test", "original") }
        val model = ReaderViewModel(document, repo)
        await { model.loaded }
        model.mode = "DRAW"
        fun event(type: String, payload: JSONObject = JSONObject()) = model.event(JSONObject().put("type", type).put("payload", payload.put("pageKey", "/index.html")))
        fun stroke() = event("stroke.completed", JSONObject().put("points", org.json.JSONArray("[[-10,0],[20,30]]")).put("coordinates", "ELEMENT_CENTER_CSS"))
        event("drawing.started"); stroke(); stroke()
        assertEquals(2, model.annotations.size)
        assertTrue(runBlocking { repo.annotations(document.id) }.isEmpty())
        model.finishDrawing(false)
        assertTrue(model.annotations.isEmpty())
        event("drawing.started"); stroke(); stroke(); model.finishDrawing(true)
        await { model.saved }
        assertEquals(2, runBlocking { repo.annotations(document.id) }.size)
        val id = model.annotations.first().id
        event("drawing.started"); event("stroke.erase", JSONObject().put("id", id))
        assertEquals(1, model.annotations.size)
        model.finishDrawing(false)
        assertEquals(2, model.annotations.size)
        assertEquals(2, runBlocking { repo.annotations(document.id) }.size)
        model.undo(); await { model.saved }
        assertTrue(runBlocking { repo.annotations(document.id) }.isEmpty())
        db.close()
    }
}
