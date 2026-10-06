@file:OptIn(ExperimentalLayoutApi::class)

package com.robotta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.robotta.ai.KeywordIdea
import com.robotta.ai.KeywordSource
import com.robotta.ai.TitleGenerator
import kotlinx.coroutines.launch

/**
 * Menu "Riset Kata Kunci": mencari istilah yang benar-benar diketik pembeli,
 * untuk dipakai di judul & hashtag produk.
 */
@Composable
fun KeywordScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var seed by rememberSaveable { mutableStateOf("") }
    var expand by rememberSaveable { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var results by remember { mutableStateOf<List<KeywordIdea>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenHeader("Riset Kata Kunci", "Cari kata yang sering diketik pembeli untuk judul & hashtag.") }
        item {
            SectionCard {
                OutlinedTextField(
                    value = seed,
                    onValueChange = { seed = it },
                    label = { Text("Kata dasar, mis. ikat pinggang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Perluas a–z", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Lebih banyak ide (±27 pencarian, sekitar 10 detik).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = expand, onCheckedChange = { expand = it })
                }
                Button(
                    enabled = !running && seed.isNotBlank(),
                    onClick = {
                        scope.launch {
                            running = true
                            progress = 0f
                            selected = emptySet()
                            results = vm.researchKeywords(seed, expand) { done, total ->
                                progress = done.toFloat() / total
                            }
                            running = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (running) "Meriset…" else "Riset kata kunci") }
                if (running) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Sumber: saran pencarian Google Indonesia (gratis) + ide AI bila API key Gemini diisi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (results.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${results.size} kata kunci · ${selected.size} dipilih",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selected = if (selected.size == results.size) emptySet() else results.map { it.text }.toSet()
                    }) { Text(if (selected.size == results.size) "Batal pilih" else "Pilih semua") }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = selected.isNotEmpty(), onClick = {
                        clipboard.setText(AnnotatedString(ordered(results, selected).joinToString("\n")))
                    }) { Text("Salin daftar") }
                    OutlinedButton(enabled = selected.isNotEmpty(), onClick = {
                        clipboard.setText(AnnotatedString(TitleGenerator.normalizeHashtags(ordered(results, selected).joinToString(","))))
                    }) { Text("Salin sbg hashtag") }
                }
            }
            items(results, key = { it.text }) { idea ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (idea.text in selected) selected - idea.text else selected + idea.text
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(checked = idea.text in selected, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(idea.text, modifier = Modifier.weight(1f))
                    Text(
                        idea.source.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idea.source == KeywordSource.GOOGLE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

private fun ordered(results: List<KeywordIdea>, selected: Set<String>): List<String> =
    results.map { it.text }.filter { it in selected }
