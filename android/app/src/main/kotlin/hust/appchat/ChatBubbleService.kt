package hust.appchat

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide

class ChatBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private val activeBubbles = mutableMapOf<String, View>()

    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "HIDE_BUBBLE"
        const val ACTION_HIDE_ALL = "HIDE_ALL"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "chat_bubble_service"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service for Android O+
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun showBubble(userId: String, userName: String, avatarUrl: String) {
        if (activeBubbles.containsKey(userId)) return

        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val bubbleView = inflater.inflate(R.layout.chat_bubble_layout, null)

            // Setup views
            val avatarView = bubbleView.findViewById<ImageView>(R.id.bubble_avatar)
            val nameView = bubbleView.findViewById<TextView>(R.id.bubble_name)

            nameView?.visibility = View.GONE // Hide name by default

            if (avatarUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bubble_background)
                    .error(R.drawable.bubble_background)
                    .into(avatarView)
            }

            // Window parameters
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }

            // Drag functionality
            setupDragListener(bubbleView, params)

            // Click to broadcast
            bubbleView.setOnClickListener {
                sendBubbleClickEvent(userId)
            }

            windowManager?.addView(bubbleView, params)
            activeBubbles[userId] = bubbleView

            // Update notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Detect click vs drag
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    if (deltaX < 10 && deltaY < 10) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun hideBubble(userId: String) {
        activeBubbles[userId]?.let { view ->
            try {
                windowManager?.removeView(view)
                activeBubbles.remove(userId)

                // Update notification
                if (activeBubbles.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideAllBubbles() {
        activeBubbles.values.forEach { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeBubbles.clear()
        stopForeground(true)
        stopSelf()
    }

    private fun sendBubbleClickEvent(userId: String) {
        val intent = Intent("CHAT_BUBBLE_CLICKED").apply {
            putExtra("userId", userId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        hideAllBubbles()
        super.onDestroy()
    }
}