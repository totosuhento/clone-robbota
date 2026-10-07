package com.robotta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.ai.ListingOptimizer
import com.robotta.ai.OptimizeResult
import com.robotta.data.entities.Product
import kotlinx.coroutines.launch

/** Menu "Optimasi Postingan FB": skor kelengkapan + saran AI untuk tiap produk. */
@Composable
fun OptimizeScreen(vm: AppViewModel) {
    val products by vm.products.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Product?>(null) }
    var result by remember { mutableStateOf<OptimizeResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    val scored = products.map { it to ListingOptimizer.quickScore(it) }.sortedBy { it.second.score }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenHeader("Optimasi Postingan FB", "Skor kelengkapan tiap produk. Ketuk untuk saran & perbaikan AI.")
        }
        if (scored.isEmpty()) {
            item { Text("Belum ada produk di Data Posting.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(scored, key = { it.first.id }) { (p, r) ->
            SectionCard(modifier = Modifier.clickable {
                selected = p
                result = null
                loading = true
                scope.launch {
                    result = vm.optimizeListing(p)
                    loading = false
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreBadge(r.score)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            r.tips.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    selected?.let { p ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Optimasi: ${p.title.take(40)}") },
            text = {
                val r = result
                if (loading || r == null) {
                    Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Column(
                        Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScoreBadge(r.score)
                            Spacer(Modifier.width(8.dp))
                            Text(if (r.fromAi) "Dinilai AI + aturan" else "Dinilai aturan (isi API key Gemini untuk saran AI)",
                                style = MaterialTheme.typography.labelMedium)
                        }
                        r.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        r.title?.let {
                            Text("Judul usulan", fontWeight = FontWeight.SemiBold)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        r.description?.let {
                            Text("Deskripsi usulan", fontWeight = FontWeight.SemiBold)
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                val r = result
                if (r != null && (r.title != null || r.description != null)) {
                    TextButton(onClick = {
                        vm.applyOptimization(p, r.title, r.description)
                        selected = null
                    }) { Text("Terapkan") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val color = when {
        score >= 80 -> Color(0xFF16A34A)
        score >= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFDC2626)
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text("$score", color = Color.White, fontWeight = FontWeight.Bold)
    }
}
