// android/app/src/main/kotlin/hust/appchat/notifications/BubbleNotificationService.kt
package hust.appchat.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import hust.appchat.bubble.BubbleManager
import hust.appchat.shortcuts.ShortcutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ✅ GIAI ĐOẠN 3: Service với Shortcut Integration
 *
 * Integration points:
 * 1. showBubbleNotification → Create shortcut + notification
 * 2. updateBubbleNotification → Ensure shortcut exists
 * 3. dismissBubble → Remove notification + shortcut
 * 4. onAppResumed → Sync shortcuts with active bubbles
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

            // ✅ GIAI ĐOẠN 3: Check shortcut support
            if (ShortcutHelper.isShortcutsSupported()) {
                Log.d(TAG, "✅ Shortcuts supported")
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
    // BUBBLE NOTIFICATION WITH SHORTCUT
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
                    // ✅ GIAI ĐOẠN 3: Create shortcut FIRST
                    Log.d(TAG, "🔗 Creating shortcut for: $userName")
                    ShortcutHelper.createShortcut(
                        context = context,
                        userId = userId,
                        userName = userName,
                        avatarUrl = avatarUrl
                    )

                    // ✅ Then create notification
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

                    // ✅ GIAI ĐOẠN 3: Ensure shortcut exists
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
    // DISMISSAL WITH SHORTCUT CLEANUP
    // ========================================

    fun dismissBubble(context: Context, userId: String) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // ✅ GIAI ĐOẠN 3: Remove shortcut
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
                    // ✅ GIAI ĐOẠN 3: Remove all shortcuts
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
    // ✅ GIAI ĐOẠN 3: SHORTCUT UTILITIES
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
    // UTILITIES
    // ========================================

    fun shouldUseBubbleApi(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun getImplementationType(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "Bubble API + Shortcuts"
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