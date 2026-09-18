package com.ringly.app.call

import android.telecom.Call

internal class InCallCallback(
    private val onChange: (Call) -> Unit
) : Call.Callback() {

    override fun onStateChanged(call: Call, state: Int) {
        onChange(call)
    }

    override fun onDetailsChanged(call: Call, details: Call.Details) {
        onChange(call)
    }
}