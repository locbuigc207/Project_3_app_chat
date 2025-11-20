package hust.appchat

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class ChatBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val activeBubbles = mutableMapOf<String, View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

    private fun showBubble(userId: String, userName: String, avatarUrl: String) {
        if (activeBubbles.containsKey(userId)) return

        if (windowManager == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.chat_bubble_layout, null)

        // Setup bubble view
        val avatarView = bubbleView?.findViewById<ImageView>(R.id.bubble_avatar)
        val nameView = bubbleView?.findViewById<TextView>(R.id.bubble_name)

        nameView?.text = userName
        if (avatarUrl.isNotEmpty()) {
            Glide.with(this).load(avatarUrl).circleCrop().into(avatarView!!)
        }

        // Window parameters
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
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

        // Add drag functionality
        setupDragListener(bubbleView!!, params!!)

        // Click to open mini chat
        bubbleView?.setOnClickListener {
            // Notify Flutter to show mini chat
            sendBubbleClickEvent(userId)
        }

        try {
            windowManager?.addView(bubbleView, params)
            activeBubbles[userId] = bubbleView!!
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
                else -> false
            }
        }
    }

    private fun hideBubble(userId: String) {
        activeBubbles[userId]?.let { view ->
            try {
                windowManager?.removeView(view)
                activeBubbles.remove(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (activeBubbles.isEmpty()) {
            stopSelf()
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

    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "HIDE_BUBBLE"
        const val ACTION_HIDE_ALL = "HIDE_ALL"
    }
}






