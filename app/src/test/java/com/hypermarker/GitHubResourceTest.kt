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
    @Test fun requestsBytesAndReopenRemainBudgeted() {
        var now = 1_000_000L
        val store = MemoryStore()
        val limits = ResourceLimits(requestsPerHour = 2, requestsPerDay = 3, requestsPerRun = 2,
            bytesPerRun = 5, bytesPerDay = 5)
        val first = ResourceGovernor(store, { now }, limits, elapsed = { now })
        first.beginRun()
        first.reserveRequest("folder")
        first.reserveBytes(3)
        first.recordResponse(304)
        first.endRun()
        val reopened = ResourceGovernor(store, { now }, limits, elapsed = { now })
        reopened.beginRun()
        reopened.reserveRequest("folder")
        reopened.reserveBytes(2)
        reopened.recordResponse(200)
        assertEquals(2, reopened.snapshot().requestsHour)
        assertEquals(5, reopened.snapshot().bytesDay)
        try { reopened.reserveRequest("folder"); fail("Se ignoró el límite por hora") } catch (_: ResourceBlocked) {}
        try { reopened.reserveBytes(1); fail("Se ignoró el límite diario de bytes") } catch (_: ResourceBlocked) {}
        assertEquals(2, reopened.snapshot().blockedOperations)
        reopened.endRun()
        now += 3_600_001
        val later = ResourceGovernor(store, { now }, limits, elapsed = { now })
        later.reserveRequest("folder")
        assertEquals(3, later.snapshot().requestsDay)
        try { later.reserveRequest("folder"); fail("Se ignoró el límite diario") } catch (_: ResourceBlocked) {}
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
    @Test fun partialFailedStreamCountsAndRemainingBudgetBoundsNextResponse() = runBlocking {
        val governor = ResourceGovernor(MemoryStore(), limits = ResourceLimits(bytesPerDay = 5, bytesPerRun = 5), elapsed = { 1_000L })
        var calls = 0
        val api = GitHubApiClient(governor, object : GitHubTransport {
            override fun get(path: String, token: String?, etag: String?, maxBytes: Long, onBytes: (Int) -> Unit): GitHubResponse {
                calls++
                if (calls == 1) { onBytes(2); throw java.io.IOException("synthetic stream failure") }
                assertEquals(3, maxBytes); onBytes(3)
                return GitHubResponse(200, emptyMap(), ByteArray(3))
            }
        })
        try { api.get("/test", null); fail() } catch (_: java.io.IOException) {}
        assertEquals(2, governor.snapshot().bytesDay)
        api.get("/test", null)
        try { api.get("/test", null); fail("Abrió red sin presupuesto de bytes") } catch (_: ResourceBlocked) {}
        assertEquals(2, calls); assertEquals(5, governor.snapshot().bytesDay)
        assertEquals(2, governor.snapshot().requestsDay)
    }

}
