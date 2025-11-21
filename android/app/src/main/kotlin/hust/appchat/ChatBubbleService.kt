package hust.appchat

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import kotlin.math.abs

class ChatBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private val activeBubbles = mutableMapOf<String, BubbleViewHolder>()
    private var closeReceiver: BroadcastReceiver? = null

    data class BubbleViewHolder(
        val containerView: View,
        val params: WindowManager.LayoutParams,
        val userId: String,
        val userName: String,
        val avatarUrl: String
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        // ✅ Register receiver để handle close bubble
        setupCloseReceiver()
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
            startForeground(NOTIFICATION_ID, createNotification())
        }

        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> {
                val userId = intent.getStringExtra("userId") ?: return START_NOT_STICKY
                val userName = intent.getStringExtra("userName") ?: ""
                val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""
                showBubble(userId, userName, avatarUrl)
            }
            ACTION_HIDE_BUBBLE -> {
                val userId = intent.getStringExtra("userId") ?: return START_NOT_STICKY
                hideBubble(userId)
            }
            ACTION_HIDE_ALL -> {
                hideAllBubbles()
            }
        }
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
        if (activeBubbles.containsKey(userId)) return

        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val containerView = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    120, // width
                    120  // height
                )
            }

            // ✅ Main bubble view
            val bubbleView = inflater.inflate(R.layout.chat_bubble_layout, null)
            val avatarView = bubbleView.findViewById<ImageView>(R.id.bubble_avatar)
            val closeButton = bubbleView.findViewById<View>(R.id.bubble_close_button)

            // Load avatar
            if (avatarUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bubble_background)
                    .error(R.drawable.bubble_background)
                    .into(avatarView)
            }

            // ✅ Close button handler
            closeButton?.setOnClickListener {
                hideBubble(userId)
            }

            // ✅ Bubble click - broadcast to Flutter
            bubbleView.setOnClickListener {
                sendBubbleClickEvent(userId, userName, avatarUrl)
            }

            // Add to container
            containerView.addView(bubbleView)

            // Window parameters
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                120,
                120,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 200
            }

            // ✅ Setup drag listener
            setupDragListener(containerView, params)

            windowManager?.addView(containerView, params)

            // Store reference
            activeBubbles[userId] = BubbleViewHolder(
                containerView, params, userId, userName, avatarUrl
            )

            updateNotification()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun hideBubble(userId: String) {
        activeBubbles[userId]?.let { holder ->
            try {
                windowManager?.removeView(holder.containerView)
                activeBubbles.remove(userId)
                updateNotification()

                if (activeBubbles.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideAllBubbles() {
        activeBubbles.values.forEach { holder ->
            try {
                windowManager?.removeView(holder.containerView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeBubbles.clear()
        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun sendBubbleClickEvent(userId: String, userName: String, avatarUrl: String) {
        val intent = Intent("CHAT_BUBBLE_CLICKED").apply {
            putExtra("userId", userId)
            putExtra("userName", userName)
            putExtra("avatarUrl", avatarUrl)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        try {
            closeReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        hideAllBubbles()
        super.onDestroy()
    }
}