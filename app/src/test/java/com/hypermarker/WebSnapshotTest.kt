package com.hypermarker

import com.hypermarker.browser.WebAddress
import com.hypermarker.browser.WebSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class WebSnapshotTest {
    @Test fun addressesSearchAndPrivilegedSchemes() {
        assertEquals("https://example.com/study", WebAddress.resolve("example.com/study"))
        assertEquals("https://duckduckgo.com/?q=notas+de+f%C3%ADsica", WebAddress.resolve("notas de física"))
        for (url in listOf("http://example.com", "file:///secret.html", "content://provider/a", "javascript:alert(1)",
            "https://user:password@example.com", "https://d123.hypermarker.invalid/index.html", "https://hypermarker.invalid")) {
            assertFalse(url, WebAddress.permitted(url))
            try { WebAddress.resolve(url); fail(url) } catch (_: IllegalArgumentException) {}
        }
    }
    private fun archive(root: File, html: String, extra: String = ""): File {
        return File(root, "page.mhtml").apply { writeText("""
            MIME-Version: 1.0
            Content-Type: multipart/related;
             boundary="snapshot-boundary"

            --snapshot-boundary
            Content-Type: text/html; charset=utf-8
            Content-Transfer-Encoding: base64
            Content-Location: https://example.com/study/index.html

            ${Base64.getEncoder().encodeToString(html.toByteArray())}
            $extra
            --snapshot-boundary--
        """.trimIndent().replace("            ", "").replace("\n", "\r\n")) }
    }
    @Test fun archiveExtractsLocalStylesImagesCidAndPreservesOriginal() {
        val root = Files.createTempDirectory("snapshot").toFile()
        try {
            val archive = archive(root, """<html><head><base href="https://elsewhere.test/"><meta charset="latin1"><link href="../style.css" rel="stylesheet"></head><body><h1>Física</h1><img src="cid:picture"><img src="../photo.png"><a href="https://example.com/other">Otro tema</a></body></html>""", """
                --snapshot-boundary
                Content-Type: text/css
                Content-Transfer-Encoding: quoted-printable
                Content-Location: https://example.com/style.css

                body{background:url('photo.png');color:=23=31=32=33}
                --snapshot-boundary
                Content-Type: image/png
                Content-Transfer-Encoding: base64
                Content-Location: https://example.com/photo.png
                Content-ID: <picture>

                AQIDBA==
            """.trimIndent())
            val before = archive.readBytes()
            val output = File(root, "original")
            WebSnapshot.extract(archive, output, "https://example.com/study/index.html")
            val html = File(output, "index.html").readText()
            assertTrue(html.contains("Física")); assertFalse(html.contains("<base")); assertFalse(html.contains("latin1"))
            assertTrue(html.contains("href=\"assets/resource-1.css\""))
            assertEquals(2, Regex("src=\"assets/resource-2.png\"").findAll(html).count())
            assertTrue(html.contains("https://example.com/other"))
            val css = File(output, "assets/resource-1.css").readText()
            assertTrue(css.contains("url(\"../assets/resource-2.png\")")); assertTrue(css.contains("color:#123"))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(output, "assets/resource-2.png").readBytes())
            assertArrayEquals(before, archive.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun malformedAndOversizedArchivesRejectedWithoutUsingMimePaths() {
        val root = Files.createTempDirectory("snapshot-invalid").toFile()
        try {
            val archive = archive(root, "<h1>Test</h1>")
            archive.writeText(archive.readText().replace("--snapshot-boundary--", ""))
            try { WebSnapshot.extract(archive, File(root, "out"), "https://example.com"); fail("Truncado aceptado") } catch (_: IllegalArgumentException) {}
            java.io.RandomAccessFile(archive, "rw").use { it.setLength(WebSnapshot.MAX_ARCHIVE + 1) }
            try { WebSnapshot.extract(archive, File(root, "out"), "https://example.com"); fail("Exceso aceptado") } catch (_: IllegalArgumentException) {}
            assertFalse(File(root, "out/index.html").exists())
        } finally { root.deleteRecursively() }
    }
}
