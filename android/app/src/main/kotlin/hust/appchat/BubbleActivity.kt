// android/app/src/main/kotlin/hust/appchat/BubbleActivity.kt
package hust.appchat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel

/**
 * ✅ BUBBLE API Activity
 *
 * Thay thế cho FlutterMiniChatActivity và WindowManager-based overlays
 *
 * Tính năng:
 * - Render Flutter content trong Bubble API notification
 * - Hỗ trợ Android 11+ (API 30+)
 * - Tự động resize và quản lý lifecycle
 * - Tích hợp MethodChannel để giao tiếp với Flutter
 *
 * @since GIAI ĐOẠN 1: Chuẩn bị & Thiết lập cơ bản
 */
@RequiresApi(Build.VERSION_CODES.R)
class BubbleActivity : FlutterActivity() {

    companion object {
        private const val TAG = "BubbleActivity"
        private const val ENGINE_ID = "bubble_chat_engine"
        private const val CHANNEL = "bubble_chat_channel"

        // ✅ Intent extras
        private const val EXTRA_USER_ID = "userId"
        private const val EXTRA_USER_NAME = "userName"
        private const val EXTRA_AVATAR_URL = "avatarUrl"

        /**
         * Tạo Intent để mở BubbleActivity
         */
        fun createIntent(
            context: Context,
            userId: String,
            userName: String,
            avatarUrl: String
        ): Intent {
            return Intent(context, BubbleActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
                putExtra(EXTRA_AVATAR_URL, avatarUrl)

                // ✅ Flags for bubble activity
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }
    }

    private var methodChannel: MethodChannel? = null
    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentAvatarUrl: String? = null

    // ========================================
    // LIFECYCLE OVERRIDES
    // ========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "✅ onCreate: BubbleActivity initialized")

        // ✅ Extract user info from intent
        currentUserId = intent.getStringExtra(EXTRA_USER_ID)
        currentUserName = intent.getStringExtra(EXTRA_USER_NAME)
        currentAvatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)

        Log.d(TAG, "📋 User: $currentUserName (ID: $currentUserId)")

        // ✅ Validate required data
        if (currentUserId == null || currentUserName == null) {
            Log.e(TAG, "❌ Missing required user data, finishing activity")
            finish()
            return
        }
    }

    override fun provideFlutterEngine(context: Context): FlutterEngine? {
        // ✅ Reuse existing engine or create new one
        var engine = FlutterEngineCache.getInstance().get(ENGINE_ID)

        if (engine == null) {
            Log.d(TAG, "🔧 Creating new Flutter Engine")
            engine = FlutterEngine(context)
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine)
        } else {
            Log.d(TAG, "♻️ Reusing existing Flutter Engine")
        }

        return engine
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        Log.d(TAG, "🔧 Configuring Flutter Engine for Bubble")

        // ✅ Setup MethodChannel
        setupMethodChannel(flutterEngine)

        // ✅ Send initial data to Flutter after short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sendInitialDataToFlutter()
        }, 500)
    }

    // ========================================
    // METHOD CHANNEL SETUP
    // ========================================

    private fun setupMethodChannel(flutterEngine: FlutterEngine) {
        try {
            methodChannel = MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                CHANNEL
            )

            methodChannel?.setMethodCallHandler { call, result ->
                Log.d(TAG, "📞 Method called: ${call.method}")

                when (call.method) {
                    "minimize" -> {
                        Log.d(TAG, "📦 Minimize bubble")
                        moveTaskToBack(true)
                        result.success(true)
                    }

                    "close" -> {
                        Log.d(TAG, "❌ Close bubble")
                        finish()
                        result.success(true)
                    }

                    "getUserInfo" -> {
                        Log.d(TAG, "📋 Get user info")
                        result.success(mapOf(
                            "userId" to currentUserId,
                            "userName" to currentUserName,
                            "avatarUrl" to currentAvatarUrl
                        ))
                    }

                    else -> {
                        Log.w(TAG, "⚠️ Unknown method: ${call.method}")
                        result.notImplemented()
                    }
                }
            }

            Log.d(TAG, "✅ MethodChannel setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ MethodChannel setup failed: $e")
        }
    }

    private fun sendInitialDataToFlutter() {
        if (currentUserId == null || currentUserName == null) {
            Log.w(TAG, "⚠️ Cannot send data: missing user info")
            return
        }

        try {
            Log.d(TAG, "📤 Sending initial data to Flutter")

            methodChannel?.invokeMethod(
                "navigateToChat",
                mapOf(
                    "peerId" to currentUserId,
                    "peerNickname" to currentUserName,
                    "peerAvatar" to (currentAvatarUrl ?: "")
                )
            )

            Log.d(TAG, "✅ Initial data sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send initial data: $e")
        }
    }

    // ========================================
    // LIFECYCLE CALLBACKS
    // ========================================

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ onPause")
    }

    override fun onDestroy() {
        Log.d(TAG, "💥 onDestroy")
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "🔄 onNewIntent")

        // ✅ Update user info if changed
        val newUserId = intent.getStringExtra(EXTRA_USER_ID)
        val newUserName = intent.getStringExtra(EXTRA_USER_NAME)
        val newAvatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)

        if (newUserId != null && newUserId != currentUserId) {
            Log.d(TAG, "🔄 Switching user: $currentUserName -> $newUserName")

            currentUserId = newUserId
            currentUserName = newUserName
            currentAvatarUrl = newAvatarUrl

            sendInitialDataToFlutter()
        }
    }

    // ========================================
    // BACK PRESS HANDLING
    // ========================================

    override fun onBackPressed() {
        Log.d(TAG, "⬅️ Back pressed - minimizing to bubble")
        moveTaskToBack(true)
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Check if running in bubble mode
     */
    private fun isInBubbleMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width() <
                    resources.displayMetrics.widthPixels
        } else {
            false
        }
    }
}