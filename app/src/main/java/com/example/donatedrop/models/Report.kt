package com.example.donatedrop.models

data class Report(
    val title: String,
    val summary: String,
    val month: Int = 0,
    val year: Int = 0
)
