// android/app/src/main/kotlin/hust/appchat/bubble/BubbleManager.kt
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * BubbleManager - Quản lý toàn bộ lifecycle của chat bubbles
 *
 * Luồng hoạt động:
 * 1. Tin nhắn đến → showBubble()
 * 2. User tap bubble → showMiniChat()
 * 3. User close chat → hideMiniChat() → bubble visible
 * 4. User drag to delete → removeBubble()
 */
object BubbleManager {
    private val activeBubbles = mutableMapOf<String, BubbleData>()
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
        context.startForegroundService(intent)

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
        context.startService(intent)
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
        context.startService(intent)
    }

    /**
     * Reset unread count khi mở chat
     */
    fun markAsRead(userId: String) {
        activeBubbles[userId]?.unreadCount = 0
    }

    /**
     * Get current user ID (cần implement)
     */
    private fun getCurrentUserId(): String? {
        // TODO: Get from SharedPreferences or Firebase Auth
        return null
    }

    fun cleanup() {
        messageListeners.values.forEach { it.remove() }
        messageListeners.clear()
        activeBubbles.clear()
    }
}