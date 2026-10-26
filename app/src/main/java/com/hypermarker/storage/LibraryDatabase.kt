package com.hypermarker.storage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders", foreignKeys = [ForeignKey(entity = Folder::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("parentId")])
data class Folder(@PrimaryKey val id: String, val parentId: String?, val name: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "documents", foreignKeys = [ForeignKey(entity = Folder::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE)], indices = [Index("folderId")])
data class Document(@PrimaryKey val id: String, val folderId: String?, val title: String, val type: String,
    val entryPath: String? = null, val networkAllowed: Boolean = false, val revision: Int = 1,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "annotations", foreignKeys = [ForeignKey(entity = Document::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("documentId")])
data class Annotation(@PrimaryKey val id: String, val documentId: String, val kind: String,
    val pageKey: String, val payload: String, val schemaVersion: Int = 1)

@Entity(tableName = "preferences", foreignKeys = [ForeignKey(entity = Document::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)])
data class ViewerPreference(@PrimaryKey val documentId: String, val zoom: Int = 100, val scrollY: Int = 0,
    val speed: Float = 60f, val direction: Int = 1, val pagePath: String? = null)

/** Punto de un documento FLOW. Coordenadas de mundo en dp; `deletedAt` es tombstone para sincronización futura. */
@Entity(tableName = "flow_nodes", foreignKeys = [ForeignKey(entity = Document::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("documentId")])
data class FlowNode(@PrimaryKey val id: String, val documentId: String, val title: String, val body: String, val x: Float, val y: Float,
    val collapsed: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(), val deletedAt: Long? = null)

/** Conexión dirigida: `fromId` es el padre. */
@Entity(tableName = "flow_edges", foreignKeys = [
    ForeignKey(entity = Document::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = FlowNode::class, parentColumns = ["id"], childColumns = ["fromId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = FlowNode::class, parentColumns = ["id"], childColumns = ["toId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId"), Index("fromId"), Index("toId")])
data class FlowEdge(@PrimaryKey val id: String, val documentId: String, val fromId: String, val toId: String,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(), val deletedAt: Long? = null)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE") fun observeFolders(): Flow<List<Folder>>
    @Query("SELECT * FROM documents ORDER BY title COLLATE NOCASE") fun observeDocuments(): Flow<List<Document>>
    @Query("SELECT * FROM folders") suspend fun folders(): List<Folder>
    @Query("SELECT * FROM documents") suspend fun documents(): List<Document>
    @Query("SELECT * FROM documents WHERE id=:id") suspend fun document(id: String): Document?
    @Query("SELECT * FROM folders WHERE id=:id") suspend fun folder(id: String): Folder?
    @Query("SELECT * FROM annotations WHERE documentId=:id ORDER BY id") fun observeAnnotations(id: String): Flow<List<Annotation>>
    @Query("SELECT * FROM annotations WHERE documentId=:id ORDER BY id") suspend fun annotations(id: String): List<Annotation>
    @Query("SELECT * FROM preferences WHERE documentId=:id") suspend fun preference(id: String): ViewerPreference?
    @Upsert suspend fun put(folder: Folder)
    @Upsert suspend fun put(document: Document)
    @Upsert suspend fun put(preference: ViewerPreference)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAnnotations(values: List<Annotation>)
    @Query("DELETE FROM annotations WHERE documentId=:id") suspend fun clearAnnotations(id: String)
    @Query("SELECT * FROM flow_nodes WHERE documentId=:id AND deletedAt IS NULL ORDER BY createdAt, id") fun observeFlowNodes(id: String): Flow<List<FlowNode>>
    @Query("SELECT * FROM flow_edges WHERE documentId=:id AND deletedAt IS NULL ORDER BY createdAt, id") fun observeFlowEdges(id: String): Flow<List<FlowEdge>>
    @Query("SELECT * FROM flow_nodes WHERE documentId=:id AND deletedAt IS NULL") suspend fun flowNodes(id: String): List<FlowNode>
    @Query("SELECT * FROM flow_edges WHERE documentId=:id AND deletedAt IS NULL") suspend fun flowEdges(id: String): List<FlowEdge>
    @Upsert suspend fun put(node: FlowNode)
    @Upsert suspend fun put(edge: FlowEdge)
    @Upsert suspend fun putFlowNodes(values: List<FlowNode>)
    @Upsert suspend fun putFlowEdges(values: List<FlowEdge>)
    @Query("DELETE FROM folders WHERE id=:id") suspend fun deleteFolder(id: String)
    @Query("DELETE FROM documents WHERE id=:id") suspend fun deleteDocument(id: String)
}

@Database(entities = [Folder::class, Document::class, Annotation::class, ViewerPreference::class, FlowNode::class, FlowEdge::class], version = 2, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
    companion object {
        /** v1 → v2: añade las tablas de Flow Dots sin tocar los datos existentes. SQL copiado del esquema exportado 2.json. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `flow_nodes` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `collapsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_nodes_documentId` ON `flow_nodes` (`documentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `flow_edges` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `fromId` TEXT NOT NULL, `toId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`fromId`) REFERENCES `flow_nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`toId`) REFERENCES `flow_nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_edges_documentId` ON `flow_edges` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_edges_fromId` ON `flow_edges` (`fromId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flow_edges_toId` ON `flow_edges` (`toId`)")
            }
        }
    }
}
