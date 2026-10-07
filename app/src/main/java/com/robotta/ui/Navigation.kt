package com.robotta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotta.BuildConfig

/** Menu samping, dikelompokkan seperti Robotta. */
enum class Screen(val title: String, val icon: ImageVector, val section: String) {
    DASHBOARD("Dashboard", Icons.Filled.Home, ""),
    ACCOUNT("Kelola Akun", Icons.Filled.Person, ""),
    BROWSER_MODE("Auto Posting FB Marketplace", Icons.Filled.PlayArrow, "BOT FB MARKETPLACE"),
    OPTIMIZE("Optimasi Postingan FB", Icons.Filled.Star, "BOT FB MARKETPLACE"),
    RENEW("Perbarui Postingan", Icons.Filled.Refresh, "BOT FB MARKETPLACE"),
    KEYWORDS("Riset Kata Kunci", Icons.Filled.Search, "BOT FB MARKETPLACE"),
    LOCATION("Riset Lokasi", Icons.Filled.LocationOn, "BOT FB MARKETPLACE"),
    DATA_POSTING("Data Posting", Icons.AutoMirrored.Filled.List, "DATA POSTING"),
    AUTO_FRAME("Auto Frame Manual", Icons.Filled.Build, "DATA POSTING"),
    AUTO_POSTING("Auto Posting (lewat Aplikasi FB)", Icons.Filled.PlayArrow, "LAINNYA"),
    TUTORIAL("Tutorial", Icons.Filled.Info, "BANTUAN"),
    SETTINGS("Pengaturan", Icons.Filled.Settings, "BANTUAN")
}

@Composable
fun AppDrawer(current: Screen, onSelect: (Screen) -> Unit) {
    ModalDrawerSheet {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AM", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Asisten Marketplace", fontWeight = FontWeight.Bold)
                    Text(
                        "V.${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            var lastSection = ""
            Screen.entries.forEach { screen ->
                if (screen.section != lastSection && screen.section.isNotBlank()) {
                    Text(
                        screen.section,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                    )
                }
                lastSection = screen.section
                NavigationDrawerItem(
                    label = { Text(screen.title) },
                    icon = { Icon(screen.icon, contentDescription = null) },
                    selected = screen == current,
                    onClick = { onSelect(screen) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

/** Penanda status di bilah atas: ● ON / ● OFF (layanan aksesibilitas). */
@Composable
fun StatusPill(on: Boolean) {
    val bg = if (on) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (on) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) Color(0xFF16A34A) else Color(0xFF94A3B8))
        )
        Spacer(Modifier.width(6.dp))
        Text(if (on) "ON" else "OFF", color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
