package com.hypermarker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.hypermarker.library.LibraryScreen
import com.hypermarker.library.LibraryViewModel
import com.hypermarker.markdown.MarkdownScreen
import com.hypermarker.viewer.ReaderScreen

class MainActivity : ComponentActivity() {
    private val model by viewModels<LibraryViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF89D5BC))
                else lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFF667B69), background = Color(0xFFFFFDF7), surface = Color(0xFFFFFDF7))
            MaterialTheme(colorScheme = scheme) {
                val docs by model.documents.collectAsState()
                val document = docs.firstOrNull { it.id == model.openedId }
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (document?.type) {
                        "HTML" -> ReaderScreen(document, model.repository, onClose = { model.openedId = null },
                            onMarkdown = { model.action { model.openedId = model.repository.createMarkdown(document.folderId, "Notas · ${document.title}").id } })
                        "MARKDOWN" -> MarkdownScreen(document, model.repository, onClose = { model.openedId = null })
                        else -> LibraryScreen(model)
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    @Suppress("DEPRECATION")
    private fun receive(intent: Intent?) {
        if (intent?.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        val values = if (intent?.action == Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else listOfNotNull(intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val uris = (values + (0 until (intent?.clipData?.itemCount ?: 0)).mapNotNull { intent?.clipData?.getItemAt(it)?.uri }).distinct()
        if (uris.isNotEmpty()) model.receive(uris)
    }
}
