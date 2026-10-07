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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.automation.EngineState
import com.robotta.automation.LogLevel
import com.robotta.automation.LogLine
import com.robotta.data.entities.ProductStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    vm: AppViewModel,
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenFacebook: () -> Unit,
    onGoToProducts: () -> Unit
) {
    val products by vm.products.collectAsStateWithLifecycle()
    val state by vm.engineState.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val pending = products.count { it.status == ProductStatus.PENDING }
    val success = products.count { it.status == ProductStatus.SUCCESS }
    val failed = products.count { it.status == ProductStatus.FAILED || it.status == ProductStatus.SKIPPED }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Auto Posting", "Asisten mengisi form Jual Barang, kamu yang memeriksa & mempublikasikan.") }

        if (!accessibilityOn) {
            item {
                SectionCard(
                    title = "Aktifkan layanan aksesibilitas",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "Asisten butuh izin aksesibilitas untuk mengisi form di aplikasi Facebook. " +
                            "Di Android 13+, jika tombolnya abu-abu: buka Info Aplikasi > ⋮ > Izinkan setelan terbatas.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenAccessibility) { Text("Buka pengaturan aksesibilitas") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Menunggu", pending, Modifier.weight(1f))
                StatTile("Terposting", success, Modifier.weight(1f))
                StatTile("Gagal/lewat", failed, Modifier.weight(1f))
            }
        }

        item {
            EnginePanel(
                state = state,
                pending = pending,
                sessionLimit = settings.sessionLimit,
                accessibilityOn = accessibilityOn,
                onStart = vm::startPosting,
                onNext = vm::next,
                onMarkPosted = vm::markPosted,
                onSkip = vm::skip,
                onRetry = vm::retry,
                onStop = vm::stop,
                onOpenFacebook = onOpenFacebook,
                onGoToProducts = onGoToProducts
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Log aktivitas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (logs.isNotEmpty()) {
                    TextButton(onClick = {
                        val text = logs.joinToString("\n") { l ->
                            timeFormat.format(Date(l.time)) + (l.product?.let { " [$it]" } ?: "") + " " + l.message
                        }
                        clipboard.setText(AnnotatedString(text))
                    }) { Text("Salin log") }
                    TextButton(onClick = vm::clearLogs) { Text("Bersihkan") }
                }
            }
        }
        if (logs.isEmpty()) {
            item {
                Text("Belum ada aktivitas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(logs.asReversed().take(80)) { LogRow(it) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnginePanel(
    state: EngineState,
    pending: Int,
    sessionLimit: Int,
    accessibilityOn: Boolean,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onMarkPosted: () -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onOpenFacebook: () -> Unit,
    onGoToProducts: () -> Unit
) {
    when (state) {
        EngineState.Idle, is EngineState.Finished -> SectionCard(title = "Sesi posting") {
            if (state is EngineState.Finished) {
                Text(
                    "Sesi terakhir: ${state.posted} terposting, ${state.notPosted} tidak.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }
            if (pending == 0) {
                Text("Belum ada produk berstatus Menunggu.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onGoToProducts) { Text("Tambah / import produk") }
            } else {
                val count = minOf(pending, sessionLimit)
                Text(
                    "$count produk akan disiapkan satu per satu. Untuk setiap produk, asisten mengisi form " +
                        "lalu berhenti; kamu periksa dan tekan Publikasikan sendiri.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onStart, enabled = accessibilityOn, modifier = Modifier.fillMaxWidth()) {
                    Text("Mulai sesi ($count produk)")
                }
            }
        }

        is EngineState.Working -> SectionCard(title = "Produk ${state.index}/${state.total} sedang diproses…") {
            LinearProgressIndicator(
                progress = { (state.index - 1).toFloat() / state.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(state.productTitle, fontWeight = FontWeight.SemiBold)
            Text(state.step.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "Jangan sentuh layar sebentar sampai asisten selesai mengisi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onStop) { Text("Berhenti") }
        }

        is EngineState.NeedsUser -> SectionCard(
            title = if (state.autoContinue) "Menunggu kamu (${state.index}/${state.total})" else "Butuh tindakanmu (${state.index}/${state.total})",
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(state.productTitle, fontWeight = FontWeight.SemiBold)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.autoContinue) Button(onClick = onOpenFacebook) { Text("Buka Facebook") }
                if (state.canRetry) Button(onClick = onRetry) { Text("Coba lagi") }
                OutlinedButton(onClick = onMarkPosted) { Text("Sudah saya posting") }
                OutlinedButton(onClick = onSkip) { Text("Lewati") }
                TextButton(onClick = onStop) { Text("Berhenti") }
            }
        }

        is EngineState.AwaitingPublish -> SectionCard(
            title = "Siap dipublikasikan (${state.index}/${state.total})",
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(state.productTitle, fontWeight = FontWeight.SemiBold)
            Text(
                "Buka Facebook, periksa foto & isian, lalu tekan Publikasikan. Asisten mendeteksinya otomatis.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.warnings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Perlu dilengkapi manual:", fontWeight = FontWeight.SemiBold)
                state.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenFacebook) { Text("Buka Facebook") }
                OutlinedButton(onClick = onMarkPosted) { Text("Sudah dipublikasikan") }
                OutlinedButton(onClick = onSkip) { Text("Lewati") }
                TextButton(onClick = onStop) { Text("Berhenti") }
            }
        }

        is EngineState.Posted -> SectionCard(
            title = "Terposting ✓ (${state.index}/${state.total})",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(state.productTitle, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row {
                if (state.hasNext) {
                    Button(onClick = onNext) { Text("Produk berikutnya") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onStop) { Text("Selesai dulu") }
                } else {
                    Button(onClick = onStop) { Text("Selesaikan sesi") }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun LogRow(line: LogLine) {
    val color = when (line.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.secondary
        LogLevel.SUCCESS -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> Color.Unspecified
    }
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(
            timeFormat.format(Date(line.time)) + (line.product?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(line.message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
