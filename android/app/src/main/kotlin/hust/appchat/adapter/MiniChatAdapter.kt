// android/app/src/main/kotlin/hust/appchat/adapter/MiniChatAdapter.kt
package hust.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import hust.appchat.R

/**
 * RecyclerView Adapter for mini chat messages
 */
class MiniChatAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<MiniChatAdapter.MessageViewHolder>() {

    data class ChatMessage(
        val id: String,
        val content: String,
        val isFromMe: Boolean,
        val timestamp: Long,
        val type: Int = 0
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromMe) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)

        fun bind(message: ChatMessage) {
            messageText.text = message.content
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
    }
}