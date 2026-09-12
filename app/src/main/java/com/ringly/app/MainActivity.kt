package com.ringly.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ringly.app.util.DeviceIdManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        findViewById<TextView>(R.id.device_id_text).text =
            getString(R.string.device_id_label_format, deviceId)
    }
}