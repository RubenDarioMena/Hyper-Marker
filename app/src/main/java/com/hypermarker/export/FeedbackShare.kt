package com.hypermarker.export

import android.net.Uri
import com.hypermarker.storage.Annotation
import com.hypermarker.storage.Document
import com.hypermarker.storage.LibraryRepository
import com.hypermarker.viewer.DocumentWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object FeedbackShare {
    suspend fun write(repository: LibraryRepository, document: Document, annotations: List<Annotation>, view: DocumentWebView, uri: Uri) {
        val stage = File(repository.context.cacheDir, "feedback-exports/${UUID.randomUUID()}")
        var archive: File? = null
        try {
            withContext(Dispatchers.IO) {
                repository.remoteMutation { FeedbackExport.prepare(view.contentRoot, File(stage, "original").apply { mkdirs() }) }
            }
            val renderer = FeedbackCapture(view, document, repository, File(stage, "original"), annotations)
            archive = withContext(Dispatchers.IO) {
                val controller = repository.context.assets.open("annotation/controller.js").bufferedReader().use { it.readText() }
                FeedbackExport.finish(document, annotations, stage, controller) { drawing, target -> renderer.capture(drawing, target) }
            }
            withContext(Dispatchers.IO) {
                repository.context.contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output) { "No se pudo abrir el destino" }
                    archive.inputStream().use { it.copyTo(output) }
                }
            }
        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { stage.deleteRecursively(); archive?.delete() } }
    }
}
