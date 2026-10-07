@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.robotta.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.ai.TitleGenerator
import com.robotta.data.FbCategories
import com.robotta.data.entities.Conditions
import com.robotta.data.entities.ProductImages
import com.robotta.image.FrameColors
import com.robotta.image.FrameStyle
import kotlinx.coroutines.launch

private const val MAX_PHOTOS = 10


@Composable
fun AddProductScreen(vm: AppViewModel, productId: Int, onClose: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable(productId) { mutableStateOf(productId == 0) }
    var title by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf(Conditions.NEW) }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var hashtags by rememberSaveable { mutableStateOf("") }
    var photosJson by rememberSaveable { mutableStateOf("[]") }
    var frameStyle by rememberSaveable { mutableStateOf(FrameStyle.SQUARE_WHITE) }
    var frameColorIndex by rememberSaveable { mutableStateOf(0) }

    var busy by remember { mutableStateOf<String?>(null) }
    var titleOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var categoryOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var keywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickedKeywords by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val fbEntries = remember { FbCategories.entries(context) }
    var showErrors by remember { mutableStateOf(false) }

    val photos = ProductImages.decode(photosJson)
    val price = priceText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    LaunchedEffect(productId) {
        if (!loaded && productId > 0) {
            vm.getProduct(productId)?.let { p ->
                title = p.title
                priceText = p.price.toString()
                category = p.category
                condition = p.condition
                description = p.description
                location = p.location
                hashtags = p.hashtags
                photosJson = p.imagePaths
            }
            loaded = true
        }
    }
    // Isi lokasi default dari profil untuk produk baru.
    LaunchedEffect(account.defaultLocation) {
        if (productId == 0 && location.isBlank()) location = account.defaultLocation
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = "Menyimpan foto…"
                val room = (MAX_PHOTOS - photos.size).coerceAtLeast(0)
                val added = vm.importPhotos(uris.take(room))
                photosJson = ProductImages.encode(ProductImages.decode(photosJson) + added)
                busy = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == 0) "Tambah produk" else "Edit produk") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        showErrors = true
                        if (title.isNotBlank() && price > 0L) {
                            vm.saveProduct(
                                id = productId,
                                title = title,
                                price = price,
                                category = category,
                                condition = condition,
                                description = description,
                                location = location,
                                hashtags = hashtags,
                                photos = photos,
                                onSaved = onClose
                            )
                        }
                    },
                    enabled = loaded && busy == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text("Simpan produk") }
            }
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            busy?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ------------------------------------------------ Foto
            SectionCard(title = "Foto (${photos.size}/$MAX_PHOTOS)") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(photos) { i, path ->
                        Box {
                            PhotoThumb(path, 88.dp)
                            if (i == 0) {
                                Text(
                                    "Sampul",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 4.dp)
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xAA000000))
                                    .clickable { photosJson = ProductImages.encode(photos.filterIndexed { idx, _ -> idx != i }) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Hapus foto", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (photos.size < MAX_PHOTOS) {
                        item {
                            Box(
                                Modifier
                                    .size(88.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                                    .clickable {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Tambah foto")
                            }
                        }
                    }
                }
                if (showErrors && photos.isEmpty()) {
                    Text("Facebook mewajibkan minimal 1 foto (boleh disimpan dulu, ditambah nanti).",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (photos.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Bingkai toko", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FrameStyle.entries.forEach { s ->
                            FilterChip(selected = frameStyle == s, onClick = { frameStyle = s }, label = { Text(s.label) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FrameColors.ALL.forEachIndexed { idx, (name, _) ->
                            FilterChip(selected = frameColorIndex == idx, onClick = { frameColorIndex = idx }, label = { Text(name) })
                        }
                    }
                    OutlinedButton(
                        enabled = busy == null,
                        onClick = {
                            scope.launch {
                                busy = "Memberi bingkai…"
                                val accent = FrameColors.ALL[frameColorIndex.coerceIn(0, FrameColors.ALL.lastIndex)].second
                                val framed = vm.applyFrame(photos, frameStyle, if (price > 0) formatRupiah(price) else "", accent)
                                photosJson = ProductImages.encode(framed)
                                busy = null
                            }
                        }
                    ) { Text("Terapkan ke semua foto") }
                    Text(
                        "Mengganti foto dengan versi berbingkai (bukan menambah salinan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ------------------------------------------------ Judul & harga
            SectionCard(title = "Info utama") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Judul / nama produk") },
                    isError = showErrors && title.isBlank(),
                    supportingText = { Text("${title.length}/100") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = busy == null && title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        scope.launch {
                            busy = "Menggenerate konten AI…"
                            vm.generateContent(title, category, condition, description, pickedKeywords.toList())?.let { c ->
                                titleOptions = c.titles
                                if (c.description.isNotBlank()) description = c.description
                                if (c.hashtags.isNotBlank()) hashtags = c.hashtags
                                // Saran AI dicocokkan ke nama kategori Facebook yang sebenarnya.
                                categoryOptions = c.categories.mapNotNull { closestFbCategory(fbEntries, it) }.distinct()
                                if (category.isBlank()) categoryOptions.firstOrNull()?.let { category = it }
                            }
                            busy = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (pickedKeywords.isEmpty()) "Buat Konten AI" else "Buat Konten AI + ${pickedKeywords.size} keyword") }
                Text(
                    "Isi nama produk dulu. AI membuat pilihan judul, deskripsi, label & saran kategori — periksa sebelum disimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (titleOptions.isNotEmpty()) {
                    Text("Ketuk untuk memakai:", style = MaterialTheme.typography.labelMedium)
                    titleOptions.forEach { option ->
                        SuggestionChip(onClick = {
                            title = option.take(100)
                            titleOptions = emptyList()
                        }, label = { Text(option) })
                    }
                }
                // ---------------- Keyword aktual
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    enabled = busy == null && title.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = "Mencari keyword aktual…"
                            val seed = title.split(' ').filter { it.length > 1 }.take(3).joinToString(" ")
                            keywords = vm.researchKeywords(seed, false) { _, _ -> }.map { it.text }.take(20)
                            pickedKeywords = emptySet()
                            busy = null
                        }
                    }
                ) { Text("Cari keyword aktual") }
                if (keywords.isNotEmpty()) {
                    Text("Ketuk keyword untuk memilih (dari pencarian Google & AI):", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        keywords.forEach { k ->
                            FilterChip(
                                selected = k in pickedKeywords,
                                onClick = { pickedKeywords = if (k in pickedKeywords) pickedKeywords - k else pickedKeywords + k },
                                label = { Text(k) }
                            )
                        }
                    }
                    if (pickedKeywords.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = {
                                val extra = pickedKeywords.flatMap { it.split(' ') }
                                    .filter { w -> w.length > 1 && !title.contains(w, ignoreCase = true) }
                                    .distinct()
                                title = (title.trim() + " " + extra.joinToString(" ")).trim().take(100)
                            }) { Text("+ Judul") }
                            FilledTonalButton(onClick = {
                                val line = "Kata kunci: " + pickedKeywords.joinToString(", ")
                                description = if (description.isBlank()) line else description.trimEnd() + "\n\n" + line
                            }) { Text("+ Deskripsi") }
                            FilledTonalButton(onClick = {
                                hashtags = TitleGenerator.normalizeHashtags(hashtags + "," + pickedKeywords.joinToString(","))
                            }) { Text("+ Label") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Harga (Rp)") },
                    isError = showErrors && price <= 0L,
                    supportingText = { if (price > 0) Text(formatRupiah(price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ------------------------------------------------ Kategori & kondisi
            SectionCard(title = "Kategori & kondisi") {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori Facebook") },
                    placeholder = { Text("Ketuk \"Pilih kategori\"") },
                    singleLine = true,
                    isError = category.isNotBlank() && closestFbCategory(fbEntries, category) == null,
                    supportingText = {
                        if (category.isNotBlank() && fbEntries.none { !it.isGroup && it.name.equals(category, true) }) {
                            Text("Belum persis sama dengan daftar Facebook — pilih dari daftar.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { showCategoryPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pilih kategori (daftar Facebook)")
                }
                Spacer(Modifier.height(6.dp))
                if (categoryOptions.isNotEmpty()) {
                    Text("Saran AI:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categoryOptions.forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Kondisi", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Conditions.ALL.forEach { c ->
                        FilterChip(selected = condition == c, onClick = { condition = c }, label = { Text(c) })
                    }
                }
            }

            // ------------------------------------------------ Deskripsi & lokasi
            SectionCard(title = "Deskripsi & lokasi") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    enabled = busy == null && title.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = "AI menulis deskripsi…"
                            vm.generateDescription(title, category, condition, description)?.let { description = it }
                            busy = null
                        }
                    }
                ) { Text(if (description.isBlank()) "Tulis deskripsi saja dengan AI" else "Rapikan deskripsi dengan AI") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hashtags,
                    onValueChange = { hashtags = it },
                    label = { Text("Label / hashtag") },
                    placeholder = { Text("#propolis #madu") },
                    supportingText = { Text("Diisi ke kolom Label produk Facebook & di akhir deskripsi.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (account.buildFooter().isNotBlank()) {
                    Text(
                        "Penutup dari Profil akan ditambahkan otomatis:\n${account.buildFooter()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lokasi barang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showRegionPicker = true }) { Text("Pilih dari daftar kota") }
            }

            Text(
                "Tips: tulis judul & deskripsi yang jujur dan beda untuk tiap produk. Facebook menurunkan atau menolak tawaran yang duplikat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            current = category,
            onDismiss = { showCategoryPicker = false },
            onPick = {
                category = it
                showCategoryPicker = false
            }
        )
    }

    if (showRegionPicker) {
        RegionPickerDialog(
            vm = vm,
            onDismiss = { showRegionPicker = false },
            onPick = {
                location = it
                showRegionPicker = false
            }
        )
    }
}
