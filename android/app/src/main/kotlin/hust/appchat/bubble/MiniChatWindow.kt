// android/app/src/main/kotlin/hust/appchat/bubble/MiniChatWindow.kt
// ✅ FIXED: All compilation errors resolved

package hust.appchat.bubble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import hust.appchat.R
import hust.appchat.adapter.MiniChatAdapter
import io.flutter.plugin.common.MethodChannel

class MiniChatWindow(
    context: Context,
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String
) : LinearLayout(context) {

    // ✅ FIX: Use getInstance() instead of instance
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var methodChannel: MethodChannel? = null

    private var messageListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var onlineListener: ListenerRegistration? = null

    private val avatarView: ImageView
    private val nameView: TextView
    private val statusView: TextView
    private val btnMinimize: ImageView
    private val btnClose: ImageView
    private val recyclerView: RecyclerView
    private val inputField: EditText
    private val btnSend: ImageView
    private val header: View
    private val loadingIndicator: ProgressBar
    private val emptyStateView: LinearLayout
    private val errorStateView: LinearLayout
    private val errorText: TextView
    private val btnRetry: Button
    private val typingIndicator: LinearLayout
    private val sendLoading: ProgressBar

    private val messages = mutableListOf<MiniChatAdapter.ChatMessage>()
    private lateinit var adapter: MiniChatAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var currentUserId: String = ""
    private var conversationId: String = ""
    private var isDetached = false
    private var isLoadingMessages = false
    private var isSendingMessage = false

    private var onMinimizeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onMessageSentListener: ((String) -> Unit)? = null

    init {
        try {
            android.util.Log.d("MiniChat", "🏗️ Initializing for: $userName")

            LayoutInflater.from(context).inflate(R.layout.mini_chat_window, this, true)

            avatarView = findViewById(R.id.mini_chat_avatar)
            nameView = findViewById(R.id.mini_chat_name)
            statusView = findViewById(R.id.mini_chat_status)
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
            setupRecyclerView()
            setupListeners()
            initializeChat()

            android.util.Log.d("MiniChat", "✅ Initialization complete")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Init failed: $e")
            android.util.Log.e("MiniChat", "Stack: ${e.stackTraceToString()}")
            throw e
        }
    }

    fun setMethodChannel(channel: MethodChannel) {
        methodChannel = channel
        android.util.Log.d("MiniChat", "✅ MethodChannel set")
    }

    private fun setupUI() {
        try {
            nameView.text = userName
            statusView.text = "Connecting..."

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

            showLoading()
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ setupUI failed: $e")
        }
    }

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(context).apply {
            reverseLayout = true
            stackFromEnd = false
        }
        adapter = MiniChatAdapter(messages)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        android.util.Log.d("MiniChat", "✅ RecyclerView setup complete")
    }

    private fun setupListeners() {
        try {
            btnMinimize.setOnClickListener {
                android.util.Log.d("MiniChat", "⬇️ Minimize clicked")
                hideKeyboard()
                onMinimizeListener?.invoke()
            }

            btnClose.setOnClickListener {
                android.util.Log.d("MiniChat", "❌ Close clicked")
                hideKeyboard()
                onCloseListener?.invoke()
            }

            btnSend.setOnClickListener {
                android.util.Log.d("MiniChat", "📤 Send clicked")
                sendMessage()
            }

            inputField.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }

            var typingTimer: android.os.CountDownTimer? = null

            inputField.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!s.isNullOrEmpty()) {
                        updateMyTypingStatus(true)

                        typingTimer?.cancel()
                        typingTimer = object : android.os.CountDownTimer(2000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {}
                            override fun onFinish() {
                                updateMyTypingStatus(false)
                            }
                        }.start()
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    if (s.isNullOrEmpty()) {
                        typingTimer?.cancel()
                        updateMyTypingStatus(false)
                    }
                }
            })

            btnRetry.setOnClickListener {
                android.util.Log.d("MiniChat", "🔄 Retry clicked")
                loadMessages()
            }

            setupDragListener()

            android.util.Log.d("MiniChat", "✅ Listeners setup complete")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ setupListeners failed: $e")
        }
    }

    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            if (isDetached) return@setOnTouchListener false

            val params = layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + event.rawX - initialTouchX).toInt()
                    params.y = (initialY + event.rawY - initialTouchY).toInt()

                    try {
                        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                            ?.updateViewLayout(this, params)
                    } catch (e: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
    }

    private fun initializeChat() {
        currentUserId = auth.currentUser?.uid ?: ""

        if (currentUserId.isEmpty()) {
            android.util.Log.e("MiniChat", "❌ No current user")
            showError("Not logged in")
            return
        }

        conversationId = if (currentUserId.compareTo(userId) > 0) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        android.util.Log.d("MiniChat", "✅ Current User: $currentUserId")
        android.util.Log.d("MiniChat", "✅ Peer User: $userId")
        android.util.Log.d("MiniChat", "✅ Conversation ID: $conversationId")

        setupPresenceListeners()
        loadMessages()
        markMessagesAsRead()
    }

    private fun loadMessages() {
        if (isLoadingMessages) {
            android.util.Log.d("MiniChat", "⏳ Already loading messages")
            return
        }

        isLoadingMessages = true
        showLoading()

        try {
            messageListener?.remove()

            android.util.Log.d("MiniChat", "📥 Starting message listener for: $conversationId")

            messageListener = firestore
                .collection("messages")
                .document(conversationId)
                .collection(conversationId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    isLoadingMessages = false

                    if (isDetached) {
                        android.util.Log.d("MiniChat", "⚠️ View detached, ignoring update")
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        android.util.Log.e("MiniChat", "❌ Listener error: $error")
                        showError("Connection failed: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        android.util.Log.d("MiniChat", "📦 Received ${snapshot.documentChanges.size} changes")

                        handleMessagesUpdate(snapshot.documentChanges)

                        if (messages.isNotEmpty()) {
                            android.util.Log.d("MiniChat", "✅ Showing ${messages.size} messages")
                            showMessages()
                        } else {
                            android.util.Log.d("MiniChat", "ℹ️ No messages yet")
                            showEmptyState()
                        }
                    }
                }

            android.util.Log.d("MiniChat", "✅ Message listener active")
        } catch (e: Exception) {
            isLoadingMessages = false
            android.util.Log.e("MiniChat", "❌ loadMessages failed: $e")
            android.util.Log.e("MiniChat", "Stack: ${e.stackTraceToString()}")
            showError("Failed to load messages")
        }
    }

    // ✅ FIX: Explicit lambda parameter types
    private fun handleMessagesUpdate(changes: List<DocumentChange>) {
        mainHandler.post {
            if (isDetached) return@post

            for (change in changes) {
                when (change.type) {
                    DocumentChange.Type.ADDED -> {
                        android.util.Log.d("MiniChat", "➕ Message added: ${change.document.id}")
                        handleNewMessage(change.document)
                    }
                    DocumentChange.Type.MODIFIED -> {
                        android.util.Log.d("MiniChat", "✏️ Message modified: ${change.document.id}")
                        handleModifiedMessage(change.document)
                    }
                    DocumentChange.Type.REMOVED -> {
                        android.util.Log.d("MiniChat", "➖ Message removed: ${change.document.id}")
                        handleRemovedMessage(change.document.id)
                    }
                }
            }
        }
    }

    private fun handleNewMessage(document: com.google.firebase.firestore.DocumentSnapshot) {
        try {
            val data = document.data ?: return

            val content = data["content"] as? String ?: ""
            val type = (data["type"] as? Long)?.toInt() ?: 0
            val idFrom = data["idFrom"] as? String ?: ""
            val timestamp = (data["timestamp"] as? String)?.toLongOrNull() ?: 0L

            val message = MiniChatAdapter.ChatMessage(
                id = document.id,
                content = if (type == 0) content else "📷 Image",
                isFromMe = idFrom == currentUserId,
                timestamp = timestamp,
                type = type
            )

            val existingIndex = messages.indexOfFirst { it.id == message.id }

            if (existingIndex == -1) {
                messages.add(0, message)
                adapter.notifyItemInserted(0)
                scrollToLatestMessage()

                android.util.Log.d("MiniChat", "✅ Added message: ${message.content.take(20)}...")

                if (!message.isFromMe) {
                    mainHandler.postDelayed({
                        markMessagesAsRead()
                    }, 500)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ handleNewMessage error: $e")
        }
    }

    private fun handleModifiedMessage(document: com.google.firebase.firestore.DocumentSnapshot) {
        try {
            val existingIndex = messages.indexOfFirst { it.id == document.id }

            if (existingIndex != -1) {
                val data = document.data ?: return

                val content = data["content"] as? String ?: ""
                val type = (data["type"] as? Long)?.toInt() ?: 0
                val idFrom = data["idFrom"] as? String ?: ""
                val timestamp = (data["timestamp"] as? String)?.toLongOrNull() ?: 0L

                val updatedMessage = MiniChatAdapter.ChatMessage(
                    id = document.id,
                    content = if (type == 0) content else "📷 Image",
                    isFromMe = idFrom == currentUserId,
                    timestamp = timestamp,
                    type = type
                )

                messages[existingIndex] = updatedMessage
                adapter.notifyItemChanged(existingIndex)

                android.util.Log.d("MiniChat", "✅ Modified message: ${document.id}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ handleModifiedMessage error: $e")
        }
    }

    private fun handleRemovedMessage(messageId: String) {
        val existingIndex = messages.indexOfFirst { it.id == messageId }

        if (existingIndex != -1) {
            messages.removeAt(existingIndex)
            adapter.notifyItemRemoved(existingIndex)

            android.util.Log.d("MiniChat", "✅ Removed message: $messageId")
        }
    }

    private fun sendMessage() {
        if (isDetached || isSendingMessage) return

        val content = inputField.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(context, "Type a message", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUserId.isEmpty()) {
            Toast.makeText(context, "Auth error", Toast.LENGTH_SHORT).show()
            return
        }

        isSendingMessage = true
        setSendLoading(true)

        val messageId = System.currentTimeMillis().toString()

        android.util.Log.d("MiniChat", "📤 Sending via MethodChannel: $content")

        mainHandler.post {
            try {
                methodChannel?.invokeMethod(
                    "sendMessage",
                    mapOf(
                        "conversationId" to conversationId,
                        "content" to content,
                        "type" to 0,
                        "messageId" to messageId
                    )
                )

                val optimisticMessage = MiniChatAdapter.ChatMessage(
                    id = messageId,
                    content = content,
                    isFromMe = true,
                    timestamp = messageId.toLong(),
                    type = 0
                )

                if (!isDetached) {
                    messages.add(0, optimisticMessage)
                    adapter.notifyItemInserted(0)
                    scrollToLatestMessage()
                    inputField.text.clear()
                    hideKeyboard()
                    isSendingMessage = false
                    setSendLoading(false)
                    onMessageSentListener?.invoke(content)
                }

                android.util.Log.d("MiniChat", "✅ Message sent via MethodChannel")
            } catch (e: Exception) {
                android.util.Log.e("MiniChat", "❌ Send via MethodChannel failed: $e")
                isSendingMessage = false
                setSendLoading(false)
                Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markMessagesAsRead() {
        if (isDetached || currentUserId.isEmpty()) return

        try {
            firestore
                .collection("messages")
                .document(conversationId)
                .collection(conversationId)
                .whereEqualTo("idTo", currentUserId)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        android.util.Log.d("MiniChat", "ℹ️ No unread messages")
                        return@addOnSuccessListener
                    }

                    val batch = firestore.batch()

                    for (doc in snapshot.documents) {
                        batch.update(doc.reference, mapOf(
                            "isRead" to true,
                            "readAt" to FieldValue.serverTimestamp()
                        ))
                    }

                    batch.commit()
                        .addOnSuccessListener {
                            android.util.Log.d("MiniChat", "✅ ${snapshot.size()} messages marked as read")
                            BubbleManager.markAsRead(context, userId)
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("MiniChat", "❌ Mark read failed: $e")
                        }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MiniChat", "❌ Get unread messages failed: $e")
                }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ markMessagesAsRead error: $e")
        }
    }

    private fun setupPresenceListeners() {
        try {
            onlineListener = firestore
                .collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (isDetached || error != null || snapshot == null) return@addSnapshotListener

                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val lastSeen = when (val value = snapshot["lastSeen"]) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    updateOnlineStatus(isOnline, lastSeen)
                }

            val typingDocId = if (currentUserId < userId) {
                "$currentUserId-$userId"
            } else {
                "$userId-$currentUserId"
            }

            typingListener = firestore
                .collection("typings")
                .document(typingDocId)
                .addSnapshotListener { snapshot, error ->
                    if (isDetached || error != null || snapshot == null) return@addSnapshotListener

                    val isTypingOpponent = snapshot.getString("idTyping") == userId
                    updateTypingIndicator(isTypingOpponent)
                }

            android.util.Log.d("MiniChat", "✅ Presence listeners active")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ setupPresenceListeners failed: $e")
        }
    }

    private fun updateOnlineStatus(isOnline: Boolean, lastSeen: Long) {
        mainHandler.post {
            if (isDetached) return@post

            statusView.text = if (isOnline) {
                "Online"
            } else {
                "Last seen: ${BubbleManager.formatTimestamp(lastSeen)}"
            }
        }
    }

    private fun updateTypingIndicator(isTyping: Boolean) {
        mainHandler.post {
            if (isDetached) return@post

            if (isTyping) {
                typingIndicator.visibility = View.VISIBLE
                statusView.visibility = View.GONE
            } else {
                typingIndicator.visibility = View.GONE
                statusView.visibility = View.VISIBLE
            }
        }
    }

    private fun updateMyTypingStatus(isTyping: Boolean) {
        if (currentUserId.isEmpty()) return

        val typingDocId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        val data = if (isTyping) {
            hashMapOf("idTyping" to currentUserId, "timestamp" to System.currentTimeMillis())
        } else {
            hashMapOf("idTyping" to "", "timestamp" to System.currentTimeMillis())
        }

        firestore
            .collection("typings")
            .document(typingDocId)
            .set(data as Map<String, Any>)
    }

    private fun scrollToLatestMessage() {
        if (isDetached) return
        try {
            if (recyclerView.canScrollVertically(-1)) {
                recyclerView.smoothScrollToPosition(0)
            }
        } catch (e: Exception) {}
    }

    private fun hideKeyboard() {
        try {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(inputField.windowToken, 0)
        } catch (e: Exception) {}
    }

    private fun setSendLoading(isLoading: Boolean) {
        mainHandler.post {
            if (isDetached) return@post
            btnSend.visibility = if (isLoading) View.GONE else View.VISIBLE
            sendLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            inputField.isEnabled = !isLoading
        }
    }

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

    fun setOnMinimizeListener(listener: () -> Unit) {
        onMinimizeListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun setOnMessageSentListener(listener: (String) -> Unit) {
        onMessageSentListener = listener
    }

    fun cleanup() {
        isDetached = true

        android.util.Log.d("MiniChat", "🧹 Cleaning up")

        try {
            messageListener?.remove()
            messageListener = null
        } catch (e: Exception) {}

        try {
            onlineListener?.remove()
            onlineListener = null
        } catch (e: Exception) {}

        try {
            typingListener?.remove()
            typingListener = null
        } catch (e: Exception) {}

        mainHandler.removeCallbacksAndMessages(null)

        try {
            Glide.with(context).clear(avatarView)
        } catch (e: Exception) {}

        onMinimizeListener = null
        onCloseListener = null
        onMessageSentListener = null
        messages.clear()

        android.util.Log.d("MiniChat", "✅ Cleanup complete")
    }

    override fun onDetachedFromWindow() {
        cleanup()
        super.onDetachedFromWindow()
    }
}