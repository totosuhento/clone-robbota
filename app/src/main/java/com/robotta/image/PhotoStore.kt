package com.robotta.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Menyalin foto yang dipilih pengguna ke penyimpanan internal aplikasi,
 * sekaligus memperbaiki rotasi (EXIF) dan mengecilkan ukuran ke maks 1600px.
 * Dengan begitu foto tetap ada walau file aslinya dipindah/dihapus.
 */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun importFromUri(uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.e(TAG, "File bukan gambar: $uri")
                return null
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = ImageUtils.sampleSizeFor(bounds.outWidth, bounds.outHeight, ImageUtils.MAX_SIDE)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val degrees = resolver.openInputStream(uri)?.use { ImageUtils.exifRotationDegrees(it) } ?: 0

            var bitmap = ImageUtils.scaleDown(decoded, ImageUtils.MAX_SIDE)
            bitmap = ImageUtils.rotate(bitmap, degrees)

            val file = File(dir, "img_${System.currentTimeMillis()}_${counter.incrementAndGet()}.jpg")
            val ok = ImageUtils.saveJpeg(bitmap, file)
            bitmap.recycle()
            if (ok) {
                Log.d(TAG, "Foto disimpan: ${file.name}")
                file.absolutePath
            } else null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Memori habis saat import $uri", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Gagal import foto $uri", e)
            null
        }
    }

    fun newFile(prefix: String): File =
        File(dir, "${prefix}_${System.currentTimeMillis()}_${counter.incrementAndGet()}.jpg")

    /** Menghapus file foto milik aplikasi (hanya yang berada di folder internal). */
    fun deleteIfOwned(path: String) {
        try {
            val file = File(path)
            if (file.absolutePath.startsWith(dir.absolutePath) && file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal hapus $path", e)
        }
    }

    private companion object {
        const val TAG = "PhotoStore"
        val counter = AtomicInteger(0)
    }
}
