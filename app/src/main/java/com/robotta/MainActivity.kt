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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotta.automation.FbLabels
import com.robotta.automation.MarketAutomationService
import com.robotta.ui.AccountScreen
import com.robotta.ui.AddProductScreen
import com.robotta.ui.AppDrawer
import com.robotta.ui.AppViewModel
import com.robotta.ui.AutoFrameScreen
import com.robotta.ui.AutomationScreen
import com.robotta.ui.BrowserModeScreen
import com.robotta.ui.DashboardScreen
import com.robotta.ui.KeywordScreen
import com.robotta.ui.LocationScreen
import com.robotta.ui.MainScreen
import com.robotta.ui.ProductListScreen
import com.robotta.ui.Screen
import com.robotta.ui.StatusPill
import com.robotta.ui.TutorialScreen
import com.robotta.ui.theme.RobottaTheme
import kotlinx.coroutines.launch

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

/** Nilai editingId: null = tidak sedang mengedit, 0 = produk baru, >0 = id produk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenFacebook: () -> Unit
) {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
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

    BackHandler(enabled = drawerState.isOpen || screen != Screen.DASHBOARD) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else screen = Screen.DASHBOARD
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(current = screen) {
                screen = it
                scope.launch { drawerState.close() }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = { StatusPill(accessibilityOn) }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        vm = vm,
                        accessibilityOn = accessibilityOn,
                        onOpenAccessibility = onOpenAccessibility,
                        onNavigate = { screen = it },
                        onAddProduct = { editingId = 0 }
                    )
                    Screen.ACCOUNT -> AccountScreen(vm = vm)
                    Screen.AUTO_POSTING -> MainScreen(
                        vm = vm,
                        accessibilityOn = accessibilityOn,
                        onOpenAccessibility = onOpenAccessibility,
                        onOpenFacebook = onOpenFacebook,
                        onGoToProducts = { screen = Screen.DATA_POSTING }
                    )
                    Screen.BROWSER_MODE -> BrowserModeScreen(vm = vm, onGoToProducts = { screen = Screen.DATA_POSTING })
                    Screen.KEYWORDS -> KeywordScreen(vm = vm)
                    Screen.LOCATION -> LocationScreen(vm = vm)
                    Screen.DATA_POSTING -> ProductListScreen(
                        vm = vm,
                        onAdd = { editingId = 0 },
                        onEdit = { editingId = it }
                    )
                    Screen.AUTO_FRAME -> AutoFrameScreen(vm = vm)
                    Screen.TUTORIAL -> TutorialScreen()
                    Screen.SETTINGS -> AutomationScreen(
                        vm = vm,
                        accessibilityOn = accessibilityOn,
                        onOpenAccessibility = onOpenAccessibility
                    )
                }
            }
        }
    }
}
