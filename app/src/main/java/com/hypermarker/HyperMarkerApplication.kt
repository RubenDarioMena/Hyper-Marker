package com.hypermarker

import android.app.Application
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.github.*

class HyperMarkerApplication : Application() {
    val repository by lazy { LibraryRepository(this) }
    val githubAuth by lazy { GitHubAuthStore(this) }
    val githubResourceSettings by lazy { ResourceSettings(this) }
    val githubGovernor by lazy { ResourceGovernor(PreferencesResourceStore(this), limits = githubResourceSettings.load()) }
    val githubApi by lazy { GitHubApiClient(githubGovernor) }
    val githubBrowser by lazy { GitHubBrowserRepository(githubApi, githubAuth) }
    val githubSync by lazy { GitHubSyncEngine(repository, githubApi, githubAuth, githubGovernor) }
}
