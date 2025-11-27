// android/app/src/main/kotlin/hust/appchat/bubble/MiniChatWindow.kt
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
 * ✅ COMPLETE: Mini Chat Window với tất cả requirements
 *
 * Features:
 * 1. ✅ WindowManager overlay (TYPE_APPLICATION_OVERLAY)
 * 2. ✅ Draggable header (OnTouchListener)
 * 3. ✅ Send messages (Firebase + broadcast to Flutter)
 * 4. ✅ Receive messages realtime (Firestore snapshot listener)
 * 5. ✅ Auto-scroll to new messages
 * 6. ✅ Loading/Empty/Error states
 * 7. ✅ Retry mechanism
 * 8. ✅ Proper input focus handling
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

    // ===== Views =====
    private val avatarView: ImageView
    private val nameView: TextView
    private val btnMinimize: ImageView
    private val btnClose: ImageView
    private val recyclerView: RecyclerView
    private val inputField: EditText
    private val btnSend: ImageView
    private val header: View

    // State Views
    private val loadingIndicator: ProgressBar
    private val emptyStateView: LinearLayout
    private val errorStateView: LinearLayout
    private val errorText: TextView
    private val btnRetry: Button
    private val typingIndicator: LinearLayout
    private val sendLoading: ProgressBar

    // ===== Listeners =====
    private var onMinimizeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onMessageSentListener: ((String) -> Unit)? = null

    // ===== State =====
    private val messages = mutableListOf<MiniChatAdapter.ChatMessage>()
    private val adapter = MiniChatAdapter(messages)
    private var isLoadingMessages = false
    private var isSendingMessage = false
    private var isDetached = false

    private var retryCount = 0
    private val maxRetries = 3

    init {
        try {
            LayoutInflater.from(context).inflate(R.layout.mini_chat_window, this, true)

            // Initialize all views
            avatarView = findViewById(R.id.mini_chat_avatar)
            nameView = findViewById(R.id.mini_chat_name)
            btnMinimize = findViewById(R.id.btn_minimize)
            btnClose = findViewById(R.id.btn_close)
            recyclerView = findViewById(R.id.mini_chat_messages)
            inputField = findViewById(R.id.mini_chat_input)
            btnSend = findViewById(R.id.btn_send)
            header = findViewById(R.id.mini_chat_header)

            loadingIndicator = findViewById(R.id.mini_chat_loading)
            emptyStateView = findViewById(R.id.mini_chat_empty_state)
            errorStateView = findViewById(R.id.mini_chat_error_state)
            errorText = findViewById(R.id.mini_chat_error_text)
            btnRetry = findViewById(R.id.btn_retry)
            typingIndicator = findViewById(R.id.typing_indicator)
            sendLoading = findViewById(R.id.send_loading)

            setupUI()
            setupListeners()
            loadMessages()

            android.util.Log.d("MiniChat", "✅ Mini chat initialized for: $userName")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to initialize: $e")
            android.util.Log.e("MiniChat", "Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    // ========================================
    // REQUIREMENT 1: WindowManager Overlay
    // Được xử lý trong BubbleOverlayService
    // ========================================

    // ========================================
    // UI SETUP
    // ========================================
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
                reverseLayout = true  // Latest messages at bottom
                stackFromEnd = false
            }
            recyclerView.layoutManager = layoutManager
            recyclerView.adapter = adapter

            // ✅ Optimize RecyclerView for realtime updates
            recyclerView.setHasFixedSize(true)
            recyclerView.itemAnimator?.changeDuration = 0
            recyclerView.itemAnimator?.addDuration = 150
            recyclerView.itemAnimator?.removeDuration = 150

            showLoading()

            android.util.Log.d("MiniChat", "✅ UI setup complete")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup UI: $e")
            showError("Failed to setup UI: ${e.message}")
        }
    }

    // ========================================
    // REQUIREMENT 2: Kéo thả mini chat
    // Implement OnTouchListener với ACTION_MOVE
    // ========================================
    private fun setupListeners() {
        try {
            // Minimize button
            btnMinimize.setOnClickListener {
                android.util.Log.d("MiniChat", "🔽 Minimize clicked")
                hideKeyboard()
                onMinimizeListener?.invoke()
            }

            // Close button
            btnClose.setOnClickListener {
                android.util.Log.d("MiniChat", "❌ Close clicked")
                hideKeyboard()
                onCloseListener?.invoke()
            }

            // Send button
            btnSend.setOnClickListener {
                sendMessage()
            }

            // Retry button
            btnRetry.setOnClickListener {
                retryCount++
                loadMessages()
            }

            // Send on enter
            inputField.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else {
                    false
                }
            }

            // Text change listener
            inputField.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    // Can send typing status here
                }
            })

            // ✅ REQUIREMENT 2: Setup drag listener with ACTION_MOVE
            setupDragListener()

            android.util.Log.d("MiniChat", "✅ Listeners setup complete")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup listeners: $e")
        }
    }

    // ✅ REQUIREMENT 2: Drag implementation
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            if (isDetached) return@setOnTouchListener false

            val params = layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial position
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    android.util.Log.d("MiniChat", "👇 Drag started at (${params.x}, ${params.y})")
                    true
                }

                // ✅ REQUIREMENT 2: Handle ACTION_MOVE
                MotionEvent.ACTION_MOVE -> {
                    // Calculate delta
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // Update position
                    params.x = (initialX + deltaX).toInt()
                    params.y = (initialY + deltaY).toInt()

                    // Apply to WindowManager
                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE)
                            as? WindowManager
                    try {
                        windowManager?.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        android.util.Log.e("MiniChat", "❌ Failed to update layout: $e")
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    android.util.Log.d("MiniChat", "👆 Drag ended at (${params.x}, ${params.y})")
                    true
                }

                else -> false
            }
        }
    }

    // ========================================
    // STATE MANAGEMENT
    // ========================================
    private fun showLoading() {
        mainHandler.post {
            if (isDetached) return@post
            loadingIndicator.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.GONE
            errorStateView.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        mainHandler.post {
            if (isDetached) return@post
            loadingIndicator.visibility = View.GONE
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
            errorStateView.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        mainHandler.post {
            if (isDetached) return@post
            loadingIndicator.visibility = View.GONE
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.GONE
            errorStateView.visibility = View.VISIBLE
            errorText.text = message

            btnRetry.visibility = if (retryCount >= maxRetries) View.GONE else View.VISIBLE
        }
    }

    private fun showMessages() {
        mainHandler.post {
            if (isDetached) return@post
            loadingIndicator.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            errorStateView.visibility = View.GONE
        }
    }

    private fun setSendLoading(isLoading: Boolean) {
        mainHandler.post {
            if (isDetached) return@post
            isSendingMessage = isLoading
            btnSend.visibility = if (isLoading) View.GONE else View.VISIBLE
            sendLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            inputField.isEnabled = !isLoading
        }
    }

    // ========================================
    // REQUIREMENT 4: Nhận tin nhắn mới
    // Realtime Firebase Firestore listener
    // ========================================
    private fun loadMessages() {
        if (isLoadingMessages || isDetached) return
        isLoadingMessages = true

        showLoading()

        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            android.util.Log.e("MiniChat", "❌ Current user ID is null")
            showError("Authentication error")
            isLoadingMessages = false
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

            // ✅ REQUIREMENT 4: Setup Firestore snapshot listener
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
                        isLoadingMessages = false
                        showError("Failed to load messages: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        if (snapshot.isEmpty) {
                            android.util.Log.d("MiniChat", "📭 No messages in conversation")
                            isLoadingMessages = false
                            showEmptyState()
                        } else {
                            // ✅ REQUIREMENT 4: Handle realtime updates
                            snapshot.documentChanges.forEach { change ->
                                when (change.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                        handleNewMessage(change.document, currentUserId)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        handleModifiedMessage(change.document, currentUserId)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        handleRemovedMessage(change.document.id)
                                    }
                                }
                            }

                            if (messages.isNotEmpty()) {
                                isLoadingMessages = false
                                showMessages()
                            }
                        }
                    }
                }

            android.util.Log.d("MiniChat", "✅ Message listener setup")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup listener: $e")
            isLoadingMessages = false
            showError("Connection failed: ${e.message}")
        }
    }

    // ✅ REQUIREMENT 4: Handle new message từ Firebase
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
                    // ✅ REQUIREMENT 4: Add new message to adapter
                    messages.add(0, message)
                    adapter.notifyItemInserted(0)

                    // ✅ Auto-scroll to new message
                    scrollToLatestMessage()

                    android.util.Log.d("MiniChat", "✅ New message added: ${message.content}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error handling message: $e")
        }
    }

    // ✅ Handle modified message
    private fun handleModifiedMessage(
        document: com.google.firebase.firestore.DocumentSnapshot,
        currentUserId: String
    ) {
        if (isDetached) return

        try {
            val messageId = document.id
            val existingIndex = messages.indexOfFirst { it.id == messageId }

            if (existingIndex != -1) {
                val content = document.getString("content") ?: ""
                val type = document.getLong("type")?.toInt() ?: 0
                val idFrom = document.getString("idFrom") ?: ""
                val timestamp = document.getString("timestamp")?.toLongOrNull() ?: 0L

                val updatedMessage = MiniChatAdapter.ChatMessage(
                    id = messageId,
                    content = if (type == 0) content else "📷 Image",
                    isFromMe = idFrom == currentUserId,
                    timestamp = timestamp,
                    type = type
                )

                mainHandler.post {
                    if (!isDetached) {
                        messages[existingIndex] = updatedMessage
                        adapter.notifyItemChanged(existingIndex)
                        android.util.Log.d("MiniChat", "✅ Message updated: $messageId")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error handling modified message: $e")
        }
    }

    // ✅ Handle removed message
    private fun handleRemovedMessage(messageId: String) {
        if (isDetached) return

        val existingIndex = messages.indexOfFirst { it.id == messageId }
        if (existingIndex != -1) {
            mainHandler.post {
                if (!isDetached) {
                    messages.removeAt(existingIndex)
                    adapter.notifyItemRemoved(existingIndex)
                    android.util.Log.d("MiniChat", "✅ Message removed: $messageId")
                }
            }
        }
    }

    // ✅ Auto-scroll to latest message
    private fun scrollToLatestMessage() {
        if (isDetached) return

        try {
            if (recyclerView.canScrollVertically(-1)) {
                recyclerView.smoothScrollToPosition(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "⚠️ Error scrolling: $e")
        }
    }

    // ========================================
    // REQUIREMENT 3: Gửi tin nhắn
    // Lấy text từ EditText → Add to RecyclerView → Send to Firebase
    // ========================================
    private fun sendMessage() {
        if (isDetached || isSendingMessage) return

        // ✅ REQUIREMENT 3.1: Lấy text từ EditText
        val content = inputField.text.toString().trim()
        if (content.isEmpty()) {
            android.util.Log.w("MiniChat", "⚠️ Empty message, not sending")
            Toast.makeText(context, "Please type a message", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            Toast.makeText(context, "Authentication error", Toast.LENGTH_SHORT).show()
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

        // Show send loading
        setSendLoading(true)

        // ✅ REQUIREMENT 3.2: Add to RecyclerView trước (optimistic update)
        val optimisticMessage = MiniChatAdapter.ChatMessage(
            id = messageId,
            content = content,
            isFromMe = true,
            timestamp = messageId.toLong(),
            type = 0
        )

        mainHandler.post {
            if (!isDetached) {
                messages.add(0, optimisticMessage)
                adapter.notifyItemInserted(0)
                scrollToLatestMessage()
            }
        }

        // ✅ REQUIREMENT 3.3: Send to Firebase
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
                        setSendLoading(false)

                        android.util.Log.d("MiniChat", "✅ Message saved to Firebase")

                        // Send broadcast to Flutter
                        sendBroadcastToFlutter(content)

                        // Notify listener
                        onMessageSentListener?.invoke(content)

                        // Update conversation
                        updateConversation(conversationId, content)
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MiniChat", "❌ Failed to send message: $e")

                // Remove optimistic message on failure
                mainHandler.post {
                    if (!isDetached) {
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index != -1) {
                            messages.removeAt(index)
                            adapter.notifyItemRemoved(index)
                        }

                        setSendLoading(false)
                        Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

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

    private fun hideKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(inputField.windowToken, 0)
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "⚠️ Error hiding keyboard: $e")
        }
    }

    // ========================================
    // PUBLIC API
    // ========================================
    fun setOnMinimizeListener(listener: () -> Unit) {
        onMinimizeListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun setOnMessageSentListener(listener: (String) -> Unit) {
        onMessageSentListener = listener
    }

    // ========================================
    // CLEANUP
    // ========================================
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