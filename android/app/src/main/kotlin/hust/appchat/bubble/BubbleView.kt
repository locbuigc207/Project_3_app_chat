// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt - ENHANCED COMPLETE
package hust.appchat.bubble

import android.animation.ValueAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.R
import kotlin.math.abs

/**
 * Enhanced BubbleView with improved features:
 * ✅ Better avatar loading with caching
 * ✅ Smooth animations
 * ✅ Optimized drag performance
 * ✅ Memory leak prevention
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

    private var onDragListener: ((Boolean) -> Unit)? = null
    private var isDragging = false
    private var isDetached = false

    // Touch tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var lastMessage: String = ""
    private var currentUnreadCount = 0

    // Animation state
    private var currentAnimator: ValueAnimator? = null

    companion object {
        private const val DELETE_ZONE_HEIGHT = 150
        private const val SNAP_ANIMATION_DURATION = 300L
        private const val TOUCH_SLOP = 10
        private const val BUBBLE_SCALE_DOWN = 0.9f
        private const val BUBBLE_SCALE_DELETE = 0.8f
        private const val DELETE_ZONE_ALPHA = 0.5f
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.chat_bubble_layout, this, true)

        avatarImageView = findViewById(R.id.bubble_avatar)
        unreadBadge = findViewById(R.id.bubble_unread_badge)
        onlineIndicator = findViewById(R.id.bubble_online_indicator)

        // Load avatar
        loadAvatar()

        // Setup touch listener
        setupTouchListener()

        android.util.Log.d("BubbleView", "✅ Enhanced bubble created for: $userName")
    }

    /**
     * ✅ ENHANCED: Load avatar with better caching and error handling
     */
    private fun loadAvatar() {
        if (isDetached) return

        try {
            val requestOptions = RequestOptions()
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.bubble_background)
                .error(R.drawable.bubble_background)
                .override(100, 100) // Optimize size

            if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(avatarUrl)
                    .apply(requestOptions)
                    .into(avatarImageView)
            } else {
                // Fallback: show first letter of name
                avatarImageView.setImageResource(R.drawable.bubble_background)
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleView", "❌ Failed to load avatar: $e")
            avatarImageView.setImageResource(R.drawable.bubble_background)
        }
    }

    /**
     * ✅ ENHANCED: Improved drag handling with better performance
     */
    private fun setupTouchListener() {
        setOnTouchListener { view, event ->
            if (isDetached) return@setOnTouchListener false

            val params = layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false

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
                    handleTouchUp(params)
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Handle touch down event
     */
    private fun handleTouchDown(params: WindowManager.LayoutParams, event: MotionEvent) {
        initialX = params.x
        initialY = params.y
        initialTouchX = event.rawX
        initialTouchY = event.rawY
        isDragging = false

        // Cancel any ongoing animation
        currentAnimator?.cancel()

        // Scale down animation
        animate()
            .scaleX(BUBBLE_SCALE_DOWN)
            .scaleY(BUBBLE_SCALE_DOWN)
            .setDuration(100)
            .start()
    }

    /**
     * Handle touch move event
     */
    private fun handleTouchMove(params: WindowManager.LayoutParams, event: MotionEvent) {
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY

        // Check if movement exceeds touch slop
        if (!isDragging && (abs(deltaX) > TOUCH_SLOP || abs(deltaY) > TOUCH_SLOP)) {
            isDragging = true
        }

        if (isDragging) {
            params.x = (initialX + deltaX).toInt()
            params.y = (initialY + deltaY).toInt()

            // Update view position
            updateLayout(params)

            // Check delete zone
            val screenHeight = context.resources.displayMetrics.heightPixels
            val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

            // Visual feedback for delete zone
            if (inDeleteZone) {
                alpha = DELETE_ZONE_ALPHA
                scaleX = BUBBLE_SCALE_DELETE
                scaleY = BUBBLE_SCALE_DELETE
            } else {
                alpha = 1f
                scaleX = BUBBLE_SCALE_DOWN
                scaleY = BUBBLE_SCALE_DOWN
            }
        }
    }

    /**
     * Handle touch up event
     */
    private fun handleTouchUp(params: WindowManager.LayoutParams) {
        // Restore scale
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150)
            .start()

        if (isDragging) {
            val screenHeight = context.resources.displayMetrics.heightPixels
            val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

            if (inDeleteZone) {
                // Notify listener to delete
                onDragListener?.invoke(true)
            } else {
                // Snap to edge
                snapToEdge(context.resources.displayMetrics.widthPixels)
            }
        } else {
            // Quick tap - trigger click
            performClick()
        }

        isDragging = false
    }

    /**
     * ✅ ENHANCED: Better layout update with error handling
     */
    private fun updateLayout(params: WindowManager.LayoutParams) {
        if (isDetached) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        try {
            windowManager?.updateViewLayout(this, params)
        } catch (e: Exception) {
            android.util.Log.e("BubbleView", "❌ Error updating layout: $e")
        }
    }

    /**
     * ✅ ENHANCED: Smoother snap animation with better edge detection
     */
    fun snapToEdge(screenWidth: Int) {
        if (isDetached) return

        val params = layoutParams as? WindowManager.LayoutParams ?: return
        val centerX = params.x + width / 2

        // Determine target edge (with 30% dead zone in center)
        val deadZoneStart = screenWidth * 0.35
        val deadZoneEnd = screenWidth * 0.65

        val targetX = when {
            centerX < deadZoneStart -> 20 // Left edge
            centerX > deadZoneEnd -> screenWidth - width - 20 // Right edge
            centerX < screenWidth / 2 -> 20 // Closer to left
            else -> screenWidth - width - 20 // Closer to right
        }

        // Cancel any ongoing animation
        currentAnimator?.cancel()

        // Smooth animation to target position
        currentAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_ANIMATION_DURATION
            interpolator = OvershootInterpolator()

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
    }

    /**
     * ✅ ENHANCED: Better unread count update with animation
     */
    fun updateUnreadCount(count: Int) {
        if (isDetached) return

        post {
            if (count > 0) {
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = if (count > 99) "99+" else count.toString()

                // Animate badge if count increased
                if (count > currentUnreadCount) {
                    unreadBadge.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(150)
                        .withEndAction {
                            if (!isDetached) {
                                unreadBadge.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(150)
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

    /**
     * Update last message
     */
    fun updateLastMessage(message: String) {
        lastMessage = message
    }

    /**
     * ✅ ENHANCED: Better new message animation
     */
    fun animateNewMessage() {
        if (isDetached) return

        post {
            // "Pop" effect with rotation
            animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .rotation(15f)
                .setDuration(150)
                .withEndAction {
                    if (!isDetached) {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(150)
                            .start()
                    }
                }
                .start()
        }
    }

    /**
     * ✅ ENHANCED: Smoother deletion animation
     */
    fun animateDelete(onComplete: () -> Unit) {
        if (isDetached) return

        animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .rotation(360f)
            .setDuration(250)
            .withEndAction {
                if (!isDetached) {
                    onComplete()
                }
            }
            .start()
    }

    /**
     * Set drag listener
     */
    fun setOnDragListener(listener: (Boolean) -> Unit) {
        this.onDragListener = listener
    }

    /**
     * ✅ NEW: Set online status with animation
     */
    fun setOnlineStatus(isOnline: Boolean) {
        if (isDetached) return

        post {
            if (isOnline) {
                onlineIndicator.visibility = View.VISIBLE
                onlineIndicator.alpha = 0f
                onlineIndicator.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } else {
                onlineIndicator.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        if (!isDetached) {
                            onlineIndicator.visibility = View.GONE
                        }
                    }
                    .start()
            }
        }
    }

    /**
     * ✅ NEW: Get bubble data for persistence
     */
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

    /**
     * ✅ NEW: Cleanup resources
     */
    fun cleanup() {
        isDetached = true
        currentAnimator?.cancel()
        currentAnimator = null
        onDragListener = null

        // Clear Glide resources
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