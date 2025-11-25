// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt
package hust.appchat.bubble

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
 * Custom view cho bubble - tương tự Messenger/Zalo
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

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    companion object {
        private const val DELETE_ZONE_HEIGHT = 150
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.chat_bubble_layout, this, true)

        avatarImageView = findViewById(R.id.bubble_avatar)
        unreadBadge = findViewById(R.id.bubble_unread_badge)
        onlineIndicator = findViewById(R.id.bubble_online_indicator)

        // Load avatar
        if (avatarUrl.isNotEmpty()) {
            Glide.with(context)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.bubble_background)
                .into(avatarImageView)
        }

        setupTouchListener()
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

                    // Scale down slightly
                    animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true

                        params.x = (initialX + deltaX).toInt()
                        params.y = (initialY + deltaY).toInt()

                        // Update view
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        windowManager.updateViewLayout(this, params)

                        // Check if in delete zone
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

                        if (inDeleteZone) {
                            // Visual feedback
                            alpha = 0.5f
                            scaleX = 0.8f
                            scaleY = 0.8f
                        } else {
                            alpha = 1f
                            scaleX = 0.95f
                            scaleY = 0.95f
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
                        .setDuration(200)
                        .start()

                    if (isDragging) {
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

                        onDragListener?.invoke(inDeleteZone)

                        if (!inDeleteZone) {
                            // Snap to edge if not deleted
                            snapToEdge(context.resources.displayMetrics.widthPixels)
                        }
                    } else {
                        // Quick tap - perform click
                        performClick()
                    }

                    true
                }

                else -> false
            }
        }
    }

    /**
     * Snap to nearest edge with animation
     */
    fun snapToEdge(screenWidth: Int) {
        val params = layoutParams as WindowManager.LayoutParams
        val centerX = params.x + width / 2

        val targetX = if (centerX < screenWidth / 2) {
            20 // Left edge
        } else {
            screenWidth - width - 20 // Right edge
        }

        // Smooth animation
        val animator = android.animation.ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 300
        animator.interpolator = OvershootInterpolator()
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                windowManager.updateViewLayout(this, params)
            } catch (e: Exception) {
                // View removed
            }
        }
        animator.start()
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
     * Update last message (không hiển thị, chỉ lưu trữ)
     */
    fun updateLastMessage(message: String) {
        // Store for later use if needed
    }

    /**
     * Animate when new message arrives
     */
    fun animateNewMessage() {
        post {
            // "Pop" effect
            animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
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
     * Animate deletion
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
}