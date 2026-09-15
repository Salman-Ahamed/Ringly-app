package com.ringly.app.dialer

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

class ContactListAdapter(
    private val onContactClick: (MergedContact) -> Unit
) : RecyclerView.Adapter<ContactListAdapter.ContactViewHolder>() {

    private val items = mutableListOf<MergedContact>()

    fun submit(list: List<MergedContact>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarLetter: TextView = itemView.findViewById(R.id.contact_avatar_letter)
        private val avatarPhoto: ImageView = itemView.findViewById(R.id.contact_avatar_photo)
        private val nameText: TextView = itemView.findViewById(R.id.contact_name_text)
        private val numberText: TextView = itemView.findViewById(R.id.contact_number_text)
        private val savedByText: TextView = itemView.findViewById(R.id.contact_saved_by_text)

        fun bind(contact: MergedContact) {
            avatarLetter.text = contact.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
            nameText.text = contact.name
            numberText.text = contact.number

            val savedBy = contact.ownerName
            if (contact.source == ContactSource.POOL && savedBy != null) {
                savedByText.text = itemView.context.getString(
                    R.string.dialer_lookup_saved_by_format, savedBy
                )
                savedByText.isVisible = true
            } else {
                savedByText.isVisible = false
            }

            if (contact.photoUrl != null) {
                avatarPhoto.load(contact.photoUrl) { transformations(CircleCropTransformation()) }
                avatarPhoto.isVisible = true
            } else {
                avatarPhoto.isVisible = false
            }

            itemView.setOnClickListener { onContactClick(contact) }
        }
    }
}