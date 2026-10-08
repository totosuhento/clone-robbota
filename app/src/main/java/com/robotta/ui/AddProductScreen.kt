@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.robotta.ui

import android.util.Log
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
import com.robotta.data.entities.Product
import com.robotta.data.entities.ProductStatus
import kotlin.random.Random
import com.robotta.data.entities.Conditions
import com.robotta.data.entities.ProductImages
import com.robotta.image.FrameColors
import com.robotta.image.FrameStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Simpan produk") }
                    // ---------- Fitur 2: Buat 10 Variasi (Anti Duplikat) ----------
                    Button(
                        onClick = {
                            scope.launch {
                                busy = "Membuat 10 variasi…"
                                try {
                                    val baseTitle = title.ifBlank { "Produk" }
                                    val basePrice = priceText.ifBlank { "10000" }.filter { it.isDigit() }.toLongOrNull() ?: 10000L
                                    val templates = listOf(
                                        "Jual {t}", "Dijual {t} murah", "{t} original",
                                        "Ready {t}", "{t} termurah", "Promo {t}",
                                        "{t} berkualitas", "Harga spesial {t}",
                                        "Best seller {t}", "{t} limited"
                                    )
                                    // Unicode bold/italic styles
                                    val unicodeStyles = listOf<((String) -> String)?>(
                                        null, // normal
                                        { s -> unicodeBold(s) },
                                        { s -> unicodeItalic(s) },
                                        { s -> unicodeBoldItalic(s) },
                                        { s -> unicodeMonospace(s) },
                                        { s -> unicodeSansBold(s) },
                                        null,
                                        { s -> unicodeBold(s) },
                                        { s -> unicodeItalic(s) },
                                        null
                                    )
                                    val photosJson = ProductImages.encode(photos)
                                    val rng = Random(System.currentTimeMillis())
                                    for (i in 0 until 10) {
                                        val tmpl = templates[i % templates.size]
                                        val rawTitle = tmpl.replace("{t}", baseTitle)
                                        val styleFn = unicodeStyles[i % unicodeStyles.size]
                                        val variantTitle = if (styleFn != null) styleFn(rawTitle) else rawTitle
                                        val priceVariation = 1.0 + (rng.nextDouble() * 0.10 + 0.05) * if (rng.nextBoolean()) 1 else -1
                                        val variantPrice = (basePrice * priceVariation).toLong().coerceAtLeast(1000L)
                                        vm.insertProductDirect(
                                            Product(
                                                title = variantTitle.take(100),
                                                price = variantPrice,
                                                category = category,
                                                condition = condition,
                                                description = description,
                                                location = location,
                                                hashtags = hashtags,
                                                imagePaths = photosJson,
                                                status = ProductStatus.PENDING
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("AddProduct", "Gagal buat variasi", e)
                                } finally {
                                    busy = null
                                }
                            }
                        },
                        enabled = loaded && busy == null && photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buat 10 Variasi (Anti Duplikat)") }
                }
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

// ---------- Unicode helpers untuk variasi judul anti-duplikat ----------

/** Convert ke Unicode bold (𝐁𝐞𝐫𝐚𝐬). */
private fun unicodeBold(text: String): String {
    val map = mapOf(
        'A' to '\uD835\uDC00', 'B' to '\uD835\uDC01', 'C' to '\uD835\uDC02', 'D' to '\uD835\uDC03',
        'E' to '\uD835\uDC04', 'F' to '\uD835\uDC05', 'G' to '\uD835\uDC06', 'H' to '\uD835\uDC07',
        'I' to '\uD835\uDC08', 'J' to '\uD835\uDC09', 'K' to '\uD835\uDC0A', 'L' to '\uD835\uDC0B',
        'M' to '\uD835\uDC0C', 'N' to '\uD835\uDC0D', 'O' to '\uD835\uDC0E', 'P' to '\uD835\uDC0F',
        'Q' to '\uD835\uDC10', 'R' to '\uD835\uDC11', 'S' to '\uD835\uDC12', 'T' to '\uD835\uDC13',
        'U' to '\uD835\uDC14', 'V' to '\uD835\uDC15', 'W' to '\uD835\uDC16', 'X' to '\uD835\uDC17',
        'Y' to '\uD835\uDC18', 'Z' to '\uD835\uDC19',
        'a' to '\uD835\uDC1A', 'b' to '\uD835\uDC1B', 'c' to '\uD835\uDC1C', 'd' to '\uD835\uDC1D',
        'e' to '\uD835\uDC1E', 'f' to '\uD835\uDC1F', 'g' to '\uD835\uDC20', 'h' to '\uD835\uDC21',
        'i' to '\uD835\uDC22', 'j' to '\uD835\uDC23', 'k' to '\uD835\uDC24', 'l' to '\uD835\uDC25',
        'm' to '\uD835\uDC26', 'n' to '\uD835\uDC27', 'o' to '\uD835\uDC28', 'p' to '\uD835\uDC29',
        'q' to '\uD835\uDC2A', 'r' to '\uD835\uDC2B', 's' to '\uD835\uDC2C', 't' to '\uD835\uDC2D',
        'u' to '\uD835\uDC2E', 'v' to '\uD835\uDC2F', 'w' to '\uD835\uDC30', 'x' to '\uD835\uDC31',
        'y' to '\uD835\uDC32', 'z' to '\uD835\uDC33'
    )
    return text.map { map[it] ?: it }.joinToString("")
}

/** Convert ke Unicode italic (𝘉𝘦𝘳𝘢𝘴). */
private fun unicodeItalic(text: String): String {
    val map = mapOf(
        'A' to '\uD835\uDC34', 'B' to '\uD835\uDC35', 'C' to '\uD835\uDC36', 'D' to '\uD835\uDC37',
        'E' to '\uD835\uDC38', 'F' to '\uD835\uDC39', 'G' to '\uD835\uDC3A', 'H' to '\uD835\uDC3B',
        'I' to '\uD835\uDC3C', 'J' to '\uD835\uDC3D', 'K' to '\uD835\uDC3E', 'L' to '\uD835\uDC3F',
        'M' to '\uD835\uDC40', 'N' to '\uD835\uDC41', 'O' to '\uD835\uDC42', 'P' to '\uD835\uDC43',
        'Q' to '\uD835\uDC44', 'R' to '\uD835\uDC45', 'S' to '\uD835\uDC46', 'T' to '\uD835\uDC47',
        'U' to '\uD835\uDC48', 'V' to '\uD835\uDC49', 'W' to '\uD835\uDC4A', 'X' to '\uD835\uDC4B',
        'Y' to '\uD835\uDC4C', 'Z' to '\uD835\uDC4D',
        'a' to '\uD835\uDC4E', 'b' to '\uD835\uDC4F', 'c' to '\uD835\uDC50', 'd' to '\uD835\uDC51',
        'e' to '\uD835\uDC52', 'f' to '\uD835\uDC53', 'g' to '\uD835\uDC54', 'h' to '\uD835\uDC55',
        'i' to '\uD835\uDC56', 'j' to '\uD835\uDC57', 'k' to '\uD835\uDC58', 'l' to '\uD835\uDC59',
        'm' to '\uD835\uDC5A', 'n' to '\uD835\uDC5B', 'o' to '\uD835\uDC5C', 'p' to '\uD835\uDC5D',
        'q' to '\uD835\uDC5E', 'r' to '\uD835\uDC5F', 's' to '\uD835\uDC60', 't' to '\uD835\uDC61',
        'u' to '\uD835\uDC62', 'v' to '\uD835\uDC63', 'w' to '\uD835\uDC64', 'x' to '\uD835\uDC65',
        'y' to '\uD835\uDC66', 'z' to '\uD835\uDC67'
    )
    return text.map { map[it] ?: it }.joinToString("")
}

/** Convert ke Unicode bold italic (𝘽𝙚𝙧𝙖𝙨). */
private fun unicodeBoldItalic(text: String): String {
    val map = mapOf(
        'A' to '\uD835\uDC68', 'B' to '\uD835\uDC69', 'C' to '\uD835\uDC6A', 'D' to '\uD835\uDC6B',
        'E' to '\uD835\uDC6C', 'F' to '\uD835\uDC6D', 'G' to '\uD835\uDC6E', 'H' to '\uD835\uDC6F',
        'I' to '\uD835\uDC70', 'J' to '\uD835\uDC71', 'K' to '\uD835\uDC72', 'L' to '\uD835\uDC73',
        'M' to '\uD835\uDC74', 'N' to '\uD835\uDC75', 'O' to '\uD835\uDC76', 'P' to '\uD835\uDC77',
        'Q' to '\uD835\uDC78', 'R' to '\uD835\uDC79', 'S' to '\uD835\uDC7A', 'T' to '\uD835\uDC7B',
        'U' to '\uD835\uDC7C', 'V' to '\uD835\uDC7D', 'W' to '\uD835\uDC7E', 'X' to '\uD835\uDC7F',
        'Y' to '\uD835\uDC80', 'Z' to '\uD835\uDC81',
        'a' to '\uD835\uDC82', 'b' to '\uD835\uDC83', 'c' to '\uD835\uDC84', 'd' to '\uD835\uDC85',
        'e' to '\uD835\uDC86', 'f' to '\uD835\uDC87', 'g' to '\uD835\uDC88', 'h' to '\uD835\uDC89',
        'i' to '\uD835\uDC8A', 'j' to '\uD835\uDC8B', 'k' to '\uD835\uDC8C', 'l' to '\uD835\uDC8D',
        'm' to '\uD835\uDC8E', 'n' to '\uD835\uDC8F', 'o' to '\uD835\uDC90', 'p' to '\uD835\uDC91',
        'q' to '\uD835\uDC92', 'r' to '\uD835\uDC93', 's' to '\uD835\uDC94', 't' to '\uD835\uDC95',
        'u' to '\uD835\uDC96', 'v' to '\uD835\uDC97', 'w' to '\uD835\uDC98', 'x' to '\uD835\uDC99',
        'y' to '\uD835\uDC9A', 'z' to '\uD835\uDC9B'
    )
    return text.map { map[it] ?: it }.joinToString("")
}

/** Convert ke Unicode monospace (𝙱𝚎𝚛𝚊𝚜). */
private fun unicodeMonospace(text: String): String {
    val map = mapOf(
        'A' to '\uD835\uDC9C', 'B' to '\uD835\uDC9D', 'C' to '\uD835\uDC9E', 'D' to '\uD835\uDC9F',
        'E' to '\uD835\uDCA0', 'F' to '\uD835\uDCA1', 'G' to '\uD835\uDCA2', 'H' to '\uD835\uDCA3',
        'I' to '\uD835\uDCA4', 'J' to '\uD835\uDCA5', 'K' to '\uD835\uDCA6', 'L' to '\uD835\uDCA7',
        'M' to '\uD835\uDCA8', 'N' to '\uD835\uDCA9', 'O' to '\uD835\uDCAA', 'P' to '\uD835\uDCAB',
        'Q' to '\uD835\uDCAC', 'R' to '\uD835\uDCAD', 'S' to '\uD835\uDCAE', 'T' to '\uD835\uDCAF',
        'U' to '\uD835\uDCB0', 'V' to '\uD835\uDCB1', 'W' to '\uD835\uDCB2', 'X' to '\uD835\uDCB3',
        'Y' to '\uD835\uDCB4', 'Z' to '\uD835\uDCB5',
        'a' to '\uD835\uDCB6', 'b' to '\uD835\uDCB7', 'c' to '\uD835\uDCB8', 'd' to '\uD835\uDCB9',
        'e' to '\uD835\uDCBA', 'f' to '\uD835\uDCBB', 'g' to '\uD835\uDCBC', 'h' to '\uD835\uDCBD',
        'i' to '\uD835\uDCBE', 'j' to '\uD835\uDCBF', 'k' to '\uD835\uDCC0', 'l' to '\uD835\uDCC1',
        'm' to '\uD835\uDCC2', 'n' to '\uD835\uDCC3', 'o' to '\uD835\uDCC4', 'p' to '\uD835\uDCC5',
        'q' to '\uD835\uDCC6', 'r' to '\uD835\uDCC7', 's' to '\uD835\uDCC8', 't' to '\uD835\uDCC9',
        'u' to '\uD835\uDCCA', 'v' to '\uD835\uDCCB', 'w' to '\uD835\uDCCC', 'x' to '\uD835\uDCCD',
        'y' to '\uD835\uDCCE', 'z' to '\uD835\uDCCF'
    )
    return text.map { map[it] ?: it }.joinToString("")
}

/** Convert ke Unicode sans-serif bold (𝗕𝗲𝗿𝗮𝘀). */
private fun unicodeSansBold(text: String): String {
    val map = mapOf(
        'A' to '\uD835\uDDD4', 'B' to '\uD835\uDDD5', 'C' to '\uD835\uDDD6', 'D' to '\uD835\uDDD7',
        'E' to '\uD835\uDDD8', 'F' to '\uD835\uDDD9', 'G' to '\uD835\uDDDA', 'H' to '\uD835\uDDDB',
        'I' to '\uD835\uDDDC', 'J' to '\uD835\uDDDD', 'K' to '\uD835\uDDDE', 'L' to '\uD835\uDDDF',
        'M' to '\uD835\uDDE0', 'N' to '\uD835\uDDE1', 'O' to '\uD835\uDDE2', 'P' to '\uD835\uDDE3',
        'Q' to '\uD835\uDDE4', 'R' to '\uD835\uDDE5', 'S' to '\uD835\uDDE6', 'T' to '\uD835\uDDE7',
        'U' to '\uD835\uDDE8', 'V' to '\uD835\uDDE9', 'W' to '\uD835\uDDEA', 'X' to '\uD835\uDDEB',
        'Y' to '\uD835\uDDEC', 'Z' to '\uD835\uDDED',
        'a' to '\uD835\uDDEE', 'b' to '\uD835\uDDEF', 'c' to '\uD835\uDDF0', 'd' to '\uD835\uDDF1',
        'e' to '\uD835\uDDF2', 'f' to '\uD835\uDDF3', 'g' to '\uD835\uDDF4', 'h' to '\uD835\uDDF5',
        'i' to '\uD835\uDDF6', 'j' to '\uD835\uDDF7', 'k' to '\uD835\uDDF8', 'l' to '\uD835\uDDF9',
        'm' to '\uD835\uDDFA', 'n' to '\uD835\uDDFB', 'o' to '\uD835\uDDFC', 'p' to '\uD835\uDDFD',
        'q' to '\uD835\uDDFE', 'r' to '\uD835\uDDFF', 's' to '\uD835\uDE00', 't' to '\uD835\uDE01',
        'u' to '\uD835\uDE02', 'v' to '\uD835\uDE03', 'w' to '\uD835\uDE04', 'x' to '\uD835\uDE05',
        'y' to '\uD835\uDE06', 'z' to '\uD835\uDE07'
    )
    return text.map { map[it] ?: it }.joinToString("")
}
