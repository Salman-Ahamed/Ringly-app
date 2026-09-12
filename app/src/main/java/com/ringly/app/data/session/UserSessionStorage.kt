package com.ringly.app.data.session

import android.content.Context

interface UserSessionStorage {
    var userId: String?
    var userName: String?
    fun clear()
}

class SharedPreferencesUserSessionStorage(context: Context) : UserSessionStorage {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    override var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    override fun clear() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_USER_NAME).apply()
    }

    private companion object {
        const val PREFS = "ringly_prefs"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
    }
}