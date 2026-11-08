package com.hypermarker

import com.hypermarker.github.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GitHubResourceTest {
    private class MemoryStore : ResourceStateStore {
        var value = ResourceSnapshot(0, 0, 0, emptyList(), 0, 0)
        override fun load() = value
        override fun save(value: ResourceSnapshot) { this.value = value }
    }
    @Test fun trafficHasNoLocalQuotaEvenAfterReopen() {
        val store = MemoryStore()
        val now = 1_000_000L
        val first = ResourceGovernor(store, { now }, elapsed = { now })
        first.beginRun()
        repeat(101) {
            first.reserveRequest("folder")
            first.reserveBytes(2L * 1024 * 1024)
            first.recordResponse(200)
        }
        first.endRun()
        val reopened = ResourceGovernor(store, { now }, elapsed = { now })
        reopened.beginRun()
        repeat(500) { reopened.reserveRequest("folder"); reopened.recordResponse(200) }
        assertEquals(601, reopened.snapshot().totals(now, 30L * 86_400_000).requests)
        assertEquals(202L * 1024 * 1024, reopened.snapshot().bytesDay)
        assertEquals(0, reopened.snapshot().blockedOperations)
        reopened.endRun()
    }

    @Test fun retryAfterAndClockRollbackFailClosed() = runBlocking {
        var now = 1_000_000L
        val store = MemoryStore()
        val governor = ResourceGovernor(store, { now }, elapsed = { now })
        var calls = 0
        val transport = object : GitHubTransport {
            override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
                calls++
                onBytes(4)
                return GitHubResponse(429, mapOf("Retry-After" to "120"), "slow".toByteArray())
            }
        }
        val api = GitHubApiClient(governor, transport, { now })
        try { api.get("/test", null); fail("Se aceptó 429") } catch (_: GitHubApiException) {}
        assertEquals(1, calls)
        assertEquals(1, governor.snapshot().requestsHour)
        assertEquals(4, governor.snapshot().bytesDay)
        try { api.get("/test", null); fail("Se reintentó antes de Retry-After") } catch (_: ResourceBlocked) {}
        assertEquals(1, calls)
        now -= 120_000
        try { api.get("/test", null); fail("El retroceso de reloj liberó el presupuesto") } catch (_: ResourceBlocked) {}
        assertEquals(1, calls)
    }
    @Test fun forwardClockChangeCannotReleaseBudgetAfterReopen() {
        val store = MemoryStore(); var wall = 1_000_000L; var elapsed = 1_000L
        ResourceGovernor(store, { wall }, elapsed = { elapsed }).reserveRequest()
        wall += 3_600_000; elapsed += 1_000
        val reopened = ResourceGovernor(store, { wall }, elapsed = { elapsed })
        try { reopened.reserveRequest(); fail("Adelantar reloj liberó la cuota") } catch (_: ResourceBlocked) {}
        wall = 1_001_000
        reopened.reserveRequest()
        assertEquals(2, reopened.snapshot().requestsHour)
    }
    @Test fun partialFailedStreamIsMeasuredWithoutExhaustingTrafficQuota() = runBlocking {
        val governor = ResourceGovernor(MemoryStore(), limits = ResourceLimits(bytesPerRun = 5), elapsed = { 1_000L })
        var calls = 0
        val api = GitHubApiClient(governor, object : GitHubTransport {
            override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
                calls++
                if (calls == 1) { onBytes(2); throw java.io.IOException("synthetic stream failure") }
                assertEquals(7L, maxBytes); onBytes(3)
                return GitHubResponse(200, emptyMap(), ByteArray(3))
            }
        })
        try { api.get("/test", null, maxBytes = 7); fail() } catch (_: java.io.IOException) {}
        assertEquals(2, governor.snapshot().bytesDay)
        api.get("/test", null, maxBytes = 7)
        api.get("/test", null, maxBytes = 7)
        assertEquals(3, calls); assertEquals(8, governor.snapshot().bytesDay)
        assertEquals(3, governor.snapshot().requestsDay)
    }

    @Test fun archiveRedirectAllowsOnlyGithubArchiveHost() {
        assertEquals("codeload.github.com", archiveRedirect("https://codeload.github.com/owner/repo/zip/abc?token=synthetic").host)
        for (url in listOf("http://codeload.github.com/repo", "https://evil.example/repo", "https://codeload.github.com.evil.example/repo", "https://user@codeload.github.com/repo", "https://codeload.github.com:444/repo")) {
            try { archiveRedirect(url); fail("Aceptó redirección insegura") } catch (_: IllegalArgumentException) {}
        }
    }
}
