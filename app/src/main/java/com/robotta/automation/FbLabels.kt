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
    /** Facebook Lite tidak didukung: tampilannya digambar sendiri, tidak terbaca layanan aksesibilitas. */
    const val FB_LITE_PACKAGE = "com.facebook.lite"

    /** Deep link ke form buat tawaran barang. */
    const val CREATE_ITEM_URL = "https://www.facebook.com/marketplace/create/item"
    /** Beranda Marketplace (jalur cadangan bila link form langsung tidak dibuka aplikasi). */
    const val MARKETPLACE_URL = "https://www.facebook.com/marketplace/"

    val MARKETPLACE = listOf("Marketplace")
    /** Tombol Menu (☰) — jalur cadangan bila tab Marketplace tidak ada di bilah navigasi. */
    val MENU = listOf("Menu")
    /** Tombol/chip "Jual" di halaman Marketplace. */
    val SELL = listOf("Jual", "Sell")
    /** Di halaman "Jual": tombol membuat tawaran. */
    val CREATE_LISTING = listOf(
        "Buat tawaran baru", "Buat Tawaran Baru", "Buat tawaran", "Tawaran baru", "Buat penawaran",
        "Pasang tawaran", "Jual sesuatu", "Tawarkan barang", "Buat baru",
        "Create new listing", "Create listing", "New listing", "Sell something"
    )
    /** Pilihan jenis tawaran di lembar "Jual barang" (dicari persis). */
    val ITEM_FOR_SALE = listOf(
        "Satu item", "Single item", "Barang untuk dijual", "Barang dijual", "Item for sale", "Barang", "Item"
    )

    val ADD_PHOTOS = listOf("Tambahkan foto", "Tambah foto", "Tambahkan Foto", "Add photos", "Add photo", "Add Photos")
    /** Tombol konfirmasi di galeri foto (dicari persis). */
    val PICKER_DONE = listOf(
        "Selesai", "Done", "Berikutnya", "Next", "Lanjut", "Lanjutkan", "Selanjutnya", "Continue",
        "Tambahkan", "Tambah", "Add", "OK", "Unggah", "Upload"
    )
    /** Tombol konfirmasi yang memuat jumlah, mis. "Tambahkan (2)" / "Add 2". */
    val PICKER_DONE_PREFIX = listOf("Tambahkan", "Tambah", "Add", "Selesai", "Done", "Berikutnya", "Next")
    val CAMERA = listOf("Kamera", "Camera", "Ambil foto", "Take photo")
    /** Bila "Tambahkan foto" memunculkan pilihan sumber dulu. */
    val GALLERY_OPTION = listOf("Pilih dari galeri", "Pilih dari Galeri", "Galeri", "Gallery", "Choose from gallery", "Unggah foto")

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
