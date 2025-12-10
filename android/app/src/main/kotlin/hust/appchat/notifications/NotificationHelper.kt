// android/app/src/main/kotlin/hust/appchat/notifications/NotificationHelper.kt
package hust.appchat.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.BubbleActivity
import hust.appchat.MainActivity
import hust.appchat.R
import kotlinx.coroutines.*
import hust.appchat.shortcuts.ShortcutHelper // ✅ CHANGE 1: Add import

/**
 * ✅ GIAI ĐOẠN 2 & 3: Notification Helper with Bubble API Support and ShortcutHelper integration
 */
object NotificationHelper {
    private const val TAG = "NotificationHelper"

    private const val CHANNEL_ID = "chat_messages"
    private const val CHANNEL_NAME = "Chat Messages"
    private const val CHANNEL_DESC = "Notifications for chat messages"
    private const val BASE_NOTIFICATION_ID = 1000

    private val avatarCache = mutableMapOf<String, Icon>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ========================================
    // INITIALIZATION
    // ========================================

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }

                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            Log.d(TAG, "✅ Notification channel created")
        }
    }

    // ========================================
    // BUBBLE NOTIFICATION
    // ========================================

    suspend fun showBubbleNotification(
        context: Context,
        userId: String,
        userName: String,
        message: String,
        avatarUrl: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "⚠️ Bubble API requires Android 11+")
            showLegacyNotification(context, userId, userName, message)
            return
        }

        try {
            Log.d(TAG, "🎈 Creating bubble notification: $userName")

            // ✅ GIAI ĐOẠN 3: ADD THIS LINE - Ensure shortcut exists BEFORE creating notification
            ShortcutHelper.ensureShortcutForNotification(
                context = context,
                userId = userId,
                userName = userName,
                avatarUrl = avatarUrl
            )

            val avatarIcon = loadAvatarIcon(context, avatarUrl, userName)

            val bubbleMetadata = createBubbleMetadata(
                context, userId, userName, avatarUrl, avatarIcon
            )

            val notification = buildBubbleNotification(
                context, userId, userName, message, avatarIcon, bubbleMetadata
            )

            val notificationId = getNotificationId(userId)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(notificationId, notification)

            Log.d(TAG, "✅ Bubble notification shown: $userName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show bubble notification: $e")
            showLegacyNotification(context, userId, userName, message)
        }
    }

    // ========================================
    // BUBBLE METADATA
    // ========================================

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createBubbleMetadata(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        avatarIcon: Icon
    ): Notification.BubbleMetadata {

        val intent = BubbleActivity.createIntent(
            context = context,
            userId = userId,
            userName = userName,
            avatarUrl = avatarUrl
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            userId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return Notification.BubbleMetadata.Builder(pendingIntent, avatarIcon)
            .setDesiredHeight(600)
            .setAutoExpandBubble(false)
            .setSuppressNotification(false)
            .build()
    }

    // ========================================
    // NOTIFICATION BUILDER
    // ========================================

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildBubbleNotification(
        context: Context,
        userId: String,
        userName: String,
        message: String,
        avatarIcon: Icon,
        bubbleMetadata: Notification.BubbleMetadata
    ): Notification {

        val person = Person.Builder()
            .setName(userName)
            .setIcon(avatarIcon)
            .setKey(userId)
            .setImportant(true)
            .build()

        val messagingStyle = Notification.MessagingStyle(person)
            .setConversationTitle(userName)
            .addMessage(message, System.currentTimeMillis(), person)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("userId", userId)
            putExtra("userName", userName)
        }

        val tapPendingIntent = PendingIntent.getActivity(
            context,
            userId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(userName)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(loadAvatarBitmap(context, avatarIcon))
            .setStyle(messagingStyle)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setBubbleMetadata(bubbleMetadata)
            .setShortcutId(userId)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(false)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    // ========================================
    // SHORTCUTS (DELEGATED TO SHORTCUTHELPER)
    // ========================================

    /**
     * ✅ GIAI ĐOẠN 3: Deprecated - Use ShortcutHelper.createShortcut() instead
     *
     * This method is kept for backward compatibility but delegates to ShortcutHelper
     */
    private suspend fun createShortcut( // ✅ CHANGE 2: Replace createShortcut() method
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        // ✅ Delegate to ShortcutHelper
        ShortcutHelper.createShortcut(
            context = context,
            userId = userId,
            userName = userName,
            avatarUrl = avatarUrl
        )

        Log.d(TAG, "✅ Shortcut created via ShortcutHelper")
    }

    // ========================================
    // AVATAR LOADING
    // ========================================

    private suspend fun loadAvatarIcon(
        context: Context,
        avatarUrl: String,
        userName: String
    ): Icon = withContext(Dispatchers.IO) {

        avatarCache[avatarUrl]?.let {
            return@withContext it
        }

        try {
            val bitmap = if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .asBitmap()
                    .load(avatarUrl)
                    .apply(
                        RequestOptions()
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(100, 100)
                    )
                    .submit()
                    .get()
            } else {
                createDefaultAvatar(context, userName)
            }

            val icon = Icon.createWithBitmap(bitmap)
            avatarCache[avatarUrl] = icon

            return@withContext icon

        } catch (e: Exception) {
            Log.e(TAG, "❌ Avatar load failed: $e")
            return@withContext Icon.createWithBitmap(
                createDefaultAvatar(context, userName)
            )
        }
    }

    private suspend fun loadAvatarIconCompat(
        context: Context,
        avatarUrl: String,
        userName: String
    ): IconCompat = withContext(Dispatchers.IO) {

        try {
            val bitmap = if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .asBitmap()
                    .load(avatarUrl)
                    .apply(
                        RequestOptions()
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(100, 100)
                    )
                    .submit()
                    .get()
            } else {
                createDefaultAvatar(context, userName)
            }

            return@withContext IconCompat.createWithBitmap(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Avatar load failed: $e")
            return@withContext IconCompat.createWithBitmap(
                createDefaultAvatar(context, userName)
            )
        }
    }

    private fun loadAvatarBitmap(context: Context, icon: Icon): Bitmap? {
        return try {
            icon.loadDrawable(context)?.let { drawable ->
                val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to convert icon to bitmap: $e")
            null
        }
    }

    private fun createDefaultAvatar(context: Context, userName: String): Bitmap {
        val size = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.parseColor("#2196F3")
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val initial = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        paint.apply {
            color = Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
        }

        val textY = (size / 2f) - ((paint.descent() + paint.ascent()) / 2)
        canvas.drawText(initial, size / 2f, textY, paint)

        return bitmap
    }

    // ========================================
    // LEGACY NOTIFICATION
    // ========================================

    private fun showLegacyNotification(
        context: Context,
        userId: String,
        userName: String,
        message: String
    ) {
        try {
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("userId", userId)
                putExtra("userName", userName)
            }

            val tapPendingIntent = PendingIntent.getActivity(
                context,
                userId.hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
                    .setContentTitle(userName)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(tapPendingIntent)
                    .setAutoCancel(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
                    .setContentTitle(userName)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(tapPendingIntent)
                    .setAutoCancel(true)
                    .build()
            }

            val notificationId = getNotificationId(userId)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(notificationId, notification)

            Log.d(TAG, "✅ Legacy notification shown: $userName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Legacy notification failed: $e")
        }
    }

    // ========================================
    // NOTIFICATION & SHORTCUT MANAGEMENT
    // ========================================

    fun removeShortcut(context: Context, userId: String) { // ✅ CHANGE 3: Replace removeShortcut() method
        try {
            // ✅ Use ShortcutHelper
            ShortcutHelper.removeShortcut(context, userId)

            Log.d(TAG, "✅ Shortcut removed via ShortcutHelper")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove shortcut: $e")
        }
    }

    /**
     * ✅ Kiểm tra xem notification có khớp với shortcut không
     */
    fun verifyShortcutExists(context: Context, userId: String): Boolean { // ✅ CHANGE 5: ADD new utility method
        return ShortcutHelper.shortcutExists(context, userId)
    }

    /**
     * ✅ Sync tất cả shortcuts với active notifications
     */
    suspend fun syncShortcutsWithNotifications( // ✅ CHANGE 5: ADD new utility method
        context: Context,
        activeUsers: List<Triple<String, String, String>> // userId, userName, avatarUrl
    ) {
        ShortcutHelper.createShortcutsBatch(context, activeUsers)
    }

    fun cancelNotification(context: Context, userId: String) { // ✅ CHANGE 6: Update cancelNotification() method
        try {
            val notificationId = getNotificationId(userId)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancel(notificationId)

            // ✅ GIAI ĐOẠN 3: ADD THIS LINE - Also remove shortcut
            ShortcutHelper.removeShortcut(context, userId)

            Log.d(TAG, "✅ Notification + shortcut cancelled: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cancel notification failed: $e")
        }
    }

    fun cancelAllNotifications(context: Context) { // ✅ CHANGE 7: Update cancelAllNotifications() method
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancelAll()

            // ✅ GIAI ĐOẠN 3: ADD THIS LINE - Also remove all shortcuts
            ShortcutHelper.removeAllShortcuts(context)

            Log.d(TAG, "✅ All notifications + shortcuts cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cancel all notifications failed: $e")
        }
    }

    private fun getNotificationId(userId: String): Int {
        return BASE_NOTIFICATION_ID + userId.hashCode() % 1000
    }

    // ========================================
    // CLEANUP
    // ========================================

    fun clearCache() {
        avatarCache.clear()
        Log.d(TAG, "✅ Avatar cache cleared")
    }

    fun cleanup() { // ✅ CHANGE 8: Update cleanup() method
        scope.cancel()
        clearCache()
        ShortcutHelper.cleanup() // ✅ ADD THIS LINE - Also cleanup shortcuts
        Log.d(TAG, "✅ NotificationHelper cleanup complete")
    }
}