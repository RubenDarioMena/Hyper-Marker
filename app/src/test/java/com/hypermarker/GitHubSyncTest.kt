package com.hypermarker

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.github.*
import com.hypermarker.storage.Annotation
import com.hypermarker.storage.LibraryDatabase
import com.hypermarker.storage.LibraryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GitHubSyncTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private class Store : ResourceStateStore {
        var state = ResourceSnapshot(0, 0, 0, emptyList(), 0, 0)
        override fun load() = state
        override fun save(value: ResourceSnapshot) { state = value }
    }
    private class FakeTransport : GitHubTransport {
        val responses = mutableMapOf<String, GitHubResponse>()
        val calls = mutableListOf<String>()
        override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
            calls += path
            val response = responses[path] ?: error("Unexpected path $path")
            if (etag != null && etag == response.header("ETag")) return GitHubResponse(304, response.headers, ByteArray(0))
            onBytes(response.body.size)
            return response
        }
        fun json(path: String, value: JSONObject) { responses[path] = GitHubResponse(200, emptyMap(), value.toString().toByteArray()) }
    }
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(context, db)
    }
    @After fun tearDown() { db.close() }

    @Test fun completeSnapshotKeepsIdsAnnotationsAndOfflineCopyOnFailure() = runBlocking {
        val transport = FakeTransport()
        var budgetClock = System.currentTimeMillis()
        val governor = ResourceGovernor(Store(), { budgetClock }, elapsed = { budgetClock })
        val api = GitHubApiClient(governor, transport)
        val engine = GitHubSyncEngine(repo, api, GitHubAuthStore(context), governor)
        val source = engine.add(GitHubRepo(123, "owner", "repo", "main", false), "main", "docs", null)
        val base = "/repos/owner/repo"
        val commit1 = "1".repeat(40); val commit2 = "2".repeat(40)
        val tree1 = "3".repeat(40); val tree2 = "4".repeat(40)
        val initial = mapOf("readme.md" to "# Uno", "index.html" to "<link rel=\"stylesheet\" href=\"style.css\"><h1>Uno</h1>", "style.css" to "h1{color:green}")
        fun configure(commit: String, tree: String, files: Map<String, String>) {
            transport.responses["$base/git/ref/heads/main"] = GitHubResponse(200, mapOf("ETag" to commit),
                JSONObject().put("object", JSONObject().put("sha", commit)).toString().toByteArray())
            val root = if (commit == commit1) "5".repeat(40) else "6".repeat(40)
            transport.json("$base/git/commits/$commit", JSONObject().put("tree", JSONObject().put("sha", root)))
            transport.json("$base/git/trees/$root", JSONObject().put("truncated", false).put("tree", JSONArray().put(
                JSONObject().put("path", "docs").put("type", "tree").put("mode", "040000").put("sha", tree))))
            val entries = JSONArray()
            files.forEach { (path, text) ->
                val bytes = text.toByteArray()
                val sha = sha(bytes)
                entries.put(JSONObject().put("path", path).put("type", "blob").put("mode", "100644").put("sha", sha).put("size", bytes.size))
                transport.json("$base/git/blobs/$sha", JSONObject().put("encoding", "base64").put("sha", sha)
                    .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            transport.json("$base/git/trees/$tree", JSONObject().put("truncated", false).put("tree", entries))
        }
        configure(commit1, tree1, initial)
        assertTrue(engine.sync(source.id, manual = true).contains("Actualizado"))
        val html = db.dao().document(db.dao().remoteFolder(source.id)!!.htmlDocumentId!!)!!
        val markdown = db.dao().documents().single { it.type == "REMOTE_MARKDOWN" }
        assertEquals("# Uno", repo.readMarkdown(markdown.id))
        assertTrue(repo.original(html.id).resolve("style.css").isFile)
        repo.replaceAnnotations(html.id, listOf(Annotation("pin-1", html.id, "PIN", "/index.html", "{}")))
        val blobCalls = transport.calls.count { "/git/blobs/" in it }
        db.dao().put(db.dao().remoteFolder(source.id)!!.copy(lastAttempt = 0, lastCheck = 0))
        engine.sync(source.id)
        assertEquals(blobCalls, transport.calls.count { "/git/blobs/" in it })
        configure(commit2, tree2, initial + ("readme.md" to "# Dos"))
        db.dao().put(db.dao().remoteFolder(source.id)!!.copy(lastCheck = 0))
        engine.sync(source.id)
        assertEquals("# Dos", repo.readMarkdown(markdown.id))
        assertEquals(html.id, db.dao().remoteFolder(source.id)!!.htmlDocumentId)
        assertEquals(1, db.dao().annotations(html.id).size)
        budgetClock += 3_600_001 // Escenarios de fallo en otra ventana de presupuesto público.
        val good = db.dao().remoteFolder(source.id)!!.latestCommitSha
        transport.responses["$base/git/ref/heads/main"] = GitHubResponse(500, emptyMap(), ByteArray(0))
        db.dao().put(db.dao().remoteFolder(source.id)!!.copy(lastCheck = 0))
        try { engine.sync(source.id); fail("Se publicó una descarga fallida") } catch (_: GitHubApiException) {}
        assertEquals(good, db.dao().remoteFolder(source.id)!!.latestCommitSha)
        assertEquals("# Dos", repo.readMarkdown(markdown.id))
        val commit3 = "7".repeat(40)
        configure(commit3, "8".repeat(40), initial + ("readme.md" to "# Tres"))
        val failedBlob = "$base/git/blobs/${sha("# Tres".toByteArray())}"
        transport.responses[failedBlob] = GitHubResponse(500, emptyMap(), ByteArray(0))
        db.dao().put(db.dao().remoteFolder(source.id)!!.copy(lastCheck = 0, nextRetryAt = 0))
        try { engine.sync(source.id); fail("Se publicó una descarga parcial") } catch (_: GitHubApiException) {}
        assertEquals(good, db.dao().remoteFolder(source.id)!!.latestCommitSha)
        assertEquals("# Dos", repo.readMarkdown(markdown.id))
        assertTrue(repo.original(html.id).resolve("style.css").isFile)
        configure("9".repeat(40), "a".repeat(40), initial - "readme.md")
        db.dao().put(db.dao().remoteFolder(source.id)!!.copy(lastCheck = 0, nextRetryAt = 0))
        engine.sync(source.id)
        assertEquals("RETIRED", db.dao().remoteEntriesForDocument(markdown.id).single().state)
        assertEquals("# Dos", repo.readMarkdown(markdown.id))
        repo.deleteRetiredRemoteDocument(markdown.id)
        assertNull(db.dao().document(markdown.id))
        assertEquals(1, db.dao().annotations(html.id).size)
    }
    private data class Harness(val engine: GitHubSyncEngine, val transport: FakeTransport, val source: com.hypermarker.storage.RemoteFolder)
    private suspend fun harness(available: () -> Long = { Long.MAX_VALUE }, storage: Long = 500L * 1024 * 1024,
        beforeCommit: suspend () -> Unit = {}): Harness {
        val transport = FakeTransport()
        val governor = ResourceGovernor(Store(), limits = ResourceLimits(requestsPerHour = 1000, requestsPerDay = 2000))
        val engine = GitHubSyncEngine(repo, GitHubApiClient(governor, transport), GitHubAuthStore(context), governor,
            availableBytes = available, storageLimit = storage, beforeCommit = beforeCommit)
        return Harness(engine, transport, engine.add(GitHubRepo(123, "owner", "repo", "main", false), "main", "", null))
    }
    private fun configure(h: Harness, commit: String = "1".repeat(40), files: Map<String, String> = mapOf("readme.md" to "# Stable"),
        entries: JSONArray? = null) {
        val base = "/repos/owner/repo"
        h.transport.json("$base/git/ref/heads/main", JSONObject().put("object", JSONObject().put("sha", commit)))
        h.transport.json("$base/git/commits/$commit", JSONObject().put("tree", JSONObject().put("sha", "2".repeat(40))))
        val tree = JSONArray()
        files.forEach { (path, text) ->
            val bytes = text.toByteArray(); val sha = sha(bytes)
            tree.put(JSONObject().put("path", path).put("type", "blob").put("mode", "100644").put("sha", sha).put("size", bytes.size))
            h.transport.json("$base/git/blobs/$sha", JSONObject().put("encoding", "base64").put("sha", sha)
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }
        h.transport.json("$base/git/trees/${"2".repeat(40)}", JSONObject().put("truncated", false).put("tree", entries ?: tree))
    }
    private suspend fun resetAttempt(h: Harness) { db.dao().put(db.dao().remoteFolder(h.source.id)!!.copy(lastCheck = 0, nextRetryAt = 0)) }

    @Test fun hostileRemotePathsModesAndCollisionsNeverPublish() = runBlocking {
        val h = harness()
        for ((path, mode, type) in listOf(Triple("../escape.md", "100644", "blob"), Triple("evil.md", "120000", "blob"),
            Triple("module", "160000", "commit"), Triple("/absolute.md", "100644", "blob"), Triple("percent%2e.md", "100644", "blob"))) {
            configure(h, entries = JSONArray().put(JSONObject().put("path", path).put("mode", mode).put("type", type).put("sha", "a".repeat(40)).put("size", 1)))
            resetAttempt(h)
            try { h.engine.sync(h.source.id); fail("Aceptó $path/$mode") } catch (_: IllegalArgumentException) {}
            assertNull(db.dao().remoteFolder(h.source.id)!!.latestCommitSha)
        }
        configure(h, files = mapOf("A.md" to "A", "a.md" to "a")); resetAttempt(h)
        try { h.engine.sync(h.source.id); fail("Aceptó colisión por mayúsculas") } catch (_: IllegalArgumentException) {}
        assertTrue(db.dao().documents().isEmpty())
        assertFalse(java.io.File(context.filesDir, "remote-sync/${h.source.id}").exists())
    }
    @Test fun thousandFilesStopBeforeDownloadingAndDiskFailureKeepsSnapshot() = runBlocking {
        var space = Long.MAX_VALUE
        val h = harness(available = { space })
        val entries = JSONArray()
        repeat(10_001) { entries.put(JSONObject().put("path", "file$it.md").put("type", "blob").put("mode", "100644")
            .put("sha", "a".repeat(40)).put("size", 1)) }
        configure(h, entries = entries)
        try { h.engine.sync(h.source.id); fail("No limitó el árbol") } catch (_: IllegalArgumentException) {}
        assertTrue(h.transport.calls.none { "/blobs/" in it })
        configure(h); resetAttempt(h); h.engine.sync(h.source.id)
        val doc = db.dao().documents().single(); val oldCommit = db.dao().remoteFolder(h.source.id)!!.latestCommitSha
        configure(h, commit = "3".repeat(40), files = mapOf("readme.md" to "# Changed")); space = 0; resetAttempt(h)
        try { h.engine.sync(h.source.id); fail("Ignoró disco lleno") } catch (_: IllegalArgumentException) {}
        assertEquals(oldCommit, db.dao().remoteFolder(h.source.id)!!.latestCommitSha)
        assertEquals("# Stable", repo.readMarkdown(doc.id))
    }
    @Test fun cancellationAndInterruptedInstallationRecoverWithoutLosingLocalData() = runBlocking {
        var cancel = false
        val h = harness(beforeCommit = { if (cancel) throw kotlinx.coroutines.CancellationException("synthetic cancellation") })
        configure(h); h.engine.sync(h.source.id)
        val doc = db.dao().documents().single()
        val flow = repo.createFlow(null, "local flow")
        val local = repo.createMarkdown(null, "local", "local text")
        cancel = true
        configure(h, commit = "3".repeat(40), files = mapOf("readme.md" to "changed")); resetAttempt(h)
        try { h.engine.sync(h.source.id); fail("Ignoró cancelación") } catch (_: kotlinx.coroutines.CancellationException) {}
        assertEquals("# Stable", repo.readMarkdown(doc.id))
        assertEquals("local text", repo.readMarkdown(local.id))
        assertNotNull(db.dao().document(flow.id))
        val orphan = java.io.File(repo.directory(doc.id), "versions/${"9".repeat(40)}/source.md").apply { parentFile!!.mkdirs(); writeText("orphan") }
        val stage = java.io.File(context.filesDir, "remote-sync/orphan/file").apply { parentFile!!.mkdirs(); writeText("staging") }
        LibraryRepository(context, db).recover()
        assertFalse(orphan.exists()); assertFalse(stage.exists())
        assertEquals("# Stable", repo.readMarkdown(doc.id))
        cancel = false; resetAttempt(h); h.engine.sync(h.source.id)
        assertEquals("changed", repo.readMarkdown(doc.id))
    }
    @Test fun authenticationErrorsPreserveSnapshotAndBranchesHaveSeparateIdentity() = runBlocking {
        val h = harness(); configure(h); h.engine.sync(h.source.id)
        val doc = db.dao().documents().single()
        val other = h.engine.add(GitHubRepo(123, "owner", "repo", "main", false), "feature/pr-123", "", null)
        assertNotEquals(h.source.id, other.id); assertNotEquals(h.source.folderId, other.folderId)
        for (status in listOf(401, 403, 404, 429)) {
            resetAttempt(h)
            h.transport.responses["/repos/owner/repo/git/ref/heads/main"] = GitHubResponse(status, emptyMap(), ByteArray(0))
            try { h.engine.sync(h.source.id); fail("Aceptó HTTP $status") } catch (_: GitHubApiException) {}
            assertEquals("# Stable", repo.readMarkdown(doc.id))
            assertEquals("1".repeat(40), db.dao().remoteFolder(h.source.id)!!.latestCommitSha)
        }
    }
    @Test fun concurrentOpeningsCoalesceAndUnlinkWaitsForPublication() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val h = harness(beforeCommit = { entered.complete(Unit); release.await() })
        configure(h)
        val first = async { h.engine.sync(h.source.id) }
        entered.await()
        val calls = h.transport.calls.size
        repeat(100) { assertTrue(h.engine.sync(h.source.id).contains("en curso")) }
        assertEquals(calls, h.transport.calls.size)
        val unlink = async { repo.unlinkRemoteFolder(h.source.id) }
        yield(); assertFalse(unlink.isCompleted)
        release.complete(Unit); first.await(); unlink.await()
        assertNull(db.dao().remoteFolder(h.source.id))
        assertEquals("MARKDOWN", db.dao().documents().single().type)
        assertEquals("# Stable", repo.readMarkdown(db.dao().documents().single().id))
    }
    @Test fun restartRepairsPointerAfterRoomCommitAndRetainsRetiredHtml() = runBlocking {
        val transport = FakeTransport(); val governor = ResourceGovernor(Store())
        var interrupt = false
        val engine = GitHubSyncEngine(repo, GitHubApiClient(governor, transport), GitHubAuthStore(context), governor,
            afterCommit = { if (interrupt) throw kotlinx.coroutines.CancellationException("process interruption") })
        val source = engine.add(GitHubRepo(123, "owner", "repo", "main", false), "main", "", null)
        val h = Harness(engine, transport, source)
        configure(h, files = mapOf("index.html" to "<h1>One</h1>")); engine.sync(source.id)
        val htmlId = db.dao().remoteFolder(source.id)!!.htmlDocumentId!!
        repo.replaceAnnotations(htmlId, listOf(Annotation("note", htmlId, "PIN", "/index.html", "{}")))
        configure(h, commit = "3".repeat(40), files = mapOf("index.html" to "<h1>Two</h1>")); resetAttempt(h); interrupt = true
        try { engine.sync(source.id); fail() } catch (_: kotlinx.coroutines.CancellationException) {}
        LibraryRepository(context, db).recover()
        assertEquals("<h1>Two</h1>", repo.original(htmlId).resolve("index.html").readText())
        interrupt = false
        configure(h, commit = "4".repeat(40), files = emptyMap()); resetAttempt(h); engine.sync(source.id)
        assertTrue(db.dao().remoteEntriesForDocument(htmlId).all { it.state == "RETIRED" })
        repo.unlinkRemoteFolder(source.id)
        assertEquals("<h1>Two</h1>", repo.original(htmlId).resolve("index.html").readText())
        assertEquals(1, db.dao().annotations(htmlId).size)
        assertFalse(java.io.File(repo.directory(htmlId), "remote-current").exists())
    }
    @Test fun copyingRemoteMarkdownWaitsForSyncAndCreatesIndependentSource() = runBlocking {
        var hold = false
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val h = harness(beforeCommit = { if (hold) { entered.complete(Unit); release.await() } })
        configure(h); h.engine.sync(h.source.id)
        val original = db.dao().documents().single()
        hold = true
        configure(h, commit = "3".repeat(40), files = mapOf("readme.md" to "# Updated")); resetAttempt(h)
        val sync = async { h.engine.sync(h.source.id) }
        entered.await()
        val copy = async { repo.copyRemoteMarkdown(original.id) }
        yield(); assertFalse(copy.isCompleted)
        release.complete(Unit); sync.await()
        val local = copy.await()
        assertNotEquals(original.id, local.id); assertEquals("MARKDOWN", local.type)
        assertEquals("# Updated", repo.readMarkdown(local.id))
        repo.saveMarkdown(local.id, "Local edit")
        assertEquals("# Updated", repo.readMarkdown(original.id))
    }
    private fun sha(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray())
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
