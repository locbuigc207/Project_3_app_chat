// android/app/src/main/kotlin/hust/appchat/services/BubbleForegroundService.kt
// ✅ CRITICAL FOR HYPEROS: Foreground Service to keep bubbles alive

package hust.appchat.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import hust.appchat.MainActivity
import hust.appchat.R

@RequiresApi(Build.VERSION_CODES.R)
class BubbleForegroundService : Service() {

    companion object {
        private const val TAG = "BubbleFgService"
        private const val CHANNEL_ID = "bubble_foreground"
        private const val NOTIFICATION_ID = 9999

        /**
         * Start the foreground service
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, BubbleForegroundService::class.java)
                context.startForegroundService(intent)
                Log.d(TAG, "✅ Service start requested")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start service: $e")
            }
        }

        /**
         * Stop the foreground service
         */
        fun stop(context: Context) {
            try {
                val intent = Intent(context, BubbleForegroundService::class.java)
                context.stopService(intent)
                Log.d(TAG, "✅ Service stop requested")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to stop service: $e")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "📍 onCreate")

        // ✅ Create notification channel
        createNotificationChannel()

        // ✅ CRITICAL: Start foreground IMMEDIATELY (within 5 seconds)
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 onStartCommand")

        // ✅ Ensure notification is always shown
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // ✅ CRITICAL: Return START_STICKY to restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "💥 onDestroy")
        super.onDestroy()
    }

    /**
     * Create notification channel for Android 8+
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chat Bubbles Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps chat bubbles running in the background"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        Log.d(TAG, "✅ Notification channel created")
    }

    /**
     * Create foreground notification
     */
    private fun createNotification(): Notification {
        // ✅ Intent to open main app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat Bubbles Active")
            .setContentText("Tap to open app")
            .setSmallIcon(R.drawable.ic_notification) // ✅ Use your notification icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // ✅ Cannot be dismissed
            .setShowWhen(false)
            .setAutoCancel(false)
            .build()
    }
}