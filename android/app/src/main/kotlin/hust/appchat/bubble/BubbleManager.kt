// android/app/src/main/kotlin/hust/appchat/bubble/BubbleManager.kt - COMPLETE
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * BubbleManager - Quản lý toàn bộ lifecycle của chat bubbles
 *
 * Features:
 * - Multiple bubbles support
 * - Realtime message listener
 * - Unread count tracking
 * - Auto cleanup
 */
object BubbleManager {
    // Active bubbles map
    val activeBubbles = mutableMapOf<String, BubbleData>()

    private var firestore: FirebaseFirestore? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()

    data class BubbleData(
        val userId: String,
        val userName: String,
        val avatarUrl: String,
        var unreadCount: Int = 0,
        var lastMessage: String = "",
        var timestamp: Long = System.currentTimeMillis()
    )

    fun init(context: Context) {
        firestore = FirebaseFirestore.getInstance()
        android.util.Log.d("BubbleManager", "✅ Initialized")
    }

    /**
     * Show bubble cho user
     */
    fun showBubble(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        message: String? = null
    ) {
        try {
            // Get or create bubble data
            val bubbleData = activeBubbles.getOrPut(userId) {
                BubbleData(userId, userName, avatarUrl)
            }

            // Update data if message provided
            message?.let {
                bubbleData.lastMessage = it
                bubbleData.unreadCount++
                bubbleData.timestamp = System.currentTimeMillis()
            }

            // Start overlay service
            val intent = Intent(context, BubbleOverlayService::class.java).apply {
                action = BubbleOverlayService.ACTION_SHOW_BUBBLE
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)
                putExtra("unreadCount", bubbleData.unreadCount)
                putExtra("lastMessage", bubbleData.lastMessage)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            // Start listening to messages
            listenToMessages(context, userId)

            android.util.Log.d("BubbleManager", "✅ Bubble shown for: $userName")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Error showing bubble: $e")
        }
    }

    /**
     * Listen to realtime messages
     */
    private fun listenToMessages(context: Context, userId: String) {
        // Don't create duplicate listeners
        if (messageListeners.containsKey(userId)) {
            android.util.Log.d("BubbleManager", "⚠️ Already listening to: $userId")
            return
        }

        val currentUserId = getCurrentUserId() ?: run {
            android.util.Log.e("BubbleManager", "❌ No current user ID")
            return
        }

        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        android.util.Log.d("BubbleManager", "🔊 Starting listener for conversation: $conversationId")

        val listener = firestore
            ?.collection("messages")
            ?.document(conversationId)
            ?.collection(conversationId)
            ?.whereEqualTo("idFrom", userId)
            ?.whereEqualTo("isRead", false)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("BubbleManager", "❌ Listen error: $error")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val message = change.document.getString("content") ?: ""
                        val type = change.document.getLong("type")?.toInt() ?: 0

                        android.util.Log.d("BubbleManager", "📩 New message from $userId: $message")

                        // Update bubble data
                        activeBubbles[userId]?.let { bubble ->
                            bubble.lastMessage = if (type == 0) message else "📷 Image"
                            bubble.unreadCount++
                            bubble.timestamp = System.currentTimeMillis()

                            // Notify overlay service to update
                            notifyBubbleUpdate(context, userId, bubble)
                        }
                    }
                }
            }

        listener?.let {
            messageListeners[userId] = it
            android.util.Log.d("BubbleManager", "✅ Listener registered for: $userId")
        }
    }

    /**
     * Notify overlay service to update bubble
     */
    private fun notifyBubbleUpdate(context: Context, userId: String, bubble: BubbleData) {
        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_UPDATE_BUBBLE
            putExtra("userId", userId)
            putExtra("unreadCount", bubble.unreadCount)
            putExtra("lastMessage", bubble.lastMessage)
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Error updating bubble: $e")
        }
    }

    /**
     * Remove bubble
     */
    fun removeBubble(context: Context, userId: String) {
        android.util.Log.d("BubbleManager", "🗑️ Removing bubble: $userId")

        activeBubbles.remove(userId)

        // Remove listener
        messageListeners.remove(userId)?.remove()

        // Hide bubble in overlay service
        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE_BUBBLE
            putExtra("userId", userId)
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Error removing bubble: $e")
        }
    }

    /**
     * Mark messages as read
     */
    fun markAsRead(userId: String) {
        activeBubbles[userId]?.unreadCount = 0
        android.util.Log.d("BubbleManager", "✅ Marked as read: $userId")
    }

    /**
     * Get current user ID from Firebase Auth
     */
    private fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    /**
     * Cleanup all bubbles and listeners
     */
    fun cleanup() {
        android.util.Log.d("BubbleManager", "🧹 Cleanup started")

        messageListeners.values.forEach { it.remove() }
        messageListeners.clear()
        activeBubbles.clear()

        android.util.Log.d("BubbleManager", "✅ Cleanup complete")
    }
}