// android/app/src/main/kotlin/hust/appchat/bubble/BubbleManager.kt
// ✅ FIX: Bubble lifecycle để không bị destroy

package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

object BubbleManager {
    private val activeBubbles = mutableMapOf<String, BubbleData>()
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()

    // ✅ CRITICAL: Track if service is running
    private var isServiceRunning = false

    private val bubblePositions = mutableMapOf<String, BubblePosition>()
    private var nextYPosition = 200

    private var lastScreenWidth = 0
    private var lastScreenHeight = 0
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

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
        val userId: String,
        var isRelative: Boolean = false
    )

    fun init(context: Context) {
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()

            updateScreenDimensions(context)

            android.util.Log.d("BubbleManager", "✅ Initialized")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to init: $e")
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        return try {
            val date = Date(timestamp)
            val formatter = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
            formatter.format(date)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
        if (newConfig.orientation != lastOrientation) {
            android.util.Log.d("BubbleManager", "📱 Orientation changed")

            val oldWidth = lastScreenWidth
            val oldHeight = lastScreenHeight

            updateScreenDimensions(context)
            repositionBubblesForRotation(context, oldWidth, oldHeight)

            lastOrientation = newConfig.orientation
        }
    }

    private fun updateScreenDimensions(context: Context) {
        val displayMetrics = context.resources.displayMetrics
        lastScreenWidth = displayMetrics.widthPixels
        lastScreenHeight = displayMetrics.heightPixels

        android.util.Log.d("BubbleManager", "📱 Screen: ${lastScreenWidth}x${lastScreenHeight}")
    }

    private fun repositionBubblesForRotation(
        context: Context,
        oldWidth: Int,
        oldHeight: Int
    ) {
        if (activeBubbles.isEmpty()) return

        bubblePositions.forEach { (userId, position) ->
            val xPercent = position.x.toFloat() / oldWidth
            val yPercent = position.y.toFloat() / oldHeight

            position.x = (xPercent * lastScreenWidth).toInt()
            position.y = (yPercent * lastScreenHeight).toInt()

            position.x = position.x.coerceIn(0, lastScreenWidth - 100)
            position.y = position.y.coerceIn(0, lastScreenHeight - 100)

            val intent = Intent(context, BubbleOverlayService::class.java).apply {
                action = "UPDATE_BUBBLE_POSITION"
                putExtra("userId", userId)
                putExtra("positionX", position.x)
                putExtra("positionY", position.y)
            }

            try {
                context.startService(intent)
            } catch (e: Exception) {}
        }
    }

    fun showBubble(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        message: String? = null
    ) {
        android.util.Log.d("BubbleManager", "🎈 showBubble: $userName")

        val bubbleData = activeBubbles.getOrPut(userId) {
            BubbleData(userId, userName, avatarUrl)
        }

        message?.let {
            bubbleData.lastMessage = it
            bubbleData.unreadCount++
            bubbleData.timestamp = System.currentTimeMillis()
        }

        val position = calculateBubblePosition(context, userId)

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

            // ✅ Mark service as running
            isServiceRunning = true

            android.util.Log.d("BubbleManager", "✅ Service started")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to start service: $e")
        }

        listenToMessages(context, userId)
    }

    private fun calculateBubblePosition(context: Context, userId: String): BubblePosition {
        bubblePositions[userId]?.let {
            return it
        }

        updateScreenDimensions(context)

        val x = lastScreenWidth - 100

        val bubbleHeight = 80
        val maxBubblesVisible = (lastScreenHeight - 300) / bubbleHeight

        val y = if (activeBubbles.size <= 1) {
            200
        } else {
            val index = (activeBubbles.size - 1) % maxBubblesVisible
            200 + (index * bubbleHeight)
        }

        val position = BubblePosition(x, y, userId)
        bubblePositions[userId] = position

        android.util.Log.d("BubbleManager", "📍 Position for $userId: x=$x, y=$y")

        return position
    }

    private fun listenToMessages(context: Context, userId: String) {
        if (messageListeners.containsKey(userId)) {
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
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val message = change.document.getString("content") ?: ""
                            val type = change.document.getLong("type")?.toInt() ?: 0

                            activeBubbles[userId]?.let { bubble ->
                                bubble.lastMessage = if (type == 0) message else "📷 Image"
                                bubble.unreadCount++
                                bubble.timestamp = System.currentTimeMillis()

                                notifyBubbleUpdate(context, userId, bubble)
                            }
                        }
                    }
                }

            listener?.let { messageListeners[userId] = it }
            android.util.Log.d("BubbleManager", "✅ Listener setup: $userId")
        } catch (e: Exception) {
            android.util.Log.e("BubbleManager", "❌ Failed to setup listener: $e")
        }
    }

    private fun notifyBubbleUpdate(context: Context, userId: String, bubble: BubbleData) {
        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_UPDATE_BUBBLE
            putExtra("userId", userId)
            putExtra("unreadCount", bubble.unreadCount)
            putExtra("lastMessage", bubble.lastMessage)
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {}
    }

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
        } catch (e: Exception) {}

        repositionBubbles(context)

        // ✅ Check if should stop service
        if (activeBubbles.isEmpty()) {
            isServiceRunning = false
        }
    }

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
    }

    fun markAsRead(context: Context, userId: String) {
        activeBubbles[userId]?.unreadCount = 0

        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_UPDATE_BUBBLE
            putExtra("userId", userId)
            putExtra("unreadCount", 0)
            putExtra("lastMessage", activeBubbles[userId]?.lastMessage ?: "")
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {}
    }

    fun getCurrentUserId(): String? {
        return try {
            auth?.currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    fun getBubbleData(userId: String): BubbleData? {
        return activeBubbles[userId]
    }

    fun isBubbleActive(userId: String): Boolean {
        return activeBubbles.containsKey(userId)
    }

    fun getActiveBubbles(): Map<String, BubbleData> {
        return activeBubbles.toMap()
    }

    fun onAppPaused() {
        android.util.Log.d("BubbleManager", "⏸️ App paused")
    }

    fun onAppResumed(context: Context) {
        android.util.Log.d("BubbleManager", "▶️ App resumed")

        activeBubbles.keys.toList().forEach { userId ->
            val bubble = activeBubbles[userId]
            if (bubble != null) {
                showBubble(context, userId, bubble.userName, bubble.avatarUrl)
            }
        }
    }

    fun cleanup() {
        android.util.Log.d("BubbleManager", "🧹 Cleanup")

        messageListeners.values.forEach {
            try {
                it.remove()
            } catch (e: Exception) {}
        }

        messageListeners.clear()
        activeBubbles.clear()
        bubblePositions.clear()
        nextYPosition = 200
        lastOrientation = Configuration.ORIENTATION_UNDEFINED
        isServiceRunning = false
    }
}