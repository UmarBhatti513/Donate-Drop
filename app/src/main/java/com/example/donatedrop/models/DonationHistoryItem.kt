package com.example.donatedrop.models

import com.google.firebase.Timestamp

data class DonationHistoryItem(
    val id: String = "",
    val bloodType: String = "",
    val receiverName: String = "",
    val responderName: String = "",
    val contact: String = "",
    val address: String = "",
    val createdAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val status: String? = null,
    val quantity: String? = null,
    val createdBy: String? = null,
    val completedBy: String? = null
)
