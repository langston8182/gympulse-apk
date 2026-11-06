package com.gympulse.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.gympulse.app.core.coroutines.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Copies gallery images into the app private storage, downscaled. Picker URIs are temporary grants,
 * so keeping a private copy guarantees the image is still there after a restart.
 */
@Singleton
class ImageStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    suspend fun import(source: Uri): String? = withContext(io) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val largest = max(bounds.outWidth, bounds.outHeight)
            if (largest <= 0) return@runCatching null
            var sample = 1
            while (largest / (sample * 2) >= MAX_SIZE_PX) sample *= 2
            val bitmap = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            file.toUri().toString()
        }.getOrNull()
    }

    suspend fun delete(uri: String) = withContext(io) {
        val path = runCatching { uri.toUri().path }.getOrNull() ?: return@withContext
        val file = File(path)
        if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
    }

    private companion object {
        const val DIRECTORY = "exercise_images"
        const val MAX_SIZE_PX = 1280
    }
}
