// android/app/src/main/kotlin/hust/appchat/MainActivity.kt - COMPLETE FIXED
package hust.appchat

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import hust.appchat.bubble.BubbleManager
import hust.appchat.bubble.BubbleOverlayService

class MainActivity : FlutterActivity() {
    private val CHANNEL = "chat_bubble_overlay"
    private val EVENT_CHANNEL = "chat_bubble_events"
    private val OVERLAY_PERMISSION_REQUEST = 1001

    private var bubbleClickReceiver: BroadcastReceiver? = null
    private var bubbleMessageReceiver: BroadcastReceiver? = null
    private var eventSink: EventChannel.EventSink? = null

    // ✅ NEW: Track permission request result
    private var pendingPermissionResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize BubbleManager
        BubbleManager.init(this)

        // ✅ Setup Method Channel
        setupMethodChannel(flutterEngine)

        // ✅ Setup Event Channel
        setupEventChannel(flutterEngine)
    }

    // ===========================================
    // METHOD CHANNEL SETUP
    // ===========================================
    private fun setupMethodChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                android.util.Log.d("MainActivity", "📞 Method called: ${call.method}")

                when (call.method) {
                    "requestPermission" -> {
                        requestOverlayPermission(result)
                    }

                    "hasPermission" -> {
                        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Settings.canDrawOverlays(this)
                        } else {
                            true
                        }
                        android.util.Log.d("MainActivity", "✅ Has permission: $hasPermission")
                        result.success(hasPermission)
                    }

                    "showBubble" -> {
                        val userId = call.argument<String>("userId")
                        val userName = call.argument<String>("userName")
                        val avatarUrl = call.argument<String>("avatarUrl")
                        val lastMessage = call.argument<String>("lastMessage")

                        if (userId != null && userName != null) {
                            android.util.Log.d(
                                "MainActivity",
                                "🎈 Creating bubble for: $userName"
                            )

                            BubbleManager.showBubble(
                                this,
                                userId,
                                userName,
                                avatarUrl ?: "",
                                lastMessage
                            )
                            result.success(true)
                        } else {
                            android.util.Log.e("MainActivity", "❌ Missing userId or userName")
                            result.success(false)
                        }
                    }

                    "hideBubble" -> {
                        val userId = call.argument<String>("userId")
                        if (userId != null) {
                            android.util.Log.d("MainActivity", "🫧 Hiding bubble: $userId")
                            BubbleManager.removeBubble(this, userId)
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }

                    "hideAllBubbles" -> {
                        android.util.Log.d("MainActivity", "🗑️ Hiding all bubbles")
                        val intent = Intent(this, BubbleOverlayService::class.java)
                        stopService(intent)
                        BubbleManager.cleanup()
                        result.success(true)
                    }

                    // ✅ NEW: Show Mini Chat
                    "showMiniChat" -> {
                        val userId = call.argument<String>("userId")
                        val userName = call.argument<String>("userName")
                        val avatarUrl = call.argument<String>("avatarUrl")

                        if (userId != null && userName != null) {
                            android.util.Log.d(
                                "MainActivity",
                                "💬 Opening mini chat for: $userName"
                            )

                            val intent = Intent(this, BubbleOverlayService::class.java).apply {
                                action = BubbleOverlayService.ACTION_SHOW_MINI_CHAT
                                putExtra("userId", userId)
                                putExtra("userName", userName)
                                putExtra("avatarUrl", avatarUrl ?: "")
                            }

                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                                result.success(true)
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "❌ Failed to show mini chat: $e"
                                )
                                result.success(false)
                            }
                        } else {
                            result.success(false)
                        }
                    }

                    // ✅ NEW: Hide Mini Chat
                    "hideMiniChat" -> {
                        android.util.Log.d("MainActivity", "🔚 Hiding mini chat")

                        val intent = Intent(this, BubbleOverlayService::class.java).apply {
                            action = BubbleOverlayService.ACTION_HIDE_MINI_CHAT
                        }

                        try {
                            startService(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "❌ Failed to hide mini chat: $e")
                            result.success(false)
                        }
                    }

                    else -> {
                        android.util.Log.w("MainActivity", "⚠️ Unknown method: ${call.method}")
                        result.notImplemented()
                    }
                }
            }
    }

    // ===========================================
    // EVENT CHANNEL SETUP
    // ===========================================
    private fun setupEventChannel(flutterEngine: FlutterEngine) {
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    android.util.Log.d("MainActivity", "📡 Event channel listening")
                    eventSink = events
                    setupBubbleListeners()
                }

                override fun onCancel(arguments: Any?) {
                    android.util.Log.d("MainActivity", "📡 Event channel cancelled")
                    eventSink = null
                    unsetupBubbleListeners()
                }
            })
    }

    // ===========================================
    // PERMISSION HANDLING
    // ===========================================
    private fun requestOverlayPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                android.util.Log.d("MainActivity", "📱 Requesting overlay permission")

                pendingPermissionResult = result

                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            } else {
                android.util.Log.d("MainActivity", "✅ Permission already granted")
                result.success(true)
            }
        } else {
            result.success(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }

            android.util.Log.d(
                "MainActivity",
                "📱 Permission result: $hasPermission"
            )

            pendingPermissionResult?.success(hasPermission)
            pendingPermissionResult = null
        }
    }

    // ===========================================
    // BROADCAST RECEIVERS
    // ===========================================
    private fun setupBubbleListeners() {
        // ✅ Bubble click listener
        bubbleClickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_CLICKED") {
                    val userId = intent.getStringExtra("userId")
                    val userName = intent.getStringExtra("userName")
                    val avatarUrl = intent.getStringExtra("avatarUrl")

                    android.util.Log.d(
                        "MainActivity",
                        "🫧 Bubble clicked: $userName"
                    )

                    eventSink?.success(
                        mapOf(
                            "type" to "click",
                            "userId" to userId,
                            "userName" to userName,
                            "avatarUrl" to avatarUrl
                        )
                    )
                }
            }
        }

        // ✅ Mini chat message listener
        bubbleMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_MESSAGE") {
                    val userId = intent.getStringExtra("userId")
                    val message = intent.getStringExtra("message")

                    android.util.Log.d(
                        "MainActivity",
                        "💬 Mini chat message from $userId: $message"
                    )

                    eventSink?.success(
                        mapOf(
                            "type" to "message",
                            "userId" to userId,
                            "message" to message
                        )
                    )
                }
            }
        }

        // Register receivers
        val clickFilter = IntentFilter("CHAT_BUBBLE_CLICKED")
        val messageFilter = IntentFilter("CHAT_BUBBLE_MESSAGE")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleClickReceiver, clickFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(bubbleMessageReceiver, messageFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(bubbleClickReceiver, clickFilter)
            registerReceiver(bubbleMessageReceiver, messageFilter)
        }

        android.util.Log.d("MainActivity", "✅ Broadcast receivers registered")
    }

    private fun unsetupBubbleListeners() {
        bubbleClickReceiver?.let {
            try {
                unregisterReceiver(it)
                android.util.Log.d("MainActivity", "✅ Click receiver unregistered")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "⚠️ Error unregistering click receiver: $e")
            }
        }

        bubbleMessageReceiver?.let {
            try {
                unregisterReceiver(it)
                android.util.Log.d("MainActivity", "✅ Message receiver unregistered")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "⚠️ Error unregistering message receiver: $e")
            }
        }

        bubbleClickReceiver = null
        bubbleMessageReceiver = null
    }

    // ===========================================
    // LIFECYCLE
    // ===========================================
    override fun onDestroy() {
        unsetupBubbleListeners()
        android.util.Log.d("MainActivity", "🛑 MainActivity destroyed")
        super.onDestroy()
    }
}