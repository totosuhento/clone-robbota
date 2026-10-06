package com.robotta.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class KeywordSource(val label: String) { GOOGLE("Google"), AI("AI") }

data class KeywordIdea(
    val text: String,
    val source: KeywordSource,
    /** Makin kecil makin populer (urutan saran Google). */
    val rank: Int
)

/**
 * Riset Kata Kunci:
 *  1. Saran pencarian Google berbahasa Indonesia untuk kata dasar + kata dasar a..z
 *     (gratis, tanpa API key) — menggambarkan apa yang benar-benar diketik orang.
 *  2. Opsional: tambahan ide dari Gemini jika API key diisi.
 * Hasilnya dipakai untuk menulis judul & hashtag yang mudah ditemukan.
 */
class KeywordResearch(private val ai: TitleGenerator) {

    suspend fun research(seed: String, useAlphabet: Boolean, onProgress: (Int, Int) -> Unit): List<KeywordIdea> {
        val base = seed.trim().lowercase()
        if (base.isBlank()) return emptyList()

        val queries = buildList {
            add(base)
            if (useAlphabet) ('a'..'z').forEach { add("$base $it") }
        }
        val results = LinkedHashMap<String, KeywordIdea>()
        queries.forEachIndexed { i, q ->
            onProgress(i + 1, queries.size)
            googleSuggest(q).forEachIndexed { pos, s ->
                val key = s.trim().lowercase()
                if (key.isNotBlank() && key != base && key !in results) {
                    // Saran dari kata dasar murni dianggap paling populer.
                    results[key] = KeywordIdea(key, KeywordSource.GOOGLE, if (i == 0) pos else 100 + i * 10 + pos)
                }
            }
            delay(120L)
        }

        if (ai.hasApiKey) {
            try {
                ai.suggestKeywords(base).forEachIndexed { pos, s ->
                    val key = s.lowercase()
                    if (key !in results) results[key] = KeywordIdea(key, KeywordSource.AI, 1000 + pos)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ide AI gagal: ${e.message}")
            }
        }
        return results.values.sortedBy { it.rank }
    }

    private suspend fun googleSuggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&hl=id&gl=id&q=" +
            URLEncoder.encode(query, "UTF-8")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Suggest HTTP ${conn.responseCode} untuk \"$query\"")
                return@withContext emptyList()
            }
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(raw).optJSONArray(1) ?: return@withContext emptyList()
            List(arr.length()) { arr.optString(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Suggest gagal untuk \"$query\": ${e.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val TAG = "KeywordResearch"
    }
}
