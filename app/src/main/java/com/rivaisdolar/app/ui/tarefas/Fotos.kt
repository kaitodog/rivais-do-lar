package com.rivaisdolar.app.ui.tarefas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Foto do depois: corrige a rotação da câmera, reduz para 1600px e comprime em JPEG. */
suspend fun prepararFoto(context: Context, uri: Uri, ladoMaximo: Int = 1600): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var amostra = 1
        while (max(limites.outWidth, limites.outHeight) / (amostra * 2) >= ladoMaximo) amostra *= 2
        val bruto = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return@runCatching null

        val graus = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val escala = minOf(1f, ladoMaximo.toFloat() / max(bruto.width, bruto.height))
        val matriz = Matrix().apply {
            postScale(escala, escala)
            postRotate(graus)
        }
        val final = Bitmap.createBitmap(bruto, 0, 0, bruto.width, bruto.height, matriz, true)
        ByteArrayOutputStream().use { saida ->
            final.compress(Bitmap.CompressFormat.JPEG, 82, saida)
            saida.toByteArray()
        }
    }.getOrNull()
}
