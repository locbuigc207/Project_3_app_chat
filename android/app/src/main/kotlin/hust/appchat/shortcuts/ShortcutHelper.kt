// android/app/src/main/kotlin/hust/appchat/shortcuts/ShortcutHelper.kt
package hust.appchat.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import hust.appchat.BubbleActivity
import hust.appchat.R
import kotlinx.coroutines.*

/**
 * ✅ GIAI ĐOẠN 3: SHORTCUT MANAGER
 *
 * Quản lý shortcuts cho Bubble API:
 * - Tạo dynamic shortcuts cho conversations
 * - Hỗ trợ avatar loading
 * - Integration với launcher
 * - Persistent shortcuts
 */
object ShortcutHelper {
    private const val TAG = "ShortcutHelper"
    private const val MAX_SHORTCUTS = 5

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val avatarCache = mutableMapOf<String, Icon>()

    // ========================================
    // PUBLIC API
    // ========================================

    /**
     * Tạo shortcut cho conversation
     * Required for Bubble API on Android 11+
     */
    fun createShortcut(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Log.w(TAG, "⚠️ Shortcuts require Android 7.1+")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "🔗 Creating shortcut: $userName")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ - Required for Bubble API
                    createModernShortcut(context, userId, userName, avatarUrl)
                } else {
                    // Android 7.1-10 - Fallback
                    createLegacyShortcut(context, userId, userName, avatarUrl)
                }

                Log.d(TAG, "✅ Shortcut created: $userName")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create shortcut: $e")
            }
        }
    }

    /**
     * Xóa shortcut khi conversation bị đóng
     */
    fun removeShortcut(context: Context, userId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.removeDynamicShortcuts(listOf(userId))

                avatarCache.remove(userId) // Clear cache

                Log.d(TAG, "✅ Shortcut removed: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove shortcut: $e")
        }
    }

    /**
     * Xóa tất cả shortcuts
     */
    fun removeAllShortcuts(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.removeAllDynamicShortcuts()

                avatarCache.clear()

                Log.d(TAG, "✅ All shortcuts removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove all shortcuts: $e")
        }
    }

    /**
     * Update shortcut (e.g., when avatar changes)
     */
    fun updateShortcut(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        // Remove old then create new
        removeShortcut(context, userId)
        createShortcut(context, userId, userName, avatarUrl)
    }

    /**
     * Kiểm tra shortcut tồn tại
     */
    fun shortcutExists(context: Context, userId: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.dynamicShortcuts?.any { it.id == userId } ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking shortcut: $e")
            false
        }
    }

    /**
     * Lấy số lượng shortcuts hiện tại
     */
    fun getShortcutCount(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.dynamicShortcuts?.size ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting shortcut count: $e")
            0
        }
    }

    // ========================================
    // MODERN SHORTCUT (Android 11+)
    // ========================================

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun createModernShortcut(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) = withContext(Dispatchers.Main) {
        try {
            val manager = context.getSystemService(ShortcutManager::class.java)

            // Check shortcut limit
            if (getShortcutCount(context) >= MAX_SHORTCUTS) {
                Log.w(TAG, "⚠️ Max shortcuts reached, removing oldest")
                removeOldestShortcut(context)
            }

            val avatarIcon = loadAvatarIcon(context, avatarUrl, userName)
            val person = createPerson(userName, avatarIcon)

            val intent = Intent(context, BubbleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)

                // Flags for shortcut intent
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val shortcut = ShortcutInfo.Builder(context, userId)
                .setShortLabel(userName)
                .setLongLabel("Chat with $userName")
                .setIcon(avatarIcon)
                .setIntent(intent)
                .setLongLived(true) // ✅ Persistent across reboots
                .setPerson(person)
                .setCategories(setOf("android.app.shortcuts.CONVERSATION"))
                .setRank(0) // Higher priority
                .build()

            manager.pushDynamicShortcut(shortcut)

            Log.d(TAG, "✅ Modern shortcut created: $userName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Modern shortcut creation failed: $e")
            throw e
        }
    }

    // ========================================
    // LEGACY SHORTCUT (Android 7.1-10)
    // ========================================

    private suspend fun createLegacyShortcut(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) = withContext(Dispatchers.Main) {
        try {
            val avatarIcon = loadAvatarIconCompat(context, avatarUrl, userName)

            val intent = Intent(context, BubbleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)
            }

            val shortcut = ShortcutInfoCompat.Builder(context, userId)
                .setShortLabel(userName)
                .setLongLabel("Chat with $userName")
                .setIcon(avatarIcon)
                .setIntent(intent)
                .setLongLived(true)
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

            Log.d(TAG, "✅ Legacy shortcut created: $userName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Legacy shortcut creation failed: $e")
            throw e
        }
    }

    // ========================================
    // PERSON BUILDER
    // ========================================

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createPerson(name: String, avatarIcon: Icon): android.app.Person {
        return android.app.Person.Builder()
            .setName(name)
            .setIcon(avatarIcon)
            .setImportant(true)
            .build()
    }

    // ========================================
    // AVATAR LOADING
    // ========================================

    private suspend fun loadAvatarIcon(
        context: Context,
        avatarUrl: String,
        userName: String
    ): Icon = withContext(Dispatchers.IO) {

        // Check cache
        avatarCache[avatarUrl]?.let {
            Log.d(TAG, "📦 Using cached avatar: $userName")
            return@withContext it
        }

        try {
            val bitmap = if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .asBitmap()
                    .load(avatarUrl)
                    .apply(
                        RequestOptions()
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(100, 100)
                    )
                    .submit()
                    .get()
            } else {
                createDefaultAvatar(context, userName)
            }

            val icon = Icon.createWithBitmap(bitmap)

            // Cache it
            avatarCache[avatarUrl] = icon

            Log.d(TAG, "✅ Avatar loaded: $userName")
            return@withContext icon

        } catch (e: Exception) {
            Log.e(TAG, "❌ Avatar load failed, using default: $e")
            return@withContext Icon.createWithBitmap(
                createDefaultAvatar(context, userName)
            )
        }
    }

    private suspend fun loadAvatarIconCompat(
        context: Context,
        avatarUrl: String,
        userName: String
    ): IconCompat = withContext(Dispatchers.IO) {

        try {
            val bitmap = if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .asBitmap()
                    .load(avatarUrl)
                    .apply(
                        RequestOptions()
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(100, 100)
                    )
                    .submit()
                    .get()
            } else {
                createDefaultAvatar(context, userName)
            }

            return@withContext IconCompat.createWithBitmap(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Avatar load failed: $e")
            return@withContext IconCompat.createWithBitmap(
                createDefaultAvatar(context, userName)
            )
        }
    }

    private fun createDefaultAvatar(context: Context, userName: String): Bitmap {
        val size = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background circle
        val paint = Paint().apply {
            color = Color.parseColor("#2196F3")
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Initial letter
        val initial = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        paint.apply {
            color = Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
        }

        val textY = (size / 2f) - ((paint.descent() + paint.ascent()) / 2)
        canvas.drawText(initial, size / 2f, textY, paint)

        return bitmap
    }

    // ========================================
    // SHORTCUT MANAGEMENT
    // ========================================

    private fun removeOldestShortcut(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                val shortcuts = manager?.dynamicShortcuts ?: return

                if (shortcuts.isNotEmpty()) {
                    // Remove lowest rank (oldest)
                    val oldest = shortcuts.maxByOrNull { it.rank }
                    oldest?.let {
                        manager.removeDynamicShortcuts(listOf(it.id))
                        Log.d(TAG, "✅ Removed oldest shortcut: ${it.id}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove oldest shortcut: $e")
        }
    }

    /**
     * Lấy danh sách shortcuts hiện tại
     */
    fun getShortcuts(context: Context): List<ShortcutInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.dynamicShortcuts ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting shortcuts: $e")
            emptyList()
        }
    }

    /**
     * Kiểm tra xem device có hỗ trợ shortcuts không
     */
    fun isShortcutsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    }

    /**
     * Kiểm tra xem có thể tạo thêm shortcut không
     */
    fun canCreateMoreShortcuts(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                val currentCount = manager?.dynamicShortcuts?.size ?: 0
                currentCount < MAX_SHORTCUTS
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking shortcut capacity: $e")
            false
        }
    }

    // ========================================
    // ✅ SYNC: Shortcut-Notification Integration
    // ========================================

    /**
     * Đảm bảo shortcut luôn sync với notification
     */
    suspend fun ensureShortcutForNotification(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        if (!shortcutExists(context, userId)) {
            Log.d(TAG, "🔗 Creating missing shortcut for notification")
            createShortcut(context, userId, userName, avatarUrl)
        } else {
            Log.d(TAG, "✅ Shortcut already exists for notification")
        }
    }

    /**
     * ✅ BATCH: Create shortcuts for multiple users
     */
    suspend fun createShortcutsBatch(
        context: Context,
        users: List<Triple<String, String, String>> // userId, userName, avatarUrl
    ) = withContext(Dispatchers.IO) {
        users.forEach { (userId, userName, avatarUrl) ->
            try {
                createShortcut(context, userId, userName, avatarUrl)
                delay(100) // Avoid overwhelming system
            } catch (e: Exception) {
                Log.e(TAG, "❌ Batch shortcut creation failed for $userName: $e")
            }
        }
    }

    // ========================================
    // CLEANUP
    // ========================================

    fun clearCache() {
        avatarCache.clear()
        Log.d(TAG, "✅ Avatar cache cleared")
    }

    fun cleanup() {
        scope.cancel()
        clearCache()
        Log.d(TAG, "✅ ShortcutHelper cleanup complete")
    }
}