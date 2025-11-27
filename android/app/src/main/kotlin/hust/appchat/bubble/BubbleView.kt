// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt - ENHANCED
package hust.appchat.bubble

import android.animation.ValueAnimator
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.R
import kotlin.math.abs
import kotlin.math.pow

/**
 * ✅ ENHANCED: BubbleView với đầy đủ tính năng
 * - Haptic feedback
 * - Smooth animations
 * - Auto-hide timeout
 * - Better drag handling
 * - Screen rotation support
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

    private var onDragListener: ((Boolean) -> Unit)? = null
    private var onClickListener: (() -> Unit)? = null
    private var isDragging = false
    private var isDetached = false
    private var isInDeleteZone = false

    // Touch tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchStartTime = 0L

    private var lastMessage: String = ""
    private var currentUnreadCount = 0

    // Animation state
    private var currentAnimator: ValueAnimator? = null

    // ✅ NEW: Haptic feedback
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // ✅ NEW: Auto-hide timeout
    private var autoHideRunnable: Runnable? = null
    private val AUTO_HIDE_DELAY = 5000L // 5 seconds

    companion object {
        private const val DELETE_ZONE_HEIGHT = 150
        private const val SNAP_ANIMATION_DURATION = 300L
        private const val TOUCH_SLOP = 15 // Increased for better drag detection
        private const val CLICK_TIMEOUT = 300L // Max duration for click
        private const val BUBBLE_SCALE_DOWN = 0.92f
        private const val BUBBLE_SCALE_DELETE = 0.75f
        private const val DELETE_ZONE_ALPHA = 0.6f

        // ✅ NEW: Haptic constants
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

        // ✅ NEW: Start auto-hide timer
        resetAutoHideTimer()

        android.util.Log.d("BubbleView", "✅ Enhanced bubble created for: $userName")
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

    // ✅ NEW: Auto-hide functionality
    private fun resetAutoHideTimer() {
        autoHideRunnable?.let { removeCallbacks(it) }
        autoHideRunnable = Runnable {
            if (!isDetached && !isDragging) {
                animateAutoHide()
            }
        }
        postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
    }

    private fun animateAutoHide() {
        animate()
            .alpha(0.5f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun cancelAutoHide() {
        autoHideRunnable?.let { removeCallbacks(it) }
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun setupTouchListener() {
        setOnTouchListener { view, event ->
            if (isDetached) return@setOnTouchListener false

            val params = layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleTouchDown(params, event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleTouchMove(params, event)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handleTouchUp(params, event)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTouchDown(params: WindowManager.LayoutParams, event: MotionEvent) {
        initialX = params.x
        initialY = params.y
        initialTouchX = event.rawX
        initialTouchY = event.rawY
        touchStartTime = System.currentTimeMillis()
        isDragging = false

        currentAnimator?.cancel()

        // ✅ NEW: Cancel auto-hide
        cancelAutoHide()

        // ✅ IMPROVED: Smoother scale animation
        animate()
            .scaleX(BUBBLE_SCALE_DOWN)
            .scaleY(BUBBLE_SCALE_DOWN)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun handleTouchMove(params: WindowManager.LayoutParams, event: MotionEvent) {
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY
        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())

        // ✅ IMPROVED: Better drag detection
        if (!isDragging && distance > TOUCH_SLOP) {
            isDragging = true
            performHapticFeedback(HAPTIC_SNAP_DURATION)
        }

        if (isDragging) {
            // ✅ IMPROVED: Smooth position update với damping
            params.x = (initialX + deltaX * 0.95f).toInt()
            params.y = (initialY + deltaY * 0.95f).toInt()

            updateLayout(params)

            val screenHeight = context.resources.displayMetrics.heightPixels
            val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

            updateDeleteIndicator(inDeleteZone)

            // ✅ IMPROVED: Smooth scale transition
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

    private fun handleTouchUp(params: WindowManager.LayoutParams, event: MotionEvent) {
        val touchDuration = System.currentTimeMillis() - touchStartTime
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY
        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())

        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150)
            .start()

        hideDeleteIndicator()

        if (isDragging) {
            val screenHeight = context.resources.displayMetrics.heightPixels
            val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

            if (inDeleteZone) {
                performHapticFeedback(HAPTIC_DELETE_DURATION)
                onDragListener?.invoke(true)
            } else {
                snapToEdge(context.resources.displayMetrics.widthPixels)
            }
        } else if (touchDuration < CLICK_TIMEOUT && distance < TOUCH_SLOP) {
            // ✅ IMPROVED: Better click detection
            android.util.Log.d("BubbleView", "👆 Click detected")
            performHapticFeedback(HAPTIC_SNAP_DURATION)
            performClick()
            onClickListener?.invoke()
        }

        isDragging = false

        // ✅ NEW: Restart auto-hide timer
        resetAutoHideTimer()
    }

    // ✅ NEW: Haptic feedback helper
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

    private fun updateLayout(params: WindowManager.LayoutParams) {
        if (isDetached) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        try {
            windowManager?.updateViewLayout(this, params)
        } catch (e: Exception) {
            android.util.Log.e("BubbleView", "❌ Error updating layout: $e")
        }
    }

    fun snapToEdge(screenWidth: Int) {
        if (isDetached) return

        val params = layoutParams as? WindowManager.LayoutParams ?: return
        val centerX = params.x + width / 2

        // ✅ IMPROVED: Better edge detection với magnetic zones
        val leftMagneticZone = screenWidth * 0.3
        val rightMagneticZone = screenWidth * 0.7

        val targetX = when {
            centerX < leftMagneticZone -> 20
            centerX > rightMagneticZone -> screenWidth - width - 20
            centerX < screenWidth / 2 -> 20
            else -> screenWidth - width - 20
        }

        currentAnimator?.cancel()

        currentAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_ANIMATION_DURATION
            interpolator = OvershootInterpolator(1.5f)

            addUpdateListener { animation ->
                if (isDetached) {
                    cancel()
                    return@addUpdateListener
                }

                params.x = animation.animatedValue as Int
                updateLayout(params)
            }

            start()
        }

        performHapticFeedback(HAPTIC_SNAP_DURATION)
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
                    // ✅ IMPROVED: Better bounce animation
                    unreadBadge.animate()
                        .scaleX(1.4f)
                        .scaleY(1.4f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator())
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
            // ✅ IMPROVED: More noticeable animation
            animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .rotation(10f)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator())
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
            .setInterpolator(DecelerateInterpolator())
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

    fun setOnDragListener(listener: (Boolean) -> Unit) {
        this.onDragListener = listener
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
        currentAnimator?.cancel()
        currentAnimator = null
        onDragListener = null
        onClickListener = null
        autoHideRunnable?.let { removeCallbacks(it) }
        autoHideRunnable = null

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