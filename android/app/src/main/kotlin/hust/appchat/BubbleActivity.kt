// android/app/src/main/kotlin/hust/appchat/BubbleActivity.kt
package hust.appchat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel

/**
 * ✅ GIAI ĐOẠN 5: Bubble Activity với Flutter Content
 *
 * Thay thế cho FlutterMiniChatActivity và WindowManager-based overlays.
 *
 * Tính năng:
 * - Render Flutter content trong Bubble API notification.
 * - Hỗ trợ Android 11+ (API 30+).
 * - Shared Flutter Engine với main app.
 * - MethodChannel để giao tiếp với Flutter.
 * - Auto-navigate đến ChatPage khi mở bubble.
 * - Handle lifecycle properly.
 * - Support minimize/close actions.
 */
@RequiresApi(Build.VERSION_CODES.R)
class BubbleActivity : FlutterActivity() {

    companion object {
        private const val TAG = "BubbleActivity"

        // ✅ SHARED ENGINE: Reuse với main app để tránh khởi động lại
        private const val ENGINE_ID = "bubble_chat_engine"

        // ✅ CHANNEL: Giao tiếp với Flutter
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

                // ✅ Flags cho bubble activity
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }
    }

    // ========================================
    // STATE
    // ========================================
    private var methodChannel: MethodChannel? = null
    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentAvatarUrl: String? = null

    private var isFlutterReady = false

    // ========================================
    // LIFECYCLE
    // ========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "✅ onCreate: BubbleActivity initialized")

        // ✅ Extract user info from intent hoặc restored state
        if (savedInstanceState == null) {
            currentUserId = intent.getStringExtra(EXTRA_USER_ID)
            currentUserName = intent.getStringExtra(EXTRA_USER_NAME)
            currentAvatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)
        } else {
            // Will be restored in onRestoreInstanceState
        }

        Log.d(TAG, "📋 User: $currentUserName (ID: $currentUserId)")

        // ✅ Validate required data
        if (currentUserId.isNullOrEmpty() || currentUserName.isNullOrEmpty()) {
            Log.e(TAG, "❌ Missing required user data, finishing activity")
            finish()
            return
        }
    }

    // ========================================
    // FLUTTER ENGINE SETUP
    // ========================================

    override fun provideFlutterEngine(context: Context): FlutterEngine? {
        // ✅ CRITICAL: Reuse existing engine hoặc tạo mới
        var engine = FlutterEngineCache.getInstance().get(ENGINE_ID)

        if (engine == null) {
            Log.d(TAG, "🔧 Creating new Flutter Engine for bubble")

            engine = FlutterEngine(context)

            // ✅ Execute Dart entrypoint
            engine.dartExecutor.executeDartEntrypoint(
                DartExecutor.DartEntrypoint.createDefault()
            )

            // ✅ Cache engine cho lần sau
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine)

            Log.d(TAG, "✅ Flutter Engine created and cached")
        } else {
            Log.d(TAG, "♻️ Reusing existing Flutter Engine")
        }

        return engine
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        Log.d(TAG, "🔧 Configuring Flutter Engine for Bubble")

        // ✅ FIX 8: Clear FLAG_NOT_FOCUSABLE to allow keyboard input
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        // ✅ FIX 8: Ensure window can receive input
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        // ✅ Setup MethodChannel
        setupMethodChannel(flutterEngine)

        // ✅ Wait for Flutter to be ready, then send data
        // Delay ngắn để đảm bảo Flutter UI đã render
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                isFlutterReady = true
                sendInitialDataToFlutter()

                // ✅ FIX 8: Show keyboard after data is sent
                showKeyboard()
            }
        }, 800)
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

            // ✅ Handle calls FROM Flutter
            methodChannel?.setMethodCallHandler { call, result ->
                Log.d(TAG, "📞 Method called from Flutter: ${call.method}")

                when (call.method) {
                    "minimize" -> {
                        Log.d(TAG, "📦 Minimize bubble")
                        // Move to back but keep bubble alive
                        moveTaskToBack(true)
                        result.success(true)
                    }

                    "close" -> {
                        Log.d(TAG, "❌ Close bubble")
                        // Finish activity và dismiss bubble
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

                    "getBubbleMode" -> {
                        // Flutter checks if running in bubble
                        result.success(true)
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

    // ========================================
    // SEND DATA TO FLUTTER
    // ========================================

    private fun sendInitialDataToFlutter() {
        if (currentUserId.isNullOrEmpty() || currentUserName.isNullOrEmpty()) {
            Log.w(TAG, "⚠️ Cannot send data: missing user info")
            return
        }

        if (!isFlutterReady) {
            Log.w(TAG, "⚠️ Flutter not ready yet")
            return
        }

        try {
            Log.d(TAG, "📤 Sending initial data to Flutter")

            // ✅ CRITICAL: Invoke method để navigate đến ChatPage
            methodChannel?.invokeMethod(
                "navigateToChat",
                mapOf(
                    "peerId" to currentUserId!!,
                    "peerNickname" to currentUserName!!,
                    "peerAvatar" to (currentAvatarUrl ?: ""),
                    "isBubbleMode" to true // ✅ Tell Flutter we're in bubble (từ phần 2)
                )
            )

            Log.d(TAG, "✅ Initial data sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send initial data: $e")

            // Retry after short delay (từ phần 2)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isFinishing) {
                    sendInitialDataToFlutter()
                }
            }, 500)
        }
    }

    // ========================================
    // ✅ FIX 8: KEYBOARD MANAGEMENT
    // ========================================

    /**
     * Show keyboard for input
     */
    private fun showKeyboard() {
        try {
            // Request focus on the window
            window.decorView.requestFocus()

            // Show soft keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)

            Log.d(TAG, "⌨️ Keyboard show requested")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show keyboard: $e")
        }
    }

    /**
     * Hide keyboard
     */
    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)

            Log.d(TAG, "⌨️ Keyboard hidden")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to hide keyboard: $e")
        }
    }

    // ========================================
    // LIFECYCLE CALLBACKS
    // ========================================

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "▶️ onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ onResume")

        // Re-send data if Flutter was paused/resumed (từ phần 2)
        if (isFlutterReady && currentUserId != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isFinishing) {
                    sendInitialDataToFlutter()
                }
            }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ onPause")

        // ✅ FIX 9: Hide keyboard when pausing
        hideKeyboard()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "⏹️ onStop")
    }

    override fun onDestroy() {
        Log.d(TAG, "💥 onDestroy")

        // ✅ FIX 9: Hide keyboard before cleanup
        hideKeyboard()

        // Cleanup
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        isFlutterReady = false

        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "🔄 onNewIntent")

        // ✅ Update user info if changed (e.g., switching between conversations)
        val newUserId = intent.getStringExtra(EXTRA_USER_ID)
        val newUserName = intent.getStringExtra(EXTRA_USER_NAME)
        val newAvatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)

        if (newUserId != null && newUserId != currentUserId) {
            Log.d(TAG, "🔄 Switching user: $currentUserName -> $newUserName")

            currentUserId = newUserId
            currentUserName = newUserName
            currentAvatarUrl = newAvatarUrl

            // Re-send data to Flutter
            if (isFlutterReady) {
                sendInitialDataToFlutter()
            }
        }
    }

    // ========================================
    // ✅ FIX 9: SAVE/RESTORE STATE
    // ========================================

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString("userId", currentUserId)
        outState.putString("userName", currentUserName)
        outState.putString("avatarUrl", currentAvatarUrl)

        Log.d(TAG, "💾 State saved")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        currentUserId = savedInstanceState.getString("userId")
        currentUserName = savedInstanceState.getString("userName")
        currentAvatarUrl = savedInstanceState.getString("avatarUrl")

        Log.d(TAG, "📦 State restored")

        // Re-initialize if needed
        if (isFlutterReady && currentUserId != null) {
            sendInitialDataToFlutter()
        }
    }

    // ========================================
    // BACK PRESS HANDLING
    // ========================================

    override fun onBackPressed() {
        Log.d(TAG, "⬅️ Back pressed - minimizing to bubble")

        // Don't finish, just minimize
        moveTaskToBack(true)
    }
}