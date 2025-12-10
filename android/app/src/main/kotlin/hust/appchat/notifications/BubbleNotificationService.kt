// android/app/src/main/kotlin/hust/appchat/notifications/BubbleNotificationService.kt
package hust.appchat.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import hust.appchat.bubble.BubbleManager
import hust.appchat.shortcuts.AvatarLoader
import hust.appchat.shortcuts.ShortcutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ✅ GIAI ĐOẠN 6: Bubble Notification Service
 *
 * Tính năng chính:
 * - Hỗ trợ cả Bubble API (Android 11+) và WindowManager Fallback (< 11).
 * - Tích hợp quản lý Shortcut (tạo/xóa/sync).
 * - Tích hợp Avatar Preloading Strategy (sử dụng AvatarLoader).
 */
object BubbleNotificationService {
    private const val TAG = "BubbleNotifService"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
            NotificationHelper.createNotificationChannel(context)

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
    // ✅ GIAI ĐOẠN 6: AVATAR PRELOADING STRATEGY
    // ========================================

    /**
     * Preload avatars for recent/active conversations
     * Call this on app start to prepare cache
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun preloadRecentAvatars(context: Context) {
        scope.launch {
            try {
                Log.d(TAG, "🔄 Preloading recent avatars...")

                // Get active bubbles from BubbleManager
                val activeBubbles = BubbleManager.getActiveBubbles()

                if (activeBubbles.isNotEmpty()) {
                    val userList = activeBubbles.map { (_, bubble) ->
                        bubble.avatarUrl to bubble.userName
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
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
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
    // BUBBLE NOTIFICATION WITH PRELOADING (Sử dụng GIAI ĐOẠN 6)
    // ========================================

    fun showBubbleNotification(
        context: Context,
        userId: String,
        userName: String,
        message: String,
        avatarUrl: String
    ) {
        if (!isInitialized) {
            Log.w(TAG, "⚠️ Service not initialized, initializing now...")
            init(context)
        }

        scope.launch {
            try {
                Log.d(TAG, "🎈 Creating bubble notification: $userName")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // ✅ STEP 1: Preload avatar first (if not cached)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        preloadAvatarForNotification(context, avatarUrl, userName)
                    }

                    // ✅ STEP 2: Create shortcut
                    Log.d(TAG, "🔗 Creating shortcut for: $userName")
                    ShortcutHelper.createShortcut(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl
                    )

                    // ✅ STEP 3: Create notification (avatar already cached)
                    NotificationHelper.showBubbleNotification(
                        context = context,
                        userId = userId,
                        userName = userName,
                        message = message,
                        avatarUrl = avatarUrl
                    )

                    activeBubbleNotifications.add(userId)
                    Log.d(TAG, "✅ Bubble notification created (Bubble API + Shortcut)")

                } else {
                    // Fallback for Android < 11
                    Log.d(TAG, "⚠️ Android < 11, using WindowManager fallback")
                    BubbleManager.showBubble(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl,
                        message = message
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create bubble notification: $e")

                // Fallback to WindowManager
                try {
                    BubbleManager.showBubble(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl,
                        message = message
                    )
                    Log.d(TAG, "✅ Fallback to WindowManager successful")
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "❌ Fallback also failed: $fallbackError")
                }
            }
        }
    }

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

                    NotificationHelper.showBubbleNotification(
                        context = context,
                        userId = userId,
                        userName = userName,
                        message = message,
                        avatarUrl = avatarUrl
                    )

                    Log.d(TAG, "✅ Bubble notification updated: $userName")
                } else {
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
    // DISMISSAL WITH CLEANUP (Sử dụng GIAI ĐOẠN 6)
    // ========================================

    fun dismissBubble(context: Context, userId: String) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // ✅ GIAI ĐOẠN 6: Remove shortcut
                    Log.d(TAG, "🗑️ Removing shortcut for: $userId")
                    ShortcutHelper.removeShortcut(context, userId)

                    NotificationHelper.cancelNotification(context, userId)

                    activeBubbleNotifications.remove(userId)
                    Log.d(TAG, "✅ Bubble dismissed (notification + shortcut)")
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
                    // ✅ GIAI ĐOẠN 6: Remove all shortcuts
                    Log.d(TAG, "🗑️ Removing all shortcuts")
                    ShortcutHelper.removeAllShortcuts(context)

                    NotificationHelper.cancelAllNotifications(context)

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
    // STATE QUERIES (Giữ nguyên)
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
    // ✅ GIAI ĐOẠN 6: AVATAR CACHE UTILITIES (Lấy từ GIAI ĐOẠN 6)
    // ========================================

    /**
     * Get avatar cache stats
     */
    fun getAvatarCacheStats(): Map<String, Any> {
        // Lưu ý: Giả định NotificationHelper.getAvatarCacheStats() gọi đến AvatarLoader.getCacheStats()
        // Do không có code NotificationHelper, giữ nguyên theo GIAI ĐOẠN 6
        return NotificationHelper.getAvatarCacheStats()
    }

    /**
     * Clear avatar cache
     */
    fun clearAvatarCache() {
        // Lưu ý: Giả định NotificationHelper.clearAllAvatarCache() gọi đến AvatarLoader.clearAllCache()
        NotificationHelper.clearAllAvatarCache()
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
                // Clear cache
                // Lưu ý: Giả định NotificationHelper.clearAvatarCache() gọi đến AvatarLoader.clearCache()
                NotificationHelper.clearAvatarCache(avatarUrl, userName)

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
    // ✅ GIAI ĐOẠN 6: SHORTCUT UTILITIES (Lấy từ GIAI ĐOẠN 6, vì trùng với GIAI ĐOẠN 3)
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
     * ✅ Sync shortcuts với active bubbles (Giữ nguyên)
     */
    fun syncShortcuts(context: Context) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.d(TAG, "🔄 Syncing shortcuts with active bubbles")

                    val activeBubbles = BubbleManager.getActiveBubbles()

                    activeBubbles.forEach { (userId, bubble) ->
                        ShortcutHelper.ensureShortcutForNotification(
                            context = context,
                            userId = userId,
                            userName = bubble.userName,
                            avatarUrl = bubble.avatarUrl
                        )
                    }

                    Log.d(TAG, "✅ Shortcuts synced: ${activeBubbles.size} shortcuts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync shortcuts failed: $e")
            }
        }
    }

    // ========================================
    // UTILITIES (Giữ nguyên)
    // ========================================

    fun shouldUseBubbleApi(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun getImplementationType(): String {
        // ✅ GIAI ĐOẠN 6: Cập nhật mô tả
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "Bubble API + Shortcuts + Avatar Cache"
        } else {
            "WindowManager"
        }
    }

    // ========================================
    // LIFECYCLE (Sử dụng GIAI ĐOẠN 6)
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
    // CLEANUP (Giữ nguyên)
    // ========================================

    fun cleanup(context: Context) {
        try {
            dismissAllBubbles(context)
            NotificationHelper.cleanup()
            ShortcutHelper.cleanup()

            activeBubbleNotifications.clear()
            isInitialized = false

            Log.d(TAG, "✅ Cleanup complete")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup failed: $e")
        }
    }
}