package com.robotta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.browser.BrowserPostActivity
import com.robotta.data.entities.ProductStatus

/** Menu "Auto Posting (Mode Browser)": Facebook di browser milik aplikasi, foto masuk langsung. */
@Composable
fun BrowserModeScreen(vm: AppViewModel, onGoToProducts: () -> Unit) {
    val context = LocalContext.current
    val products by vm.products.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pending = products.count { it.status == ProductStatus.PENDING }
    val noPhoto = products.count { it.status == ProductStatus.PENDING && it.imageList().isEmpty() }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Mode Browser", "Seperti Robotta: Facebook dibuka di browser aplikasi, foto Data Posting langsung masuk.")

        SectionCard(title = "Cara kerja") {
            Text("1. Login Facebook di browser ini (sekali saja, login tersimpan).", style = MaterialTheme.typography.bodyMedium)
            Text("2. Bot membuka form Tawaran baru, memasukkan foto dari Data Posting, lalu mengisi judul, harga, kategori, kondisi, deskripsi & lokasi.", style = MaterialTheme.typography.bodyMedium)
            Text("3. Kamu periksa, lalu tekan \"Terbitkan\". Bot otomatis lanjut ke produk berikutnya.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tidak perlu layanan aksesibilitas, izin galeri, atau album khusus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "Antrean") {
            Text("$pending produk menunggu · maks ${settings.sessionLimit} per sesi", style = MaterialTheme.typography.bodyMedium)
            if (noPhoto > 0) {
                Text("$noPhoto produk belum punya foto — tambahkan dulu di Data Posting.", color = MaterialTheme.colorScheme.error)
            }
        }

        if (pending == 0) {
            Button(onClick = onGoToProducts, modifier = Modifier.fillMaxWidth()) { Text("Tambah produk di Data Posting") }
        } else {
            Button(onClick = { BrowserPostActivity.start(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Buka Mode Browser & mulai")
            }
        }
        Text(
            "Tampilan memakai Facebook versi web (desktop); cubit untuk memperbesar bila perlu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
