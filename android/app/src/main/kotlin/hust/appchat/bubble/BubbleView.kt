// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt - COMPLETE TOUCH FIX
package hust.appchat.bubble

import android.animation.ValueAnimator
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.R
import kotlin.math.abs
import kotlin.math.sqrt

class BubbleView(
    context: Context,
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String
) : FrameLayout(context) {

    private val avatarImageView: ImageView
    private val unreadBadge: TextView
    private val onlineIndicator: View
    private val deleteIndicator: ImageView

    private val screenWidth: Int
    private val screenHeight: Int
    private val bubbleSize = 64

    // Listeners
    private var onDragListener: ((Boolean, Float, Float) -> Unit)? = null
    private var onDragEndListener: (() -> Unit)? = null
    private var onClickListener: (() -> Unit)? = null

    private var isDragging = false
    private var isDetached = false
    private var isInDeleteZone = false

    // ✅ CRITICAL: Proper touch tracking
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var touchStartTime = 0L
    private var hasMoved = false

    private var lastMessage: String = ""
    private var currentUnreadCount = 0

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    companion object {
        private const val DELETE_ZONE_HEIGHT = 150
        private const val TOUCH_SLOP = 15
        private const val CLICK_TIMEOUT = 300L
        private const val BUBBLE_SCALE_DOWN = 0.92f
        private const val BUBBLE_SCALE_DELETE = 0.75f
        private const val DELETE_ZONE_ALPHA = 0.6f
        private const val HAPTIC_SNAP_DURATION = 10L
        private const val HAPTIC_DELETE_DURATION = 50L
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.chat_bubble_layout, this, true)

        avatarImageView = findViewById(R.id.bubble_avatar)
        unreadBadge = findViewById(R.id.bubble_unread_badge)
        onlineIndicator = findViewById(R.id.bubble_online_indicator)
        deleteIndicator = findViewById(R.id.delete_indicator)

        // ✅ CRITICAL: Enable interaction
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        android.util.Log.d("BubbleView", "📱 Screen: ${screenWidth}x${screenHeight}")

        loadAvatar()
        setupTouchListener()

        android.util.Log.d("BubbleView", "✅ Bubble created: $userName")
    }

    private fun loadAvatar() {
        if (isDetached) return

        try {
            val requestOptions = RequestOptions()
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.bubble_background)
                .error(R.drawable.bubble_background)
                .override(100, 100)

            if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(avatarUrl)
                    .apply(requestOptions)
                    .into(avatarImageView)
            } else {
                avatarImageView.setImageResource(R.drawable.bubble_background)
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleView", "❌ Avatar load error: $e")
            avatarImageView.setImageResource(R.drawable.bubble_background)
        }
    }

    // ✅ CRITICAL: COMPLETE TOUCH FIX
    private fun setupTouchListener() {
        setOnTouchListener { view, event ->
            if (isDetached) return@setOnTouchListener false

            // ✅ Always consume touch events
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleTouchDown(event)
                    true // Consume
                }
                MotionEvent.ACTION_MOVE -> {
                    handleTouchMove(event)
                    true // Consume
                }
                MotionEvent.ACTION_UP -> {
                    handleTouchUp(event)
                    true // Consume
                }
                MotionEvent.ACTION_CANCEL -> {
                    resetVisuals()
                    true // Consume
                }
                else -> false
            }
        }
    }

    private fun handleTouchDown(event: MotionEvent) {
        initialTouchX = event.x
        initialTouchY = event.y
        lastRawX = event.rawX
        lastRawY = event.rawY
        touchStartTime = System.currentTimeMillis()
        isDragging = false
        hasMoved = false

        // Visual feedback
        animate()
            .scaleX(BUBBLE_SCALE_DOWN)
            .scaleY(BUBBLE_SCALE_DOWN)
            .setDuration(150)
            .start()

        android.util.Log.d("BubbleView", "👆 Touch down: (${event.rawX}, ${event.rawY})")
    }

    private fun handleTouchMove(event: MotionEvent) {
        val deltaX = event.x - initialTouchX
        val deltaY = event.y - initialTouchY
        val distance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())

        // Check if moved beyond threshold
        if (distance > TOUCH_SLOP) {
            hasMoved = true

            if (!isDragging) {
                isDragging = true
                performHapticFeedback(HAPTIC_SNAP_DURATION)
                android.util.Log.d("BubbleView", "🖐️ Drag started")
            }
        }

        if (isDragging) {
            // Calculate delta from last position
            val moveX = event.rawX - lastRawX
            val moveY = event.rawY - lastRawY

            lastRawX = event.rawX
            lastRawY = event.rawY

            // ✅ Notify listener with delta
            onDragListener?.invoke(false, moveX, moveY)

            // Check delete zone
            val inDeleteZone = event.rawY > (screenHeight - DELETE_ZONE_HEIGHT)
            updateDeleteIndicator(inDeleteZone)

            val targetScale = if (inDeleteZone) BUBBLE_SCALE_DELETE else BUBBLE_SCALE_DOWN
            val targetAlpha = if (inDeleteZone) DELETE_ZONE_ALPHA else 1f

            animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .alpha(targetAlpha)
                .setDuration(100)
                .start()
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        val touchDuration = System.currentTimeMillis() - touchStartTime

        // Reset visuals
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150)
            .start()

        hideDeleteIndicator()

        if (isDragging) {
            // Check delete zone
            val inDeleteZone = event.rawY > (screenHeight - DELETE_ZONE_HEIGHT)

            if (inDeleteZone) {
                performHapticFeedback(HAPTIC_DELETE_DURATION)
                onDragListener?.invoke(true, 0f, 0f)
                android.util.Log.d("BubbleView", "🗑️ Bubble deleted")
            } else {
                android.util.Log.d("BubbleView", "🫧 Drag ended")
            }

            // Notify drag end
            onDragEndListener?.invoke()
        } else if (!hasMoved && touchDuration < CLICK_TIMEOUT) {
            // ✅ CLICK DETECTED
            android.util.Log.d("BubbleView", "👆 CLICK detected for: $userName")
            performHapticFeedback(HAPTIC_SNAP_DURATION)
            performClick()
            onClickListener?.invoke()
        }

        isDragging = false
        hasMoved = false
    }

    private fun resetVisuals() {
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150)
            .start()
        hideDeleteIndicator()
        isDragging = false
        hasMoved = false
    }

    private fun performHapticFeedback(duration: Long) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleView", "⚠️ Haptic error: $e")
        }
    }

    override fun performClick(): Boolean {
        android.util.Log.d("BubbleView", "🫧 performClick() called for: $userName")
        super.performClick()
        return true
    }

    private fun updateDeleteIndicator(show: Boolean) {
        if (isInDeleteZone == show) return
        isInDeleteZone = show

        if (show) {
            performHapticFeedback(HAPTIC_SNAP_DURATION)
        }

        deleteIndicator.animate()
            .alpha(if (show) 1f else 0f)
            .scaleX(if (show) 1.3f else 1f)
            .scaleY(if (show) 1.3f else 1f)
            .rotation(if (show) 15f else 0f)
            .setDuration(200)
            .start()
    }

    private fun hideDeleteIndicator() {
        isInDeleteZone = false
        deleteIndicator.animate()
            .alpha(0f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(150)
            .start()
    }

    fun updateUnreadCount(count: Int) {
        if (isDetached) return

        post {
            if (count > 0) {
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = when {
                    count > 99 -> "99+"
                    else -> count.toString()
                }

                if (count > currentUnreadCount) {
                    unreadBadge.animate()
                        .scaleX(1.4f)
                        .scaleY(1.4f)
                        .setDuration(200)
                        .withEndAction {
                            if (!isDetached) {
                                unreadBadge.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                                    .start()
                            }
                        }
                        .start()
                }
            } else {
                unreadBadge.visibility = View.GONE
            }

            currentUnreadCount = count
        }
    }

    fun updateLastMessage(message: String) {
        lastMessage = message
    }

    fun animateNewMessage() {
        if (isDetached) return

        post {
            animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .rotation(10f)
                .setDuration(150)
                .withEndAction {
                    if (!isDetached) {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(200)
                            .start()
                    }
                }
                .start()

            performHapticFeedback(HAPTIC_SNAP_DURATION)
        }
    }

    fun animateDelete(onComplete: () -> Unit) {
        if (isDetached) return

        performHapticFeedback(HAPTIC_DELETE_DURATION)

        animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .rotation(360f)
            .setDuration(300)
            .withEndAction {
                if (!isDetached) {
                    onComplete()
                }
            }
            .start()
    }

    fun setOnlineStatus(isOnline: Boolean) {
        if (isDetached) return

        post {
            if (isOnline) {
                onlineIndicator.visibility = View.VISIBLE
                onlineIndicator.alpha = 0f
                onlineIndicator.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            } else {
                onlineIndicator.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        if (!isDetached) {
                            onlineIndicator.visibility = View.GONE
                        }
                    }
                    .start()
            }
        }
    }

    // Listener setters
    fun setOnDragListener(listener: (Boolean, Float, Float) -> Unit) {
        this.onDragListener = listener
    }

    fun setOnDragEndListener(listener: () -> Unit) {
        this.onDragEndListener = listener
    }

    fun setOnClickListener(listener: () -> Unit) {
        this.onClickListener = listener
    }

    fun getBubbleData(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "userName" to userName,
            "avatarUrl" to avatarUrl,
            "lastMessage" to lastMessage,
            "unreadCount" to currentUnreadCount,
            "timestamp" to System.currentTimeMillis()
        )
    }

    fun cleanup() {
        isDetached = true
        onDragListener = null
        onDragEndListener = null
        onClickListener = null

        try {
            Glide.with(context).clear(avatarImageView)
        } catch (e: Exception) {
            android.util.Log.e("BubbleView", "❌ Glide clear error: $e")
        }
    }

    override fun onDetachedFromWindow() {
        cleanup()
        super.onDetachedFromWindow()
    }
}