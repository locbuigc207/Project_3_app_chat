package hust.appchat;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "chat_bubble_overlay";
    private static final int REQUEST_CODE_OVERLAY = 1000;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    switch (call.method) {
                        case "hasPermission":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                result.success(Settings.canDrawOverlays(this));
                            } else {
                                result.success(true);
                            }
                            break;

                        case "requestPermission":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (!Settings.canDrawOverlays(this)) {
                                    Intent intent = new Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:" + getPackageName())
                                    );
                                    startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                                    result.success(false);
                                } else {
                                    result.success(true);
                                }
                            } else {
                                result.success(true);
                            }
                            break;

                        case "showBubble":
                            // Implement bubble display logic here
                            result.success(true);
                            break;

                        case "hideBubble":
                            // Implement bubble hide logic here
                            result.success(true);
                            break;

                        case "hideAllBubbles":
                            // Implement hide all logic here
                            result.success(true);
                            break;

                        default:
                            result.notImplemented();
                            break;
                    }
                });
    }
}