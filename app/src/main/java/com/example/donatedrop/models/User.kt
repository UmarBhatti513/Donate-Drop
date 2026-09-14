package com.example.donatedrop.models

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "User",
    val avatarUrl: String? = null,
    val phone: String? = null
)
