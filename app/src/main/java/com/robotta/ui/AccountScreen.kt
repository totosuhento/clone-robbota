package com.robotta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotta.data.entities.Account

@Composable
fun AccountScreen(vm: AppViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()

    var storeName by rememberSaveable { mutableStateOf("") }
    var fbProfileName by rememberSaveable { mutableStateOf("") }
    var defaultLocation by rememberSaveable { mutableStateOf("") }
    var whatsapp by rememberSaveable { mutableStateOf("") }
    var footer by rememberSaveable { mutableStateOf("") }

    // Sinkronkan form saat data profil dari database berubah.
    LaunchedEffect(account) {
        storeName = account.storeName
        fbProfileName = account.fbProfileName
        defaultLocation = account.defaultLocation
        whatsapp = account.whatsapp
        footer = account.descriptionFooter
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Kelola Akun", "Dipakai untuk bingkai foto, lokasi default, dan penutup deskripsi.")

        SectionCard(title = "Akun Facebook") {
            Text(
                "Asisten bekerja dengan akun Facebook yang sedang login di aplikasi Facebook HP ini. " +
                    "Untuk ganti akun, ganti langsung di aplikasi Facebook.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = fbProfileName,
                onValueChange = { fbProfileName = it },
                label = { Text("Nama akun FB (catatan)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard(title = "Data toko") {
            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it.take(40) },
                label = { Text("Nama toko (muncul di bingkai foto)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = defaultLocation,
                onValueChange = { defaultLocation = it },
                label = { Text("Lokasi default") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = whatsapp,
                onValueChange = { whatsapp = it },
                label = { Text("Nomor WhatsApp (opsional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = footer,
                onValueChange = { footer = it },
                label = { Text("Penutup deskripsi (opsional)") },
                placeholder = { Text("Mis. Bisa COD area kota. Stok selalu update.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                vm.saveAccount(
                    Account(
                        storeName = storeName.trim(),
                        fbProfileName = fbProfileName.trim(),
                        defaultLocation = defaultLocation.trim(),
                        whatsapp = whatsapp.trim(),
                        descriptionFooter = footer.trim()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Simpan profil") }
    }
}
