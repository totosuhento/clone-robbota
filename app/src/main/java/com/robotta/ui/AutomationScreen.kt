package com.robotta.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.data.AppSettings
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun AutomationScreen(vm: AppViewModel, accessibilityOn: Boolean, onOpenAccessibility: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var delayMs by rememberSaveable { mutableStateOf(settings.stepDelayMs.toFloat()) }
    var limit by rememberSaveable { mutableStateOf(settings.sessionLimit.toFloat()) }
    var autoPick by rememberSaveable { mutableStateOf(settings.autoPickPhotos) }
    var showPanel by rememberSaveable { mutableStateOf(settings.showFloatingPanel) }
    var apiKey by rememberSaveable { mutableStateOf(settings.geminiApiKey) }
    var model by rememberSaveable { mutableStateOf(settings.geminiModel) }

    LaunchedEffect(settings) {
        delayMs = settings.stepDelayMs.toFloat()
        limit = settings.sessionLimit.toFloat()
        autoPick = settings.autoPickPhotos
        showPanel = settings.showFloatingPanel
        apiKey = settings.geminiApiKey
        model = settings.geminiModel
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        SectionCard(title = "Layanan aksesibilitas") {
            Text(
                if (accessibilityOn) "Aktif ✓" else "Belum aktif",
                color = if (accessibilityOn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onOpenAccessibility) { Text("Buka pengaturan aksesibilitas") }
            Spacer(Modifier.height(10.dp))
            Text(
                "Layanan sering mati sendiri? Di HP Oppo/Realme/Vivo/Xiaomi, matikan optimasi baterai dan " +
                    "izinkan \"Mulai otomatis\" untuk aplikasi ini, lalu kunci aplikasi di daftar aplikasi terbaru.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                OutlinedButton(onClick = { openSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) {
                    Text("Optimasi baterai")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { openAppDetails(context) }) { Text("Info aplikasi") }
            }
        }

        SectionCard(title = "Sesi posting") {
            Text("Jeda antar langkah: ${delayMs.roundToLong()} ms", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = delayMs,
                onValueChange = { delayMs = (it / 100f).roundToInt() * 100f },
                valueRange = AppSettings.MIN_DELAY.toFloat()..AppSettings.MAX_DELAY.toFloat()
            )
            Text(
                "Naikkan jika HP lambat dan pengisian sering meleset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text("Produk per sesi: ${limit.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = limit,
                onValueChange = { limit = it.roundToInt().toFloat() },
                valueRange = 1f..AppSettings.MAX_SESSION.toFloat(),
                steps = AppSettings.MAX_SESSION - 2
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pilih foto otomatis", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Jika mati atau gagal, kamu diminta memilih foto teratas di galeri.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = autoPick, onCheckedChange = { autoPick = it })
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Panel melayang", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Jendela kontrol kecil di atas Facebook: status, progres, tombol Berikutnya/Lewati/Stop. Bisa digeser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = showPanel, onCheckedChange = { showPanel = it })
            }
        }

        SectionCard(title = "AI judul & deskripsi (Gemini)") {
            Text(
                "Ambil API key gratis di aistudio.google.com/apikey. Tanpa key, dipakai template offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.trim() },
                label = { Text("Model") },
                supportingText = { Text("Default: ${AppSettings.DEFAULT_MODEL}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = vm::testAi, enabled = settings.geminiApiKey.isNotBlank()) {
                Text("Tes koneksi (simpan dulu)")
            }
        }

        Button(
            onClick = {
                vm.saveSettings(
                    AppSettings(
                        stepDelayMs = delayMs.roundToLong().coerceIn(AppSettings.MIN_DELAY, AppSettings.MAX_DELAY),
                        sessionLimit = limit.roundToInt().coerceIn(1, AppSettings.MAX_SESSION),
                        autoPickPhotos = autoPick,
                        showFloatingPanel = showPanel,
                        geminiApiKey = apiKey,
                        geminiModel = model.ifBlank { AppSettings.DEFAULT_MODEL }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Simpan pengaturan") }

        Text(
            "Catatan: Facebook bisa membatasi akun yang memasang banyak tawaran mirip dalam waktu singkat. " +
                "Pasang produk yang benar-benar kamu jual, hindari duplikat, dan sebar postingan sepanjang hari.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Log.w("AutomationScreen", "Tidak bisa membuka $action", e)
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Log.e("AutomationScreen", "Tidak bisa membuka info aplikasi", e)
    }
}
