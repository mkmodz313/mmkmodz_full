package com.mkmodz.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mkmodz.assistant.R
import com.mkmodz.assistant.ui.main.MainActivity

class MkmodzOverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "SHOW_OVERLAY"
        const val ACTION_UPDATE = "UPDATE_STATE"
        const val EXTRA_SPEAKING = "speaking"
        private const val CHANNEL_ID = "mkmodz_overlay_channel"
        private const val NOTIF_ID = 4101

        fun updateState(context: Context, speaking: Boolean) {
            try {
                val intent = Intent(context, MkmodzOverlayService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_SPEAKING, speaking)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Service could not start (permission or background limits) - ignore.
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(intent?.getBooleanExtra(EXTRA_SPEAKING, false) ?: false)
        if (intent?.action == ACTION_SHOW) {
            try {
                val launch = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("TRIGGER_MIC", true)
                }
                startActivity(launch)
            } catch (e: Exception) {
                // Background activity start not allowed - ignore.
            }
        }
        return START_STICKY
    }

    private fun startAsForeground(speaking: Boolean) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, "mkmodz", NotificationManager.IMPORTANCE_MIN)
                nm.createNotificationChannel(ch)
            }
            val openApp = android.app.PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mkmodz_notif)
                .setContentTitle("mkmodz")
                .setContentText(if (speaking) "Bol rahi hoon..." else "Ready")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            startForeground(NOTIF_ID, notif)
        } catch (e: Exception) {
            // Foreground start rejected (e.g. mic permission missing) - keep running quietly.
        }
    }
}
