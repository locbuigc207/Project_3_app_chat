// android/app/src/main/kotlin/hust/appchat/bubble/MiniChatWindow.kt - COMPLETE
package hust.appchat.bubble

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
 * ✅ COMPLETE: Mini Chat Window với đầy đủ state management
 *
 * States:
 * 1. Closed/Bubble Only
 * 2. Opening (animation)
 * 3. Open/Expanded
 * 4. Typing
 * 5. Sending
 * 6. Receiving/Update
 * 7. Closing/Minimize
 */
class MiniChatWindow(
    context: Context,
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String
) : LinearLayout(context) {

    // ===== State Management =====
    enum class ChatState {
        CLOSED,
        OPENING,
        OPEN,
        TYPING,
        SENDING,
        RECEIVING,
        CLOSING
    }

    private var currentState = ChatState.CLOSED
    private var isExpanded = false
    private var isKeyboardVisible = false

    // ===== Saved State =====
    private var savedScrollPosition = 0
    private var savedInputText = ""
    private val pendingMessages = mutableListOf<String>()

    // ===== Firebase =====
    private val firestore = FirebaseFirestore.getInstance()
    private var messageListener: ListenerRegistration? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ===== Views =====
    private val avatarView: ImageView
    private val nameView: TextView
    private val statusView: TextView
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
    private val newMessageIndicator: TextView

    // ===== Listeners =====
    private var onMinimizeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onMessageSentListener: ((String) -> Unit)? = null
    private var onStateChangedListener: ((ChatState) -> Unit)? = null

    // ===== State =====
    private val messages = mutableListOf<MiniChatAdapter.ChatMessage>()
    private lateinit var adapter: MiniChatAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var isLoadingMessages = false
    private var isSendingMessage = false
    private var isDetached = false
    private var retryCount = 0
    private val maxRetries = 3

    // ===== Auto-scroll tracking =====
    private var isUserScrollingUp = false
    private var unreadCountWhileScrolling = 0

    init {
        try {
            LayoutInflater.from(context).inflate(R.layout.mini_chat_window, this, true)

            // Initialize all views
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
            newMessageIndicator = findViewById(R.id.new_message_indicator)

            setupUI()
            setupListeners()
            setupRecyclerView()

            // ✅ Start in OPENING state with animation
            transitionToState(ChatState.OPENING)
            loadMessages()

            android.util.Log.d("MiniChat", "✅ Mini chat initialized for: $userName")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to initialize: $e")
            throw e
        }
    }

    // ===========================================
    // STATE MANAGEMENT
    // ===========================================
    private fun transitionToState(newState: ChatState) {
        if (currentState == newState) return

        android.util.Log.d("MiniChat", "🔄 State: $currentState → $newState")

        val oldState = currentState
        currentState = newState

        // Handle state transitions
        when (newState) {
            ChatState.OPENING -> handleOpeningState()
            ChatState.OPEN -> handleOpenState()
            ChatState.TYPING -> handleTypingState()
            ChatState.SENDING -> handleSendingState()
            ChatState.RECEIVING -> handleReceivingState()
            ChatState.CLOSING -> handleClosingState()
            ChatState.CLOSED -> handleClosedState()
        }

        onStateChangedListener?.invoke(newState)
    }

    private fun handleOpeningState() {
        isExpanded = true
        animateExpand()
    }

    private fun handleOpenState() {
        isExpanded = true
        restoreState()
    }

    private fun handleTypingState() {
        // User is typing - show typing indicator to other user
        // This would be sent via Firebase
    }

    private fun handleSendingState() {
        setSendLoading(true)
    }

    private fun handleReceivingState() {
        // New message received - handled in message listener
    }

    private fun handleClosingState() {
        isExpanded = false
        saveState()
        animateCollapse()
    }

    private fun handleClosedState() {
        isExpanded = false
    }

    // ===========================================
    // SAVE & RESTORE STATE
    // ===========================================
    private fun saveState() {
        try {
            savedScrollPosition = layoutManager.findFirstVisibleItemPosition()
            savedInputText = inputField.text.toString()

            android.util.Log.d("MiniChat", "💾 State saved: scroll=$savedScrollPosition, input='$savedInputText'")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error saving state: $e")
        }
    }

    private fun restoreState() {
        try {
            if (savedInputText.isNotEmpty()) {
                inputField.setText(savedInputText)
                inputField.setSelection(savedInputText.length)
            }

            if (savedScrollPosition > 0) {
                layoutManager.scrollToPosition(savedScrollPosition)
            }

            android.util.Log.d("MiniChat", "📦 State restored")
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error restoring state: $e")
        }
    }

    // ===========================================
    // ANIMATIONS
    // ===========================================
    private fun animateExpand() {
        alpha = 0f
        scaleX = 0.3f
        scaleY = 0.3f

        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.5f))
            .withEndAction {
                if (!isDetached) {
                    transitionToState(ChatState.OPEN)
                }
            }
            .start()
    }

    private fun animateCollapse() {
        animate()
            .alpha(0f)
            .scaleX(0.3f)
            .scaleY(0.3f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                if (!isDetached) {
                    transitionToState(ChatState.CLOSED)
                    onMinimizeListener?.invoke()
                }
            }
            .start()
    }

    // ===========================================
    // UI SETUP
    // ===========================================
    private fun setupUI() {
        try {
            nameView.text = userName
            statusView.text = "Online" // Would be updated from presence

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
            android.util.Log.e("MiniChat", "❌ Failed to setup UI: $e")
            showError("Failed to setup UI: ${e.message}")
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
        recyclerView.itemAnimator?.changeDuration = 0
        recyclerView.itemAnimator?.addDuration = 150
        recyclerView.itemAnimator?.removeDuration = 150

        // ✅ Scroll listener for auto-scroll logic
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                isUserScrollingUp = firstVisible > 3 // User scrolled up

                // Update new message indicator
                updateNewMessageIndicator()
            }
        })
    }

    private fun setupListeners() {
        try {
            btnMinimize.setOnClickListener {
                android.util.Log.d("MiniChat", "🔽 Minimize clicked")
                hideKeyboard()
                transitionToState(ChatState.CLOSING)
            }

            btnClose.setOnClickListener {
                android.util.Log.d("MiniChat", "❌ Close clicked")
                hideKeyboard()
                onCloseListener?.invoke()
            }

            btnSend.setOnClickListener {
                sendMessage()
            }

            btnRetry.setOnClickListener {
                retryCount++
                loadMessages()
            }

            inputField.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else {
                    false
                }
            }

            // ✅ Typing state detection
            inputField.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!s.isNullOrEmpty() && currentState == ChatState.OPEN) {
                        transitionToState(ChatState.TYPING)
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s.isNullOrEmpty() && currentState == ChatState.TYPING) {
                        transitionToState(ChatState.OPEN)
                    }
                }
            })

            // ✅ Keyboard visibility detection
            inputField.setOnFocusChangeListener { _, hasFocus ->
                isKeyboardVisible = hasFocus
                if (hasFocus) {
                    // Keyboard shown - adjust view
                    postDelayed({
                        scrollToLatestMessage()
                    }, 300)
                }
            }

            setupDragListener()
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Failed to setup listeners: $e")
        }
    }

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
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    params.x = (initialX + deltaX).toInt()
                    params.y = (initialY + deltaY).toInt()

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
                    true
                }

                else -> false
            }
        }
    }

    // ===========================================
    // STATE VIEWS
    // ===========================================
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

    private fun updateNewMessageIndicator() {
        mainHandler.post {
            if (isDetached) return@post

            if (isUserScrollingUp && unreadCountWhileScrolling > 0) {
                newMessageIndicator.visibility = View.VISIBLE
                newMessageIndicator.text = "$unreadCountWhileScrolling new message${if (unreadCountWhileScrolling > 1) "s" else ""}"
            } else {
                newMessageIndicator.visibility = View.GONE
                unreadCountWhileScrolling = 0
            }
        }
    }

    // ===========================================
    // MESSAGE LOADING
    // ===========================================
    private fun loadMessages() {
        if (isLoadingMessages || isDetached) return
        isLoadingMessages = true

        showLoading()

        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            showError("Authentication error")
            isLoadingMessages = false
            return
        }

        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

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
                        isLoadingMessages = false
                        showError("Failed to load messages: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        if (snapshot.isEmpty) {
                            isLoadingMessages = false
                            showEmptyState()
                        } else {
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
        } catch (e: Exception) {
            isLoadingMessages = false
            showError("Connection failed: ${e.message}")
        }
    }

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

            val existingIndex = messages.indexOfFirst { it.id == message.id }

            mainHandler.post {
                if (isDetached) return@post

                if (existingIndex == -1) {
                    // ✅ NEW message
                    messages.add(0, message)
                    adapter.notifyItemInserted(0)

                    // ✅ Transition to RECEIVING state
                    if (currentState != ChatState.RECEIVING) {
                        transitionToState(ChatState.RECEIVING)

                        // Return to OPEN after animation
                        postDelayed({
                            if (currentState == ChatState.RECEIVING) {
                                transitionToState(ChatState.OPEN)
                            }
                        }, 500)
                    }

                    // ✅ Auto-scroll logic
                    if (!isUserScrollingUp) {
                        scrollToLatestMessage()
                    } else {
                        unreadCountWhileScrolling++
                        updateNewMessageIndicator()
                    }

                    // ✅ Highlight new message animation
                    animateNewMessage()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error handling message: $e")
        }
    }

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
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "❌ Error handling modified message: $e")
        }
    }

    private fun handleRemovedMessage(messageId: String) {
        if (isDetached) return

        val existingIndex = messages.indexOfFirst { it.id == messageId }
        if (existingIndex != -1) {
            mainHandler.post {
                if (!isDetached) {
                    messages.removeAt(existingIndex)
                    adapter.notifyItemRemoved(existingIndex)
                }
            }
        }
    }

    private fun animateNewMessage() {
        // Bounce animation for new message
        recyclerView.alpha = 0.7f
        recyclerView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

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

    // ===========================================
    // SEND MESSAGE
    // ===========================================
    private fun sendMessage() {
        if (isDetached || isSendingMessage) return

        val content = inputField.text.toString().trim()
        if (content.isEmpty()) {
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
            "type" to 0,
            "isRead" to false
        )

        // ✅ Transition to SENDING state
        transitionToState(ChatState.SENDING)

        // ✅ Optimistic update
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

        // ✅ Send to Firebase
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

                        // ✅ Return to OPEN state
                        transitionToState(ChatState.OPEN)

                        onMessageSentListener?.invoke(content)
                        updateConversation(conversationId, content)
                    }
                }
            }
            .addOnFailureListener { e ->
                // Remove optimistic message
                mainHandler.post {
                    if (!isDetached) {
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index != -1) {
                            messages.removeAt(index)
                            adapter.notifyItemRemoved(index)
                        }

                        setSendLoading(false)
                        transitionToState(ChatState.OPEN)
                        Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show()
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

    // ===========================================
    // PUBLIC API
    // ===========================================
    fun setOnMinimizeListener(listener: () -> Unit) {
        onMinimizeListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun setOnMessageSentListener(listener: (String) -> Unit) {
        onMessageSentListener = listener
    }

    fun setOnStateChangedListener(listener: (ChatState) -> Unit) {
        onStateChangedListener = listener
    }

    fun getCurrentState() = currentState

    fun isExpanded() = isExpanded

    fun getSavedState() = Bundle().apply {
        putInt("scroll_position", savedScrollPosition)
        putString("input_text", savedInputText)
    }

    fun restoreFromBundle(bundle: Bundle) {
        savedScrollPosition = bundle.getInt("scroll_position", 0)
        savedInputText = bundle.getString("input_text", "")
        restoreState()
    }

    // ===========================================
    // CLEANUP
    // ===========================================
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
        onStateChangedListener = null

        messages.clear()
    }

    override fun onDetachedFromWindow() {
        cleanup()
        super.onDetachedFromWindow()
    }
}