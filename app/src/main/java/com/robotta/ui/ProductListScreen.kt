@file:OptIn(ExperimentalLayoutApi::class)

package com.robotta.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.data.entities.Product
import com.robotta.data.entities.ProductStatus

private enum class Filter(val label: String) { ALL("Semua"), PENDING("Menunggu"), SUCCESS("Terposting"), FAILED("Gagal/lewat") }

@Composable
fun ProductListScreen(vm: AppViewModel, onAdd: () -> Unit, onEdit: (Int) -> Unit) {
    val products by vm.products.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var toDelete by remember { mutableStateOf<Product?>(null) }
    var showCsvHelp by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    val shown = when (filter) {
        Filter.ALL -> products
        Filter.PENDING -> products.filter { it.status == ProductStatus.PENDING }
        Filter.SUCCESS -> products.filter { it.status == ProductStatus.SUCCESS }
        Filter.FAILED -> products.filter { it.status == ProductStatus.FAILED || it.status == ProductStatus.SKIPPED }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ScreenHeader("Produk", "${products.size} produk tersimpan") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCsvHelp = true }) { Text("Import CSV") }
                    OutlinedButton(onClick = vm::resetFailed) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ulangi yang gagal")
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (products.isEmpty()) "Belum ada produk. Tekan \"Tambah produk\" atau import dari CSV."
                        else "Tidak ada produk di filter ini.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
            items(shown, key = { it.id }) { product ->
                ProductRow(
                    product = product,
                    onClick = { onEdit(product.id) },
                    onDelete = { toDelete = product },
                    onRequeue = { vm.setPending(product) }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Tambah produk") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    toDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus produk?") },
            text = { Text("\"${product.title}\" dan fotonya akan dihapus dari aplikasi. Postingan di Facebook tidak ikut terhapus.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProduct(product)
                    toDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }

    if (showCsvHelp) {
        AlertDialog(
            onDismissRequest = { showCsvHelp = false },
            title = { Text("Format CSV") },
            text = {
                Text(
                    "Baris pertama header. Wajib: judul, harga. Opsional: kategori, kondisi, deskripsi, lokasi.\n\n" +
                        "Contoh:\njudul,harga,kategori,kondisi,deskripsi,lokasi\n" +
                        "Kipas Angin Meja 16 inch,185000,Peralatan Rumah Tangga,Baru,Garansi 1 tahun,Banjar\n\n" +
                        "Dari Excel/Google Sheets: simpan sebagai CSV. Foto ditambahkan per produk setelah import."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCsvHelp = false
                    csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
                }) { Text("Pilih file") }
            },
            dismissButton = { TextButton(onClick = { showCsvHelp = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit, onDelete: () -> Unit, onRequeue: () -> Unit) {
    val photos = product.imageList()
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photos.isNotEmpty()) {
                PhotoThumb(photos.first(), 64.dp)
            } else {
                Box(Modifier.width(64.dp)) {
                    Text("Tanpa\nfoto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    formatRupiah(product.price) + " · " + product.condition +
                        (if (photos.isNotEmpty()) " · ${photos.size} foto" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(product.status)
                    if (product.status == ProductStatus.FAILED || product.status == ProductStatus.SKIPPED) {
                        TextButton(onClick = onRequeue) { Text("Antrekan lagi") }
                    }
                }
                if (product.errorMessage.isNotBlank()) {
                    Text(
                        product.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus")
            }
        }
    }
}
