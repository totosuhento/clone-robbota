@file:OptIn(ExperimentalLayoutApi::class)

package com.robotta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.data.Region

/** Daftar kabupaten/kota yang bisa dicari & difilter per provinsi. */
@Composable
private fun RegionSearchList(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onPick: (Region) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf<String?>(null) }
    var provinces by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<Region>>(emptyList()) }

    LaunchedEffect(Unit) { provinces = vm.provinces() }
    LaunchedEffect(query, province) { results = vm.searchRegions(query, province) }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cari kota / kabupaten") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            item {
                FilterChip(selected = province == null, onClick = { province = null }, label = { Text("Semua") })
            }
            items(provinces) { p ->
                FilterChip(selected = province == p, onClick = { province = if (province == p) null else p }, label = { Text(p) })
            }
        }
        Text(
            "${results.size} lokasi ditemukan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(results, key = { it.displayName + it.province }) { region ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(region) }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(region.displayName, fontWeight = FontWeight.SemiBold)
                        Text(region.province, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** Dialog pemilih lokasi, dipakai di form Data Posting. */
@Composable
fun RegionPickerDialog(vm: AppViewModel, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih lokasi") },
        text = {
            RegionSearchList(vm, Modifier.heightIn(max = 460.dp)) { onPick(it.locationText) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Menu "Riset Lokasi": cari kabupaten/kota se-Indonesia dan jadikan lokasi toko. */
@Composable
fun LocationScreen(vm: AppViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var picked by remember { mutableStateOf<Region?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader("Riset Lokasi", "514 kabupaten/kota se-Indonesia. Pilih lokasi tempat barangmu benar-benar berada.")
        SectionCard {
            Text("Lokasi default sekarang", style = MaterialTheme.typography.labelMedium)
            Text(account.defaultLocation.ifBlank { "Belum diatur" }, fontWeight = FontWeight.SemiBold)
        }
        RegionSearchList(vm, Modifier.weight(1f)) { picked = it }
    }

    picked?.let { region ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(region.displayName) },
            text = {
                Text(
                    "${region.locationText}\n\nJadikan lokasi default untuk produk baru? " +
                        "Saat posting, asisten mengetik \"${region.name}\" di kolom lokasi Facebook lalu memilih saran yang cocok."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDefaultLocation(region.locationText)
                    picked = null
                }) { Text("Jadikan default") }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(region.locationText))
                    picked = null
                }) { Text("Salin") }
            }
        )
    }
}
