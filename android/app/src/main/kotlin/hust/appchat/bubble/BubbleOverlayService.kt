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
import androidx.core.app.NotificationCompat
import hust.appchat.R // Đảm bảo tệp R.drawable.bubble_background tồn tại

// KHAI BÁO CLASS/INTERFACE GIẢ ĐỊNH (Cần phải có trong project của bạn)
// interface BubbleView { fun updateUnreadCount(count: Int); fun updateLastMessage(msg: String); fun animateNewMessage(); fun setOnDragListener(...); fun setOnDragEndListener(...); fun animateDelete(onEnd: () -> Unit); fun cleanup(); }
// interface DeleteZoneView { fun show(); fun hide(); }
// object BubbleManager { fun init(context: Context); fun updateBubblePosition(userId: String, x: Int, y: Int); fun removeBubble(context: Context, userId: String); fun markAsRead(context: Context, userId: String); fun onAppPaused(); }


class BubbleOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bubbleViews = mutableMapOf<String, BubbleView>()
    private val bubbleParams = mutableMapOf<String, WindowManager.LayoutParams>()

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
        const val ACTION_RESTORE_BUBBLES = "RESTORE_BUBBLES" // ✅ HÀNH ĐỘNG MỚI CHO PERSISTENCE

        // Action Broadcast cho Flutter (cho Mini Chat Overlay)
        const val BROADCAST_SHOW_MINI_CHAT = "MINI_CHAT_SHOW"
        const val BROADCAST_HIDE_MINI_CHAT = "MINI_CHAT_HIDE"

        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "chat_bubbles"
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

            // Init BubbleManager. Gọi này sẽ trigger restoreBubbles() bên trong BubbleManager
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

        // START_STICKY đảm bảo service tự khởi động lại nếu hệ thống kill nó
        return START_STICKY
    }

    // ✅ NEW: Xử lý khi ứng dụng bị vuốt khỏi recent apps
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        android.util.Log.d("BubbleService", "⚠️ Task removed - preserving bubbles")

        try {
            // 1. Lưu trạng thái hiện tại (bao gồm vị trí)
            BubbleManager.onAppPaused()

            // 2. Khởi động lại service để giữ bubbles sống
            val restartIntent = Intent(applicationContext, BubbleOverlayService::class.java).apply {
                action = ACTION_RESTORE_BUBBLES // Cờ báo hiệu việc khôi phục
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }

            android.util.Log.d("BubbleService", "✅ Service restart scheduled")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ Failed to restart service: $e")
        }
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

                    // ✅ Lưu vị trí vào BubbleManager ngay lập tức (cho trường hợp không bị snap)
                    BubbleManager.updateBubblePosition(userId, positionX, positionY)
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

            ACTION_RESTORE_BUBBLES -> {
                android.util.Log.d("BubbleService", "🔄 Service restarted. Bubbles restoring via BubbleManager.init() in onCreate.")
            }
        }
    }

    // ========================================
    // MINI CHAT Logic (Trigger Flutter Overlay)
    // ========================================
    private fun showMiniChat(userId: String, userName: String, avatarUrl: String) {
        android.util.Log.d("BubbleService", "💬 Triggering Flutter overlay for: $userName")

        // 1. Ẩn Bubble hiện tại (hoặc thu nhỏ tùy theo logic của BubbleView)
        bubbleViews[userId]?.visibility = View.GONE

        // 2. Gửi lệnh Broadcast để Flutter side bắt và hiển thị Overlay
        try {
            val intent = Intent(BROADCAST_SHOW_MINI_CHAT).apply {
                putExtra("peerId", userId)
                putExtra("peerNickname", userName)
                putExtra("peerAvatar", avatarUrl)
            }
            sendBroadcast(intent)
            android.util.Log.d("BubbleService", "✅ Broadcast sent to Flutter")
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ showMiniChat failed: $e")
        }
    }

    private fun hideMiniChat() {
        android.util.Log.d("BubbleService", "📦 Hiding mini chat")

        // Gửi lệnh Broadcast để Flutter side bắt và ẩn Overlay
        try {
            val intent = Intent(BROADCAST_HIDE_MINI_CHAT)
            sendBroadcast(intent)
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "❌ hideMiniChat failed: $e")
        }
    }

    private fun onBubbleClicked(userId: String, userName: String, avatarUrl: String) {
        try {
            // Mở mini chat khi bubble được click
            showMiniChat(userId, userName, avatarUrl)
            BubbleManager.markAsRead(this, userId)

            // Gửi broadcast đến Main App (nếu cần)
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

    // ========================================
    // BUBBLE UI & POSITION LOGIC
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
        android.util.Log.d("BubbleService", "🎈 showBubble: $userName at ($positionX, $positionY)")

        mainHandler.post {
            try {
                // Xóa view cũ trước khi thêm view mới
                bubbleViews[userId]?.let {
                    try {
                        windowManager?.removeView(it)
                    } catch (e: Exception) {}
                }

                // Khởi tạo BubbleView
                val bubbleView = BubbleView(this, userId, userName, avatarUrl)
                bubbleView.updateUnreadCount(unreadCount)
                bubbleView.updateLastMessage(lastMessage)

                bubbleView.setOnClickListener {
                    android.util.Log.d("BubbleService", "🫧 Bubble clicked: $userName")
                    onBubbleClicked(userId, userName, avatarUrl)
                }

                // Xử lý kéo và xóa
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

                            // Giới hạn vị trí trong màn hình
                            params.x = params.x.coerceIn(0, screenWidth - bubbleView.width)
                            params.y = params.y.coerceIn(0, screenHeight - bubbleView.height)

                            try {
                                windowManager?.updateViewLayout(bubbleView, params)
                            } catch (e: Exception) {}
                        }
                    }
                    true
                }

                bubbleView.setOnDragEndListener {
                    hideDeleteZone()
                    isDraggingAnyBubble = false

                    // ✅ LƯU VỊ TRÍ CUỐI CÙNG TRƯỚC KHI SNAP
                    bubbleParams[userId]?.let { params ->
                        BubbleManager.updateBubblePosition(userId, params.x, params.y)
                    }

                    mainHandler.postDelayed({
                        if (bubbleViews.containsKey(userId)) {
                            snapBubbleToEdge(userId)
                        }
                    }, 100)
                }

                // Tham số layout
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
            // ✅ LƯU VỊ TRÍ SAU KHI SNAP (Sử dụng AnimatorListener)
            addListener(object: android.animation.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    BubbleManager.updateBubblePosition(userId, targetX, params.y)
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
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
                // Cần đảm bảo BubbleView có hàm này
                // bubble.animateNewMessage()
            }
        } catch (e: Exception) {}
    }

    private fun hideBubble(userId: String) {
        mainHandler.post {
            try {
                val view = bubbleViews.remove(userId)
                bubbleParams.remove(userId)

                view?.let {
                    // Cần đảm bảo BubbleView có hàm này
                    // it.cleanup()
                    windowManager?.removeView(it)
                }

                if (bubbleViews.isEmpty()) {
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
                        // view.cleanup()
                        windowManager?.removeView(view)
                    } catch (e: Exception) {}
                }

                bubbleViews.clear()
                bubbleParams.clear()

                stopForeground(true)
                stopSelf()
                isServiceRunning = false
            } catch (e: Exception) {}
        }
    }

    // ========================================
    // UI/Notification Setup
    // ========================================

    private fun showDeleteZone() {
        if (deleteZoneView != null) {
            deleteZoneView?.show()
            return
        }

        try {
            // Giả định DeleteZoneView được định nghĩa và implement
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

        val contentText = when {
            bubbleCount > 0 -> "$bubbleCount bubble(s) active"
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
            // ✅ Lưu trạng thái trước khi Service bị hủy
            BubbleManager.onAppPaused()

            deleteZoneView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (e: Exception) {}
            }
            deleteZoneView = null

            bubbleViews.values.forEach { view ->
                try {
                    // view.cleanup()
                    windowManager?.removeView(view)
                } catch (e: Exception) {}
            }
            bubbleViews.clear()
            bubbleParams.clear()

            isServiceRunning = false
        } catch (e: Exception) {}

        super.onDestroy()
    }
}