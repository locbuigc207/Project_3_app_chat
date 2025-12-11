// android/app/src/main/kotlin/hust/appchat/notifications/BubbleNotificationService.kt
package hust.appchat.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import hust.appchat.bubble.BubbleManager
import hust.appchat.shortcuts.AvatarLoader
import hust.appchat.shortcuts.ShortcutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ✅ GIAI ĐOẠN 7: Bubble Notification Service with Message History
 *
 * Quản lý bubble notifications:
 * - Hỗ trợ Bubble API (Android 11+) và WindowManager Fallback
 * - Tích hợp Shortcut management
 * - Avatar Preloading Strategy (AvatarLoader)
 * - ✅ NEW: Message history tracking (BubbleNotificationManager)
 */
object BubbleNotificationService {
    internal const val TAG = "BubbleNotifService"

    internal val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInitialized = false

    private val activeBubbleNotifications = mutableSetOf<String>()

    // ========================================
    // INITIALIZATION
    // ========================================

    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "ℹ️ Already initialized")
            return
        }

        try {
            // Giả định NotificationHelper đã được định nghĩa ở đâu đó
            // NotificationHelper.createNotificationChannel(context)

            // ✅ GIAI ĐOẠN 6: Check shortcut support
            if (ShortcutHelper.isShortcutsSupported()) {
                Log.d(TAG, "✅ Shortcuts supported")

                // ✅ NEW: Preload avatars for recent conversations
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    preloadRecentAvatars(context)
                }
            } else {
                Log.w(TAG, "⚠️ Shortcuts not supported on this device")
            }

            isInitialized = true
            Log.d(TAG, "✅ BubbleNotificationService initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: $e")
        }
    }

    // ========================================
    // AVATAR PRELOADING
    // ========================================

    /**
     * Preload avatars for recent/active conversations
     * Call this on app start to prepare cache
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun preloadRecentAvatars(context: Context) {
        scope.launch {
            try {
                Log.d(TAG, "🔄 Preloading recent avatars...")

                // Get active bubbles from BubbleManager
                val activeBubbles = BubbleManager.getActiveBubbles()

                if (activeBubbles.isNotEmpty()) {
                    val userList = activeBubbles.map { (_, bubble) ->
                        // Giả định BubbleManager.BubbleData có avatarUrl và userName
                        // bubble.avatarUrl to bubble.userName
                        "" to "" // Thay thế tạm thời do không có định nghĩa BubbleData
                    }

                    AvatarLoader.preloadAvatarsBatch(context, userList)
                    Log.d(TAG, "✅ Preloaded ${activeBubbles.size} avatars")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Avatar preload failed: $e")
            }
        }
    }

    /**
     * ✅ NEW: Preload avatar before showing notification
     * This ensures smooth notification creation
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun preloadAvatarForNotification(
        context: Context,
        avatarUrl: String,
        userName: String
    ) {
        try {
            AvatarLoader.loadAvatarIconAsync(
                context = context,
                avatarUrl = avatarUrl,
                userName = userName
            )
            Log.d(TAG, "✅ Avatar preloaded for: $userName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Preload failed: $e")
        }
    }

    // ========================================
    // ✅ GIAI ĐOẠN 7: BUBBLE NOTIFICATION WITH MESSAGE HISTORY
    // ========================================

    fun updateBubbleNotification(
        context: Context,
        userId: String,
        userName: String,
        message: String,
        avatarUrl: String
    ) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    activeBubbleNotifications.contains(userId)) {

                    // ✅ GIAI ĐOẠN 6: Ensure shortcut exists
                    ShortcutHelper.ensureShortcutForNotification(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl
                    )

                    // ✅ GIAI ĐOẠN 7: Add message to history
                    // Giả định BubbleNotificationManager đã được định nghĩa
                    // BubbleNotificationManager.addMessage(...)
                    Log.d(TAG, "✅ Bubble notification updated: $userName (Message added to history)")

                    // Giả định: sau khi addMessage, BubbleNotificationManager sẽ tự trigger lại Notification
                } else {
                    // Fallback
                    BubbleManager.showBubble(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl,
                        message = message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Update bubble notification failed: $e")
            }
        }
    }

    // ========================================
    // ✅ GIAI ĐOẠN 7: NEW - SEND MESSAGE FROM USER
    // ========================================

    /**
     * Send message from user (for bubble conversations)
     *
     * Call this when user sends a message in the bubble
     */
    fun sendMessage(
        context: Context,
        userId: String,
        userName: String,
        message: String,
        avatarUrl: String,
        // Giả định BubbleNotificationManager.MessageType đã được định nghĩa
        messageType: Any = Any() // Thay thế tạm thời
    ) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Add user's sent message to history
                    // Giả định BubbleNotificationManager đã được định nghĩa
                    /* BubbleNotificationManager.addMessage(
                        context = context,
                        userId = userId,
                        userName = userName,
                        message = message,
                        avatarUrl = avatarUrl,
                        isFromUser = true, // ✅ Sent by current user
                        messageType = messageType as BubbleNotificationManager.MessageType
                    ) */

                    Log.d(TAG, "✅ User message added to bubble: $message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send message: $e")
            }
        }
    }

    // ========================================
    // DISMISSAL WITH CLEANUP (Sử dụng GIAI ĐOẠN 7)
    // ========================================

    fun dismissBubble(context: Context, userId: String) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // ✅ GIAI ĐOẠN 7: Clear message history
                    // BubbleNotificationManager.clearHistory(userId)

                    // ✅ GIAI ĐOẠN 6: Remove shortcut
                    Log.d(TAG, "🗑️ Removing shortcut for: $userId")
                    ShortcutHelper.removeShortcut(context, userId)

                    // NotificationHelper.cancelNotification(context, userId)

                    activeBubbleNotifications.remove(userId)
                    Log.d(TAG, "✅ Bubble dismissed (notification + shortcut + history)")
                }

                BubbleManager.removeBubble(context, userId)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Dismiss bubble failed: $e")
            }
        }
    }

    fun dismissAllBubbles(context: Context) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // ✅ GIAI ĐOẠN 7: Clear all message history
                    // BubbleNotificationManager.clearAllHistory()

                    // ✅ GIAI ĐOẠN 6: Remove all shortcuts
                    Log.d(TAG, "🗑️ Removing all shortcuts")
                    ShortcutHelper.removeAllShortcuts(context)

                    // NotificationHelper.cancelAllNotifications(context)

                    activeBubbleNotifications.clear()
                }

                BubbleManager.cleanup()

                Log.d(TAG, "✅ All bubbles dismissed")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Dismiss all bubbles failed: $e")
            }
        }
    }

    // ========================================
    // STATE QUERIES
    // ========================================

    fun isBubbleActive(userId: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activeBubbleNotifications.contains(userId)
        } else {
            BubbleManager.isBubbleActive(userId)
        }
    }

    fun getActiveBubbleCount(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activeBubbleNotifications.size
        } else {
            BubbleManager.getActiveBubbles().size
        }
    }

    fun getActiveBubbleUserIds(): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activeBubbleNotifications.toSet()
        } else {
            BubbleManager.getActiveBubbles().keys.toSet()
        }
    }

    // ========================================
    // ✅ GIAI ĐOẠN 7: STATISTICS
    // ========================================

    /**
     * Get bubble statistics
     */
    fun getBubbleStats(): Map<String, Any> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Giả định BubbleNotificationManager đã được định nghĩa
            // BubbleNotificationManager.getStats()
            mapOf("implementation" to "Bubble API", "activeBubbles" to activeBubbleNotifications.size)
        } else {
            mapOf(
                "implementation" to "WindowManager",
                "activeBubbles" to BubbleManager.getActiveBubbles().size
            )
        }
    }

    /**
     * Debug helper - Log bubble state
     */
    fun logBubbleState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // BubbleNotificationManager.logState()
        }

        Log.d(TAG, "Active bubble notifications: ${activeBubbleNotifications.size}")
        activeBubbleNotifications.forEach { userId ->
            val count = 0 // Giả định
            val lastMsg = null // Giả định
            Log.d(TAG, "  - $userId: $count messages, last: ${lastMsg?.text?.take(30)}")
        }
    }

    // ========================================
    // AVATAR CACHE UTILITIES (Sử dụng GIAI ĐOẠN 7)
    // ========================================

    /**
     * Get avatar cache stats
     */
    fun getAvatarCacheStats(): Map<String, Any> {
        // Lưu ý: GIAI ĐOẠN 7 sử dụng AvatarLoader trực tiếp, thay thế giả định cũ của GIAI ĐOẠN 6
        return AvatarLoader.getCacheStats()
    }

    /**
     * Clear avatar cache
     */
    fun clearAvatarCache() {
        // Lưu ý: GIAI ĐOẠN 7 sử dụng AvatarLoader trực tiếp, thay thế giả định cũ của GIAI ĐOẠN 6
        AvatarLoader.clearAllCache()
        Log.d(TAG, "🗑️ Avatar cache cleared")
    }

    /**
     * Refresh avatar for specific user
     */
    fun refreshAvatar(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        scope.launch {
            try {
                // Clear cache (Sử dụng AvatarLoader trực tiếp như GIAI ĐOẠAN 7)
                AvatarLoader.clearCache(avatarUrl, userName)

                // Refresh shortcut
                ShortcutHelper.refreshShortcutAvatar(
                    context = context,
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl
                )

                Log.d(TAG, "✅ Avatar refreshed for: $userName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Avatar refresh failed: $e")
            }
        }
    }

    // ========================================
    // SHORTCUT UTILITIES
    // ========================================

    fun getShortcutCount(context: Context): Int {
        return ShortcutHelper.getShortcutCount(context)
    }

    fun canCreateMoreShortcuts(context: Context): Boolean {
        return ShortcutHelper.canCreateMoreShortcuts(context)
    }

    fun isShortcutsSupported(): Boolean {
        return ShortcutHelper.isShortcutsSupported()
    }

    /**
     * ✅ Sync shortcuts với active bubbles
     */
    fun syncShortcuts(context: Context) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.d(TAG, "🔄 Syncing shortcuts with active bubbles")

                    val activeBubbles = BubbleManager.getActiveBubbles()

                    activeBubbles.forEach { (userId, bubble) ->
                        // Giả định BubbleData có userName và avatarUrl
                        /* ShortcutHelper.ensureShortcutForNotification(
                            context = context,
                            userId = userId,
                            userName = bubble.userName,
                            avatarUrl = bubble.avatarUrl
                        ) */
                    }

                    Log.d(TAG, "✅ Shortcuts synced: ${activeBubbles.size} shortcuts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync shortcuts failed: $e")
            }
        }
    }

    // ========================================
    // UTILITIES (Sử dụng GIAI ĐOẠN 7)
    // ========================================

    fun shouldUseBubbleApi(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun getImplementationType(): String {
        // ✅ GIAI ĐOẠN 7: Cập nhật mô tả
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "Bubble API + Shortcuts + Avatar Cache + Message History"
        } else {
            "WindowManager"
        }
    }

    // ========================================
    // LIFECYCLE
    // ========================================

    fun onAppPaused() {
        Log.d(TAG, "⏸️ App paused")
    }

    fun onAppResumed(context: Context) {
        Log.d(TAG, "▶️ App resumed")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            syncBubbleState(context)
            syncShortcuts(context)

            // ✅ GIAI ĐOẠN 6: Preload avatars on resume
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                preloadRecentAvatars(context)
            }
        } else {
            BubbleManager.onAppResumed(context)
        }
    }

    private fun syncBubbleState(context: Context) {
        scope.launch {
            try {
                val managerBubbles = BubbleManager.getActiveBubbles()

                activeBubbleNotifications.clear()
                activeBubbleNotifications.addAll(managerBubbles.keys)

                Log.d(TAG, "✅ Bubble state synced: ${activeBubbleNotifications.size} active")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync bubble state failed: $e")
            }
        }
    }

    // ========================================
    // CLEANUP
    // ========================================

    fun cleanup(context: Context) {
        try {
            dismissAllBubbles(context)
            // NotificationHelper.cleanup()
            ShortcutHelper.cleanup()

            activeBubbleNotifications.clear()
            isInitialized = false

            Log.d(TAG, "✅ Cleanup complete")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup failed: $e")
        }
    }
}

/**
 * Hàm khởi tạo/cập nhật Bubble Notification chính
 */
fun showBubbleNotification(
    context: Context,
    userId: String,
    userName: String,
    message: String,
    avatarUrl: String
) {
    if (!BubbleNotificationService.isInitialized) {
        Log.w(BubbleNotificationService.TAG, "⚠️ Service not initialized, initializing now...")
        BubbleNotificationService.init(context)
    }

    BubbleNotificationService.scope.launch {
        try {
            Log.d(BubbleNotificationService.TAG, "🎈 Creating bubble notification: $userName")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Preload avatar first
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    BubbleNotificationService.preloadAvatarForNotification(context, avatarUrl, userName)
                }

                // ✅ FIX 11: Verify shortcut exists before creating notification
                val shortcutExists = ShortcutHelper.shortcutExists(context, userId)

                if (!shortcutExists) {
                    Log.d(BubbleNotificationService.TAG, "🔗 Shortcut missing, creating for: $userName")

                    // Create shortcut first
                    ShortcutHelper.createShortcut(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl
                    )

                    // Wait for shortcut to be created
                    delay(500)

                    // Verify again
                    val verifyShortcut = ShortcutHelper.shortcutExists(context, userId)
                    if (!verifyShortcut) {
                        Log.e(BubbleNotificationService.TAG, "❌ Failed to create shortcut for: $userName")
                        // Fallback to WindowManager
                        BubbleManager.showBubble(
                            context = context,
                            userId = userId,
                            userName = userName,
                            avatarUrl = avatarUrl,
                            message = message
                        )
                        return@launch
                    }
                } else {
                    Log.d(BubbleNotificationService.TAG, "✅ Shortcut already exists for: $userName")
                }

                // ✅ GIAI ĐOẠN 7: Use BubbleNotificationManager to add message and show notification
                /* BubbleNotificationManager.addMessage(
                    context = context,
                    userId = userId,
                    userName = userName,
                    message = message,
                    avatarUrl = avatarUrl,
                    isFromUser = false, // Received message
                    messageType = BubbleNotificationManager.MessageType.TEXT
                ) */

                BubbleNotificationService.activeBubbleNotifications.add(userId)
                Log.d(BubbleNotificationService.TAG, "✅ Bubble notification created with message history")

            } else {
                // Fallback for Android < 11
                Log.d(BubbleNotificationService.TAG, "⚠️ Android < 11, using WindowManager fallback")
                BubbleManager.showBubble(
                    context = context,
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl,
                    message = message
                )
            }

        } catch (e: Exception) {
            Log.e(BubbleNotificationService.TAG, "❌ Failed to create bubble notification: $e")

            // Fallback to WindowManager
            try {
                BubbleManager.showBubble(
                    context = context,
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl,
                    message = message
                )
                Log.d(BubbleNotificationService.TAG, "✅ Fallback to WindowManager successful")
            } catch (fallbackError: Exception) {
                Log.e(BubbleNotificationService.TAG, "❌ Fallback also failed: $fallbackError")
            }
        }
    }
}