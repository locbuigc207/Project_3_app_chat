// android/app/src/main/kotlin/hust/appchat/bubble/MiniChatWindow.kt
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import hust.appchat.R
import hust.appchat.adapter.MiniChatAdapter

/**
 * ✅ COMPLETE FIXED: Mini Chat Window with all features
 *
 * Features:
 * - Real Firebase message sync
 * - Send messages to Flutter via broadcast
 * - Draggable header
 * - Auto-scroll to latest
 * - Proper keyboard handling
 * - Memory leak prevention
 * - Error handling
 */
class MiniChatWindow(
    context: Context,
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String
) : LinearLayout(context) {

    private val firestore = FirebaseFirestore.getInstance()
    private var messageListener: ListenerRegistration? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Views
    private val avatarView: ImageView
    private val nameView: TextView
    private val btnMinimize: ImageView
    private val btnClose: ImageView
    private val recyclerView: RecyclerView
    private val inputField: EditText
    private val btnSend: ImageView
    private val header: View
    private val loadingIndicator: ProgressBar

    // Listeners
    private var onMinimizeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onMessageSentListener: ((String) -> Unit)? = null

    // Message data
    private val messages = mutableListOf<MiniChatAdapter.ChatMessage>()
    private val adapter = MiniChatAdapter(messages)
    private var isLoadingMessages = false
    private var isDetached = false

    init {
        try {
            LayoutInflater.from(context).inflate(R.layout.mini_chat_window, this, true)

            // Initialize views
            avatarView = findViewById(R.id.mini_chat_avatar)
            nameView = findViewById(R.id.mini_chat_name)
            btnMinimize = findViewById(R.id.btn_minimize)
            btnClose = findViewById(R.id.btn_close)
            recyclerView = findViewById(R.id.mini_chat_messages)
            inputField = findViewById(R.id.mini_chat_input)
            btnSend = findViewById(R.id.btn_send)
            header = findViewById(R.id.mini_chat_header)
            loadingIndicator = findViewById(R.id.mini_chat_loading)

            setupUI()
            setupListeners()
            loadMessages()

            android.util.Log.d("MiniChat", "✅ Mini chat initialized for: $userName")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to initialize: $e")
            throw e
        }
    }

    /**
     * ✅ Setup UI components
     */
    private fun setupUI() {
        try {
            // Set user info
            nameView.text = userName

            // Load avatar
            if (avatarUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bubble_background)
                    .error(R.drawable.bubble_background)
                    .into(avatarView)
            } else {
                avatarView.setImageResource(R.drawable.bubble_background)
            }

            // Setup RecyclerView
            val layoutManager = LinearLayoutManager(context).apply {
                reverseLayout = true
                stackFromEnd = false
            }
            recyclerView.layoutManager = layoutManager
            recyclerView.adapter = adapter

            // Show loading
            loadingIndicator.visibility = View.VISIBLE

            android.util.Log.d("MiniChat", "✅ UI setup complete")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup UI: $e")
        }
    }

    /**
     * ✅ Setup all listeners
     */
    private fun setupListeners() {
        try {
            btnMinimize.setOnClickListener {
                android.util.Log.d("MiniChat", "🔽 Minimize clicked")
                onMinimizeListener?.invoke()
            }

            btnClose.setOnClickListener {
                android.util.Log.d("MiniChat", "❌ Close clicked")
                onCloseListener?.invoke()
            }

            btnSend.setOnClickListener {
                sendMessage()
            }

            // Send on enter key
            inputField.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else {
                    false
                }
            }

            // Setup drag
            setupDragListener()

            android.util.Log.d("MiniChat", "✅ Listeners setup complete")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup listeners: $e")
        }
    }

    /**
     * ✅ Make header draggable
     */
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            if (isDetached) return@setOnTouchListener false

            val params = layoutParams as? android.view.WindowManager.LayoutParams
                ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()

                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE)
                            as? android.view.WindowManager
                    try {
                        windowManager?.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        android.util.Log.e("MiniChat", "❌ Failed to update layout: $e")
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * ✅ CRITICAL: Load messages from Firestore
     */
    private fun loadMessages() {
        if (isLoadingMessages || isDetached) return
        isLoadingMessages = true

        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            android.util.Log.e("MiniChat", "❌ Current user ID is null")
            hideLoading()
            return
        }

        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        android.util.Log.d("MiniChat", "📥 Loading messages from: $conversationId")

        try {
            messageListener?.remove()
            messageListener = firestore
                .collection("messages")
                .document(conversationId)
                .collection(conversationId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (isDetached) return@addSnapshotListener

                    if (error != null) {
                        android.util.Log.e("MiniChat", "❌ Listen error: $error")
                        hideLoading()
                        showError("Failed to load messages")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        snapshot.documentChanges.forEach { change ->
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                    handleNewMessage(change.document, currentUserId)
                                }
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    // Handle edited messages if needed
                                }
                                else -> {}
                            }
                        }
                    }

                    hideLoading()
                }

            android.util.Log.d("MiniChat", "✅ Message listener setup")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup listener: $e")
            hideLoading()
            showError("Failed to setup message listener")
        }
    }

    /**
     * ✅ Handle new incoming message
     */
    private fun handleNewMessage(
        document: com.google.firebase.firestore.DocumentSnapshot,
        currentUserId: String
    ) {
        if (isDetached) return

        try {
            val content = document.getString("content") ?: ""
            val type = document.getLong("type")?.toInt() ?: 0
            val idFrom = document.getString("idFrom") ?: ""
            val timestamp = document.getString("timestamp")?.toLongOrNull() ?: 0L

            val message = MiniChatAdapter.ChatMessage(
                id = document.id,
                content = if (type == 0) content else "📷 Image",
                isFromMe = idFrom == currentUserId,
                timestamp = timestamp,
                type = type
            )

            // Check if message already exists
            val existingIndex = messages.indexOfFirst { it.id == message.id }

            mainHandler.post {
                if (isDetached) return@post

                if (existingIndex == -1) {
                    // Add new message at top
                    messages.add(0, message)
                    adapter.notifyItemInserted(0)
                    recyclerView.smoothScrollToPosition(0)

                    android.util.Log.d("MiniChat", "✅ Message added: ${message.content}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error handling message: $e")
        }
    }

    /**
     * ✅ CRITICAL: Send message to Firebase AND Flutter
     */
    private fun sendMessage() {
        if (isDetached) return

        val content = inputField.text.toString().trim()
        if (content.isEmpty()) {
            android.util.Log.w("MiniChat", "⚠️ Empty message, not sending")
            return
        }

        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            showError("User not logged in")
            return
        }

        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        val messageId = System.currentTimeMillis().toString()
        val messageData = hashMapOf(
            "idFrom" to currentUserId,
            "idTo" to userId,
            "timestamp" to messageId,
            "content" to content,
            "type" to 0, // Text message
            "isRead" to false
        )

        android.util.Log.d("MiniChat", "✉️ Sending message: $content")

        // Disable send button temporarily
        btnSend.isEnabled = false

        // 1. Save to Firebase
        firestore
            .collection("messages")
            .document(conversationId)
            .collection(conversationId)
            .document(messageId)
            .set(messageData)
            .addOnSuccessListener {
                if (isDetached) return@addOnSuccessListener

                mainHandler.post {
                    if (!isDetached) {
                        inputField.text.clear()
                        hideKeyboard()
                        btnSend.isEnabled = true

                        android.util.Log.d("MiniChat", "✅ Message saved to Firebase")

                        // 2. Send broadcast to Flutter
                        sendBroadcastToFlutter(content)

                        // 3. Notify listener
                        onMessageSentListener?.invoke(content)

                        // 4. Update conversation
                        updateConversation(conversationId, content)
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MiniChat", "❌ Failed to send message: $e")
                mainHandler.post {
                    if (!isDetached) {
                        btnSend.isEnabled = true
                        showError("Failed to send message")
                    }
                }
            }
    }

    /**
     * ✅ CRITICAL: Send broadcast to Flutter
     */
    private fun sendBroadcastToFlutter(message: String) {
        try {
            val intent = Intent("CHAT_BUBBLE_MESSAGE").apply {
                putExtra("userId", userId)
                putExtra("message", message)
            }
            context.sendBroadcast(intent)

            android.util.Log.d("MiniChat", "📡 Broadcast sent to Flutter")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to send broadcast: $e")
        }
    }

    /**
     * ✅ Update conversation last message
     */
    private fun updateConversation(conversationId: String, lastMessage: String) {
        try {
            firestore
                .collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "lastMessage" to lastMessage,
                        "lastMessageTime" to System.currentTimeMillis().toString(),
                        "lastMessageType" to 0
                    )
                )
                .addOnFailureListener { e ->
                    android.util.Log.e("MiniChat", "⚠️ Failed to update conversation: $e")
                }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error updating conversation: $e")
        }
    }

    /**
     * ✅ Show error message
     */
    private fun showError(message: String) {
        mainHandler.post {
            if (!isDetached) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * ✅ Hide loading indicator
     */
    private fun hideLoading() {
        mainHandler.post {
            if (!isDetached) {
                loadingIndicator.visibility = View.GONE
                isLoadingMessages = false
            }
        }
    }

    /**
     * ✅ Hide keyboard
     */
    private fun hideKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(inputField.windowToken, 0)
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "⚠️ Error hiding keyboard: $e")
        }
    }

    /**
     * ✅ Set minimize listener
     */
    fun setOnMinimizeListener(listener: () -> Unit) {
        onMinimizeListener = listener
    }

    /**
     * ✅ Set close listener
     */
    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    /**
     * ✅ Set message sent listener
     */
    fun setOnMessageSentListener(listener: (String) -> Unit) {
        onMessageSentListener = listener
    }

    /**
     * ✅ CRITICAL: Cleanup resources
     */
    fun cleanup() {
        isDetached = true

        try {
            messageListener?.remove()
            messageListener = null
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "⚠️ Error removing listener: $e")
        }

        mainHandler.removeCallbacksAndMessages(null)

        try {
            Glide.with(context).clear(avatarView)
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "⚠️ Error clearing Glide: $e")
        }

        onMinimizeListener = null
        onCloseListener = null
        onMessageSentListener = null

        messages.clear()

        android.util.Log.d("MiniChat", "🧹 Cleanup complete")
    }

    override fun onDetachedFromWindow() {
        cleanup()
        super.onDetachedFromWindow()
    }
}