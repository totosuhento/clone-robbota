package com.robotta.data.entities

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val price: Long,
    val category: String,
    /** Salah satu nilai di [Conditions.ALL]. */
    val condition: String,
    val description: String,
    val location: String,
    /** Hashtag dipisah spasi, ditambahkan di akhir deskripsi saat posting. */
    val hashtags: String = "",
    /** JSON array berisi path absolut foto di penyimpanan internal aplikasi. */
    val imagePaths: String = "[]",
    /** Salah satu nilai di [ProductStatus]. */
    val status: String = ProductStatus.PENDING,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val postedAt: Long? = null
) {
    fun imageList(): List<String> = ProductImages.decode(imagePaths)
}

object ProductStatus {
    const val PENDING = "pending"
    const val SUCCESS = "success"
    const val FAILED = "failed"
    const val SKIPPED = "skipped"

    fun label(status: String): String = when (status) {
        PENDING -> "Menunggu"
        SUCCESS -> "Terposting"
        FAILED -> "Gagal"
        SKIPPED -> "Dilewati"
        else -> status
    }
}

object ProductImages {
    private const val TAG = "ProductImages"

    fun encode(paths: List<String>): String = JSONArray(paths).toString()

    fun decode(json: String): List<String> = try {
        val arr = JSONArray(json.ifBlank { "[]" })
        List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "JSON foto rusak: $json", e)
        emptyList()
    }
}

object Conditions {
    const val NEW = "Baru"
    const val USED_LIKE_NEW = "Bekas - Seperti Baru"
    const val USED_GOOD = "Bekas - Baik"
    const val USED_FAIR = "Bekas - Cukup Baik"

    val ALL = listOf(NEW, USED_LIKE_NEW, USED_GOOD, USED_FAIR)

    /** Mengubah teks bebas (mis. dari CSV) menjadi salah satu nilai [ALL]. */
    fun normalize(raw: String): String {
        val s = raw.trim().lowercase()
        return when {
            s.isEmpty() -> NEW
            "seperti" in s || "like new" in s -> USED_LIKE_NEW
            "cukup" in s || "fair" in s -> USED_FAIR
            "bekas" in s || "baik" in s || "used" in s || "good" in s -> USED_GOOD
            else -> NEW
        }
    }

    /** Label yang mungkin dipakai Facebook (bahasa Indonesia & Inggris) untuk kondisi ini. */
    fun facebookLabels(condition: String): List<String> = when (condition) {
        USED_LIKE_NEW -> listOf("Bekas - Seperti Baru", "Bekas – Seperti Baru", "Seperti Baru", "Used - Like New", "Used – Like New", "Like New")
        USED_GOOD -> listOf("Bekas - Baik", "Bekas – Baik", "Used - Good", "Used – Good")
        USED_FAIR -> listOf("Bekas - Cukup Baik", "Bekas – Cukup Baik", "Used - Fair", "Used – Fair")
        else -> listOf("Baru", "New")
    }
}
