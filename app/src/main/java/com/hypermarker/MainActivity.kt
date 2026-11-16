package com.hypermarker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.hypermarker.browser.BrowserScreen
import com.hypermarker.flow.FlowScreen
import com.hypermarker.github.GitHubAddScreen
import com.hypermarker.github.RemoteMarkdownScreen
import com.hypermarker.library.LibraryScreen
import com.hypermarker.library.LibraryViewModel
import com.hypermarker.markdown.MarkdownScreen
import com.hypermarker.viewer.ReaderScreen
import com.hypermarker.ui.theme.HyperMarkerTheme

class MainActivity : ComponentActivity() {
    private val model by viewModels<LibraryViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent)
        setContent {
            HyperMarkerTheme {
                val docs by model.documents.collectAsState()
                val document = docs.firstOrNull { it.id == model.openedId }
                LaunchedEffect(model.openedId) { model.openedId?.let(model::markOpened) }
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        model.browserScreen -> BrowserScreen(model.repository, model.browserUrl, model.folderId,
                            onClose = { model.browserScreen = false }, onSaved = { model.browserScreen = false; model.open(it) })
                        model.githubScreen -> GitHubAddScreen(model)
                        document?.type == "REMOTE_MARKDOWN" -> RemoteMarkdownScreen(document, model.repository,
                            onClose = model::closeDocument, onCopy = { model.open(it) })
                        (document?.type == "HTML" || document?.type == "WEB_SNAPSHOT") -> ReaderScreen(requireNotNull(document), model.repository, onClose = model::closeDocument, onBrowse = model::browse,
                            onMarkdown = { model.action { model.open(model.repository.createMarkdown(document.folderId, "Notas · ${document.title}").id) } })
                        document?.type == "MARKDOWN" -> MarkdownScreen(document, model.repository, onClose = model::closeDocument)
                        document?.type == "FLOW" -> FlowScreen(document, model.repository, model.previews, onClose = model::closeDocument,
                            onOpen = { id -> model.open(id, from = document.id) },
                            onOpenFolder = { id -> model.openFolder(id, from = document.id) })
                        else -> LibraryScreen(model)
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    @Suppress("DEPRECATION")
    private fun receive(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.takeIf { it.scheme in listOf("content", "file") }?.let {
                model.browserScreen = false; model.githubScreen = false; model.receive(listOf(it))
            }
            return
        }
        if (intent?.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        val values = if (intent?.action == Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else listOfNotNull(intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val uris = (values + (0 until (intent?.clipData?.itemCount ?: 0)).mapNotNull { intent?.clipData?.getItemAt(it)?.uri }).distinct()
        if (uris.isNotEmpty()) model.receive(uris)
    }
}
