package com.robotta.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generator judul & deskripsi memakai Gemini API (ada kuota gratis).
 * Ambil API key di https://aistudio.google.com/apikey lalu isi di menu Pengaturan.
 * Tanpa API key, dipakai template offline sederhana.
 */
class TitleGenerator(
    private val apiKey: String,
    private val model: String
) {

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    suspend fun generateTitles(productName: String, category: String, condition: String): List<String> {
        if (!hasApiKey) return offlineTitles(productName, condition)
        val prompt = """
            Buat 3 judul iklan Facebook Marketplace dalam Bahasa Indonesia untuk produk berikut.
            Aturan: maksimal 80 karakter per judul, jujur sesuai produk, sebutkan kata kunci yang biasa dicari pembeli,
            tanpa emoji, tanpa nomor urut, tanpa tanda kutip, satu judul per baris, langsung judulnya saja.
            Produk: $productName
            Kategori: ${category.ifBlank { "-" }}
            Kondisi: ${condition.ifBlank { "-" }}
        """.trimIndent()
        val lines = callGemini(prompt)
            .lines()
            .map { cleanLine(it) }
            .filter { it.length in 3..100 }
        if (lines.isEmpty()) throw IOException("Jawaban AI kosong, coba lagi.")
        return lines.take(3)
    }

    suspend fun generateDescription(productName: String, category: String, condition: String, notes: String): String {
        if (!hasApiKey) return offlineDescription(productName, condition, notes)
        val prompt = """
            Tulis deskripsi iklan Facebook Marketplace dalam Bahasa Indonesia yang santai dan jelas untuk produk ini.
            Aturan: 60-120 kata, jujur (jangan mengarang spesifikasi yang tidak disebutkan), gunakan poin singkat dengan tanda "-"
            untuk detail, akhiri dengan ajakan chat. Tanpa markdown tebal/miring, tanpa emoji berlebihan.
            Produk: $productName
            Kategori: ${category.ifBlank { "-" }}
            Kondisi: ${condition.ifBlank { "-" }}
            Catatan penjual: ${notes.ifBlank { "-" }}
        """.trimIndent()
        val text = callGemini(prompt).replace("**", "").trim()
        if (text.isBlank()) throw IOException("Jawaban AI kosong, coba lagi.")
        return text
    }

    /** Uji koneksi; mengembalikan pesan singkat untuk ditampilkan. */
    suspend fun testConnection(): String {
        val reply = callGemini("Balas dengan satu kata: siap")
        return "Terhubung ke $model (${reply.take(20)})"
    }

    private suspend fun callGemini(prompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IOException("API key Gemini belum diisi di Pengaturan.")
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            val body = JSONObject()
                .put(
                    "contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    )
                )
                .put("generationConfig", JSONObject().put("temperature", 0.8))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.e(TAG, "Gemini HTTP $code: $raw")
                throw IOException("Gemini error $code: ${errorMessage(raw)}")
            }
            val candidates = JSONObject(raw).optJSONArray("candidates")
                ?: throw IOException("Jawaban AI tidak berisi teks (mungkin diblokir filter keamanan).")
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            buildString {
                for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
            }.trim()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memanggil Gemini", e)
            throw IOException("Gagal memanggil AI: ${e.message}", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(raw: String): String = try {
        JSONObject(raw).getJSONObject("error").optString("message").ifBlank { raw.take(160) }
    } catch (_: Exception) {
        raw.take(160)
    }

    companion object {
        private const val TAG = "TitleGenerator"

        fun cleanLine(line: String): String = line.trim()
            .replace(Regex("^(\\d+[.)]|[-*•])\\s*"), "")
            .trim('"', '\'', '“', '”', ' ')
            .replace("**", "")
            .trim()

        fun offlineTitles(productName: String, condition: String): List<String> {
            val name = productName.trim().replaceFirstChar { it.uppercase() }
            val kondisi = condition.ifBlank { "Baru" }
            return listOf(
                "$name – $kondisi",
                "Jual $name, Siap Kirim",
                "$name Murah Berkualitas"
            ).map { it.take(80) }
        }

        fun offlineDescription(productName: String, condition: String, notes: String): String = buildString {
            appendLine("Dijual $productName.")
            appendLine()
            appendLine("- Kondisi: ${condition.ifBlank { "Baru" }}")
            if (notes.isNotBlank()) appendLine("- ${notes.trim()}")
            appendLine("- Bisa COD / kirim sesuai kesepakatan")
            appendLine()
            append("Silakan chat untuk tanya stok dan ongkir.")
        }
    }
}
