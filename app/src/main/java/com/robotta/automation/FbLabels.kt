package com.robotta.automation

/**
 * Teks tombol/kolom di aplikasi Facebook yang dicari oleh layanan aksesibilitas.
 *
 * Facebook sering mengubah tampilan & kata-katanya. Jika suatu langkah selalu gagal,
 * buka form "Jual Barang" di HP, catat teks persis yang tampil, lalu tambahkan ke
 * daftar yang sesuai di bawah ini (urutan = prioritas). Pencocokan tidak peka huruf besar/kecil.
 */
object FbLabels {
    const val FB_PACKAGE = "com.facebook.katana"

    /** Deep link ke form buat tawaran barang. */
    const val CREATE_ITEM_URL = "https://www.facebook.com/marketplace/create/item"

    val MARKETPLACE = listOf("Marketplace")
    val SELL = listOf("Jual", "Sell", "Buat tawaran baru", "Create new listing", "Buat tawaran", "Create listing")
    val ITEM_FOR_SALE = listOf("Barang untuk dijual", "Barang", "Item for sale", "Items")

    val ADD_PHOTOS = listOf("Tambahkan foto", "Tambah foto", "Tambahkan Foto", "Add photos", "Add photo", "Add Photos")
    val PICKER_DONE = listOf("Selesai", "Done", "Berikutnya", "Next", "Lanjutkan", "Continue", "Tambahkan", "Add")
    val CAMERA = listOf("Kamera", "Camera", "Ambil foto", "Take photo")

    val TITLE = listOf("Judul", "Title")
    val PRICE = listOf("Harga", "Price")
    val CATEGORY = listOf("Kategori", "Category")
    val CONDITION = listOf("Kondisi", "Condition")
    val DESCRIPTION = listOf("Deskripsi", "Description", "Keterangan")
    val LOCATION = listOf("Lokasi", "Location")
    val SEARCH = listOf("Cari", "Search")

    /** Tombol akhir. Layanan TIDAK pernah menekan ini, hanya mendeteksi saat kamu menekannya. */
    val PUBLISH = listOf("Publikasikan", "Publish", "Terbitkan", "Pasang", "Post")
    /** Tombol lanjut di halaman form (sebelum layar Publikasikan). Juga tidak ditekan otomatis. */
    val FORM_NEXT = listOf("Berikutnya", "Next")
}
