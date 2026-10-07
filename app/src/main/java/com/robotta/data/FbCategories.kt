package com.robotta.data

import android.content.Context
import android.util.Log
import org.json.JSONArray

/** Satu baris di pemilih kategori: judul kelompok atau kategori yang bisa dipilih. */
data class CategoryEntry(val name: String, val isGroup: Boolean)

/**
 * Daftar kategori Facebook Marketplace.
 *
 * - Bawaan: disusun dari layar kategori Facebook versi Indonesia (kelompok + subkategori).
 * - Rekaman: saat Mode Browser pertama kali membuka dropdown Kategori, seluruh isi daftar
 *   Facebook di akunmu direkam dan dipakai di Data Posting, jadi namanya persis sama.
 */
object FbCategories {

    private const val TAG = "FbCategories"
    private const val PREFS = "fb_categories"
    private const val KEY_LIST = "learned"
    private const val KEY_AT = "learned_at"

    /** Kelompok -> kategori. "Rumah & Taman" disalin dari layar Facebook pengguna. */
    val DEFAULT: List<Pair<String, List<String>>> = listOf(
        "Rumah & Taman" to listOf("Peralatan", "Mebel", "Peralatan rumah tangga", "Kebun", "Perkakas"),
        "Hiburan" to listOf("Video game", "Buku, film & musik"),
        "Pakaian & Aksesori" to listOf(
            "Tas & koper", "Pakaian & sepatu wanita", "Pakaian & sepatu pria", "Perhiasan & aksesori"
        ),
        "Keluarga" to listOf(
            "Kesehatan & kecantikan", "Perlengkapan hewan peliharaan", "Bayi & anak-anak", "Mainan & game"
        ),
        "Elektronik" to listOf("Elektronik & komputer", "Ponsel"),
        "Hobi" to listOf(
            "Sepeda", "Seni & kerajinan", "Olahraga & outdoor", "Suku cadang otomotif",
            "Alat musik", "Barang antik & koleksi"
        ),
        "Rupa-rupa" to listOf("Obral garasi", "Lain-lain")
    )

    private val GROUP_NAMES = DEFAULT.map { it.first.lowercase() }.toSet()

    /** Teks yang bukan kategori (label kolom, tombol) — dibuang dari hasil rekaman. */
    private val JUNK = setOf(
        "kategori", "category", "judul", "title", "harga", "price", "kondisi", "condition", "deskripsi",
        "description", "lokasi", "location", "cari", "search", "batal", "cancel", "tawaran baru", "terbitkan",
        "berikutnya", "kembali", "tersedia pengiriman", "opsional", "foto", "marketplace"
    )

    fun hasLearned(context: Context): Boolean = learned(context).isNotEmpty()

    fun learned(context: Context): List<String> = try {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        Log.e(TAG, "Daftar kategori rekaman rusak", e)
        emptyList()
    }

    /** Simpan daftar hasil rekaman Mode Browser (dibersihkan dari teks non-kategori). */
    fun saveLearned(context: Context, raw: List<String>) {
        val clean = raw.map { it.trim() }
            .filter { it.length in 3..45 }
            .filter { it.lowercase() !in JUNK }
            .filter { t -> !t.contains("Rp") && t.count { it.isDigit() } < 3 }
            .filter { t -> !t.startsWith("Foto") }
            .distinct()
        if (clean.size < 5) {
            Log.w(TAG, "Rekaman kategori terlalu sedikit (${clean.size}), diabaikan")
            return
        }
        prefs(context).edit()
            .putString(KEY_LIST, JSONArray(clean).toString())
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Disimpan ${clean.size} kategori dari Facebook")
    }

    fun clearLearned(context: Context) {
        prefs(context).edit().remove(KEY_LIST).remove(KEY_AT).apply()
    }

    /** Baris untuk pemilih kategori: dari rekaman Facebook bila ada, selain itu daftar bawaan. */
    fun entries(context: Context): List<CategoryEntry> {
        val learned = learned(context)
        if (learned.isNotEmpty()) {
            return learned.map { CategoryEntry(it, isGroup = it.lowercase() in GROUP_NAMES) }
        }
        return DEFAULT.flatMap { (group, items) ->
            listOf(CategoryEntry(group, true)) + items.map { CategoryEntry(it, false) }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
