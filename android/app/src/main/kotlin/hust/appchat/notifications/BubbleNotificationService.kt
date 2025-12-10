// android/app/src/main/kotlin/hust/appchat/notifications/BubbleNotificationService.kt
package hust.appchat.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import hust.appchat.bubble.BubbleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ✅ GIAI ĐOẠN 2: Service kết nối BubbleManager với NotificationHelper
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

            isInitialized = true
            Log.d(TAG, "✅ BubbleNotificationService initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: $e")
        }
    }

    // ========================================
    // BUBBLE NOTIFICATION
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
                    NotificationHelper.showBubbleNotification(
                        context = context,
                        userId = userId,
                        userName = userName,
                        message = message,
                        avatarUrl = avatarUrl
                    )

                    activeBubbleNotifications.add(userId)
                    Log.d(TAG, "✅ Bubble notification created (Bubble API)")

                } else {
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
    // DISMISSAL
    // ========================================

    fun dismissBubble(context: Context, userId: String) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    NotificationHelper.cancelNotification(context, userId)
                    NotificationHelper.removeShortcut(context, userId)

                    activeBubbleNotifications.remove(userId)
                    Log.d(TAG, "✅ Bubble notification dismissed: $userId")
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
                    NotificationHelper.cancelAllNotifications(context)

                    activeBubbleNotifications.forEach { userId ->
                        NotificationHelper.removeShortcut(context, userId)
                    }

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
    // UTILITIES
    // ========================================

    fun shouldUseBubbleApi(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun getImplementationType(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "Bubble API"
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

            activeBubbleNotifications.clear()
            isInitialized = false

            Log.d(TAG, "✅ Cleanup complete")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup failed: $e")
        }
    }
}