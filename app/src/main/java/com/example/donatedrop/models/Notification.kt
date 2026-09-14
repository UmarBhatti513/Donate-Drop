package com.example.donatedrop.models

import com.google.firebase.Timestamp

data class Notification(
    val id: String,
    val to: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val requestId: String = "",
    val message: String = "",
    val type: String = "",          // "claimed" or "completed" etc.
    val createdAt: Timestamp? = null,
    val read: Boolean = false,
    val meta: Map<String, Any>? = null
)