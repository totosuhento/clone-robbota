package com.robotta.automation

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Menerima tombol-tombol di notifikasi (Produk berikutnya, Lewati, Berhenti, dst). */
class EngineActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Aksi notifikasi: ${intent.action}")
        try {
            when (intent.action) {
                ACTION_NEXT -> AutomationEngine.nextProduct()
                ACTION_MARK_POSTED -> AutomationEngine.markCurrentPosted()
                ACTION_SKIP -> AutomationEngine.skipCurrent()
                ACTION_RETRY -> AutomationEngine.retryCurrent()
                ACTION_STOP -> AutomationEngine.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menjalankan aksi ${intent.action}", e)
        }
    }

    companion object {
        private const val TAG = "EngineActionReceiver"
        const val ACTION_NEXT = "com.robotta.action.NEXT"
        const val ACTION_MARK_POSTED = "com.robotta.action.MARK_POSTED"
        const val ACTION_SKIP = "com.robotta.action.SKIP"
        const val ACTION_RETRY = "com.robotta.action.RETRY"
        const val ACTION_STOP = "com.robotta.action.STOP"

        fun pendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, EngineActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
