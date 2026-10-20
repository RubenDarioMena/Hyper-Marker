package com.hypermarker

import com.hypermarker.security.FolderRules
import com.hypermarker.security.PathGuard
import com.hypermarker.storage.ImportLimits
import com.hypermarker.storage.SafeImporter
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private fun zip(vararg entries: Triple<String, String, Int>): File {
        val file = temp.newFile()
        ZipArchiveOutputStream(file).use { out -> entries.forEach { (path, body, mode) ->
            val entry = ZipArchiveEntry(path); if (mode != 0) entry.unixMode = mode
            out.putArchiveEntry(entry); out.write(body.toByteArray()); out.closeArchiveEntry()
        } }
        return file
    }
    private fun fails(block: () -> Unit) { try { block(); fail("Debió rechazar la entrada") } catch (_: IllegalArgumentException) {} }
    @Test fun rejectsTraversalAbsoluteAndEncodedPaths() {
        listOf("../x", "a/../../x", "/root", "C:/x", "a\\b", "a//b", "a/./b", "%2e%2e/x", "x\u0000y").forEach { path -> fails { PathGuard.resolve(temp.root, path) } }
        assertEquals(File(temp.root, "assets/imagen ñ.png").canonicalFile, PathGuard.resolve(temp.root, "assets/imagen ñ.png"))
    }
    @Test fun preventsCyclesWithoutRestrictingValidNestedFolders() {
        val parents = mapOf("a" to null, "b" to "a", "c" to "b", "d" to null)
        fails { FolderRules.validateMove("a", "c", parents) }
        fails { FolderRules.validateMove("b", "b", parents) }
        fails { FolderRules.validateMove("a", "missing", parents) }
        FolderRules.validateMove("c", "d", parents)
        FolderRules.validateMove("a", null, parents)
    }
    @Test fun extractsRelativeResources() = runBlocking {
        val root = temp.newFolder()
        SafeImporter.extractZip(zip(Triple("index.html", "<h1>ok</h1>", 0), Triple("css/site.css", "body{}", 0)), root)
        assertEquals("body{}", File(root, "css/site.css").readText())
    }
    @Test fun rejectsZipTraversalAndSymlink() {
        val root = temp.newFolder()
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("../escape.txt", "bad", 0)), root) } }
        assertFalse(File(root.parentFile, "escape.txt").exists())
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("link", "../secret", 0xA1FF)), root) } }
    }
    @Test fun rejectsNormalizedAndCaseCollisions() {
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("index.html", "a", 0), Triple("INDEX.html", "b", 0)), temp.newFolder()) } }
    }
    @Test fun enforcesActualByteFileCountAndCompressionLimits() {
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("a", "0123456789", 0)), temp.newFolder(), ImportLimits(perFile = 5)) } }
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("a", "a", 0), Triple("b", "b", 0)), temp.newFolder(), ImportLimits(files = 1)) } }
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("a", "abc", 0), Triple("b", "def", 0)), temp.newFolder(), ImportLimits(total = 5)) } }
        fails { runBlocking { SafeImporter.extractZip(zip(Triple("a", "0".repeat(20_000), 0)), temp.newFolder(), ImportLimits(ratio = 2)) } }
    }
}
