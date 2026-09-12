package com.ringly.app

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ringly.app.util.DeviceIdManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        findViewById<TextView>(R.id.device_id_text).text =
            getString(R.string.device_id_label_format, deviceId)

        val statusText = findViewById<TextView>(R.id.status_text)
        val session = (application as RinglyApp).sessionManager

        val existingUserId = session.userId
        if (existingUserId != null) {
            statusText.text = getString(R.string.status_registered_format, existingUserId)
        } else {
            statusText.text = getString(R.string.status_registering)
            lifecycleScope.launch {
                session.ensureRegistered(deviceId, Build.MODEL)
                    .onSuccess { userId ->
                        statusText.text = getString(R.string.status_registered_format, userId)
                    }
                    .onFailure {
                        statusText.text = getString(R.string.status_register_failed)
                    }
            }
        }
    }
}