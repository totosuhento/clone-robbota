package com.robotta.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.robotta.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layanan aksesibilitas yang mengisi form "Jual Barang" di aplikasi Facebook.
 *
 * Prinsip:
 *  - Hanya bertindak saat AutomationEngine memintanya (sesi yang kamu mulai).
 *  - Tidak pernah menekan tombol Publikasikan/Berikutnya di form; kamu yang memeriksa
 *    dan mempublikasikan. Layanan hanya MENDETEKSI saat kamu menekan Publikasikan.
 */
class MarketAutomationService : AccessibilityService() {

    // ---------------------------------------------------------------- lifecycle

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var panel: FloatingPanel? = null
    private var ownAppForeground = false
    private var wasActive = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Layanan aksesibilitas tersambung")
        panel = FloatingPanel(this)
        uiScope.launch {
            AutomationEngine.state.collect { state ->
                if (state.isActive && !wasActive) panel?.resetDismissed()
                wasActive = state.isActive
                updatePanel()
            }
        }
        AutomationEngine.onServiceConnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Layanan aksesibilitas dilepas")
        releasePanel()
        instance = null
        AutomationEngine.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releasePanel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun releasePanel() {
        panel?.hide()
        panel = null
        uiScope.cancel()
    }

    /** Panel tampil saat sesi aktif, kecuali sedang membuka aplikasi ini sendiri. */
    private fun updatePanel() {
        val p = panel ?: return
        val state = AutomationEngine.state.value
        val enabled = try {
            SettingsStore(this).load().showFloatingPanel
        } catch (e: Exception) {
            true
        }
        if (enabled && state.isActive && !ownAppForeground && !p.dismissed) {
            p.show()
            p.render(state)
        } else {
            p.hide()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val active = rootInActiveWindow?.packageName?.toString()
                if (active != null && (active == packageName) != ownAppForeground) {
                    ownAppForeground = active == packageName
                    updatePanel()
                }
            }
            if (event.packageName?.toString() != FbLabels.FB_PACKAGE) return
            if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
            if (!AutomationEngine.isAwaitingPublish()) return

            val pieces = mutableListOf<String>()
            event.text?.forEach { it?.let { cs -> pieces += cs.toString() } }
            event.contentDescription?.let { pieces += it.toString() }
            event.source?.let { pieces += NodeFinder.texts(it) }

            val isPublish = pieces.any { text ->
                FbLabels.PUBLISH.any { label -> NodeFinder.matchText(text, label, MatchMode.EXACT) }
            }
            if (isPublish) {
                Log.d(TAG, "Pengguna menekan Publikasikan: $pieces")
                AutomationEngine.onPublishClicked()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error memproses event", e)
        }
    }

    // ---------------------------------------------------------------- pembaca layar

    /** Root semua jendela aplikasi yang tampil (bukan keyboard, bukan system UI, bukan aplikasi ini). */
    fun roots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows?.forEach { w ->
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                val root = w.root ?: return@forEach
                val pkg = root.packageName?.toString()
                if (pkg != packageName && pkg != "com.android.systemui") list += root
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca daftar jendela", e)
        }
        if (list.isEmpty()) {
            rootInActiveWindow?.let { root ->
                if (root.packageName?.toString() != packageName) list += root
            }
        }
        return list
    }

    fun isFacebookForeground(): Boolean =
        roots().any { it.packageName?.toString() == FbLabels.FB_PACKAGE }

    suspend fun <T> waitFor(timeoutMs: Long, pollMs: Long = 400L, block: (List<AccessibilityNodeInfo>) -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val result = try {
                block(roots())
            } catch (e: Exception) {
                Log.w(TAG, "waitFor: ${e.message}")
                null
            }
            if (result != null) return result
            if (SystemClock.uptimeMillis() >= end) return null
            delay(pollMs)
        }
    }

    fun isFormVisible(roots: List<AccessibilityNodeInfo> = roots()): Boolean {
        val fbRoots = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
        if (fbRoots.isEmpty()) return false
        return NodeFinder.findEditable(fbRoots, FbLabels.TITLE) != null ||
            NodeFinder.findEditable(fbRoots, FbLabels.PRICE) != null
    }

    // ---------------------------------------------------------------- aksi dasar

    /** Klik node; jika tidak bisa diklik, naik ke induk yang bisa diklik; terakhir: ketuk koordinat. */
    fun clickNode(node: AccessibilityNodeInfo, climb: Boolean = true): Boolean {
        try {
            var current: AccessibilityNodeInfo? = node
            var depth = 0
            val maxDepth = if (climb) 5 else 0
            while (current != null && depth <= maxDepth) {
                if (current.isClickable && current.isEnabled &&
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
                current = current.parent
                depth++
            }
            val r = NodeFinder.bounds(node)
            if (r.isEmpty) return false
            return tapAt(r.exactCenterX(), r.exactCenterY())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal klik ${NodeFinder.label(node)}", e)
            return false
        }
    }

    fun tapAt(x: Float, y: Float): Boolean = try {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        dispatchGesture(gesture, null, null)
    } catch (e: Exception) {
        Log.e(TAG, "Gagal ketuk ($x,$y)", e)
        false
    }

    fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true

            // Cadangan: tempel lewat clipboard.
            Log.d(TAG, "SET_TEXT gagal, coba tempel dari clipboard")
            val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText("asisten", value))
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val currentLength = node.text?.length ?: 0
            if (currentLength > 0) {
                val sel = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentLength)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengisi teks", e)
            return false
        }
    }

    fun scrollForward(): Boolean = try {
        val target = roots()
            .filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
            .flatMap { NodeFinder.findAll(it) { n -> n.isScrollable } }
            .maxByOrNull { NodeFinder.area(it) }
        target?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    } catch (e: Exception) {
        Log.w(TAG, "Gagal scroll", e)
        false
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    suspend fun tapByLabels(labels: List<String>, timeoutMs: Long, maxMode: MatchMode = MatchMode.STARTS_WITH): Boolean {
        val node = waitFor(timeoutMs) { NodeFinder.findByLabels(it, labels, maxMode) } ?: run {
            Log.d(TAG, "Tidak ketemu: $labels")
            return false
        }
        Log.d(TAG, "Tap: ${NodeFinder.label(node)}")
        return clickNode(node)
    }

    // ---------------------------------------------------------------- navigasi Facebook

    fun launchFacebookApp(): Boolean = try {
        val intent = packageManager.getLaunchIntentForPackage(FbLabels.FB_PACKAGE)
        if (intent == null) {
            Log.e(TAG, "Aplikasi Facebook tidak terpasang")
            false
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Gagal membuka Facebook", e)
        false
    }

    private fun launchCreateItemLink(): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FbLabels.CREATE_ITEM_URL))
            .setPackage(FbLabels.FB_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) == null) {
            false
        } else {
            startActivity(intent)
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Deep link form tidak bisa dibuka", e)
        false
    }

    private fun formOrPhotoButtonVisible(roots: List<AccessibilityNodeInfo>): Boolean? {
        if (isFormVisible(roots)) return true
        val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
        return if (NodeFinder.findByLabels(fb, FbLabels.ADD_PHOTOS) != null) true else null
    }

    /** Cek cepat (tanpa menunggu) apakah form Jual Barang sudah tampil. */
    fun isSellFormShowing(): Boolean = formOrPhotoButtonVisible(roots()) == true

    private fun fbRoots(): List<AccessibilityNodeInfo> =
        roots().filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }

    /** Tap label hanya di jendela Facebook (bukan aplikasi lain). */
    private suspend fun tapInFacebook(labels: List<String>, timeoutMs: Long, mode: MatchMode = MatchMode.STARTS_WITH): Boolean {
        val node = waitFor(timeoutMs) { roots ->
            NodeFinder.findByLabels(roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }, labels, mode)
        } ?: run {
            Log.d(TAG, "Tidak ketemu di Facebook: $labels")
            return false
        }
        Log.d(TAG, "Tap (FB): ${NodeFinder.label(node)}")
        return clickNode(node)
    }

    suspend fun navigateToMarketplace(): Boolean {
        if (tapInFacebook(FbLabels.MARKETPLACE, 6_000L)) return true
        // Tab Marketplace tidak ada di bilah navigasi: buka lewat Menu (☰).
        Log.d(TAG, "Tab Marketplace tidak ada, coba lewat Menu")
        if (!tapInFacebook(FbLabels.MENU, 3_000L, MatchMode.EXACT) && !tapInFacebook(FbLabels.MENU, 2_000L)) return false
        delay(1_500L)
        repeat(3) {
            if (tapInFacebook(FbLabels.MARKETPLACE, 2_500L)) return true
            if (!scrollForward()) return false
            delay(600L)
        }
        return false
    }

    suspend fun tapSellButton(): Boolean = tapInFacebook(FbLabels.SELL, 6_000L, MatchMode.EXACT) ||
        tapInFacebook(FbLabels.SELL, 2_000L, MatchMode.STARTS_WITH)

    /**
     * Membuka form Jual Barang:
     *  1. deep link facebook.com/marketplace/create/item,
     *  2. jika gagal: Facebook -> Marketplace (atau Menu -> Marketplace) -> Jual
     *     -> Buat tawaran baru -> Barang untuk dijual.
     * Mengembalikan false jika form tidak terlihat; pemanggil lalu meminta pengguna membukanya.
     */
    suspend fun openSellForm(stepDelay: Long): Boolean {
        if (launchCreateItemLink()) {
            if (waitFor(10_000L) { formOrPhotoButtonVisible(it) } != null) {
                Log.d(TAG, "Form terbuka lewat deep link")
                return true
            }
            Log.d(TAG, "Deep link tidak membuka form. Layar: ${describeScreen()}")
        }
        Log.d(TAG, "Navigasi manual ke form Jual")
        if (!launchFacebookApp()) return false
        if (waitFor(10_000L) { if (isFacebookForeground()) true else null } == null) return false
        delay(stepDelay * 2)

        // Facebook mungkin sudah berada di form / halaman Jual dari percobaan sebelumnya.
        if (isSellFormShowing()) return true
        val onSellPage = NodeFinder.findByLabels(fbRoots(), FbLabels.CREATE_LISTING) != null
        if (!onSellPage) {
            if (!navigateToMarketplace()) {
                Log.d(TAG, "Marketplace tidak ditemukan. Layar: ${describeScreen()}")
                return false
            }
            delay(stepDelay * 2)
            if (!tapSellButton()) {
                Log.d(TAG, "Tombol Jual tidak ditemukan. Layar: ${describeScreen()}")
                return false
            }
            delay(stepDelay * 2)
        }

        // Halaman "Jual" -> "Buat tawaran baru" (jika ada).
        if (waitFor(1_500L) { formOrPhotoButtonVisible(it) } == null) {
            tapInFacebook(FbLabels.CREATE_LISTING, 4_000L)
            delay(stepDelay * 2)
        }
        // Pilihan jenis tawaran -> "Barang untuk dijual" (jika ada).
        if (waitFor(1_500L) { formOrPhotoButtonVisible(it) } == null) {
            tapInFacebook(FbLabels.ITEM_FOR_SALE, 4_000L)
        }
        val ok = waitFor(10_000L) { formOrPhotoButtonVisible(it) } != null
        if (!ok) Log.d(TAG, "Form tetap tidak terlihat. Layar: ${describeScreen()}")
        return ok
    }

    /** Ringkasan teks yang terlihat di layar, untuk log diagnosa. */
    fun describeScreen(maxItems: Int = 20): String = try {
        roots().joinToString(" || ") { root ->
            val texts = NodeFinder.findAll(root) { it.isVisibleToUser && NodeFinder.texts(it).isNotEmpty() }
                .flatMap { NodeFinder.texts(it) }
                .map { it.replace('\n', ' ').take(40) }
                .distinct()
                .take(maxItems)
            "[${root.packageName}] " + texts.joinToString(" | ")
        }.take(900).ifBlank { "(tidak ada jendela terbaca)" }
    } catch (e: Exception) {
        "(gagal membaca layar: ${e.message})"
    }

    // ---------------------------------------------------------------- foto

    suspend fun selectPhotos(count: Int, autoPick: Boolean, stepDelay: Long): PhotoPickResult {
        val opened = tapByLabels(FbLabels.ADD_PHOTOS, 6_000L, MatchMode.STARTS_WITH)
        if (!opened) {
            Log.d(TAG, "Tombol tambah foto tidak ketemu, minta pengguna")
            return PhotoPickResult.NEED_USER
        }
        if (!autoPick) return PhotoPickResult.NEED_USER

        delay(stepDelay * 2)
        // Pemilih foto terbuka = form tidak lagi terlihat.
        if (waitFor(6_000L) { if (!isFormVisible(it)) true else null } == null) return PhotoPickResult.NEED_USER

        val grid = waitFor(6_000L) { roots ->
            roots.flatMap { r -> NodeFinder.findAll(r) { n -> n.isScrollable && n.childCount >= 2 } }
                .maxByOrNull { NodeFinder.area(it) }
        } ?: return PhotoPickResult.NEED_USER

        val items = (0 until grid.childCount)
            .mapNotNull { grid.getChild(it) }
            .filter { child ->
                val r = NodeFinder.bounds(child)
                !r.isEmpty && !NodeFinder.matches(child, FbLabels.CAMERA, MatchMode.CONTAINS)
            }
            .sortedWith(compareBy({ NodeFinder.bounds(it).top }, { NodeFinder.bounds(it).left }))

        if (items.size < count) {
            Log.d(TAG, "Item galeri kurang (${items.size} < $count)")
            return PhotoPickResult.NEED_USER
        }
        for (item in items.take(count)) {
            if (!clickNode(item, climb = false)) return PhotoPickResult.NEED_USER
            delay(400L)
        }
        delay(stepDelay)

        // Pastikan masih di pemilih foto sebelum menekan Selesai (jangan sampai menekan "Berikutnya" di form).
        if (isFormVisible()) return PhotoPickResult.DONE
        if (!tapByLabels(FbLabels.PICKER_DONE, 4_000L, MatchMode.EXACT)) return PhotoPickResult.NEED_USER
        return if (waitFor(10_000L) { if (isFormVisible(it)) true else null } != null) {
            PhotoPickResult.DONE
        } else PhotoPickResult.NEED_USER
    }

    /** Menunggu pengguna memilih foto sendiri: pemilih terbuka, lalu kembali ke form. */
    suspend fun waitForUserPhotoSelection(timeoutMs: Long): Boolean {
        val pickerOpened = waitFor(timeoutMs, 700L) { if (!isFormVisible(it)) true else null } ?: return false
        Log.d(TAG, "Pemilih foto terbuka ($pickerOpened), menunggu kembali ke form")
        delay(800L)
        return waitFor(timeoutMs, 700L) { if (isFormVisible(it)) true else null } != null
    }

    // ---------------------------------------------------------------- isian form

    private suspend fun fillField(labels: List<String>, value: String, scrollAttempts: Int = 0): Boolean {
        var attempt = 0
        while (true) {
            val field = waitFor(2_500L) { roots ->
                NodeFinder.findEditable(roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }, labels)
            }
            if (field != null) {
                val ok = setText(field, value)
                Log.d(TAG, "Isi $labels -> ${if (ok) "OK" else "GAGAL"}")
                return ok
            }
            if (attempt >= scrollAttempts || !scrollForward()) return false
            attempt++
            delay(600L)
        }
    }

    suspend fun inputTitle(title: String): Boolean = fillField(FbLabels.TITLE, title, scrollAttempts = 1)

    suspend fun inputPrice(price: String): Boolean = fillField(FbLabels.PRICE, price, scrollAttempts = 2)

    suspend fun inputDescription(description: String): Boolean =
        fillField(FbLabels.DESCRIPTION, description, scrollAttempts = 5)

    /** Membuka pilihan kategori, mencari, lalu memilih hasil yang cocok. */
    suspend fun inputCategory(category: String, stepDelay: Long): Boolean {
        if (!openDropdown(FbLabels.CATEGORY)) return false
        delay(stepDelay)
        val search = waitFor(2_500L) { roots ->
            NodeFinder.findEditable(roots, FbLabels.SEARCH)
                ?: roots.flatMap { r -> NodeFinder.findAll(r) { NodeFinder.isEditable(it) } }.firstOrNull()
        }
        if (search != null && !isFormVisible()) {
            setText(search, category)
            delay(1_500L)
        }
        val option = waitFor(5_000L) { NodeFinder.findByLabels(it, listOf(category), MatchMode.CONTAINS) }
            ?: return false
        return clickNode(option)
    }

    suspend fun inputCondition(conditionLabels: List<String>, stepDelay: Long): Boolean {
        if (!openDropdown(FbLabels.CONDITION)) return false
        delay(stepDelay)
        val option = waitFor(4_000L) { NodeFinder.findByLabels(it, conditionLabels, MatchMode.STARTS_WITH) }
            ?: return false
        return clickNode(option)
    }

    suspend fun inputLocation(location: String, stepDelay: Long): Boolean {
        // Ada versi dengan kolom isian langsung, ada yang membuka layar pencarian.
        val inline = waitFor(1_500L) { roots ->
            NodeFinder.findEditable(roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }, FbLabels.LOCATION)
        }
        if (inline == null) {
            if (!openDropdown(FbLabels.LOCATION)) return false
            delay(stepDelay)
            // Layar pencarian lokasi harus terbuka; jangan sampai mengetik di kolom lain di form.
            if (isFormVisible()) return false
        }
        val field = inline ?: waitFor(3_000L) { roots ->
            roots.flatMap { r -> NodeFinder.findAll(r) { NodeFinder.isEditable(it) } }.firstOrNull()
        } ?: return false
        // "Makassar, Sulawesi Selatan" -> ketik "Makassar", lalu pilih saran yang memuat nama kota.
        val city = location.substringBefore(',').trim().ifBlank { location }
        if (!setText(field, city)) return false
        delay(2_000L)
        val suggestion = waitFor(5_000L) { NodeFinder.findByLabels(it, listOf(location, city), MatchMode.CONTAINS) }
            ?: return false
        return clickNode(suggestion)
    }

    private suspend fun openDropdown(labels: List<String>): Boolean {
        var attempt = 0
        while (true) {
            val node = waitFor(2_000L) { roots ->
                NodeFinder.findByLabels(roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }, labels, MatchMode.STARTS_WITH, excludeEditable = false)
            }
            if (node != null) return clickNode(node)
            if (attempt >= 4 || !scrollForward()) return false
            attempt++
            delay(600L)
        }
    }

    /** Jika sebuah langkah gagal di tengah dropdown, kembali ke form. */
    suspend fun recoverToForm(stepDelay: Long): Boolean {
        if (waitFor(1_500L) { if (isFormVisible(it)) true else null } != null) return true
        Log.d(TAG, "Form tidak terlihat, tekan Kembali sekali")
        goBack()
        delay(stepDelay)
        return waitFor(2_500L) { if (isFormVisible(it)) true else null } != null
    }

    /** Scroll ke bawah sampai tombol Publikasikan/Berikutnya terlihat. TIDAK menekannya. */
    suspend fun revealPublishButton(): Boolean {
        repeat(6) {
            val found = NodeFinder.findByLabels(roots(), FbLabels.PUBLISH + FbLabels.FORM_NEXT, MatchMode.EXACT)
            if (found != null && found.isVisibleToUser) return true
            if (!scrollForward()) return found != null
            delay(500L)
        }
        return false
    }

    companion object {
        private const val TAG = "MarketAutomation"

        @Volatile
        var instance: MarketAutomationService? = null
            private set

        fun isInstalled(context: Context, pkg: String): Boolean = try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, MarketAutomationService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == expected
            }
        }
    }
}
