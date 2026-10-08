package com.robotta.automation

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Navigasi ke grup Facebook dan membagikan postingan.
 * Digunakan oleh MarketAutomationService saat fitur "Share ke Grup" aktif.
 */
object GroupSharer {
    private const val TAG = "GroupSharer"

    /**
     * Navigasi ke halaman grup tertentu di Facebook lalu membagikan postingan.
     *
     * Alur:
     * 1. Buka Facebook
     * 2. Cari menu/tab Grup
     * 3. Cari grup berdasarkan nama
     * 4. Klik grup
     * 5. Cari tombol "Bagikan" atau "Tulis sesuatu"
     * 6. Tempel link/teks postingan
     */
    suspend fun shareToGroup(
        service: MarketAutomationService,
        groupName: String,
        postLink: String = "",
        stepDelay: Long
    ): Boolean {
        Log.d(TAG, "Mulai share ke grup: $groupName")

        // Cari tab Grup
        val groupsTab: AccessibilityNodeInfo? = service.waitFor(5_000L) { roots ->
            val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
            NodeFinder.findByLabels(fb, FbLabels.GROUPS, MatchMode.CONTAINS)
        }
        if (groupsTab != null) {
            service.tapAndVerify(groupsTab)
        } else {
            Log.d(TAG, "Tab Grup tidak ditemukan, coba lewat Menu")
            // Coba navigasi via Menu -> Grup
            if (!service.tapInFacebook(FbLabels.MENU, 3_000L, MatchMode.EXACT) &&
                !service.tapInFacebook(FbLabels.MENU, 2_000L)
            ) return false
            kotlinx.coroutines.delay(1_500L)
            if (!service.tapInFacebook(FbLabels.GROUPS, 4_000L, MatchMode.CONTAINS)) return false
        }
        kotlinx.coroutines.delay(stepDelay * 2)

        // Cari grup berdasarkan nama
        val searchField = service.waitFor(5_000L) { roots ->
            val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
            NodeFinder.findEditable(fb, FbLabels.GROUP_SEARCH)
        }

        if (searchField != null) {
            service.setText(searchField, groupName)
            kotlinx.coroutines.delay(2_000L)
        }

        // Klik grup yang cocok
        val groupNode = service.waitFor(5_000L) { roots ->
            val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
            NodeFinder.findByLabels(fb, listOf(groupName), MatchMode.CONTAINS)
        } ?: run {
            Log.d(TAG, "Grup '$groupName' tidak ditemukan")
            return false
        }
        service.tapAndVerify(groupNode)
        kotlinx.coroutines.delay(stepDelay * 2)

        // Cari tombol tulis postingan atau bagikan
        val shareBtn = service.waitFor(5_000L) { roots ->
            val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
            NodeFinder.findByLabels(fb, FbLabels.POST_TO_GROUP + FbLabels.SHARE, MatchMode.CONTAINS)
        }

        if (shareBtn != null) {
            service.tapAndVerify(shareBtn)
            kotlinx.coroutines.delay(stepDelay)
            Log.d(TAG, "Tombol share/bagikan di grup '$groupName' diklik")
            return true
        }

        // Jika ada link postingan, tempel
        if (postLink.isNotBlank()) {
            val writeField = service.waitFor(3_000L) { roots ->
                val fb = roots.filter { it.packageName?.toString() == FbLabels.FB_PACKAGE }
                NodeFinder.findEditable(fb, listOf("Tulis sesuatu", "Write something", "Tulis"))
            }
            if (writeField != null) {
                service.setText(writeField, postLink)
                kotlinx.coroutines.delay(stepDelay)
                Log.d(TAG, "Link postingan ditempel di grup '$groupName'")
                return true
            }
        }

        Log.d(TAG, "Tidak bisa share ke grup '$groupName'")
        return false
    }
}