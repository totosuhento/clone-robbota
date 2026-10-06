@file:OptIn(ExperimentalLayoutApi::class)

package com.robotta.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.image.FrameColors
import com.robotta.image.FrameStyle
import com.robotta.image.GalleryExporter
import kotlinx.coroutines.launch

/**
 * Menu "Auto Frame": beri bingkai/teks toko ke foto lalu simpan ke galeri.
 * Satu foto menghasilkan satu foto berbingkai.
 */
@Composable
fun AutoFrameScreen(vm: AppViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var style by rememberSaveable { mutableStateOf(FrameStyle.COLOR_BORDER) }
    var colorIndex by rememberSaveable { mutableStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(account.storeName) { if (text.isBlank()) text = account.storeName }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                val accent = FrameColors.ALL[colorIndex.coerceIn(0, FrameColors.ALL.lastIndex)].second
                val price = priceText.filter { it.isDigit() }.toLongOrNull()
                results = vm.frameToGallery(uris, style, text.trim(), price?.let { formatRupiah(it) } ?: "", accent)
                busy = false
            }
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Auto Frame", "Rapikan foto produk dengan bingkai & nama toko, lalu simpan ke galeri.")

        SectionCard(title = "Gaya bingkai") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FrameStyle.entries.forEach { s ->
                    FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s.label) })
                }
            }
            Text("Warna", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FrameColors.ALL.forEachIndexed { i, (name, _) ->
                    FilterChip(selected = colorIndex == i, onClick = { colorIndex = i }, label = { Text(name) })
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text("Teks (nama toko)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (style == FrameStyle.PRICE_TAG) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Harga di label (Rp)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            enabled = !busy,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Memproses…" else "Pilih foto & proses (maks 10)") }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        if (results.isNotEmpty()) {
            SectionCard(title = "Hasil (${results.size})") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { PhotoThumb(it, 110.dp) }
                }
                Text(
                    "Tersimpan di galeri: Pictures/${GalleryExporter.FRAME_FOLDER}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Untuk produk di Data Posting, bingkai juga bisa diterapkan langsung dari form produk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
