package com.robotta.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.robotta.ai.TitleGenerator
import com.robotta.data.AppDatabase
import com.robotta.data.SettingsStore
import com.robotta.data.entities.Account
import com.robotta.data.entities.Conditions
import com.robotta.data.entities.Product
import com.robotta.data.entities.ProductStatus
import com.robotta.ui.theme.RobottaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Mode Browser: Facebook dibuka di browser milik aplikasi (seperti Robotta).
 * Foto dari Data Posting dimasukkan langsung ke form web, tanpa lewat galeri.
 *
 * Satu akun (yang kamu login-kan sendiri di sini), satu tawaran per produk,
 * dan setiap produk tayang hanya setelah kamu menekan "Terbitkan".
 */
class BrowserPostActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val status = mutableStateOf("Memuat Facebook…")
    private val progress = mutableStateOf("")
    private val notes = mutableStateOf<List<String>>(emptyList())
    private val phase = mutableStateOf(Phase.LOADING)

    private var queue: List<Product> = emptyList()
    private var index = 0
    private var posted = 0
    private var account = Account()
    private var fillJob: Job? = null
    private var advanceJob: Job? = null
    private var fillScript: String = ""
    private var lastFilledUrl: String? = null

    /** Foto produk yang sedang diisi, untuk AsistenBridge.photo(i). */
    @Volatile
    private var currentPhotos: List<String> = emptyList()

    /** Pemilih file manual (bila pengguna menekan "Tambahkan foto" sendiri). */
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    enum class Phase { LOADING, LOGIN, READY, FILLING, REVIEW, POSTED, DONE, RENEW_SCAN, RENEW_READY, RENEWING }

    /** Mode "Perbarui Postingan": memperbarui tawaran lama di halaman Tawaran Anda. */
    private var renewMode = false
    private val renewCount = mutableStateOf(0)
    private val confirmRenew = mutableStateOf(false)
    private var renewScanned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fillScript = try {
            assets.open("fb_fill.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Skrip pengisi tidak bisa dibaca", e)
            ""
        }
        webView = buildWebView()
        renewMode = intent.getBooleanExtra(EXTRA_RENEW, false)
        setContent { RobottaTheme { Screen() } }
        if (renewMode) startRenewMode() else loadQueueAndStart()
    }

    override fun onDestroy() {
        fillJob?.cancel()
        advanceJob?.cancel()
        try {
            CookieManager.getInstance().flush()
            webView.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menutup WebView", e)
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    @androidx.compose.runtime.Composable
    private fun Screen() {
        BackHandler {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (progress.value.isNotBlank()) {
                        Text(progress.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        status.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    notes.value.takeIf { it.isNotEmpty() }?.let { list ->
                        Text(
                            list.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (phase.value in setOf(Phase.FILLING, Phase.LOADING, Phase.RENEW_SCAN, Phase.RENEWING)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (phase.value) {
                            Phase.LOGIN -> Button(onClick = { if (renewMode) startRenewMode() else openCurrentForm() }) {
                                Text("Sudah login, mulai")
                            }
                            Phase.READY -> Button(onClick = ::openCurrentForm) { Text("Isi produk ini") }
                            Phase.REVIEW -> OutlinedButton(onClick = ::fillAgain) { Text("Isi ulang") }
                            Phase.POSTED -> Button(onClick = ::nextProduct) { Text("Berikutnya sekarang") }
                            Phase.DONE -> Button(onClick = ::finish) { Text("Tutup") }
                            Phase.RENEW_READY -> {
                                if (renewCount.value > 0) {
                                    Button(onClick = { confirmRenew.value = true }) {
                                        Text("Perbarui semua (${minOf(renewCount.value, MAX_RENEW)})")
                                    }
                                }
                                OutlinedButton(onClick = ::scanRenewAgain) { Text("Pindai ulang") }
                            }
                            else -> Unit
                        }
                        if (renewMode && phase.value != Phase.DONE) {
                            TextButton(onClick = ::finish) { Text("Tutup") }
                        }
                        if (!renewMode && phase.value != Phase.DONE && queue.isNotEmpty()) {
                            TextButton(onClick = ::skipProduct) { Text("Lewati") }
                            TextButton(onClick = ::finishSession) { Text("Selesai") }
                        }
                    }
                }
            }
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }
        if (confirmRenew.value) {
            val n = minOf(renewCount.value, MAX_RENEW)
            AlertDialog(
                onDismissRequest = { confirmRenew.value = false },
                title = { Text("Perbarui $n tawaran?") },
                text = {
                    Text(
                        "Bot akan menekan \"Perbarui\" pada $n tawaranmu yang sudah bisa diperbarui, satu per satu " +
                            "dengan jeda. Ini fitur bawaan Facebook untuk menaikkan kembali tawaran lama."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRenew.value = false
                        runRenew(n)
                    }) { Text("Perbarui") }
                },
                dismissButton = { TextButton(onClick = { confirmRenew.value = false }) { Text("Batal") } }
            )
        }
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Versi web desktop: form Marketplace lengkap dan tidak mengalihkan ke aplikasi Facebook.
        settings.userAgentString = DESKTOP_UA
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), "AsistenBridge")

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val scheme = url.scheme ?: ""
                // Jangan biarkan link membuka aplikasi Facebook / aplikasi lain.
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                currentHost = Uri.parse(url ?: "").host ?: ""
            }

            override fun onPageFinished(view: WebView, url: String?) {
                currentHost = Uri.parse(url ?: "").host ?: ""
                CookieManager.getInstance().flush()
                onPageReady(url ?: "")
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                // Facebook berpindah halaman tanpa memuat ulang (SPA).
                currentHost = Uri.parse(url ?: "").host ?: ""
                onPageReady(url ?: "")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Pengguna menekan "Tambahkan foto" sendiri: tetap bisa memilih dari galeri.
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    pickImages.launch("image/*")
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }
    }

    @Volatile
    private var currentHost: String = ""

    private fun isFacebookHost(host: String) = host == "facebook.com" || host.endsWith(".facebook.com")

    private fun onPageReady(url: String) {
        val path = Uri.parse(url).path ?: ""
        when {
            path.contains("/login") || path.contains("/checkpoint") -> {
                phase.value = Phase.LOGIN
                status.value = "Login ke Facebook dulu (sekali saja). Setelah masuk, tekan \"Sudah login, mulai\"."
            }
            renewMode && path.startsWith("/marketplace/you") -> {
                if (!renewScanned && (phase.value == Phase.LOADING || phase.value == Phase.LOGIN)) {
                    renewScanned = true
                    scanRenew()
                }
            }
            path.startsWith("/marketplace/create/item") -> {
                val canStart = phase.value == Phase.LOADING || phase.value == Phase.READY || phase.value == Phase.LOGIN
                if (canStart && lastFilledUrl != url && current() != null) {
                    lastFilledUrl = url
                    startFill()
                }
            }
        }
    }

    // ---------------------------------------------------------------- perbarui postingan

    private fun startRenewMode() {
        progress.value = "Perbarui Postingan"
        phase.value = Phase.LOADING
        status.value = "Membuka Tawaran Anda…"
        renewScanned = false
        webView.loadUrl(SELLING_URL)
    }

    private fun scanRenew() {
        lifecycleScope.launch {
            phase.value = Phase.RENEW_SCAN
            status.value = "Mencari tawaran yang bisa diperbarui… jangan sentuh layar."
            delay(3_000L)
            webView.evaluateJavascript(fillScript, null)
            webView.evaluateJavascript("window.__asisten && window.__asisten.scanRenew();", null)
        }
    }

    private fun scanRenewAgain() {
        renewScanned = true
        scanRenew()
    }

    private fun runRenew(max: Int) {
        phase.value = Phase.RENEWING
        status.value = "Memperbarui tawaran… jangan sentuh layar."
        webView.evaluateJavascript("window.__asisten && window.__asisten.renewAll($max);", null)
    }

    // ---------------------------------------------------------------- sesi

    private fun loadQueueAndStart() {
        lifecycleScope.launch {
            val settings = SettingsStore(this@BrowserPostActivity).load()
            val (pending, profile) = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(this@BrowserPostActivity)
                db.productDao().getByStatus(ProductStatus.PENDING) to (db.accountDao().getProfile() ?: Account())
            }
            account = profile
            queue = pending.take(settings.sessionLimit)
            index = 0
            if (queue.isEmpty()) {
                phase.value = Phase.DONE
                status.value = "Tidak ada produk berstatus Menunggu di Data Posting."
                webView.loadUrl("https://www.facebook.com/")
                return@launch
            }
            openCurrentForm()
        }
    }

    private fun current(): Product? = queue.getOrNull(index)

    private fun openCurrentForm() {
        val p = current() ?: run { finishSession(); return }
        advanceJob?.cancel()
        notes.value = emptyList()
        progress.value = "Produk ${index + 1}/${queue.size}: ${p.title}"
        phase.value = Phase.LOADING
        status.value = "Membuka form Tawaran baru…"
        lastFilledUrl = null
        webView.loadUrl(CREATE_URL)
    }

    private fun fillAgain() {
        lastFilledUrl = null
        phase.value = Phase.READY
        startFill()
    }

    private fun startFill() {
        val p = current() ?: return
        if (fillScript.isBlank()) {
            status.value = "Skrip pengisi tidak tersedia."
            return
        }
        fillJob?.cancel()
        fillJob = lifecycleScope.launch {
            phase.value = Phase.FILLING
            status.value = "Mengisi otomatis… jangan sentuh layar."
            notes.value = emptyList()
            currentPhotos = p.imageList().filter { File(it).exists() }
            delay(2_500L)
            val data = JSONObject()
                .put("title", p.title)
                .put("price", p.price)
                .put("category", p.category)
                .put("categoryMain", p.category.split('&', ',', '/').first().trim())
                .put("conditionLabels", JSONArray(Conditions.facebookLabels(p.condition)))
                .put("description", buildDescription(p))
                .put("location", p.location.ifBlank { account.defaultLocation })
                .put("autoNext", true)
            webView.evaluateJavascript(fillScript, null)
            webView.evaluateJavascript("window.__asisten && window.__asisten.run($data);", null)
        }
    }

    private fun buildDescription(p: Product): String =
        listOf(p.description.trim(), account.buildFooter(), TitleGenerator.normalizeHashtags(p.hashtags))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    private fun onFillFinished(json: String) {
        try {
            val o = JSONObject(json)
            if (!o.optBoolean("form")) {
                phase.value = Phase.READY
                status.value = "Form belum terbuka. Pastikan sudah login, lalu tekan \"Isi produk ini\"."
                return
            }
            phase.value = Phase.REVIEW
            status.value = if (notes.value.isEmpty()) {
                "Form terisi ✓  Periksa, lalu tekan \"Terbitkan\" di bawah form."
            } else {
                "Form terisi sebagian. Lengkapi yang ditandai, lalu tekan \"Terbitkan\"."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hasil pengisian tidak terbaca: $json", e)
        }
    }

    private fun onPublished() {
        val p = current() ?: return
        if (phase.value == Phase.POSTED) return
        phase.value = Phase.POSTED
        posted++
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.get(this@BrowserPostActivity).productDao()
                    .updateStatus(p.id, ProductStatus.SUCCESS, "", System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menyimpan status", e)
            }
        }
        val hasNext = index + 1 < queue.size
        advanceJob = lifecycleScope.launch {
            for (s in 8 downTo 1) {
                status.value = if (hasNext) "Terbit ✓  Produk berikutnya dalam $s detik…" else "Terbit ✓  Semua produk selesai."
                if (!hasNext) break
                delay(1_000L)
            }
            if (hasNext) nextProduct() else finishSession()
        }
    }

    private fun nextProduct() {
        advanceJob?.cancel()
        index++
        if (index >= queue.size) finishSession() else openCurrentForm()
    }

    private fun skipProduct() {
        val p = current()
        if (p != null && phase.value != Phase.POSTED) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    AppDatabase.get(this@BrowserPostActivity).productDao()
                        .updateStatus(p.id, ProductStatus.SKIPPED, "Dilewati (Mode Browser)", null)
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal menyimpan status", e)
                }
            }
        }
        nextProduct()
    }

    private fun finishSession() {
        fillJob?.cancel()
        advanceJob?.cancel()
        phase.value = Phase.DONE
        progress.value = ""
        status.value = "Sesi selesai: $posted produk terbit."
    }

    // ---------------------------------------------------------------- jembatan JS

    private inner class Bridge {
        private fun allowed() = isFacebookHost(currentHost)

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "JS: $message")
        }

        @JavascriptInterface
        fun step(name: String, ok: Boolean, note: String) {
            Log.d(TAG, "Langkah $name: ${if (ok) "OK" else "GAGAL"} $note")
            if (!ok) runOnUiThread { notes.value = notes.value + "$name: $note" }
            else runOnUiThread { status.value = "Mengisi… $name ✓" }
        }

        @JavascriptInterface
        fun finished(json: String) {
            runOnUiThread { onFillFinished(json) }
        }

        @JavascriptInterface
        fun published() {
            if (!allowed()) return
            runOnUiThread { onPublished() }
        }

        @JavascriptInterface
        fun renewFound(count: Int) {
            runOnUiThread {
                renewCount.value = count
                phase.value = Phase.RENEW_READY
                status.value = if (count > 0) {
                    "Ditemukan $count tawaran yang bisa diperbarui."
                } else {
                    "Belum ada tawaran yang bisa diperbarui (Facebook mengizinkan perbarui setelah beberapa hari)."
                }
            }
        }

        @JavascriptInterface
        fun renewProgress(done: Int) {
            runOnUiThread { status.value = "Memperbarui… $done tawaran selesai." }
        }

        @JavascriptInterface
        fun renewFinished(done: Int) {
            runOnUiThread {
                phase.value = Phase.DONE
                status.value = "Selesai: $done tawaran diperbarui ✓"
            }
        }

        @JavascriptInterface
        fun photoCount(): Int = if (allowed()) currentPhotos.size else 0

        /** Foto ke-i sebagai base64 JPEG (hanya untuk halaman facebook.com). */
        @JavascriptInterface
        fun photo(i: Int): String {
            if (!allowed()) return ""
            return try {
                val f = File(currentPhotos.getOrNull(i) ?: return "")
                Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membaca foto $i", e)
                ""
            }
        }
    }

    companion object {
        private const val TAG = "BrowserPost"
        const val CREATE_URL = "https://www.facebook.com/marketplace/create/item"
        const val SELLING_URL = "https://www.facebook.com/marketplace/you/selling"
        const val EXTRA_RENEW = "renew"
        const val MAX_RENEW = 50
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36"

        fun start(activity: android.content.Context, renew: Boolean = false) {
            try {
                activity.startActivity(Intent(activity, BrowserPostActivity::class.java).putExtra(EXTRA_RENEW, renew))
            } catch (e: Exception) {
                Toast.makeText(activity, "Mode Browser tidak bisa dibuka: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
