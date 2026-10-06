package com.robotta.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Profil toko. Aplikasi bekerja dengan SATU akun Facebook, yaitu akun yang
 * sedang login di aplikasi Facebook pada HP ini. Profil ini menyimpan data
 * pelengkap yang dipakai saat mengisi form dan membuat bingkai foto.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: Int = PROFILE_ID,
    val storeName: String = "",
    /** Catatan nama akun FB yang login, hanya untuk pengingat. */
    val fbProfileName: String = "",
    val defaultLocation: String = "",
    val whatsapp: String = "",
    /** Ditambahkan di akhir setiap deskripsi produk. */
    val descriptionFooter: String = "",
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
