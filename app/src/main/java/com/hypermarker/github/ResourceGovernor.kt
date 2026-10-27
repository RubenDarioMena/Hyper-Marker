package com.hypermarker.github

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Límite local, independiente de las cuotas que informa GitHub. */
data class ResourceLimits(
    val requestsPerHour: Int = 60,
    val requestsPerDay: Int = 500,
    val requestsPerRun: Int = 30,
    val bytesPerRun: Long = 30L * 1024 * 1024,
    val bytesPerDay: Long = 150L * 1024 * 1024,
    val fileBytes: Long = 10L * 1024 * 1024,
)

data class ResourceEvent(val at: Long, val status: Int, val bytesIn: Long, val bytesOut: Long, val folderId: String?,
    val durationMs: Long = 0, val githubRemaining: Long? = null, val githubLimit: Long? = null,
    val githubReset: Long? = null)
data class DailyResourceAggregate(val day: Long, val requests: Int, val bytesIn: Long,
    val statuses: Map<Int, Int>, val folderId: String? = null, val bytesOut: Long = 0, val durationMs: Long = 0)
data class ResourceSnapshot(val requestsHour: Int, val requestsDay: Int, val bytesDay: Long,
    val events: List<ResourceEvent>, val blockedUntil: Long, val lastClock: Long,
    val byStatus: Map<Int, Int> = emptyMap(), val bytes30Days: Long = 0,
    val daily: List<DailyResourceAggregate> = emptyList(), val pausedByUser: Boolean = false,
    val blockedOperations: Long = 0, val runs: List<SyncResourceRun> = emptyList(),
    val authSuspended: Boolean = false, val lastElapsed: Long = 0, val bootCount: Int = -1)
data class SyncResourceRun(val at: Long, val folderId: String, val durationMs: Long,
    val cpuMs: Long, val outcome: String, val reusedBytes: Long, val files: Int,
    val background: Boolean, val batteryPercent: Int?, val charging: Boolean?)
data class ResourceTotals(val requests: Int, val bytesIn: Long, val bytesOut: Long,
    val durationMs: Long, val statuses: Map<Int, Int>)

fun ResourceSnapshot.totals(now: Long, span: Long, folderId: String? = null): ResourceTotals {
    val recent = events.filter { it.at > now - span && (folderId == null || it.folderId == folderId) }
    val folded = if (span >= 30L * 86_400_000) daily.filter { folderId == null || it.folderId == folderId } else emptyList()
    val statuses = mutableMapOf<Int, Int>()
    recent.forEach { statuses[it.status] = (statuses[it.status] ?: 0) + 1 }
    folded.forEach { d -> d.statuses.forEach { (status, count) -> statuses[status] = (statuses[status] ?: 0) + count } }
    return ResourceTotals(recent.size + folded.sumOf { it.requests }, recent.sumOf { it.bytesIn } + folded.sumOf { it.bytesIn },
        recent.sumOf { it.bytesOut } + folded.sumOf { it.bytesOut }, recent.sumOf { it.durationMs } + folded.sumOf { it.durationMs }, statuses)
}

/** Agregados sin nombres de repositorio, rutas, credenciales ni cuerpos HTTP. */
fun ResourceSnapshot.safeExport(now: Long): String {
    val sources = (events.mapNotNull { it.folderId } + daily.mapNotNull { it.folderId }).distinct()
    val result = StringBuilder("Hyper-Marker · consumo HTTP local\nNo incluye overhead TLS; ahorro de caché estimado. CPU/energía de agentes: N/A.\n")
    for (source in listOf<String?>(null) + sources) {
        result.append(if (source == null) "Total\n" else "Fuente ${sources.indexOf(source) + 1}\n")
        for ((label, span) in listOf("1 h" to 3_600_000L, "24 h" to 86_400_000L, "30 días" to 30L * 86_400_000)) {
            val t = totals(now, span, source)
            result.append("$label: ${t.requests} solicitudes; ${t.bytesIn} B recibidos; ${t.bytesOut} B enviados; ${t.durationMs} ms HTTP; estados ${t.statuses.toSortedMap()}\n")
        }
    }
    result.append("Bloqueadas: $blockedOperations; pausa manual: $pausedByUser; credencial suspendida: $authSuspended\n")
    result.append("Ejecuciones recientes: ${runs.size}; ahorro estimado: ${runs.sumOf { it.reusedBytes }} B; CPU de sync: ${runs.sumOf { it.cpuMs }} ms\n")
    return result.toString()
}

/** Agregados locales de lo observado; valores del proveedor son información, no presupuesto propio. */
class ResourceMeter {
    fun summarize(state: ResourceSnapshot, now: Long): ResourceSnapshot {
        val hour = state.events.filter { it.at > now - 3_600_000 }
        val day = state.events.filter { it.at > now - 86_400_000 }
        return state.copy(requestsHour = hour.size, requestsDay = day.size,
            bytesDay = day.sumOf { it.bytesIn },
            byStatus = day.groupingBy { it.status }.eachCount(),
            bytes30Days = state.events.sumOf { it.bytesIn } + state.daily.sumOf { it.bytesIn })
    }
}

interface ResourceStateStore {
    fun bootCount(): Int = -1
    fun load(): ResourceSnapshot
    fun save(value: ResourceSnapshot)
}

class PreferencesResourceStore(private val context: Context) : ResourceStateStore {
    private val prefs = context.getSharedPreferences("github_resources", Context.MODE_PRIVATE)
    override fun bootCount(): Int = android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1)
    override fun load(): ResourceSnapshot {
        return try {
            val raw = prefs.getString("state", null) ?: return ResourceSnapshot(0, 0, 0, emptyList(), 0, 0)
            val json = JSONObject(raw)
            val array = json.getJSONArray("events")
            val events = (0 until array.length()).map { i ->
                val event = array.getJSONObject(i)
                ResourceEvent(event.getLong("at"), event.getInt("status"), event.getLong("in"),
                    event.getLong("out"), event.optString("folder").takeIf { it.isNotEmpty() },
                    event.optLong("duration"), event.optLong("remaining", -1).takeIf { it >= 0 },
                    event.optLong("limit", -1).takeIf { it >= 0 }, event.optLong("reset", -1).takeIf { it >= 0 })
            }
            val dailyArray = json.optJSONArray("daily") ?: JSONArray()
            val daily = (0 until dailyArray.length()).map { i ->
                val value = dailyArray.getJSONObject(i)
                val statuses = value.getJSONObject("statuses")
                DailyResourceAggregate(value.getLong("day"), value.getInt("requests"), value.getLong("in"),
                    statuses.keys().asSequence().associate { it.toInt() to statuses.getInt(it) },
                    value.optString("folder").takeIf { it.isNotEmpty() }, value.optLong("out"), value.optLong("duration"))
            }
            ResourceSnapshot(0, 0, 0, events, json.getLong("blocked"), json.getLong("clock"),
                lastElapsed = json.optLong("elapsed"), bootCount = json.optInt("boot", -1),
                daily = daily, pausedByUser = json.optBoolean("paused", false),
                blockedOperations = json.optLong("blockedOperations", 0), authSuspended = json.optBoolean("authSuspended"),
                runs = (json.optJSONArray("runs") ?: JSONArray()).let { a -> (0 until a.length()).map { i ->
                    val v = a.getJSONObject(i)
                    SyncResourceRun(v.getLong("at"), v.getString("folder"), v.getLong("duration"), v.optLong("cpu"),
                        v.getString("outcome"), v.optLong("reused"), v.optInt("files"), v.optBoolean("background"),
                        v.optInt("battery", -1).takeIf { it >= 0 }, v.optInt("charging", -1).takeIf { it >= 0 }?.let { it == 1 })
                } })
        } catch (_: Exception) {
            // Datos corruptos: impedir solicitudes automáticas hasta que el usuario repare los ajustes.
            ResourceSnapshot(0, 0, 0, emptyList(), Long.MAX_VALUE, Long.MAX_VALUE)
        }
    }
    override fun save(value: ResourceSnapshot) {
        val json = JSONObject().put("elapsed", value.lastElapsed).put("boot", value.bootCount).put("blocked", value.blockedUntil).put("clock", value.lastClock)
            .put("paused", value.pausedByUser).put("blockedOperations", value.blockedOperations).put("authSuspended", value.authSuspended)
        val events = JSONArray()
        value.events.forEach { e -> events.put(JSONObject().put("at", e.at).put("status", e.status)
            .put("in", e.bytesIn).put("out", e.bytesOut).put("folder", e.folderId ?: "")
            .put("duration", e.durationMs).put("remaining", e.githubRemaining ?: -1)
            .put("limit", e.githubLimit ?: -1).put("reset", e.githubReset ?: -1)) }
        json.put("events", events)
        val daily = JSONArray()
        value.daily.forEach { aggregate ->
            val statuses = JSONObject()
            aggregate.statuses.forEach { (status, count) -> statuses.put(status.toString(), count) }
            daily.put(JSONObject().put("day", aggregate.day).put("requests", aggregate.requests)
                .put("in", aggregate.bytesIn).put("statuses", statuses).put("folder", aggregate.folderId ?: "")
                .put("out", aggregate.bytesOut).put("duration", aggregate.durationMs))
        }
        json.put("daily", daily)
        json.put("runs", JSONArray().apply { value.runs.forEach { r -> put(JSONObject().put("at", r.at).put("folder", r.folderId)
            .put("duration", r.durationMs).put("cpu", r.cpuMs).put("outcome", r.outcome).put("reused", r.reusedBytes)
            .put("files", r.files).put("background", r.background).put("battery", r.batteryPercent ?: -1)
            .put("charging", r.charging?.let { if (it) 1 else 0 } ?: -1)) } })
        check(prefs.edit().putString("state", json.toString()).commit()) { "No se pudo guardar el presupuesto GitHub" }
    }
}

class ResourceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("github_resource_settings", Context.MODE_PRIVATE)
    fun load(): ResourceLimits = runCatching { ResourceLimits(
        fileBytes = prefs.getInt("file_mib", 10).coerceIn(10, 20).toLong() * 1024 * 1024,
        bytesPerRun = prefs.getInt("run_mib", 30).coerceIn(30, 60).toLong() * 1024 * 1024) }.getOrDefault(ResourceLimits())
    fun save(fileMiB: Int, runMiB: Int): ResourceLimits {
        require(fileMiB in listOf(10, 20) && runMiB in listOf(30, 60))
        check(prefs.edit().putInt("file_mib", fileMiB).putInt("run_mib", runMiB).commit())
        return load()
    }
}

class ResourceBlocked(message: String, val until: Long = 0) : Exception(message)

/** Una instancia por aplicación, compartida por UI y WorkManager. La reserva se guarda antes de abrir la red. */
class ResourceGovernor(
    private val store: ResourceStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
    limits: ResourceLimits = ResourceLimits(),
    private val elapsed: () -> Long = android.os.SystemClock::elapsedRealtime,
) {
    @Volatile var limits = limits; private set
    private val lock = ReentrantLock()
    private val meter = ResourceMeter()
    private var state = store.load()
    private val bootCount = store.bootCount()
    private var runRequests = 0
    private var runBytes = 0L
    private var runActive = false
    fun setPaused(value: Boolean) = lock.withLock { state = state.copy(pausedByUser = value); store.save(state) }
    fun updateLimits(value: ResourceLimits) = lock.withLock {
        require(value.fileBytes <= 20L * 1024 * 1024 && value.bytesPerRun <= 60L * 1024 * 1024)
        limits = value
    }

    fun suspendAuthentication(value: Boolean) = lock.withLock { state = state.copy(authSuspended = value); store.save(state) }
    fun recordRun(value: SyncResourceRun) = lock.withLock {
        state = state.copy(runs = (state.runs + value).filter { it.at > clock() - 30L * 86_400_000 }.takeLast(500)); store.save(state)
    }
    fun repairCorruptState() = lock.withLock {
        require(state.lastClock == Long.MAX_VALUE) { "El presupuesto no está corrupto" }
        val now = clock()
        state = ResourceSnapshot(0, 0, 0, emptyList(), now + 86_400_000, now, pausedByUser = true)
        store.save(state)
    }
    fun beginRun() = lock.withLock {
        checkedNow()
        if (runActive) throw ResourceBlocked("Ya hay una sincronización en curso")
        runActive = true; runRequests = 0; runBytes = 0
    }
    fun endRun() = lock.withLock { runActive = false; runRequests = 0; runBytes = 0 }

    private fun checkedNow(): Long {
        val now = clock()
        if (state.lastClock != 0L && now + 60_000 < state.lastClock)
            throw ResourceBlocked("El reloj retrocedió; revisa fecha y hora antes de sincronizar")
        if (state.lastElapsed > 0 && state.bootCount == bootCount && elapsed() >= state.lastElapsed &&
            kotlin.math.abs((now - state.lastClock) - (elapsed() - state.lastElapsed)) > 5 * 60_000)
            throw ResourceBlocked("El reloj cambió respecto al tiempo del sistema; corrige fecha y hora")
        return now
    }
    private fun block(message: String, until: Long = 0): Nothing {
        state = state.copy(blockedOperations = state.blockedOperations + 1)
        store.save(state)
        throw ResourceBlocked(message, until)
    }
    private fun recent(now: Long, span: Long) = state.events.filter { it.at > now - span }
    private fun foldEvents(events: List<ResourceEvent>, now: Long): List<DailyResourceAggregate> {
        val days = state.daily.associateBy { it.day to it.folderId }.toMutableMap()
        events.forEach { event ->
            val day = event.at / 86_400_000
            val key = day to event.folderId
            val old = days[key] ?: DailyResourceAggregate(day, 0, 0, emptyMap(), event.folderId)
            days[key] = old.copy(requests = old.requests + 1, bytesIn = old.bytesIn + event.bytesIn,
                bytesOut = old.bytesOut + event.bytesOut, durationMs = old.durationMs + event.durationMs,
                statuses = old.statuses + (event.status to (old.statuses[event.status] ?: 0) + 1))
        }
        return days.values.filter { it.day >= now / 86_400_000 - 30 }.sortedBy { it.day }
    }

    fun reserveRequest(folderId: String? = null, authenticated: Boolean = false, probe: Boolean = false) = lock.withLock {
        val now = checkedNow()
        if (authenticated && !probe && state.authSuspended) block("Reconecta GitHub: la credencial fue rechazada")
        if (state.pausedByUser) block("La sincronización GitHub está pausada en Ajustes")
        if (now < state.blockedUntil) block("GitHub está en pausa hasta ${state.blockedUntil}", state.blockedUntil)
        val hour = recent(now, 3_600_000)
        val day = recent(now, 86_400_000)
        if (hour.size >= minOf(limits.requestsPerHour, if (authenticated) 60 else 20) || day.size >= minOf(limits.requestsPerDay, if (authenticated) 500 else 100) || (runActive && runRequests >= limits.requestsPerRun))
            block("Límite local de peticiones alcanzado")
        // status=0 es una reserva; un proceso muerto también consumió presupuesto.
        val combined = state.events + ResourceEvent(now, 0, 0, 0, folderId)
        val kept = combined.filter { it.at > now - 30L * 86_400_000 }.takeLast(500)
        val removed = combined.take(combined.size - kept.size)
        state = state.copy(events = kept, daily = foldEvents(removed, now), lastClock = now, lastElapsed = elapsed(), bootCount = bootCount)
        store.save(state)
        if (runActive) runRequests++
        state.events.lastIndex
    }

    fun remainingBytes(): Long = lock.withLock {
        val daily = recent(checkedNow(), 86_400_000).sumOf { it.bytesIn }
        minOf((limits.bytesPerDay - daily).coerceAtLeast(0),
            if (runActive) (limits.bytesPerRun - runBytes).coerceAtLeast(0) else Long.MAX_VALUE)
    }
    fun reserveBytes(count: Long) = lock.withLock {
        require(count >= 0)
        val now = checkedNow()
        val daily = recent(now, 86_400_000).sumOf { it.bytesIn }
        if (count > limits.bytesPerDay - daily || (runActive && count > limits.bytesPerRun - runBytes))
            block("Límite local de datos alcanzado")
        // Persistir antes de leer el siguiente bloque. El bloque queda contabilizado si el proceso termina.
        val events = state.events.toMutableList()
        val last = events.lastOrNull() ?: throw IllegalStateException("No hay petición reservada")
        events[events.lastIndex] = last.copy(bytesIn = last.bytesIn + count)
        state = state.copy(events = events, lastClock = now, lastElapsed = elapsed(), bootCount = bootCount)
        store.save(state)
        if (runActive) runBytes += count
    }

    fun recordResponse(status: Int, bytesOut: Long = 0, retryAfter: Long = 0,
        headers: Map<String, String> = emptyMap()) = lock.withLock {
        val now = checkedNow()
        val events = state.events.toMutableList()
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.trim()?.toLongOrNull()
        if (events.isNotEmpty()) events[events.lastIndex] = events.last().copy(status = status, bytesOut = bytesOut,
            durationMs = (now - events.last().at).coerceAtLeast(0), githubRemaining = header("X-RateLimit-Remaining"),
            githubLimit = header("X-RateLimit-Limit"), githubReset = header("X-RateLimit-Reset"))
        state = state.copy(events = events, blockedUntil = maxOf(state.blockedUntil, retryAfter), lastClock = now, lastElapsed = elapsed(), bootCount = bootCount)
        store.save(state)
    }

    fun snapshot(): ResourceSnapshot = lock.withLock {
        val now = maxOf(clock(), state.lastClock.takeUnless { it == Long.MAX_VALUE } ?: clock())
        meter.summarize(state.copy(daily = state.daily.filter { it.day >= now / 86_400_000 - 30 }), now)
    }
}
