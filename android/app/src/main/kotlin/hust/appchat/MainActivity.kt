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

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize BubbleManager
        BubbleManager.init(this)

        // Method Channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
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
                        result.success(hasPermission)
                    }
                    "showBubble" -> {
                        val userId = call.argument<String>("userId")
                        val userName = call.argument<String>("userName")
                        val avatarUrl = call.argument<String>("avatarUrl")
                        val message = call.argument<String>("message")

                        if (userId != null && userName != null) {
                            BubbleManager.showBubble(
                                this,
                                userId,
                                userName,
                                avatarUrl ?: "",
                                message
                            )
                            result.success(true)
                        } else {
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
                        // Stop the service completely
                        val intent = Intent(this, BubbleOverlayService::class.java)
                        stopService(intent)
                        BubbleManager.cleanup()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        // Event Channel
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

    private fun requestOverlayPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                result.success(false)
            } else {
                result.success(true)
            }
        } else {
            result.success(true)
        }
    }

    private fun setupBubbleListeners() {
        // Bubble click listener
        bubbleClickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_CLICKED") {
                    val userId = intent.getStringExtra("userId")
                    val userName = intent.getStringExtra("userName")
                    val avatarUrl = intent.getStringExtra("avatarUrl")

                    eventSink?.success(mapOf(
                        "type" to "click",
                        "userId" to userId,
                        "userName" to userName,
                        "avatarUrl" to avatarUrl
                    ))
                }
            }
        }

        // Mini chat message listener
        bubbleMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_MESSAGE") {
                    val userId = intent.getStringExtra("userId")
                    val message = intent.getStringExtra("message")

                    eventSink?.success(mapOf(
                        "type" to "message",
                        "userId" to userId,
                        "message" to message
                    ))
                }
            }
        }

        val clickFilter = IntentFilter("CHAT_BUBBLE_CLICKED")
        val messageFilter = IntentFilter("CHAT_BUBBLE_MESSAGE")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleClickReceiver, clickFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(bubbleMessageReceiver, messageFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(bubbleClickReceiver, clickFilter)
            registerReceiver(bubbleMessageReceiver, messageFilter)
        }
    }

    private fun unsetupBubbleListeners() {
        bubbleClickReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        bubbleMessageReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            // Permission result handled by system
        }
    }

    override fun onDestroy() {
        unsetupBubbleListeners()
        super.onDestroy()
    }
}