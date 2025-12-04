// android/app/src/main/kotlin/hust/appchat/bubble/BubbleManager.kt
// ✅ COMPLETE FIX: Bubble persistence with SharedPreferences
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

object BubbleManager {
    private val activeBubbles = mutableMapOf<String, BubbleData>()
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()

    private var isServiceRunning = false

    private val bubblePositions = mutableMapOf<String, BubblePosition>()
    private var nextYPosition = 200

    private var lastScreenWidth = 0
    private var lastScreenHeight = 0
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    // ✅ NEW: SharedPreferences for persistence
    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private const val PREFS_NAME = "bubble_manager_prefs"
    private const val KEY_ACTIVE_BUBBLES = "active_bubbles"
    private const val KEY_LAST_SAVE_TIME = "last_save_time"
    private const val EXPIRY_HOURS = 24L

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

    // ✅ NEW: Serializable data for persistence
    data class BubblePersistData(
        val userId: String,
        val userName: String,
        val avatarUrl: String,
        val unreadCount: Int,
        val lastMessage: String,
        val timestamp: Long,
        val positionX: Int,
        val positionY: Int
    )

    fun init(context: Context) {
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            updateScreenDimensions(context)

            // ✅ NEW: Restore bubbles from storage
            restoreBubbles(context)

            Log.d("BubbleManager", "✅ Initialized")
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to init: $e")
        }
    }

    // ========================================
    // Persistence Logic (Save/Restore/Clear)
    // ========================================

    // ✅ NEW: Save bubbles to SharedPreferences
    private fun saveBubbles() {
        try {
            val persistDataList = activeBubbles.mapNotNull { (userId, bubble) ->
                // Chỉ lưu những bubble có userId và position hợp lệ
                val position = bubblePositions[userId] ?: return@mapNotNull null
                BubblePersistData(
                    userId = bubble.userId,
                    userName = bubble.userName,
                    avatarUrl = bubble.avatarUrl,
                    unreadCount = bubble.unreadCount,
                    lastMessage = bubble.lastMessage,
                    timestamp = bubble.timestamp,
                    positionX = position.x,
                    positionY = position.y
                )
            }

            if (persistDataList.isNotEmpty()) {
                val json = gson.toJson(persistDataList)
                prefs?.edit()?.apply {
                    putString(KEY_ACTIVE_BUBBLES, json)
                    putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
                    apply()
                }
                Log.d("BubbleManager", "💾 Saved ${persistDataList.size} bubbles")
            } else {
                clearSavedBubbles()
            }
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to save bubbles: $e")
        }
    }

    // ✅ NEW: Restore bubbles from SharedPreferences
    private fun restoreBubbles(context: Context) {
        try {
            val json = prefs?.getString(KEY_ACTIVE_BUBBLES, null)
            if (json.isNullOrEmpty()) {
                Log.d("BubbleManager", "ℹ️ No saved bubbles")
                return
            }

            val lastSaveTime = prefs?.getLong(KEY_LAST_SAVE_TIME, 0) ?: 0
            val hoursSinceLastSave = (System.currentTimeMillis() - lastSaveTime) / (1000 * 60 * 60)

            // ✅ Only restore if saved within expiry time (e.g., 24 hours)
            if (hoursSinceLastSave > EXPIRY_HOURS) {
                Log.d("BubbleManager", "⏰ Saved bubbles too old ($hoursSinceLastSave h), clearing")
                clearSavedBubbles()
                return
            }

            val type = object : TypeToken<List<BubblePersistData>>() {}.type
            val persistDataList: List<BubblePersistData> = gson.fromJson(json, type)

            Log.d("BubbleManager", "📦 Restoring ${persistDataList.size} bubbles")

            persistDataList.forEach { data ->
                // 1. Restore data to memory
                activeBubbles[data.userId] = BubbleData(
                    userId = data.userId,
                    userName = data.userName,
                    avatarUrl = data.avatarUrl,
                    unreadCount = data.unreadCount,
                    lastMessage = data.lastMessage,
                    timestamp = data.timestamp
                )

                // 2. Restore position to memory
                bubblePositions[data.userId] = BubblePosition(
                    x = data.positionX,
                    y = data.positionY,
                    userId = data.userId
                )

                // 3. ✅ Show bubble (Service will be started via startForegroundService/startService)
                showBubble(
                    context = context,
                    userId = data.userId,
                    userName = data.userName,
                    avatarUrl = data.avatarUrl,
                    message = data.lastMessage // Message is only passed here for display/update
                )
            }

            Log.d("BubbleManager", "✅ Bubbles restored and services triggered")
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to restore bubbles: $e")
            clearSavedBubbles()
        }
    }

    // ✅ NEW: Clear saved bubbles
    fun clearSavedBubbles() {
        prefs?.edit()?.apply {
            remove(KEY_ACTIVE_BUBBLES)
            remove(KEY_LAST_SAVE_TIME)
            apply()
        }
        Log.d("BubbleManager", "🗑️ Cleared saved bubbles")
    }

    // ========================================
    // Lifecycle and Utility Methods
    // ========================================

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
            Log.d("BubbleManager", "📱 Orientation changed")

            val oldWidth = lastScreenWidth
            val oldHeight = lastScreenHeight

            updateScreenDimensions(context)
            repositionBubblesForRotation(context, oldWidth, oldHeight)

            lastOrientation = newConfig.orientation

            // ✅ Save after rotation
            saveBubbles()
        }
    }

    private fun updateScreenDimensions(context: Context) {
        val displayMetrics = context.resources.displayMetrics
        lastScreenWidth = displayMetrics.widthPixels
        lastScreenHeight = displayMetrics.metrics.heightPixels

        Log.d("BubbleManager", "📱 Screen: ${lastScreenWidth}x${lastScreenHeight}")
    }

    private fun repositionBubblesForRotation(
        context: Context,
        oldWidth: Int,
        oldHeight: Int
    ) {
        if (activeBubbles.isEmpty()) return

        bubblePositions.forEach { (userId, position) ->
            // Use old dimensions for proportional recalculation
            val xPercent = position.x.toFloat() / oldWidth
            val yPercent = position.y.toFloat() / oldHeight

            // Apply new dimensions
            position.x = (xPercent * lastScreenWidth).toInt()
            position.y = (yPercent * lastScreenHeight).toInt()

            // Keep within bounds (assuming bubble size is approx 100x100)
            position.x = position.x.coerceIn(0, lastScreenWidth - 100)
            position.y = position.y.coerceIn(0, lastScreenHeight - 100)

            // Notify service to update UI position
            val intent = Intent(context, BubbleOverlayService::class.java).apply {
                action = BubbleOverlayService.ACTION_UPDATE_BUBBLE_POSITION
                putExtra("userId", userId)
                putExtra("positionX", position.x)
                putExtra("positionY", position.y)
            }

            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("BubbleManager", "❌ Failed to reposition bubble $userId on rotation: $e")
            }
        }
    }

    // ========================================
    // Main Bubble Operations
    // ========================================

    fun showBubble(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        message: String? = null
    ) {
        Log.d("BubbleManager", "🎈 showBubble: $userName")

        val bubbleData = activeBubbles.getOrPut(userId) {
            // Re-initialize listener if not present (might happen after crash/kill)
            listenToMessages(context, userId)
            BubbleData(userId, userName, avatarUrl)
        }

        // Update unread count and message if a new message is explicitly passed
        message?.let {
            bubbleData.lastMessage = it
            bubbleData.unreadCount++
            bubbleData.timestamp = System.currentTimeMillis()
        }

        val position = calculateBubblePosition(context, userId)

        // Send Intent to BubbleOverlayService to display/update the UI bubble
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

            isServiceRunning = true

            // ✅ Save after showing bubble
            saveBubbles()

            Log.d("BubbleManager", "✅ Service started/updated")
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to start service: $e")
        }

        // Ensure message listening is active
        listenToMessages(context, userId)
    }

    private fun calculateBubblePosition(context: Context, userId: String): BubblePosition {
        // ✅ 1. Use saved position if exists
        bubblePositions[userId]?.let {
            Log.d("BubbleManager", "📍 Using saved position for $userId")
            return it
        }

        // 2. Calculate new position if not saved
        updateScreenDimensions(context)

        val x = lastScreenWidth - 100

        val bubbleHeight = 80
        val maxBubblesVisible = (lastScreenHeight - 300) / bubbleHeight

        val y = if (activeBubbles.size <= 1) {
            200
        } else {
            // Find an empty spot or stack vertically
            val index = (activeBubbles.size - 1) % maxBubblesVisible
            200 + (index * bubbleHeight)
        }

        val position = BubblePosition(x, y, userId)
        bubblePositions[userId] = position

        Log.d("BubbleManager", "📍 New position for $userId: x=$x, y=$y")

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
                        if (change.type == DocumentChange.Type.ADDED) {
                            val message = change.document.getString("content") ?: ""
                            val type = change.document.getLong("type")?.toInt() ?: 0

                            activeBubbles[userId]?.let { bubble ->
                                bubble.lastMessage = if (type == 0) message else "📷 Image"
                                bubble.unreadCount++
                                bubble.timestamp = System.currentTimeMillis()

                                notifyBubbleUpdate(context, userId, bubble)

                                // ✅ Save after new message update
                                saveBubbles()
                            }
                        }
                    }
                }

            listener?.let { messageListeners[userId] = it }
            Log.d("BubbleManager", "✅ Listener setup: $userId")
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to setup listener: $e")
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
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to notify update: $e")
        }
    }

    fun removeBubble(context: Context, userId: String) {
        Log.d("BubbleManager", "🗑️ Removing bubble: $userId")

        activeBubbles.remove(userId)
        bubblePositions.remove(userId)
        messageListeners.remove(userId)?.remove()

        val intent = Intent(context, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE_BUBBLE
            putExtra("userId", userId)
        }

        try {
            context.startService(intent)
            Log.d("BubbleManager", "✅ Bubble removed: $userId")
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to send hide bubble intent: $e")
        }

        // Reposition and then save
        repositionBubbles(context)

        if (activeBubbles.isEmpty()) {
            isServiceRunning = false
            clearSavedBubbles()
        } else {
            // ✅ Save after removal/reposition
            saveBubbles()
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

            // Optionally notify service to update position immediately if needed,
            // but for simplicity, we rely on the next showBubble call or snapToEdge.
            // ... (optional service notification logic here)

            yPos += 80
        }

        nextYPosition = yPos
    }

    // ✅ NEW: Update bubble position (called when user drags in BubbleOverlayService)
    fun updateBubblePosition(userId: String, x: Int, y: Int) {
        bubblePositions[userId]?.apply {
            this.x = x
            this.y = y
        }

        // ✅ Save after position update
        saveBubbles()

        Log.d("BubbleManager", "📍 Updated position for $userId: ($x, $y)")
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

            // ✅ Save after mark as read
            saveBubbles()
        } catch (e: Exception) {
            Log.e("BubbleManager", "❌ Failed to send markAsRead intent: $e")
        }
    }

    fun getCurrentUserId(): String? {
        return try {
            auth?.currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    // ... (Getter functions remain the same) ...
    fun getBubbleData(userId: String): BubbleData? {
        return activeBubbles[userId]
    }

    fun isBubbleActive(userId: String): Boolean {
        return activeBubbles.containsKey(userId)
    }

    fun getActiveBubbles(): Map<String, BubbleData> {
        return activeBubbles.toMap()
    }

    // ========================================
    // Service/App Lifecycle Hooks
    // ========================================

    fun onAppPaused() {
        Log.d("BubbleManager", "⏸️ App paused (or service kill detected)")
        // ✅ Save when app goes to background or before service is killed
        saveBubbles()
    }

    fun cleanup() {
        Log.d("BubbleManager", "🧹 Cleanup: Removing listeners and data.")

        // Don't call saveBubbles() here unless explicitly clearing.
        // We want the data to persist for potential restore.
        // saveBubbles()

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