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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.robotta.browser.BrowserPostActivity

/** Menu "Perbarui Postingan": menaikkan kembali tawaran lama lewat fitur Perbarui bawaan Facebook. */
@Composable
fun RenewScreen() {
    val context = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Perbarui Postingan", "Naikkan lagi tawaran lama di Marketplace dengan tombol Perbarui bawaan Facebook.")
        SectionCard(title = "Cara kerja") {
            Text("1. Bot membuka halaman Tawaran Anda di Mode Browser (pakai login yang sama).", style = MaterialTheme.typography.bodyMedium)
            Text("2. Bot menghitung tawaran yang sudah bisa diperbarui.", style = MaterialTheme.typography.bodyMedium)
            Text("3. Kamu konfirmasi sekali, lalu bot menekan \"Perbarui\" satu per satu (maks ${BrowserPostActivity.MAX_RENEW}).", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Facebook hanya mengizinkan perbarui untuk tawaran yang sudah berumur beberapa hari.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = { BrowserPostActivity.start(context, renew = true) }, modifier = Modifier.fillMaxWidth()) {
            Text("Buka & pindai tawaran")
        }
    }
}
