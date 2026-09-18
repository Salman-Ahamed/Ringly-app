package com.ringly.app.sms

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ringly.app.R

data class SmsUiMessage(
    val body: String,
    val timeLabel: String,
    val outgoing: Boolean
)

class SmsMessageAdapter : RecyclerView.Adapter<SmsMessageAdapter.MessageViewHolder>() {

    private val items = mutableListOf<SmsUiMessage>()

    fun submit(list: List<SmsUiMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: LinearLayout = itemView.findViewById(R.id.message_bubble)
        private val bodyText: TextView = itemView.findViewById(R.id.message_body_text)
        private val timeText: TextView = itemView.findViewById(R.id.message_time_text)

        fun bind(message: SmsUiMessage) {
            bodyText.text = message.body
            timeText.text = message.timeLabel

            bubble.setBackgroundResource(
                if (message.outgoing) R.drawable.bg_bubble_out else R.drawable.bg_bubble_in
            )
            bodyText.setTextColor(
                itemView.context.getColor(
                    if (message.outgoing) R.color.colorOnPrimaryContainer else R.color.text_navy
                )
            )

            val params = bubble.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (message.outgoing) Gravity.END else Gravity.START
            bubble.layoutParams = params
        }
    }
}