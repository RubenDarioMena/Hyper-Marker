package com.hypermarker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hypermarker.github.GitHubSyncWorker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GitHubWorkerIntegrationTest {
    @Test fun workerExecutesWithoutCredentialAndWithoutAnySources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as HyperMarkerApplication
        assertNull(app.githubAuth.read())
        assertTrue(kotlinx.coroutines.runBlocking { app.repository.dao.remoteFolders().none { it.autoSync } })
        val manager = WorkManager.getInstance(context)
        val request = androidx.work.OneTimeWorkRequestBuilder<GitHubSyncWorker>().build()
        manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
        try {
            val end = System.currentTimeMillis() + 15_000
            var state = requireNotNull(manager.getWorkInfoById(request.id).get()).state
            while (!state.isFinished && System.currentTimeMillis() < end) {
                Thread.sleep(100); state = requireNotNull(manager.getWorkInfoById(request.id).get()).state
            }
            assertEquals(WorkInfo.State.SUCCEEDED, state)
        } finally { manager.cancelWorkById(request.id) }
    }

    @Test fun repeatedSchedulingKeepsOneConstrainedGlobalWorker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val work = WorkManager.getInstance(context)
        GitHubSyncWorker.cancel(context).result.get(10, TimeUnit.SECONDS)
        work.pruneWork().result.get(10, TimeUnit.SECONDS)
        try {
            repeat(100) { GitHubSyncWorker.schedule(context).result.get(10, TimeUnit.SECONDS) }
            val active = work.getWorkInfosForUniqueWork("github-sync-global").get(15, TimeUnit.SECONDS)
                .filter { !it.state.isFinished }
            assertEquals(1, active.size)
            assertEquals(NetworkType.UNMETERED, active.single().constraints.requiredNetworkType)
            assertTrue(active.single().constraints.requiresBatteryNotLow())
            assertTrue(active.single().constraints.requiresStorageNotLow())
        } finally { GitHubSyncWorker.cancel(context) }
    }
}
