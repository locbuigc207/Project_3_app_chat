// android/app/src/main/kotlin/hust/appchat/bubble/BubbleOverlayService.kt - FIXED
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
 * ✅ FIXED: Service with proper WindowManager configuration
 *
 * KEY FIXES:
 * 1. FLAG_NOT_TOUCH_MODAL instead of FLAG_NOT_FOCUSABLE for bubbles
 * 2. Proper position update handling
 * 3. Mini chat with correct flags and keyboard support
 * 4. Reference tracking for WindowManager updates
 */
class BubbleOverlayService : Service() {

    private var windowManager: WindowManager? = null

    // ✅ FIXED: Track bubble views AND their params for updates
    private val bubbleViews = mutableMapOf<String, BubbleView>()
    private val bubbleParams = mutableMapOf<String, WindowManager.LayoutParams>()

    private var miniChatWindow: MiniChatWindow? = null
    private var miniChatParams: WindowManager.LayoutParams? = null
    private var currentMiniChatUserId: String? = null

    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "HIDE_BUBBLE"
        const val ACTION_UPDATE_BUBBLE = "UPDATE_BUBBLE"
        const val ACTION_UPDATE_BUBBLE_POSITION = "UPDATE_BUBBLE_POSITION"  // ✅ ADDED
        const val ACTION_SHOW_MINI_CHAT = "SHOW_MINI_CHAT"
        const val ACTION_HIDE_MINI_CHAT = "HIDE_MINI_CHAT"

        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "chat_bubbles"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

            android.util.Log.d("BubbleService", "📱 Screen: ${screenWidth}x${screenHeight}")

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

                // ✅ FIXED: Handle position updates
                ACTION_UPDATE_BUBBLE_POSITION -> {
                    val userId = intent.getStringExtra("userId") ?: return
                    val positionX = intent.getIntExtra("positionX", -1)
                    val positionY = intent.getIntExtra("positionY", -1)

                    if (positionX >= 0 && positionY >= 0) {
                        updateBubblePosition(userId, positionX, positionY)
                    }
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

    // ✅ FIXED: Bubble with FLAG_NOT_TOUCH_MODAL
    private fun showBubble(
        userId: String,
        userName: String,
        avatarUrl: String,
        unreadCount: Int,
        lastMessage: String,
        positionX: Int,
        positionY: Int
    ) {
        android.util.Log.d("BubbleService", "🎈 Showing bubble: $userName at ($positionX, $positionY)")

        try {
            // Remove existing bubble if present
            bubbleViews[userId]?.let {
                try {
                    windowManager?.removeView(it)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing old bubble: $e")
                }
            }

            val bubbleView = BubbleView(this, userId, userName, avatarUrl)
            bubbleView.updateUnreadCount(unreadCount)
            bubbleView.updateLastMessage(lastMessage)

            // ✅ Set click listener - handle internally instead of broadcast
            bubbleView.setOnClickListener {
                android.util.Log.d("BubbleService", "🫧 Bubble clicked: $userName")
                onBubbleClicked(userId, userName, avatarUrl)
            }

            // ✅ Set drag listener with position callback
            bubbleView.setOnDragListener { isInDeleteZone, deltaX, deltaY ->
                if (isInDeleteZone) {
                    bubbleView.animateDelete {
                        BubbleManager.removeBubble(this, userId)
                    }
                } else {
                    // Update position during drag
                    bubbleParams[userId]?.let { params ->
                        params.x += deltaX.toInt()
                        params.y += deltaY.toInt()

                        // Ensure within bounds
                        params.x = params.x.coerceIn(0, screenWidth - bubbleView.width)
                        params.y = params.y.coerceIn(0, screenHeight - bubbleView.height)

                        try {
                            windowManager?.updateViewLayout(bubbleView, params)
                        } catch (e: Exception) {
                            android.util.Log.e("BubbleService", "❌ Update layout error: $e")
                        }
                    }
                }
            }

            // ✅ CRITICAL FIX: Use FLAG_NOT_TOUCH_MODAL instead of FLAG_NOT_FOCUSABLE
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
                // ✅ FIXED: NOT_TOUCH_MODAL allows bubble to receive touches
                // while still passing touches outside bubble bounds to views below
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = positionX
                y = positionY
            }

            windowManager?.addView(bubbleView, params)

            // ✅ FIXED: Store both view and params for updates
            bubbleViews[userId] = bubbleView
            bubbleParams[userId] = params

            // Snap to edge after initial display
            bubbleView.postDelayed({
                snapBubbleToEdge(userId)
            }, 300)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, createNotification())
            }

            android.util.Log.d("BubbleService", "✅ Bubble added: $userName")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to add bubble: $e")
            android.util.Log.e("BubbleService", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    // ✅ NEW: Snap bubble to screen edge
    private fun snapBubbleToEdge(userId: String) {
        val bubbleView = bubbleViews[userId] ?: return
        val params = bubbleParams[userId] ?: return

        val centerX = params.x + bubbleView.width / 2
        val targetX = if (centerX < screenWidth / 2) {
            20 // Left edge
        } else {
            screenWidth - bubbleView.width - 20 // Right edge
        }

        // Animate position change
        android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 300
            interpolator = android.view.animation.OvershootInterpolator(1.5f)

            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                try {
                    windowManager?.updateViewLayout(bubbleView, params)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "❌ Snap animation error: $e")
                    cancel()
                }
            }

            start()
        }
    }

    // ✅ NEW: Update bubble position (from BubbleManager or rotation)
    private fun updateBubblePosition(userId: String, x: Int, y: Int) {
        android.util.Log.d("BubbleService", "📍 Updating bubble position: $userId to ($x, $y)")

        val bubbleView = bubbleViews[userId]
        val params = bubbleParams[userId]

        if (bubbleView == null || params == null) {
            android.util.Log.w("BubbleService", "⚠️ Cannot update position - bubble not found: $userId")
            return
        }

        params.x = x.coerceIn(0, screenWidth - bubbleView.width)
        params.y = y.coerceIn(0, screenHeight - bubbleView.height)

        try {
            windowManager?.updateViewLayout(bubbleView, params)
            android.util.Log.d("BubbleService", "✅ Position updated: $userId")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to update position: $e")
        }
    }

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

    private fun hideBubble(userId: String) {
        try {
            val view = bubbleViews.remove(userId)
            bubbleParams.remove(userId)

            view?.let {
                windowManager?.removeView(it)
                android.util.Log.d("BubbleService", "✅ Bubble removed: $userId")
            }

            if (bubbleViews.isEmpty() && miniChatWindow == null) {
                stopForeground(true)
                stopSelf()
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error removing bubble: $e")
        }
    }

    private fun onBubbleClicked(userId: String, userName: String, avatarUrl: String) {
        android.util.Log.d("BubbleService", "🫧 Bubble clicked: $userName")

        try {
            // Hide the bubble temporarily
            bubbleViews[userId]?.visibility = View.GONE

            // Show mini chat
            showMiniChat(userId, userName, avatarUrl)

            // Mark as read
            BubbleManager.markAsRead(userId)

            // ✅ OPTIONAL: Send broadcast to Flutter if needed
            val intent = Intent("CHAT_BUBBLE_CLICKED").apply {
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)
            }
            sendBroadcast(intent)

            android.util.Log.d("BubbleService", "📡 Broadcast sent: CHAT_BUBBLE_CLICKED")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error handling bubble click: $e")
        }
    }

    // ✅ FIXED: Mini chat with proper flags and keyboard support
    private fun showMiniChat(userId: String, userName: String, avatarUrl: String) {
        android.util.Log.d("BubbleService", "💬 Showing mini chat: $userName")

        try {
            // Remove existing mini chat
            miniChatWindow?.let {
                try {
                    it.cleanup()
                    windowManager?.removeView(it)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing old mini chat: $e")
                }
            }

            val miniChat = try {
                MiniChatWindow(this, userId, userName, avatarUrl)
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ Failed to create MiniChatWindow: $e")
                android.util.Log.e("BubbleService", "Stack trace: ${e.stackTraceToString()}")
                return
            }

            currentMiniChatUserId = userId

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

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // ✅ Calculate size (90% width, 70% height) with boundaries
            val width = (screenWidth * 0.9).toInt().coerceAtMost(600) // Max 600dp wide
            val height = (screenHeight * 0.7).toInt().coerceAtMost(800) // Max 800dp tall

            android.util.Log.d("BubbleService", "📏 Mini chat size: ${width}x${height}")

            // ✅ CRITICAL FIX: Proper flags for mini chat
            val params = WindowManager.LayoutParams(
                width,
                height,
                layoutFlag,
                // ✅ FIXED FLAGS:
                // - NOT_TOUCH_MODAL: Allows interaction within window
                // - WATCH_OUTSIDE_TOUCH: Detect touches outside
                // - No LAYOUT_NO_LIMITS: Keep within screen bounds
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                // ✅ CRITICAL: Keyboard support
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            }

            try {
                windowManager?.addView(miniChat, params)
                miniChatWindow = miniChat
                miniChatParams = params
                android.util.Log.d("BubbleService", "✅ Mini chat opened successfully")
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ Failed to add mini chat to window: $e")
                android.util.Log.e("BubbleService", "Stack trace: ${e.stackTraceToString()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to show mini chat: $e")
            android.util.Log.e("BubbleService", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    private fun hideMiniChat() {
        try {
            miniChatWindow?.let { view ->
                view.cleanup()
                windowManager?.removeView(view)
                miniChatWindow = null
                miniChatParams = null
                currentMiniChatUserId = null
                android.util.Log.d("BubbleService", "✅ Mini chat closed")
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Error closing mini chat: $e")
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
            notificationManager?.createNotificationChannel(channel)
        }
    }

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

    override fun onDestroy() {
        try {
            BubbleManager.cleanup()

            bubbleViews.values.forEach { view ->
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleService", "⚠️ Error removing bubble: $e")
                }
            }
            bubbleViews.clear()
            bubbleParams.clear()

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