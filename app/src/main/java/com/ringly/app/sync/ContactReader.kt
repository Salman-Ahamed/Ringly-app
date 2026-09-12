package com.ringly.app.sync

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import com.ringly.app.util.NumberNormalizer

interface ContactReader {
    fun readAll(): List<ContactReadCandidate>
    fun readEncodedPhoto(contactId: String): String?
}

class DefaultContactReader(private val context: Context) : ContactReader {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override fun readAll(): List<ContactReadCandidate> {
        val rows = mutableListOf<RawContactRow>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex) ?: continue
                val contactId = cursor.getString(idIndex) ?: continue
                rows.add(RawContactRow(contactId, cursor.getString(nameIndex) ?: "", number))
            }
        }

        val hashCache = HashMap<String, String?>()
        return mapCandidates(rows) { contactId ->
            hashCache.getOrPut(contactId) { photoHash(contactId) }
        }
    }

    override fun readEncodedPhoto(contactId: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        val stream = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, true)
            ?: return null
        val bitmap = stream.use { BitmapFactory.decodeStream(it) } ?: return null
        return ContactPhotoEncoder.encode(bitmap)
    }

    private fun photoHash(contactId: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        val stream = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, true)
            ?: return null
        val bytes = stream.use { it.readBytes() }
        if (bytes.isEmpty()) return null
        return ContactPhotoEncoder.sha256Hex(bytes)
    }

    companion object {
        const val MAX_NAME_LENGTH = 255

        private val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        fun mapCandidates(
            rows: List<RawContactRow>,
            photoHashFor: (String) -> String?
        ): List<ContactReadCandidate> {
            val candidates = mutableListOf<ContactReadCandidate>()
            for (row in rows) {
                val number = NumberNormalizer.normalize(row.number) ?: continue
                val name = row.name.trim().take(MAX_NAME_LENGTH).ifEmpty { number }
                candidates.add(ContactReadCandidate(number, row.contactId, name, photoHashFor(row.contactId)))
            }
            return candidates
        }
    }
}