package com.robotta.ai

import android.util.Log
import com.robotta.data.entities.Product
import org.json.JSONObject

/** Hasil "Optimasi Postingan" untuk satu produk. */
data class OptimizeResult(
    val score: Int,
    val tips: List<String>,
    val title: String? = null,
    val description: String? = null,
    val fromAi: Boolean = false
)

/**
 * Menilai kelengkapan & kualitas tawaran (skor 0–100) dan memberi saran.
 * Penilaian dasar berjalan offline; bila API key Gemini diisi, AI menambahkan
 * judul & deskripsi perbaikan yang tetap jujur sesuai data produk.
 */
object ListingOptimizer {

    private const val TAG = "ListingOptimizer"

    fun quickScore(p: Product): OptimizeResult {
        var score = 100
        val tips = mutableListOf<String>()
        val photos = p.imageList().size
        when {
            photos == 0 -> { score -= 30; tips += "Tambahkan foto — tawaran tanpa foto tidak bisa diterbitkan." }
            photos < 3 -> { score -= 10; tips += "Tambah foto jadi 3–5 dari sudut berbeda (depan, belakang, detail, kemasan)." }
        }
        val title = p.title.trim()
        when {
            title.length < 15 -> { score -= 15; tips += "Judul terlalu pendek. Sebutkan jenis barang, merek/ukuran, dan keunggulan utama." }
            title.length > 80 -> { score -= 5; tips += "Judul terlalu panjang; Facebook memotongnya di daftar hasil." }
        }
        val letters = title.filter { it.isLetter() }
        if (letters.length >= 8 && letters.count { it.isUpperCase() } > letters.length * 0.6) {
            score -= 5; tips += "Kurangi huruf kapital semua; lebih mudah dibaca dengan huruf biasa."
        }
        if (p.price <= 0) { score -= 20; tips += "Isi harga yang jelas." }
        when {
            p.description.isBlank() -> { score -= 20; tips += "Tulis deskripsi: kondisi, ukuran/spesifikasi, isi paket, cara kirim/COD." }
            p.description.length < 80 -> { score -= 10; tips += "Deskripsi masih singkat; tambahkan detail yang sering ditanyakan pembeli." }
        }
        if (p.category.isBlank()) { score -= 10; tips += "Pilih kategori agar muncul di pencarian kategori." }
        if (p.location.isBlank()) { score -= 10; tips += "Isi lokasi asli barang (menu Riset Lokasi)." }
        if (p.hashtags.isBlank()) { score -= 5; tips += "Tambahkan 3–6 hashtag dari Riset Kata Kunci." }
        if (tips.isEmpty()) tips += "Tawaran sudah lengkap. Pertahankan foto jelas dan respons cepat ke pembeli."
        return OptimizeResult(score.coerceIn(0, 100), tips)
    }

    suspend fun optimize(p: Product, ai: TitleGenerator): OptimizeResult {
        val base = quickScore(p)
        if (!ai.hasApiKey) return base
        val prompt = """
            Kamu konsultan penjualan Facebook Marketplace Indonesia. Nilai tawaran berikut lalu perbaiki.
            Judul: ${p.title}
            Harga: ${p.price}
            Kategori: ${p.category.ifBlank { "-" }}
            Kondisi: ${p.condition}
            Deskripsi: ${p.description.ifBlank { "-" }}
            Jumlah foto: ${p.imageList().size}

            Aturan: tetap jujur, JANGAN mengarang spesifikasi/merek/klaim yang tidak ada di data, tanpa emoji berlebihan,
            judul maks 80 karakter berisi kata yang dicari pembeli, deskripsi 60–150 kata dengan poin "- ".
            Balas HANYA JSON: {"skor": 0-100, "saran": ["...", "..."], "judul": "...", "deskripsi": "..."}
        """.trimIndent()
        return try {
            val o = JSONObject(TitleGenerator.stripFence(ai.callGemini(prompt, jsonMode = true)))
            val arr = o.optJSONArray("saran")
            val aiTips = if (arr == null) emptyList() else List(arr.length()) { arr.optString(it).trim() }.filter { it.isNotBlank() }
            OptimizeResult(
                score = minOf(o.optInt("skor", base.score), base.score + 10).coerceIn(0, 100),
                tips = (base.tips.filterNot { it.startsWith("Tawaran sudah lengkap") } + aiTips).distinct().take(8),
                title = o.optString("judul").let { TitleGenerator.cleanLine(it) }.takeIf { it.length >= 5 }?.take(100),
                description = o.optString("deskripsi").replace("**", "").trim().takeIf { it.length >= 20 },
                fromAi = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Optimasi AI gagal", e)
            base.copy(tips = base.tips + "AI tidak tersedia: ${e.message?.take(80)}")
        }
    }
}
