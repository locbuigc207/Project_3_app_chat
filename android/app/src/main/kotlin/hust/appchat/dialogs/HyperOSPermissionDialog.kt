// android/app/src/main/kotlin/hust/appchat/dialogs/HyperOSPermissionDialog.kt
// ✅ Guide users to enable required permissions on HyperOS/MIUI

package hust.appchat.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object HyperOSPermissionDialog {
    private const val TAG = "HyperOSPermission"

    /**
     * Show dialog to guide user through HyperOS setup
     */
    fun show(context: Context) {
        if (!isHyperOS()) {
            Log.d(TAG, "ℹ️ Not HyperOS, skipping")
            return
        }

        AlertDialog.Builder(context)
            .setTitle("⚙️ Setup Required for Chat Bubbles")
            .setMessage(buildMessage())
            .setPositiveButton("Open Settings") { _, _ ->
                openSettings(context)
            }
            .setNegativeButton("Later", null)
            .setNeutralButton("Don't Show Again") { _, _ ->
                markAsShown(context)
            }
            .show()
    }

    /**
     * Check if dialog should be shown
     */
    fun shouldShow(context: Context): Boolean {
        if (!isHyperOS()) return false

        val prefs = context.getSharedPreferences("hyperos_guide", Context.MODE_PRIVATE)
        return !prefs.getBoolean("shown", false)
    }

    /**
     * Mark as shown (don't show again)
     */
    private fun markAsShown(context: Context) {
        val prefs = context.getSharedPreferences("hyperos_guide", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("shown", true).apply()
    }

    /**
     * Build instruction message
     */
    private fun buildMessage(): String {
        return """
            For Chat Bubbles to work on HyperOS/MIUI, you need to:
            
            1️⃣ Disable Battery Optimization
               Settings → Battery → App battery saver
               Find this app → No restrictions
            
            2️⃣ Enable Auto-start
               Settings → Apps → Manage apps
               Find this app → Auto-start → ON
            
            3️⃣ Lock app in Recent Apps
               Open Recent Apps
               Swipe down on this app → Tap lock icon
            
            These steps prevent the system from killing the app.
        """.trimIndent()
    }

    /**
     * Open app settings
     */
    private fun openSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open settings: $e")
        }
    }

    /**
     * Detect if running on HyperOS/MIUI
     */
    private fun isHyperOS(): Boolean {
        return try {
            val miui = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                    Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
                    Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                    Build.BRAND.equals("Redmi", ignoreCase = true)

            if (miui) {
                Log.d(TAG, "✅ Detected Xiaomi/Redmi device (HyperOS/MIUI)")
            }

            miui
        } catch (e: Exception) {
            false
        }
    }
}