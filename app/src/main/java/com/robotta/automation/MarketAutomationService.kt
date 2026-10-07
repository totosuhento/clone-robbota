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
        if (NodeFinder.findEditable(fbRoots, FbLabels.TITLE) != null ||
            NodeFinder.findEditable(fbRoots, FbLabels.PRICE) != null
        ) return true
        // Sebagian versi menampilkan kolom sebagai baris berlabel yang baru bisa diketik setelah diketuk.
        val titleRow = NodeFinder.findByLabels(fbRoots, FbLabels.TITLE, MatchMode.EXACT, excludeEditable = false)
        val priceRow = NodeFinder.findByLabels(fbRoots, FbLabels.PRICE, MatchMode.EXACT, excludeEditable = false)
        return titleRow != null && priceRow != null
    }

    /** Jejak navigasi pembukaan form, ditampilkan di log aplikasi saat gagal. */
    val navTrail = mutableListOf<String>()

    private fun trail(step: String, withScreen: Boolean = true) {
        val line = if (withScreen) "$step → ${describeScreen(25)}" else step
        navTrail += line
        Log.d(TAG, line)
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
        // Panel melayang tidak boleh "menangkap" ketukan otomatis.
        panel?.passThroughBriefly()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 80L, 60L))
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

    private fun launchCreateItemLink(): Boolean = launchFbUrl(FbLabels.CREATE_ITEM_URL)

    private fun launchFbUrl(url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
        val label = NodeFinder.texts(node).firstOrNull()?.take(30).orEmpty()
        val ok = tapAndVerify(node)
        trail("Ketuk \"$label\" → ${if (ok) "layar berubah" else "tidak ada perubahan"}", withScreen = false)
        return ok
    }

    /** Ringkas isi layar (selain aplikasi ini) untuk mendeteksi perubahan setelah ketukan. */
    private fun screenSignature(): Int = try {
        roots().filter { it.packageName?.toString() != packageName }.flatMap { r ->
            NodeFinder.findAll(r) { it.isVisibleToUser && NodeFinder.texts(it).isNotEmpty() }
                .take(80)
                .flatMap { NodeFinder.texts(it) }
        }.joinToString("|").hashCode()
    } catch (e: Exception) {
        0
    }

    /**
     * Ketuk seperti jari (gesture di tengah tombol), lalu pastikan layar berubah.
     * Facebook kadang "menerima" klik aksesibilitas tanpa menjalankannya, jadi
     * gesture dicoba dulu, lalu klik aksesibilitas sebagai cadangan.
     */
    suspend fun tapAndVerify(node: AccessibilityNodeInfo, waitMs: Long = 3_000L): Boolean {
        val before = screenSignature()
        fun changed(): Boolean? = if (screenSignature() != before) true else null
        val b = NodeFinder.bounds(node)
        if (!b.isEmpty && node.isVisibleToUser) {
            tapAt(b.exactCenterX(), b.exactCenterY())
            if (waitFor(waitMs, 300L) { changed() } != null) return true
            Log.d(TAG, "Gesture tidak mengubah layar, coba klik aksesibilitas")
        }
        clickNode(node)
        return waitFor(waitMs, 300L) { changed() } != null
    }

    /** Ambil lalu kosongkan jejak navigasi (untuk ditulis ke log aplikasi). */
    fun takeTrail(): List<String> = navTrail.toList().also { navTrail.clear() }

    /** Ikon/tab Marketplace di bilah navigasi (atas atau bawah layar), bukan teks di dalam postingan. */
    private fun findMarketplaceTab(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val h = resources.displayMetrics.heightPixels
        return roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
            .flatMap { r -> NodeFinder.findAll(r) { n -> n.isVisibleToUser && NodeFinder.matches(n, FbLabels.MARKETPLACE, MatchMode.STARTS_WITH) } }
            .filter { n ->
                val b = NodeFinder.bounds(n)
                (b.bottom <= h * 0.25f || b.top >= h * 0.82f) && NodeFinder.hasClickableAncestor(n, 2)
            }
            .minByOrNull { NodeFinder.area(it) }
    }

    suspend fun navigateToMarketplace(): Boolean {
        val tab = waitFor(6_000L) { findMarketplaceTab(it) }
        if (tab != null) {
            Log.d(TAG, "Tap tab: ${NodeFinder.label(tab)}")
            val ok = tapAndVerify(tab)
            trail("Ketuk tab Marketplace → ${if (ok) "layar berubah" else "tidak ada perubahan"}", withScreen = false)
            if (ok) return true
        }
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
        navTrail.clear()
        if (launchCreateItemLink()) {
            if (waitFor(10_000L) { formOrPhotoButtonVisible(it) } != null) {
                Log.d(TAG, "Form terbuka lewat deep link")
                return true
            }
            trail("1) Link form")
        }
        // Jalur 2: buka beranda Marketplace lewat link, lalu Jual -> Buat tawaran baru -> Barang.
        if (launchFbUrl(FbLabels.MARKETPLACE_URL)) {
            val sellVisible = waitFor(10_000L) { roots ->
                val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
                NodeFinder.findByLabels(fb, FbLabels.SELL, MatchMode.EXACT)
                    ?: NodeFinder.findByLabels(fb, FbLabels.CREATE_LISTING)
            }
            if (sellVisible != null && continueFromSellPage(stepDelay)) {
                Log.d(TAG, "Form terbuka lewat beranda Marketplace")
                return true
            }
            trail("2) Beranda Marketplace")
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
                trail("3) Tab Marketplace tidak ketemu")
                return false
            }
            delay(stepDelay * 2)
            if (!tapSellButton()) {
                trail("3) Tombol Jual tidak ketemu")
                return false
            }
            delay(stepDelay * 2)
        }

        val ok = continueFromSellPage(stepDelay, sellAlreadyTapped = true)
        if (!ok) trail("3) Setelah Jual")
        return ok
    }

    /** Dari beranda Marketplace / halaman Jual sampai form Barang untuk dijual terbuka. */
    private suspend fun continueFromSellPage(stepDelay: Long, sellAlreadyTapped: Boolean = false): Boolean {
        if (isSellFormShowing()) return true
        if (!sellAlreadyTapped && NodeFinder.findByLabels(fbRoots(), FbLabels.CREATE_LISTING) == null) {
            if (!tapSellButton()) return false
            delay(stepDelay * 2)
        }
        // Halaman "Jual barang" -> "Buat tawaran baru" (jika ada; kadang perlu digulir).
        if (waitFor(2_000L) { formOrPhotoButtonVisible(it) } == null) {
            var tapped = tapInFacebook(FbLabels.CREATE_LISTING, 4_000L)
            var scrolls = 0
            while (!tapped && scrolls < 3 && scrollForward()) {
                delay(700L)
                tapped = tapInFacebook(FbLabels.CREATE_LISTING, 1_500L)
                scrolls++
            }
            delay(stepDelay * 2)
        }
        // Pilihan jenis tawaran -> "Barang untuk dijual" (jika ada).
        if (waitFor(2_000L) { formOrPhotoButtonVisible(it) } == null) {
            tapInFacebook(FbLabels.ITEM_FOR_SALE, 4_000L, MatchMode.EXACT)
        }
        return waitFor(10_000L) { formOrPhotoButtonVisible(it) } != null
    }

    /** Ringkasan teks yang terlihat di layar, untuk log diagnosa. */
    fun describeScreen(maxItems: Int = 20): String = try {
        roots().joinToString(" || ") { root ->
            val texts = NodeFinder.findAll(root) { it.isVisibleToUser && NodeFinder.texts(it).isNotEmpty() }
                .flatMap { n ->
                    val mark = when {
                        NodeFinder.isEditable(n) -> "✎"
                        NodeFinder.hasClickableAncestor(n, 1) -> "•"
                        else -> ""
                    }
                    NodeFinder.texts(n).map { mark + it.replace('\n', ' ').take(40) }
                }
                .distinct()
                .take(maxItems)
            "[${root.packageName}] " + texts.joinToString(" | ")
        }.take(2500).ifBlank { "(tidak ada jendela terbaca)" }
    } catch (e: Exception) {
        "(gagal membaca layar: ${e.message})"
    }

    // ---------------------------------------------------------------- foto

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    /** Catatan dari langkah terakhir (mis. "kategori dipilih otomatis: X"), dibaca AutomationEngine. */
    @Volatile
    var lastNote: String? = null

    /**
     * Thumbnail foto di layar pemilih: node terlihat, hampir persegi, lebar 15–40% layar.
     * Cara ini tidak bergantung pada teks/ID, jadi tetap jalan walau tampilan galeri berubah.
     */
    private fun thumbnails(roots: List<AccessibilityNodeInfo> = roots()): List<AccessibilityNodeInfo> {
        val minW = (screenW * 0.15f).toInt()
        val maxW = (screenW * 0.40f).toInt()
        val minTop = (screenH * 0.06f).toInt()
        val seen = HashSet<String>()
        return roots.flatMap { r ->
            NodeFinder.findAll(r) { n ->
                if (!n.isVisibleToUser || NodeFinder.isEditable(n)) return@findAll false
                val b = NodeFinder.bounds(n)
                val w = b.width()
                val h = b.height()
                w in minW..maxW && h > 0 && kotlin.math.abs(w - h) <= w * 0.2f && b.top >= minTop
            }
        }.filter { seen.add(NodeFinder.bounds(it).flattenToString()) }
            .sortedWith(compareBy({ NodeFinder.bounds(it).top }, { NodeFinder.bounds(it).left }))
    }

    /**
     * Memilih [count] foto produk dari galeri. Facebook versi baru meminta "foto utama dulu",
     * jadi pemilihan bisa berlangsung beberapa putaran: foto 1, lalu "Tambahkan foto" lagi untuk sisanya.
     * Foto produk sudah diekspor paling baru, jadi berada paling depan di galeri.
     */
    suspend fun selectPhotos(count: Int, autoPick: Boolean, stepDelay: Long): PhotoPickResult {
        lastNote = null
        if (!autoPick) {
            tapInFacebook(FbLabels.ADD_PHOTOS, 6_000L)
            return PhotoPickResult.NEED_USER
        }
        var picked = 0
        var round = 0
        while (picked < count && round < 4) {
            round++
            val opened = tapInFacebook(FbLabels.ADD_PHOTOS, if (round == 1) 6_000L else 3_000L)
            if (!opened) {
                Log.d(TAG, "Tombol tambah foto tidak ketemu (putaran $round). Layar: ${describeScreen()}")
                break
            }
            delay(stepDelay)
            val r = pickRound(skip = picked, want = count - picked, stepDelay = stepDelay)
            if (r <= 0) break
            picked += r
            delay(stepDelay)
        }
        return when {
            picked >= count -> PhotoPickResult.DONE
            picked > 0 -> {
                lastNote = "Baru $picked dari $count foto yang ditambahkan — tambahkan sisanya bila perlu."
                PhotoPickResult.DONE
            }
            else -> PhotoPickResult.NEED_USER
        }
    }

    /** Satu putaran di galeri. Mengembalikan jumlah foto yang berhasil ditambahkan (0 = gagal). */
    private suspend fun pickRound(skip: Int, want: Int, stepDelay: Long): Int {
        var grid = waitFor(5_000L, 500L) { roots -> thumbnails(roots).takeIf { it.size >= 3 } }
        if (grid == null) {
            // Mungkin muncul pilihan sumber foto dulu (Galeri / Kamera).
            if (tapByLabels(FbLabels.GALLERY_OPTION, 1_500L, MatchMode.EXACT)) {
                grid = waitFor(6_000L, 500L) { roots -> thumbnails(roots).takeIf { it.size >= 3 } }
            }
        }
        if (grid == null) {
            Log.d(TAG, "Grid foto tidak terdeteksi. Layar: ${describeScreen()}")
            return 0
        }
        val gridKeys = grid.map { NodeFinder.bounds(it).flattenToString() }.toSet()
        fun closed(roots: List<AccessibilityNodeInfo>): Boolean {
            val still = thumbnails(roots).count { NodeFinder.bounds(it).flattenToString() in gridKeys }
            return still < gridKeys.size / 2
        }

        val photos = grid.filterNot { NodeFinder.matches(it, FbLabels.CAMERA, MatchMode.CONTAINS) }
        val targets = photos.drop(skip).take(want)
        if (targets.isEmpty()) {
            Log.d(TAG, "Tidak ada foto tersisa di galeri untuk dipilih")
            goBack()
            return 0
        }
        var tapped = 0
        for (item in targets) {
            val b = NodeFinder.bounds(item)
            tapAt(b.exactCenterX(), b.exactCenterY())
            tapped++
            delay(700L)
            // Mode pilih-satu: galeri langsung tertutup setelah satu foto.
            if (closed(roots())) {
                Log.d(TAG, "Galeri tertutup setelah $tapped foto")
                return tapped
            }
        }
        delay(stepDelay)
        if (closed(roots())) return tapped

        val done = waitFor(4_000L) { roots ->
            val pickerRoots = roots.filter { it.packageName?.toString() != packageName }
            NodeFinder.findByLabels(pickerRoots, FbLabels.PICKER_DONE, MatchMode.EXACT)
                ?: NodeFinder.findByLabels(pickerRoots, FbLabels.PICKER_DONE_PREFIX, MatchMode.STARTS_WITH)
        }
        if (done == null) {
            Log.d(TAG, "Tombol Selesai di galeri tidak ketemu. Layar: ${describeScreen()}")
            return 0
        }
        clickNode(done)
        return if (waitFor(10_000L, 500L) { if (closed(it)) true else null } != null) {
            tapped
        } else {
            Log.d(TAG, "Galeri tidak tertutup. Layar: ${describeScreen()}")
            0
        }
    }

    /** Menunggu pengguna memilih foto sendiri: grid galeri muncul lalu tertutup lagi. */
    suspend fun waitForUserPhotoSelection(timeoutMs: Long): Boolean {
        val grid = waitFor(timeoutMs, 700L) { roots -> thumbnails(roots).takeIf { it.size >= 3 } }
            ?: return isFormVisible()
        val keys = grid.map { NodeFinder.bounds(it).flattenToString() }.toSet()
        return waitFor(timeoutMs, 700L) { roots ->
            val still = thumbnails(roots).count { NodeFinder.bounds(it).flattenToString() in keys }
            if (still < keys.size / 2) true else null
        } != null
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
            // Baris berlabel yang baru menjadi kolom isian setelah diketuk.
            val row = NodeFinder.findByLabels(fbRoots(), labels, MatchMode.EXACT, excludeEditable = false)
            if (row != null && clickNode(row)) {
                delay(800L)
                val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && NodeFinder.isEditable(focused)) {
                    val ok = setText(focused, value)
                    Log.d(TAG, "Isi (lewat baris) $labels -> ${if (ok) "OK" else "GAGAL"}")
                    return ok
                }
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

    private fun visibleKeysOfEditables(): Set<String> =
        NodeFinder.visibleEditables(roots()).map { NodeFinder.key(it) }.toSet()

    /** Kolom pencarian baru yang muncul setelah membuka pilihan (kategori/lokasi). */
    private suspend fun waitNewEditable(before: Set<String>, timeoutMs: Long): AccessibilityNodeInfo? =
        waitFor(timeoutMs) { roots ->
            NodeFinder.visibleEditables(roots.filter { it.packageName?.toString() != packageName })
                .firstOrNull { NodeFinder.key(it) !in before }
        }

    /** Opsi terlihat di bawah posisi [minTop] yang cocok dengan salah satu label. */
    private fun findOptionBelow(labels: List<String>, minTop: Int): AccessibilityNodeInfo? {
        val all = roots().filter { it.packageName?.toString() != packageName }.flatMap { r ->
            NodeFinder.findAll(r) { n ->
                n.isVisibleToUser && !NodeFinder.isEditable(n) && NodeFinder.bounds(n).top >= minTop
            }
        }
        for (mode in listOf(MatchMode.EXACT, MatchMode.STARTS_WITH, MatchMode.CONTAINS)) {
            all.filter { NodeFinder.matches(it, labels, mode) }
                .minByOrNull { NodeFinder.bounds(it).top }
                ?.let { return it }
        }
        return null
    }

    /** Baris hasil pencarian pertama di bawah [minTop] (cadangan bila nama persis tidak ada). */
    private fun firstResultBelow(minTop: Int, typed: String): AccessibilityNodeInfo? {
        val typedNorm = NodeFinder.normalize(typed)
        return roots().filter { it.packageName?.toString() != packageName }.flatMap { r ->
            NodeFinder.findAll(r) { n ->
                n.isVisibleToUser && !NodeFinder.isEditable(n) &&
                    NodeFinder.bounds(n).top >= minTop && NodeFinder.texts(n).isNotEmpty()
            }
        }.filter { n ->
            NodeFinder.texts(n).none { NodeFinder.normalize(it) == typedNorm } && NodeFinder.hasClickableAncestor(n)
        }.minByOrNull { NodeFinder.bounds(it).top }
    }

    /** Cari baris form berlabel [labels]; kembalikan teks di sekitarnya (nilai yang sedang terisi). */
    private fun formRowText(labels: List<String>): String? {
        val row = NodeFinder.findByLabels(fbRoots(), labels, MatchMode.STARTS_WITH, excludeEditable = false)
            ?: return null
        return NodeFinder.contextText(row, 1)
    }

    /**
     * Pilih dari layar pencarian (kategori/lokasi): buka baris form, ketik di kolom pencarian
     * yang baru muncul, lalu pilih hasil yang cocok. Cadangan: hasil teratas (dicatat di [lastNote]).
     */
    private suspend fun pickViaSearch(
        rowLabels: List<String>,
        query: String,
        wanted: List<String>,
        what: String,
        stepDelay: Long
    ): Boolean {
        val before = visibleKeysOfEditables()
        if (!openDropdown(rowLabels)) return false
        delay(stepDelay)

        val search = waitNewEditable(before, 3_000L)
        val minTop: Int
        if (search != null) {
            setText(search, query)
            delay(2_000L)
            minTop = NodeFinder.bounds(search).bottom
        } else {
            // Daftar tanpa kolom pencarian (mis. sheet dari bawah).
            minTop = (screenH * 0.15f).toInt()
        }

        val exact = waitFor(4_000L) { findOptionBelow(wanted, minTop) }
        if (exact != null) {
            Log.d(TAG, "$what dipilih: ${NodeFinder.label(exact)}")
            return clickNode(exact)
        }
        if (search != null) {
            val first = firstResultBelow(minTop, query)
            if (first != null) {
                val label = NodeFinder.texts(first).firstOrNull().orEmpty()
                lastNote = "$what dipilih otomatis: \"$label\" — pastikan sesuai."
                Log.d(TAG, "$what cadangan: $label")
                return clickNode(first)
            }
        }
        Log.d(TAG, "$what tidak ketemu. Layar: ${describeScreen()}")
        return false
    }

    /** Membuka pilihan kategori, mencari, lalu memilih hasil yang cocok. */
    suspend fun inputCategory(category: String, stepDelay: Long): Boolean {
        lastNote = null
        if (formRowText(FbLabels.CATEGORY)?.contains(category, ignoreCase = true) == true) return true
        // "Kesehatan & Kecantikan" -> cari juga "Kesehatan" bila hasil persis tidak ada.
        val main = category.split('&', ',', '/').first().trim().ifBlank { category }
        return pickViaSearch(FbLabels.CATEGORY, category, listOf(category, main), "Kategori", stepDelay)
    }

    suspend fun inputCondition(conditionLabels: List<String>, stepDelay: Long): Boolean {
        lastNote = null
        if (!openDropdown(FbLabels.CONDITION)) return false
        delay(stepDelay)
        val option = waitFor(4_000L) { findOptionBelow(conditionLabels, (screenH * 0.1f).toInt()) }
            ?: return false
        return clickNode(option)
    }

    suspend fun inputLocation(location: String, stepDelay: Long): Boolean {
        lastNote = null
        // "Makassar, Sulawesi Selatan" -> ketik "Makassar", lalu pilih saran yang memuat nama kota.
        val city = location.substringBefore(',').trim().ifBlank { location }
        if (formRowText(FbLabels.LOCATION)?.contains(city, ignoreCase = true) == true) {
            Log.d(TAG, "Lokasi sudah $city")
            return true
        }
        // Versi dengan kolom isian langsung di form.
        val inline = NodeFinder.findEditable(fbRoots(), FbLabels.LOCATION)
        if (inline != null) {
            if (!setText(inline, city)) return false
            delay(2_000L)
            val s = waitFor(4_000L) { findOptionBelow(listOf(location, city), NodeFinder.bounds(inline).bottom) }
                ?: return false
            return clickNode(s)
        }
        return pickViaSearch(FbLabels.LOCATION, city, listOf(location, city), "Lokasi", stepDelay)
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
