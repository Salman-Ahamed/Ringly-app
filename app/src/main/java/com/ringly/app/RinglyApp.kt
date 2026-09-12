package com.ringly.app

import android.app.Application
import com.ringly.app.data.session.SessionManager
import com.ringly.app.data.session.SharedPreferencesUserSessionStorage

class RinglyApp : Application() {

    val sessionManager: SessionManager by lazy {
        SessionManager(SharedPreferencesUserSessionStorage(this))
    }
}