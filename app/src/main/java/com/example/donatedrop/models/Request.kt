package com.example.donatedrop.models

data class Request(
    val id: String = "",
    val createdBy: String? = null,
    val bloodType: String? = null,
    val units: Int? = null,
    val phone: String? = null,
    val address: String? = null,
    val status: String? = null,
    val timestamp: Long? = null
)