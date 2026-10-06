package com.robotta.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robotta.ai.AiContent
import com.robotta.ai.KeywordIdea
import com.robotta.ai.KeywordResearch
import com.robotta.ai.TitleGenerator
import com.robotta.automation.AutomationEngine
import com.robotta.automation.EngineState
import com.robotta.automation.LogLine
import com.robotta.data.AppDatabase
import com.robotta.data.AppSettings
import com.robotta.data.CsvImporter
import com.robotta.data.Region
import com.robotta.data.Regions
import com.robotta.data.SettingsStore
import com.robotta.data.entities.Account
import com.robotta.data.entities.Product
import com.robotta.data.entities.ProductImages
import com.robotta.data.entities.ProductStatus
import com.robotta.image.FrameProcessor
import com.robotta.image.FrameStyle
import com.robotta.image.GalleryExporter
import com.robotta.image.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val productDao = db.productDao()
    private val accountDao = db.accountDao()
    private val settingsStore = SettingsStore(app)
    private val photoStore = PhotoStore(app)
    private val frameProcessor = FrameProcessor(app)

    val products: StateFlow<List<Product>> = productDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val account: StateFlow<Account> = accountDao.observeProfile()
        .map { it ?: Account() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Account())

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val engineState: StateFlow<EngineState> = AutomationEngine.state
    val logs: StateFlow<List<LogLine>> = AutomationEngine.logs

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private fun say(message: String) {
        _toast.tryEmit(message)
    }

    private fun generator(): TitleGenerator =
        _settings.value.let { TitleGenerator(it.geminiApiKey, it.geminiModel) }

    // ---------------------------------------------------------------- produk

    suspend fun getProduct(id: Int): Product? = withContext(Dispatchers.IO) {
        try {
            productDao.getById(id)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat produk $id", e)
            null
        }
    }

    fun saveProduct(
        id: Int,
        title: String,
        price: Long,
        category: String,
        condition: String,
        description: String,
        location: String,
        hashtags: String,
        photos: List<String>,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val existing = if (id > 0) productDao.getById(id) else null
                    if (existing != null) {
                        val newStatus =
                            if (existing.status == ProductStatus.SUCCESS) existing.status else ProductStatus.PENDING
                        productDao.update(
                            existing.copy(
                                title = title.trim(),
                                price = price,
                                category = category.trim(),
                                condition = condition,
                                description = description.trim(),
                                location = location.trim(),
                                hashtags = TitleGenerator.normalizeHashtags(hashtags),
                                imagePaths = ProductImages.encode(photos),
                                status = newStatus,
                                errorMessage = if (newStatus == ProductStatus.PENDING) "" else existing.errorMessage
                            )
                        )
                        // Hapus file foto yang tidak dipakai lagi.
                        existing.imageList().filter { it !in photos }.forEach { photoStore.deleteIfOwned(it) }
                    } else {
                        productDao.insert(
                            Product(
                                title = title.trim(),
                                price = price,
                                category = category.trim(),
                                condition = condition,
                                description = description.trim(),
                                location = location.trim(),
                                hashtags = TitleGenerator.normalizeHashtags(hashtags),
                                imagePaths = ProductImages.encode(photos)
                            )
                        )
                    }
                }
                say("Produk disimpan")
                onSaved()
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menyimpan produk", e)
                say("Gagal menyimpan: ${e.message}")
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    productDao.delete(product)
                    product.imageList().forEach { photoStore.deleteIfOwned(it) }
                }
                say("Produk dihapus")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menghapus produk", e)
                say("Gagal menghapus: ${e.message}")
            }
        }
    }

    fun setPending(product: Product) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                productDao.updateStatus(product.id, ProductStatus.PENDING, "", null)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal reset status", e)
            }
        }
    }

    fun resetFailed() {
        viewModelScope.launch {
            try {
                val n = withContext(Dispatchers.IO) { productDao.resetFailedAndSkipped() }
                say("$n produk dikembalikan ke Menunggu")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal reset", e)
            }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: run {
                    say("File tidak bisa dibuka")
                    return@launch
                }
                val defaultLocation = withContext(Dispatchers.IO) { accountDao.getProfile()?.defaultLocation.orEmpty() }
                val result = CsvImporter.parse(text, defaultLocation)
                if (result.products.isNotEmpty()) {
                    withContext(Dispatchers.IO) { productDao.insertAll(result.products) }
                }
                val skipped = if (result.errors.isEmpty()) "" else ", ${result.errors.size} baris dilewati (${result.errors.first()})"
                say("${result.products.size} produk diimpor$skipped. Tambahkan fotonya di tiap produk.")
            } catch (e: Exception) {
                Log.e(TAG, "Import CSV gagal", e)
                say("Import gagal: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------- foto

    suspend fun importPhotos(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val paths = uris.mapNotNull { photoStore.importFromUri(it) }
        if (paths.size < uris.size) say("${uris.size - paths.size} foto gagal dibaca")
        paths
    }

    suspend fun applyFrame(paths: List<String>, style: FrameStyle, priceText: String, accentColor: Int): List<String> =
        withContext(Dispatchers.Default) {
            try {
                val storeName = accountDao.getProfile()?.storeName.orEmpty()
                if (storeName.isBlank() && (style == FrameStyle.COLOR_BORDER || style == FrameStyle.WATERMARK)) {
                    say("Isi Nama Toko di menu Profil supaya nama toko muncul di bingkai")
                }
                frameProcessor.applyToAll(paths, style, storeName, priceText, accentColor)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memberi bingkai", e)
                say("Gagal memberi bingkai: ${e.message}")
                paths
            }
        }

    // ---------------------------------------------------------------- AI

    suspend fun generateTitles(name: String, category: String, condition: String): List<String> {
        val gen = generator()
        if (!gen.hasApiKey) say("API key Gemini kosong — memakai template offline")
        return try {
            gen.generateTitles(name, category, condition)
        } catch (e: Exception) {
            Log.e(TAG, "AI judul gagal", e)
            say(e.message ?: "AI gagal")
            emptyList()
        }
    }

    suspend fun generateDescription(name: String, category: String, condition: String, notes: String): String? {
        val gen = generator()
        if (!gen.hasApiKey) say("API key Gemini kosong — memakai template offline")
        return try {
            gen.generateDescription(name, category, condition, notes)
        } catch (e: Exception) {
            Log.e(TAG, "AI deskripsi gagal", e)
            say(e.message ?: "AI gagal")
            null
        }
    }

    /** "Buat Konten AI": judul + deskripsi + hashtag + saran kategori untuk satu produk. */
    suspend fun generateContent(name: String, category: String, condition: String, notes: String): AiContent? {
        val gen = generator()
        if (!gen.hasApiKey) say("API key Gemini kosong — memakai template offline")
        return try {
            gen.generateContent(name, category, condition, notes)
        } catch (e: Exception) {
            Log.e(TAG, "Buat Konten AI gagal", e)
            say(e.message ?: "AI gagal")
            null
        }
    }

    suspend fun researchKeywords(seed: String, expand: Boolean, onProgress: (Int, Int) -> Unit): List<KeywordIdea> =
        try {
            KeywordResearch(generator()).research(seed, expand, onProgress).also {
                if (it.isEmpty()) say("Tidak ada saran. Cek koneksi internet atau coba kata lain.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Riset kata kunci gagal", e)
            say("Riset gagal: ${e.message}")
            emptyList()
        }

    // ---------------------------------------------------------------- riset lokasi

    suspend fun searchRegions(query: String, province: String?): List<Region> = withContext(Dispatchers.Default) {
        Regions.search(getApplication(), query, province)
    }

    suspend fun provinces(): List<String> = withContext(Dispatchers.IO) { Regions.provinces(getApplication()) }

    fun setDefaultLocation(location: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = accountDao.getProfile() ?: Account()
                    accountDao.save(current.copy(defaultLocation = location, updatedAt = System.currentTimeMillis()))
                }
                say("Lokasi default: $location")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menyimpan lokasi", e)
                say("Gagal menyimpan lokasi")
            }
        }
    }

    // ---------------------------------------------------------------- auto frame (alat terpisah)

    /** Memberi bingkai lalu menyimpan hasilnya ke galeri (Pictures/AutoFrame). Satu foto -> satu hasil. */
    suspend fun frameToGallery(uris: List<Uri>, style: FrameStyle, text: String, priceText: String, accentColor: Int): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val sources = uris.mapNotNull { photoStore.importFromUri(it) }
                val framed = frameProcessor.applyToAll(sources, style, text, priceText, accentColor)
                val saved = GalleryExporter(getApplication()).saveCopies(framed, GalleryExporter.FRAME_FOLDER)
                // Foto sumber sementara tidak dibutuhkan lagi.
                sources.filter { it !in framed }.forEach { photoStore.deleteIfOwned(it) }
                say("$saved foto disimpan ke galeri (Pictures/${GalleryExporter.FRAME_FOLDER})")
                framed
            } catch (e: Exception) {
                Log.e(TAG, "Auto frame gagal", e)
                say("Gagal: ${e.message}")
                emptyList()
            }
        }

    fun testAi() {
        viewModelScope.launch {
            try {
                say(generator().testConnection())
            } catch (e: Exception) {
                say(e.message ?: "Tes gagal")
            }
        }
    }

    // ---------------------------------------------------------------- profil & pengaturan

    fun saveAccount(account: Account) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    accountDao.save(account.copy(id = Account.PROFILE_ID, updatedAt = System.currentTimeMillis()))
                }
                say("Profil disimpan")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menyimpan profil", e)
                say("Gagal menyimpan profil")
            }
        }
    }

    fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        settingsStore.save(settings)
        say("Pengaturan disimpan")
    }

    // ---------------------------------------------------------------- sesi posting

    fun startPosting() {
        viewModelScope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { productDao.getByStatus(ProductStatus.PENDING) }
                val profile = withContext(Dispatchers.IO) { accountDao.getProfile() } ?: Account()
                AutomationEngine.startSession(getApplication(), pending, _settings.value, profile)?.let { say(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memulai sesi", e)
                say("Gagal memulai: ${e.message}")
            }
        }
    }

    fun next() = AutomationEngine.nextProduct()
    fun markPosted() = AutomationEngine.markCurrentPosted()
    fun skip() = AutomationEngine.skipCurrent()
    fun retry() = AutomationEngine.retryCurrent()
    fun stop() = AutomationEngine.stop()
    fun clearLogs() = AutomationEngine.clearLogs()

    private companion object {
        const val TAG = "AppViewModel"
    }
}
