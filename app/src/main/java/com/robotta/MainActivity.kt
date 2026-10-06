package com.robotta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotta.automation.FbLabels
import com.robotta.automation.MarketAutomationService
import com.robotta.ui.AccountScreen
import com.robotta.ui.AddProductScreen
import com.robotta.ui.AppViewModel
import com.robotta.ui.AutomationScreen
import com.robotta.ui.MainScreen
import com.robotta.ui.ProductListScreen
import com.robotta.ui.theme.RobottaTheme

class MainActivity : ComponentActivity() {

    private val accessibilityOn = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(TAG, "Hasil izin: $result")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        setContent {
            RobottaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        accessibilityOn = accessibilityOn.value,
                        onOpenAccessibility = ::openAccessibilitySettings,
                        onOpenFacebook = ::openFacebook
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityOn.value = MarketAutomationService.isEnabled(this)
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (needed.isNotEmpty()) {
            try {
                permissionLauncher.launch(needed.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "Gagal meminta izin", e)
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Pilih \"${getString(R.string.accessibility_service_label)}\" lalu aktifkan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Tidak bisa membuka pengaturan aksesibilitas", e)
        }
    }

    private fun openFacebook() {
        val intent = packageManager.getLaunchIntentForPackage(FbLabels.FB_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, "Aplikasi Facebook belum terpasang", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(intent)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    POSTING("Posting", Icons.Filled.PlayArrow),
    PRODUCTS("Produk", Icons.Filled.ShoppingCart),
    PROFILE("Profil", Icons.Filled.Person),
    SETTINGS("Pengaturan", Icons.Filled.Settings)
}

/** Nilai editingId: null = tidak sedang mengedit, 0 = produk baru, >0 = id produk. */
@Composable
private fun AppRoot(
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenFacebook: () -> Unit
) {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.POSTING) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val currentEdit = editingId
    if (currentEdit != null) {
        BackHandler { editingId = null }
        AddProductScreen(vm = vm, productId = currentEdit, onClose = { editingId = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.POSTING -> MainScreen(
                    vm = vm,
                    accessibilityOn = accessibilityOn,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenFacebook = onOpenFacebook,
                    onGoToProducts = { tab = Tab.PRODUCTS }
                )
                Tab.PRODUCTS -> ProductListScreen(
                    vm = vm,
                    onAdd = { editingId = 0 },
                    onEdit = { editingId = it }
                )
                Tab.PROFILE -> AccountScreen(vm = vm)
                Tab.SETTINGS -> AutomationScreen(
                    vm = vm,
                    accessibilityOn = accessibilityOn,
                    onOpenAccessibility = onOpenAccessibility
                )
            }
        }
    }
}
