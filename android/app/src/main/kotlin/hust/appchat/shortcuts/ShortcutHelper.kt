// android/app/src/main/kotlin/hust/appchat/shortcuts/ShortcutHelper.kt
// ✅ COMPLETE FIXED VERSION - All missing methods added

package hust.appchat.shortcuts

import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import hust.appchat.BubbleActivity
import kotlinx.coroutines.*
import java.io.File

@RequiresApi(Build.VERSION_CODES.R)
object ShortcutHelper {
    private const val TAG = "ShortcutHelper"
    private const val MAX_SHORTCUTS = 5

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Create shortcut with Person using TYPE_URI icon
     */
    fun createShortcut(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "⚠️ Shortcuts require Android 11+")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "🔗 Creating shortcut with Person: $userName")

                val avatarIcon = loadAvatarAsUri(context, avatarUrl, userName, userId)

                val person = Person.Builder()
                    .setName(userName)
                    .setIcon(avatarIcon)
                    .setKey(userId)
                    .setImportant(true)
                    .build()

                createShortcutWithPerson(context, userId, userName, avatarUrl, person)

                Log.d(TAG, "✅ Shortcut created with Person: $userName")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create shortcut: $e")
            }
        }
    }

    /**
     * Load avatar and return as TYPE_URI Icon
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun loadAvatarAsUri(
        context: Context,
        avatarUrl: String,
        userName: String,
        userId: String
    ): Icon = withContext(Dispatchers.IO) {
        try {
            val bitmap = AvatarLoader.loadAvatarIconAsync(
                context = context,
                avatarUrl = avatarUrl,
                userName = userName
            ).loadDrawable(context)?.let { drawable ->
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                val bitmap = android.graphics.Bitmap.createBitmap(
                    width, height, android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }

            if (bitmap == null) {
                Log.w(TAG, "⚠️ Failed to load bitmap, using default")
                return@withContext AvatarLoader.createDefaultAvatarIcon(context, userName)
            }

            val avatarFile = File(context.filesDir, "avatars/$userId.png")
            avatarFile.parentFile?.mkdirs()

            avatarFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.d(TAG, "✅ Avatar saved to: ${avatarFile.absolutePath}")

            val avatarUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                avatarFile
            )

            Log.d(TAG, "✅ Avatar URI created: $avatarUri")

            return@withContext Icon.createWithContentUri(avatarUri)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading avatar as URI: $e")
            return@withContext AvatarLoader.createDefaultAvatarIcon(context, userName)
        }
    }

    /**
     * Create shortcut with Person
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun createShortcutWithPerson(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String,
        person: Person
    ) = withContext(Dispatchers.Main) {
        try {
            val manager = context.getSystemService(ShortcutManager::class.java)

            val currentCount = manager?.dynamicShortcuts?.size ?: 0
            if (currentCount >= MAX_SHORTCUTS) {
                Log.w(TAG, "⚠️ Max shortcuts reached, removing oldest")
                removeOldestShortcut(context)
            }

            val intent = Intent(context, BubbleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("userId", userId)
                putExtra("userName", userName)
                putExtra("avatarUrl", avatarUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val shortcut = ShortcutInfo.Builder(context, userId)
                .setShortLabel(userName)
                .setLongLabel("Chat with $userName")
                .setIcon(person.icon)
                .setIntent(intent)
                .setLongLived(true)
                .setPerson(person)
                .setCategories(setOf("android.app.shortcuts.CONVERSATION"))
                .setRank(0)
                .build()

            manager?.pushDynamicShortcut(shortcut)

            Log.d(TAG, "✅ Shortcut pushed with Person")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create shortcut: $e")
            throw e
        }
    }

    /**
     * Remove shortcut
     */
    fun removeShortcut(context: Context, userId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.removeDynamicShortcuts(listOf(userId))

                val avatarFile = File(context.filesDir, "avatars/$userId.png")
                if (avatarFile.exists()) {
                    avatarFile.delete()
                    Log.d(TAG, "🗑️ Avatar file deleted")
                }

                Log.d(TAG, "✅ Shortcut removed: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove shortcut: $e")
        }
    }

    /**
     * ✅ NEW: Remove all shortcuts
     */
    fun removeAllShortcuts(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.removeAllDynamicShortcuts()

                val avatarsDir = File(context.filesDir, "avatars")
                if (avatarsDir.exists() && avatarsDir.isDirectory) {
                    avatarsDir.listFiles()?.forEach { it.delete() }
                }

                Log.d(TAG, "✅ All shortcuts removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove all shortcuts: $e")
        }
    }

    /**
     * Remove oldest shortcut
     */
    private fun removeOldestShortcut(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val manager = context.getSystemService(ShortcutManager::class.java)
                val shortcuts = manager?.dynamicShortcuts ?: return

                if (shortcuts.isNotEmpty()) {
                    val oldest = shortcuts.maxByOrNull { it.rank }
                    oldest?.let {
                        removeShortcut(context, it.id)
                        Log.d(TAG, "✅ Removed oldest shortcut: ${it.id}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove oldest shortcut: $e")
        }
    }

    /**
     * Check if shortcut exists
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
     * Get shortcut count
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

    /**
     * ✅ NEW: Check if can create more shortcuts
     */
    fun canCreateMoreShortcuts(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val currentCount = getShortcutCount(context)
                currentCount < MAX_SHORTCUTS
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking shortcuts capacity: $e")
            false
        }
    }

    /**
     * Check if device supports shortcuts
     */
    fun isShortcutsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    }

    /**
     * Ensure shortcut exists for notification
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
            delay(500)
        }
    }

    /**
     * ✅ NEW: Refresh shortcut avatar
     */
    fun refreshShortcutAvatar(
        context: Context,
        userId: String,
        userName: String,
        avatarUrl: String
    ) {
        scope.launch {
            try {
                Log.d(TAG, "🔄 Refreshing avatar for shortcut: $userName")

                removeShortcut(context, userId)
                delay(200)
                createShortcut(context, userId, userName, avatarUrl)

                Log.d(TAG, "✅ Shortcut avatar refreshed: $userName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to refresh shortcut avatar: $e")
            }
        }
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        scope.cancel()
        AvatarLoader.clearAllCache()
        Log.d(TAG, "✅ ShortcutHelper cleanup complete")
    }
}