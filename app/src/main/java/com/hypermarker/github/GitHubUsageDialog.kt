package com.hypermarker.github

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hypermarker.library.LibraryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun GitHubUsageDialog(model: LibraryViewModel, close: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var disk by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val folders by model.remoteFolders.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val snapshot = remember(revision) { model.githubUsage() }
    val limits = model.githubLimits()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { model.exportGitHubUsage(it) } }
    LaunchedEffect(revision, folders) { disk = model.githubDiskUsage() }
    fun date(at: Long) = if (at == 0L) "pendiente" else DateFormat.getDateTimeInstance().format(Date(at))
    fun bytes(n: Long) = when {
        n < 1024 -> "$n B"
        n < 1048576 -> "%.1f KiB".format(n / 1024.0)
        else -> "%.1f MiB".format(n / 1048576.0)
    }
    AlertDialog(onDismissRequest = close, title = { Text("GitHub · Sincronización y consumo") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alcance", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { selected = null }) { Text(if (selected == null) "✓ Total" else "Total") }
            folders.forEach { folder ->
                TextButton(onClick = { selected = folder.id }) { Text("${if (selected == folder.id) "✓ " else ""}${folder.owner}/${folder.repo} · ${folder.branch} / ${folder.repoPath.ifBlank { "raíz" }}") }
            }
            val now = System.currentTimeMillis()
            for ((label, span) in listOf("Última hora" to 3_600_000L, "Últimas 24 h" to 86_400_000L, "Últimos 30 días" to 30L * 86_400_000)) {
                val totals = snapshot.totals(now, span, selected)
                Text("$label: ${totals.requests} peticiones · ${bytes(totals.bytesIn)} recibidos · ${totals.bytesOut} B enviados · ${totals.durationMs} ms HTTP")
                Text("Estados: ${totals.statuses.toSortedMap().entries.joinToString { "${if (it.key == -1) "red" else if (it.key == 0) "interrumpida" else it.key.toString()}: ${it.value}" }.ifBlank { "sin peticiones" }}", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Sin token: máximo 20 peticiones/h y 100 por 24 h.")
            Text("Presupuesto global: ${snapshot.requestsHour}/${limits.requestsPerHour} peticiones/h; ${snapshot.requestsDay}/${limits.requestsPerDay} por 24 h; ${bytes(snapshot.bytesDay)}/${bytes(limits.bytesPerDay)}.")
            val usedDisk = if (selected == null) disk.values.sum() else disk[selected] ?: 0
            Text("Copia offline: ${bytes(usedDisk)}. Total: ${bytes(disk.values.sum())}/500 MiB.")
            if (snapshot.requestsHour >= limits.requestsPerHour * .8 || snapshot.requestsDay >= limits.requestsPerDay * .8 ||
                snapshot.bytesDay >= limits.bytesPerDay * .8 || disk.values.sum() >= 400L * 1024 * 1024)
                Text("Aviso: al menos un límite está al 80 % o más.", color = MaterialTheme.colorScheme.error)
            Text("Operaciones bloqueadas: ${snapshot.blockedOperations} (total local).")
            if (snapshot.lastClock == Long.MAX_VALUE) {
                Text("El presupuesto guardado está corrupto. La red está bloqueada; reparar mantiene una espera conservadora de 24 h.")
                TextButton(onClick = { model.repairGitHubBudget(); revision++ }) { Text("Reparar y pausar 24 h") }
            } else {
                Text("Pausa del proveedor: ${if (snapshot.blockedUntil <= now) "sin pausa" else date(snapshot.blockedUntil)}")
                if (snapshot.lastClock > now + 60_000) Text("El reloj retrocedió. Corrige fecha y hora para volver a sincronizar.")
            }
            if (snapshot.authSuspended) Text("Credencial rechazada: reemplaza el token desde Añadir carpeta de GitHub.")
            snapshot.events.lastOrNull { it.githubRemaining != null }?.let {
                Text("GitHub reportó ${it.githubRemaining}/${it.githubLimit ?: "?"} restantes; restablecimiento: ${date((it.githubReset ?: 0) * 1000)}. Cuota separada del presupuesto local.")
            }
            val runs = snapshot.runs.filter { selected == null || it.folderId == selected }
            Text("Hasta 500 ejecuciones recientes: ${runs.size}; caché evitó aproximadamente ${bytes(runs.sumOf { it.reusedBytes })} de blobs.")
            runs.takeLast(8).reversed().forEach { run ->
                Text("${date(run.at)} · ${run.outcome} · ${run.durationMs} ms · CPU del proceso ${run.cpuMs} ms · ${run.files} archivos · ${if (run.background) "periódica" else "primer plano"} · batería ${run.batteryPercent?.let { "$it %" } ?: "N/A"} · carga ${run.charging?.let { if (it) "sí" else "no" } ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
            }
            folders.firstOrNull { it.id == selected }?.let { Text("Último chequeo: ${date(it.lastCheck)}. Último éxito: ${date(it.lastSuccess)}. Estado: ${remoteStatusLabel(it.status)}. Próximo intento: ${date(it.nextRetryAt)}.") }
            Text("Bytes: cuerpos HTTP; los GET envían 0 B de cuerpo. Cabeceras/TLS y consumo facturado no están incluidos. CPU del proceso es una medida indirecta, no energía. GPU, vatios, mAh, tokens y costo de agentes: N/A.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { model.setGitHubPaused(!snapshot.pausedByUser); revision++ }) { Text(if (snapshot.pausedByUser) "Reanudar sincronización" else "Pausar toda la sincronización") }
            TextButton(onClick = { model.setGitHubLimits(if (limits.fileBytes == 10L * 1024 * 1024) 20 else 10, (limits.bytesPerRun / 1024 / 1024).toInt()); revision++ }) { Text("Archivo: ${limits.fileBytes / 1024 / 1024} MiB (cambiar 10/20)") }
            TextButton(onClick = { model.setGitHubLimits((limits.fileBytes / 1024 / 1024).toInt(), if (limits.bytesPerRun == 30L * 1024 * 1024) 60 else 30); revision++ }) { Text("Por sincronización: ${limits.bytesPerRun / 1024 / 1024} MiB (cambiar 30/60)") }
            TextButton(onClick = { revision++ }) { Text("Actualizar contadores") }
            TextButton(onClick = { export.launch("hypermarker-consumo.txt") }) { Text("Exportar agregados sin nombres ni rutas") }
            TextButton(onClick = { model.disconnectGitHub(); close() }) { Text("Desconectar cuenta (conservar copias)") }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Cerrar") } })
}

fun remoteStatusLabel(status: String): String = when (status) {
    "CURRENT" -> "Actualizado"; "SYNCING" -> "Actualizando"; "PENDING" -> "Pendiente"
    "OFFLINE" -> "Sin conexión"; "AUTH" -> "Reconectar cuenta"; "PAUSED" -> "En pausa"; else -> "Error"
}
