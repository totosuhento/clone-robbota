package com.robotta.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.robotta.MainActivity
import com.robotta.R
import com.robotta.automation.EngineActionReceiver

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "status_posting"
    private const val NOTIFICATION_ID = 4201

    data class Action(val label: String, val action: String)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Status posting",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Memberi tahu saat produk siap dipublikasikan atau butuh tindakanmu" }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat channel notifikasi", e)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, text: String, actions: List<Action> = emptyList(), ongoing: Boolean = false) {
        if (!canNotify(context)) {
            Log.d(TAG, "Izin notifikasi belum diberikan: $title")
            return
        }
        try {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
            actions.forEach { a ->
                builder.addAction(0, a.label, EngineActionReceiver.pendingIntent(context, a.action))
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan notifikasi", e)
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menutup notifikasi", e)
        }
    }
}
