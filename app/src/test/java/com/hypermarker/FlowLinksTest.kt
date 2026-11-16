package com.hypermarker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.preview.MarkdownPreviewText
import com.hypermarker.preview.PreviewStore
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.storage.MAX_FLOW_LINKS
import com.hypermarker.storage.PrivateImages
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** FLOW-09…15 (parte JVM): relaciones, fotos, duplicado, borrado en cascada y migración v3 → v4. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FlowLinksTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LibraryDatabase
    private lateinit var repo: LibraryRepository
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(context, database)
    }
    @After fun tearDown() { database.close() }

    @Test fun relateDocumentsFoldersAndRevive() = runBlocking {
        val flow = repo.createFlow(null, "Mapa")
        val node = repo.createFlowNode(flow.id, 0f, 0f, "Anotar", "")
        val note = repo.createMarkdown(null, "Notas")
        repo.createFolder(null, "Proyectos")
        val folder = repo.dao.folders().single()
        assertEquals(2, repo.linkFlowNode(flow.id, node.id, listOf(note.id), listOf(folder.id)))
        assertEquals("Repetir no duplica", 0, repo.linkFlowNode(flow.id, node.id, listOf(note.id), listOf(folder.id)))
        val links = repo.dao.flowLinks(flow.id)
        assertEquals(listOf(0, 1), links.map { it.position })
        try { repo.linkFlowNode(flow.id, node.id, listOf(flow.id), emptyList()); fail("Un Flow consigo mismo") } catch (_: IllegalArgumentException) {}
        try { repo.linkFlowNode(flow.id, node.id, listOf("00000000-0000-0000-0000-000000000000"), emptyList()); fail("Documento inexistente") } catch (_: IllegalArgumentException) {}
        // Quitar deja tombstone; volver a relacionar lo revive sin violar el índice único.
        repo.unlinkFlowNode(flow.id, links.first { it.targetDocumentId == note.id }.id)
        assertEquals(1, repo.dao.flowLinks(flow.id).size)
        assertEquals(1, repo.linkFlowNode(flow.id, node.id, listOf(note.id), emptyList()))
        assertEquals(2, repo.dao.flowLinks(flow.id).size)
        assertEquals(2, repo.dao.allFlowLinks(flow.id).size)
        val before = repo.dao.flowLinks(flow.id).map { it.id }
        repo.reorderFlowLinks(flow.id, node.id, before.reversed())
        assertEquals(before.reversed(), repo.dao.flowLinks(flow.id).map { it.id })
        try { repo.reorderFlowLinks(flow.id, node.id, before.take(1)); fail("Orden incompleto aceptado") } catch (_: IllegalArgumentException) {}
    }

    /** FLOW-12: borrar el documento relacionado elimina la relación (decisión 5: sin marcas de «roto»). */
    @Test fun deletingTargetCascades() = runBlocking {
        val flow = repo.createFlow(null, "Mapa")
        val node = repo.createFlowNode(flow.id, 0f, 0f, "A", "")
        val note = repo.createMarkdown(null, "Notas")
        repo.createFolder(null, "Carpeta"); val folder = repo.dao.folders().single()
        repo.linkFlowNode(flow.id, node.id, listOf(note.id), listOf(folder.id))
        repo.delete(note.id, false)
        assertEquals(listOf(folder.id), repo.dao.flowLinks(flow.id).map { it.targetFolderId })
        repo.delete(folder.id, true)
        assertTrue(repo.dao.allFlowLinks(flow.id).isEmpty())
        // Borrar el punto marca sus relaciones; borrar el Flow las elimina.
        repo.linkFlowNode(flow.id, node.id, listOf(repo.createMarkdown(null, "Otra").id), emptyList())
        repo.deleteFlowNode(flow.id, node.id)
        assertTrue(repo.dao.flowLinks(flow.id).isEmpty())
        repo.delete(flow.id, false)
        assertTrue(repo.dao.allFlowLinks(flow.id).isEmpty())
    }

    /** FLOW-13: duplicar copia relaciones (con nuevos ids) y archivos privados. */
    @Test fun duplicateCopiesLinksAndPrivateFiles() = runBlocking {
        val flow = repo.createFlow(null, "Original")
        val node = repo.createFlowNode(flow.id, 0f, 0f, "A", "")
        val note = repo.createMarkdown(null, "Notas")
        repo.linkFlowNode(flow.id, node.id, listOf(note.id), emptyList())
        File(repo.directory(flow.id), "avatars").mkdirs()
        File(repo.directory(flow.id), "avatars/foto.webp").writeText("x")
        repo.dao.put(repo.dao.flowNodes(flow.id).single().copy(avatarPath = "avatars/foto.webp", avatarLinkId = repo.dao.flowLinks(flow.id).single().id, ringColor = 0xFFE5B62F))
        repo.duplicate(flow.id, false)
        val copy = repo.dao.documents().first { it.type == "FLOW" && it.id != flow.id }
        val copied = repo.dao.flowNodes(copy.id).single()
        val link = repo.dao.flowLinks(copy.id).single()
        assertEquals(note.id, link.targetDocumentId); assertEquals(copied.id, link.nodeId)
        assertNotEquals(repo.dao.flowLinks(flow.id).single().id, link.id)
        assertEquals("La foto apunta a la relación copiada", link.id, copied.avatarLinkId)
        assertTrue(File(repo.directory(copy.id), "avatars/foto.webp").isFile)
        assertEquals(0xFFE5B62F, copied.ringColor)
    }

    @Test fun limitsAndPrivatePaths() = runBlocking {
        val flow = repo.createFlow(null, "Mapa")
        val node = repo.createFlowNode(flow.id, 0f, 0f, "A", "")
        val notes = (0..MAX_FLOW_LINKS).map { repo.createMarkdown(null, "N$it").id }
        try { repo.linkFlowNode(flow.id, node.id, notes, emptyList()); fail("Más de $MAX_FLOW_LINKS relaciones") } catch (_: IllegalArgumentException) {}
        assertTrue(repo.dao.flowLinks(flow.id).isEmpty())
        assertNotNull(repo.flowFile(flow.id, "attachments/abc-1.webp"))
        listOf("../x.webp", "avatars/../../library.db", "/etc/passwd", "avatars/x.png", "other/x.webp").forEach { path ->
            try { repo.flowFile(flow.id, path); fail("Ruta aceptada: $path") } catch (_: IllegalArgumentException) {}
        }
        repo.markOpened(flow.id)
        assertNotNull(repo.dao.document(flow.id)!!.lastOpenedAt)
        assertEquals("Abrir no cambia la revisión", 1, repo.dao.document(flow.id)!!.revision)
    }

    /** FLOW-15: una base v3 real migra a v4 conservando datos y con el esquema que Room espera. */
    @Test fun migrationFromVersionThree() = runBlocking {
        database.close()
        val name = "migration-v3.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.hypermarker.storage.LibraryDatabase/3.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { list -> for (j in 0 until list.length()) db.execSQL(list.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO documents (id, folderId, title, type, entryPath, networkAllowed, revision, createdAt, updatedAt) VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'Flow v3', 'FLOW', NULL, 0, 3, 5, 6)")
            db.execSQL("INSERT INTO flow_nodes (id, documentId, title, body, x, y, collapsed, createdAt, updatedAt, deletedAt) VALUES ('n1', '11111111-1111-1111-1111-111111111111', 'Punto', 'Texto', 1, 2, 1, 0, 0, NULL)")
            db.execSQL("INSERT INTO flow_nodes (id, documentId, title, body, x, y, collapsed, createdAt, updatedAt, deletedAt) VALUES ('n2', '11111111-1111-1111-1111-111111111111', 'Hijo', '', 1, 2, 0, 0, 0, NULL)")
            db.execSQL("INSERT INTO flow_edges (id, documentId, fromId, toId, createdAt, updatedAt, deletedAt) VALUES ('e1', '11111111-1111-1111-1111-111111111111', 'n1', 'n2', 0, 0, NULL)")
            db.version = 3
        }
        database = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_3_4).build()
        repo = LibraryRepository(context, database)
        val doc = repo.dao.document("11111111-1111-1111-1111-111111111111")!!
        assertEquals(3, doc.revision); assertNull(doc.lastOpenedAt)
        val nodes = repo.dao.flowNodes(doc.id)
        assertEquals(setOf("n1", "n2"), nodes.map { it.id }.toSet()); assertTrue(nodes.first { it.id == "n1" }.collapsed)
        assertTrue(nodes.all { it.avatarPath == null && it.ringColor == null })
        assertEquals(1, repo.dao.flowEdges(doc.id).size)
        val note = repo.createMarkdown(null, "Nueva")
        assertEquals(1, repo.linkFlowNode(doc.id, "n1", listOf(note.id), emptyList()))
        database.close()
    }

    @Test fun previewHelpers() {
        val lines = MarkdownPreviewText.lines("# Título\n\n```\ncódigo\n```\n- **uno** con [enlace](https://x)\n> cita\n---\n1. dos", 10)
        assertEquals(listOf("Título", "uno con enlace", "cita", "dos"), lines.map { it.text })
        assertTrue(lines[0].heading); assertTrue(lines[1].bullet); assertTrue(lines[3].bullet)
        assertEquals("a-b.webp", PreviewStore.fileName("a", "b"))
        assertEquals(512 to 256, PrivateImages.fittedSize(2048, 1024, 512))
        assertEquals(100 to 50, PrivateImages.fittedSize(100, 50, 512))
        assertEquals(4, PrivateImages.sampleSize(4000, 3000, 512))
    }
}
