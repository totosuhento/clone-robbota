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
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val MIN_DELAY = 400L
        const val MAX_DELAY = 2500L
        const val MAX_SESSION = 30
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pengaturan", Context.MODE_PRIVATE)

    fun load(): AppSettings = try {
        AppSettings(
            stepDelayMs = prefs.getLong(KEY_DELAY, 800L).coerceIn(AppSettings.MIN_DELAY, AppSettings.MAX_DELAY),
            sessionLimit = prefs.getInt(KEY_LIMIT, 10).coerceIn(1, AppSettings.MAX_SESSION),
            autoPickPhotos = prefs.getBoolean(KEY_AUTO_PHOTO, true),
            geminiApiKey = prefs.getString(KEY_GEMINI_KEY, "") ?: "",
            geminiModel = (prefs.getString(KEY_GEMINI_MODEL, AppSettings.DEFAULT_MODEL) ?: "")
                .ifBlank { AppSettings.DEFAULT_MODEL }
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
                .putString(KEY_GEMINI_KEY, settings.geminiApiKey.trim())
                .putString(KEY_GEMINI_MODEL, settings.geminiModel.trim())
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
        const val KEY_GEMINI_KEY = "gemini_api_key"
        const val KEY_GEMINI_MODEL = "gemini_model"
    }
}
