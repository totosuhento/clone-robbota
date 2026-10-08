package com.robotta.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Profil toko / akun Facebook. Mendukung banyak akun, masing-masing dengan cookies
 * untuk login ulang otomatis.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "",
    /** Catatan nama akun FB yang login, hanya untuk pengingat. */
    val fbProfileName: String = "",
    val defaultLocation: String = "",
    val whatsapp: String = "",
    /** Ditambahkan di akhir setiap deskripsi produk. */
    val descriptionFooter: String = "",
    /** Data cookies dari WebView login (format JSON). */
    val cookiesData: String = "",
    /** Apakah akun ini yang aktif dipakai untuk posting. */
    val isActive: Boolean = false,
    /** Timestamp terakhir akun ini dipakai. */
    val lastUsedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun buildFooter(): String {
        val lines = mutableListOf<String>()
        if (descriptionFooter.isNotBlank()) lines += descriptionFooter.trim()
        if (whatsapp.isNotBlank()) lines += "WA: ${whatsapp.trim()}"
        return lines.joinToString("\n")
    }

    companion object {
        const val PROFILE_ID = 1
    }
}