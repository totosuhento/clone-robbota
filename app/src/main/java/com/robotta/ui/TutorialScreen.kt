package com.robotta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class TutorialStep(val title: String, val body: String)

private val STEPS = listOf(
    TutorialStep(
        "Aktifkan layanan aksesibilitas",
        "Pengaturan HP → Aksesibilitas → Aplikasi terinstal → \"Asisten Marketplace\" → Aktifkan. " +
            "Android 13+: jika abu-abu, buka Info Aplikasi → ⋮ → Izinkan setelan terbatas."
    ),
    TutorialStep(
        "Login Facebook di aplikasinya",
        "Asisten memakai akun yang sedang login di aplikasi Facebook. Pakai bahasa Indonesia atau Inggris."
    ),
    TutorialStep(
        "Isi Kelola Akun",
        "Nama toko (untuk bingkai foto), nomor WA, penutup deskripsi, dan lokasi default lewat Riset Lokasi."
    ),
    TutorialStep(
        "Riset kata kunci",
        "Cari istilah yang diketik pembeli, lalu pakai di judul dan hashtag produk."
    ),
    TutorialStep(
        "Buat Data Posting",
        "Satu data = satu produk yang kamu jual. Tambah foto, tekan \"Buat Konten AI\" untuk judul, deskripsi, " +
            "hashtag & saran kategori, lalu sunting seperlunya. Bisa juga import CSV."
    ),
    TutorialStep(
        "Mulai Auto Posting",
        "Asisten membuka form Jual Barang dan mengisinya. Jangan sentuh layar sampai panel melayang " +
            "menampilkan \"Siap\"."
    ),
    TutorialStep(
        "Periksa & publikasikan",
        "Cek foto dan isian di Facebook, lengkapi yang ditandai, lalu tekan Publikasikan. " +
            "Asisten mendeteksinya dan mencatat produk sebagai terposting."
    ),
    TutorialStep(
        "Lanjut produk berikutnya",
        "Tekan \"Berikutnya\" di panel melayang atau notifikasi. Panel bisa digeser dan diperkecil."
    ),
    TutorialStep(
        "Jaga akun tetap aman",
        "Pasang produk asli dengan lokasi sebenarnya, hindari tawaran duplikat, dan sebar postingan sepanjang hari. " +
            "Facebook membatasi akun yang memasang banyak tawaran mirip sekaligus."
    )
)

@Composable
fun TutorialScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenHeader("Tutorial", "Langkah memakai Asisten Marketplace") }
        itemsIndexed(STEPS) { i, step ->
            SectionCard {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(step.title, fontWeight = FontWeight.SemiBold)
                        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
