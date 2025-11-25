package hust.appchat.bubble

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import hust.appchat.R

/**
 * Mini Chat Window - cửa sổ chat dạng overlay
 */
class MiniChatWindow(
    context: Context,
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String
) : LinearLayout(context) {

    private val firestore = FirebaseFirestore.getInstance()
    private var messageListener: ListenerRegistration? = null

    private val avatarView: ImageView
    private val nameView: TextView
    private val btnMinimize: ImageView
    private val btnClose: ImageView
    private val recyclerView: RecyclerView
    private val inputField: EditText
    private val btnSend: ImageView
    private val header: View

    private var onMinimizeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onMessageSentListener: ((String) -> Unit)? = null

    private val messages = mutableListOf<ChatMessage>()
    private val adapter = MiniChatAdapter(messages)

    data class ChatMessage(
        val id: String,
        val content: String,
        val isFromMe: Boolean,
        val timestamp: Long,
        val type: Int = 0
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.mini_chat_window, this, true)

        // Get views
        avatarView = findViewById(R.id.mini_chat_avatar)
        nameView = findViewById(R.id.mini_chat_name)
        btnMinimize = findViewById(R.id.btn_minimize)
        btnClose = findViewById(R.id.btn_close)
        recyclerView = findViewById(R.id.mini_chat_messages)
        inputField = findViewById(R.id.mini_chat_input)
        btnSend = findViewById(R.id.btn_send)
        header = findViewById(R.id.mini_chat_header)

        // Setup UI
        nameView.text = userName
        if (avatarUrl.isNotEmpty()) {
            try {
                Glide.with(context)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bubble_background)
                    .into(avatarView)
            } catch (e: Exception) {
                android.util.Log.e("MiniChat", "Failed to load avatar: $e")
            }
        }

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context).apply {
            reverseLayout = true
            stackFromEnd = false
        }
        recyclerView.adapter = adapter

        // Setup listeners
        btnMinimize.setOnClickListener { onMinimizeListener?.invoke() }
        btnClose.setOnClickListener { onCloseListener?.invoke() }
        btnSend.setOnClickListener { sendMessage() }

        // Send on enter key
        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Make header draggable
        setupDragListener()

        // Load messages
        loadMessages()
    }

    /**
     * Setup drag functionality cho header
     */
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            val params = layoutParams as? android.view.WindowManager.LayoutParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (initialTouchX - event.rawX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()

                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE)
                            as? android.view.WindowManager
                    try {
                        windowManager?.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        android.util.Log.e("MiniChat", "Failed to update layout: $e")
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Load messages from Firestore
     */
    private fun loadMessages() {
        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            android.util.Log.e("MiniChat", "Current user ID is null")
            return
        }

        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        try {
            messageListener = firestore
                .collection("messages")
                .document(conversationId)
                .collection(conversationId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("MiniChat", "Listen error: $error")
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { change ->
                        when (change.type) {
                            com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                val doc = change.document
                                val message = ChatMessage(
                                    id = doc.id,
                                    content = doc.getString("content") ?: "",
                                    isFromMe = doc.getString("idFrom") == currentUserId,
                                    timestamp = doc.getString("timestamp")?.toLongOrNull() ?: 0L,
                                    type = doc.getLong("type")?.toInt() ?: 0
                                )

                                // Add at position 0 (most recent)
                                post {
                                    messages.add(0, message)
                                    adapter.notifyItemInserted(0)
                                    recyclerView.smoothScrollToPosition(0)
                                }
                            }
                            else -> {}
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "Failed to setup listener: $e")
        }
    }

    /**
     * Send message
     */
    private fun sendMessage() {
        val content = inputField.text.toString().trim()
        if (content.isEmpty()) return

        val currentUserId = BubbleManager.getCurrentUserId()
        if (currentUserId == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
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

        firestore
            .collection("messages")
            .document(conversationId)
            .collection(conversationId)
            .document(messageId)
            .set(messageData)
            .addOnSuccessListener {
                post {
                    inputField.text.clear()

                    // Notify listener
                    onMessageSentListener?.invoke(content)
                }

                // Update conversation
                updateConversation(conversationId, content)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MiniChat", "Failed to send message: $e")
                Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Update conversation last message
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
                    android.util.Log.e("MiniChat", "Failed to update conversation: $e")
                }
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "Error updating conversation: $e")
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
        try {
            messageListener?.remove()
            messageListener = null
        } catch (e: Exception) {
            android.util.Log.e("MiniChat", "Error cleaning up: $e")
        }
    }
}

/**
 * RecyclerView Adapter for mini chat
 */
class MiniChatAdapter(
    private val messages: List<MiniChatWindow.ChatMessage>
) : RecyclerView.Adapter<MiniChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == 0) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }

        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromMe) 0 else 1
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.message_text)

        fun bind(message: MiniChatWindow.ChatMessage) {
            textView.text = message.content
        }
    }
}