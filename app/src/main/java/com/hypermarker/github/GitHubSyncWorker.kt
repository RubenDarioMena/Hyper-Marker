package com.hypermarker.github

import android.content.Context
import androidx.work.*
import com.hypermarker.HyperMarkerApplication
import java.util.concurrent.TimeUnit

class GitHubSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HyperMarkerApplication
        for (folder in app.repository.dao.remoteFolders().filter { it.autoSync }) {
            if (isStopped) break
            try { app.githubSync.sync(folder.id, background = true) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* El estado y la pausa se conservan; no pedir retry inmediato a WorkManager. */ }
        }
        return Result.success()
    }
    companion object {
        private const val NAME = "github-sync-global"
        fun schedule(context: Context): Operation {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()
            val work = PeriodicWorkRequestBuilder<GitHubSyncWorker>(60, TimeUnit.MINUTES)
                .setConstraints(constraints).build()
            return WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }
        fun cancel(context: Context): Operation = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
