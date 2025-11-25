// android/app/src/main/kotlin/hust/appchat/bubble/MiniChatWindow.kt - COMPLETE
package hust.appchat.bubble

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import hust.appchat.R
import hust.appchat.adapter.MiniChatAdapter

/**
 * Mini Chat Window - Overlay draggable chat window
 *
 * Features:
 * - Draggable header
 * - Realtime messages
 * - Send messages
 * - Minimize/Close actions
 */
class MiniChatWindow(
    context: Context,
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String
) : LinearLayout(context) {

    private val firestore = FirebaseFirestore.getInstance()
    private var messageListener: ListenerRegistration? = null

    // UI Components
    private val avatarView: ImageView
    private val nameView: TextView
    private val btnMinimize: ImageView
    private val btnClose: ImageView
    private val recyclerView: RecyclerView
    private val inputField: EditText
    private val btnSend: ImageView
    private val header: View

    // Callbacks
    private var onMinimizeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null

    // Messages
    private val messages = mutableListOf<MiniChatAdapter.ChatMessage>()
    private val adapter = MiniChatAdapter(messages)

    init {
        LayoutInflater.from(context).inflate(R.layout.mini_chat_window, this, true)

        // Get view references
        avatarView = findViewById(R.id.mini_chat_avatar)
        nameView = findViewById(R.id.mini_chat_name)
        btnMinimize = findViewById(R.id.btn_minimize)
        btnClose = findViewById(R.id.btn_close)
        recyclerView = findViewById(R.id.mini_chat_messages)
        inputField = findViewById(R.id.mini_chat_input)
        btnSend = findViewById(R.id.btn_send)
        header = findViewById(R.id.mini_chat_header)

        setupUI()
        setupListeners()
        loadMessages()

        android.util.Log.d("MiniChatWindow", "✅ Mini chat created for: $userName")
    }

    private fun setupUI() {
        // Set user info
        nameView.text = userName
        if (avatarUrl.isNotEmpty()) {
            Glide.with(context)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.bubble_background)
                .into(avatarView)
        }

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context).apply {
            reverseLayout = true
            stackFromEnd = false
        }
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        // Minimize button
        btnMinimize.setOnClickListener {
            onMinimizeListener?.invoke()
        }

        // Close button
        btnClose.setOnClickListener {
            onCloseListener?.invoke()
        }

        // Send button
        btnSend.setOnClickListener {
            sendMessage()
        }

        // Enter key to send
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
    }

    /**
     * Setup drag functionality for header
     */
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            val params = layoutParams as android.view.WindowManager.LayoutParams

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
                            as android.view.WindowManager
                    try {
                        windowManager.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        android.util.Log.e("MiniChatWindow", "Error moving window: $e")
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
        val currentUserId = getCurrentUserId() ?: run {
            android.util.Log.e("MiniChatWindow", "❌ No current user ID")
            return
        }

        val conversationId = if (currentUserId < userId) {
            "$currentUserId-$userId"
        } else {
            "$userId-$currentUserId"
        }

        android.util.Log.d("MiniChatWindow", "📖 Loading messages from: $conversationId")

        messageListener = firestore
            .collection("messages")
            .document(conversationId)
            .collection(conversationId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MiniChatWindow", "❌ Listen error: $error")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document

                        val message = MiniChatAdapter.ChatMessage(
                            id = doc.id,
                            content = doc.getString("content") ?: "",
                            isFromMe = doc.getString("idFrom") == currentUserId,
                            timestamp = doc.getString("timestamp")?.toLongOrNull() ?: 0L,
                            type = doc.getLong("type")?.toInt() ?: 0
                        )

                        // Insert at beginning (most recent)
                        messages.add(0, message)
                        adapter.notifyItemInserted(0)
                        recyclerView.smoothScrollToPosition(0)
                    }
                }
            }
    }

    /**
     * Send message to Firestore
     */
    private fun sendMessage() {
        val content = inputField.text.toString().trim()
        if (content.isEmpty()) return

        val currentUserId = getCurrentUserId() ?: return

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
                inputField.text.clear()
                updateConversation(conversationId, content)

                android.util.Log.d("MiniChatWindow", "✅ Message sent")
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                android.util.Log.e("MiniChatWindow", "❌ Send error: $e")
            }
    }

    /**
     * Update conversation last message
     */
    private fun updateConversation(conversationId: String, lastMessage: String) {
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
    }

    /**
     * Get current user ID from Firebase Auth
     */
    private fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    fun setOnMinimizeListener(listener: () -> Unit) {
        onMinimizeListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun cleanup() {
        messageListener?.remove()
        android.util.Log.d("MiniChatWindow", "🧹 Cleanup complete")
    }
}