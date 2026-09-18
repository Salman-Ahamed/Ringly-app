package com.ringly.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.ringly.app.sync.SyncLog

class DefaultSmsChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("package")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_DEFAULT_SMS, pkg == context.packageName)
        }
        SyncLog.d(TAG, "default sms package: $pkg")
    }

    companion object {
        private const val TAG = "RinglySms"
        private const val PREFS = "sms_prefs"
        private const val KEY_DEFAULT_SMS = "default_sms_app"
    }
}