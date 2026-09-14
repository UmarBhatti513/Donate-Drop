package com.example.donatedrop.models

import com.google.firebase.Timestamp

data class CreateRequest(
    val id: String,
    val name: String,
    val contact: String,
    val urgency: String,
    val bloodType: String,
    val quantityStr: String,
    val address: String,
    val notes: String,
    val createdAt: Timestamp?,
    val createdBy: String,
    val status: String,
    val timestamp: Long? = null,
    val responderId: String? = null,
    val responderName: String? = null,
)