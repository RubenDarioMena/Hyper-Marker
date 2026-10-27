package com.hypermarker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FlowRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LibraryDatabase
    private lateinit var repo: LibraryRepository
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(context, database)
    }
    @After fun tearDown() { database.close() }

    @Test fun nodesEdgesCollapseAndTombstones() = runBlocking {
        val flow = repo.createFlow(null, "Mapa")
        assertEquals("FLOW", flow.type)
        val a = repo.createFlowNode(flow.id, 0f, 0f, "Raíz", "")
        val b = repo.createFlowNode(flow.id, 100f, 50f, "", "Sólo cuerpo")
        val c = repo.createFlowNode(flow.id, 200f, 50f, "Hoja", "")
        try { repo.createFlowNode(flow.id, 0f, 0f, " ", ""); fail("Punto vacío permitido") } catch (_: IllegalArgumentException) {}
        try { repo.createFlowNode(flow.id, Float.NaN, 0f, "x", ""); fail("Coordenada inválida permitida") } catch (_: IllegalArgumentException) {}
        repo.connectFlowNodes(flow.id, a.id, b.id)
        repo.connectFlowNodes(flow.id, b.id, c.id)
        try { repo.connectFlowNodes(flow.id, c.id, a.id); fail("Ciclo permitido") } catch (_: IllegalArgumentException) {}
        try { repo.connectFlowNodes(flow.id, a.id, b.id); fail("Duplicado permitido") } catch (_: IllegalArgumentException) {}
        repo.updateFlowNode(flow.id, a.id, "Inicio", "Texto")
        repo.moveFlowNode(flow.id, a.id, -20f, 30f)
        repo.setFlowCollapsed(flow.id, a.id, true)
        val stored = repo.dao.flowNodes(flow.id).first { it.id == a.id }
        assertEquals("Inicio", stored.title); assertEquals(-20f, stored.x); assertTrue(stored.collapsed)

        repo.deleteFlowNode(flow.id, b.id)
        assertEquals(setOf(a.id, c.id), repo.dao.flowNodes(flow.id).map { it.id }.toSet())
        assertTrue(repo.dao.flowEdges(flow.id).isEmpty())
        // El tombstone sigue guardado para una sincronización futura.
        database.query("SELECT COUNT(*) FROM flow_edges WHERE deletedAt IS NOT NULL", null).use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
        // Al borrar b ya no hay ruta c → a, así que la conexión antes cíclica ahora es válida.
        repo.connectFlowNodes(flow.id, c.id, a.id)
        Unit
    }

    @Test fun duplicateCopiesTheGraphWithNewIdentitiesAndDeleteCascades() = runBlocking {
        val flow = repo.createFlow(null, "Original")
        val a = repo.createFlowNode(flow.id, 0f, 0f, "A", "")
        val b = repo.createFlowNode(flow.id, 1f, 1f, "B", "")
        repo.connectFlowNodes(flow.id, a.id, b.id)
        repo.duplicate(flow.id, false)
        val copy = repo.dao.documents().first { it.id != flow.id }
        val nodes = repo.dao.flowNodes(copy.id)
        assertEquals(setOf("A", "B"), nodes.map { it.title }.toSet())
        assertTrue(nodes.none { it.id == a.id || it.id == b.id })
        val edge = repo.dao.flowEdges(copy.id).single()
        assertEquals(nodes.first { it.title == "A" }.id, edge.fromId)
        repo.updateFlowNode(copy.id, edge.fromId, "Cambiada", "")
        assertEquals("A", repo.dao.flowNodes(flow.id).first { it.id == a.id }.title)
        repo.delete(flow.id, false)
        assertTrue(repo.dao.flowNodes(flow.id).isEmpty())
        assertFalse(repo.directory(flow.id).exists())
        assertEquals(2, repo.dao.flowNodes(copy.id).size)
    }

    /** Crea una base v1 con el esquema exportado, la migra con Room y comprueba que los datos sobreviven. */
    @Test fun migrationFromVersionOneKeepsDataAndValidatesSchema() = runBlocking {
        database.close()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.hypermarker.storage.LibraryDatabase/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { list -> for (j in 0 until list.length()) db.execSQL(list.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO documents (id, folderId, title, type, entryPath, networkAllowed, revision, createdAt, updatedAt) VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'Antigua', 'MARKDOWN', NULL, 0, 1, 0, 0)")
            db.version = 1
        }
        database = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3).build()
        repo = LibraryRepository(context, database)
        assertEquals("Antigua", repo.dao.documents().single().title)
        val flow = repo.createFlow(null, "Después de migrar")
        repo.createFlowNode(flow.id, 0f, 0f, "Punto", "")
        assertEquals(1, repo.dao.flowNodes(flow.id).size)
    }

    @Test fun migrationFromVersionTwoKeepsFlowAndAnnotations() = runBlocking {
        database.close()
        val name = "migration-v2-github.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.hypermarker.storage.LibraryDatabase/2.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { list -> for (j in 0 until list.length())
                    db.execSQL(list.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO documents (id, folderId, title, type, entryPath, networkAllowed, revision, createdAt, updatedAt) VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'Flow previo', 'FLOW', NULL, 0, 1, 0, 0)")
            db.execSQL("INSERT INTO flow_nodes (id, documentId, title, body, x, y, collapsed, createdAt, updatedAt, deletedAt) VALUES ('n1', '11111111-1111-1111-1111-111111111111', 'Punto', 'Texto', 1, 2, 0, 0, 0, NULL)")
            db.execSQL("INSERT INTO annotations (id, documentId, kind, pageKey, payload, schemaVersion) VALUES ('a1', '11111111-1111-1111-1111-111111111111', 'PIN', '/', '{}', 1)")
            db.version = 2
        }
        database = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_2_3).build()
        repo = LibraryRepository(context, database)
        assertEquals("Punto", repo.dao.flowNodes("11111111-1111-1111-1111-111111111111").single().title)
        assertEquals(1, repo.dao.annotations("11111111-1111-1111-1111-111111111111").size)
        assertTrue(repo.dao.remoteFolders().isEmpty())
    }
}
