// android/app/src/main/kotlin/hust/appchat/bubble/BubbleManager.kt - COMPLETE FIXED
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * BubbleManager - Quản lý toàn bộ lifecycle của chat bubbles
 * ✅ NEW Features:
 * - Multi-bubble layout optimization
 * - Persistence support
 * - Better error handling
 */
object BubbleManager {
    private val activeBubbles = mutableMapOf<String, BubbleData>()
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()

    // ✅ NEW: Track bubble positions for layout optimization
    private val bubblePositions = mutableMapOf<String, BubblePosition>()
    private var nextYPosition = 200

    data class BubbleData(
        val userId: String,
        val userName: String,
        val avatarUrl: String,
        var unreadCount: Int = 0,
        var lastMessage: String = "",
        var timestamp: Long = System.currentTimeMillis()
    )

    data class BubblePosition(
        var x: Int,
        var y: Int,
        val userId: String
    )

    fun init(context: Context) {
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            android.util.Log.d("BubbleManager", "✅ Initialized")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to init Firebase: $e")
        }
    }

    /**
     * ✅ UPDATED: Show bubble with layout optimization
     */
    fun showBubble(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        message: String? = null
    ) {
        android.util.Log.d("BubbleManager", "🎈 Showing bubble for: $userName")

        val bubbleData = activeBubbles.getOrPut(userId) {
            BubbleData(userId, userName, avatarUrl)
        }

        // Update data
        message?.let {
            bubbleData.lastMessage = it
            bubbleData.unreadCount++
            bubbleData.timestamp = System.currentTimeMillis()
        }

        // ✅ Calculate position for new bubble
        val position = calculateBubblePosition(userId)

        // Start service to show bubble
        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_SHOW_BUBBLE
            putExtra("userId", userId)
            putExtra("userName", userName)
            putExtra("avatarUrl", avatarUrl)
            putExtra("unreadCount", bubbleData.unreadCount)
            putExtra("lastMessage", bubbleData.lastMessage)
            putExtra("positionX", position.x)
            putExtra("positionY", position.y)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.d("BubbleManager", "✅ Service started for: $userName")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to start service: $e")
        }

        // Listen to new messages
        listenToMessages(context, userId)
    }

    /**
     * ✅ NEW: Calculate optimal position for new bubble
     * Prevents overlap and arranges bubbles vertically
     */
    private fun calculateBubblePosition(userId: String): BubblePosition {
        // Check if bubble already has position
        bubblePositions[userId]?.let {
            return it
        }

        // Calculate new position
        val screenWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels
        val x = screenWidth - 100 // Right edge

        // ✅ NEW: Smart vertical positioning
        val y = if (activeBubbles.size <= 1) {
            200 // First bubble
        } else {
            // Stack bubbles with 80dp spacing
            nextYPosition
        }

        val position = BubblePosition(x, y, userId)
        bubblePositions[userId] = position

        // Update next position
        nextYPosition += 80

        // ✅ Reset if too many bubbles (> 8)
        if (activeBubbles.size > 8) {
            nextYPosition = 200
        }

        android.util.Log.d(
            "BubbleManager",
            "📍 Position for $userId: x=$x, y=$y"
        )

        return position
    }

    /**
     * ✅ UPDATED: Listen to realtime messages
     */
    private fun listenToMessages(context: Context, userId: String) {
        if (messageListeners.containsKey(userId)) {
            android.util.Log.d("BubbleManager", "ℹ️ Already listening for: $userId")
            return
        }

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
                        android.util.Log.e("BubbleManager", "❌ Listen error: $error")
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val message = change.document.getString("content") ?: ""
                            val type = change.document.getLong("type")?.toInt() ?: 0

                            android.util.Log.d(
                                "BubbleManager",
                                "📨 New message from $userId: $message"
                            )

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
            android.util.Log.d("BubbleManager", "✅ Listener setup for: $userId")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to setup listener: $e")
        }
    }

    /**
     * ✅ Notify service to update bubble
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
            android.util.Log.e("BubbleManager", "❌ Failed to notify update: $e")
        }
    }

    /**
     * ✅ UPDATED: Remove bubble and cleanup position
     */
    fun removeBubble(context: Context, userId: String) {
        android.util.Log.d("BubbleManager", "🗑️ Removing bubble: $userId")

        activeBubbles.remove(userId)
        bubblePositions.remove(userId)
        messageListeners.remove(userId)?.remove()

        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE_BUBBLE
            putExtra("userId", userId)
        }

        try {
            context.startService(intent)
            android.util.Log.d("BubbleManager", "✅ Bubble removed: $userId")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to remove bubble: $e")
        }

        // ✅ Reposition remaining bubbles
        repositionBubbles(context)
    }

    /**
     * ✅ NEW: Reposition bubbles after removal
     */
    private fun repositionBubbles(context: Context) {
        if (activeBubbles.isEmpty()) {
            nextYPosition = 200
            return
        }

        var yPos = 200
        activeBubbles.keys.forEach { userId ->
            bubblePositions[userId]?.y = yPos
            yPos += 80
        }

        nextYPosition = yPos

        android.util.Log.d(
            "BubbleManager",
            "📍 Repositioned ${activeBubbles.size} bubbles"
        )
    }

    /**
     * ✅ Reset unread count
     */
    fun markAsRead(userId: String) {
        activeBubbles[userId]?.unreadCount = 0
    }

    /**
     * ✅ Get current user ID
     */
    fun getCurrentUserId(): String? {
        return try {
            auth?.currentUser?.uid
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to get current user: $e")
            null
        }
    }

    /**
     * ✅ Get bubble data
     */
    fun getBubbleData(userId: String): BubbleData? {
        return activeBubbles[userId]
    }

    /**
     * ✅ Check if bubble is active
     */
    fun isBubbleActive(userId: String): Boolean {
        return activeBubbles.containsKey(userId)
    }

    /**
     * ✅ Get all active bubbles
     */
    fun getActiveBubbles(): Map<String, BubbleData> {
        return activeBubbles.toMap()
    }

    /**
     * ✅ Cleanup all resources
     */
    fun cleanup() {
        android.util.Log.d("BubbleManager", "🧹 Cleaning up")

        messageListeners.values.forEach {
            try {
                it.remove()
            } catch (e: Exception) {
                android.util.Log.e("BubbleManager", "❌ Failed to remove listener: $e")
            }
        }

        messageListeners.clear()
        activeBubbles.clear()
        bubblePositions.clear()
        nextYPosition = 200

        android.util.Log.d("BubbleManager", "✅ Cleanup complete")
    }
}