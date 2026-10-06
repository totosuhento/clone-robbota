package com.robotta.data

import android.content.Context
import android.util.Log

/** Satu kabupaten/kota di Indonesia (514 data, dari assets/wilayah.txt). */
data class Region(val name: String, val type: String, val province: String) {
    /** Teks yang diisi ke kolom lokasi Facebook. */
    val locationText: String get() = "$name, $province"
    val displayName: String get() = if (type.isBlank()) name else "$type $name"
}

/** Riset Lokasi: pencarian kabupaten/kota se-Indonesia secara offline. */
object Regions {
    private const val TAG = "Regions"

    @Volatile
    private var cache: List<Region>? = null

    fun all(context: Context): List<Region> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Region> = try {
        context.assets.open("wilayah.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split(';')
                if (parts.size == 3 && parts[0].isNotBlank()) Region(parts[0], parts[1], parts[2]) else null
            }.toList()
        }.also { Log.d(TAG, "Memuat ${it.size} wilayah") }
    } catch (e: Exception) {
        Log.e(TAG, "Gagal memuat data wilayah", e)
        emptyList()
    }

    fun provinces(context: Context): List<String> = all(context).map { it.province }.distinct().sorted()

    fun search(context: Context, query: String, province: String?): List<Region> {
        val q = query.trim().lowercase()
        return all(context).asSequence()
            .filter { province == null || it.province == province }
            .filter {
                q.isEmpty() || it.name.lowercase().contains(q) || it.province.lowercase().contains(q)
            }
            .sortedWith(compareBy({ !it.name.lowercase().startsWith(q) }, { it.name }))
            .toList()
    }
}
