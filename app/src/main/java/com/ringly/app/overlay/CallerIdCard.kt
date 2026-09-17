package com.ringly.app.overlay

data class CallerIdCard(
    val number: String,
    val name: String,
    val sourceLabel: String,
    val photoUrl: String? = null
)