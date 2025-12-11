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
import androidx.core.app.NotificationCompat // Thêm từ phần FIX
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutManagerCompat // Phần 1 có, phần 2 không (không dùng nhưng giữ theo phần 1)
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.BubbleActivity
import hust.appchat.MainActivity
import hust.appchat.R
import hust.appchat.shortcuts.AvatarLoader
import hust.appchat.shortcuts.ShortcutHelper
import kotlinx.coroutines.*

/**
 * ✅ Notification Helper with AvatarLoader Integration and ShortcutHelper delegation
 * Hợp nhất GIAI ĐOẠN 6 (logic chung) và FIX (cải tiến builder)
 */
object NotificationHelper {
    private const val TAG = "NotificationHelper"

    private const val CHANNEL_ID = "chat_messages"
    private const val CHANNEL_NAME = "Chat Messages"
    private const val CHANNEL_DESC = "Notifications for chat messages"
    private const val BASE_NOTIFICATION_ID = 1000

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Giữ lại scope từ phần 2/FIX (Phần 1 REMOVED nhưng vẫn có hàm gọi scope.cancel() trong cleanup)

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

            // ✅ GIAI ĐOẠN 3 & 6: Ensure shortcut exists BEFORE creating notification
            ShortcutHelper.ensureShortcutForNotification(
                context = context,
                userId = userId,
                userName = userName,
                avatarUrl = avatarUrl
            )

            // ✅ GIAI ĐOẠN 6: Use AvatarLoader with caching
            val avatarIcon = AvatarLoader.loadAvatarIconAsync(
                context = context,
                avatarUrl = avatarUrl,
                userName = userName
            )

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
    // NOTIFICATION BUILDER (Sử dụng logic FIX từ phần 2)
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

        // ✅ FIX 1: Create Person properly
        val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Person.Builder()
                .setName(userName)
                .setIcon(avatarIcon)
                .setKey(userId)
                .setImportant(true)
                .build()
        } else {
            null
        }

        // ✅ FIX 2: Build MessagingStyle correctly
        val messagingStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && person != null) {
            Notification.MessagingStyle(person)
                .addMessage(message, System.currentTimeMillis(), person)
        } else {
            Notification.MessagingStyle(userName)
                .addMessage(message, System.currentTimeMillis(), null as CharSequence?)
        }

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

    /**
     * ✅ Kiểm tra xem notification có khớp với shortcut không
     * Tên hàm thống nhất: verifyShortcut (GĐ6)
     */
    fun verifyShortcut(context: Context, userId: String): Boolean {
        return ShortcutHelper.shortcutExists(context, userId)
    }

    /**
     * ✅ Sync tất cả shortcuts với active notifications
     */
    suspend fun syncShortcutsWithNotifications(
        context: Context,
        activeUsers: List<Triple<String, String, String>> // userId, userName, avatarUrl
    ) {
        ShortcutHelper.createShortcutsBatch(context, activeUsers)
    }


    // ========================================
    // AVATAR LOADING (OLD/DEPRECATED - GIỮ LẠI CHO CÁC PHƯƠNG THỨC KHÔNG DÙNG AVATARLOADER)
    // ========================================

    /**
     * ✅ Giữ lại hàm này cho mục đích tương thích ngược/delegation nhưng không dùng trong luồng chính GĐ6
     */
    private suspend fun loadAvatarIcon(
        context: Context,
        avatarUrl: String,
        userName: String
    ): Icon = withContext(Dispatchers.IO) {
        try {
            return@withContext AvatarLoader.loadAvatarIconAsync(context, avatarUrl, userName)
        } catch (e: Exception) {
            Log.e(TAG, "❌ [DELEGATED] Avatar load failed: $e")
            return@withContext AvatarLoader.createDefaultAvatarIcon(context, userName)
        }
    }

    /**
     * ✅ Giữ lại hàm này cho mục đích tương thích ngược/delegation nhưng không dùng trong luồng chính GĐ6
     */
    private suspend fun loadAvatarIconCompat(
        context: Context,
        avatarUrl: String,
        userName: String
    ): IconCompat = withContext(Dispatchers.IO) {
        // Sử dụng logic GĐ3/2 vì logic này có trong phần 1
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
            Log.e(TAG, "❌ [OLD] Avatar load failed: $e")
            return@withContext IconCompat.createWithBitmap(
                createDefaultAvatar(context, userName)
            )
        }
    }

    /**
     * ✅ GIAI ĐOẠN 6: AVATAR CONVERSION (Giữ lại hàm này cho Notification.Builder.setLargeIcon)
     * Sử dụng phiên bản gọn hơn (từ phần 2/FIX)
     */
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

    /**
     * ✅ GIAI ĐOẠN 3: createDefaultAvatar (Giữ lại hàm này để dùng cho loadAvatarIconCompat cũ)
     */
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
    // LEGACY NOTIFICATION (Sử dụng logic từ phần 2/FIX để đảm bảo tính gọn)
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
    // ✅ GIAI ĐOẠN 6: AVATAR PRELOADING
    // ========================================

    /**
     * Preload avatar cho user để chuẩn bị trước khi show notification
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun preloadAvatar(
        context: Context,
        avatarUrl: String,
        userName: String
    ) {
        AvatarLoader.preloadAvatar(context, avatarUrl, userName)
        Log.d(TAG, "✅ Preloaded avatar: $userName")
    }

    /**
     * Preload avatars cho nhiều users (batch operation)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun preloadAvatarsBatch(
        context: Context,
        users: List<Triple<String, String, String>> // userId, userName, avatarUrl
    ) {
        val avatarList = users.map { (_, userName, avatarUrl) ->
            avatarUrl to userName
        }

        AvatarLoader.preloadAvatarsBatch(context, avatarList)
        Log.d(TAG, "✅ Batch preload complete: ${users.size} avatars")
    }

    /**
     * Clear avatar cache cho user cụ thể
     */
    fun clearAvatarCache(avatarUrl: String, userName: String) {
        AvatarLoader.clearCache(avatarUrl, userName)
        Log.d(TAG, "🗑️ Cleared avatar cache: $userName")
    }

    /**
     * Clear all avatar cache (Tên hàm đổi thành clearAllAvatarCache theo GĐ6)
     */
    fun clearAllAvatarCache() {
        AvatarLoader.clearAllCache()
        Log.d(TAG, "🗑️ Cleared all avatar cache")
    }

    /**
     * Get avatar cache stats
     */
    fun getAvatarCacheStats(): Map<String, Any> {
        return AvatarLoader.getCacheStats()
    }

    // ========================================
    // NOTIFICATION & SHORTCUT MANAGEMENT
    // ========================================

    fun removeShortcut(context: Context, userId: String) { // ✅ CHANGE 3: Replace removeShortcut() method (delegated)
        try {
            // ✅ Use ShortcutHelper
            ShortcutHelper.removeShortcut(context, userId)

            Log.d(TAG, "✅ Shortcut removed via ShortcutHelper")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove shortcut: $e")
        }
    }

    fun cancelNotification(context: Context, userId: String) { // ✅ CHANGE 6: Update cancelNotification() method
        try {
            val notificationId = getNotificationId(userId)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancel(notificationId)

            // ✅ GIAI ĐOẠN 3 & 6: ADD THIS LINE - Also remove shortcut
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

            // ✅ GIAI ĐOẠN 3 & 6: ADD THIS LINE - Also remove all shortcuts
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

    /**
     * ✅ GIAI ĐOẠN 3: clearCache (Đã bị thay thế bởi clearAllAvatarCache trong GĐ6, nhưng giữ lại)
     */
    fun clearCache() {
        clearAllAvatarCache() // Delegate to GĐ6's functionality
        Log.d(TAG, "✅ Avatar cache cleared (via clearAllAvatarCache)")
    }

    fun cleanup() { // ✅ CHANGE 8: Update cleanup() method (GĐ6 logic)
        scope.cancel()
        clearAllAvatarCache() // ✅ GĐ6's clear all cache
        ShortcutHelper.cleanup() // ✅ ADD THIS LINE - Also cleanup shortcuts
        Log.d(TAG, "✅ NotificationHelper cleanup complete")
    }
}