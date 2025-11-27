// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt - COMPLETE
package hust.appchat.bubble

import android.animation.ValueAnimator
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.R
import kotlin.math.abs

/**
 * ✅ COMPLETE: BubbleView with drag end listener
 */
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

    // ✅ All listeners
    private var onDragListener: ((Boolean, Float, Float) -> Unit)? = null
    private var onDragEndListener: (() -> Unit)? = null
    private var onClickListener: (() -> Unit)? = null

    private var isDragging = false
    private var isDetached = false
    private var isInDeleteZone = false

    // Touch tracking
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartTime = 0L

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

        isClickable = true
        isFocusable = true

        loadAvatar()
        setupTouchListener()

        android.util.Log.d("BubbleView", "✅ Bubble created for: $userName")
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
            android.util.Log.e("BubbleView", "❌ Failed to load avatar: $e")
            avatarImageView.setImageResource(R.drawable.bubble_background)
        }
    }

    private fun setupTouchListener() {
        setOnTouchListener { view, event ->
            if (isDetached) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleTouchDown(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleTouchMove(event)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handleTouchUp(event)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTouchDown(event: MotionEvent) {
        initialTouchX = event.rawX
        initialTouchY = event.rawY
        lastTouchX = event.rawX
        lastTouchY = event.rawY
        touchStartTime = System.currentTimeMillis()
        isDragging = false

        // Visual feedback
        animate()
            .scaleX(BUBBLE_SCALE_DOWN)
            .scaleY(BUBBLE_SCALE_DOWN)
            .setDuration(150)
            .start()

        android.util.Log.d("BubbleView", "👆 Touch down at: (${event.rawX}, ${event.rawY})")
    }

    private fun handleTouchMove(event: MotionEvent) {
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY
        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())

        // Start dragging if moved beyond slop
        if (!isDragging && distance > TOUCH_SLOP) {
            isDragging = true
            performHapticFeedback(HAPTIC_SNAP_DURATION)
            android.util.Log.d("BubbleView", "🖐️ Drag started")
        }

        if (isDragging) {
            // Calculate delta from last position
            val moveX = event.rawX - lastTouchX
            val moveY = event.rawY - lastTouchY

            lastTouchX = event.rawX
            lastTouchY = event.rawY

            // Get screen height to check delete zone
            val screenHeight = context.resources.displayMetrics.heightPixels
            val currentY = event.rawY
            val inDeleteZone = currentY > (screenHeight - DELETE_ZONE_HEIGHT)

            // ✅ Notify listener with delta movement
            onDragListener?.invoke(false, moveX, moveY)

            // Visual feedback
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
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY
        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())

        // Reset visuals
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150)
            .start()

        hideDeleteIndicator()

        if (isDragging) {
            // Check if in delete zone
            val screenHeight = context.resources.displayMetrics.heightPixels
            val inDeleteZone = event.rawY > (screenHeight - DELETE_ZONE_HEIGHT)

            if (inDeleteZone) {
                performHapticFeedback(HAPTIC_DELETE_DURATION)
                // ✅ Notify listener to delete
                onDragListener?.invoke(true, 0f, 0f)
                android.util.Log.d("BubbleView", "🗑️ Bubble deleted")
            } else {
                android.util.Log.d("BubbleView", "🫧 Drag ended - snap to edge (handled by Service)")
            }

            // ✅ CRITICAL: Notify drag end
            onDragEndListener?.invoke()
        } else if (touchDuration < CLICK_TIMEOUT && distance < TOUCH_SLOP) {
            // Click detected
            android.util.Log.d("BubbleView", "👆 Click detected")
            performHapticFeedback(HAPTIC_SNAP_DURATION)
            performClick()
            onClickListener?.invoke()
        }

        isDragging = false
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
            android.util.Log.e("BubbleView", "⚠️ Haptic feedback error: $e")
        }
    }

    override fun performClick(): Boolean {
        android.util.Log.d("BubbleView", "🫧 performClick() called")
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

    // ✅ COMPLETE: All listener setters
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
            android.util.Log.e("BubbleView", "❌ Error clearing Glide: $e")
        }
    }

    override fun onDetachedFromWindow() {
        cleanup()
        super.onDetachedFromWindow()
    }
}