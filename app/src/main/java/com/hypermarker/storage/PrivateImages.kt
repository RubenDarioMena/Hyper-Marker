package com.hypermarker.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Copias privadas y reescaladas de imágenes elegidas por el usuario (Photo Picker) o de miniaturas.
 * Nunca se guarda el original: se decodifica reducido, se recorta o ajusta y se escribe como WebP
 * dentro del directorio privado del documento. No requiere permisos de almacenamiento.
 */
object PrivateImages {
    const val AVATAR_SIDE = 512
    const val ATTACHMENT_SIDE = 1600
    private const val MAX_SOURCE_PIXELS = 60_000_000L

    /** Lado mayor tras reducir `width × height` para que no supere `maxSide`. Puro, para pruebas. */
    fun fittedSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxSide > 0)
        val factor = min(1f, maxSide.toFloat() / max(width, height))
        return max(1, (width * factor).roundToInt()) to max(1, (height * factor).roundToInt())
    }

    /** Muestreo potencia de dos para decodificar sin superar ~2× el tamaño pedido. */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Decodifica `uri` reducido para que su lado menor (`crop`) o mayor quede cerca de `side`. Respeta la orientación EXIF en API 28+. */
    fun decode(context: Context, uri: Uri, side: Int, crop: Boolean): Bitmap {
        require(uri.scheme == "content") { "Solo se aceptan imágenes elegidas en el selector del sistema" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width; val h = info.size.height
                require(w > 0 && h > 0 && w.toLong() * h <= MAX_SOURCE_PIXELS) { "La imagen es demasiado grande" }
                val reference = if (crop) min(w, h) else max(w, h)
                val factor = min(1f, side * 1.5f / reference)
                decoder.setTargetSize(max(1, (w * factor).roundToInt()), max(1, (h * factor).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(requireNotNull(it), null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "No es una imagen válida" }
            val reference = if (crop) min(bounds.outWidth, bounds.outHeight) else max(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(reference, reference, side) }
            context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(requireNotNull(it), null, options)) { "No es una imagen válida" } }
        }
    }

    fun decodeFile(file: File, side: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, side) })
    }

    /** Recorte cuadrado centrado de lado `side`. */
    fun square(source: Bitmap, side: Int): Bitmap {
        val edge = min(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - edge) / 2, (source.height - edge) / 2, edge, edge)
        val target = min(side, edge)
        return if (target == edge) cropped else Bitmap.createScaledBitmap(cropped, target, target, true)
    }

    /** Ajuste proporcional sin recorte para que el lado mayor no supere `maxSide`. */
    fun fit(source: Bitmap, maxSide: Int): Bitmap {
        val (w, h) = fittedSize(source.width, source.height, maxSide)
        return if (w == source.width && h == source.height) source else Bitmap.createScaledBitmap(source, w, h, true)
    }

    @Suppress("DEPRECATION")
    fun writeWebp(bitmap: Bitmap, file: File, quality: Int = 86) {
        file.parentFile!!.mkdirs()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            check(bitmap.compress(format, quality, stream)) { "No se pudo guardar la imagen" }
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
}
