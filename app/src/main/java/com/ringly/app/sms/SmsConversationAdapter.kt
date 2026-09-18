package com.ringly.app.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.ringly.app.R

data class SmsUiConversation(
    val threadId: Long,
    val address: String,
    val title: String,
    val photoUrl: String?,
    val snippet: String,
    val timeLabel: String,
    val unread: Boolean
)

class SmsConversationAdapter(
    private val onConversationClick: (SmsUiConversation) -> Unit
) : RecyclerView.Adapter<SmsConversationAdapter.ConversationViewHolder>() {

    private val items = mutableListOf<SmsUiConversation>()

    fun submit(list: List<SmsUiConversation>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarLetter: TextView = itemView.findViewById(R.id.conversation_avatar_letter)
        private val avatarPhoto: ImageView = itemView.findViewById(R.id.conversation_avatar_photo)
        private val nameText: TextView = itemView.findViewById(R.id.conversation_name_text)
        private val snippetText: TextView = itemView.findViewById(R.id.conversation_snippet_text)
        private val timeText: TextView = itemView.findViewById(R.id.conversation_time_text)
        private val unreadDot: TextView = itemView.findViewById(R.id.conversation_unread_dot)

        fun bind(conversation: SmsUiConversation) {
            avatarLetter.text = conversation.title.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
            nameText.text = conversation.title
            snippetText.text = conversation.snippet
            timeText.text = conversation.timeLabel
            unreadDot.isVisible = conversation.unread

            if (conversation.photoUrl != null) {
                avatarPhoto.load(conversation.photoUrl) { transformations(CircleCropTransformation()) }
                avatarPhoto.isVisible = true
            } else {
                avatarPhoto.isVisible = false
            }

            itemView.setOnClickListener { onConversationClick(conversation) }
        }
    }
}