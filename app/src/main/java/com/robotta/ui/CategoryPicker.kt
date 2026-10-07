package com.robotta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotta.data.CategoryEntry
import com.robotta.data.FbCategories

/** Kemiripan sederhana nama kategori (untuk mencocokkan saran AI ke daftar Facebook). */
fun categorySimilarity(a: String, b: String): Double {
    fun canon(s: String) = s.lowercase().replace("&", " dan ").replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    val x = canon(a)
    val y = canon(b)
    if (x.isEmpty() || y.isEmpty()) return 0.0
    if (x == y) return 1.0
    if (x.startsWith(y) || y.startsWith(x)) return 0.9
    if (x.contains(y) || y.contains(x)) return 0.8
    val xt = x.split(' ').filter { it.length > 2 && it != "dan" }
    val yt = y.split(' ').filter { it.length > 2 && it != "dan" }
    if (xt.isEmpty() || yt.isEmpty()) return 0.0
    val hit = yt.count { t -> xt.any { it == t || it.startsWith(t) || t.startsWith(it) } }
    return 0.7 * hit / maxOf(xt.size, yt.size)
}

/** Kategori Facebook yang paling mirip dengan [name], atau null bila tidak ada yang cukup mirip. */
fun closestFbCategory(entries: List<CategoryEntry>, name: String): String? =
    entries.filter { !it.isGroup }
        .maxByOrNull { categorySimilarity(it.name, name) }
        ?.takeIf { categorySimilarity(it.name, name) >= 0.45 }
        ?.name

@Composable
fun CategoryPickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val entries = remember { FbCategories.entries(context) }
    val learned = remember { FbCategories.hasLearned(context) }
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) entries else entries.filter { !it.isGroup && it.name.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategori Facebook") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cari kategori") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (learned) "Daftar ini direkam dari Facebook di akunmu ✓"
                    else "Daftar sementara. Akan disamakan otomatis dengan Facebook saat Mode Browser pertama kali dipakai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { (if (it.isGroup) "g:" else "c:") + it.name }) { e ->
                        if (e.isGroup) {
                            Text(
                                e.name,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        } else {
                            Text(
                                e.name + if (e.name.equals(current, ignoreCase = true)) "  ✓" else "",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(e.name) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
