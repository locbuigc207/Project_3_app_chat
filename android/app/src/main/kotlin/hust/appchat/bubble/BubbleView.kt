// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt - COMPLETE
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
import hust.appchat.R
import kotlin.math.abs

/**
 * Custom view cho bubble - giống Messenger/Zalo
 *
 * Features:
 * - Drag & drop với smooth animation
 * - Snap to edge tự động
 * - Unread badge
 * - Delete zone detection
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

    // Touch tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var lastMessage: String = ""

    companion object {
        private const val DELETE_ZONE_HEIGHT = 150
        private const val SNAP_ANIMATION_DURATION = 300L
        private const val TOUCH_SLOP = 10
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.chat_bubble_layout, this, true)

        avatarImageView = findViewById(R.id.bubble_avatar)
        unreadBadge = findViewById(R.id.bubble_unread_badge)
        onlineIndicator = findViewById(R.id.bubble_online_indicator)

        // Load avatar với Glide
        loadAvatar()

        // Setup touch listener
        setupTouchListener()

        android.util.Log.d("BubbleView", "✅ Bubble created for: $userName")
    }

    private fun loadAvatar() {
        if (avatarUrl.isNotEmpty()) {
            Glide.with(context)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.bubble_background)
                .error(R.drawable.bubble_background)
                .into(avatarImageView)
        }
    }

    /**
     * Setup drag & drop functionality
     */
    private fun setupTouchListener() {
        setOnTouchListener { view, event ->
            val params = layoutParams as WindowManager.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false

                    // Scale down animation
                    animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .start()

                    true
                }

                MotionEvent.ACTION_MOVE -> {
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
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (e: Exception) {
                            android.util.Log.e("BubbleView", "Error updating layout: $e")
                        }

                        // Check delete zone
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

                        // Visual feedback for delete zone
                        if (inDeleteZone) {
                            alpha = 0.5f
                            scaleX = 0.8f
                            scaleY = 0.8f
                        } else {
                            alpha = 1f
                            scaleX = 0.9f
                            scaleY = 0.9f
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
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
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Snap bubble to nearest edge with smooth animation
     */
    fun snapToEdge(screenWidth: Int) {
        val params = layoutParams as? WindowManager.LayoutParams ?: return
        val centerX = params.x + width / 2

        val targetX = if (centerX < screenWidth / 2) {
            20 // Snap to left edge
        } else {
            screenWidth - width - 20 // Snap to right edge
        }

        // Smooth animation to target position
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_ANIMATION_DURATION
            interpolator = OvershootInterpolator()

            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int

                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                try {
                    windowManager.updateViewLayout(this@BubbleView, params)
                } catch (e: Exception) {
                    android.util.Log.e("BubbleView", "Error during snap animation: $e")
                    cancel()
                }
            }

            start()
        }
    }

    /**
     * Update unread count badge
     */
    fun updateUnreadCount(count: Int) {
        post {
            if (count > 0) {
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                unreadBadge.visibility = View.GONE
            }
        }
    }

    /**
     * Update last message
     */
    fun updateLastMessage(message: String) {
        lastMessage = message
    }

    /**
     * Animate when new message arrives
     */
    fun animateNewMessage() {
        post {
            // "Pop" effect
            animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(150)
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }

    /**
     * Animate deletion with fade out
     */
    fun animateDelete(onComplete: () -> Unit) {
        animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(200)
            .withEndAction(onComplete)
            .start()
    }

    /**
     * Set drag listener
     */
    fun setOnDragListener(listener: (Boolean) -> Unit) {
        this.onDragListener = listener
    }

    /**
     * Set online status
     */
    fun setOnlineStatus(isOnline: Boolean) {
        onlineIndicator.visibility = if (isOnline) View.VISIBLE else View.GONE
    }
}