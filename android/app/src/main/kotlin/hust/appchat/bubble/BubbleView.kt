// android/app/src/main/kotlin/hust/appchat/bubble/BubbleView.kt
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
 * ✅ FIXED: BubbleView with proper click handling
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
    private var isDragging = false
    private var isDetached = false
    private var isInDeleteZone = false

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
        deleteIndicator = findViewById(R.id.delete_indicator)

        // ✅ CRITICAL: Make view clickable
        isClickable = true
        isFocusable = true

        loadAvatar()
        setupTouchListener()

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
                    handleTouchUp(params)
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
        isDragging = false

        currentAnimator?.cancel()

        animate()
            .scaleX(BUBBLE_SCALE_DOWN)
            .scaleY(BUBBLE_SCALE_DOWN)
            .setDuration(100)
            .start()
    }

    private fun handleTouchMove(params: WindowManager.LayoutParams, event: MotionEvent) {
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY

        if (!isDragging && (abs(deltaX) > TOUCH_SLOP || abs(deltaY) > TOUCH_SLOP)) {
            isDragging = true
        }

        if (isDragging) {
            params.x = (initialX + deltaX).toInt()
            params.y = (initialY + deltaY).toInt()

            updateLayout(params)

            val screenHeight = context.resources.displayMetrics.heightPixels
            val inDeleteZone = params.y > (screenHeight - DELETE_ZONE_HEIGHT)

            updateDeleteIndicator(inDeleteZone)

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

    private fun handleTouchUp(params: WindowManager.LayoutParams) {
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
                onDragListener?.invoke(true)
            } else {
                snapToEdge(context.resources.displayMetrics.widthPixels)
            }
        } else {
            // ✅ FIXED: Trigger click
            android.util.Log.d("BubbleView", "👆 Tap detected, calling performClick()")
            performClick()
        }

        isDragging = false
    }

    // ✅ CRITICAL: Override performClick for accessibility
    override fun performClick(): Boolean {
        android.util.Log.d("BubbleView", "🫧 performClick() called")
        super.performClick()
        return true
    }

    private fun updateDeleteIndicator(show: Boolean) {
        if (isInDeleteZone == show) return
        isInDeleteZone = show

        deleteIndicator.animate()
            .alpha(if (show) 1f else 0f)
            .scaleX(if (show) 1.2f else 1f)
            .scaleY(if (show) 1.2f else 1f)
            .setDuration(150)
            .start()
    }

    private fun hideDeleteIndicator() {
        isInDeleteZone = false
        deleteIndicator.animate()
            .alpha(0f)
            .scaleX(1f)
            .scaleY(1f)
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

        val deadZoneStart = screenWidth * 0.35
        val deadZoneEnd = screenWidth * 0.65

        val targetX = when {
            centerX < deadZoneStart -> 20
            centerX > deadZoneEnd -> screenWidth - width - 20
            centerX < screenWidth / 2 -> 20
            else -> screenWidth - width - 20
        }

        currentAnimator?.cancel()

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

    fun updateLastMessage(message: String) {
        lastMessage = message
    }

    fun animateNewMessage() {
        if (isDetached) return

        post {
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

    fun setOnDragListener(listener: (Boolean) -> Unit) {
        this.onDragListener = listener
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