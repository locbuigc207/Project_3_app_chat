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
 * Service quản lý overlay bubbles và mini chat windows
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> {
                val userId = intent.getStringExtra("userId") ?: return START_STICKY
                val userName = intent.getStringExtra("userName") ?: ""
                val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""
                val unreadCount = intent.getIntExtra("unreadCount", 0)
                val lastMessage = intent.getStringExtra("lastMessage") ?: ""

                showBubble(userId, userName, avatarUrl, unreadCount, lastMessage)
            }

            ACTION_UPDATE_BUBBLE -> {
                val userId = intent.getStringExtra("userId") ?: return START_STICKY
                val unreadCount = intent.getIntExtra("unreadCount", 0)
                val lastMessage = intent.getStringExtra("lastMessage") ?: ""

                updateBubble(userId, unreadCount, lastMessage)
            }

            ACTION_HIDE_BUBBLE -> {
                val userId = intent.getStringExtra("userId") ?: return START_STICKY
                hideBubble(userId)
            }

            ACTION_SHOW_MINI_CHAT -> {
                val userId = intent.getStringExtra("userId") ?: return START_STICKY
                val userName = intent.getStringExtra("userName") ?: ""
                val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""

                showMiniChat(userId, userName, avatarUrl)
            }

            ACTION_HIDE_MINI_CHAT -> {
                hideMiniChat()
            }
        }

        return START_STICKY
    }

    /**
     * Hiển thị bubble
     */
    private fun showBubble(
        userId: String,
        userName: String,
        avatarUrl: String,
        unreadCount: Int,
        lastMessage: String
    ) {
        // Remove existing bubble
        bubbleViews[userId]?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "Error removing old bubble: $e")
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
                // Animate to delete
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
            x = screenWidth - 100
            y = 200 + (bubbleViews.size * 100)
        }

        // Add to window
        try {
            windowManager?.addView(bubbleView, params)
            bubbleViews[userId] = bubbleView

            // Snap to edge after a short delay
            bubbleView.postDelayed({
                bubbleView.snapToEdge(screenWidth)
            }, 300)

            android.util.Log.d("BubbleService", "✅ Bubble added: $userName")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to add bubble: $e")
        }
    }

    /**
     * Cập nhật bubble
     */
    private fun updateBubble(userId: String, unreadCount: Int, lastMessage: String) {
        bubbleViews[userId]?.let { bubble ->
            bubble.updateUnreadCount(unreadCount)
            bubble.updateLastMessage(lastMessage)
            bubble.animateNewMessage()
        }
    }

    /**
     * Ẩn bubble
     */
    private fun hideBubble(userId: String) {
        bubbleViews.remove(userId)?.let { view ->
            try {
                windowManager?.removeView(view)
                android.util.Log.d("BubbleService", "✅ Bubble removed: $userId")
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ Error removing bubble: $e")
            }
        }

        // Stop service if no more bubbles
        if (bubbleViews.isEmpty() && miniChatWindow == null) {
            stopForeground(true)
            stopSelf()
        }
    }

    /**
     * Bubble được click → hiển thị mini chat
     */
    private fun onBubbleClicked(userId: String, userName: String, avatarUrl: String) {
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
    }

    /**
     * Hiển thị mini chat window
     */
    private fun showMiniChat(userId: String, userName: String, avatarUrl: String) {
        // Remove existing mini chat
        miniChatWindow?.let {
            try {
                it.cleanup()
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "Error removing mini chat: $e")
            }
        }

        // Create mini chat
        val miniChat = MiniChatWindow(this, userId, userName, avatarUrl)
        currentMiniChatUserId = userId

        // Set listeners
        miniChat.setOnMinimizeListener {
            hideMiniChat()
            bubbleViews[userId]?.visibility = View.VISIBLE
        }

        miniChat.setOnCloseListener {
            hideMiniChat()
            BubbleManager.removeBubble(this, userId)
        }

        miniChat.setOnMessageSentListener { message ->
            // Send broadcast to Flutter
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

        try {
            windowManager?.addView(miniChat, params)
            miniChatWindow = miniChat

            android.util.Log.d("BubbleService", "✅ Mini chat opened")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to show mini chat: $e")
        }
    }

    /**
     * Ẩn mini chat
     */
    private fun hideMiniChat() {
        miniChatWindow?.let { view ->
            try {
                view.cleanup()
                windowManager?.removeView(view)
                miniChatWindow = null
                currentMiniChatUserId = null
                android.util.Log.d("BubbleService", "✅ Mini chat closed")
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ Error closing mini chat: $e")
            }
        }
    }

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
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val bubbleCount = bubbleViews.size
        val contentText = if (bubbleCount > 0) {
            "$bubbleCount bubble(s) active"
        } else {
            "Chat bubbles ready"
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

    override fun onDestroy() {
        BubbleManager.cleanup()

        // Remove all views
        bubbleViews.values.forEach { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "Error removing bubble view: $e")
            }
        }
        bubbleViews.clear()

        miniChatWindow?.let {
            try {
                it.cleanup()
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "Error removing mini chat: $e")
            }
        }

        android.util.Log.d("BubbleService", "✅ Service destroyed")
        super.onDestroy()
    }
}