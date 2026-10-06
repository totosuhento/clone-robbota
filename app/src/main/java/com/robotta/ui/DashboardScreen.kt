@file:OptIn(ExperimentalLayoutApi::class)

package com.robotta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.data.entities.ProductStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onAddProduct: () -> Unit
) {
    val products by vm.products.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val state by vm.engineState.collectAsStateWithLifecycle()

    val pending = products.count { it.status == ProductStatus.PENDING }
    val success = products.count { it.status == ProductStatus.SUCCESS }
    val failed = products.count { it.status == ProductStatus.FAILED || it.status == ProductStatus.SKIPPED }
    val noPhoto = products.count { it.status == ProductStatus.PENDING && it.imageList().isEmpty() }
    val recent = products.filter { it.postedAt != null }.sortedByDescending { it.postedAt }.take(5)
    val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("id-ID"))

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                "Halo${if (account.storeName.isNotBlank()) ", ${account.storeName}" else ""} 👋",
                "Ringkasan toko Marketplace kamu"
            )
        }

        item {
            SectionCard(
                title = if (accessibilityOn) "Asisten siap" else "Asisten belum aktif",
                containerColor = if (accessibilityOn) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    if (accessibilityOn) {
                        if (state.isActive) "Sesi posting sedang berjalan — buka menu Auto Posting untuk detail."
                        else "Layanan aksesibilitas aktif. Siapkan Data Posting lalu mulai Auto Posting."
                    } else {
                        "Aktifkan layanan aksesibilitas agar asisten bisa mengisi form di aplikasi Facebook."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!accessibilityOn) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenAccessibility) { Text("Aktifkan sekarang") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatBox("Menunggu", pending, Modifier.weight(1f))
                StatBox("Terposting", success, Modifier.weight(1f))
                StatBox("Gagal/lewat", failed, Modifier.weight(1f))
            }
        }

        if (noPhoto > 0) {
            item {
                SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text("$noPhoto produk menunggu belum punya foto. Facebook mewajibkan minimal 1 foto.")
                }
            }
        }

        item {
            SectionCard(title = "Aksi cepat") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddProduct) { Text("Tambah produk") }
                    FilledTonalButton(onClick = { onNavigate(Screen.AUTO_POSTING) }) { Text("Auto Posting") }
                    OutlinedButton(onClick = { onNavigate(Screen.KEYWORDS) }) { Text("Riset Kata Kunci") }
                    OutlinedButton(onClick = { onNavigate(Screen.LOCATION) }) { Text("Riset Lokasi") }
                    OutlinedButton(onClick = { onNavigate(Screen.AUTO_FRAME) }) { Text("Auto Frame") }
                }
            }
        }

        item { Text("Terakhir terposting", style = MaterialTheme.typography.titleMedium) }
        if (recent.isEmpty()) {
            item { Text("Belum ada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(recent, key = { it.id }) { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    p.imageList().firstOrNull()?.let { PhotoThumb(it, 44.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatRupiah(p.price) + " · " + dateFormat.format(Date(p.postedAt ?: 0L)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
