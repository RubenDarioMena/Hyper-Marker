package com.hypermarker.storage

import androidx.room.*
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
    @Query("DELETE FROM folders WHERE id=:id") suspend fun deleteFolder(id: String)
    @Query("DELETE FROM documents WHERE id=:id") suspend fun deleteDocument(id: String)
}

@Database(entities = [Folder::class, Document::class, Annotation::class, ViewerPreference::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() { abstract fun dao(): LibraryDao }
