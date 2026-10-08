package com.robotta.data

import android.content.Context
import android.util.Log

data class AppSettings(
    /** Jeda antar langkah pengisian (ms). Naikkan untuk HP yang lambat. */
    val stepDelayMs: Long = 800L,
    /** Jumlah produk maksimal dalam satu sesi posting. */
    val sessionLimit: Int = 10,
    /** Coba pilih foto di galeri secara otomatis; jika gagal, kamu diminta memilih sendiri. */
    val autoPickPhotos: Boolean = true,
    /** Panel kontrol melayang di atas Facebook selama sesi berjalan. */
    val showFloatingPanel: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_MODEL,
    // ---------- Multi Akun ----------
    val activeAccountId: Int = 1,
    // ---------- Anti Duplikat ----------
    val antiDuplikatEnabled: Boolean = false,
    // ---------- Lokasi Random ----------
    val randomLocationEnabled: Boolean = false,
    val randomLocationCount: Int = 5,
    // ---------- Share Grup ----------
    val autoShareGroups: Boolean = false,
    val targetGroups: String = ""
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val MIN_DELAY = 400L
        const val MAX_DELAY = 2500L
        const val MAX_SESSION = 30
        const val MAX_RANDOM_CITIES = 30
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pengaturan", Context.MODE_PRIVATE)

    fun load(): AppSettings = try {
        AppSettings(
            stepDelayMs = prefs.getLong(KEY_DELAY, 800L).coerceIn(AppSettings.MIN_DELAY, AppSettings.MAX_DELAY),
            sessionLimit = prefs.getInt(KEY_LIMIT, 10).coerceIn(1, AppSettings.MAX_SESSION),
            autoPickPhotos = prefs.getBoolean(KEY_AUTO_PHOTO, true),
            showFloatingPanel = prefs.getBoolean(KEY_PANEL, true),
            geminiApiKey = prefs.getString(KEY_GEMINI_KEY, "") ?: "",
            geminiModel = (prefs.getString(KEY_GEMINI_MODEL, AppSettings.DEFAULT_MODEL) ?: "")
                .ifBlank { AppSettings.DEFAULT_MODEL },
            activeAccountId = prefs.getInt(KEY_ACTIVE_ACCOUNT, 1).coerceAtLeast(1),
            antiDuplikatEnabled = prefs.getBoolean(KEY_ANTI_DUPLIKAT, false),
            randomLocationEnabled = prefs.getBoolean(KEY_RANDOM_LOC, false),
            randomLocationCount = prefs.getInt(KEY_RANDOM_COUNT, 5)
                .coerceIn(1, AppSettings.MAX_RANDOM_CITIES),
            autoShareGroups = prefs.getBoolean(KEY_AUTO_SHARE, false),
            targetGroups = prefs.getString(KEY_TARGET_GROUPS, "") ?: ""
        )
    } catch (e: Exception) {
        Log.e(TAG, "Gagal membaca pengaturan, pakai default", e)
        AppSettings()
    }

    fun save(settings: AppSettings) {
        try {
            prefs.edit()
                .putLong(KEY_DELAY, settings.stepDelayMs)
                .putInt(KEY_LIMIT, settings.sessionLimit)
                .putBoolean(KEY_AUTO_PHOTO, settings.autoPickPhotos)
                .putBoolean(KEY_PANEL, settings.showFloatingPanel)
                .putString(KEY_GEMINI_KEY, settings.geminiApiKey.trim())
                .putString(KEY_GEMINI_MODEL, settings.geminiModel.trim())
                .putInt(KEY_ACTIVE_ACCOUNT, settings.activeAccountId.coerceAtLeast(1))
                .putBoolean(KEY_ANTI_DUPLIKAT, settings.antiDuplikatEnabled)
                .putBoolean(KEY_RANDOM_LOC, settings.randomLocationEnabled)
                .putInt(KEY_RANDOM_COUNT, settings.randomLocationCount.coerceIn(1, AppSettings.MAX_RANDOM_CITIES))
                .putBoolean(KEY_AUTO_SHARE, settings.autoShareGroups)
                .putString(KEY_TARGET_GROUPS, settings.targetGroups.trim())
                .apply()
            Log.d(TAG, "Pengaturan disimpan")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan pengaturan", e)
        }
    }

    private companion object {
        const val TAG = "SettingsStore"
        const val KEY_DELAY = "step_delay_ms"
        const val KEY_LIMIT = "session_limit"
        const val KEY_AUTO_PHOTO = "auto_pick_photos"
        const val KEY_PANEL = "show_floating_panel"
        const val KEY_GEMINI_KEY = "gemini_api_key"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_ACTIVE_ACCOUNT = "active_account_id"
        const val KEY_ANTI_DUPLIKAT = "anti_duplikat"
        const val KEY_RANDOM_LOC = "random_location"
        const val KEY_RANDOM_COUNT = "random_location_count"
        const val KEY_AUTO_SHARE = "auto_share_groups"
        const val KEY_TARGET_GROUPS = "target_groups"
    }
}