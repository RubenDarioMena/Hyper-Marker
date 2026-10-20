package com.hypermarker

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportIntegrationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val repository: LibraryRepository get() = (context.applicationContext as HyperMarkerApplication).repository
    private val sources = mutableListOf<Uri>()
    private val imported = mutableListOf<String>()
    private fun shared(asset: String, mime: String): Uri {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, asset.substringAfterLast('/'))
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/HyperMarkerTests")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)); sources += uri
        instrument.context.assets.open(asset).use { input -> context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
        return uri
    }
    @Before fun recover() = runBlocking { repository.recover() }
    @After fun clean() { runBlocking { imported.forEach { repository.delete(it, false) } }; sources.forEach { context.contentResolver.delete(it, null, null) } }
    @Test fun htmlShareCopiesOriginalAndSurvivesSourceRemoval() = runBlocking {
        val uri = shared("standalone/index.html", "text/html")
        val pending = repository.importer.prepare(listOf(uri))
        val doc = repository.finishImport(pending, pending.preferred!!, null); imported += doc.id
        context.contentResolver.delete(uri, null, null)
        assertTrue(repository.original(doc.id).resolve(doc.entryPath!!).readText().contains("Tus ideas"))
    }
    @Test fun zipPreservesRelativeAssetsAndWrappedEntry() = runBlocking {
        for (asset in listOf("bundle/project.zip", "bundle/wrapped.zip")) {
            val pending = repository.importer.prepare(listOf(shared(asset, "application/zip")))
            val doc = repository.finishImport(pending, pending.preferred!!, null); imported += doc.id
            val page = repository.original(doc.id).resolve(doc.entryPath!!)
            assertTrue(page.parentFile!!.resolve("assets/app.js").isFile)
            assertTrue(page.parentFile!!.resolve("assets/shape.svg").isFile)
        }
    }
    @Test fun ambiguousEntryRequiresChoiceAndMaliciousArchivesLeaveNoDocument() = runBlocking {
        val pending = repository.importer.prepare(listOf(shared("bundle/ambiguous.zip", "application/zip")))
        assertNull(pending.preferred); assertEquals(listOf("a.html", "b.html"), pending.entries)
        val doc = repository.finishImport(pending, "b.html", null); imported += doc.id
        val count = repository.dao.documents().size
        for (asset in listOf("traversal", "absolute", "symlink", "collision")) {
            try { repository.importer.prepare(listOf(shared("malicious/$asset.zip", "application/zip"))); fail("ZIP malicioso aceptado") }
            catch (_: IllegalArgumentException) {}
        }
        assertEquals(count, repository.dao.documents().size)
        assertFalse(context.filesDir.resolve("escape.txt").exists())
    }
    @Test fun markdownImportRetainsExactSourceAndAtomicSave() = runBlocking {
        val uri = shared("markdown/source-only.md", "text/markdown")
        val doc = repository.importMarkdown(uri, null); imported += doc.id
        val source = instrument.context.assets.open("markdown/source-only.md").bufferedReader().use { it.readText() }
        assertEquals(source, repository.readMarkdown(doc.id))
        repository.saveMarkdown(doc.id, source + "\nOtra idea")
        assertEquals(source + "\nOtra idea", repository.readMarkdown(doc.id))
    }
}
