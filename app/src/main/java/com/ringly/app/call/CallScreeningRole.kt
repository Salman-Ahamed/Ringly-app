package com.ringly.app.call

object CallScreeningRole {

    enum class Status {
        GRANTED,
        NEEDED,
        UNAVAILABLE
    }

    fun isSupported(apiLevel: Int): Boolean = apiLevel >= 29

    fun status(apiLevel: Int, roleHeld: Boolean): Status = when {
        !isSupported(apiLevel) -> Status.UNAVAILABLE
        roleHeld -> Status.GRANTED
        else -> Status.NEEDED
    }
}