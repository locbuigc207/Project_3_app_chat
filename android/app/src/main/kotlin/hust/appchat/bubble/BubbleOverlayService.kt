// android/app/src/main/kotlin/hust/appchat/bubble/BubbleOverlayService.kt
package hust.appchat.bubble

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import androidx.core.app.NotificationCompat
import hust.appchat.R

/**
 * ✅ COMPLETE: Service quản lý overlay bubbles và mini chat windows
 */
class BubbleOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private val bubbleViews = mutableMapOf<String, BubbleView>()
    private var miniChatWindow: MiniChatWindow? = null
    private var currentMiniChatUserId: String? = null

    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "HIDE_BUBBLE"
        const val ACTION_UPDATE_BUBBLE = "UPDATE_BUBBLE"
        const val ACTION_SHOW_MINI_CHAT = "SHOW_MINI_CHAT"
        const val ACTION_HIDE_MINI_CHAT = "HIDE_MINI_CHAT"

        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "chat_bubbles"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

            // Get screen dimensions
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }

            BubbleManager.init(this)

            android.util.Log.d("BubbleService", "✅ Service created")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to create service: $e")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, createNotification())
            }

            intent?.let { handleIntent(it) }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error in onStartCommand: $e")
        }

        return START_STICKY
    }

    /**
     * ✅ Handle incoming intents
     */
    private fun handleIntent(intent: Intent) {
        android.util.Log.d("BubbleService", "📨 Received action: ${intent.action}")

        try {
            when (intent.action) {
                ACTION_SHOW_BUBBLE -> {
                    val userId = intent.getStringExtra("userId") ?: return
                    val userName = intent.getStringExtra("userName") ?: ""
                    val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""
                    val unreadCount = intent.getIntExtra("unreadCount", 0)
                    val lastMessage = intent.getStringExtra("lastMessage") ?: ""
                    val positionX = intent.getIntExtra("positionX", screenWidth - 100)
                    val positionY = intent.getIntExtra("positionY", 200)

                    showBubble(userId, userName, avatarUrl, unreadCount, lastMessage, positionX, positionY)
                }

                ACTION_UPDATE_BUBBLE -> {
                    val userId = intent.getStringExtra("userId") ?: return
                    val unreadCount = intent.getIntExtra("unreadCount", 0)
                    val lastMessage = intent.getStringExtra("lastMessage") ?: ""

                    updateBubble(userId, unreadCount, lastMessage)
                }

                ACTION_HIDE_BUBBLE -> {
                    val userId = intent.getStringExtra("userId") ?: return
                    hideBubble(userId)
                }

                ACTION_SHOW_MINI_CHAT -> {
                    val userId = intent.getStringExtra("userId") ?: return
                    val userName = intent.getStringExtra("userName") ?: ""
                    val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""

                    showMiniChat(userId, userName, avatarUrl)
                }

                ACTION_HIDE_MINI_CHAT -> {
                    hideMiniChat()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error handling intent: $e")
        }
    }

    /**
     * ✅ Show bubble
     */
    private fun showBubble(
        userId: String,
        userName: String,
        avatarUrl: String,
        unreadCount: Int,
        lastMessage: String,
        positionX: Int,
        positionY: Int
    ) {
        android.util.Log.d("BubbleService", "🎈 Showing bubble: $userName")

        try {
            // Remove existing bubble
            bubbleViews[userId]?.let {
                try {
                    windowManager?.removeView(it)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing old bubble: $e")
                }
            }

            // Create new bubble
            val bubbleView = BubbleView(this, userId, userName, avatarUrl)
            bubbleView.updateUnreadCount(unreadCount)
            bubbleView.updateLastMessage(lastMessage)

            // Set click listener
            bubbleView.setOnClickListener {
                onBubbleClicked(userId, userName, avatarUrl)
            }

            // Set drag listener
            bubbleView.setOnDragListener { isInDeleteZone ->
                if (isInDeleteZone) {
                    bubbleView.animateDelete {
                        BubbleManager.removeBubble(this, userId)
                    }
                }
            }

            // Layout params
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = positionX
                y = positionY
            }

            // Add to window
            windowManager?.addView(bubbleView, params)
            bubbleViews[userId] = bubbleView

            // Snap to edge
            bubbleView.postDelayed({
                bubbleView.snapToEdge(screenWidth)
            }, 300)

            // Update notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, createNotification())
            }

            android.util.Log.d("BubbleService", "✅ Bubble added: $userName")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to add bubble: $e")
        }
    }

    /**
     * ✅ Update bubble
     */
    private fun updateBubble(userId: String, unreadCount: Int, lastMessage: String) {
        try {
            bubbleViews[userId]?.let { bubble ->
                bubble.updateUnreadCount(unreadCount)
                bubble.updateLastMessage(lastMessage)
                bubble.animateNewMessage()
                android.util.Log.d("BubbleService", "✅ Bubble updated: $userId")
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to update bubble: $e")
        }
    }

    /**
     * ✅ Hide bubble
     */
    private fun hideBubble(userId: String) {
        try {
            bubbleViews.remove(userId)?.let { view ->
                windowManager?.removeView(view)
                android.util.Log.d("BubbleService", "✅ Bubble removed: $userId")
            }

            // Stop service if no more bubbles and no mini chat
            if (bubbleViews.isEmpty() && miniChatWindow == null) {
                stopForeground(true)
                stopSelf()
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error removing bubble: $e")
        }
    }

    /**
     * ✅ Bubble clicked
     */
    private fun onBubbleClicked(userId: String, userName: String, avatarUrl: String) {
        android.util.Log.d("BubbleService", "🫧 Bubble clicked: $userName")

        try {
            // Hide bubble
            bubbleViews[userId]?.visibility = View.GONE

            // Show mini chat
            showMiniChat(userId, userName, avatarUrl)

            // Mark as read
            BubbleManager.markAsRead(userId)

            // Send broadcast to Flutter
            val intent = Intent("CHAT_BUBBLE_CLICKED").apply {
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error handling bubble click: $e")
        }
    }

    /**
     * ✅ Show mini chat
     */
    private fun showMiniChat(userId: String, userName: String, avatarUrl: String) {
        android.util.Log.d("BubbleService", "💬 Showing mini chat: $userName")

        try {
            // Remove existing mini chat
            miniChatWindow?.let {
                try {
                    it.cleanup()
                    windowManager?.removeView(it)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing mini chat: $e")
                }
            }

            // Create mini chat
            val miniChat = MiniChatWindow(this, userId, userName, avatarUrl)
            currentMiniChatUserId = userId

            // Set listeners
            miniChat.setOnMinimizeListener {
                android.util.Log.d("BubbleService", "⬇️ Minimize mini chat")
                hideMiniChat()
                bubbleViews[userId]?.visibility = View.VISIBLE
            }

            miniChat.setOnCloseListener {
                android.util.Log.d("BubbleService", "❌ Close mini chat")
                hideMiniChat()
                BubbleManager.removeBubble(this, userId)
            }

            miniChat.setOnMessageSentListener { message ->
                android.util.Log.d("BubbleService", "✉️ Message sent: $message")

                val intent = Intent("CHAT_BUBBLE_MESSAGE").apply {
                    putExtra("userId", userId)
                    putExtra("message", message)
                }
                sendBroadcast(intent)
            }

            // Layout params
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                (screenWidth * 0.9).toInt(),
                (screenHeight * 0.7).toInt(),
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            windowManager?.addView(miniChat, params)
            miniChatWindow = miniChat

            android.util.Log.d("BubbleService", "✅ Mini chat opened")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to show mini chat: $e")
        }
    }

    /**
     * ✅ Hide mini chat
     */
    private fun hideMiniChat() {
        try {
            miniChatWindow?.let { view ->
                view.cleanup()
                windowManager?.removeView(view)
                miniChatWindow = null
                currentMiniChatUserId = null
                android.util.Log.d("BubbleService", "✅ Mini chat closed")
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error closing mini chat: $e")
        }
    }

    /**
     * ✅ Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Bubbles",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active chat bubbles"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * ✅ Create notification
     */
    private fun createNotification(): Notification {
        val bubbleCount = bubbleViews.size
        val hasMiniChat = miniChatWindow != null

        val contentText = when {
            bubbleCount > 0 && hasMiniChat -> "$bubbleCount bubble(s) + Mini chat active"
            bubbleCount > 0 -> "$bubbleCount bubble(s) active"
            hasMiniChat -> "Mini chat active"
            else -> "Chat bubbles ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat Bubbles")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.bubble_background)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * ✅ Cleanup
     */
    override fun onDestroy() {
        try {
            BubbleManager.cleanup()

            // Remove all bubbles
            bubbleViews.values.forEach { view ->
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing bubble: $e")
                }
            }
            bubbleViews.clear()

            // Remove mini chat
            miniChatWindow?.let {
                try {
                    it.cleanup()
                    windowManager?.removeView(it)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing mini chat: $e")
                }
            }

            android.util.Log.d("BubbleService", "✅ Service destroyed")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error in onDestroy: $e")
        }

        super.onDestroy()
    }
}