package hust.appchat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "chat_bubble_overlay"
    private val OVERLAY_PERMISSION_REQUEST = 1001

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "requestPermission" -> {
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // Permission granted
                }
            }
        }
    }
}