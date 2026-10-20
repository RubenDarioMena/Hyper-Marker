package com.hypermarker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RepositoryTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repo: LibraryRepository
    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        repo = LibraryRepository(context, database)
    }
    @After fun tearDown() { database.close() }
    @Test fun nestedLibraryMoveDuplicateDeleteAndMarkdownPersistence() = runBlocking {
        repo.createFolder(null, "A")
        val a = repo.dao.folders().single()
        repo.createFolder(a.id, "B"); val b = repo.dao.folders().first { it.name == "B" }
        repo.createFolder(b.id, "C"); val c = repo.dao.folders().first { it.name == "C" }
        val doc = repo.createMarkdown(c.id, "Idea", "# Uno\n\n**Texto**")
        repo.saveMarkdown(doc.id, "# Dos\n\n- uno\n- dos")
        assertEquals("# Dos\n\n- uno\n- dos", repo.readMarkdown(doc.id))
        repo.rename(doc.id, false, "Nueva idea")
        repo.move(doc.id, false, b.id)
        assertEquals(b.id, repo.dao.document(doc.id)!!.folderId)
        try { repo.move(a.id, true, c.id); fail("Ciclo permitido") } catch (_: IllegalArgumentException) {}
        repo.duplicate(a.id, true)
        assertEquals(2, repo.dao.documents().size)
        val copy = repo.dao.documents().first { it.id != doc.id }
        repo.saveMarkdown(copy.id, "Independiente")
        assertNotEquals(repo.readMarkdown(doc.id), repo.readMarkdown(copy.id))
        repo.delete(a.id, true)
        assertEquals(listOf(copy.id), repo.dao.documents().map { it.id })
        assertFalse(repo.directory(doc.id).exists())
        assertTrue(repo.directory(copy.id).exists())
    }
    @Test fun recoveryCleansOrphanFilesButKeepsCommittedDocuments() = runBlocking {
        val doc = repo.createMarkdown(null, "Conservar", "Fuente")
        val orphan = repo.directory("00000000-0000-0000-0000-000000000000").apply { mkdirs() }
        repo.recover()
        assertFalse(orphan.exists())
        assertEquals("Fuente", repo.readMarkdown(doc.id))
    }
    @Test fun firstImportCreatesParentAndDiskDatabaseSurvivesReopen() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        database = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        repo = LibraryRepository(context, database)
        val id = java.util.UUID.randomUUID().toString()
        val staging = java.io.File(context.filesDir, "imports/$id").apply { mkdirs() }
        java.io.File(staging, "original/index.html").apply { parentFile!!.mkdirs(); writeText("<h1>Persistente</h1>") }
        val doc = repo.finishImport(com.hypermarker.storage.PendingImport(id, staging, "HTML", listOf("index.html"), "index.html"), "index.html", null)
        database.close()
        database = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        repo = LibraryRepository(context, database)
        assertEquals(doc.id, repo.dao.documents().single().id)
        assertEquals("<h1>Persistente</h1>", repo.original(doc.id).resolve("index.html").readText())
    }
}
