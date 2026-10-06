package com.robotta.automation

import android.content.Context
import android.util.Log
import com.robotta.data.AppDatabase
import com.robotta.data.AppSettings
import com.robotta.data.entities.Account
import com.robotta.data.entities.Conditions
import com.robotta.data.entities.Product
import com.robotta.data.entities.ProductStatus
import com.robotta.image.GalleryExporter
import com.robotta.util.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Otak asisten posting. Alurnya per produk:
 *
 *   siapkan foto -> buka form Jual -> pilih foto -> isi judul, harga, kategori,
 *   kondisi, deskripsi, lokasi -> BERHENTI, kamu periksa lalu tekan Publikasikan
 *   -> asisten mencatat sukses -> kamu tekan "Produk berikutnya".
 *
 * Tidak ada posting tanpa pengawasan: setiap produk butuh satu tekanan
 * Publikasikan darimu, dan produk berikutnya baru disiapkan atas perintahmu.
 */
object AutomationEngine {

    private const val TAG = "AutomationEngine"
    private const val MAX_LOGS = 200
    private const val USER_PHOTO_TIMEOUT_MS = 5 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    private var appContext: Context? = null
    private var settings = AppSettings()
    private var account = Account()
    private var queue: List<Product> = emptyList()
    private var index = 0
    private var postedCount = 0
    private var job: Job? = null

    // ---------------------------------------------------------------- API untuk UI

    /** @return pesan error untuk ditampilkan, atau null jika sesi dimulai. */
    fun startSession(context: Context, pending: List<Product>, settings: AppSettings, account: Account): String? {
        if (_state.value.isActive) return "Sesi masih berjalan. Selesaikan atau tekan Berhenti dulu."
        if (MarketAutomationService.instance == null) {
            return "Layanan aksesibilitas belum aktif. Aktifkan dulu di Pengaturan Aksesibilitas."
        }
        if (pending.isEmpty()) return "Tidak ada produk berstatus Menunggu."

        appContext = context.applicationContext
        this.settings = settings
        this.account = account
        queue = pending.take(settings.sessionLimit)
        index = 0
        postedCount = 0
        log(null, "Sesi dimulai: ${queue.size} produk (batas sesi ${settings.sessionLimit}).", LogLevel.INFO)
        prepareCurrent()
        return null
    }

    fun isAwaitingPublish(): Boolean = _state.value is EngineState.AwaitingPublish

    /** Dipanggil layanan saat kamu menekan Publikasikan di Facebook. */
    fun onPublishClicked() {
        scope.launch {
            if (_state.value is EngineState.AwaitingPublish) {
                log(currentTitle(), "Terdeteksi tombol Publikasikan ditekan.", LogLevel.INFO)
                markCurrentPosted()
            }
        }
    }

    /** Tandai produk sekarang sudah terposting (otomatis atau lewat tombol manual). */
    fun markCurrentPosted() {
        val s = _state.value
        if (s !is EngineState.AwaitingPublish && s !is EngineState.NeedsUser) return
        val product = queue.getOrNull(index) ?: return
        job?.cancel()
        postedCount++
        updateStatus(product.id, ProductStatus.SUCCESS, "", System.currentTimeMillis())
        log(product.title, "Terposting ✓", LogLevel.SUCCESS)
        val hasNext = index + 1 < queue.size
        _state.value = EngineState.Posted(index + 1, queue.size, product.title, hasNext)
        notify(
            "Terposting: ${product.title}",
            if (hasNext) "Tekan \"Produk berikutnya\" saat kamu siap." else "Ini produk terakhir di sesi ini.",
            if (hasNext) listOf(
                NotificationHelper.Action("Produk berikutnya", EngineActionReceiver.ACTION_NEXT),
                NotificationHelper.Action("Berhenti", EngineActionReceiver.ACTION_STOP)
            ) else listOf(NotificationHelper.Action("Selesai", EngineActionReceiver.ACTION_STOP))
        )
    }

    fun nextProduct() {
        if (_state.value !is EngineState.Posted) return
        index++
        if (index >= queue.size) finish() else prepareCurrent()
    }

    fun skipCurrent() {
        if (!_state.value.isActive) return
        val product = queue.getOrNull(index)
        job?.cancel()
        if (product != null && _state.value !is EngineState.Posted) {
            updateStatus(product.id, ProductStatus.SKIPPED, "Dilewati oleh pengguna", null)
            log(product.title, "Dilewati.", LogLevel.WARN)
        }
        index++
        if (index >= queue.size) finish() else prepareCurrent()
    }

    fun retryCurrent() {
        if (_state.value !is EngineState.NeedsUser) return
        prepareCurrent()
    }

    fun stop() {
        if (!_state.value.isActive) return
        job?.cancel()
        log(null, "Sesi dihentikan.", LogLevel.WARN)
        finish()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun onServiceConnected() {
        log(null, "Layanan aksesibilitas aktif.", LogLevel.INFO)
    }

    fun onServiceDisconnected() {
        val s = _state.value
        if (s is EngineState.Working || (s is EngineState.NeedsUser && s.autoContinue)) {
            job?.cancel()
            val product = queue.getOrNull(index)
            _state.value = EngineState.NeedsUser(
                index + 1, queue.size, product?.title.orEmpty(),
                "Layanan aksesibilitas mati. Aktifkan lagi lalu tekan Coba lagi.", canRetry = true
            )
        }
        log(null, "Layanan aksesibilitas nonaktif.", LogLevel.WARN)
    }

    // ---------------------------------------------------------------- inti

    private fun prepareCurrent() {
        val ctx = appContext ?: return
        val queued = queue.getOrNull(index) ?: run { finish(); return }
        job?.cancel()
        job = scope.launch {
            // Ambil data terbaru (mungkin kamu baru menambah foto setelah gagal).
            val product = withContext(Dispatchers.IO) {
                AppDatabase.get(ctx).productDao().getById(queued.id)
            } ?: queued
            queue = queue.toMutableList().also { it[index] = product }
            try {
                runProduct(ctx, product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error tak terduga", e)
                fail(product, "Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun runProduct(ctx: Context, product: Product) {
        val service = MarketAutomationService.instance ?: run {
            needsUser(product, "Layanan aksesibilitas belum aktif. Aktifkan lalu tekan Coba lagi.", canRetry = true)
            return
        }
        val d = settings.stepDelayMs
        val warnings = mutableListOf<String>()
        log(product.title, "Mulai menyiapkan (${index + 1}/${queue.size}).", LogLevel.INFO)

        // 1. Foto ke galeri
        working(product, ActionStep.EXPORT_PHOTOS)
        val photos = product.imageList().filter { File(it).exists() }
        if (photos.isEmpty()) {
            fail(product, "Produk belum punya foto. Tambahkan foto lalu Coba lagi, atau Lewati.")
            return
        }
        val exported = withContext(Dispatchers.IO) {
            GalleryExporter(ctx).run {
                clearPrevious()
                export(photos)
            }
        }
        if (exported == 0) {
            fail(product, "Gagal menyalin foto ke galeri. Periksa izin penyimpanan.")
            return
        }

        // 2. Form Jual Barang
        working(product, ActionStep.OPEN_FORM)
        if (!service.openSellForm(d)) {
            fail(product, "Form Jual Barang tidak ditemukan. Pastikan Facebook sudah login dan bahasanya Indonesia/Inggris.")
            return
        }
        delay(d)

        // 3. Foto
        working(product, ActionStep.SELECT_PHOTOS)
        when (service.selectPhotos(exported, settings.autoPickPhotos, d)) {
            PhotoPickResult.DONE -> log(product.title, "$exported foto dipilih.", LogLevel.INFO)
            PhotoPickResult.NEED_USER -> {
                val msg = "Pilih $exported foto teratas (folder ${GalleryExporter.FOLDER}), lalu tekan Selesai. " +
                    "Asisten lanjut mengisi form setelah kamu kembali ke form."
                needsUser(product, msg, canRetry = false, autoContinue = true)
                if (!service.waitForUserPhotoSelection(USER_PHOTO_TIMEOUT_MS)) {
                    fail(product, "Waktu memilih foto habis (5 menit).")
                    return
                }
                log(product.title, "Foto dipilih manual.", LogLevel.INFO)
            }
            PhotoPickResult.FAILED -> {
                fail(product, "Tidak bisa membuka pemilih foto.")
                return
            }
        }
        delay(d)

        // 4-5. Judul & harga (wajib)
        working(product, ActionStep.FILL_TITLE)
        if (!service.inputTitle(product.title)) {
            fail(product, "Kolom Judul tidak ditemukan. Tambahkan teks kolomnya di FbLabels.TITLE.")
            return
        }
        delay(d)
        working(product, ActionStep.FILL_PRICE)
        if (!service.inputPrice(product.price.toString())) {
            fail(product, "Kolom Harga tidak ditemukan. Tambahkan teks kolomnya di FbLabels.PRICE.")
            return
        }
        delay(d)

        // 6-9. Opsional: jika gagal, dicatat dan kamu isi sendiri saat memeriksa.
        var formOk = true

        suspend fun optional(step: ActionStep, skip: Boolean, warning: String, action: suspend () -> Boolean) {
            if (skip || !formOk) return
            working(product, step)
            val ok = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Langkah ${step.name} error", e)
                false
            }
            if (!ok) {
                warnings += warning
                log(product.title, warning, LogLevel.WARN)
                formOk = service.recoverToForm(d)
                if (!formOk) warnings += "Form tidak terlihat lagi — lanjutkan pengisian secara manual."
            }
            delay(d)
        }

        optional(ActionStep.SELECT_CATEGORY, product.category.isBlank(), "Kategori \"${product.category}\" belum terpilih — pilih sendiri.") {
            service.inputCategory(product.category, d)
        }
        optional(ActionStep.SELECT_CONDITION, false, "Kondisi belum terpilih — pilih \"${product.condition}\".") {
            service.inputCondition(Conditions.facebookLabels(product.condition), d)
        }
        val description = buildDescription(product)
        optional(ActionStep.FILL_DESCRIPTION, description.isBlank(), "Deskripsi belum terisi — tempel manual.") {
            service.inputDescription(description)
        }
        val location = product.location.ifBlank { account.defaultLocation }
        optional(ActionStep.FILL_LOCATION, location.isBlank(), "Lokasi \"$location\" belum terpilih — cek lokasinya.") {
            service.inputLocation(location, d)
        }

        // 10. Serahkan ke pengguna
        working(product, ActionStep.REVIEW)
        if (formOk) service.revealPublishButton()
        _state.value = EngineState.AwaitingPublish(index + 1, queue.size, product.title, warnings.toList())
        log(
            product.title,
            if (warnings.isEmpty()) "Form terisi. Periksa lalu tekan Publikasikan."
            else "Form terisi sebagian (${warnings.size} catatan). Lengkapi lalu tekan Publikasikan.",
            if (warnings.isEmpty()) LogLevel.SUCCESS else LogLevel.WARN
        )
        notify(
            "Siap dipublikasikan (${index + 1}/${queue.size})",
            "${product.title}\nPeriksa isiannya di Facebook, lalu tekan Publikasikan sendiri." +
                if (warnings.isNotEmpty()) "\nCatatan: " + warnings.joinToString("; ") else "",
            listOf(
                NotificationHelper.Action("Sudah dipublikasikan", EngineActionReceiver.ACTION_MARK_POSTED),
                NotificationHelper.Action("Lewati", EngineActionReceiver.ACTION_SKIP)
            ),
            ongoing = true
        )
    }

    private fun buildDescription(product: Product): String {
        val footer = account.buildFooter()
        return listOf(product.description.trim(), footer, product.hashtags.trim())
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    // ---------------------------------------------------------------- helper status

    private fun working(product: Product, step: ActionStep) {
        _state.value = EngineState.Working(index + 1, queue.size, product.title, step)
        Log.d(TAG, "[${product.title}] ${step.label}")
    }

    private fun needsUser(product: Product, message: String, canRetry: Boolean, autoContinue: Boolean = false) {
        _state.value = EngineState.NeedsUser(index + 1, queue.size, product.title, message, canRetry, autoContinue)
        log(product.title, message, LogLevel.WARN)
        notify(
            "Butuh tindakanmu",
            message,
            buildList {
                if (canRetry) add(NotificationHelper.Action("Coba lagi", EngineActionReceiver.ACTION_RETRY))
                add(NotificationHelper.Action("Lewati", EngineActionReceiver.ACTION_SKIP))
                add(NotificationHelper.Action("Berhenti", EngineActionReceiver.ACTION_STOP))
            },
            ongoing = true
        )
    }

    private fun fail(product: Product, reason: String) {
        updateStatus(product.id, ProductStatus.FAILED, reason, null)
        log(product.title, "Gagal: $reason", LogLevel.ERROR)
        needsUser(product, reason, canRetry = true)
    }

    private fun finish() {
        job?.cancel()
        val total = queue.size
        val notPosted = (total - postedCount).coerceAtLeast(0)
        _state.value = EngineState.Finished(postedCount, notPosted)
        log(null, "Sesi selesai: $postedCount terposting, $notPosted tidak.", LogLevel.INFO)
        notify("Sesi selesai", "$postedCount produk terposting, $notPosted tidak terposting.", emptyList())
    }

    private fun currentTitle(): String? = queue.getOrNull(index)?.title

    private fun updateStatus(id: Int, status: String, error: String, postedAt: Long?) {
        val ctx = appContext ?: return
        scope.launch(Dispatchers.IO) {
            try {
                AppDatabase.get(ctx).productDao().updateStatus(id, status, error, postedAt)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal update status produk $id", e)
            }
        }
    }

    private fun notify(title: String, text: String, actions: List<NotificationHelper.Action>, ongoing: Boolean = false) {
        val ctx = appContext ?: return
        NotificationHelper.show(ctx, title, text, actions, ongoing)
    }

    private fun log(product: String?, message: String, level: LogLevel) {
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, "[${product ?: "-"}] $message")
            LogLevel.WARN -> Log.w(TAG, "[${product ?: "-"}] $message")
            else -> Log.d(TAG, "[${product ?: "-"}] $message")
        }
        _logs.value = (_logs.value + LogLine(System.currentTimeMillis(), product, message, level)).takeLast(MAX_LOGS)
    }
}
