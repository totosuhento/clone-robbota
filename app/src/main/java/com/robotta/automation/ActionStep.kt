package com.robotta.automation

/** Langkah-langkah pengisian satu produk, berurutan. */
enum class ActionStep(val label: String, val required: Boolean) {
    EXPORT_PHOTOS("Menyiapkan foto di galeri", true),
    OPEN_FORM("Membuka form Jual Barang di Facebook", true),
    SELECT_PHOTOS("Memilih foto", true),
    FILL_TITLE("Mengisi judul", true),
    FILL_PRICE("Mengisi harga", true),
    SELECT_CATEGORY("Memilih kategori", false),
    SELECT_CONDITION("Memilih kondisi", false),
    FILL_DESCRIPTION("Mengisi deskripsi", false),
    FILL_LOCATION("Mengisi lokasi", false),
    REVIEW("Menunggu kamu memeriksa & mempublikasikan", true)
}

enum class PhotoPickResult { DONE, NEED_USER, FAILED }

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class LogLine(
    val time: Long,
    val product: String?,
    val message: String,
    val level: LogLevel
)

/** Status mesin asisten yang ditampilkan di UI & notifikasi. */
sealed class EngineState {
    data object Idle : EngineState()

    data class Working(
        val index: Int,
        val total: Int,
        val productTitle: String,
        val step: ActionStep
    ) : EngineState()

    /** Butuh tindakanmu. Jika [autoContinue] = true, asisten lanjut sendiri setelah kamu selesai. */
    data class NeedsUser(
        val index: Int,
        val total: Int,
        val productTitle: String,
        val message: String,
        val canRetry: Boolean,
        val autoContinue: Boolean = false
    ) : EngineState()

    data class AwaitingPublish(
        val index: Int,
        val total: Int,
        val productTitle: String,
        val warnings: List<String>
    ) : EngineState()

    data class Posted(
        val index: Int,
        val total: Int,
        val productTitle: String,
        val hasNext: Boolean
    ) : EngineState()

    data class Finished(val posted: Int, val notPosted: Int) : EngineState()

    val isActive: Boolean
        get() = this is Working || this is NeedsUser || this is AwaitingPublish || this is Posted
}
