package com.robotta.image

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Menyalin foto produk ke galeri (folder Pictures/MarketAsisten) supaya muncul
 * paling atas di pemilih foto Facebook. Foto lama dari sesi sebelumnya dihapus
 * dulu agar galeri tidak penuh.
 */
class GalleryExporter(private val context: Context) {

    fun clearPrevious() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val deleted = context.contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf("%${Environment.DIRECTORY_PICTURES}/$FOLDER/")
                )
                Log.d(TAG, "Hapus $deleted foto lama dari galeri")
            } else {
                legacyDir().listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            // Mis. setelah aplikasi di-install ulang, foto lama bukan milik aplikasi lagi.
            Log.w(TAG, "Tidak bisa menghapus foto lama (aman diabaikan)", e)
        }
    }

    /**
     * @return jumlah foto yang berhasil disalin.
     * Foto pertama diberi waktu paling baru agar tampil paling depan.
     */
    fun export(paths: List<String>): Int {
        val base = System.currentTimeMillis()
        var count = 0
        // Tulis dari foto terakhir ke foto pertama.
        for (i in paths.indices.reversed()) {
            val src = File(paths[i])
            if (!src.exists()) {
                Log.w(TAG, "Foto tidak ada: ${src.absolutePath}")
                continue
            }
            val takenAt = base + (paths.size - i) * 1000L
            val name = "ma_${takenAt}_$i.jpg"
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportScoped(src, name, takenAt, FOLDER)
            } else {
                exportLegacy(src, name, FOLDER)
            }
            if (ok) count++
        }
        Log.d(TAG, "Disalin $count/${paths.size} foto ke galeri")
        return count
    }

    /** Simpan salinan foto ke Pictures/<folder> tanpa menghapus isi lama (untuk Auto Frame). */
    fun saveCopies(paths: List<String>, folder: String): Int {
        var count = 0
        paths.forEachIndexed { i, path ->
            val src = File(path)
            if (!src.exists()) return@forEachIndexed
            val now = System.currentTimeMillis()
            val name = "frame_${now}_$i.jpg"
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportScoped(src, name, now, folder)
            } else {
                exportLegacy(src, name, folder)
            }
            if (ok) count++
        }
        Log.d(TAG, "Disimpan $count foto ke Pictures/$folder")
        return count
    }

    private fun exportScoped(src: File, name: String, takenAt: Long, folder: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folder")
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat entri galeri", e)
            null
        } ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("Output stream null")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyalin ${src.name}", e)
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun exportLegacy(src: File, name: String, folder: String): Boolean = try {
        val dest = File(legacyDir(folder).apply { mkdirs() }, name)
        src.copyTo(dest, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("image/jpeg"), null)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Gagal menyalin (legacy) ${src.name}. Izin penyimpanan sudah diberikan?", e)
        false
    }

    @Suppress("DEPRECATION")
    private fun legacyDir(folder: String = FOLDER): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder)

    companion object {
        const val FOLDER = "MarketAsisten"
        const val FRAME_FOLDER = "AutoFrame"
        private const val TAG = "GalleryExporter"
    }
}
