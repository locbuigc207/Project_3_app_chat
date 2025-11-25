package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * BubbleManager - Quản lý toàn bộ lifecycle của chat bubbles
 */
object BubbleManager {
    private val activeBubbles = mutableMapOf<String, BubbleData>()
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
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
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "Failed to init Firebase: $e")
        }
    }

    /**
     * Hiển thị bubble khi có tin nhắn đến
     */
    fun showBubble(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        message: String? = null
    ) {
        val bubbleData = activeBubbles.getOrPut(userId) {
            BubbleData(userId, userName, avatarUrl)
        }

        // Update data
        message?.let {
            bubbleData.lastMessage = it
            bubbleData.unreadCount++
            bubbleData.timestamp = System.currentTimeMillis()
        }

        // Start service to show bubble
        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_SHOW_BUBBLE
            putExtra("userId", userId)
            putExtra("userName", userName)
            putExtra("avatarUrl", avatarUrl)
            putExtra("unreadCount", bubbleData.unreadCount)
            putExtra("lastMessage", bubbleData.lastMessage)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "Failed to start service: $e")
        }

        // Listen to new messages
        listenToMessages(context, userId)
    }

    /**
     * Lắng nghe tin nhắn realtime
     */
    private fun listenToMessages(context: Context, userId: String) {
        if (messageListeners.containsKey(userId)) return

        val currentUserId = getCurrentUserId() ?: return
        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        try {
            val listener = firestore
                ?.collection("messages")
                ?.document(conversationId)
                ?.collection(conversationId)
                ?.whereEqualTo("idFrom", userId)
                ?.whereEqualTo("isRead", false)
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("BubbleManager", "Listen error: $error")
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val message = change.document.getString("content") ?: ""
                            val type = change.document.getLong("type")?.toInt() ?: 0

                            // Update bubble
                            activeBubbles[userId]?.let { bubble ->
                                bubble.lastMessage = if (type == 0) message else "📷 Image"
                                bubble.unreadCount++
                                bubble.timestamp = System.currentTimeMillis()

                                // Notify service to update UI
                                notifyBubbleUpdate(context, userId, bubble)
                            }
                        }
                    }
                }

            listener?.let { messageListeners[userId] = it }
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "Failed to setup listener: $e")
        }
    }

    /**
     * Thông báo service cập nhật bubble
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
            android.util.Log.e("BubbleManager", "Failed to notify update: $e")
        }
    }

    /**
     * Xóa bubble
     */
    fun removeBubble(context: Context, userId: String) {
        activeBubbles.remove(userId)
        messageListeners.remove(userId)?.remove()

        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE_BUBBLE
            putExtra("userId", userId)
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "Failed to remove bubble: $e")
        }
    }

    /**
     * Reset unread count khi mở chat
     */
    fun markAsRead(userId: String) {
        activeBubbles[userId]?.unreadCount = 0
    }

    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? {
        return try {
            auth?.currentUser?.uid
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "Failed to get current user: $e")
            null
        }
    }

    /**
     * Get active bubble data
     */
    fun getBubbleData(userId: String): BubbleData? {
        return activeBubbles[userId]
    }

    /**
     * Check if bubble is active
     */
    fun isBubbleActive(userId: String): Boolean {
        return activeBubbles.containsKey(userId)
    }

    fun cleanup() {
        messageListeners.values.forEach {
            try {
                it.remove()
            } catch (e: Exception) {
                android.util.Log.e("BubbleManager", "Failed to remove listener: $e")
            }
        }
        messageListeners.clear()
        activeBubbles.clear()
    }
}