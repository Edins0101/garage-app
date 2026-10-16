package com.arianna.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.arianna.app.domain.repository.DataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Prepara una foto para subirla: lado mayor de [MAX_SIDE] px, rotación corregida y JPEG ~80 %.
 * Al recodificar se pierden los metadatos EXIF (incluida la ubicación GPS), que es lo que queremos.
 */
object ImageCompressor {
    private const val MAX_SIDE = 1280
    private const val QUALITY = 80

    suspend fun compress(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1) Solo leer el tamaño, para decodificar ya reducida (evita cargar 12 MP en memoria).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DataException("No se pudo leer la foto", DataException.Kind.INVALID)
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw DataException("No se pudo leer la foto", DataException.Kind.INVALID)

        // 2) Las fotos de cámara guardan la rotación en EXIF: aplicarla a los píxeles.
        val rotation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).rotationDegrees
        } ?: 0

        // 3) Escalar al tamaño final y rotar en una sola transformación.
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val finalBitmap = if (matrix.isIdentity) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        ByteArrayOutputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (finalBitmap !== decoded) decoded.recycle()
            finalBitmap.recycle()
            out.toByteArray()
        }
    }
}
