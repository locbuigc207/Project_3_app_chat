package hust.appchat

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.sqrt

class ChatBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private val activeBubbles = mutableMapOf<String, BubbleViewHolder>()
    private var activeMiniChat: MiniChatHolder? = null
    private var closeReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private val deleteZoneHeight = 150 // Height of delete zone at bottom

    data class BubbleViewHolder(
        val containerView: View,
        val params: WindowManager.LayoutParams,
        val userId: String,
        val userName: String,
        val avatarUrl: String
    )

    data class MiniChatHolder(
        val containerView: View,
        val params: WindowManager.LayoutParams,
        val userId: String
    )

    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "HIDE_BUBBLE"
        const val ACTION_HIDE_ALL = "HIDE_ALL"
        const val ACTION_CLOSE_BUBBLE = "CLOSE_BUBBLE"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "chat_bubble_service"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                android.util.Log.e("ChatBubbleService", "❌ WindowManager is null")
                stopSelf()
                return
            }

            // Get screen dimensions
            val displayMetrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

        } catch (e: Exception) {
            android.util.Log.e("ChatBubbleService", "❌ Error getting WindowManager: $e")
            stopSelf()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        setupCloseReceiver()
        android.util.Log.d("ChatBubbleService", "✅ Service created")
    }

    private fun setupCloseReceiver() {
        closeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val userId = intent?.getStringExtra("userId") ?: return
                hideBubble(userId)
            }
        }

        val filter = IntentFilter(ACTION_CLOSE_BUBBLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, createNotification())
                android.util.Log.d("ChatBubbleService", "✅ Foreground service started")
            } catch (e: Exception) {
                android.util.Log.e("ChatBubbleService", "❌ Failed to start foreground: $e")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        mainHandler.postDelayed({
            when (intent?.action) {
                ACTION_SHOW_BUBBLE -> {
                    val userId = intent.getStringExtra("userId") ?: return@postDelayed
                    val userName = intent.getStringExtra("userName") ?: ""
                    val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""

                    android.util.Log.d("ChatBubbleService", "🎈 Showing bubble for: $userName")
                    showBubble(userId, userName, avatarUrl)
                }
                ACTION_HIDE_BUBBLE -> {
                    val userId = intent.getStringExtra("userId") ?: return@postDelayed
                    hideBubble(userId)
                }
                ACTION_HIDE_ALL -> {
                    hideAllBubbles()
                }
            }
        }, 300)

        return START_STICKY
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
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(this)
        }

        return builder
            .setContentTitle("Chat Bubbles Active")
            .setContentText("${activeBubbles.size} bubble(s) active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun showBubble(userId: String, userName: String, avatarUrl: String) {
        if (windowManager == null) {
            android.util.Log.e("ChatBubbleService", "❌ WindowManager is null")
            return
        }

        // Remove existing bubble first
        if (activeBubbles.containsKey(userId)) {
            android.util.Log.d("ChatBubbleService", "ℹ️ Removing existing bubble")
            hideBubble(userId)
            Thread.sleep(200)
        }

        try {
            val themedContext = ContextThemeWrapper(
                applicationContext,
                android.R.style.Theme_Material_Light
            )

            val inflater = LayoutInflater.from(themedContext)
            val bubbleView = inflater.inflate(R.layout.chat_bubble_layout, null)

            val avatarView = bubbleView.findViewById<ImageView>(R.id.bubble_avatar)
            val closeButton = bubbleView.findViewById<View>(R.id.bubble_close_button)

            // Load avatar
            if (avatarUrl.isNotEmpty()) {
                try {
                    Glide.with(applicationContext)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bubble_background)
                        .error(R.drawable.bubble_background)
                        .into(avatarView)
                } catch (e: Exception) {
                    android.util.Log.e("ChatBubbleService", "❌ Glide error: $e")
                    avatarView.setImageResource(R.drawable.bubble_background)
                }
            }

            // Bubble click - show mini chat
            bubbleView.setOnClickListener {
                android.util.Log.d("ChatBubbleService", "👆 Bubble clicked: $userName")
                showMiniChat(userId, userName, avatarUrl)
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // ✅ Start at right edge
                x = screenWidth - 80
                y = 200 + (activeBubbles.size * 80)
            }

            setupBubbleDragListener(bubbleView, params, userId, closeButton)

            mainHandler.post {
                try {
                    windowManager?.addView(bubbleView, params)

                    activeBubbles[userId] = BubbleViewHolder(
                        bubbleView, params, userId, userName, avatarUrl
                    )

                    // ✅ Snap to edge animation
                    snapToEdge(bubbleView, params)

                    updateNotification()
                    android.util.Log.d("ChatBubbleService", "✅ Bubble added for: $userName")
                } catch (e: Exception) {
                    android.util.Log.e("ChatBubbleService", "❌ Failed to add bubble: $e")
                    e.printStackTrace()
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("ChatBubbleService", "❌ Error creating bubble: $e")
            e.printStackTrace()
        }
    }

    // ✅ Advanced drag with snap to edge and delete zone
    private fun setupBubbleDragListener(
        view: View,
        params: WindowManager.LayoutParams,
        userId: String,
        closeButton: View
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var dragStartTime = 0L

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    dragStartTime = System.currentTimeMillis()

                    // Show close button hint
                    closeButton.visibility = View.VISIBLE
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true
                        params.x = (initialX + deltaX).toInt()
                        params.y = (initialY + deltaY).toInt()

                        try {
                            windowManager?.updateViewLayout(view, params)

                            // ✅ Check if in delete zone
                            val inDeleteZone = params.y > (screenHeight - deleteZoneHeight)
                            if (inDeleteZone) {
                                view.alpha = 0.5f
                                view.scaleX = 0.8f
                                view.scaleY = 0.8f
                            } else {
                                view.alpha = 1f
                                view.scaleX = 1f
                                view.scaleY = 1f
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatBubbleService", "❌ Error updating: $e")
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    closeButton.visibility = View.GONE

                    if (!isDragging) {
                        // Quick tap - show mini chat
                        view.performClick()
                    } else {
                        // ✅ Check if dropped in delete zone
                        val inDeleteZone = params.y > (screenHeight - deleteZoneHeight)

                        if (inDeleteZone) {
                            // Delete with animation
                            view.animate()
                                .alpha(0f)
                                .scaleX(0f)
                                .scaleY(0f)
                                .setDuration(200)
                                .withEndAction {
                                    hideBubble(userId)
                                }
                                .start()
                        } else {
                            // ✅ Snap to nearest edge
                            snapToEdge(view, params)
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    // ✅ Snap bubble to nearest edge with animation
    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val centerX = params.x + view.width / 2
        val targetX = if (centerX < screenWidth / 2) {
            20 // Left edge
        } else {
            screenWidth - view.width - 20 // Right edge
        }

        // Animate to edge
        val startX = params.x
        val animator = android.animation.ValueAnimator.ofInt(startX, targetX)
        animator.duration = 300
        animator.interpolator = OvershootInterpolator()
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                // View might be removed
            }
        }
        animator.start()
    }

    // ✅ Show mini chat window
    private fun showMiniChat(userId: String, userName: String, avatarUrl: String) {
        if (windowManager == null) return

        // Hide existing mini chat
        activeMiniChat?.let {
            try {
                windowManager?.removeView(it.containerView)
            } catch (e: Exception) {}
        }

        try {
            val themedContext = ContextThemeWrapper(
                applicationContext,
                android.R.style.Theme_Material_Light
            )

            val inflater = LayoutInflater.from(themedContext)
            val miniChatView = inflater.inflate(R.layout.mini_chat_window, null)

            // Setup header
            val avatarView = miniChatView.findViewById<ImageView>(R.id.mini_chat_avatar)
            val nameView = miniChatView.findViewById<TextView>(R.id.mini_chat_name)
            val btnMinimize = miniChatView.findViewById<ImageView>(R.id.btn_minimize)
            val btnClose = miniChatView.findViewById<ImageView>(R.id.btn_close)
            val btnSend = miniChatView.findViewById<ImageView>(R.id.btn_send)
            val inputField = miniChatView.findViewById<EditText>(R.id.mini_chat_input)

            nameView.text = userName

            if (avatarUrl.isNotEmpty()) {
                try {
                    Glide.with(applicationContext)
                        .load(avatarUrl)
                        .circleCrop()
                        .into(avatarView)
                } catch (e: Exception) {}
            }

            // Minimize - back to bubble
            btnMinimize.setOnClickListener {
                hideMiniChat()
            }

            // Close - remove bubble completely
            btnClose.setOnClickListener {
                hideMiniChat()
                hideBubble(userId)
            }

            // Send message
            btnSend.setOnClickListener {
                val message = inputField.text.toString().trim()
                if (message.isNotEmpty()) {
                    sendMessage(userId, message)
                    inputField.text.clear()
                }
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
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 100
            }

            // Make header draggable
            val header = miniChatView.findViewById<View>(R.id.mini_chat_header)
            setupMiniChatDragListener(miniChatView, header, params)

            mainHandler.post {
                try {
                    windowManager?.addView(miniChatView, params)

                    activeMiniChat = MiniChatHolder(
                        miniChatView, params, userId
                    )

                    // Hide bubble while mini chat is open
                    activeBubbles[userId]?.let { bubble ->
                        bubble.containerView.visibility = View.GONE
                    }

                    android.util.Log.d("ChatBubbleService", "✅ Mini chat opened")
                } catch (e: Exception) {
                    android.util.Log.e("ChatBubbleService", "❌ Failed to show mini chat: $e")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("ChatBubbleService", "❌ Error creating mini chat: $e")
        }
    }

    private fun setupMiniChatDragListener(
        view: View,
        dragHandle: View,
        params: WindowManager.LayoutParams
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (initialTouchX - event.rawX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()

                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun hideMiniChat() {
        activeMiniChat?.let { miniChat ->
            try {
                windowManager?.removeView(miniChat.containerView)

                // Show bubble again
                activeBubbles[miniChat.userId]?.let { bubble ->
                    bubble.containerView.visibility = View.VISIBLE
                }

                activeMiniChat = null
            } catch (e: Exception) {
                android.util.Log.e("ChatBubbleService", "❌ Error hiding mini chat: $e")
            }
        }
    }

    private fun sendMessage(userId: String, message: String) {
        // ✅ Broadcast message to Flutter
        val intent = Intent("CHAT_BUBBLE_MESSAGE").apply {
            putExtra("userId", userId)
            putExtra("message", message)
            setPackage(packageName)
        }
        sendBroadcast(intent)

        android.util.Log.d("ChatBubbleService", "📤 Message sent: $message")
    }

    private fun hideBubble(userId: String) {
        activeBubbles[userId]?.let { holder ->
            try {
                mainHandler.post {
                    try {
                        windowManager?.removeView(holder.containerView)
                        android.util.Log.d("ChatBubbleService", "✅ View removed from window")
                    } catch (e: Exception) {
                        android.util.Log.e("ChatBubbleService", "❌ Error removing view: $e")
                    }
                }
                activeBubbles.remove(userId)

                // Also hide mini chat if open
                if (activeMiniChat?.userId == userId) {
                    hideMiniChat()
                }

                updateNotification()
                android.util.Log.d("ChatBubbleService", "✅ Bubble removed: $userId")

                if (activeBubbles.isEmpty()) {
                    android.util.Log.d("ChatBubbleService", "🛑 No more bubbles, stopping")
                    stopForeground(true)
                    stopSelf()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatBubbleService", "❌ Error in hideBubble: $e")
                activeBubbles.remove(userId)
            }
        }
    }

    private fun hideAllBubbles() {
        activeBubbles.values.forEach { holder ->
            try {
                mainHandler.post {
                    try {
                        windowManager?.removeView(holder.containerView)
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
        }
        activeBubbles.clear()

        hideMiniChat()

        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                android.util.Log.e("ChatBubbleService", "❌ Error updating notification: $e")
            }
        }
    }

    override fun onDestroy() {
        try {
            closeReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            android.util.Log.e("ChatBubbleService", "❌ Error: $e")
        }
        hideAllBubbles()
        super.onDestroy()
    }
}