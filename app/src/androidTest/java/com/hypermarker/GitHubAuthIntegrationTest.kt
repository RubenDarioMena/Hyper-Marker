package com.hypermarker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermarker.github.GitHubAuthStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubAuthIntegrationTest {
    @Test fun keystoreEncryptsRotatesAndClearsSyntheticCredential() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = GitHubAuthStore(context)
        store.clear()
        val first = "github_pat_synthetic_first_1234567890"
        val second = "github_pat_synthetic_second_1234567890"
        try {
            store.save(first)
            assertEquals(first, store.read())
            val prefs = context.getSharedPreferences("github_auth", Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.joinToString().contains(first))
            store.save(second)
            assertEquals(second, store.read())
            assertFalse(prefs.all.values.joinToString().contains(second))
        } finally { store.clear() }
        assertNull(store.read())
    }
}
