package com.robotta.data

import android.util.Log
import com.robotta.ai.TitleGenerator
import com.robotta.data.entities.Conditions
import com.robotta.data.entities.Product

/**
 * Import produk dari CSV (bisa dibuat dari Excel/Google Sheets: File > Download > CSV).
 *
 * Baris pertama = header. Kolom wajib: judul, harga.
 * Kolom opsional: kategori, kondisi, deskripsi, lokasi, hashtag.
 * Pemisah koma (,) atau titik koma (;) dideteksi otomatis.
 * Foto ditambahkan lewat aplikasi setelah import.
 */
object CsvImporter {

    private const val TAG = "CsvImporter"

    data class Result(val products: List<Product>, val errors: List<String>)

    fun parse(raw: String, defaultLocation: String): Result {
        return try {
            parseInternal(raw, defaultLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal parse CSV", e)
            Result(emptyList(), listOf("File tidak bisa dibaca: ${e.message}"))
        }
    }

    private fun parseInternal(raw: String, defaultLocation: String): Result {
        val text = raw.removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return Result(emptyList(), listOf("File kosong."))
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

        val rows = parseRows(text, delimiter).filter { row -> row.any { it.isNotBlank() } }
        if (rows.size < 2) {
            return Result(emptyList(), listOf("Tidak ada baris data. Baris pertama harus berisi header."))
        }

        val header = rows[0].map { it.trim().lowercase() }
        fun indexOf(vararg names: String): Int = header.indexOfFirst { it in names }

        val iTitle = indexOf("judul", "title", "nama", "nama produk")
        val iPrice = indexOf("harga", "price")
        val iCategory = indexOf("kategori", "category")
        val iCondition = indexOf("kondisi", "condition")
        val iDescription = indexOf("deskripsi", "description", "keterangan")
        val iLocation = indexOf("lokasi", "location", "kota")
        val iHashtag = indexOf("hashtag", "hashtags", "tagar")

        if (iTitle < 0 || iPrice < 0) {
            return Result(emptyList(), listOf("Header wajib berisi kolom 'judul' dan 'harga'."))
        }

        val products = mutableListOf<Product>()
        val errors = mutableListOf<String>()

        rows.drop(1).forEachIndexed { i, row ->
            val lineNo = i + 2
            fun col(index: Int): String = if (index in row.indices) row[index].trim() else ""

            val title = col(iTitle)
            val price = parsePrice(col(iPrice))
            if (title.isBlank()) {
                errors += "Baris $lineNo: judul kosong"
                return@forEachIndexed
            }
            if (price == null || price <= 0L) {
                errors += "Baris $lineNo: harga tidak valid (\"${col(iPrice)}\")"
                return@forEachIndexed
            }
            products += Product(
                title = title.take(100),
                price = price,
                category = col(iCategory),
                condition = Conditions.normalize(col(iCondition)),
                description = col(iDescription),
                location = col(iLocation).ifBlank { defaultLocation },
                hashtags = TitleGenerator.normalizeHashtags(col(iHashtag))
            )
        }
        Log.d(TAG, "CSV: ${products.size} produk valid, ${errors.size} baris dilewati")
        return Result(products, errors)
    }

    /** "Rp 150.000" -> 150000, "150000.00" -> 150000, "1,250,000" -> 1250000 */
    fun parsePrice(raw: String): Long? {
        val cleaned = raw.trim().replace(Regex("[.,]\\d{2}$"), "")
        val digits = cleaned.filter { it.isDigit() }
        return digits.toLongOrNull()
    }

    /** Parser CSV sederhana yang mendukung tanda kutip dan baris baru di dalam kutip. */
    fun parseRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }
}
