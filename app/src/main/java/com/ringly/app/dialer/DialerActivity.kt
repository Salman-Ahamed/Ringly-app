package com.ringly.app.dialer

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.ringly.app.R
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.repository.ContactRepository
import com.ringly.app.sync.SharedPreferencesSyncSnapshotStorage
import com.ringly.app.util.PermissionHelper

class DialerActivity : AppCompatActivity() {

    private val contactRepository by lazy { ContactRepository() }
    private val snapshotStorage by lazy { SharedPreferencesSyncSnapshotStorage(this) }

    private lateinit var digitsText: TextView
    private lateinit var suggestionRow: LinearLayout
    private lateinit var suggestionAvatarLetter: TextView
    private lateinit var suggestionAvatarPhoto: ImageView
    private lateinit var suggestionNameText: TextView
    private lateinit var suggestionSavedByText: TextView

    private lateinit var lookupEngine: OutgoingContactLookup

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                placeCall()
            } else {
                openDialerWithNumber()
            }
        }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            lookupEngine.onDigitsChanged(s?.toString().orEmpty())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)

        digitsText = findViewById(R.id.digits_text)
        suggestionRow = findViewById(R.id.suggestion_row)
        suggestionAvatarLetter = findViewById(R.id.suggestion_avatar_letter)
        suggestionAvatarPhoto = findViewById(R.id.suggestion_avatar_photo)
        suggestionNameText = findViewById(R.id.suggestion_name_text)
        suggestionSavedByText = findViewById(R.id.suggestion_saved_by_text)

        buildDialpad(findViewById(R.id.dialpad))

        findViewById<Button>(R.id.delete_button).setOnClickListener {
            val current = digitsText.text.toString()
            if (current.isNotEmpty()) {
                digitsText.setText(current.dropLast(1))
            }
        }

        findViewById<View>(R.id.call_button).setOnClickListener { handleCallClick() }

        lookupEngine = OutgoingContactLookup(
            scope = lifecycleScope,
            lookup = { contactRepository.lookup(it) },
            localNumbers = { snapshotStorage.load().entries.keys },
            onResult = { onSuggestionResult(it) }
        )

        digitsText.addTextChangedListener(textWatcher)
        handleIncomingDialIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDialIntent(intent)
    }

    private fun handleIncomingDialIntent(intent: Intent?) {
        val number = dialNumberFrom(intent) ?: return
        digitsText.setText(number)
    }

    private fun dialNumberFrom(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        return when (uri.scheme) {
            "tel" -> uri.schemeSpecificPart
            "voicemail" -> "voicemail"
            else -> null
        }
    }

    override fun onDestroy() {
        digitsText.removeTextChangedListener(textWatcher)
        lookupEngine.clear()
        super.onDestroy()
    }

    private fun buildDialpad(dialpad: GridLayout) {
        dialpad.rowCount = 4
        dialpad.columnCount = 3
        val keys = listOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "*", "0", "#"
        )
        var index = 0
        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val key = keys[index++]
                val button = Button(this)
                button.text = key
                button.textSize = 26f
                button.typeface = Typeface.MONOSPACE
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(row, 1f),
                    GridLayout.spec(col, 1f)
                )
                params.width = 0
                params.height = 0
                button.layoutParams = params
                button.setOnClickListener { digitsText.append(key) }
                dialpad.addView(button, params)
            }
        }
    }

    private fun handleCallClick() {
        if (digitsText.text.isNullOrEmpty()) return
        if (PermissionHelper.hasPermission(this, android.Manifest.permission.CALL_PHONE)) {
            placeCall()
        } else {
            callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
        }
    }

    private fun placeCall() {
        val raw = dialDigits()
        if (raw.isEmpty()) return
        val telecomManager = getSystemService(TelecomManager::class.java) ?: run {
            openDialerWithNumber()
            return
        }
        try {
            val account = telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
                ?: telecomManager.callCapablePhoneAccounts?.firstOrNull()
            val extras = Bundle()
            if (account != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            }
            telecomManager.placeCall(Uri.parse("tel:$raw"), extras)
        } catch (e: SecurityException) {
            openDialerWithNumber()
        }
    }

    private fun openDialerWithNumber() {
        val raw = dialDigits()
        if (raw.isEmpty()) return
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$raw")))
    }

    private fun dialDigits(): String =
        digitsText.text.toString().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    private fun onSuggestionResult(match: LookupMatch?) {
        if (match == null) {
            suggestionRow.isVisible = false
            return
        }
        suggestionAvatarLetter.text = match.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        suggestionNameText.text = match.name
        suggestionSavedByText.text = getString(R.string.dialer_lookup_saved_by_format, match.ownerName)
        if (match.photoUrl != null) {
            suggestionAvatarPhoto.load(match.photoUrl) {
                transformations(CircleCropTransformation())
            }
            suggestionAvatarPhoto.isVisible = true
        } else {
            suggestionAvatarPhoto.isVisible = false
        }
        suggestionRow.isVisible = true
    }
}