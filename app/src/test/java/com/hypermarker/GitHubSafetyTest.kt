package com.hypermarker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hypermarker.github.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GitHubSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun freshStore(): PreferencesResourceStore {
        context.getSharedPreferences("github_resources", Context.MODE_PRIVATE).edit().clear().commit()
        return PreferencesResourceStore(context)
    }
    @Test fun corruptedBudgetSurvivesRestartAndRepairCannotReleaseBurst() {
        val store = freshStore()
        context.getSharedPreferences("github_resources", Context.MODE_PRIVATE).edit().putInt("state", 123).commit()
        assertEquals(Long.MAX_VALUE, ResourceGovernor(store).snapshot().blockedUntil)
        context.getSharedPreferences("github_resources", Context.MODE_PRIVATE).edit().putString("state", "{broken").commit()
        val now = 1_000_000L
        var governor = ResourceGovernor(store, { now }, elapsed = { now })
        assertEquals(Long.MAX_VALUE, governor.snapshot().blockedUntil)
        try { governor.reserveRequest(); fail("La corrupción liberó red") } catch (_: ResourceBlocked) {}
        governor.repairCorruptState()
        governor = ResourceGovernor(PreferencesResourceStore(context), { now }, elapsed = { now })
        governor.setPaused(false)
        try { governor.reserveRequest(); fail("Reparar liberó presupuesto desconocido") } catch (_: ResourceBlocked) {}
        assertEquals(now + 86_400_000, governor.snapshot().blockedUntil)
    }
    @Test fun aggregateExportRoundTripsWithoutPrivateNamesOrCredential() {
        val store = freshStore(); var now = 1_000_000L
        val governor = ResourceGovernor(store, { now }, elapsed = { now })
        for (day in 0..29) {
            repeat(20) {
                governor.reserveRequest("private-owner/private-repo/secret-path", authenticated = true)
                governor.reserveBytes(8); governor.recordResponse(if (it % 2 == 0) 304 else 200, bytesOut = 2)
            }
            if (day != 29) now += 86_400_000
        }
        val state = ResourceGovernor(PreferencesResourceStore(context), { now }, elapsed = { now }).snapshot()
        assertEquals(500, state.events.size)
        assertEquals(600, state.totals(now, 30L * 86_400_000).requests)
        assertEquals(4800, state.totals(now, 30L * 86_400_000).bytesIn)
        assertEquals(1200, state.totals(now, 30L * 86_400_000).bytesOut)
        assertEquals(20, state.totals(now, 3_600_000).requests)
        val export = state.safeExport(now)
        assertFalse(export.contains("private-owner")); assertFalse(export.contains("secret-path"))
        assertTrue(export.contains("Fuente 1")); assertTrue(export.contains("600 solicitudes"))
    }
    @Test fun authenticationSuspensionPersistsAndOnlySuccessfulProbeClearsIt() = runBlocking {
        val store = freshStore(); val governor = ResourceGovernor(store)
        var status = 401; var calls = 0
        val api = GitHubApiClient(governor, object : GitHubTransport {
            override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
                calls++; return GitHubResponse(status, emptyMap(), ByteArray(0))
            }
        })
        try { api.get("/user", "synthetic-credential"); fail() } catch (_: GitHubApiException) {}
        assertTrue(ResourceGovernor(PreferencesResourceStore(context)).snapshot().authSuspended)
        try { api.get("/user", "synthetic-credential"); fail() } catch (_: ResourceBlocked) {}
        assertEquals(1, calls)
        status = 200; api.get("/user", "replacement", authProbe = true)
        assertFalse(governor.snapshot().authSuspended)
    }
    @Test fun paginatedReposAndBranchesFollowLinksAndRejectForeignPagination() = runBlocking {
        val governor = ResourceGovernor(freshStore())
        val calls = mutableListOf<String>(); var foreign = false
        val api = GitHubApiClient(governor, object : GitHubTransport {
            override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
                calls += path
                val page = path.substringAfter("page=100&page=", "").substringBefore('&').toInt()
                val route = path.substringBefore('?')
                val link = if (page == 5) emptyMap() else mapOf("Link" to "<https://${if (foreign) "evil.example" else "api.github.com"}$route?per_page=100&page=${page+1}>; rel=\"next\"")
                val body = if (route == "/user/repos") "[{\"id\":$page,\"owner\":{\"login\":\"owner\"},\"name\":\"repo$page\",\"default_branch\":\"main\"}]" else "[{\"name\":\"feature/pr-$page\"}]"
                onBytes(body.length); return GitHubResponse(200, link, body.toByteArray())
            }
        })
        val browser = GitHubBrowserRepository(api) { "synthetic" }
        var page: Int? = 1; val names = mutableListOf<String>()
        while (page != null) { val result = browser.repositoryPage(page); names += result.items.map { it.name }; page = result.nextPage }
        assertEquals(5, names.size)
        assertEquals(2, browser.branchPage("owner", "repo").nextPage)
        assertEquals("feature/pr-2", browser.branchPage("owner", "repo", 2).items.single().name)
        foreign = true
        try { browser.repositoryPage(); fail("Aceptó redirección de paginación") } catch (_: IllegalArgumentException) {}
        assertTrue(calls.all { it.startsWith("/user/repos") || it.startsWith("/repos/owner/repo/branches") })
    }
}
