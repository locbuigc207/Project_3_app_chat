// android/app/src/main/kotlin/hust/appchat/MainActivity.kt
// ✅ XIAOMI 14T PRO - ANDROID 16 COMPATIBLE

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
    private val MINI_CHAT_CHANNEL = "mini_chat_overlay"
    private val OVERLAY_PERMISSION_REQUEST = 1001

    private var bubbleClickReceiver: BroadcastReceiver? = null
    private var bubbleMessageReceiver: BroadcastReceiver? = null
    private var eventSink: EventChannel.EventSink? = null
    private var pendingPermissionResult: MethodChannel.Result? = null

    private var receiversRegistered = false

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        BubbleManager.init(this)

        setupMethodChannel(flutterEngine)
        setupEventChannel(flutterEngine)
    }

    private fun setupMethodChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                android.util.Log.d("MainActivity", "📞 Method called: ${call.method}")

                when (call.method) {
                    "requestPermission" -> {
                        requestOverlayPermission(result)
                    }

                    "hasPermission" -> {
                        val hasPermission = checkOverlayPermission()
                        android.util.Log.d("MainActivity", "✅ Has permission: $hasPermission")
                        result.success(hasPermission)
                    }

                    "showBubble" -> {
                        val userId = call.argument<String>("userId")
                        val userName = call.argument<String>("userName")
                        val avatarUrl = call.argument<String>("avatarUrl")
                        val lastMessage = call.argument<String>("lastMessage")

                        if (userId != null && userName != null) {
                            android.util.Log.d("MainActivity", "🎈 Creating bubble for: $userName")

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
                            BubbleManager.removeBubble(this, userId)
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }

                    "hideAllBubbles" -> {
                        val intent = Intent(this, BubbleOverlayService::class.java)
                        stopService(intent)
                        BubbleManager.cleanup()
                        result.success(true)
                    }

                    "showMiniChat" -> {
                        val userId = call.argument<String>("userId")
                        val userName = call.argument<String>("userName")
                        val avatarUrl = call.argument<String>("avatarUrl")

                        if (userId != null && userName != null) {
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
                                android.util.Log.e("MainActivity", "❌ Failed to show mini chat: $e")
                                result.success(false)
                            }
                        } else {
                            result.success(false)
                        }
                    }

                    "hideMiniChat" -> {
                        val intent = Intent(this, BubbleOverlayService::class.java).apply {
                            action = BubbleOverlayService.ACTION_HIDE_MINI_CHAT
                        }

                        try {
                            startService(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            result.success(false)
                        }
                    }

                    else -> {
                        result.notImplemented()
                    }
                }
            }
    }

    private fun setupEventChannel(flutterEngine: FlutterEngine) {
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    setupBubbleListeners()
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    unsetupBubbleListeners()
                }
            })
    }

    // ✅ XIAOMI SPECIFIC: Enhanced permission check
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // ✅ XIAOMI SPECIFIC: Enhanced permission request with fallback
    private fun requestOverlayPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                android.util.Log.d("MainActivity", "📱 Requesting overlay permission (Xiaomi)")

                pendingPermissionResult = result

                try {
                    // ✅ Standard Android permission request
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ Failed to open permission settings: $e")

                    // ✅ XIAOMI FALLBACK: Try MIUI/HyperOS specific settings
                    try {
                        val xiaomiIntent = Intent("miui.intent.action.APP_PERM_EDITOR")
                        xiaomiIntent.putExtra("extra_pkgname", packageName)
                        startActivityForResult(xiaomiIntent, OVERLAY_PERMISSION_REQUEST)
                    } catch (e2: Exception) {
                        android.util.Log.e("MainActivity", "❌ Xiaomi fallback failed: $e2")
                        result.success(false)
                    }
                }
            } else {
                result.success(true)
            }
        } else {
            result.success(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            val hasPermission = checkOverlayPermission()
            android.util.Log.d("MainActivity", "📱 Permission result: $hasPermission")

            pendingPermissionResult?.success(hasPermission)
            pendingPermissionResult = null
        }
    }

    private fun setupBubbleListeners() {
        if (receiversRegistered) return

        bubbleClickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_CLICKED") {
                    val userId = intent.getStringExtra("userId") ?: ""
                    val userName = intent.getStringExtra("userName") ?: ""
                    val avatarUrl = intent.getStringExtra("avatarUrl") ?: ""

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

        bubbleMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_MESSAGE") {
                    val userId = intent.getStringExtra("userId") ?: ""
                    val message = intent.getStringExtra("message") ?: ""

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

        try {
            val clickFilter = IntentFilter("CHAT_BUBBLE_CLICKED")
            val messageFilter = IntentFilter("CHAT_BUBBLE_MESSAGE")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bubbleClickReceiver, clickFilter, Context.RECEIVER_NOT_EXPORTED)
                registerReceiver(bubbleMessageReceiver, messageFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(bubbleClickReceiver, clickFilter)
                registerReceiver(bubbleMessageReceiver, messageFilter)
            }

            receiversRegistered = true
            android.util.Log.d("MainActivity", "✅ Receivers registered (API ${Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error registering receivers: $e")
        }
    }

    private fun unsetupBubbleListeners() {
        if (!receiversRegistered) return

        bubbleClickReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }

        bubbleMessageReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }

        bubbleClickReceiver = null
        bubbleMessageReceiver = null
        receiversRegistered = false
    }

    override fun onResume() {
        super.onResume()
        BubbleManager.onAppResumed(this)
    }

    override fun onPause() {
        super.onPause()
        BubbleManager.onAppPaused()
    }

    override fun onDestroy() {
        unsetupBubbleListeners()
        super.onDestroy()
    }
}