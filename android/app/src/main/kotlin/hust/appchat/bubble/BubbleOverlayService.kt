// android/app/src/main/kotlin/hust/appchat/bubble/BubbleOverlayService.kt
// ✅ FIXED: Mini Chat = Chat Page in Overlay với Flutter Engine

package hust.appchat.bubble

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import hust.appchat.R
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel

class BubbleOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bubbleViews = mutableMapOf<String, BubbleView>()
    private val bubbleParams = mutableMapOf<String, WindowManager.LayoutParams>()

    // ✅ NEW: Flutter Engine for Mini Chat
    private var miniChatFlutterView: FlutterView? = null
    private var miniChatParams: WindowManager.LayoutParams? = null
    private var miniChatEngine: FlutterEngine? = null
    private var miniChatChannel: MethodChannel? = null
    private var currentMiniChatUserId: String? = null
    private var currentMiniChatUserName: String? = null
    private var currentMiniChatAvatarUrl: String? = null

    private var deleteZoneView: DeleteZoneView? = null
    private var isDraggingAnyBubble = false

    private var screenWidth = 0
    private var screenHeight = 0
    private var isServiceRunning = false

    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "HIDE_BUBBLE"
        const val ACTION_UPDATE_BUBBLE = "UPDATE_BUBBLE"
        const val ACTION_UPDATE_BUBBLE_POSITION = "UPDATE_BUBBLE_POSITION"
        const val ACTION_SHOW_MINI_CHAT = "SHOW_MINI_CHAT"
        const val ACTION_HIDE_MINI_CHAT = "HIDE_MINI_CHAT"
        const val ACTION_HIDE_ALL_BUBBLES = "HIDE_ALL_BUBBLES"

        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "chat_bubbles"
        private const val MINI_CHAT_ENGINE_ID = "mini_chat_engine"
        private const val MINI_CHAT_CHANNEL = "mini_chat_channel"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

            val displayMetrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.getRealMetrics(displayMetrics)
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            }

            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

            android.util.Log.d("BubbleService", "✅ onCreate: ${screenWidth}x${screenHeight}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }

            BubbleManager.init(this)

        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ onCreate failed: $e")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, createNotification())
            }

            isServiceRunning = true
            intent?.let { handleIntent(it) }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ onStartCommand error: $e")
        }

        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        android.util.Log.d("BubbleService", "📥 Action: $action")

        when (action) {
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

            ACTION_HIDE_ALL_BUBBLES -> {
                hideAllBubbles()
            }
        }
    }

    // ========================================
    // ✅ NEW: MINI CHAT WITH FLUTTER ENGINE
    // ========================================

    private fun showMiniChat(userId: String, userName: String, avatarUrl: String) {
        android.util.Log.d("BubbleService", "💬 showMiniChat: $userName")

        mainHandler.post {
            try {
                // Hide existing mini chat
                miniChatFlutterView?.let {
                    try {
                        windowManager?.removeView(it)
                    } catch (e: Exception) {
                        android.util.Log.e("BubbleService", "⚠️ Error removing old mini chat: $e")
                    }
                }

                currentMiniChatUserId = userId
                currentMiniChatUserName = userName
                currentMiniChatAvatarUrl = avatarUrl

                // ✅ Get or create Flutter Engine
                miniChatEngine = FlutterEngineCache.getInstance().get(MINI_CHAT_ENGINE_ID)
                if (miniChatEngine == null) {
                    android.util.Log.d("BubbleService", "🔧 Creating new Flutter Engine")
                    miniChatEngine = FlutterEngine(this)
                    miniChatEngine!!.dartExecutor.executeDartEntrypoint(
                        DartExecutor.DartEntrypoint.createDefault()
                    )
                    FlutterEngineCache.getInstance().put(MINI_CHAT_ENGINE_ID, miniChatEngine!!)
                }

                // ✅ Create FlutterView
                miniChatFlutterView = FlutterView(this)
                miniChatFlutterView!!.attachToFlutterEngine(miniChatEngine!!)

                // ✅ Setup MethodChannel
                setupMiniChatChannel()

                // ✅ Send initial data to Flutter
                sendMiniChatData(userId, userName, avatarUrl)

                // ✅ Calculate size
                val density = resources.displayMetrics.density
                val width = ((screenWidth * 0.85).toInt()).coerceIn(300, 600)
                val height = ((screenHeight * 0.7).toInt()).coerceIn(400, 900)

                android.util.Log.d("BubbleService", "📏 Mini chat size: ${width}x${height}")

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                miniChatParams = WindowManager.LayoutParams(
                    width,
                    height,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, // ✅ CRITICAL: Keyboard support
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                }

                // ✅ Add to window manager
                windowManager?.addView(miniChatFlutterView, miniChatParams)

                android.util.Log.d("BubbleService", "✅ Mini chat added")

            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ showMiniChat failed: $e")
                android.util.Log.e("BubbleService", "Stack: ${e.stackTraceToString()}")
                Toast.makeText(this, "Failed to show mini chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMiniChatChannel() {
        try {
            miniChatChannel = MethodChannel(
                miniChatEngine!!.dartExecutor.binaryMessenger,
                MINI_CHAT_CHANNEL
            )

            miniChatChannel!!.setMethodCallHandler { call, result ->
                android.util.Log.d("BubbleService", "📞 Mini chat method: ${call.method}")

                when (call.method) {
                    "minimize" -> {
                        hideMiniChat()
                        // Show bubble back
                        currentMiniChatUserId?.let { userId ->
                            bubbleViews[userId]?.visibility = android.view.View.VISIBLE
                        }
                        result.success(true)
                    }

                    "close" -> {
                        hideMiniChat()
                        currentMiniChatUserId?.let { userId ->
                            BubbleManager.removeBubble(this, userId)
                        }
                        result.success(true)
                    }

                    "sendMessage" -> {
                        val message = call.argument<String>("message") ?: ""
                        android.util.Log.d("BubbleService", "✉️ Message from mini chat: $message")

                        // Send broadcast to Flutter app
                        val intent = Intent("CHAT_BUBBLE_MESSAGE").apply {
                            putExtra("userId", currentMiniChatUserId)
                            putExtra("message", message)
                        }
                        sendBroadcast(intent)

                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }

            android.util.Log.d("BubbleService", "✅ MethodChannel setup complete")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ MethodChannel setup failed: $e")
        }
    }

    private fun sendMiniChatData(userId: String, userName: String, avatarUrl: String) {
        mainHandler.postDelayed({
            try {
                miniChatChannel?.invokeMethod(
                    "initMiniChat",
                    mapOf(
                        "userId" to userId,
                        "userName" to userName,
                        "avatarUrl" to avatarUrl
                    )
                )
                android.util.Log.d("BubbleService", "✅ Sent data to mini chat")
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ Failed to send data: $e")
            }
        }, 500) // Delay to ensure Flutter is ready
    }

    private fun hideMiniChat() {
        mainHandler.post {
            try {
                miniChatFlutterView?.let { view ->
                    windowManager?.removeView(view)
                    miniChatFlutterView = null
                    miniChatParams = null
                }

                currentMiniChatUserId = null
                currentMiniChatUserName = null
                currentMiniChatAvatarUrl = null

                android.util.Log.d("BubbleService", "✅ Mini chat hidden")
            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ Error hiding mini chat: $e")
            }
        }
    }

    // ========================================
    // BUBBLE OPERATIONS (Keep existing code)
    // ========================================

    private fun showBubble(
        userId: String,
        userName: String,
        avatarUrl: String,
        unreadCount: Int,
        lastMessage: String,
        positionX: Int,
        positionY: Int
    ) {
        android.util.Log.d("BubbleService", "🎈 showBubble: $userName")

        mainHandler.post {
            try {
                bubbleViews[userId]?.let {
                    try {
                        windowManager?.removeView(it)
                    } catch (e: Exception) {}
                }

                val bubbleView = BubbleView(this, userId, userName, avatarUrl)
                bubbleView.updateUnreadCount(unreadCount)
                bubbleView.updateLastMessage(lastMessage)

                bubbleView.setOnClickListener {
                    android.util.Log.d("BubbleService", "🫧 Bubble clicked: $userName")
                    onBubbleClicked(userId, userName, avatarUrl)
                }

                bubbleView.setOnDragListener { isInDeleteZone: Boolean, deltaX: Float, deltaY: Float ->
                    if (!isDraggingAnyBubble && (deltaX != 0f || deltaY != 0f)) {
                        isDraggingAnyBubble = true
                        showDeleteZone()
                    }

                    if (isInDeleteZone) {
                        bubbleView.animateDelete {
                            hideDeleteZone()
                            isDraggingAnyBubble = false
                            BubbleManager.removeBubble(this, userId)
                        }
                    } else {
                        bubbleParams[userId]?.let { params ->
                            params.x += deltaX.toInt()
                            params.y += deltaY.toInt()
                            params.x = params.x.coerceIn(0, screenWidth - bubbleView.width)
                            params.y = params.y.coerceIn(0, screenHeight - bubbleView.height)

                            try {
                                windowManager?.updateViewLayout(bubbleView, params)
                            } catch (e: Exception) {}
                        }
                    }
                }

                bubbleView.setOnDragEndListener {
                    hideDeleteZone()
                    isDraggingAnyBubble = false
                    mainHandler.postDelayed({
                        if (bubbleViews.containsKey(userId)) {
                            snapBubbleToEdge(userId)
                        }
                    }, 100)
                }

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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = positionX
                    y = positionY
                }

                windowManager?.addView(bubbleView, params)

                bubbleViews[userId] = bubbleView
                bubbleParams[userId] = params

                android.util.Log.d("BubbleService", "✅ Bubble added: $userName")

                mainHandler.postDelayed({
                    if (bubbleViews.containsKey(userId)) {
                        snapBubbleToEdge(userId)
                    }
                }, 500)

            } catch (e: Exception) {
                android.util.Log.e("BubbleService", "❌ showBubble failed: $e")
                Toast.makeText(this, "Failed to create bubble", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun snapBubbleToEdge(userId: String) {
        val bubbleView = bubbleViews[userId] ?: return
        val params = bubbleParams[userId] ?: return

        val centerX = params.x + bubbleView.width / 2
        val targetX = if (centerX < screenWidth / 2) 20 else screenWidth - bubbleView.width - 20

        android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 300
            interpolator = android.view.animation.OvershootInterpolator(1.5f)
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                try {
                    windowManager?.updateViewLayout(bubbleView, params)
                } catch (e: Exception) {
                    cancel()
                }
            }
            start()
        }
    }

    private fun updateBubblePosition(userId: String, x: Int, y: Int) {
        val bubbleView = bubbleViews[userId] ?: return
        val params = bubbleParams[userId] ?: return

        params.x = x.coerceIn(0, screenWidth - bubbleView.width)
        params.y = y.coerceIn(0, screenHeight - bubbleView.height)

        try {
            windowManager?.updateViewLayout(bubbleView, params)
        } catch (e: Exception) {}
    }

    private fun updateBubble(userId: String, unreadCount: Int, lastMessage: String) {
        try {
            bubbleViews[userId]?.let { bubble ->
                bubble.updateUnreadCount(unreadCount)
                bubble.updateLastMessage(lastMessage)
                bubble.animateNewMessage()
            }
        } catch (e: Exception) {}
    }

    private fun hideBubble(userId: String) {
        mainHandler.post {
            try {
                val view = bubbleViews.remove(userId)
                bubbleParams.remove(userId)

                view?.let {
                    it.cleanup()
                    windowManager?.removeView(it)
                }

                if (bubbleViews.isEmpty() && miniChatFlutterView == null) {
                    stopForeground(true)
                    stopSelf()
                    isServiceRunning = false
                }
            } catch (e: Exception) {}
        }
    }

    private fun hideAllBubbles() {
        mainHandler.post {
            try {
                bubbleViews.values.forEach { view ->
                    try {
                        view.cleanup()
                        windowManager?.removeView(view)
                    } catch (e: Exception) {}
                }

                bubbleViews.clear()
                bubbleParams.clear()

                if (miniChatFlutterView == null) {
                    stopForeground(true)
                    stopSelf()
                    isServiceRunning = false
                }
            } catch (e: Exception) {}
        }
    }

    private fun onBubbleClicked(userId: String, userName: String, avatarUrl: String) {
        try {
            bubbleViews[userId]?.visibility = android.view.View.GONE
            showMiniChat(userId, userName, avatarUrl)
            BubbleManager.markAsRead(this, userId)

            val intent = Intent("CHAT_BUBBLE_CLICKED").apply {
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {}
    }

    private fun showDeleteZone() {
        if (deleteZoneView != null) {
            deleteZoneView?.show()
            return
        }

        try {
            val deleteZone = DeleteZoneView(this)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                150,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
            }

            windowManager?.addView(deleteZone, params)
            deleteZoneView = deleteZone

            deleteZone.show()
        } catch (e: Exception) {}
    }

    private fun hideDeleteZone() {
        deleteZoneView?.hide()
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
        val hasMiniChat = miniChatFlutterView != null

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
        android.util.Log.d("BubbleService", "🛑 onDestroy")

        try {
            BubbleManager.cleanup()

            deleteZoneView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (e: Exception) {}
            }
            deleteZoneView = null

            bubbleViews.values.forEach { view ->
                try {
                    view.cleanup()
                    windowManager?.removeView(view)
                } catch (e: Exception) {}
            }
            bubbleViews.clear()
            bubbleParams.clear()

            miniChatFlutterView?.let {
                try {
                    it.detachFromFlutterEngine()
                    windowManager?.removeView(it)
                } catch (e: Exception) {}
            }
            miniChatFlutterView = null

            // Keep engine for reuse
            // miniChatEngine = null

            isServiceRunning = false
        } catch (e: Exception) {}

        super.onDestroy()
    }
}