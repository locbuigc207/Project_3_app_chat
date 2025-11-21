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

class MainActivity : FlutterActivity() {
    private val CHANNEL = "chat_bubble_overlay"
    private val EVENT_CHANNEL = "chat_bubble_events"
    private val OVERLAY_PERMISSION_REQUEST = 1001

    private var bubbleClickReceiver: BroadcastReceiver? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ✅ Method Channel - request/check permissions, show/hide bubbles
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

                        if (userId != null) {
                            val intent = Intent(this, ChatBubbleService::class.java).apply {
                                action = ChatBubbleService.ACTION_SHOW_BUBBLE
                                putExtra("userId", userId)
                                putExtra("userName", userName)
                                putExtra("avatarUrl", avatarUrl)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }
                    "hideBubble" -> {
                        val userId = call.argument<String>("userId")
                        if (userId != null) {
                            val intent = Intent(this, ChatBubbleService::class.java).apply {
                                action = ChatBubbleService.ACTION_HIDE_BUBBLE
                                putExtra("userId", userId)
                            }
                            startService(intent)
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }
                    "hideAllBubbles" -> {
                        val intent = Intent(this, ChatBubbleService::class.java).apply {
                            action = ChatBubbleService.ACTION_HIDE_ALL
                        }
                        startService(intent)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        // ✅ Event Channel - listen for bubble clicks
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    setupBubbleClickListener()
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    unsetupBubbleClickListener()
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

    private fun setupBubbleClickListener() {
        bubbleClickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "CHAT_BUBBLE_CLICKED") {
                    val userId = intent.getStringExtra("userId")
                    val userName = intent.getStringExtra("userName")
                    val avatarUrl = intent.getStringExtra("avatarUrl")

                    eventSink?.success(mapOf(
                        "userId" to userId,
                        "userName" to userName,
                        "avatarUrl" to avatarUrl
                    ))
                }
            }
        }

        val filter = IntentFilter("CHAT_BUBBLE_CLICKED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleClickReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(bubbleClickReceiver, filter)
        }
    }

    private fun unsetupBubbleClickListener() {
        bubbleClickReceiver?.let {
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
        unsetupBubbleClickListener()
        super.onDestroy()
    }
}